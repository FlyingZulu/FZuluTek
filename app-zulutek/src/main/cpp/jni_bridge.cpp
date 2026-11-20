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
    if (slot >= 0 && slot < ZT_NPRESETS) return env->NewStringUTF(ztek_preset_name(slot));
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
