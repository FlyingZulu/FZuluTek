package com.zulu.drums

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.zulu.AudioEngine
import com.zulu.DrumSeqView
import com.zulu.ZuluLink
import kotlin.math.roundToInt

/**
 * ZuluDrums - la boete a rythme de ZuluFx, transplantee telle quelle sur le module sonore.
 * Meme moteur natif, meme DrumSeqView, meme langage. Ajouts : page MIX (tirettes par voix)
 * et ecoute ZuluLink (tempo, transport, CC) pour jouer cadencee par le chef d'orchestre.
 */
class ZuluDrumsActivity : Activity() {

    private val gens = intArrayOf(5, 6, 7, 16, 20, 22, 24)
    private val fxSlots = intArrayOf(51, 52, 53, 62, 80, 82, 83)
    private val voiceNames = arrayOf("KICK", "SNARE", "HAT", "SIR1", "KIK8", "MAL", "TABLA")
    private val kitDefs = mapOf(
        51 to listOf("TUNE", "DECAY", "CLICK", "NIVEAU"),
        52 to listOf("TON", "DECAY", "GRAINS", "NIVEAU"),
        53 to listOf("TON", "DECAY", "PITCH", "NIVEAU"),
        62 to listOf("TON", "DECAY", "MORDANT", "NIVEAU"),
        80 to listOf("TUNE", "PUNCH", "DECAY", "NIVEAU"),
        82 to listOf("TUNE", "BOIS", "DECAY", "NIVEAU"),
        83 to listOf("TUNE", "METAL", "DECAY", "NIVEAU")
    )
    private val kitDefaults = mapOf(
        51 to floatArrayOf(0.5f, 0.5f, 0.2f, 0.5f),
        52 to floatArrayOf(0.5f, 0.5f, 0.33f, 0.5f),
        53 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        62 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        80 to floatArrayOf(0.5f, 0.5f, 0.3f, 0.5f),
        82 to floatArrayOf(0.5f, 0.4f, 0.3f, 0.5f),
        83 to floatArrayOf(0.5f, 0.4f, 0.4f, 0.5f)
    )

    private val engine = AudioEngine()
    private lateinit var seq: DrumSeqView
    private val handler = Handler(Looper.getMainLooper())

    private var running = false
    private var bpm = 120f
    private var startMs = 0L
    private var lastFrac = 0f
    private val rowCol = IntArray(7) { -1 }
    private val glitchOn = BooleanArray(7)
    private val mutOn = BooleanArray(7)
    private val glitchAmt = 1f
    private val mutAmt = 1f
    private var liveRec = false

    private val vol = FloatArray(7) { 1f }
    private var master = 0.85f

    private var voiceSel = 0
    private val decDepth = FloatArray(7) { 1f }
    private val driveDepth = FloatArray(7) { 1f }
    private val voiceBtns = ArrayList<Button>()
    private val voiceSeeks = ArrayList<SeekBar>()
    private val voiceLbls = ArrayList<TextView>()
    private val voiceMacSeeks = ArrayList<SeekBar>()
    private val mixFaders = ArrayList<FaderView>()

    private var runBtn: Button? = null
    private var recBtn: Button? = null
    private var bpmLabel: TextView? = null
    private var linkLabel: TextView? = null
    private var mixPanel: LinearLayout? = null
    private var kitPanel: LinearLayout? = null
    private val modeBtns = ArrayList<Button>()

    private val cream = Color.rgb(236, 224, 196)
    private val silk = Color.rgb(165, 172, 185)
    private val amber = Color.rgb(255, 190, 0)
    private val green = Color.rgb(70, 255, 130)
    private val red = Color.rgb(200, 60, 40)

    private var lastTap = 0L
    private val tapDeltas = ArrayList<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val d = resources.displayMetrics.density

        val ok = engine.nativeStart(false)
        if (!ok) Toast.makeText(this, "AUDIO : demarrage impossible", Toast.LENGTH_LONG).show()
        engine.nativeSetDrumDec(0.25f + 2.25f * 0.31f)
        engine.nativeSetDrumDrive(0f)
        engine.nativeSetDrumFil(0f)
        engine.nativeSetDrumLoud(master)
        for (v in gens.indices) {
            val slot = fxSlots[v]
            val defs = kitDefaults[slot] ?: continue
            for (k in 0 until 4) engine.nativeSetFxParam(slot, k, defs[k])
            engine.nativeSetDrumVoiceMacro(v, 0, 1f)
            engine.nativeSetDrumVoiceMacro(v, 1, 1f)
        }

        val root = FrameLayout(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setBackgroundColor(Color.argb(225, 10, 11, 15))
        page.setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
        root.addView(page, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // ----- ligne transport : RUN BPM- BPM+ TAP | KIT MIX | LINK -----
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val runB = makeButton("RUN", green, 11f)
        runB.setOnClickListener { toggleRun(false) }
        runBtn = runB
        top.addView(runB, LinearLayout.LayoutParams((56 * d).toInt(), (32 * d).toInt()))
        val bpmMoins = makeButton("\u2212", silk, 12f)
        bpmMoins.setOnClickListener { setBpm(bpm - 5f) }
        top.addView(bpmMoins, LinearLayout.LayoutParams((34 * d).toInt(), (32 * d).toInt()))
        val bl = TextView(this)
        bl.text = "120"
        bl.setTextColor(cream)
        bl.textSize = 16f
        bl.typeface = Typeface.MONOSPACE
        bl.gravity = Gravity.CENTER
        bpmLabel = bl
        top.addView(bl, LinearLayout.LayoutParams((52 * d).toInt(), (32 * d).toInt()))
        val bpmPlus = makeButton("+", silk, 12f)
        bpmPlus.setOnClickListener { setBpm(bpm + 5f) }
        top.addView(bpmPlus, LinearLayout.LayoutParams((34 * d).toInt(), (32 * d).toInt()))
        val tapB = makeButton("TAP", silk, 9f)
        tapB.setOnClickListener { tap() }
        top.addView(tapB, LinearLayout.LayoutParams((42 * d).toInt(), (32 * d).toInt()))
        val kitB = makeButton("KIT", silk, 9f)
        kitB.setOnClickListener {
            kitPanel!!.visibility =
                if (kitPanel!!.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refreshVoicePanel()
        }
        top.addView(kitB, LinearLayout.LayoutParams((48 * d).toInt(), (32 * d).toInt()))
        val mixB = makeButton("MIX", amber, 9f)
        mixB.setOnClickListener {
            mixPanel!!.visibility =
                if (mixPanel!!.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        top.addView(mixB, LinearLayout.LayoutParams((48 * d).toInt(), (32 * d).toInt()))
        val ll = TextView(this)
        ll.text = "LINK: 0"
        ll.setTextColor(silk)
        ll.textSize = 9f
        ll.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        linkLabel = ll
        top.addView(ll, LinearLayout.LayoutParams(0, (32 * d).toInt(), 1f))
        page.addView(top, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ----- ligne modes : PROBA ROLL LEN PAN GLITCH MUT UNDO REC -----
        val modes = LinearLayout(this)
        modes.orientation = LinearLayout.HORIZONTAL
        val modeDefs = listOf("PROBA" to "proba", "ROLL" to "roll", "LEN" to "len",
            "PAN" to "pan", "GLITCH" to "glitch", "MUT" to "mut")
        for ((label, _) in modeDefs) {
            val b = makeButton(label, silk, 9f)
            b.setOnClickListener { toggleMode(label) }
            modeBtns.add(b)
            modes.addView(b, LinearLayout.LayoutParams(0, (32 * d).toInt(), 1f))
        }
        val undoB = makeButton("UNDO", silk, 9f)
        undoB.setOnClickListener { seq.undo() }
        modes.addView(undoB, LinearLayout.LayoutParams(0, (32 * d).toInt(), 0.9f))
        val recB = makeButton("REC", red, 9f)
        recB.setOnClickListener {
            liveRec = !liveRec
            recB.setTextColor(if (liveRec) amber else red)
        }
        recBtn = recB
        modes.addView(recB, LinearLayout.LayoutParams(0, (32 * d).toInt(), 0.8f))
        page.addView(modes, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ----- panneau KIT (masque) -----
        kitPanel = buildKitPanel(d)
        kitPanel!!.visibility = View.GONE
        page.addView(kitPanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ----- sequenceur -----
        seq = DrumSeqView(this)
        for (i in gens.indices) seq.rowNames[i] = voiceNames[i].take(7)
        seq.onSwipe = { right ->
            if (right && seq.page == 0) seq.doubleTo32()
            else if (!right && seq.page == 1) { seq.page = 0; seq.invalidate() }
        }
        page.addView(seq, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // ----- pads de declenchement -----
        val pads = LinearLayout(this)
        pads.orientation = LinearLayout.HORIZONTAL
        for (i in gens.indices) {
            val pb = makeButton(voiceNames[i].take(5), cream, 9f)
            pb.setOnClickListener {
                if (seq.glitchMode || seq.mutMode) {
                    if (seq.glitchMode) glitchOn[i] = !glitchOn[i] else mutOn[i] = !mutOn[i]
                    val on = if (seq.glitchMode) glitchOn[i] else mutOn[i]
                    pb.setTextColor(if (on) amber else silk)
                    bubble((if (seq.glitchMode) "GLITCH " else "MUTATION ") + voiceNames[i] + if (on) " ON" else " OFF")
                    return@setOnClickListener
                }
                engine.nativeSetGenHitVel(1f * vol[i])
                engine.nativeSetGenHitPan(0.5f)
                engine.nativeGenHit(gens[i], i / 6f, 0.6f)
                if (liveRec && running) {
                    val st = seq.playStep % seq.rowLen[i].coerceAtLeast(1)
                    seq.steps[i][st] = true
                    seq.invalidate()
                }
            }
            pads.addView(pb, LinearLayout.LayoutParams(0, (38 * d).toInt(), 1f))
        }
        page.addView(pads, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (38 * d).toInt()))

        // ----- page MIX (overlay Hammerhead-style) -----
        mixPanel = buildMixPanel(d)
        mixPanel!!.visibility = View.GONE
        root.addView(mixPanel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)

        // groove de bienvenue
        seq.steps[0][0] = true; seq.steps[0][8] = true
        seq.steps[1][4] = true; seq.steps[1][12] = true
        seq.steps[2][2] = true; seq.steps[2][6] = true
        seq.steps[2][10] = true; seq.steps[2][14] = true

        startLink()
        handler.post(tick)
    }

    // ================= UI =================

    private fun makeButton(label: String, color: Int, size: Float): Button {
        val d = resources.displayMetrics.density
        val b = Button(this)
        b.text = label
        b.setTextColor(color)
        b.textSize = size
        b.isAllCaps = false
        b.minHeight = 0; b.minimumHeight = 0; b.minimumWidth = 0
        b.setPadding((7 * d).toInt(), (5 * d).toInt(), (7 * d).toInt(), (5 * d).toInt())
        b.typeface = Typeface.SANS_SERIF
        b.background = panelBg()
        return b
    }

    private fun panelBg(): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(Color.rgb(28, 30, 38))
        g.cornerRadius = 8f
        g.setStroke(2, Color.rgb(60, 64, 76))
        return g
    }

    private fun bubble(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun macroCol(label: String, init: Int, color: Int, onChange: (Float) -> Unit): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val lab = TextView(this)
        lab.text = label
        lab.setTextColor(color)
        lab.textSize = 8f
        col.addView(lab)
        val sb = SeekBar(this)
        sb.max = 100
        sb.progress = init
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pv: Int, fromUser: Boolean) {
                if (fromUser) onChange(pv / 100f)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        col.addView(sb)
        return col
    }

    private fun buildKitPanel(d: Float): LinearLayout {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(Color.argb(235, 14, 15, 20))

        // macros globales
        val gRow = LinearLayout(this)
        gRow.orientation = LinearLayout.HORIZONTAL
        gRow.addView(macroCol("MACRO A \u00b7 DECAY", 31, silk) { engine.nativeSetDrumDec(0.25f + 2.25f * it) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        gRow.addView(macroCol("MACRO B \u00b7 DRIVE", 0, silk) { engine.nativeSetDrumDrive(it) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        gRow.addView(macroCol("FILTRE LP\u2194HP", 50, silk) { engine.nativeSetDrumFil(it * 2f - 1f) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(gRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // selection de voix
        val voiceRow = LinearLayout(this)
        voiceRow.orientation = LinearLayout.HORIZONTAL
        for (i in gens.indices) {
            val vb = makeButton(voiceNames[i].take(6), silk, 9f)
            vb.setOnClickListener {
                voiceSel = i
                refreshVoicePanel()
            }
            voiceBtns.add(vb)
            voiceRow.addView(vb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        panel.addView(voiceRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 4 params de la voix
        val seekRow = LinearLayout(this)
        seekRow.orientation = LinearLayout.HORIZONTAL
        for (k in 0 until 4) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.setTextColor(silk)
            lab.textSize = 8f
            voiceLbls.add(lab)
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.progress = 50
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pv: Int, fromUser: Boolean) {
                    if (fromUser) engine.nativeSetFxParam(fxSlots[voiceSel], k, pv / 100f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            voiceSeeks.add(sb)
            col.addView(sb)
            seekRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        panel.addView(seekRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // macros par voix
        val macRow = LinearLayout(this)
        macRow.orientation = LinearLayout.HORIZONTAL
        for (m in 0 until 2) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.text = if (m == 0) "MACRO A \u00b7 DECAY" else "MACRO B \u00b7 DRIVE"
            lab.setTextColor(amber)
            lab.textSize = 8f
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.progress = 100
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pv: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val depth = pv / 100f
                    if (m == 0) { decDepth[voiceSel] = depth; engine.nativeSetDrumVoiceMacro(voiceSel, 0, depth) }
                    else { driveDepth[voiceSel] = depth; engine.nativeSetDrumVoiceMacro(voiceSel, 1, depth) }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            voiceMacSeeks.add(sb)
            col.addView(sb)
            macRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        panel.addView(macRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return panel
    }

    private fun refreshVoicePanel() {
        val slot = fxSlots[voiceSel]
        for ((i, b) in voiceBtns.withIndex()) b.setTextColor(if (i == voiceSel) amber else silk)
        val defs = kitDefs[slot] ?: return
        val dfl = kitDefaults[slot]
        for (k in 0 until 4) {
            voiceLbls.getOrNull(k)?.text = defs.getOrNull(k) ?: ""
            voiceSeeks.getOrNull(k)?.progress = (((dfl?.getOrNull(k)) ?: 0.5f) * 100f).toInt()
        }
        voiceMacSeeks.getOrNull(0)?.progress = (decDepth[voiceSel] * 100f).toInt()
        voiceMacSeeks.getOrNull(1)?.progress = (driveDepth[voiceSel] * 100f).toInt()
    }

    // ================= page MIX =================

    private fun buildMixPanel(d: Float): LinearLayout {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(Color.argb(242, 12, 13, 17))
        panel.setPadding((10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())

        val title = TextView(this)
        title.text = "MIX"
        title.setTextColor(amber)
        title.textSize = 14f
        title.typeface = Typeface.DEFAULT_BOLD
        panel.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        for (i in gens.indices) {
            val f = FaderView(this)
            f.label = voiceNames[i].take(5)
            f.value = vol[i]
            f.onVal = { v -> vol[i] = v }
            mixFaders.add(f)
            row.addView(f, LinearLayout.LayoutParams(0, (170 * d).toInt(), 1f))
        }
        val fm = FaderView(this)
        fm.label = "MASTER"
        fm.value = master
        fm.isMaster = true
        fm.onVal = { v -> master = v; engine.nativeSetDrumLoud(v) }
        mixFaders.add(fm)
        row.addView(fm, LinearLayout.LayoutParams(0, (170 * d).toInt(), 1.1f))
        panel.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val closeB = makeButton("FERMER", silk, 10f)
        closeB.setOnClickListener { panel.visibility = View.GONE }
        panel.addView(closeB, LinearLayout.LayoutParams(
            (110 * d).toInt(), (34 * d).toInt()))
        return panel
    }

    private inner class FaderView(context: android.content.Context) : View(context) {
        var value = 1f
        var label = ""
        var isMaster = false
        var onVal: ((Float) -> Unit)? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(20, 22, 28)
            c.drawRoundRect(0f, 0f, w, h, 8f, 8f, paint)
            val top = h * 0.14f
            val bot = h * 0.96f
            val railL = w * 0.30f
            val railR = w * 0.70f
            paint.color = Color.rgb(12, 13, 17)
            c.drawRoundRect(railL, top, railR, bot, 4f, 4f, paint)
            val hy = top + (bot - top) * (1f - value)
            paint.color = if (isMaster) green else amber
            c.drawRoundRect(railL, hy, railR, bot, 4f, 4f, paint)
            paint.color = cream
            c.drawCircle(w / 2f, hy, w * 0.26f, paint)
            paint.color = silk
            paint.textSize = (w * 0.17f).coerceAtMost(12f)
            paint.textAlign = Paint.Align.CENTER
            c.drawText(label, w / 2f, h * 0.09f, paint)
            c.drawText(((value * 100f).roundToInt()).toString(), w / 2f, bot + h * 0.02f, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val top = height * 0.14f
            val bot = height * 0.96f
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    value = (1f - (e.y - top) / (bot - top)).coerceIn(0f, 1f)
                    onVal?.invoke(value)
                    invalidate()
                    return true
                }
            }
            return true
        }
    }

    // ================= transport =================

    private fun setBpm(v: Float) {
        bpm = v.coerceIn(40f, 240f)
        bpmLabel?.text = bpm.roundToInt().toString()
        ZuluLink.tempo = bpm.toDouble()
    }

    private fun tap() {
        val now = System.currentTimeMillis()
        if (now - lastTap > 2200L) tapDeltas.clear() else tapDeltas.add(now - lastTap)
        lastTap = now
        if (tapDeltas.size >= 2) {
            val avg = tapDeltas.takeLast(4).average()
            setBpm((60000.0 / avg).toFloat())
            bubble("TAP " + bpm.roundToInt() + " BPM")
        }
    }

    private fun toggleRun(fromNet: Boolean = false) {
        running = !running
        if (running) {
            startMs = System.currentTimeMillis()
            lastFrac = 0f
            for (r in 0 until 7) rowCol[r] = -1
        }
        runBtn?.setTextColor(if (running) amber else green)
        if (fromNet) { bubble(if (running) "START (reseau)" else "STOP (reseau)"); return }
        val console = ZuluLink.peersSnapshot().firstOrNull { it.role == ZuluLink.Role.CONSOLE && !it.lost }
        if (console != null) {
            if (running) ZuluLink.sendStart(console.name) else ZuluLink.sendStop(console.name)
        }
        bubble(if (running) "START" else "STOP")
    }

    private fun toggleMode(label: String) {
        fun isOn(m: String) = when (m) {
            "PROBA" -> seq.probaMode; "ROLL" -> seq.rollMode; "LEN" -> seq.lenMode
            "PAN" -> seq.panMode; "GLITCH" -> seq.glitchMode; else -> seq.mutMode
        }
        when (label) {
            "PROBA" -> seq.probaMode = !seq.probaMode
            "ROLL" -> seq.rollMode = !seq.rollMode
            "LEN" -> seq.lenMode = !seq.lenMode
            "PAN" -> seq.panMode = !seq.panMode
            "GLITCH" -> seq.glitchMode = !seq.glitchMode
            else -> seq.mutMode = !seq.mutMode
        }
        if (isOn(label)) {
            seq.probaMode = label == "PROBA"
            seq.rollMode = label == "ROLL"
            seq.lenMode = label == "LEN"
            seq.panMode = label == "PAN"
            seq.glitchMode = label == "GLITCH"
            seq.mutMode = label == "MUT"
        }
        for (b in modeBtns) b.setTextColor(if (isOn(b.text.toString())) amber else silk)
        seq.invalidate()
    }

    // ================= moteur du sequenceur =================

    private fun hit(r: Int, c: Int) {
        engine.nativeSetGenHitVel(seq.vel[r][c] * vol[r])
        engine.nativeSetGenHitPan(seq.pan[r][c])
        engine.nativeGenHit(gens[r], r / 6f, 0.6f)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (running) {
                val now = System.currentTimeMillis()
                val stepMs = 15000f / bpm.coerceAtLeast(40f)
                val elapsed = (now - startMs).toFloat()
                val stepTotal = (elapsed / stepMs).toInt()
                val frac = (elapsed - stepTotal * stepMs) / stepMs
                seq.playStep = stepTotal
                seq.invalidate()
                for (r in 0 until seq.rows) {
                    val rc = stepTotal % seq.rowLen[r]
                    if (rc != rowCol[r]) {
                        rowCol[r] = rc
                        for (c in seq.steps[r].indices) {
                            if (seq.steps[r][c] && Math.round(c + seq.slides[r][c]) == rc &&
                                (seq.prob[r][c] >= 1f || Math.random() < seq.prob[r][c])
                            ) {
                                hit(r, c)
                                if (glitchOn[r] && glitchAmt > 0f && seq.glitch[r][c] &&
                                    Math.random() < glitchAmt
                                ) {
                                    val rr = r
                                    val cc = c
                                    handler.postDelayed({ hit(rr, cc) }, (stepMs * 0.5f).toLong())
                                    if (Math.random() < glitchAmt) {
                                        handler.postDelayed({ hit(rr, cc) }, (stepMs * 0.75f).toLong())
                                    }
                                }
                            }
                        }
                        if (mutOn[r] && mutAmt > 0f && seq.mut[r][rc] && Math.random() < mutAmt) {
                            seq.steps[r][rc] = !seq.steps[r][rc]
                            seq.invalidate()
                        }
                    }
                    if (seq.steps[r][rc]) {
                        val rn = seq.roll[r][rc]
                        for (k in 1..rn) {
                            val th = k.toFloat() / (rn + 1)
                            if (lastFrac < th && frac >= th &&
                                (seq.prob[r][rc] >= 1f || Math.random() < seq.prob[r][rc])
                            ) hit(r, rc)
                        }
                    }
                }
                lastFrac = frac
            }
            handler.postDelayed(this, 25L)
        }
    }

    // ================= ZuluLink =================

    private fun startLink() {
        try {
            ZuluLink.start("ZULU-DRUMS", ZuluLink.Role.MODULE)
        } catch (e: Exception) {
            bubble("ZuluLink indisponible")
            return
        }
        ZuluLink.tempo = bpm.toDouble()
        ZuluLink.onPeersChanged = {
            runOnUiThread {
                val n = ZuluLink.peersSnapshot().count { !it.lost }
                linkLabel?.text = "LINK: " + n
            }
        }
        ZuluLink.onMessage = { rx ->
            runOnUiThread {
                when (rx.type) {
                    ZuluLink.T_CLOCK -> setBpm(rx.tempo.toFloat())
                    ZuluLink.T_START -> if (!running) toggleRun(true)
                    ZuluLink.T_STOP -> if (running) toggleRun(true)
                    ZuluLink.T_TAP -> tap()
                    ZuluLink.T_CC -> onCc(rx.cc, rx.value)
                }
            }
        }
    }

    private fun onCc(cc: Int, raw: Int) {
        val v = (raw and 0xFF) / 255f
        when {
            cc == 0 -> engine.nativeSetDrumDec(0.25f + 2.25f * v)
            cc == 1 -> engine.nativeSetDrumDrive(v)
            cc == 2 -> engine.nativeSetDrumFil(v * 2f - 1f)
            cc == 3 -> {
                master = v
                engine.nativeSetDrumLoud(v)
                mixFaders.getOrNull(7)?.let { it.value = v; it.invalidate() }
            }
            cc in 10..16 -> {
                val i = cc - 10
                vol[i] = v
                mixFaders.getOrNull(i)?.let { it.value = v; it.invalidate() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { ZuluLink.stop() } catch (_: Exception) {}
        engine.nativeStop()
    }
}
