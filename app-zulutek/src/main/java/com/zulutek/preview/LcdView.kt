/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * LcdView: the APK's imitation of the FM-1's 240x240 LCD (ST7789), per docs/ZULUTEK-UI-STYLE.md:
 * black background, white MONOSPACE ink, fluo accents only for important states
 * (ZU pink / LU yellow witnesses, windows turquoise, REC red blinking, warnings amber).
 * Everything is drawn in logical 240x240 coordinates, scaled to the view. */
package com.zulutek.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

class LcdState {
    var bpm = 120
    var playing = false
    var seqOn = false
    var engine = 0
    val engineNames = arrayOf("303", "PLUK", "FM", "SUB", "8BIT")
    var driveType = 0
    val driveNames = arrayOf("OFF", "OD", "RAT", "FUZZ", "MOJO")
    val knobLabels = arrayOf("CUT", "RES", "ENV", "DEC")
    val knobVals = floatArrayOf(0.7f, 0.25f, 0.5f, 0.55f)
    var dly = 0.25f
    var rev = 0.2f
    var powHeld = false
    var padX = -1f
    var padY = -1f
    var padNote = -1
    val slotOn = BooleanArray(16)
    val slotRec = BooleanArray(16)
    val popUntil = LongArray(10)      // per-widget pop animation (400 ms shrink after a hold)
    fun pop(i: Int) { popUntil[i] = System.currentTimeMillis() + 1200 }
}

class LcdView(ctx: Context, val st: LcdState) : View(ctx) {

    companion object {
        const val W = 240
        const val H = 240
        val BG = Color.BLACK
        val INK = Color.WHITE
        val DIM = Color.argb(255, 120, 120, 120)
        val FLUO_PINK = Color.rgb(255, 45, 160)     // ZU
        val FLUO_YEL = Color.rgb(255, 238, 0)       // LU
        val FLUO_TURQ = Color.rgb(0, 255, 224)      // windows / pops
        val REC_RED = Color.argb(255, 255, 60, 60)
        val REC_RED_OFF = Color.argb(255, 96, 32, 32)
        val AMBER = Color.argb(255, 255, 200, 60)
    }

    private val mono = Paint().apply {
        typeface = Typeface.MONOSPACE
        isAntiAlias = false
    }
    private val frame = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f }

    private fun text(c: Canvas, x: Float, y: Float, s: String, color: Int, size: Float) {
        mono.color = color
        mono.textSize = size
        mono.style = Paint.Style.FILL
        c.drawText(s, x, y, mono)
    }

    private fun box(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        frame.color = color
        c.drawRect(x0, y0, x1, y1, frame)
    }

    /* widget i pops (turquoise frame) while the animation runs */
    private fun popColor(i: Int, base: Int): Int {
        val left = st.popUntil[i] - System.currentTimeMillis()
        return if (left > 0) FLUO_TURQ else base
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = width.toFloat() / W
        c.drawColor(BG)
        c.save()
        c.scale(s, s)

        val now = System.currentTimeMillis()
        val blink = (now / 260) % 2 == 0L

        // ---- top bar: title, transport ----
        text(c, 6f, 15f, "ZULUTEK", FLUO_YEL, 12f)
        text(c, 150f, 15f, if (st.playing) "PLAY" else "STOP", if (st.playing) FLUO_PINK else DIM, 10f)
        text(c, 186f, 15f, "${st.bpm}", INK, 10f)
        text(c, 208f, 15f, if (st.seqOn) "SEQ" else "seq", if (st.seqOn) FLUO_TURQ else DIM, 10f)

        // ---- pad (Kaoss zone), y 22..118 ----
        val padX0 = 8f; val padY0 = 22f; val padX1 = 232f; val padY1 = 118f
        box(c, padX0, padY0, padX1, padY1, popColor(0, DIM))
        for (i in 1 until 8) {
            frame.color = Color.argb(255, 40, 40, 40)
            c.drawLine(padX0 + (padX1 - padX0) * i / 8f, padY0, padX0 + (padX1 - padX0) * i / 8f, padY1, frame)
        }
        for (i in 1 until 4) {
            c.drawLine(padX0, padY0 + (padY1 - padY0) * i / 4f, padX1, padY0 + (padY1 - padY0) * i / 4f, frame)
        }
        if (st.padX >= 0f) {
            mono.color = FLUO_YEL
            mono.style = Paint.Style.FILL
            c.drawCircle(padX0 + st.padX * (padX1 - padX0), padY0 + st.padY * (padY1 - padY0), 5f, mono)
            mono.style = Paint.Style.STROKE
            val n = st.padNote
            if (n >= 0) text(c, padX1 - 40f, padY1 - 6f, "n$n", FLUO_PINK, 10f)
        }
        text(c, padX0 + 3f, padY0 + 10f, "XY", DIM, 9f)

        // ---- knobs row, y 124..148: 4 cells ----
        val ky0 = 124f; val ky1 = 148f
        for (i in 0 until 4) {
            val x0 = 8f + i * 57f
            box(c, x0, ky0, x0 + 53f, ky1, popColor(1 + i, DIM))
            text(c, x0 + 3f, ky0 + 10f, st.knobLabels[i], DIM, 8f)
            val v = st.knobVals[i]
            text(c, x0 + 3f, ky0 + 22f, "${(v * 100).toInt()}", INK, 10f)
            // the value bar
            frame.color = FLUO_TURQ
            c.drawLine(x0 + 3f, ky1 - 4f, x0 + 3f + 47f * v, ky1 - 4f, frame)
        }

        // ---- engine row, y 154..172: 5 cells ----
        val ey0 = 154f; val ey1 = 172f
        for (i in st.engineNames.indices) {
            val x0 = 8f + i * 45f
            val sel = i == st.engine
            box(c, x0, ey0, x0 + 41f, ey1, if (sel) FLUO_PINK else DIM)
            text(c, x0 + 3f, ey0 + 12f, st.engineNames[i], if (sel) INK else DIM, 9f)
        }

        // ---- FX row, y 178..198: DRIVE cell, DLY, REV, POW! ----
        val fy0 = 178f; val fy1 = 198f
        box(c, 8f, fy0, 56f, fy1, popColor(5, DIM))
        text(c, 11f, fy0 + 10f, "DRV", DIM, 8f)
        text(c, 11f, fy0 + 20f, st.driveNames[st.driveType], INK, 9f)
        box(c, 60f, fy0, 116f, fy1, popColor(6, DIM))
        text(c, 63f, fy0 + 10f, "DLY", DIM, 8f)
        text(c, 63f, fy0 + 20f, "${(st.dly * 100).toInt()}", INK, 9f)
        box(c, 120f, fy0, 176f, fy1, popColor(7, DIM))
        text(c, 123f, fy0 + 10f, "REV", DIM, 8f)
        text(c, 123f, fy0 + 20f, "${(st.rev * 100).toInt()}", INK, 9f)
        box(c, 180f, fy0, 232f, fy1, if (st.powHeld) REC_RED else DIM)
        text(c, 190f, fy0 + 13f, "POW!", if (st.powHeld) INK else DIM, 9f)

        // ---- 16 keys, 2 rows of 8, y 204..238 ----
        for (i in 0 until 16) {
            val col = i % 8
            val row = i / 8
            val x0 = 8f + col * 29f
            val y0 = 204f + row * 18f
            val on = st.slotOn[i]
            val rec = st.slotRec[i]
            val color = if (rec && blink) REC_RED else if (rec) REC_RED_OFF else if (on) FLUO_YEL else DIM
            box(c, x0, y0, x0 + 26f, y0 + 15f, color)
            if (on || rec) text(c, x0 + 8f, y0 + 11f, "${i + 1}", INK, 8f)
        }

        c.restore()
    }
}
