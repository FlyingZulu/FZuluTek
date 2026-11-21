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

    /* page FINE (18) : réglages fins voix — même contrat que le firmware (patch 0012).
     * opt 0..10 = AT_OPT_*, 11 = f_model (ORG/NEW), 12 = f_morph 0..255 (LP->HP). */
    const val AT_NOPTS = 11
    const val FINE_OPT_MODEL = 11
    const val FINE_OPT_MORPH = 12
    const val FINE_NOPT = 13
    val FINE_OPT_NAMES = arrayOf("PW", "SQ DRP", "SAW RS", "SAW DRP", "DRIVE", "SLIDE", "LEGATO",
        "OTA", "SAT", "DRIFT", "UNIT", "MODEL", "MORPH")
    private val FINE_NCHOICES = intArrayOf(5, 5, 4, 4, 3, 3, 2, 3, 3, 4, 4, 2, 256)
    val FINE_VALS = arrayOf(
        arrayOf("44%", "47%", "50%", "53%", "56%"), arrayOf("NONE", "8HZ", "15HZ", "30HZ", "60HZ"),
        arrayOf("1US", "3US", "8US", "20US"), arrayOf("0.7", "3", "8", "15"), arrayOf("0dB", "+6dB", "+12dB"),
        arrayOf("60ms", "120ms", "220ms"), arrayOf("OFF", "ON"), arrayOf("NONE", "SOFT", "HARD"),
        arrayOf("NONE", "SOFT", "HARD"), arrayOf("NONE", "SUBTLE", "MARKED", "HEAVY"), arrayOf("1", "2", "3", "4"),
        arrayOf("ORG", "NEW"), arrayOf("LP", "HP"))
    fun fineOptCount(idx: Int): Int = FINE_NCHOICES.getOrElse(idx) { 2 }
    fun fineValName(idx: Int, v: Int): String =
        if (idx == FINE_OPT_MORPH) "${v * 100 / 255}%" else FINE_VALS[idx].getOrElse(v) { "?" }
    /* bancs façon Shruthi (SEL = banc, K1..K4 = réglages du banc) — idem firmware 0012 */
    val FINE_BANKS = arrayOf("OSC", "FILTRE", "AMP")
    val FINE_BANK_OPT = arrayOf(
        intArrayOf(0, 1, 2, 3),                          // OSC : PW / SQ DRP / SAW RS / SAW DRP
        intArrayOf(FINE_OPT_MODEL, FINE_OPT_MORPH, 4, 7), // FILTRE : MODEL / MORPH / DRIVE / OTA
        intArrayOf(5, 6, 8, 9))                          // AMP : SLIDE / LEGATO / SAT / DRIFT
    fun setOption(opt: Int, v: Int) { if (handle != 0L) nativeSetOption(handle, opt, v) }
    fun optionGet(opt: Int): Int = if (handle != 0L) nativeOptionGet(handle, opt) else 0

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

    fun fxGet(p: Int): Float = if (handle != 0L) nativeFxGet(handle, p) else 0f
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
    fun loopSetChoke(slot: Int, grp: Int) { if (handle != 0L) nativeLoopSetChoke(handle, slot, grp) }
    fun loopGetChoke(slot: Int): Int = if (handle != 0L) nativeLoopGetChoke(handle, slot) else 0
    fun loopChokeMaster(grp: Int): Int = if (handle != 0L) nativeLoopChokeMaster(handle, grp) else -1
    fun loopSetLeader(on: Int) { if (handle != 0L) nativeLoopSetLeader(handle, on) }
    fun loopGetLeader(): Int = if (handle != 0L) nativeLoopGetLeader(handle) else 1
    fun loopSetLeaderSlot(slot: Int, on: Int) { if (handle != 0L) nativeLoopSetLeaderSlot(handle, slot, on) } // POLY
    fun loopClearLeader(grp: Int) { if (handle != 0L) nativeLoopClearLeader(handle, grp) }

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
    fun sirenWave(w: Int) { if (handle != 0L) nativeSirenWave(handle, w) }
    fun sirenWaveGet(): Int = if (handle != 0L) nativeSirenWaveGet(handle) else 0
    fun sirenMix(m: Float) { if (handle != 0L) nativeSirenMix(handle, m) }
    fun sirenMixGet(): Float = if (handle != 0L) nativeSirenMixGet(handle) else 0f
    val SIREN_WAVES = arrayOf("CLASSIQUE", "CHOP", "POLICE", "LASER")

    /* resample dans un slot looper (ring audio) */
    fun loopRsStart(dst: Int, monmask: Int, fmt: Int, khz: Int) { if (handle != 0L) nativeLoopRsStart(handle, dst, monmask, fmt, khz) }
    fun loopRsStop() { if (handle != 0L) nativeLoopRsStop(handle) }
    fun loopRsRec(): Boolean = handle != 0L && nativeLoopRsRec(handle)

    /* page MIX + baie de patch (src: 0 VOIX 1 SIREN 2 SMP 3 LOOP ; dst: 0 FX 1 MASTER 2 POW!) */
    fun mixSetVol(slot: Int, v: Float) { if (handle != 0L) nativeMixSetVol(handle, slot, v) }
    fun mixSetAux(slot: Int, on: Int) { if (handle != 0L) nativeMixSetAux(handle, slot, on) }
    fun mixSetGain(g: Float) { if (handle != 0L) nativeMixSetGain(handle, g) }
    fun mixGetGain(): Float = if (handle != 0L) nativeMixGetGain(handle) else 1f
    fun masterPeak(): Float = if (handle != 0L) nativeMasterPeak(handle) else 0f
    fun loopSlotAct(slot: Int): Int = if (handle != 0L) nativeLoopSlotAct(handle, slot) else 0
    fun patchSet(src: Int, dst: Int, on: Int) { if (handle != 0L) nativePatchSet(handle, src, dst, on) }
    fun patchGet(src: Int, dst: Int): Int = if (handle != 0L) nativePatchGet(handle, src, dst) else 0
    fun patchCompat(src: Int, dst: Int): Int = if (handle != 0L) nativePatchCompat(handle, src, dst) else 0

    /* baie de patch v3 : 22 sources x 158 ports groupés par module.
     * Ports 0..34 = matrice ASSIGN de la branche seq-plus (VOIX 0..7, FX 8..25, SEQ 26..34),
     * puis SIREN 35..37, SMP 38..42, LOOP 43..154 (16 slots x 7), OUT 155..157.
     * Sources 6..21 = modulation LFO/ENV (branche seq-plus PR #3, même ordre que SRC_NAMES là-bas). */
    const val NSRC = 23                // 22 = DRUMS (boîte à rythmes, PR #5 vendored)
    const val PSRC_DRUMS = 22
    /* sources affichées dans la baie : audio d'abord (VOIX/SIREN/SMP/LOOP/DRUMS), puis X/Y */
    val SRC_IDX = intArrayOf(0, 1, 2, 3, PSRC_DRUMS, 4, 5)
    val NSRC_ACTIVE = SRC_IDX.size     // 0..5 + DRUMS actifs ici ; 6..21 câblés par le moteur de PR #3
    const val NMOD = 9
    const val NDST = 223
    const val MOD_OUT = 6
    const val MOD_AUX = 7              // 8 slots aux libres (2 x 4 tranches du main mix)
    const val MOD_MIX = 8              // 55 ports : 13 params x 4 tranches + 3 master
    val MOD_OFF = intArrayOf(0, 8, 26, 35, 38, 43, 155, 160, 168, 223)
    val MOD_NAME = arrayOf("VOIX", "FX", "SEQ", "SIREN", "SMP", "LOOP", "OUT", "AUX", "MIX")
    val MOD_NPORT = intArrayOf(8, 18, 9, 3, 5, 112, 5, 8, 55)
    val SRC_NAME = arrayOf("VOIX", "SIREN", "SMP", "LOOP", "X", "Y",
        "LFO1", "LFO2", "LFO3", "LFO4", "LAB1", "LAB2", "LAB3", "LAB4",
        "ENV1", "ENV2", "EF", "LOG", "SLEW", "CMP", "QUANT", "ATT", "DRUMS")

    /* boîte à rythmes (source DRUMS) */
    const val DR_NPADS = 8
    val DR_PAD_NAMES = arrayOf("KICK", "SNARE", "HATC", "HATO", "CLAP", "TOM", "CONGA", "COWBELL")
    fun drumPlay(on: Int) { if (handle != 0L) nativeDrumPlay(handle, on) }
    fun drumHit(pad: Int) { if (handle != 0L) nativeDrumHit(handle, pad) }
    fun drumPat(pad: Int, step: Int, on: Int) { if (handle != 0L) nativeDrumPat(handle, pad, step, on) }
    fun drumGetPat(pad: Int, step: Int): Int = if (handle != 0L) nativeDrumGetPat(handle, pad, step) else 0
    fun drumClear(pad: Int) { if (handle != 0L) nativeDrumClear(handle, pad) }
    fun drumRand(pad: Int, density: Float, seed: Int) { if (handle != 0L) nativeDrumRand(handle, pad, density, seed) }
    fun drumSwing(s: Float) { if (handle != 0L) nativeDrumSwing(handle, s) }
    fun drumLevel(pad: Int, lvl: Float) { if (handle != 0L) nativeDrumLevel(handle, pad, lvl) }
    fun drumMaster(m: Float) { if (handle != 0L) nativeDrumMaster(handle, m) }
    fun drumStep(): Int = if (handle != 0L) nativeDrumStep(handle) else 0
    /* one-shot drums + looper (liaison APK/FM-1 : SHIFT+SEQ sur les pages DRUMS/LOOP) */
    fun drumSetOneshot(on: Int) { if (handle != 0L) nativeDrumSetOneshot(handle, on) }
    fun drumGetOneshot(): Int = if (handle != 0L) nativeDrumGetOneshot(handle) else 0
    fun loopSetOneshot(on: Int) { if (handle != 0L) nativeLoopSetOneshot(handle, on) }
    fun loopGetOneshot(): Int = if (handle != 0L) nativeLoopGetOneshot(handle) else 0
    fun drumSetChoke(pad: Int, grp: Int) { if (handle != 0L) nativeDrumSetChoke(handle, pad, grp) }
    fun drumGetChoke(pad: Int): Int = if (handle != 0L) nativeDrumGetChoke(handle, pad) else 0
    fun drumChokeMaster(grp: Int): Int = if (handle != 0L) nativeDrumChokeMaster(handle, grp) else -1
    fun drumSetLeader(on: Int) { if (handle != 0L) nativeDrumSetLeader(handle, on) }
    fun drumGetLeader(): Int = if (handle != 0L) nativeDrumGetLeader(handle) else 1
    fun drumSetLeaderPad(pad: Int, on: Int) { if (handle != 0L) nativeDrumSetLeaderPad(handle, pad, on) }   // POLY (noire 10)
    fun drumClearLeader(grp: Int) { if (handle != 0L) nativeDrumClearLeader(handle, grp) }
    fun modOf(dst: Int): Int {
        if (dst < 0 || dst >= NDST) return -1
        var m = NMOD - 1
        while (m > 0 && dst < MOD_OFF[m]) m--
        return m
    }
    fun assignSet(src: Int, dst: Int, on: Int) { if (handle != 0L) nativeAssignSet(handle, src, dst, on) }
    fun assignGet(src: Int, dst: Int): Int = if (handle != 0L) nativeAssignGet(handle, src, dst) else 0
    fun assignWords(src: Int): LongArray = if (handle != 0L) nativeAssignWords(handle, src) else LongArray(4)
    fun assignBit(words: LongArray, dst: Int): Boolean = (words[dst / 64] shr (dst % 64)) and 1L == 1L

    private external fun nativeCreate(sampleRate: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeNoteOn(handle: Long, note: Int, vel: Int, accent: Int, slide: Int)
    private external fun nativeNoteOff(handle: Long, note: Int)
    private external fun nativeAllNotesOff(handle: Long)
    private external fun nativeSetParam(handle: Long, p: Int, v: Float)
    private external fun nativeSetEngine(handle: Long, e: Int)
    private external fun nativeSetOption(handle: Long, opt: Int, v: Int)
    private external fun nativeOptionGet(handle: Long, opt: Int): Int
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
    private external fun nativeSirenWave(handle: Long, w: Int)
    private external fun nativeSirenWaveGet(handle: Long): Int
    private external fun nativeSirenMix(handle: Long, m: Float)
    private external fun nativeSirenMixGet(handle: Long): Float
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
    fun loopMutate(slot: Int, layer: Int) { if (handle != 0L) nativeLoopMutate(handle, slot, layer) }  // ENV/LFO/SEQ sur les loops
    fun loopRandp(p: Float) { if (handle != 0L) nativeLoopRandp(handle, p) }                             // RAND au wrap des loops
    /* arpégiateur (bouton ARP, page 13) : k=0 MODE 1 OCT 2 DIV 3 GATE */
    val ARP_MODES = arrayOf("UP", "DOWN", "UP-DN", "RAND", "PLAY")
    val ARP_DIVS = arrayOf("1/4", "1/8", "1/16", "1/32", "1/8T", "1/16T")
    fun arpOn(on: Int) { if (handle != 0L) nativeArpOn(handle, on) }
    fun arpGetOn(): Int = if (handle != 0L) nativeArpGetOn(handle) else 0
    fun arpKnob(k: Int, v: Float) { if (handle != 0L) nativeArpKnob(handle, k, v) }
    fun arpKnobGet(k: Int): Float = if (handle != 0L) nativeArpKnobGet(handle, k) else 0f
    fun arpHeld(): Int = if (handle != 0L) nativeArpHeld(handle) else 0
    /* page MACRO (16 slots assignables) : dst 0..50 = voix/FX/seq/mix-macro, 100+slot = aux->PoW!,
       120+c = baie de patch, 140 = type PoW! */
    const val ZT_NMACRO = 16
    const val ZT_NDST_ALL = 51
    const val ZT_MDST_AUX0 = 100
    const val ZT_MDST_PATCH = 120
    const val ZT_MDST_POWT = 140
    fun macroSet(slot: Int, dst: Int, sub: Int, v: Float) { if (handle != 0L) nativeMacroSet(handle, slot, dst, sub, v) }
    fun macroApply(slot: Int) { if (handle != 0L) nativeMacroApply(handle, slot) }
    fun macroLevel(slot: Int, v: Float) { if (handle != 0L) nativeMacroLevel(handle, slot, v) }
    fun macroErase(slot: Int) { if (handle != 0L) nativeMacroErase(handle, slot) }
    fun macroUsed(slot: Int): Int = if (handle != 0L) nativeMacroUsed(handle, slot) else 0
    fun macroDst(slot: Int): Int = if (handle != 0L) nativeMacroDst(handle, slot) else 0
    fun macroSub(slot: Int): Int = if (handle != 0L) nativeMacroSub(handle, slot) else 0
    fun macroVal(slot: Int): Float = if (handle != 0L) nativeMacroVal(handle, slot) else 0f
    fun punchTypeGet(): Int = if (handle != 0L) nativePunchTypeGet(handle) else 8
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
    fun presetSave(part: Int, slot: Int) = presetSaveNamed(part, slot, "")
    fun presetRename(part: Int, slot: Int, name: String) {   // renomme un preset existant (crée un save user si slot usine) + re-persiste le blob
        if (handle == 0L) return
        if (!presetUsed(part, slot)) nativePresetSave(handle, part, slot)
        nativePresetSlotSetName(handle, part, slot, name)
        val blob = nativePresetExport(handle, part, slot) ?: return
        prefs?.edit()?.putString(pkey(part, slot),
            android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP))?.apply()
    }
    fun presetSaveNamed(part: Int, slot: Int, name: String) {   // name vide = nom auto "P<part>-<slot>"
        if (handle == 0L) return
        nativePresetSave(handle, part, slot)
        if (name.isNotEmpty()) nativePresetSlotSetName(handle, part, slot, name)
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
    /* v3 ZTF : blob ZTP1 brut pour export/import fichier (filesDir/ztek/presets, partage, push FM-1) */
    fun presetExportBlob(part: Int, slot: Int): ByteArray? =
        if (handle != 0L) nativePresetExport(handle, part, slot) else null
    fun presetImportBlob(part: Int, slot: Int, data: ByteArray): Boolean =
        handle != 0L && nativePresetImport(handle, part, slot, data)

    private external fun nativeSeqKnob(handle: Long, k: Int, v: Float)
    private external fun nativeSeqKnobGet(handle: Long, k: Int): Float
    private external fun nativeSeqMutate(handle: Long, layer: Int)
    private external fun nativeLoopMutate(handle: Long, slot: Int, layer: Int)
    private external fun nativeLoopRandp(handle: Long, p: Float)
    private external fun nativeArpOn(handle: Long, on: Int)
    private external fun nativeArpGetOn(handle: Long): Int
    private external fun nativeArpKnob(handle: Long, k: Int, v: Float)
    private external fun nativeArpKnobGet(handle: Long, k: Int): Float
    private external fun nativeArpHeld(handle: Long): Int
    private external fun nativeMacroSet(handle: Long, slot: Int, dst: Int, sub: Int, v: Float)
    private external fun nativeMacroApply(handle: Long, slot: Int)
    private external fun nativeMacroLevel(handle: Long, slot: Int, v: Float)
    private external fun nativeMacroErase(handle: Long, slot: Int)
    private external fun nativeMacroUsed(handle: Long, slot: Int): Int
    private external fun nativeMacroDst(handle: Long, slot: Int): Int
    private external fun nativeMacroSub(handle: Long, slot: Int): Int
    private external fun nativeMacroVal(handle: Long, slot: Int): Float
    private external fun nativePunchTypeGet(handle: Long): Int
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

    /* ===== page MIX + baie de patch (branche looper, PR #1 @ 7d87264) ===== */
    private external fun nativeMixSetVol(handle: Long, slot: Int, v: Float)
    private external fun nativeMixSetAux(handle: Long, slot: Int, on: Int)
    private external fun nativeMixSetGain(handle: Long, g: Float)
    private external fun nativeMixGetGain(handle: Long): Float
    // table de mixage 4 tranches VOICE/DRUMS/LOOP/SIREN (décision mixeur 4 tranches, docs/ZULUTEK-MIX-SIREN.md)
    const val STRIP_VOICE = 0
    const val STRIP_DRUMS = 1
    const val STRIP_LOOP = 2
    const val STRIP_SIREN = 3
    const val MIX_CUT = 0; const val MIX_FMODE = 1; const val MIX_VOL = 2; const val MIX_PAN = 3
    const val MIX_MUT = 4; const val MIX_SOLO = 5; const val MIX_LDR = 6
    const val MIX_AUX_POW = 7; const val MIX_AUX_LOOP = 8
    const val MIX_AUX1 = 9; const val MIX_AUX2 = 10; const val MIX_AUX3 = 11; const val MIX_AUX4 = 12
    const val MIX_N = 13
    const val MIXM_GAIN = 0; const val MIXM_AUX1 = 1; const val MIXM_AUX2 = 2
    val MIX_PAR_NAMES = arrayOf("CUT", "FILT", "VOL", "PAN", "MUT", "SOLO", "LDR",
        "P>POW", "L>LOOP", "AUX1", "AUX2", "AUX3", "AUX4")
    val STRIP_NAMES = arrayOf("VOICE", "DRUMS", "LOOP", "SIREN")
    fun mixSetStrip(strip: Int, param: Int, v: Float) { if (handle != 0L) nativeMixSetStrip(handle, strip, param, v) }
    fun mixGetStrip(strip: Int, param: Int): Float = if (handle != 0L) nativeMixGetStrip(handle, strip, param) else 0f
    fun mixSetMaster(param: Int, v: Float) { if (handle != 0L) nativeMixSetMaster(handle, param, v) }
    fun mixGetMaster(param: Int): Float = if (handle != 0L) nativeMixGetMaster(handle, param) else 0f
    private external fun nativeMixSetStrip(handle: Long, strip: Int, param: Int, v: Float)
    private external fun nativeMixGetStrip(handle: Long, strip: Int, param: Int): Float
    private external fun nativeMixSetMaster(handle: Long, param: Int, v: Float)
    private external fun nativeMixGetMaster(handle: Long, param: Int): Float
    private external fun nativeMasterPeak(handle: Long): Float
    private external fun nativeLoopSlotAct(handle: Long, slot: Int): Int
    private external fun nativePatchSet(handle: Long, src: Int, dst: Int, on: Int)
    private external fun nativePatchGet(handle: Long, src: Int, dst: Int): Int
    private external fun nativePatchCompat(handle: Long, src: Int, dst: Int): Int
    private external fun nativeAssignSet(handle: Long, src: Int, dst: Int, on: Int)
    private external fun nativeAssignGet(handle: Long, src: Int, dst: Int): Int
    private external fun nativeAssignWords(handle: Long, src: Int): LongArray
    private external fun nativeDrumPlay(handle: Long, on: Int)
    private external fun nativeDrumHit(handle: Long, pad: Int)
    private external fun nativeDrumPat(handle: Long, pad: Int, step: Int, on: Int)
    private external fun nativeDrumGetPat(handle: Long, pad: Int, step: Int): Int
    private external fun nativeDrumClear(handle: Long, pad: Int)
    private external fun nativeDrumRand(handle: Long, pad: Int, density: Float, seed: Int)
    private external fun nativeDrumSwing(handle: Long, s: Float)
    private external fun nativeDrumLevel(handle: Long, pad: Int, lvl: Float)
    private external fun nativeDrumMaster(handle: Long, m: Float)
    private external fun nativeDrumStep(handle: Long): Int
    private external fun nativeDrumSetOneshot(handle: Long, on: Int)
    private external fun nativeDrumGetOneshot(handle: Long): Int
    private external fun nativeLoopSetOneshot(handle: Long, on: Int)
    private external fun nativeLoopGetOneshot(handle: Long): Int
    private external fun nativeDrumSetChoke(handle: Long, pad: Int, grp: Int)
    private external fun nativeDrumGetChoke(handle: Long, pad: Int): Int
    private external fun nativeDrumChokeMaster(handle: Long, grp: Int): Int
    private external fun nativeDrumSetLeader(handle: Long, on: Int)
    private external fun nativeDrumGetLeader(handle: Long): Int
    private external fun nativeDrumSetLeaderPad(handle: Long, pad: Int, on: Int)
    private external fun nativeDrumClearLeader(handle: Long, grp: Int)
    private external fun nativeLoopSetChoke(handle: Long, slot: Int, grp: Int)
    private external fun nativeLoopGetChoke(handle: Long, slot: Int): Int
    private external fun nativeLoopChokeMaster(handle: Long, grp: Int): Int
    private external fun nativeLoopSetLeader(handle: Long, on: Int)
    private external fun nativeLoopGetLeader(handle: Long): Int
    private external fun nativeLoopSetLeaderSlot(handle: Long, slot: Int, on: Int)
    private external fun nativeLoopClearLeader(handle: Long, grp: Int)
    private external fun nativeFxGet(handle: Long, p: Int): Float
    fun setEco(on: Int) { if (handle != 0L) nativeSetEco(handle, on) }       // OPTIONS > ECO
    fun eco(): Int = if (handle != 0L) nativeGetEco(handle) else 0
    private external fun nativeSetEco(handle: Long, on: Int)
    private external fun nativeGetEco(handle: Long): Int
    fun setLuxe(on: Int) { if (handle != 0L) nativeSetLuxe(handle, on) }     // OPTIONS > LUXE (APK only)
    fun luxe(): Int = if (handle != 0L) nativeGetLuxe(handle) else 0
    private external fun nativeSetLuxe(handle: Long, on: Int)
    private external fun nativeGetLuxe(handle: Long): Int

    /* ---- pedalboard FX (menu FX, REC+FX) : 4 chaînes x 3 slots = 12 ---- */
    const val NCHAIN = 4
    const val SPC = 3
    const val NSLOT = 12
    const val NCAT = 20
    const val NBLOCK = 8
    val FXT_NAMES = arrayOf("OFF", "DRIVE", "CHORUS", "FLANGER", "VIBRATO", "SVF", "WAH",
        "SLAP", "ECHO", "DOTTED", "BBD", "TAPE", "GATE", "OCT",
        "AUTOPAN", "RINGMOD", "TREM", "REV PLATE", "REV SPRING", "ROOM")
    fun fxslotSet(slot: Int, type: Int, p0: Float, p1: Float, p2: Float, p3: Float, mix: Float) {
        if (handle != 0L) nativeFxslotSet(handle, slot, type, p0, p1, p2, p3, mix)
    }
    fun fxslotType(slot: Int): Int = if (handle != 0L) nativeFxslotType(handle, slot) else 0
    fun fxslotPar(slot: Int, k: Int): Float = if (handle != 0L) nativeFxslotPar(handle, slot, k) else 0f
    fun fxslotMix(slot: Int): Float = if (handle != 0L) nativeFxslotMix(handle, slot) else 0f
    fun fxchainOrder(order: IntArray) { if (handle != 0L) nativeFxchainOrder(handle, order) }
    fun fxchainOrderGet(): IntArray = if (handle != 0L) nativeFxchainOrderGet(handle) else intArrayOf(0, 1, 2, 3)
    fun fxchainMix(chain: Int, m: Float) { if (handle != 0L) nativeFxchainMix(handle, chain, m) }
    fun fxchainMixGet(chain: Int): Float = if (handle != 0L) nativeFxchainMixGet(handle, chain) else 1f
    fun fxblockSave(blk: Int) { if (handle != 0L) nativeFxblockSave(handle, blk) }
    fun fxblockLoad(blk: Int) { if (handle != 0L) nativeFxblockLoad(handle, blk) }
    fun fxblockUsed(blk: Int): Int = if (handle != 0L) nativeFxblockUsed(handle, blk) else 0
    private external fun nativeFxslotSet(handle: Long, slot: Int, type: Int, p0: Float, p1: Float, p2: Float, p3: Float, mix: Float)
    private external fun nativeFxslotType(handle: Long, slot: Int): Int
    private external fun nativeFxslotPar(handle: Long, slot: Int, k: Int): Float
    private external fun nativeFxslotMix(handle: Long, slot: Int): Float
    private external fun nativeFxchainOrder(handle: Long, order: IntArray)
    private external fun nativeFxchainOrderGet(handle: Long): IntArray
    private external fun nativeFxchainMix(handle: Long, chain: Int, m: Float)
    private external fun nativeFxchainMixGet(handle: Long, chain: Int): Float
    private external fun nativeFxblockSave(handle: Long, blk: Int)
    private external fun nativeFxblockLoad(handle: Long, blk: Int)
    private external fun nativeFxblockUsed(handle: Long, blk: Int): Int
}
