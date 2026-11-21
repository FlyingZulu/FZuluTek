/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * SurfaceView: the APK's clone of the FM-1 front panel, reproduced after the canonical
 * panel art of the FM-1 emulator (github.com/joakimeriksson/fm1-emu, crates/fm1-live/src/
 * panel.rs, drawn after Felucca's controls diagram) wearing the Zulu charte
 * (black body, fluo turquoise candy caps and pills, pink ZULUTEK brand). Logical canvas 1280x820, scaled to fit:
 * MASTER/SELECT/PRESETS/ALGORITHM left with OCT-/OCT+ under them, the LCD bezel centre
 * (the LcdView sits above), KNOB1..4 then two rows of six candy buttons right (FX SCL ENV
 * LFO EDIT GLO / HOME SAVE ARP SEQ PLAY REC, PLAY+REC outlined), and the 27 pill keys
 * F3..G5 across the bottom (16 whites = looper slots, 11 blacks = playable notes).
 * Dispatch indices stay the ones of SurfaceListener (Sel PRE ALG K1..K4, APK button order);
 * the panel's matrix ids are mapped in BTN_OF. */
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
        val WHITE_NOTES = intArrayOf(53, 55, 57, 59, 60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79)

        /* the panel's logical canvas (panel.rs: W, H) */
        const val PW = 1280f
        const val PH = 820f

        /* Deux palettes (Flying Zulu) :
         * DA ZULU : fond noir charte, bonbons/pills turquoise fluo, marque rose fluo.
         * MAISON : le FM-1 custom maison peint (photo du 21/11) : face vert fluo, plateau
         * bleu vif, capuchons orange, pills bleu profond, marque rouge sang.
         * Couleurs mutables, applySkin() bascule via OPTIONS > SKIN (ligne SKIN). */
        val ROOM_DA     = 0xFF090C10
        val BODY_TOP_DA = 0xFF151920
        val BODY_BOT_DA = 0xFF0E1116
        val TRAY_DA     = 0xFF1B222B
        val SILK_INK_DA = 0xFF9FE0D6
        val BRAND_INK_DA = 0xFFFF2DA0
        val BTN_TOP_DA  = 0xFF00FFE0
        val BTN_BOT_DA  = 0xFF00A692
        val BTN_INK_DA  = 0xFF09120F
        val KEY_TOP_DA  = 0xFF00E8D0
        val KEY_BOT_DA  = 0xFF00948A
        val SKIRT_DA    = 0xFF3A3D43
        val SKIRT_HI_DA = 0xFF6D7178
        val POINTER_DA  = 0xFFEEEAE2
        val RIDGE_DA    = 0xFF7BD6C5
        val ROOM_M      = 0xFF0B100A
        val BODY_TOP_M  = 0xFF00D90C
        val BODY_BOT_M  = 0xFF039004
        val TRAY_M      = 0xFF0A62D8
        val SILK_INK_M  = 0xFFECFDF0
        val BRAND_INK_M = 0xFFF90910
        val BTN_TOP_M   = 0xFFFC7A03
        val BTN_BOT_M   = 0xFFD85700
        val BTN_INK_M   = 0xFF301202
        val KEY_TOP_M   = 0xFF2F8CFF
        val KEY_BOT_M   = 0xFF0A48A8
        val SKIRT_M     = 0xFF145214
        val SKIRT_HI_M  = 0xFF2E7D2A
        val POINTER_M   = 0xFFF6FFF8
        val RIDGE_M     = 0xFFDEF2FF
        const val AMBER_LED = 0xFFF2A33A
        const val GREEN_LED = 0xFF62D36E

        var ROOM = ROOM_DA
        var BODY_TOP = BODY_TOP_DA
        var BODY_BOT = BODY_BOT_DA
        var TRAY = TRAY_DA
        var SILK_INK = SILK_INK_DA
        var BRAND_INK = BRAND_INK_DA
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
                SILK_INK = SILK_INK_DA; BRAND_INK = BRAND_INK_DA
                BTN_TOP = BTN_TOP_DA; BTN_BOT = BTN_BOT_DA; BTN_INK = BTN_INK_DA
                KEY_TOP = KEY_TOP_DA; KEY_BOT = KEY_BOT_DA
                SKIRT = SKIRT_DA; SKIRT_HI = SKIRT_HI_DA; POINTER = POINTER_DA; RIDGE = RIDGE_DA
            }
        }

        /* geometry, verbatim from panel.rs */
        val ENCS = arrayOf(          // (cx, cy, r) label handled separately
            Triple(250f, 135f, 44f), Triple(110f, 295f, 44f), Triple(250f, 295f, 44f),
            Triple(935f, 120f, 36f), Triple(1027f, 120f, 36f), Triple(1119f, 120f, 36f), Triple(1211f, 120f, 36f))
        val ENC_SILK = arrayOf("SELECT", "PRESETS", "ALGORITHM", "KNOB1", "KNOB2", "KNOB3", "KNOB4")
        val POT = Triple(110f, 135f, 44f)
        // matrix-order buttons: id, x, y, w, h (panel.rs buttons())
        val BTNS = arrayOf(
            intArrayOf(0, 62, 398, 112, 44), intArrayOf(1, 186, 398, 112, 44),
            intArrayOf(2, 906, 232, 50, 64), intArrayOf(3, 964, 232, 50, 64),
            intArrayOf(4, 1022, 232, 50, 64), intArrayOf(5, 1080, 232, 50, 64),
            intArrayOf(6, 1138, 232, 50, 64), intArrayOf(7, 1196, 232, 50, 64),
            intArrayOf(8, 906, 308, 50, 64), intArrayOf(9, 964, 308, 50, 64),
            intArrayOf(10, 1022, 308, 50, 64), intArrayOf(11, 1080, 308, 50, 64),
            intArrayOf(12, 1138, 308, 50, 64), intArrayOf(13, 1196, 308, 50, 64))
    }

    // key geometry (panel.rs keys()): a black pill sits over the gap after white key after
    private val KW = 62f; private val KGAP = 11f; private val KX0 = 61f
    private val blacksAfter = intArrayOf(0, 1, 2, 4, 5, 7, 8, 9, 11, 12, 14)
    private fun whiteKeyX(i: Int) = KX0 + i * (KW + KGAP)
    private fun blackKeyX(bi: Int) = KX0 + (blacksAfter[bi] + 1) * (KW + KGAP) - KGAP / 2f - KW / 2f

    private val mono = Paint().apply { typeface = Typeface.MONOSPACE; isAntiAlias = true }
    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val frame = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 1f }
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

    /* scale: the 1280x820 panel fits the view, centered (panel.rs panel_width/height) */
    private fun sc() = if (height > 0 && width > 0) min(width / PW, height / PH) else 1f
    private fun ox() = (width - PW * sc()) / 2f
    private fun oy() = (height - PH * sc()) / 2f
    private fun toX(px: Float) = (px - ox()) / sc()
    private fun toY(py: Float) = (py - oy()) / sc()

    /* panel hit-test, ported from panel.rs hit() */
    private fun zoneAt(x: Float, y: Float): Pair<Int, Int> {
        for (i in ENCS.indices) {
            val (cx, cy, r) = ENCS[i]
            val dx = x - cx; val dy = y - cy
            if (dx * dx + dy * dy <= (r + 6f) * (r + 6f)) return 1 to i
        }
        for (b in BTNS) {
            if (x >= b[1] && x < b[1] + b[3] && y >= b[2] && y < b[2] + b[4])
                return 2 to M2I[b[0]]
        }
        for (bi in BLACK_NOTES.indices.reversed()) {     // blacks first: they sit above the whites
            val x0 = blackKeyX(bi)
            if (x >= x0 && x < x0 + KW && y >= 574f && y < 574f + 94f) return 4 to bi
        }
        for (i in 0 until 16) {
            val x0 = whiteKeyX(i)
            if (x >= x0 && x < x0 + KW && y >= 680f && y < 680f + 106f) return 3 to i
        }
        return 0 to -1
    }

    /* ---- drawing helpers in panel coordinates ---- */
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
        fill.color = 0xFF26272A.toInt(); c.drawCircle(cx + 2f, cy + 4f, r + 2f, fill)      // shadow
        if (glow > 0f) {
            frame.strokeWidth = 5f
            frame.color = Color.argb((255 * glow).toInt(), Color.red(AMBER_LED), Color.green(AMBER_LED), Color.blue(AMBER_LED))
            c.drawCircle(cx, cy, r + 4f, frame)
        }
        fill.shader = LinearGradient(cx, cy - r, cx, cy + r, SKIRT_HI, SKIRT, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill); fill.shader = null                                  // knurled skirt
        fill.shader = LinearGradient(cx - r, cy - r, cx + r * 0.4f, cy + r * 0.4f, SKIRT_HI, SKIRT, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.72f, fill); fill.shader = null                           // cap
        val a = Math.toRadians(angle.toDouble())
        c.drawLine(cx + (r * 0.12f * sin(a)).toFloat(), cy - (r * 0.12f * cos(a)).toFloat(),
                   cx + (r * 0.62f * sin(a)).toFloat(), cy - (r * 0.62f * cos(a)).toFloat(),
                   Paint().apply { color = POINTER; strokeWidth = 3f; isAntiAlias = true })
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        applySkin(st.skinMaison)   // OPTIONS > SKIN : DA ZULU ou MAISON (le FM-1 custom maison)
        c.drawColor(ROOM)
        val s = sc()
        c.save()
        c.translate(ox(), oy())
        c.scale(s, s)

        // body, bezel, trays, brand (panel.rs base)
        rrect(c, 14f, 14f, PW - 28f, PH - 28f, 30f, BODY_TOP, BODY_BOT)
        rrect(c, 352f, 24f, 516f, 516f, 22f, 0xFF0E0E10.toInt(), 0xFF0B0B0C.toInt())       // LCD bezel (the LcdView sits above)
        rrect(c, 370f, 42f, 480f, 480f, 8f, 0xFF181818.toInt(), 0xFF141414.toInt())
        rrect(c, 50f, 388f, 260f, 64f, 14f, TRAY)                                           // OCT tray
        rrect(c, 896f, 222f, 360f, 160f, 16f, TRAY)                                         // button tray
        rrect(c, 34f, 562f, 1212f, 236f, 26f, 0xFF161A20.toInt(), 0xFF111419.toInt())       // keyboard tray
        label(c, POT.first, POT.second - POT.third - 14f, "MASTER", SILK_INK, 16f)
        for (i in ENCS.indices)
            label(c, ENCS[i].first, ENCS[i].second - ENCS[i].third - 8f, ENC_SILK[i], SILK_INK, 16f)
        rrect(c, 1134f, 302f, 112f, 72f, 10f, 0xFF3C6B43.toInt())                           // PLAY/REC outlined
        rrect(c, 1136f, 304f, 108f, 68f, 9f, TRAY)
        label(c, 640f, 552f, "ZULUTEK", BRAND_INK, 10f)

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
        // buttons: shadow, cap, label (panel.rs draw)
        for (b in BTNS) {
            val i = M2I[b[0]]
            val led = ledFor(i)
            val down = zone == 2 && idx == i
            rrect(c, b[1] + 1f, b[2] + 3f, b[3].toFloat(), b[4].toFloat(), 9f, 0xFF1C1D20.toInt())  // shadow
            val tint = if (b[0] == 12) GREEN_LED else AMBER_LED
            val top = if (led > 0f) mix(BTN_TOP, tint, 0.85f) else BTN_TOP
            val bot = if (led > 0f) mix(BTN_BOT, tint, 0.7f) else BTN_BOT
            val off = if (down) 2f else 0f
            rrect(c, b[1].toFloat(), b[2] + off, b[3].toFloat(), b[4].toFloat(), 9f, top, bot)
            val txt = if (st.labelsZulu) ZULU_LABELS[i] else SILK[b[0]]
            label(c, b[1] + b[3] / 2f, b[2] + b[4] / 2f + 5f + off, txt,
                if (led > 0.5f) 0xFF2A1A03 else BTN_INK, 11f)
        }
        // encoders + the MASTER pot (decorative: the volume lives in the FX/MIX pages)
        for (i in ENCS.indices) {
            val (cx, cy, r) = ENCS[i]
            val glow = if (st.popUntil[i] > now) 1f else 0f
            knob(c, cx, cy, r, -135f + 270f * st.encVals[i], glow)
            label(c, cx, cy + r + 13f, st.encLabels[i], SILK_INK, 11f)
        }
        knob(c, POT.first, POT.second, POT.third, -135f + 270f * 0.5f, 0f)

        // keys: mint pills with a ridge, heat and ink follow the charte states
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
            val off = if (down) 3f else 0f
            val x = whiteKeyX(i)
            rrect(c, x + 1f, 680f + 4f, KW, 106f, KW / 2f, 0xFF1A1A1C.toInt())              // shadow
            rrect(c, x, 680f + off, KW, 106f, KW / 2f, mix(KEY_TOP, AMBER_LED, heat), mix(KEY_BOT, AMBER_LED, heat * 0.8f))
            rrect(c, x + KW / 2f - 2.5f, 680f + 18f + off, 5f, 106f - 36f, 2.5f,
                if (heat > 0f) 0xFFEFE2B0 else RIDGE)
        }
        for (bi in BLACK_NOTES.indices) {
            val note = BLACK_NOTES[bi]
            val down = zone == 4 && idx == bi
            val heat = if (note == st.curNote) 0.75f else if (down) 0.75f else 0f
            val off = if (down) 3f else 0f
            val x = blackKeyX(bi)
            rrect(c, x + 1f, 574f + 4f, KW, 94f, KW / 2f, 0xFF1A1A1C.toInt())
            rrect(c, x, 574f + off, KW, 94f, KW / 2f, mix(KEY_TOP, AMBER_LED, heat), mix(KEY_BOT, AMBER_LED, heat * 0.8f))
            rrect(c, x + KW / 2f - 2.5f, 574f + 18f + off, 5f, 94f - 36f, 2.5f,
                if (heat > 0f) 0xFFEFE2B0.toInt() else RIDGE)
        }
        c.restore()
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.argb(255,
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val px = toX(ev.x)
        val py = toY(ev.y)
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
                    val v = (startVal + (startY - py) / 180f).coerceIn(0f, 1f)
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
