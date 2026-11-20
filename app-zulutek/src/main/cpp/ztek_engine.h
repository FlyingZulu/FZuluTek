/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * ztek_engine: the host-side ZuluTek engine for the Android preview APK. It runs the same
 * vendored DSP as the FM-1 firmware (dsp/, copied as-is from the zuluTek firmware tree):
 * the ATEK303 voice + the bass engines, the FX chain with PoW! punch-ins, the acid pattern
 * generator and the MIDI looper. One mono synth bus -> fx_process -> stereo out, plus the
 * looper's resample ring mixed in. The UI drives it through this small C API (see
 * jni_bridge.cpp); everything audio-thread-safe is a plain state setter, as on the device. */
#pragma once
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#include "dsp/atek_voice.h"     /* AT_NPARAMS: voice destinations 0..7 */
#include "dsp/fx.h"             /* FX_NPARAMS: FX destinations 8..(8+FX_NPARAMS-1) */

typedef struct ztek_s ztek_t;

ztek_t *ztek_create(float sr);
void ztek_destroy(ztek_t *z);

/* the Oboe output stream lives in the engine (C++ side, as in the app module): start/stop it
 * from the UI thread; the audio callback renders blocks through ztek_render */
int  ztek_start(ztek_t *z);      /* 1 ok */
void ztek_stop(ztek_t *z);

/* n stereo frames, separate L/R planes, not cleared: the engine adds into them */
void ztek_render(ztek_t *z, float *outL, float *outR, uint32_t n);

void ztek_note_on(ztek_t *z, int note, int vel, int accent, int slide);
void ztek_note_off(ztek_t *z, int note);
void ztek_all_notes_off(ztek_t *z);

void ztek_set_param(ztek_t *z, int param, float v);   /* the AT_ ids (see dsp/atek_voice.h) */
void ztek_set_engine(ztek_t *z, int engine);           /* 0..4: 303, PLUK, FM, SUB, 8BIT */
int  ztek_get_engine(const ztek_t *z);
void ztek_set_bpm(ztek_t *z, float bpm);
void ztek_set_root(ztek_t *z, int root);               /* MIDI note of the scale root */
void ztek_set_scale(ztek_t *z, int scale);             /* index in ACID_SCALES */

void ztek_fx_set(ztek_t *z, int param, float v);       /* the FX_ ids (see dsp/fx.h) */
void ztek_punch(ztek_t *z, int on, int type, float a, float b, float mix);
void ztek_punch_tail(ztek_t *z, float t);

/* the MIDI looper (dsp/midi_looper.h) */
void ztek_loop_play(ztek_t *z, int on);
void ztek_loop_toggle(ztek_t *z, int slot);
void ztek_loop_rec_start(ztek_t *z, int slot, int od);
void ztek_loop_rec_stop(ztek_t *z, int slot);
void ztek_loop_erase(ztek_t *z, int slot);
void ztek_loop_set_len(ztek_t *z, int slot, int len64);
void ztek_loop_set_sp(ztek_t *z, int slot, int idx, float v);
int  ztek_loop_slot_on(const ztek_t *z, int slot);
int  ztek_loop_slot_rec(const ztek_t *z, int slot);

/* the acid pattern sequencer (dsp/acid_pattern.h) */
void ztek_acid_generate(ztek_t *z, uint32_t seed);
void ztek_acid_play(ztek_t *z, int on);
void ztek_acid_knob(ztek_t *z, int which, float v);    /* 0 density 1 accent 2 slide 3 range 4 tie */

/* ---- sequencer advanced sub-mode (double-tap SEQ on page SEQ) ----------------------
 * 5 extra knobs, also matrix destinations 30..34:
 *   k=5 TM LOCK  0 = pure VCV/ATEK303 mode, >0 = Turing Machine active (lock probability)
 *   k=6 TM LEN   4..64, the TM shift-register window length (in steps)
 *   k=7 MUT      mutation rate (live mutation burst size + TM mutation probability)
 *   k=8 PLAY     0 NORM, 1 REV, 2 PING-PONG (by tiers)
 *   k=9 RAND     probability of a full re-generation at each loop wrap            */
void ztek_seq_knob(ztek_t *z, int k, float v);           /* k = 5..9 */
float ztek_seq_knob_get(const ztek_t *z, int k);
/* live mutation of the current 16-step page, as the ATEK303 ENV/LFO buttons:
 * layer = ACID_MUT_TIME (ENV alone), ACID_MUT_PITCH (LFO alone), ACID_MUT_ARTICULATION (both) */
void ztek_seq_mutate(ztek_t *z, int layer);

/* ---- modulation section (pages LFO=10 and ENV/TOOLS=11) -----------------------------
 * 16 sources (ZT_NSRC), computed per 32-sample block:
 *   0..3  LFO1..4   (page LFO, subs 0..3)  knobs: RATE, WAVE, DEPTH, SHAPE
 *                   WAVE 0..5 = SIN TRI SQ SAW S&H DRUNK
 *   4..7  LABO1..4  (page LFO, subs 4..7)  knobs: TYPE, SRC1, SRC2, DEPTH
 *                   TYPE 0..7 = LINK MYCELIUM HUMUS NET VINTAGE CHAOS OISEAUX INSECTES MAREE
 *   8..9  ENV1/ENV2 (page ENV, subs 0..1)  knobs: A, D, S, R  (loopable ADSR)
 *   10..15 EF LOG SLEW CMP QUANT ATT (page ENV, subs 2..7) knobs: SRC, P1, P2, DEPTH */
#define ZT_NSRC 16
#define ZT_NDST (AT_NPARAMS + FX_NPARAMS + 9)   /* 0..7 voice, 8.. FX, 26..34 seq (steps scale tempo root + k5..k9) */
#define ZT_SEQ_DST0 (AT_NPARAMS + FX_NPARAMS)   /* 26: first seq destination */

void ztek_mod_knob(ztek_t *z, int src, int k, float v); /* src 0..15, k 0..3 */
float ztek_mod_knob_get(const ztek_t *z, int src, int k);
void ztek_lfo_opt(ztek_t *z, int which, int on);        /* 0 = LFO SYNC (clock divisions), 1 = LFO TRIG (retrig on note) */
void ztek_env_loop(ztek_t *z, int e, int on);           /* e 0..1: ENV loopable on/off */
float ztek_src_val(const ztek_t *z, int src);           /* current source value 0..1 (LCD/debug) */

/* the ASSIGN matrix: each destination routes one source with a depth (bipolar around base) */
void ztek_assign(ztek_t *z, int dst, int src, float depth);  /* src 255 = off */
int   ztek_assign_src(const ztek_t *z, int dst);
float ztek_assign_depth(const ztek_t *z, int dst);

/* pre-patched synth presets (ZuluFx style: LFO/modulation pre-assigned), for the FM-1 too */
#define ZT_NPRESETS 8
void ztek_preset(ztek_t *z, int i);              /* 0..ZT_NPRESETS-1, wraps */
const char *ztek_preset_name(int i);

#ifdef __cplusplus
}
#endif
