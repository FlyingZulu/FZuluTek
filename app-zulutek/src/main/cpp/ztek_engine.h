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

#include "dsp/atek_voice.h"
#include "dsp/fx.h"

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
/* pack SEQ+ sur les MIDI loops (mêmes fonctions ENV/LFO/SEQ que le seq acid et le drum) :
 * mutation live d'un slot de loop, RAND au wrap, mode RANDOM via ML_SP_MODE=3 */
void ztek_loop_mutate(ztek_t *z, int slot, int layer);   /* layer 1=TIME 2=PITCH 3=ARTIC */
void ztek_loop_randp(ztek_t *z, float p);                /* RAND global des loops 0..1 */

/* ---- arpégiateur (bouton ARP, page 13) ----------------------------------------------
 * Tant que ARP est on, les notes jouées au clavier alimentent une liste de notes tenues
 * (elles sont quand même enregistrées dans le looper) et l'arp les rejoue en boucle :
 *   k=0 MODE : 0 UP, 1 DOWN, 2 UP-DOWN (ping-pong), 3 RANDOM, 4 AS-PLAYED (ordre de jeu)
 *   k=1 OCT  : 1..4 octaves
 *   k=2 DIV  : 0=1/4 1=1/8 2=1/16 3=1/32 4=1/8 triolet 5=1/16 triolet
 *   k=3 GATE : 0.05..1.0 de la durée du pas */
void ztek_arp_set_on(ztek_t *z, int on);
int  ztek_arp_get_on(const ztek_t *z);
void ztek_arp_knob(ztek_t *z, int k, float v);           /* k = 0..3 */
float ztek_arp_knob_get(const ztek_t *z, int k);
int  ztek_arp_held(const ztek_t *z);                     /* nb de notes tenues (LCD) */

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

/* ---- page MACRO (16 slots assignables, SPEC Notion « SPEC MACRO zuluTek ») --------------
 * Un macro = conteneur : cible (dst) + sous-cible + niveau 0..1. Cibles :
 *   0..ZT_NDST-1          = mêmes destinations que la matrice ASSIGN (voix 0..7, FX 8..25, seq 26..34)
 *   ZT_NDST..ZT_NDST_ALL-1 = les 16 mix des macros eux-mêmes (la matrice peut piloter un macro)
 *   ZT_MDST_AUX0 + s      = aux du slot loop s -> PoW! (v >= 0.5 = on)
 *   ZT_MDST_PATCH + c     = connexion c (row*3+col) de la baie de patch (v >= 0.5 = on)
 *   ZT_MDST_POWT          = type PoW! actif (v -> 0..PN_NTYPES-1, utilisé au prochain POW) */
#define ZT_NMACRO 16
#define ZT_NDST_ALL (ZT_NDST + ZT_NMACRO)       /* 51 destinations au total */
#define ZT_MDST_AUX0  100
#define ZT_MDST_PATCH 120
#define ZT_MDST_POWT  140
void ztek_macro_set(ztek_t *z, int slot, int dst, int sub, float val);  /* contenu du slot */
void ztek_macro_apply(ztek_t *z, int slot);       /* applique : cible <- val */
void ztek_macro_level(ztek_t *z, int slot, float v);  /* niveau seul, réappliqué */
void ztek_macro_erase(ztek_t *z, int slot);
int  ztek_macro_used(const ztek_t *z, int slot);
int  ztek_macro_dst(const ztek_t *z, int slot);
int  ztek_macro_sub(const ztek_t *z, int slot);
float ztek_macro_val(const ztek_t *z, int slot);
int  ztek_punch_type_get(const ztek_t *z);         /* type PoW! courant (macro POWT) */

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

/* preset bank per part: part 0 = BASSE, 1 = LEAD, 2 = DRUMS; 16 slots each. The bank is
 * FREE: the first ZT_NPRESETS slots hold the factory pre-patched presets, but any slot can
 * be overwritten by a user save. ztek_bank_load() picks the right one (user save wins over
 * the factory preset). A preset is a POD snapshot of the whole modulable state; export/
 * import give a binary blob so the APK can persist it (SharedPreferences) and the FM-1
 * its flash. */
#define ZT_NPARTS 3
#define ZT_PSLOTS 16
typedef struct {
    uint32_t magic;                  /* 'ZTP1' when used */
    int32_t part, slot;
    char name[16];
    int engine;
    float param[AT_NPARAMS];
    float fx[FX_NPARAMS];
    float bpm;
    int root, scale;
    float mod_knob[ZT_NSRC][4];
    int lab_type[4];
    int lab_src[4][2];
    int lfo_sync, lfo_trig;
    int env_loop[2];
    int asg_src[ZT_NDST_ALL];
    float asg_depth[ZT_NDST_ALL];
    float seqk[5];
    uint8_t mac_used[ZT_NMACRO];
    int16_t mac_dst[ZT_NMACRO];
    int16_t mac_sub[ZT_NMACRO];
    float mac_val[ZT_NMACRO];
} ztek_preset_t;

void ztek_preset_save(ztek_t *z, int part, int slot);
void ztek_preset_load(ztek_t *z, int part, int slot);      /* no-op if empty */
void ztek_bank_load(ztek_t *z, int part, int slot);        /* user save if any, else factory preset if slot < ZT_NPRESETS */
int  ztek_preset_used(const ztek_t *z, int part, int slot);
void ztek_preset_clear(ztek_t *z, int part, int slot);
void ztek_preset_set_name(ztek_t *z, int part, int slot, const char *name);
const char *ztek_preset_slot_name(const ztek_t *z, int part, int slot);
int  ztek_preset_export(const ztek_t *z, int part, int slot, uint8_t *buf, int cap);  /* bytes, 0 if empty */
int  ztek_preset_import(ztek_t *z, int part, int slot, const uint8_t *buf, int len); /* 1 ok */

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
