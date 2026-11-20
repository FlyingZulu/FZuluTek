package com.zulutek.drums

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Grille drum zuluTek : rangée de 16 steps en haut (mode STEP) + pads 4x4 en bas.
 * - mode STEP (editMode=true) : tap sur une step = toggle du pad sélectionné
 * - sinon : tap sur un pad = hit ; tap sur une step = sélectionne le pad de cette step? non :
 *   tap step hors mode STEP = rien (les steps servent à visualiser la lecture).
 */
class DrumGridView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var editMode = false
    var selPad = 0
    var curStep = -1
    /** (pad, step) -> on, mis à jour depuis le moteur au touch */
    val pat = Array(DrumsAudio.NPADS) { BooleanArray(DrumsAudio.NSTEPS) }

    var onPadTap: ((pad: Int) -> Unit)? = null
    var onStepTap: ((step: Int) -> Unit)? = null

    private val bg = Paint().apply { color = Color.rgb(10, 10, 16) }
    private val ink = Paint().apply { color = Color.rgb(235, 238, 245); isAntiAlias = true }
    private val dim = Paint().apply { color = Color.rgb(90, 94, 108); isAntiAlias = true }
    private val turq = Paint().apply { color = Color.rgb(0, 255, 224); isAntiAlias = true }
    private val padPaints = Array(DrumsAudio.NPADS) { i ->
        Paint().apply {
            isAntiAlias = true
            color = when (i) {
                0 -> Color.rgb(255, 70, 70)    // KICK
                1 -> Color.rgb(255, 170, 60)   // SNARE
                2, 3 -> Color.rgb(255, 238, 0) // HATS
                4 -> Color.rgb(255, 120, 220)  // CLAP
                5 -> Color.rgb(150, 90, 255)   // TOM
                6 -> Color.rgb(0, 200, 255)    // CONGA
                else -> Color.rgb(0, 255, 140)  // COWBELL
            }
        }
    }
    private val txt = Paint().apply { color = Color.rgb(235, 238, 245); isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val txtDim = Paint().apply { color = Color.rgb(90, 94, 108); isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val ring = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f
        color = Color.rgb(90, 94, 108)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawRect(0f, 0f, w, h, bg)
        // ---- rangée des 16 steps ----
        val stepH = h * 0.16f
        val sw = w / DrumsAudio.NSTEPS
        for (s in 0 until DrumsAudio.NSTEPS) {
            val on = pat[selPad][s]
            val p = if (s == curStep) turq else if (on) padPaints[selPad] else dim
            c.drawRect(s * sw + 1f, 4f, (s + 1) * sw - 1f, stepH - 4f, p.apply { style = Paint.Style.FILL })
            if (s % 4 == 0) {
                txtDim.textSize = stepH * 0.28f
                c.drawText("${s + 1}", s * sw + sw / 2f, stepH + stepH * 0.22f, txtDim)
            }
        }
        txt.textSize = stepH * 0.34f
        c.drawText(if (editMode) "STEP: ${DrumsAudio.PAD_NAMES[selPad]}" else "PLAY",
            w / 2f, stepH + stepH * 0.62f, if (editMode) turq else ink)
        // ---- grille 4x4 pads ----
        val gy0 = stepH + stepH * 0.85f
        val gh = h - gy0 - 8f
        val cw = w / 4f
        val ch = gh / 4f
        for (p in 0 until DrumsAudio.NPADS) {
            val col = p % 4; val row = p / 4
            val cx = col * cw + cw / 2f
            val cy = gy0 + row * ch + ch / 2f
            val r = minOf(cw, ch) * 0.36f
            val sel = (p == selPad)
            if (sel) { turq.style = Paint.Style.STROKE; turq.strokeWidth = 3f; c.drawCircle(cx, cy, r + 4f, turq) }
            else c.drawCircle(cx, cy, r, ring)
            c.drawCircle(cx, cy, r, padPaints[p])
            txt.textSize = r * 0.42f
            c.drawText(DrumsAudio.PAD_NAMES[p].take(4), cx, cy + r * 0.15f, txt)
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action != MotionEvent.ACTION_DOWN) return true
        val w = width.toFloat(); val h = height.toFloat()
        val stepH = h * 0.16f
        val x = ev.x; val y = ev.y
        if (y < stepH) {
            val s = (x / (w / DrumsAudio.NSTEPS)).toInt().coerceIn(0, DrumsAudio.NSTEPS - 1)
            if (editMode) onStepTap?.invoke(s) else { /* en mode PLAY, tap step = rien */ }
        } else {
            val gy0 = stepH + stepH * 0.85f
            val cw = w / 4f
            val ch = (h - gy0 - 8f) / 4f
            val col = (x / cw).toInt().coerceIn(0, 3)
            val row = ((y - gy0) / ch).toInt().coerceIn(0, 3)
            val pad = row * 4 + col
            selPad = pad
            onPadTap?.invoke(pad)
            invalidate()
        }
        return true
    }

    fun refreshPat() {
        for (p in 0 until DrumsAudio.NPADS)
            for (s in 0 until DrumsAudio.NSTEPS)
                pat[p][s] = DrumsAudio.getStep(p, s)
    }
}
