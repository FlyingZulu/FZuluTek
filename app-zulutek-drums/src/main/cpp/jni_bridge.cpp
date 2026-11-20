/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * JNI bridge entre l'UI Kotlin (com.zulutek.drums.DrumsAudio) et drum_engine (C99).
 * Le flux Oboe vit côté natif (oboe::oboe via prefab), comme dans app-zulutek :
 * l'UI ne fait que start/stop + setters d'état. Moteur mono-instance (comme le FM-1). */
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
    float lbuf[DR_MAX_BLOCK];

    drum_engine_s(float sr) { drum_init(&d, sr); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override
    {
        if (stream != out.get()) return oboe::DataCallbackResult::Stop;
        float *o = static_cast<float *>(audioData);
        uint32_t done = 0;
        while (done < (uint32_t)numFrames) {
            uint32_t blk = (uint32_t)numFrames - done;
            if (blk > DR_MAX_BLOCK) blk = DR_MAX_BLOCK;
            memset(lbuf, 0, blk * sizeof(float));
            drum_render(&d, lbuf, (int)blk);
            for (uint32_t i = 0; i < blk; i++) {   /* mono -> stéréo */
                o[(done + i) * 2] = lbuf[i];
                o[(done + i) * 2 + 1] = lbuf[i];
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

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetStep(JNIEnv *, jclass, jlong h, jint pad, jint step, jint on)
{
    drum_set_pat(&dre(NULL, h)->d, pad, step, on);
}

DR_JNI jint JNICALL Java_com_zulutek_drums_DrumsAudio_nativeGetStep(JNIEnv *, jclass, jlong h, jint pad, jint step)
{
    return drum_get_pat(&dre(NULL, h)->d, pad, step);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeClear(JNIEnv *, jclass, jlong h, jint pad)
{
    drum_clear_pat(&dre(NULL, h)->d, pad);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeRand(JNIEnv *, jclass, jlong h, jint pad, jfloat density, jint seed)
{
    drum_rand_pat(&dre(NULL, h)->d, pad, density, (unsigned)seed);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetBpm(JNIEnv *, jclass, jlong h, jfloat bpm)
{
    drum_set_bpm(&dre(NULL, h)->d, bpm);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetSwing(JNIEnv *, jclass, jlong h, jfloat s)
{
    drum_set_swing(&dre(NULL, h)->d, s);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetLevel(JNIEnv *, jclass, jlong h, jint pad, jfloat lvl)
{
    drum_set_level(&dre(NULL, h)->d, pad, lvl);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeSetMaster(JNIEnv *, jclass, jlong h, jfloat m)
{
    drum_set_master(&dre(NULL, h)->d, m);
}

DR_JNI jint JNICALL Java_com_zulutek_drums_DrumsAudio_nativeGetCurStep(JNIEnv *, jclass, jlong h)
{
    return drum_get_step(&dre(NULL, h)->d);
}

DR_JNI void JNICALL Java_com_zulutek_drums_DrumsAudio_nativeReset(JNIEnv *, jclass, jlong h)
{
    drum_reset(&dre(NULL, h)->d);
}
