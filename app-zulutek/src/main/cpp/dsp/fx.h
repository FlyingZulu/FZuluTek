/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Javier (Animatek)
 *
 * ATEK303's effects for the FM-1, the classic acid chain, mono in, stereo out:
 *
 *   DRIVE   a pedal after the 303: OVERDRIVE (soft, asymmetric), RAT (op-amp gain into hard diode
 *           clipping, with its pre-emphasis), FUZZ (huge gain, lopsided) or MOJO (airwindows' soft
 *           curve, MIT, plus a bitcrusher: BIT the depth, HOLD the sample hold, see LICENSE-airwindows.txt).
 *           Antiderivative anti-aliasing on the clipper, so a square through it doesn't fizz;
 *           TONE and LEVEL after (on MOJO, the page's knobs 3 and 4 are BIT and HOLD).
 *   MOD     CHORUS (two voices of a modulated 7 ms delay, left and right a quarter turn apart) or
 *           FLANGER (a short modulated delay fed back).
 *   DELAY   ping-pong bucket-brigade (BBD): TIME is free, 10..~600 ms, not synced to the tempo; the
 *           delay steps in 4096 "stages" like an MN3005 and the clock hisses a little; each repeat
 *           darker (a low-pass in the loop) and without mud (a high-pass); TIME glides like tape.
 *   REVERB  Dattorro's plate (J. Dattorro, "Effect Design Part 1", JAES 1997), with its modulated
 *           tank and a pre-delay.
 *
 * All of it single precision, no libm (fastmath.h). The buffers live in the device's .pool RAM.
 * fx_process() runs in the audio interrupt; fx_set() and fx_tempo() between blocks. */
#pragma once
#include <stdint.h>

enum {
    FX_DRIVE_TYPE, FX_DRIVE, FX_TONE, FX_LEVEL,          /* type: 0 off, 1 overdrive, 2 rat, 3 fuzz, 4 mojo */
    FX_MOD_TYPE, FX_MOD_RATE, FX_MOD_DEPTH, FX_MOD_MIX,  /* type: 0 chorus, 1 flanger */
    FX_DLY_TIME, FX_DLY_FEEDBACK, FX_DLY_TONE, FX_DLY_MIX,   /* BBD time: 0..1, free, fx_dly_ms() */
    FX_REV_DECAY, FX_REV_DAMP, FX_REV_PRE, FX_REV_MIX,
    FX_CRUSH,                                  /* MOJO only: bitcrusher bit depth (appended: the saved ids do not move) */
    FX_HOLD,                                   /* MOJO only: bitcrusher sample hold, in samples */
    FX_NPARAMS
};
#define FX_DRIVE_TYPES 5
#define FX_MOD_TYPES 2
extern const char *const FX_DRIVE_NAME[FX_DRIVE_TYPES];
extern const char *const FX_MOD_NAME[FX_MOD_TYPES];

/* PUNCH-IN (PoW!): REC + FX arms the 16 white keys as momentary FX slots; REC + a key opens the slot's
 * settings page (K1 the type, K2/K3 its settings, K4 the mix). The ring below always records the master;
 * while a slot is held, its type reads it back its own way. */
#define PN_NTYPES 15
extern const char *const PUNCH_TYPE_NAME[PN_NTYPES];   /* OFF, REPEAT, 1/16 REP, 1/8 DOWN/UP/SHRINK, BUILD, STRETCH,
                                                          * ECHO, REVERSE, STOP, SCRATCH, LPF, CRUSH, GATE — the ZuluFx
                                                          * names (TEMPS/TEXTURE) plus Amenity's white-key rolls */
extern const uint8_t PUNCH_USES[PN_NTYPES];   /* which settings each type uses: bit0 K2 (a), bit1 K3 (b);
                                                 * a knob the type does not use is free (the page gives it
                                                 * the dry/wet) */
void fx_punch(int on, int type, float a, float b, float mix);   /* between blocks */
void fx_punch_off_tail(float t);   /* circuit bend release: keep the effect in, fading over t seconds */

void fx_init(float sr);
void fx_set(int param, float value);   /* knobs 0..1; the types as their index */
float fx_get(int param);               /* readback (UI) — appended, saved ids do not move */
void fx_set_eco(int on);               /* MODE ECO: crush 96+tanh en entrée, BBD mono, swirl off, pre-delay /2 */
int  fx_get_eco(void);
void fx_tempo(float sixteenth_s);      /* kept for the API; the delay is free now, no sync */
float fx_dly_ms(float t01);            /* BBD TIME 0..1 -> free ms, 10..~600 */
float fx_delay_seconds(void);          /* the delay time it plays (clamped to the buffer) */
void fx_process(const float *in, float *l, float *r, uint32_t n);
