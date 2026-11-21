/*
 * Copyright (C) 2026 FlyingZulu
 * See midi_looper.h. Ported from the ZuluFx app's MIDI loops (fm1-Zulu, GPL-3.0) and
 * inspired by the Kaoss Pad 3 loop recorder: loop length in beats, quantized at stop,
 * overdub, tap pad = play/stop. Adapted to the FM-1 (16 slots = the 16 white keys, one
 * shared clock synced to PLAY/bpm, events timed in 1/64 of a bar).
 *
 * Per-slot edit params (sp): pitch (K1), start position (K2), play mode normal/reverse/
 * ping-pong (K3), mutation (K4), record source bass/synth/drums/all/resample (ALGO),
 * include destination FX (key25+ALGO), level (PRESET). Length via SELECT (0..128 beats =
 * 0..2048 units): shrink truncates, extend clones the original loop. Resample mode
 * records the monitored slots into an audio ring (auto-sized to the free RAM).
 * Fixed-size state, no libm, no allocation in the audio path. */
#pragma once
#include <stdint.h>

#define ML_NSLOTS 16
#define ML_MAXEV 96          /* note events per slot */
#define ML_MAXPEV 48         /* param events per slot */
#define ML_BAR64 64          /* one 4/4 bar = 64 sixty-fourths */
#define ML_MAXLEN 2048       /* longest loop: 32 bars, in 1/64 bar (1 beat = 16) */
#define ML_RS_MAX 16384      /* resample ring max, samples (auto-sized down on low RAM) */

/* slot param indices for ml_set_sp / ml_get_sp */
enum { ML_SP_PITCH, ML_SP_POS, ML_SP_MODE, ML_SP_MUT, ML_SP_SRC, ML_SP_FX, ML_SP_VOL, ML_SP_N };
/* sp.src: 0 bass, 1 synth, 2 drums, 3 all, 4 resample, 5 midi in, 6 all+midi in (REC+GLO+OCTAVE) ;
 * sp.mode: 0 normal, 1 reverse, 2 ping-pong */

typedef struct { uint16_t t; uint8_t note, vel, dur; } ml_note_t;   /* t, dur in 1/64 bar */
typedef struct { uint16_t t; uint8_t key; float v; } ml_par_t;       /* param automation */

typedef struct {
    int8_t pitch;        /* K1: -12..+12 semitones */
    uint16_t pos;        /* K2: start offset, 1/64 bar */
    uint8_t mode, mut, src, fx, vol;   /* K3 mode, K4 mutation 0..255, ALGO src, key25 fx, PRESET vol 0..255 */
} ml_sp_t;

typedef struct {
    ml_note_t note[ML_MAXEV];
    ml_par_t par[ML_MAXPEV];
    uint16_t n_note, n_par;
    uint16_t len;            /* loop length in 1/64 bar; len_set=0 -> quantized at rec stop */
    uint8_t len_set;
    uint8_t on;              /* playing */
    uint8_t rec;             /* recording (events) */
    uint8_t rs;              /* resampled audio loop */
    uint8_t od;              /* overdub: keep the existing events, append */
    uint16_t od_base;        /* event count at overdub start */
    float rec_clk;           /* clock at rec start */
    float pos;               /* playback position, loop coords */
    int8_t dir;              /* ping-pong direction */
    float last_pos;
    uint8_t n_act;
    struct { uint8_t note; uint16_t off; } act[8];   /* sounding notes (for note_off) */
    ml_sp_t sp;
} ml_slot_t;

typedef struct {
    float sr, bpm;
    float loop_gain;             /* MIX page: the loop return level */
    float randp;                 /* RAND global (pack SEQ+) : proba de re-générer un slot à son wrap */
    ml_slot_t slot[ML_NSLOTS];
    int sel;                 /* the selected slot (SELECT) */
    int playing;
    float clk;               /* the master position, in 1/64 bar */
    uint32_t rng;
    /* choke (style Koala) par slot, hors ml_sp_t (la baie garde ses 7 params/slot) :
     * groupe 0 = off, 1..6 = groupe. Le premier slot assigné à un groupe en est le master ;
     * en mode leader, seul le master arrête les autres membres quand il démarre,
     * sinon (leader off) tout membre arrête les autres (Koala classique). */
    uint8_t  choke[ML_NSLOTS];
    uint32_t choke_order[ML_NSLOTS];
    uint32_t choke_tick;
    int8_t   lead[7];        /* leader explicite par groupe (touche POLY), -1 = auto (1er assigné) */
    uint8_t  leader;         /* 1 = mode hybride (leader), 0 = choke mutuel classique */
    uint8_t  eco;            /* MODE ECO : resample en µ-law + paliers lo-fi, ring 32 kHz, capacité /2 */
    uint8_t  oneshot;        /* one-shot (liaison APK/FM-1) : un slot qui wrap s'arrête (notes relevées) au lieu de boucler */
    /* resample (reloop): record the monitored slots' output into an audio ring */
    uint16_t monmask;        /* bit i = slot i is monitored during a resample rec */
    int rs_rec, rs_dst;      /* resampling now, into slot rs_dst */
    int rs_fmt;              /* ring format: 0 = int16, 1 = µ-law 8-bit (SP-1200-style companding: half the RAM),
                                2 = µ-law + tiered lo-fi bits by duration (0-7 s light, 7-14 s medium, 14+ s strong) */
    int rs_bits;             /* render quantization (16 = off), set by the fmt-2 tiers at stop */
    union { int16_t w[ML_RS_MAX]; uint8_t b[ML_RS_MAX * 2]; } rs_ring;
    float rs_decim;          /* ring decimation: ring rate = sr / rs_decim (1 = native; ~1.38 for a 32 kHz ring) */
    float rs_ph, rs_rph;     /* decimation phase (record), render phase */
    uint32_t rs_cap, rs_len, rs_pos;   /* ring capacity (auto-sized), loop length (ring samples), play/write pos */
    /* event callbacks, fired from ml_render (audio thread) */
    void (*note_on)(void *ctx, int slot, int note, int vel);
    void (*note_off)(void *ctx, int slot, int note);
    void (*param)(void *ctx, int slot, int key, float v);
    void *ctx;
} ml_t;

void ml_init(ml_t *m, float sr);
void ml_set_bpm(ml_t *m, float bpm);
void ml_play(ml_t *m, int on);
void ml_set_cb(ml_t *m, void *ctx,
               void (*note_on)(void *, int, int, int), void (*note_off)(void *, int, int),
               void (*param)(void *, int, int, float));

void ml_toggle(ml_t *m, int slot);          /* play/stop a slot */
void ml_set_choke(ml_t *m, int slot, int grp);   /* grp 0..6 ; 1er assigné = master */
int  ml_get_choke(const ml_t *m, int slot);
int  ml_choke_master(const ml_t *m, int grp);    /* slot master du groupe, -1 si aucun */
void ml_set_leader(ml_t *m, int on);             /* 1 hybride (leader), 0 mutuel (Koala) */
int  ml_get_leader(const ml_t *m);
void ml_set_leader_slot(ml_t *m, int slot, int on);  /* POLY : on=1 désigne leader, on=0 le désassigne */
void ml_clear_leader(ml_t *m, int grp);              /* POLY sans pad : mode leader désactivé pour le groupe */
void ml_set_eco(ml_t *m, int on);                    /* MODE ECO : bitrate du ring resample au plus bas */
int  ml_get_eco(const ml_t *m);
void ml_set_oneshot(ml_t *m, int on);                 /* one-shot : fin au wrap, notes/coupes relevées */
int  ml_get_oneshot(const ml_t *m);
void ml_set_len(ml_t *m, int slot, int len64);  /* 0 = auto at rec stop, else 1..2048 */
void ml_resize(ml_t *m, int slot, int newlen);  /* shrink: truncate; extend: clone the original loop */
void ml_set_sp(ml_t *m, int slot, int idx, float v);   /* ML_SP_* */
float ml_get_sp(ml_t *m, int slot, int idx);
void ml_rec_start(ml_t *m, int slot, int od);  /* arm+start: od=0 clears the slot, od=1 overdubs (keeps events) */
void ml_step_toggle(ml_t *m, int slot, int k, int note, int vel);  /* deep edit: toggle a note on step k of 16 */
void ml_step_vel(ml_t *m, int slot, int k, int vel);              /* deep edit: velocity of step k */
void ml_rec_stop(ml_t *m, int slot);         /* stop: predefined len kept, else quantized to the tempo */
void ml_erase(ml_t *m, int slot);
void ml_note_in(ml_t *m, int note, int vel, int dur64, int cls);  /* cls: 0 bass, 1 synth, 2 drums */
void ml_note_off_in(ml_t *m, int note);
void ml_par_in(ml_t *m, int key, float v, int cls);

void ml_rs_start(ml_t *m, int dst, uint16_t monmask, uint32_t cap, int fmt, int khz);  /* resample rec armed; fmt 0 int16 / 1 µ-law, khz 0 = native rate (32 = 32 kHz ring) */
void ml_rs_feed(ml_t *m, const float *in, uint32_t n);               /* engine feeds the monitored bus */
void ml_rs_stop(ml_t *m);                                            /* quantize, slot becomes an audio loop */
void ml_rs_render(ml_t *m, float *outL, float *outR, uint32_t n);    /* mix the resample loop into L/R */

void ml_render(ml_t *m, uint32_t n);         /* advance the clock, fire the loops' events */

/* pack SEQ+ — mêmes fonctions ENV / LFO / SEQ que le seq acid et le drum :
 * mutation live d'un slot (layer 1=TIME 2=PITCH 3=ARTIC/vel, comme EV_MUTATE),
 * RAND au wrap (randp 0..1), mode de lecture 3=RANDOM (voir ML_SP_MODE). */
void ml_set_randp(ml_t *m, float p);
void ml_mutate(ml_t *m, int slot, int layer);
