/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * See ztek_engine.h. The DSP itself is C (dsp/); this file is the C++ glue that owns one
 * instance of each block and renders a stereo block: looper clock/events, acid sequencer,
 * voice (engine 0 = the ATEK303 voice) or bass engine (1..4), FX chain, resample ring. */
#include "ztek_engine.h"

extern "C" {
#include "dsp/atek_voice.h"
#include "dsp/bass_engines.h"
#include "dsp/fx.h"
#include "dsp/acid_pattern.h"
#include "dsp/midi_looper.h"
#include "dsp/fastmath.h"
}

#include <stdlib.h>
#include <string.h>
#include <new>
#include <memory>
#include <oboe/Oboe.h>

#define ZTEK_MAX_BLOCK 4096

struct ztek_s : public oboe::AudioStreamDataCallback {
    float sr;
    float bpm;
    float master;
    int engine;                     /* 0..AT_NENGINES-1 */
    int root;                       /* MIDI note of the acid scale root */
    int scale;

    at_voice_t voice;
    bass_t bass;
    float param[AT_NPARAMS];        /* last AT_ values, re-applied on engine switch */

    ml_t loop;

    acid_pattern_t pat;
    acid_params_t ap;
    acid_grid_t grid;
    int acid_on;
    int seq_step;
    float acid_clk;                 /* 1/64-bar accumulator */
    int seq_note;                   /* the note the sequencer is holding, -1 none */

    float *mono;                    /* scratch, ZTEK_MAX_BLOCK */
    float lbuf[ZTEK_MAX_BLOCK], rbuf[ZTEK_MAX_BLOCK];

    std::shared_ptr<oboe::AudioStream> out;

    /* SMP page: 4 s audio-in buffer + one-shot player */
    std::shared_ptr<oboe::AudioStream> in;
    float *smp;                     /* ZTEK_SMP_SECONDS * sr frames, heap (".pool" on the FM-1) */
    uint32_t smp_cap, smp_len, smp_wr;
    int smp_rec, smp_play;
    float smp_start, smp_end, smp_vol, smp_pitch;   /* 0..1, 0..1, 0..1, 0..1 (±24 st) */
    int smp_mode;                   /* 0 OFF 1 BEAT 2 CLEAN 3 OLD */
    double smp_pos;                 /* play position, in frames within [start,end) */
    int32_t smp_delay;              /* BEAT: samples until the next beat */

    /* circuit bend (post-master) */
    int bend_on, bend_mode;
    float bend_x, bend_y;
    float bend_hold;                /* sample-hold state */
    float bend_trem_ph;             /* trémolo phase */
    uint32_t bend_rng;

    /* copie des derniers fx_set (fx.c garde les siens en static) — pour SNAP/KIT */
    float fxparam[FX_NPARAMS];

    /* page XY (la modulation LFO/ENV/ASSIGN vit côté branche seq-plus, PR #3) */
    int xy_on, xy_hold;
    int xy_target[2];               /* 0..7 voix/AT_ENGINE, 8..25 FX */
    float xy_saved[26];             /* valeurs à l'entrée de la page (MOM) */

    /* sirène */
    int siren_on;
    float siren_lvl, siren_t, siren_fb;
    float siren_ph, siren_f, siren_out, siren_sw;

    /* PoW! (dernier état, pour les snapshots) */
    int punch_on, punch_type;
    float punch_a, punch_b, punch_mix;

    /* SNAP (16) / KIT (16) / undo-redo (3 niveaux) */
    struct ztek_state_t {
        int engine;
        float param[AT_NPARAMS];
        float fx[FX_NPARAMS];
        int punch_on, punch_type;
        float pa, pb, pmix;
        int xy_on, xy_hold;
        int xy_target[2];
        float bpm;
        uint16_t slotmask;
    } snap[16], kit[16], undo_st[3], redo_st[3];
    int undo_n, redo_n;

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override
    {
        if (stream == out.get()) {
            float *o = static_cast<float *>(audioData);
            uint32_t done = 0;
            while (done < (uint32_t)numFrames) {
                uint32_t blk = (uint32_t)numFrames - done;
                if (blk > ZTEK_MAX_BLOCK) blk = ZTEK_MAX_BLOCK;
                memset(lbuf, 0, blk * sizeof(float));
                memset(rbuf, 0, blk * sizeof(float));
                ztek_render(this, lbuf, rbuf, blk);
                for (uint32_t i = 0; i < blk; i++) {
                    o[(done + i) * 2] = lbuf[i];
                    o[(done + i) * 2 + 1] = rbuf[i];
                }
                done += blk;
            }
        } else if (stream == in.get() && smp_rec) {
            /* record the audio-in into the SMP buffer */
            const float *ip = static_cast<const float *>(audioData);
            for (int32_t i = 0; i < numFrames; i++) {
                if (smp_wr < smp_cap) smp[smp_wr++] = ip[i];
            }
            if (smp_wr >= smp_cap) smp_wr = 0;   /* ring: overwrite the oldest */
        }
        return oboe::DataCallbackResult::Continue;
    }
};

int ztek_start(ztek_t *z)
{
    if (z->out) return 1;
    oboe::AudioStreamBuilder b;
    b.setDirection(oboe::Direction::Output)
     ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
     ->setSharingMode(oboe::SharingMode::Exclusive)
     ->setSampleRate((int32_t)z->sr)
     ->setChannelCount(2)
     ->setFormat(oboe::AudioFormat::Float)
     ->setDataCallback(z);
    oboe::Result r = b.openStream(z->out);
    if (r != oboe::Result::OK) {
        b.setSharingMode(oboe::SharingMode::Shared);
        r = b.openStream(z->out);
    }
    if (r != oboe::Result::OK) return 0;
    return z->out->requestStart() == oboe::Result::OK ? 1 : 0;
}

void ztek_stop(ztek_t *z)
{
    if (z->out) {
        z->out->stop();
        z->out->close();
        z->out.reset();
    }
}

/* ---- looper callbacks (audio thread): loop events drive the synth -------------------- */

static void ztek_note_on_int(ztek_t *z, int note, int vel, int accent, int slide, int record);

static void cb_note_on(void *ctx, int slot, int note, int vel)
{
    ztek_t *z = (ztek_t *)ctx;
    int n = note + z->loop.slot[slot].sp.pitch;
    if (n < 0) n = 0;
    if (n > 127) n = 127;
    ztek_note_on_int(z, n, vel, vel > 100 ? 1 : 0, 0, 0);
}

static void cb_note_off(void *ctx, int slot, int note)
{
    ztek_t *z = (ztek_t *)ctx;
    int n = note + z->loop.slot[slot].sp.pitch;
    if (n < 0) n = 0;
    if (n > 127) n = 127;
    if (z->engine == 0) at_gate(&z->voice, 0, n, 0, 0);
    else bass_gate(&z->bass, 0, n, 0, 0);
}

static void cb_param(void *ctx, int slot, int key, float v)
{
    ztek_t *z = (ztek_t *)ctx;
    (void)slot;
    if (key >= 0 && key < AT_NPARAMS) ztek_set_param(z, key, v);
}

/* ---- engine ------------------------------------------------------------------------ */

ztek_t *ztek_create(float sr)
{
    ztek_t *z = new (std::nothrow) ztek_t();   /* value-init: ztek_s has a shared_ptr member */
    if (!z) return NULL;
    z->sr = sr;
    z->bpm = 120.0f;
    z->master = 0.8f;
    z->engine = 0;
    z->root = 45;                   /* A2 */
    z->scale = 0;

    at_init(&z->voice, sr);
    at_option(&z->voice, AT_OPT_DRIFT, 1);
    bass_init(&z->bass, sr);
    for (int p = 0; p < AT_NPARAMS; p++) {
        z->param[p] = 0.5f;
        at_set(&z->voice, p, z->param[p]);
        bass_set(&z->bass, p, z->param[p]);
    }
    z->param[AT_CUTOFF] = 0.7f;
    z->param[AT_DECAY] = 0.55f;
    at_set(&z->voice, AT_CUTOFF, 0.7f);
    at_set(&z->voice, AT_DECAY, 0.55f);
    bass_set(&z->bass, AT_CUTOFF, 0.7f);
    bass_set(&z->bass, AT_DECAY, 0.55f);

    fx_init(sr);
    fx_set(FX_LEVEL, 0.9f);

    ml_init(&z->loop, sr);
    ml_set_cb(&z->loop, z, cb_note_on, cb_note_off, cb_param);

    acid_params_default(&z->ap);
    z->ap.scale = 0;
    acid_clear(&z->pat);
    acid_generate(&z->pat, &z->ap, 0x20261119u);
    acid_render(&z->pat, &z->grid);
    z->acid_on = 0;
    z->seq_step = 0;
    z->acid_clk = 0.0f;
    z->seq_note = -1;

    z->mono = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    z->smp_cap = (uint32_t)(ZTEK_SMP_SECONDS * sr);
    z->smp = (float *)malloc(z->smp_cap * sizeof(float));
    if (!z->mono || !z->smp) { free(z->mono); free(z->smp); delete z; return NULL; }
    z->smp_len = 0; z->smp_wr = 0;
    z->smp_rec = 0; z->smp_play = 0;
    z->smp_start = 0.0f; z->smp_end = 1.0f; z->smp_vol = 0.8f; z->smp_pitch = 0.5f;
    z->smp_mode = 2;                 /* CLEAN par défaut */
    z->smp_pos = 0.0; z->smp_delay = 0;
    z->bend_on = 0; z->bend_mode = 0; z->bend_x = 0.0f; z->bend_y = 0.0f;
    z->bend_hold = 0.0f; z->bend_trem_ph = 0.0f; z->bend_rng = 0xBEAD01u & 0x7fffffffu;
    z->xy_on = 0; z->xy_hold = 0; z->xy_target[0] = 1; z->xy_target[1] = 2;   /* X->CUTOFF Y->RESO */
    z->siren_on = 0; z->siren_lvl = 0.3f; z->siren_t = 0.5f; z->siren_fb = 0.0f;
    z->siren_ph = 0.0f; z->siren_f = 600.0f; z->siren_out = 0.0f; z->siren_sw = 0.0f;
    z->punch_on = 0; z->punch_type = 0; z->punch_a = z->punch_b = z->punch_mix = 0.0f;
    z->undo_n = 0; z->redo_n = 0;
    memset(z->fxparam, 0, sizeof z->fxparam);
    memset(z->snap, 0, sizeof z->snap);
    memset(z->kit, 0, sizeof z->kit);
    return z;
}

void ztek_destroy(ztek_t *z)
{
    if (!z) return;
    ztek_stop(z);
    if (z->in) { z->in->stop(); z->in->close(); z->in.reset(); }
    free(z->mono);
    free(z->smp);
    delete z;
}

static void ztek_note_on_int(ztek_t *z, int note, int vel, int accent, int slide, int record)
{
    int cls = z->engine == 0 ? 0 : 1;           /* 0 bass, 1 synth (midi_looper.h) */
    if (record) ml_note_in(&z->loop, note, vel, 4, cls);
    if (z->engine == 0) at_gate(&z->voice, 1, note, accent, slide);
    else bass_gate(&z->bass, 1, note, accent, slide);
}

void ztek_note_on(ztek_t *z, int note, int vel, int accent, int slide)
{
    ztek_note_on_int(z, note, vel, accent, slide, 1);
}

void ztek_note_off(ztek_t *z, int note)
{
    ml_note_off_in(&z->loop, note);
    if (z->engine == 0) at_gate(&z->voice, 0, note, 0, 0);
    else bass_gate(&z->bass, 0, note, 0, 0);
}

void ztek_all_notes_off(ztek_t *z)
{
    if (z->engine == 0) at_panic(&z->voice);
    else bass_panic(&z->bass);
    z->seq_note = -1;
}

void ztek_set_param(ztek_t *z, int param, float v)
{
    if (param < 0 || param >= AT_NPARAMS) return;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    z->param[param] = v;
    at_set(&z->voice, param, v);
    bass_set(&z->bass, param, v);
}

void ztek_set_engine(ztek_t *z, int engine)
{
    if (engine < 0) engine = 0;
    if (engine >= AT_NENGINES) engine = AT_NENGINES - 1;
    z->engine = engine;
    z->param[AT_ENGINE] = (float)engine / (float)(AT_NENGINES - 1);
    /* re-apply the shared knobs (incl. AT_ENGINE) so the new engine starts from the same page */
    for (int p = 0; p < AT_NPARAMS; p++) {
        at_set(&z->voice, p, z->param[p]);
        bass_set(&z->bass, p, z->param[p]);
    }
}

int ztek_get_engine(const ztek_t *z) { return z->engine; }

void ztek_set_bpm(ztek_t *z, float bpm)
{
    z->bpm = bpm;
    ml_set_bpm(&z->loop, bpm);
}

void ztek_set_root(ztek_t *z, int root) { z->root = root; }
void ztek_set_scale(ztek_t *z, int scale)
{
    z->scale = scale;
    z->ap.scale = scale;
}

void ztek_fx_set(ztek_t *z, int param, float v)
{
    if (param >= 0 && param < FX_NPARAMS) z->fxparam[param] = v;
    fx_set(param, v);
}

void ztek_punch(ztek_t *z, int on, int type, float a, float b, float mix)
{
    z->punch_on = on; z->punch_type = type;
    z->punch_a = a; z->punch_b = b; z->punch_mix = mix;
    fx_punch(on, type, a, b, mix);
}

void ztek_punch_tail(ztek_t *z, float t) { (void)z; fx_punch_off_tail(t); }

void ztek_loop_play(ztek_t *z, int on) { ml_play(&z->loop, on); }
void ztek_loop_toggle(ztek_t *z, int slot) { ml_toggle(&z->loop, slot); }
void ztek_loop_rec_start(ztek_t *z, int slot, int od) { ml_rec_start(&z->loop, slot, od); }
void ztek_loop_rec_stop(ztek_t *z, int slot) { ml_rec_stop(&z->loop, slot); }
void ztek_loop_erase(ztek_t *z, int slot) { ml_erase(&z->loop, slot); }
void ztek_loop_set_len(ztek_t *z, int slot, int len64) { ml_set_len(&z->loop, slot, len64); }
void ztek_loop_set_sp(ztek_t *z, int slot, int idx, float v) { ml_set_sp(&z->loop, slot, idx, v); }
int ztek_loop_slot_on(const ztek_t *z, int slot)
{
    return slot >= 0 && slot < ML_NSLOTS ? z->loop.slot[slot].on : 0;
}
int ztek_loop_slot_rec(const ztek_t *z, int slot)
{
    return slot >= 0 && slot < ML_NSLOTS ? z->loop.slot[slot].rec : 0;
}

float ztek_loop_get_sp(const ztek_t *z, int slot, int idx)
{
    return ml_get_sp((ml_t *)&z->loop, slot, idx);
}

void ztek_acid_generate(ztek_t *z, uint32_t seed)
{
    z->ap.scale = z->scale;
    acid_generate(&z->pat, &z->ap, seed);
    acid_render(&z->pat, &z->grid);
    z->seq_step = 0;
}

void ztek_acid_play(ztek_t *z, int on) { z->acid_on = on ? 1 : 0; }

void ztek_acid_knob(ztek_t *z, int which, float v)
{
    switch (which) {
    case 0: z->ap.density = v; break;
    case 1: z->ap.accent = v; break;
    case 2: z->ap.slide = v; break;
    case 3: z->ap.range = v; break;
    case 4: z->ap.tie = v; break;
    }
}

/* ---- SMP page (audio-in sampler) ------------------------------------------------ */

void ztek_smp_rec(ztek_t *z, int on)
{
    if (on && !z->in) {
        oboe::AudioStreamBuilder b;
        b.setDirection(oboe::Direction::Input)
         ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
         ->setSharingMode(oboe::SharingMode::Shared)
         ->setSampleRate((int32_t)z->sr)
         ->setChannelCount(1)
         ->setFormat(oboe::AudioFormat::Float)
         ->setDataCallback(z);
        if (b.openStream(z->in) != oboe::Result::OK) return;
        if (z->in->requestStart() != oboe::Result::OK) { z->in->close(); z->in.reset(); return; }
    }
    if (on) {
        z->smp_wr = 0;
        z->smp_len = 0;
        z->smp_rec = 1;
    } else {
        z->smp_rec = 0;
        z->smp_len = z->smp_wr;
        if (z->in) { z->in->stop(); z->in->close(); z->in.reset(); }
    }
}

void ztek_smp_play(ztek_t *z, int on)
{
    if (on && z->smp_len > 0) {
        z->smp_pos = 0.0;
        z->smp_delay = 0;
        if (z->smp_mode == 1 && z->loop.playing) {
            /* BEAT : démarre sur le prochain temps (1/16) de l'horloge du looper */
            float beat64 = 16.0f * 60.0f / z->bpm;            /* 1/64 bar en samples */
            float next = (fm_floorf(z->loop.clk / 16.0f) + 1.0f) * 16.0f;   /* next beat, in 1/64 */
            float d = (next - z->loop.clk) * beat64 / 16.0f;
            z->smp_delay = (int32_t)(d > 0.0f ? d : beat64);
        }
        z->smp_play = 1;
    } else {
        z->smp_play = 0;
    }
}

void ztek_smp_set(ztek_t *z, int which, float v)
{
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    switch (which) {
    case 0: z->smp_start = v; break;
    case 1: z->smp_end = v; if (z->smp_end <= z->smp_start) z->smp_end = z->smp_start + 0.01f; break;
    case 2: z->smp_vol = v; break;
    case 3: z->smp_pitch = v; break;
    case 4: z->smp_mode = (int)(v * 3.999f); break;
    }
}

int ztek_smp_len(const ztek_t *z) { return (int)z->smp_len; }

void ztek_bend(ztek_t *z, int on, float x, float y, int mode)
{
    z->bend_on = on ? 1 : 0;
    z->bend_x = x < 0.0f ? 0.0f : x > 1.0f ? 1.0f : x;
    z->bend_y = y < 0.0f ? 0.0f : y > 1.0f ? 1.0f : y;
    z->bend_mode = mode;
}

/* ---- SNAP / KIT / undo-redo -------------------------------------------------------- */

static void ztek_capture(ztek_t *z, ztek_t::ztek_state_t *s)
{
    int i;
    s->engine = z->engine;
    memcpy(s->param, z->param, sizeof s->param);
    memcpy(s->fx, z->fxparam, sizeof s->fx);
    s->punch_on = z->punch_on; s->punch_type = z->punch_type;
    s->pa = z->punch_a; s->pb = z->punch_b; s->pmix = z->punch_mix;
    s->xy_on = z->xy_on; s->xy_hold = z->xy_hold;
    s->xy_target[0] = z->xy_target[0]; s->xy_target[1] = z->xy_target[1];
    s->bpm = z->bpm;
    s->slotmask = 0;
    for (i = 0; i < ML_NSLOTS; i++) if (z->loop.slot[i].on) s->slotmask |= (uint16_t)(1u << i);
}

static void ztek_apply(ztek_t *z, const ztek_t::ztek_state_t *s)
{
    int i;
    ztek_set_engine(z, s->engine);
    for (i = 0; i < AT_NPARAMS; i++) ztek_set_param(z, i, s->param[i]);
    for (i = 0; i < FX_NPARAMS; i++) ztek_fx_set(z, i, s->fx[i]);
    ztek_punch(z, s->punch_on, s->punch_type, s->pa, s->pb, s->pmix);
    z->xy_hold = s->xy_hold;
    z->xy_target[0] = s->xy_target[0]; z->xy_target[1] = s->xy_target[1];
    ztek_set_bpm(z, s->bpm);
    for (i = 0; i < ML_NSLOTS; i++) {
        int want = (s->slotmask >> i) & 1u;
        if (want != z->loop.slot[i].on) ml_toggle(&z->loop, i);
    }
}

static void ztek_push_undo(ztek_t *z)
{
    if (z->undo_n >= 3) {
        memmove(z->undo_st, z->undo_st + 1, 2 * sizeof(ztek_t::ztek_state_t));
        z->undo_n = 2;
    }
    ztek_capture(z, &z->undo_st[z->undo_n++]);
    z->redo_n = 0;
}

void ztek_snap_store(ztek_t *z, int slot)
{
    if (slot < 0 || slot >= 16) return;
    ztek_push_undo(z);
    ztek_capture(z, &z->snap[slot]);
}

void ztek_snap_load(ztek_t *z, int slot)
{
    if (slot < 0 || slot >= 16) return;
    if (z->snap[slot].bpm <= 0.0f) return;      /* slot vide */
    ztek_push_undo(z);
    ztek_apply(z, &z->snap[slot]);
}

void ztek_undo(ztek_t *z)
{
    if (z->undo_n <= 0) return;
    if (z->redo_n < 3) ztek_capture(z, &z->redo_st[z->redo_n++]);
    ztek_apply(z, &z->undo_st[--z->undo_n]);
}

void ztek_redo(ztek_t *z)
{
    if (z->redo_n <= 0) return;
    ztek_push_undo(z);
    z->undo_n--;                                 /* push_undo a remis l'état courant : on l'échange */
    ztek_apply(z, &z->redo_st[--z->redo_n]);
}

void ztek_kit_store(ztek_t *z, int slot)
{
    if (slot < 0 || slot >= 16) return;
    ztek_capture(z, &z->kit[slot]);
}

void ztek_kit_load(ztek_t *z, int slot)
{
    if (slot < 0 || slot >= 16) return;
    if (z->kit[slot].bpm <= 0.0f) return;
    ztek_push_undo(z);
    ztek_apply(z, &z->kit[slot]);
}

/* ---- page XY ------------------------------------------------------------------------ */

static float ztek_target_value(ztek_t *z, int t)
{
    if (t <= 6) return z->param[t];
    if (t == 7) return (float)z->engine / (float)(AT_NENGINES - 1);
    if (t < 26) return z->fxparam[t - 8];
    return 0.0f;
}

static void ztek_apply_target(ztek_t *z, int t, float v)
{
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    if (t <= 6) ztek_set_param(z, t, v);
    else if (t == 7) ztek_set_engine(z, at_engine_index(v));
    else if (t < 26) ztek_fx_set(z, t - 8, v);
}

void ztek_xy_target(ztek_t *z, int axis, int target)
{
    if (axis < 0 || axis > 1) return;
    if (target < 0) target = 0;
    if (target > 25) target = 25;
    z->xy_target[axis] = target;
}

void ztek_xy_hold(ztek_t *z, int holdMode) { z->xy_hold = holdMode ? 1 : 0; }

void ztek_xy_set(ztek_t *z, int on, float x, float y)
{
    if (on && !z->xy_on) {
        int t;
        for (t = 0; t < 26; t++) z->xy_saved[t] = ztek_target_value(z, t);
    }
    if (!on && z->xy_on && !z->xy_hold) {
        int t;
        for (t = 0; t < 26; t++) ztek_apply_target(z, t, z->xy_saved[t]);
    }
    z->xy_on = on ? 1 : 0;
    if (z->xy_on) {
        ztek_apply_target(z, z->xy_target[0], x);
        ztek_apply_target(z, z->xy_target[1], y);
    }
}

/* ---- sirène ------------------------------------------------------------------------- */

void ztek_siren(ztek_t *z, int on, float lvl, float t, float fb)
{
    z->siren_on = on ? 1 : 0;
    z->siren_lvl = lvl < 0.0f ? 0.0f : lvl > 1.0f ? 1.0f : lvl;
    z->siren_t = t < 0.0f ? 0.0f : t > 1.0f ? 1.0f : t;
    z->siren_fb = fb;
}

void ztek_smp_peaks(const ztek_t *z, float *out, int nbuckets)
{
    /* min/max par bucket pour la waveform du LCD */
    int i;
    uint32_t len = z->smp_len;
    for (i = 0; i < nbuckets; i++) { out[i * 2] = 0.0f; out[i * 2 + 1] = 0.0f; }
    if (len == 0) return;
    for (i = 0; i < nbuckets; i++) {
        uint32_t b0 = (uint32_t)((uint64_t)i * len / nbuckets);
        uint32_t b1 = (uint32_t)((uint64_t)(i + 1) * len / nbuckets);
        float mn = 0.0f, mx = 0.0f;
        uint32_t k;
        if (b1 <= b0) b1 = b0 + 1;
        for (k = b0; k < b1 && k < len; k++) {
            float x = z->smp[k];
            if (x < mn) mn = x;
            if (x > mx) mx = x;
        }
        out[i * 2] = mn;
        out[i * 2 + 1] = mx;
    }
}

/* one 1/64-bar of the acid sequencer: gate the grid's step */
static void ztek_acid_step(ztek_t *z)
{
    int steps = z->pat.time_length;
    int s;
    if (steps < 1) steps = 1;
    if (steps > ACID_MAX_STEPS) steps = ACID_MAX_STEPS;
    s = z->seq_step;
    if (s >= steps) s = 0;
    z->seq_step = (z->seq_step + 1) % steps;
    if (z->grid.gate[s]) {
        int semi = acid_semi_of(z->grid.deg[s], z->grid.oct[s], z->scale);
        int note = z->root + semi;
        if (note < 0) note = 0;
        if (note > 127) note = 127;
        if (z->seq_note >= 0 && z->seq_note != note) ztek_note_off(z, z->seq_note);
        ztek_note_on_int(z, note, z->grid.accent[s] ? 110 : 90,
                         z->grid.accent[s] ? 1 : 0, z->grid.slide[s] ? 1 : 0, 1);
        z->seq_note = note;
    } else if (z->seq_note >= 0 && !z->grid.tie[s]) {
        ztek_note_off(z, z->seq_note);
        z->seq_note = -1;
    }
}

void ztek_render(ztek_t *z, float *outL, float *outR, uint32_t n)
{
    uint32_t done = 0;
    if (n == 0) return;
    while (done < n) {
        uint32_t blk = n - done;
        if (blk > ZTEK_MAX_BLOCK) blk = ZTEK_MAX_BLOCK;

        /* 1. the looper's clock; its events fire the synth through the callbacks */
        ml_render(&z->loop, blk);

        /* 2. the acid sequencer, one 1/64 bar at a time */
        if (z->acid_on && z->loop.playing) {
            float rate = z->bpm * 16.0f / 60.0f / z->sr;   /* 1/64 bar per sample */
            uint32_t i;
            for (i = 0; i < blk; i++) {
                z->acid_clk += rate;
                while (z->acid_clk >= 1.0f) {
                    z->acid_clk -= 1.0f;
                    ztek_acid_step(z);
                }
            }
        }

        /* 3. the synth bus: engine 0 is the ATEK303 voice, 1..4 the bass engines */
        if (z->engine == 0) at_render(&z->voice, z->mono, blk);
        else bass_render(&z->bass, z->mono, blk);

        /* 3b. sirène : deux sinus désaccordés, balayage lent 300..1400 Hz (période = siren_t) */
        if (z->siren_on) {
            float fsr = 1.0f / z->sr;
            float period = 0.5f + 7.5f * z->siren_t;          /* s */
            z->siren_sw += (float)blk / (z->sr * period);
            if (z->siren_sw >= 1.0f) z->siren_sw -= 1.0f;
            z->siren_f = 300.0f + 1100.0f * (0.5f + 0.5f * fm_sin_turns(z->siren_sw));
            for (uint32_t i = 0; i < blk; i++) {
                z->siren_ph += z->siren_f * fsr;
                if (z->siren_ph >= 1.0f) z->siren_ph -= 1.0f;
                float s = fm_sin_turns(z->siren_ph) + 0.5f * fm_sin_turns(z->siren_ph * 2.003f + z->siren_fb);
                z->siren_out = s * 0.5f * z->siren_lvl;
                z->mono[i] += z->siren_out;
            }
        }

        /* 4. FX chain (mono in, stereo out) + the resample ring */
        fx_process(z->mono, outL + done, outR + done, blk);
        ml_rs_feed(&z->loop, z->mono, blk);
        ml_rs_render(&z->loop, outL + done, outR + done, blk);

        /* 4b. SMP page: one-shot playback of the audio-in buffer, START..END, pitch/mode */
        if (z->smp_play && z->smp_len > 0) {
            uint32_t s0 = (uint32_t)(z->smp_start * (float)z->smp_len);
            uint32_t s1 = (uint32_t)(z->smp_end * (float)z->smp_len);
            if (s1 <= s0) s1 = s0 + 1;
            if (s1 > z->smp_len) s1 = z->smp_len;
            /* ratio de lecture : PITCH ±24 demi-tons ; OLD = décimation x2 en plus */
            float semis = (z->smp_pitch - 0.5f) * 48.0f;
            float ratio = fm_exp2f(semis / 12.0f);
            if (z->smp_mode == 3) ratio *= 0.5f;
            uint32_t span = s1 - s0;
            for (uint32_t i = 0; i < blk; i++) {
                if (z->smp_delay > 0) { z->smp_delay--; continue; }
                uint32_t p = (uint32_t)z->smp_pos;
                if (p >= span) { z->smp_play = 0; break; }
                float fr = (float)p - fm_floorf((float)p);
                uint32_t p1 = p + 1 < span ? p + 1 : p;
                float x = z->smp[s0 + p] + (z->smp[s0 + p1] - z->smp[s0 + p]) * fr;
                x *= z->smp_vol * 0.9f;
                outL[done + i] += x;
                outR[done + i] += x;
                z->smp_pos += (double)ratio;
            }
        }

        done += blk;
    }

    /* 5. master + circuit bend (post-master, docs/ZULUTEK.md Lot D) */
    {
        uint32_t i;
        for (i = 0; i < n; i++) {
            float l = outL[i] * z->master;
            float r = outR[i] * z->master;
            l = fm_tanhf(l * 1.2f) * 0.85f;
            r = fm_tanhf(r * 1.2f) * 0.85f;
            if (z->bend_on) {
                z->bend_rng = z->bend_rng * 1103515245u + 12345u;
                if (z->bend_mode == 0) {
                    /* GLITCH : sample-hold aléatoire + bit-crush + trémolo (X = densité, Y = niveau) */
                    if ((float)(z->bend_rng >> 16) / 32768.0f >= z->bend_x * 0.5f)
                        z->bend_hold = l;          /* on met à jour l'échantillon retenu */
                    l = z->bend_hold;              /* sinon on rejoue le précédent (hold) */
                    float bits = 8.0f - z->bend_x * 6.0f;
                    if (bits < 1.0f) bits = 1.0f;
                    float lv = fm_exp2f(bits - 1.0f);
                    l = fm_floorf(l * lv + 0.5f) / lv;
                    z->bend_trem_ph += (0.02f + 0.2f * z->bend_x) * FM_TWO_PI / z->sr * 60.0f;
                    if (z->bend_trem_ph > FM_TWO_PI) z->bend_trem_ph -= FM_TWO_PI;
                    l *= 1.0f - z->bend_y * 0.8f * (0.5f + 0.5f * fm_sinf(z->bend_trem_ph));
                    r = l;
                } else {
                    /* LOW BAT : décimation + soft-clip dur */
                    z->bend_hold = l;
                    l = fm_tanhf(z->bend_hold * (1.5f + 3.0f * z->bend_y));
                    r = l;
                }
            }
            outL[i] = l;
            outR[i] = r;
        }
    }
}
