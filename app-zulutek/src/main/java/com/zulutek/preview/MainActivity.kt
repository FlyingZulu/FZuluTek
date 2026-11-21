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
import android.content.Intent
import android.net.Uri
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
    private lateinit var rootLayout: android.widget.FrameLayout

    private var root = 45
    private var scale = 0
    private var bpm = 120f
    private val tapTimes = LongArray(8)
    private var tapIdx = 0

    /* pack SEQ+ (PR #3) */
    private var presetIdx = 0
    private var lastEncPreset = -1
    private var lastSeqTap = 0L
    private var lastGloTap = 0L

    private fun forbidden(sug: String) {
        if (!st.ludiqueOn) return
        st.noEntryUntil = System.currentTimeMillis() + 1200
        st.sugMsg = sug
        lcd.invalidate()
    }
    private fun hint(msg: String) {
        if (!st.ludiqueOn) return
        st.hintUntil = System.currentTimeMillis() + 1600
        st.hintMsg = msg
        lcd.invalidate()
    }

    /* panneau pop d'un paramètre touché (charte UI) : remplit popVal/popState affichés par
     * LcdView — valeur en grand au centre, ou la cible quand l'encodeur sert à la navigation
     * (SLOT n / OPT x / TAB x / BNK). État : S = SNAP armé, L = page LFO, E = page ENV. */
    private fun popPanelInfo(i: Int, v: Float) {
        if (i !in 0..6) { st.popVal = ""; return }
        st.popVal = when {
            (st.page == 0 || st.page == 1 || st.page == 2 || st.page == 3 || st.page == 15) && i == 0 ->
                "SLOT ${st.selSlot + 1}"
            st.page == 16 && i == 0 ->
                "OPT " + arrayOf("ECO", "LABELS", "VUE", "LUXE", "SKIN")[st.optRow.coerceIn(0, 4)]
            st.page == 17 && i == 0 -> "LIGNE ${st.linkSel + 1}"
            st.page == 17 && i == 1 ->
                "TAB " + arrayOf("FM-1", "LOCAL", "PARAMS", "PRESETS")[st.linkTab.coerceIn(0, 3)]
            st.page == 18 && i == 0 ->
                "BNK ${st.fineBank + 1}/${ZtekAudio.FINE_BANKS.size}"
            else -> "${(v * 100).toInt()}"
        }
        st.popState = buildString {
            if (st.snapArm) append('S')
            if (st.page == 7) append('L')
            if (st.page == 8) append('E')
        }
    }
    private fun srcNamesUpTo(maxExclusive: Int): String {
        val sb = StringBuilder()
        for (i in 0 until maxExclusive.coerceAtMost(ZtekAudio.ZT_NSRC)) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(ZtekAudio.SRC_NAMES[i])
        }
        return sb.toString()
    }
    private fun dstHint(dst: Int, src: Int): String {
        val srcName = if (src in 0 until ZtekAudio.ZT_NSRC) ZtekAudio.SRC_NAMES[src] else "SRC?"
        return when {
            dst < 8 -> srcName + " -> VOIX: ex LFO1->CUTOFF, ENV1->DECAY"
            dst < ZtekAudio.ZT_SEQ_DST0 -> srcName + " -> FX: ex LFO2->DLY, LABO->REV"
            dst < ZtekAudio.ZT_NDST -> srcName + " -> SEQ: ex LFO->TEMPO, ENV->STEPS, LFO->TM"
            else -> srcName + " -> MACRO" + (dst - ZtekAudio.ZT_NDST + 1) + " (mix du macro)"
        }
    }
    private fun setEncLabels(labels: Array<String>) { st.encLabels = labels }

    /* page FINE (18) — 4 knobs façon Shruthi, idem firmware patch 0012 (page 14 FM-1) */
    private fun syncFineBank() {
        val b = st.fineBank.coerceIn(0, ZtekAudio.FINE_BANKS.size - 1)
        st.fineBank = b
        val opts = ZtekAudio.FINE_BANK_OPT[b]
        setEncLabels(arrayOf("BNK", "PRE", "ALG",
            ZtekAudio.FINE_OPT_NAMES[opts[0]], ZtekAudio.FINE_OPT_NAMES[opts[1]],
            ZtekAudio.FINE_OPT_NAMES[opts[2]], ZtekAudio.FINE_OPT_NAMES[opts[3]]))
        st.encVals[0] = b / (ZtekAudio.FINE_BANKS.size - 1).toFloat()
        for (k in 0..3) {
            val idx = opts[k]
            val v = ZtekAudio.optionGet(idx)
            st.fineVals[idx] = v
            st.encVals[3 + k] = if (idx == ZtekAudio.FINE_OPT_MORPH) v / 255f
                                else v.toFloat() / (ZtekAudio.fineOptCount(idx) - 1).coerceAtLeast(1)
        }
    }
    private fun enterFine() { syncFineBank() }

    /* page MACRO : resynchronise slot/dst/sub/val depuis le moteur */
    private fun syncMacroSel() {
        if (ZtekAudio.macroUsed(st.macSlot) == 1) {
            st.macDst = ZtekAudio.macroDst(st.macSlot)
            st.macSub = ZtekAudio.macroSub(st.macSlot)
            st.macVals[st.macSlot] = ZtekAudio.macroVal(st.macSlot)
        }
        for (m in 0 until ZtekAudio.ZT_NMACRO) st.macUsed[m] = ZtekAudio.macroUsed(m) == 1
    }

    /* veille auto (docs/ZULUTEK.md) : 150 s -> traversée 7 s, 180 s -> spectacle 30 s,
     * 210 s -> écran éteint + lampe torche / 30 s ; tout appui réveille */
    private val handler = Handler(Looper.getMainLooper())
    private var lastActivity = System.currentTimeMillis()
    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val idle = now - lastActivity
            if (st.page != prevPage) {
                prevPage = st.page
                selHeld = false
                st.psSaveArm = false
                st.psRename = false
                if (sirenModePersistent) {
                    sirenModePersistent = false
                    st.sirenOn = false
                    ZtekAudio.siren(0, st.sirenLvl, st.sirenRate, st.sirenFb)
                }
            }
            st.sirenMode = sirenModePersistent
            if (st.snapArm && now > st.snapArmUntil) st.snapArm = false
            if (st.loopRecArm == -2 && now > st.loopRecWinUntil) st.loopRecArm = -1
            updateViewCompact()   // OPTIONS > VUE = AUTO : vue compacte selon état du lien
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
            if (st.page in 6..10) {
                for (i in 0 until ZtekAudio.ZT_NSRC) st.srcVals[i] = ZtekAudio.srcVal(i)
                for (k in 0 until 5) st.seqkVals[k] = ZtekAudio.seqKnobGet(5 + k)
                if (st.page == 9) {
                    st.asgSrc = ZtekAudio.assignSrc(st.sub)
                    st.asgDepth = ZtekAudio.assignDepth(st.sub)
                }
                if (st.page == 10) {
                    val bn = ZtekAudio.bankName(st.psPart, st.psSlot)
                    st.psUsed = bn.isNotEmpty() || ZtekAudio.presetUsed(st.psPart, st.psSlot)
                    st.psName = if (bn.isNotEmpty()) bn else
                        (if (ZtekAudio.presetUsed(st.psPart, st.psSlot)) ZtekAudio.presetSlotName(st.psPart, st.psSlot) else "")
                }
            }
            if (st.page == 15) {   // page DRUMS : grille 16 steps + step courant du séquenceur
                for (p in 0 until ZtekAudio.DR_NPADS)
                    for (s2 in 0 until 16) st.drumPat[p * 16 + s2] = ZtekAudio.drumGetPat(p, s2) == 1
                st.drumStep = ZtekAudio.drumStep()
            }
            if (st.page == 1 || st.page == 15 || st.page == 16) {   // choke des séquenceurs (LOOP + DRUMS) + OPTIONS
                for (i in 0 until 16) st.loopChoke[i] = ZtekAudio.loopGetChoke(i)
                st.loopLeader = ZtekAudio.loopGetLeader()
                for (p in 0 until ZtekAudio.DR_NPADS) st.drumChoke[p] = ZtekAudio.drumGetChoke(p)
                st.drumLeader = ZtekAudio.drumGetLeader()
                java.util.Arrays.fill(st.loopMaster, false)
                java.util.Arrays.fill(st.drumMaster, false)
                for (g in 1..6) {
                    val ml = ZtekAudio.loopChokeMaster(g)
                    if (ml >= 0) st.loopMaster[ml] = true
                    val md = ZtekAudio.drumChokeMaster(g)
                    if (md >= 0) st.drumMaster[md] = true
                }
            }
            if (st.chokeWin && now > st.chokeWinUntil) {   // 1,5 s sans action = la fenêtre choke se ferme
                chokeWinClose()
                lcd.invalidate()
            }
            if (st.page == 11) {   // MIX 4 tranches : rafraîchit l'état des tranches + câbles baie + VU master
                st.mixGain = ZtekAudio.mixGetGain()
                st.masterPeak = ZtekAudio.masterPeak()
                val words = Array(ZtekAudio.NSRC_ACTIVE) { ZtekAudio.assignWords(ZtekAudio.SRC_IDX[it]) }
                // départs aux libres du master (AUX1/AUX2, ports OUT 158/159) : nb de câbles
                for (a in 0..1) {
                    var n = 0
                    for (k in words.indices) if (ZtekAudio.assignBit(words[k], ZtekAudio.MOD_OFF[6] + 3 + a)) n++
                    st.auxN[a] = n
                }
                for (s in 0 until 4) {
                    st.stripCut[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_CUT)
                    st.stripFmode[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_FMODE).toInt().coerceIn(0, 2)
                    st.stripVol[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_VOL)
                    st.stripPan[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_PAN)
                    st.stripMut[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_MUT) > 0.5f
                    st.stripSolo[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_SOLO) > 0.5f
                    st.stripLdr[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_LDR) > 0.5f
                    st.stripAuxPow[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_AUX_POW)
                    st.stripAuxLoop[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_AUX_LOOP)
                    st.stripAux1[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_AUX1)
                    st.stripAux2[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_AUX2)
                    st.stripAux3[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_AUX3)
                    st.stripAux4[s] = ZtekAudio.mixGetStrip(s, ZtekAudio.MIX_AUX4)
                }
                st.masterAux[0] = ZtekAudio.mixGetMaster(ZtekAudio.MIXM_AUX1)
                st.masterAux[1] = ZtekAudio.mixGetMaster(ZtekAudio.MIXM_AUX2)
                // appui long OP5+Shift (sans voie) = sous-menu master
                if (op5Held && shiftHeld && !op5ShiftFired &&
                    System.currentTimeMillis() - op5ShiftAt > 600) {
                    op5ShiftFired = true
                    st.mixSub = 4
                    hint("MASTER")
                }
            }
            if (st.page == 18) {   // page FINE : rafraîchit les valeurs depuis le moteur
                for (i in 0 until ZtekAudio.FINE_NOPT) st.fineVals[i] = ZtekAudio.optionGet(i)
            }
            if (st.page == 5) {   // menu FX : rafraîchit sirène + pedalboard depuis le moteur
                st.sirenWave = ZtekAudio.sirenWaveGet()
                st.sirenMix = ZtekAudio.sirenMixGet()
                if (st.fxView == 0) {
                    for (s in 0 until 12) {
                        st.fxSlotType[s] = ZtekAudio.fxslotType(s)
                        for (k in 0..3) st.fxSlotP[s][k] = ZtekAudio.fxslotPar(s, k)
                        st.fxSlotMix[s] = ZtekAudio.fxslotMix(s)
                    }
                    for (c in 0..3) st.fxChainMix[c] = ZtekAudio.fxchainMixGet(c)
                    ZtekAudio.fxchainOrderGet().copyInto(st.fxChainOrder)
                }
            }
            if (st.page == 17 && tickN % 8 == 0) { linkRefreshLocal(); linkRefreshPresets() }   // page LINK : samples locaux + banc presets
            if (st.page == 12) {   // PATCH v3 : rebâtit toutes les vues depuis les bitsets du moteur
                val idx = ZtekAudio.SRC_IDX
                val words = Array(idx.size) { ZtekAudio.assignWords(idx[it]) }
                fun bit(k: Int, d: Int) = ZtekAudio.assignBit(words[k], d)   // k = position dans SRC_IDX
                val srcPos = idx.indexOf(st.srcBox).coerceAtLeast(0)
                for (k in idx.indices) {
                    var n = 0
                    for (d in 0 until ZtekAudio.NDST) if (bit(k, d)) n++
                    st.srcOut[idx[k]] = n
                }
                for (m in 0 until ZtekAudio.NMOD) {
                    var n = 0
                    for (d in ZtekAudio.MOD_OFF[m] until ZtekAudio.MOD_OFF[m + 1]) {
                        for (k in idx.indices) if (bit(k, d)) { n++; break }
                    }
                    st.modIn[m] = n
                }
                st.inList.clear(); st.inOk.clear(); st.inOn.clear()
                for (m in 0 until ZtekAudio.NMOD) {
                    st.inList.add(-1 - m); st.inOk.add(true); st.inOn.add(st.modIn[m] > 0)
                    if (st.expanded[m] || st.modIn[m] > 0)   // module replié = un seul slot
                        for (d in ZtekAudio.MOD_OFF[m] until ZtekAudio.MOD_OFF[m + 1]) {
                            st.inList.add(d)
                            st.inOk.add(ZtekAudio.patchCompat(st.srcBox, d) == 1)
                            st.inOn.add(bit(srcPos, d))
                        }
                }
                if (st.inList.isNotEmpty()) st.inSel = st.inSel.coerceIn(0, st.inList.size - 1)
                st.srcCables.clear()
                for (d in 0 until ZtekAudio.NDST) if (bit(srcPos, d)) st.srcCables.add(d)
                st.modRows.clear()
                for (d in ZtekAudio.MOD_OFF[st.modBox] until ZtekAudio.MOD_OFF[st.modBox + 1])
                    for (k in idx.indices)
                        if (bit(k, d)) st.modRows.add(idx[k] to d)
                st.flatRows.clear()
                for (k in idx.indices)
                    for (d in 0 until ZtekAudio.NDST) if (bit(k, d)) st.flatRows.add(idx[k] to d)
                st.rowSel = st.rowSel.coerceIn(0, ((if (st.patchView == 2) st.modRows.size else st.flatRows.size) - 1).coerceAtLeast(0))
                st.listScroll = st.listScroll.coerceIn(0, (st.srcCables.size - 5).coerceAtLeast(0))
            }
            val peers = ZuluLink.peersSnapshot()
            st.linkPeers = peers.count { !it.lost }
            st.linkName = peers.firstOrNull { !it.lost }?.name?.take(6)?.let { ":$it" } ?: ""
            if (!st.ecoOn || ++tickN % 4 == 0) lcd.invalidate()   // OPTIONS > ECO : LCD throttlé (~1 s)
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
        ZtekAudio.initPrefs(this)
        // OPTIONS persistées (OBJ_OPTIONS côté FM-1) : ECO uniquement
        // (CHOKE LDR est supprimé d'OPTIONS : remplacé par le LDR par tranche, page MIX)
        st.ecoOn = optsPrefs.getBoolean("eco", false)
        st.labelsZulu = optsPrefs.getBoolean("labels_zulu", false)
        st.viewMode = optsPrefs.getInt("vue", 0).coerceIn(0, 2)
        st.luxeOn = optsPrefs.getBoolean("luxe", false)
        st.skinMaison = optsPrefs.getBoolean("skin_maison", false)
        ZtekAudio.setEco(if (st.ecoOn) 1 else 0)
        ZtekAudio.setLuxe(if (st.luxeOn) 1 else 0)
        ZtekAudio.setEngine(0)
        // page FINE (18) : options persistées (équivalent OBJ_OPTIONS côté FM-1, patch 0012)
        for (i in 0 until ZtekAudio.FINE_NOPT) {
            val fv = optsPrefs.getInt("fine_$i", -1)
            if (fv >= 0) ZtekAudio.setOption(i, fv)
            st.fineVals[i] = ZtekAudio.optionGet(i)
        }
        ZtekAudio.setBpm(bpm)
        ZtekAudio.setRoot(root)
        ZtekAudio.setScale(scale)
        ZtekAudio.setParam(ZtekAudio.AT_CUTOFF, st.encVals[3])
        ZtekAudio.setParam(ZtekAudio.AT_RESONANCE, st.encVals[4])
        ZtekAudio.setParam(ZtekAudio.AT_ENVMOD, st.encVals[5])
        ZtekAudio.setParam(ZtekAudio.AT_DECAY, st.encVals[6])

        lcd = LcdView(this, st)
        surface = SurfaceView(this, st, this)

        /* Le LCD vit dans l'emplacement dedie de la facade : SurfaceView dessine le bezel
         * (352,24,516x516 ; ecran utile 370,42,480x480 en coordonnees panneau 1280x820).
         * Facade plein ecran, LCD pose dessus, cale apres chaque layout via placeLcdInBezel(). */
        rootLayout = android.widget.FrameLayout(this).apply {
            setBackgroundColor(LcdView.BG)
        }
        rootLayout.addView(surface, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        rootLayout.addView(lcd, android.widget.FrameLayout.LayoutParams(480, 480))
        setContentView(rootLayout)
        surface.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (surface.visibility == android.view.View.VISIBLE) placeLcdInBezel()
        }
        surface.post { placeLcdInBezel() }
        updateViewCompact()   // OPTIONS > VUE (vue compacte initiale, views prêtes)
        handler.postDelayed(tick, 250)

        /* ZuluLink : l'APK est CONSOLE (surface + horloge). Le FM-1 (module) suit notre
         * tempo ; on reçoit ses CC (74..80 voix/moteur, 20..37 FX, 102..115 boutons). */
        ZuluLink.onMessage = { msg -> handler.post { onZuluMessage(msg) } }
        ZuluLink.onPeersChanged = { handler.post { lcd.invalidate() } }
        ZuluLink.start("zulu-apk", ZuluLink.Role.CONSOLE)

        /* liaison ZTF avec le FM-1 (page LINK 17) : callbacks worker -> UI via handler */
        Fm1Usb.init(this)
        Fm1Usb.onStatus = { s -> handler.post { st.linkStatus = s; lcd.invalidate() } }
        Fm1Usb.onProgress = { s -> handler.post { st.linkProg = s; lcd.invalidate() } }
        Fm1Usb.onFiles = { fs -> handler.post {
            st.linkFiles.clear()
            for (f in fs) st.linkFiles.add("${f.name}  ${f.size}o")
            if (st.linkSel >= st.linkFiles.size) st.linkSel = 0
            lcd.invalidate()
        } }
        Fm1UsbAudio.onStatus = { s -> handler.post { st.linkStatus = s; lcd.invalidate() } }
        Fm1Usb.onParams = { ns -> handler.post {
            for ((id, v) in ns) if (id in 0 until LINK_PARAM_NAMES.size) linkParamVals[id] = v
            linkParamsRefresh()
            lcd.invalidate()
        } }
        linkRefreshLocal()

        /* dans l'APK le LCD est tactile : POW maintenu + toucher = circuit bend
         * (bible : REC maintenu + toucher l'écran ; REC sert déjà au looper ici) */
        lcd.setOnTouchListener { _, ev ->
            val s = lcd.scaleF()   // v3 : tient compte de la "vue étendue" (plein écran, centrée)
            if (s <= 0f) return@setOnTouchListener false
            val bx = (ev.x / s - lcd.transX()) / LcdView.W
            val by = (ev.y / s - lcd.transY()) / LcdView.H
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (st.page == 12) {          // baie v3 : tap = raccourci tactile (= PLAY sur la ligne)
                        val py = by * LcdView.H
                        when (st.patchView) {
                            0 -> { val i = st.winFirst(st.boxSel, st.patchBoxes, 11) + ((py - 26f) / 17f).toInt()
                                   if (i in 0 until st.patchBoxes) { st.boxSel = i; patchPlay() } }
                            1 -> { if (py < 26f + 6 * 17f) {
                                       val i = st.winFirst(st.inSel, st.inList.size, 6) + ((py - 26f) / 17f).toInt()
                                       if (i in st.inList.indices) { st.inSel = i; patchPlay() }
                                   } else {
                                       val i = st.listScroll + ((py - 144f) / 17f).toInt()
                                       if (i in st.srcCables.indices) ZtekAudio.assignSet(st.srcBox, st.srcCables[i], 0)
                                   } }
                            2 -> { val i = st.winFirst(st.rowSel, st.modRows.size, 11) + ((py - 26f) / 17f).toInt()
                                   if (i in st.modRows.indices) { st.rowSel = i; patchPlay() } }
                            else -> { val i = st.winFirst(st.rowSel, st.flatRows.size, 11) + ((py - 26f) / 17f).toInt()
                                      if (i in st.flatRows.indices) { st.rowSel = i; patchPlay() } }
                        }
                        lcd.invalidate()
                    } else if (st.page == 4) {   // page XY : le LCD tactile devient le pad   // page XY : le LCD tactile devient le pad
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
                    }
                }
                MotionEvent.ACTION_MOVE -> if (st.page == 4) {
                    st.xyX = bx.coerceIn(0f, 1f)
                    st.xyY = by.coerceIn(0f, 1f)
                    ZtekAudio.xySet(1, st.xyX, st.xyY)
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

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        ZuluLink.stop()
        Fm1UsbAudio.release()
        Fm1Usb.release()
        ZtekAudio.stop()
        super.onDestroy()
    }

    /* rotation : configChanges déclaré dans le manifeste -> pas de recréation de l'activité
     * (le moteur audio ZtekAudio continue), on redit juste les vues */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        lcd.invalidate()
        surface.invalidate()
    }

    // ---- page LINK (17) : liaison ZTF APK <-> FM-1 ------------------------------

    private val encDefault = arrayOf("SEL", "PRE", "ALG", "K1", "K2", "K3", "K4")
    private val encLink = arrayOf("SEL", "TAB", "DST", "PAGE", "TEMP", "CUT", "LPG")

    /* noms des 12 params ZTF (ordre P_* de ztek_link.c, patch 0010) */
    private val LINK_PARAM_NAMES = arrayOf("CUTOFF", "RESO", "ENVMOD", "DECAY", "WAVE", "ENGINE",
        "TEMPO", "PAGE", "LOOPG", "DLYT", "REVM", "SWING")
    private val linkParamVals = HashMap<Int, Int>()
    private val linkSendAt = LongArray(7)
    private val linkSendVal = IntArray(7)

    private fun linkParamsRefresh() {
        st.linkParams.clear()
        for (id in 0 until LINK_PARAM_NAMES.size) {
            val v = linkParamVals[id] ?: continue
            st.linkParams.add("${LINK_PARAM_NAMES[id]} $v")
        }
    }

    /** v2 mode A : demande la liste des params + active le watch (notifications live) */
    private fun linkWatchStart() {
        Fm1Usb.remote("WATCH") {
            paramWatch(true)
            val m = paramGet((0 until 12).toList().toIntArray())
            handler.post {
                linkParamVals.clear()
                for ((id, v) in m) linkParamVals[id] = v
                linkParamsRefresh()
                lcd.invalidate()
            }
        }
    }

    private fun linkRefreshLocal() {
        st.linkLocal.clear()
        for (f in SampleLibrary.list(this)) st.linkLocal.add(f.name)
    }

    /* v3 onglet PRESETS : liste le banc APK de la partie st.linkDst (0..2) */
    private fun linkRefreshPresets() {
        st.linkPresets.clear()
        for (s in 0 until ZtekAudio.ZT_PSLOTS) {
            val nm = ZtekAudio.bankName(st.linkDst, s)
            st.linkPresets.add("${s + 1} ${if (nm.isEmpty()) "-" else nm}")
        }
    }

    /* v3 « vue étendue » : la baie de patch (page 12) occupe tout l'écran du téléphone */
    private fun linkWideToggle() {
        st.wide = !st.wide
        if (st.wide) {
            st.page = 12
            st.patchView = 0
            surface.visibility = android.view.View.GONE
            val lp = lcd.layoutParams as android.widget.FrameLayout.LayoutParams
            lp.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            lp.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            lp.leftMargin = 0; lp.topMargin = 0
            lcd.layoutParams = lp
        } else {
            st.page = 17
            surface.visibility = android.view.View.VISIBLE
            placeLcdInBezel()
            setEncLabels(encLink)
        }
        lcd.invalidate(); surface.invalidate()
    }

    /* v3 onglet PRESETS : PLAY = export ZTP1 vers filesDir/ztek/presets, SEQ = import .ztp1, ARP = load dans l'APK */
    /** nom companion ZPRE d'un preset (part/slot) — mapping à confirmer quand le patch firmware ZPRE sera mergé */
    private fun zpreName(part: Int, slot: Int): String =
        "ZPRE%02d".format((part * ZtekAudio.ZT_NPRESETS + slot).coerceIn(0, 31))

    private fun linkPresetExport() {
        val part = st.linkDst
        val slot = st.linkSel.coerceIn(0, ZtekAudio.ZT_PSLOTS - 1)
        val blob = ZtekAudio.presetExportBlob(part, slot)
        if (blob == null) { hint("preset vide"); return }
        val f = java.io.File(SampleLibrary.presetsDir(this), "P${part}S%02d.ztp1".format(slot))
        f.writeBytes(blob)
        if (Fm1Usb.connected) {
            val zn = zpreName(part, slot)
            Fm1Usb.pushNamed(zn, blob, "PUSH $zn")
            st.linkStatus = "export ${f.name} + push $zn (${blob.size} o)"
        } else {
            st.linkStatus = "export -> ${f.name} (${blob.size} o)"
        }
        lcd.invalidate()
    }

    private fun linkPresetImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/zip", "audio/*", "text/plain"))
        }
        try { startActivityForResult(intent, 4243) } catch (e: Exception) {
            st.linkStatus = "SAF indisponible: ${e.message}"
        }
    }

    private fun linkPresetLoadApk() {
        val part = st.linkDst
        val slot = st.linkSel.coerceIn(0, ZtekAudio.ZT_PSLOTS - 1)
        val nm = ZtekAudio.bankName(part, slot)
        if (nm.isNotEmpty()) {
            ZtekAudio.bankLoad(part, slot)
            st.presetName = nm
            st.linkStatus = "LOAD APK ${ZtekAudio.PART_NAMES.getOrElse(part) { "?" }} ${slot + 1}: $nm"
        } else hint("SLT ${slot + 1} vide")
        lcd.invalidate()
    }

    private fun linkScan() {
        st.linkStatus = "scan USB…"
        Fm1Usb.scan()
        Fm1Usb.ping()
        Fm1Usb.refreshFiles()
        linkWatchStart()
        linkRefreshLocal()
    }

    /** PLAY en page LINK : onglet 0 = pull du fichier FM-1 vers le téléphone,
     *  onglet 1 = push du sample local vers ZSMP<linkDst> (ZSM1 µ-law, tronqué au max FM-1) */
    private fun linkAction() {
        if (st.linkTab == 3) { linkPresetExport(); return }   // onglet PRESETS : pas besoin d'USB
        if (!Fm1Usb.connected) { linkScan(); return }
        if (st.linkTab == 0) {
            if (st.linkSel >= st.linkFiles.size) { hint("aucun fichier"); return }
            val name = st.linkFiles[st.linkSel].substringBefore(' ').trim()
            val dest = java.io.File(SampleLibrary.pullDir(this), SampleLibrary.pullName(name))
            Fm1Usb.run("PULL") {
                val f = Fm1Usb.transport!!.fileIndexOf(name)
                val data = Fm1Usb.transport!!.fileRead(f.index) { d, t ->
                    Fm1Usb.onProgress("PULL $d/$t")
                }
                dest.parentFile?.mkdirs()
                dest.writeBytes(data)
                // si c'est un ZSM1, on prépare aussi un WAV écoutable à côté
                val z = ZtfCodec.decodeZsm(data)
                if (z != null) SampleLibrary.zsm1ToWav(z, java.io.File(dest.parentFile, dest.name + ".wav"))
                handler.post {
                    st.linkStatus = "PULL -> ${dest.name} (${data.size} o)"
                    linkRefreshLocal(); lcd.invalidate()
                }
            }
        } else {
            if (st.linkSel >= st.linkLocal.size) { hint("aucun sample local (SEQ = importer)"); return }
            val f = SampleLibrary.list(this).getOrNull(st.linkSel) ?: return
            val pcm = SampleLibrary.decodeMono(f) ?: run {
                handler.post { st.linkStatus = "WAV illisible: ${f.name}"; lcd.invalidate() }
                return
            }
            val blob = SampleLibrary.encodeZsm1ForFm1(pcm, SampleLibrary.FM1_SR, 1)   // µ-law, max ~92 ms
            val name = "ZSMP%02d".format(st.linkDst)
            Fm1Usb.pushNamed(name, blob, "PUSH $name")
        }
    }

    private fun linkLoad() {
        if (st.linkTab != 0 || st.linkSel >= st.linkFiles.size) return
        val name = st.linkFiles[st.linkSel].substringBefore(' ').trim()
        Fm1Usb.run("LOAD") {
            val f = Fm1Usb.transport!!.fileIndexOf(name)
            val ok = Fm1Usb.transport!!.ztfLoad(f.index)
            handler.post {
                st.linkStatus = if (ok) "LOAD $name appliqué sur le FM-1" else "LOAD $name a échoué"
                lcd.invalidate()
            }
        }
    }

    private fun linkDelete() {
        if (st.linkTab != 0 || st.linkSel >= st.linkFiles.size) return
        val name = st.linkFiles[st.linkSel].substringBefore(' ').trim()
        Fm1Usb.run("RM") {
            val f = Fm1Usb.transport!!.fileIndexOf(name)
            Fm1Usb.transport!!.fileDelete(f.index)
            handler.post { st.linkStatus = "$name supprimé"; lcd.invalidate() }
            Fm1Usb.refreshFiles()
        }
    }

    private fun linkAudioToggle() {
        if (Fm1UsbAudio.captureOn || Fm1UsbAudio.playOn) {
            Fm1UsbAudio.release()
            st.linkAud = false
        } else {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 43)
            }
            val cap = Fm1UsbAudio.startCapture(this)
            var play = false
            if (st.linkTab == 1 && st.linkSel < st.linkLocal.size) {
                val f = SampleLibrary.list(this).getOrNull(st.linkSel)
                if (f != null) play = Fm1UsbAudio.playSample(this, f)
            }
            st.linkAud = cap || play
        }
        lcd.invalidate()
    }

    private fun linkImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        try { startActivityForResult(intent, 4242) } catch (e: Exception) {
            st.linkStatus = "SAF indisponible: ${e.message}"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4242 && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                val f = SampleLibrary.importUri(this, uri)
                st.linkStatus = if (f != null) "importé: ${f.name}" else "import échoué"
                linkRefreshLocal()
                lcd.invalidate()
            }
        }
        if (requestCode == 4243 && resultCode == Activity.RESULT_OK) {   // v3 : import d'un preset ZTP1
            val uri: Uri? = data?.data
            if (uri != null) {
                try {
                    val blob = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (blob != null && blob.size >= 4) {   // le magic ZTP1 est validé par nativePresetImport
                        val part = st.linkDst
                        val slot = st.linkSel.coerceIn(0, ZtekAudio.ZT_PSLOTS - 1)
                        val ok = ZtekAudio.presetImportBlob(part, slot, blob)
                        if (ok) {
                            ZtekAudio.bankLoad(part, slot)
                            st.presetName = ZtekAudio.bankName(part, slot)
                            st.linkStatus = "preset importé -> ${ZtekAudio.PART_NAMES.getOrElse(part) { "?" }} ${slot + 1}"
                        } else st.linkStatus = "import ZTP1 refusé (${blob.size} o)"
                    } else st.linkStatus = "fichier illisible"
                } catch (e: Exception) { st.linkStatus = "import: ${e.message}" }
                linkRefreshPresets()
                lcd.invalidate()
            }
        }
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
                            curEngine = (v * 4.999f).toInt().coerceIn(0, 4); ZtekAudio.setEngine(curEngine)
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
            "REC" -> when (st.page) { 1, in 6..18 -> { st.page = 0; lcd.invalidate() } else -> { /* long-REC ne fait plus redo : undo/redo = HOME+REC / HOME+GLO uniquement */ } }
            "HOME" -> {   // long-HOME : page LINK (17) liaison ZTF avec le FM-1
                st.page = if (st.page == 17) 0 else 17
                if (st.page == 17) { setEncLabels(encLink); linkScan() }
                else setEncLabels(encDefault)
                lcd.invalidate()
            }
        }
    }

    // ---- OPTIONS (page 16, liste) : SEL=navigue, ALG=valeur live, Shift=reset ----
    private fun optSet(row: Int, v: Float) {
        when (row.coerceIn(0, 4)) {
            0 -> { st.ecoOn = v > 0.5f; ZtekAudio.setEco(if (st.ecoOn) 1 else 0); optsPrefs.edit().putBoolean("eco", st.ecoOn).apply() }
            1 -> { st.labelsZulu = v > 0.5f; optsPrefs.edit().putBoolean("labels_zulu", st.labelsZulu).apply(); surface.invalidate() }
            2 -> { st.viewMode = (v * 2.999f).toInt().coerceIn(0, 2); optsPrefs.edit().putInt("vue", st.viewMode).apply(); updateViewCompact() }
            3 -> { st.luxeOn = v > 0.5f; ZtekAudio.setLuxe(if (st.luxeOn) 1 else 0); optsPrefs.edit().putBoolean("luxe", st.luxeOn).apply() }
            4 -> { st.skinMaison = v > 0.5f; optsPrefs.edit().putBoolean("skin_maison", st.skinMaison).apply(); surface.invalidate() }
        }
        lcd.invalidate()
    }
    private fun optReset(row: Int) {   // Shift court = reset de la ligne courante
        optSet(row, 0f)
        hint("reset " + arrayOf("ECO", "LABELS", "VUE", "LUXE", "SKIN")[row.coerceIn(0, 4)])
    }
    private fun optResetAll() { for (r in 0..4) optSet(r, 0f) }   // Shift long = reset all

    // ---- OPTIONS > VUE = PLEIN/CLAV/AUTO (APK only) : AUTO = CLAV si FM-1/ZTF ou peer ZuluLink up (+ pop singe 400 ms)
    private fun linkUpNow() = Fm1Usb.connected || ZuluLink.peersSnapshot().any { !it.lost }
    /* Calque le LCD sur l'ecran du bezel : memes cotes que SurfaceView (canevas panneau
     * 1280x820 centre et mis a l'echelle) ; l'ecran utile du bezel est 370,42,480x480. */
    private fun placeLcdInBezel() {
        val r = surface.lcdRect()   // geometrie V2 : SurfaceView calcule l'ecran utile du bezel
        if (r[2] <= 0f) return
        val lp = lcd.layoutParams as android.widget.FrameLayout.LayoutParams
        lp.width = r[2].toInt()
        lp.height = r[2].toInt()
        lp.leftMargin = r[0].toInt()
        lp.topMargin = r[1].toInt()
        lcd.layoutParams = lp
    }

    private fun updateViewCompact() {
        val up = linkUpNow()
        val compact = when (st.viewMode.coerceIn(0, 2)) { 1 -> true; 2 -> up; else -> false }
        if (compact != st.viewCompact) { st.viewCompact = compact; surface.invalidate() }
        if (up && !prevLinkUp) { st.monkeyUntil = System.currentTimeMillis() + 400; lcd.invalidate() }
        prevLinkUp = up
    }

    // ---- encoders ----
    override fun onEncoderHold(i: Int) { if (i == 0) selHeld = true }
    override fun onEncoderPress(i: Int) {
        if (i == 0) {   // appui SEL = page MIX (bible) ; REC+SEL = page SNAP (bible l.40) ; page PRESETS 10 : appui SEL = LOAD (confirme le choix)
            if (recHeld) st.page = if (st.page == 2) 0 else 2
            else if (st.page == 10 && (st.psSaveArm || st.psRename)) {   // mode save/renommage PRESETS : appui SEL = char suivante / valide le nom
                selHeld = false
                if (st.psNameEdit) {
                    if (st.psNameCur >= st.psSaveName.length || st.psNameCur >= 11) {
                        st.psNameEdit = false
                        val nm = st.psSaveName.trim().ifEmpty { randomPresetName() }
                        val wasRename = st.psRename
                        if (wasRename) {   // renommage : applique tout de suite
                            ZtekAudio.presetRename(st.psPart, st.psSlot, nm)
                            st.psName = nm; st.psUsed = true; st.psRename = false
                        }
                        st.psSaveName = nm
                        hint("NOM " + nm + if (wasRename) " (renommé)" else "")
                    } else st.psNameCur++
                } else {
                    st.psNameEdit = true
                    st.psNameCur = st.psSaveName.length.coerceIn(0, 10)
                }
                lcd.invalidate()
                return
            }
            else if (st.page == 10) {
                selHeld = false
                val nm = ZtekAudio.bankName(st.psPart, st.psSlot)
                if (nm.isNotEmpty()) {
                    ZtekAudio.bankLoad(st.psPart, st.psSlot)
                    st.presetName = nm
                    hint("LOAD " + ZtekAudio.PART_NAMES.getOrElse(st.psPart) { "?" } + " " + (st.psSlot + 1) + ": " + nm)
                } else hint("SLT ${st.psSlot + 1} vide")
                lcd.invalidate()
                return
            }
            else st.page = if (st.page == 11) 0 else 11
            if (st.page == 11) st.patchView = 0
            lcd.invalidate()
        }
        if (i == 1) {   // appui PRE = page FINE (18) — idem REC+EDIT sur FM-1 (patch 0012)
            st.page = if (st.page == 18) 0 else 18
            if (st.page == 18) enterFine()
            else setEncLabels(arrayOf("SEL", "PRE", "ALG", "K1", "K2", "K3", "K4"))
            lcd.invalidate()
        }
    }
    override fun onEncoder(i: Int, v: Float) {
        st.pop(i)
        popPanelInfo(i, v)
        val dv = v - encPrev[i]
        encPrev[i] = v
        if (st.page in 6..10) {
            when (st.page) {
                6 -> when (i) {
                    0 -> { st.seqkVals[4] = v; ZtekAudio.seqKnob(9, v) }
                    1 -> ZtekAudio.acidKnob(0, v)
                    2 -> ZtekAudio.acidKnob(1, v)
                    3 -> { st.seqkVals[0] = v; ZtekAudio.seqKnob(5, v) }
                    4 -> { st.seqkVals[1] = v; ZtekAudio.seqKnob(6, v) }
                    5 -> { st.seqkVals[2] = v; ZtekAudio.seqKnob(7, v) }
                    6 -> { st.seqkVals[3] = v; ZtekAudio.seqKnob(8, v) }
                }
                7 -> {
                    val sub = st.sub
                    when (i) {
                        0 -> st.sub = (v * 7.999f).toInt().coerceIn(0, 7)
                        1 -> { st.lfoSync = v > 0.5f; ZtekAudio.lfoOpt(0, if (st.lfoSync) 1 else 0) }
                        2 -> { st.lfoTrig = v > 0.5f; ZtekAudio.lfoOpt(1, if (st.lfoTrig) 1 else 0) }
                        else -> {
                            val k = i - 3
                            var vv = v
                            if (sub >= 4 && (k == 1 || k == 2)) {
                                val maxV = (sub - 1) / 16.999f
                                if (v > maxV) { forbidden("-> " + srcNamesUpTo(sub)); vv = maxV }
                            }
                            ZtekAudio.modKnob(sub, k, vv)
                        }
                    }
                }
                8 -> {
                    val sub = st.sub
                    when (i) {
                        0 -> st.sub = (v * 7.999f).toInt().coerceIn(0, 7)
                        1 -> if (sub < 2) ZtekAudio.envLoop(sub, if (v > 0.5f) 1 else 0)
                        else -> {
                            val k = i - 3
                            var vv = v
                            if (sub >= 2 && k == 0) {
                                val maxV = (7 + sub) / 16.999f
                                if (v > maxV) { forbidden("-> " + srcNamesUpTo(8 + sub)); vv = maxV }
                            }
                            ZtekAudio.modKnob(8 + sub, k, vv)
                        }
                    }
                }
                9 -> when (i) {
                    0 -> {
                        val raw = (v * 50.999f).toInt().coerceIn(0, 50)
                        if (raw < ZtekAudio.ZT_NDST_ALL) {
                            st.sub = raw
                            st.asgSrc = ZtekAudio.assignSrc(st.sub)
                            st.asgDepth = ZtekAudio.assignDepth(st.sub)
                            hint(dstHint(st.sub, st.asgSrc))
                        } else forbidden("DST: VOIX 0-7 FX 8-25 SEQ 26-34 MACRO 35-50")
                    }
                    1 -> {
                        val raw = (v * 33.999f).toInt().coerceIn(0, 33)
                        if (raw <= 16) {
                            ZtekAudio.assign(st.sub, if (raw == 16) ZtekAudio.DST_OFF else raw, st.asgDepth)
                            hint(dstHint(st.sub, raw))
                        } else forbidden("SRC: " + srcNamesUpTo(16) + " OFF")
                    }
                    2 -> {
                        st.ludiqueOn = v > 0.5f
                        st.encVals[2] = if (st.ludiqueOn) 1f else 0f
                        hint(if (st.ludiqueOn) "LUDIQUE ON" else "LUDIQUE OFF: mode expert")
                    }
                    3 -> { st.asgDepth = v; ZtekAudio.assign(st.sub, st.asgSrc, v) }
                }
                10 -> when (i) {   // PRESETS : LOAD uniquement par appui SEL ou SEL maintenu+touche — jamais à la rotation ; en mode save/renommage+nom, SEL édite le nom
                    0 -> if ((st.psSaveArm || st.psRename) && st.psNameEdit) psSetChar(PS_CHARS[(v * (PS_CHARS.length - 0.001f)).toInt()])
                         else st.psSlot = (v * (ZtekAudio.ZT_PSLOTS - 0.001f)).toInt().coerceIn(0, ZtekAudio.ZT_PSLOTS - 1)
                    1 -> st.psPart = (v * 2.999f).toInt().coerceIn(0, ZtekAudio.ZT_NPARTS - 1)
                    4 -> {
                        ZtekAudio.presetSave(st.psPart, st.psSlot)
                        hint("SAVE " + ZtekAudio.PART_NAMES[st.psPart] + " " + (st.psSlot + 1))
                    }
                    5 -> ZtekAudio.presetClear(st.psPart, st.psSlot)
                }
            }
            lcd.invalidate()
            return
        }
        if (st.page == 13) {                    // page ARP : SEL=MODE PRE=OCT ALG=DIV K1=GATE
            when (i) {
                0 -> { st.arpVals[0] = v; ZtekAudio.arpKnob(0, v) }
                1 -> { st.arpVals[1] = v; ZtekAudio.arpKnob(1, v) }
                2 -> { st.arpVals[2] = v; ZtekAudio.arpKnob(2, v) }
                3 -> { st.arpVals[3] = v; ZtekAudio.arpKnob(3, v) }
            }
            lcd.invalidate()
            return
        }
        if (st.page == 14) {                    // page MACRO : SEL=slot PRE=cible ALG=sous K1=niveau
            when (i) {
                0 -> { st.macSlot = (v * 15.999f).toInt().coerceIn(0, ZtekAudio.ZT_NMACRO - 1); syncMacroSel() }
                1 -> { st.macDst = (v * 140.999f).toInt().coerceIn(0, 140); syncMacroSel() }
                2 -> { st.macSub = (v * 15.999f).toInt().coerceIn(0, 15) }
                3 -> { st.macVals[st.macSlot] = v; ZtekAudio.macroLevel(st.macSlot, v) }
            }
            lcd.invalidate()
            return
        }
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
        if (st.page == 12) {                     // baie v3 hiérarchique, 100% encodeurs (FM-1)
            when (st.patchView) {
                0 -> if (i == 0 || i == 2)         // SELECT/ALGO = boîte
                    st.boxSel = (v * (st.patchBoxes - 0.001f)).toInt().coerceIn(0, st.patchBoxes - 1)
                1 -> when (i) {
                    2 -> if (st.inList.isNotEmpty())   // ALGO = curseur IN (liste groupée par module)
                        st.inSel = (v * (st.inList.size - 0.001f)).toInt().coerceIn(0, st.inList.size - 1)
                    0 -> st.listScroll = (v * ((st.srcCables.size - 5).coerceAtLeast(0) + 0.001f)).toInt()
                            .coerceIn(0, (st.srcCables.size - 5).coerceAtLeast(0))   // SELECT = scroll câbles
                    3 -> if (kotlin.math.abs(dv) > 0.03f) patchToggleExpand()       // K1 = plier/déplier
                }
                2, 3 -> {
                    val n = if (st.patchView == 2) st.modRows.size else st.flatRows.size
                    if ((i == 0 || i == 2) && n > 0)
                        st.rowSel = (v * (n - 0.001f)).toInt().coerceIn(0, n - 1)
                }
            }
            lcd.invalidate()
            return
        }
        if (st.page == 11 && st.punchOv >= 0) { // overlay PUNCH (Shift+slot en page MIX) : PRE=slot K1=type K2=a K3=b K4=mix
            val s = st.punchOv
            when (i) {
                1 -> { }   // PRE = slot fixe (ouvert par Shift+slot)
                3 -> st.punchTypeS[s] = (v * 14.999f).toInt().coerceIn(0, 14)
                4 -> st.punchAS[s] = v
                5 -> st.punchBS[s] = v
                6 -> st.punchMS[s] = v
            }
            lcd.invalidate()
            return
        }
        if (st.page == 11) {                     // MIX 4 tranches : K1..K4=VOL, SEL=voie, PRE=filtre, ALG=CUT ; sous-menus voie/master
            when (st.mixSub) {
                0 -> when (i) {   // page globale : SEL=voie PRE=mode filtre ALG=CUT K1..K4=VOL ch1..4
                    0 -> st.mixStrip = (v * 3.999f).toInt().coerceIn(0, 3)
                    1 -> { val f = (v * 2.999f).toInt().coerceIn(0, 2); st.stripFmode[st.mixStrip] = f
                           ZtekAudio.mixSetStrip(st.mixStrip, ZtekAudio.MIX_FMODE, f.toFloat()) }
                    2 -> { st.stripCut[st.mixStrip] = v; ZtekAudio.mixSetStrip(st.mixStrip, ZtekAudio.MIX_CUT, v) }
                    3, 4, 5, 6 -> { val s = i - 3; st.stripVol[s] = v; ZtekAudio.mixSetStrip(s, ZtekAudio.MIX_VOL, v) }
                }
                1 -> when (i) {   // sous-menu voie p1 : VOL, PAN, aux->PoW!, aux->Loop
                    0 -> st.mixSubStrip = (v * 3.999f).toInt().coerceIn(0, 3)
                    3 -> { st.stripVol[st.mixSubStrip] = v; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_VOL, v) }
                    4 -> { st.stripPan[st.mixSubStrip] = v; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_PAN, v) }
                    5 -> { st.stripAuxPow[st.mixSubStrip] = v; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_AUX_POW, v) }
                    6 -> { st.stripAuxLoop[st.mixSubStrip] = v; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_AUX_LOOP, v) }
                }
                2 -> when (i) {   // sous-menu voie p2 : AUX1..4 libres sur K1..K4
                    0 -> st.mixSubStrip = (v * 3.999f).toInt().coerceIn(0, 3)
                    3 -> { st.stripAux1[st.mixSubStrip] = v; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_AUX1, v) }
                    4 -> { st.stripAux2[st.mixSubStrip] = v; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_AUX2, v) }
                    5 -> { st.stripAux3[st.mixSubStrip] = v; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_AUX3, v) }
                    6 -> { st.stripAux4[st.mixSubStrip] = v; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_AUX4, v) }
                }
                3 -> when (i) {   // sous-menu voie p3 : flags MUT/SOLO/LDR
                    0 -> st.mixSubStrip = (v * 3.999f).toInt().coerceIn(0, 3)
                    3 -> { val m = v > 0.5f; st.stripMut[st.mixSubStrip] = m; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_MUT, if (m) 1f else 0f) }
                    4 -> { val m = v > 0.5f; st.stripSolo[st.mixSubStrip] = m; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_SOLO, if (m) 1f else 0f) }
                    5 -> { val m = v > 0.5f; st.stripLdr[st.mixSubStrip] = m; ZtekAudio.mixSetStrip(st.mixSubStrip, ZtekAudio.MIX_LDR, if (m) 1f else 0f) }
                }
                else -> when (i) {   // sous-menu master : K1=GAIN K2=AUX1 K3=AUX2
                    3 -> { st.mixGain = v * 2f; ZtekAudio.mixSetMaster(ZtekAudio.MIXM_GAIN, st.mixGain) }
                    4 -> { st.masterAux[0] = v; ZtekAudio.mixSetMaster(ZtekAudio.MIXM_AUX1, v) }
                    5 -> { st.masterAux[1] = v; ZtekAudio.mixSetMaster(ZtekAudio.MIXM_AUX2, v) }
                }
            }
            lcd.invalidate()
            return
        }
        if (st.page == 5 && st.fxView == 1) {   // vue SIRENE (ALG sur la vue principale) : SEL=ON/OFF ALG=type K1..K4
            when (i) {
                0 -> { val on = v > 0.5f; st.sirenOn = on
                       ZtekAudio.siren(if (on) 1 else 0, st.sirenLvl, st.sirenRate, st.sirenFb) }
                2 -> { st.sirenWave = (v * 3.999f).toInt().coerceIn(0, 3); ZtekAudio.sirenWave(st.sirenWave) }
                3 -> { st.sirenRate = v; if (st.sirenOn) ZtekAudio.siren(1, st.sirenLvl, v, st.sirenFb) }
                4 -> { st.sirenLvl = v; if (st.sirenOn) ZtekAudio.siren(1, v, st.sirenRate, st.sirenFb) }
                5 -> { st.sirenFb = v; if (st.sirenOn) ZtekAudio.siren(1, st.sirenLvl, st.sirenRate, v) }
                6 -> { st.sirenMix = v; ZtekAudio.sirenMix(v) }
            }
            lcd.invalidate()
            return
        }
        if (st.page == 5 && st.fxView == 0) {   // menu FX, vue PEDALBOARD
            when (st.fxSub) {
                0 -> when (i) {   // vue d'ensemble : SEL=dry/wet pédale active, PRE=bloc, ALG=bascule sirène, K1..K3=dry/wet des 3 slots
                    0 -> { st.fxChainMix[st.fxChainSel] = v; ZtekAudio.fxchainMix(st.fxChainSel, v) }
                    1 -> st.fxBlockSel = (v * 7.999f).toInt().coerceIn(0, 7)
                    2 -> { st.fxView = 1 - st.fxView; hint(if (st.fxView == 1) "SIRENE" else "PEDALBOARD") }
                    3, 4, 5 -> { val k = i - 3; val s = st.fxChainSel * 3 + k
                                  st.fxSlotMix[s] = v; ZtekAudio.fxslotSet(s, st.fxSlotType[s],
                                      st.fxSlotP[s][0], st.fxSlotP[s][1], st.fxSlotP[s][2], st.fxSlotP[s][3], v) }
                }
                1 -> when (i) {   // sous-menu chaîne : SEL=chaîne, ALG=échange la chaîne dans l'ordre, PRE=bloc
                    0 -> st.fxChainSel = (v * 3.999f).toInt().coerceIn(0, 3)
                    1 -> st.fxBlockSel = (v * 7.999f).toInt().coerceIn(0, 7)
                    2 -> {   // échange la chaîne sélectionnée avec la suivante dans l'ordre
                        val ord = st.fxChainOrder.clone()
                        val pos = ord.indexOf(st.fxChainSel)
                        val np = (pos + 1) % 4
                        val t = ord[pos]; ord[pos] = ord[np]; ord[np] = t
                        ord.copyInto(st.fxChainOrder)
                        ZtekAudio.fxchainOrder(ord)
                    }
                }
                else -> when (i) {   // sous-menu slot : SEL=slot(0..2) ALG=effet du catalogue PRE=p3 K1..K3=p0..p2 K4=dry/wet slot
                    0 -> st.fxSlotSel = st.fxChainSel * 3 + (v * 2.999f).toInt().coerceIn(0, 2)
                    1 -> { val s = st.fxSlotSel; st.fxSlotP[s][3] = v
                           ZtekAudio.fxslotSet(s, st.fxSlotType[s], st.fxSlotP[s][0], st.fxSlotP[s][1], st.fxSlotP[s][2], v, st.fxSlotMix[s]) }
                    2 -> { val s = st.fxSlotSel
                           st.fxSlotType[s] = (v * (ZtekAudio.NCAT - 0.001f)).toInt().coerceIn(0, ZtekAudio.NCAT - 1)
                           ZtekAudio.fxslotSet(s, st.fxSlotType[s], st.fxSlotP[s][0], st.fxSlotP[s][1], st.fxSlotP[s][2], st.fxSlotP[s][3], st.fxSlotMix[s]) }
                    3, 4, 5 -> { val s = st.fxSlotSel; val k = i - 3; st.fxSlotP[s][k] = v
                                  ZtekAudio.fxslotSet(s, st.fxSlotType[s], st.fxSlotP[s][0], st.fxSlotP[s][1], st.fxSlotP[s][2], st.fxSlotP[s][3], st.fxSlotMix[s]) }
                    6 -> { val s = st.fxSlotSel; st.fxSlotMix[s] = v
                           ZtekAudio.fxslotSet(s, st.fxSlotType[s], st.fxSlotP[s][0], st.fxSlotP[s][1], st.fxSlotP[s][2], st.fxSlotP[s][3], v) }
                }
            }
            lcd.invalidate()
            return
        }
        if (st.page == 1) {                     // page LOOP : SEL=slot K1..K4=PITCH/POS/MODE/MUT PRE=VOL ALG=SRC
            if (st.loopView == 1) {             // sous-vue CHK : fenêtre choke ouverte -> SEL = groupe du pad édité
                if (st.chokeWin) {
                    if (i == 0) chokeSetGroup((v * 6.999f).toInt().coerceIn(0, 6))
                    else chokeWinClose()   // toute autre action quitte la fenêtre
                    lcd.invalidate()
                    return
                }
                when (i) {
                    0 -> st.selSlot = (v * 15.999f).toInt().coerceIn(0, 15)
                    3 -> { val g = (v * 6.999f).toInt().coerceIn(0, 6)
                           ZtekAudio.loopSetChoke(st.selSlot, g); st.loopChoke[st.selSlot] = g }
                }
                lcd.invalidate()
                return
            }
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
        if (st.page == 15) {                     // page DRUMS : SEL=pad PRE=niveau ALG=step K1=step on/off K2=swing K3=clear K4=rand
            if (st.chokeWin) {                  // fenêtre choke ouverte -> SEL = groupe du pad édité
                if (i == 0) chokeSetGroup((v * 6.999f).toInt().coerceIn(0, 6))
                else chokeWinClose()
                lcd.invalidate()
                return
            }
            when (i) {
                0 -> st.drumPad = (v * (ZtekAudio.DR_NPADS - 0.001f)).toInt().coerceIn(0, ZtekAudio.DR_NPADS - 1)
                1 -> { st.drumLvl[st.drumPad] = v; ZtekAudio.drumLevel(st.drumPad, v) }          // PRE = niveau pad
                2 -> st.drumStepCur = (v * 15.999f).toInt().coerceIn(0, 15)                        // ALG = curseur step
                3 -> {                                                                             // K1 = toggle step
                    val s2 = st.drumStepCur
                    val on = !st.drumPat[st.drumPad * 16 + s2]
                    ZtekAudio.drumPat(st.drumPad, s2, if (on) 1 else 0)
                    st.drumPat[st.drumPad * 16 + s2] = on
                }
                4 -> { st.drumSwing = v * 0.5f; ZtekAudio.drumSwing(st.drumSwing) }                 // K2 = swing
                5 -> { ZtekAudio.drumClear(st.drumPad)                                             // K3 = clear pad
                       for (s2 in 0 until 16) st.drumPat[st.drumPad * 16 + s2] = false }
                6 -> { ZtekAudio.drumRand(st.drumPad, 0.4f, (Math.random() * 1e9).toInt())          // K4 = rand pad
                       for (s2 in 0 until 16) st.drumPat[st.drumPad * 16 + s2] = ZtekAudio.drumGetPat(st.drumPad, s2) == 1 }
            }
            lcd.invalidate()
            return
        }
        if (st.page == 2 || st.page == 3) {      // SNAP/KIT : SEL=slot, K1=fader ZU/LU (SNAP), K4=FLY ±12 st (SNAP)
            when (i) {
                0 -> st.selSlot = (v * 15.999f).toInt().coerceIn(0, 15)
                3 -> if (st.page == 2) st.snapSide = if (v > 0.5f) 1 else 0
                6 -> if (st.page == 2) { ZtekAudio.setParam(ZtekAudio.AT_TUNING, v); hint("FLY ${((v - 0.5f) * 24).toInt()} st") }
            }
            lcd.invalidate()
            return
        }
        if (st.page == 16) {                     // OPTIONS liste : SEL=navigue, ALG=valeur live
            if (i == 0) st.optRow = (v * 4.999f).toInt().coerceIn(0, 4)
            if (i == 2) optSet(st.optRow, v)
            lcd.invalidate()
            return
        }
        if (st.page == 17) {                  // page LINK : SEL=ligne TAB=onglet DST=slot + télécommande ZTF K1..K4
            fun send(i: Int, value: Int, op: ZtfTransport.() -> Unit) {   // anti-rebond : 1 envoi / valeur, puis 1,2 s
                val now = System.currentTimeMillis()
                if (linkSendVal[i] == value && now - linkSendAt[i] < 1200) return
                linkSendVal[i] = value; linkSendAt[i] = now
                Fm1Usb.remote("L$i", op)
            }
            when (i) {
                0 -> {
                    val list = when (st.linkTab) { 0 -> st.linkFiles; 1 -> st.linkLocal; 2 -> st.linkParams; else -> st.linkPresets }
                    val n = list.size.coerceAtLeast(1)
                    st.linkSel = (v * (n - 0.001f)).toInt().coerceIn(0, n - 1)
                }
                1 -> st.linkTab = (v * 3.999f).toInt().coerceIn(0, 3)   // 0 FM-1, 1 LOCAL, 2 PARAMS, 3 PRESETS
                2 -> {
                    if (st.linkTab == 3) { st.linkDst = (v * 2.999f).toInt().coerceIn(0, 2); linkRefreshPresets() } // PRT
                    else st.linkDst = (v * 7.999f).toInt().coerceIn(0, 7)                                              // ZSMP
                }
                3 -> { val p = (v * 13.999f).toInt().coerceIn(0, 13); send(3, p) { ztfPageSet(p) } }        // PAGE FM-1
                4 -> { val t = 30 + (v * 270.999f).toInt().coerceIn(0, 270); send(4, t) { ztfParamSet(6, t) } } // TEMPO
                5 -> { val c = (v * 1000.999f).toInt().coerceIn(0, 1000); send(5, c) { ztfParamSet(0, c) } } // CUTOFF
                6 -> { val g = (v * 1000.999f).toInt().coerceIn(0, 1000); send(6, g) { ztfParamSet(8, g) } } // LOOP GAIN
            }
            lcd.invalidate()
            return
        }
        if (st.page == 18) {   // page FINE : SEL=banque, K1..K4 = 4 options de la banque (idem patch 0012)
            when (i) {
                0 -> {
                    val b = (v * (ZtekAudio.FINE_BANKS.size - 1).toFloat()).toInt().coerceIn(0, ZtekAudio.FINE_BANKS.size - 1)
