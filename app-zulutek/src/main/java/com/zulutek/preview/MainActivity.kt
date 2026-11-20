/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * ZuluTek preview: LcdView renders the FM-1's 240x240 LCD (display only), SurfaceView is the
 * cloned control surface (7 encoders, 14 buttons, 27 keys F3..G5). Everything drives the
 * native ztek engine through ZtekAudio.
 *
 * Mapping (docs/ZULUTEK.md):
 *  - 16 white keys = the 16 looper slots (tap = play/stop, long = rec arm/stop)
 *  - 11 black keys = playable notes (gate while held)
 *  - encoders: SEL = slot select, PRE = drive type, ALG = WAVE, K1..K4 = CUTOFF/RESO/ENVMOD/DECAY
 *  - buttons:  PLAY REC SEQ GEN TAP SCL R- R+ ERS DRV DLY REV POW MUT */
package com.zulutek.preview

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.LinearLayout
import kotlin.math.roundToInt

class MainActivity : Activity(), SurfaceListener {

    private val st = LcdState()
    private lateinit var lcd: LcdView
    private lateinit var surface: SurfaceView

    private var root = 45
    private var scale = 0
    private var bpm = 120f
    private val tapTimes = LongArray(8)
    private var tapIdx = 0

    /* veille auto (docs/ZULUTEK.md) : 150 s -> traversée 7 s, 180 s -> spectacle 30 s,
     * 210 s -> écran éteint + lampe torche / 30 s ; tout appui réveille */
    private val handler = Handler(Looper.getMainLooper())
    private var lastActivity = System.currentTimeMillis()
    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val idle = now - lastActivity
            val ph = when {
                !st.veilleOn -> 0
                idle < 150_000 -> 0
                idle < 157_000 -> 1
                idle < 210_000 -> 2
                else -> 3
            }
            st.veillePhase = ph
            st.catX = when (ph) {
                1 -> ((idle - 150_000) / 7000f).coerceIn(0f, 1f)
                2 -> {
                    val t = ((idle - 157_000) / 30000f) * 6f   // 3 allers-retours en 30 s
                    val leg = t.toInt()
                    if (leg % 2 == 0) t - leg else 1f - (t - leg)
                }
                else -> 0f
            }
            st.torch = ph == 3 && (now % 30_000) < 3_000
            if (st.smpPage) {
                st.smpLen = ZtekAudio.smpLen()
                ZtekAudio.smpPeaks(st.smpPeaks)
            }
            if (st.page == 1) {   // page LOOP : rafraîchit PITCH/POS/MODE/MUT/SRC/VOL
                for (k in 0 until 6) st.loopParam[k] = ZtekAudio.loopGetSp(st.selSlot, k)
            }
            if (st.page == 6) {   // MIX : niveaux/aux/gain
                st.mixGain = ZtekAudio.mixGetGain()
                for (i in 0 until 16) {
                    st.slotVol[i] = ZtekAudio.loopGetSp(i, ZtekAudio.ML_SP_VOL)
                    st.slotAux[i] = ZtekAudio.loopGetSp(i, 5) > 0.5f   // ML_SP_FX
                }
            }
            if (st.page == 7) {   // PATCH : état des connexions + compatibilité depuis le moteur
                for (r in 0 until 4) for (d in 0 until 3) {
                    st.patchGrid[r * 3 + d] = ZtekAudio.patchGet(r, d) == 1
                    st.patchOk[r * 3 + d] = ZtekAudio.patchCompat(r, d) == 1
                }
            }
            val peers = ZuluLink.peersSnapshot()
            st.linkPeers = peers.count { !it.lost }
            st.linkName = peers.firstOrNull { !it.lost }?.name?.take(6)?.let { ":$it" } ?: ""
            lcd.invalidate()
            handler.postDelayed(this, 250)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        lastActivity = System.currentTimeMillis()
        if (st.veillePhase > 0) {
            st.veillePhase = 0
            st.coucouUntil = lastActivity + 1000
            lcd.invalidate()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ZtekAudio.start()
        ZtekAudio.setEngine(0)
        ZtekAudio.setBpm(bpm)
        ZtekAudio.setRoot(root)
        ZtekAudio.setScale(scale)
        ZtekAudio.setParam(ZtekAudio.AT_CUTOFF, st.encVals[3])
        ZtekAudio.setParam(ZtekAudio.AT_RESONANCE, st.encVals[4])
        ZtekAudio.setParam(ZtekAudio.AT_ENVMOD, st.encVals[5])
        ZtekAudio.setParam(ZtekAudio.AT_DECAY, st.encVals[6])

        lcd = LcdView(this, st)
        surface = SurfaceView(this, st, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(LcdView.BG)
        }
        layout.addView(lcd, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(surface, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        handler.postDelayed(tick, 250)

        /* ZuluLink : l'APK est CONSOLE (surface + horloge). Le FM-1 (module) suit notre
         * tempo ; on reçoit ses CC (74..80 voix/moteur, 20..37 FX, 102..115 boutons). */
        ZuluLink.onMessage = { msg -> handler.post { onZuluMessage(msg) } }
        ZuluLink.onPeersChanged = { handler.post { lcd.invalidate() } }
        ZuluLink.start("zulu-apk", ZuluLink.Role.CONSOLE)

        /* dans l'APK le LCD est tactile : POW maintenu + toucher = circuit bend
         * (bible : REC maintenu + toucher l'écran ; REC sert déjà au looper ici) */
        lcd.setOnTouchListener { _, ev ->
            val s = lcd.width.toFloat() / LcdView.W
            if (s <= 0f) return@setOnTouchListener false
            val bx = ev.x / s / LcdView.W
            val by = ev.y / s / LcdView.H
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (st.page == 7) {          // baie de patch : tap cellule = toggle connexion
                        val r = ((by * LcdView.H - 52f) / 44f).toInt().coerceIn(0, 3)
                        val d = ((bx * LcdView.W - 96f) / 46f).toInt().coerceIn(0, 2)
                        if (by * LcdView.H >= 52f && by * LcdView.H <= 52f + 4 * 44f) {
                            st.patchRow = r; st.patchCol = d
                            togglePatch(r, d)
                        }
                        lcd.invalidate()
                    } else if (st.page == 4) {   // page XY : le LCD tactile devient le pad
                        val now = System.currentTimeMillis()
                        if (now - lastLcdTap < 350) {   // double-tap = HOLD/MOM
                            st.xyHold = !st.xyHold
                            ZtekAudio.xyHold(if (st.xyHold) 1 else 0)
                            lastLcdTap = 0
                        } else lastLcdTap = now
                        st.xyOn = true
                        st.xyX = bx.coerceIn(0f, 1f)
                        st.xyY = by.coerceIn(0f, 1f)
                        ZtekAudio.xySet(1, st.xyX, st.xyY)
                        lcd.invalidate()
                    } else if (powHeld) {
                        st.bendOn = true
                        st.bendX = bx.coerceIn(0f, 1f)
                        st.bendY = by.coerceIn(0f, 1f)
                        ZtekAudio.bend(1, st.bendX, st.bendY, st.bendMode)
                        lcd.invalidate()
                    }
                }
                MotionEvent.ACTION_MOVE -> if (st.page == 4) {
                    st.xyX = bx.coerceIn(0f, 1f)
                    st.xyY = by.coerceIn(0f, 1f)
                    ZtekAudio.xySet(1, st.xyX, st.xyY)
                    lcd.invalidate()
                } else if (powHeld) {
                    st.bendX = bx.coerceIn(0f, 1f)
                    st.bendY = by.coerceIn(0f, 1f)
                    ZtekAudio.bend(1, st.bendX, st.bendY, st.bendMode)
                    lcd.invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (st.page == 4 && st.xyOn) {
                        st.xyOn = false
                        ZtekAudio.xySet(0, st.xyX, st.xyY)
                        lcd.invalidate()
                    } else if (st.bendOn) {
                        st.bendOn = false
                        ZtekAudio.bend(0, 0f, 0f, 0)
                        lcd.invalidate()
                    }
                }
            }
            true
        }
    }

    private var lastLcdTap = 0L

    private var powHeld = false

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        ZuluLink.stop()
        ZtekAudio.stop()
        super.onDestroy()
    }

    /* messages ZuluLink reçus (thread UI via handler.post) */
    private fun onZuluMessage(msg: ZuluLink.RxMessage) {
        when (msg.type) {
            ZuluLink.T_CLOCK -> {
                bpm = msg.tempo.toFloat().coerceIn(30f, 300f)
                ZtekAudio.setBpm(bpm)
                st.bpm = bpm.roundToInt()
            }
            ZuluLink.T_START -> { st.playing = true; ZtekAudio.loopPlay(1) }
            ZuluLink.T_STOP -> { st.playing = false; ZtekAudio.loopPlay(0) }
            ZuluLink.T_TAP -> tapTempo()
            ZuluLink.T_CC -> {
                val v = msg.value / 127f
                when {
                    msg.cc in 74..80 -> {
                        if (msg.cc == 80) {
                            ZtekAudio.setEngine((v * 4.999f).toInt().coerceIn(0, 4))
                            st.engine = (v * 4.999f).toInt().coerceIn(0, 4)
                        } else {
                            ZtekAudio.setParam(msg.cc - 74, v)
                            st.encVals[3 + (msg.cc - 74)] = v
                        }
                    }
                    msg.cc in 20..37 -> ZtekAudio.fxSet(msg.cc - 20, v)
                    msg.cc in 102..115 -> onButton(msg.cc - 102, msg.value > 0)
                }
            }
        }
        lcd.invalidate()
        surface.invalidate()
    }

    // ---- appui long sur un bouton : PLAY bascule la veille auto, SEQ ouvre la page SMP ----
    override fun onButtonLong(i: Int) {
        when (SurfaceView.BTN_LABELS[i]) {
            "PLAY" -> {
                st.veilleOn = !st.veilleOn
                st.veillePhase = 0
                lcd.invalidate()
            }
            "SEQ" -> {
                st.smpPage = !st.smpPage
                if (st.smpPage && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 42)
                }
                lcd.invalidate()
            }
            "POW" -> st.bendMode = (st.bendMode + 1) % 2   // GLITCH / LOW BAT
            "DRV" -> { st.page = if (st.page == 2) 0 else 2; lcd.invalidate() }   // page SNAP
            "MUT" -> { st.page = if (st.page == 3) 0 else 3; lcd.invalidate() }   // page KIT
            "GEN" -> {                                                    // page XY
                st.page = if (st.page == 4) 0 else 4
                ZtekAudio.xyHold(if (st.xyHold) 1 else 0)
                ZtekAudio.xySet(0, 0f, 0f)
                lcd.invalidate()
            }
            "REC" -> when (st.page) { 1, 6, 7 -> { st.page = 0; lcd.invalidate() } else -> ZtekAudio.redo() }
            "DLY" -> {
                val old = st.page
                st.page = when (old) { 0 -> 6; 6 -> 7; 7 -> 6; else -> old }
                if (st.page != old) lcd.invalidate()
            }
            "REV" -> if (st.page == 1) {
                // resample : enregistre les slots actifs dans le slot sélectionné (ring µ-law 32 kHz)
                var mask = 0
                for (i in 0 until 16) if (ZtekAudio.loopSlotOn(i)) mask = mask or (1 shl i)
                ZtekAudio.loopRsStart(st.selSlot, mask, 1, 32)
                st.recArmed = true
            } else { st.page = if (st.page == 5) 0 else 5; lcd.invalidate() }   // page POW!
            "TAP" -> {                                                    // sirène on/off
                st.sirenOn = !st.sirenOn
                ZtekAudio.siren(if (st.sirenOn) 1 else 0, 0.3f, 0.5f, 0.0f)
                lcd.invalidate()
            }
        }
    }

    // ---- encoders ----
    override fun onEncoder(i: Int, v: Float) {
        st.pop(i)
        if (st.page == 4) {                     // page XY : SEL=Xtarget PRE=Ytarget ALG=swap K1=X K2=Y
            when (i) {
                0 -> { xyT[0] = (v * 25.999f).toInt().coerceIn(0, 25); ZtekAudio.xyTarget(0, xyT[0]); st.xyTargetNames[0] = st.targetName(xyT[0]) }
                1 -> { xyT[1] = (v * 25.999f).toInt().coerceIn(0, 25); ZtekAudio.xyTarget(1, xyT[1]); st.xyTargetNames[1] = st.targetName(xyT[1]) }
                2 -> { val t = xyT[0]; xyT[0] = xyT[1]; xyT[1] = t
                       ZtekAudio.xyTarget(0, xyT[0]); ZtekAudio.xyTarget(1, xyT[1])
                       st.xyTargetNames[0] = st.targetName(xyT[0]); st.xyTargetNames[1] = st.targetName(xyT[1]) }
                3 -> { st.xyX = v; if (st.xyOn) ZtekAudio.xySet(1, st.xyX, st.xyY) }
                4 -> { st.xyY = v; if (st.xyOn) ZtekAudio.xySet(1, st.xyX, st.xyY) }
            }
            lcd.invalidate()
            return
        }
        if (st.page == 7) {                     // baie de patch : SEL=rangée(source) ALG=colonne(dest)
            when (i) {
                0 -> st.patchRow = (v * 3.999f).toInt().coerceIn(0, 3)
                2 -> st.patchCol = (v * 2.999f).toInt().coerceIn(0, 2)
            }
            lcd.invalidate()
            return
        }
        if (st.page == 6) {                     // MIX : SEL=slot K1=vol PRE=aux>POW ALG=gain bus
            when (i) {
                0 -> st.selSlot = (v * 15.999f).toInt().coerceIn(0, 15)
                3 -> { st.slotVol[st.selSlot] = v; ZtekAudio.mixSetVol(st.selSlot, v) }
                1 -> { val on = if (v > 0.5f) 1 else 0; st.slotAux[st.selSlot] = on == 1; ZtekAudio.mixSetAux(st.selSlot, on) }
                2 -> { st.mixGain = v * 2f; ZtekAudio.mixSetGain(st.mixGain) }
            }
            lcd.invalidate()
            return
        }
        if (st.page == 5) {                     // page POW! : K1=type K2=a K3=b K4=mix PRE=slot
            when (i) {
                1 -> st.selSlot = (v * 15.999f).toInt().coerceIn(0, 15)
                3 -> st.punchType = (v * 14.999f).toInt().coerceIn(0, 14)
                4 -> st.punchA = v
                5 -> st.punchB = v
                6 -> st.punchMix = v
            }
            lcd.invalidate()
            return
        }
        if (st.page == 1) {                     // page LOOP : SEL=slot K1..K4=PITCH/POS/MODE/MUT PRE=VOL ALG=SRC
            when (i) {
                0 -> st.selSlot = (v * 15.999f).toInt().coerceIn(0, 15)
                3 -> ZtekAudio.loopSetSp(st.selSlot, 0, v)   // PITCH (v*24-12 côté affichage)
                4 -> ZtekAudio.loopSetSp(st.selSlot, 1, v)   // POS
                5 -> ZtekAudio.loopSetSp(st.selSlot, 2, (v * 2.999f).toInt().toFloat())  // MODE
                6 -> ZtekAudio.loopSetSp(st.selSlot, 3, v)   // MUT
                1 -> ZtekAudio.loopSetSp(st.selSlot, 6, v)   // VOL
                2 -> ZtekAudio.loopSetSp(st.selSlot, 4, (v * 6.999f).toInt().toFloat())  // SRC
            }
            lcd.invalidate()
            return
        }
        if (st.smpPage) {
            // page SMP : SEL = mode de stretch, K1..K4 = START/END/VOL/PITCH
            when (i) {
                0 -> { st.smpMode = (v * 3.999f).toInt().coerceIn(0, 3); ZtekAudio.smpSet(4, v) }
                3 -> ZtekAudio.smpSet(0, v)   // START
                4 -> ZtekAudio.smpSet(1, v)   // END
                5 -> ZtekAudio.smpSet(2, v)   // VOL
                6 -> ZtekAudio.smpSet(3, v)   // PITCH
            }
            lcd.invalidate()
            return
        }
        when (i) {
            0 -> st.selSlot = (v * 15.999f).toInt().coerceIn(0, 15)
            1 -> {
                val t = (v * 4.999f).toInt().coerceIn(0, 4)
                if (t != st.driveType) {
                    st.driveType = t
                    ZtekAudio.fxSet(ZtekAudio.FX_DRIVE_TYPE, t.toFloat())
                    ZtekAudio.fxSet(ZtekAudio.FX_DRIVE, 0.6f)
                }
            }
            2 -> ZtekAudio.setParam(ZtekAudio.AT_WAVE, v)
            3 -> ZtekAudio.setParam(ZtekAudio.AT_CUTOFF, v)
            4 -> ZtekAudio.setParam(ZtekAudio.AT_RESONANCE, v)
            5 -> ZtekAudio.setParam(ZtekAudio.AT_ENVMOD, v)
            6 -> ZtekAudio.setParam(ZtekAudio.AT_DECAY, v)
        }
        lcd.invalidate()
    }

    // ---- 14 buttons ----
    private var lastRecTap = 0L
    private var lastDrvTap = 0L
    private var lastPlayTap = 0L
    private var lastSlotTap = -1
    private var lastSlotTapTime = 0L
    private var stLoopLen = 0
    private val xyT = intArrayOf(1, 2)   // cibles X/Y de la page XY

    override fun onButton(i: Int, down: Boolean) {
        val label = SurfaceView.BTN_LABELS[i]
        if (!down) {
            // seul POW a un comportement au relâché (punch off + bend off)
            if (label == "POW" && st.page == 0 && !st.smpPage) {
                powHeld = false
                ZtekAudio.punch(0, 0, 0f, 0f, 0f)
                ZtekAudio.punchTail(0.3f)
                if (st.bendOn) { st.bendOn = false; ZtekAudio.bend(0, 0f, 0f, 0); lcd.invalidate() }
            }
            return
        }
        // pages SNAP / KIT / LOOP / XY : les boutons gardent des rôles de page
        when (st.page) {
            2, 3 -> { // SNAP / KIT : REC = undo, ERS = efface le slot sélectionné
                if (label == "REC") { ZtekAudio.undo(); return }
                if (label == "ERS") {
                    if (st.page == 2) st.snapFilled[st.selSlot] = false else st.kitFilled[st.selSlot] = false
                    lcd.invalidate(); return
                }
                if (label == "PLAY" && st.page == 2) { ZtekAudio.snapStore(st.selSlot); st.snapFilled[st.selSlot] = true; lcd.invalidate(); return }
                return
            }
            1 -> { // LOOP
                when (label) {
                    "REC" -> {
                        val s = st.selSlot
                        if (ZtekAudio.loopRsRec()) { ZtekAudio.loopRsStop(); st.recArmed = false }
                        else if (ZtekAudio.loopSlotRec(s)) { ZtekAudio.loopRecStop(s); st.recArmed = false }
                        else { ZtekAudio.loopRecStart(s, 1); st.recArmed = true }
                    }
                    "ERS" -> ZtekAudio.loopErase(st.selSlot)
                    "DLY" -> { // cycle la longueur prédéfinie : auto, 1, 2, 4, 8 mesures
                        val lens = intArrayOf(0, 64, 128, 256, 512)
                        val next = (lens.indexOf(stLoopLen).let { if (it < 0) 0 else it } + 1) % lens.size
                        stLoopLen = lens[next]
                        ZtekAudio.loopSetLen(st.selSlot, stLoopLen)
                    }
                    "REV" -> { // cycle le mode de lecture : normal/reverse/ping-pong
                        val m = (ZtekAudio.loopGetSp(st.selSlot, ZtekAudio.ML_SP_MODE).toInt() + 1) % 3
                        ZtekAudio.loopSetSp(st.selSlot, ZtekAudio.ML_SP_MODE, m.toFloat())
                    }
                }
                syncSlots(); lcd.invalidate(); return
            }
            4 -> { // XY : PLAY = hold/mom
                if (label == "PLAY") {
                    st.xyHold = !st.xyHold
                    ZtekAudio.xyHold(if (st.xyHold) 1 else 0)
                    lcd.invalidate()
                }
                return
            }
            5 -> { // POW! : REC = quitter
                if (label == "REC") { st.page = 0; ZtekAudio.punch(0, 0, 0f, 0f, 0f); lcd.invalidate() }
                return
            }
            6, 7 -> { // MIX / PATCH : PLAY bascule la connexion au curseur (page 7)
                if (label == "PLAY" && st.page == 7) togglePatch(st.patchRow, st.patchCol)
                return
            }
        }
        // double-taps en page MAIN : REC = undo, DRV = page SNAP
        if (st.page == 0) {
            val now = System.currentTimeMillis()
            if (label == "REC" && now - lastRecTap < 350) { ZtekAudio.undo(); lastRecTap = 0; return }
            if (label == "DRV" && now - lastDrvTap < 350) { st.page = 2; lastDrvTap = 0; lcd.invalidate(); return }
            if (label == "PLAY" && now - lastPlayTap < 350) {   // REC+PLAY (dbl-tap PLAY) = capture SNAP + flash
                ZtekAudio.snapStore(st.selSlot)
                st.snapFilled[st.selSlot] = true
                st.flashUntil = now + 120
                lastPlayTap = 0
                lcd.invalidate()
                return
            }
            if (label == "REC") lastRecTap = now
            if (label == "DRV") lastDrvTap = now
            if (label == "PLAY") lastPlayTap = now
        }
        if (st.smpPage) {
            when (SurfaceView.BTN_LABELS[i]) {
                "REC" -> if (down) {
                    st.smpRecOn = !st.smpRecOn
                    ZtekAudio.smpRec(if (st.smpRecOn) 1 else 0)
                }
                "PLAY" -> if (down) ZtekAudio.smpPlay(1)
                "ERS" -> if (down) { st.smpRecOn = false; ZtekAudio.smpRec(0) }
            }
            lcd.invalidate()
            return
        }
        when (SurfaceView.BTN_LABELS[i]) {
            "PLAY" -> if (down) {
                st.playing = !st.playing
                ZtekAudio.loopPlay(if (st.playing) 1 else 0)
                if (st.playing) ZuluLink.sendStart("TOUS") else ZuluLink.sendStop("TOUS")
            }
            "REC" -> if (down) {
                val s = st.selSlot
                if (ZtekAudio.loopSlotRec(s)) { ZtekAudio.loopRecStop(s); st.recArmed = false }
                else { ZtekAudio.loopRecStart(s, 1); st.recArmed = true }
                st.pop(9)
            }
            "SEQ" -> if (down) {
                st.seqOn = !st.seqOn
                ZtekAudio.acidPlay(if (st.seqOn) 1 else 0)
            }
            "GEN" -> if (down) ZtekAudio.acidGenerate((Math.random() * 1e9).toInt())
            "TAP" -> if (down) tapTempo()
            "SCL" -> if (down) {
                scale = (scale + 1) % 8
                ZtekAudio.setScale(scale)
                ZtekAudio.acidGenerate((Math.random() * 1e9).toInt())
            }
            "R-" -> if (down) { root = (root - 1).coerceIn(24, 72); ZtekAudio.setRoot(root) }
            "R+" -> if (down) { root = (root + 1).coerceIn(24, 72); ZtekAudio.setRoot(root) }
            "ERS" -> if (down) { ZtekAudio.loopErase(st.selSlot); st.recArmed = false }
            "DRV" -> if (down) {
                st.driveType = (st.driveType + 1) % 5
                ZtekAudio.fxSet(ZtekAudio.FX_DRIVE_TYPE, st.driveType.toFloat())
                ZtekAudio.fxSet(ZtekAudio.FX_DRIVE, 0.6f)
                st.encVals[1] = st.driveType / 4f
            }
            "DLY" -> if (down) {
                st.dly = ((st.dly + 0.25f) % 1.0f)
                ZtekAudio.fxSet(ZtekAudio.FX_DLY_MIX, st.dly)
            }
            "REV" -> if (down) {
                st.rev = ((st.rev + 0.25f) % 1.0f)
                ZtekAudio.fxSet(ZtekAudio.FX_REV_MIX, st.rev)
            }
            "POW" -> {
                powHeld = down
                if (down) ZtekAudio.punch(1, 8, 0.5f, 0.5f, 0.8f)
                else {
                    ZtekAudio.punch(0, 0, 0f, 0f, 0f); ZtekAudio.punchTail(0.3f)
                    if (st.bendOn) { st.bendOn = false; ZtekAudio.bend(0, 0f, 0f, 0); lcd.invalidate() }
                }
            }
            "MUT" -> if (down) { ZtekAudio.allNotesOff(); st.curNote = -1 }
        }
        syncSlots()
        lcd.invalidate()
        surface.invalidate()
    }

    // ---- white keys = looper slots (SNAP/KIT : load/store ; SMP : PLAY) ----
    override fun onWhiteKey(slot: Int, down: Boolean, longPress: Boolean) {
        if (st.smpPage && down && !longPress) { ZtekAudio.smpPlay(1); return }
        st.selSlot = slot
        if (st.page == 5) {                     // PoW! momentané tant que la touche est maintenue
            if (down) ZtekAudio.punch(1, st.punchType, st.punchA, st.punchB, st.punchMix)
            else { ZtekAudio.punch(0, 0, 0f, 0f, 0f); ZtekAudio.punchTail(0.3f) }
            return
        }
        if (st.page == 2 || st.page == 3) {
            val now = System.currentTimeMillis()
            if (longPress) {
                if (st.page == 2) { ZtekAudio.snapStore(slot); st.snapFilled[slot] = true }
                else { ZtekAudio.kitStore(slot); st.kitFilled[slot] = true }
            } else if (down) {
                if (st.page == 3 && slot == lastSlotTap && now - lastSlotTapTime < 350) {
                    // double-tap = copie vers le slot miroir (xor 8), comme les snaps ZU/LU
                    ZtekAudio.kitStore(slot xor 8); st.kitFilled[slot xor 8] = st.kitFilled[slot]
                    lastSlotTap = -1
                } else {
                    if (st.page == 2) ZtekAudio.snapLoad(slot) else ZtekAudio.kitLoad(slot)
                    lastSlotTap = slot; lastSlotTapTime = now
                }
            }
            lcd.invalidate()
            return
        }
        if (longPress) {
            if (ZtekAudio.loopSlotRec(slot)) { ZtekAudio.loopRecStop(slot); st.recArmed = false }
            else { ZtekAudio.loopRecStart(slot, 1); st.recArmed = true }
        } else if (down) {
            if (ZtekAudio.loopSlotRec(slot)) ZtekAudio.loopRecStop(slot)
            else ZtekAudio.loopToggle(slot)
            if (st.recArmed && !ZtekAudio.loopSlotRec(slot)) st.recArmed = false
        }
        syncSlots()
        lcd.invalidate()
        surface.invalidate()
    }

    // ---- black keys = notes ----
    override fun onBlackKey(note: Int, down: Boolean) {
        if (down) {
            st.curNote = note
            ZtekAudio.noteOn(note, 100, 0, 0)
        } else {
            ZtekAudio.noteOff(note)
            if (st.curNote == note) st.curNote = -1
        }
        lcd.invalidate()
        surface.invalidate()
    }

    private fun tapTempo() {
        val now = System.currentTimeMillis()
        tapTimes[tapIdx % 8] = now
        tapIdx++
        if (tapIdx >= 4) {
            val n = 8.coerceAtMost(tapIdx)
            var span = 0L
            for (i in 1 until n) span += tapTimes[(tapIdx - i) % 8] - tapTimes[(tapIdx - i - 1) % 8]
            if (span > 0) {
                bpm = (60000f * (n - 1) / span).coerceIn(30f, 300f)
                ZtekAudio.setBpm(bpm)
                st.bpm = bpm.roundToInt()
                ZuluLink.sendClock(bpm.toDouble(), 0.0, System.currentTimeMillis())
            }
        }
        st.pop(9)
        lcd.invalidate()
    }

    /* bascule la connexion source r -> destination d dans la baie de patch
     * (les connexions incompatibles sont estompées et ignorées, façon VCV Rack/miRack) */
    private fun togglePatch(r: Int, d: Int) {
        if (ZtekAudio.patchCompat(r, d) == 0) return
        val on = 1 - ZtekAudio.patchGet(r, d)
        ZtekAudio.patchSet(r, d, on)
        st.patchGrid[r * 3 + d] = on == 1
        st.pop(9)
    }

    private fun syncSlots() {
        for (i in 0 until 16) {
            st.slotOn[i] = ZtekAudio.loopSlotOn(i)
            st.slotRec[i] = ZtekAudio.loopSlotRec(i)
        }
    }
}
