package com.zulutek.drums

/**
 * Pont JNI vers le moteur natif zuludrums (drum_engine.c : 8 pads analogiques + seq 16 steps).
 * Le flux Oboe vit côté natif ; ici on ne fait que start/stop + setters.
 */
object DrumsAudio {
    const val NPADS = 8
    const val NSTEPS = 16
    val PAD_NAMES = arrayOf("KICK", "SNARE", "HAT", "HAT-O", "CLAP", "TOM", "CONGA", "COWBELL")

    @Volatile private var handle: Long = 0L

    init {
        System.loadLibrary("zuludrums")
    }

    fun create(sampleRate: Int = 48000) {
        if (handle == 0L) handle = nativeCreate(sampleRate)
    }

    fun destroy() {
        if (handle != 0L) { nativeStop(handle); nativeDestroy(handle); handle = 0L }
    }

    fun start(): Boolean = handle != 0L && nativeStart(handle)
    fun stop() { if (handle != 0L) nativeStop(handle) }

    fun hit(pad: Int) { if (handle != 0L) nativeHit(handle, pad) }
    fun setStep(pad: Int, step: Int, on: Boolean) { if (handle != 0L) nativeSetStep(handle, pad, step, if (on) 1 else 0) }
    fun getStep(pad: Int, step: Int): Boolean = handle != 0L && nativeGetStep(handle, pad, step) == 1
    fun clear(pad: Int) { if (handle != 0L) nativeClear(handle, pad) }
    fun rand(pad: Int, density: Float, seed: Int) { if (handle != 0L) nativeRand(handle, pad, density, seed) }
    fun setBpm(bpm: Float) { if (handle != 0L) nativeSetBpm(handle, bpm) }
    fun setSwing(s: Float) { if (handle != 0L) nativeSetSwing(handle, s) }
    fun setLevel(pad: Int, lvl: Float) { if (handle != 0L) nativeSetLevel(handle, pad, lvl) }
    fun setMaster(m: Float) { if (handle != 0L) nativeSetMaster(handle, m) }
    fun curStep(): Int = if (handle != 0L) nativeGetCurStep(handle) else -1
    fun reset() { if (handle != 0L) nativeReset(handle) }

    private external fun nativeCreate(sampleRate: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeHit(handle: Long, pad: Int)
    private external fun nativeSetStep(handle: Long, pad: Int, step: Int, on: Int)
    private external fun nativeGetStep(handle: Long, pad: Int, step: Int): Int
    private external fun nativeClear(handle: Long, pad: Int)
    private external fun nativeRand(handle: Long, pad: Int, density: Float, seed: Int)
    private external fun nativeSetBpm(handle: Long, bpm: Float)
    private external fun nativeSetSwing(handle: Long, s: Float)
    private external fun nativeSetLevel(handle: Long, pad: Int, lvl: Float)
    private external fun nativeSetMaster(handle: Long, m: Float)
    private external fun nativeGetCurStep(handle: Long): Int
    private external fun nativeReset(handle: Long)
}
