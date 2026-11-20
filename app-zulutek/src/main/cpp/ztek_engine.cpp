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

    /* ---- modulation section (pages LFO/ENV), all per 32-sample block -------------- */
    float mod_knob[ZT_NSRC][4];     /* 4 knobs per source */
    float src_val[ZT_NSRC];         /* last computed source values 0..1 */
    int   lfo_sync, lfo_trig;
    float lfo_ph[4], lfo_sh[4], lfo_drunk[4];
    int   lab_type[4];              /* 0..7: LINK MYCELIUM HUMUS NET VINTAGE CHAOS OISEAUX INSECTES MAREE */
    int   lab_src[4][2];            /* two modulation sources per labo */
    float lab_out[4];
    float lab_bird[4][2][16];       /* per-lab bird ring (OISEAUX), two axes */
    int   lab_birdpos[4][2];
    uint32_t lab_rng[4];
    int   lab_clk[4];
    float lab_phase[4];
    float env_ph[2];                /* 0..1 ADSR phase */
    float env_lvl[2];               /* current env level */
    int   env_loop[2];
    int   env_stage[2];             /* 0 A, 1 D, 2 S, 3 R */
    float ef_lvl;                   /* envelope follower state */
    float blk_peak;                 /* peak of the last synth block (EF source) */
    float tool_state[6];            /* SLEW/CMP history etc. */
    int   asg_src[ZT_NDST];         /* 255 = off */
    float asg_depth[ZT_NDST];
    float dst_base[ZT_NDST];        /* base value of each destination (set by the UI setters) */
    float fx_base[FX_NPARAMS];

    /* ---- sequencer advanced ------------------------------------------------------- */
    float seqk[5];                  /* 5 TM LOCK, 6 TM LEN, 7 MUT, 8 PLAY, 9 RAND */
    int   play_pos;                 /* position in the current play mode */
    int   play_dir;                 /* ping-pong direction */
    uint32_t seq_rng;
    int   seq_wrapped;              /* a loop wrap happened, pending TM/RAND at block edge */

    std::shared_ptr<oboe::AudioStream> out;

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *, void *audioData, int32_t numFrames) override
    {
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

    /* modulation defaults */
    for (int s = 0; s < ZT_NSRC; s++) {
        z->src_val[s] = 0.0f;
        for (int k = 0; k < 4; k++) z->mod_knob[s][k] = 0.5f;
    }
    z->mod_knob[0][0] = 0.35f;      /* LFO1 rate */
    z->lfo_sync = 0;
    z->lfo_trig = 0;
    for (int i = 0; i < 4; i++) {
        z->lfo_ph[i] = 0.0f;
        z->lfo_sh[i] = 0.0f;
        z->lfo_drunk[i] = 0.5f;
        z->lab_type[i] = (i == 0) ? 4 : 0;      /* LABO1 defaults to CHAOS */
        z->lab_src[i][0] = 0;
        z->lab_src[i][1] = 1;
        z->lab_out[i] = 0.0f;
        for (int a = 0; a < 2; a++) {
            z->lab_birdpos[i][a] = 0;
            for (int b = 0; b < 16; b++) z->lab_bird[i][a][b] = 0.5f;
        }
        z->lab_rng[i] = 0x1234567u + (uint32_t)i * 2654435761u;
        z->lab_clk[i] = 0;
        z->lab_phase[i] = 0.0f;
    }
    for (int e = 0; e < 2; e++) {
        z->env_ph[e] = 0.0f;
        z->env_lvl[e] = 0.0f;
        z->env_loop[e] = 1;         /* the two ENVs are loopable by default */
        z->env_stage[e] = 0;
    }
    z->ef_lvl = 0.0f;
    z->blk_peak = 0.0f;
    for (int t = 0; t < 6; t++) z->tool_state[t] = 0.0f;
    for (int d = 0; d < ZT_NDST; d++) {
        z->asg_src[d] = 255;
        z->asg_depth[d] = 0.0f;
        z->dst_base[d] = 0.5f;
    }
    for (int p = 0; p < FX_NPARAMS; p++) z->fx_base[p] = 0.5f;
    /* voice destinations mirror z->param (set above) */
    for (int p = 0; p < AT_NPARAMS; p++) z->dst_base[p] = z->param[p];

    /* sequencer advanced defaults */
    for (int k = 0; k < 5; k++) z->seqk[k] = 0.0f;
    z->play_pos = 0;
    z->play_dir = 1;
    z->seq_rng = 0x9e3779b9u;
    z->seq_wrapped = 0;

    z->mono = (float *)malloc(ZTEK_MAX_BLOCK * sizeof(float));
    if (!z->mono) { delete z; return NULL; }
    return z;
}

void ztek_destroy(ztek_t *z)
{
    if (!z) return;
    ztek_stop(z);
    free(z->mono);
    delete z;
}

static void ztek_note_on_int(ztek_t *z, int note, int vel, int accent, int slide, int record)
{
    int cls = z->engine == 0 ? 0 : 1;           /* 0 bass, 1 synth (midi_looper.h) */
    if (record) ml_note_in(&z->loop, note, vel, 4, cls);
    if (z->lfo_trig) {                          /* LFO TRIG: retrigger the 4 LFOs + 2 ENVs */
        for (int i = 0; i < 4; i++) { z->lfo_ph[i] = 0.0f; z->lfo_sh[i] = 0.0f; }
        for (int e = 0; e < 2; e++) { z->env_ph[e] = 0.0f; z->env_stage[e] = 0; }
    }
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
    z->dst_base[param] = v;
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
    z->dst_base[ZT_SEQ_DST0 + 2] = bpm / 300.0f;
    ml_set_bpm(&z->loop, bpm);
}

void ztek_set_root(ztek_t *z, int root)
{
    z->root = root;
    z->dst_base[ZT_SEQ_DST0 + 3] = (float)(root - 24) / 48.0f;
}
void ztek_set_scale(ztek_t *z, int scale)
{
    z->scale = scale;
    z->ap.scale = scale;
    z->dst_base[ZT_SEQ_DST0 + 1] = (float)scale / 7.0f;
}

void ztek_fx_set(ztek_t *z, int param, float v)
{
    if (param < 0 || param >= FX_NPARAMS) return;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    z->fx_base[param] = v;
    if (param < ZT_NDST - 8) z->dst_base[8 + param] = v;
    fx_set(param, v);
}

void ztek_punch(ztek_t *z, int on, int type, float a, float b, float mix)
{
    (void)z;
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

void ztek_acid_generate(ztek_t *z, uint32_t seed)
{
    z->ap.scale = z->scale;
    acid_generate(&z->pat, &z->ap, seed);
    acid_render(&z->pat, &z->grid);
    z->seq_step = 0;
    z->play_pos = 0;
    z->play_dir = 1;
    z->dst_base[ZT_SEQ_DST0 + 0] = (float)z->pat.time_length / 64.0f;
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

static uint32_t ztek_lcg(ztek_t *z)
{
    z->seq_rng = z->seq_rng * 1103515245u + 12345u;
    return z->seq_rng;
}

static float ztek_rnd01(ztek_t *z) { return (float)(ztek_lcg(z) >> 8) / 16777216.0f; }

/* advance the play position according to the PLAY mode (seqk[3]); returns 1 on a loop wrap */
static int ztek_seq_advance(ztek_t *z, int steps)
{
    int mode = z->seqk[3] < 0.34f ? 0 : (z->seqk[3] < 0.67f ? 1 : 2);  /* NORM REV PING-PONG */
    int wrapped = 0;
    if (mode == 0) {
        z->play_pos++;
        if (z->play_pos >= steps) { z->play_pos = 0; wrapped = 1; }
    } else if (mode == 1) {
        z->play_pos--;
        if (z->play_pos < 0) { z->play_pos = steps - 1; wrapped = 1; }
    } else {
        z->play_pos += z->play_dir;
        if (z->play_pos >= steps) {
            z->play_pos = steps - 2; if (z->play_pos < 0) z->play_pos = 0;
            z->play_dir = -1; wrapped = 1;
        } else if (z->play_pos < 0) {
            z->play_pos = 1; if (z->play_pos >= steps) z->play_pos = steps - 1;
            z->play_dir = 1; wrapped = 1;
        }
    }
    return wrapped;
}

/* at each loop wrap: RAND (re-generate) and the Turing Machine (rotate the window) */
static void ztek_seq_wrap(ztek_t *z)
{
    int steps = z->pat.time_length;
    if (steps < 1) steps = 1;
    if (steps > ACID_MAX_STEPS) steps = ACID_MAX_STEPS;

    /* RAND: probability of a full re-generation at each loop wrap */
    if (z->seqk[4] > 0.0001f && ztek_rnd01(z) < z->seqk[4]) {
        z->ap.scale = z->scale;
        acid_generate(&z->pat, &z->ap, ztek_lcg(z));
        acid_render(&z->pat, &z->grid);
        z->play_pos = 0;
        z->play_dir = 1;
        return;
    }

    /* Turing Machine: rotate the window [0, wl) one step to the right. The new head
     * (index 0, the shifted-out tail is not re-injected as-is) is: kept when r < lock,
     * mutated when lock <= r < lock+mut, re-rolled otherwise. lock = 0 -> TM off. */
    float lock = z->seqk[0];
    if (lock > 0.0001f) {
        int wl = (int)(z->seqk[1] * 60.0f + 4.0f);      /* TM LEN 4..64 */
        if (wl < 4) wl = 4;
        if (wl > steps) wl = steps;
        acid_grid_t g = z->grid;
        int8_t tdeg = g.deg[wl-1]; int8_t toct = g.oct[wl-1];
        uint8_t tg = g.gate[wl-1], ta = g.accent[wl-1], ts = g.slide[wl-1], tt = g.tie[wl-1];
        for (int i = wl - 1; i > 0; i--) {
            g.deg[i] = g.deg[i-1]; g.oct[i] = g.oct[i-1];
            g.gate[i] = g.gate[i-1]; g.accent[i] = g.accent[i-1];
            g.slide[i] = g.slide[i-1]; g.tie[i] = g.tie[i-1];
        }
        float r = ztek_rnd01(z);
        g.deg[0] = tdeg; g.oct[0] = toct; g.gate[0] = tg;
        g.accent[0] = ta; g.slide[0] = ts; g.tie[0] = tt;
        if (r >= lock) {
            if (r < lock + z->seqk[2]) {
                uint32_t m = ztek_lcg(z) & 3u;          /* mutate one field of the head */
                if (m == 0) g.deg[0] += (ztek_lcg(z) & 1u) ? 1 : -1;
                else if (m == 1) g.oct[0] += (ztek_lcg(z) & 1u) ? 1 : -1;
                else if (m == 2) g.gate[0] = g.gate[0] ? 0 : 1;
                else g.accent[0] = g.accent[0] ? 0 : 1;
            } else {                                    /* re-rolled head */
                g.gate[0] = (ztek_lcg(z) % 100u) < 70u ? 1 : 0;
                g.deg[0] = (int8_t)((int)(ztek_lcg(z) % 12u) - 6);
                g.oct[0] = (int8_t)((int)(ztek_lcg(z) % 3u) - 1);
                g.accent[0] = (ztek_lcg(z) % 100u) < 30u ? 1 : 0;
                g.slide[0] = 0; g.tie[0] = 0;
            }
        }
        z->grid = g;
        acid_import(&z->pat, &z->grid, steps, z->pat.seed, z->pat.algorithm_version);
        acid_render(&z->pat, &z->grid);
    }
}

/* one 1/64-bar of the acid sequencer: gate the grid's step */
static void ztek_acid_step(ztek_t *z)
{
    int steps = z->pat.time_length;
    int s;
    if (steps < 1) steps = 1;
    if (steps > ACID_MAX_STEPS) steps = ACID_MAX_STEPS;
    if (z->play_pos >= steps) z->play_pos = 0;
    s = z->play_pos;
    if (ztek_seq_advance(z, steps)) z->seq_wrapped = 1;
    z->seq_step = z->play_pos;
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

/* ---- modulation section: everything below runs per 32-sample block --------------- */

static float ztek_wave(int w, float ph)
{
    float p = ph - floorf(ph);
    switch (w) {
    case 1:  return p < 0.5f ? p * 2.0f : 2.0f - p * 2.0f;   /* TRI 0..1..0 */
    case 2:  return p < 0.5f ? 1.0f : 0.0f;                   /* SQ */
    case 3:  return p;                                        /* SAW */
    default: return 0.5f + 0.5f * sinf(p * 6.2831853f);       /* SIN */
    }
}

static void ztek_lfo_block(ztek_t *z, float dt)
{
    static const float DIVS[6] = { 16.f, 8.f, 4.f, 2.f, 1.f, 0.5f };  /* in beats */
    for (int i = 0; i < 4; i++) {
        float rate = z->mod_knob[i][0];
        float hz;
        if (z->lfo_sync) {                            /* LFO SYNC: snap to clock divisions */
            int idx = (int)(rate * 5.99f); if (idx > 5) idx = 5;
            hz = (z->bpm / 60.0f) / DIVS[idx];
        } else {
            hz = 0.02f + rate * rate * 40.0f;         /* 0.02..40 Hz, musical taper */
        }
        float ph = z->lfo_ph[i] + hz * dt;
        int w = (int)(z->mod_knob[i][1] * 5.99f); if (w > 5) w = 5;   /* SIN TRI SQ SAW S&H DRUNK */
        if (ph >= 1.0f) {
            ph -= floorf(ph);
            if (w == 4) z->lfo_sh[i] = ztek_rnd01(z);                 /* S&H: re-sample on wrap */
        }
        z->lfo_ph[i] = ph;
        float v;
        if (w == 4) v = z->lfo_sh[i];
        else if (w == 5) {                                            /* DRUNK: random walk */
            z->lfo_drunk[i] += (ztek_rnd01(z) - 0.5f) * 0.25f;
            if (z->lfo_drunk[i] < 0.f) z->lfo_drunk[i] = 0.f;
            if (z->lfo_drunk[i] > 1.f) z->lfo_drunk[i] = 1.f;
            v = z->lfo_drunk[i];
        } else {
            v = ztek_wave(w, ph);
            if (w == 2) {                                             /* SQ duty */
                float p = ph - floorf(ph);
                v = p < (0.2f + z->mod_knob[i][3] * 0.6f) ? 1.0f : 0.0f;
            }
        }
        float amp = z->mod_knob[i][2] * 2.0f;                         /* DEPTH around 0.5 */
        if (amp > 2.0f) amp = 2.0f;
        v = 0.5f + (v - 0.5f) * amp;
        if (v < 0.f) v = 0.f;
        if (v > 1.f) v = 1.f;
        z->src_val[i] = v;
    }
}

/* loopable ADSR for ENV1/ENV2 (sources 8..9): A -> D -> S(hold) -> R -> loop */
static void ztek_env_block(ztek_t *z, float dt)
{
    for (int e = 0; e < 2; e++) {
        float A = 0.005f + z->mod_knob[8 + e][0] * 0.5f;
        float D = 0.005f + z->mod_knob[8 + e][1] * 0.5f;
        float S = z->mod_knob[8 + e][2];
        float R = 0.005f + z->mod_knob[8 + e][3] * 1.0f;
        if (A < 1e-4f) A = 1e-4f;
        if (D < 1e-4f) D = 1e-4f;
        if (R < 1e-4f) R = 1e-4f;
        int st = z->env_stage[e];
        float lvl = z->env_lvl[e];
        float ph = z->env_ph[e];
        switch (st) {
        case 0: lvl += dt / A; if (lvl >= 1.f) { lvl = 1.f; st = 1; } break;
        case 1: lvl -= dt / D * (1.f - S); if (lvl <= S) { lvl = S; st = 2; } break;
        case 2: ph += dt / R; if (ph >= 1.f) { ph = 0.f; st = 3; } break;   /* sustain hold */
        default: lvl -= dt / R; if (lvl <= 0.f) { lvl = 0.f; if (z->env_loop[e]) st = 0; } break;
        }
        z->env_stage[e] = st;
        z->env_lvl[e] = lvl;
        z->env_ph[e] = ph;
        z->src_val[8 + e] = lvl;
    }
}

/* the 4 labos (sources 4..7): the ZuluFx network profiles, computed per block.
 * knobs: TYPE, SRC1, SRC2, DEPTH. Sources must be lower ids (no cycles). */
static void ztek_lab_block(ztek_t *z, float dt)
{
    (void)dt;
    for (int i = 0; i < 4; i++) {
        int type = z->lab_type[i];
        if (type < 0) type = 0;
        if (type > 7) type = 7;
        int s1 = z->lab_src[i][0], s2 = z->lab_src[i][1];
        float a = (s1 >= 0 && s1 < 4 + i) ? z->src_val[s1] : 0.5f;
        float b = (s2 >= 0 && s2 < 4 + i) ? z->src_val[s2] : 0.5f;
        float depth = z->mod_knob[4 + i][3];
        uint32_t *rng = &z->lab_rng[i];
        *rng = *rng * 1103515245u + 12345u;
        float jit = ((float)(*rng >> 8) / 16777216.0f - 0.5f) * 2.0f;
        int clk = z->lab_clk[i]++;
        float out;
        switch (type) {
        case 1:  out = a + (b - 0.5f) * 0.5f + jit * 0.12f; break;              /* MYCELIUM */
        case 2:  out = a + (b - 0.5f) * 0.3f + jit * 0.04f; break;              /* HUMUS */
        case 3: {                                                              /* NET VINTAGE */
            out = a + jit * 0.45f + (b - 0.5f) * 0.2f;
            float st = 0.5f - 0.5f * 0.42f;                                    /* aliasing quantize */
            out = floorf(out / st + 0.5f) * st;
            break;
        }
        case 4:  out = a + jit * 0.85f + (b - 0.5f) * 1.6f; break;             /* CHAOS */
        case 5: {                                                              /* OISEAUX */
            if ((clk & 7) == 0) {
                z->lab_bird[i][0][z->lab_birdpos[i][0]] = a;
                z->lab_birdpos[i][0] = (z->lab_birdpos[i][0] + 1) & 15;
                z->lab_bird[i][1][z->lab_birdpos[i][1]] = b;
                z->lab_birdpos[i][1] = (z->lab_birdpos[i][1] + 1) & 15;
            }
            float bird = z->lab_bird[i][1][(z->lab_birdpos[i][1] - 8 + 16) & 15];
            out = a + (bird - 0.5f) * 0.9f + jit * 0.1f;
            break;
        }
        case 6: {                                                              /* INSECTES */
            out = a;
            if ((clk % 8) == 0) out += jit * 0.5f;
            if ((clk / 3) & 1) out += (b - 0.5f) * 0.3f;
            break;
        }
        case 7: {                                                              /* MAREE */
            z->lab_phase[i] += 32.0f / (z->sr * 9.0f);
            if (z->lab_phase[i] >= 1.0f) z->lab_phase[i] -= 1.0f;
            out = a + (sinf(z->lab_phase[i] * 6.2831853f) * 0.5f + 0.5f) * 0.4f + (b - 0.5f) * 0.2f;
            break;
        }
        default: out = a + (b - 0.5f); break;                                  /* LINK */
        }
        if (out < 0.f) out = 0.f;
        if (out > 1.f) out = 1.f;
        float amp = 0.3f + depth * 1.7f;
        out = 0.5f + (out - 0.5f) * amp;
        if (out < 0.f) out = 0.f;
        if (out > 1.f) out = 1.f;
        z->src_val[4 + i] = out;
    }
}

/* the 6 tools (sources 10..15): EF LOG SLEW CMP QUANT ATT. knobs: SRC, P1, P2, DEPTH */
static float ztek_tool_src(ztek_t *z, int tool)
{
    int s = (int)(z->mod_knob[tool][0] * 16.999f);      /* 0..15 source, 16 = OFF */
    if (s >= ZT_NSRC) return 0.0f;
    if (s >= tool) s = 0;                                /* no self/forward cycles */
    return z->src_val[s];
}

static void ztek_tool_block(ztek_t *z, float dt)
{
    for (int t = 0; t < 6; t++) {
        int src = 10 + t;
        float in = ztek_tool_src(z, src);
        float p1 = z->mod_knob[src][1], p2 = z->mod_knob[src][2], depth = z->mod_knob[src][3];
        float out = in;
        switch (t) {
        case 0: {   /* EF: envelope follower of the synth bus (blk_peak) */
            float peak = z->blk_peak;
            float k = peak > z->ef_lvl ? (0.01f + p1 * 2.0f) : (0.001f + p2 * 0.5f);
            z->ef_lvl += (peak - z->ef_lvl) * k;
            out = z->ef_lvl * (0.2f + p1 * 4.0f);
            break;
        }
        case 1:     /* LOG: log curve */
            out = logf(1.0f + in * 9.0f) / logf(10.0f);
            out = out * (0.2f + p1 * 2.0f) + p2;
            break;
        case 2: {   /* SLEW: lag, separate up/down rates (per second) */
            float st = z->tool_state[t];
            float rate = (in > st ? p1 : p2);
            float step = (0.0005f + rate * rate * 4.0f) * dt * 60.0f;
            if (in > st) { st += step; if (st > in) st = in; }
            else { st -= step; if (st < in) st = in; }
            z->tool_state[t] = st;
            out = st;
            break;
        }
        case 3: {   /* CMP: comparator with hysteresis */
            float th = p1, hy = 0.02f + p2 * 0.2f;
            float st = z->tool_state[t];
            if (in > th + hy) st = 1.0f;
            else if (in < th - hy) st = 0.0f;
            z->tool_state[t] = st;
            out = st;
            break;
        }
        case 4: {   /* QUANT: snap to a scale of steps */
            int steps = 2 + (int)(p1 * 30.0f);
            out = floorf(in * steps + 0.5f) / steps + p2 * 0.1f;
            break;
        }
        default:    /* ATT: gain + offset */
            out = in * p1 + p2 * 0.5f;
            break;
        }
        out = out * depth + in * (1.0f - depth);           /* DEPTH = wet */
        if (out < 0.f) out = 0.f;
        if (out > 1.f) out = 1.f;
        z->src_val[src] = out;
    }
}

/* the ASSIGN matrix: v = base + (src - 0.5) * 2 * depth, applied to voice/FX/seq params */
static void ztek_matrix_block(ztek_t *z)
{
    for (int d = 0; d < ZT_NDST; d++) {
        int s = z->asg_src[d];
        if (s < 0 || s >= ZT_NSRC) continue;
        float depth = z->asg_depth[d];
        if (depth <= 0.0001f) continue;
        float v = z->dst_base[d] + (z->src_val[s] - 0.5f) * 2.0f * depth;
        if (v < 0.f) v = 0.f;
        if (v > 1.f) v = 1.f;
        if (d < AT_NPARAMS) {
            at_set(&z->voice, d, v);
            bass_set(&z->bass, d, v);
        } else if (d < ZT_SEQ_DST0) {
            fx_set(d - AT_NPARAMS, v);
        } else {
            int k = d - ZT_SEQ_DST0;    /* 0 steps, 1 scale, 2 tempo, 3 root, 4..8 seqk[0..4] */
            if (k == 0) {
                int st = 1 + (int)(v * 63.0f);
                if (st != z->ap.steps) {
                    z->ap.steps = st;
                    acid_sanitize(&z->pat, z->scale);
                    acid_render(&z->pat, &z->grid);
                }
            } else if (k == 1) {
                int sc = (int)(v * 7.99f);
                if (sc != z->scale) ztek_set_scale(z, sc);
            } else if (k == 2) {
                ztek_set_bpm(z, v * 300.0f);
            } else if (k == 3) {
                ztek_set_root(z, 24 + (int)(v * 48.99f));
            } else {
                z->seqk[k - 4] = v;
            }
        }
    }
}

/* one 32-sample block of modulation: sources, then the matrix */
static void ztek_mod_block(ztek_t *z, float dt)
{
    ztek_lfo_block(z, dt);
    ztek_lab_block(z, dt);
    ztek_env_block(z, dt);
    ztek_tool_block(z, dt);
    ztek_matrix_block(z);
    if (z->seq_wrapped) {
        z->seq_wrapped = 0;
        ztek_seq_wrap(z);
    }
}

/* ---- modulation API ---------------------------------------------------------------- */

void ztek_mod_knob(ztek_t *z, int src, int k, float v)
{
    if (src < 0 || src >= ZT_NSRC || k < 0 || k > 3) return;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    z->mod_knob[src][k] = v;
    /* derived fields */
    if (src >= 4 && src < 8) {
        if (k == 0) z->lab_type[src - 4] = (int)(v * 7.99f);
        else if (k == 1) z->lab_src[src - 4][0] = (int)(v * 16.999f) == 16 ? -1 : (int)(v * 15.99f);
        else if (k == 2) z->lab_src[src - 4][1] = (int)(v * 16.999f) == 16 ? -1 : (int)(v * 15.99f);
    }
}

float ztek_mod_knob_get(const ztek_t *z, int src, int k)
{
    if (src < 0 || src >= ZT_NSRC || k < 0 || k > 3) return 0.0f;
    return z->mod_knob[src][k];
}

void ztek_lfo_opt(ztek_t *z, int which, int on)
{
    if (which == 0) z->lfo_sync = on ? 1 : 0;
    else if (which == 1) z->lfo_trig = on ? 1 : 0;
}

void ztek_env_loop(ztek_t *z, int e, int on)
{
    if (e >= 0 && e < 2) z->env_loop[e] = on ? 1 : 0;
}

float ztek_src_val(const ztek_t *z, int src)
{
    if (src < 0 || src >= ZT_NSRC) return 0.0f;
    return z->src_val[src];
}

void ztek_assign(ztek_t *z, int dst, int src, float depth)
{
    if (dst < 0 || dst >= ZT_NDST) return;
    z->asg_src[dst] = src;
    if (depth < 0.0f) depth = 0.0f;
    if (depth > 1.0f) depth = 1.0f;
    z->asg_depth[dst] = depth;
}

int ztek_assign_src(const ztek_t *z, int dst)
{
    if (dst < 0 || dst >= ZT_NDST) return 255;
    return z->asg_src[dst];
}

float ztek_assign_depth(const ztek_t *z, int dst)
{
    if (dst < 0 || dst >= ZT_NDST) return 0.0f;
    return z->asg_depth[dst];
}

/* ---- pre-patched presets (ZuluFx style) ------------------------------------------- */

static const char *const ZT_PRESET_NAMES[ZT_NPRESETS] = {
    "ACID 303", "FILTRE WOBBLE", "PITCH DRIFT", "GATE 303",
    "SLIDE CITY", "LABO CHAOS", "TM MAX", "MAREE"
};

const char *ztek_preset_name(int i)
{
    if (i < 0) i = 0;
    if (i >= ZT_NPRESETS) i = ZT_NPRESETS - 1;
    return ZT_PRESET_NAMES[i];
}

static void ztek_preset_reset(ztek_t *z)
{
    for (int d = 0; d < ZT_NDST; d++) { z->asg_src[d] = 255; z->asg_depth[d] = 0.0f; }
    for (int k = 0; k < 5; k++) { z->seqk[k] = 0.0f; z->dst_base[ZT_SEQ_DST0 + 4 + k] = 0.0f; }
    z->lfo_sync = 0;
    z->lfo_trig = 0;
    for (int e = 0; e < 2; e++) z->env_loop[e] = 1;
    ztek_set_param(z, AT_CUTOFF, 0.7f);
    ztek_set_param(z, AT_RESONANCE, 0.25f);
    ztek_set_param(z, AT_ENVMOD, 0.5f);
    ztek_set_param(z, AT_DECAY, 0.55f);
    ztek_set_param(z, AT_WAVE, 0.0f);
    ztek_set_bpm(z, 120.0f);
}

void ztek_preset(ztek_t *z, int i)
{
    if (i < 0) i = 0;
    if (i >= ZT_NPRESETS) i = ZT_NPRESETS - 1;
    ztek_preset_reset(z);
    switch (i) {
    case 0:   /* ACID 303: sober defaults, TM off */
        break;
    case 1:   /* FILTRE WOBBLE: LFO1 SIN -> CUTOFF */
        ztek_mod_knob(z, 0, 0, 0.55f);   /* rate */
        ztek_mod_knob(z, 0, 1, 0.05f);   /* SIN */
        ztek_mod_knob(z, 0, 2, 0.7f);    /* depth */
        z->lfo_sync = 1;
        ztek_set_param(z, AT_CUTOFF, 0.35f);
        ztek_assign(z, AT_CUTOFF, 0, 0.7f);
        break;
    case 2:   /* PITCH DRIFT: LFO2 DRUNK -> TUNING */
        ztek_mod_knob(z, 1, 0, 0.35f);
        ztek_mod_knob(z, 1, 1, 0.85f);   /* DRUNK */
        ztek_mod_knob(z, 1, 2, 0.5f);
        ztek_set_param(z, AT_TUNING, 0.5f);
        ztek_assign(z, AT_TUNING, 1, 0.35f);
        break;
    case 3:   /* GATE 303: ENV1 loop -> ENVMOD + DECAY */
        ztek_mod_knob(z, 8, 0, 0.15f);   /* A */
        ztek_mod_knob(z, 8, 1, 0.4f);    /* D */
        ztek_mod_knob(z, 8, 2, 0.6f);    /* S */
        ztek_mod_knob(z, 8, 3, 0.5f);    /* R */
        ztek_assign(z, AT_ENVMOD, 8, 0.7f);
        ztek_assign(z, AT_DECAY, 8, 0.4f);
        break;
    case 4:   /* SLIDE CITY: PING-PONG + LFO3 SAW sync -> TEMPO */
        ztek_seq_knob(z, 8, 0.9f);       /* PLAY = PING-PONG */
        ztek_mod_knob(z, 2, 0, 0.5f);
        ztek_mod_knob(z, 2, 1, 0.55f);   /* SAW */
        ztek_mod_knob(z, 2, 2, 0.6f);
        z->lfo_sync = 1;
        ztek_assign(z, ZT_SEQ_DST0 + 2, 2, 0.15f);   /* -> TEMPO */
        break;
    case 5:   /* LABO CHAOS: LABO1 CHAOS(LFO1 SQ, LFO2) -> RESONANCE */
        ztek_mod_knob(z, 0, 1, 0.55f);   /* LFO1 SQ */
        ztek_mod_knob(z, 0, 0, 0.7f);
        ztek_mod_knob(z, 1, 1, 0.05f);   /* LFO2 SIN */
        ztek_mod_knob(z, 1, 0, 0.4f);
        ztek_mod_knob(z, 4, 0, 0.55f);   /* LABO1 type = CHAOS (4) */
        ztek_mod_knob(z, 4, 1, 0.0f);    /* SRC1 = LFO1 */
        ztek_mod_knob(z, 4, 2, 0.07f);   /* SRC2 = LFO2 */
        ztek_mod_knob(z, 4, 3, 0.8f);    /* DEPTH */
        ztek_assign(z, AT_RESONANCE, 4, 0.8f);
        break;
    case 6:   /* TM MAX: Turing Machine wide open */
        ztek_seq_knob(z, 5, 0.8f);       /* TM LOCK */
        ztek_seq_knob(z, 6, 0.2f);       /* TM LEN ~16 */
        ztek_seq_knob(z, 7, 0.4f);       /* MUT */
        ztek_seq_knob(z, 8, 0.9f);       /* PLAY PING-PONG */
        ztek_seq_knob(z, 9, 0.1f);       /* RAND */
        ztek_set_bpm(z, 138.0f);
        break;
    case 7:   /* MAREE: LABO4 MAREE(ENV1) -> REV MIX, ENV2 -> DLY TIME */
        ztek_mod_knob(z, 8, 0, 0.3f);
        ztek_mod_knob(z, 8, 1, 0.3f);
        ztek_mod_knob(z, 8, 2, 0.7f);
        ztek_mod_knob(z, 8, 3, 0.8f);    /* ENV1 slow */
        ztek_mod_knob(z, 9, 0, 0.1f);    /* ENV2 very slow */
        ztek_mod_knob(z, 9, 1, 0.1f);
        ztek_mod_knob(z, 9, 2, 0.5f);
        ztek_mod_knob(z, 9, 3, 0.9f);
        ztek_mod_knob(z, 7, 0, 0.9f);    /* LABO4 type = MAREE (7) */
        ztek_mod_knob(z, 7, 1, 0.53f);   /* SRC1 = ENV1 (8/16) */
        ztek_mod_knob(z, 7, 2, 0.6f);    /* SRC2 = ENV2 (9/16) */
        ztek_mod_knob(z, 7, 3, 0.7f);
        ztek_assign(z, 8 + FX_REV_MIX, 7, 0.6f);
        ztek_assign(z, 8 + FX_DLY_TIME, 9, 0.5f);
        break;
    }
}

/* ---- sequencer advanced API -------------------------------------------------------- */

void ztek_seq_knob(ztek_t *z, int k, float v)
{
    if (k < 5 || k > 9) return;
    if (v < 0.0f) v = 0.0f;
    if (v > 1.0f) v = 1.0f;
    z->seqk[k - 5] = v;
    z->dst_base[ZT_SEQ_DST0 + 4 + (k - 5)] = v;
}

float ztek_seq_knob_get(const ztek_t *z, int k)
{
    if (k < 5 || k > 9) return 0.0f;
    return z->seqk[k - 5];
}

/* live mutation of the current 16-step page (the ATEK303 ENV/LFO buttons) */
void ztek_seq_mutate(ztek_t *z, int layer)
{
    int steps = z->pat.time_length;
    if (steps < 1) steps = 1;
    if (steps > ACID_MAX_STEPS) steps = ACID_MAX_STEPS;
    int page = (z->seq_step / 16) * 16;
    int lo = page, hi = page + 16;
    if (hi > steps) hi = steps;
    if (lo >= hi) { lo = 0; hi = steps; }
    int ops = 1 + (int)(z->seqk[2] * 7.0f);            /* MUT knob: 1..8 operations */
    uint32_t last = 0;
    acid_mutate_burst_range(&z->pat, layer, ztek_lcg(z), ops, z->scale, &last, lo, hi);
    acid_render(&z->pat, &z->grid);
}

void ztek_render(ztek_t *z, float *outL, float *outR, uint32_t n)
{
    uint32_t done = 0;
    if (n == 0) return;
    while (done < n) {
        uint32_t blk = n - done;
        if (blk > ZTEK_MAX_BLOCK) blk = ZTEK_MAX_BLOCK;

        /* 0. modulation sources + matrix + seq wrap, per 32-sample block */
        {
            uint32_t i;
            for (i = 0; i < blk; i += 32) {
                uint32_t nb = blk - i;
                if (nb > 32) nb = 32;
                ztek_mod_block(z, (float)nb / z->sr);
            }
        }

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
        {
            float pk = 0.0f;
            uint32_t i;
            for (i = 0; i < blk; i++) {
                float a = z->mono[i];
                if (a < 0.f) a = -a;
                if (a > pk) pk = a;
            }
            z->blk_peak = pk;
        }

        /* 4. FX chain (mono in, stereo out) + the resample ring */
        fx_process(z->mono, outL + done, outR + done, blk);
        ml_rs_feed(&z->loop, z->mono, blk);
        ml_rs_render(&z->loop, outL + done, outR + done, blk);

        done += blk;
    }

    /* 5. master */
    {
        uint32_t i;
        for (i = 0; i < n; i++) {
            float l = outL[i] * z->master;
            float r = outR[i] * z->master;
            outL[i] = fm_tanhf(l * 1.2f) * 0.85f;
            outR[i] = fm_tanhf(r * 1.2f) * 0.85f;
        }
    }
}
