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
 * ZuluTek Drums — drum machine analogique 8 pads + seq 16 steps (moteur natif zuludrums).
 * UI : grille pads/steps, BPM, swing, niveau du pad sélectionné, master, STEP (édition),
 * CLEAR, RAND. Style sombre ZuluTek.
 */
class DrumsActivity : Activity() {

    private lateinit var grid: DrumGridView
    private lateinit var bpmVal: TextView
    private lateinit var lvlVal: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

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
            setPadding(16, 24, 16, 16)
        }

        val title = TextView(this).apply {
            text = "ZuluTek DRUMS — 8 pads analog + seq 16"
            setTextColor(Color.rgb(0, 255, 224))
            textSize = 16f
        }
        root.addView(title)

        grid = DrumGridView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            onPadTap = { pad -> DrumsAudio.hit(pad) }
            onStepTap = { step ->
                val on = !DrumsAudio.getStep(selPad, step)
                DrumsAudio.setStep(selPad, step, on)
                pat[selPad][step] = on
                invalidate()
            }
        }
        root.addView(grid)

        // ---- ligne de contrôles ----
        fun row(label: String, tv: TextView): LinearLayout {
            val l = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val lt = TextView(this).apply {
                text = label; setTextColor(Color.rgb(235, 238, 245)); textSize = 13f
                layoutParams = LinearLayout.LayoutParams(90, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            l.addView(lt); l.addView(tv)
            return l
        }

        bpmVal = TextView(this).apply { setTextColor(Color.rgb(0, 255, 224)); textSize = 13f }
        val bpmBar = SeekBar(this).apply {
            max = 280; progress = 120 - 60
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val bpm = p + 60f
                    bpmVal.text = "${bpm.toInt()} BPM"
                    DrumsAudio.setBpm(bpm)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        root.addView(row("TEMPO", bpmVal))
        root.addView(bpmBar)
        bpmVal.text = "120 BPM"

        lvlVal = TextView(this).apply { setTextColor(Color.rgb(0, 255, 224)); textSize = 13f }
        val lvlBar = SeekBar(this).apply {
            max = 100; progress = 90
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    lvlVal.text = "${DrumsAudio.PAD_NAMES[grid.selPad]} ${(p / 100f).let { "%.2f".format(it) }}"
                    DrumsAudio.setLevel(grid.selPad, p / 100f)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        root.addView(row("LEVEL", lvlVal))
        root.addView(lvlBar)
        lvlVal.text = "KICK 0.90"

        val swingVal = TextView(this).apply { setTextColor(Color.rgb(0, 255, 224)); textSize = 13f }
        val swingBar = SeekBar(this).apply {
            max = 50; progress = 0
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    swingVal.text = "SWING ${p}%"
                    DrumsAudio.setSwing(p / 100f)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        root.addView(row("SWING", swingVal))
        root.addView(swingBar)
        swingVal.text = "SWING 0%"

        // ---- boutons ----
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun mk(label: String, cb: () -> Unit): Button {
            val b = Button(this).apply {
                text = label
                setTextColor(Color.rgb(10, 10, 16))
                setBackgroundColor(Color.rgb(0, 255, 224))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(4, 8, 4, 8)
                }
                setOnClickListener { cb() }
            }
            btns.addView(b)
            return b
        }
        val stepBtn = mk("STEP") {
            grid.editMode = !grid.editMode
            stepBtn.setBackgroundColor(if (grid.editMode) Color.rgb(255, 238, 0) else Color.rgb(0, 255, 224))
            grid.invalidate()
        }
        mk("CLEAR") { DrumsAudio.clear(grid.selPad); grid.refreshPat(); grid.invalidate() }
        mk("RAND") { DrumsAudio.rand(grid.selPad, 0.4f, (System.nanoTime() shr 8).toInt()); grid.refreshPat(); grid.invalidate() }
        mk("RESET") { DrumsAudio.reset() }
        val playBtn = mk(if (running) "STOP" else "PLAY") {
            running = !running
            if (running) DrumsAudio.start() else DrumsAudio.stop()
            playBtn.text = if (running) "STOP" else "PLAY"
        }
        root.addView(btns)

        setContentView(root)
        grid.post { grid.refreshPat(); grid.invalidate() }
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
