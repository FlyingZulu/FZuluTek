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

#define ZTEK_MAX_BLOCK 4096

struct ztek_s {
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
};

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
    ztek_t *z = new (std::nothrow) ztek_t();
    if (!z) return NULL;
    memset(z, 0, sizeof *z);
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
    if (!z->mono) { delete z; return NULL; }
    return z;
}

void ztek_destroy(ztek_t *z)
{
    if (!z) return;
    free(z->mono);
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
    bass_set(&z->bass, AT_ENGINE, (float)engine / (float)(AT_NENGINES - 1));
    /* re-apply the shared knobs so the new engine starts from the same page */
    for (int p = 0; p < AT_NPARAMS; p++) bass_set(&z->bass, p, z->param[p]);
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

void ztek_fx_set(ztek_t *z, int param, float v) { (void)z; fx_set(param, v); }

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
