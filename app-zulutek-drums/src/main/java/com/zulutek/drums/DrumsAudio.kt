package com.zulutek.drums

/**
 * Pont JNI vers drum_engine v2 (drum machine hybride façon PĒRKONS HD-01 :
 * chaque pad = moteur custom + moteur basse/lead, crossfade HYB ; seq façon MIDI loop).
 * Le flux Oboe vit côté natif ; ici start/stop + setters.
 */
object DrumsAudio {
    const val NPADS = 8
    const val NSTEPS = 16
    val PAD_NAMES = arrayOf("KICK", "SNARE", "HAT", "CLAP", "TOM", "ELIXIR", "COWBELL", "FM")
    // Règle : P0 = PITCH (hauteur) sur TOUTES les voies, comme 808/909/PĒRKONS.
    // 12 knobs/voie = 3 pages de 4. Règle : P0 = PITCH sur toutes les voies.
    // page 3 (P8..P11) : PAN, SEND DELAY, SEND REVERB, SEND COMP.
    val PARAM_NAMES = arrayOf(
        arrayOf("PITCH", "DECAY", "DROP", "DRIVE", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"),
        arrayOf("PITCH", "DECAY", "NOISE", "DRIVE", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"),
        arrayOf("PITCH", "DECAY", "METAL", "-", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"),
        arrayOf("PITCH", "DECAY", "BURST", "SPC", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"),
        arrayOf("PITCH", "DECAY", "DROP", "-", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"),
        arrayOf("PITCH", "DECAY", "TYPE", "NOISE", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"),
        arrayOf("PITCH", "DECAY", "-", "-", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"),
        arrayOf("PITCH", "DECAY", "RATIO", "INDEX", "HYB", "B.WAVE", "B.RES", "LEVEL", "PAN", "D.SEND", "R.SEND", "C.SEND"))
    val ELIXIR_NAMES = arrayOf("BELL", "CHIME", "KALIMBA", "GLASS", "PING")
    const val NPAR = 12
    val COND_NAMES = arrayOf("1:1", "1:2", "1:3", "1:4", "1:8", "FILL")
    val MODE_NAMES = arrayOf("NORM", "REV", "PING", "RND")
    val KIT_NAMES = arrayOf("ZULU", "808", "909")
    const val COND_ALWAYS = 0
    const val COND_FILL = 5

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

    /** step complet : on, vel 0..127, cond, slide -8..+8 */
    fun setStep(pad: Int, step: Int, on: Boolean, vel: Int = 100, cond: Int = COND_ALWAYS, slide: Int = 0) {
        if (handle != 0L) nativeSetStep(handle, pad, step, if (on) 1 else 0, vel, cond, slide)
    }

    /** lit un step -> intArrayOf(on, vel, cond, slide), décodé du jlong packé */
    fun getStep(pad: Int, step: Int): IntArray {
        if (handle == 0L) return intArrayOf(0, 0, 0, 0)
        val v = nativeGetStep(handle, pad, step).toInt()
        return intArrayOf(v and 1, (v shr 8) and 0xFF, (v shr 16) and 0xFF, ((v shr 24) and 0xFF) - 8)
    }

    fun clear(pad: Int) { if (handle != 0L) nativeClear(handle, pad) }
    fun rand(pad: Int, density: Float, seed: Int) { if (handle != 0L) nativeRand(handle, pad, density, seed) }

    fun setParam(pad: Int, k: Int, v: Float) { if (handle != 0L) nativeSetParam(handle, pad, k, v) }
    fun getParam(pad: Int, k: Int): Float = if (handle != 0L) nativeGetParam(handle, pad, k) else 0f
    fun setHyb(pad: Int, h: Float) { if (handle != 0L) nativeSetHyb(handle, pad, h) }
    fun getHyb(pad: Int): Float = if (handle != 0L) nativeGetHyb(handle, pad) else 0f
    fun setKit(kit: Int) { if (handle != 0L) nativeSetKit(handle, kit) }
    fun setPadMode(pad: Int, mode: Int) { if (handle != 0L) nativeSetPadMode(handle, pad, mode) }
    fun setPadMut(pad: Int, mut: Int) { if (handle != 0L) nativeSetPadMut(handle, pad, mut) }
    fun setLevel(pad: Int, lvl: Float) { if (handle != 0L) nativeSetLevel(handle, pad, lvl) }
    fun setBpm(bpm: Float) { if (handle != 0L) nativeSetBpm(handle, bpm) }
    fun setSwing(s: Float) { if (handle != 0L) nativeSetSwing(handle, s) }
    fun setMaster(m: Float) { if (handle != 0L) nativeSetMaster(handle, m) }
    fun play(on: Boolean) { if (handle != 0L) nativePlay(handle, if (on) 1 else 0) }
    fun setRec(on: Boolean) { if (handle != 0L) nativeSetRec(handle, if (on) 1 else 0) }
    fun curStep(): Int = if (handle != 0L) nativeGetCurStep(handle) else -1
    fun clk64(): Float = if (handle != 0L) nativeGetClk64(handle) else 0f
    fun reset() { if (handle != 0L) nativeReset(handle) }

    fun setDelay(time: Float, fb: Float, wet: Float) { if (handle != 0L) nativeSetDelay(handle, time, fb, wet) }
    fun setRev(wet: Float) { if (handle != 0L) nativeSetRev(handle, wet) }
    fun setGmut(mut: Int) { if (handle != 0L) nativeSetGmut(handle, mut) }        // mutation globale (ZuluFx)
    fun setGlitch(g: Float) { if (handle != 0L) nativeSetGlitch(handle, g) }     // glitch/chaos global (labo CHAOS)
    // ENV / LFO / SEQ (pack SEQ+) : mutate 1=TIME(ENV) 2=PITCH(LFO) 3=ARTIC(ENV+LFO)
    fun mutate(layer: Int) { if (handle != 0L) nativeMutate(handle, layer) }
    fun seqMode(mode: Int) { if (handle != 0L) nativeSeqMode(handle, mode) }     // 0 NORM 1 REV 2 PING 3 RANDOM
    fun setRandp(p: Float) { if (handle != 0L) nativeSetRandp(handle, p) }
    fun setLfo(i: Int, rate: Float, wave: Int, depth: Float, dst: Int) {
        if (handle != 0L) nativeSetLfo(handle, i, rate, wave, depth, dst)
    }
    val LFO_DST_NAMES = arrayOf("PITCH", "DECAY", "PAN", "D.SEND", "R.SEND", "GLITCH", "COMP")
    val LFO_WAVE_NAMES = arrayOf("SIN", "TRI", "SQ", "SAW", "S&H", "DRUNK")
    const val SEQ_MODE_N = 4
    val SEQ_MODE_NAMES = arrayOf("NORM", "REV", "PING", "RANDOM")

    /** compresseur de bus (AUV3 Fac Smash-like) ; ratio 20 = SMASH radical (limiter+distorsion) */
    fun setComp(on: Boolean, thr: Float, ratio: Float, makeup: Float) {
        if (handle != 0L) nativeSetComp(handle, if (on) 1 else 0, thr, ratio, makeup)
    }

    /** lit le comp -> intArrayOf(on, thr*100, ratio*10, makeup*100) */
    fun getComp(): IntArray {
        if (handle == 0L) return intArrayOf(0, 0, 0, 0)
        val v = nativeGetComp(handle).toInt()
        return intArrayOf(v and 1, (v shr 8) and 0xFF, (v shr 16) and 0xFF, (v shr 24) and 0xFF)
    }

    private external fun nativeCreate(sampleRate: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeHit(handle: Long, pad: Int)
    private external fun nativeSetStep(handle: Long, pad: Int, step: Int, on: Int, vel: Int, cond: Int, slide: Int)
    private external fun nativeGetStep(handle: Long, pad: Int, step: Int): Long
    private external fun nativeClear(handle: Long, pad: Int)
    private external fun nativeRand(handle: Long, pad: Int, density: Float, seed: Int)
    private external fun nativeSetParam(handle: Long, pad: Int, k: Int, v: Float)
    private external fun nativeGetParam(handle: Long, pad: Int, k: Int): Float
    private external fun nativeSetHyb(handle: Long, pad: Int, v: Float)
    private external fun nativeGetHyb(handle: Long, pad: Int): Float
    private external fun nativeSetKit(handle: Long, kit: Int)
    private external fun nativeSetPadMode(handle: Long, pad: Int, mode: Int)
    private external fun nativeSetPadMut(handle: Long, pad: Int, mut: Int)
    private external fun nativeSetLevel(handle: Long, pad: Int, lvl: Float)
    private external fun nativeSetBpm(handle: Long, bpm: Float)
    private external fun nativeSetSwing(handle: Long, s: Float)
    private external fun nativeSetMaster(handle: Long, m: Float)
    private external fun nativePlay(handle: Long, on: Int)
    private external fun nativeSetRec(handle: Long, on: Int)
    private external fun nativeGetCurStep(handle: Long): Int
    private external fun nativeGetClk64(handle: Long): Float
    private external fun nativeReset(handle: Long)
    private external fun nativeSetComp(handle: Long, on: Int, thr: Float, ratio: Float, makeup: Float)
    private external fun nativeGetComp(handle: Long): Long
    private external fun nativeSetDelay(handle: Long, time: Float, fb: Float, wet: Float)
    private external fun nativeSetRev(handle: Long, wet: Float)
    private external fun nativeSetGmut(handle: Long, mut: Int)
    private external fun nativeSetGlitch(handle: Long, g: Float)
    private external fun nativeMutate(handle: Long, layer: Int)
    private external fun nativeSeqMode(handle: Long, mode: Int)
    private external fun nativeSetRandp(handle: Long, p: Float)
    private external fun nativeSetLfo(handle: Long, i: Int, rate: Float, wave: Int, depth: Float, dst: Int)
}
