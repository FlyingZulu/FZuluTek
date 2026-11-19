package com.zulu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

// Greffe ZuluDrums : DrumSeqView extrait de ZuluControls.kt (ZuluFx) avec ses 4 dépendances
// (ZuluTheme, markerFont, couleurs, extension Paint.fill).
private val amber = Color.rgb(255, 190, 0)
private val silk = Color.rgb(165, 172, 185)

private fun Paint.fill(color: Int) {
    style = Paint.Style.FILL
    this.color = color
}

object ZuluTheme {
    var retro = false
    var gameboy = false
    var electribe = false
    var caustic = false
    var fontId = 0
    var xFiles = false
    var bgColor = Color.rgb(16, 18, 23)
    var bgDots = false
    var zebraWidthDp = 12f
    var dotSizeDp = 3f
    var zebraColor = Color.WHITE

    val panel: Int
        get() = when {
            gameboy -> Color.rgb(30, 58, 42)
            electribe -> Color.rgb(52, 52, 58)
            caustic -> Color.rgb(38, 50, 66)
            retro -> Color.rgb(56, 51, 42)
            else -> Color.rgb(38, 42, 52)
        }

    val slot: Int
        get() = when {
            gameboy -> Color.rgb(24, 46, 33)
            electribe -> Color.rgb(42, 42, 47)
            caustic -> Color.rgb(30, 40, 54)
            retro -> Color.rgb(44, 40, 33)
            else -> Color.rgb(30, 33, 40)
        }

    val edge: Int
        get() = when {
            gameboy -> Color.rgb(78, 128, 92)
            electribe -> Color.rgb(128, 64, 58)
            caustic -> Color.rgb(74, 96, 124)
            retro -> Color.rgb(126, 114, 92)
            else -> Color.rgb(75, 82, 98)
        }

    val padBg: Int
        get() = when {
            gameboy -> Color.rgb(15, 32, 22)
            electribe -> Color.rgb(34, 34, 37)
            caustic -> Color.rgb(18, 24, 33)
            retro -> Color.rgb(38, 34, 28)
            else -> Color.rgb(20, 22, 28)
        }
}

fun markerFont(context: Context): Typeface = when (ZuluTheme.fontId) {
    1 -> Typeface.SANS_SERIF
    2 -> runCatching {
        Typeface.createFromAsset(context.assets, "fonts/PermanentMarker-Regular.ttf")
    }.getOrDefault(Typeface.DEFAULT)
    else -> runCatching {
        Typeface.createFromAsset(context.assets, "fonts/DotGothic16-Regular.ttf")
    }.getOrDefault(Typeface.DEFAULT)
}

class DrumSeqView(context: Context) : View(context) {

    val rows = 7
    val cols = 16
    var playCol = -1
    var probaMode = false
    var rollMode = false
    var lenMode = false
    var panMode = false
    var glitchMode = false
    var mutMode = false
    val pan = Array(7) { FloatArray(32) { 0.5f } }
    val glitch = Array(7) { BooleanArray(32) }
    val mut = Array(7) { BooleanArray(32) }
    var playStep = -1
    val rowLen = IntArray(7) { 16 }
    var rowNames = Array(7) { "D${it + 1}" }
    var page = 0
    var onSwipe: ((Boolean) -> Unit)? = null
    val steps = Array(7) { BooleanArray(32) }
    val slides = Array(7) { FloatArray(32) }
    val vel = Array(7) { FloatArray(32) { 1f } }
    val prob = Array(7) { FloatArray(32) { 1f } }
    val roll = Array(7) { IntArray(32) }

    private class Snap(
        val steps: Array<BooleanArray>, val slides: Array<FloatArray>,
        val vel: Array<FloatArray>, val prob: Array<FloatArray>,
        val roll: Array<IntArray>, val pan: Array<FloatArray>, val rowLen: IntArray,
        val glitch: Array<BooleanArray>, val mut: Array<BooleanArray>
    )

    private var undoSnap: Snap? = null

    fun pushUndo() {
        undoSnap = Snap(
            Array(7) { steps[it].copyOf() }, Array(7) { slides[it].copyOf() },
            Array(7) { vel[it].copyOf() }, Array(7) { prob[it].copyOf() },
            Array(7) { roll[it].copyOf() }, Array(7) { pan[it].copyOf() }, rowLen.copyOf(),
            Array(7) { glitch[it].copyOf() }, Array(7) { mut[it].copyOf() }
        )
    }

    fun undo() {
        val s = undoSnap ?: return
        for (r in 0 until 7) {
            steps[r] = s.steps[r].copyOf()
            slides[r] = s.slides[r].copyOf()
            vel[r] = s.vel[r].copyOf()
            prob[r] = s.prob[r].copyOf()
            roll[r] = s.roll[r].copyOf()
            pan[r] = s.pan[r].copyOf()
            rowLen[r] = s.rowLen[r]
            glitch[r] = s.glitch[r].copyOf()
            mut[r] = s.mut[r].copyOf()
        }
        undoSnap = null
        invalidate()
    }

    fun doubleTo32() {
        for (r in 0 until 7) {
            for (c in 0 until 16) {
                steps[r][c + 16] = steps[r][c]
                slides[r][c + 16] = slides[r][c]
                vel[r][c + 16] = vel[r][c]
                prob[r][c + 16] = prob[r][c]
                roll[r][c + 16] = roll[r][c]
                pan[r][c + 16] = pan[r][c]
                glitch[r][c + 16] = glitch[r][c]
                mut[r][c + 16] = mut[r][c]
            }
        }
        page = 1
        invalidate()
    }

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    private var dragRow = -1
    private var dragCol = -1
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartOff = 0f
    private var dragStartVel = 1f
    private var dragStartProb = 1f
    private var dragStartRoll = 0
    private var dragStartPan = 0.5f
    private var dragStartLen = 16
    private var dragWasOn = false

    private fun cellW(): Float {
        val gap = 2.5f * d
        val labW = 30f * d
        return (width - labW - gap * (cols + 1)) / cols
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = 2.5f * d
        val labW = 30f * d
        val top = 4f * d
        val cw = cellW()
        val cellH = (h - top - gap * (rows + 1)) / rows

        paint.fill(Color.rgb(16, 18, 24))
        canvas.drawRoundRect(0f, 0f, w, h, 4f * d, 4f * d, paint)

        paint.typeface = marker
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 9f * d
        paint.color = if (page == 0) silk else amber
        canvas.drawText("P${page + 1}", w - 12f * d, 12f * d, paint)
        for (r in 0 until rows) {
            val t = top + gap + r * (cellH + gap)
            paint.textSize = min(9f * d, cellH * 0.5f)
            paint.color = Color.rgb(150, 158, 172)
            val rlLbl = if (rowLen[r] != 16) rowNames[r] + "\u00b7" + rowLen[r] else rowNames[r]
            canvas.drawText(rlLbl, labW / 2f, t + cellH * 0.68f, paint)
            for (c in 0 until cols) {
                val dc = page * 16 + c
                val off = slides[r][dc]
                val l = labW + gap + (c + off) * (cw + gap) + gap
                val on = steps[r][dc]
                val isPlay = playStep >= 0 && playStep % rowLen[r] == dc
                val v = vel[r][dc]
                paint.fill(
                    when {
                        on && isPlay -> Color.rgb(255, (244 * v).toInt(), (130 * v).toInt())
                        on -> Color.rgb((255 * v).toInt(), (170 * v).toInt(), (20 * v).toInt())
                        isPlay -> Color.rgb(52, 56, 68)
                        c % 4 == 0 -> Color.rgb(34, 38, 48)
                        else -> Color.rgb(26, 30, 38)
                    }
                )
                canvas.drawRoundRect(l, t, l + cw, t + cellH, 3f * d, 3f * d, paint)
                if (on && roll[r][dc] > 0) {
                    paint.color = Color.rgb(20, 22, 28)
                    paint.textSize = min(7f * d, cellH * 0.42f)
                    canvas.drawText("R${roll[r][dc]}", l + cw / 2f, t + cellH * 0.72f, paint)
                } else if (on && prob[r][dc] < 0.99f) {
                    paint.color = Color.rgb(20, 22, 28)
                    paint.textSize = min(7f * d, cellH * 0.42f)
                    canvas.drawText("P", l + cw / 2f, t + cellH * 0.72f, paint)
                }
                if (on && (pan[r][dc] < 0.48f || pan[r][dc] > 0.52f)) {
                    paint.fill(Color.rgb(20, 22, 28))
                    val py = t + cellH - 2.5f * d
                    canvas.drawCircle(l + cw * pan[r][dc], py, 2f * d, paint)
                    paint.fill(Color.rgb(0, 240, 255))
                    canvas.drawCircle(l + cw * pan[r][dc], py, 1.2f * d, paint)
                }
                if (glitch[r][dc]) {
                    paint.fill(Color.rgb(255, 255, 255))
                    canvas.drawCircle(l + 4f * d, t + 4f * d, 1.5f * d, paint)
                }
                if (mut[r][dc]) {
                    paint.fill(Color.rgb(190, 120, 255))
                    canvas.drawCircle(l + cw - 4f * d, t + 4f * d, 1.5f * d, paint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val h = height.toFloat()
        val gap = 2.5f * d
        val labW = 30f * d
        val top = 4f * d
        val cw = cellW()
        val cellH = (h - top - gap * (rows + 1)) / rows
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val r = ((event.y - top - gap) / (cellH + gap)).toInt().coerceIn(0, rows - 1)
                val t = top + gap + r * (cellH + gap)
                if (event.y < t || event.y > t + cellH) return true
                val cx = event.x - labW - gap
                val c = (cx / (cw + gap)).toInt().coerceIn(0, cols - 1)
                val dc = page * 16 + c
                if (glitchMode || mutMode) {
                    pushUndo()
                    if (glitchMode) glitch[r][dc] = !glitch[r][dc] else mut[r][dc] = !mut[r][dc]
                    invalidate()
                    return true
                }
                pushUndo()
                dragRow = r
                dragCol = dc
                dragStartX = event.x
                dragStartY = event.y
                dragStartOff = slides[r][dc]
                dragStartVel = vel[r][dc]
                dragStartProb = prob[r][dc]
                dragStartRoll = roll[r][dc]
                dragStartPan = pan[r][dc]
                dragStartLen = rowLen[r]
                dragWasOn = steps[r][dc]
                if (!steps[r][dc]) {
                    steps[r][dc] = true
                    performClick()
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragRow >= 0 && !dragWasOn && Math.abs(event.x - dragStartX) > 50f * d) {
                    onSwipe?.invoke(event.x > dragStartX)
                    dragRow = -1
                    dragCol = -1
                    return true
                }
                if (dragRow >= 0 && steps[dragRow][dragCol]) {
                    if (Math.abs(event.x - dragStartX) > 6f * d || Math.abs(event.y - dragStartY) > 6f * d) {
                        val dy = (event.y - dragStartY) / (h * 0.7f)
                        vel[dragRow][dragCol] = (dragStartVel - dy).coerceIn(0.05f, 1f)
                        val dx = (event.x - dragStartX) / (cw + gap)
                        when {
                            lenMode -> rowLen[dragRow] =
                                (dragStartLen + Math.round(dx * 4f)).coerceIn(2, 32)
                            rollMode -> roll[dragRow][dragCol] =
                                (dragStartRoll + Math.round(dx * 2f)).coerceIn(0, 3)
                            panMode -> pan[dragRow][dragCol] =
                                (dragStartPan + dx * 0.5f).coerceIn(0f, 1f)
                            probaMode -> prob[dragRow][dragCol] =
                                (dragStartProb + dx).coerceIn(0f, 1f)
                            else -> slides[dragRow][dragCol] =
                                (dragStartOff + dx).coerceIn(-0.45f, 0.45f)
                        }
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragRow >= 0 && event.actionMasked == MotionEvent.ACTION_UP &&
                    Math.abs(event.x - dragStartX) < 6f * d && Math.abs(event.y - dragStartY) < 6f * d &&
                    dragWasOn
                ) {
                    steps[dragRow][dragCol] = false
                    slides[dragRow][dragCol] = 0f
                    vel[dragRow][dragCol] = 1f
                    prob[dragRow][dragCol] = 1f
                    roll[dragRow][dragCol] = 0
                    pan[dragRow][dragCol] = 0.5f
                }
                dragRow = -1
                dragCol = -1
                invalidate()
            }
        }
        return true
    }
}
