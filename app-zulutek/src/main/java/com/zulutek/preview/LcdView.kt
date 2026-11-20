/* SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 FlyingZulu
 *
 * LcdView: the APK's imitation of the FM-1's 240x240 non-touch LCD (ST7789), per
 * docs/ZULUTEK-UI-STYLE.md: black background, white MONOSPACE ink, fluo accents only for
 * important states (ZU pink / LU yellow witnesses, windows turquoise, REC red blinking,
 * warnings amber). Display only — all interaction goes through the cloned surface
 * (SurfaceView). Drawn in logical 240x240 coordinates, scaled to the view. */
package com.zulutek.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.View

class LcdState {
    var bpm = 120
    var playing = false
    var seqOn = false
    var recArmed = false
    var engine = 0
    val engineNames = arrayOf("ATEK303", "PLUK", "FM", "SUB", "8BIT")
    var selSlot = 0
    var encLabels = arrayOf("SEL", "PRE", "ALG", "K1", "K2", "K3", "K4")   // relabel selon la page
    val encVals = floatArrayOf(0f, 0f, 0f, 0.7f, 0.25f, 0.5f, 0.55f)
    var driveType = 0
    val driveNames = arrayOf("OFF", "OD", "RAT", "FUZZ", "MOJO")
    var dly = 0.25f
    var rev = 0.2f
    var curNote = -1
    val slotOn = BooleanArray(16)
    val slotRec = BooleanArray(16)
    val popUntil = LongArray(10)      // per-widget pop animation (frame turquoise while it runs)
    fun pop(i: Int) { popUntil[i] = System.currentTimeMillis() + 1200 }

    // veille auto (docs/ZULUTEK.md) : 0 off, 1 traversée ~7 s, 2 spectacle 30 s, 3 écran éteint
    var veilleOn = true
    var veillePhase = 0
    var catX = 0f                     // 0..1 across the 240 px
    var coucouUntil = 0L             // pop de réveil au centre
    var torch = false                // lampe torche (écran éteint, toutes les 30 s)

    // page SMP (sampleur audio-in, page 17)
    var smpPage = false
    var smpRecOn = false
    var smpMode = 2                  // 0 OFF 1 BEAT 2 CLEAN 3 OLD
    val smpModeNames = arrayOf("OFF", "BEAT", "CLEAN", "OLD")
    var smpLen = 0
    val smpPeaks = FloatArray(240)   // min/max par bucket (120 buckets x 2)

    // circuit bend (POW maintenu + toucher le LCD dans l'APK ; REC+écran sur la bible)
    var bendOn = false
    var bendX = 0f
    var bendY = 0f
    var bendMode = 0                 // 0 GLITCH 1 LOW BAT

    // ZuluLink : pairs vus sur le réseau local
    var linkPeers = 0
    var linkName = ""
    var flashUntil = 0L                  // flash blanc 120 ms à la capture SNAP (REC+PLAY)

    // pages : 0 MAIN, 1 LOOP (édition slot), 2 SNAP, 3 KIT, 4 XY
    var page = 0
    val snapFilled = BooleanArray(16)
    val kitFilled = BooleanArray(16)
    val loopParam = FloatArray(6)       // PITCH POS MODE MUT SRC VOL du slot édité
    var sirenOn = false
    var xyOn = false
    var xyX = 0.5f
    var xyY = 0.5f
    var xyHold = false
    var xyTargetNames = arrayOf("CUT", "RES")

    // page POW! (5) : réglages du punch-in
    var punchType = 8
    var punchA = 0.5f
    var punchB = 0.5f
    var punchMix = 0.8f
    val punchNames = arrayOf("OFF", "REPEAT", "1/16REP", "1/8DN", "1/8UP", "SHRINK", "BUILD",
        "STRETCH", "ECHO", "REVERSE", "STOP", "SCRATCH", "LPF", "CRUSH", "GATE")

    // page MIX (6) + sous-page baie de patch (7)
    var mixGain = 1f
    val slotVol = FloatArray(16)
    val slotAux = BooleanArray(16)          // send vers PoW!
    val patchGrid = BooleanArray(12)        // 4 sources x 3 dests, index = row*3+col
    val patchOk = BooleanArray(12)          // compatibilité (VCV Rack/miRack : l'incompatible est estompé)
    var patchRow = 0                        // curseur : SELECT = rangée (source)
    var patchCol = 0                        // ALGO = colonne (destination)
    val patchSrcNames = arrayOf("VOIX", "SIREN", "SMP", "LOOP")
    val patchDstNames = arrayOf("FX", "MASTER", "POW!")
    val patchCableColors = intArrayOf(
        Color.rgb(255, 45, 160), Color.rgb(255, 238, 0), Color.rgb(0, 255, 224),
        Color.rgb(255, 60, 60), Color.rgb(255, 160, 40), Color.rgb(150, 80, 255),
        Color.rgb(60, 220, 90), Color.rgb(60, 160, 255), Color.rgb(255, 120, 200),
        Color.rgb(120, 200, 255), Color.rgb(220, 255, 120), Color.rgb(255, 200, 90))

    // page MIX (6) + sous-page baie de patch (7) v3 hiérarchique :
    // vue 0 = boîtes (sources puis modules, compteurs de câbles), 1 = sous-boîte source
    // (sélecteur IN + liste des câbles qui grandit), 2 = sous-boîte module (câbles entrants),
    // 3 = liste plate de tous les câbles. Pilotable 100% encodeurs/boutons (FM-1 non tactile).
    val slotSrc = IntArray(16)              // ML_SP_SRC 0..6 -> lettre B/S/D/A/R/M/+
    val slotMut = BooleanArray(16)          // ML_SP_MUT
    val slotAct = BooleanArray(16)          // notes actives (VU tranche)
    val slotPatch = IntArray(16)            // nb de câbles de modulation sur le slot (baie v3)
    var masterPeak = 0f                     // VU master
    val auxN = IntArray(2)                  // nb de câbles sur les départs aux libres du master (AUX1/AUX2)
    val srcLetters = arrayOf("B", "S", "D", "A", "R", "M", "+")
    fun srcLetter(i: Int) = srcLetters.getOrElse(slotSrc[i]) { "?" }
    var patchView = 0
    val patchBoxes = ZtekAudio.NSRC_ACTIVE + ZtekAudio.NMOD
    var boxSel = 0                          // vue 0 : 0..NSRC_ACTIVE-1 = source, puis module
    var srcBox = 0                          // vue 1 : source courante
    val inList = ArrayList<Int>()           // entrées visibles : -1-m = header module m, sinon port
    val inOk = ArrayList<Boolean>()         // compatibilité de chaque entrée (estompée si faux)
    val inOn = ArrayList<Boolean>()         // câble déjà branché sur chaque entrée
    var inSel = 0                           // curseur liste IN (vue 1)
    val srcCables = ArrayList<Int>()        // dst des câbles de srcBox (vue 1)
    var listScroll = 0                      // scroll des câbles (vue 1)
    var modBox = 0                          // vue 2 : module courant
    val modRows = ArrayList<Pair<Int, Int>>()   // (src,dst) entrants dans modBox (vue 2)
    val flatRows = ArrayList<Pair<Int, Int>>()  // tous les câbles (vue 3)
    var rowSel = 0                          // ligne sélectionnée (vues 2/3)
    val srcOut = IntArray(ZtekAudio.NSRC)           // nb câbles sortants par source (vue 0)
    var drumOn = false                              // boîte à rythmes on/off
    val loopChoke = IntArray(16)                    // groupe de choke par slot LOOP (0=off, 1..6)
    val drumChoke = IntArray(ZtekAudio.DR_NPADS)    // groupe de choke par pad DRUMS
    val loopMaster = BooleanArray(16)               // slot master de son groupe (marqueur M)
    val drumMaster = BooleanArray(ZtekAudio.DR_NPADS)// pad master de son groupe
    var loopLeader = 1                              // LDR LOOP : 1=leader(hybride) 0=normal(Koala)
    var drumLeader = 1                              // LDR DRUMS
    var loopView = 0                                // page LOOP : 0=params, 1=sous-vue CHK
    var drumPad = 0                                 // pad sélectionné (page DRUMS)
    var chokeWin = false                            // fenêtre d'édition choke ouverte (touche noire MONO)
    var chokeWinUntil = 0L                          // auto-fermeture après 1,5 s sans action
    var chokePad = -1                               // pad/slot en cours d'édition choke
    var polyArmed = false                           // POLY pressé : le prochain pad tapé sera désigné leader
    var ecoOn = false                               // OPTIONS > ECO ON : LCD throttlé + blink figé
    var optRow = 0                                  // page OPTIONS (9) : ligne sélectionnée
    var mixStrip = 0                                // page MIX (11) : tranche sélectionnée (0..3, 4=master)
    val stripCut = FloatArray(4)                    // filtre traversant par tranche
    val stripFmode = IntArray(4)                    // 0 LP, 1 neutre, 2 HP
    val stripVol = FloatArray(4)
    val stripPan = FloatArray(4)
    val stripMut = BooleanArray(4)
    val stripSolo = BooleanArray(4)
    val stripLdr = BooleanArray(4)                  // choke général de la tranche
    val stripAuxPow = FloatArray(4)                 // départ post-fader PoW!
    val stripAuxLoop = FloatArray(4)                // départ post-fader Loop
    val stripAux1 = FloatArray(4)
    val stripAux2 = FloatArray(4)
    val stripAux3 = FloatArray(4)
    val stripAux4 = FloatArray(4)
    val masterAux = FloatArray(2)
    var mixMode = 0                                 // 0 = MUT, 1 = SOLO (bascule OP5, noire 5)
    var mixSub = 0                                  // 0 global, 1..3 sous-menu voie, 4 = master
    var mixSubStrip = 0                             // tranche du sous-menu
    var punchOv = -1                                // overlay PUNCH du slot PoW! (-1 = fermé)
    val punchTypeS = IntArray(16).apply { fill(8) } // type PoW! par slot (défaut ECHO)
    val punchAS = FloatArray(16).apply { fill(0.5f) }
    val punchBS = FloatArray(16).apply { fill(0.5f) }
    val punchMS = FloatArray(16).apply { fill(0.8f) }
    var sirenWave = 0
    var sirenLvl = 0.3f
    var sirenRate = 0.5f
    var sirenFb = 0f
    var sirenMix = 0f

    // menu FX (page 5, REC+FX) : fxView 0 = pedalboard, 1 = sirène (ALG bascule sur la vue principale)
    var fxView = 0
    var fxSub = 0            // 0 = vue d'ensemble, 1 = sous-menu chaîne, 2 = sous-menu slot
    var fxChainSel = 0       // pédale sélectionnée (OP1..OP4)
    var fxSlotSel = 0        // slot en édition (sous-menu slot)
    var fxBlockSel = 0       // bloc (preset du board) sélectionné
    val fxSlotType = IntArray(12)
    val fxSlotP = Array(12) { FloatArray(4) }
    val fxSlotMix = FloatArray(12)
    val fxChainMix = FloatArray(4)
    val fxChainOrder = IntArray(4)
    val drumPat = BooleanArray(ZtekAudio.DR_NPADS * 16)  // grille 16 steps par pad
    val drumLvl = FloatArray(ZtekAudio.DR_NPADS)     // niveau par pad
    var drumSwing = 0f                              // swing du séquenceur drums
    var drumStep = 0                                // step courant du séquenceur
    var drumStepCur = 0                             // curseur d'édition de step
    val modIn = IntArray(ZtekAudio.NMOD)            // nb câbles entrants par module (vue 0)
    val expanded = BooleanArray(ZtekAudio.NMOD)     // module déplié dans la liste IN

    val targetNames = arrayOf(
        "TUN", "CUT", "RES", "ENV", "DEC", "ACC", "WAV", "ENG",
        "DRVT", "DRV", "TONE", "LVL", "MODT", "RATE", "DEPT", "MIX",
        "DLYT", "FB", "DLT", "DLM", "RVD", "RVP", "RVM", "CRSH", "HOLD")

    fun targetName(t: Int) = targetNames.getOrElse(t) { "?" }

    /* nom d'un port de destination de la baie (ports 0..34 = VOIX/FX/SEQ comme PR #3) */
    fun dstName(p: Int): String {
        val m = ZtekAudio.modOf(p)
        if (m < 0) return "?"
        val rel = p - ZtekAudio.MOD_OFF[m]
        return when (m) {
            0 -> targetName(rel)                                   // VOIX : AT_
            1 -> targetName(8 + rel)                               // FX : FX_
            2 -> arrayOf("DEN", "ACC", "SLD", "RNG", "TIE", "STP", "SCL", "SQ8", "SQ9")
                    .getOrElse(rel) { "?" }                        // SEQ 26..34
            3 -> arrayOf("LVL", "T", "FB").getOrElse(rel) { "?" }   // SIREN
            4 -> arrayOf("START", "END", "VOL", "PITCH", "MODE").getOrElse(rel) { "?" }  // SMP
            5 -> "S${rel / 7 + 1}." +                              // LOOP : 16 slots x 7
                    arrayOf("PITCH", "POS", "MOD", "MUT", "SRC", "FX", "VOL").getOrElse(rel % 7) { "?" }
            6 -> arrayOf("FX", "MASTER", "POW!", "AUX1", "AUX2").getOrElse(rel) { "?" }   // OUT
            7 -> "AUX${rel + 1}"                                                        // AUX (libres)
            else -> {                                                                   // MIX : 13 x 4 + 3 master
                if (rel < ZtekAudio.MIX_N * 4)
                    ZtekAudio.STRIP_NAMES[rel / ZtekAudio.MIX_N] + "." +
                        ZtekAudio.MIX_PAR_NAMES.getOrElse(rel % ZtekAudio.MIX_N) { "?" }
                else arrayOf("GAIN", "AUX1", "AUX2").getOrElse(rel - ZtekAudio.MIX_N * 4) { "?" }
            }
        }
    }

    fun cableLabel(src: Int, dst: Int) =
        ZtekAudio.SRC_NAME.getOrElse(src) { "?" } + ">" + dstName(dst)

    fun cableColor(src: Int) = patchCableColors[src % patchCableColors.size]

    /* première ligne visible d'une fenêtre de `vis` lignes autour de `sel` dans `n` entrées */
    fun winFirst(sel: Int, n: Int, vis: Int) = (sel - vis / 2).coerceIn(0, (n - vis).coerceAtLeast(0))

    /* pack SEQ+ (PR #3) : pages 6 SEQ+, 7 LFO, 8 ENV, 9 ASSIGN, 10 PRESETS */
    val pageNames2 = mapOf(6 to "SEQ+", 7 to "LFO", 8 to "ENV", 9 to "ASSIGN", 10 to "PRESETS",
        11 to "MIX", 12 to "PATCH", 13 to "ARP", 14 to "MACRO", 15 to "DRUMS", 16 to "OPTIONS",
        17 to "LINK")
    var arpOn = false
    val arpVals = FloatArray(4)             // MODE OCT DIV GATE
    var macSlot = 0                         // page MACRO (14)
    var macDst = 0
    var macSub = 0
    val macVals = FloatArray(16)
    val macUsed = BooleanArray(16)
    fun macroDstName(d: Int): String = when {
        d < 26 -> targetName(d)
        d < ZtekAudio.ZT_SEQ_DST0 + 9 -> "SEQ" + (d - ZtekAudio.ZT_SEQ_DST0)
        d < ZtekAudio.ZT_NDST_ALL -> "MAC" + (d - ZtekAudio.ZT_NDST + 1)
        d < ZtekAudio.ZT_MDST_PATCH -> "AUX" + (d - ZtekAudio.ZT_MDST_AUX0)
        d < ZtekAudio.ZT_MDST_POWT -> "PT" + (d - ZtekAudio.ZT_MDST_PATCH)
        else -> "POWT"
    }
    var sub = 0
    val srcVals = FloatArray(16)
    var lfoSync = false
    var lfoTrig = false
    val seqkVals = FloatArray(5)
    var asgSrc = 255
    var asgDepth = 0f
    var noEntryUntil = 0L
    var sugMsg = ""
    var hintUntil = 0L
    var hintMsg = ""
    var ludiqueOn = true
    var psPart = 0
    var psSlot = 0
    var psUsed = false
    var psName = ""
    var presetName = "ACID 303"

    // page LINK (17) : liaison ZTF APK <-> FM-1 (Fm1Usb / ZtfTransport / SampleLibrary)
    var linkTab = 0                          // 0 = fichiers du FM-1, 1 = samples locaux (téléphone)
    var linkSel = 0                          // ligne sélectionnée dans l'onglet courant
    val linkFiles = ArrayList<String>()      // onglet 0 : "ZSMP00  1234 o"
    val linkLocal = ArrayList<String>()      // onglet 1 : noms des WAV locaux
    var linkStatus = "USB off"
    var linkProg = ""
    var linkDst = 0                          // slot ZSMP cible pour le push (0..7)
    var linkAud = false                      // pont audio hybride on/off
    val linkParams = ArrayList<String>()     // onglet 2 PARAMS : miroir live "NOM val" (0x62)
    val linkPresets = ArrayList<String>()    // onglet 3 PRESETS : banc APK "n NOM" (partie = linkDst)
    var wide = false                         // v3 « vue étendue » : la baie occupe tout l'écran du téléphone
}

class LcdView(ctx: Context, val st: LcdState) : View(ctx) {

    companion object {
        const val W = 240
        const val H = 240
        val BG = Color.BLACK
        val INK = Color.WHITE
        val DIM = Color.argb(255, 120, 120, 120)
        val FLUO_PINK = Color.rgb(255, 45, 160)     // ZU
        val FLUO_YEL = Color.rgb(255, 238, 0)       // LU
        val FLUO_TURQ = Color.rgb(0, 255, 224)      // windows / pops
        val REC_RED = Color.argb(255, 255, 60, 60)
        val REC_RED_OFF = Color.argb(255, 96, 32, 32)
        val AMBER = Color.argb(255, 255, 200, 60)
    }

    private val mono = Paint().apply {
        typeface = Typeface.MONOSPACE
        isAntiAlias = false
        style = Paint.Style.FILL
    }
    private val frame = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f }

    /* décor PCB pixel art (généré en CI par tools/make_icon.py ; absent en build local nu :
     * référencé par nom pour que le code compile même sans les PNG générés) */
    private val pcb: Bitmap? by lazy {
        val id = ctx.resources.getIdentifier("pcb_scene", "drawable", ctx.packageName)
        if (id != 0) BitmapFactory.decodeResource(ctx.resources, id) else null
    }

    private val RAINBOW = intArrayOf(
        Color.rgb(255, 60, 60), Color.rgb(255, 160, 40), Color.rgb(255, 238, 0),
        Color.rgb(60, 220, 90), Color.rgb(60, 160, 255), Color.rgb(150, 80, 255))

    /* the Nyan Cat ghetto blaster crossing the FM-1's inside, rainbow trail behind */
    private fun drawCat(c: Canvas, x: Float, y: Float, scale: Float) {
        val body = Paint().apply { color = Color.rgb(255, 150, 190); style = Paint.Style.FILL }
        val head = Paint().apply { color = Color.rgb(190, 195, 205); style = Paint.Style.FILL }
        // traînée arc-en-ciel
        for (k in RAINBOW.indices) {
            frame.color = RAINBOW[k]
            frame.strokeWidth = 2.2f * scale
            val yy = y + (k - 2.5f) * 3.2f * scale
            c.drawLine(x - 8f * scale, yy, x - 52f * scale, yy, frame)
        }
        // corps (pop-tart) + tête + oreilles + yeux
        c.drawRect(x - 10f * scale, y - 8f * scale, x + 14f * scale, y + 8f * scale, body)
        c.drawCircle(x + 18f * scale, y, 9f * scale, head)
        c.drawRect(x + 12f * scale, y - 15f * scale, x + 16f * scale, y - 8f * scale, head)
        c.drawRect(x + 20f * scale, y - 15f * scale, x + 24f * scale, y - 8f * scale, head)
        mono.color = Color.BLACK
        mono.textSize = 6f * scale
        c.drawText("o", x + 15f * scale, y + 2f * scale, mono)
        c.drawText("o", x + 21f * scale, y + 2f * scale, mono)
    }

    private fun text(c: Canvas, x: Float, y: Float, s: String, color: Int, size: Float) {
        mono.color = color
        mono.textSize = size
        mono.style = Paint.Style.FILL
        c.drawText(s, x, y, mono)
    }

    private fun box(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        frame.color = color
        c.drawRect(x0, y0, x1, y1, frame)
    }

    private fun popColor(i: Int, base: Int): Int =
        if (st.popUntil[i] > System.currentTimeMillis()) FLUO_TURQ else base

    /* v3 vue étendue : échelle = min(w/W, h/H) + centrage quand st.wide (plein écran) */
    fun scaleF() = if (st.wide) minOf(width.toFloat() / W, height.toFloat() / H) else width.toFloat() / W
    fun transX() = if (st.wide) (width / scaleF() - W) / 2f else 0f
    fun transY() = if (st.wide) (height / scaleF() - H) / 2f else 0f

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = scaleF()
        c.drawColor(BG)
        c.save()
        c.translate(transX(), transY())
        c.scale(s, s)

        val now = System.currentTimeMillis()
        val blink = if (st.ecoOn) true else (now / 260) % 2 == 0L
        val blinkFast = if (st.ecoOn) false else (now / 120) % 2 == 0L   // eco: animations figées
        // clignotement rapide = pad leader du choke

        // flash de capture SNAP (REC+PLAY) : inversion blanc/noir 120 ms
        if (now < st.flashUntil) {
            c.drawColor(INK)
            c.restore()
            return
        }

        // ---- veille auto : décor PCB + Nyan Cat, puis écran éteint + lampe torche ----
        if (st.veillePhase > 0) {
            val bmp = pcb
            if (bmp != null) c.drawBitmap(bmp, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), null)
            else c.drawColor(Color.argb(255, 16, 18, 22))
            when (st.veillePhase) {
                1, 2 -> {
                    drawCat(c, 20f + st.catX * (W - 60f), H / 2f, 1f)
                    if (st.veillePhase == 2 && (now / 130) % 7 == 0L) {
                        // glitch : bandes RGB décalées
                        for (k in 0 until 3) {
                            frame.color = RAINBOW[(k * 2 + (now / 130).toInt()) % RAINBOW.size]
                            frame.alpha = 60
                            val yy = ((now / 130) % H).toFloat()
                            c.drawRect(0f, yy, W.toFloat(), yy + 4f, frame)
                            frame.alpha = 255
                        }
                    }
                }
                3 -> {
                    // écran « éteint » : tout noir, la lampe torche balaie le coin bas-droit
                    c.drawColor(Color.argb(255, 4, 4, 6))
                    if (st.torch) {
                        val g = RadialGradient(216f, 216f, 70f,
                            Color.argb(160, 255, 255, 240), Color.argb(0, 255, 255, 240),
                            android.graphics.Shader.TileMode.CLAMP)
                        val p = Paint().apply {
                            shader = g
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                        }
                        c.drawCircle(216f, 216f, 70f, p)
                    }
                }
            }
            if (now < st.coucouUntil) drawCat(c, W / 2f, H / 2f, 1.6f)   // coucou au réveil
            c.restore()
            return
        }

        // ---- page SMP : waveform turquoise, marqueurs START/END jaunes, point REC rouge ----
        if (st.smpPage) {
            text(c, 6f, 15f, "SMP", FLUO_TURQ, 12f)
            text(c, 60f, 15f, st.smpModeNames[st.smpMode], INK, 10f)
            if (st.smpRecOn && blink) {
                mono.color = REC_RED
                c.drawCircle(226f, 11f, 5f, mono)
            }
            text(c, 6f, 30f, "LEN ${(st.smpLen / 48000.0).toString().take(4)}s", DIM, 9f)
            text(c, 90f, 30f, "K1..K4 STA/END/VOL/PIT", DIM, 8f)
            // waveform, y 60..170
            val wy0 = 60f; val wy1 = 170f; val wym = (wy0 + wy1) / 2f
            box(c, 6f, wy0, 234f, wy1, DIM)
            frame.color = FLUO_TURQ
            frame.strokeWidth = 1f
            val nb = st.smpPeaks.size / 2
            for (i in 0 until nb) {
                val x = 8f + (226f - 8f) * i / (nb - 1).coerceAtLeast(1)
                val mn = st.smpPeaks[i * 2]
                val mx = st.smpPeaks[i * 2 + 1]
                c.drawLine(x, wym - mx * 50f, x, wym - mn * 50f, frame)
            }
            // marqueurs START/END (jaunes)
            frame.color = FLUO_YEL
            frame.strokeWidth = 2f
            val sx = 8f + (st.encVals[3] * (226f - 8f))
            val ex = 8f + (st.encVals[4] * (226f - 8f))
            c.drawLine(sx, wy0, sx, wy1, frame)
            c.drawLine(ex, wy0, ex, wy1, frame)
            // valeurs K3/K4 en dessous
            text(c, 8f, 190f, "VOL ${(st.encVals[5] * 100).toInt()}", INK, 10f)
            text(c, 90f, 190f, "PITCH ${((st.encVals[6] - 0.5f) * 48).toInt()}st", INK, 10f)
            text(c, 8f, 210f, "REC: PRESET  PLAY: touche blanche / PLAY", DIM, 8f)
            text(c, 8f, 226f, "long-SEQ = fermer la page", DIM, 8f)
            c.restore()
            return
        }

        // ---- page LOOP : édition du slot sélectionné (K1 PITCH K2 POS K3 MODE K4 MUT, PRE VOL, ALG SRC) ----
        if (st.page == 1) {
            if (st.loopView == 1) {   // sous-vue CHK : groupes de choke des 16 slots (style Koala)
                text(c, 6f, 15f, "LOOP CHK", FLUO_TURQ, 12f)
                text(c, 96f, 15f, if (st.loopLeader == 1) "LDR ON" else "LDR OFF",
                    if (st.loopLeader == 1) FLUO_YEL else DIM, 9f)
                for (i in 0 until 16) {
                    val col = i % 8
                    val row = i / 8
                    val x0 = 6f + col * 29f
                    val y0 = 28f + row * 78f
                    val g = st.loopChoke[i]
                    val sel = i == st.selSlot
                    val mst = st.loopMaster[i]
                    val leadBlink = mst && blinkFast     // leader = clignotement rapide
                    box(c, x0, y0, x0 + 26f, y0 + 68f,
                        if (leadBlink) REC_RED else if (sel) FLUO_TURQ else if (g > 0) FLUO_YEL else DIM)
                    text(c, x0 + 8f, y0 + 42f, "${i + 1}", if (sel || g > 0) INK else DIM, 10f)
                    text(c, x0 + 3f, y0 + 12f, if (g > 0) "C$g" else "·",
                        if (leadBlink) REC_RED else if (g > 0) INK else DIM, 8f)
                    if (mst) text(c, x0 + 16f, y0 + 12f, "M", if (leadBlink) REC_RED else FLUO_YEL, 8f)
                    if (st.slotOn[i]) text(c, x0 + 4f, y0 + 60f, "▶", FLUO_YEL, 8f)
                }
                text(c, 6f, 196f, "SEL=slot  K1=CHK 0..6  K2=LDR ON/OFF", DIM, 8f)
                text(c, 6f, 210f, "même Cn = choke · M = master · MONO/POLY = noires 9/10", DIM, 8f)
                text(c, 6f, 224f, "SCL=params  REC=quitte", DIM, 8f)
                if (st.chokeWin) {
                    val g = st.loopChoke[st.chokePad.coerceIn(0, 15)]
                    box(c, 34f, 84f, 206f, 156f, FLUO_YEL)
                    text(c, 44f, 102f, "CHOKE SLOT ${st.chokePad + 1}", INK, 10f)
                    text(c, 44f, 126f, "C$g", FLUO_YEL, 16f)
                    text(c, 44f, 140f, "SEL=groupe  MONO=off  POLY=LDR", DIM, 8f)
                    text(c, 44f, 152f, "tap=+C$g  re-tap=LDR", DIM, 8f)
                }
                c.restore()
                return
            }
            text(c, 6f, 15f, "LOOP ${st.selSlot + 1}", FLUO_TURQ, 12f)
            if (st.slotRec[st.selSlot] && blink) { mono.color = REC_RED; c.drawCircle(226f, 11f, 5f, mono) }
            val labels = arrayOf("PITCH", "POS", "MODE", "MUT", "SRC", "VOL")
            for (i in 0 until 6) {
                val y0 = 30f + i * 26f
                text(c, 6f, y0 + 10f, labels[i], DIM, 9f)
                box(c, 60f, y0, 234f, y0 + 16f, popColor(i, DIM))
                val v = st.loopParam[i]
                frame.color = FLUO_TURQ
                c.drawLine(62f, y0 + 8f, 62f + 168f * v, y0 + 8f, frame)
                text(c, 200f, y0 + 12f, if (i == 0) "${(v * 24 - 12).toInt()}st" else "${(v * 100).toInt()}", INK, 9f)
            }
            text(c, 6f, 232f, "REC=arm/stop ERS=erase DLY=len REV=mode SCL=CHK long-REC=quitte", DIM, 8f)
            c.restore()
            return
        }

        // ---- pages SNAP / KIT : 16 slots en 2x8 (8 ZU / 8 LU pour SNAP) ----
        if (st.page == 2 || st.page == 3) {
            val isSnap = st.page == 2
            text(c, 6f, 15f, if (isSnap) "SNAP" else "KIT", if (isSnap) FLUO_PINK else AMBER, 12f)
            text(c, 80f, 15f, if (isSnap) "ZU|LU" else "kit global", DIM, 9f)
            if (st.sirenOn) text(c, 200f, 15f, "SIREN", REC_RED, 9f)
            val filled = if (isSnap) st.snapFilled else st.kitFilled
            for (i in 0 until 16) {
                val col = i % 8
                val row = i / 8
                val x0 = 6f + col * 29f
                val y0 = 30f + row * 80f
                val f = filled[i]
                val sel = i == st.selSlot
                box(c, x0, y0, x0 + 26f, y0 + 70f,
                    if (f) (if (isSnap) FLUO_YEL else AMBER) else if (sel) FLUO_TURQ else DIM)
                text(c, x0 + 8f, y0 + 40f, "${i + 1}", if (f || sel) INK else DIM, 10f)
                if (isSnap) text(c, x0 + 6f, y0 + 12f, if (i < 8) "ZU" else "LU", DIM, 8f)
            }
            text(c, 6f, 200f, if (isSnap) "tap=charger  long=stocker  REC=undo  dbl-REC=redo"
                              else "tap=charger  long=stocker  dbl-tap=miroir(xor8)",
                DIM, 8f)
            text(c, 6f, 214f, "long-DRV/MUT = quitter", DIM, 8f)
            c.restore()
            return
        }

        // ---- page XY (18) : pad sur le LCD (tactile côté APK), cibles X/Y ----
        if (st.page == 4) {
            text(c, 6f, 15f, "XY", FLUO_TURQ, 12f)
            text(c, 40f, 15f, "X>${st.xyTargetNames[0]}  Y>${st.xyTargetNames[1]}", INK, 10f)
            text(c, 200f, 15f, if (st.xyHold) "HOLD" else "MOM", if (st.xyHold) FLUO_YEL else DIM, 9f)
            box(c, 6f, 24f, 234f, 200f, DIM)
            for (i in 1 until 8) { frame.color = Color.argb(255, 40, 40, 40)
                c.drawLine(6f + 228f * i / 8f, 24f, 6f + 228f * i / 8f, 200f, frame) }
            for (i in 1 until 6) { c.drawLine(6f, 24f + 176f * i / 6f, 234f, 24f + 176f * i / 6f, frame) }
            if (st.xyOn) {
                mono.color = FLUO_YEL; mono.style = Paint.Style.FILL
                c.drawCircle(6f + st.xyX * 228f, 24f + st.xyY * 176f, 7f, mono)
            }
            text(c, 6f, 216f, "SEL=Xtarget PRE=Ytarget ALG=swap  dbl-tap=hold/mom", DIM, 8f)
            text(c, 6f, 230f, "long-GEN = quitter", DIM, 8f)
            c.restore()
            return
        }

        // ---- menu FX (5, REC+FX) : vue 0 = PEDALBOARD, vue 1 = SIRENE (ALG bascule) ----
        if (st.page == 5 && st.fxView == 1) {
            text(c, 6f, 15f, "SIREN", REC_RED, 12f)
            text(c, 70f, 15f, if (st.sirenOn) "ON" else "OFF", if (st.sirenOn) REC_RED else DIM, 9f)
            text(c, 6f, 40f, "TYPE", DIM, 9f)
            text(c, 60f, 40f, ZtekAudio.SIREN_WAVES[st.sirenWave.coerceIn(0, 3)], FLUO_YEL, 12f)
            val bars = arrayOf("RATE" to st.sirenRate, "DEPTH" to st.sirenLvl, "FB" to st.sirenFb, "MIX" to st.sirenMix)
            for (i in bars.indices) {
                val y0 = 60f + i * 40f
                text(c, 6f, y0 + 12f, bars[i].first, DIM, 9f)
                box(c, 60f, y0, 234f, y0 + 20f, DIM)
                frame.color = FLUO_TURQ
                c.drawLine(62f, y0 + 10f, 62f + 168f * bars[i].second, y0 + 10f, frame)
                text(c, 200f, y0 + 15f, "${(bars[i].second * 100).toInt()}", INK, 9f)
            }
            text(c, 6f, 224f, "SEL=ON/OFF  ALG=type(4)  K1=RATE K2=DEPTH K3=FB K4=MIX", DIM, 8f)
            text(c, 6f, 236f, "clavier chromatique  ALG=retour pedalboard  REC=quitte", DIM, 8f)
            c.restore()
            return
        }
        if (st.page == 5) {   // vue PEDALBOARD
            text(c, 6f, 15f, "FX", FLUO_TURQ, 12f)
            text(c, 40f, 15f, "PEDALBOARD", FLUO_TURQ, 9f)
            text(c, 190f, 15f, "ALG=SIREN", DIM, 8f)
            if (st.fxSub == 0) {
                // vue d'ensemble : 4 chaînes dans l'ordre d'exécution, 3 slots chacune
                for (ci in 0 until 4) {
                    val ch = st.fxChainOrder[ci]
                    val x0 = 6f + ci * 59f
                    val sel = ch == st.fxChainSel
                    box(c, x0, 24f, x0 + 55f, 118f, if (sel) FLUO_TURQ else DIM)
                    text(c, x0 + 3f, 36f, "CH${ch + 1}${if (ci < 3) "→" else ""}", if (sel) INK else DIM, 8f)
                    for (k in 0 until 3) {
                        val s = ch * 3 + k
                        val t = st.fxSlotType[s].coerceIn(0, ZtekAudio.NCAT - 1)
                        val y0 = 42f + k * 22f
                        text(c, x0 + 3f, y0 + 9f, ZtekAudio.FXT_NAMES[t].take(7), if (t == 0) DIM else FLUO_YEL, 7f)
                        frame.color = if (t == 0) Color.argb(255, 50, 50, 50) else FLUO_PINK
                        c.drawLine(x0 + 3f, y0 + 14f, x0 + 3f + 46f * st.fxSlotMix[s].coerceIn(0f, 1f), y0 + 14f, frame)
                    }
                    // dry/wet de la pédale (SEL)
                    frame.color = FLUO_TURQ
                    c.drawLine(x0 + 3f, 112f, x0 + 3f + 46f * st.fxChainMix[ch].coerceIn(0f, 1f), 112f, frame)
                    text(c, x0 + 3f, 118f, "DW${(st.fxChainMix[ch] * 9).toInt()}", DIM, 7f)
                }
                // blocs (presets du board)
                text(c, 6f, 134f, "BLOCS", DIM, 8f)
                for (b in 0 until 8) {
                    val x0 = 50f + b * 24f
                    val used = ZtekAudio.fxblockUsed(b) == 1
                    box(c, x0, 128f, x0 + 20f, 144f, if (b == st.fxBlockSel) FLUO_TURQ else if (used) FLUO_YEL else DIM)
                    text(c, x0 + 6f, 139f, "${b + 1}", if (b == st.fxBlockSel || used) INK else DIM, 8f)
                }
                text(c, 6f, 158f, "OP1..4=pédale  SEL=dry/wet pédale  K1..K3=dry/wet slots  PRE=bloc", DIM, 8f)
                text(c, 6f, 170f, "Shift+OPx=sous-menu pédale  PLAY=bloc load/save  REC=quitte", DIM, 8f)
            } else if (st.fxSub == 1) {
                // sous-menu chaîne : la pédale et ses 3 slots
                val ch = st.fxChainSel
                text(c, 6f, 30f, "PÉDALE CH${ch + 1}   dry/wet SEL=${(st.fxChainMix[ch] * 100).toInt()}", FLUO_TURQ, 10f)
                for (k in 0 until 3) {
                    val s = ch * 3 + k
                    val t = st.fxSlotType[s].coerceIn(0, ZtekAudio.NCAT - 1)
                    val y0 = 44f + k * 44f
                    val sel = st.fxSlotSel == s
                    box(c, 6f, y0, 234f, y0 + 38f, if (sel) FLUO_TURQ else DIM)
                    text(c, 12f, y0 + 14f, "SLOT ${k + 1}", DIM, 8f)
                    text(c, 70f, y0 + 14f, ZtekAudio.FXT_NAMES[t], if (t == 0) DIM else FLUO_YEL, 10f)
                    frame.color = FLUO_PINK
                    c.drawLine(70f, y0 + 26f, 70f + 150f * st.fxSlotMix[s].coerceIn(0f, 1f), y0 + 26f, frame)
                    text(c, 12f, y0 + 32f, "DW${(st.fxSlotMix[s] * 100).toInt()}", DIM, 8f)
                }
                text(c, 6f, 186f, "Shift+OP1/2/3 = sous-menu slot  ALG=échange ordre  SEL=chaîne", DIM, 8f)
                text(c, 6f, 198f, "REC=retour", DIM, 8f)
            } else {
                // sous-menu slot : choix de l'effet (ALG) + réglages fins K1..K3, PRE=p3, K4=dry/wet
                val s = st.fxSlotSel
                val t = st.fxSlotType[s].coerceIn(0, ZtekAudio.NCAT - 1)
                text(c, 6f, 30f, "SLOT ${s + 1} (CH${s / 3 + 1}.${s % 3 + 1})", FLUO_TURQ, 10f)
                text(c, 6f, 52f, "EFFET", DIM, 9f)
                text(c, 70f, 52f, ZtekAudio.FXT_NAMES[t], FLUO_YEL, 12f)
                text(c, 200f, 52f, "ALG", DIM, 8f)
                val kn = arrayOf("K1" to st.fxSlotP[s][0], "K2" to st.fxSlotP[s][1], "K3" to st.fxSlotP[s][2],
                                 "PRE" to st.fxSlotP[s][3], "K4 DW" to st.fxSlotMix[s])
                for (i in kn.indices) {
                    val y0 = 66f + i * 30f
                    text(c, 6f, y0 + 12f, kn[i].first, DIM, 9f)
                    box(c, 60f, y0, 234f, y0 + 18f, DIM)
                    frame.color = if (i == 4) FLUO_PINK else FLUO_TURQ
                    c.drawLine(62f, y0 + 9f, 62f + 168f * kn[i].second.coerceIn(0f, 1f), y0 + 9f, frame)
                }
                text(c, 6f, 226f, "ALG=effet  K1..K3=réglages  PRE=p3  K4=dry/wet  REC=retour", DIM, 8f)
            }
            c.restore()
            return
        }

        // ---- page MIX (11) : niveaux des 16 slots, aux -> PoW!, gain du bus LOOP ----

        if (st.page == 15) {
            // ---- page DRUMS : 8 pads (choke) + grille 16 steps du pad sélectionné ----
            text(c, 6f, 15f, "DRUMS", AMBER, 12f)
            text(c, 70f, 15f, if (st.drumOn) "ON" else "OFF", if (st.drumOn) FLUO_YEL else DIM, 9f)
            text(c, 140f, 15f, if (st.drumLeader == 1) "LDR" else "KOALA", if (st.drumLeader == 1) FLUO_YEL else DIM, 8f)
            for (p in 0 until ZtekAudio.DR_NPADS) {
                val col = p % 4
                val row = p / 4
                val x0 = 6f + col * 59f
                val y0 = 22f + row * 40f
                val g = st.drumChoke[p]
                val sel = p == st.drumPad
                val mst = st.drumMaster[p]
                val leadBlink = mst && blinkFast         // leader = clignotement rapide ; hors groupe = éteint
                box(c, x0, y0, x0 + 55f, y0 + 36f,
                    if (leadBlink) REC_RED else if (sel) FLUO_TURQ else if (g > 0) FLUO_YEL else DIM)
                text(c, x0 + 3f, y0 + 14f, ZtekAudio.DR_PAD_NAMES[p], if (sel || g > 0) INK else DIM, 8f)
                // niveau du pad
                frame.color = if (sel) FLUO_TURQ else Color.argb(255, 60, 60, 60)
                c.drawLine(x0 + 3f, y0 + 30f, x0 + 3f + 48f * st.drumLvl[p].coerceIn(0f, 1f), y0 + 30f, frame)
                if (g > 0 || mst)
                    text(c, x0 + 40f, y0 + 11f, (if (g > 0) "C$g" else "") + (if (mst) "M" else ""),
                        if (leadBlink) REC_RED else FLUO_YEL, 7f)
            }
            // grille 16 steps du pad sélectionné (2 rangées de 8)
            val p = st.drumPad
            text(c, 6f, 108f, "STEPS ${ZtekAudio.DR_PAD_NAMES[p]}", DIM, 9f)
            text(c, 200f, 108f, "sw ${(st.drumSwing * 100).toInt()}", DIM, 8f)
            for (s2 in 0 until 16) {
                val col = s2 % 8
                val row = s2 / 8
                val x0 = 6f + col * 29f
                val y0 = 116f + row * 26f
                val on = st.drumPat[p * 16 + s2]
                val cur = s2 == st.drumStep && st.drumOn
                val edit = s2 == st.drumStepCur
                box(c, x0, y0, x0 + 26f, y0 + 22f,
                    if (cur && blink) FLUO_PINK else if (edit) FLUO_TURQ else if (on) FLUO_YEL else DIM)
                if (on) text(c, x0 + 9f, y0 + 16f, "•", if (cur && blink) FLUO_PINK else INK, 10f)
            }
            text(c, 6f, 178f, "SEL=pad PRE=niv ALG=step K1=step K2=swing K3=clear K4=rand PLAY=hit", DIM, 8f)
            text(c, 6f, 192f, "MONO=noire9 choke · POLY=noire10 leader · REC/long-ERS=quitte", DIM, 8f)
            if (st.chokeWin) {
                val cp = st.chokePad.coerceIn(0, ZtekAudio.DR_NPADS - 1)
                box(c, 34f, 84f, 206f, 156f, FLUO_YEL)
                text(c, 44f, 102f, "CHOKE ${ZtekAudio.DR_PAD_NAMES[cp]}", INK, 10f)
                text(c, 44f, 126f, "C${st.drumChoke[cp]}", FLUO_YEL, 16f)
                text(c, 44f, 140f, "SEL=groupe  MONO=off  POLY=LDR", DIM, 8f)
                text(c, 44f, 152f, "tap=+C${st.drumChoke[cp]}  POLY+pad=LDR", DIM, 8f)
            }
            c.restore()
            return
        }

        if (st.page == 11) {
            // ---- page MAIN MIX : 4 tranches (K1..K4=VOL, SEL=voie, PRE=filtre, ALG=CUT) + 16 pads PoW! en bas ----
            text(c, 6f, 15f, "MIX", FLUO_TURQ, 12f)
            // mode MUT/SOLO (OP5, noire 5) : fixe en solo, oscillant en mute
            val modeCol = if (st.mixMode == 1) FLUO_YEL else if (blink) FLUO_YEL else DIM
            text(c, 46f, 15f, if (st.mixMode == 1) "SOLO" else "MUT", modeCol, 9f)
            val subNames = arrayOf("", "VOL/PAN/P·L", "AUX1..4", "MUT/SOLO/LDR", "MASTER")
            text(c, 92f, 15f, if (st.mixSub == 0) "tr ${ZtekAudio.STRIP_NAMES[st.mixStrip]}"
                else "${ZtekAudio.STRIP_NAMES[st.mixSubStrip]} ${subNames[st.mixSub]}", DIM, 8f)
            if (st.sirenOn) text(c, 200f, 15f, "SIREN", REC_RED, 9f)
            val fmNames = arrayOf("LP", "·", "HP")
            if (st.mixSub == 0) {
                // page globale : 4 tranches en 2x2 (VOL + PAN + FLT + flags)
                for (s in 0 until 4) {
                    val x0 = 6f + (s % 2) * 116f
                    val y0 = 22f + (s / 2) * 78f
                    val sel = s == st.mixStrip
                    val mut = st.stripMut[s] || (st.stripSolo.any { it } && !st.stripSolo[s])
                    box(c, x0, y0, x0 + 110f, y0 + 72f, if (sel) FLUO_TURQ else if (mut) DIM else FLUO_YEL)
                    text(c, x0 + 4f, y0 + 13f, ZtekAudio.STRIP_NAMES[s], if (mut) DIM else INK, 10f)
                    if (st.stripMut[s]) text(c, x0 + 66f, y0 + 13f, "MUT", REC_RED, 8f)
                    if (st.stripSolo[s]) text(c, x0 + 66f, y0 + 13f, "SOL", FLUO_PINK, 8f)
                    if (st.stripLdr[s]) text(c, x0 + 88f, y0 + 13f, "LDR", FLUO_TURQ, 8f)
                    val fm = st.stripFmode[s].coerceIn(0, 2)
                    text(c, x0 + 4f, y0 + 26f, "FLT ${fmNames[fm]}", if (fm == 1) DIM else FLUO_TURQ, 8f)
                    frame.color = if (fm == 1) Color.argb(255, 60, 60, 60) else FLUO_TURQ
                    c.drawLine(x0 + 46f, y0 + 22f, x0 + 46f + 60f * st.stripCut[s].coerceIn(0f, 1f), y0 + 22f, frame)
                    // VOL (K du canal) + PAN
                    text(c, x0 + 4f, y0 + 40f, "VOL", DIM, 8f)
                    box(c, x0 + 30f, y0 + 32f, x0 + 106f, y0 + 40f, DIM)
                    frame.color = if (mut) DIM else FLUO_YEL
                    c.drawLine(x0 + 32f, y0 + 36f, x0 + 32f + 72f * st.stripVol[s].coerceIn(0f, 1f), y0 + 36f, frame)
                    text(c, x0 + 4f, y0 + 54f, "PAN", DIM, 8f)
                    box(c, x0 + 30f, y0 + 47f, x0 + 106f, y0 + 53f, DIM)
                    frame.color = FLUO_TURQ
                    val px = x0 + 30f + 76f * st.stripPan[s].coerceIn(0f, 1f)
                    c.drawLine(px, y0 + 45f, px, y0 + 55f, frame)
                    text(c, x0 + 4f, y0 + 68f, "P${(st.stripAuxPow[s] * 9).toInt()} L${(st.stripAuxLoop[s] * 9).toInt()} A${(st.stripAux1[s] * 9).toInt()}${(st.stripAux2[s] * 9).toInt()}${(st.stripAux3[s] * 9).toInt()}${(st.stripAux4[s] * 9).toInt()}", DIM, 7f)
                }
            } else {
                // sous-menu voie/master : grosses barres K1..K4 du contexte
                val s = if (st.mixSub == 4) -1 else st.mixSubStrip
                val labels = when (st.mixSub) {
                    1 -> arrayOf("VOL" to st.stripVol[s], "PAN" to st.stripPan[s], "P>POW" to st.stripAuxPow[s], "L>LOOP" to st.stripAuxLoop[s])
                    2 -> arrayOf("AUX1" to st.stripAux1[s], "AUX2" to st.stripAux2[s], "AUX3" to st.stripAux3[s], "AUX4" to st.stripAux4[s])
                    3 -> arrayOf("MUT" to if (st.stripMut[s]) 1f else 0f, "SOLO" to if (st.stripSolo[s]) 1f else 0f, "LDR" to if (st.stripLdr[s]) 1f else 0f, "FLT" to st.stripFmode[s] / 2f)
                    else -> arrayOf("GAIN" to st.mixGain / 2f, "AUX1" to st.masterAux[0], "AUX2" to st.masterAux[1], "VU" to st.masterPeak)
                }
                text(c, 6f, 30f, if (st.mixSub == 4) "MASTER" else "VOIE ${ZtekAudio.STRIP_NAMES[s]} - p${st.mixSub}", FLUO_TURQ, 10f)
                for (i in 0 until 4) {
                    val y0 = 44f + i * 42f
                    text(c, 6f, y0 + 14f, labels[i].first, DIM, 9f)
                    box(c, 70f, y0, 234f, y0 + 22f, DIM)
                    frame.color = if (i == 2 && st.mixSub == 3) REC_RED else FLUO_TURQ
                    c.drawLine(72f, y0 + 11f, 72f + 160f * labels[i].second.coerceIn(0f, 1f), y0 + 11f, frame)
                    text(c, 200f, y0 + 17f, "${(labels[i].second.coerceIn(0f, 1f) * 100).toInt()}", INK, 9f)
                }
            }
            // 16 pads PoW! en bas (momentanés ; Shift+pad = réglages PUNCH du slot)
            text(c, 6f, 182f, "POW!", REC_RED, 8f)
            for (p in 0 until 16) {
                val x0 = 34f + (p % 8) * 25f
                val y0 = 176f + (p / 8) * 22f
                box(c, x0, y0, x0 + 22f, y0 + 18f,
                    if (st.punchOv == p) FLUO_TURQ else if (p == st.selSlot) FLUO_YEL else DIM)
                text(c, x0 + 7f, y0 + 13f, "${p + 1}", if (st.punchOv == p || p == st.selSlot) INK else DIM, 8f)
            }
            // bandeau master : BUS + AUX libres + VU
            text(c, 6f, 226f, "BUS", DIM, 8f)
            box(c, 30f, 218f, 110f, 230f, DIM)
            frame.color = FLUO_TURQ
            c.drawLine(32f, 224f, 32f + 76f * (st.mixGain / 2f).coerceIn(0f, 1f), 224f, frame)
            text(c, 120f, 226f, "AUX${(st.masterAux[0] * 9).toInt()}/${(st.masterAux[1] * 9).toInt()}", DIM, 8f)
            text(c, 190f, 226f, "VU", DIM, 8f)
            box(c, 208f, 218f, 236f, 230f, DIM)
            frame.color = if (st.masterPeak > 0.85f) REC_RED else FLUO_TURQ
            c.drawLine(210f, 224f, 210f + 24f * st.masterPeak.coerceIn(0f, 1f), 224f, frame)
            text(c, 6f, 240f, "K1..K4=VOL  SEL=voie PRE=FLT ALG=CUT  OP1..4=voies OP5=mode Shift= combos  Shift+pad=PUNCH", DIM, 7f)
            // overlay PUNCH du slot (Shift+slot)
            if (st.punchOv >= 0) {
                val p = st.punchOv
                box(c, 20f, 40f, 220f, 170f, FLUO_YEL)
                text(c, 30f, 58f, "PUNCH slot ${p + 1}", INK, 11f)
                text(c, 30f, 76f, "TYPE ${st.punchNames[st.punchTypeS[p].coerceIn(0, 14)]}", FLUO_YEL, 10f)
                val bars = arrayOf("a" to st.punchAS[p], "b" to st.punchBS[p], "MIX" to st.punchMS[p])
                for (i in bars.indices) {
                    val y0 = 88f + i * 24f
                    text(c, 30f, y0 + 12f, bars[i].first, DIM, 8f)
                    box(c, 60f, y0, 210f, y0 + 14f, DIM)
                    frame.color = FLUO_TURQ
                    c.drawLine(62f, y0 + 7f, 62f + 146f * bars[i].second, y0 + 7f, frame)
                }
                text(c, 30f, 162f, "K1=type K2=a K3=b K4=mix  REC=ferme", INK, 8f)
            }
            c.restore()
            return
        }

        // ---- sous-page baie de patch (7) v3 hiérarchique (pilotable sans tactile, FM-1) ----

        if (st.page == 12) {
            val DIMC = Color.argb(255, 70, 70, 70)
            val RH = 17f
            val Y0 = 26f
            when (st.patchView) {
                0 -> {   // vue générale : une boîte par source puis par module, compteur de câbles
                    text(c, 6f, 15f, "PATCH", FLUO_TURQ, 12f)
                    text(c, 78f, 15f, "SEL=boîte PLAY=ouvre", DIM, 8f)
                    val first = st.winFirst(st.boxSel, st.patchBoxes, 11)
                    for (k in 0 until 11) {
                        val i = first + k
                        if (i >= st.patchBoxes) break
                        val y = Y0 + k * RH
                        val sel = i == st.boxSel
                        val isSrc = i < ZtekAudio.NSRC_ACTIVE
                        val si = if (isSrc) ZtekAudio.SRC_IDX[i] else 0
                        val name = if (isSrc) ">" + ZtekAudio.SRC_NAME[si]
                                   else "<" + ZtekAudio.MOD_NAME[i - ZtekAudio.NSRC_ACTIVE]
                        val n = if (isSrc) st.srcOut[si] else st.modIn[i - ZtekAudio.NSRC_ACTIVE]
                        box(c, 6f, y, 234f, y + 15f, if (sel) (if (blink) FLUO_PINK else FLUO_YEL) else DIM)
                        text(c, 12f, y + 12f, name, if (sel) INK else DIM, 10f)
                        text(c, 196f, y + 12f, if (isSrc) "▶$n" else "$n▶",
                            if (n > 0) FLUO_TURQ else DIM, 10f)
                    }
                    text(c, 6f, 236f, "PLAY=ouvre  MUT=liste câbles  ERS=plier/déplier  REC=retour MIX", DIM, 8f)
                }
                1 -> {   // sous-boîte source : sélecteur IN (modules groupés/repliés) + câbles
                    text(c, 6f, 15f, ZtekAudio.SRC_NAME[st.srcBox] + " >", FLUO_YEL, 12f)
                    val e = st.inList.getOrElse(st.inSel) { -1 }
                    val eName = if (e < 0) ZtekAudio.MOD_NAME[-1 - e] else st.dstName(e)
                    text(c, 120f, 15f, "IN:$eName", DIM, 8f)
                    val first = st.winFirst(st.inSel, st.inList.size, 6)
                    for (k in 0 until 6) {
                        val i = first + k
                        if (i >= st.inList.size) break
                        val y = Y0 + k * RH
                        val en = st.inList[i]
                        val sel = i == st.inSel
                        if (en < 0) {   // header de module : replié = un seul slot
                            val m = -1 - en
                            box(c, 6f, y, 234f, y + 15f, if (sel) FLUO_YEL else DIM)
                            text(c, 12f, y + 12f, (if (st.expanded[m]) "▾ " else "▸ ") + ZtekAudio.MOD_NAME[m],
                                if (st.modIn[m] > 0) AMBER else DIM, 10f)
                            text(c, 210f, y + 12f, "${st.modIn[m]}", if (st.modIn[m] > 0) FLUO_TURQ else DIM, 9f)
                        } else {        // port : estompé + × si incompatible (protection VCV Rack)
                            val ok = st.inOk.getOrElse(i) { true }
                            val on = st.inOn.getOrElse(i) { false }
                            val col = if (!ok) DIMC else if (on) st.cableColor(st.srcBox)
                                      else if (sel) FLUO_YEL else INK
                            if (sel) box(c, 6f, y, 234f, y + 15f, if (blink) FLUO_PINK else FLUO_YEL)
                            text(c, 12f, y + 12f, (if (on) "● " else "○ ") + st.dstName(en) + (if (!ok) "  ×" else ""), col, 10f)
                        }
                    }
                    val cy = Y0 + 6 * RH + 8f
                    text(c, 6f, cy, "CÂBLES ${st.srcCables.size}", AMBER, 9f)
                    val cfirst = st.listScroll.coerceIn(0, (st.srcCables.size - 5).coerceAtLeast(0))
                    for (k in 0 until 5) {
                        val i = cfirst + k
                        if (i >= st.srcCables.size) break
                        text(c, 12f, cy + 8f + k * RH, st.cableLabel(st.srcBox, st.srcCables[i]),
                            st.cableColor(st.srcBox), 9f)
                    }
                    text(c, 6f, 236f, "ALG=IN  SEL=câbles  PLAY=on/off  ERS=plier  MUT=liste  REC=retour", DIM, 8f)
                }
                2 -> {   // sous-boîte module : câbles entrants, PLAY sur une ligne = débranche
                    val m = st.modBox
                    text(c, 6f, 15f, "< " + ZtekAudio.MOD_NAME[m], FLUO_YEL, 12f)
                    text(c, 120f, 15f, "${st.modRows.size} câble(s)", DIM, 8f)
                    if (st.modRows.isEmpty()) text(c, 12f, Y0 + 12f, "vide", DIM, 10f)
                    val first = st.winFirst(st.rowSel, st.modRows.size, 11)
                    for (k in 0 until 11) {
                        val i = first + k
                        if (i >= st.modRows.size) break
                        val y = Y0 + k * RH
                        val (s, d) = st.modRows[i]
                        val sel = i == st.rowSel
                        if (sel) box(c, 6f, y, 234f, y + 15f, if (blink) FLUO_PINK else FLUO_YEL)
                        text(c, 12f, y + 12f, st.dstName(d) + " ← " + ZtekAudio.SRC_NAME[s],
                            if (sel) INK else st.cableColor(s), 10f)
                    }
                    text(c, 6f, 236f, "SEL=ligne  PLAY=débranche  MUT=liste  REC=retour", DIM, 8f)
                }
                else -> {  // vue 3 : liste plate de tous les câbles (la baie grandit avec les patchs)
                    text(c, 6f, 15f, "CÂBLES", FLUO_TURQ, 12f)
                    text(c, 90f, 15f, "${st.flatRows.size} au total", DIM, 8f)
                    if (st.flatRows.isEmpty()) text(c, 12f, Y0 + 12f, "aucun câble", DIM, 10f)
                    val first = st.winFirst(st.rowSel, st.flatRows.size, 11)
                    for (k in 0 until 11) {
                        val i = first + k
                        if (i >= st.flatRows.size) break
                        val y = Y0 + k * RH
                        val (s, d) = st.flatRows[i]
                        val sel = i == st.rowSel
                        if (sel) box(c, 6f, y, 234f, y + 15f, if (blink) FLUO_PINK else FLUO_YEL)
                        text(c, 12f, y + 12f, st.cableLabel(s, d), if (sel) INK else st.cableColor(s), 10f)
                    }
                    text(c, 6f, 236f, "SEL=ligne  PLAY=débranche  MUT/REC=retour", DIM, 8f)
                }
            }
            c.restore()
            return
        }

        // ---- page OPTIONS (16) : OPTIONS > ECO = OFF/ON (CHOKE LDR déplacé en LDR par tranche, page MIX) ----
        if (st.page == 16) {
            text(c, 6f, 15f, "OPTIONS", FLUO_TURQ, 12f)
            text(c, 6f, 60f, "ECO", FLUO_TURQ, 10f)
            text(c, 90f, 60f, if (st.ecoOn) "ON" else "OFF", if (st.ecoOn) FLUO_YEL else INK, 10f)
            text(c, 6f, 232f, "K1=ON/OFF ECO   REC=quitte", DIM, 8f)
            c.restore()
            return
        }

        // ---- circuit bend : bandes glitch RGB + roue au point du doigt ----
        if (st.bendOn) {
            for (k in 0 until 3) {
                frame.color = RAINBOW[(k * 2 + 1) % RAINBOW.size]
                frame.alpha = 70
                val yy = (st.bendY * H + k * 37f) % H
                c.drawRect(0f, yy, W.toFloat(), yy + 3f, frame)
                frame.alpha = 255
            }
            val bx = st.bendX * W
            val by = st.bendY * H
            val sweep = SweepGradient(bx, by, intArrayOf(
                FLUO_PINK, FLUO_YEL, FLUO_TURQ, REC_RED, FLUO_PINK), null)
            val wp = Paint().apply { shader = sweep; style = Paint.Style.STROKE; strokeWidth = 3f }
            c.drawCircle(bx, by, 26f, wp)
            text(c, bx - 12f, by + 3f, if (st.bendMode == 0) "GLT" else "LB", INK, 9f)
        }

        // ---- top bar ----
        text(c, 6f, 15f, "ZULUTEK", FLUO_YEL, 12f)
        text(c, 140f, 15f, if (st.playing) "PLAY" else "STOP", if (st.playing) FLUO_PINK else DIM, 10f)
        text(c, 176f, 15f, "${st.bpm}", INK, 10f)
        text(c, 200f, 15f, if (st.seqOn) "SEQ" else "seq", if (st.seqOn) FLUO_TURQ else DIM, 10f)
        if (st.recArmed && blink) text(c, 222f, 15f, "R", REC_RED, 10f)

        // ---- engine + drive line ----
        text(c, 6f, 30f, if (st.linkPeers > 0) "LNK:${st.linkPeers}${st.linkName}" else "LNK:--",
            if (st.linkPeers > 0) FLUO_TURQ else DIM, 8f)
        text(c, 6f, 34f, "ENG", DIM, 9f)
        text(c, 34f, 34f, st.engineNames[st.engine], FLUO_PINK, 11f)
        text(c, 110f, 34f, "DRV", DIM, 9f)
        text(c, 138f, 34f, st.driveNames[st.driveType], INK, 10f)
        text(c, 190f, 34f, "n${st.curNote}", if (st.curNote >= 0) FLUO_YEL else DIM, 10f)

        // ---- 7 encoders, y 42..86: 7 cells ----
        for (i in 0 until 7) {
            val x0 = 6f + i * 33f
            box(c, x0, 42f, x0 + 30f, 86f, popColor(i, DIM))
            text(c, x0 + 3f, 52f, st.encLabels[i], DIM, 8f)
            val v = st.encVals[i]
            text(c, x0 + 3f, 66f, "${(v * 100).toInt()}", INK, 9f)
            frame.color = FLUO_TURQ
            c.drawLine(x0 + 3f, 80f, x0 + 3f + 24f * v, 80f, frame)
        }

        // ---- FX line, y 94..110 ----
        text(c, 6f, 105f, "DLY", DIM, 9f)
        text(c, 36f, 105f, "${(st.dly * 100).toInt()}", INK, 10f)
        text(c, 90f, 105f, "REV", DIM, 9f)
        text(c, 120f, 105f, "${(st.rev * 100).toInt()}", INK, 10f)
        text(c, 170f, 105f, "SEL", DIM, 9f)
        text(c, 200f, 105f, "${st.selSlot + 1}", if (st.recArmed) REC_RED else INK, 10f)

        // ---- 16 slot keys, 2 rows of 8, y 118..238 (big, like the FM-1 white keys) ----
        for (i in 0 until 16) {
            val col = i % 8
            val row = i / 8
            val x0 = 6f + col * 29f
            val y0 = 118f + row * 60f
            val on = st.slotOn[i]
            val rec = st.slotRec[i]
            val sel = i == st.selSlot
            val color = when {
                rec && blink -> REC_RED
                rec -> REC_RED_OFF
                on -> FLUO_YEL
                sel -> AMBER
                else -> DIM
            }
            box(c, x0, y0, x0 + 26f, y0 + 54f, color)
            text(c, x0 + 8f, y0 + 30f, "${i + 1}", if (on || rec || sel) INK else DIM, 10f)
        }


        // ---- pack SEQ+ (PR #3): pages 6..10 + MIX/PATCH (PR #1 agglomérés): 6..12 ----
        if (st.page >= 6 && st.page <= 14) {
            val pn = st.pageNames2[st.page] ?: "?"
            text(c, 6f, 105f, pn, FLUO_TURQ, 10f)
            when (st.page) {
                6 -> {
                    val nm = arrayOf("LCK", "LEN", "MUT", "PLY", "RND")
                    for (i in 0 until 5) {
                        val x0 = 52f + i * 37f
                        text(c, x0, 105f, nm[i], DIM, 8f)
                        text(c, x0 + 14f, 105f, "${(st.seqkVals[i] * 100).toInt()}", INK, 9f)
                    }
                }
                7, 8 -> {
                    val nm = if (st.page == 7) ZtekAudio.SRC_NAMES.getOrElse(st.sub) { "?" }
                             else ZtekAudio.TOOL_NAMES.getOrElse(st.sub) { "?" }
                    text(c, 44f, 105f, "SUB", DIM, 8f)
                    text(c, 70f, 105f, nm + " " + st.sub, INK, 9f)
                    if (st.page == 7) {
                        text(c, 130f, 105f, if (st.lfoSync) "SYNC" else "sync", if (st.lfoSync) FLUO_PINK else DIM, 8f)
                        text(c, 172f, 105f, if (st.lfoTrig) "TRIG" else "trig", if (st.lfoTrig) FLUO_PINK else DIM, 8f)
                    }
                    for (i in 0 until 16) {
                        val x0 = 6f + i * 14.5f
                        val hh = st.srcVals[i] * 12f
                        frame.color = if (i == st.sub) FLUO_YEL else DIM
                        c.drawRect(x0, 116f - hh, x0 + 11f, 116f, frame)
                        if (st.srcVals[i] > 0.01f) {
                            frame.color = FLUO_TURQ
                            c.drawRect(x0, 116f - hh, x0 + 11f, 116f, frame)
                        }
                    }
                }
                9 -> {
                    text(c, 44f, 105f, "DST", DIM, 8f)
                    text(c, 74f, 105f, "${st.sub}", INK, 9f)
                    text(c, 100f, 105f, "SRC", DIM, 8f)
                    text(c, 126f, 105f, if (st.asgSrc == 255) "OFF" else ZtekAudio.SRC_NAMES.getOrElse(st.asgSrc) { "?" }, INK, 9f)
                    text(c, 180f, 105f, "DPT ${(st.asgDepth * 100).toInt()}", INK, 9f)
                    text(c, 212f, 105f, if (st.ludiqueOn) "LUD" else "lud", if (st.ludiqueOn) FLUO_PINK else DIM, 8f)
                }
                10 -> {
                    text(c, 44f, 105f, "PRT", DIM, 8f)
                    text(c, 70f, 105f, ZtekAudio.PART_NAMES.getOrElse(st.psPart) { "?" }, FLUO_PINK, 9f)
                    text(c, 116f, 105f, "SLT", DIM, 8f)
                    text(c, 140f, 105f, "${st.psSlot + 1}${if (st.psUsed) "*" else " "}", INK, 9f)
                    text(c, 170f, 105f, if (st.psName.isEmpty()) "-" else st.psName, if (st.psUsed) FLUO_YEL else DIM, 9f)
                }
                13 -> {
                    val m = (st.arpVals[0] * 4.99f).toInt().coerceIn(0, 4)
                    val d = (st.arpVals[2] * 5.99f).toInt().coerceIn(0, 5)
                    text(c, 44f, 105f, "MODE", DIM, 8f)
                    text(c, 84f, 105f, ZtekAudio.ARP_MODES[m], FLUO_PINK, 9f)
                    text(c, 130f, 105f, "OCT", DIM, 8f)
                    text(c, 156f, 105f, "${1 + (st.arpVals[1] * 3.99f).toInt().coerceIn(0, 3)}", INK, 9f)
                    text(c, 178f, 105f, ZtekAudio.ARP_DIVS[d], INK, 9f)
                    text(c, 212f, 105f, "G${(st.arpVals[3] * 100).toInt()}", INK, 9f)
                    text(c, 6f, 92f, "held: ${ZtekAudio.arpHeld()}", DIM, 8f)
                }
                14 -> {
                    text(c, 44f, 105f, "SLT", DIM, 8f)
                    text(c, 68f, 105f, "${st.macSlot + 1}${if (st.macUsed[st.macSlot]) "*" else " "}", FLUO_PINK, 9f)
                    text(c, 100f, 105f, "DST", DIM, 8f)
                    text(c, 126f, 105f, st.macroDstName(st.macDst), INK, 9f)
                    text(c, 190f, 105f, "V${(st.macVals[st.macSlot] * 100).toInt()}", INK, 9f)
                    for (i in 0 until 16) {
                        val x0 = 6f + i * 14.5f
                        frame.color = if (i == st.macSlot) FLUO_YEL else if (st.macUsed[i]) FLUO_TURQ else DIM
                        c.drawRect(x0, 116f, x0 + 11f, 128f, frame)
                    }
                    text(c, 6f, 140f, "tap touche blanche = applique", DIM, 8f)
                }
            }
        }
        // ---- page LINK (17) : liaison ZTF avec le FM-1 ----
        if (st.page == 17) {
            text(c, 6f, 105f, "LINK", FLUO_TURQ, 10f)
            text(c, 60f, 105f, when (st.linkTab) { 0 -> "FM-1"; 1 -> "LOCAL"; 2 -> "PARAMS"; else -> "PRESETS" }, INK, 9f)
            if (st.linkTab == 1) text(c, 110f, 105f, "ZSMP$st.linkDst", DIM, 8f)
            else if (st.linkTab == 3) text(c, 110f, 105f, "PRT ${ZtekAudio.PART_NAMES.getOrElse(st.linkDst) { "?" }}", DIM, 8f)
            text(c, 180f, 105f, if (Fm1Usb.connected) "USB" else "off",
                if (Fm1Usb.connected) FLUO_TURQ else REC_RED, 8f)
            val list = when (st.linkTab) { 0 -> st.linkFiles; 1 -> st.linkLocal; 2 -> st.linkParams; else -> st.linkPresets }
            val n = minOf(list.size, 7)
            for (k in 0 until n) {
                val y = 120f + k * 13f
                if (k == st.linkSel) { frame.color = FLUO_YEL; c.drawRect(4f, y - 9f, 236f, y + 2f, frame) }
                text(c, 8f, y, list[k], INK, 9f)
            }
            if (list.isEmpty()) text(c, 8f, 120f, when (st.linkTab) {
                0 -> "(SAVE = scan USB)"
                1 -> "(SEQ = importer WAV)"
                2 -> "(miroir live 0x62 — SAVE = watch)"
                else -> "(PLAY = export ZTP1, SEQ = import, ARP = load)"
            }, DIM, 8f)
            text(c, 6f, 216f, st.linkStatus, AMBER, 8f)
            if (st.linkProg.isNotEmpty()) text(c, 6f, 228f, st.linkProg, FLUO_TURQ, 8f)
            if (st.linkAud) text(c, 200f, 228f, "AUD", FLUO_PINK, 8f)
            text(c, 6f, 92f, "PLAY push/pull SAVE scan ARP load FX rm ENV aud SEQ wav EDIT wide | K1 PG K2 TMP K3 CUT K4 LPG", DIM, 7f)
        }
        // panneau "sens interdit" (rond rouge + barre blanche) + suggestions / bulles de conseils
        if (now < st.noEntryUntil) {
            val cx = 120f; val cy = 62f
            val pulse = 1f + 0.15f * kotlin.math.sin(now / 40f)
            val r = 12f * pulse
            mono.color = REC_RED
            c.drawCircle(cx, cy, r, mono)
            mono.color = INK
            c.drawRect(cx - r, cy - 3f, cx + r, cy + 3f, mono)
            if (st.sugMsg.isNotEmpty()) text(c, 6f, 92f, st.sugMsg, AMBER, 9f)
        } else if (now < st.hintUntil && st.hintMsg.isNotEmpty()) {
            frame.color = FLUO_TURQ
            val pulse = 1f + 0.15f * kotlin.math.sin(now / 40f)
            c.drawCircle(120f, 62f, 6f * pulse, frame)
            text(c, 6f, 92f, st.hintMsg, FLUO_TURQ, 9f)
        }
        c.restore()
    }
}
