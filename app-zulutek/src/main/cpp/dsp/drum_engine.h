/* drum_engine.h — mini drum machine « analogique » 8 pads + séquenceur 16 steps (C99).
 * Autonome (pas de dépendance au moteur ztek) : voix synthétiques style 808/909
 * (kick à pitch env, snare bruit+tonalité, hats HP, clap 3 bursts, tom, conga, cowbell).
 * Calcul par blocs (appelé depuis le callback Oboe), mono.
 * zuluTek — Flying Zulu, dérivé zuluTek/ATEK303 (GPL-3.0, crédits Animatek). */
#ifndef DRUM_ENGINE_H
#define DRUM_ENGINE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define DR_NPADS 8
#define DR_NSTEPS 16
#define DR_NCHOKE 7                     /* groupes de choke : 0 = off, 1..6 (style Koala) */

enum { DR_KICK = 0, DR_SNARE, DR_HATC, DR_HATO, DR_CLAP, DR_TOM, DR_CONGA, DR_COWBELL };
extern const char *DR_PAD_NAMES[DR_NPADS];

typedef struct {
    float t;        /* secondes depuis le trigger, < 0 = voix inactive */
    float f0, f1;   /* fréquence de départ / d'arrivée (pitch env) */
    float amp;      /* niveau au trigger */
    float dec;      /* decay (s) de l'enveloppe d'amplitude */
    int   kind;     /* DR_* */
    float phase;    /* phase de l'oscillateur (cycles) */
    unsigned nr;    /* état du LCG de bruit */
    float hp;       /* mémoire du filtre HP (x - x_prev) */
} dr_voice_t;

typedef struct {
    float sr;
    float bpm;
    float swing;    /* 0..0.5 : décalage des steps impairs (en fraction de step) */
    float master;   /* gain global 0..1 */
    int   pat[DR_NPADS][DR_NSTEPS];
    float lvl[DR_NPADS];
    int   step;     /* step courant 0..15 */
    float ph;       /* horloge interne (secondes depuis le dernier step) */
    dr_voice_t v[DR_NPADS];
    /* choke (style Koala) : groupe 0 = off, 1..6 = groupe. Le premier pad assigné à un
     * groupe en est le master (le plus ancien choke_order) ; en mode leader, seul le master
     * coupe les autres membres du groupe, sinon (leader off) tout membre coupe les autres. */
    uint8_t  choke[DR_NPADS];
    uint32_t choke_order[DR_NPADS];
    uint32_t choke_tick;
    int8_t   lead[DR_NCHOKE];   /* leader explicite par groupe (touche POLY), -1 = auto (1er assigné) */
    uint8_t  leader;    /* 1 = mode hybride (leader), 0 = choke mutuel classique */
    uint8_t  eco;       /* MODE ECO : polyphonie 8 -> 5 (la plus vieille voix est coupée) */
} drum_t;

void drum_init(drum_t *d, float sr);                 /* patterns 4-on-the-floor par défaut */
void drum_render(drum_t *d, float *out, int n);      /* mono, sommé dans out */
void drum_hit(drum_t *d, int pad);                   /* déclenche un pad (mute si inactif) */
void drum_set_pat(drum_t *d, int pad, int step, int on);
int  drum_get_pat(const drum_t *d, int pad, int step);
void drum_clear_pat(drum_t *d, int pad);
void drum_rand_pat(drum_t *d, int pad, float density, unsigned seed);
void drum_set_bpm(drum_t *d, float bpm);
void drum_set_swing(drum_t *d, float s);
void drum_set_level(drum_t *d, int pad, float lvl);
void drum_set_master(drum_t *d, float m);
int  drum_get_step(const drum_t *d);
void drum_reset(drum_t *d);                          /* remet le seq au step 0 */

void drum_set_choke(drum_t *d, int pad, int grp);    /* grp 0..6 ; 1er assigné = master */
int  drum_get_choke(const drum_t *d, int pad);
int  drum_choke_master(const drum_t *d, int grp);    /* pad master du groupe, -1 si aucun */
void drum_set_leader(drum_t *d, int on);             /* 1 hybride (leader), 0 mutuel (Koala) */
int  drum_get_leader(const drum_t *d);
void drum_set_leader_pad(drum_t *d, int pad, int on); /* POLY : on=1 désigne leader du groupe, on=0 le désassigne */
void drum_clear_leader(drum_t *d, int grp);           /* POLY sans pad : mode leader désactivé pour le groupe */
void drum_set_eco(drum_t *d, int on);                 /* MODE ECO : polyphonie max 5 */
int  drum_get_eco(const drum_t *d);

#ifdef __cplusplus
}
#endif

#endif /* DRUM_ENGINE_H */
