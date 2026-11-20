/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * JNI bridge between the Kotlin UI (com.zulutek.preview.ZtekAudio) and ztek_engine.
 * As in the app module, the Oboe stream lives in the native engine (oboe::oboe via prefab):
 * the UI only starts/stops it and sets control state. All control calls are plain state
 * setters, safe to call from the UI thread (the engine is single-instance, as on the FM-1). */
#include <jni.h>
#include <stddef.h>
#include <stdint.h>
#include "ztek_engine.h"

#define ZTEK_JNI extern "C" JNIEXPORT

static inline ztek_t *zte(JNIEnv *, jlong h) { return reinterpret_cast<ztek_t *>(h); }

ZTEK_JNI jlong JNICALL Java_com_zulutek_preview_ZtekAudio_nativeCreate(JNIEnv *, jclass, jint sampleRate)
{
    return (jlong)ztek_create((float)sampleRate);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDestroy(JNIEnv *, jclass, jlong h)
{
    ztek_destroy(zte(NULL, h));
}

ZTEK_JNI jboolean JNICALL Java_com_zulutek_preview_ZtekAudio_nativeStart(JNIEnv *, jclass, jlong h)
{
    return ztek_start(zte(NULL, h)) ? JNI_TRUE : JNI_FALSE;
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeStop(JNIEnv *, jclass, jlong h)
{
    ztek_stop(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeNoteOn(JNIEnv *, jclass, jlong h,
    jint note, jint vel, jint accent, jint slide)
{
    ztek_note_on(zte(NULL, h), note, vel, accent, slide);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeNoteOff(JNIEnv *, jclass, jlong h, jint note)
{
    ztek_note_off(zte(NULL, h), note);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeAllNotesOff(JNIEnv *, jclass, jlong h)
{
    ztek_all_notes_off(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSetParam(JNIEnv *, jclass, jlong h, jint p, jfloat v)
{
    ztek_set_param(zte(NULL, h), p, v);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSetEngine(JNIEnv *, jclass, jlong h, jint e)
{
    ztek_set_engine(zte(NULL, h), e);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSetBpm(JNIEnv *, jclass, jlong h, jfloat bpm)
{
    ztek_set_bpm(zte(NULL, h), bpm);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSetRoot(JNIEnv *, jclass, jlong h, jint root)
{
    ztek_set_root(zte(NULL, h), root);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSetScale(JNIEnv *, jclass, jlong h, jint s)
{
    ztek_set_scale(zte(NULL, h), s);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxSet(JNIEnv *, jclass, jlong h, jint p, jfloat v)
{
    ztek_fx_set(zte(NULL, h), p, v);
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxGet(JNIEnv *, jclass, jlong h, jint p)
{
    return ztek_fx_get(zte(NULL, h), p);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSetEco(JNIEnv *, jclass, jlong h, jint on)
{
    ztek_set_eco(zte(NULL, h), on);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeGetEco(JNIEnv *, jclass, jlong h)
{
    return ztek_get_eco(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSetLuxe(JNIEnv *, jclass, jlong h, jint on)
{
    ztek_set_luxe(zte(NULL, h), on);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeGetLuxe(JNIEnv *, jclass, jlong h)
{
    return ztek_get_luxe(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativePunch(JNIEnv *, jclass, jlong h,
    jint on, jint type, jfloat a, jfloat b, jfloat mix)
{
    ztek_punch(zte(NULL, h), on, type, a, b, mix);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativePunchTail(JNIEnv *, jclass, jlong h, jfloat t)
{
    ztek_punch_tail(zte(NULL, h), t);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopPlay(JNIEnv *, jclass, jlong h, jint on)
{
    ztek_loop_play(zte(NULL, h), on);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopToggle(JNIEnv *, jclass, jlong h, jint slot)
{
    ztek_loop_toggle(zte(NULL, h), slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopRecStart(JNIEnv *, jclass, jlong h, jint slot, jint od)
{
    ztek_loop_rec_start(zte(NULL, h), slot, od);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopRecStop(JNIEnv *, jclass, jlong h, jint slot)
{
    ztek_loop_rec_stop(zte(NULL, h), slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopErase(JNIEnv *, jclass, jlong h, jint slot)
{
    ztek_loop_erase(zte(NULL, h), slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopSetLen(JNIEnv *, jclass, jlong h, jint slot, jint len64)
{
    ztek_loop_set_len(zte(NULL, h), slot, len64);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopSetSp(JNIEnv *, jclass, jlong h,
    jint slot, jint idx, jfloat v)
{
    ztek_loop_set_sp(zte(NULL, h), slot, idx, v);
}

ZTEK_JNI jboolean JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopSlotOn(JNIEnv *, jclass, jlong h, jint slot)
{
    return ztek_loop_slot_on(zte(NULL, h), slot) ? JNI_TRUE : JNI_FALSE;
}

ZTEK_JNI jboolean JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopSlotRec(JNIEnv *, jclass, jlong h, jint slot)
{
    return ztek_loop_slot_rec(zte(NULL, h), slot) ? JNI_TRUE : JNI_FALSE;
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopGetSp(JNIEnv *, jclass, jlong h, jint slot, jint idx)
{
    return ztek_loop_get_sp(zte(NULL, h), slot, idx);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeAcidGenerate(JNIEnv *, jclass, jlong h, jint seed)
{
    ztek_acid_generate(zte(NULL, h), (uint32_t)seed);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeAcidPlay(JNIEnv *, jclass, jlong h, jint on)
{
    ztek_acid_play(zte(NULL, h), on);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeAcidKnob(JNIEnv *, jclass, jlong h, jint which, jfloat v)
{
    ztek_acid_knob(zte(NULL, h), which, v);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSmpRec(JNIEnv *, jclass, jlong h, jint on)
{
    ztek_smp_rec(zte(NULL, h), on);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSmpPlay(JNIEnv *, jclass, jlong h, jint on)
{
    ztek_smp_play(zte(NULL, h), on);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSmpSet(JNIEnv *, jclass, jlong h, jint which, jfloat v)
{
    ztek_smp_set(zte(NULL, h), which, v);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSmpLen(JNIEnv *, jclass, jlong h)
{
    return ztek_smp_len(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSmpPeaks(JNIEnv *env, jclass, jlong h, jfloatArray out)
{
    ztek_t *z = zte(NULL, h);
    jsize n = env->GetArrayLength(out);
    jfloat *el = env->GetFloatArrayElements(out, NULL);
    if (el) {
        ztek_smp_peaks(z, el, n / 2);
        env->ReleaseFloatArrayElements(out, el, 0);
    }
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeBend(JNIEnv *, jclass, jlong h,
    jint on, jfloat x, jfloat y, jint mode)
{
    ztek_bend(zte(NULL, h), on, x, y, mode);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSnapStore(JNIEnv *, jclass, jlong h, jint slot)
{
    ztek_snap_store(zte(NULL, h), slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSnapLoad(JNIEnv *, jclass, jlong h, jint slot)
{
    ztek_snap_load(zte(NULL, h), slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeUndo(JNIEnv *, jclass, jlong h)
{
    ztek_undo(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeRedo(JNIEnv *, jclass, jlong h)
{
    ztek_redo(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeKitStore(JNIEnv *, jclass, jlong h, jint slot)
{
    ztek_kit_store(zte(NULL, h), slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeKitLoad(JNIEnv *, jclass, jlong h, jint slot)
{
    ztek_kit_load(zte(NULL, h), slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeXySet(JNIEnv *, jclass, jlong h,
    jint on, jfloat x, jfloat y)
{
    ztek_xy_set(zte(NULL, h), on, x, y);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeXyTarget(JNIEnv *, jclass, jlong h, jint axis, jint target)
{
    ztek_xy_target(zte(NULL, h), axis, target);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeXyHold(JNIEnv *, jclass, jlong h, jint hold)
{
    ztek_xy_hold(zte(NULL, h), hold);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSiren(JNIEnv *, jclass, jlong h,
    jint on, jfloat lvl, jfloat t, jfloat fb)
{
    ztek_siren(zte(NULL, h), on, lvl, t, fb);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSirenWave(JNIEnv *, jclass, jlong h, jint w)
{
    ztek_siren_wave(zte(NULL, h), w);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSirenWaveGet(JNIEnv *, jclass, jlong h)
{
    return ztek_siren_wave_get(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSirenMix(JNIEnv *, jclass, jlong h, jfloat m)
{
    ztek_siren_mix(zte(NULL, h), m);
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSirenMixGet(JNIEnv *, jclass, jlong h)
{
    return ztek_siren_mix_get(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumPlay(JNIEnv *, jclass, jlong h, jint on)
{
    ztek_drum_play(zte(NULL, h), on);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumHit(JNIEnv *, jclass, jlong h, jint pad)
{
    ztek_drum_hit(zte(NULL, h), pad);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumPat(JNIEnv *, jclass, jlong h, jint pad, jint step, jint on)
{
    ztek_drum_pat(zte(NULL, h), pad, step, on);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumGetPat(JNIEnv *, jclass, jlong h, jint pad, jint step)
{
    return ztek_drum_get_pat(zte(NULL, h), pad, step);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumClear(JNIEnv *, jclass, jlong h, jint pad)
{
    ztek_drum_clear(zte(NULL, h), pad);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumRand(JNIEnv *, jclass, jlong h, jint pad, jfloat density, jint seed)
{
    ztek_drum_rand(zte(NULL, h), pad, density, (uint32_t)seed);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumSwing(JNIEnv *, jclass, jlong h, jfloat s)
{
    ztek_drum_swing(zte(NULL, h), s);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumLevel(JNIEnv *, jclass, jlong h, jint pad, jfloat lvl)
{
    ztek_drum_level(zte(NULL, h), pad, lvl);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumMaster(JNIEnv *, jclass, jlong h, jfloat m)
{
    ztek_drum_master(zte(NULL, h), m);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumStep(JNIEnv *, jclass, jlong h)
{
    return ztek_drum_step(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumSetChoke(JNIEnv *, jclass, jlong h, jint pad, jint grp)
{
    ztek_drum_set_choke(zte(NULL, h), pad, grp);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumGetChoke(JNIEnv *, jclass, jlong h, jint pad)
{
    return ztek_drum_get_choke(zte(NULL, h), pad);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumChokeMaster(JNIEnv *, jclass, jlong h, jint grp)
{
    return ztek_drum_choke_master(zte(NULL, h), grp);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumSetLeader(JNIEnv *, jclass, jlong h, jint on)
{
    ztek_drum_set_leader(zte(NULL, h), on);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumGetLeader(JNIEnv *, jclass, jlong h)
{
    return ztek_drum_get_leader(zte(NULL, h));
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumSetLeaderPad(JNIEnv *, jclass, jlong h, jint pad, jint on)
{
    ztek_drum_set_leader_pad(zte(NULL, h), pad, on);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeDrumClearLeader(JNIEnv *, jclass, jlong h, jint grp)
{
    ztek_drum_clear_leader(zte(NULL, h), grp);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopSetChoke(JNIEnv *, jclass, jlong h, jint slot, jint grp)
{
    ztek_loop_set_choke(zte(NULL, h), slot, grp);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopGetChoke(JNIEnv *, jclass, jlong h, jint slot)
{
    return ztek_loop_get_choke(zte(NULL, h), slot);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopChokeMaster(JNIEnv *, jclass, jlong h, jint grp)
{
    return ztek_loop_choke_master(zte(NULL, h), grp);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopSetLeader(JNIEnv *, jclass, jlong h, jint on)
{
    ztek_loop_set_leader(zte(NULL, h), on);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopGetLeader(JNIEnv *, jclass, jlong h)
{
    return ztek_loop_get_leader(zte(NULL, h));
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopSetLeaderSlot(JNIEnv *, jclass, jlong h, jint slot, jint on)
{
    ztek_loop_set_leader_slot(zte(NULL, h), slot, on);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopClearLeader(JNIEnv *, jclass, jlong h, jint grp)
{
    ztek_loop_clear_leader(zte(NULL, h), grp);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopRsStart(JNIEnv *, jclass, jlong h,
    jint dst, jint monmask, jint fmt, jint khz)
{
    ztek_loop_rs_start(zte(NULL, h), dst, (uint16_t)monmask, fmt, khz);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopRsStop(JNIEnv *, jclass, jlong h)
{
    ztek_loop_rs_stop(zte(NULL, h));
}

ZTEK_JNI jboolean JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopRsRec(JNIEnv *, jclass, jlong h)
{
    return ztek_loop_rs_rec(zte(NULL, h)) ? JNI_TRUE : JNI_FALSE;
}

/* sequencer advanced (double-tap SEQ): k = 5..9 TM LOCK / TM LEN / MUT / PLAY / RAND */
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSeqKnob(JNIEnv *, jclass, jlong h, jint k, jfloat v)
{
    ztek_seq_knob(zte(NULL, h), k, v);
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSeqKnobGet(JNIEnv *, jclass, jlong h, jint k)
{
    return ztek_seq_knob_get(zte(NULL, h), k);
}

/* live mutation (ENV/LFO buttons): layer = ACID_MUT_TIME/PITCH/ARTICULATION */
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSeqMutate(JNIEnv *, jclass, jlong h, jint layer)
{
    ztek_seq_mutate(zte(NULL, h), layer);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopMutate(JNIEnv *, jclass, jlong h, jint slot, jint layer)
{
    ztek_loop_mutate(zte(NULL, h), slot, layer);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopRandp(JNIEnv *, jclass, jlong h, jfloat p)
{
    ztek_loop_randp(zte(NULL, h), p);
}

/* arpégiateur (bouton ARP, page 13) */
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeArpOn(JNIEnv *, jclass, jlong h, jint on)
{
    ztek_arp_set_on(zte(NULL, h), on);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeArpGetOn(JNIEnv *, jclass, jlong h)
{
    return ztek_arp_get_on(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeArpKnob(JNIEnv *, jclass, jlong h, jint k, jfloat v)
{
    ztek_arp_knob(zte(NULL, h), k, v);
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeArpKnobGet(JNIEnv *, jclass, jlong h, jint k)
{
    return ztek_arp_knob_get(zte(NULL, h), k);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeArpHeld(JNIEnv *, jclass, jlong h)
{
    return ztek_arp_held(zte(NULL, h));
}

/* page MACRO (16 slots assignables) */
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMacroSet(JNIEnv *, jclass, jlong h, jint slot, jint dst, jint sub, jfloat val)
{
    ztek_macro_set(zte(NULL, h), slot, dst, sub, val);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMacroApply(JNIEnv *, jclass, jlong h, jint slot)
{
    ztek_macro_apply(zte(NULL, h), slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMacroLevel(JNIEnv *, jclass, jlong h, jint slot, jfloat v)
{
    ztek_macro_level(zte(NULL, h), slot, v);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMacroErase(JNIEnv *, jclass, jlong h, jint slot)
{
    ztek_macro_erase(zte(NULL, h), slot);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMacroUsed(JNIEnv *, jclass, jlong h, jint slot)
{
    return ztek_macro_used(zte(NULL, h), slot);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMacroDst(JNIEnv *, jclass, jlong h, jint slot)
{
    return ztek_macro_dst(zte(NULL, h), slot);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMacroSub(JNIEnv *, jclass, jlong h, jint slot)
{
    return ztek_macro_sub(zte(NULL, h), slot);
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMacroVal(JNIEnv *, jclass, jlong h, jint slot)
{
    return ztek_macro_val(zte(NULL, h), slot);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativePunchTypeGet(JNIEnv *, jclass, jlong h)
{
    return ztek_punch_type_get(zte(NULL, h));
}

/* modulation: 16 sources x 4 knobs (pages LFO/ENV) */
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeModKnob(JNIEnv *, jclass, jlong h, jint src, jint k, jfloat v)
{
    ztek_mod_knob(zte(NULL, h), src, k, v);
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeModKnobGet(JNIEnv *, jclass, jlong h, jint src, jint k)
{
    return ztek_mod_knob_get(zte(NULL, h), src, k);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLfoOpt(JNIEnv *, jclass, jlong h, jint which, jint on)
{
    ztek_lfo_opt(zte(NULL, h), which, on);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeEnvLoop(JNIEnv *, jclass, jlong h, jint e, jint on)
{
    ztek_env_loop(zte(NULL, h), e, on);
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSrcVal(JNIEnv *, jclass, jlong h, jint src)
{
    return ztek_src_val(zte(NULL, h), src);
}

/* ASSIGN matrix: dst 0..ZT_NDST-1, src 0..15 (255 = off), depth 0..1 */
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeAssign(JNIEnv *, jclass, jlong h,
    jint dst, jint src, jfloat depth)
{
    ztek_assign(zte(NULL, h), dst, src, depth);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeAssignSrc(JNIEnv *, jclass, jlong h, jint dst)
{
    return ztek_assign_src(zte(NULL, h), dst);
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeAssignDepth(JNIEnv *, jclass, jlong h, jint dst)
{
    return ztek_assign_depth(zte(NULL, h), dst);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativePreset(JNIEnv *, jclass, jlong h, jint i)
{
    ztek_preset(zte(NULL, h), i);
}

ZTEK_JNI jstring JNICALL Java_com_zulutek_preview_ZtekAudio_nativePresetName(JNIEnv *env, jclass, jint i)
{
    return env->NewStringUTF(ztek_preset_name(i));
}

/* user presets per part (0 bass, 1 lead, 2 drums) x 8 slots */
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativePresetSave(JNIEnv *, jclass, jlong h,
    jint part, jint slot)
{
    ztek_preset_save(zte(NULL, h), part, slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativePresetLoad(JNIEnv *, jclass, jlong h,
    jint part, jint slot)
{
    ztek_preset_load(zte(NULL, h), part, slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeBankLoad(JNIEnv *, jclass, jlong h,
    jint part, jint slot)
{
    ztek_bank_load(zte(NULL, h), part, slot);
}

ZTEK_JNI jstring JNICALL Java_com_zulutek_preview_ZtekAudio_nativeBankName(JNIEnv *env, jclass, jlong h,
    jint part, jint slot)
{
    ztek_t *z = zte(NULL, h);
    if (ztek_preset_used(z, part, slot)) return env->NewStringUTF(ztek_preset_slot_name(z, part, slot));
    if (slot >= 0 && slot < ZT_NPRESETS) return env->NewStringUTF(ztek_preset_name_part(part, slot));
    return env->NewStringUTF("");
}

ZTEK_JNI jboolean JNICALL Java_com_zulutek_preview_ZtekAudio_nativePresetUsed(JNIEnv *, jclass, jlong h,
    jint part, jint slot)
{
    return ztek_preset_used(zte(NULL, h), part, slot) ? JNI_TRUE : JNI_FALSE;
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativePresetClear(JNIEnv *, jclass, jlong h,
    jint part, jint slot)
{
    ztek_preset_clear(zte(NULL, h), part, slot);
}

ZTEK_JNI jbyteArray JNICALL Java_com_zulutek_preview_ZtekAudio_nativePresetExport(JNIEnv *env, jclass, jlong h,
    jint part, jint slot)
{
    uint8_t buf[2048];
    int n = ztek_preset_export(zte(NULL, h), part, slot, buf, (int)sizeof(buf));
    if (n <= 0) return NULL;
    jbyteArray a = env->NewByteArray(n);
    if (a) env->SetByteArrayRegion(a, 0, n, (const jbyte *)buf);
    return a;
}

ZTEK_JNI jboolean JNICALL Java_com_zulutek_preview_ZtekAudio_nativePresetImport(JNIEnv *env, jclass, jlong h,
    jint part, jint slot, jbyteArray data)
{
    if (!data) return JNI_FALSE;
    jsize n = env->GetArrayLength(data);
    if (n <= 0 || n > 2048) return JNI_FALSE;
    uint8_t buf[2048];
    env->GetByteArrayRegion(data, 0, n, (jbyte *)buf);
    return ztek_preset_import(zte(NULL, h), part, slot, buf, n) ? JNI_TRUE : JNI_FALSE;
}

ZTEK_JNI jstring JNICALL Java_com_zulutek_preview_ZtekAudio_nativePresetSlotName(JNIEnv *env, jclass, jlong h,
    jint part, jint slot)
{
    return env->NewStringUTF(ztek_preset_slot_name(zte(NULL, h), part, slot));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativePresetSlotSetName(JNIEnv *env, jclass, jlong h,
    jint part, jint slot, jstring name)
{
    const char *s = name ? env->GetStringUTFChars(name, NULL) : NULL;
    ztek_preset_set_name(zte(NULL, h), part, slot, s);
    if (s) env->ReleaseStringUTFChars(name, s);
}

/* ---- natives page MIX + baie de patch (branche looper, PR #1 @ 7d87264) ---- */
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMixSetVol(JNIEnv *, jclass, jlong h, jint slot, jfloat v)
{
    ztek_mix_set_vol(zte(NULL, h), slot, v);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMixSetAux(JNIEnv *, jclass, jlong h, jint slot, jint on)
{
    ztek_mix_set_aux(zte(NULL, h), slot, on);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMixSetGain(JNIEnv *, jclass, jlong h, jfloat g)
{
    ztek_mix_set_gain(zte(NULL, h), g);
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMixGetGain(JNIEnv *, jclass, jlong h)
{
    return ztek_mix_get_gain(zte(NULL, h));
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMixSetStrip(JNIEnv *, jclass, jlong h, jint strip, jint param, jfloat v)
{
    ztek_mix_set_strip(zte(NULL, h), strip, param, v);
}
ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMixGetStrip(JNIEnv *, jclass, jlong h, jint strip, jint param)
{
    return ztek_mix_get_strip(zte(NULL, h), strip, param);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMixSetMaster(JNIEnv *, jclass, jlong h, jint param, jfloat v)
{
    ztek_mix_set_master(zte(NULL, h), param, v);
}
ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMixGetMaster(JNIEnv *, jclass, jlong h, jint param)
{
    return ztek_mix_get_master(zte(NULL, h), param);
}

ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeMasterPeak(JNIEnv *, jclass, jlong h)
{
    return ztek_master_peak(zte(NULL, h));
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeLoopSlotAct(JNIEnv *, jclass, jlong h, jint slot)
{
    return ztek_loop_slot_act(zte(NULL, h), slot);
}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativePatchSet(JNIEnv *, jclass, jlong h, jint src, jint dst, jint on)
{
    ztek_patch_set(zte(NULL, h), src, dst, on);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativePatchGet(JNIEnv *, jclass, jlong h, jint src, jint dst)
{
    return ztek_patch_get(zte(NULL, h), src, dst);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativePatchCompat(JNIEnv *, jclass, jlong, jint src, jint dst)
{
    return ztek_patch_compat(src, dst);

}

ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeAssignSet(JNIEnv *, jclass, jlong h, jint src, jint dst, jint on)
{
    ztek_assign_set(zte(NULL, h), src, dst, on);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeAssignGet(JNIEnv *, jclass, jlong h, jint src, jint dst)
{
    return ztek_assign_get(zte(NULL, h), src, dst);
}

ZTEK_JNI jlongArray JNICALL Java_com_zulutek_preview_ZtekAudio_nativeAssignWords(JNIEnv *env, jclass, jlong h, jint src)
{
    uint64_t w[ZTEK_AWORDS];
    jlong out[ZTEK_AWORDS];
    int i;
    ztek_assign_words(zte(NULL, h), src, w);
    for (i = 0; i < ZTEK_AWORDS; i++) out[i] = (jlong)w[i];
    jlongArray arr = env->NewLongArray(ZTEK_AWORDS);
    if (arr) env->SetLongArrayRegion(arr, 0, ZTEK_AWORDS, out);
    return arr;
}

/* page FINE (APK 18 / firmware patch 0012) : réglages fins voix */
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeSetOption(JNIEnv *, jclass, jlong h, jint opt, jint v)
{
    ztek_set_option(zte(NULL, h), opt, v);
}

ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeOptionGet(JNIEnv *, jclass, jlong h, jint opt)
{
    return ztek_get_option(zte(NULL, h), opt);
}

/* ---- pedalboard FX (menu FX) ---- */
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxslotSet(JNIEnv *, jclass, jlong h,
    jint slot, jint type, jfloat p0, jfloat p1, jfloat p2, jfloat p3, jfloat mix)
{
    ztek_fxslot_set(zte(NULL, h), slot, type, p0, p1, p2, p3, mix);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxslotType(JNIEnv *, jclass, jlong h, jint slot)
{
    return ztek_fxslot_type(zte(NULL, h), slot);
}
ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxslotPar(JNIEnv *, jclass, jlong h, jint slot, jint k)
{
    return ztek_fxslot_par(zte(NULL, h), slot, k);
}
ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxslotMix(JNIEnv *, jclass, jlong h, jint slot)
{
    return ztek_fxslot_mix(zte(NULL, h), slot);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxchainOrder(JNIEnv *env, jclass, jlong h, jintArray order)
{
    jint tmp[ZTEK_NCHAIN] = {0,1,2,3};
    if (!order) { ztek_fxchain_order(zte(NULL, h), tmp); return; }
    jsize n = env->GetArrayLength(order);
    if (n > ZTEK_NCHAIN) n = ZTEK_NCHAIN;
    env->GetIntArrayRegion(order, 0, n, tmp);
    ztek_fxchain_order(zte(NULL, h), tmp);
}
ZTEK_JNI jintArray JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxchainOrderGet(JNIEnv *env, jclass, jlong h)
{
    int o[ZTEK_NCHAIN];
    ztek_fxchain_order_get(zte(NULL, h), o);
    jintArray arr = env->NewIntArray(ZTEK_NCHAIN);
    if (arr) { jint t[ZTEK_NCHAIN]; for (int i = 0; i < ZTEK_NCHAIN; i++) t[i] = o[i]; env->SetIntArrayRegion(arr, 0, ZTEK_NCHAIN, t); }
    return arr;
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxchainMix(JNIEnv *, jclass, jlong h, jint chain, jfloat m)
{
    ztek_fxchain_mix(zte(NULL, h), chain, m);
}
ZTEK_JNI jfloat JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxchainMixGet(JNIEnv *, jclass, jlong h, jint chain)
{
    return ztek_fxchain_mix_get(zte(NULL, h), chain);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxblockSave(JNIEnv *, jclass, jlong h, jint blk)
{
    ztek_fxblock_save(zte(NULL, h), blk);
}
ZTEK_JNI void JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxblockLoad(JNIEnv *, jclass, jlong h, jint blk)
{
    ztek_fxblock_load(zte(NULL, h), blk);
}
ZTEK_JNI jint JNICALL Java_com_zulutek_preview_ZtekAudio_nativeFxblockUsed(JNIEnv *, jclass, jlong h, jint blk)
{
    return ztek_fxblock_used(zte(NULL, h), blk);
}
