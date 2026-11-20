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
