package com.zulutek.drums

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Grille drum zuluTek v2 (façon PĒRKONS) : rangée de 16 steps en haut (hauteur = vélocité,
 * couleur = COND, petit trait = slide) + pads 4x4 en bas.
 * - mode STEP (editMode) : tap step = toggle on/off ; long-press step = cycle COND
 * - sinon : tap pad = hit (et REC si actif)
 */
class DrumGridView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var editMode = false
    var selPad = 0
    var curStep = -1
    /** steps du pad sélectionné, relues depuis le moteur : intArrayOf(on, vel, cond, slide) */
    val steps = Array(DrumsAudio.NSTEPS) { intArrayOf(0, 0, 0, 0) }

    var onPadTap: ((pad: Int) -> Unit)? = null
    var onStepTap: ((step: Int) -> Unit)? = null
    var onStepLong: ((step: Int) -> Unit)? = null

    private val bg = Paint().apply { color = Color.rgb(10, 10, 16) }
    private val ink = Paint().apply { color = Color.rgb(235, 238, 245); isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val dim = Paint().apply { color = Color.rgb(90, 94, 108); isAntiAlias = true }
    private val turq = Paint().apply { color = Color.rgb(0, 255, 224); isAntiAlias = true }
    private val condPaints = arrayOf(
        Paint().apply { color = Color.rgb(0, 255, 224); isAntiAlias = true },   // 1:1 turquoise
        Paint().apply { color = Color.rgb(255, 238, 0); isAntiAlias = true },   // 1:2 jaune
        Paint().apply { color = Color.rgb(255, 170, 60); isAntiAlias = true },  // 1:3 orange
        Paint().apply { color = Color.rgb(255, 120, 60); isAntiAlias = true },  // 1:4
        Paint().apply { color = Color.rgb(255, 70, 70); isAntiAlias = true },   // 1:8 rouge
        Paint().apply { color = Color.rgb(200, 90, 255); isAntiAlias = true })  // FILL violet
    private val padPaints = Array(DrumsAudio.NPADS) { i ->
        Paint().apply {
            isAntiAlias = true
            color = when (i) {
                0 -> Color.rgb(255, 70, 70)
                1 -> Color.rgb(255, 170, 60)
                2 -> Color.rgb(255, 238, 0)
                3 -> Color.rgb(255, 120, 220)
                4 -> Color.rgb(150, 90, 255)
                5 -> Color.rgb(0, 200, 255)
                6 -> Color.rgb(0, 255, 140)
                else -> Color.rgb(120, 160, 255)
            }
        }
    }
    private val ring = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.rgb(90, 94, 108) }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawRect(0f, 0f, w, h, bg)
        // ---- 16 steps : hauteur = vélocité, couleur = COND ----
        val stepH = h * 0.22f
        val sw = w / DrumsAudio.NSTEPS
        for (s in 0 until DrumsAudio.NSTEPS) {
            val st = steps[s]
            val on = st[0] == 1
            val velH = stepH * 0.14f + (stepH * 0.66f) * (st[1].coerceIn(0, 127) / 127f)
            val p = if (s == curStep) turq else if (on) condPaints[st[2].coerceIn(0, 5)] else dim
            c.drawRect(s * sw + 1f, stepH - 4f - velH, (s + 1) * sw - 1f, stepH - 4f, p)
            if (st[3] != 0) {  // slide : petit trait décalé
                val dx = st[3] * sw * 0.09f
                c.drawRect(s * sw + sw / 2f + dx - 1.5f, 2f, s * sw + sw / 2f + dx + 1.5f, 6f, ink)
            }
            if (s % 4 == 0) {
                ink.textSize = stepH * 0.20f
                c.drawText("${s + 1}", s * sw + sw / 2f, stepH + stepH * 0.18f, ink)
            }
        }
        ink.textSize = stepH * 0.26f
        c.drawText(if (editMode) "STEP: ${DrumsAudio.PAD_NAMES[selPad]} (tap=on/off, long=COND)" else "PLAY — ${DrumsAudio.PAD_NAMES[selPad]}",
            w / 2f, stepH + stepH * 0.52f, if (editMode) turq else ink)
        // ---- pads 4x4 ----
        val gy0 = stepH + stepH * 0.72f
        val gh = h - gy0 - 8f
        val cw = w / 4f
        val ch = gh / 4f
        for (p in 0 until DrumsAudio.NPADS) {
            val col = p % 4; val row = p / 4
            val cx = col * cw + cw / 2f
            val cy = gy0 + row * ch + ch / 2f
            val r = minOf(cw, ch) * 0.36f
            if (p == selPad) { turq.style = Paint.Style.STROKE; turq.strokeWidth = 3f; c.drawCircle(cx, cy, r + 4f, turq) }
            else c.drawCircle(cx, cy, r, ring)
            c.drawCircle(cx, cy, r, padPaints[p])
            ink.textSize = r * 0.42f
            c.drawText(DrumsAudio.PAD_NAMES[p].take(4), cx, cy + r * 0.15f, ink)
        }
    }

    private var downStep = -1
    private var downTime = 0L

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val w = width.toFloat(); val h = height.toFloat()
        val stepH = h * 0.22f
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                if (ev.y < stepH) {
                    downStep = (ev.x / (w / DrumsAudio.NSTEPS)).toInt().coerceIn(0, DrumsAudio.NSTEPS - 1)
                    downTime = System.currentTimeMillis()
                } else {
                    val gy0 = stepH + stepH * 0.72f
                    val cw = w / 4f
                    val ch = (h - gy0 - 8f) / 4f
                    val col = (ev.x / cw).toInt().coerceIn(0, 3)
                    val row = ((ev.y - gy0) / ch).toInt().coerceIn(0, 3)
                    selPad = row * 4 + col
                    onPadTap?.invoke(selPad)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (downStep >= 0) {
                    if (System.currentTimeMillis() - downTime > 450) onStepLong?.invoke(downStep)
                    else if (editMode) onStepTap?.invoke(downStep)
                    downStep = -1
                }
            }
        }
        return true
    }

    fun refreshSteps() {
        for (s in 0 until DrumsAudio.NSTEPS) steps[s] = DrumsAudio.getStep(selPad, s)
    }
}
