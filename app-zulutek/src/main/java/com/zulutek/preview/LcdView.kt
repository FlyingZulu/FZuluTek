/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * LcdView: the APK's imitation of the FM-1's 240x240 non-touch LCD (ST7789), per
 * docs/ZULUTEK-UI-STYLE.md: black background, white MONOSPACE ink, fluo accents only for
 * important states (ZU pink / LU yellow witnesses, windows turquoise, REC red blinking,
 * warnings amber). Display only — all interaction goes through the cloned surface
 * (SurfaceView). Drawn in logical 240x240 coordinates, scaled to the view. */
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
    var recArmed = false
    var engine = 0
    val engineNames = arrayOf("ATEK303", "PLUK", "FM", "SUB", "8BIT")
    var selSlot = 0
    var encLabels = arrayOf("SEL", "PRE", "ALG", "K1", "K2", "K3", "K4")   // re-labelled per page
    val encVals = floatArrayOf(0f, 0f, 0f, 0.7f, 0.25f, 0.5f, 0.55f)
    var driveType = 0
    val driveNames = arrayOf("OFF", "OD", "RAT", "FUZZ", "MOJO")
    var dly = 0.25f
    var rev = 0.2f
    var curNote = -1
    val slotOn = BooleanArray(16)
    val slotRec = BooleanArray(16)
    val popUntil = LongArray(10)      // per-widget pop animation (frame turquoise while it runs)
    fun pop(i: Int) { popUntil[i] = System.currentTimeMillis() + 1200 }

    /* pages: 0 MAIN, 3 SEQ+ (advanced), 10 LFO, 11 ENV/TOOLS, 12 ASSIGN */
    var page = 0
    var sub = 0                       // selected sub-module on pages 10/11 (0..7), dst on 12
    val pageNames = mapOf(0 to "MAIN", 3 to "SEQ+", 10 to "LFO", 11 to "ENV", 12 to "ASSIGN")
    val srcVals = FloatArray(16)      // live source values 0..1 (16 bars)
    var lfoSync = false
    var lfoTrig = false
    val seqkVals = FloatArray(5)      // TM LOCK, TM LEN, MUT, PLAY, RAND
    var asgSrc = 255
    var asgDepth = 0f
    var noEntryUntil = 0L             // "sens interdit" pop animation (forbidden assignment)
    var sugMsg = ""                   // suggestions shown under the no-entry sign
    var hintUntil = 0L                // transient contextual hint (valid assignment)
    var hintMsg = ""
    var ludiqueOn = true              // mode ludique (option LUDIQUE, FM-1: OPTIONS > LUDIQUE)
    var presetName = "ACID 303"       // current pre-patched preset
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
        style = Paint.Style.FILL
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

    private fun popColor(i: Int, base: Int): Int =
        if (st.popUntil[i] > System.currentTimeMillis()) FLUO_TURQ else base

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = width.toFloat() / W
        c.drawColor(BG)
        c.save()
        c.scale(s, s)

        val now = System.currentTimeMillis()
        val blink = (now / 260) % 2 == 0L

        // ---- top bar ----
        text(c, 6f, 15f, "ZULUTEK", FLUO_YEL, 12f)
        text(c, 140f, 15f, if (st.playing) "PLAY" else "STOP", if (st.playing) FLUO_PINK else DIM, 10f)
        text(c, 176f, 15f, "${st.bpm}", INK, 10f)
        text(c, 200f, 15f, if (st.seqOn) "SEQ" else "seq", if (st.seqOn) FLUO_TURQ else DIM, 10f)
        if (st.recArmed && blink) text(c, 222f, 15f, "R", REC_RED, 10f)

        // ---- engine + drive line ----
        text(c, 6f, 34f, "ENG", DIM, 9f)
        text(c, 34f, 34f, st.engineNames[st.engine], FLUO_PINK, 11f)
        text(c, 110f, 34f, "DRV", DIM, 9f)
        text(c, 138f, 34f, st.driveNames[st.driveType], INK, 10f)
        text(c, 190f, 34f, "n${st.curNote}", if (st.curNote >= 0) FLUO_YEL else DIM, 10f)

        // ---- 7 encoders, y 42..86: 7 cells ----
        for (i in 0 until 7) {
            val x0 = 6f + i * 33f
            box(c, x0, 42f, x0 + 30f, 86f, popColor(i, DIM))
            text(c, x0 + 3f, 52f, st.encLabels[i], DIM, 8f)
            val v = st.encVals[i]
            text(c, x0 + 3f, 66f, "${(v * 100).toInt()}", INK, 9f)
            frame.color = FLUO_TURQ
            c.drawLine(x0 + 3f, 80f, x0 + 3f + 24f * v, 80f, frame)
        }

        // ---- page line, y 94..110 ----
        val pn = st.pageNames[st.page] ?: "MAIN"
        text(c, 6f, 105f, pn, FLUO_TURQ, 10f)
        when (st.page) {
            3 -> {  // SEQ advanced: LOCK LEN MUT PLAY RAND
                val nm = arrayOf("LCK", "LEN", "MUT", "PLY", "RND")
                for (i in 0 until 5) {
                    val x0 = 52f + i * 37f
                    text(c, x0, 105f, nm[i], DIM, 8f)
                    text(c, x0 + 14f, 105f, "${(st.seqkVals[i] * 100).toInt()}", INK, 9f)
                }
            }
            10, 11 -> {  // LFO / ENV page: sub name + 16 source bars
                val nm = if (st.page == 10) ZtekAudio.SRC_NAMES.getOrElse(st.sub) { "?" }
                         else ZtekAudio.TOOL_NAMES.getOrElse(st.sub) { "?" }
                text(c, 44f, 105f, "SUB", DIM, 8f)
                text(c, 70f, 105f, "$nm ${st.sub}", INK, 9f)
                if (st.page == 10) {
                    text(c, 130f, 105f, if (st.lfoSync) "SYNC" else "sync", if (st.lfoSync) FLUO_PINK else DIM, 8f)
                    text(c, 172f, 105f, if (st.lfoTrig) "TRIG" else "trig", if (st.lfoTrig) FLUO_PINK else DIM, 8f)
                }
                for (i in 0 until 16) {
                    val x0 = 6f + i * 14.5f
                    val h = st.srcVals[i] * 12f
                    frame.color = if (i == st.sub && (st.page == 10 || st.page == 11 && st.sub < 8)) FLUO_YEL else DIM
                    c.drawRect(x0, 116f - h, x0 + 11f, 116f, frame)
                    if (st.srcVals[i] > 0.01f) {
                        frame.color = FLUO_TURQ
                        c.drawRect(x0, 116f - h, x0 + 11f, 116f, frame)
                    }
                }
            }
            12 -> {  // ASSIGN: dst, src, depth, option LUDIQUE
                text(c, 44f, 105f, "DST", DIM, 8f)
                text(c, 74f, 105f, "${st.sub}", INK, 9f)
                text(c, 100f, 105f, "SRC", DIM, 8f)
                text(c, 126f, 105f, if (st.asgSrc == 255) "OFF" else ZtekAudio.SRC_NAMES.getOrElse(st.asgSrc) { "?" }, INK, 9f)
                text(c, 180f, 105f, "DPT ${(st.asgDepth * 100).toInt()}", INK, 9f)
                text(c, 212f, 105f, if (st.ludiqueOn) "LUD" else "lud", if (st.ludiqueOn) FLUO_PINK else DIM, 8f)
            }
            else -> {
                text(c, 44f, 105f, "DLY", DIM, 9f)
                text(c, 72f, 105f, "${(st.dly * 100).toInt()}", INK, 10f)
                text(c, 110f, 105f, "REV", DIM, 9f)
                text(c, 138f, 105f, "${(st.rev * 100).toInt()}", INK, 10f)
                text(c, 168f, 105f, "PST", FLUO_PINK, 8f)
                text(c, 188f, 105f, st.presetName, FLUO_PINK, 9f)
            }
        }

        // ---- "sens interdit" pop + suggestions (forbidden assignment, VCV-style typed patch) ----
        if (now < st.noEntryUntil) {
            // panneau "sens interdit": rond rouge plein + barre blanche horizontale (🚫)
            val cx = 120f
            val cy = 62f
            val pulse = 1f + 0.15f * kotlin.math.sin(now / 40f)
            val r = 12f * pulse
            mono.color = REC_RED
            c.drawCircle(cx, cy, r, mono)                       // disque rouge plein
            mono.color = INK
            c.drawRect(cx - r, cy - 3f, cx + r, cy + 3f, mono)  // barre blanche horizontale
            if (st.sugMsg.isNotEmpty()) text(c, 6f, 92f, st.sugMsg, AMBER, 9f)
        } else if (now < st.hintUntil && st.hintMsg.isNotEmpty()) {
            // contextual suggestion bubble (valid assignment): small turquoise "pop" + text below
            frame.color = FLUO_TURQ
            val pulse = 1f + 0.15f * kotlin.math.sin(now / 40f)
            c.drawCircle(120f, 62f, 6f * pulse, frame)
            text(c, 6f, 92f, st.hintMsg, FLUO_TURQ, 9f)
        }

        // ---- 16 slot keys, 2 rows of 8, y 118..238 (big, like the FM-1 white keys) ----
        for (i in 0 until 16) {
            val col = i % 8
            val row = i / 8
            val x0 = 6f + col * 29f
            val y0 = 118f + row * 60f
            val on = st.slotOn[i]
            val rec = st.slotRec[i]
            val sel = i == st.selSlot
            val color = when {
                rec && blink -> REC_RED
                rec -> REC_RED_OFF
                on -> FLUO_YEL
                sel -> AMBER
                else -> DIM
            }
            box(c, x0, y0, x0 + 26f, y0 + 54f, color)
            text(c, x0 + 8f, y0 + 30f, "${i + 1}", if (on || rec || sel) INK else DIM, 10f)
        }

        c.restore()
    }
}
