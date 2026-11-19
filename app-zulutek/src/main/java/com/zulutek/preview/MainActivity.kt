/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * ZuluTek preview: one LcdView renders the FM-1's 240x240 LCD (docs/ZULUTEK-UI-STYLE.md);
 * touches are mapped into logical LCD coordinates and drive the native ztek engine through
 * ZtekAudio. Pad zone = play (X note / Y cutoff), rows = knobs / engines / FX, bottom = the
 * 16 looper keys (tap = play/stop, long = rec). */
package com.zulutek.preview

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private val st = LcdState()
    private lateinit var lcd: LcdView

    private var root = 45
    private var scale = 0
    private var bpm = 120f
    private val tapTimes = LongArray(8)
    private var tapIdx = 0
    private var curNote = -1

    // touch tracking
    private var touchZone = 0          // 0 none, 1 top bar, 2 pad, 3 knob, 4 engine, 5 fx, 6 key
    private var zoneIdx = -1
    private var startY = 0f
    private var startVal = 0f
    private var longPressPosted = false
    private val handler = Handler(Looper.getMainLooper())
    private val keyLongPress = Runnable {
        longPressPosted = false
        if (touchZone == 6 && zoneIdx >= 0) {
            if (ZtekAudio.loopSlotRec(zoneIdx)) ZtekAudio.loopRecStop(zoneIdx)
            else ZtekAudio.loopRecStart(zoneIdx, 1)
            syncSlots(); st.pop(8)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ZtekAudio.start()
        ZtekAudio.setEngine(0)
        ZtekAudio.setBpm(bpm)
        ZtekAudio.setRoot(root)
        ZtekAudio.setScale(scale)

        lcd = LcdView(this, st)
        val frame = FrameLayout(this).apply { setBackgroundColor(LcdView.BG) }
        frame.addView(lcd, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        setContentView(frame)

        lcd.setOnTouchListener { _, ev -> onLcdTouch(ev) }
    }

    override fun onDestroy() {
        ZtekAudio.stop()
        super.onDestroy()
    }

    /* logical layout (240x240), mirrors LcdView.onDraw */
    private fun zoneAt(x: Float, y: Float): Pair<Int, Int> {
        return when {
            y < 22f -> 1 to topButton(x)
            y < 122f -> 2 to 0                                   // pad
            y < 152f -> 3 to ((x - 8f) / 57f).toInt().coerceIn(0, 3)   // knobs
            y < 176f -> 4 to ((x - 8f) / 45f).toInt().coerceIn(0, 4)   // engines
            y < 202f -> 5 to fxCell(x)                            // FX row
            else -> 6 to keyAt(x, y)
        }
    }

    private fun topButton(x: Float): Int = when {
        x >= 146f && x < 182f -> 0      // PLAY/STOP
        x >= 182f && x < 204f -> 1      // BPM tap
        else -> 2                       // SEQ
    }

    private fun fxCell(x: Float): Int = when {
        x < 58f -> 0                    // DRIVE cycle
        x < 118f -> 1                   // DLY
        x < 178f -> 2                   // REV
        else -> 3                       // POW!
    }

    private fun keyAt(x: Float, y: Float): Int {
        val col = ((x - 8f) / 29f).toInt().coerceIn(0, 7)
        val row = ((y - 204f) / 18f).toInt().coerceIn(0, 1)
        return row * 8 + col
    }

    private fun onLcdTouch(ev: MotionEvent): Boolean {
        val s = lcd.width.toFloat() / LcdView.W
        if (s <= 0f) return false
        val x = ev.x / s
        val y = ev.y / s
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val (z, idx) = zoneAt(x, y)
                touchZone = z; zoneIdx = idx; startY = y; longPressPosted = false
                when (z) {
                    1 -> when (idx) {
                        0 -> { st.playing = !st.playing; ZtekAudio.loopPlay(if (st.playing) 1 else 0); st.pop(9) }
                        1 -> tapTempo()
                        2 -> { st.seqOn = !st.seqOn; ZtekAudio.acidPlay(if (st.seqOn) 1 else 0); st.pop(9) }
                    }
                    2 -> {
                        st.padX = (x - 8f) / 224f; st.padY = (y - 22f) / 96f
                        curNote = noteAt(st.padX)
                        st.padNote = curNote
                        ZtekAudio.noteOn(curNote, 100, 0, 0)
                        ZtekAudio.setParam(ZtekAudio.AT_CUTOFF, 1f - st.padY)
                        st.knobVals[0] = 1f - st.padY
                        st.pop(0)
                    }
                    3 -> { startVal = st.knobVals[idx]; st.pop(1 + idx) }
                    4 -> {
                        st.engine = idx
                        ZtekAudio.allNotesOff()
                        ZtekAudio.setEngine(idx)
                        st.pop(5)
                    }
                    5 -> when (idx) {
                        0 -> {
                            st.driveType = (st.driveType + 1) % 5
                            ZtekAudio.fxSet(ZtekAudio.FX_DRIVE_TYPE, st.driveType.toFloat())
                            ZtekAudio.fxSet(ZtekAudio.FX_DRIVE, 0.6f)
                            st.pop(5)
                        }
                        1 -> { startVal = st.dly; st.pop(6) }
                        2 -> { startVal = st.rev; st.pop(7) }
                        3 -> {
                            st.powHeld = true
                            ZtekAudio.punch(1, 8, 0.5f, 0.5f, 0.8f)
                            st.pop(7)
                        }
                    }
                    6 -> {
                        handler.postDelayed(keyLongPress, 500)
                        longPressPosted = true
                    }
                }
                lcd.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                when (touchZone) {
                    2 -> {
                        st.padX = ((x - 8f) / 224f).coerceIn(0f, 1f)
                        st.padY = ((y - 22f) / 96f).coerceIn(0f, 1f)
                        val n = noteAt(st.padX)
                        if (n != curNote) {
                            if (curNote >= 0) ZtekAudio.noteOff(curNote)
                            curNote = n
                            st.padNote = n
                            ZtekAudio.noteOn(n, 100, 0, 1)
                        }
                        ZtekAudio.setParam(ZtekAudio.AT_CUTOFF, 1f - st.padY)
                        st.knobVals[0] = 1f - st.padY
                        st.pop(0)
                    }
                    3 -> {
                        val v = (startVal + (startY - y) / 48f).coerceIn(0f, 1f)
                        st.knobVals[zoneIdx] = v
                        ZtekAudio.setParam(knobParam(zoneIdx), v)
                    }
                    5 -> {
                        if (zoneIdx == 1) {
                            st.dly = (startVal + (startY - y) / 48f).coerceIn(0f, 1f)
                            ZtekAudio.fxSet(ZtekAudio.FX_DLY_MIX, st.dly)
                        } else if (zoneIdx == 2) {
                            st.rev = (startVal + (startY - y) / 48f).coerceIn(0f, 1f)
                            ZtekAudio.fxSet(ZtekAudio.FX_REV_MIX, st.rev)
                        }
                    }
                }
                lcd.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(keyLongPress)
                if (touchZone == 6 && zoneIdx >= 0 && longPressPosted) {
                    // it was a tap (long press never fired): toggle play / stop rec
                    if (ZtekAudio.loopSlotRec(zoneIdx)) ZtekAudio.loopRecStop(zoneIdx)
                    else ZtekAudio.loopToggle(zoneIdx)
                    syncSlots()
                }
                if (touchZone == 2) {
                    if (curNote >= 0) ZtekAudio.noteOff(curNote)
                    curNote = -1
                    st.padX = -1f; st.padY = -1f; st.padNote = -1
                }
                if (touchZone == 5 && zoneIdx == 3) {
                    st.powHeld = false
                    ZtekAudio.punch(0, 0, 0f, 0f, 0f)
                    ZtekAudio.punchTail(0.3f)
                }
                touchZone = 0; zoneIdx = -1; longPressPosted = false
                lcd.invalidate()
                return true
            }
        }
        return false
    }

    private fun noteAt(px: Float): Int = root + (px * 24.999f).toInt().coerceIn(0, 24)

    private fun knobParam(i: Int) = when (i) {
        0 -> ZtekAudio.AT_CUTOFF
        1 -> ZtekAudio.AT_RESONANCE
        2 -> ZtekAudio.AT_ENVMOD
        else -> ZtekAudio.AT_DECAY
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
    }

    private fun syncSlots() {
        for (i in 0 until 16) {
            st.slotOn[i] = ZtekAudio.loopSlotOn(i)
            st.slotRec[i] = ZtekAudio.loopSlotRec(i)
        }
        lcd.invalidate()
    }
}
