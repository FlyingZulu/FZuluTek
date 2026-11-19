/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * The zuluTek bass engines, see bass_engines.h. Engines 1..4 are ported from the ZuluFx app's
 * generators (t==18 PLUK, t==14 FM, plus SUB and 8BIT), re-expressed on the ATEK303 voice's
 * AT_ knobs so the FM-1 pages mean the same thing for every engine:
 *   TUNING    fine tune, +/-5 semitones around the note (the ZuluFx generators' fxP(.,0) - 0.5)
 *   CUTOFF    tone: PLUK's string loop filter, FM/SUB/8BIT's output low-pass (and 8BIT's duty)
 *   RESONANCE FM's modulation index (8BIT: unused)
 *   ENVMOD    PLUK's excitation brightness, FM's extra index, 8BIT: unused
 *   DECAY     envelope decay time (PLUK's string damping), 40 ms .. 3 s
 *   ACCENT    accent gain on the attack
 *   WAVE      8BIT's waveform (pulse / triangle / noise); the 303 voice uses it for its osc list
 * Single precision, no libm (fastmath.h). */
#include "bass_engines.h"
#include "fastmath.h"
#include <string.h>

enum { ENG_303 = 0, ENG_PLUK, ENG_FM, ENG_SUB, ENG_8BIT };

const char *const AT_ENGINE_NAME[AT_NENGINES] = {"303", "PLUK", "FM", "SUB", "8BIT"};

int at_engine_index(float v)
{
    int i = (int)(v * (float)(AT_NENGINES - 1) + 0.5f);
    if (i < 0) i = 0;
    if (i >= AT_NENGINES) i = AT_NENGINES - 1;
    return i;
}

static inline float clampf(float x, float lo, float hi) { return x < lo ? lo : x > hi ? hi : x; }

static inline float note_hz(int note, float tune01)
{
    /* +/-5 semitones, like the ZuluFx generators' powf(2, p - 0.5) */
    return 440.0f * fm_exp2f(((float)note - 69.0f + (tune01 - 0.5f) * 10.0f) / 12.0f);
}

void bass_init(bass_t *b, float sr)
{
    memset(b, 0, sizeof *b);
    b->sr = sr;
    b->engine = ENG_PLUK;
    b->param[AT_TUNING] = 0.5f;
    b->param[AT_CUTOFF] = 0.7f;
    b->param[AT_RESONANCE] = 0.25f;
    b->param[AT_ENVMOD] = 0.5f;
    b->param[AT_DECAY] = 0.5f;
    b->param[AT_ACCENT] = 0.5f;
    b->param[AT_WAVE] = 0.0f;
    b->param[AT_ENGINE] = 0.0f;
    b->freq = 110.0f;
    b->freq_t = 110.0f;
    b->rng = 0xBA55EEDu & 0x7fffffffu;
    if (!b->rng) b->rng = 0x12345678u;
}

void bass_set(bass_t *b, int param, float value)
{
    if (param == AT_ENGINE) {
        b->engine = at_engine_index(value);
        return;
    }
    if (param < 0 || param >= AT_NPARAMS) return;
    b->param[param] = clampf(value, 0.0f, 1.0f);
}

void bass_gate(bass_t *b, int gate, int note, int accent, int slide)
{
    if (gate && !b->gate) {
        b->freq_t = note_hz(note, b->param[AT_TUNING]);
        if (slide && b->held) {
            /* slide into the new note: freq slews to freq_t in bass_render */
        } else {
            b->freq = b->freq_t;
        }
        b->gate = 1;
        b->held = 1;
        b->accent = accent ? 1 : 0;
        if (b->engine == ENG_PLUK) {
            /* re-excite the string: noise burst, brightness from ENVMOD (the ZuluFx t==18) */
            uint32_t n = b->ks_n;
            float amp = 0.4f + 0.6f * b->param[AT_ENVMOD];
            uint32_t i;
            for (i = 0; i < n; i++) {
                b->rng = b->rng * 1103515245u + 12345u;
                b->ks[i] = ((float)(b->rng >> 16) / 32768.0f - 1.0f) * amp;
            }
            b->ks_pos = 0;
            b->ks_last = 0.0f;
        }
    }
    if (!gate) {
        b->gate = 0;
        if (!slide) b->held = 0;    /* slide held: the note keeps ringing (303 SLIDE) */
    }
}

void bass_panic(bass_t *b)
{
    float sr = b->sr;
    int engine = b->engine;
    float param[AT_NPARAMS + 1];
    memcpy(param, b->param, sizeof param);
    memset(b, 0, sizeof *b);
    b->sr = sr;
    b->engine = engine;
    memcpy(b->param, param, sizeof param);
    b->freq = 110.0f;
    b->freq_t = 110.0f;
    b->rng = 0x12345678u;
}

/* the amp envelope: 5 ms attack, exponential decay from DECAY (40 ms..3 s), 30 ms release,
 * accent boosts the attack peak (the 303's ACCENT) */
static float bass_env(bass_t *b)
{
    float fsr = 1.0f / b->sr;
    float atk = 1.0f / (0.005f * b->sr);
    float tau = (40.0f + 2960.0f * b->param[AT_DECAY]) / 1000.0f;
    float rel = 1.0f / (0.030f * b->sr);
    if (b->gate) {
        if (b->env < 1.0f) {
            b->env += atk;
            if (b->env > 1.0f) b->env = 1.0f;
        } else {
            b->env -= b->env * (fsr / tau);
            if (b->env < 0.0f) b->env = 0.0f;
        }
    } else {
        b->env -= rel;
        if (b->env < 0.0f) b->env = 0.0f;
    }
    (void)fsr;
    if (b->accent && b->env > 0.5f) {
        float g = 1.0f + 0.6f * b->param[AT_ACCENT];
        return b->env * g > 1.5f ? 1.5f : b->env * g;
    }
    return b->env;
}

static float bass_lp(bass_t *b, float in, float *y, float cutoff01)
{
    /* one-pole low-pass, 120 Hz .. 12 kHz (the Kaossilator pad's Y axis range) */
    float fc = 120.0f * fm_powf(100.0f, cutoff01);
    float c = 1.0f - fm_expf(-FM_TWO_PI * fc / b->sr);
    *y += c * (in - *y);
    return *y;
}

void bass_render(bass_t *b, float *out, uint32_t n)
{
    float fsr = 1.0f / b->sr;
    uint32_t i;
    float env;

    /* slide: freq slews to freq_t, tau 120 ms (the module's slideTauIdx 1) */
    {
        float c = 1.0f - fm_expf(-1.0f / (0.120f * b->sr));
        if (b->freq != b->freq_t) b->freq += (b->freq_t - b->freq) * c;
    }

    env = bass_env(b);
    if (env <= 0.0005f && !b->gate) {
        for (i = 0; i < n; i++) out[i] = 0.0f;
        b->accent = 0;
        return;
    }

    switch (b->engine) {
    case ENG_PLUK: {
        /* Karplus-Strong, the ZuluFx t==18: damp from DECAY, loop filter from CUTOFF */
        float damp = 0.9f + 0.099f * b->param[AT_DECAY];
        float cf = 0.1f + 0.8f * b->param[AT_CUTOFF];
        uint32_t len = (uint32_t)(b->sr / (b->freq < 20.0f ? 20.0f : b->freq));
        if (len < 8) len = 8;
        if (len > 2304) len = 2304;
        if (b->ks_n != len) {           /* the string follows the pitch (re-init on change) */
            uint32_t k;
            float amp = 0.4f + 0.6f * b->param[AT_ENVMOD];
            for (k = 0; k < len; k++) {
                b->rng = b->rng * 1103515245u + 12345u;
                b->ks[k] = ((float)(b->rng >> 16) / 32768.0f - 1.0f) * amp;
            }
            b->ks_n = len;
            b->ks_pos = 0;
            b->ks_last = 0.0f;
        }
        for (i = 0; i < n; i++) {
            float y = b->ks[b->ks_pos];
            float lp = (1.0f - cf * 0.5f) * y + (cf * 0.5f) * b->ks_last;
            b->ks[b->ks_pos] = damp * lp;
            b->ks_last = y;
            b->ks_pos++;
            if (b->ks_pos >= b->ks_n) b->ks_pos = 0;
            out[i] = y * env * 0.9f;
        }
        break;
    }
    case ENG_FM: {
        /* the ZuluFx t==14: carrier + 6x modulator, index from RESONANCE (+ENVMOD) */
        float idx = 0.5f + b->param[AT_RESONANCE] * 7.5f + b->param[AT_ENVMOD] * 2.0f;
        for (i = 0; i < n; i++) {
            float s;
            b->ph += b->freq * fsr;
            if (b->ph >= 1.0f) b->ph -= 1.0f;
            b->ph_m += b->freq * 6.0f * fsr;
            if (b->ph_m >= 1.0f) b->ph_m -= 1.0f;
            s = fm_sin_turns(b->ph + idx * fm_sin_turns(b->ph_m)) * 0.45f * env;
            out[i] = bass_lp(b, s, &b->lp_y, b->param[AT_CUTOFF]);
        }
        break;
    }
    case ENG_SUB: {
        /* sine + sub-octave sine */
        for (i = 0; i < n; i++) {
            float s;
            b->ph += b->freq * fsr;
            if (b->ph >= 1.0f) b->ph -= 1.0f;
            b->ph_m += b->freq * 0.5f * fsr;
            if (b->ph_m >= 1.0f) b->ph_m -= 1.0f;
            s = (0.6f * fm_sin_turns(b->ph) + 0.7f * fm_sin_turns(b->ph_m)) * env;
            out[i] = bass_lp(b, s, &b->lp_y, b->param[AT_CUTOFF]);
        }
        break;
    }
    default: {  /* ENG_8BIT: pulse / triangle / noise, 3-bit quantized, hard envelope */
        float w = b->param[AT_WAVE];
        float duty = 0.1f + 0.8f * b->param[AT_CUTOFF];
        for (i = 0; i < n; i++) {
            float s;
            b->ph += b->freq * fsr;
            if (b->ph >= 1.0f) b->ph -= 1.0f;
            if (w < 0.33f) s = (b->ph < duty ? 1.0f : -1.0f) * 0.4f;
            else if (w < 0.66f) s = (1.0f - 4.0f * fm_fabsf(b->ph - 0.5f)) * 0.5f;
            else {
                b->rng = b->rng * 1103515245u + 12345u;
                s = ((float)(b->rng >> 16) / 32768.0f - 1.0f) * 0.5f;
            }
            s = fm_floorf(s * 3.5f + 0.5f) / 3.5f;      /* 3-bit quantize */
            out[i] = bass_lp(b, s * env, &b->lp_y, 0.9f);
        }
        break;
    }
    }
}
