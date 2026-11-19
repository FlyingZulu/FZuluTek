/* fastmath.h shim for Zulu on Android: atek_voice.c (vendored from animatek/FM1-Animatek,
 * GPL-3.0-or-later) was written for the M-VAVE FM-1, where there is no libm, and calls
 * fm1-base's fast approximations (include/fastmath.h, MPL-2.0). Here we have libm, so the
 * same API maps straight to it; the semantics are the ones fm1-base documents:
 *   fm_sin_pi(x) = sin(x) for x in [-pi, pi], fm_sin_turns(ph) = sin(2*pi*ph),
 *   fm_exp2f(x) = 2^x, fm_expf = expf, fm_logf = logf, fm_powf = powf, ... */
#pragma once
#include <math.h>

#define FM_PI 3.14159265358979f
#define FM_TWO_PI 6.28318530717959f
#define FM_LN2 0.693147180559945f

static inline float fm_fabsf(float x) { return fabsf(x); }
static inline float fm_minf(float a, float b) { return a < b ? a : b; }
static inline float fm_maxf(float a, float b) { return a > b ? a : b; }
static inline float fm_floorf(float x) { return floorf(x); }
static inline float fm_exp2f(float x) { return exp2f(x); }
static inline float fm_expf(float x) { return expf(x); }
static inline float fm_logf(float x) { return logf(x); }
static inline float fm_log2f(float x) { return log2f(x); }
static inline float fm_powf(float x, float y) { return powf(x, y); }
static inline float fm_tanhf(float x) { return tanhf(x); }
static inline float fm_sin_pi(float x) { return sinf(x); }
static inline float fm_sinf(float x) { return sinf(x); }
static inline float fm_cosf(float x) { return cosf(x); }
static inline float fm_tanf(float x) { return tanf(x); }
static inline float fm_sin_turns(float ph) { return sinf(FM_TWO_PI * ph); }
static inline float fm_sqrtf(float x) { return sqrtf(x); }
