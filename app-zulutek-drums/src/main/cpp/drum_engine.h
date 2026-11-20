/* drum_engine.h — drum machine « analogique inspirée Elektron » + moteurs shapables
 * (kick/snare style Mutable Instruments Braids/Elements, GPL-3.0, crédits Émilie Gillet),
 * piochant dans les générateurs ZuluFx (audio_engine.cpp cases 5/6/7 : kick glide+click+drive,
 * snare bruit+corps, hat 6 carrés inharmoniques), avec un séquenceur inspiré du séquenceur
 * de la section MIDI loop (midi_looper.c) : horloge en 1/64 de mesure, événements
 * step = {on, vélocité, condition de trig façon Elektron, slide micro-timing 1/64},
 * modes normal/reverse/ping-pong, mutation, REC live (overdub).
 *
 * zuluTek — Flying Zulu. Dérivé zuluTek/ATEK303 (GPL-3.0, crédits Animatek) + ZuluFx (GPL-3.0)
 * + concepts Mutable Instruments Braids/Elements (GPL-3.0, crédits Émilie Gillet) — pas de
 * copie de binaire proprio. */
#ifndef DRUM_ENGINE_H
#define DRUM_ENGINE_H

#include <stdint.h>
#include "bass_engines.h"   /* moteurs basse/lead réutilisés (PLUK/FM/SUB/8BIT) pour TOM/PERC/FM */

#ifdef __cplusplus
extern "C" {
#endif

#define DR_NPADS 8
#define DR_NSTEPS 16
#define DR_BAR64 64            /* 1 mesure 4/4 = 64 soixante-quatrièmes ; 1 step = 4 unités */

enum { DR_KICK = 0, DR_SNARE, DR_HAT, DR_CLAP, DR_TOM, DR_PERC, DR_COWBELL, DR_FM };
extern const char *DR_PAD_NAMES[DR_NPADS];
extern const char *DR_ELIXIR_NAMES[5];   /* sous-sons du pad ELIXIR (P2 = TYPE) */
/* 12 knobs de params par voie (3 pages de 4, façon PĒRKONS HD-01 qui a 8 commandes + sends) :
 * page 1 : P0..P3 = moteur custom (P0 = PITCH partout), page 2 : P4 = HYB, P5/P6 = moteur
 * basse/lead (B.WAVE/B.RES), P7 = LEVEL, page 3 : P8 = PAN, P9 = SEND DELAY, P10 = SEND REVERB,
 * P11 = SEND COMP (contribution de la voie au détecteur du compresseur, pour du ducking ciblé). */
#define DR_NPAR 12
extern const char *DR_PARAM_NAMES[DR_NPADS][DR_NPAR];

/* FX bus partagés, économes (delay ring + reverb 2 allpass façon Schroeder court) */
#ifdef DR_FM1_ECO
#define DR_DLY_MAX 8192     /* 0,19 s à 44,1 kHz — mode éco FM-1 (RAM) */
#else
#define DR_DLY_MAX 16384    /* 0,34 s à 48 kHz */
#endif
#define DR_REV_D1 211       /* allpass 1, ~4,8 ms à 44,1 kHz */
#define DR_REV_D2 331       /* allpass 2, ~7,5 ms */

typedef struct {
    float dly[DR_DLY_MAX];
    uint32_t dly_pos;
    float dly_time, dly_fb, dly_wet;   /* 0..1, 0..0.95, 0..1 */
    float rev_a[DR_REV_D1], rev_b[DR_REV_D2];
    uint32_t rev_pa, rev_pb;
    float rev_wet;                     /* 0..1 */
} dr_fx_t;

/* conditions de trig façon Elektron (COND) */
enum { DR_COND_ALWAYS = 0, DR_COND_1_2, DR_COND_1_3, DR_COND_1_4, DR_COND_1_8, DR_COND_FILL, DR_COND_N };
extern const char *DR_COND_NAMES[DR_COND_N];

/* modes de lecture par pad (comme ML_SP_MODE du séquenceur MIDI loop) */
enum { DR_MODE_NORM = 0, DR_MODE_REV, DR_MODE_PINGPONG, DR_MODE_N };

/* kits façon ZuluFx (sélecteur ZULU / 808 / 909) : réinitialisent les 4 params par défaut
 * des 8 pads — 808 = kick grave à long decay + drop profond, snare sec peu de bruit ;
 * 909 = kick plus haut/aigu avec click+drive, snare dense, hat métal. */
enum { DR_KIT_ZULU = 0, DR_KIT_808, DR_KIT_909, DR_KIT_N };
extern const char *DR_KIT_NAMES[DR_KIT_N];

typedef struct {
    uint8_t on;      /* step actif */
    uint8_t vel;     /* vélocité 0..127 */
    uint8_t cond;    /* DR_COND_* : condition de déclenchement */
    int8_t  slide;   /* micro-timing -8..+8 en 1/64 de mesure (comme le slide du MIDI loop) */
} dr_step_t;

typedef struct {
    float t;         /* secondes depuis le trigger, < 0 = inactif */
    float f0, f1;    /* pitch env : fréquence départ / arrivée */
    float amp;
    float dec;       /* decay amplitude (s) */
    float pdec;      /* decay du pitch env (s) */
    int   kind;
    float phase, phase2;
    unsigned nr;
    float hp;
    float lp;        /* état one-pole LP (CLAP : filtre BP pitché par P0) */
    float par[DR_NPAR]; /* snapshot des 8 knobs du pad au moment du hit (p-lock façon Elektron/PĒRKONS) */
} dr_voice_t;

typedef struct {
    dr_step_t st[DR_NSTEPS];
    uint8_t mode;    /* DR_MODE_* */
    uint8_t mut;     /* mutation : proba 0..255 de re-générer le step au passage */
    float   par[DR_NPAR];  /* 8 knobs 0..1 : P0..P3 custom, P4 HYB, P5/P6 bass engine, P7 LEVEL */
} dr_pad_t;

/* compresseur de bus master, inspiré AUV3 "Fac Smash" (et de la section compression de la
 * PĒRKONS HD-01) : follower de crête linéaire (cheap, pas de powf -> ok FM-1), soft-knee,
 * ratio 1..20 (20 = "smash"/limiter), makeup gain. */
typedef struct {
    int   on;
    float thr;      /* seuil 0..1 */
    float ratio;    /* 1..20 */
    float makeup;   /* 0..2 */
    float env;      /* état du follower */
    float gr;       /* gain reduction courant 0..1 */
} dr_comp_t;

typedef struct {
    float ph, rate;      /* rate en Hz */
    int wave;            /* DR_LFO_WAVES */
    float depth;         /* 0..1 */
    int dst;             /* DR_LFO_DST_* */
    float sh, drunk;     /* états S&H / DRUNK */
    unsigned nr;
} dr_lfo_t;

typedef struct {
    float sr;
    float bpm;
    float swing;     /* 0..0.5 fraction de step sur les steps impairs */
    float master;
    dr_pad_t pad[DR_NPADS];
    int   step;      /* step courant 0..15 */
    float clk64;     /* horloge master en 1/64 de mesure (comme ml_t.clk) */
    float ph;        /* reste d'horloge (secondes) */
    int   playing;
    int   rec;       /* REC live : les hits sont enregistrés à la position courante */
    int   play_pos;  /* position de lecture 0..15 (avec mode rev/ping-pong) */
    int   play_dir;
    unsigned rng;
    dr_voice_t v[DR_NPADS];
    bass_t bass[DR_NPADS];   /* pads délégués aux moteurs basse/lead (TOM/PERC/FM) */
    int trig_count[DR_NPADS]; /* compteur de triggers (debug/tests/UI) */
    dr_comp_t comp;          /* compresseur de bus (AUV3 Fac Smash-like) */
    dr_fx_t fx;              /* delay + reverb partagés (sends par voie en P9/P10) */
    int   gmut;              /* MUTATION GLOBALE 0..255 : re-roll des steps actifs à chaque mesure (façon ZuluFx) */
    float glitch;            /* GLITCH/CHAOS GLOBAL 0..1 : sample-hold + bit-crush sur le bus (labo CHAOS ZuluFx) */
    float sh;                /* état sample-hold */
    int   shn;               /* échantillons restants de sample-hold */
    /* ---- fonctions ENV / LFO / SEQ du pack SEQ+ (mêmes sémantiques que les synthés/basse) ---- */
    int   seq_mode;          /* 0 NORM, 1 REV, 2 PING-PONG, 3 RANDOM — lecture globale (bouton SEQ) */
    int   seq_dir;           /* direction ping-pong */
    int   gpos;              /* position de lecture globale 0..15 */
    float randp;             /* RAND : proba de re-générer les patterns à chaque wrap de mesure */
    dr_lfo_t lfo[2];         /* 2 LFO globaux (matrice façon ASSIGN : dst + depth) */
    float mod_pitch, mod_dec, mod_pan, mod_dsend, mod_rsend, mod_glitch, mod_thr; /* valeurs LFO courantes */
    int   lfo_blk;           /* compteur d'échantillons pour le calcul LFO par blocs de 32 */
} drum_t;

/* destinations LFO (matrice façon ASSIGN du pack SEQ+) */
enum { DR_LFO_DST_PITCH = 0, DR_LFO_DST_DECAY, DR_LFO_DST_PAN, DR_LFO_DST_DSEND,
       DR_LFO_DST_RSEND, DR_LFO_DST_GLITCH, DR_LFO_DST_COMP, DR_LFO_DST_N };
extern const char *DR_LFO_DST_NAMES[DR_LFO_DST_N];
/* ondes LFO : 0 SIN 1 TRI 2 SQ 3 SAW 4 S&H 5 DRUNK (comme le pack SEQ+) */
#define DR_LFO_WAVES 6
extern const char *DR_LFO_WAVE_NAMES[DR_LFO_WAVES];

void drum_init(drum_t *d, float sr);
void drum_render(drum_t *d, float *outL, float *outR, int n);  /* stéréo (pan par voie en P8) */
void drum_set_delay(drum_t *d, float time, float fb, float wet);  /* delay bus : 0..1, 0..0.95, 0..1 */
void drum_set_rev(drum_t *d, float wet);                          /* reverb bus : 0..1 */
void drum_set_gmut(drum_t *d, int mut);                           /* mutation globale 0..255 */
void drum_set_glitch(drum_t *d, float g);                         /* glitch/chaos global 0..1 */
/* ENV / LFO / SEQ (pack SEQ+) */
void drum_mutate(drum_t *d, int layer);                           /* live : 1=TIME 2=PITCH 3=ARTIC (ENV/LFO/ENV+LFO) */
void drum_seq_mode(drum_t *d, int mode);                          /* 0 NORM 1 REV 2 PING-PONG 3 RANDOM */
void drum_set_randp(drum_t *d, float p);                          /* RAND au wrap 0..1 */
void drum_set_lfo(drum_t *d, int i, float rate, int wave, float depth, int dst);  /* i 0..1 */
float drum_lfo_val(const drum_t *d, int i);

void drum_hit(drum_t *d, int pad);                 /* hit manuel (REC ajoute l'événement si rec=1) */
void drum_set_step(drum_t *d, int pad, int step, int on, int vel, int cond, int slide);
void drum_get_step(const drum_t *d, int pad, int step, int *on, int *vel, int *cond, int *slide);
void drum_clear_pat(drum_t *d, int pad);
void drum_rand_pat(drum_t *d, int pad, float density, unsigned seed);
void drum_set_param(drum_t *d, int pad, int k, float v);   /* k 0..7 (8 knobs/voie), shapable */
float drum_get_param(const drum_t *d, int pad, int k);
void drum_set_kit(drum_t *d, int kit);                     /* DR_KIT_* : params par défaut des pads */
void drum_set_hyb(drum_t *d, int pad, float h);            /* hybridation custom <-> bass engine */
float drum_get_hyb(const drum_t *d, int pad);
void drum_set_comp(drum_t *d, int on, float thr, float ratio, float makeup);
void drum_get_comp(const drum_t *d, int *on, float *thr, float *ratio, float *makeup);
void drum_set_pad_mode(drum_t *d, int pad, int mode);
void drum_set_pad_mut(drum_t *d, int pad, int mut);
void drum_set_level(drum_t *d, int pad, float lvl);
void drum_set_bpm(drum_t *d, float bpm);
void drum_set_swing(drum_t *d, float s);
void drum_set_master(drum_t *d, float m);
void drum_play(drum_t *d, int on);
void drum_set_rec(drum_t *d, int on);
int  drum_get_cur_step(const drum_t *d);
float drum_get_clk64(const drum_t *d);
void drum_reset(drum_t *d);

#ifdef __cplusplus
}
#endif

#endif /* DRUM_ENGINE_H */
