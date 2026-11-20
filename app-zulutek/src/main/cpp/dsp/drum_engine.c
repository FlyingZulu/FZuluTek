/* drum_engine.c — voir drum_engine.h. C99, aucune lib externe (sinf de math.h). */
#include "drum_engine.h"
#include <math.h>
#include <string.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

const char *DR_PAD_NAMES[DR_NPADS] = {
    "KICK", "SNARE", "HAT", "HAT-O", "CLAP", "TOM", "CONGA", "COWBELL"
};

static unsigned lcg(unsigned *s)
{
    *s = *s * 1664525u + 1013904223u;
    return *s;
}

static float noise(dr_voice_t *v)   /* bruit blanc [-1,1] */
{
    return ((lcg(&v->nr) >> 9) & 0xFFFF) / 32768.0f - 1.0f;
}

void drum_init(drum_t *d, float sr)
{
    memset(d, 0, sizeof(*d));
    d->sr = sr;
    d->bpm = 120.0f;
    d->swing = 0.0f;
    d->master = 0.8f;
    d->step = 0;
    d->ph = 0.0f;
    for (int p = 0; p < DR_NPADS; p++) {
        d->lvl[p] = 0.9f;
        d->v[p].t = -1.0f;
    }
    /* patterns par défaut : 4-on-the-floor, backbeat, 8th hats */
    for (int s = 0; s < DR_NSTEPS; s += 4) d->pat[DR_KICK][s] = 1;
    d->pat[DR_SNARE][4] = d->pat[DR_SNARE][12] = 1;
    for (int s = 0; s < DR_NSTEPS; s += 2) d->pat[DR_HATC][s] = 1;
    d->pat[DR_HATO][14] = 1;
    d->pat[DR_CLAP][12] = 1;
    d->pat[DR_CONGA][6] = d->pat[DR_CONGA][14] = 1;
    /* choke désactivé par défaut (tout à 0) ; leader ON, pas de leader désigné */
    d->leader = 1;
    for (int g = 0; g < DR_NCHOKE; g++) d->lead[g] = -1;
}

void drum_set_choke(drum_t *d, int pad, int grp)
{
    if (pad < 0 || pad >= DR_NPADS) return;
    if (grp < 0) grp = 0;
    if (grp >= DR_NCHOKE) grp = DR_NCHOKE - 1;
    if (d->choke[pad] != (uint8_t)grp) {       /* nouvelle assignation = nouvel ordre */
        int old = d->choke[pad];
        if (old > 0 && d->lead[old] == pad) d->lead[old] = -1;   /* le leader explicite quitte le groupe */
        d->choke[pad] = (uint8_t)grp;
        d->choke_order[pad] = ++d->choke_tick;
    }
}

int drum_get_choke(const drum_t *d, int pad)
{
    if (pad < 0 || pad >= DR_NPADS) return 0;
    return d->choke[pad];
}

int drum_choke_master(const drum_t *d, int grp)
{
    int best = -1;
    uint32_t bo = 0xFFFFFFFFu;
    if (grp <= 0 || grp >= DR_NCHOKE) return -1;
    if (d->lead[grp] >= 0 && d->choke[d->lead[grp]] == (uint8_t)grp)
        return d->lead[grp];                    /* leader désigné explicitement (POLY) */
    for (int p = 0; p < DR_NPADS; p++)
        if (d->choke[p] == (uint8_t)grp && d->choke_order[p] < bo) {
            bo = d->choke_order[p];
            best = p;
        }
    return best;
}

void drum_set_leader(drum_t *d, int on) { d->leader = on ? 1 : 0; }
int  drum_get_leader(const drum_t *d)   { return d->leader; }
void drum_set_leader_pad(drum_t *d, int pad, int on)
{
    int g;
    if (pad < 0 || pad >= DR_NPADS) return;
    g = d->choke[pad];
    if (g <= 0) return;
    if (on) d->lead[g] = (int8_t)pad;                       /* désigner leader (sticky) */
    else if (d->lead[g] == pad) d->lead[g] = -1;            /* désassigner le leader -> retour à l'auto */
}

void drum_reset(drum_t *d)
{
    d->step = 0;
    d->ph = 0.0f;
    for (int p = 0; p < DR_NPADS; p++) d->v[p].t = -1.0f;
}

void drum_set_eco(drum_t *d, int on) { d->eco = on ? 1 : 0; }
int  drum_get_eco(const drum_t *d)   { return d->eco; }

void drum_hit(drum_t *d, int pad)
{
    if (pad < 0 || pad >= DR_NPADS) return;
    if (d->eco) {   /* MODE ECO : polyphonie 8 -> 5, la voix la plus vieille est coupée */
        int act = 0, oldest = -1;
        float ot = -1.0f;
        for (int p = 0; p < DR_NPADS; p++)
            if (d->v[p].t >= 0.0f) {
                act++;
                if (d->v[p].t > ot) { ot = d->v[p].t; oldest = p; }
            }
        if (act >= 5 && oldest >= 0 && oldest != pad) d->v[oldest].t = -1.0f;
    }
    {
        int g = d->choke[pad];
        if (g > 0) {
            /* mode leader : seul le master du groupe coupe ; sinon (Koala) tout membre coupe */
            if (!d->leader || drum_choke_master(d, g) == pad)
                for (int p = 0; p < DR_NPADS; p++)
                    if (p != pad && d->choke[p] == (uint8_t)g)
                        d->v[p].t = -1.0f;      /* tue la voix */
        }
    }
    dr_voice_t *v = &d->v[pad];
    v->t = 0.0f;
    v->kind = pad;
    v->amp = d->lvl[pad];
    v->phase = 0.0f;
    v->hp = 0.0f;
    v->nr = 0x1234u + (unsigned)pad * 7919u;
    switch (pad) {
    case DR_KICK:   v->f0 = 150.0f; v->f1 = 45.0f;  v->dec = 0.28f; break;
    case DR_SNARE:  v->f0 = 185.0f; v->f1 = 180.0f;  v->dec = 0.16f; break;
    case DR_HATC:   v->f0 = 0.0f;   v->f1 = 0.0f;    v->dec = 0.035f; break;
    case DR_HATO:   v->f0 = 0.0f;   v->f1 = 0.0f;    v->dec = 0.22f; break;
    case DR_CLAP:   v->f0 = 0.0f;   v->f1 = 0.0f;    v->dec = 0.20f; break;
    case DR_TOM:    v->f0 = 210.0f; v->f1 = 95.0f;   v->dec = 0.30f; break;
    case DR_CONGA:  v->f0 = 480.0f; v->f1 = 330.0f;  v->dec = 0.16f; break;
    case DR_COWBELL:v->f0 = 845.0f; v->f1 = 560.0f;  v->dec = 0.35f; break;
    }
}

/* un échantillon d'une voix ; avance son horloge interne */
static float voice_sample(drum_t *d, dr_voice_t *v)
{
    float sr = d->sr;
    float t = v->t;
    float f = v->f1 + (v->f0 - v->f1) * expf(-t / 0.06f);  /* pitch env */
    v->phase += f / sr;
    float ph = v->phase - floorf(v->phase);
    float env = expf(-t / v->dec);
    float n = noise(v);
    float hp = n - v->hp;                                  /* HP = différentiateur */
    v->hp = n;
    float s = 0.0f;
    switch (v->kind) {
    case DR_KICK:
        s = sinf(2.0f * (float)M_PI * ph) * env * 0.9f
          + noise(v) * expf(-t / 0.004f) * 0.5f;          /* click */
        break;
    case DR_SNARE:
        s = hp * env * 0.55f + sinf(2.0f * (float)M_PI * ph) * expf(-t / 0.09f) * 0.45f;
        break;
    case DR_HATC:
    case DR_HATO:
        s = hp * env * 0.5f;
        break;
    case DR_CLAP: {
        /* 3 bursts de bruit puis decay long */
        float burst = (t < 0.012f) || (t > 0.020f && t < 0.032f) || (t > 0.042f && t < 0.058f);
        s = hp * (burst ? 1.0f : 0.25f) * expf(-t / 0.09f) * 0.7f;
        break;
    }
    case DR_TOM:
        s = sinf(2.0f * (float)M_PI * ph) * env * 0.9f;
        break;
    case DR_CONGA:
        s = sinf(2.0f * (float)M_PI * ph) * env * 0.8f + hp * expf(-t / 0.05f) * 0.25f;
        break;
    case DR_COWBELL: {
        float sq1 = ph < 0.5f ? 1.0f : -1.0f;
        float f2 = v->f1; v->phase -= f2 / sr;             /* 2e osc à f1 */
        float ph2 = v->phase - floorf(v->phase);
        float sq2 = ph2 < 0.5f ? 1.0f : -1.0f;
        s = (sq1 * 0.6f + sq2 * 0.4f) * env * 0.5f + hp * expf(-t / 0.10f) * 0.15f;
        break;
    }
    }
    return s * v->amp;
}

/* horloge du séquenceur : déclenche les pads du step courant */
static void clock(drum_t *d, float dt)
{
    d->ph += dt;
    float stepDur = 60.0f / (d->bpm * 4.0f);              /* 16e de note */
    float off = (d->step % 2 == 1) ? d->swing * stepDur : 0.0f;
    if (d->ph >= stepDur + off) {
        d->ph -= stepDur + off;
        d->step = (d->step + 1) % DR_NSTEPS;
        for (int p = 0; p < DR_NPADS; p++)
            if (d->pat[p][d->step]) drum_hit(d, p);
    }
}

void drum_render(drum_t *d, float *out, int n)
{
    float dt = 1.0f / d->sr;
    for (int i = 0; i < n; i++) {
        clock(d, dt);
        float s = 0.0f;
        for (int p = 0; p < DR_NPADS; p++) {
            dr_voice_t *v = &d->v[p];
            if (v->t < 0.0f) continue;
            v->t += dt;
            if (v->t > 1.5f) { v->t = -1.0f; continue; }
            s += voice_sample(d, v);
        }
        out[i] += s * d->master * 0.4f;
    }
}

void drum_set_pat(drum_t *d, int pad, int step, int on)
{
    if (pad < 0 || pad >= DR_NPADS || step < 0 || step >= DR_NSTEPS) return;
    d->pat[pad][step] = on ? 1 : 0;
}

int drum_get_pat(const drum_t *d, int pad, int step)
{
    if (pad < 0 || pad >= DR_NPADS || step < 0 || step >= DR_NSTEPS) return 0;
    return d->pat[pad][step];
}

void drum_clear_pat(drum_t *d, int pad)
{
    if (pad < 0 || pad >= DR_NPADS) return;
    memset(d->pat[pad], 0, sizeof(d->pat[pad]));
}

void drum_rand_pat(drum_t *d, int pad, float density, unsigned seed)
{
    if (pad < 0 || pad >= DR_NPADS) return;
    unsigned s = seed ? seed : 0xC0FFEEu;
    for (int st = 0; st < DR_NSTEPS; st++) {
        float r = ((lcg(&s) >> 9) & 0xFFFF) / 65536.0f;
        d->pat[pad][st] = (r < density) ? 1 : 0;
    }
}

void drum_set_bpm(drum_t *d, float bpm)   { if (bpm > 20.0f && bpm < 400.0f) d->bpm = bpm; }
void drum_set_swing(drum_t *d, float s)   { if (s >= 0.0f && s <= 0.5f) d->swing = s; }
void drum_set_level(drum_t *d, int pad, float lvl)
{
    if (pad < 0 || pad >= DR_NPADS) return;
    d->lvl[pad] = lvl;
}
void drum_set_master(drum_t *d, float m)  { if (m >= 0.0f && m <= 1.0f) d->master = m; }
int  drum_get_step(const drum_t *d)       { return d->step; }

void drum_clear_leader(drum_t *d, int grp)          /* POLY sans sélection : mode leader off */
{
    if (grp > 0 && grp < DR_NCHOKE) d->lead[grp] = -1;
}
