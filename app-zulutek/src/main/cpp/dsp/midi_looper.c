/*
 * Copyright (C) 2026 FlyingZulu
 * See midi_looper.h. Ported from the ZuluFx app's MIDI loops (fm1-Zulu, GPL-3.0). */
#include "midi_looper.h"
#include "fastmath.h"
#include <string.h>

static inline float clampf(float x, float lo, float hi) { return x < lo ? lo : x > hi ? hi : x; }

/* x mod len, in [0,len), by division (clk grows large, so no repeated subtraction) */
static float ml_fmodf(float x, float len)
{
    float r = x - len * fm_floorf(x / len);
    if (r < 0.0f) r += len;
    if (r >= len) r -= len;
    return r;
}
static uint16_t ml_fmod64(float x, uint16_t len) { return (uint16_t)ml_fmodf(x, (float)len); }

static uint32_t ml_rnd(ml_t *m)
{
    m->rng ^= m->rng << 13; m->rng ^= m->rng >> 17; m->rng ^= m->rng << 5;
    return m->rng;
}

/* quantize a span (1/64 bar) to the nearest musical length: 1,2,4,...,128 beats */
static uint16_t ml_quantize(float span)
{
    static const uint16_t P[8] = {16, 32, 64, 128, 256, 512, 1024, 2048};
    int i, best = 0;
    float bd = 1e30f;
    for (i = 0; i < 8; i++) {
        float d = span > (float)P[i] ? span - (float)P[i] : (float)P[i] - span;
        if (d < bd) { bd = d; best = i; }
    }
    return P[best];
}

void ml_init(ml_t *m, float sr)
{
    int i;
    memset(m, 0, sizeof *m);
    m->sr = sr;
    m->bpm = 120.0f;
    m->loop_gain = 1.0f;
    m->sel = 0;
    m->rng = 0x5EED1234u;
    m->leader = 1;                 /* mode leader (hybride) par défaut ; choke tout à 0 (off) */
    for (i = 0; i < 7; i++) m->lead[i] = -1;
    for (i = 0; i < ML_NSLOTS; i++) {
        m->slot[i].len = ML_BAR64;   /* placeholder; len_set=0 -> quantized at rec stop */
        m->slot[i].len_set = 0;
        m->slot[i].sp.src = 3;     /* ALL */
        m->slot[i].sp.vol = 255;
        m->slot[i].dir = 1;
    }
}

void ml_set_bpm(ml_t *m, float bpm)
{
    if (bpm < 30.0f) bpm = 30.0f;
    if (bpm > 300.0f) bpm = 300.0f;
    m->bpm = bpm;
}

void ml_play(ml_t *m, int on)
{
    int i;
    m->playing = on ? 1 : 0;
    if (on) {
        m->clk = 0.0f;
        for (i = 0; i < ML_NSLOTS; i++)
            if (m->slot[i].on) {
                m->slot[i].pos = 0.0f;
                m->slot[i].last_pos = 0.0f;
                m->slot[i].dir = 1;
                m->slot[i].n_act = 0;
            }
    }
}

void ml_set_cb(ml_t *m, void *ctx,
               void (*note_on)(void *, int, int, int), void (*note_off)(void *, int, int),
               void (*param)(void *, int, int, float))
{
    m->ctx = ctx;
    m->note_on = note_on;
    m->note_off = note_off;
    m->param = param;
}

void ml_toggle(ml_t *m, int slot)
{
    ml_slot_t *s;
    if (slot < 0 || slot >= ML_NSLOTS) return;
    s = &m->slot[slot];
    if (s->rec) return;                        /* no toggle while recording */
    s->on = s->on ? 0 : 1;
    if (s->on && (s->n_note + s->n_par > 0 || s->rs)) {
        s->pos = ml_fmod64(m->clk, s->len ? s->len : ML_BAR64);
        s->last_pos = s->pos;
        s->dir = 1;
        s->n_act = 0;
    }
    if (s->on) {
        /* choke au déclenchement : mode leader = seul le master du groupe arrête les autres,
         * sinon (normal/Koala) tout membre arrête les autres membres du même groupe */
        int g = m->choke[slot];
        if (g > 0 && (!m->leader || ml_choke_master(m, g) == slot)) {
            int i, a;
            for (i = 0; i < ML_NSLOTS; i++) {
                ml_slot_t *o;
                if (i == slot || m->choke[i] != (uint8_t)g || !m->slot[i].on) continue;
                o = &m->slot[i];
                o->on = 0;
                for (a = 0; a < o->n_act; a++)
                    if (m->note_off) m->note_off(m->ctx, i, o->act[a].note);
                o->n_act = 0;
            }
        }
    }
}

void ml_set_choke(ml_t *m, int slot, int grp)
{
    if (slot < 0 || slot >= ML_NSLOTS) return;
    if (grp < 0) grp = 0;
    if (grp > 6) grp = 6;
    if (m->choke[slot] != (uint8_t)grp) {   /* nouvelle assignation = nouvel ordre */
        int old = m->choke[slot];
        if (old > 0 && m->lead[old] == slot) m->lead[old] = -1;   /* le leader explicite quitte le groupe */
        m->choke[slot] = (uint8_t)grp;
        m->choke_order[slot] = ++m->choke_tick;
    }
}

int ml_get_choke(const ml_t *m, int slot)
{
    if (slot < 0 || slot >= ML_NSLOTS) return 0;
    return m->choke[slot];
}

int ml_choke_master(const ml_t *m, int grp)
{
    int best = -1, i;
    uint32_t bo = 0xFFFFFFFFu;
    if (grp <= 0 || grp > 6) return -1;
    if (m->lead[grp] >= 0 && m->choke[m->lead[grp]] == (uint8_t)grp)
        return m->lead[grp];                /* leader désigné explicitement (POLY) */
    for (i = 0; i < ML_NSLOTS; i++)
        if (m->choke[i] == (uint8_t)grp && m->choke_order[i] < bo) {
            bo = m->choke_order[i];
            best = i;
        }
    return best;
}

void ml_set_leader(ml_t *m, int on) { m->leader = on ? 1 : 0; }
int  ml_get_leader(const ml_t *m)   { return m->leader; }
void ml_set_leader_slot(ml_t *m, int slot, int on)
{
    int g;
    if (slot < 0 || slot >= ML_NSLOTS) return;
    g = m->choke[slot];
    if (g <= 0) return;
    if (on) m->lead[g] = (int8_t)slot;                      /* désigner leader (sticky) */
    else if (m->lead[g] == slot) m->lead[g] = -1;           /* désassigner -> retour à l'auto */
}

void ml_set_len(ml_t *m, int slot, int len64)
{
    ml_slot_t *s;
    if (slot < 0 || slot >= ML_NSLOTS) return;
    s = &m->slot[slot];
    if (len64 <= 0) { s->len_set = 0; return; }       /* 0 = auto at rec stop */
    if (len64 > ML_MAXLEN) len64 = ML_MAXLEN;
    s->len = (uint16_t)len64;
    s->len_set = 1;
}

void ml_resize(ml_t *m, int slot, int newlen)
{
    ml_slot_t *s;
    if (slot < 0 || slot >= ML_NSLOTS || newlen <= 0 || newlen > ML_MAXLEN) return;
    s = &m->slot[slot];
    if (!s->len_set || s->len == 0) {                  /* nothing recorded yet: just set */
        s->len = (uint16_t)newlen;
        s->len_set = 1;
        return;
    }
    if (newlen < s->len) {                             /* shrink: truncate */
        uint16_t nn = 0, np = 0, i;
        for (i = 0; i < s->n_note; i++)
            if (s->note[i].t < newlen) s->note[nn++] = s->note[i];
        for (i = 0; i < s->n_par; i++)
            if (s->par[i].t < newlen) s->par[np++] = s->par[i];
        s->n_note = nn;
        s->n_par = np;
    } else if (newlen > s->len) {                      /* extend: clone the original loop */
        uint16_t old = s->len, k, on = s->n_note, op = s->n_par;
        for (k = old; k < (uint16_t)newlen; k += old) {
            uint16_t i;
            for (i = 0; i < on && s->n_note < ML_MAXEV; i++) {   /* only the original events */
                uint16_t t2 = (uint16_t)(s->note[i].t + k);
                if (t2 >= (uint16_t)newlen) continue;
                s->note[s->n_note++] = s->note[i];
                s->note[s->n_note - 1].t = t2;
            }
            for (i = 0; i < op && s->n_par < ML_MAXPEV; i++) {
                uint16_t t2 = (uint16_t)(s->par[i].t + k);
                if (t2 >= (uint16_t)newlen) continue;
                s->par[s->n_par++] = s->par[i];
                s->par[s->n_par - 1].t = t2;
            }
        }
    }
    s->len = (uint16_t)newlen;
    s->len_set = 1;
}

void ml_set_sp(ml_t *m, int slot, int idx, float v)
{
    ml_slot_t *s;
    if (slot < 0 || slot >= ML_NSLOTS) return;
    s = &m->slot[slot];
    switch (idx) {
    case ML_SP_PITCH: s->sp.pitch = (int8_t)clampf(v, -12.0f, 12.0f); break;
    case ML_SP_POS:   s->sp.pos = (uint16_t)clampf(v, 0.0f, s->len ? (float)s->len - 1.0f : 0.0f); break;
    case ML_SP_MODE:  s->sp.mode = (uint8_t)clampf(v, 0.0f, 3.0f); if (s->sp.mode == 2) s->dir = 1; else if (s->sp.mode == 1) s->dir = -1; break;
    case ML_SP_MUT:   s->sp.mut = (uint8_t)clampf(v * 255.0f, 0.0f, 255.0f); break;
    case ML_SP_SRC:   s->sp.src = (uint8_t)clampf(v, 0.0f, 6.0f); break;
    case ML_SP_FX:    s->sp.fx = v >= 0.5f ? 1 : 0; break;
    case ML_SP_VOL:   s->sp.vol = (uint8_t)clampf(v * 255.0f, 0.0f, 255.0f); break;
    }
}

float ml_get_sp(ml_t *m, int slot, int idx)
{
    ml_slot_t *s;
    if (slot < 0 || slot >= ML_NSLOTS) return 0.0f;
    s = &m->slot[slot];
    switch (idx) {
    case ML_SP_PITCH: return (float)s->sp.pitch;
    case ML_SP_POS:   return (float)s->sp.pos;
    case ML_SP_MODE:  return (float)s->sp.mode;
    case ML_SP_MUT:   return s->sp.mut / 255.0f;
    case ML_SP_SRC:   return (float)s->sp.src;
    case ML_SP_FX:    return (float)s->sp.fx;
    case ML_SP_VOL:   return s->sp.vol / 255.0f;
    }
    return 0.0f;
}

void ml_rec_start(ml_t *m, int slot, int od)
{
    ml_slot_t *s;
    if (slot < 0 || slot >= ML_NSLOTS) return;
    s = &m->slot[slot];
    if (!od) {
        s->n_note = 0;
        s->n_par = 0;
    } else {
        s->od_base = s->n_note;                /* overdub: keep the existing events, append new ones */
    }
    s->od = od ? 1 : 0;
    s->n_act = 0;
    s->rec = 1;
    s->on = 1;                                 /* monitor while recording */
    s->rs = 0;
    s->rec_clk = m->clk;
    s->pos = 0.0f;
    s->last_pos = 0.0f;
}

void ml_rec_stop(ml_t *m, int slot)
{
    ml_slot_t *s;
    float span;
    uint16_t len;
    uint32_t base;
    int i;
    if (slot < 0 || slot >= ML_NSLOTS) return;
    s = &m->slot[slot];
    if (!s->rec) return;
    s->rec = 0;
    span = m->clk - s->rec_clk;
    len = s->len_set ? s->len : ml_quantize(span);   /* predefined length kept, else quantized to the tempo */
    if (len == 0) len = ML_BAR64;
    /* events were recorded relative to rec start; rebase the NEW ones (from od_base on) to the
     * master clock so the loop starts exactly at rec start (perfect loop on the shared clock);
     * in overdub the older events are already rebased */
    base = (uint32_t)fm_floorf(s->rec_clk);
    {
        uint16_t nn = 0, np = 0, ob = s->od ? s->od_base : 0;
        for (i = 0; i < s->n_note; i++) {
            if (s->note[i].t >= len) continue;
            if ((uint16_t)i >= ob)
                s->note[i].t = (uint16_t)((s->note[i].t + base) % len);
            s->note[nn++] = s->note[i];
        }
        for (i = 0; i < s->n_par; i++) {
            if (s->par[i].t >= len) continue;
            s->par[i].t = (uint16_t)((s->par[i].t + base) % len);
            s->par[np++] = s->par[i];
        }
        s->n_note = nn;
        s->n_par = np;
    }
    s->len = len;
    s->len_set = 1;
    s->pos = ml_fmod64(m->clk, len);
    s->last_pos = s->pos;
    s->dir = 1;
    s->n_act = 0;
}

/* deep edit: the loop split in 16 steps; step k covers [k*len/16, (k+1)*len/16) */
static uint16_t ml_step_t(uint16_t len, int k) { return (uint16_t)(((uint32_t)k * len) / 16u); }

void ml_step_toggle(ml_t *m, int slot, int k, int note, int vel)
{
    ml_slot_t *s;
    uint16_t t;
    int i;
    if (slot < 0 || slot >= ML_NSLOTS || k < 0 || k >= 16) return;
    s = &m->slot[slot];
    if (s->len == 0) return;
    t = ml_step_t(s->len, k);
    for (i = 0; i < s->n_note; i++)
        if (s->note[i].t == t) {              /* already there: remove */
            s->note[i] = s->note[s->n_note - 1];
            s->n_note--;
            return;
        }
    if (s->n_note < ML_MAXEV) {
        s->note[s->n_note].t = t;
        s->note[s->n_note].note = (uint8_t)note;
        s->note[s->n_note].vel = (uint8_t)clampf((float)vel, 1.0f, 127.0f);
        s->note[s->n_note].dur = 4;
        s->n_note++;
    }
}

void ml_step_vel(ml_t *m, int slot, int k, int vel)
{
    ml_slot_t *s;
    uint16_t t;
    int i;
    if (slot < 0 || slot >= ML_NSLOTS || k < 0 || k >= 16) return;
    s = &m->slot[slot];
    if (s->len == 0) return;
    t = ml_step_t(s->len, k);
    for (i = 0; i < s->n_note; i++)
        if (s->note[i].t == t) {
            s->note[i].vel = (uint8_t)clampf((float)vel, 1.0f, 127.0f);
            return;
        }
}

void ml_erase(ml_t *m, int slot)
{
    ml_slot_t *s;
    if (slot < 0 || slot >= ML_NSLOTS) return;
    s = &m->slot[slot];
    s->n_note = 0;
    s->n_par = 0;
    s->on = 0;
    s->rec = 0;
    s->rs = 0;
    s->n_act = 0;
}

/* the recording slot, if the source class matches its sp.src (3 = ALL, 6 = ALL+MIDI IN;
 * cls 4 = MIDI in maps to src 5) */
static int ml_rec_slot(ml_t *m, int cls)
{
    int i, want = cls == 4 ? 5 : cls;
    for (i = 0; i < ML_NSLOTS; i++)
        if (m->slot[i].rec && (m->slot[i].sp.src == 3 || m->slot[i].sp.src == 6 || (int)m->slot[i].sp.src == want))
            return i;
    return -1;
}

void ml_note_in(ml_t *m, int note, int vel, int dur64, int cls)
{
    int r = ml_rec_slot(m, cls);
    if (r >= 0) {
        ml_slot_t *s = &m->slot[r];
        float tr = s->od ? m->clk : m->clk - s->rec_clk;    /* overdub: absolute, else relative to rec start */
        if (tr >= 0.0f && tr < (float)ML_MAXLEN && s->n_note < ML_MAXEV) {
            ml_note_t *e = &s->note[s->n_note++];
            e->t = (uint16_t)fm_floorf(tr) % ML_MAXLEN;
            e->note = (uint8_t)note;
            e->vel = (uint8_t)clampf((float)vel, 1.0f, 127.0f);
            e->dur = (uint8_t)clampf((float)dur64, 1.0f, 64.0f);
        }
    }
    /* the live gate is done by the engine (drain), not here: the callback is for the loop playback only */
}

void ml_note_off_in(ml_t *m, int note)
{
    int r = -1, i;
    for (i = 0; i < ML_NSLOTS; i++)
        if (m->slot[i].rec) { r = i; break; }
    if (r < 0) return;
    {
        ml_slot_t *s = &m->slot[r];
        float now64 = m->clk - s->rec_clk;
        for (i = s->n_note - 1; i >= 0; i--)
            if (s->note[i].note == (uint8_t)note && s->note[i].dur == 0) {
                int d = (int)(now64 - (float)s->note[i].t);
                if (d < 1) d = 1;
                if (d > ML_MAXLEN) d = ML_MAXLEN;
                s->note[i].dur = (uint8_t)d;
                break;
            }
    }
}

void ml_par_in(ml_t *m, int key, float v, int cls)
{
    int r = ml_rec_slot(m, cls);
    if (r >= 0) {
        ml_slot_t *s = &m->slot[r];
        float tr = s->od ? m->clk : m->clk - s->rec_clk;
        if (tr >= 0.0f && tr < (float)ML_MAXLEN && s->n_par < ML_MAXPEV) {
            ml_par_t *e = &s->par[s->n_par++];
            e->t = (uint16_t)fm_floorf(tr);
            e->key = (uint8_t)key;
            e->v = clampf(v, 0.0f, 1.0f);
        }
    }
    if (m->param) m->param(m->ctx, r, key, v);           /* applied live while recording */
}

/* G.711 µ-law (SP-1200-style companding): 8-bit storage, ~13-bit quality, half the RAM of int16 */
static uint8_t ml_ulaw_enc(float x)
{
    int sign, exp, mant, sample, mask;
    if (x < 0.0f) { sign = 0x80; x = -x; } else sign = 0;
    if (x > 1.0f) x = 1.0f;
    sample = (int)(x * 32768.0f) + 0x84;              /* bias */
    exp = 7; mask = 0x4000;
    while (exp > 0 && !(sample & mask)) { mask >>= 1; exp--; }
    mant = (sample >> (exp + 3)) & 0x0F;
    return (uint8_t)~(sign | (exp << 4) | mant);
}
static float ml_ulaw_dec(uint8_t u)
{
    int t, sample;
    u = (uint8_t)~u;
    t = ((u & 0x0F) << 3) + 0x84;
    t <<= (u >> 4) & 0x07;
    sample = (u & 0x80) ? (0x84 - t) : (t - 0x84);
    return (float)sample / 32768.0f;
}

/* resample (reloop): record the monitored bus into the ring.
 * fmt: 0 = int16, 1 = µ-law 8-bit (SP-1200 style, half RAM -> cap may reach 2*ML_RS_MAX);
 * khz: 0 = native ring rate, else the ring runs at khz kHz (32 = lighter on the FM-1) */
void ml_set_eco(ml_t *m, int on) { m->eco = on ? 1 : 0; }
int  ml_get_eco(const ml_t *m)   { return m->eco; }

void ml_rs_start(ml_t *m, int dst, uint16_t monmask, uint32_t cap, int fmt, int khz)
{
    uint32_t maxc;
    if (dst < 0 || dst >= ML_NSLOTS) return;
    if (m->eco) {   /* MODE ECO : bitrate du loop le plus bas — µ-law + paliers lo-fi, ring 32 kHz min, capa /2 */
        if (fmt < 2) fmt = 2;
        if (khz < 32) khz = 32;
    }
    m->rs_fmt = fmt == 2 ? 2 : (fmt ? 1 : 0);
    maxc = m->rs_fmt ? (uint32_t)ML_RS_MAX * 2u : (uint32_t)ML_RS_MAX;
    if (cap > maxc) cap = maxc;
    if (m->eco && cap > ML_RS_MAX) cap = ML_RS_MAX;
    if (cap < 512) cap = 512;
    m->rs_decim = khz > 0 ? m->sr / (khz * 1000.0f) : 1.0f;   /* ring rate = khz kHz (32 = 32 kHz ring) */
    if (m->rs_decim < 1.0f) m->rs_decim = 1.0f;
    m->rs_bits = 16;
    if (m->rs_decim < 1.0f) m->rs_decim = 1.0f;
    m->rs_rec = 1;
    m->rs_dst = dst;
    m->monmask = monmask;
    m->rs_cap = cap;
    m->rs_len = 0;
    m->rs_pos = 0;
    m->rs_ph = 0.0f;
    m->rs_rph = 0.0f;
    m->slot[dst].rec = 0;
    m->slot[dst].rs = 1;
    m->slot[dst].on = 1;
}

void ml_rs_feed(ml_t *m, const float *in, uint32_t n)
{
    uint32_t i;
    if (!m->rs_rec) return;
    for (i = 0; i < n; i++) {
        m->rs_ph += 1.0f;
        if (m->rs_ph >= m->rs_decim) {    /* decimate to the ring rate (32 kHz rings lighten the FM-1) */
            m->rs_ph -= m->rs_decim;
            if (m->rs_fmt)
                m->rs_ring.b[m->rs_pos] = ml_ulaw_enc(in[i]);
            else
                m->rs_ring.w[m->rs_pos] = (int16_t)clampf(in[i] * 16000.0f, -32000.0f, 32000.0f);
            if (++m->rs_pos >= m->rs_cap) m->rs_pos = 0;
            m->rs_len++;                  /* ring samples recorded (may wrap the ring) */
        }
    }
}

void ml_rs_stop(ml_t *m)
{
    ml_slot_t *s;
    float spu, rsr;                       /* samples per 1/64 bar, at the RING rate */
    if (!m->rs_rec) return;
    m->rs_rec = 0;
    s = &m->slot[m->rs_dst];
    rsr = m->rs_decim > 0.0f ? m->sr / m->rs_decim : m->sr;
    spu = rsr / (m->bpm * 16.0f / 60.0f);
    /* quantize the ring length to a whole number of 1/64 bars, like the event loops */
    {
        float units = m->rs_len / spu;
        uint16_t len = ml_quantize(units);
        if (len < 16) len = 16;
        m->rs_len = (uint32_t)((float)len * spu);
        if (m->rs_len > m->rs_cap) m->rs_len = m->rs_cap;
        if (m->rs_len < 64) m->rs_len = 64;
        s->len = len;
        s->len_set = 1;
    }
    s->pos = ml_fmod64(m->clk, s->len);
    s->last_pos = s->pos;
    m->rs_pos = 0;
    m->rs_rph = 0.0f;
    if (m->rs_fmt == 2) {              /* tiered lo-fi (ZuluFx-style): 0-7 s light, 7-14 s medium, 14+ s strong */
        float secs = (float)m->rs_len / rsr;
        m->rs_bits = secs < 7.0f ? 14 : secs < 14.0f ? 11 : 8;
    }
}

void ml_rs_render(ml_t *m, float *outL, float *outR, uint32_t n)
{
    ml_slot_t *s;
    uint32_t i;
    int any = 0;
    for (i = 0; i < ML_NSLOTS; i++)
        if (m->slot[i].on && m->slot[i].rs && m->rs_len >= 64) { any = 1; break; }
    if (!any || !m->playing) return;
    s = &m->slot[m->rs_dst];
    if (!s->on || !s->rs || m->rs_len < 64) return;
    {
        float g = (float)s->sp.vol / 255.0f * 0.9f * m->loop_gain;
        for (i = 0; i < n; i++) {
            uint32_t p0 = m->rs_pos, p1;
            float x0, x1, x;
            if (m->rs_fmt) { x0 = ml_ulaw_dec(m->rs_ring.b[p0]); }
            else x0 = (float)m->rs_ring.w[p0] / 16000.0f;
            m->rs_rph += m->rs_decim;
            while (m->rs_rph >= 1.0f) {
                m->rs_rph -= 1.0f;
                if (++m->rs_pos >= m->rs_len) m->rs_pos = 0;
            }
            p1 = m->rs_pos + 1;
            if (p1 >= m->rs_len) p1 = 0;
            if (m->rs_fmt) x1 = ml_ulaw_dec(m->rs_ring.b[p1]); else x1 = (float)m->rs_ring.w[p1] / 16000.0f;
            if (m->rs_bits < 16) {                     /* tiered quantization (lo-fi), soft-clipped at the strong tier */
                float lv = (float)(1 << (m->rs_bits - 1));
                x0 = fm_floorf(x0 * lv + 0.5f) / lv;
                x1 = fm_floorf(x1 * lv + 0.5f) / lv;
                if (m->rs_bits <= 8) { x0 = x0 * 1.2f; if (x0 > 1.0f) x0 = 1.0f; if (x0 < -1.0f) x0 = -1.0f;
                                       x1 = x1 * 1.2f; if (x1 > 1.0f) x1 = 1.0f; if (x1 < -1.0f) x1 = -1.0f; }
            }
            x = (x0 + (x1 - x0) * m->rs_rph) * g;      /* linear interp at the ring rate */
            outL[i] += x;
            outR[i] += x;
        }
    }
}

/* fire the events whose effective time lies on the segment p0 -> p1 (dir +1 fwd, -1 rev),
 * with wrap, in loop coords; sp.pos shifts the start point, sp.mut jitters vel/params */
static void ml_fire(ml_t *m, int si, ml_slot_t *s, float p0, float p1, int dir)
{
    uint16_t len = s->len;
    float d = dir > 0 ? ml_fmodf(p1 - p0, (float)len) : ml_fmodf(p0 - p1, (float)len);
    int i;
    if (d <= 0.0f) return;
    for (i = 0; i < s->n_note; i++) {
        float te = ml_fmodf((float)s->note[i].t - (float)s->sp.pos, (float)len);
        float e = dir > 0 ? ml_fmodf(te - p0, (float)len) : ml_fmodf(p0 - te, (float)len);
        int vel;
        if (!(e < d)) continue;              /* [0,d): each event fires exactly once per pass */
        vel = s->note[i].vel;
        if (s->sp.mut && (int)(ml_rnd(m) & 255u) < s->sp.mut)
            vel = (int)clampf((float)vel + (float)((int)(ml_rnd(m) & 63u) - 32), 1.0f, 127.0f);
        if (m->note_on) m->note_on(m->ctx, si, s->note[i].note, vel);
        if (s->n_act < 8) {
            uint32_t dd = s->note[i].dur ? s->note[i].dur : len;
            s->act[s->n_act].note = s->note[i].note;
            s->act[s->n_act].off = (uint16_t)(((uint32_t)te + dd) % len);
            s->n_act++;
        }
    }
    for (i = 0; i < s->n_par; i++) {
        float te = ml_fmodf((float)s->par[i].t - (float)s->sp.pos, (float)len);
        float e = dir > 0 ? ml_fmodf(te - p0, (float)len) : ml_fmodf(p0 - te, (float)len);
        float v = s->par[i].v;
        if (!(e < d)) continue;
        if (s->sp.mut && (int)(ml_rnd(m) & 255u) < s->sp.mut)
            v = clampf(v + ((float)(ml_rnd(m) & 63u) - 32.0f) / 213.0f, 0.0f, 1.0f);
        if (m->param) m->param(m->ctx, si, s->par[i].key, v);
    }
    /* note-offs */
    {
        int a = 0;
        while (a < s->n_act) {
            float te = ml_fmodf((float)s->act[a].off - (float)s->sp.pos, (float)len);
            float e = dir > 0 ? ml_fmodf(te - p0, (float)len) : ml_fmodf(p0 - te, (float)len);
            if (e < d) {
                if (m->note_off) m->note_off(m->ctx, si, s->act[a].note);
                s->act[a] = s->act[s->n_act - 1];
                s->n_act--;
            } else a++;
        }
    }
}

static void ml_rand_slot(ml_t *m, int slot);   /* défini plus bas (pack SEQ+ RAND) */

void ml_render(ml_t *m, uint32_t n)
{
    float rate, step;
    int i;
    if (!m->playing) return;
    rate = m->bpm * 16.0f / 60.0f / m->sr;    /* 1/64 bar per sample (16 per beat) */
    step = (float)n * rate;
    m->clk += step;
    for (i = 0; i < ML_NSLOTS; i++) {
        ml_slot_t *s = &m->slot[i];
        float p0, p1;
        if (!s->on || s->len == 0 || s->rec || s->rs) continue;   /* a recording/resample slot doesn't replay itself */
        if (m->rs_rec && !((m->monmask >> i) & 1u)) continue;    /* resample: only the monitored slots sound */
        p0 = s->last_pos;
        if (s->sp.mode == 1) {                                 /* reverse */
            p1 = p0 - step;
            if (p1 < 0.0f) { ml_fire(m, i, s, p0, 0.0f, -1); p1 += (float)s->len; ml_fire(m, i, s, (float)s->len, p1, -1); }
            else ml_fire(m, i, s, p0, p1, -1);
        } else if (s->sp.mode == 2) {                          /* ping-pong */
            if (s->dir > 0) {
                p1 = p0 + step;
                if (p1 >= (float)s->len) {
                    ml_fire(m, i, s, p0, (float)s->len, 1);
                    s->dir = -1;
                    p1 = 2.0f * (float)s->len - p1;
                    ml_fire(m, i, s, (float)s->len, p1, -1);
                } else ml_fire(m, i, s, p0, p1, 1);
            } else {
                p1 = p0 - step;
                if (p1 <= 0.0f) {
                    ml_fire(m, i, s, p0, 0.0f, -1);
                    s->dir = 1;
                    p1 = -p1;
                    ml_fire(m, i, s, 0.0f, p1, 1);
                } else ml_fire(m, i, s, p0, p1, -1);
            }
        } else if (s->sp.mode == 3) {                          /* RANDOM (pack SEQ+) : saut aléatoire au wrap */
            p1 = p0 + step;
            if (p1 >= (float)s->len) {
                ml_fire(m, i, s, p0, (float)s->len, 1);
                p1 = (float)(ml_rnd(m) % (uint32_t)s->len);    /* nouvelle position aléatoire */
                if (m->randp > 0.0f && (float)(ml_rnd(m) & 0xFFFF) / 65536.0f < m->randp)
                    ml_rand_slot(m, i);                        /* RAND au wrap : re-génère le slot */
                ml_fire(m, i, s, 0.0f, p1, 1);
            } else ml_fire(m, i, s, p0, p1, 1);
        } else {                                               /* normal */
            p1 = p0 + step;
            if (p1 >= (float)s->len) {
                ml_fire(m, i, s, p0, (float)s->len, 1);
                p1 -= (float)s->len;
                if (m->randp > 0.0f && (float)(ml_rnd(m) & 0xFFFF) / 65536.0f < m->randp)
                    ml_rand_slot(m, i);                        /* RAND au wrap (mode normal aussi) */
                ml_fire(m, i, s, 0.0f, p1, 1);
            }
            else ml_fire(m, i, s, p0, p1, 1);
        }
        s->pos = p1;
        s->last_pos = p1;
    }
}

/* ---- pack SEQ+ : RAND au wrap + mutation live ENV/LFO/SEQ (mêmes sémantiques que le
 * seq acid EV_MUTATE et le drum) ------------------------------------------------ */

void ml_set_randp(ml_t *m, float p)
{
    m->randp = p < 0.0f ? 0.0f : p > 1.0f ? 1.0f : p;
}

/* re-génère le contenu d'un slot (notes aléatoires dans la longueur conservée) */
static void ml_rand_slot(ml_t *m, int slot)
{
    ml_slot_t *s;
    int i, n;
    if (slot < 0 || slot >= ML_NSLOTS) return;
    s = &m->slot[slot];
    if (s->len == 0 || s->rec || s->rs) return;
    n = 1 + (int)(ml_rnd(m) % (uint32_t)(s->len / 8 + 1));      /* ~len/8 notes */
    if (n > ML_MAXEV) n = ML_MAXEV;
    s->n_note = (uint16_t)n;
    for (i = 0; i < n; i++) {
        s->note[i].t = (uint16_t)(ml_rnd(m) % s->len);
        s->note[i].note = (uint8_t)(36 + (ml_rnd(m) % 37));      /* 36..72 */
        s->note[i].vel = (uint8_t)(50 + (ml_rnd(m) % 78));
        s->note[i].dur = (uint8_t)(1 + (ml_rnd(m) % (uint32_t)(s->len / 4 + 1)));
    }
    s->n_par = 0;
}

/* mutation live d'un slot : layer 1=TIME (dur/timing) 2=PITCH (transpose) 3=ARTIC (vélocité) */
void ml_mutate(ml_t *m, int slot, int layer)
{
    ml_slot_t *s;
    int i;
    if (slot < 0 || slot >= ML_NSLOTS) return;
    s = &m->slot[slot];
    switch (layer) {
    case 1:                                                     /* TIME (ENV seul) */
        for (i = 0; i < s->n_note; i++) {
            int d = (int)s->note[i].dur + ((int)(ml_rnd(m) % 9) - 4);
            s->note[i].dur = (uint8_t)(d < 1 ? 1 : d);
            int t = (int)s->note[i].t + ((int)(ml_rnd(m) % 5) - 2);
            if (t < 0) t = 0;
            if (s->len && t >= (int)s->len) t = (int)s->len - 1;
            s->note[i].t = (uint16_t)t;
        }
        break;
    case 2: {                                                   /* PITCH (LFO seul) : transpose le slot */
        int dp = ((int)(ml_rnd(m) % 5) - 2);                    /* -2..+2 demi-tons */
        int np = s->sp.pitch + dp;
        if (np < -12) np = -12;
        if (np > 12) np = 12;
        s->sp.pitch = (int8_t)np;
        for (i = 0; i < s->n_note; i++) {
            int nn = (int)s->note[i].note + dp;
            if (nn < 0) nn = 0;
            if (nn > 127) nn = 127;
            s->note[i].note = (uint8_t)nn;
        }
        break;
    }
    default:                                                    /* ARTIC (ENV+LFO) : vélocités */
        for (i = 0; i < s->n_note; i++)
            s->note[i].vel = (uint8_t)(40 + (ml_rnd(m) % 88));
        break;
    }
}

void ml_clear_leader(ml_t *m, int grp)
{
    if (grp > 0 && grp <= 6) m->lead[grp] = -1;
}
