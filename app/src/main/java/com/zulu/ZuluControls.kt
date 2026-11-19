package com.zulu

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val cream = Color.rgb(236, 224, 196)
private val ink = Color.rgb(40, 38, 32)
private val needleRed = Color.rgb(200, 60, 40)
private val amber = Color.rgb(255, 190, 0)
private val silk = Color.rgb(165, 172, 185)

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

class PlayShapeDrawable : Drawable() {
    var playing = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val d = Resources.getSystem().displayMetrics.density
    override fun draw(c: Canvas) {
        val r = bounds
        if (r.isEmpty) return
        val inset = 5f * d
        p.style = Paint.Style.FILL
        p.color = if (playing) Color.rgb(70, 220, 120) else Color.rgb(220, 60, 50)
        if (playing) {
            path.reset()
            path.moveTo(r.left + inset, r.top + inset)
            path.lineTo(r.right - inset, r.exactCenterY())
            path.lineTo(r.left + inset, r.bottom - inset)
            path.close()
            c.drawPath(path, p)
        } else {
            c.drawRoundRect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset, 4f * d, 4f * d, p)
        }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class ThemeButtonDrawable(private val round: Boolean) : Drawable() {
    var fillColor = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = Resources.getSystem().displayMetrics.density
    override fun draw(c: Canvas) {
        val r = bounds
        if (r.isEmpty) return
        val cx = r.exactCenterX()
        val cy = r.exactCenterY()
        val rad = if (round) min(r.width(), r.height()) / 2f else 6f * d
        p.style = Paint.Style.FILL
        p.color = if (fillColor != 0) fillColor else ZuluTheme.slot
        c.drawRoundRect(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), rad, rad, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d
        p.color = ZuluTheme.edge
        c.drawRoundRect(r.left + 0.5f * d, r.top + 0.5f * d, r.right - 0.5f * d, r.bottom - 0.5f * d, rad, rad, p)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal fun drawSketchyLine(
    canvas: Canvas,
    paint: Paint,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    seed: Int
) {
    val rnd = java.util.Random(seed.toLong())
    for (pass in 0..1) {
        val j0 = (rnd.nextFloat() - 0.5f) * 2.2f
        val j1 = (rnd.nextFloat() - 0.5f) * 2.2f
        val j2 = (rnd.nextFloat() - 0.5f) * 2.2f
        val j3 = (rnd.nextFloat() - 0.5f) * 2.2f
        canvas.drawLine(x0 + j0, y0 + j1, x1 + j2, y1 + j3, paint)
    }
}

internal fun drawRelief(c: Canvas, l: Float, t: Float, r: Float, b: Float, d: Float) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 1.2f * d
    p.color = Color.WHITE
    p.alpha = 16
    c.drawLine(l + 6f * d, t + 1.2f * d, r - 6f * d, t + 1.2f * d, p)
    p.color = Color.BLACK
    p.alpha = 42
    c.drawLine(l + 6f * d, b - 1.2f * d, r - 6f * d, b - 1.2f * d, p)
    p.color = Color.WHITE
    p.alpha = 8
    c.drawLine(l + 1.2f * d, t + 6f * d, l + 1.2f * d, b - 6f * d, p)
    p.color = Color.BLACK
    p.alpha = 26
    c.drawLine(r - 1.2f * d, t + 6f * d, r - 1.2f * d, b - 6f * d, p)
    p.alpha = 255
    p.style = Paint.Style.FILL
}

internal fun drawZebra(c: Canvas, w: Float, h: Float, d: Float) {
    val p = Paint()
    p.style = Paint.Style.STROKE
    if (ZuluTheme.retro) {
        p.strokeWidth = 5f * d
        p.color = Color.WHITE
        p.alpha = 26
        var x = -h
        while (x < w + h) {
            c.drawLine(x, 0f, x + h, h, p)
            x += 12f * d
        }
        p.alpha = 255
        return
    }
    p.strokeWidth = ZuluTheme.zebraWidthDp * d
    p.color = ZuluTheme.zebraColor
    p.alpha = 235
    val period = 40f * d
    var x = -h
    var n = 0
    while (x < w + h) {
        drawSketchyLine(c, p, x, 0f, x + h, h, 9000 + n)
        x += period
        n++
    }
    p.alpha = 255
    if (ZuluTheme.bgDots) {
        p.style = Paint.Style.FILL
        val r = ZuluTheme.dotSizeDp * d
        var row = 0
        var y = period * 0.5f
        while (y < h + period) {
            var px = -period + (if (row % 2 == 1) period * 0.5f else 0f)
            while (px < w + period) {
                val u = ((px - y) % period + period) % period
                val dist = min(u, period - u)
                p.color = if (dist < ZuluTheme.zebraWidthDp * d * 0.5f) ZuluTheme.bgColor else ZuluTheme.zebraColor
                p.alpha = 235
                c.drawCircle(px, y, r, p)
                px += period
            }
            y += period
            row++
        }
        p.alpha = 255
    }
}

class ZebraBgDrawable : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawColor(ZuluTheme.bgColor)
        val d = Resources.getSystem().displayMetrics.density
        if (ZuluTheme.electribe) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f * d
            p.color = Color.WHITE
            p.alpha = 26
            var y = 0f
            while (y < b.height()) {
                canvas.drawLine(0f, y, b.width().toFloat(), y, p)
                y += 9f * d
            }
            p.alpha = 255
            return
        }
        if (ZuluTheme.caustic) {
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            p.alpha = 24
            var y = 4f * d
            while (y < b.height()) {
                var x = 4f * d
                while (x < b.width()) {
                    canvas.drawCircle(x, y, 1f * d, p)
                    x += 14f * d
                }
                y += 14f * d
            }
            p.alpha = 255
            return
        }
        if (ZuluTheme.gameboy) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2.5f * d
            p.color = Color.WHITE
            p.alpha = 26
            var y = 0f
            while (y < b.height()) {
                canvas.drawLine(0f, y, b.width().toFloat(), y, p)
                y += 5f * d
            }
            p.alpha = 18
            p.strokeWidth = 4f * d
            var x = 0f
            while (x < b.width()) {
                canvas.drawLine(x, 0f, x, b.height().toFloat(), p)
                x += 26f * d
            }
            p.alpha = 255
            return
        }
        drawZebra(canvas, b.width().toFloat(), b.height().toFloat(), d)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal fun drawScrews(
    canvas: Canvas,
    paint: Paint,
    l: Float,
    t: Float,
    r: Float,
    b: Float,
    d: Float
) {
    if (!ZuluTheme.electribe) return
    val inset = 7f * d
    val rad = 2.2f * d
    val pts = listOf(
        l + inset to t + inset, r - inset to t + inset,
        l + inset to b - inset, r - inset to b - inset
    )
    for ((x, y) in pts) {
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(48, 50, 56)
        canvas.drawCircle(x, y, rad, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.9f * d
        paint.color = Color.rgb(158, 163, 174)
        canvas.drawCircle(x, y, rad, paint)
        canvas.drawLine(x - rad * 0.7f, y - rad * 0.7f, x + rad * 0.7f, y + rad * 0.7f, paint)
        paint.style = Paint.Style.FILL
    }
}

internal fun drawSketchyRoundRect(
    canvas: Canvas,
    paint: Paint,
    l: Float,
    t: Float,
    r: Float,
    b: Float,
    rad: Float,
    seed: Int
) {
    val rnd = java.util.Random(seed.toLong())
    for (pass in 0..1) {
        val jx = (rnd.nextFloat() - 0.5f) * 2.4f
        val jy = (rnd.nextFloat() - 0.5f) * 2.4f
        canvas.drawRoundRect(l + jx, t + jy, r + jx, b + jy, rad, rad, paint)
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

private fun tint(c: Int, t: Int, f: Float): Int {
    val r = (Color.red(c) + (Color.red(t) - Color.red(c)) * f).toInt()
    val g = (Color.green(c) + (Color.green(t) - Color.green(c)) * f).toInt()
    val b = (Color.blue(c) + (Color.blue(t) - Color.blue(c)) * f).toInt()
    return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

private fun Paint.fill(color: Int) {
    style = Paint.Style.FILL
    this.color = color
}

private fun Paint.stroke(color: Int, width: Float) {
    style = Paint.Style.STROKE
    this.color = color
    strokeWidth = width
}

class BarVuView(context: Context) : View(context) {

    val cmdId = "vu"

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    var levelL = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    var levelR = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    var inLevelL = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    var inLevelR = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    var overlayText: String? = null
        set(v) {
            field = v
            invalidate()
        }

    var outFilter = 0f
        set(v) {
            field = v.coerceIn(-1f, 1f)
            invalidate()
        }

    var inFilter = 0f
        set(v) {
            field = v.coerceIn(-1f, 1f)
            invalidate()
        }

    var onLongPress: (() -> Unit)? = null
    var onVolDelta: ((Float) -> Unit)? = null
    var onInVolDelta: ((Float) -> Unit)? = null
    var onOutFilterDelta: ((Float) -> Unit)? = null
    var onInFilterDelta: ((Float) -> Unit)? = null
    var onOutFilterReset: (() -> Unit)? = null
    var onInFilterReset: (() -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null

    var pressed = false
        private set
    var onPressed: ((Boolean) -> Unit)? = null

    private var longPressPending = false
    private var lastX = 0f
    private var lastY = 0f
    private var swipeAxis = 0
    private var swipeSide = 0
    private var tapCount = 0
    private var tapAt = 0L

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.fill(Color.rgb(213, 236, 200))
        canvas.drawRoundRect(0f, 0f, w, h, 3f * d, 3f * d, paint)
        paint.stroke(Color.rgb(126, 152, 116), 1.2f * d)
        canvas.drawRoundRect(0.6f * d, 0.6f * d, w - 0.6f * d, h - 0.6f * d, 3f * d, 3f * d, paint)

        val m = 4f * d
        val top = m + 9f * d
        val barW = (w - 5f * m) / 4f
        drawBar(canvas, m, top, m + barW, h - m, inLevelL, true)
        drawBar(canvas, m * 2f + barW, top, m * 2f + barW * 2f, h - m, inLevelR, true)
        drawBar(canvas, m * 3f + barW * 2f, top, m * 3f + barW * 3f, h - m, levelL, false)
        drawBar(canvas, m * 4f + barW * 3f, top, w - m, h - m, levelR, false)

        paint.fill(Color.rgb(62, 74, 62))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = marker
        paint.textSize = 7f * d
        val half = m + barW
        canvas.drawText("IN", m + half, m + 6.5f * d, paint)
        canvas.drawText("OUT", w - m - half, m + 6.5f * d, paint)

        paint.textSize = 6.5f * d
        paint.textAlign = Paint.Align.LEFT
        if (inFilter < -0.02f) canvas.drawText("LP", m, h - 1.5f * d, paint)
        else if (inFilter > 0.02f) canvas.drawText("HP", m, h - 1.5f * d, paint)
        paint.textAlign = Paint.Align.RIGHT
        if (outFilter < -0.02f) canvas.drawText("LP", w - m, h - 1.5f * d, paint)
        else if (outFilter > 0.02f) canvas.drawText("HP", w - m, h - 1.5f * d, paint)

        overlayText?.let {
            paint.fill(Color.rgb(16, 18, 23))
            paint.alpha = 220
            canvas.drawRoundRect(w * 0.06f, h * 0.26f, w * 0.94f, h * 0.8f, 3f * d, 3f * d, paint)
            paint.alpha = 255
            paint.fill(Color.rgb(255, 244, 130))
            paint.textSize = 10f * d
            canvas.drawText(it, w / 2f, h * 0.64f, paint)
        }
    }

    private fun drawBar(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, level: Float, isIn: Boolean) {
        val segs = 20
        val segH = (b - t) / segs
        val lit = (level * segs).toInt()
        for (i in 0 until segs) {
            paint.fill(
                when {
                    i >= lit -> Color.rgb(184, 206, 176)
                    i >= segs * 85 / 100 -> Color.rgb(255, 0, 150)
                    i >= segs * 65 / 100 -> Color.rgb(212, 255, 0)
                    else -> if (isIn) Color.rgb(0, 190, 180) else Color.rgb(50, 190, 40)
                }
            )
            canvas.drawRoundRect(
                l + 1.5f * d,
                b - (i + 1) * segH + 1f,
                r - 1.5f * d,
                b - i * segH - 1f,
                2f * d,
                2f * d,
                paint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressPending = true
                pressed = true
                swipeAxis = 0
                swipeSide = if (event.x < width / 2f) 0 else 1
                lastX = event.x
                lastY = event.y
                onPressed?.invoke(true)
                postDelayed({
                    if (longPressPending) {
                        longPressPending = false
                        onLongPress?.invoke()
                    }
                }, 600)
                val now = System.currentTimeMillis()
                if (now - tapAt < 350) tapCount++ else tapCount = 1
                tapAt = now
                if (tapCount >= 2) {
                    tapCount = 0
                    if (onDoubleTap != null) onDoubleTap?.invoke()
                    else if (swipeSide == 0) onInFilterReset?.invoke() else onOutFilterReset?.invoke()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (swipeAxis == 0 && (Math.abs(dx) > 10f * d || Math.abs(dy) > 10f * d)) {
                    swipeAxis = if (Math.abs(dy) > Math.abs(dx)) 1 else 2
                    longPressPending = false
                }
                if (swipeAxis == 1) {
                    if (swipeSide == 0) onInVolDelta?.invoke(-(dy / (45f * d)))
                    else onVolDelta?.invoke(-(dy / (45f * d)))
                } else if (swipeAxis == 2) {
                    if (swipeSide == 0) onInFilterDelta?.invoke(dx / (45f * d))
                    else onOutFilterDelta?.invoke(dx / (45f * d))
                }
                if (swipeAxis != 0) {
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressPending = false
                if (pressed) {
                    pressed = false
                    onPressed?.invoke(false)
                }
            }
        }
        return true
    }
}

class IoKnobView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    var label = "IN"
    var level = 0.5f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var filter = 0f
        set(v) { field = v.coerceIn(-1f, 1f); invalidate() }

    var onLevelDelta: ((Float) -> Unit)? = null
    var onFilterDelta: ((Float) -> Unit)? = null
    var onFilterReset: (() -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var downAt = 0L
    private var tapCount = 0
    private var tapAt = 0L

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.fill(Color.rgb(15, 24, 15))
        canvas.drawRoundRect(0f, 0f, w, h, 3f * d, 3f * d, paint)
        paint.stroke(Color.rgb(48, 99, 48), 1.2f * d)
        canvas.drawRoundRect(0.6f * d, 0.6f * d, w - 0.6f * d, h - 0.6f * d, 3f * d, 3f * d, paint)

        val segs = 7
        val segW = (w - 8f * d - 20f * d) / segs
        val lit = (level * segs).toInt()
        for (i in 0 until segs) {
            paint.fill(if (i < lit) Color.rgb(155, 188, 15) else Color.rgb(48, 99, 48))
            canvas.drawRect(4f * d + i * (segW + 0.5f * d), h / 2f - 3f * d,
                4f * d + (i + 1) * segW + i * 0.5f * d, h / 2f + 3f * d, paint)
        }

        paint.fill(Color.rgb(155, 188, 15))
        paint.textSize = 8f * d
        paint.typeface = marker
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(label, w - 3f * d, h / 2f + 3f * d, paint)
        paint.textAlign = Paint.Align.LEFT
        if (filter < -0.02f) {
            canvas.drawText("LP", 3f * d, 7.5f * d, paint)
        } else if (filter > 0.02f) {
            canvas.drawText("HP", 3f * d, 7.5f * d, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = false
                downAt = System.currentTimeMillis()
                val now = System.currentTimeMillis()
                if (now - tapAt < 350) tapCount++ else tapCount = 1
                tapAt = now
                if (tapCount >= 2) {
                    tapCount = 0
                    filter = 0f
                    onFilterReset?.invoke()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && System.currentTimeMillis() - downAt > 150) dragging = true
                if (dragging) {
                    val dy = (event.y - lastY) / (height * 1.6f)
                    val dx = (event.x - lastX) / (width * 1.6f)
                    if (Math.abs(dx) > Math.abs(dy)) {
                        filter = (filter - dx).coerceIn(-1f, 1f)
                        onFilterDelta?.invoke(filter)
                    } else {
                        level = (level - dy).coerceIn(0f, 1f)
                        onLevelDelta?.invoke(level)
                    }
                }
                lastX = event.x
                lastY = event.y
            }
        }
        invalidate()
        return true
    }
}

class BpmLcdView(context: Context) : View(context) {

    val cmdId = "bpm"

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var onBpmChange: ((Float) -> Unit)? = null

    var onLongPress: (() -> Unit)? = null
    private var longPressPending = false
    var catOutfit = 0
    var catBodyColor = -1
    var onCatTap: (() -> Unit)? = null
    private val catRect = android.graphics.RectF()
    private val sirenZone = android.graphics.RectF()
    var shiftCheck: (() -> Boolean)? = null
    var onSirenTouch: ((Boolean, Float, Float) -> Unit)? = null
    var onInVolDelta: ((Float) -> Unit)? = null
    var onOutVolDelta: ((Float) -> Unit)? = null
    var onInFilterDelta: ((Float) -> Unit)? = null
    var onOutFilterDelta: ((Float) -> Unit)? = null
    var onInFilterReset: (() -> Unit)? = null
    var onOutFilterReset: (() -> Unit)? = null
    var onInLongPress: (() -> Unit)? = null
    var onOutLongPress: (() -> Unit)? = null
    var inFilter = 0f
    var outFilter = 0f
    private var edgeDrag = 0
    private var edgeAxis = 0
    private var edgeLastX = 0f
    private var edgeLastY = 0f
    private var edgeLongPending = false
    private var sirenActive = false
    private val idleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastActMs = 0L
    private var miaouOn = false
    private var catSleep = false
    private var miaouText = "MIAOU"
    private val miaouWords = listOf("MIAOU", "MOWOW", "MIAOOON", "MYAOU", "MRRIAOU", "MIAW", "MIAOU !", "PURRE")
    private var downX = 0f

    var bpm = 120f
        set(v) {
            field = v.coerceIn(40f, 200f)
            invalidate()
        }

    var trackName = ""
        set(v) {
            field = v.take(9)
            invalidate()
        }

    var groupLabel = "G1"
        set(v) {
            field = v.take(3)
            invalidate()
        }

    var bendCat = false
    private val catRnd = java.util.Random()

    var catInLevel = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
        }

    var catOutLevel = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
        }

    var hintText = ""
        set(v) {
            field = v.take(20)
            invalidate()
        }

    var statusText = ""
        set(v) {
            field = v.take(20)
            statusUntil = System.currentTimeMillis() + 2500
            invalidate()
            postDelayed({
                if (System.currentTimeMillis() >= statusUntil) {
                    field = ""
                    invalidate()
                }
            }, 2600)
        }

    private var statusUntil = 0L

    var inputEnabled = true

    var pressed = false
        private set
    var onPressed: ((Boolean) -> Unit)? = null

    private val taps = ArrayList<Long>()
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private var dragY = 0f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.fill(Color.rgb(213, 236, 200))
        canvas.drawRoundRect(0f, 0f, w, h, 3f * d, 3f * d, paint)
        paint.stroke(Color.rgb(126, 152, 116), 1.2f * d)
        canvas.drawRoundRect(0.6f * d, 0.6f * d, w - 0.6f * d, h - 0.6f * d, 3f * d, 3f * d, paint)

        val per = 60000f / bpm.coerceAtLeast(40f)
        val beatOn = (System.currentTimeMillis() % per.toLong()) < per / 2

        val bandW = 5f * d
        val bandM = 2.5f * d
        val bandT = 4f * d
        val bandB = h - 4f * d
        fun drawBand(x: Float, level: Float, isIn: Boolean) {
            val segs = 12
            val segH = (bandB - bandT) / segs
            val lit = (level * segs).toInt().coerceIn(0, segs)
            for (i in 0 until segs) {
                paint.fill(
                    when {
                        i >= lit -> Color.rgb(184, 206, 176)
                        i >= segs * 80 / 100 -> Color.rgb(255, 0, 150)
                        i >= segs * 60 / 100 -> Color.rgb(212, 255, 0)
                        else -> if (isIn) Color.rgb(0, 190, 180) else Color.rgb(50, 190, 40)
                    }
                )
                canvas.drawRoundRect(
                    x,
                    bandB - (i + 1) * segH + 1f,
                    x + bandW,
                    bandB - i * segH - 1f,
                    1.5f * d,
                    1.5f * d,
                    paint
                )
            }
        }
        drawBand(bandM, catInLevel, true)
        drawBand(w - bandM - bandW, catOutLevel, false)
        if (Math.abs(inFilter) > 0.02f) {
            paint.fill(Color.rgb(213, 236, 200))
            canvas.drawRect(bandM - 1f * d, bandT + 1.5f * d, bandM + bandW + 1f * d, bandT + 9.5f * d, paint)
            paint.fill(Color.rgb(38, 92, 34))
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.MONOSPACE
            paint.textSize = 6f * d
            canvas.drawText(if (inFilter < 0f) "LP" else "HP", bandM + bandW / 2f, bandT + 7.5f * d, paint)
        }
        if (Math.abs(outFilter) > 0.02f) {
            paint.fill(Color.rgb(213, 236, 200))
            canvas.drawRect(w - bandM - bandW - 1f * d, bandT + 1.5f * d, w - bandM + 1f * d, bandT + 9.5f * d, paint)
            paint.fill(Color.rgb(38, 92, 34))
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.MONOSPACE
            paint.textSize = 6f * d
            canvas.drawText(if (outFilter < 0f) "LP" else "HP", w - bandM - bandW / 2f, bandT + 7.5f * d, paint)
        }

        val catPx = min(3.6f * d, h * 0.075f)
        var catX = (w - 8f * catPx) / 2f
        var catY = 3f * d + if (beatOn) 0f else catPx * 0.45f
        val pale = Color.rgb(213, 236, 200)
        var bodyCol = when (catOutfit) {
            1 -> Color.rgb(200, 55, 45)
            2 -> Color.rgb(24, 26, 32)
            3 -> Color.rgb(24, 26, 32)
            4 -> Color.rgb(200, 145, 0)
            5 -> Color.rgb(60, 140, 50)
            6 -> Color.rgb(170, 40, 120)
            else -> Color.rgb(38, 92, 34)
        }
        if (catBodyColor >= 0) bodyCol = catBodyColor
        catRect.set(catX, catY, catX + 8f * catPx, catY + 7f * catPx)
        val vw = width.coerceAtLeast(1).toFloat()
        val vh = height.coerceAtLeast(1).toFloat()
        sirenZone.set(vw * 0.45f, 0f, vw, vh)
        if (bendCat) {
            catX += (catRnd.nextInt(5) - 2) * catPx * 0.5f
            catY += (catRnd.nextInt(5) - 2) * catPx * 0.5f
            bodyCol = when (catRnd.nextInt(4)) {
                0 -> Color.rgb(255, 90, 200)
                1 -> Color.rgb(0, 240, 255)
                2 -> Color.rgb(255, 190, 0)
                else -> Color.rgb(38, 92, 34)
            }
        }
        val u = catPx
        val hx = catX
        val hy = catY
        val lineCol = Color.rgb(40, 42, 48)
        val white = Color.rgb(252, 252, 250)
        var bowCol = when (catOutfit) {
            1 -> Color.rgb(200, 55, 45)
            2 -> Color.rgb(255, 60, 180)
            3 -> Color.rgb(90, 60, 200)
            4 -> Color.rgb(230, 160, 20)
            5 -> Color.rgb(60, 140, 50)
            6 -> Color.rgb(170, 40, 120)
            else -> Color.rgb(220, 60, 60)
        }
        if (catBodyColor >= 0) bowCol = catBodyColor
        if (bendCat) bowCol = when (catRnd.nextInt(4)) {
            0 -> Color.rgb(255, 90, 200)
            1 -> Color.rgb(0, 240, 255)
            2 -> Color.rgb(255, 190, 0)
            else -> bowCol
        }
        val head = android.graphics.Path()
        head.moveTo(hx + 1.0f * u, hy + 3.2f * u)
        head.quadTo(hx + 0.5f * u, hy + 1.9f * u, hx + 1.1f * u, hy + 1.0f * u)
        head.lineTo(hx + 2.7f * u, hy + 1.7f * u)
        head.quadTo(hx + 4.0f * u, hy + 1.1f * u, hx + 5.3f * u, hy + 1.7f * u)
        head.lineTo(hx + 6.9f * u, hy + 1.0f * u)
        head.quadTo(hx + 7.5f * u, hy + 1.9f * u, hx + 7.0f * u, hy + 3.2f * u)
        head.quadTo(hx + 7.5f * u, hy + 5.5f * u, hx + 4.0f * u, hy + 6.1f * u)
        head.quadTo(hx + 0.5f * u, hy + 5.5f * u, hx + 1.0f * u, hy + 3.2f * u)
        head.close()
        paint.fill(white)
        canvas.drawPath(head, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.30f
        paint.color = lineCol
        canvas.drawPath(head, paint)
        paint.strokeWidth = u * 0.20f
        canvas.drawLine(hx + 1.3f * u, hy + 3.3f * u, hx + 0.3f * u, hy + 2.9f * u, paint)
        canvas.drawLine(hx + 1.2f * u, hy + 3.9f * u, hx + 0.1f * u, hy + 3.9f * u, paint)
        canvas.drawLine(hx + 1.3f * u, hy + 4.5f * u, hx + 0.3f * u, hy + 4.9f * u, paint)
        canvas.drawLine(hx + 6.7f * u, hy + 3.3f * u, hx + 7.7f * u, hy + 2.9f * u, paint)
        canvas.drawLine(hx + 6.8f * u, hy + 3.9f * u, hx + 7.9f * u, hy + 3.9f * u, paint)
        canvas.drawLine(hx + 6.7f * u, hy + 4.5f * u, hx + 7.7f * u, hy + 4.9f * u, paint)
        paint.style = Paint.Style.FILL
        if (beatOn && !catSleep) {
            paint.fill(lineCol)
            canvas.drawOval(android.graphics.RectF(hx + 1.9f * u, hy + 3.0f * u, hx + 2.7f * u, hy + 4.1f * u), paint)
            canvas.drawOval(android.graphics.RectF(hx + 5.3f * u, hy + 3.0f * u, hx + 6.1f * u, hy + 4.1f * u), paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = u * 0.28f
            paint.color = lineCol
            canvas.drawLine(hx + 1.9f * u, hy + 3.6f * u, hx + 2.7f * u, hy + 3.6f * u, paint)
            canvas.drawLine(hx + 5.3f * u, hy + 3.6f * u, hx + 6.1f * u, hy + 3.6f * u, paint)
            paint.style = Paint.Style.FILL
        }
        paint.fill(Color.rgb(255, 200, 60))
        canvas.drawOval(android.graphics.RectF(hx + 3.4f * u, hy + 4.0f * u, hx + 4.6f * u, hy + 4.7f * u), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.16f
        paint.color = lineCol
        canvas.drawOval(android.graphics.RectF(hx + 3.4f * u, hy + 4.0f * u, hx + 4.6f * u, hy + 4.7f * u), paint)
        paint.style = Paint.Style.FILL
        paint.fill(bowCol)
        canvas.drawCircle(hx + 1.2f * u, hy + 0.9f * u, 0.62f * u, paint)
        canvas.drawCircle(hx + 2.3f * u, hy + 0.7f * u, 0.55f * u, paint)
        canvas.drawCircle(hx + 1.75f * u, hy + 0.9f * u, 0.34f * u, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.14f
        paint.color = lineCol
        canvas.drawCircle(hx + 1.2f * u, hy + 0.9f * u, 0.62f * u, paint)
        canvas.drawCircle(hx + 2.3f * u, hy + 0.7f * u, 0.55f * u, paint)
        canvas.drawCircle(hx + 1.75f * u, hy + 0.9f * u, 0.34f * u, paint)
        paint.style = Paint.Style.FILL

        val rowdy = maxOf(catInLevel, catOutLevel)
        if (rowdy > 0.35f) {
            val energy = ((rowdy - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val ballR = catPx * 2.2f
            val jig = energy * catPx
            val ballX = catX + 10.5f * catPx + (catRnd.nextInt(7) - 3) * jig * 0.4f
            val ballY = catY + 3f * catPx -
                sin(System.currentTimeMillis() * 0.02f).coerceAtLeast(0f) * jig -
                if (beatOn) jig * 0.5f else 0f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = catPx * 0.5f
            paint.color = bodyCol
            canvas.drawCircle(ballX, ballY, ballR, paint)
            paint.strokeWidth = catPx * 0.35f
            canvas.drawArc(ballX - ballR, ballY - ballR, ballX + ballR, ballY + ballR, 30f, 120f, false, paint)
            canvas.drawArc(ballX - ballR * 0.7f, ballY - ballR * 0.7f, ballX + ballR * 0.7f, ballY + ballR * 0.7f, 200f, 100f, false, paint)
            paint.style = Paint.Style.FILL
            val plugX = ballX + ballR + catPx * 1.2f
            val plugY = ballY + ballR * 0.7f
            canvas.drawRoundRect(plugX, plugY - catPx * 0.4f, plugX + catPx * 1.6f, plugY + catPx * 0.4f, catPx * 0.3f, catPx * 0.3f, paint)
            canvas.drawRect(plugX + catPx * 1.6f, plugY - catPx * 0.2f, plugX + catPx * 2.2f, plugY + catPx * 0.2f, paint)
        }

        val info = if (statusText.isNotEmpty() && System.currentTimeMillis() < statusUntil) statusText else hintText
        if (info.isNotEmpty()) {
            paint.fill(Color.rgb(38, 92, 34))
            paint.textSize = h * 0.24f
            paint.textAlign = Paint.Align.RIGHT
            paint.isFakeBoldText = true
            canvas.drawText(info, w - 4f * d, h * 0.28f, paint)
        }

        paint.fill(Color.rgb(38, 92, 34))
        paint.textSize = h * 0.22f
        paint.textAlign = Paint.Align.LEFT
        paint.isFakeBoldText = false
        canvas.drawText(trackName, bandM + bandW + 3f * d, h - 5f * d, paint)
        canvas.drawText(groupLabel, bandM + bandW + 3f * d + w * 0.20f, h - 5f * d, paint)
        paint.isFakeBoldText = true

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = h * 0.38f
        canvas.drawText(bpm.toInt().toString(), w - bandM - bandW - 3f * d, h - 4.5f * d, paint)
        paint.textSize = h * 0.17f
        canvas.drawText("BPM", w - bandM - bandW - 3f * d, h * 0.30f, paint)

        val px = 2.2f * d
        val bx = w - bandM - bandW - 34f * d
        val by = h - 4.5f * d - px * 4f
        paint.fill(if (beatOn) Color.rgb(38, 92, 34) else Color.rgb(160, 190, 150))
        canvas.drawRect(bx, by, bx + px, by + px, paint)
        canvas.drawRect(bx + px * 2f, by + px, bx + px * 3f, by + px * 2f, paint)
        canvas.drawRect(bx + px, by + px * 2f, bx + px * 2f, by + px * 3f, paint)
        canvas.drawRect(bx, by + px * 3f, bx + px, by + px * 4f, paint)
        canvas.drawRect(bx + px * 2f, by + px * 3f, bx + px * 3f, by + px * 4f, paint)

        if (lastActMs == 0L) pokeIdle()
        if (catSleep) {
            paint.fill(Color.rgb(62, 74, 62))
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.MONOSPACE
            paint.textSize = h * 0.16f
            val bob = (System.currentTimeMillis() % 1400 / 1400f) * 1.5f * d
            canvas.drawText("Zzz", catRect.right + 1f * d, catRect.top + bob, paint)
        }
        if (miaouOn) {
            val mh = 5.5f * d
            paint.textSize = mh * 0.52f
            paint.typeface = Typeface.MONOSPACE
            val mw = (paint.measureText(miaouText) + 4f * d).coerceAtLeast(8f * d)
            val mx = (catRect.left - mw - 2f * d).coerceAtLeast(bandM + bandW + 2f * d)
            val my = (catRect.top - 1f * d).coerceAtLeast(2f * d)
            paint.fill(Color.rgb(255, 255, 255))
            canvas.drawRoundRect(mx, my, mx + mw, my + mh, 2f * d, 2f * d, paint)
            canvas.drawRect(mx + mw, my + mh * 0.3f, catRect.left - 0.5f * d, my + mh * 0.3f + 1.3f * d, paint)
            paint.fill(Color.rgb(28, 30, 36))
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(miaouText, mx + mw * 0.5f, my + mh * 0.74f, paint)
        }
    }

    private val idleRunnable: Runnable = object : Runnable {
        override fun run() {
            val idle = System.currentTimeMillis() - lastActMs
            if (idle >= 40000) {
                if (!catSleep) {
                    catSleep = true
                    miaouOn = false
                    invalidate()
                }
                idleHandler.postDelayed(this, 15000)
            } else if (idle >= 8800) {
                miaouText = miaouWords[catRnd.nextInt(miaouWords.size)]
                miaouOn = true
                invalidate()
                idleHandler.postDelayed({
                    miaouOn = false
                    invalidate()
                    idleHandler.postDelayed(this, 12000)
                }, 2400)
            } else {
                idleHandler.postDelayed(this, 9000)
            }
        }
    }
    private fun pokeIdle() {
        lastActMs = System.currentTimeMillis()
        if (miaouOn || catSleep) {
            miaouOn = false
            catSleep = false
            invalidate()
        }
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, 9000)
    }

    private fun edgeStart(e: MotionEvent, side: Int) {
        edgeDrag = side
        edgeAxis = 0
        edgeLongPending = true
        edgeLastX = e.x
        edgeLastY = e.y
        pressed = true
        onPressed?.invoke(true)
        postDelayed({
            if (edgeLongPending && edgeDrag == side && edgeAxis == 0) {
                edgeLongPending = false
                edgeDrag = 0
                pressed = false
                onPressed?.invoke(false)
                if (side == 1) onInLongPress?.invoke() else onOutLongPress?.invoke()
            }
        }, 600)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pokeIdle()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (shiftCheck?.invoke() == true && sirenZone.contains(event.x, event.y)) {
                    sirenActive = true
                    onSirenTouch?.invoke(true, (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f), (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f))
                    return true
                }
                if (event.x < 10f * d) {
                    edgeStart(event, 1)
                    return true
                }
                if (event.x > width - 10f * d) {
                    edgeStart(event, 2)
                    return true
                }
                dragY = event.y
                downY = event.y
                downX = event.x
                downTime = System.currentTimeMillis()
                moved = false
                pressed = true
                longPressPending = true
                onPressed?.invoke(true)
                postDelayed({
                    if (longPressPending) {
                        longPressPending = false
                        onLongPress?.invoke()
                    }
                }, 600)
            }
            MotionEvent.ACTION_MOVE -> {
                if (sirenActive) {
                    onSirenTouch?.invoke(true, (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f), (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f))
                    return true
                }
                if (edgeDrag != 0) {
                    val dx = event.x - edgeLastX
                    val dy = event.y - edgeLastY
                    if (edgeAxis == 0 && (Math.abs(dx) > 10f * d || Math.abs(dy) > 10f * d)) {
                        edgeAxis = if (Math.abs(dy) > Math.abs(dx)) 1 else 2
                        edgeLongPending = false
                    }
                    if (edgeAxis == 1) {
                        val dv = -(dy / (45f * d))
                        if (edgeDrag == 1) onInVolDelta?.invoke(dv) else onOutVolDelta?.invoke(dv)
                    } else if (edgeAxis == 2) {
                        val dv = dx / (45f * d)
                        if (edgeDrag == 1) onInFilterDelta?.invoke(dv) else onOutFilterDelta?.invoke(dv)
                    }
                    edgeLastX = event.x
                    edgeLastY = event.y
                    return true
                }
                if (Math.abs(event.y - downY) > 8f * d) {
                    moved = true
                    longPressPending = false
                }
                if (moved && inputEnabled) {
                    val dy = dragY - event.y
                    dragY = event.y
                    bpm += dy * 0.4f
                    onBpmChange?.invoke(bpm)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (sirenActive) {
                    sirenActive = false
                    onSirenTouch?.invoke(false, (event.x / width.coerceAtLeast(1)).coerceIn(0f, 1f), (event.y / height.coerceAtLeast(1)).coerceIn(0f, 1f))
                    return true
                }
                if (edgeDrag != 0) {
                    val side = edgeDrag
                    val wasTap = edgeAxis == 0 && edgeLongPending
                    edgeDrag = 0
                    edgeAxis = 0
                    edgeLongPending = false
                    pressed = false
                    onPressed?.invoke(false)
                    if (wasTap) {
                        if (side == 1) onInFilterReset?.invoke() else onOutFilterReset?.invoke()
                    }
                    return true
                }
                pressed = false
                longPressPending = false
                onPressed?.invoke(false)
                val dt = System.currentTimeMillis() - downTime
                if (inputEnabled && !moved && dt < 300) {
                    if (catRect.contains(downX, downY)) {
                        miaouText = miaouWords[catRnd.nextInt(miaouWords.size)]
                        miaouOn = true
                        onCatTap?.invoke()
                        idleHandler.removeCallbacks(idleRunnable)
                        idleHandler.postDelayed({
                            miaouOn = false
                            invalidate()
                            idleHandler.postDelayed(idleRunnable, 9000)
                        }, 1800)
                        invalidate()
                        return true
                    }
                    val now = System.currentTimeMillis()
                    if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
                    taps.add(now)
                    if (taps.size >= 2) {
                        val recent = taps.takeLast(4)
                        var sum = 0L
                        for (i in 1 until recent.size) sum += recent[i] - recent[i - 1]
                        bpm = (60000.0 * (recent.size - 1) / sum).toFloat()
                        onBpmChange?.invoke(bpm)
                    }
                    if (taps.size > 8) taps.removeAt(0)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (sirenActive) {
                    sirenActive = false
                    onSirenTouch?.invoke(false, 0.5f, 0.5f)
                    return true
                }
                if (edgeDrag != 0) {
                    edgeDrag = 0
                    edgeAxis = 0
                    edgeLongPending = false
                }
                pressed = false
                onPressed?.invoke(false)
            }
        }
        return true
    }
}

class PadGridView(context: Context) : View(context) {

    val cmdId = "pads"

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    var shiftMode = false
        set(value) { field = value; invalidate() }

    var activeBank = 0

    var padCount = 8
        set(value) { field = value; invalidate() }

    var blinkOn = true
        set(value) { field = value; invalidate() }

    var scaleHint = false
        set(value) { field = value; invalidate() }

    var modeFlash = ""
    var modeFlashCol = Color.rgb(255, 190, 0)
    var modeFlashAt = 0L

    var recPad = -1
        set(value) { field = value; invalidate() }

    var recArmed = false
        set(value) { field = value; invalidate() }

    var recLive = false
        set(value) { field = value; invalidate() }

    var sampEdit = false
        set(value) { field = value; invalidate() }

    var sampMask = 255
        set(value) { field = value; invalidate() }

    var sampN = 8
        set(value) { field = value; invalidate() }

    var slotVol = FloatArray(12) { 1f }
        set(value) { field = value; invalidate() }

    var slotProb = FloatArray(12) { 1f }
        set(value) { field = value; invalidate() }

    var slotPitch = FloatArray(12) { 0.5f }
    var slotPan = FloatArray(12) { 0.5f }
    var slotToff = FloatArray(12) { 0.5f }
    var seqMode = false
    var sampRev = 0
        set(value) { field = value; invalidate() }
    var sampOffset = 0.5f

    var bankHas = BooleanArray(8)
        set(value) { field = value; invalidate() }

    var miniMixMode = false
        set(value) { field = value; invalidate() }

    var moveMode = false
        set(value) { field = value; invalidate() }

    var onSwapPads: ((Int, Int) -> Unit)? = null
    var onMergePads: ((Int, Int) -> Unit)? = null
    private var moveHoverPad = -1
    private var moveHoverAt = 0L
    private var mergeAsked = false

    var onSlotParam: ((Int, Int, Float) -> Unit)? = null

    private var slotDownX = 0f
    private var slotDownY = 0f
    private var slotDownAt = 0L
    private var slotLastX = 0f
    private var slotLastY = 0f
    private var slotDragging = false

    var soloMask = 0

    var groupMode = false
        set(value) { field = value; invalidate() }

    var groupFlashAt = 0L
        set(value) { field = value; invalidate() }

    private val xGlyphs =
        arrayOf("\u25c7", "\u25c6", "\u25b3", "\u25bd", "\u25cb", "\u25cf", "\u25a1", "\u25a0", "\u25b2", "\u25bc", "\u2726", "\u2731", "\u2206", "\u03a9", "\u2211", "\u2207")

    var padMode = 0
        set(value) { field = value; invalidate() }

    var snapSides = IntArray(14)
        set(value) { field = value; invalidate() }

    var toolGlow = IntArray(0)
        set(value) { field = value; invalidate() }

    var activeSnap = intArrayOf(-1, -1)
        set(value) { field = value; invalidate() }

    var bankTints = IntArray(0)
        set(value) { field = value; invalidate() }

    var onPadTouch: ((Int) -> Unit)? = null
    var onPadDown: ((Int) -> Unit)? = null
    var onPadUp: (() -> Unit)? = null
    var onPadLongTouch: ((Int) -> Unit)? = null

    private var flash = -1
    private var longPad = -1
    private var longRunnable: Runnable? = null

    private val zuColor = Color.rgb(0, 229, 255)
    private val luColor = Color.rgb(190, 120, 255)

    private val rows: Int get() = (padCount + 3) / 4

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = 4f * d
        val rowsF = rows.toFloat()
        val padW = (w - 5f * gap) / 4f
        val padH = (h - (rowsF + 1f) * gap) / rowsF

        for (i in 0 until padCount) {
            val col = i % 4
            val row = i / 4
            val l = gap + col * (padW + gap)
            val t = gap + row * (padH + gap)
            val soloPad = padCount >= 12 && i >= 8
            val soloOn = soloPad && (soloMask and (1 shl (i - 8))) != 0
            val lit = (shiftMode && activeBank == i + 1) || flash == i
            val rec = (shiftMode || recArmed) && recPad == i
            val side = if (i < snapSides.size) snapSides[i] else 0

            val live = recLive && recPad == i
            val base = when {
                padMode == 0 -> if (col < 2) zuColor else luColor
                padMode == 1 -> Color.rgb(255, 150, 60)
                padMode == 2 -> Color.rgb(178, 102, 255)
                padMode == 4 -> Color.rgb(80, 170, 255)
                else -> Color.rgb(90, 96, 110)
            }
            var pc = when {
                live && blinkOn -> Color.rgb(255, 90, 70)
                live -> Color.rgb(120, 40, 34)
                rec && blinkOn -> Color.rgb(255, 90, 70)
                rec -> Color.rgb(120, 40, 34)
                soloOn -> Color.rgb(0, 229, 255)
                lit -> Color.rgb(255, 244, 130)
                side == 1 -> Color.rgb(18, 58, 60)
                side == 2 -> Color.rgb(56, 38, 78)
                i < 8 && i < bankTints.size && bankHas[i] -> bankTints[i]
                else -> base
            }
            if (recArmed && !rec) pc = tint(pc, Color.rgb(255, 70, 55), 0.25f)
            if (sampEdit && i < sampN) {
                val purple = if (seqMode) Color.rgb(255, 150, 60) else Color.rgb(178, 102, 255)
                pc = if (sampMask and (1 shl i) != 0) purple else tint(pc, purple, 0.16f)
                if (sampMask and (1 shl i) != 0) {
                    val v = if (i < slotVol.size) slotVol[i] else 1f
                    val pr = if (i < slotProb.size) slotProb[i] else 1f
                    val dim = (1f - v) * 0.6f + (if (pr < 1f && !blinkOn) (1f - pr) * 0.5f else 0f)
                    pc = tint(pc, Color.rgb(16, 18, 23), dim.coerceIn(0f, 0.85f))
                }
            }
            if (groupMode) pc = Color.rgb(0, 229, 255)
            if (miniMixMode && !groupMode) pc = tint(pc, Color.rgb(70, 255, 130), 0.3f)
            if (moveMode) pc = tint(pc, Color.rgb(255, 190, 0), 0.3f)
            val tg = if (i < toolGlow.size) toolGlow[i] else 0
            if (tg == 2) pc = Color.rgb(80, 170, 255)
            else if (tg == 1) pc = tint(pc, Color.rgb(80, 170, 255), 0.22f)
            paint.fill(pc)
            val rad = if (ZuluTheme.retro) 0f else 5f * d
            canvas.drawRoundRect(l, t, l + padW, t + padH, rad, rad, paint)
            paint.stroke(ZuluTheme.edge, 1f * d)
            drawSketchyRoundRect(canvas, paint, l, t, l + padW, t + padH, rad, 100 + i)
            if (moveMode && slotDragging && i == moveHoverPad) {
                paint.stroke(Color.rgb(255, 190, 0), 2f * d)
                canvas.drawRoundRect(l, t, l + padW, t + padH, rad, rad, paint)
            }
            if (sampEdit && i < sampN && sampMask and (1 shl i) != 0 && sampRev and (1 shl i) != 0) {
                paint.fill(Color.rgb(16, 18, 23))
                val mw = 5f * d
                val mx = l + padW - mw - 3f * d
                val my = t + 3f * d
                val path = android.graphics.Path()
                path.moveTo(mx + mw, my)
                path.lineTo(mx, my + mw / 2f)
                path.lineTo(mx + mw, my + mw)
                path.close()
                canvas.drawPath(path, paint)
            }

            if (side != 0 && !soloPad && !shiftMode) {
                val isActive = (side == 1 && activeSnap[0] == i) ||
                    (side == 2 && activeSnap[1] == i)
                paint.stroke(if (side == 1) zuColor else luColor, if (isActive) 2f * d else 1f * d)
                paint.alpha = if (isActive) 255 else 130
                canvas.drawRoundRect(l, t, l + padW, t + padH, rad, rad, paint)
                paint.alpha = 255
                if (isActive) {
                    paint.fill(if (side == 1) zuColor else luColor)
                    canvas.drawCircle(l + padW / 2f, t + padH / 2f, 3f * d, paint)
                }
            }

            if (shiftMode) {
                paint.fill(if (lit) ink else silk)
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = marker
                paint.textSize = padH * 0.5f
                canvas.drawText(if (ZuluTheme.xFiles) xGlyphs[i % xGlyphs.size] else "${i + 1}", l + padW / 2f, t + padH * 0.7f, paint)
            } else if (soloPad) {
                paint.fill(if (soloOn) ink else silk)
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = marker
                paint.textSize = padH * 0.42f
                canvas.drawText(if (ZuluTheme.xFiles) xGlyphs[i % xGlyphs.size] else "${i - 7}", l + padW / 2f, t + padH * 0.68f, paint)
            } else {
                paint.fill(if (lit) ink else Color.rgb(40, 26, 12))
                canvas.drawCircle(l + padW / 2f, t + padH / 2f, 2.5f * d, paint)
            }
        }

        val modeCol = when {
            padMode == 2 -> Color.rgb(70, 255, 130)
            padMode == 1 -> Color.rgb(0, 240, 255)
            else -> Color.rgb(255, 190, 0)
        }
        val modeName = when {
            padMode == 2 -> "SAMPLES"
            padMode == 1 -> "MOTION"
            else -> "SC\u00c8NE"
        }
        paint.stroke(modeCol, 1.5f * d)
        paint.alpha = 150
        canvas.drawRoundRect(1f * d, 1f * d, w - 1f * d, h - 1f * d, 7f * d, 7f * d, paint)
        paint.alpha = 255

        if (scaleHint) {
            paint.fill(Color.rgb(16, 18, 23))
            paint.alpha = 200
            canvas.drawCircle(w - 15f * d, h - 15f * d, 11f * d, paint)
            paint.alpha = 255
            paint.color = Color.rgb(255, 190, 0)
            paint.textSize = 8f * d
            paint.typeface = marker
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("SCL", w - 15f * d, h - 12.5f * d, paint)
        }

        if (modeFlashAt > 0) {
            val age = System.currentTimeMillis() - modeFlashAt
            if (age < 900) {
                paint.fill(Color.rgb(10, 11, 15))
                paint.alpha = (210 * (1f - age / 900f)).toInt().coerceIn(0, 210)
                canvas.drawRoundRect(2f * d, 2f * d, w - 2f * d, h - 2f * d, 7f * d, 7f * d, paint)
                paint.alpha = 255
                paint.color = modeFlashCol
                paint.textSize = h * 0.3f
                paint.typeface = marker
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(modeFlash, w / 2f, h * 0.58f, paint)
            } else {
                modeFlashAt = 0
            }
        }

        if (groupMode) {
            paint.fill(Color.rgb(16, 18, 23))
            paint.alpha = 200
            canvas.drawCircle(w - 26f * d, 16f * d, 13f * d, paint)
            paint.alpha = 255
            paint.color = Color.rgb(0, 229, 255)
            paint.textSize = 9f * d
            paint.typeface = marker
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("GRP", w - 26f * d, 19f * d, paint)
            paint.textAlign = Paint.Align.LEFT
            val age = System.currentTimeMillis() - groupFlashAt
            if (groupFlashAt > 0 && age < 1200) {
                paint.alpha = (255 * (1f - age / 1200f)).toInt().coerceIn(0, 255)
                paint.color = Color.rgb(0, 229, 255)
                paint.textSize = 16f * d
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("MODE GROUPE", w / 2f, h / 2f, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.alpha = 255
                postInvalidateOnAnimation()
            }
        }

        if (sampEdit) {
            val mc = if (seqMode) Color.rgb(255, 150, 60) else Color.rgb(178, 102, 255)
            val sym = if (seqMode) "\u25b6 SEQ" else "\u221e LOOP"
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(16, 18, 23)
            paint.alpha = 190
            canvas.drawCircle(w - 26f * d, 16f * d, 13f * d, paint)
            paint.alpha = 255
            paint.color = mc
            paint.textSize = 9f * d
            paint.typeface = marker
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(sym, w - 26f * d, 19f * d, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        drawScrews(canvas, paint, 0f, 0f, w, h, d)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val ai = event.actionIndex
                val i = padAt(event.getX(ai), event.getY(ai))
                if (i >= 0) {
                    flash = i
                    onPadDown?.invoke(i + 1)
                    onPadTouch?.invoke(i + 1)
                    longPad = i
                    longRunnable = Runnable {
                        if (longPad == i) onPadLongTouch?.invoke(i + 1)
                    }.also { postDelayed(it, 600) }
                }
                slotDownX = event.getX(ai)
                slotDownY = event.getY(ai)
                slotLastX = slotDownX
                slotLastY = slotDownY
                slotDownAt = System.currentTimeMillis()
                slotDragging = false
                moveHoverPad = -1
                moveHoverAt = 0L
                mergeAsked = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (moveMode && !sampEdit && longPad in 0 until 8 && bankHas[longPad]) {
                    val mx = event.x
                    val my = event.y
                    if (!slotDragging && System.currentTimeMillis() - slotDownAt > 250 &&
                        (Math.abs(mx - slotDownX) > 8f * d || Math.abs(my - slotDownY) > 8f * d)) {
                        slotDragging = true
                        longRunnable?.let { removeCallbacks(it) }
                        longRunnable = null
                    }
                    if (slotDragging) {
                        val t = padAt(mx, my)
                        if (t != moveHoverPad) {
                            moveHoverPad = t
                            moveHoverAt = System.currentTimeMillis()
                            mergeAsked = false
                        }
                        if (!mergeAsked && moveHoverPad in 0 until 8 && moveHoverPad != longPad &&
                            bankHas[moveHoverPad] && moveHoverAt > 0L &&
                            System.currentTimeMillis() - moveHoverAt > 600) {
                            mergeAsked = true
                            onMergePads?.invoke(longPad, moveHoverPad)
                        }
                    }
                }
                if (sampEdit && longPad in 0 until sampN) {
                    val x = event.x
                    val y = event.y
                    if (!slotDragging && System.currentTimeMillis() - slotDownAt > 350 &&
                        (Math.abs(x - slotDownX) > 8f * d || Math.abs(y - slotDownY) > 8f * d)) {
                        slotDragging = true
                        longRunnable?.let { removeCallbacks(it) }
                        longRunnable = null
                    }
                    if (slotDragging) {
                        val s = longPad
                        val dy = (y - slotLastY) / (height * 0.8f)
                        val dx = (x - slotLastX) / (width * 0.8f)
                        when {
                            shiftMode && seqMode -> {
                                slotPitch[s] = (slotPitch[s] - dy).coerceIn(0f, 1f)
                                slotPan[s] = (slotPan[s] + dx).coerceIn(0f, 1f)
                                onSlotParam?.invoke(s, 2, slotPitch[s])
                                onSlotParam?.invoke(s, 3, slotPan[s])
                            }
                            shiftMode -> {
                                slotProb[s] = (slotProb[s] + dx).coerceIn(0f, 1f)
                                onSlotParam?.invoke(s, 1, slotProb[s])
                            }
                            else -> {
                                slotVol[s] = (slotVol[s] - dy).coerceIn(0f, 1f)
                                if (seqMode) {
                                    slotToff[s] = (slotToff[s] + dx).coerceIn(0f, 1f)
                                    onSlotParam?.invoke(s, 0, slotVol[s])
                                    onSlotParam?.invoke(s, 4, slotToff[s])
                                } else {
                                    sampOffset = (sampOffset + dx).coerceIn(0f, 1f)
                                    onSlotParam?.invoke(s, 0, slotVol[s])
                                    onSlotParam?.invoke(s, 5, sampOffset)
                                }
                            }
                        }
                    }
                    slotLastX = x
                    slotLastY = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (moveMode && slotDragging && !mergeAsked &&
                    moveHoverPad in 0 until 8 && moveHoverPad != longPad && longPad in 0 until 8) {
                    onSwapPads?.invoke(longPad, moveHoverPad)
                }
                flash = -1
                onPadUp?.invoke()
                longRunnable?.let { removeCallbacks(it) }
                longRunnable = null
                longPad = -1
                slotDragging = false
                moveHoverPad = -1
                moveHoverAt = 0L
                mergeAsked = false
            }
        }
        invalidate()
        return true
    }

    private fun padAt(x: Float, y: Float): Int {
        val w = width.toFloat()
        val gap = 4f * d
        val rowsF = rows.toFloat()
        val padW = (w - 5f * gap) / 4f
        val padH = (height - (rowsF + 1f) * gap) / rowsF
        val col = ((x - gap) / (padW + gap)).toInt()
        val row = ((y - gap) / (padH + gap)).toInt()
        if (col !in 0..3 || row !in 0 until rows) return -1
        val i = row * 4 + col
        return if (i in 0 until padCount) i else -1
    }
}

class FlyFaderView(context: Context) : View(context) {

    val cmdId = "fly"

    val stutterAmount: Float
        get() = ((value - 0.75f) / 0.25f).coerceIn(0f, 1f)

    val stutter: Boolean
        get() = stutterAmount > 0f

    var onValueChange: ((Float, Boolean) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    var value = 0f
        private set

    fun setValue(v: Float) {
        value = v.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val m = 8f * d

        paint.fill(ZuluTheme.slot)
        canvas.drawRoundRect(0f, 0f, w, h, 8f * d, 8f * d, paint)
        drawRelief(canvas, 0f, 0f, w, h, d)
        paint.stroke(ZuluTheme.edge, 1f * d)
        drawSketchyRoundRect(canvas, paint, 0.8f * d, 0.8f * d, w - 0.8f * d, h - 0.8f * d, 8f * d, 71)

        val cy = h / 2f
        paint.stroke(Color.rgb(18, 20, 25), 5f * d)
        canvas.drawLine(m, cy, w - m, cy, paint)
        paint.stroke(Color.rgb(60, 66, 80), 1f * d)
        canvas.drawLine(m, cy, w - m, cy, paint)

        val zoneStart = m + (w - 2f * m) * 0.75f
        paint.stroke(if (stutter) amber else Color.rgb(52, 58, 72), 1f * d)
        var hx = zoneStart
        while (hx < w - m - 2f) {
            canvas.drawLine(hx, cy - 6f * d, hx - 3f * d, cy + 6f * d, paint)
            hx += 4f * d
        }

        val kx = m + value * (w - 2f * m)
        paint.fill(amber)
        canvas.drawRoundRect(kx - 11f * d, cy - 17f * d, kx + 11f * d, cy + 17f * d, 5f * d, 5f * d, paint)
        paint.fill(Color.WHITE)
        paint.alpha = 55
        canvas.drawRect(kx - 10f * d, cy - 16f * d, kx + 10f * d, cy, paint)
        paint.fill(Color.BLACK)
        paint.alpha = 60
        canvas.drawRect(kx - 10f * d, cy, kx + 10f * d, cy + 16f * d, paint)
        paint.alpha = 255
        paint.stroke(if (stutter) Color.WHITE else silk, 1.2f * d)
        paint.alpha = if (stutter) 220 else 90
        canvas.drawRoundRect(kx - 11f * d, cy - 17f * d, kx + 11f * d, cy + 17f * d, 5f * d, 5f * d, paint)
        paint.alpha = 255
        paint.stroke(Color.rgb(40, 26, 8), 1.5f * d)
        canvas.drawLine(kx, cy - 12f * d, kx, cy + 12f * d, paint)

        paint.fill(amber)
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = marker
        paint.textSize = 9f * d
        canvas.drawText("FLY", m + 2f * d, m + 9f * d, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {
                val w = width.toFloat()
                val m = 8f * d
                value = ((event.x - m) / (w - 2f * m)).coerceIn(0f, 1f)
                onValueChange?.invoke(value, stutter)
            }
        }
        invalidate()
        return true
    }
}

class GlitchScopeView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)
    private val rnd = java.util.Random()
    private var phase = 0f
    private var running = false

    fun start() {
        running = true
        scaleX = 0.7f
        scaleY = 0.7f
        animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        postInvalidateOnAnimation()
    }

    fun stop() {
        running = false
        visibility = GONE
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        paint.fill(Color.rgb(12, 13, 16))
        canvas.drawRoundRect(0f, 0f, w, h, 4f * d, 4f * d, paint)
        paint.stroke(ZuluTheme.edge, 1f * d)
        drawSketchyRoundRect(canvas, paint, 0.8f * d, 0.8f * d, w - 0.8f * d, h - 0.8f * d, 4f * d, 17)

        phase += 0.11f
        paint.stroke(amber, 1.2f * d)
        val n = 36
        var px = 6f * d
        var py = h / 2f
        for (i in 1..n) {
            val x = 6f * d + (w - 12f * d) * i / n
            val y = h / 2f + sin(phase + i * 0.9f) * h * 0.22f +
                (if (rnd.nextInt(12) == 0) (rnd.nextFloat() - 0.5f) * h * 0.6f else 0f)
            canvas.drawLine(px, py, x, y, paint)
            px = x
            py = y
        }

        paint.fill(silk)
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = marker
        paint.textSize = 7f * d
        canvas.drawText("BEND", w - 5f * d, h - 4f * d, paint)

        if (running) postInvalidateOnAnimation()
    }
}

class MacroPanelView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    val faderVals = FloatArray(6) { 0.5f }
    var hFaderVal = 0.5f
    val buttonOn = BooleanArray(4)
    val faderTargetLbl = arrayOfNulls<String>(6)

    var onFaderChange: ((Int, Float) -> Unit)? = null
    var onHFaderChange: ((Float) -> Unit)? = null
    var onButtonTap: ((Int, Boolean) -> Unit)? = null
    var onBendChange: ((Int, Float) -> Unit)? = null
    var shiftCheck: (() -> Boolean)? = null

    var bendActive = false
        set(value) {
            field = value
            if (value) {
                System.arraycopy(faderVals, 0, baseVals, 0, 6)
                bendMode = if (bendModeSelect >= 0) bendModeSelect else rnd.nextInt(5)
                bendP1 = 0.2f + rnd.nextFloat() * 0.8f
                bendP2 = 0.2f + rnd.nextFloat() * 0.8f
                bendClock = 0
                java.util.Arrays.fill(bendWalk, 0f)
            }
            invalidate()
        }

    var bendModeSelect = -1
    private var bendMode = 0
    private var bendP1 = 0.5f
    private var bendP2 = 0.5f
    private val baseVals = FloatArray(6) { 0.5f }
    private val bendWalk = FloatArray(6)
    private val rnd = java.util.Random()
    private var bendClock = 0
    private var bendSpin = 0f

    private var touchMode = 0
    private var touchIdx = -1

    private val fluo = intArrayOf(
        Color.rgb(57, 255, 20), Color.rgb(0, 255, 200), Color.rgb(255, 0, 110),
        Color.rgb(204, 255, 0), Color.rgb(120, 80, 255), Color.rgb(255, 130, 0)
    )

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.fill(ZuluTheme.slot)
        canvas.drawRoundRect(0f, 0f, w, h, 8f * d, 8f * d, paint)
        drawRelief(canvas, 0f, 0f, w, h, d)
        paint.stroke(ZuluTheme.edge, 1f * d)
        drawSketchyRoundRect(canvas, paint, 0.8f * d, 0.8f * d, w - 0.8f * d, h - 0.8f * d, 8f * d, 73)

        if (bendActive) {
            bendClock++
            val amt = hFaderVal
            when (bendMode) {
                0 -> if (bendClock > 4 + (bendP2 * 10f).toInt() + rnd.nextInt(6)) {
                    bendClock = 0
                    bendWalk[rnd.nextInt(6)] = (rnd.nextFloat() - 0.5f) * 1.6f * bendP1
                }
                2 -> if (bendClock > 6 + (bendP2 * 30f).toInt() + rnd.nextInt(14)) {
                    bendClock = 0
                    bendWalk[rnd.nextInt(6)] = (rnd.nextFloat() - 0.5f) * 1.8f * bendP1
                }
                3 -> if (bendClock > 2 + (bendP2 * 6f).toInt()) {
                    bendClock = 0
                    for (i in 0 until 6) bendWalk[i] = (rnd.nextFloat() - 0.5f) * 0.3f * bendP1
                }
                4 -> if (bendClock > (30f + bendP2 * 90f).toInt() + rnd.nextInt(30)) {
                    bendClock = 0
                    for (i in 0 until 6) bendWalk[i] = (rnd.nextFloat() - 0.5f) * 1.9f * bendP1
                }
            }
            val ph = bendClock * (0.02f + 0.1f * bendP2)
            for (i in 0 until 6) {
                val nv = when (bendMode) {
                    0 -> {
                        bendWalk[i] += (rnd.nextFloat() - 0.5f) * 0.2f * bendP1
                        bendWalk[i] = bendWalk[i].coerceIn(-0.95f, 0.95f)
                        baseVals[i] + bendWalk[i]
                    }
                    1 -> baseVals[i] + sin(ph + i * 1.05f).toFloat() * 0.7f * bendP1 +
                        sin(ph * 2.71f + i * 2.3f + bendP2 * 6f).toFloat() * 0.15f * bendP1
                    else -> baseVals[i] + bendWalk[i]
                }
                faderVals[i] = (baseVals[i] + (nv - baseVals[i]) * amt).coerceIn(0f, 1f)
                onBendChange?.invoke(i, faderVals[i])
            }
        }

        val hFaderH = 30f * d
        val btnW = 26f * d
        val gap = 5f * d
        val vfAreaR = w - btnW - 3f * gap
        val vfTop = 6f * d
        val vfBottom = h - hFaderH - 3f * gap
        val fw = (vfAreaR - 4f * gap) / 6f

        for (i in 0 until 6) {
            val cx = 2f * gap + i * fw + fw / 2f
            paint.stroke(Color.rgb(18, 20, 25), 5f * d)
            canvas.drawLine(cx, vfTop + 12f * d, cx, vfBottom, paint)
            paint.stroke(Color.rgb(60, 66, 80), 1f * d)
            canvas.drawLine(cx, vfTop + 12f * d, cx, vfBottom, paint)
            val ty = vfBottom - faderVals[i] * (vfBottom - vfTop - 12f * d)
            paint.fill(fluo[i])
            canvas.drawRoundRect(cx - fw * 0.32f, ty - 3f * d, cx + fw * 0.32f, ty + 3f * d, 3f * d, 3f * d, paint)
            paint.fill(silk)
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = marker
            paint.textSize = 8f * d
            canvas.drawText("M${i + 1}", cx, vfTop + 9f * d, paint)
            faderTargetLbl[i]?.let { tl ->
                paint.fill(fluo[i])
                paint.textSize = 6.5f * d
                val trimmed = if (tl.length > 9) tl.take(9) else tl
                canvas.drawText(trimmed, cx, vfBottom - 1f * d, paint)
            }
        }

        val hy = h - hFaderH
        val hm = 8f * d
        paint.stroke(Color.rgb(18, 20, 25), 5f * d)
        canvas.drawLine(hm, hy + hFaderH / 2f, w - hm, hy + hFaderH / 2f, paint)
        paint.stroke(Color.rgb(60, 66, 80), 1f * d)
        canvas.drawLine(hm, hy + hFaderH / 2f, w - hm, hy + hFaderH / 2f, paint)
        val kx = hm + hFaderVal * (w - 2f * hm)
        paint.fill(Color.rgb(0, 240, 215))
        canvas.drawRoundRect(kx - 11f * d, hy + 3f * d, kx + 11f * d, hy + hFaderH - 3f * d, 5f * d, 5f * d, paint)
        paint.fill(silk)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 8f * d
        canvas.drawText("MACRO", hm + 2f * d, hy + 9f * d, paint)

        val bh = (h - 5f * gap) / 4f
        for (i in 0 until 4) {
            val bt = 2f * gap + i * bh
            val bb = bt + bh - 3f * d
            paint.fill(if (buttonOn[i]) fluo[(i + 2) % 6] else Color.rgb(24, 27, 33))
            canvas.drawRoundRect(vfAreaR + gap, bt, w - 4f * d, bb, 4f * d, 4f * d, paint)
            paint.fill(if (buttonOn[i]) Color.rgb(12, 13, 16) else silk)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 9f * d
            canvas.drawText("${i + 1}", (vfAreaR + gap + w - 4f * d) / 2f, (bt + bb) / 2f + 3f * d, paint)
        }

        if (bendActive) {
            val bands = 2 + rnd.nextInt(3)
            for (b in 0 until bands) {
                val by = rnd.nextFloat() * h
                val bh = (3f + rnd.nextFloat() * 18f) * d
                val off = (rnd.nextFloat() - 0.5f) * 22f * d
                paint.fill(Color.argb(60, 255, 255, 255))
                canvas.drawRect(0f, by, w, by + bh, paint)
                paint.fill(Color.argb(90, 255, 60, 60))
                canvas.drawRect(off, by, w, by + bh * 0.4f, paint)
                paint.fill(Color.argb(90, 0, 240, 255))
                canvas.drawRect(-off, by + bh * 0.6f, w, by + bh, paint)
            }
            val hm2 = 8f * d
            val kx2 = hm2 + hFaderVal * (w - 2f * hm2)
            val ky2 = h - 15f * d
            val r2 = 11f * d
            bendSpin = (bendSpin + 8f) % 360f
            paint.fill(Color.WHITE)
            paint.alpha = 80
            canvas.drawCircle(kx2, ky2, 22f * d, paint)
            paint.alpha = 255
            val cols = intArrayOf(
                Color.rgb(255, 60, 60), Color.rgb(255, 220, 0),
                Color.rgb(60, 255, 120), Color.rgb(0, 240, 255),
                Color.rgb(80, 120, 255), Color.rgb(255, 80, 220),
                Color.rgb(255, 60, 60)
            )
            paint.shader = SweepGradient(kx2, ky2, cols, null)
            canvas.save()
            canvas.rotate(bendSpin, kx2, ky2)
            canvas.drawCircle(kx2, ky2, r2, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f * d
            paint.color = Color.rgb(12, 13, 18)
            paint.alpha = 230
            for (arm in 0 until 3) {
                val spiral = android.graphics.Path()
                val segs = 18
                for (s in 0..segs) {
                    val t = s / segs.toFloat()
                    val ang = arm * 2.0944f - t * 5.2f
                    val rr = r2 * (0.08f + t * 0.95f)
                    val px = kx2 + rr * cos(ang.toDouble()).toFloat()
                    val py = ky2 + rr * sin(ang.toDouble()).toFloat()
                    if (s == 0) spiral.moveTo(px, py) else spiral.lineTo(px, py)
                }
                canvas.drawPath(spiral, paint)
            }
            canvas.restore()
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * d
            paint.color = Color.WHITE
            paint.alpha = 235
            canvas.drawCircle(kx2, ky2, r2, paint)
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        val hFaderH = 30f * d
        val btnW = 26f * d
        val gap = 5f * d
        val vfAreaR = w - btnW - 3f * gap
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.x
                val y = event.y
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchMode = 0
                    touchIdx = -1
                    if (bendActive) {
                        touchMode = 2
                    } else if (y > h - hFaderH) {
                        if (shiftCheck?.invoke() == true) bendActive = true
                        touchMode = 2
                    }
                    else if (x > vfAreaR) {
                        touchMode = 3
                        val bh = (h - 5f * gap) / 4f
                        touchIdx = (((y - 2f * gap) / bh).toInt()).coerceIn(0, 3)
                    } else {
                        touchMode = 1
                        val fw = (vfAreaR - 4f * gap) / 6f
                        touchIdx = (((x - 2f * gap) / fw).toInt()).coerceIn(0, 5)
                    }
                }
                when (touchMode) {
                    1 -> {
                        val fw = (vfAreaR - 4f * gap) / 6f
                        val cx = 2f * gap + touchIdx * fw + fw / 2f
                        val vfTop = 6f * d
                        val vfBottom = h - hFaderH - 3f * gap
                        faderVals[touchIdx] =
                            ((vfBottom - y) / (vfBottom - vfTop - 12f * d)).coerceIn(0f, 1f)
                        onFaderChange?.invoke(touchIdx, faderVals[touchIdx])
                    }
                    2 -> {
                        val hm = 8f * d
                        hFaderVal = ((x - hm) / (w - 2f * hm)).coerceIn(0f, 1f)
                        onHFaderChange?.invoke(hFaderVal)
                    }
                    3 -> {
                        buttonOn[touchIdx] = !buttonOn[touchIdx]
                        onButtonTap?.invoke(touchIdx, buttonOn[touchIdx])
                        touchMode = 0
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (bendActive) {
                    bendActive = false
                    for (i in 0 until 6) {
                        bendWalk[i] = 0f
                        faderVals[i] = baseVals[i]
                        onBendChange?.invoke(i, baseVals[i])
                    }
                    invalidate()
                }
            }
        }
        return true
    }
}

class VFaderView(
    context: Context,
    private val label: String,
    private val marks: List<Pair<Float, String>> = emptyList(),
    private val thumbColor: Int = Color.rgb(165, 172, 185),
    private val blendMorph: Boolean = false
) : View(context) {

    private val turquoise = Color.rgb(0, 240, 215)
    private val violet = Color.rgb(200, 90, 255)

    private fun currentThumbColor(): Int {
        if (!blendMorph) return thumbColor
        val t = value.coerceIn(0f, 1f)
        fun lerp(a: Int, b: Int) = (a + (b - a) * t).toInt()
        return Color.rgb(
            lerp(Color.red(turquoise), Color.red(violet)),
            lerp(Color.green(turquoise), Color.green(violet)),
            lerp(Color.blue(turquoise), Color.blue(violet))
        )
    }

    val cmdId = "vfader_$label"

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    var onValueChange: ((Float) -> Unit)? = null
    var shiftCheck: (() -> Boolean)? = null
    var bendModeSelect = -1

    var bendActive = false
        set(v) {
            field = v
            if (v) {
                bendBase = value
                bendMode = if (bendModeSelect >= 0) bendModeSelect else rnd.nextInt(5)
                bendP1 = 0.2f + rnd.nextFloat() * 0.8f
                bendP2 = 0.2f + rnd.nextFloat() * 0.8f
                bendWalk = 0f
                bendClock = 0
            }
            if (!v) ghostPos.clear()
            invalidate()
        }

    private var bendBase = 0.5f
    private var bendWalk = 0f
    private var bendClock = 0
    private var bendMode = 0
    private var bendP1 = 0.5f
    private var bendP2 = 0.5f
    private val rnd = java.util.Random()
    private val ghostPos = ArrayList<Float>()

    var value = 0.5f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val m = 8f * d

        paint.fill(ZuluTheme.slot)
        canvas.drawRoundRect(0f, 0f, w, h, 8f * d, 8f * d, paint)
        drawRelief(canvas, 0f, 0f, w, h, d)
        paint.stroke(ZuluTheme.edge, 1f * d)
        drawSketchyRoundRect(canvas, paint, 0.8f * d, 0.8f * d, w - 0.8f * d, h - 0.8f * d, 8f * d, 73)

        if (bendActive) {
            bendClock++
            val nv = when (bendMode) {
                0 -> {
                    if (bendClock > 4 + (bendP2 * 12f).toInt() + rnd.nextInt(8)) {
                        bendClock = 0
                        bendWalk = (rnd.nextFloat() - 0.5f) * 1.6f * bendP1
                    }
                    bendWalk += (rnd.nextFloat() - 0.5f) * 0.2f * bendP1
                    bendWalk = bendWalk.coerceIn(-0.95f, 0.95f)
                    bendBase + bendWalk
                }
                1 -> {
                    val ph = bendClock * (0.02f + 0.1f * bendP2)
                    bendBase + sin(ph.toDouble()).toFloat() * 0.7f * bendP1 +
                        sin(ph * 2.71 + bendP2 * 6f).toFloat() * 0.15f * bendP1
                }
                2 -> {
                    if (bendClock > 6 + (bendP2 * 30f).toInt() + rnd.nextInt(14)) {
                        bendClock = 0
                        bendWalk = (rnd.nextFloat() - 0.5f) * 1.8f * bendP1
                    }
                    bendBase + bendWalk
                }
                3 -> {
                    if (bendClock > 2 + (bendP2 * 6f).toInt()) {
                        bendClock = 0
                        bendWalk = (rnd.nextFloat() - 0.5f) * 0.3f * bendP1
                    }
                    bendBase + bendWalk
                }
                else -> {
                    if (bendClock > (30f + bendP2 * 90f).toInt() + rnd.nextInt(30)) {
                        bendClock = 0
                        bendWalk = (rnd.nextFloat() - 0.5f) * 1.9f * bendP1
                    }
                    bendBase + bendWalk
                }
            }
            value = nv.coerceIn(0f, 1f)
            ghostPos.add(value)
            if (ghostPos.size > 7) ghostPos.removeAt(0)
            onValueChange?.invoke(value)
            postInvalidateOnAnimation()
        }

        val cy = h / 2f
        paint.stroke(Color.rgb(18, 20, 25), 5f * d)
        canvas.drawLine(m, cy, w - m, cy, paint)
        paint.stroke(Color.rgb(60, 66, 80), 1f * d)
        canvas.drawLine(m, cy, w - m, cy, paint)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = marker
        paint.textSize = 8f * d
        for ((p, txt) in marks) {
            val mx = m + p * (w - 2f * m)
            paint.stroke(Color.rgb(90, 97, 114), 1f * d)
            canvas.drawLine(mx, cy + 4f * d, mx, cy + 9f * d, paint)
            paint.fill(Color.rgb(120, 128, 144))
            canvas.drawText(txt, mx, cy + 20f * d, paint)
        }

        val kx = m + value * (w - 2f * m)
        val th = min(h * 0.85f, 56f * d)
        if (bendActive) {
            for (g in ghostPos.indices) {
                val gp = ghostPos[g]
                val gx = m + gp * (w - 2f * m)
                val ga = 30 + g * 26
                paint.fill(turquoise)
                paint.alpha = ga
                canvas.drawRoundRect(gx - 16f * d, cy - th / 2f + 3f * d, gx - 4f * d, cy + th / 2f - 3f * d, 4f * d, 4f * d, paint)
                paint.fill(violet)
                canvas.drawRoundRect(gx + 4f * d, cy - th / 2f + 3f * d, gx + 16f * d, cy + th / 2f - 3f * d, 4f * d, 4f * d, paint)
            }
            paint.alpha = 255
        }
        val tc = if (bendActive && rnd.nextInt(3) == 0) Color.WHITE else currentThumbColor()
        paint.fill(tc)
        canvas.drawRoundRect(kx - 11f * d, cy - th / 2f, kx + 11f * d, cy + th / 2f, 5f * d, 5f * d, paint)
        paint.fill(Color.WHITE)
        paint.alpha = 55
        canvas.drawRect(kx - 10f * d, cy - th / 2f + 1f, kx + 10f * d, cy, paint)
        paint.fill(Color.BLACK)
        paint.alpha = 60
        canvas.drawRect(kx - 10f * d, cy, kx + 10f * d, cy + th / 2f - 1f, paint)
        paint.alpha = 255
        paint.stroke(Color.WHITE, 1.2f * d)
        paint.alpha = 110
        canvas.drawRoundRect(kx - 11f * d, cy - th / 2f, kx + 11f * d, cy + th / 2f, 5f * d, 5f * d, paint)
        paint.alpha = 255
        paint.stroke(Color.rgb(20, 22, 28), 1.5f * d)
        canvas.drawLine(kx, cy - th / 2f + 4f * d, kx, cy + th / 2f - 4f * d, paint)

        paint.fill(silk)
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = marker
        paint.textSize = 9f * d
        canvas.drawText(label, m + 2f * d, m + 10f * d, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (shiftCheck?.invoke() == true) {
                    bendActive = true
                }
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {}
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (bendActive) {
                    bendActive = false
                    value = bendBase
                    onValueChange?.invoke(bendBase)
                }
            }
        }
        if (!bendActive) {
            val w = width.toFloat()
            val m = 8f * d
            value = ((event.x - m) / (w - 2f * m)).coerceIn(0f, 1f)
            onValueChange?.invoke(value)
        } else {
            val w = width.toFloat()
            val m = 8f * d
            bendBase = ((event.x - m) / (w - 2f * m)).coerceIn(0f, 1f)
        }
        invalidate()
        return true
    }
}

class StepEditorView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    var bank = 0
    var stepCount = 8
    var pitch = FloatArray(14)
    var vel = FloatArray(14) { 1f }

    var onStepChange: ((Int, Float, Float) -> Unit)? = null

    private val pitchMax = 12f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val m = 10f * d
        val n = stepCount.coerceIn(1, 14)
        val colW = (w - 2f * m) / n
        val split = m + (h - 2f * m) * 0.58f

        paint.fill(Color.rgb(16, 18, 24))
        canvas.drawRoundRect(0f, 0f, w, h, 6f * d, 6f * d, paint)
        paint.stroke(ZuluTheme.edge, 1f * d)
        drawSketchyRoundRect(canvas, paint, 0.8f * d, 0.8f * d, w - 0.8f * d, h - 0.8f * d, 6f * d, 23)

        paint.textSize = 8f * d
        paint.typeface = marker
        paint.textAlign = Paint.Align.LEFT
        paint.fill(Color.rgb(120, 128, 144))
        canvas.drawText("PITCH", m, m + 8f * d, paint)
        canvas.drawText("VEL", m, split + 10f * d, paint)

        for (s in 0 until n) {
            val l = m + s * colW

            val pt = m + 12f * d
            val pb = split - 4f * d
            paint.fill(Color.rgb(26, 30, 38))
            canvas.drawRect(l + 1f, pt, l + colW - 2f, pb, paint)
            val cy = (pt + pb) / 2f
            paint.stroke(Color.rgb(60, 66, 80), 1f * d)
            canvas.drawLine(l + 1f, cy, l + colW - 2f, cy, paint)
            val p = pitch[s].coerceIn(-pitchMax, pitchMax)
            val half = (pb - pt) / 2f
            val barY = cy - (p / pitchMax) * half
            paint.fill(Color.rgb(0, 229, 255))
            if (p >= 0) canvas.drawRect(l + 2f, barY, l + colW - 3f, cy, paint)
            else canvas.drawRect(l + 2f, cy, l + colW - 3f, barY, paint)
            paint.fill(Color.rgb(236, 224, 196))
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                if (p > 0.5f) "+${p.toInt()}" else "${p.toInt()}",
                l + colW / 2f, pt + 9f * d, paint
            )

            val vt = split + 14f * d
            val vb = h - m
            paint.fill(Color.rgb(26, 30, 38))
            canvas.drawRect(l + 1f, vt, l + colW - 2f, vb, paint)
            val v = vel[s].coerceIn(0f, 1f)
            val vy = vb - v * (vb - vt)
            paint.fill(Color.rgb(255, 176, 0))
            canvas.drawRect(l + 2f, vy, l + colW - 3f, vb, paint)
            paint.fill(Color.rgb(120, 128, 144))
            canvas.drawText("${(v * 99).toInt()}", l + colW / 2f, vt + 9f * d, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {
                val w = width.toFloat()
                val h = height.toFloat()
                val m = 10f * d
                val n = stepCount.coerceIn(1, 14)
                val colW = (w - 2f * m) / n
                val split = m + (h - 2f * m) * 0.58f
                val s = (((event.x - m) / colW).toInt()).coerceIn(0, n - 1)
                if (event.y < split) {
                    val pt = m + 12f * d
                    val pb = split - 4f * d
                    val cy = (pt + pb) / 2f
                    val half = (pb - pt) / 2f
                    val p = if (half > 0f) ((cy - event.y) / half * pitchMax) else 0f
                    pitch[s] = p.coerceIn(-pitchMax, pitchMax).toInt().toFloat()
                    onStepChange?.invoke(s, pitch[s], vel[s])
                } else {
                    val vt = split + 14f * d
                    val vb = h - m
                    val v = if (vb > vt) 1f - (event.y - vt) / (vb - vt) else 1f
                    vel[s] = v.coerceIn(0f, 1f)
                    onStepChange?.invoke(s, pitch[s], vel[s])
                }
            }
        }
        invalidate()
        return true
    }
}

class LfoDrawView(context: Context) : View(context) {

    var onShapeChange: ((FloatArray) -> Unit)? = null
    private val shape = FloatArray(64) { 0.5f }
    private var lastBin = -1
    private var lastVal = 0.5f
    private val d = resources.displayMetrics.density

    private val pBg = Paint().apply { color = Color.rgb(12, 14, 18) }
    private val pGrid = Paint().apply {
        color = Color.argb(40, 110, 118, 134)
        strokeWidth = 1f * d
    }
    private val pMid = Paint().apply {
        color = Color.rgb(75, 82, 98)
        strokeWidth = 1f * d
    }
    private val pLine = Paint().apply {
        color = Color.rgb(255, 176, 64)
        strokeWidth = 2.5f * d
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val pPlay = Paint().apply {
        color = Color.argb(120, 236, 224, 196)
        strokeWidth = 1.5f * d
    }

    fun setShape(s: FloatArray) {
        for (i in 0 until 64) shape[i] = s[i].coerceIn(0f, 1f)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val x = (e.x / width).coerceIn(0f, 1f)
                val v = 1f - (e.y / height).coerceIn(0f, 1f)
                val bin = (x * 63f).toInt().coerceIn(0, 63)
                if (lastBin >= 0 && bin != lastBin) {
                    val from = min(lastBin, bin)
                    val to = maxOf(lastBin, bin)
                    val span = (bin - lastBin).toFloat()
                    for (i in from..to) {
                        val t = if (span == 0f) 0f else (i - lastBin) / span
                        shape[i] = lastVal + (v - lastVal) * t
                    }
                } else {
                    shape[bin] = v
                }
                lastBin = bin
                lastVal = v
                onShapeChange?.invoke(shape.copyOf())
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lastBin = -1
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        pBg.color = ZuluTheme.padBg
        c.drawRect(0f, 0f, w, h, pBg)
        for (i in 1 until 8) c.drawLine(w * i / 8f, 0f, w * i / 8f, h, pGrid)
        c.drawLine(0f, h / 2f, w, h / 2f, pMid)
        val path = android.graphics.Path()
        for (i in 0 until 64) {
            val x = w * i / 63f
            val y = (1f - shape[i]) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        c.drawPath(path, pLine)
        val ph = (System.nanoTime() % 1_000_000_000L) / 1_000_000_000f
        c.drawLine(w * ph, 0f, w * ph, h, pPlay)
        if (isShown) postInvalidateOnAnimation()
    }
}

class PunchPadView(context: Context) : View(context) {
    private val marker = markerFont(context)
    var onPunchDown: ((Int) -> Unit)? = null
    var onPunchUp: ((Int) -> Unit)? = null
    var onZoneLongPress: ((Int) -> Unit)? = null
    var onExit: (() -> Unit)? = null
    var shiftCheck: (() -> Boolean)? = null
    private val lpHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lpRunnable: Runnable? = null

    private var labels = listOf("REV", "DLY", "ECH", "RPT", "STP", "FRZ", "SCR", "TDO", "CRS")
    private val colNames = listOf("ZULU", "APHX", "HGTN", "SKRX", "DOOM", "WAIT", "RHED", "BOC", "DAFT", "W/Y")
    private var colIdx = 0
    var onCollectionChange: ((Int) -> Unit)? = null
    private var headerH = 0f
    private val active = BooleanArray(9)
    private val pointers = android.util.SparseArray<Int>()
    private val pChip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = silk
        typeface = marker
        textAlign = Paint.Align.CENTER
    }

    private val pBg = Paint().apply { color = ZuluTheme.padBg }
    private val pScrew = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 64, 76) }
    private val pGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(75, 82, 98)
        strokeWidth = 2f
    }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = silk
        typeface = marker
        textAlign = Paint.Align.CENTER
    }
    private val pOnFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 176, 0)
    }
    private val pOnBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = amber
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun setActive(zone: Int, on: Boolean) {
        if (zone in 0 until 9) {
            active[zone] = on
            invalidate()
        }
    }

    fun setLabels(l: List<String>) {
        labels = l
        invalidate()
    }

    fun setCollectionIndex(i: Int) {
        colIdx = i
        invalidate()
    }

    fun releaseAll() {
        for (z in 0 until 9) {
            if (active[z]) {
                active[z] = false
                onPunchUp?.invoke(z)
            }
        }
        pointers.clear()
        invalidate()
    }

    private fun zoneOf(x: Float, y: Float): Int {
        val gy = (y - headerH).coerceIn(0f, height - headerH - 1f)
        val c = (x / (width / 3f)).toInt().coerceIn(0, 2)
        val r = (gy / ((height - headerH) / 3f)).toInt().coerceIn(0, 2)
        return r * 3 + c
    }

    private fun press(id: Int, z: Int) {
        pointers.put(id, z)
        if (!active[z]) {
            active[z] = true
            onPunchDown?.invoke(z)
        }
        lpRunnable?.let { lpHandler.removeCallbacks(it) }
        val lp = Runnable { if (pointers.get(id, -1) == z) onZoneLongPress?.invoke(z) }
        lpRunnable = lp
        lpHandler.postDelayed(lp, 500)
        invalidate()
    }

    private fun release(id: Int) {
        lpRunnable?.let { lpHandler.removeCallbacks(it) }
        lpRunnable = null
        val z = pointers.get(id, -1)
        if (z >= 0) {
            active[z] = false
            onPunchUp?.invoke(z)
            pointers.delete(id)
            invalidate()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (shiftCheck?.invoke() == true) {
                    onExit?.invoke()
                    return true
                }
                val i = e.actionIndex
                if (e.getY(i) < headerH) {
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                        val chip = (e.getX(i) / (width / colNames.size.toFloat())).toInt()
                            .coerceIn(0, colNames.size - 1)
                        if (chip != colIdx) onCollectionChange?.invoke(chip)
                    }
                    return true
                }
                press(e.getPointerId(i), zoneOf(e.getX(i), e.getY(i)))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val z = zoneOf(e.getX(i), e.getY(i))
                    val old = pointers.get(id, -1)
                    if (old >= 0 && old != z) {
                        release(id)
                        press(id, z)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                release(e.getPointerId(e.actionIndex))
                if (e.pointerCount <= 1) releaseAll()
            }
            MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        headerH = h / 7f
        pBg.color = ZuluTheme.padBg
        c.drawRect(0f, 0f, w, h, pBg)
        drawScrews(c, pScrew, 0f, 0f, w, h, context.resources.displayMetrics.density)
        val cw = w / 3f
        val gh = h - headerH
        val ch = gh / 3f
        for (i in 1 until 3) {
            c.drawLine(cw * i, headerH, cw * i, h, pGrid)
            c.drawLine(0f, headerH + ch * i, w, h, pGrid)
        }
        c.drawLine(0f, headerH, w, headerH, pGrid)
        val chw = w / colNames.size
        for (i in colNames.indices) {
            pChip.color = if (i == colIdx) amber else Color.rgb(105, 111, 128)
            pChip.textSize = minOf(headerH * 0.45f, chw / (colNames[i].length * 0.85f))
            c.drawText(colNames[i], chw * i + chw / 2f, headerH * 0.68f, pChip)
        }
        if (colIdx in colNames.indices) {
            c.drawLine(chw * colIdx + chw * 0.15f, headerH - 4f,
                chw * (colIdx + 1) - chw * 0.15f, headerH - 4f, pOnBorder)
        }
        pLabel.textSize = ch / 3.6f
        for (z in 0 until 9) {
            val cx = cw * (z % 3) + cw / 2f
            val cy = headerH + ch * (z / 3) + ch / 2f
            if (active[z]) {
                c.drawRect(cw * (z % 3) + 3f, headerH + ch * (z / 3) + 3f,
                    cw * (z % 3 + 1) - 3f, headerH + ch * (z / 3 + 1) - 3f, pOnFill)
                c.drawRect(cw * (z % 3) + 3f, headerH + ch * (z / 3) + 3f,
                    cw * (z % 3 + 1) - 3f, headerH + ch * (z / 3 + 1) - 3f, pOnBorder)
            }
            c.drawText(labels[z], cx, cy + pLabel.textSize / 3f, pLabel)
        }
    }
}

class ParamPageView(context: Context) : View(context) {
    private val marker = markerFont(context)
    var onParam: ((Int, Float) -> Unit)? = null
    var onAssignCycle: ((Int) -> Unit)? = null
    var onParamLongPress: ((Int) -> Unit)? = null
    var onExit: (() -> Unit)? = null
    var onMasterToggle: (() -> Unit)? = null
    var onSnapToggle: (() -> Unit)? = null
    var shiftCheck: (() -> Boolean)? = null
    var masterOn = false
    var snapMode = 0
    var accentColor = amber
    var bankIdx = -1
    var bankCols = IntArray(0)
    var onBankTap: ((Int) -> Unit)? = null
    var onCommTap: (() -> Unit)? = null
    var onPatchTap: (() -> Unit)? = null
    var mapAvailable = false
    var mapMode = false
    var mapVals: Array<FloatArray>? = null
    var mapMask = -1
    var mapCols = 16
    var onMapTab: (() -> Unit)? = null
    var onMapCell: ((Int, Int, Float) -> Unit)? = null

    private var labels: List<String> = emptyList()
    private var values = FloatArray(0)
    private var assigns = IntArray(0)
    private var dragSlot = -1
    private val longHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPending: Runnable? = null
    private var downX = 0f
    private var downY = 0f
    private var mapRow = -1

    private val pBg = Paint().apply { color = ZuluTheme.padBg }
    private val pScrew = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 64, 76) }
    private val pSlot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ZuluTheme.slot }
    private val pHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = amber }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ZuluTheme.slot }
    private val pBox = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ZuluTheme.slot }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = silk
        typeface = marker
        textAlign = Paint.Align.CENTER
    }
    private val pLegendTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = silk
        typeface = marker
    }
    private val ledCols = intArrayOf(
        Color.rgb(0, 240, 255),
        Color.rgb(70, 255, 130),
        Color.rgb(255, 70, 205),
        amber
    )

    private val tabNamesFull = listOf("PATCH", "COMM", "MAP", "SNAP", "MASTER")
    private fun actionTabs(): List<String> =
        if (mapAvailable) tabNamesFull else tabNamesFull.filter { it != "MAP" }

    private val mapRowNames = listOf("VOL", "PRB", "PTC", "PAN", "TOF")

    private fun dens(): Float = context.resources.displayMetrics.density

    fun setContent(l: List<String>, v: FloatArray, a: IntArray) {
        labels = l
        values = v
        assigns = a
        invalidate()
    }

    fun refresh() {
        invalidate()
    }

    private fun faderTop(): Float = maxOf(height * 0.26f, 62f * dens())
    private fun faderBot(): Float = height * 0.80f

    private class MapGeom(val labW: Float, val top: Float, val cw: Float, val ch: Float, val gap: Float, val cols: Int)

    private fun mapGeom(): MapGeom? {
        val mv = mapVals ?: return null
        val dd = dens()
        val cols = mapCols.coerceIn(1, mv[0].size.coerceAtMost(16))
        val labW = minOf(30f * dd, width * 0.18f)
        val gap = 1.5f * dd
        val top = 42f * dd
        val bot = (height - 10f * dd).toFloat()
        val cw = ((width - labW - gap * (cols + 1)) / cols).coerceAtLeast(3f)
        val ch = ((bot - top - gap * 6f) / 5f).coerceAtLeast(3f)
        return MapGeom(labW, top, cw, ch, gap, cols)
    }

    private fun mapSet(x: Float) {
        val m = mapGeom() ?: return
        val mv = mapVals ?: return
        if (mapRow < 0 || mapRow >= mv.size) return
        if (x < m.labW + m.gap) return
        val c = ((x - m.labW) / (m.cw + m.gap)).toInt().coerceIn(0, m.cols - 1)
        val v = ((x - m.labW - m.gap) / m.cw).coerceIn(0f, 1f)
        if (mv[mapRow][c] != v) {
            mv[mapRow][c] = v
            onMapCell?.invoke(c, mapRow, v)
            invalidate()
        }
    }

    private fun setFromY(slot: Int, y: Float) {
        val top = faderTop()
        val bot = faderBot()
        val v = 1f - ((y - top) / (bot - top))
        values[slot] = v.coerceIn(0f, 1f)
        onParam?.invoke(slot, values[slot])
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val dd = dens()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (e.y < 18f * dd) {
                    val chip = (e.x * 9f / width.coerceAtLeast(1)).toInt().coerceIn(0, 8)
                    onBankTap?.invoke(chip)
                    return true
                }
                val tabs = actionTabs()
                if (e.y < 35f * dd) {
                    val idx = (e.x * tabs.size / width.coerceAtLeast(1)).toInt().coerceIn(0, tabs.size - 1)
                    when (tabs[idx]) {
                        "PATCH" -> onPatchTap?.invoke()
                        "COMM" -> onCommTap?.invoke()
                        "MAP" -> onMapTab?.invoke()
                        "SNAP" -> onSnapToggle?.invoke()
                        else -> onMasterToggle?.invoke()
                    }
                    return true
                }
                if (mapMode) {
                    val m = mapGeom()
                    if (m != null && e.x >= m.labW && e.y >= m.top) {
                        val r = ((e.y - m.top) / (m.ch + m.gap)).toInt()
                        if (r in 0..4) {
                            mapRow = r
                            mapSet(e.x)
                        }
                    }
                    return true
                }
                if (shiftCheck?.invoke() == true) {
                    if (e.y < 44f * dd || e.y > height * 0.92f) {
                        onExit?.invoke()
                    } else {
                        val n = labels.size.coerceAtLeast(1)
                        val slot = (e.x * n / width).toInt().coerceIn(0, n - 1)
                        onAssignCycle?.invoke(slot)
                    }
                    return true
                }
                val n = labels.size.coerceAtLeast(1)
                if (e.y < 52f * dd) {
                    val slot = (e.x * n / width).toInt().coerceIn(0, n - 1)
                    onAssignCycle?.invoke(slot)
                    return true
                }
                dragSlot = (e.x * n / width).toInt().coerceIn(0, n - 1)
                downX = e.x
                downY = e.y
                val slot = dragSlot
                longPending = Runnable {
                    longPending = null
                    dragSlot = -1
                    onParamLongPress?.invoke(slot)
                }
                longHandler.postDelayed(longPending!!, 450)
                setFromY(dragSlot, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (mapMode) {
                    if (mapRow >= 0) mapSet(e.x)
                    return true
                }
                if (longPending != null &&
                    (Math.abs(e.x - downX) > 8 * dd || Math.abs(e.y - downY) > 8 * dd)) {
                    longHandler.removeCallbacks(longPending!!)
                    longPending = null
                }
                if (dragSlot >= 0) setFromY(dragSlot, e.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mapMode) {
                    mapRow = -1
                    return true
                }
                longPending?.let { longHandler.removeCallbacks(it) }
                longPending = null
                dragSlot = -1
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = dens()
        pBg.color = ZuluTheme.padBg
        c.drawRect(0f, 0f, w, h, pBg)
        drawRelief(c, 0f, 0f, w, h, d)
        drawScrews(c, pScrew, 0f, 0f, w, h, d)
        val chipW = w / 9f
        for (i in 0 until 9) {
            val l = i * chipW + 1f * d
            val r = (i + 1) * chipW - 1f * d
            val bc = if (i < bankCols.size) bankCols[i] else amber
            pBox.style = Paint.Style.FILL
            pBox.color = if (i == bankIdx) bc else ZuluTheme.slot
            c.drawRoundRect(l, 2.5f * d, r, 16.5f * d, 3f * d, 3f * d, pBox)
            pBox.style = Paint.Style.STROKE
            pBox.strokeWidth = d
            pBox.color = if (i == bankIdx) bc else ZuluTheme.edge
            c.drawRoundRect(l, 2.5f * d, r, 16.5f * d, 3f * d, 3f * d, pBox)
            pBox.style = Paint.Style.FILL
            pLabel.textSize = minOf(chipW * 0.5f, 9.5f * d)
            pLabel.color = if (i == bankIdx) Color.rgb(16, 18, 23) else silk
            c.drawText((i + 1).toString(), (l + r) / 2f, 13.5f * d, pLabel)
        }
        val tabs = actionTabs()
        val tw = w / tabs.size
        for (j in tabs.indices) {
            val name = tabs[j]
            val l = j * tw + 1.5f * d
            val r = (j + 1) * tw - 1.5f * d
            val t = 19.5f * d
            val b = 33.5f * d
            pBox.style = Paint.Style.FILL
            pBox.color = when (name) {
                "MAP" -> if (mapMode) accentColor else ZuluTheme.slot
                "SNAP" -> when (snapMode) {
                    1 -> Color.rgb(0, 240, 255)
                    2 -> Color.rgb(70, 255, 130)
                    else -> ZuluTheme.slot
                }
                "MASTER" -> if (masterOn) Color.rgb(185, 110, 255) else ZuluTheme.slot
                else -> ZuluTheme.slot
            }
            c.drawRoundRect(l, t, r, b, 3f * d, 3f * d, pBox)
            pBox.style = Paint.Style.STROKE
            pBox.strokeWidth = d
            pBox.color = when (name) {
                "PATCH" -> Color.rgb(190, 120, 255)
                "COMM" -> Color.rgb(255, 120, 190)
                "MAP" -> accentColor
                "SNAP" -> when (snapMode) {
                    1 -> Color.rgb(0, 240, 255)
                    2 -> Color.rgb(70, 255, 130)
                    else -> ZuluTheme.edge
                }
                else -> if (masterOn) Color.rgb(185, 110, 255) else ZuluTheme.edge
            }
            c.drawRoundRect(l, t, r, b, 3f * d, 3f * d, pBox)
            pBox.style = Paint.Style.FILL
            pLabel.textSize = minOf(tw * 0.16f, 8.5f * d)
            val active = when (name) {
                "MAP" -> mapMode
                "SNAP" -> snapMode > 0
                "MASTER" -> masterOn
                else -> false
            }
            pLabel.color = if (active) Color.rgb(16, 18, 23) else silk
            val tabTxt = when {
                name == "SNAP" && snapMode == 1 -> "SN·M"
                name == "SNAP" && snapMode == 2 -> "SN·S"
                else -> name
            }
            c.drawText(tabTxt, (l + r) / 2f, b - 5f * d, pLabel)
        }
        pBox.color = accentColor
        c.drawRect(0f, 35.5f * d, w, 37f * d, pBox)

        if (mapMode) {
            val m = mapGeom()
            val mv = mapVals
            if (m != null && mv != null) {
                for (r in 0 until 5) {
                    val y = m.top + r * (m.ch + m.gap)
                    pLegendTxt.textSize = minOf(m.ch * 0.55f, 9f * d)
                    pLegendTxt.color = silk
                    c.drawText(mapRowNames[r], 2f * d, y + m.ch * 0.7f, pLegendTxt)
                    for (ci in 0 until m.cols) {
                        val x = m.labW + m.gap + ci * (m.cw + m.gap)
                        val enabled = mapMask < 0 || (mapMask and (1 shl ci)) != 0
                        val v = mv[r].getOrNull(ci) ?: 0f
                        pBox.color = if (enabled) Color.rgb(18, 20, 26) else Color.rgb(12, 13, 17)
                        c.drawRoundRect(x, y, x + m.cw, y + m.ch, 1.5f * d, 1.5f * d, pBox)
                        pHandle.color = accentColor
                        pHandle.alpha = if (enabled) 255 else 70
                        c.drawRect(x, y + 1f * d, x + m.cw * v.coerceAtLeast(0.02f), y + m.ch - 1f * d, pHandle)
                        pHandle.alpha = 255
                        if (!enabled) {
                            pBox.style = Paint.Style.STROKE
                            pBox.strokeWidth = d
                            pBox.color = Color.rgb(90, 96, 110)
                            c.drawLine(x, y, x + m.cw, y + m.ch, pBox)
                            c.drawLine(x + m.cw, y, x, y + m.ch, pBox)
                            pBox.style = Paint.Style.FILL
                        }
                    }
                }
                pLegendTxt.color = accentColor
                pLegendTxt.textSize = 9f * d
                c.drawText("B${(bankIdx + 1).coerceAtLeast(1)} · MAP", w - 4f * d, h - 4f * d, pLegendTxt)
            }
            return
        }

        val n = labels.size
        if (n == 0) return
        val names = listOf("XY", "LFO", "ARP", "SNAP")
        var lx = 12f * d
        val ly = h - 5.5f * d
        pLegendTxt.textSize = 9f * d
        for (j in 0 until 4) {
            pDot.color = ledCols[j]
            c.drawCircle(lx + 3.5f * d, ly, 3f * d, pDot)
            pLegendTxt.color = silk
            c.drawText(names[j], lx + 9f * d, ly + 3.2f * d, pLegendTxt)
            lx += 9f * d + pLegendTxt.measureText(names[j]) + 7f * d
        }
        val top = faderTop()
        val bot = faderBot()
        val cw = w / n
        val sw = maxOf(cw * 0.28f, 8f * d)
        val ledR = minOf(cw * 0.1f, 3.5f * d)
        val ledStep = cw * 0.24f
        for (i in 0 until n) {
            val cx = cw * i + cw / 2f
            for (j in 0 until 4) {
                val lit = when (j) {
                    0 -> assigns[i] == 1
                    1 -> assigns[i] == 2 || assigns[i] == 3
                    2 -> assigns[i] == 4
                    else -> assigns[i] == 5
                }
                pDot.color = if (lit) ledCols[j] else ZuluTheme.slot
                c.drawCircle(cx + (j - 1.5f) * ledStep, 47f * d, ledR, pDot)
            }
            c.drawRect(cx - sw / 2f, top, cx + sw / 2f, bot, pSlot)
            val hy = bot - values[i] * (bot - top)
            pHandle.color = accentColor
            c.drawRect(cx - sw / 2f - 4f * d, hy - 5f * d, cx + sw / 2f + 4f * d, hy + 5f * d, pHandle)
            pLabel.textSize = minOf(cw * 0.3f, 13f * d)
            c.drawText(labels[i], cx, h * 0.88f, pLabel)
        }
    }
}

class LineInKnob(context: Context) : View(context) {

    val cmdId = "in"

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    var value = 1f
        private set
    var onValueChange: ((Float) -> Unit)? = null

    fun setVal(v: Float) {
        value = v.coerceIn(0f, 2f)
        invalidate()
    }

    private var dragY = 0f
    private var dragV = 0f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.42f

        paint.fill(ZuluTheme.slot)
        canvas.drawCircle(cx, cy, r + 2.5f * d, paint)
        paint.stroke(Color.rgb(90, 98, 116), 1.2f * d)
        canvas.drawCircle(cx, cy, r + 2.5f * d, paint)

        val a = (-135f + 270f * (value / 2f)) * Math.PI.toFloat() / 180f
        paint.stroke(Color.rgb(0, 229, 255), 2f * d)
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -135f, 270f * (value / 2f), false, paint)

        paint.fill(cream)
        canvas.drawCircle(cx, cy, r * 0.55f, paint)
        paint.stroke(Color.rgb(255, 190, 0), 1.4f * d)
        canvas.drawLine(cx, cy, cx + (r * 0.5f) * Math.cos(a.toDouble()).toFloat(),
            cy + (r * 0.5f) * Math.sin(a.toDouble()).toFloat(), paint)

        paint.fill(silk)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = marker
        paint.textSize = 7f * d
        canvas.drawText("IN", cx, h - 1.5f * d, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragY = event.y
                dragV = value
            }
            MotionEvent.ACTION_MOVE -> {
                val dv = (dragY - event.y) / (60f * d)
                value = (dragV + dv).coerceIn(0f, 2f)
                onValueChange?.invoke(value)
                invalidate()
            }
        }
        return true
    }
}

class DrumGridView(context: Context) : View(context) {

    val cmdId = "drums"

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    var padCount = 16
        set(value) { field = value; invalidate() }
    var drumMode = 0
    var octShift = 0
    var kitMask = 0
        set(value) { field = value; invalidate() }

    var onDrum: ((Int) -> Unit)? = null
    var onDrumRecStart: ((Int) -> Unit)? = null
    var onDrumRecStop: (() -> Unit)? = null
    var onModeChange: ((Int) -> Unit)? = null
    var onExit: (() -> Unit)? = null

    private var flash = -1
    private var flashAt = 0L

    private val modeNames = listOf("NOTES", "SLICE", "RND", "KIT")
    private val recHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var recCandidate = -1
    private var recActive = false

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = 4f * d
        val top = 34f * d
        val cols = if (padCount > 16) 8 else 4
        val rows = (padCount + cols - 1) / cols
        val rowsF = rows.toFloat()
        val padW = (w - (cols + 1) * gap) / cols
        val padH = (h - top - (rowsF + 1) * gap) / rowsF
        val rad = if (ZuluTheme.retro) 0f else 5f * d

        paint.fill(Color.rgb(20, 22, 28))
        canvas.drawRect(0f, 0f, w, h, paint)
        drawZebra(canvas, w, h, d)

        paint.textAlign = Paint.Align.CENTER
        paint.typeface = marker
        for (i in 0 until padCount) {
            val col = i % cols
            val row = i / cols
            val l = gap + col * (padW + gap)
            val t = top + gap + row * (padH + gap)
            val lit = flash == i && System.currentTimeMillis() - flashAt < 120
            paint.fill(
                when {
                    lit -> Color.rgb(255, 244, 130)
                    drumMode == 3 && (kitMask and (1 shl i)) != 0 -> Color.rgb(0, 190, 160)
                    row == rows - 1 -> Color.rgb(198, 42, 140)
                    else -> Color.rgb(196, 108, 20)
                }
            )
            canvas.drawRoundRect(l, t, l + padW, t + padH, rad, rad, paint)
            paint.stroke(ZuluTheme.edge, 1f * d)
            drawSketchyRoundRect(canvas, paint, l, t, l + padW, t + padH, rad, 300 + i)
            paint.fill(if (lit) Color.rgb(40, 26, 12) else Color.rgb(255, 244, 130))
            paint.textSize = padH * 0.42f
            canvas.drawText("${i + 1}", l + padW / 2f, t + padH * 0.7f, paint)
        }

        paint.fill(Color.rgb(16, 18, 23))
        paint.alpha = 225
        canvas.drawRoundRect(3f * d, 2f * d, 3f * d + 320f * d, 17f * d, 4f * d, 4f * d, paint)
        paint.alpha = 255
        paint.stroke(Color.rgb(0, 255, 170), 1f * d)
        canvas.drawRoundRect(3f * d, 2f * d, 3f * d + 320f * d, 17f * d, 4f * d, 4f * d, paint)
        for ((m, name) in modeNames.withIndex()) {
            val mw = 80f * d
            val mx = 3f * d + m * mw
            paint.fill(if (drumMode == m) Color.rgb(0, 255, 170) else Color.rgb(120, 128, 146))
            paint.textSize = 9f * d
            canvas.drawText(name, mx + mw / 2f, 13f * d, paint)
        }

        paint.fill(Color.rgb(16, 18, 23))
        paint.alpha = 225
        canvas.drawRoundRect(3f * d, 20f * d, 3f * d + 230f * d, 31f * d, 4f * d, 4f * d, paint)
        paint.alpha = 255
        paint.stroke(Color.rgb(255, 190, 0), 1f * d)
        canvas.drawRoundRect(3f * d, 20f * d, 3f * d + 230f * d, 31f * d, 4f * d, 4f * d, paint)
        for ((o, lbl) in listOf("-2", "-1", "0", "+1", "+2").withIndex()) {
            val cw = 46f * d
            val cx = 3f * d + o * cw
            paint.fill(if (octShift == o - 2) Color.rgb(255, 190, 0) else Color.rgb(120, 128, 146))
            paint.textSize = 8f * d
            canvas.drawText(lbl, cx + cw / 2f, 28f * d, paint)
        }
        paint.fill(Color.rgb(255, 96, 70))
        paint.textSize = 12f * d
        canvas.drawText("\u00d7", w - 10f * d, 14f * d, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(event.actionIndex)
                val y = event.getY(event.actionIndex)
                if (x > w - 24f * d && y < 20f * d) {
                    onExit?.invoke()
                    return true
                }
                if (y < 20f * d && x < 3f * d + 320f * d) {
                    val m = ((x - 3f * d) / (80f * d)).toInt().coerceIn(0, 3)
                    if (m != drumMode) {
                        drumMode = m
                        onModeChange?.invoke(m)
                        invalidate()
                    }
                    return true
                }
                if (y >= 20f * d && y < 32f * d && x < 3f * d + 230f * d) {
                    val o = (((x - 3f * d) / (46f * d)).toInt() - 2).coerceIn(-2, 2)
                    if (o != octShift) {
                        octShift = o
                        invalidate()
                    }
                    return true
                }
                val gap = 4f * d
                val top = 34f * d
                val cols = if (padCount > 16) 8 else 4
                val rows = (padCount + cols - 1) / cols
                val padW = (w - (cols + 1) * gap) / cols
                val padH = (height - top - rows * gap + gap) / rows
                val col = ((x - gap) / (padW + gap)).toInt()
                val row = ((y - top - gap) / (padH + gap)).toInt()
                if (col in 0 until cols && row in 0 until rows) {
                    val i = row * cols + col
                    if (i in 0 until padCount) {
                        flash = i
                        flashAt = System.currentTimeMillis()
                        onDrum?.invoke(i)
                        if (drumMode == 3) {
                            recCandidate = i
                            val pi = i
                            recHandler.postDelayed({
                                if (recCandidate == pi && !recActive) {
                                    recActive = true
                                    onDrumRecStart?.invoke(pi)
                                }
                            }, 600)
                        }
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                recHandler.removeCallbacksAndMessages(null)
                if (recActive) {
                    recActive = false
                    onDrumRecStop?.invoke()
                }
                recCandidate = -1
                flash = -1
                invalidate()
            }
        }
        return true
    }
}

class ModeBadgeView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    var mode = 0
        set(value) { field = value; invalidate() }

    var subInfo = ""
        set(value) { field = value; invalidate() }

    var onBadgeTap: (() -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val col = when (mode) {
            1 -> Color.rgb(0, 240, 255)
            2 -> Color.rgb(70, 255, 130)
            3 -> Color.rgb(0, 229, 255)
            else -> Color.rgb(255, 190, 0)
        }
        val name = when (mode) {
            1 -> if (ZuluTheme.xFiles) "\u2243" else "MOTION"
            2 -> if (ZuluTheme.xFiles) "\u25a3" else "SAMPLES"
            3 -> if (ZuluTheme.xFiles) "\u25c6" else "GRP"
            else -> if (ZuluTheme.xFiles) "\u25c7" else "SC\u00c8NE"
        }
        paint.fill(Color.rgb(16, 18, 23))
        canvas.drawRoundRect(0f, 0f, w, h, 5f * d, 5f * d, paint)
        paint.stroke(col, 1.2f * d)
        canvas.drawRoundRect(0f, 0f, w, h, 5f * d, 5f * d, paint)
        paint.fill(col)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = marker
        paint.textSize = h * 0.42f
        canvas.drawText(name, w / 2f, if (subInfo.isEmpty()) h * 0.68f else h * 0.52f, paint)
        if (subInfo.isNotEmpty()) {
            paint.textSize = h * 0.28f
            canvas.drawText(subInfo, w / 2f, h * 0.88f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            onBadgeTap?.invoke()
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }
}

class MiniKeysView(context: Context) : View(context) {
    var onKey: ((Int, Boolean) -> Unit)? = null
    var onOctave: (() -> Unit)? = null
    var octaves = 2
    var baseOctave = 3
    var glideMode = false

    private val pressed = HashSet<Int>()
    private val whiteKey = Paint()
    private val blackKey = Paint()
    private val pressKey = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(165, 172, 185)
        typeface = markerFont(context)
        textAlign = Paint.Align.CENTER
    }

    private val rowSemis = 18
    private val whitesRow = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12, 14, 16, 17)
    private val blacksRow = listOf(1 to 1, 3 to 2, 6 to 4, 8 to 5, 10 to 6, 13 to 8, 15 to 9)

    fun keySemis(): IntRange = 0 until rowSemis * 2

    fun releaseAll() {
        for (k in pressed.toList()) onKey?.invoke(k, false)
        pressed.clear()
        invalidate()
    }

    private fun keyAt(x: Float, y: Float): Int {
        val w = width.toFloat()
        val h = height.toFloat()
        val rh = h / 2f
        val row = (y / rh).toInt().coerceIn(0, 1)
        val ww = w / whitesRow.size
        val wi = (x / ww).toInt().coerceIn(0, whitesRow.size - 1)
        var semi = whitesRow[wi]
        val rowTop = row * rh
        if (y - rowTop < rh * 0.62f) {
            for ((b, boundary) in blacksRow) {
                val bx = boundary * ww
                if (x > bx - ww * 0.3f && x < bx + ww * 0.3f) {
                    semi = b
                    break
                }
            }
        }
        return row * rowSemis + semi
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val k = keyAt(e.getX(i), e.getY(i))
                if (k !in pressed) {
                    pressed.add(k)
                    onKey?.invoke(k, true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val alive = HashSet<Int>()
                for (i in 0 until e.pointerCount) {
                    alive.add(keyAt(e.getX(i), e.getY(i)))
                }
                for (k in pressed.toList()) {
                    if (k !in alive) {
                        pressed.remove(k)
                        onKey?.invoke(k, false)
                    }
                }
                for (k in alive) {
                    if (k !in pressed) {
                        pressed.add(k)
                        onKey?.invoke(k, true)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val k = keyAt(e.getX(i), e.getY(i))
                pressed.remove(k)
                onKey?.invoke(k, false)
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        invalidate()
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = context.resources.displayMetrics.density
        c.drawRect(0f, 0f, w, h, Paint().apply { color = ZuluTheme.padBg })
        val ww = w / whitesRow.size
        val rh = h / 2f
        whiteKey.color = Color.rgb(225, 228, 235)
        blackKey.color = Color.rgb(35, 38, 48)
        pressKey.color = if (glideMode) Color.rgb(0, 240, 215) else Color.rgb(255, 190, 0)
        labelP.textSize = 8f * d
        for (row in 0 until 2) {
            val rowTop = row * rh
            for (i in 0 until whitesRow.size) {
                val x = i * ww
                val semi = row * rowSemis + whitesRow[i]
                val paint = if (semi in pressed) pressKey else whiteKey
                c.drawRect(x + 1f, rowTop, x + ww - 1f, rowTop + rh, paint)
                if (whitesRow[i] % 12 == 0) {
                    labelP.color = Color.rgb(100, 106, 120)
                    c.drawText("C${baseOctave + (row * rowSemis + whitesRow[i]) / 12}", x + ww / 2f, rowTop + rh - 4f * d, labelP)
                }
            }
            for ((b, boundary) in blacksRow) {
                val semi = row * rowSemis + b
                val bx = boundary * ww
                val paint = if (semi in pressed) pressKey else blackKey
                c.drawRect(bx - ww * 0.3f, rowTop, bx + ww * 0.3f, rowTop + rh * 0.62f, paint)
            }
        }
        if (glideMode) {
            labelP.color = Color.rgb(0, 240, 215)
            labelP.textAlign = Paint.Align.RIGHT
            c.drawText("GLIDE", w - 4f * d, 10f * d, labelP)
            labelP.textAlign = Paint.Align.CENTER
        }
    }
}

class SirenPadView(context: Context) : View(context) {
    var onCtl: ((Float, Float) -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var nx = 0.5f
    var ny = 0.5f

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (e.x > width - 20f * d && e.y < 20f * d) {
                    onStop?.invoke()
                    return true
                }
                nx = (e.x / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                ny = (e.y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
                invalidate()
                onCtl?.invoke(nx, ny)
            }
        }
        return true
    }
}

class PatchBayView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val marker = markerFont(context)
    private val chipP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(236, 224, 196)
        textSize = 10f * d
        typeface = marker
    }
    private val cabP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val jackP = Paint(Paint.ANTI_ALIAS_FLAG)

    var rows: List<Triple<String, String, Int>> = emptyList()
    var onTap: (() -> Unit)? = null

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) onTap?.invoke()
        return true
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.argb(235, 10, 11, 15))
        val wpt = width.toFloat()
        val hgt = height.toFloat()
        txtP.textSize = 13f * d
        txtP.color = Color.rgb(190, 120, 255)
        txtP.textAlign = Paint.Align.CENTER
        c.drawText("BAIE DE PATCH", wpt / 2f, 8f * d + 13f * d, txtP)
        txtP.textSize = 9f * d
        txtP.color = silk
        c.drawText("TOUT CE QUI EST BRANCHÉ · TAP POUR FERMER", wpt / 2f, 8f * d + 26f * d, txtP)

        val top = 44f * d
        val bot = hgt - 10f * d
        val chipW = 86f * d
        val chipH = 20f * d
        val x0 = 10f * d
        val x1 = wpt - 10f * d - chipW
        val srcs = rows.map { it.first }.distinct()
        val dsts = rows.map { it.second }.distinct()
        fun yPos(list: List<String>, idx: Int): Float =
            if (list.size < 2) (top + bot) / 2f else top + (bot - top) * idx / (list.size - 1)

        txtP.textAlign = Paint.Align.CENTER
        txtP.textSize = 9f * d
        txtP.color = Color.rgb(120, 130, 148)
        c.drawText("SOURCES", x0 + chipW / 2f, top - 6f * d, txtP)
        c.drawText("DESTS", x1 + chipW / 2f, top - 6f * d, txtP)

        val hsv = FloatArray(3)
        for (ci in rows.indices) {
            val (src, dst, col) = rows[ci]
            val si = srcs.indexOf(src)
            val di = dsts.indexOf(dst)
            if (si < 0 || di < 0) continue
            val sy = yPos(srcs, si)
            val dy = yPos(dsts, di)
            val midX = (x0 + chipW + x1) / 2f
            val path = android.graphics.Path()
            path.moveTo(x0 + chipW, sy)
            path.cubicTo(midX, sy, midX, dy, x1, dy)
            android.graphics.Color.colorToHSV(col, hsv)
            hsv[0] = (hsv[0] + ci * 14f) % 360f
            hsv[1] = (hsv[1] * 0.85f + 0.15f).coerceAtMost(1f)
            val cCol = android.graphics.Color.HSVToColor(hsv)
            cabP.color = cCol
            cabP.alpha = 55
            cabP.strokeWidth = 7f * d
            c.drawPath(path, cabP)
            cabP.alpha = 210
            cabP.strokeWidth = 2.5f * d
            c.drawPath(path, cabP)
        }

        fun chip(x: Float, y: Float, label: String, col: Int, jackRight: Boolean) {
            chipP.color = Color.rgb(16, 18, 24)
            chipP.alpha = 255
            c.drawRoundRect(x, y - chipH / 2f, x + chipW, y + chipH / 2f, 6f * d, 6f * d, chipP)
            chipP.color = col
            chipP.alpha = 55
            c.drawRoundRect(x, y - chipH / 2f, x + chipW, y + chipH / 2f, 6f * d, 6f * d, chipP)
            chipP.alpha = 255
            jackP.color = col
            c.drawCircle(if (jackRight) x + chipW - 5f * d else x + 5f * d, y, 3.2f * d, jackP)
            txtP.color = Color.rgb(236, 224, 196)
            txtP.textSize = 9f * d
            c.drawText(label, x + chipW / 2f, y + 3.5f * d, txtP)
        }
        for ((i, s) in srcs.withIndex()) chip(x0, yPos(srcs, i), s, rows.first { it.first == s }.third, true)
        for ((i, s) in dsts.withIndex()) chip(x1, yPos(dsts, i), s, rows.first { it.second == s }.third, false)
        if (rows.isEmpty()) {
            txtP.color = silk
            txtP.textSize = 12f * d
            c.drawText("RIEN N'EST BRANCHÉ", wpt / 2f, hgt / 2f, txtP)
        }
    }
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

class MidiSeqView(context: Context) : View(context) {

    val rows = 6
    val cols = 16
    var playStep = -1
    var rowNames = Array(6) { "" }
    val cells = Array(6) { FloatArray(16) { -1f } }
    var onCellChange: ((Int, Int, Float) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val marker = markerFont(context)

    private var dragRow = -1
    private var dragCol = -1
    private var dragStartCol = -1
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartVal = 0.5f
    private var dragMoved = false

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
        for (r in 0 until rows) {
            val t = top + gap + r * (cellH + gap)
            paint.textSize = min(9f * d, cellH * 0.5f)
            paint.color = Color.rgb(150, 158, 172)
            val nm = rowNames[r].take(6)
            canvas.drawText(if (nm.isEmpty()) "-" else nm, labW / 2f, t + cellH * 0.68f, paint)
            for (c in 0 until cols) {
                val l = labW + gap + c * (cw + gap) + gap
                val v = cells[r][c]
                val isPlay = playStep >= 0 && c == playStep
                paint.fill(
                    when {
                        v >= 0f && isPlay -> Color.rgb(255, (244 * v).toInt(), (130 * v).toInt())
                        v >= 0f -> Color.rgb((255 * v).toInt(), (170 * v).toInt(), (20 * v).toInt())
                        isPlay -> Color.rgb(52, 56, 68)
                        c % 4 == 0 -> Color.rgb(34, 38, 48)
                        else -> Color.rgb(26, 30, 38)
                    }
                )
                canvas.drawRoundRect(l, t, l + cw, t + cellH, 3f * d, 3f * d, paint)
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
                dragRow = r
                dragCol = c
                dragStartCol = c
                dragStartX = event.x
                dragStartY = event.y
                dragStartVal = if (cells[r][c] >= 0f) cells[r][c] else 0.5f
                dragMoved = false
                if (cells[r][c] < 0f) {
                    cells[r][c] = 0.5f
                    onCellChange?.invoke(r, c, 0.5f)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragRow >= 0 && cells[dragRow][dragCol] >= 0f) {
                    if (Math.abs(event.x - dragStartX) > 6f * d || Math.abs(event.y - dragStartY) > 6f * d) {
                        dragMoved = true
                        val dy = (event.y - dragStartY) / (h * 0.7f)
                        val v = (dragStartVal - dy).coerceIn(0.02f, 1f)
                        val dx = (event.x - dragStartX) / (cw + gap)
                        val nc = (dragStartCol + Math.round(dx)).coerceIn(0, cols - 1)
                        if (nc != dragCol) {
                            onCellChange?.invoke(dragRow, dragCol, -1f)
                            cells[dragRow][dragCol] = -1f
                            dragCol = nc
                            cells[dragRow][dragCol] = v
                            onCellChange?.invoke(dragRow, dragCol, v)
                        } else {
                            cells[dragRow][dragCol] = v
                            onCellChange?.invoke(dragRow, dragCol, v)
                        }
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragRow >= 0 && event.actionMasked == MotionEvent.ACTION_UP && !dragMoved) {
                    cells[dragRow][dragCol] = -1f
                    onCellChange?.invoke(dragRow, dragCol, -1f)
                }
                dragRow = -1
                dragCol = -1
                dragStartCol = -1
                invalidate()
            }
        }
        return true
    }
}

class PhylactereView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = android.text.TextPaint(android.text.TextPaint.ANTI_ALIAS_FLAG)

    var message = ""
        set(value) { field = value; requestLayout(); invalidate() }

    var confirmMode = false
        set(value) { field = value; requestLayout(); invalidate() }

    var onYes: (() -> Unit)? = null
    var onNo: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    var yesLabel = "OUI"
        set(value) { field = value; invalidate() }

    var noLabel = "NON"
        set(value) { field = value; invalidate() }

    private var textLayout: android.text.StaticLayout? = null
    private var bodyW = 0f
    private var bodyH = 0f
    private val pad = 14f * d
    private val tailH = 12f * d
    private val btnH = 34f * d
    private var yesRect = android.graphics.RectF()
    private var noRect = android.graphics.RectF()
    private var crossRect = android.graphics.RectF()
    private var dismissing = false

    init {
        textPaint.color = Color.rgb(22, 24, 30)
        textPaint.textSize = 13f * d
        textPaint.typeface = Typeface.SANS_SERIF
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxTextW = (210f * d).toInt()
        val tl = android.text.StaticLayout.Builder.obtain(
            message, 0, message.length, textPaint, maxTextW
        ).build()
        textLayout = tl
        bodyW = maxTextW + 2f * pad
        bodyH = tl.height + 2f * pad + (if (confirmMode) btnH + 8f * d else 0f)
        setMeasuredDimension(
            (bodyW + 4f * d).toInt(),
            (bodyH + tailH + 4f * d).toInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        val rad = 18f * d
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(248, 252, 251, 244)
        canvas.drawRoundRect(2f * d, 2f * d, 2f * d + bodyW, 2f * d + bodyH, rad, rad, paint)
        val path = android.graphics.Path()
        val cx = bodyW / 2f + 2f * d
        path.moveTo(cx - 7f * d, 2f * d + bodyH)
        path.lineTo(cx, 2f * d + bodyH + tailH)
        path.lineTo(cx + 7f * d, 2f * d + bodyH)
        path.close()
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * d
        paint.color = Color.rgb(24, 26, 32)
        canvas.drawRoundRect(2f * d, 2f * d, 2f * d + bodyW, 2f * d + bodyH, rad, rad, paint)
        canvas.drawLine(cx - 7f * d, 2f * d + bodyH, cx, 2f * d + bodyH + tailH, paint)
        canvas.drawLine(cx, 2f * d + bodyH + tailH, cx + 7f * d, 2f * d + bodyH, paint)
        canvas.save()
        canvas.translate(pad + 2f * d, pad + 2f * d)
        textLayout?.draw(canvas)
        canvas.restore()
        val cr = 9f * d
        val ccx = 2f * d + bodyW - pad - cr
        val ccy = 2f * d + pad + cr
        crossRect = android.graphics.RectF(ccx - cr * 1.6f, ccy - cr * 1.6f, ccx + cr * 1.6f, ccy + cr * 1.6f)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(24, 26, 32)
        canvas.drawCircle(ccx, ccy, cr, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * d
        paint.color = Color.rgb(252, 251, 244)
        canvas.drawLine(ccx - 4f * d, ccy - 4f * d, ccx + 4f * d, ccy + 4f * d, paint)
        canvas.drawLine(ccx - 4f * d, ccy + 4f * d, ccx + 4f * d, ccy - 4f * d, paint)
        if (confirmMode) {
            val by = 2f * d + bodyH - btnH - 6f * d
            val bw = (bodyW - 3f * pad) / 2f
            yesRect = android.graphics.RectF(pad, by, pad + bw, by + btnH)
            noRect = android.graphics.RectF(2f * pad + bw, by, pad + bodyW - pad, by + btnH)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(255, 190, 0)
            canvas.drawRoundRect(yesRect, 6f * d, 6f * d, paint)
            paint.color = Color.rgb(70, 76, 90)
            canvas.drawRoundRect(noRect, 6f * d, 6f * d, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(14, 16, 20)
            paint.textSize = 13f * d
            paint.typeface = Typeface.SANS_SERIF
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(yesLabel, yesRect.centerX(), yesRect.centerY() + 4.5f * d, paint)
            paint.color = Color.rgb(240, 236, 220)
            canvas.drawText(noLabel, noRect.centerX(), noRect.centerY() + 4.5f * d, paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }

    fun dismissWithFun() {
        if (dismissing) return
        dismissing = true
        pivotX = width / 2f
        pivotY = height.toFloat()
        animate().scaleX(0.02f).scaleY(0.02f).alpha(0f).setDuration(260L)
            .withEndAction {
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
                dismissing = false
                onClose?.invoke()
            }.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y
            if (crossRect.contains(x, y)) {
                dismissWithFun()
                return true
            }
            if (confirmMode) {
                when {
                    yesRect.contains(x, y) -> { onYes?.invoke(); return true }
                    noRect.contains(x, y) -> { onNo?.invoke(); return true }
                }
            }
        }
        return confirmMode
    }
}
