/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * ztek_engine: the host-side ZuluTek engine for the Android preview APK. It runs the same
 * vendored DSP as the FM-1 firmware (dsp/, copied as-is from the zuluTek firmware tree):
 * the ATEK303 voice + the bass engines, the FX chain with PoW! punch-ins, the acid pattern
 * generator and the Loop (looper). One mono synth bus -> fx_process -> stereo out, plus the
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
float ztek_fx_get(const ztek_t *z, int param);         /* readback (UI mixeur) */
void ztek_set_eco(ztek_t *z, int on);                  /* OPTIONS > ECO : runtime DSP éco (fx/drum/loop) */
int  ztek_get_eco(const ztek_t *z);
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
void ztek_siren_wave(ztek_t *z, int w);              /* 0 CLASSIQUE 1 CHOP 2 POLICE 3 LASER */
int  ztek_siren_wave_get(const ztek_t *z);
void ztek_siren_mix(ztek_t *z, float m);             /* 0 = pre-FX, 1 = post-FX (master) */
float ztek_siren_mix_get(const ztek_t *z);

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
const char *ztek_preset_name_part(int part, int i);   /* factory name per part (0 bass, 1 lead, 2 drums) */
void ztek_preset_factory(ztek_t *z, int part, int i);  /* factory pre-patched preset of a part: generic
                                                        * preset + part engine/sound + 4 macro slots filled */

/* ---- table de mixage principale : 4 tranches BASSE/LEAD/DRUMS/LOOP -------------------- */
enum { ZTEK_STRIP_BASSE = 0, ZTEK_STRIP_LEAD, ZTEK_STRIP_DRUMS, ZTEK_STRIP_LOOP, ZTEK_NSTRIP };
enum { ZTEK_MIX_CUT, ZTEK_MIX_FMODE, ZTEK_MIX_VOL, ZTEK_MIX_PAN, ZTEK_MIX_MUT, ZTEK_MIX_SOLO,
       ZTEK_MIX_LDR, ZTEK_MIX_AUX_POW, ZTEK_MIX_AUX_LOOP,
       ZTEK_MIX_AUX1, ZTEK_MIX_AUX2, ZTEK_MIX_AUX3, ZTEK_MIX_AUX4, ZTEK_MIX_N };
enum { ZTEK_MIXM_GAIN, ZTEK_MIXM_AUX1, ZTEK_MIXM_AUX2, ZTEK_MIXM_N };   /* master : bus + 2 aux libres */

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
    float strip_par[ZTEK_NSTRIP][ZTEK_MIX_N];   /* table de mixage 4 tranches (base manuelle) */
    float master_par[ZTEK_MIXM_N];
    int   siren_wave;
    float siren_mix;
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

/* ---- table de mixage principale : 4 tranches BASSE/LEAD/DRUMS/LOOP --------------------
 * Par tranche : filtre traversant en tête (pré-fader : 0 LP, 1 neutre, 2 HP + cutoff),
 * puis PAN (pré-fader), VOL, MUT/SOLO, départs auxiliaires post-fader
 * (P->PoW!, L->Loop, AUX1..4 libres ; la tranche LOOP n'a pas de départ Loop).
 * LDR = choke général de la tranche : BASSE ON = une note de basse coupe le lead (at_panic),
 * LEAD ON = une note de lead coupe la basse (bass_panic), DRUMS/LOOP ON = mode leader/hybride
 * des chokes de séquenceurs (défaut ON), OFF = Koala mutuel. Default OFF sur BASSE/LEAD.
 * BASSE et LEAD partagent le bus voix physique (engine 0 = LEAD, 1..4 = BASSE). */
void  ztek_mix_set_strip(ztek_t *z, int strip, int param, float v);
float ztek_mix_get_strip(const ztek_t *z, int strip, int param);
void  ztek_mix_set_master(ztek_t *z, int param, float v);
float ztek_mix_get_master(const ztek_t *z, int param);
void ztek_mix_set_gain(ztek_t *z, float g);               /* loop_gain (page MIX) */
float ztek_mix_get_gain(const ztek_t *z);
float ztek_master_peak(const ztek_t *z);                  /* VU master (page MIX), 0..~1 */
int   ztek_loop_slot_act(const ztek_t *z, int slot);       /* notes actives du slot (VU tranche) */

/* ---- baie de patch v3 : sources x ports de destination, groupés par module ----------
 * Sources (22) : 0 VOIX 1 SIREN 2 SMP/AIN 3 bus LOOP 4 axe X 5 axe Y, puis les 16 sources de
 * modulation de la branche seq-plus (PR #3, même ordre que ZtekAudio.SRC_NAMES) :
 * 6..9 LFO1..4, 10..13 LAB1..4, 14 ENV1 15 ENV2 16 EF 17 LOG 18 SLEW 19 CMP 20 QUANT 21 ATT.
 * Source 22 = DRUMS (boîte à rythmes, dsp/drum_engine.c vendored de la PR #5), audio comme 0..3.
 * Destinations : 158 ports aplatis, groupés par module. Les ports 0..34 sont identiques à la
 * matrice ASSIGN de PR #3 (ZT_NDST=35) pour fusionner les deux baies au merge :
 *   module 0 VOIX  : 8   params AT_ 0..7                          offset 0
 *   module 1 FX    : 18  params FX_ 0..17                         offset 8
 *   module 2 SEQ   : 9   dst seq 26..34 (mêmes cibles que PR #3)   offset 26
 *   module 3 SIREN : 3   (lvl, t, fb)                             offset 35
 *   module 4 SMP   : 5   (start,end,vol,pitch,mode)               offset 38
 *   module 5 LOOP  : 112 = 16 slots x 7 params ML_SP_*            offset 43
 *   module 6 OUT   : 5   (155 entrée chaîne FX, 156 MASTER, 157 anneau PoW!,
 *                          158 AUX1, 159 AUX2 = départs auxiliaires LIBRES du master,
 *                          vides par défaut et assignables dans la baie)          offset 155
 *   module 7 AUX   : 8   = 2 slots aux libres x 4 tranches du main mix (BASSE/LEAD/DRUMS/LOOP),
 *                          vides par défaut, assignables                          offset 160
 *   module 8 MIX   : 55  = 13 params x 4 tranches (CUT,FMODE,VOL,PAN,MUT,SOLO,LDR,
 *                          AUX_POW,AUX_LOOP,AUX1..4) + 3 master (GAIN,AUX1,AUX2)     offset 168
 * Compatibilité (protection façon VCV Rack/miRack) : sources audio (0..3, 22 DRUMS) -> OUT/AUX ;
 * sources CV (X/Y + modulation, 4..21) -> modules paramètres 0..5 et MIX (8) seulement. */
#define ZTEK_NSRC 23
#define ZTEK_PSRC_DRUMS 22
#define ZTEK_NMOD 9
#define ZTEK_NDST 223
#define ZTEK_AWORDS 4                       /* bitset par source : 4 x uint64 = 256 bits */
#define ZTEK_MOD_OUT 6
#define ZTEK_MOD_AUX 7
#define ZTEK_MOD_MIX 8
#define ZTEK_MIX_NSTRIP_PORTS (ZTEK_MIX_N * ZTEK_NSTRIP)          /* 52 */
#define ZTEK_MIX_NPORT (ZTEK_MIX_NSTRIP_PORTS + ZTEK_MIXM_N)      /* 55 */
extern const int ZTEK_MOD_OFF[ZTEK_NMOD + 1];       /* offsets des modules dans les ports */
extern const char *const ZTEK_MOD_NAME[ZTEK_NMOD];
extern const int ZTEK_MOD_NPORT[ZTEK_NMOD];
extern const char *const ZTEK_SRC_NAME[ZTEK_NSRC];

int  ztek_mod_of(int dst);                           /* port -> module */
int  ztek_patch_compat(int src, int dst);           /* 0 = impossible (estompé/refusé) */
void ztek_assign_set(ztek_t *z, int src, int dst, int on);
int  ztek_assign_get(const ztek_t *z, int src, int dst);
void ztek_assign_words(const ztek_t *z, int src, uint64_t out[ZTEK_AWORDS]);  /* bitset brut */

/* compatibilité avec l'API v1 (routage audio) : dst 0=FX 1=MASTER 2=POW! -> ports OUT 0..2 */
#define ZTEK_PSRC_VOIX 0
#define ZTEK_PSRC_SIREN 1
#define ZTEK_PSRC_SMP 2
#define ZTEK_PSRC_LOOP 3

/* ---- boîte à rythmes (source DRUMS = 22) : vendored de la PR #5 (app-zulutek-drums) ---------- */
void ztek_drum_play(ztek_t *z, int on);
void ztek_drum_hit(ztek_t *z, int pad);                 /* DR_* 0..7 */
void ztek_drum_pat(ztek_t *z, int pad, int step, int on);/* step 0..15 */
int  ztek_drum_get_pat(const ztek_t *z, int pad, int step);
void ztek_drum_clear(ztek_t *z, int pad);
void ztek_drum_rand(ztek_t *z, int pad, float density, uint32_t seed);
void ztek_drum_swing(ztek_t *z, float s);               /* 0..0.5 */
void ztek_drum_level(ztek_t *z, int pad, float lvl);
void ztek_drum_master(ztek_t *z, float m);
int  ztek_drum_step(const ztek_t *z);
/* choke des séquenceurs (style Koala, mode leader/hybride ou normal) */
void ztek_drum_set_choke(ztek_t *z, int pad, int grp);   /* grp 0..6 ; 1er assigné = master */
int  ztek_drum_get_choke(const ztek_t *z, int pad);
int  ztek_drum_choke_master(const ztek_t *z, int grp);   /* -1 si aucun */
void ztek_drum_set_leader(ztek_t *z, int on);            /* 1 = leader (hybride), 0 = normal (Koala) */
int  ztek_drum_get_leader(const ztek_t *z);
void ztek_drum_set_leader_pad(ztek_t *z, int pad, int on); /* POLY (noire 10) : on=1 leader, on=0 désassigne */
void ztek_drum_clear_leader(ztek_t *z, int grp);         /* POLY sans pad : mode leader off pour le groupe */
void ztek_loop_set_choke(ztek_t *z, int slot, int grp);
int  ztek_loop_get_choke(const ztek_t *z, int slot);
int  ztek_loop_choke_master(const ztek_t *z, int grp);
void ztek_loop_set_leader(ztek_t *z, int on);
int  ztek_loop_get_leader(const ztek_t *z);
void ztek_loop_set_leader_slot(ztek_t *z, int slot, int on); /* POLY : on=1 leader, on=0 désassigne */
void ztek_loop_clear_leader(ztek_t *z, int grp);         /* POLY sans pad : mode leader off */
#define ZTEK_PDST_FX 0
#define ZTEK_PDST_MASTER 1
#define ZTEK_PDST_POW 2
void ztek_patch_set(ztek_t *z, int src, int dst, int on);
int  ztek_patch_get(const ztek_t *z, int src, int dst);

/* ---- pedalboard FX (menu FX, REC+FX) : 4 chaînes ordonnables x 3 slots = 12 ----------
 * Chaque slot choisit un module dans le catalogue FXT_*. Modules légers = DSP propre
 * par slot (clonables) ; modules lourds (FXT_REV_PLATE, FXT_BBD, FXT_TAPE) = délégués à
 * la chaîne fx.c globale, exemplaire UNIQUE (un seul slot lourd actif à la fois).
 * dry/wet par slot (mix) + dry/wet par chaîne (chain_mix, knob SEL sur la pédale active).
 * Ordre des chaînes = permutation chain_order[]. Blocs = presets du board (8 slots). */
#define ZTEK_NCHAIN 4
#define ZTEK_SPC 3                          /* slots par chaîne ("agglomérer par trois") */
#define ZTEK_NSLOT (ZTEK_NCHAIN * ZTEK_SPC) /* 12 */
enum {
    FXT_OFF = 0, FXT_DRIVE, FXT_CHORUS, FXT_FLANGER, FXT_VIBRATO, FXT_SVF, FXT_WAH,
    FXT_SLAP, FXT_ECHO, FXT_DOTTED, FXT_BBD, FXT_TAPE, FXT_GATE, FXT_OCT,
    FXT_AUTOPAN, FXT_RINGMOD, FXT_TREM, FXT_REV_PLATE, FXT_REV_SPRING, FXT_ROOM,
    FXT_NCAT
};
#define ZTEK_NBLOCK 8
extern const char *const ZTEK_FXT_NAME[FXT_NCAT];
void  ztek_fxslot_set(ztek_t *z, int slot, int type, float p0, float p1, float p2, float p3, float mix);
int   ztek_fxslot_type(const ztek_t *z, int slot);
float ztek_fxslot_par(const ztek_t *z, int slot, int k);   /* k 0..3 */
float ztek_fxslot_mix(const ztek_t *z, int slot);
void  ztek_fxchain_order(ztek_t *z, const int order[ZTEK_NCHAIN]);  /* permutation 0..3 */
void  ztek_fxchain_order_get(const ztek_t *z, int out[ZTEK_NCHAIN]);
void  ztek_fxchain_mix(ztek_t *z, int chain, float m);             /* dry/wet pédale (SEL) */
float ztek_fxchain_mix_get(const ztek_t *z, int chain);
void  ztek_fxblock_save(ztek_t *z, int blk);
void  ztek_fxblock_load(ztek_t *z, int blk);
int   ztek_fxblock_used(const ztek_t *z, int blk);

#ifdef __cplusplus
}
#endif
