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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
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
    val encLabels = arrayOf("SEL", "PRE", "ALG", "K1", "K2", "K3", "K4")
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

    // veille auto (docs/ZULUTEK.md) : 0 off, 1 traversée ~7 s, 2 spectacle 30 s, 3 écran éteint
    var veilleOn = true
    var veillePhase = 0
    var catX = 0f                     // 0..1 across the 240 px
    var coucouUntil = 0L             // pop de réveil au centre
    var torch = false                // lampe torche (écran éteint, toutes les 30 s)
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

    /* décor PCB pixel art (généré en CI par tools/make_icon.py ; absent en build local nu :
     * référencé par nom pour que le code compile même sans les PNG générés) */
    private val pcb: Bitmap? by lazy {
        val id = ctx.resources.getIdentifier("pcb_scene", "drawable", ctx.packageName)
        if (id != 0) BitmapFactory.decodeResource(ctx.resources, id) else null
    }

    private val RAINBOW = intArrayOf(
        Color.rgb(255, 60, 60), Color.rgb(255, 160, 40), Color.rgb(255, 238, 0),
        Color.rgb(60, 220, 90), Color.rgb(60, 160, 255), Color.rgb(150, 80, 255))

    /* the Nyan Cat ghetto blaster crossing the FM-1's inside, rainbow trail behind */
    private fun drawCat(c: Canvas, x: Float, y: Float, scale: Float) {
        val body = Paint().apply { color = Color.rgb(255, 150, 190); style = Paint.Style.FILL }
        val head = Paint().apply { color = Color.rgb(190, 195, 205); style = Paint.Style.FILL }
        // traînée arc-en-ciel
        for (k in RAINBOW.indices) {
            frame.color = RAINBOW[k]
            frame.strokeWidth = 2.2f * scale
            val yy = y + (k - 2.5f) * 3.2f * scale
            c.drawLine(x - 8f * scale, yy, x - 52f * scale, yy, frame)
        }
        // corps (pop-tart) + tête + oreilles + yeux
        c.drawRect(x - 10f * scale, y - 8f * scale, x + 14f * scale, y + 8f * scale, body)
        c.drawCircle(x + 18f * scale, y, 9f * scale, head)
        c.drawRect(x + 12f * scale, y - 15f * scale, x + 16f * scale, y - 8f * scale, head)
        c.drawRect(x + 20f * scale, y - 15f * scale, x + 24f * scale, y - 8f * scale, head)
        mono.color = Color.BLACK
        mono.textSize = 6f * scale
        c.drawText("o", x + 15f * scale, y + 2f * scale, mono)
        c.drawText("o", x + 21f * scale, y + 2f * scale, mono)
    }

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

        // ---- veille auto : décor PCB + Nyan Cat, puis écran éteint + lampe torche ----
        if (st.veillePhase > 0) {
            val bmp = pcb
            if (bmp != null) c.drawBitmap(bmp, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), null)
            else c.drawColor(Color.argb(255, 16, 18, 22))
            when (st.veillePhase) {
                1, 2 -> {
                    drawCat(c, 20f + st.catX * (W - 60f), H / 2f, 1f)
                    if (st.veillePhase == 2 && (now / 130) % 7 == 0L) {
                        // glitch : bandes RGB décalées
                        for (k in 0 until 3) {
                            frame.color = RAINBOW[(k * 2 + (now / 130).toInt()) % RAINBOW.size]
                            frame.alpha = 60
                            val yy = ((now / 130) % H).toFloat()
                            c.drawRect(0f, yy, W.toFloat(), yy + 4f, frame)
                            frame.alpha = 255
                        }
                    }
                }
                3 -> {
                    // écran « éteint » : tout noir, la lampe torche balaie le coin bas-droit
                    c.drawColor(Color.argb(255, 4, 4, 6))
                    if (st.torch) {
                        val g = RadialGradient(216f, 216f, 70f,
                            Color.argb(160, 255, 255, 240), Color.argb(0, 255, 255, 240),
                            android.graphics.Shader.TileMode.CLAMP)
                        val p = Paint().apply {
                            shader = g
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                        }
                        c.drawCircle(216f, 216f, 70f, p)
                    }
                }
            }
            if (now < st.coucouUntil) drawCat(c, W / 2f, H / 2f, 1.6f)   // coucou au réveil
            c.restore()
            return
        }

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

        // ---- FX line, y 94..110 ----
        text(c, 6f, 105f, "DLY", DIM, 9f)
        text(c, 36f, 105f, "${(st.dly * 100).toInt()}", INK, 10f)
        text(c, 90f, 105f, "REV", DIM, 9f)
        text(c, 120f, 105f, "${(st.rev * 100).toInt()}", INK, 10f)
        text(c, 170f, 105f, "SEL", DIM, 9f)
        text(c, 200f, 105f, "${st.selSlot + 1}", if (st.recArmed) REC_RED else INK, 10f)

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
