/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * ZuluTek preview: LcdView renders the FM-1's 240x240 LCD (display only), SurfaceView is the
 * cloned control surface (7 encoders, 14 buttons, 27 keys F3..G5). Everything drives the
 * native ztek engine through ZtekAudio.
 *
 * Pages (as on the FM-1): 0 MAIN, 3 SEQ+ (double-tap SEQ: TM LOCK/LEN, MUT, PLAY, RAND),
 * 10 LFO (REC+LFO: 4 LFO + 4 labos), 11 ENV (REC+ENV: ENV1/ENV2 loopable + EF/LOG/SLEW/CMP/
 * QUANT/ATT), 12 ASSIGN (REC+HOME: typed matrix, source -> destination only, like a VCV Rack
 * patchbay: an input is never offered as a source, and no feedback cycles are proposed).
 *
 * Mapping (docs/ZULUTEK.md + docs/ZULUTEK-SEQ-PLUS.md):
 *  - 16 white keys = the 16 looper slots (tap = play/stop, long = rec arm/stop)
 *  - 11 black keys = playable notes (gate while held)
 *  - buttons: FX SEL ENV LFO EDIT GLO HOME SAVE ARP SEQ PLAY REC OCT- OCT+
 *      SEQ tap = seq play/stop, SEQ double-tap = generate + SEQ+ page
 *      ENV alone = mutate TIME, LFO alone = mutate PITCH, ENV+LFO = mutate ARTICULATION
 *      REC+ENV = ENV page, REC+LFO = LFO page, REC+HOME = ASSIGN page, HOME = MAIN
 *  - encoders on page MAIN: SEL slot, PRE drive type, ALG WAVE, K1..K4 CUTOFF/RESO/ENVMOD/DECAY */
package com.zulutek.preview

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import kotlin.math.roundToInt

class MainActivity : Activity(), SurfaceListener {

    private val st = LcdState()
    private lateinit var lcd: LcdView
    private lateinit var surface: SurfaceView

    private var root = 45
    private var scale = 0
    private var bpm = 120f
    private val tapTimes = LongArray(8)
    private var tapIdx = 0

    /* button hold state for REC+ combos and ENV+LFO */
    private var recDown = false
    private var envDown = false
    private var lfoDown = false
    private var lastSeqTap = 0L
    private var presetIdx = 0
    private var lastEncPreset = -1       // last index selected with the PRESET knob on MAIN

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            for (i in 0 until ZtekAudio.ZT_NSRC) st.srcVals[i] = ZtekAudio.srcVal(i)
            for (k in 0 until 5) st.seqkVals[k] = ZtekAudio.seqKnobGet(5 + k)
            if (st.page == 12) {
                st.asgSrc = ZtekAudio.assignSrc(st.sub)
                st.asgDepth = ZtekAudio.assignDepth(st.sub)
            }
            if (st.page == 13) {
                val bn = ZtekAudio.bankName(st.psPart, st.psSlot)
                st.psUsed = bn.isNotEmpty() || ZtekAudio.presetUsed(st.psPart, st.psSlot)
                st.psName = if (bn.isNotEmpty()) bn else
                    (if (ZtekAudio.presetUsed(st.psPart, st.psSlot)) ZtekAudio.presetSlotName(st.psPart, st.psSlot) else "")
            }
            lcd.invalidate()
            handler.postDelayed(this, 120)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ZtekAudio.start()
        ZtekAudio.initPrefs(this)
        ZtekAudio.setEngine(0)
        ZtekAudio.setBpm(bpm)
        ZtekAudio.setRoot(root)
        ZtekAudio.setScale(scale)
        ZtekAudio.setParam(ZtekAudio.AT_CUTOFF, st.encVals[3])
        ZtekAudio.setParam(ZtekAudio.AT_RESONANCE, st.encVals[4])
        ZtekAudio.setParam(ZtekAudio.AT_ENVMOD, st.encVals[5])
        ZtekAudio.setParam(ZtekAudio.AT_DECAY, st.encVals[6])

        lcd = LcdView(this, st)
        surface = SurfaceView(this, st, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(LcdView.BG)
        }
        layout.addView(lcd, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(surface, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        handler.postDelayed(refresh, 120)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        ZtekAudio.stop()
        super.onDestroy()
    }

    /* forbidden assignment: "sens interdit" pop + valid suggestions below (VCV-style typed patch).
     * Both hints and the no-entry sign are part of the playful mode (LUDIQUE option, off for
     * advanced users). */
    private fun forbidden(sug: String) {
        if (!st.ludiqueOn) return
        st.noEntryUntil = System.currentTimeMillis() + 1200
        st.sugMsg = sug
        lcd.invalidate()
    }

    private fun hint(msg: String) {
        if (!st.ludiqueOn) return
        st.hintUntil = System.currentTimeMillis() + 1600
        st.hintMsg = msg
        lcd.invalidate()
    }

    /* contextual suggestion for a valid destination, by category */
    private fun dstHint(dst: Int, src: Int): String {
        val srcName = if (src in 0 until ZtekAudio.ZT_NSRC) ZtekAudio.SRC_NAMES[src] else "SRC?"
        return when {
            dst < 8 -> "$srcName -> VOIX: ex LFO1->CUTOFF, ENV1->DECAY"
            dst < ZtekAudio.ZT_SEQ_DST0 -> "$srcName -> FX: ex LFO2->DLY, LABO->REV"
            else -> "$srcName -> SEQ: ex LFO->TEMPO, ENV->STEPS, LFO->TM"
        }
    }

    private fun srcNamesUpTo(maxExclusive: Int): String {
        val sb = StringBuilder()
        for (i in 0 until maxExclusive.coerceAtMost(ZtekAudio.ZT_NSRC)) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(ZtekAudio.SRC_NAMES[i])
        }
        return sb.toString()
    }

    private fun setPage(p: Int) {
        st.page = p
        st.encLabels = when (p) {
            3 -> arrayOf("RND", "DEN", "ACC", "LCK", "LEN", "MUT", "PLY")
            10 -> arrayOf("SUB", "SYN", "TRG", "K1", "K2", "K3", "K4")
            11 -> arrayOf("SUB", "LOOP", "", "K1", "K2", "K3", "K4")
            12 -> arrayOf("DST", "SRC", "LUD", "DPT", "", "", "")
            13 -> arrayOf("SLT", "PRT", "", "LD", "SV", "CLR", "")
            else -> arrayOf("SEL", "PST", "ALG", "K1", "K2", "K3", "K4")
        }
        lcd.invalidate()
        surface.invalidate()
    }

    // ---- encoders ----
    override fun onEncoder(i: Int, v: Float) {
        st.pop(i)
        when (st.page) {
            3 -> when (i) {                       // SEQ advanced sub-mode
                0 -> { st.seqkVals[4] = v; ZtekAudio.seqKnob(9, v) }   // SELECT = RAND
                1 -> ZtekAudio.acidKnob(0, v)                          // PRE = density
                2 -> ZtekAudio.acidKnob(1, v)                          // ALG = accent
                3 -> { st.seqkVals[0] = v; ZtekAudio.seqKnob(5, v) }   // K1 = TM LOCK
                4 -> { st.seqkVals[1] = v; ZtekAudio.seqKnob(6, v) }   // K2 = TM LEN
                5 -> { st.seqkVals[2] = v; ZtekAudio.seqKnob(7, v) }   // K3 = MUT
                6 -> { st.seqkVals[3] = v; ZtekAudio.seqKnob(8, v) }   // K4 = PLAY
            }
            10 -> {                               // LFO page: subs 0..7 = LFO1..4 + LABO1..4
                val sub = st.sub
                when (i) {
                    0 -> st.sub = (v * 7.999f).toInt().coerceIn(0, 7)
                    1 -> { st.lfoSync = v > 0.5f; ZtekAudio.lfoOpt(0, if (st.lfoSync) 1 else 0) }
                    2 -> { st.lfoTrig = v > 0.5f; ZtekAudio.lfoOpt(1, if (st.lfoTrig) 1 else 0) }
                    else -> {
                        val k = i - 3
                        /* typed sources: a labo (sub 4..7) can only source lower ids (no cycle,
                         * like a VCV patchbay). Beyond that: no-entry sign + suggestions. */
                        var vv = v
                        if (sub >= 4 && (k == 1 || k == 2)) {
                            val maxV = (sub - 1) / 16.999f
                            if (v > maxV) {
                                forbidden("-> ${srcNamesUpTo(sub)}")
                                vv = maxV
                            }
                        }
                        ZtekAudio.modKnob(sub, k, vv)
                    }
                }
            }
            11 -> {                               // ENV page: subs 0..7 = ENV1..ATT (sources 8..15)
                val sub = st.sub
                when (i) {
                    0 -> st.sub = (v * 7.999f).toInt().coerceIn(0, 7)
                    1 -> if (sub < 2) ZtekAudio.envLoop(sub, if (v > 0.5f) 1 else 0)
                    else -> {
                        val k = i - 3
                        var vv = v
                        if (sub >= 2 && k == 0) {   // tool SRC: only lower source ids
                            val maxV = (7 + sub) / 16.999f
                            if (v > maxV) {
                                forbidden("-> ${srcNamesUpTo(8 + sub)}")
                                vv = maxV
                            }
                        }
                        ZtekAudio.modKnob(8 + sub, k, vv)
                    }
                }
            }
            13 -> when (i) {                      // PRESETS: banc libre 16 slots par partie
                0 -> st.psSlot = (v * (ZtekAudio.ZT_PSLOTS - 0.001f)).toInt().coerceIn(0, ZtekAudio.ZT_PSLOTS - 1)
                1 -> st.psPart = (v * 2.999f).toInt().coerceIn(0, ZtekAudio.ZT_NPARTS - 1)
                3 -> {                            // K1 = LOAD (bank: user save > usine)
                    val nm = ZtekAudio.bankName(st.psPart, st.psSlot)
                    if (nm.isNotEmpty()) {
                        ZtekAudio.bankLoad(st.psPart, st.psSlot)
                        st.presetName = nm
                        st.hintUntil = System.currentTimeMillis() + 1600
                        st.hintMsg = "LOAD ${ZtekAudio.PART_NAMES[st.psPart]} ${st.psSlot + 1}: $nm"
                    }
                }
                4 -> {                            // K2 = SAVE (persisted)
                    ZtekAudio.presetSave(st.psPart, st.psSlot)
                    st.hintUntil = System.currentTimeMillis() + 1600
                    st.hintMsg = "SAVE ${ZtekAudio.PART_NAMES[st.psPart]} ${st.psSlot + 1}"
                }
                5 -> ZtekAudio.presetClear(st.psPart, st.psSlot)   // K3 = effacer
            }
            12 -> when (i) {                      // ASSIGN: typed matrix, out -> in only
                0 -> {                            // SEL sweeps dsts AND sources: sources are NOT valid dsts
                    val raw = (v * 50.999f).toInt().coerceIn(0, 50)
                    if (raw < ZtekAudio.ZT_NDST) {   // 0..34: the param inputs -> OK
                        st.sub = raw
                        st.asgSrc = ZtekAudio.assignSrc(st.sub)
                        st.asgDepth = ZtekAudio.assignDepth(st.sub)
                        hint(dstHint(st.sub, st.asgSrc))
                    } else {                          // 35..50 = a source used as destination: forbidden
                        forbidden("DST: VOIX 0-7 FX 8-25 SEQ 26-34")
                    }
                }
                1 -> {                            // PRE sweeps sources AND dsts: dsts are NOT valid sources
                    val raw = (v * 33.999f).toInt().coerceIn(0, 33)
                    if (raw <= 16) {                 // 0..15 sources, 16 = OFF
                        ZtekAudio.assign(st.sub, if (raw == 16) ZtekAudio.DST_OFF else raw, st.asgDepth)
                        hint(dstHint(st.sub, raw))
                    } else {                          // 17..33 = a param used as source: forbidden
                        forbidden("SRC: ${srcNamesUpTo(16)} OFF")
                    }
                }
                2 -> {                            // ALG = option LUDIQUE (mode ludique on/off)
                    st.ludiqueOn = v > 0.5f
                    st.encVals[2] = if (st.ludiqueOn) 1f else 0f
                    hint(if (st.ludiqueOn) "LUDIQUE ON: conseils actifs" else "LUDIQUE OFF: mode expert")
                }
                3 -> {                            // K1 = depth
                    st.asgDepth = v
                    ZtekAudio.assign(st.sub, st.asgSrc, v)
                }
            }
            else -> when (i) {                    // MAIN page
                0 -> st.selSlot = (v * 15.999f).toInt().coerceIn(0, 15)
                1 -> {                            // PRESET knob: scroll the free bank live while playing
                    val idx = (v * (ZtekAudio.ZT_PSLOTS - 0.001f)).toInt().coerceIn(0, ZtekAudio.ZT_PSLOTS - 1)
                    if (idx != lastEncPreset) {
                        lastEncPreset = idx
                        st.psSlot = idx
                        val nm = ZtekAudio.bankName(st.psPart, idx)
                        if (nm.isNotEmpty()) {                  // user save or factory preset
                            ZtekAudio.bankLoad(st.psPart, idx)
                            st.presetName = nm
                            if (idx < ZtekAudio.ZT_NPRESETS) presetIdx = idx
                        } else {
                            forbidden("SLT ${idx + 1} vide (${ZtekAudio.PART_NAMES[st.psPart]}) — SAVE d'abord")
                        }
                    }
                }
                2 -> ZtekAudio.setParam(ZtekAudio.AT_WAVE, v)
                3 -> ZtekAudio.setParam(ZtekAudio.AT_CUTOFF, v)
                4 -> ZtekAudio.setParam(ZtekAudio.AT_RESONANCE, v)
                5 -> ZtekAudio.setParam(ZtekAudio.AT_ENVMOD, v)
                6 -> ZtekAudio.setParam(ZtekAudio.AT_DECAY, v)
            }
        }
        lcd.invalidate()
    }

    // ---- 14 buttons (FM-1 order) ----
    override fun onButton(i: Int, down: Boolean) {
        val lbl = SurfaceView.BTN_LABELS[i]
        when (lbl) {
            "PLAY" -> if (down) {
                st.playing = !st.playing
                ZtekAudio.loopPlay(if (st.playing) 1 else 0)
            }
            "REC" -> {
                recDown = down
                if (down) {
                    val s = st.selSlot
                    if (ZtekAudio.loopSlotRec(s)) { ZtekAudio.loopRecStop(s); st.recArmed = false }
                    else { ZtekAudio.loopRecStart(s, 1); st.recArmed = true }
                    st.pop(9)
                }
            }
            "SEQ" -> if (down) {
                val now = System.currentTimeMillis()
                if (now - lastSeqTap < 350) {           // double-tap SEQ: generate + SEQ+ page
                    ZtekAudio.acidGenerate((Math.random() * 1e9).toInt())
                    setPage(if (st.page == 3) 0 else 3)
                    lastSeqTap = 0L
                } else {
                    lastSeqTap = now
                    st.seqOn = !st.seqOn
                    ZtekAudio.acidPlay(if (st.seqOn) 1 else 0)
                }
            }
            "ENV" -> {
                envDown = down
                if (down) {
                    if (recDown) setPage(11)                       // REC+ENV = ENV/tools page
                    else if (lfoDown) ZtekAudio.seqMutate(ZtekAudio.ACID_MUT_ARTICULATION)
                    else ZtekAudio.seqMutate(ZtekAudio.ACID_MUT_TIME)   // ENV alone = mutate TIME
                }
            }
            "LFO" -> {
                lfoDown = down
                if (down) {
                    if (recDown) setPage(10)                       // REC+LFO = LFO page
                    else if (envDown) ZtekAudio.seqMutate(ZtekAudio.ACID_MUT_ARTICULATION)
                    else ZtekAudio.seqMutate(ZtekAudio.ACID_MUT_PITCH)  // LFO alone = mutate PITCH
                }
            }
            "HOME" -> if (down) {
                if (recDown) setPage(12) else setPage(0)           // REC+HOME = ASSIGN page
            }
            "OCT-" -> if (down) { root = (root - 1).coerceIn(24, 72); ZtekAudio.setRoot(root) }
            "OCT+" -> if (down) { root = (root + 1).coerceIn(24, 72); ZtekAudio.setRoot(root) }
            "SEL" -> if (down) {
                st.engine = (st.engine + 1) % 5
                ZtekAudio.setEngine(st.engine)
            }
            "GLO" -> if (down) {                       // GLO = type de drive (le knob PRESET sert aux presets)
                st.driveType = (st.driveType + 1) % 5
                ZtekAudio.fxSet(ZtekAudio.FX_DRIVE_TYPE, st.driveType.toFloat())
                ZtekAudio.fxSet(ZtekAudio.FX_DRIVE, 0.6f)
            }
            "ARP" -> {
                if (down) ZtekAudio.punch(1, 8, 0.5f, 0.5f, 0.8f)
                else { ZtekAudio.punch(0, 0, 0f, 0f, 0f); ZtekAudio.punchTail(0.3f) }
            }
            "SAVE" -> if (down) {
                st.dly = (st.dly + 0.25f) % 1.0f
                ZtekAudio.fxSet(ZtekAudio.FX_DLY_MIX, st.dly)
            }
            "EDIT" -> if (down) {
                if (recDown) setPage(13)               // REC+EDIT = page PRESETS user
                else {                                  // EDIT = preset pré-patché suivant (ZuluFx style)
                    presetIdx = (presetIdx + 1) % ZtekAudio.ZT_NPRESETS
                    ZtekAudio.preset(presetIdx)
                    st.presetName = ZtekAudio.presetName(presetIdx)
                    st.hintUntil = System.currentTimeMillis() + 1600
                    st.hintMsg = "PRESET: ${st.presetName}"
                    lcd.invalidate()
                }
            }
            "FX" -> if (down) { ZtekAudio.allNotesOff(); st.curNote = -1 }
        }
        syncSlots()
        lcd.invalidate()
        surface.invalidate()
    }

    // ---- white keys = looper slots ----
    override fun onWhiteKey(slot: Int, down: Boolean, longPress: Boolean) {
        st.selSlot = slot
        if (longPress) {
            if (ZtekAudio.loopSlotRec(slot)) { ZtekAudio.loopRecStop(slot); st.recArmed = false }
            else { ZtekAudio.loopRecStart(slot, 1); st.recArmed = true }
        } else if (down) {
            if (ZtekAudio.loopSlotRec(slot)) ZtekAudio.loopRecStop(slot)
            else ZtekAudio.loopToggle(slot)
            if (st.recArmed && !ZtekAudio.loopSlotRec(slot)) st.recArmed = false
        }
        syncSlots()
        lcd.invalidate()
        surface.invalidate()
    }

    // ---- black keys = notes ----
    override fun onBlackKey(note: Int, down: Boolean) {
        if (down) {
            st.curNote = note
            ZtekAudio.noteOn(note, 100, 0, 0)
        } else {
            ZtekAudio.noteOff(note)
            if (st.curNote == note) st.curNote = -1
        }
        lcd.invalidate()
        surface.invalidate()
    }

    private fun syncSlots() {
        for (i in 0 until 16) {
            st.slotOn[i] = ZtekAudio.loopSlotOn(i)
            st.slotRec[i] = ZtekAudio.loopSlotRec(i)
        }
    }
}
