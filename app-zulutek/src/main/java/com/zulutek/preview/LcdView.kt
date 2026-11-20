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
import android.graphics.SweepGradient
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

    // page SMP (sampleur audio-in, page 17)
    var smpPage = false
    var smpRecOn = false
    var smpMode = 2                  // 0 OFF 1 BEAT 2 CLEAN 3 OLD
    val smpModeNames = arrayOf("OFF", "BEAT", "CLEAN", "OLD")
    var smpLen = 0
    val smpPeaks = FloatArray(240)   // min/max par bucket (120 buckets x 2)

    // circuit bend (POW maintenu + toucher le LCD dans l'APK ; REC+écran sur la bible)
    var bendOn = false
    var bendX = 0f
    var bendY = 0f
    var bendMode = 0                 // 0 GLITCH 1 LOW BAT

    // ZuluLink : pairs vus sur le réseau local
    var linkPeers = 0
    var linkName = ""

    // pages : 0 MAIN, 1 LOOP (édition slot), 2 SNAP, 3 KIT, 4 XY
    var page = 0
    val snapFilled = BooleanArray(16)
    val kitFilled = BooleanArray(16)
    val loopParam = FloatArray(6)       // PITCH POS MODE MUT SRC VOL du slot édité
    var sirenOn = false
    var xyOn = false
    var xyX = 0.5f
    var xyY = 0.5f
    var xyHold = false
    var xyTargetNames = arrayOf("CUT", "RES")

    val targetNames = arrayOf(
        "TUN", "CUT", "RES", "ENV", "DEC", "ACC", "WAV", "ENG",
        "DRVT", "DRV", "TONE", "LVL", "MODT", "RATE", "DEPT", "MIX",
        "DLYT", "FB", "DLT", "DLM", "RVD", "RVP", "RVM", "CRSH", "HOLD")

    fun targetName(t: Int) = targetNames.getOrElse(t) { "?" }
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

        // ---- page SMP : waveform turquoise, marqueurs START/END jaunes, point REC rouge ----
        if (st.smpPage) {
            text(c, 6f, 15f, "SMP", FLUO_TURQ, 12f)
            text(c, 60f, 15f, st.smpModeNames[st.smpMode], INK, 10f)
            if (st.smpRecOn && blink) {
                mono.color = REC_RED
                c.drawCircle(226f, 11f, 5f, mono)
            }
            text(c, 6f, 30f, "LEN ${(st.smpLen / 48000.0).toString().take(4)}s", DIM, 9f)
            text(c, 90f, 30f, "K1..K4 STA/END/VOL/PIT", DIM, 8f)
            // waveform, y 60..170
            val wy0 = 60f; val wy1 = 170f; val wym = (wy0 + wy1) / 2f
            box(c, 6f, wy0, 234f, wy1, DIM)
            frame.color = FLUO_TURQ
            frame.strokeWidth = 1f
            val nb = st.smpPeaks.size / 2
            for (i in 0 until nb) {
                val x = 8f + (226f - 8f) * i / (nb - 1).coerceAtLeast(1)
                val mn = st.smpPeaks[i * 2]
                val mx = st.smpPeaks[i * 2 + 1]
                c.drawLine(x, wym - mx * 50f, x, wym - mn * 50f, frame)
            }
            // marqueurs START/END (jaunes)
            frame.color = FLUO_YEL
            frame.strokeWidth = 2f
            val sx = 8f + (st.encVals[3] * (226f - 8f))
            val ex = 8f + (st.encVals[4] * (226f - 8f))
            c.drawLine(sx, wy0, sx, wy1, frame)
            c.drawLine(ex, wy0, ex, wy1, frame)
            // valeurs K3/K4 en dessous
            text(c, 8f, 190f, "VOL ${(st.encVals[5] * 100).toInt()}", INK, 10f)
            text(c, 90f, 190f, "PITCH ${((st.encVals[6] - 0.5f) * 48).toInt()}st", INK, 10f)
            text(c, 8f, 210f, "REC: PRESET  PLAY: touche blanche / PLAY", DIM, 8f)
            text(c, 8f, 226f, "long-SEQ = fermer la page", DIM, 8f)
            c.restore()
            return
        }

        // ---- page LOOP : édition du slot sélectionné (K1 PITCH K2 POS K3 MODE K4 MUT, PRE VOL, ALG SRC) ----
        if (st.page == 1) {
            text(c, 6f, 15f, "LOOP ${st.selSlot + 1}", FLUO_TURQ, 12f)
            if (st.slotRec[st.selSlot] && blink) { mono.color = REC_RED; c.drawCircle(226f, 11f, 5f, mono) }
            val labels = arrayOf("PITCH", "POS", "MODE", "MUT", "SRC", "VOL")
            for (i in 0 until 6) {
                val y0 = 30f + i * 26f
                text(c, 6f, y0 + 10f, labels[i], DIM, 9f)
                box(c, 60f, y0, 234f, y0 + 16f, popColor(i, DIM))
                val v = st.loopParam[i]
                frame.color = FLUO_TURQ
                c.drawLine(62f, y0 + 8f, 62f + 168f * v, y0 + 8f, frame)
                text(c, 200f, y0 + 12f, if (i == 0) "${(v * 24 - 12).toInt()}st" else "${(v * 100).toInt()}", INK, 9f)
            }
            text(c, 6f, 232f, "REC=arm/stop ERS=erase DLY=len REV=mode long-REC=quitte", DIM, 8f)
            c.restore()
            return
        }

        // ---- pages SNAP / KIT : 16 slots en 2x8 (8 ZU / 8 LU pour SNAP) ----
        if (st.page == 2 || st.page == 3) {
            val isSnap = st.page == 2
            text(c, 6f, 15f, if (isSnap) "SNAP" else "KIT", if (isSnap) FLUO_PINK else AMBER, 12f)
            text(c, 80f, 15f, if (isSnap) "ZU|LU" else "kit global", DIM, 9f)
            if (st.sirenOn) text(c, 200f, 15f, "SIREN", REC_RED, 9f)
            val filled = if (isSnap) st.snapFilled else st.kitFilled
            for (i in 0 until 16) {
                val col = i % 8
                val row = i / 8
                val x0 = 6f + col * 29f
                val y0 = 30f + row * 80f
                val f = filled[i]
                val sel = i == st.selSlot
                box(c, x0, y0, x0 + 26f, y0 + 70f,
                    if (f) (if (isSnap) FLUO_YEL else AMBER) else if (sel) FLUO_TURQ else DIM)
                text(c, x0 + 8f, y0 + 40f, "${i + 1}", if (f || sel) INK else DIM, 10f)
                if (isSnap) text(c, x0 + 6f, y0 + 12f, if (i < 8) "ZU" else "LU", DIM, 8f)
            }
            text(c, 6f, 200f, if (isSnap) "tap=charger  long=stocker  REC=undo  dbl-REC=redo"
                              else "tap=charger  long=stocker  dbl-tap=miroir(xor8)",
                DIM, 8f)
            text(c, 6f, 214f, "long-DRV/MUT = quitter", DIM, 8f)
            c.restore()
            return
        }

        // ---- page XY (18) : pad sur le LCD (tactile côté APK), cibles X/Y ----
        if (st.page == 4) {
            text(c, 6f, 15f, "XY", FLUO_TURQ, 12f)
            text(c, 40f, 15f, "X>${st.xyTargetNames[0]}  Y>${st.xyTargetNames[1]}", INK, 10f)
            text(c, 200f, 15f, if (st.xyHold) "HOLD" else "MOM", if (st.xyHold) FLUO_YEL else DIM, 9f)
            box(c, 6f, 24f, 234f, 200f, DIM)
            for (i in 1 until 8) { frame.color = Color.argb(255, 40, 40, 40)
                c.drawLine(6f + 228f * i / 8f, 24f, 6f + 228f * i / 8f, 200f, frame) }
            for (i in 1 until 6) { c.drawLine(6f, 24f + 176f * i / 6f, 234f, 24f + 176f * i / 6f, frame) }
            if (st.xyOn) {
                mono.color = FLUO_YEL; mono.style = Paint.Style.FILL
                c.drawCircle(6f + st.xyX * 228f, 24f + st.xyY * 176f, 7f, mono)
            }
            text(c, 6f, 216f, "SEL=Xtarget PRE=Ytarget ALG=swap  dbl-tap=hold/mom", DIM, 8f)
            text(c, 6f, 230f, "long-GEN = quitter", DIM, 8f)
            c.restore()
            return
        }

        // ---- circuit bend : bandes glitch RGB + roue au point du doigt ----
        if (st.bendOn) {
            for (k in 0 until 3) {
                frame.color = RAINBOW[(k * 2 + 1) % RAINBOW.size]
                frame.alpha = 70
                val yy = (st.bendY * H + k * 37f) % H
                c.drawRect(0f, yy, W.toFloat(), yy + 3f, frame)
                frame.alpha = 255
            }
            val bx = st.bendX * W
            val by = st.bendY * H
            val sweep = SweepGradient(bx, by, intArrayOf(
                FLUO_PINK, FLUO_YEL, FLUO_TURQ, REC_RED, FLUO_PINK), null)
            val wp = Paint().apply { shader = sweep; style = Paint.Style.STROKE; strokeWidth = 3f }
            c.drawCircle(bx, by, 26f, wp)
            text(c, bx - 12f, by + 3f, if (st.bendMode == 0) "GLT" else "LB", INK, 9f)
        }

        // ---- top bar ----
        text(c, 6f, 15f, "ZULUTEK", FLUO_YEL, 12f)
        text(c, 140f, 15f, if (st.playing) "PLAY" else "STOP", if (st.playing) FLUO_PINK else DIM, 10f)
        text(c, 176f, 15f, "${st.bpm}", INK, 10f)
        text(c, 200f, 15f, if (st.seqOn) "SEQ" else "seq", if (st.seqOn) FLUO_TURQ else DIM, 10f)
        if (st.recArmed && blink) text(c, 222f, 15f, "R", REC_RED, 10f)

        // ---- engine + drive line ----
        text(c, 6f, 30f, if (st.linkPeers > 0) "LNK:${st.linkPeers}${st.linkName}" else "LNK:--",
            if (st.linkPeers > 0) FLUO_TURQ else DIM, 8f)
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
