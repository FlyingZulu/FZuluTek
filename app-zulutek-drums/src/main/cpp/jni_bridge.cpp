/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * JNI bridge entre l'UI Kotlin (com.zulutek.drums.DrumsAudio) et drum_engine (C99, moteurs
 * hybrides façon PĒRKONS HD-01). Le flux Oboe vit côté natif (prefab), comme app-zulutek :
 * l'UI ne fait que start/stop + setters. Moteur mono-instance. */
#include <jni.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <memory>
#include <oboe/Oboe.h>
#include "drum_engine.h"

#define DR_JNI extern "C" JNIEXPORT

#define DR_MAX_BLOCK 256

struct drum_engine_s : public oboe::AudioStreamDataCallback {
    drum_t d;
    std::shared_ptr<oboe::AudioStream> out;
    float lbufL[DR_MAX_BLOCK], lbufR[DR_MAX_BLOCK];

    drum_engine_s(float sr) { drum_init(&d, sr); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override
    {
        if (stream != out.get()) return oboe::DataCallbackResult::Stop;
        float *o = static_cast<float *>(audioData);
        uint32_t done = 0;
        while (done < (uint32_t)numFrames) {
            uint32_t blk = (uint32_t)numFrames - done;
            if (blk > DR_MAX_BLOCK) blk = DR_MAX_BLOCK;
            memset(lbufL, 0, blk * sizeof(float));
            memset(lbufR, 0, blk * sizeof(float));
            drum_render(&d, lbufL, lbufR, (int)blk);   /* stéréo (pan par voie) */
            for (uint32_t i = 0; i < blk; i++) {
                o[(done + i) * 2] = lbufL[i];
                o[(done + i) * 2 + 1] = lbufR[i];
            }
            done += blk;
        }
        return oboe::DataCallbackResult::Continue;
    }

    int start() {
        if (out) return 1;
        oboe::AudioStreamBuilder b;
        b.setDirection(oboe::Direction::Output)
         ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
         ->setSharingMode(oboe::SharingMode::Exclusive)
         ->setSampleRate((int32_t)d.sr)
         ->setChannelCount(2)
         ->setFormat(oboe::AudioFormat::Float)
         ->setDataCallback(this);
        oboe::Result r = b.openStream(out);
        if (r != oboe::Result::OK) {
            b.setSharingMode(oboe::SharingMode::Shared);
            r = b.openStream(out);
        }
        if (r != oboe::Result::OK) return 0;
        return out->requestStart() == oboe::Result::OK ? 1 : 0;
    }

    void stop() {
        if (out) { out->stop(); out->close(); out.reset(); }
    }
};

static inline drum_engine_s *dre(JNIEnv *, jlong h) { return reinterpret_cast<drum_engine_s *>(h); }

DR_JNI jlong JNICALL Java_com_zulutek_drums_DrumsAudio_nativeCreate(JNIEnv *, jclass, jint sampleRate)
{
    return (jlong)new drum_engine_s((float)sampleRate);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeDestroy(JNIEnv *, jclass, jlong h)
{
    drum_engine_s *e = dre(NULL, h);
    if (e) { e->stop(); delete e; }
}

DR_JNI jboolean JNICALL Java_com_zulutek_drums_DrumsAudio_nativeStart(JNIEnv *, jclass, jlong h)
{
    return dre(NULL, h)->start() ? JNI_TRUE : JNI_FALSE;
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeStop(JNIEnv *, jclass, jlong h)
{
    dre(NULL, h)->stop();
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeHit(JNIEnv *, jclass, jlong h, jint pad)
{
    drum_hit(&dre(NULL, h)->d, pad);
}

/* step complet : on, vel, cond, slide */
DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetStep(JNIEnv *, jclass, jlong h,
    jint pad, jint step, jint on, jint vel, jint cond, jint slide)
{
    drum_set_step(&dre(NULL, h)->d, pad, step, on, vel, cond, slide);
}

/* step packé en jlong : bit0 on, bits 8..15 vel, bits 16..23 cond, bits 24..31 slide+8 */
DR_JNI jlong JNICALL Java_com_zulutek_drums_DrumsAudio_nativeGetStep(JNIEnv *, jclass, jlong h,
    jint pad, jint step)
{
    int on = 0, vel = 0, cond = 0, slide = 0;
    drum_get_step(&dre(NULL, h)->d, pad, step, &on, &vel, &cond, &slide);
    return (jlong)((on & 1) | ((vel & 0xFF) << 8) | ((cond & 0xFF) << 16) | (((slide + 8) & 0xFF) << 24));
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeClear(JNIEnv *, jclass, jlong h, jint pad)
{
    drum_clear_pat(&dre(NULL, h)->d, pad);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeRand(JNIEnv *, jclass, jlong h, jint pad, jfloat density, jint seed)
{
    drum_rand_pat(&dre(NULL, h)->d, pad, density, (unsigned)seed);
}

/* 4 params shapables du pad (TUNE/DECAY/TONE/X) + hybridation HYB */
DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetParam(JNIEnv *, jclass, jlong h, jint pad, jint k, jfloat v)
{
    drum_set_param(&dre(NULL, h)->d, pad, k, v);
}

DR_JNI jfloat JNICALL Java_com_zulutek_drums_DrumsAudio_nativeGetParam(JNIEnv *, jclass, jlong h, jint pad, jint k)
{
    return drum_get_param(&dre(NULL, h)->d, pad, k);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetHyb(JNIEnv *, jclass, jlong h, jint pad, jfloat v)
{
    drum_set_hyb(&dre(NULL, h)->d, pad, v);
}

DR_JNI jfloat JNICALL Java_com_zulutek_drums_DrumsAudio_nativeGetHyb(JNIEnv *, jclass, jlong h, jint pad)
{
    return drum_get_hyb(&dre(NULL, h)->d, pad);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetKit(JNIEnv *, jclass, jlong h, jint kit)
{
    drum_set_kit(&dre(NULL, h)->d, kit);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetPadMode(JNIEnv *, jclass, jlong h, jint pad, jint mode)
{
    drum_set_pad_mode(&dre(NULL, h)->d, pad, mode);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetPadMut(JNIEnv *, jclass, jlong h, jint pad, jint mut)
{
    drum_set_pad_mut(&dre(NULL, h)->d, pad, mut);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetLevel(JNIEnv *, jclass, jlong h, jint pad, jfloat lvl)
{
    drum_set_level(&dre(NULL, h)->d, pad, lvl);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetBpm(JNIEnv *, jclass, jlong h, jfloat bpm)
{
    drum_set_bpm(&dre(NULL, h)->d, bpm);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetSwing(JNIEnv *, jclass, jlong h, jfloat s)
{
    drum_set_swing(&dre(NULL, h)->d, s);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetMaster(JNIEnv *, jclass, jlong h, jfloat m)
{
    drum_set_master(&dre(NULL, h)->d, m);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativePlay(JNIEnv *, jclass, jlong h, jint on)
{
    drum_play(&dre(NULL, h)->d, on);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetRec(JNIEnv *, jclass, jlong h, jint on)
{
    drum_set_rec(&dre(NULL, h)->d, on);
}

DR_JNI jint JNICALL Java_com_zulutek_drums_DrumsAudio_nativeGetCurStep(JNIEnv *, jclass, jlong h)
{
    return drum_get_cur_step(&dre(NULL, h)->d);
}

DR_JNI jfloat JNICALL Java_com_zulutek_drums_DrumsAudio_nativeGetClk64(JNIEnv *, jclass, jlong h)
{
    return drum_get_clk64(&dre(NULL, h)->d);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeReset(JNIEnv *, jclass, jlong h)
{
    drum_reset(&dre(NULL, h)->d);
}

/* compresseur de bus (AUV3 Fac Smash-like) : set on/thr/ratio/makeup, get packé en jlong */
DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetComp(JNIEnv *, jclass, jlong h,
    jint on, jfloat thr, jfloat ratio, jfloat makeup)
{
    drum_set_comp(&dre(NULL, h)->d, on, thr, ratio, makeup);
}

DR_JNI jlong JNICALL Java_com_zulutek_drums_DrumsAudio_nativeGetComp(JNIEnv *, jclass, jlong h)
{
    int on = 0; float thr = 0, ratio = 1, makeup = 1;
    drum_get_comp(&dre(NULL, h)->d, &on, &thr, &ratio, &makeup);
    return (jlong)((on & 1) | (((int)(thr * 100) & 0xFF) << 8) |
                   (((int)(ratio * 10) & 0xFF) << 16) | (((int)(makeup * 100) & 0xFF) << 24));
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetDelay(JNIEnv *, jclass, jlong h,
    jfloat time, jfloat fb, jfloat wet)
{
    drum_set_delay(&dre(NULL, h)->d, time, fb, wet);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetRev(JNIEnv *, jclass, jlong h, jfloat wet)
{
    drum_set_rev(&dre(NULL, h)->d, wet);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetGmut(JNIEnv *, jclass, jlong h, jint mut)
{
    drum_set_gmut(&dre(NULL, h)->d, mut);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetGlitch(JNIEnv *, jclass, jlong h, jfloat g)
{
    drum_set_glitch(&dre(NULL, h)->d, g);
}

/* ENV / LFO / SEQ (pack SEQ+, mêmes sémantiques que synthés/basse) */
DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeMutate(JNIEnv *, jclass, jlong h, jint layer)
{
    drum_mutate(&dre(NULL, h)->d, layer);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSeqMode(JNIEnv *, jclass, jlong h, jint mode)
{
    drum_seq_mode(&dre(NULL, h)->d, mode);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetRandp(JNIEnv *, jclass, jlong h, jfloat p)
{
    drum_set_randp(&dre(NULL, h)->d, p);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetLfo(JNIEnv *, jclass, jlong h, jint i,
    jfloat rate, jint wave, jfloat depth, jint dst)
{
    drum_set_lfo(&dre(NULL, h)->d, i, rate, wave, depth, dst);
}
