/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Javier (Animatek)
 *
 * The ATEK303's bass engines, chosen by AT_ENGINE: 0 is the ATEK303 voice itself (atek_voice.c),
 * 1..4 are here, ported from the ZuluFx app's generators (FlyingZulu/fm1-Zulu, GPL-3.0): PLUK
 * (Karplus-Strong, its t==18), FM (its melodic FM, t==14), SUB (a sine sub-octave under the note)
 * and 8BIT (cheaptune: pulse duties, triangle and noise, quantized to 3 bits, hard envelopes).
 * Same gate / note / accent / slide semantics and the same AT_ params as the ATEK303 voice, so the
 * pages' knobs mean the same per engine. Single precision, no libm (fastmath.h). */
#pragma once
#include <stdint.h>
#include "atek_voice.h"                      /* the AT_ param ids */

#define AT_NENGINES 5
extern const char *const AT_ENGINE_NAME[AT_NENGINES];
int at_engine_index(float v);               /* AT_ENGINE's 0..1 to its entry */

typedef struct {
    float sr;
    float param[AT_NPARAMS];
    int gate, held, accent, slide;
    float env;                              /* amp envelope, 0..1 */
    float freq, freq_t;                     /* Hz; freq slews to freq_t on slide */
    float ph, ph_m;                         /* oscillator / FM modulator phases */
    float ks[2304];                         /* PLUK's string, up to sr/20 Hz */
    uint32_t ks_n, ks_pos;
    float ks_last;
    uint32_t rng;
} bass_t;

void bass_init(bass_t *b, float sr);
void bass_set(bass_t *b, int param, float value);   /* 0..1, the AT_ ids */
void bass_gate(bass_t *b, int gate, int note, int accent, int slide);
void bass_panic(bass_t *b);
void bass_render(bass_t *b, float *out, uint32_t n);