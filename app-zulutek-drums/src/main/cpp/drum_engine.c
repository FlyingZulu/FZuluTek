/* drum_engine.c — voir drum_engine.h (spec complète dans docs/ZULUTEK-DRUMS.md et la page
 * Notion « SPEC Drum Engine zuluTek »). C99, math.h uniquement, pas d'allocation au rendu.
 *
 * Voix : kick/snare style Mutable Instruments Braids/Elements (GPL-3.0, crédits Émilie Gillet)
 * + pioches ZuluFx audio_engine.cpp cases 5/6/7. Séquenceur inspiré de midi_looper.c :
 * horloge 1/64 de mesure, steps {on,vel,cond,slide}, modes NORM/REV/PING-PONG, COND façon
 * Elektron (1:2,1:3,1:4,1:8,FILL), swing, mutation, REC live. */
#include "drum_engine.h"
#include <math.h>
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

const char *DR_PAD_NAMES[DR_NPADS] = {
    "KICK", "SNARE", "HAT", "CLAP", "TOM", "ELIXIR", "COWBELL", "FM"
};
const char *DR_ELIXIR_NAMES[5] = { "BELL", "CHIME", "KALIMBA", "GLASS", "PING" };
const char *DR_LFO_DST_NAMES[DR_LFO_DST_N] = { "PITCH", "DECAY", "PAN", "D.SEND", "R.SEND", "GLITCH", "COMP" };
const char *DR_LFO_WAVE_NAMES[DR_LFO_WAVES] = { "SIN", "TRI", "SQ", "SAW", "S&H", "DRUNK" };

/* Règle FlyingZulu : le knob P0 de TOUTES les voies = PITCH (hauteur), comme 808/909/PĒRKONS.
 * 12 knobs = 3 pages de 4 : page3 = PAN / SEND DELAY / SEND REVERB / SEND COMP. */
const char *DR_PARAM_NAMES[DR_NPADS][DR_NPAR] = {
    {"PITCH", "DECAY", "DROP", "DRIVE", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"},
    {"PITCH", "DECAY", "NOISE", "DRIVE", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"},
    {"PITCH", "DECAY", "METAL", "-", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"},
    {"PITCH", "DECAY", "BURST", "SPC", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"},
    {"PITCH", "DECAY", "DROP", "-", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"},
    {"PITCH", "DECAY", "TYPE", "NOISE", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"}, /* ELIXIR */
    {"PITCH", "DECAY", "-", "-", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"},
    {"PITCH", "DECAY", "RATIO", "INDEX", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"},
};

const char *DR_COND_NAMES[DR_COND_N] = { "1:1", "1:2", "1:3", "1:4", "1:8", "FILL" };
const char *DR_KIT_NAMES[DR_KIT_N] = { "ZULU", "808", "909" };

/* params par défaut des 8 pads x 8 knobs, par kit (ZULU / 808 / 909).
 * Colonnes : P0..P3 custom, P4 HYB, P5 B.WAVE, P6 B.RES, P7 LEVEL. */
static const float KITPAR[DR_KIT_N][DR_NPADS][DR_NPAR] = {
    { /* ZULU */
        {0.45f, 0.55f, 0.60f, 0.25f, 0.25f, 0.50f, 0.50f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.35f, 0.45f, 0.50f, 0.20f, 0.25f, 0.50f, 0.50f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.50f, 0.10f, 0.40f, 0.00f, 0.25f, 0.50f, 0.50f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.00f, 0.50f, 0.60f, 0.50f, 0.25f, 0.50f, 0.50f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.40f, 0.50f, 0.50f, 0.00f, 0.25f, 0.50f, 0.50f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.30f, 0.40f, 0.30f, 0.10f, 0.25f, 0.50f, 0.50f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.20f, 0.50f, 0.00f, 0.00f, 0.25f, 0.50f, 0.50f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.50f, 0.50f, 0.40f, 0.20f, 0.25f, 0.50f, 0.50f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
    },
    { /* TR-808 : kick grave, long decay, drop profond ; snare sec peu bruyant */
        {0.25f, 0.80f, 0.90f, 0.10f, 0.15f, 0.40f, 0.30f, 0.95f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.40f, 0.30f, 0.25f, 0.00f, 0.10f, 0.30f, 0.20f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.60f, 0.06f, 0.30f, 0.00f, 0.10f, 0.50f, 0.40f, 0.85f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.00f, 0.70f, 0.40f, 0.30f, 0.10f, 0.40f, 0.30f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.20f, 0.80f, 0.80f, 0.00f, 0.20f, 0.50f, 0.40f, 0.95f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.15f, 0.60f, 0.50f, 0.05f, 0.20f, 0.50f, 0.40f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.10f, 0.70f, 0.00f, 0.00f, 0.10f, 0.50f, 0.50f, 0.85f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.30f, 0.60f, 0.20f, 0.10f, 0.15f, 0.40f, 0.30f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
    },
    { /* TR-909 : kick plus aigu, click+drive ; snare dense ; hat métal */
        {0.58f, 0.45f, 0.50f, 0.45f, 0.30f, 0.60f, 0.60f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.45f, 0.25f, 0.60f, 0.25f, 0.25f, 0.60f, 0.70f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.70f, 0.08f, 0.75f, 0.00f, 0.20f, 0.70f, 0.70f, 0.85f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.00f, 0.40f, 0.70f, 0.60f, 0.20f, 0.60f, 0.60f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.55f, 0.40f, 0.40f, 0.00f, 0.30f, 0.50f, 0.50f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.50f, 0.35f, 0.40f, 0.20f, 0.30f, 0.50f, 0.60f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.40f, 0.45f, 0.00f, 0.00f, 0.20f, 0.70f, 0.70f, 0.85f, 0.50f, 0.00f, 0.00f, 0.00f},
        {0.70f, 0.45f, 0.50f, 0.30f, 0.30f, 0.60f, 0.50f, 0.90f, 0.50f, 0.00f, 0.00f, 0.00f},
    },
};

static unsigned lcg(unsigned *s)
{
    *s = *s * 1664525u + 1013904223u;
    return *s;
}

/* Hybridation (façon Elektron) : CHAQUE pad somme deux moteurs —
 *   - le moteur custom (kick/snare Mutable, hat ZuluFx 6-carrés, clap, tom sinus, perc modal,
 *     cowbell 2 carrés, FM drum) ;
 *   - un moteur basse/lead existant (bass_engines.c) choisi par pad, pour "graisser" le son :
 *     KICK->SUB (grave), SNARE/HAT/CLAP/COWBELL->8BIT (bruit/pulse lo-fi), TOM/PERC->PLUK
 *     (Karplus-Strong = corde pincée), FM->FM.
 * Le param hyb (0..1) fait le crossfade entre les deux. */
static const int BASS_ENG[DR_NPADS] = { 3, 4, 4, 4, 1, 1, 4, 2 };  /* SUB 8BIT 8BIT 8BIT PLUK PLUK 8BIT FM */
static const int BASS_NOTE0[DR_NPADS] = { 24, 45, 70, 50, 40, 60, 80, 36 }; /* note de base (midi) */
static const int BASS_NOTESPAN[DR_NPADS] = { 24, 12, 12, 12, 24, 24, 8, 24 };

static float frand(unsigned *s) { return ((lcg(s) >> 9) & 0xFFFF) / 65536.0f; }

void drum_init(drum_t *d, float sr)
{
    memset(d, 0, sizeof(*d));
    d->sr = sr;
    d->bpm = 120.0f;
    d->swing = 0.0f;
    d->master = 0.8f;
    d->step = -1;      /* le prochain seq_advance joue le step 0 (sinon il est sauté au départ) */
    d->clk64 = 0.0f;
    d->ph = 0.0f;
    d->playing = 1;
    d->play_pos = 0;
    d->play_dir = 1;
    d->rng = 0x2026u;
    /* compresseur de bus, défaut "glue" doux (section compression PĒRKONS-like) */
    d->comp.on = 1;
    d->comp.thr = 0.65f;
    d->comp.ratio = 4.0f;
    d->comp.makeup = 1.3f;
    d->comp.env = 0.0f;
    d->comp.gr = 0.0f;
    /* FX bus : delay + reverb courte, defaults doux */
    d->fx.dly_pos = 0;
    d->fx.dly_time = 0.30f; d->fx.dly_fb = 0.30f; d->fx.dly_wet = 0.15f;
    d->fx.rev_pa = 0; d->fx.rev_pb = 0;
    d->fx.rev_wet = 0.12f;
    d->gmut = 0;
    d->glitch = 0.0f;
    d->sh = 0.0f;
    d->shn = 0;
    /* ENV / LFO / SEQ (pack SEQ+) */
    d->seq_mode = 0; d->seq_dir = 1; d->gpos = -1;
    d->randp = 0.0f;
    for (int i = 0; i < 2; i++) {
        d->lfo[i].ph = 0.0f; d->lfo[i].rate = 2.0f; d->lfo[i].wave = 0;
        d->lfo[i].depth = 0.0f; d->lfo[i].dst = DR_LFO_DST_PITCH;
        d->lfo[i].sh = 0.0f; d->lfo[i].drunk = 0.0f; d->lfo[i].nr = 0xABCDu + i;
    }
    d->mod_pitch = 1.0f; d->mod_dec = 1.0f; d->mod_pan = 0.0f;
    d->mod_dsend = 1.0f; d->mod_rsend = 1.0f; d->mod_glitch = 0.0f; d->mod_thr = 0.0f;
    d->lfo_blk = 0;
    for (int p = 0; p < DR_NPADS; p++) {
        d->pad[p].mode = DR_MODE_NORM;
        memcpy(d->pad[p].par, KITPAR[DR_KIT_ZULU][p], sizeof(d->pad[p].par));
        d->v[p].t = -1.0f;
        bass_init(&d->bass[p], sr);
    }
    /* patterns par défaut (comme ml_ : 4-on-the-floor, backbeat, 8th hats) */
    for (int s = 0; s < DR_NSTEPS; s += 4) { d->pad[DR_KICK].st[s].on = 1; d->pad[DR_KICK].st[s].vel = 110; d->pad[DR_KICK].st[s].cond = DR_COND_ALWAYS; }
    d->pad[DR_SNARE].st[4].on = d->pad[DR_SNARE].st[12].on = 1;
    d->pad[DR_SNARE].st[4].vel = d->pad[DR_SNARE].st[12].vel = 100;
    for (int s = 0; s < DR_NSTEPS; s += 2) { d->pad[DR_HAT].st[s].on = 1; d->pad[DR_HAT].st[s].vel = 70; }
    d->pad[DR_HAT].st[14].vel = 90;
    d->pad[DR_CLAP].st[12].on = 1; d->pad[DR_CLAP].st[12].vel = 90;
    d->pad[DR_PERC].st[6].on = d->pad[DR_PERC].st[14].on = 1;
    d->pad[DR_PERC].st[6].vel = d->pad[DR_PERC].st[14].vel = 80;
}

void drum_reset(drum_t *d)
{
    d->step = -1; d->gpos = -1; d->clk64 = 0.0f; d->ph = 0.0f;
    d->play_pos = 0; d->play_dir = 1; d->seq_dir = 1;
    for (int p = 0; p < DR_NPADS; p++) d->v[p].t = -1.0f;
}

/* ---- LFO (par bloc de 32 échantillons, comme ztek_mod_block) ---- */
static float lfo_sample(dr_lfo_t *l, float sr, int blk)
{
    l->ph += l->rate * (float)blk / sr;
    float ph = l->ph - floorf(l->ph);
    switch (l->wave) {
    case 1: return ph < 0.5f ? ph * 4.0f - 1.0f : 3.0f - 4.0f * ph;   /* TRI */
    case 2: return ph < 0.5f ? 1.0f : -1.0f;                          /* SQ */
    case 3: return ph * 2.0f - 1.0f;                                  /* SAW */
    case 4: if (ph < 0.02f) l->sh = frand(&l->nr) * 2.0f - 1.0f; return l->sh;  /* S&H */
    case 5: l->drunk += (frand(&l->nr) * 2.0f - 1.0f) * 0.3f;         /* DRUNK */
            l->drunk *= 0.98f; return l->drunk * 2.0f;
    default: return sinf(2.0f * (float)M_PI * ph);                   /* SIN */
    }
}

static void lfo_block(drum_t *d)
{
    /* recalcule les mod_* courants à partir des valeurs de base + LFO (bloc de 32) */
    d->mod_pitch = 1.0f; d->mod_dec = 1.0f; d->mod_pan = 0.0f;
    d->mod_dsend = 1.0f; d->mod_rsend = 1.0f; d->mod_glitch = 0.0f; d->mod_thr = 0.0f;
    for (int i = 0; i < 2; i++) {
        dr_lfo_t *l = &d->lfo[i];
        if (l->depth <= 0.0f) continue;
        float v = lfo_sample(l, d->sr, 32);           /* -1..1 */
        float a = v * l->depth;
        switch (l->dst) {
        case DR_LFO_DST_PITCH:  d->mod_pitch += a; break;                 /* 1 + a (bipolaire) */
        case DR_LFO_DST_DECAY:  d->mod_dec += a; break;
        case DR_LFO_DST_PAN:    d->mod_pan += a * 0.5f; break;
        case DR_LFO_DST_DSEND:  d->mod_dsend += a; break;
        case DR_LFO_DST_RSEND:  d->mod_rsend += a; break;
        case DR_LFO_DST_GLITCH: d->mod_glitch += a * 0.5f; break;
        case DR_LFO_DST_COMP:   d->mod_thr += a * 0.3f; break;
        }
    }
    if (d->mod_pitch < 0.25f) d->mod_pitch = 0.25f;
    if (d->mod_pitch > 4.0f) d->mod_pitch = 4.0f;
    if (d->mod_dec < 0.1f) d->mod_dec = 0.1f;
    if (d->mod_dec > 4.0f) d->mod_dec = 4.0f;
}

/* ---- mutation live ENV / LFO / ENV+LFO (comme EV_MUTATE du pack SEQ+) ---- */
void drum_mutate(drum_t *d, int layer)
{
    for (int p = 0; p < DR_NPADS; p++) {
        for (int s = 0; s < DR_NSTEPS; s++) {
            dr_step_t *e = &d->pad[p].st[s];
            if (!e->on) continue;
            switch (layer) {
            case 1:  /* TIME (ENV seul) : slide + vélocité */
                if (frand(&d->rng) < 0.5f) e->slide = (int)(frand(&d->rng) * 17.0f) - 8;
                if (frand(&d->rng) < 0.5f) e->vel = 30 + (int)(frand(&d->rng) * 97.0f);
                break;
            case 2:  /* PITCH (LFO seul) : hauteur P0 de la voie */
                if (frand(&d->rng) < 0.5f) {
                    float np = d->pad[p].par[0] + (frand(&d->rng) - 0.5f) * 0.5f;
                    d->pad[p].par[0] = np < 0.0f ? 0.0f : np > 1.0f ? 1.0f : np;
                }
                break;
            default: /* ARTICULATION (ENV+LFO) : conditions de trig */
                if (frand(&d->rng) < 0.5f) e->cond = (int)(frand(&d->rng) * DR_COND_N);
                break;
            }
        }
    }
}

void drum_seq_mode(drum_t *d, int mode)
{
    if (mode < 0 || mode > 3) return;
    d->seq_mode = mode;
    if (mode != 2) d->seq_dir = 1;
}

void drum_set_randp(drum_t *d, float p)
{
    d->randp = p < 0.0f ? 0.0f : p > 1.0f ? 1.0f : p;
}

void drum_set_lfo(drum_t *d, int i, float rate, int wave, float depth, int dst)
{
    if (i < 0 || i > 1) return;
    dr_lfo_t *l = &d->lfo[i];
    l->rate = rate < 0.01f ? 0.01f : rate > 40.0f ? 40.0f : rate;
    l->wave = wave < 0 ? 0 : wave >= DR_LFO_WAVES ? DR_LFO_WAVES - 1 : wave;
    l->depth = depth < 0.0f ? 0.0f : depth > 1.0f ? 1.0f : depth;
    l->dst = dst < 0 ? 0 : dst >= DR_LFO_DST_N ? DR_LFO_DST_N - 1 : dst;
}

float drum_lfo_val(const drum_t *d, int i)
{
    if (i < 0 || i > 1) return 0.0f;
    return lfo_sample((dr_lfo_t *)&d->lfo[i], d->sr, 1) * 0.5f + 0.5f;
}

/* ---- déclenchement d'une voix avec snapshot des 4 params (p-lock façon Elektron) ---- */
static void trigger(drum_t *d, int pad, int vel)
{
    if (pad < 0 || pad >= DR_NPADS) return;
    dr_voice_t *v = &d->v[pad];
    const float *par = d->pad[pad].par;
    v->t = 0.0f;
    v->kind = pad;
    v->amp = par[7] * (vel > 0 ? vel / 127.0f : 1.0f);   /* P7 = LEVEL */
    v->phase = 0.0f; v->phase2 = 0.0f;
    v->hp = 0.0f;
    v->lp = 0.0f;
    v->nr = 0x1234u + (unsigned)pad * 7919u + (unsigned)(d->clk64 * 16);
    memcpy(v->par, par, sizeof(v->par));
    switch (pad) {
    case DR_KICK:
        v->f0 = 40.0f + 160.0f * par[0];            /* TUNE 40..200 Hz */
        v->f1 = v->f0;                              /* arrivée = base ; le drop est multiplicatif */
        v->dec = 0.05f + 0.75f * par[1];            /* DECAY */
        v->pdec = 0.02f + 0.08f * par[2];           /* DROP : vitesse du pitch env */
        break;
    case DR_SNARE:
        v->f0 = v->f1 = 150.0f + 250.0f * par[0];    /* TONE corps */
        v->dec = 0.05f + 0.16f * par[1];
        v->pdec = 0.05f;
        break;
    case DR_HAT:
        v->f0 = v->f1 = 1.0f;                       /* hatF[] * genF2 */
        v->dec = 0.02f + 0.38f * par[1];
        v->pdec = 0.05f;
        break;
    case DR_CLAP:
        v->f0 = v->f1 = 1.0f;
        v->dec = 0.08f + 0.30f * par[1];
        v->pdec = 0.05f;
        break;
    case DR_TOM:
        v->f0 = 80.0f + 220.0f * par[0];
        v->f1 = v->f0;
        v->dec = 0.10f + 0.50f * par[1];
        v->pdec = 0.02f + 0.08f * par[2];
        break;
    case DR_PERC:
        v->f0 = 200.0f + 800.0f * par[0];
        v->f1 = v->f0;
        v->dec = 0.05f + 0.30f * par[1];
        v->pdec = 0.05f;
        break;
    case DR_COWBELL: {
        float mul = powf(2.0f, (par[0] - 0.5f) * 2.0f);   /* P0 = PITCH */
        v->f0 = 845.0f * mul;
        v->f1 = 560.0f * mul;
        v->dec = 0.10f + 0.50f * par[1];
        v->pdec = 0.05f;
        break;
    }
    case DR_FM:
        v->f0 = 60.0f + 340.0f * par[0];            /* P0 = PITCH porteuse */
        v->f1 = 1.0f + 7.0f * par[2];               /* P2 = RATIO modulateur */
        v->dec = 0.05f + 0.40f * par[1];
        v->pdec = 0.05f;
        break;
    }
    /* hybridation : configure aussi le moteur basse/lead du pad (SUB/8BIT/PLUK/FM) */
    {
        bass_t *b = &d->bass[pad];
        int eng = BASS_ENG[pad];
        bass_set(b, AT_ENGINE, (float)eng / (float)(AT_NENGINES - 1));
        bass_set(b, AT_TUNING, par[0]);
        bass_set(b, AT_DECAY, par[1]);
        bass_set(b, AT_WAVE, par[5]);                   /* P5 = B.WAVE */
        bass_set(b, AT_RESONANCE, par[6]);              /* P6 = B.RES  */
        bass_set(b, AT_CUTOFF, 0.85f);
        bass_set(b, AT_ENVMOD, 0.35f);
        bass_set(b, AT_ACCENT, 0.8f);
        int note = BASS_NOTE0[pad] + (int)(par[0] * (float)BASS_NOTESPAN[pad]);
        bass_gate(b, 0, note, 0, 0);                 /* reset */
        bass_gate(b, 1, note, 1, 0);                 /* re-strike */
    }
}

void drum_hit(drum_t *d, int pad)
{
    if (pad < 0 || pad >= DR_NPADS) return;
    /* REC live : enregistre le hit à la position courante (overdub, comme ml_rec od=1) */
    if (d->rec && d->playing) {
        dr_step_t *s = &d->pad[pad].st[d->play_pos];
        s->on = 1; s->vel = 100; s->cond = DR_COND_ALWAYS; s->slide = 0;
    }
    trigger(d, pad, 110);
}

/* ---- un échantillon de voix ---- */
static float voice_sample(drum_t *d, dr_voice_t *v)
{
    float sr = d->sr;
    float t = v->t;
    const float *par = v->par;
    float env = expf(-t / (v->dec * d->mod_dec));   /* LFO DECAY */
    float penv = expf(-t / v->pdec);
    float n = ((lcg(&v->nr) >> 9) & 0xFFFF) / 32768.0f - 1.0f;
    float hp = n - v->hp; v->hp = n;
    float f, s = 0.0f;
    switch (v->kind) {
    case DR_KICK: {
        /* Mutable Braids analog kick : pitch env exp multiplicatif + drive tanh + click */
        float drop = par[2] * 8.0f;
        f = v->f0 * (1.0f + drop * penv);
        v->phase += f * d->mod_pitch / sr;   /* LFO PITCH */
        float ph = v->phase - floorf(v->phase);
        float sig = sinf(2.0f * (float)M_PI * ph) * env * 0.9f;
        float drv = 1.0f + 8.0f * par[3];
        sig = tanhf(sig * drv) * (0.9f / sqrtf(drv));       /* drive (ZuluFx case 5) */
        sig += env * env * env * 0.3f * (0.5f + 0.5f * par[2]); /* click (ZuluFx case 5) */
        s = sig;
        break;
    }
    case DR_SNARE: {
        /* Mutable Elements snare : bruit + corps sinus, mix NOISE, drive */
        float mix = par[2];
        v->phase += v->f0 * d->mod_pitch / sr;
        float ph = v->phase - floorf(v->phase);
        float body = sinf(2.0f * (float)M_PI * ph) * (0.5f - 0.3f * mix); /* ZuluFx case 6 */
        float sig = (hp * (0.3f + 0.45f * mix) + body) * env * 0.9f;
        float drv = 1.0f + 5.0f * par[3];
        s = tanhf(sig * drv) * (0.9f / sqrtf(drv));
        break;
    }
    case DR_HAT: {
        /* ZuluFx case 7 : 6 carrés inharmoniques + HP, bright/métal */
        static const float hatF[6] = {263.f, 400.f, 421.f, 474.f, 587.f, 845.f};
        float g2 = powf(2.0f, (par[0] - 0.5f) * 2.0f);
        float band = 0.0f;
        for (int h = 0; h < 6; h++) {
            v->phase += hatF[h] * g2 * d->mod_pitch / sr;
            if (v->phase >= 1.0f) v->phase -= floorf(v->phase);
            band += (v->phase < 0.5f) ? 1.0f : -1.0f;
        }
        band *= (1.0f / 6.0f);
        float hpf = band - v->hp; v->hp = band;
        float bright = 0.35f + 0.65f * par[2];
        s = (hpf * bright + band * (1.0f - bright) * 0.3f) * env * 0.45f;
        break;
    }
    case DR_CLAP: {
        /* P0 = PITCH du filtre passe-bande (one-pole LP + HP = BP), fc 400..3600 Hz */
        float fc = 400.0f + 3200.0f * par[0];
        float c = fc / (fc + sr * 0.159f);
        v->lp += c * (n - v->lp);
        float bp = n - v->lp;
        int nburst = 1 + (int)(par[2] * 2.99f);             /* 1..3 bursts */
        float spc = 0.012f * (0.5f + par[3]);               /* espacement */
        float burst = 0.0f;
        for (int b = 0; b < nburst; b++) {
            float tb = b * spc;
            if (t >= tb && t < tb + 0.012f) burst = 1.0f;
        }
        s = bp * (burst > 0.0f ? 1.0f : 0.25f) * expf(-t / 0.09f) * 0.7f * env;
        break;
    }
    case DR_TOM: {
        float drop = par[2] * 4.0f;
        f = v->f0 * (1.0f + drop * penv);
        v->phase += f * d->mod_pitch / sr;   /* LFO PITCH */
        float ph = v->phase - floorf(v->phase);
        s = sinf(2.0f * (float)M_PI * ph) * env * 0.9f;
        break;
    }
    case DR_PERC: {
        /* pad ELIXIR : 5 sous-sons légers/aériens, P2 = TYPE (0 BELL, 1 CHIME, 2 KALIMBA, 3 GLASS, 4 PING) */
        int type = (int)(par[2] * 4.99f);
        if (type > 4) type = 4;
        v->phase += v->f0 * d->mod_pitch / sr;
        float ph = v->phase - floorf(v->phase);
        float sin1 = sinf(2.0f * (float)M_PI * ph);
        switch (type) {
        case 0: {  /* BELL : cloche inharmonique (partiel 2.76x) */
            v->phase2 += v->f0 * 2.76f * d->mod_pitch / sr;
            float ph2 = v->phase2 - floorf(v->phase2);
            s = (sin1 * 0.75f + sinf(2.0f * (float)M_PI * ph2) * 0.25f) * env;
            break;
        }
        case 1: {  /* CHIME : 2 partiels 1x + 3.01x */
            v->phase2 += v->f0 * 3.01f * d->mod_pitch / sr;
            float ph2 = v->phase2 - floorf(v->phase2);
            s = (sin1 * 0.6f + sinf(2.0f * (float)M_PI * ph2) * 0.4f) * env;
            break;
        }
        case 2: {  /* KALIMBA : corps boisé (1x + 4x faible) + attaque */
            v->phase2 += v->f0 * 4.0f * d->mod_pitch / sr;
            float ph2 = v->phase2 - floorf(v->phase2);
            s = (sin1 * 0.85f + sinf(2.0f * (float)M_PI * ph2) * 0.15f) * env
              + hp * expf(-t / 0.015f) * 0.3f;
            break;
        }
        case 3: {  /* GLASS : bruit HP cristallin + sinus aigu, decay très court */
            v->phase2 += v->f0 * 8.0f * d->mod_pitch / sr;
            float ph2 = v->phase2 - floorf(v->phase2);
            s = (hp * expf(-t / 0.03f) * 0.5f + sinf(2.0f * (float)M_PI * ph2) * 0.5f) * env;
            break;
        }
        default: { /* PING : FM aigu (mod 3.5x) */
            v->phase2 += v->f0 * 3.5f * d->mod_pitch / sr;
            float pm = v->phase2 - floorf(v->phase2);
            s = sinf(2.0f * (float)M_PI * ph + 2.5f * sinf(2.0f * (float)M_PI * pm)) * env;
            break;
        }
        }
        s += hp * expf(-t / 0.02f) * 0.2f * par[3];   /* P3 = NOISE */
        break;
    }
    case DR_COWBELL: {
        v->phase += v->f0 * d->mod_pitch / sr;
        v->phase2 += v->f1 / sr;
        float p1 = v->phase - floorf(v->phase);
        float p2 = v->phase2 - floorf(v->phase2);
        float sq1 = p1 < 0.5f ? 1.0f : -1.0f;
        float sq2 = p2 < 0.5f ? 1.0f : -1.0f;
        s = (sq1 * 0.6f + sq2 * 0.4f) * env * 0.5f + hp * expf(-t / 0.10f) * 0.15f;
        break;
    }
    case DR_FM: {
        /* Braids FM drum : P0=PITCH porteuse, P2=RATIO, P3=INDEX, feedback fixe */
        float idx = par[3] * 5.0f;
        float fb = 0.4f;
        v->phase2 += v->f0 * v->f1 * d->mod_pitch / sr;                   /* modulateur */
        float pm = v->phase2 - floorf(v->phase2);
        float mod = idx * sinf(2.0f * (float)M_PI * pm) + fb * v->hp;
        v->phase += v->f0 * d->mod_pitch / sr;
        float pc = v->phase - floorf(v->phase);
        v->hp = sinf(2.0f * (float)M_PI * pc + mod);         /* feedback sur la sortie */
        s = v->hp * env * 0.8f;
        break;
    }
    }
    return s * v->amp;
}

/* ---- conditions de trig façon Elektron ---- */
static int cond_ok(drum_t *d, int pad, int stepPos, int bar_n)
{
    int c = d->pad[pad].st[stepPos].cond;
    switch (c) {
    case DR_COND_ALWAYS: return 1;
    case DR_COND_1_2: return (bar_n % 2) == 0;
    case DR_COND_1_3: return (bar_n % 3) == 0;
    case DR_COND_1_4: return (bar_n % 4) == 0;
    case DR_COND_1_8: return (bar_n % 8) == 0;
    case DR_COND_FILL:
        /* ne joue que si un autre pad est actif sur ce step */
        for (int p = 0; p < DR_NPADS; p++)
            if (p != pad && d->pad[p].st[stepPos].on) return 1;
        return 0;
    }
    return 1;
}

/* ---- horloge + séquenceur (façon midi_looper.c : clk en 1/64 de mesure) ---- */
/* avance la position de lecture GLOBALE selon seq_mode — même fonctionnement que le
 * séquenceur des MIDI loops (ml_) : NORM / REV / PING-PONG / RANDOM, avec RAND au wrap. */
static void seq_advance(drum_t *d, float dt)
{
    if (!d->playing) return;
    float stepDur = 60.0f / (d->bpm * 4.0f);               /* 16e de note */
    d->ph += dt;
    float off = (d->gpos % 2 == 1) ? d->swing * stepDur : 0.0f;
    if (d->ph < stepDur + off) return;
    d->ph -= stepDur + off;
    int prev = d->gpos;
    switch (d->seq_mode) {
    case 1:  d->gpos = (d->gpos + DR_NSTEPS - 1) % DR_NSTEPS; break;            /* REV */
    case 2:  d->gpos += d->seq_dir;                                             /* PING-PONG */
             if (d->gpos >= DR_NSTEPS - 1) { d->gpos = DR_NSTEPS - 1; d->seq_dir = -1; }
             else if (d->gpos <= 0)           { d->gpos = 0; d->seq_dir = 1; }
             break;
    case 3:  d->gpos = (int)(frand(&d->rng) * DR_NSTEPS); break;                /* RANDOM */
    default: d->gpos = (d->gpos + 1) % DR_NSTEPS;                               /* NORM */
    }
    d->step = d->gpos;                                  /* pour l'UI */
    d->clk64 += 4.0f;
    if (d->gpos == 0) {
        /* wrap de mesure */
        if (d->gmut > 0) {
            /* MUTATION GLOBALE (façon ZuluFx / acid_mutate_burst) */
            for (int p = 0; p < DR_NPADS; p++) {
                if (frand(&d->rng) * 255.0f >= d->gmut) continue;
                for (int s = 0; s < DR_NSTEPS; s++) {
                    dr_step_t *e = &d->pad[p].st[s];
                    if (!e->on) continue;
                    if (frand(&d->rng) < 0.5f) e->vel = 30 + (int)(frand(&d->rng) * 97.0f);
                    if (frand(&d->rng) < 0.3f) e->cond = (int)(frand(&d->rng) * DR_COND_N);
                    if (frand(&d->rng) < 0.3f) e->slide = (int)(frand(&d->rng) * 17.0f) - 8;
                }
            }
        }
        if (d->randp > 0.0f && frand(&d->rng) < d->randp) {
            /* RAND au wrap : re-génère les patterns (proba randp), façon SEQ+ RAND */
            for (int p = 0; p < DR_NPADS; p++) drum_rand_pat(d, p, 0.35f, 0);
        }
        (void)prev;
    }
    int bar_n = (int)(d->clk64 / DR_BAR64);
    for (int p = 0; p < DR_NPADS; p++) {
        dr_pad_t *pd = &d->pad[p];
        int pos = d->gpos;
        if (pd->mode == DR_MODE_REV) pos = (DR_NSTEPS - 1) - d->gpos;   /* mode par pad en plus */
        dr_step_t *s = &pd->st[pos];
        if (!s->on) continue;
        if (!cond_ok(d, p, pos, bar_n)) continue;
        int vel = s->vel;
        if (pd->mut && frand(&d->rng) * 255.0f < pd->mut) vel = 30 + (int)(frand(&d->rng) * 97.0f);
        trigger(d, p, vel);
        d->trig_count[p]++;
    }
}

static float comp_sample(drum_t *d, float x, float det);   /* compresseur de bus, défini plus bas */

/* delay mono partagé (ring) + reverb "room courte" = 2 allpass en série (Schroeder light),
 * très économe : 2 buffers circulaires, pas de comb. */
static float fx_delay(drum_t *d, float in)
{
    dr_fx_t *f = &d->fx;
    uint32_t rp = (f->dly_pos + DR_DLY_MAX - (uint32_t)(f->dly_time * d->sr * 0.5f)) % DR_DLY_MAX;
    float dv = f->dly[rp];
    f->dly[f->dly_pos] = in + dv * f->dly_fb;
    f->dly_pos = (f->dly_pos + 1) % DR_DLY_MAX;
    return dv;
}

/* allpass Schroeder, un buffer circulaire (buf[k] = v[k], v[n] = x[n] + g*v[n-d], y[n] = v[n-d] - g*v[n]) */
static float fx_allpass(float *buf, uint32_t *pos, int n, int d, float g, float x)
{
    uint32_t wp = *pos;
    uint32_t rp = (wp + (uint32_t)(n - d)) % (uint32_t)n;
    float vold = buf[rp];                 /* v[n-d] */
    float v = x + g * vold;
    buf[wp] = v;
    *pos = (wp + 1) % (uint32_t)n;
    return vold - g * v;
}

void drum_render(drum_t *d, float *outL, float *outR, int n)
{
    float dt = 1.0f / d->sr;
    for (int i = 0; i < n; i++) {
        if ((i & 31) == 0) lfo_block(d);        /* LFO par blocs de 32, comme ztek_mod_block */
        seq_advance(d, dt);
        float dryL = 0.0f, dryR = 0.0f, det = 0.0f, dlyIn = 0.0f, revIn = 0.0f;
        for (int p = 0; p < DR_NPADS; p++) {
            dr_voice_t *v = &d->v[p];
            if (v->t < 0.0f) continue;
            v->t += dt;
            if (v->t > 2.0f) { v->t = -1.0f; bass_gate(&d->bass[p], 0, 0, 0, 0); continue; }
            float cs = voice_sample(d, v);           /* moteur custom */
            float bs = 0.0f;
            bass_render(&d->bass[p], &bs, 1);        /* moteur basse/lead */
            float h = d->pad[p].par[4];              /* P4 = HYB */
            float s = cs * (1.0f - h) + bs * h;
            const float *par = d->pad[p].par;
            float pan = par[8] + d->mod_pan;         /* P8 = PAN + LFO PAN */
            if (pan < 0.0f) pan = 0.0f; if (pan > 1.0f) pan = 1.0f;
            float pl = cosf(pan * 1.5707963f), pr = sinf(pan * 1.5707963f);
            dryL += s * pl; dryR += s * pr;
            det += fabsf(s) * (0.2f + 1.6f * par[11]); /* P11 = C.SEND */
            dlyIn += s * par[9] * d->mod_dsend;      /* P9 = SEND DELAY (+LFO) */
            revIn += s * par[10] * d->mod_rsend;     /* P10 = SEND REVERB (+LFO) */
        }
        float dlyOut = fx_delay(d, dlyIn);
        /* reverb "room courte" : 2 allpass en série (211 + 331 éch ≈ 12 ms), très économe */
        float r = fx_allpass(d->fx.rev_a, &d->fx.rev_pa, DR_REV_D1, DR_REV_D1 / 2, 0.5f, revIn);
        r = fx_allpass(d->fx.rev_b, &d->fx.rev_pb, DR_REV_D2, DR_REV_D2 / 2, 0.5f, r);
        float wet = dlyOut * d->fx.dly_wet + r * d->fx.rev_wet * 0.5f;
        float cl = comp_sample(d, dryL, det);
        float cr = comp_sample(d, dryR, det);
        /* GLITCH/CHAOS global (labo CHAOS ZuluFx / bend GLITCH) : sample-hold + bit-crush */
        float glitch_eff = d->glitch + d->mod_glitch;   /* LFO GLITCH */
        if (glitch_eff < 0.0f) glitch_eff = 0.0f; if (glitch_eff > 1.0f) glitch_eff = 1.0f;
        if (glitch_eff > 0.0f) {
            if (d->shn <= 0 && frand(&d->rng) < glitch_eff * 0.02f)
                d->shn = 4 + (int)(frand(&d->rng) * 24.0f);   /* rafale de sample-hold */
            if (d->shn > 0) { d->shn--; cl = cr = d->sh; }
            else d->sh = cl;
            if (glitch_eff > 0.02f) {                          /* bit-crush : +glitch = -de bits */
                float q = 1.0f + (1.0f - glitch_eff) * 15.0f;
                cl = floorf(cl * q) / q;
                cr = floorf(cr * q) / q;
            }
        }
        outL[i] += (cl + wet) * d->master * 0.35f;
        outR[i] += (cr + wet) * d->master * 0.35f;
    }
}

/* compresseur de bus, inspiré AUV3 Fac Smash : follower de crête (atk 0.2 ms / rel 80 ms),
 * soft-knee linéaire sans powf (cheap FM-1), ratio 1..20, makeup. */
static float comp_sample(drum_t *d, float x, float det)
{
    dr_comp_t *c = &d->comp;
    if (!c->on) return x;
    float ax = det > 0.0f ? det : (x < 0.0f ? -x : x);   /* détecteur = somme pondérée des voies (C.SEND) */
    float atk = 0.25f, rel = 0.0025f;                /* lerp par sample : atk ~3 ech, rel ~400 ech (~8 ms) */
    atk *= 48000.0f / d->sr; rel *= 48000.0f / d->sr;
    if (ax > c->env) c->env += atk * (ax - c->env);
    else c->env += rel * (ax - c->env);
    float thr = c->thr + d->mod_thr;                 /* LFO COMP */
    if (thr < 0.01f) thr = 0.01f; if (thr > 1.0f) thr = 1.0f;
    float over = c->env - thr;
    if (over <= 0.0f) {
        c->gr *= 0.7f;                               /* release doux */
        return x * (1.0f - c->gr) * c->makeup;
    }
    if (c->ratio >= 19.9f) {
        /* SMASH radical (ratio à fond) : limiteur DUR au seuil + saturation tanh ->
         * écrase tout ce qui dépasse, son très agressif façon "Fac Smash" poussé. */
        float lim = thr / (c->env > 1e-6f ? c->env : 1e-6f);
        if (lim > 1.0f) lim = 1.0f;
        c->gr = 1.0f - lim;
        float y = x * lim * c->makeup;
        return tanhf(y * 2.5f) * 0.8f;
    }
    float k = 1.0f - 1.0f / c->ratio;                /* 0 = ratio 1 (rien), ->0.95 */
    float gr = k * (over / (thr + over));             /* soft-knee linéaire */
    if (gr > 1.0f) gr = 1.0f;
    c->gr += 0.3f * (gr - c->gr);                     /* lissage du gain */
    return x * (1.0f - c->gr) * c->makeup;
}

/* ---- API séquenceur ---- */
void drum_set_step(drum_t *d, int pad, int step, int on, int vel, int cond, int slide)
{
    if (pad < 0 || pad >= DR_NPADS || step < 0 || step >= DR_NSTEPS) return;
    dr_step_t *s = &d->pad[pad].st[step];
    s->on = on ? 1 : 0;
    if (vel >= 0) s->vel = (uint8_t)(vel > 127 ? 127 : vel);
    if (cond >= 0) s->cond = (uint8_t)cond;
    s->slide = (int8_t)(slide < -8 ? -8 : slide > 8 ? 8 : slide);
}

void drum_get_step(const drum_t *d, int pad, int step, int *on, int *vel, int *cond, int *slide)
{
    if (pad < 0 || pad >= DR_NPADS || step < 0 || step >= DR_NSTEPS) return;
    const dr_step_t *s = &d->pad[pad].st[step];
    if (on) *on = s->on;
    if (vel) *vel = s->vel;
    if (cond) *cond = s->cond;
    if (slide) *slide = s->slide;
}

void drum_clear_pat(drum_t *d, int pad)
{
    if (pad < 0 || pad >= DR_NPADS) return;
    memset(d->pad[pad].st, 0, sizeof(d->pad[pad].st));
}

void drum_rand_pat(drum_t *d, int pad, float density, unsigned seed)
{
    if (pad < 0 || pad >= DR_NPADS) return;
    unsigned s = seed ? seed : 0xC0FFEEu;
    for (int st = 0; st < DR_NSTEPS; st++) {
        dr_step_t *e = &d->pad[pad].st[st];
        e->on = frand(&s) < density;
        e->vel = 40 + (int)(frand(&s) * 87.0f);
        e->cond = DR_COND_ALWAYS;
        e->slide = 0;
    }
}

void drum_set_param(drum_t *d, int pad, int k, float v)
{
    if (pad < 0 || pad >= DR_NPADS || k < 0 || k >= DR_NPAR) return;
    d->pad[pad].par[k] = v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
}

float drum_get_param(const drum_t *d, int pad, int k)
{
    if (pad < 0 || pad >= DR_NPADS || k < 0 || k >= DR_NPAR) return 0.0f;
    return d->pad[pad].par[k];
}

void drum_set_kit(drum_t *d, int kit)
{
    if (kit < 0 || kit >= DR_KIT_N) return;
    for (int p = 0; p < DR_NPADS; p++)
        memcpy(d->pad[p].par, KITPAR[kit][p], sizeof(d->pad[p].par));
}

void drum_set_hyb(drum_t *d, int pad, float h)       /* alias knob P4 */
{
    if (pad < 0 || pad >= DR_NPADS) return;
    d->pad[pad].par[4] = h < 0.0f ? 0.0f : h > 1.0f ? 1.0f : h;
}

float drum_get_hyb(const drum_t *d, int pad)
{
    if (pad < 0 || pad >= DR_NPADS) return 0.0f;
    return d->pad[pad].par[4];
}

void drum_set_comp(drum_t *d, int on, float thr, float ratio, float makeup)
{
    d->comp.on = on ? 1 : 0;
    d->comp.thr = thr < 0.0f ? 0.0f : thr > 1.0f ? 1.0f : thr;
    d->comp.ratio = ratio < 1.0f ? 1.0f : ratio > 20.0f ? 20.0f : ratio;
    d->comp.makeup = makeup < 0.0f ? 0.0f : makeup > 2.0f ? 2.0f : makeup;
}

void drum_get_comp(const drum_t *d, int *on, float *thr, float *ratio, float *makeup)
{
    if (on) *on = d->comp.on;
    if (thr) *thr = d->comp.thr;
    if (ratio) *ratio = d->comp.ratio;
    if (makeup) *makeup = d->comp.makeup;
}

void drum_set_delay(drum_t *d, float time, float fb, float wet)
{
    d->fx.dly_time = time < 0.0f ? 0.0f : time > 1.0f ? 1.0f : time;
    d->fx.dly_fb = fb < 0.0f ? 0.0f : fb > 0.95f ? 0.95f : fb;
    d->fx.dly_wet = wet < 0.0f ? 0.0f : wet > 1.0f ? 1.0f : wet;
}

void drum_set_rev(drum_t *d, float wet)
{
    d->fx.rev_wet = wet < 0.0f ? 0.0f : wet > 1.0f ? 1.0f : wet;
}

void drum_set_gmut(drum_t *d, int mut)
{
    d->gmut = mut < 0 ? 0 : mut > 255 ? 255 : mut;
}

void drum_set_glitch(drum_t *d, float g)
{
    d->glitch = g < 0.0f ? 0.0f : g > 1.0f ? 1.0f : g;
}

void drum_set_pad_mode(drum_t *d, int pad, int mode)
{
    if (pad < 0 || pad >= DR_NPADS || mode < 0 || mode >= DR_MODE_N) return;
    d->pad[pad].mode = (uint8_t)mode;
}

void drum_set_pad_mut(drum_t *d, int pad, int mut)
{
    if (pad < 0 || pad >= DR_NPADS) return;
    d->pad[pad].mut = (uint8_t)(mut > 255 ? 255 : mut);
}

void drum_set_level(drum_t *d, int pad, float lvl)   /* alias knob P7 */
{
    if (pad < 0 || pad >= DR_NPADS) return;
    d->pad[pad].par[7] = lvl < 0.0f ? 0.0f : lvl > 1.0f ? 1.0f : lvl;
}

void drum_set_bpm(drum_t *d, float bpm)  { if (bpm > 20.0f && bpm < 400.0f) d->bpm = bpm; }
void drum_set_swing(drum_t *d, float s)  { if (s >= 0.0f && s <= 0.5f) d->swing = s; }
void drum_set_master(drum_t *d, float m) { if (m >= 0.0f && m <= 1.0f) d->master = m; }
void drum_play(drum_t *d, int on)        { d->playing = on ? 1 : 0; if (on) drum_reset(d); }
void drum_set_rec(drum_t *d, int on)     { d->rec = on ? 1 : 0; }
int  drum_get_cur_step(const drum_t *d)   { return d->step; }
float drum_get_clk64(const drum_t *d)    { return d->clk64; }
