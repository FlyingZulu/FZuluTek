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

/* the SMP page (audio-in sampler, docs/ZULUTEK.md page 17): 4 s buffer @ sr, recorded from
 * the input stream; played one-shot between START and END. which: 0 START 1 END 2 VOL
 * 3 PITCH 4 MODE (0 OFF 1 BEAT snapped to the tempo 2 CLEAN resample 3 OLD = x2 decimation) */
#define ZTEK_SMP_SECONDS 4
void ztek_smp_rec(ztek_t *z, int on);
void ztek_smp_play(ztek_t *z, int on);
void ztek_smp_set(ztek_t *z, int which, float v);
int  ztek_smp_len(const ztek_t *z);                    /* recorded frames */
void ztek_smp_peaks(const ztek_t *z, float *out, int nbuckets);   /* min/max per bucket for the LCD */

/* circuit bend, post-master (docs/ZULUTEK.md "Lot D") : on + position du doigt x/y (densité/niveau),
 * mode 0 GLITCH (sample-hold + bit-crush + trémolo) 1 LOW BAT (décimation + soft-clip) */
void ztek_bend(ztek_t *z, int on, float x, float y, int mode);

#ifdef __cplusplus
}
#endif
