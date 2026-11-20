/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * The audio side of the ZuluTek preview APK. As in the app module, Oboe runs in the native
 * lib (oboe::oboe via prefab, stream owned by ztek_engine): here we only load libzulutek.so,
 * start/stop the stream and set control state through JNI. */
package com.zulutek.preview

object ZtekAudio {

    /* the AT_ param ids (dsp/atek_voice.h) */
    const val AT_TUNING = 0
    const val AT_CUTOFF = 1
    const val AT_RESONANCE = 2
    const val AT_ENVMOD = 3
    const val AT_DECAY = 4
    const val AT_ACCENT = 5
    const val AT_WAVE = 6
    const val AT_ENGINE = 7

    /* the FX_ param ids (dsp/fx.h) */
    const val FX_DRIVE_TYPE = 0
    const val FX_DRIVE = 1
    const val FX_DLY_MIX = 11
    const val FX_REV_MIX = 15

    /* the looper slot params (dsp/midi_looper.h) */
    const val ML_SP_PITCH = 0
    const val ML_SP_MODE = 2
    const val ML_SP_SRC = 4
    const val ML_SP_VOL = 6

    private var handle: Long = 0
    const val SAMPLE_RATE = 48000

    val running: Boolean get() = handle != 0L

    init {
        System.loadLibrary("zulutek")
    }

    fun start(): Boolean {
        if (handle != 0L) return true
        handle = nativeCreate(SAMPLE_RATE)
        if (handle == 0L) return false
        if (!nativeStart(handle)) {
            nativeDestroy(handle)
            handle = 0
            return false
        }
        nativeSetBpm(handle, 120f)
        return true
    }

    fun stop() {
        if (handle != 0L) {
            nativeStop(handle)
            nativeDestroy(handle)
            handle = 0
        }
    }

    fun noteOn(note: Int, vel: Int = 100, accent: Int = 0, slide: Int = 0) {
        if (handle != 0L) nativeNoteOn(handle, note, vel, accent, slide)
    }

    fun noteOff(note: Int) {
        if (handle != 0L) nativeNoteOff(handle, note)
    }

    fun allNotesOff() {
        if (handle != 0L) nativeAllNotesOff(handle)
    }

    fun setParam(p: Int, v: Float) {
        if (handle != 0L) nativeSetParam(handle, p, v)
    }

    fun setEngine(e: Int) {
        if (handle != 0L) nativeSetEngine(handle, e)
    }

    fun setBpm(bpm: Float) {
        if (handle != 0L) nativeSetBpm(handle, bpm)
    }

    fun setRoot(root: Int) {
        if (handle != 0L) nativeSetRoot(handle, root)
    }

    fun setScale(s: Int) {
        if (handle != 0L) nativeSetScale(handle, s)
    }

    fun fxSet(p: Int, v: Float) {
        if (handle != 0L) nativeFxSet(handle, p, v)
    }

    fun punch(on: Int, type: Int, a: Float, b: Float, mix: Float) {
        if (handle != 0L) nativePunch(handle, on, type, a, b, mix)
    }

    fun punchTail(t: Float) {
        if (handle != 0L) nativePunchTail(handle, t)
    }

    fun loopPlay(on: Int) { if (handle != 0L) nativeLoopPlay(handle, on) }
    fun loopToggle(slot: Int) { if (handle != 0L) nativeLoopToggle(handle, slot) }
    fun loopRecStart(slot: Int, od: Int) { if (handle != 0L) nativeLoopRecStart(handle, slot, od) }
    fun loopRecStop(slot: Int) { if (handle != 0L) nativeLoopRecStop(handle, slot) }
    fun loopErase(slot: Int) { if (handle != 0L) nativeLoopErase(handle, slot) }
    fun loopSetLen(slot: Int, len64: Int) { if (handle != 0L) nativeLoopSetLen(handle, slot, len64) }
    fun loopSetSp(slot: Int, idx: Int, v: Float) { if (handle != 0L) nativeLoopSetSp(handle, slot, idx, v) }
    fun loopSlotOn(slot: Int): Boolean = handle != 0L && nativeLoopSlotOn(handle, slot)
    fun loopSlotRec(slot: Int): Boolean = handle != 0L && nativeLoopSlotRec(handle, slot)
    fun loopGetSp(slot: Int, idx: Int): Float = if (handle != 0L) nativeLoopGetSp(handle, slot, idx) else 0f

    fun acidGenerate(seed: Int) { if (handle != 0L) nativeAcidGenerate(handle, seed) }
    fun acidPlay(on: Int) { if (handle != 0L) nativeAcidPlay(handle, on) }
    fun acidKnob(which: Int, v: Float) { if (handle != 0L) nativeAcidKnob(handle, which, v) }

    /* page SMP (sampleur audio-in) : which = 0 START 1 END 2 VOL 3 PITCH 4 MODE */
    fun smpRec(on: Int) { if (handle != 0L) nativeSmpRec(handle, on) }
    fun smpPlay(on: Int) { if (handle != 0L) nativeSmpPlay(handle, on) }
    fun smpSet(which: Int, v: Float) { if (handle != 0L) nativeSmpSet(handle, which, v) }
    fun smpLen(): Int = if (handle != 0L) nativeSmpLen(handle) else 0
    fun smpPeaks(out: FloatArray) { if (handle != 0L) nativeSmpPeaks(handle, out) }

    /* circuit bend post-master : mode 0 GLITCH 1 LOW BAT */
    fun bend(on: Int, x: Float, y: Float, mode: Int) {
        if (handle != 0L) nativeBend(handle, on, x, y, mode)
    }

    /* SNAP (16) / KIT (16) / undo-redo */
    fun snapStore(slot: Int) { if (handle != 0L) nativeSnapStore(handle, slot) }
    fun snapLoad(slot: Int) { if (handle != 0L) nativeSnapLoad(handle, slot) }
    fun undo() { if (handle != 0L) nativeUndo(handle) }
    fun redo() { if (handle != 0L) nativeRedo(handle) }
    fun kitStore(slot: Int) { if (handle != 0L) nativeKitStore(handle, slot) }
    fun kitLoad(slot: Int) { if (handle != 0L) nativeKitLoad(handle, slot) }

    /* page XY */
    fun xySet(on: Int, x: Float, y: Float) { if (handle != 0L) nativeXySet(handle, on, x, y) }
    fun xyTarget(axis: Int, t: Int) { if (handle != 0L) nativeXyTarget(handle, axis, t) }
    fun xyHold(h: Int) { if (handle != 0L) nativeXyHold(handle, h) }

    /* sirène */
    fun siren(on: Int, lvl: Float, t: Float, fb: Float) { if (handle != 0L) nativeSiren(handle, on, lvl, t, fb) }

    /* resample dans un slot looper (ring audio) */
    fun loopRsStart(dst: Int, monmask: Int, fmt: Int, khz: Int) { if (handle != 0L) nativeLoopRsStart(handle, dst, monmask, fmt, khz) }
    fun loopRsStop() { if (handle != 0L) nativeLoopRsStop(handle) }
    fun loopRsRec(): Boolean = handle != 0L && nativeLoopRsRec(handle)

    private external fun nativeCreate(sampleRate: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeNoteOn(handle: Long, note: Int, vel: Int, accent: Int, slide: Int)
    private external fun nativeNoteOff(handle: Long, note: Int)
    private external fun nativeAllNotesOff(handle: Long)
    private external fun nativeSetParam(handle: Long, p: Int, v: Float)
    private external fun nativeSetEngine(handle: Long, e: Int)
    private external fun nativeSetBpm(handle: Long, bpm: Float)
    private external fun nativeSetRoot(handle: Long, root: Int)
    private external fun nativeSetScale(handle: Long, s: Int)
    private external fun nativeFxSet(handle: Long, p: Int, v: Float)
    private external fun nativePunch(handle: Long, on: Int, type: Int, a: Float, b: Float, mix: Float)
    private external fun nativePunchTail(handle: Long, t: Float)
    private external fun nativeLoopPlay(handle: Long, on: Int)
    private external fun nativeLoopToggle(handle: Long, slot: Int)
    private external fun nativeLoopRecStart(handle: Long, slot: Int, od: Int)
    private external fun nativeLoopRecStop(handle: Long, slot: Int)
    private external fun nativeLoopErase(handle: Long, slot: Int)
    private external fun nativeLoopSetLen(handle: Long, slot: Int, len64: Int)
    private external fun nativeLoopSetSp(handle: Long, slot: Int, idx: Int, v: Float)
    private external fun nativeLoopSlotOn(handle: Long, slot: Int): Boolean
    private external fun nativeLoopSlotRec(handle: Long, slot: Int): Boolean
    private external fun nativeAcidGenerate(handle: Long, seed: Int)
    private external fun nativeAcidPlay(handle: Long, on: Int)
    private external fun nativeAcidKnob(handle: Long, which: Int, v: Float)
    private external fun nativeSmpRec(handle: Long, on: Int)
    private external fun nativeSmpPlay(handle: Long, on: Int)
    private external fun nativeSmpSet(handle: Long, which: Int, v: Float)
    private external fun nativeSmpLen(handle: Long): Int
    private external fun nativeSmpPeaks(handle: Long, out: FloatArray)
    private external fun nativeBend(handle: Long, on: Int, x: Float, y: Float, mode: Int)
    private external fun nativeSnapStore(handle: Long, slot: Int)
    private external fun nativeSnapLoad(handle: Long, slot: Int)
    private external fun nativeUndo(handle: Long)
    private external fun nativeRedo(handle: Long)
    private external fun nativeKitStore(handle: Long, slot: Int)
    private external fun nativeKitLoad(handle: Long, slot: Int)
    private external fun nativeXySet(handle: Long, on: Int, x: Float, y: Float)
    private external fun nativeXyTarget(handle: Long, axis: Int, target: Int)
    private external fun nativeXyHold(handle: Long, hold: Int)
    private external fun nativeSiren(handle: Long, on: Int, lvl: Float, t: Float, fb: Float)
    private external fun nativeLoopGetSp(handle: Long, slot: Int, idx: Int): Float
    private external fun nativeLoopRsStart(handle: Long, dst: Int, monmask: Int, fmt: Int, khz: Int)
    private external fun nativeLoopRsStop(handle: Long)
    private external fun nativeLoopRsRec(handle: Long): Boolean

    /* ===== pack SEQ+ (branche seq-plus, PR #3) ===== */
    /* modulation sources: page LFO subs 0..7 = LFO1..4 + LABO1..4 ; page ENV subs 0..7 = ENV1,ENV2,EF,LOG,SLEW,CMP,QUANT,ATT */
    const val ZT_NSRC = 16
    const val ZT_NDST = 35                 /* 0..7 voice, 8..25 FX, 26..34 seq */
    const val ZT_SEQ_DST0 = 26
    val SRC_NAMES = arrayOf("LFO1", "LFO2", "LFO3", "LFO4",
        "LAB1", "LAB2", "LAB3", "LAB4",
        "ENV1", "ENV2", "EF", "LOG", "SLEW", "CMP", "QUANT", "ATT")
    val LAB_NAMES = arrayOf("LINK", "MYCEL", "HUMUS", "NETVINT", "CHAOS", "OISEAUX", "INSECT", "MAREE")
    val LFO_WAVES = arrayOf("SIN", "TRI", "SQ", "SAW", "S&H", "DRUNK")
    val TOOL_NAMES = arrayOf("ENV1", "ENV2", "EF", "LOG", "SLEW", "CMP", "QUANT", "ATT")
    const val ACID_MUT_TIME = 1
    const val ACID_MUT_PITCH = 2
    const val ACID_MUT_ARTICULATION = 3
    const val DST_OFF = 255
    const val ZT_NPRESETS = 8
    val PRESET_NAMES = arrayOf("ACID 303", "FILTRE WOBBLE", "PITCH DRIFT", "GATE 303",
        "SLIDE CITY", "LABO CHAOS", "TM MAX", "MAREE")
    const val ZT_NPARTS = 3          // 0 BASSE, 1 LEAD, 2 DRUMS
    val PART_NAMES = arrayOf("BASSE", "LEAD", "DRUMS")
    const val ZT_PSLOTS = 16         // banc libre par partie

    private var prefs: android.content.SharedPreferences? = null
    fun initPrefs(ctx: android.content.Context) {
        prefs = ctx.getSharedPreferences("zulutek_presets", android.content.Context.MODE_PRIVATE)
    }
    private fun pkey(part: Int, slot: Int) = "p" + part + "s" + slot

    fun seqKnob(k: Int, v: Float) { if (handle != 0L) nativeSeqKnob(handle, k, v) }
    fun seqKnobGet(k: Int): Float = if (handle != 0L) nativeSeqKnobGet(handle, k) else 0f
    fun seqMutate(layer: Int) { if (handle != 0L) nativeSeqMutate(handle, layer) }
    fun modKnob(src: Int, k: Int, v: Float) { if (handle != 0L) nativeModKnob(handle, src, k, v) }
    fun modKnobGet(src: Int, k: Int): Float = if (handle != 0L) nativeModKnobGet(handle, src, k) else 0f
    fun lfoOpt(which: Int, on: Int) { if (handle != 0L) nativeLfoOpt(handle, which, on) }
    fun envLoop(e: Int, on: Int) { if (handle != 0L) nativeEnvLoop(handle, e, on) }
    fun srcVal(src: Int): Float = if (handle != 0L) nativeSrcVal(handle, src) else 0f
    fun assign(dst: Int, src: Int, depth: Float) { if (handle != 0L) nativeAssign(handle, dst, src, depth) }
    fun assignSrc(dst: Int): Int = if (handle != 0L) nativeAssignSrc(handle, dst) else DST_OFF
    fun assignDepth(dst: Int): Float = if (handle != 0L) nativeAssignDepth(handle, dst) else 0f

    fun preset(i: Int) { if (handle != 0L) nativePreset(handle, i) }
    fun presetName(i: Int): String = if (handle != 0L) nativePresetName(i) else "?"
    fun presetSave(part: Int, slot: Int) {
        if (handle == 0L) return
        nativePresetSave(handle, part, slot)
        val blob = nativePresetExport(handle, part, slot) ?: return
        prefs?.edit()?.putString(pkey(part, slot),
            android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP))?.apply()
    }
    fun presetLoad(part: Int, slot: Int): Boolean {
        if (handle == 0L) return false
        var blob = nativePresetExport(handle, part, slot)
        if (blob == null) {
            val st = prefs?.getString(pkey(part, slot), null) ?: return false
            blob = android.util.Base64.decode(st, android.util.Base64.NO_WRAP)
            if (!nativePresetImport(handle, part, slot, blob)) return false
        }
        nativePresetLoad(handle, part, slot)
        return true
    }
    fun presetUsed(part: Int, slot: Int): Boolean =
        handle != 0L && (nativePresetUsed(handle, part, slot) || (prefs?.getString(pkey(part, slot), null) != null))
    fun presetClear(part: Int, slot: Int) {
        if (handle != 0L) nativePresetClear(handle, part, slot)
        prefs?.edit()?.remove(pkey(part, slot))?.apply()
    }
    fun presetSlotName(part: Int, slot: Int): String =
        if (handle != 0L) nativePresetSlotName(handle, part, slot) else ""
    fun bankLoad(part: Int, slot: Int) { if (handle != 0L) nativeBankLoad(handle, part, slot) }
    fun bankName(part: Int, slot: Int): String =
        if (handle != 0L) nativeBankName(handle, part, slot) else ""

    private external fun nativeSeqKnob(handle: Long, k: Int, v: Float)
    private external fun nativeSeqKnobGet(handle: Long, k: Int): Float
    private external fun nativeSeqMutate(handle: Long, layer: Int)
    private external fun nativeModKnob(handle: Long, src: Int, k: Int, v: Float)
    private external fun nativeModKnobGet(handle: Long, src: Int, k: Int): Float
    private external fun nativeLfoOpt(handle: Long, which: Int, on: Int)
    private external fun nativeEnvLoop(handle: Long, e: Int, on: Int)
    private external fun nativeSrcVal(handle: Long, src: Int): Float
    private external fun nativeAssign(handle: Long, dst: Int, src: Int, depth: Float)
    private external fun nativeAssignSrc(handle: Long, dst: Int): Int
    private external fun nativeAssignDepth(handle: Long, dst: Int): Float
    private external fun nativePreset(handle: Long, i: Int)
    private external fun nativePresetName(i: Int): String
    private external fun nativePresetSave(handle: Long, part: Int, slot: Int)
    private external fun nativePresetLoad(handle: Long, part: Int, slot: Int)
    private external fun nativePresetUsed(handle: Long, part: Int, slot: Int): Boolean
    private external fun nativePresetClear(handle: Long, part: Int, slot: Int)
    private external fun nativePresetExport(handle: Long, part: Int, slot: Int): ByteArray?
    private external fun nativePresetImport(handle: Long, part: Int, slot: Int, data: ByteArray): Boolean
    private external fun nativePresetSlotName(handle: Long, part: Int, slot: Int): String
    private external fun nativePresetSlotSetName(handle: Long, part: Int, slot: Int, name: String)
    private external fun nativeBankLoad(handle: Long, part: Int, slot: Int)
    private external fun nativeBankName(handle: Long, part: Int, slot: Int): String
}
