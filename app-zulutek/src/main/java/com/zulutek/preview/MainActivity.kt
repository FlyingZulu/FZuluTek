/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * ZuluTek preview: LcdView renders the FM-1's 240x240 LCD (display only), SurfaceView is the
 * cloned control surface (7 encoders, 14 buttons, 27 keys F3..G5). Everything drives the
 * native ztek engine through ZtekAudio.
 *
 * Mapping (docs/ZULUTEK.md):
 *  - 16 white keys = the 16 looper slots (tap = play/stop, long = rec arm/stop)
 *  - 11 black keys = playable notes (gate while held)
 *  - encoders: SEL = slot select, PRE = drive type, ALG = WAVE, K1..K4 = CUTOFF/RESO/ENVMOD/DECAY
 *  - buttons:  PLAY REC SEQ GEN TAP SCL R- R+ ERS DRV DLY REV POW MUT */
package com.zulutek.preview

import android.app.Activity
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ZtekAudio.start()
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
    }

    override fun onDestroy() {
        ZtekAudio.stop()
        super.onDestroy()
    }

    // ---- encoders ----
    override fun onEncoder(i: Int, v: Float) {
        st.pop(i)
        when (i) {
            0 -> st.selSlot = (v * 15.999f).toInt().coerceIn(0, 15)
            1 -> {
                val t = (v * 4.999f).toInt().coerceIn(0, 4)
                if (t != st.driveType) {
                    st.driveType = t
                    ZtekAudio.fxSet(ZtekAudio.FX_DRIVE_TYPE, t.toFloat())
                    ZtekAudio.fxSet(ZtekAudio.FX_DRIVE, 0.6f)
                }
            }
            2 -> ZtekAudio.setParam(ZtekAudio.AT_WAVE, v)
            3 -> ZtekAudio.setParam(ZtekAudio.AT_CUTOFF, v)
            4 -> ZtekAudio.setParam(ZtekAudio.AT_RESONANCE, v)
            5 -> ZtekAudio.setParam(ZtekAudio.AT_ENVMOD, v)
            6 -> ZtekAudio.setParam(ZtekAudio.AT_DECAY, v)
        }
        lcd.invalidate()
    }

    // ---- 14 buttons ----
    override fun onButton(i: Int, down: Boolean) {
        when (SurfaceView.BTN_LABELS[i]) {
            "PLAY" -> if (down) {
                st.playing = !st.playing
                ZtekAudio.loopPlay(if (st.playing) 1 else 0)
            }
            "REC" -> if (down) {
                val s = st.selSlot
                if (ZtekAudio.loopSlotRec(s)) { ZtekAudio.loopRecStop(s); st.recArmed = false }
                else { ZtekAudio.loopRecStart(s, 1); st.recArmed = true }
                st.pop(9)
            }
            "SEQ" -> if (down) {
                st.seqOn = !st.seqOn
                ZtekAudio.acidPlay(if (st.seqOn) 1 else 0)
            }
            "GEN" -> if (down) ZtekAudio.acidGenerate((Math.random() * 1e9).toInt())
            "TAP" -> if (down) tapTempo()
            "SCL" -> if (down) {
                scale = (scale + 1) % 8
                ZtekAudio.setScale(scale)
                ZtekAudio.acidGenerate((Math.random() * 1e9).toInt())
            }
            "R-" -> if (down) { root = (root - 1).coerceIn(24, 72); ZtekAudio.setRoot(root) }
            "R+" -> if (down) { root = (root + 1).coerceIn(24, 72); ZtekAudio.setRoot(root) }
            "ERS" -> if (down) { ZtekAudio.loopErase(st.selSlot); st.recArmed = false }
            "DRV" -> if (down) {
                st.driveType = (st.driveType + 1) % 5
                ZtekAudio.fxSet(ZtekAudio.FX_DRIVE_TYPE, st.driveType.toFloat())
                ZtekAudio.fxSet(ZtekAudio.FX_DRIVE, 0.6f)
                st.encVals[1] = st.driveType / 4f
            }
            "DLY" -> if (down) {
                st.dly = ((st.dly + 0.25f) % 1.0f)
                ZtekAudio.fxSet(ZtekAudio.FX_DLY_MIX, st.dly)
            }
            "REV" -> if (down) {
                st.rev = ((st.rev + 0.25f) % 1.0f)
                ZtekAudio.fxSet(ZtekAudio.FX_REV_MIX, st.rev)
            }
            "POW" -> {
                if (down) ZtekAudio.punch(1, 8, 0.5f, 0.5f, 0.8f)
                else { ZtekAudio.punch(0, 0, 0f, 0f, 0f); ZtekAudio.punchTail(0.3f) }
            }
            "MUT" -> if (down) { ZtekAudio.allNotesOff(); st.curNote = -1 }
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

    private fun tapTempo() {
        val now = System.currentTimeMillis()
        tapTimes[tapIdx % 8] = now
        tapIdx++
        if (tapIdx >= 4) {
            val n = 8.coerceAtMost(tapIdx)
            var span = 0L
            for (i in 1 until n) span += tapTimes[(tapIdx - i) % 8] - tapTimes[(tapIdx - i - 1) % 8]
            if (span > 0) {
                bpm = (60000f * (n - 1) / span).coerceIn(30f, 300f)
                ZtekAudio.setBpm(bpm)
                st.bpm = bpm.roundToInt()
            }
        }
        st.pop(9)
        lcd.invalidate()
    }

    private fun syncSlots() {
        for (i in 0 until 16) {
            st.slotOn[i] = ZtekAudio.loopSlotOn(i)
            st.slotRec[i] = ZtekAudio.loopSlotRec(i)
        }
    }
}
