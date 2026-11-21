successfully downloaded text file (SHA: a6f5b111641c244cd99881c1fdf7c4435e804b5f)
/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * SurfaceView: the APK's clone of the FM-1 control surface — 7 encoders (SELECT/PRESET/ALGO/
 * K1..K4), 14 buttons, 27 keys F3..G5 (16 white keys = the looper/PoW! slots, 11 black keys =
 * playable notes). Same charte as the LCD (black, white mono ink, fluo accents). Touch zones
 * are reported through SurfaceListener; the FM-1 LCD itself is not touch, so the LcdView
 * stays display-only. */
package com.zulutek.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

interface SurfaceListener {
    fun onEncoder(i: Int, v: Float)
    fun onEncoderPress(i: Int) {}    // appui court sur un encodeur (SEL pushable du FM-1)
    fun onEncoderHold(i: Int) {}     // maintien long sur un encodeur (SEL maintenu + touche = LOAD preset)
    fun onButton(i: Int, down: Boolean)
    fun onButtonLong(i: Int)
    fun onWhiteKey(slot: Int, down: Boolean, longPress: Boolean)
    fun onBlackKey(note: Int, down: Boolean)
}

class SurfaceView(ctx: Context, val st: LcdState, val listener: SurfaceListener) : View(ctx) {

    companion object {
        /* the FM-1's 14 buttons, same order (BTN_NAMES of the firmware) */
        val BTN_LABELS = arrayOf("FX", "SEL", "ENV", "LFO", "EDIT", "GLO", "HOME",
                                 "SAVE", "ARP", "SEQ", "PLAY", "REC", "OCT-", "OCT+")
        /* labels custom Zulu (ancienne surface), affichés si OPTIONS > LABELS = ZULU ; le dispatch reste par index */
        val ZULU_LABELS = arrayOf("PLAY", "REC", "SEQ", "GEN", "TAP", "SCL", "R-",
                                  "R+", "ERS", "DRV", "DLY", "REV", "POW", "MUT")
        // 27 keys F3..G5: white keys (16) carry slot indices, black keys (11) MIDI notes
        val BLACK_NOTES = intArrayOf(54, 56, 58, 61, 63, 66, 68, 70, 73, 75, 78)
        val WHITE_NOTES = intArrayOf(53, 55, 57, 59, 60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79)
    }

    // layout selon st.viewCompact (OPTIONS > VUE = CLAV/AUTO) : clavier -30 %, contrôles agrandis (APK only)
    private fun encH() = if (st.viewCompact) 96f else 72f
    private fun encCy() = encH() / 2f
    private fun encR() = if (st.viewCompact) 34f else 24f
    private fun btnY0() = encH() + 4f
    private fun btnRowH() = if (st.viewCompact) 36f else 30f
    private fun btnH() = btnRowH() - 6f
    private fun keyTop() = btnY0() + 2 * btnRowH() + 14f      // 150 plein, 186 compact
    private fun keyBot() = 270f
    private fun blackH() = (keyBot() - keyTop()) * 0.75f       // 90 plein, 63 compact

    private val mono = Paint().apply { typeface = Typeface.MONOSPACE; isAntiAlias = false; style = Paint.Style.FILL }
    private val frame = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val handler = Handler(Looper.getMainLooper())

    // touch state
    private var zone = 0            // 0 none, 1 encoder, 2 button, 3 white key, 4 black key
    private var idx = -1
    private var startY = 0f
    private var startVal = 0f
    private var longPosted = false
    private var downTime = 0L
    private var encMoved = false
    private val encHoldRunnable = Runnable {
        if (zone == 1 && idx >= 0 && !encMoved) listener.onEncoderHold(idx)
    }
    private val longRunnable = Runnable {
        longPosted = false
        when (zone) {
            3 -> if (idx >= 0) listener.onWhiteKey(idx, true, true)
            2 -> if (idx >= 0) listener.onButtonLong(idx)
        }
    }

    private fun encX(i: Int) = width * (i + 0.5f) / 7f
    private fun whiteW() = width / 16f

    private fun zoneAt(x: Float, y: Float): Pair<Int, Int> {
        return when {
            y < encH() -> 1 to ((x / width * 7f).toInt().coerceIn(0, 6))
            y < btnY0() + 2 * btnRowH() -> 2 to ((((y - btnY0()) / btnRowH()).toInt() * 7 + (x / width * 7f).toInt()).coerceIn(0, 13))
            y < keyTop() + blackH() -> {
                val w = whiteW()
                for (bi in BLACK_NOTES.indices) {
                    val e = blackEntry(bi)
                    if (x >= e.first && x <= e.first + w * 0.6f) return 4 to bi
                }
                3 to ((x / w).toInt().coerceIn(0, 15))
            }
            else -> 3 to ((x / whiteW()).toInt().coerceIn(0, 15))
        }
    }

    // keyLayout: 27 entries in chromatic order (x0, isBlack), F3..G5
    private val keyLayout = ArrayList<Pair<Float, Boolean>>()
    private fun buildLayout() {
        keyLayout.clear()
        val w = whiteW()
        var x = 0f
        for (midi in 53..79) {
            val black = (midi % 12) == 1 || (midi % 12) == 3 || (midi % 12) == 6 ||
                        (midi % 12) == 8 || (midi % 12) == 10
            if (black) keyLayout.add(x - w * 0.3f to true)      // sits on the boundary, drawn on top
            else { keyLayout.add(x to false); x += w }
        }
    }

    /* the bi-th black key entry of keyLayout */
    private fun blackEntry(bi: Int): Pair<Float, Boolean> {
        var n = 0
        for (e in keyLayout) {
            if (e.second) {
                if (n == bi) return e
                n++
            }
        }
        return 0f to true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildLayout()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (keyLayout.isEmpty()) buildLayout()
        c.drawColor(LcdView.BG)

        // ---- encoders ----
        for (i in 0 until 7) {
            val cx = encX(i)
            val cy = encCy()
            frame.color = if (st.popUntil[i] > System.currentTimeMillis()) LcdView.FLUO_TURQ else LcdView.DIM
            c.drawCircle(cx, cy, encR(), frame)
            val v = st.encVals[i]
            frame.color = LcdView.FLUO_TURQ
            val a = -2.356f + v * 4.712f
            c.drawLine(cx, cy, cx + encR() * 0.75f * cos(a), cy + encR() * 0.75f * sin(a), frame)
            mono.color = LcdView.DIM; mono.textSize = 9f
            c.drawText(st.encLabels[i], cx - 9f, encH() - 6f, mono)
        }

        // ---- 14 buttons, 2 rows of 7 ----
        val bw = width / 7f
        for (i in 0 until 14) {
            val row = i / 7
            val col = i % 7
            val x0 = col * bw + 3f
            val y0 = btnY0() + row * btnRowH()
            val active = (i == 10 && st.playing) || (i == 11 && st.recArmed) || (i == 9 && st.seqOn) ||
                         (st.slotRec.getOrElse(st.selSlot) { false } && i == 11) ||
                         (i == 5 && st.sirenMode && (System.currentTimeMillis() / 260) % 2 == 0L) ||
                         ((i == 5 || i == 11) && st.loopRecArm >= 0 && (System.currentTimeMillis() / 260) % 2 == 0L)
            // rappel lock clavier MIDI (charte) : HOME clignote une fois (260 ms) toutes les 20 s sans jouer
            val nowMs = System.currentTimeMillis()
            val homeNudge = i == 6 && st.lastKeyUse > 0 &&
                           nowMs - st.lastKeyUse > 20000 && (nowMs % 20000) < 260
            frame.color = when {
                active -> LcdView.REC_RED
                homeNudge -> LcdView.FLUO_TURQ
                else -> LcdView.DIM
            }
            c.drawRect(x0, y0, x0 + bw - 6f, y0 + btnH(), frame)
            mono.color = when {
                active -> LcdView.INK
                homeNudge -> LcdView.FLUO_TURQ
                else -> LcdView.DIM
            }
            mono.textSize = 9f
            val labels = if (st.labelsZulu) ZULU_LABELS else BTN_LABELS
            c.drawText(labels[i], x0 + 4f, y0 + btnH() - 8f, mono)
        }

        // ---- keys: whites then blacks on top ----
        val w = whiteW()
        for (i in 0 until 16) {
            val x0 = i * w
            val on = st.slotOn[i]
            val rec = st.slotRec[i]
            val now = System.currentTimeMillis()
            val blink = (now / 260) % 2 == 0L
            frame.color = when {
                rec && blink -> LcdView.REC_RED
                rec -> LcdView.REC_RED_OFF
                on -> LcdView.FLUO_YEL
                i == st.selSlot -> LcdView.AMBER
                else -> LcdView.DIM
            }
            c.drawRect(x0 + 1f, keyTop(), x0 + w - 1f, keyBot(), frame)
        }
        for (bi in BLACK_NOTES.indices) {
            val x0 = blackEntry(bi).first
            val note = BLACK_NOTES[bi]
            frame.color = if (note == st.curNote) LcdView.FLUO_PINK else Color.argb(255, 60, 60, 60)
            c.drawRect(x0, keyTop(), x0 + w * 0.6f, keyTop() + blackH(), frame)
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val (z, i) = zoneAt(x, y)
                zone = z; idx = i; startY = y; longPosted = false
                when (z) {
                    1 -> { startVal = st.encVals[i]; downTime = System.currentTimeMillis(); encMoved = false
                           handler.postDelayed(encHoldRunnable, 600) }
                    2 -> {
                        listener.onButton(i, true)
                        handler.postDelayed(longRunnable, 600)
                        longPosted = true
                    }
                    3 -> {
                        handler.postDelayed(longRunnable, 500)
                        longPosted = true
                        st.lastKeyUse = System.currentTimeMillis()   // rappel HOME 20 s (charte)
                        listener.onWhiteKey(i, true, false)
                    }
                    4 -> {
                        val note = BLACK_NOTES[i]
                        st.lastKeyUse = System.currentTimeMillis()   // rappel HOME 20 s (charte)
                        listener.onBlackKey(note, true)
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (zone == 1) {
                    encMoved = true
                    handler.removeCallbacks(encHoldRunnable)
                    val v = (startVal + (startY - y) / 90f).coerceIn(0f, 1f)
                    st.encVals[idx] = v
                    listener.onEncoder(idx, v)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longRunnable)
                handler.removeCallbacks(encHoldRunnable)
                when (zone) {
                    1 -> if (!encMoved && System.currentTimeMillis() - downTime < 350)
                             listener.onEncoderPress(idx)   // tap encodeur = appui SEL/... du FM-1
                    2 -> listener.onButton(idx, false)
                    3 -> if (longPosted) listener.onWhiteKey(idx, false, false)
                    4 -> listener.onBlackKey(BLACK_NOTES[idx], false)
                }
                zone = 0; idx = -1; longPosted = false
                invalidate()
                return true
            }
        }
        return false
    }
}
