package com.zulutek.drums

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * ZuluTek Drums v2 — façon PĒRKONS HD-01 (Erica Synths) : 8 pads hybrides
 * (moteur custom + moteur basse/lead, mix HYB), 4 params shapables par pad,
 * kits ZULU/808/909, seq 16 steps façon MIDI loop (COND ODDS/FILL, modes, swing, REC live).
 */
class DrumsActivity : Activity() {

    private lateinit var grid: DrumGridView
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var rec = false
    private var kit = 0
    private var paramPage = 0
    private var dlyTime = 0.30f; private var dlyFb = 0.30f; private var dlyWet = 0.15f; private var revWet = 0.12f
    private var lfoRate = 2.0f; private var lfoDepth = 0.0f
    private val paramVals = arrayOfNulls<TextView>(4)
    private lateinit var condBtn: Button
    private lateinit var modeBtn: Button
    private var padMode = 0
    private var padCond = 0
    private var padMut = 0

    private val uiTick = object : Runnable {
        override fun run() {
            grid.curStep = DrumsAudio.curStep()
            grid.invalidate()
            handler.postDelayed(this, 33)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DrumsAudio.create(48000)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 10, 16))
            setPadding(14, 20, 14, 12)
        }

        root.addView(TextView(this).apply {
            text = "ZuluTek DRUMS — 8 pads hybrides (PĒRKONS-like) + seq 16"
            setTextColor(Color.rgb(0, 255, 224)); textSize = 15f
        })

        grid = DrumGridView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            onPadTap = { pad -> DrumsAudio.hit(pad); refreshParams() }
            onStepTap = { step ->
                val st = DrumsAudio.getStep(selPad, step)
                DrumsAudio.setStep(selPad, step, st[0] == 0, st[1], st[2], st[3])
                refreshSteps(); invalidate()
            }
            onStepLong = { step ->   // cycle COND du step
                val st = DrumsAudio.getStep(selPad, step)
                val nc = (st[2] + 1) % DrumsAudio.COND_NAMES.size
                DrumsAudio.setStep(selPad, step, st[0] == 1, st[1], nc, st[3])
                refreshSteps(); invalidate()
            }
        }
        root.addView(grid)

        fun sliderRow(label: String, tv: TextView, max: Int, prog: Int, cb: (Float) -> Unit): SeekBar {
            val l = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            l.addView(TextView(this).apply {
                text = label; setTextColor(Color.rgb(235, 238, 245)); textSize = 12f
                layoutParams = LinearLayout.LayoutParams(64, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            l.addView(tv)
            root.addView(l)
            val bar = SeekBar(this).apply {
                this.max = max; progress = prog
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { cb(p.toFloat()) }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }
            root.addView(bar)
            return bar
        }

        // ---- transport + tempo + swing ----
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lateinit var playBtn: Button
        lateinit var recBtn: Button
        fun mk(label: String, cb: () -> Unit): Button {
            val b = Button(this).apply {
                text = label; setTextColor(Color.rgb(10, 10, 16)); setBackgroundColor(Color.rgb(0, 255, 224))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(3, 6, 3, 6) }
                setOnClickListener { cb() }
            }
            btns.addView(b); return b
        }
        playBtn = mk("PLAY") {
            running = !running
            DrumsAudio.play(running)
            if (running) DrumsAudio.start()
            playBtn.text = if (running) "STOP" else "PLAY"
        }
        recBtn = mk("REC") {
            rec = !rec
            DrumsAudio.setRec(rec)
            recBtn.setBackgroundColor(if (rec) Color.rgb(255, 70, 70) else Color.rgb(0, 255, 224))
        }
        mk("STEP") {
            grid.editMode = !grid.editMode
            grid.invalidate()
        }
        mk("CLEAR") { DrumsAudio.clear(grid.selPad); grid.refreshSteps(); grid.invalidate() }
        mk("RAND") { DrumsAudio.rand(grid.selPad, 0.4f, (System.nanoTime() shr 8).toInt()); grid.refreshSteps(); grid.invalidate() }
        mk("RESET") { DrumsAudio.reset() }
        root.addView(btns)

        val bpmVal = TextView(this).apply { setTextColor(Color.rgb(0, 255, 224)); textSize = 12f; text = "120 BPM" }
        sliderRow("TEMPO", bpmVal, 280, 60) { p ->
            val bpm = p + 60f
            bpmVal.text = "${bpm.toInt()} BPM"
            DrumsAudio.setBpm(bpm)
        }
        val swingVal = TextView(this).apply { setTextColor(Color.rgb(0, 255, 224)); textSize = 12f; text = "SWING 0%" }
        sliderRow("SWING", swingVal, 50, 0) { p ->
            swingVal.text = "SWING ${p}%"
            DrumsAudio.setSwing(p / 100f)
        }

        // ---- kit ZULU/808/909 ----
        val kitVal = TextView(this).apply { setTextColor(Color.rgb(255, 238, 0)); textSize = 12f; text = "KIT ZULU" }
        mk("KIT") {
            kit = (kit + 1) % DrumsAudio.KIT_NAMES.size
            DrumsAudio.setKit(kit)
            kitVal.text = "KIT ${DrumsAudio.KIT_NAMES[kit]}"
            refreshParams()
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@DrumsActivity).apply { text = "KIT"; setTextColor(Color.rgb(235, 238, 245)); textSize = 12f
                layoutParams = LinearLayout.LayoutParams(64, LinearLayout.LayoutParams.WRAP_CONTENT) })
            addView(kitVal)
        })

        // ---- 8 knobs par voie, 2 pages de 4 (P0..P3 / P4..P7) — façon 8 commandes/voix PĒRKONS ----
        // P0 = PITCH (hauteur) sur toutes les voies ; P4 = HYB, P5/P6 = moteur basse, P7 = LEVEL.
        for (k in 0..3) {
            val tv = TextView(this).apply { setTextColor(Color.rgb(0, 255, 224)); textSize = 12f }
            paramVals[k] = tv
            sliderRow("K${k + 1}", tv, 100, 50) { p ->
                DrumsAudio.setParam(grid.selPad, paramPage * 4 + k, p / 100f)
                tv.text = "${DrumsAudio.PARAM_NAMES[grid.selPad][paramPage * 4 + k]} ${(p / 100f).let { "%.2f".format(it) }}"
            }
        }
        mk2(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }.also { root.addView(it) },
            "PAGE 1/3 (P0-P3)") { b ->
            paramPage = (paramPage + 1) % 3
            b.text = arrayOf("PAGE 1/3 (P0-P3)", "PAGE 2/3 (P4-P7)", "PAGE 3/3 (PAN/SEND)")[paramPage]
            refreshParams()
        }

        // ---- COND / MODE / MUT du pad ----
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        condBtn = mk2(row2, "COND 1:1") { _ ->
            padCond = (padCond + 1) % DrumsAudio.COND_NAMES.size
            condBtn.text = "COND ${DrumsAudio.COND_NAMES[padCond]}"
            // applique au step sélectionné de la grille
            val st = DrumsAudio.getStep(grid.selPad, grid.curStep.coerceIn(0, 15))
            DrumsAudio.setStep(grid.selPad, grid.curStep.coerceIn(0, 15), st[0] == 1, st[1], padCond, st[3])
            grid.refreshSteps(); grid.invalidate()
        }
        modeBtn = mk2(row2, "MODE NORM") { _ ->
            padMode = (padMode + 1) % DrumsAudio.MODE_NAMES.size
            DrumsAudio.setPadMode(grid.selPad, padMode)
            modeBtn.text = "MODE ${DrumsAudio.MODE_NAMES[padMode]}"
        }
        mk2(row2, "MUT 0") { _ ->
            padMut = (padMut + 32) % 256
            DrumsAudio.setPadMut(grid.selPad, padMut)
        }
        root.addView(row2)

        // ---- compresseur de bus (inspiré AUV3 Fac Smash ; ratio 20 = SMASH radical) ----
        var compOn = true
        var compThr = 0.65f; var compRatio = 4f; var compMk = 1.3f
        val compRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        mk2(compRow, "COMP ON") { b ->
            compOn = !compOn
            b.text = if (compOn) "COMP ON" else "COMP OFF"
            DrumsAudio.setComp(compOn, compThr, compRatio, compMk)
        }
        root.addView(compRow)
        val thrVal = TextView(this).apply { setTextColor(Color.rgb(255, 170, 60)); textSize = 12f; text = "THR 0.65" }
        sliderRow("THR", thrVal, 100, 65) { p ->
            compThr = p / 100f; thrVal.text = "THR ${"%.2f".format(compThr)}"
            DrumsAudio.setComp(compOn, compThr, compRatio, compMk)
        }
        val ratioVal = TextView(this).apply { setTextColor(Color.rgb(255, 170, 60)); textSize = 12f; text = "RATIO 4 (20=SMASH)" }
        sliderRow("RATIO", ratioVal, 190, 30) { p ->
            compRatio = 1f + p / 10f; ratioVal.text = "RATIO ${"%.0f".format(compRatio)}${if (compRatio >= 19.9f) " SMASH!" else ""}"
            DrumsAudio.setComp(compOn, compThr, compRatio, compMk)
        }
        val mkVal = TextView(this).apply { setTextColor(Color.rgb(255, 170, 60)); textSize = 12f; text = "MAKEUP 1.30" }
        sliderRow("MAKEUP", mkVal, 200, 130) { p ->
            compMk = p / 100f; mkVal.text = "MAKEUP ${"%.2f".format(compMk)}"
            DrumsAudio.setComp(compOn, compThr, compRatio, compMk)
        }

        // ---- ENV / LFO / SEQ (pack SEQ+, mêmes sémantiques que synthés/basse) ----
        val rowEnv = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        mk2(rowEnv, "ENV=MUT.TIME") { _ -> DrumsAudio.mutate(1); grid.refreshSteps(); grid.invalidate() }
        mk2(rowEnv, "LFO=MUT.PITCH") { _ -> DrumsAudio.mutate(2); refreshParams() }
        mk2(rowEnv, "ENV+LFO=ARTIC") { _ -> DrumsAudio.mutate(3); grid.refreshSteps(); grid.invalidate() }
        var seqMode = 0
        mk2(rowEnv, "SEQ NORM") { b ->
            seqMode = (seqMode + 1) % DrumsAudio.SEQ_MODE_N
            DrumsAudio.seqMode(seqMode)
            b.text = "SEQ ${DrumsAudio.SEQ_MODE_NAMES[seqMode]}"
        }
        root.addView(rowEnv)
        val randVal = TextView(this).apply { setTextColor(Color.rgb(255, 120, 220)); textSize = 12f; text = "RAND 0%" }
        sliderRow("RAND", randVal, 100, 0) { p ->
            randVal.text = "RAND ${p}%"
            DrumsAudio.setRandp(p / 100f)
        }
        // LFO1 global : rate + depth + dst (wave cyclique)
        var lfoWave = 0; var lfoDst = 0
        val lfoR = TextView(this).apply { setTextColor(Color.rgb(0, 200, 255)); textSize = 12f; text = "LFO1 2.0Hz" }
        sliderRow("LFO1 RATE", lfoR, 400, 20) { p ->
            lfoR.text = "LFO1 ${"%.1f".format(p / 10f)}Hz"
            DrumsAudio.setLfo(0, p / 10f, lfoWave, lfoDepth, lfoDst)
        }
        val lfoD = TextView(this).apply { setTextColor(Color.rgb(0, 200, 255)); textSize = 12f; text = "LFO1 DEP 0%" }
        sliderRow("LFO1 DEPTH", lfoD, 100, 0) { p ->
            lfoDepth = p / 100f
            lfoD.text = "LFO1 DEP ${p}%"
            DrumsAudio.setLfo(0, lfoRate, lfoWave, lfoDepth, lfoDst)
        }
        mk2(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }.also { root.addView(it) }, "LFO1 SIN>PITCH") { b ->
            // cycle wave puis dst
            lfoWave = (lfoWave + 1) % DrumsAudio.LFO_WAVE_NAMES.size
            b.text = "LFO1 ${DrumsAudio.LFO_WAVE_NAMES[lfoWave]}>${DrumsAudio.LFO_DST_NAMES[lfoDst]}"
            DrumsAudio.setLfo(0, lfoRate, lfoWave, lfoDepth, lfoDst)
        }

        // ---- mutation / glitch GLOBAUX (façon ZuluFx : acid_mutate + labo CHAOS) ----
        val gmutVal = TextView(this).apply { setTextColor(Color.rgb(255, 120, 220)); textSize = 12f; text = "MUT 0" }
        sliderRow("MUT", gmutVal, 255, 0) { p ->
            gmutVal.text = "MUT $p"
            DrumsAudio.setGmut(p)
        }
        val glitchVal = TextView(this).apply { setTextColor(Color.rgb(255, 120, 220)); textSize = 12f; text = "GLITCH 0%" }
        sliderRow("GLITCH", glitchVal, 100, 0) { p ->
            glitchVal.text = "GLITCH ${p}%"
            DrumsAudio.setGlitch(p / 100f)
        }

        // ---- FX bus globaux : delay (time/fb/wet) + reverb courte (wet) ----
        val dlyT = TextView(this).apply { setTextColor(Color.rgb(120, 160, 255)); textSize = 12f; text = "DLY 0.30s" }
        sliderRow("D.TIME", dlyT, 100, 30) { p ->
            dlyT.text = "DLY ${"%.2f".format(p / 100f)}s"
            DrumsAudio.setDelay(p / 100f, dlyFb, dlyWet)
        }
        val dlyF = TextView(this).apply { setTextColor(Color.rgb(120, 160, 255)); textSize = 12f; text = "D.FB 0.30" }
        sliderRow("D.FB", dlyF, 95, 30) { p ->
            dlyFb = p / 100f; dlyF.text = "D.FB ${"%.2f".format(dlyFb)}"
            DrumsAudio.setDelay(dlyTime, dlyFb, dlyWet)
        }
        val dlyW = TextView(this).apply { setTextColor(Color.rgb(120, 160, 255)); textSize = 12f; text = "D.WET 0.15" }
        sliderRow("D.WET", dlyW, 100, 15) { p ->
            dlyWet = p / 100f; dlyW.text = "D.WET ${"%.2f".format(dlyWet)}"
            DrumsAudio.setDelay(dlyTime, dlyFb, dlyWet)
        }
        val revW = TextView(this).apply { setTextColor(Color.rgb(120, 160, 255)); textSize = 12f; text = "REV 0.12" }
        sliderRow("REV", revW, 100, 12) { p ->
            revWet = p / 100f; revW.text = "REV ${"%.2f".format(revWet)}"
            DrumsAudio.setRev(revWet)
        }

        setContentView(root)
        grid.post { grid.refreshSteps(); refreshParams(); grid.invalidate() }
    }

    private fun mk2(row: LinearLayout, label: String, cb: (Button) -> Unit): Button {
        return Button(this).apply {
            text = label; setTextColor(Color.rgb(10, 10, 16)); setBackgroundColor(Color.rgb(0, 255, 224))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(3, 6, 3, 6) }
            setOnClickListener { cb(this) }
            row.addView(this)
        }
    }

    private fun refreshParams() {
        for (k in 0..3) {
            val kk = paramPage * 4 + k
            val v = DrumsAudio.getParam(grid.selPad, kk)
            paramVals[k]?.text = "${DrumsAudio.PARAM_NAMES[grid.selPad][kk]} ${"%.2f".format(v)}"
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(uiTick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(uiTick)
    }

    override fun onDestroy() {
        if (running) { DrumsAudio.stop(); running = false }
        DrumsAudio.destroy()
        super.onDestroy()
    }
}
