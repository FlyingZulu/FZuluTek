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
float ztek_loop_get_sp(const ztek_t *z, int slot, int idx);   /* ML_SP_* */
/* resample (reloop) : enregistre le bus monitoré dans un slot en ring audio (µ-law 8 bits) */
void ztek_loop_rs_start(ztek_t *z, int dst, uint16_t monmask, int fmt, int khz);
void ztek_loop_rs_stop(ztek_t *z);
int  ztek_loop_rs_rec(const ztek_t *z);

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

/* ---- SNAP / KIT / undo-redo (docs/ZULUTEK.md) ------------------------------------
 * Un état = moteur + params AT_ + params FX + PoW! + page XY + bpm + masque des slots actifs.
 * 16 snapshots (8 ZU / 8 LU), undo/redo 3 niveaux, 16 kits (kits = état + slots). */
void ztek_snap_store(ztek_t *z, int slot);
void ztek_snap_load(ztek_t *z, int slot);
void ztek_undo(ztek_t *z);
void ztek_redo(ztek_t *z);
void ztek_kit_store(ztek_t *z, int slot);
void ztek_kit_load(ztek_t *z, int slot);

/* ---- page XY (18) : un pad, cibles routables --------------------------------------
 * target 0..6 = params AT_, 7 = AT_ENGINE, 8..25 = params FX (0..17).
 * (les cibles LFO 26..33 et la matrice ASSIGN appartiennent à la branche seq-plus, PR #3)
 * holdMode 0 MOM (revient au repos en sortant) 1 HOLD (reste). */
void ztek_xy_set(ztek_t *z, int on, float x, float y);
void ztek_xy_target(ztek_t *z, int axis, int target);   /* axis 0 = X, 1 = Y */
void ztek_xy_hold(ztek_t *z, int holdMode);

/* ---- sirène (mode SIREN, triple-tap GLO côté FM-1) -------------------------------- */
void ztek_siren(ztek_t *z, int on, float lvl, float t, float fb);

/* ---- page MIX + sous-page baie de patch (façon AUM) --------------------------------
 * MIX : niveaux des 16 slots + aux (send vers PoW!) + gain du bus LOOP. */
void ztek_mix_set_vol(ztek_t *z, int slot, float v);      /* ML_SP_VOL */
void ztek_mix_set_aux(ztek_t *z, int slot, int on);       /* ML_SP_FX : slot -> PoW! */
void ztek_mix_set_gain(ztek_t *z, float g);               /* loop_gain (page MIX) */
float ztek_mix_get_gain(const ztek_t *z);

/* baie de patch : src/dst en bitsets. src: 0 VOIX 1 SIREN 2 SMP/AIN 3 bus LOOP.
 * dst: 0 chaîne FX 1 MASTER (direct) 2 entrée anneau PoW! */
#define ZTEK_PSRC_VOIX 0
#define ZTEK_PSRC_SIREN 1
#define ZTEK_PSRC_SMP 2
#define ZTEK_PSRC_LOOP 3
#define ZTEK_PDST_FX 0
#define ZTEK_PDST_MASTER 1
#define ZTEK_PDST_POW 2
void ztek_patch_set(ztek_t *z, int src, int dst, int on);
int  ztek_patch_get(const ztek_t *z, int src, int dst);
int  ztek_patch_compat(int src, int dst);           /* 0 = connexion impossible (estompée dans la baie) */

#ifdef __cplusplus
}
#endif
