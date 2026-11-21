/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Flying Zulu
 *
 * SurfaceView: the APK's clone of the FM-1 front panel (after the canonical panel art of the
 * FM-1 emulator, github.com/joakimeriksson/fm1-emu) wearing the Zulu charte. V2 adaptative
 * (Flying Zulu, 21/11/2026) : plus de canevas logique fige 1280x820, toute la geometrie est
 * derivee de la taille reelle du device (w, h) pour optimiser la surface de jeu :
 *  - paysage : colonne gauche knobs (MASTER SELECT PRESETS ALGORITHM + OCT- / OCT+),
 *    LCD carre auto au centre sur bezel noir serre, colonne droite KNOB1..4 + 2 rangees de
 *    six candies, clavier 27 pills pleine largeur en bas (zone pouce).
 * Pas de vue verticale (Flying Zulu, 21/11/2026) : l'app se verrouille en paysage comme le
 * FM-1 et tout l'ecran va au jeu tactile. Le LCD reste un carre automatique : la priorite va
 * aux controles. Dispatch et zones
 * logiques inchanges (SurfaceListener, BTN_OF, BLACK_NOTES). */
package com.zulutek.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
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
        /* the machine's silk, in the panel's matrix order: OCT- 0, OCT+ 1, FX 2, SCL 3,
         * ENV 4, LFO 5, EDIT 6, GLO 7, HOME 8, SAVE 9, ARP 10, SEQ 11, PLAY 12, REC 13 */
        val SILK = arrayOf("OCT-", "OCT+", "FX", "SCL", "ENV", "LFO", "EDIT", "GLO",
                           "HOME", "SAVE", "ARP", "SEQ", "PLAY", "REC")
        val M2I = intArrayOf(12, 13, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)  // matrix id -> listener index
        val I2M = intArrayOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 0, 1)  // listener index -> matrix id
        // 27 keys F3..G5: white keys (16) carry slot indices, black keys (11) MIDI notes
        val BLACK_NOTES = intArrayOf(54, 56, 58, 61, 63, 66, 68, 70, 73, 75, 78)

        /* Deux palettes (Flying Zulu) :
         * DA ZULU : fond noir charte, bonbons/pills turquoise fluo, marque rose fluo.
         * MAISON : le FM-1 custom maison peint (photo du 21/11) : face vert fluo, plateau
         * bleu vif, capuchons orange, pills bleu profond, marque rouge sang. */
        val ROOM_DA     = 0xFF090C10.toInt()
        val BODY_TOP_DA = 0xFF151920.toInt()
        val BODY_BOT_DA = 0xFF0E1116.toInt()
        val TRAY_DA     = 0xFF1B222B.toInt()
        val SILK_INK_DA = 0xFF9FE0D6.toInt()
        val BRAND_INK_DA = 0xFFFF2DA0.toInt()
        val BTN_TOP_DA  = 0xFF00FFE0.toInt()
        val BTN_BOT_DA  = 0xFF00A692.toInt()
        val BTN_INK_DA  = 0xFF09120F.toInt()
        val KEY_TOP_DA  = 0xFF00E8D0.toInt()
        val KEY_BOT_DA  = 0xFF00948A.toInt()
        val SKIRT_DA    = 0xFF3A3D43.toInt()
        val SKIRT_HI_DA = 0xFF6D7178.toInt()
        val POINTER_DA  = 0xFFEEEAE2.toInt()
        val RIDGE_DA    = 0xFF7BD6C5.toInt()
        val ROOM_M      = 0xFF0B100A.toInt()
        val BODY_TOP_M  = 0xFF00D90C.toInt()
        val BODY_BOT_M  = 0xFF039004.toInt()
        val TRAY_M      = 0xFF0A62D8.toInt()
        val SILK_INK_M  = 0xFFECFDF0.toInt()
        val BRAND_INK_M = 0xFFF90910.toInt()
        val BTN_TOP_M   = 0xFFFC7A03.toInt()
        val BTN_BOT_M   = 0xFFD85700.toInt()
        val BTN_INK_M   = 0xFF301202.toInt()
        val KEY_TOP_M   = 0xFF2F8CFF.toInt()
        val KEY_BOT_M   = 0xFF0A48A8.toInt()
        val SKIRT_M     = 0xFF145214.toInt()
        val SKIRT_HI_M  = 0xFF2E7D2A.toInt()
        val POINTER_M   = 0xFFF6FFF8.toInt()
        val RIDGE_M     = 0xFFDEF2FF.toInt()
        val AMBER_LED = 0xFFF2A33A.toInt()
        val GREEN_LED = 0xFF62D36E.toInt()

        var ROOM = ROOM_DA
        var BODY_TOP = BODY_TOP_DA
        var BODY_BOT = BODY_BOT_DA
        var TRAY = TRAY_DA
        var SILK_INK = SILK_INK_DA
        var BRAND_INK = 0xFFFF2DA0.toInt()
        var BTN_TOP = BTN_TOP_DA
        var BTN_BOT = BTN_BOT_DA
        var BTN_INK = BTN_INK_DA
        var KEY_TOP = KEY_TOP_DA
        var KEY_BOT = KEY_BOT_DA
        var SKIRT = SKIRT_DA
        var SKIRT_HI = SKIRT_HI_DA
        var POINTER = POINTER_DA
        var RIDGE = RIDGE_DA

        fun applySkin(maison: Boolean) {
            if (maison) {
                ROOM = ROOM_M; BODY_TOP = BODY_TOP_M; BODY_BOT = BODY_BOT_M; TRAY = TRAY_M
                SILK_INK = SILK_INK_M; BRAND_INK = BRAND_INK_M
                BTN_TOP = BTN_TOP_M; BTN_BOT = BTN_BOT_M; BTN_INK = BTN_INK_M
                KEY_TOP = KEY_TOP_M; KEY_BOT = KEY_BOT_M
                SKIRT = SKIRT_M; SKIRT_HI = SKIRT_HI_M; POINTER = POINTER_M; RIDGE = RIDGE_M
            } else {
                ROOM = ROOM_DA; BODY_TOP = BODY_TOP_DA; BODY_BOT = BODY_BOT_DA; TRAY = TRAY_DA
                SILK_INK = SILK_INK_DA; BRAND_INK = 0xFFFF2DA0.toInt()
                BTN_TOP = BTN_TOP_DA; BTN_BOT = BTN_BOT_DA; BTN_INK = BTN_INK_DA
                KEY_TOP = KEY_TOP_DA; KEY_BOT = KEY_BOT_DA
                SKIRT = SKIRT_DA; SKIRT_HI = SKIRT_HI_DA; POINTER = POINTER_DA; RIDGE = RIDGE_DA
            }
        }
    }

    /* ---- geometry adaptative : tout en pixels du device, recalcule au layout ---- */
    private var vw = 0f
    private var vh = 0f
    private var pad = 0f
    private var ts = 1f                       // facteur d'echelle des textes
    private val enc = Array(7) { FloatArray(3) }   // [SELECT, PRESETS, ALGO, K1, K2, K3, K4] cx, cy, r
    private val encSilkL = Array(7) { FloatArray(2) }
    private val pot = FloatArray(3)                // MASTER
    private val btn = Array(14) { FloatArray(4) }   // matrix id 0..13 : x, y, w, h
    private val octTray = FloatArray(4)
    private val bdTray = FloatArray(4)             // plateau des candies
    private val kbTray = FloatArray(4)
    private val playRing = FloatArray(4)           // encadrement PLAY+REC
    private val bezelR = FloatArray(4)             // bezel noir : x, y, cote, frame
    private var lcdL = 0f; private var lcdT = 0f; private var lcdS = 0f   // l'ecran utile (le LcdView se pose dessus)
    private var brandCx = 0f; private var brandCy = 0f
    private var wX0 = 0f; private var wDx = 0f; private var wW = 0f       // touches blanches
    private var wY = 0f; private var wH = 0f
    private var bW = 0f; private var bH = 0f; private var bY = 0f          // touches noires
    private val blacksAfter = intArrayOf(0, 1, 2, 4, 5, 7, 8, 9, 11, 12, 14)
    private fun whiteKeyX(i: Int) = wX0 + i * wDx
    private fun blackKeyX(bi: Int) = wX0 + (blacksAfter[bi] + 1) * wDx - (wDx - wW) / 2f - bW / 2f

    /** rect de l'ecran LCD utile, en coordonnees de la vue (pour poser le LcdView dessus) */
    fun lcdRect(): FloatArray = floatArrayOf(lcdL, lcdT, lcdS)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        vw = w.toFloat(); vh = h.toFloat()
        pad = 0.018f * min(vw, vh)
        ts = min(vw, vh) / 640f
        layoutLandscape()   // pas de vue verticale (Flying Zulu) : le FM-1 est une facade paysage
    }

    private fun layoutLandscape() {
        val W = vw; val H = vh
        val p = pad
        val kbH = 0.26f * H
        val kbTop = H - p - kbH
        val topH = kbTop - p
        val lw = 0.20f * W      // comme sur FM-1 : MASTER SELECT PRESETS ALGORITHM en 2x2 + OCT dessous
        val rw = 0.26f * W      // cote droit optimise : place liberee pour le LCD
        val gp = 0.012f * W
        val cx0 = p + lw + gp
        val cx1 = W - p - rw - gp
        val centerW = cx1 - cx0
        val brandH = 0.05f * H
        // bezel noir serre + LCD carre auto au centre
        val bs = min(centerW, topH - brandH)
        val bx = cx0 + (centerW - bs) / 2f
        val by = p + (topH - brandH - bs) / 2f
        val frame = min(bs * 0.045f, 0.02f * min(W, H)).coerceAtLeast(3f)
        bezelR[0] = bx; bezelR[1] = by; bezelR[2] = bs; bezelR[3] = frame
        lcdL = bx + frame; lcdT = by + frame; lcdS = bs - 2f * frame
        brandCx = bx + bs / 2f; brandCy = by + bs + brandH * 0.8f
        // colonne gauche comme sur le FM-1 (panel.rs) : 2x2 MASTER SELECT PRESETS ALGORITHM
        // (centres a 23 % / 77 % du plateau, lignes a 26 % / 58 % de la zone haute) + OCT dessous
        val trayL = p + lw * 0.05f
        val trayW = lw * 0.90f
        val r1 = min(trayW * 0.225f, topH * 0.085f)
        val kx1 = trayL + trayW * 0.23f
        val kx2 = trayL + trayW * 0.77f
        val ky1 = p + topH * 0.26f
        val ky2 = p + topH * 0.58f
        pot[0] = kx1; pot[1] = ky1; pot[2] = r1
        enc[0][0] = kx2; enc[0][1] = ky1; enc[0][2] = r1    // SELECT
        enc[1][0] = kx1; enc[1][1] = ky2; enc[1][2] = r1    // PRESETS
        enc[2][0] = kx2; enc[2][1] = ky2; enc[2][2] = r1    // ALGORITHM
        for (i in 0..2) { encSilkL[i][0] = enc[i][0]; encSilkL[i][1] = enc[i][1] - r1 - 8f * ts }
        val otY = p + topH * 0.74f
        val otH = topH * 0.115f
        octTray[0] = trayL; octTray[1] = otY; octTray[2] = trayW; octTray[3] = otH
        val obW = (octTray[2] - otH * 0.4f) / 2f
        btn[0][0] = octTray[0] + otH * 0.2f; btn[0][1] = otY + otH * 0.18f
        btn[0][2] = obW; btn[0][3] = otH * 0.64f
        btn[1][0] = octTray[0] + octTray[2] - otH * 0.2f - obW; btn[1][1] = btn[0][1]
        btn[1][2] = obW; btn[1][3] = btn[0][3]
        // colonne droite : KNOB1..4 puis 2 rangees de six candies
        val x0 = W - p - rw
        val r2 = min(rw * 0.083f, topH * 0.125f)
        val ky = p + topH * 0.15f
        for (i in 0..3) { enc[3 + i][0] = x0 + rw * (0.125f + 0.25f * i); enc[3 + i][1] = ky; enc[3 + i][2] = r2 }
        for (i in 3..6) { encSilkL[i][0] = enc[i][0]; encSilkL[i][1] = ky - r2 - 8f * ts }
        val tY = p + topH * 0.30f
        val tH = topH * 0.66f
        bdTray[0] = x0 + rw * 0.03f; bdTray[1] = tY; bdTray[2] = rw * 0.94f; bdTray[3] = tH
        val cw = bdTray[2] / 6f * 0.82f
        val ch = tH / 2f * 0.70f
        for (id in 2..13) {
            val col = (id - 2) % 6; val row = (id - 2) / 6
            btn[id][0] = bdTray[0] + bdTray[2] * (col + 0.5f) / 6f - cw / 2f
            btn[id][1] = tY + tH * (row + 0.5f) / 2f - ch / 2f
            btn[id][2] = cw; btn[id][3] = ch
        }
        // clavier : 27 pills pleine largeur, noires posees sur les raccords
        kbTray[0] = p; kbTray[1] = kbTop; kbTray[2] = W - 2f * p; kbTray[3] = kbH
        val m = 0.012f * W
        val avail = kbTray[2] - 2f * m
        wW = avail / 3.7f
        val g = 0.18f * wW
        wDx = wW + g
        wX0 = p + m
        bW = wW * 0.94f
        bH = kbH * 0.44f; bY = kbTop + kbH * 0.08f
        wH = kbH * 0.50f; wY = kbTop + kbH * 0.46f
        // encadrement PLAY+REC (derniere colonne, rangee du bas)
        playRing[0] = btn[12][0] - 3f * ts; playRing[1] = min(btn[12][1], btn[13][1]) - 3f * ts
        playRing[2] = btn[13][0] + btn[13][2] + 3f * ts - playRing[0]
        playRing[3] = btn[13][1] + btn[13][3] + 3f * ts - playRing[1]
    }

    private val mono = Paint().apply { typeface = Typeface.MONOSPACE; isAntiAlias = true }
    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val frameP = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val rr = RectF()
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

    /* hit-test en pixels du device (la geometrie est deja adaptative) */
    private fun zoneAt(x: Float, y: Float): Pair<Int, Int> {
        for (i in enc.indices) {
            val dx = x - enc[i][0]; val dy = y - enc[i][1]
            val r = enc[i][2] + 8f * ts
            if (dx * dx + dy * dy <= r * r) return 1 to i
        }
        for (b in btn) {
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3])
                return 2 to M2I[indexOfBtn(b)]
        }
        for (bi in BLACK_NOTES.indices.reversed()) {     // blacks first: they sit above the whites
            val x0 = blackKeyX(bi)
            if (x >= x0 && x < x0 + bW && y >= bY && y < bY + bH) return 4 to bi
        }
        for (i in 0 until 16) {
            val x0 = whiteKeyX(i)
            if (x >= x0 && x < x0 + wW && y >= wY && y < wY + wH) return 3 to i
        }
        return 0 to -1
    }
    private fun indexOfBtn(b: FloatArray): Int {
        for (i in btn.indices) if (btn[i] === b) return i
        return -1
    }

    /* ---- drawing helpers ---- */
    private fun rrect(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float,
                      top: Int, bot: Int) {
        fill.shader = LinearGradient(0f, y, 0f, y + h, top, bot, Shader.TileMode.CLAMP)
        rr.set(x, y, x + w, y + h)
        c.drawRoundRect(rr, r, r, fill)
        fill.shader = null
    }
    private fun rrect(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, col: Int) =
        rrect(c, x, y, w, h, r, col, col)
    private fun label(c: Canvas, cx: Float, cy: Float, s: String, col: Int, size: Float) {
        mono.textSize = size; mono.color = col
        c.drawText(s, cx - mono.measureText(s) / 2f, cy, mono)
    }
    private fun knob(c: Canvas, cx: Float, cy: Float, r: Float, angle: Float, glow: Float) {
        fill.color = 0xFF26272A.toInt(); c.drawCircle(cx + 2f * ts, cy + 4f * ts, r + 2f * ts, fill)      // shadow
        if (glow > 0f) {
            frameP.strokeWidth = 5f * ts
            frameP.color = Color.argb((255 * glow).toInt(), Color.red(AMBER_LED), Color.green(AMBER_LED), Color.blue(AMBER_LED))
            c.drawCircle(cx, cy, r + 4f * ts, frameP)
        }
        fill.shader = LinearGradient(cx, cy - r, cx, cy + r, SKIRT_HI, SKIRT, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill); fill.shader = null                                  // knurled skirt
        fill.shader = LinearGradient(cx - r, cy - r, cx + r * 0.4f, cy + r * 0.4f, SKIRT_HI, SKIRT, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.72f, fill); fill.shader = null                           // cap
        val a = Math.toRadians(angle.toDouble())
        c.drawLine(cx + (r * 0.12f * sin(a)).toFloat(), cy - (r * 0.12f * cos(a)).toFloat(),
                   cx + (r * 0.62f * sin(a)).toFloat(), cy - (r * 0.62f * cos(a)).toFloat(),
                   Paint().apply { color = POINTER; strokeWidth = 3f * ts; isAntiAlias = true })
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        applySkin(st.skinMaison)   // OPTIONS > SKIN : DA ZULU ou MAISON (le FM-1 custom maison)
        c.drawColor(ROOM)
        if (vw <= 0f) return
        val bz = bezelR
        // corps + plateaux + marque
        rrect(c, pad, pad, vw - 2f * pad, vh - 2f * pad, 26f * ts, BODY_TOP, BODY_BOT)
        rrect(c, octTray[0], octTray[1], octTray[2], octTray[3], 10f * ts, TRAY)
        rrect(c, bdTray[0], bdTray[1], bdTray[2], bdTray[3], 12f * ts, TRAY)
        rrect(c, kbTray[0], kbTray[1], kbTray[2], kbTray[3], 18f * ts, 0xFF161A20.toInt(), 0xFF111419.toInt())
        // bezel noir serre + ecran (le LcdView se pose dessus : lcdRect())
        rrect(c, bz[0], bz[1], bz[2], bz[2], 14f * ts, 0xFF050607.toInt(), 0xFF030405.toInt())
        label(c, pot[0], pot[1] - pot[2] - 8f * ts, "MASTER", SILK_INK, 13f * ts)
        val silkNames = arrayOf("SELECT", "PRESETS", "ALGORITHM", "KNOB1", "KNOB2", "KNOB3", "KNOB4")
        for (i in enc.indices)
            label(c, encSilkL[i][0], encSilkL[i][1], silkNames[i], SILK_INK, 13f * ts)
        if (brandCy > 0f) label(c, brandCx, brandCy, "ZULUTEK", BRAND_INK, 12f * ts)

        // LED states (charte): the cap tints amber when active, green for PLAY
        val now = System.currentTimeMillis()
        val blink = (now / 260) % 2 == 0L
        fun ledFor(i: Int): Float = when {
            i == 10 && st.playing -> 1f
            i == 11 && (st.recArmed || st.loopRecArm >= 0) && blink -> 1f
            i == 9 && st.seqOn -> 1f
            i == 5 && st.sirenMode -> 1f
            else -> 0f
        }
        // boutons : ombre, capot, sérigraphie
        rrect(c, playRing[0], playRing[1], playRing[2], playRing[3], 8f * ts, 0xFF3C6B43.toInt())
        for (id in btn.indices) {
            val b = btn[id]
            val i = M2I[id]
            val led = ledFor(i)
            val down = zone == 2 && idx == i
            rrect(c, b[0] + 1f * ts, b[1] + 3f * ts, b[2], b[3], 8f * ts, 0xFF1C1D20.toInt())  // shadow
            val tint = if (id == 12) GREEN_LED else AMBER_LED
            val top = if (led > 0f) mix(BTN_TOP, tint, 0.85f) else BTN_TOP
            val bot = if (led > 0f) mix(BTN_BOT, tint, 0.7f) else BTN_BOT
            val off = if (down) 2f * ts else 0f
            rrect(c, b[0], b[1] + off, b[2], b[3], 8f * ts, top, bot)
            val txt = if (st.labelsZulu) ZULU_LABELS[i] else SILK[id]
            label(c, b[0] + b[2] / 2f, b[1] + b[3] / 2f + 4f * ts + off, txt,
                if (led > 0.5f) 0xFF2A1A03.toInt() else BTN_INK, 11f * ts)
        }
        // encodeurs + MASTER decoratif (le volume vit dans les pages FX/MIX)
        for (i in enc.indices) {
            val (cx2, cy2, r2) = Triple(enc[i][0], enc[i][1], enc[i][2])
            val glow = if (st.popUntil[i] > now) 1f else 0f
            knob(c, cx2, cy2, r2, -135f + 270f * st.encVals[i], glow)
            label(c, cx2, cy2 + r2 + 12f * ts, st.encLabels[i], SILK_INK, 10f * ts)
        }
        knob(c, pot[0], pot[1], pot[2], -135f + 270f * 0.5f, 0f)

        // clavier : pills menthe a ridée, chaleur et encre suivent la charte
        val heatOf: (Int, Boolean, Boolean) -> Float = { i, on, rec ->
            when {
                rec && blink -> 1f
                rec -> 0.7f
                on -> 0.55f
                i == st.selSlot -> 0.3f
                else -> 0f
            }
        }
        for (i in 0 until 16) {
            val down = zone == 3 && idx == i
            val heat = maxOf(heatOf(i, st.slotOn[i], st.slotRec[i]), if (down) 0.75f else 0f).coerceAtMost(1f)
            val off = if (down) 3f * ts else 0f
            val x = whiteKeyX(i)
            rrect(c, x + 1f * ts, wY + 4f * ts, wW, wH, wW / 2f, 0xFF1A1A1C.toInt())              // shadow
            rrect(c, x, wY + off, wW, wH, wW / 2f, mix(KEY_TOP, AMBER_LED, heat), mix(KEY_BOT, AMBER_LED, heat * 0.8f))
            rrect(c, x + wW / 2f - 2.5f * ts, wY + 14f * ts + off, 5f * ts, wH - 28f * ts, 2.5f * ts,
                if (heat > 0f) 0xFFEFE2B0.toInt() else RIDGE)
        }
        for (bi in BLACK_NOTES.indices) {
            val note = BLACK_NOTES[bi]
            val down = zone == 4 && idx == bi
            val heat = if (note == st.curNote) 0.75f else if (down) 0.75f else 0f
            val off = if (down) 3f * ts else 0f
            val x = blackKeyX(bi)
            rrect(c, x + 1f * ts, bY + 4f * ts, bW, bH, bW / 2f, 0xFF1A1A1C.toInt())
            rrect(c, x, bY + off, bW, bH, bW / 2f, mix(KEY_TOP, AMBER_LED, heat), mix(KEY_BOT, AMBER_LED, heat * 0.8f))
            rrect(c, x + bW / 2f - 2.5f * ts, bY + 14f * ts + off, 5f * ts, bH - 28f * ts, 2.5f * ts,
                if (heat > 0f) 0xFFEFE2B0.toInt() else RIDGE)
        }
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.argb(255,
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val px = ev.x
        val py = ev.y
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val (z, i) = zoneAt(px, py)
                zone = z; idx = i; startY = py; longPosted = false
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
                    else -> { zone = 0; idx = -1 }
                }
                invalidate()
                return zone != 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (zone == 1) {
                    encMoved = true
                    handler.removeCallbacks(encHoldRunnable)
                    val v = (startVal + (startY - py) / (160f * ts)).coerceIn(0f, 1f)
                    st.encVals[idx] = v
                    listener.onEncoder(idx, v)
                    invalidate()
                } else if (zone == 3) {
                    // glissando over the white pills (panel.rs main.js)
                    val (z, i) = zoneAt(px, py)
                    if (z == 3 && i != idx) {
                        if (idx >= 0) listener.onWhiteKey(idx, false, false)
                        idx = i
                        st.lastKeyUse = System.currentTimeMillis()
                        listener.onWhiteKey(i, true, false)
                        invalidate()
                    }
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
