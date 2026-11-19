package com.zulu

import android.app.Activity
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.view.ViewGroup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.concurrent.thread
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private val engine = AudioEngine()
    private val levels = FloatArray(2)
    private val inLevels = FloatArray(2)
    private var vuSlow = 0f
    private val vuHandler = Handler(Looper.getMainLooper())
    private val recEndHandler = Handler(Looper.getMainLooper())
    private val phylHandler = Handler(Looper.getMainLooper())
    private val timeSigHandler = Handler(Looper.getMainLooper())
    private val timeSigHide = Runnable {
        if (this::timeSigConsole.isInitialized) timeSigConsole.visibility = View.GONE
    }
    private lateinit var phyl: PhylactereView
    private val vuRunnable = object : Runnable {
        override fun run() {
            engine.nativeGetLevels(levels)
            engine.nativeGetInLevels(inLevels)
            val lL = Math.sqrt(levels[0].toDouble()).toFloat()
            val lR = Math.sqrt(levels[1].toDouble()).toFloat()
            vuMeter.levelL = lL
            vuMeter.levelR = lR
            vuMeter.inLevelL = Math.sqrt(inLevels[0].toDouble()).toFloat()
            vuMeter.inLevelR = Math.sqrt(inLevels[1].toDouble()).toFloat()
            val lvl = maxOf(lL, lR)
            vuSlow += 0.06f * (lvl - vuSlow)
            pad.setAudio(lvl, (lvl - vuSlow).coerceAtLeast(0f) * 3f)
            bpmLcd.bendCat = pad.bendActive
            bpmLcd.catInLevel = (vuMeter.inLevelL + vuMeter.inLevelR) * 0.5f
            bpmLcd.catOutLevel = (lL + lR) * 0.5f
            bpmLcd.invalidate()
            vuHandler.postDelayed(this, 50)
        }
    }

    private lateinit var pad: ZuluPad
    private lateinit var macroPanel: MacroPanelView
    private lateinit var vuMeter: BarVuView
    private lateinit var bpmLcd: BpmLcdView
    private lateinit var padGrid: PadGridView
    private lateinit var zuLuFader: VFaderView
    private lateinit var flyFader: FlyFaderView
    private lateinit var mixFader: VFaderView
    private lateinit var glitchScope: GlitchScopeView
    private lateinit var btnMute: Button
    private lateinit var btnShift: Button
    private lateinit var btnFx: Button
    private lateinit var btnF: Button
    private lateinit var btnZ: Button
    private lateinit var btnRec: Button
    private lateinit var btnZu: Button
    private lateinit var btnLu: Button
    private lateinit var btnUndo: Button
    private lateinit var btnArp: Button
    private lateinit var btnPlay: Button
    private lateinit var playShape: PlayShapeDrawable
    private lateinit var fxOverlay: FrameLayout
    private lateinit var scaleOverlay: FrameLayout
    private lateinit var projOverlay: FrameLayout
    private lateinit var gridParams: LinearLayout.LayoutParams
    private val projSlotButtons = ArrayList<Button>()
    private lateinit var arpOverlay: FrameLayout
    private lateinit var padPane: FrameLayout

    private var fxOpen = false
    private var fxHeld = false
    private val fxHeldClear = Runnable { fxHeld = false }
    private var editFxMode = false
    private var fxConsumed = false
    private var activeFx = -1
    private val assignNames = listOf("CUTOFF", "RESO", "REV", "BITS", "VIT.", "LFO V.", "LFO P.", "DRIVE")
    private var assignXIdx = 0
    private var assignYIdx = 1
    private var btnAssignX: Button? = null
    private var btnAssignY: Button? = null
    private var loopLenSteps = 8
    private var loopWetA = false
    private var loopWetB = false
    private var activeScale = 0
    private var customScaleMask = 4095
    private var genScaleOn = true
    private var gridScaleOn = false
    private var keysScaleBtn: Button? = null
    private var activeRoot = 9
    private var arpOn = false
    private var arpMode = 0
    private var arpRateIdx = 1
    private var arpOctIdx = 0
    private var arpGateIdx = 1
    private var arpOctJump = false
    private var arpSlicesIdx = 2
    private val arpButtons = ArrayList<Button>()

    private var activeFilter = 0
    private var padAxisX = 0.5f
    private val userPresets = arrayOfNulls<UserPreset>(9)
    private val factorySlots = BooleanArray(9)
    private val bankDescs = arrayOf(
        "SPACE DUB : ECHO + DUB, LE DELAI TEMPO-SYNCHE ET SON FEEDBACK",
        "MODULANT : AUTOPAN + CHORUS + TREMOLO, TOUT BOUGE AUTOUR DE TOI",
        "TEMPETE : REPEAT + REVERSE + SCRATCH, LE TEMPS DERAILLE",
        "SALE : CRUSH + DRIVE + FUZZ, SATURATION LO-FI BIEN GRASSE",
        "GRANULEUX : GRAIN + CLOUD + SHIM, TEXTURE COSMIQUE",
        "STUDIO : EQ3 + COMP + WARM, POLI ET CHALEUREUX",
        "ACIDE : SYNTH 303 + RESO + DRIVE, LA BASSLINE CLASSIQUE",
        "DUB PLUCK : SYNTH PLUK + DUB + PLATE, L'ESPACE ET LA PROFONDEUR",
        "HARPE : SYNTH HARP1 + CHORUS + ECHO, ARPEGE CRYSTALLIN AVEC L'ARP ACTIF"
    )
    private var btnPunch: Button? = null

    private data class Snap(
        val ax: Float,
        val ay: Float,
        val bx: Float,
        val by: Float,
        val mix: Float,
        val filter: Int,
        val assignX: Int,
        val assignY: Int,
        val punch: BooleanArray? = null,
        val punchCol2: Int = 0,
        val motA: FloatArray? = null,
        val motB: FloatArray? = null,
        val motLen: Int = 0,
        val motMode: Int = 0,
        val motAmt: Float = 0.5f,
        val axMulti: List<Int>? = null,
        val ayMulti: List<Int>? = null,
        val axCurve: IntArray? = null,
        val ayCurve: IntArray? = null,
        val bxMulti: List<Int>? = null,
        val byMulti: List<Int>? = null,
        val bxCurve: IntArray? = null,
        val byCurve: IntArray? = null,
        val masterFxList: List<Int>? = null
    )
    private val snapPads = arrayOfNulls<Snap>(14)
    private var activeZu = -1
    private var activeLu = -1
    private var autoZu: Snap? = null
    private var autoLu: Snap? = null
    private val stepPitch = Array(8) { FloatArray(14) }
    private val stepVel = Array(8) { FloatArray(14) { 1f } }
    private val bankSteps = IntArray(8)
    private var stepEditBank = -1
    private var stepEditor: StepEditorView? = null
    private var stepTitle: TextView? = null
    private lateinit var stepOverlay: FrameLayout
    private var zuDownAt = 0L
    private var luDownAt = 0L
    private lateinit var menuOverlay: FrameLayout
    private val menuPadButtons = ArrayList<Button>()
    private var menuLoopLbl: Button? = null
    private var menuMirrorBtn: Button? = null
    private val menuCurveButtons = ArrayList<Button>()
    private val menuFingerButtons = ArrayList<Button>()
    private val menuArtButtons = ArrayList<Button>()
    private val menuStyleButtons = ArrayList<Button>()
    private var menuXFilesButton: Button? = null
    private val menuBgButtons = ArrayList<Button>()
    private val bgColors = listOf(
        "NOIR" to Color.rgb(16, 18, 23),
        "NUIT" to Color.rgb(10, 14, 26),
        "GB" to Color.rgb(13, 27, 18),
        "BRUN" to Color.rgb(28, 22, 15),
        "ARDOISE" to Color.rgb(25, 28, 33)
    )
    private val menuPatternButtons = ArrayList<Button>()
    private val menuZebraColorButtons = ArrayList<Button>()
    private val menuCatColorButtons = ArrayList<Button>()
    private val menuSirenButtons = ArrayList<Button>()
    private val menuSirenWaveButtons = ArrayList<Button>()
    private val catColors = listOf(
        "TENUE" to -1,
        "BLANC" to Color.WHITE,
        "ROUGE" to Color.rgb(210, 60, 50),
        "TURQ" to Color.rgb(40, 200, 180),
        "VIOLET" to Color.rgb(150, 90, 220),
        "AMBRE" to Color.rgb(255, 190, 0),
        "ROSE" to Color.rgb(255, 105, 180),
        "NOIR" to Color.rgb(20, 20, 24)
    )
    private val zebraColors = listOf(
        "BLANC" to Color.WHITE,
        "ROUGE" to Color.rgb(210, 60, 50),
        "TURQ" to Color.rgb(40, 200, 180),
        "VIOLET" to Color.rgb(150, 90, 220),
        "AMBRE" to Color.rgb(255, 190, 0)
    )
    private val menuEcoButtons = ArrayList<Button>()
    private var ecoOpt = false
    private var bgArtEcoPrev = 0
    private var lastDrumZone = -1
    private var padTouchDown = false
    private var bgArtOpt = 1
    private var menuFreezeBtn: Button? = null
    private val menuBillardButtons = ArrayList<Button>()
    private val menuLofiButtons = ArrayList<Button>()
    private val menuStretchButtons = ArrayList<Button>()
    private val bankStretch = IntArray(8)
    private val sampleParamVals = Array(8) { floatArrayOf(0.5f, 0f, 1f, 1f, 0f, 1f, 0f, 0f, 0.5f, 0f, 0f) }
    private val sampleParamAssign = IntArray(11)
    private val sampleParamDefs =
        listOf("PITCH", "ATTAQUE", "DECAY", "TONE", "D\u00c9BUT", "FIN", "STRETCH", "PING-PONG", "D\u00c9CALAGE", "GRILLE", "ONE-SHOT")
    private val bankSlotMask = IntArray(8) { 65535 }
    private val bankSlotN = IntArray(8) { 16 }
    private val bankSlotVol = Array(8) { FloatArray(16) { 1f } }
    private val bankSlotProb = Array(8) { FloatArray(16) { 1f } }
    private val bankSlotPitch = Array(8) { FloatArray(16) { 0.5f } }
    private val bankSlotPan = Array(8) { FloatArray(16) { 0.5f } }
    private val bankSlotToff = Array(8) { FloatArray(16) { 0.5f } }
    private val bankSlotRev = IntArray(8)
    private var lastSlotTapPad = -1
    private var lastSlotTapAt = 0L
    private var sampleEditBank = 0
    private var lastShiftPad = -1
    private var lastShiftPadAt = 0L
    private var shiftRecPending: Runnable? = null
    private val shiftHandler = Handler(Looper.getMainLooper())
    private val bankRev = BooleanArray(8)
    private var micOn = false
    private var inMuteSaved = 1f
    private var pendingMicRequest = false
    private val menuMicButtons = ArrayList<Button>()
    private val larsenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (micOn && inVol > 0.01f) {
                inMuteSaved = inVol
                inVol = 0f
                engine.nativeSetInGain(0f)
                if (::inKnob.isInitialized) inKnob.setVal(0f)
                showVuOverlay("IN MUET")
                bubble("CASQUE D\u00c9BRANCH\u00c9 \u00b7 LINE-IN COUP\u00c9", true)
            }
        }
    }
    private var recFlavor = 0
    private var menuPunchBtn: Button? = null
    private var punchMode = false
    private var punchView: PunchPadView? = null
    private var punchEditorDlg: android.app.AlertDialog? = null
    private var envDlg: android.app.AlertDialog? = null
    private var logDlg: android.app.AlertDialog? = null
    private var punchCol = 0
    private var paramView: ParamPageView? = null
    private var paramFx = -1
    private val fxParamInit = BooleanArray(84)
    private val fxParamVals = Array(84) { FloatArray(8) }
    private val fxParamAssign = Array(84) { IntArray(8) }
    private val fxParamDefs = mapOf(
        27 to listOf("TEMPS", "FB", "TONAL", "SAT"),
        28 to listOf("GRAVE", "AIGU", "DRIVE", "HUM"),
        29 to listOf("WOW", "DROP", "SAT", "SOUFFLE"),
        30 to listOf("PLAGE", "VITESSE", "LISSAGE", "NIVEAU"),
        31 to listOf("VITESSE", "GRAVE", "AIGU", "RESO"),
        32 to listOf("ACCORD", "AMORTI", "EXCIT", "NIVEAU"),
        33 to listOf("POSITION", "PITCH", "MODE", "NIVEAU"),
        34 to listOf("GRAIN", "PITCH", "SPRAY", "NIVEAU"),
        35 to listOf("DENSITE", "FOLIE", "REVERSE", "NIVEAU"),
        36 to listOf("SLICES", "RANDOM", "PITCH", "NIVEAU"),
        37 to listOf("GATE", "PITCH", "ORDRE", "NIVEAU"),
        38 to listOf("DENSITE", "TAILLE", "NUAGE", "NIVEAU"),
        39 to listOf("POINT", "TAILLE", "VITESSE", "NIVEAU"),
        40 to listOf("GRAVE", "MED", "AIGU", "NIVEAU"),
        41 to listOf("SEUIL", "RATIO", "REPRISE", "NIVEAU"),
        42 to listOf("DRIVE", "TONAL", "NIVEAU"),
        43 to listOf("GRAVE", "AIGU", "GATE", "NIVEAU"),
        44 to listOf("FUZZ", "TONAL", "NIVEAU"),
        45 to listOf("GAIN", "TONAL", "NIVEAU"),
        0 to listOf("TEMPS", "FB", "TONAL", "NIVEAU"),
        1 to listOf("TEMPS", "FB", "TONAL", "NIVEAU"),
        2 to listOf("VITESSE", "GRAVE", "AIGU", "NIVEAU"),
        3 to listOf("VITESSE", "PROF", "FORME", "NIVEAU"),
        4 to listOf("VITESSE", "FORME", "PROF", "NIVEAU"),
        5 to listOf("VITESSE", "PROF", "FB", "NIVEAU"),
        6 to listOf("VITESSE", "PROF", "SPREAD", "NIVEAU"),
        7 to listOf("FREQUENCE", "TIMBRE", "PROF", "NIVEAU"),
        8 to listOf("WOW", "FLUTTER", "SOUFFLE", "NIVEAU"),
        9 to listOf("VITESSE", "FENETRE", "FIGURE", "NIVEAU"),
        10 to listOf("TAILLE", "POINT", "TONAL", "NIVEAU"),
        11 to listOf("SEUIL", "GAIN", "REPRISE", "NIVEAU"),
        12 to listOf("VITESSE", "RELANCE", "PROF", "NIVEAU"),
        13 to listOf("DUREE", "POINT", "TONAL", "NIVEAU"),
        14 to listOf("TAILLE", "POINT", "TONAL", "NIVEAU"),
        15 to listOf("ACCORD", "AMORTI", "RICHESSE", "NIVEAU"),
        16 to listOf("TENSION", "RICHESSE", "ROULIS", "NIVEAU"),
        17 to listOf("GAIN", "ASYM", "REPLI", "NIVEAU"),
        18 to listOf("VITESSE", "PLAGE", "SENS", "NIVEAU"),
        19 to listOf("TAILLE", "PITCH", "ESPACE", "NIVEAU"),
        20 to listOf("TAILLE", "FOLIE", "SAUT", "NIVEAU"),
        21 to listOf("DUREE", "GRAVE", "REVERB", "NIVEAU"),
        22 to listOf("DRIVE", "CHALEUR", "GRAIN", "NIVEAU"),
        23 to listOf("VITESSE", "DRIVE", "TROU", "NIVEAU"),
        24 to listOf("DUREE", "GRAVE", "REVERB", "NIVEAU"),
        25 to listOf("DUREE", "COURBE", "NUAGE", "NIVEAU"),
        26 to listOf("PUNCH", "PROF", "FORME", "NIVEAU"),
        46 to listOf("TUNE", "ATTAQUE", "RELEASE", "NIVEAU"),
        51 to listOf("TUNE", "DECAY", "CLICK", "NIVEAU"),
        52 to listOf("TON", "DECAY", "GRAINS", "NIVEAU"),
        53 to listOf("TON", "DECAY", "PITCH", "NIVEAU"),
        58 to listOf("TUNE", "ATTAQUE", "RELEASE", "NIVEAU"),
        59 to listOf("TUNE", "ATTAQUE", "RELEASE", "NIVEAU"),
        60 to listOf("TUNE", "ATTAQUE", "RELEASE", "NIVEAU"),
        61 to listOf("CUT", "GLIDE", "RESO", "NIVEAU"),
        62 to listOf("TON", "DECAY", "MORDANT", "NIVEAU"),
        69 to listOf("TUNE", "FORMANT", "AIR", "NIVEAU"),
        70 to listOf("TUNE", "AMORTI", "RICHE", "NIVEAU"),
        71 to listOf("TUNE", "FORME", "PLI", "NIVEAU"),
        75 to listOf("DIVISION", "FB", "TONAL", "NIVEAU"),
        76 to listOf("TEMPS", "FB", "TONAL", "NIVEAU"),
        77 to listOf("TAILLE", "AMORTI", "TONAL", "NIVEAU"),
        78 to listOf("TAILLE", "OCTAVE", "TONAL", "NIVEAU"),
        79 to listOf("TEMPS", "SEUIL", "TONAL", "NIVEAU"),
        80 to listOf("TUNE", "PUNCH", "DECAY", "NIVEAU"),
        81 to listOf("TUNE", "VOYELLE", "MODE", "NIVEAU"),
        82 to listOf("TUNE", "BOIS", "DECAY", "NIVEAU"),
        83 to listOf("TUNE", "MÉTAL", "DECAY", "NIVEAU")
    )
    private val fxParamDefaults = mapOf(
        27 to floatArrayOf(0.45f, 0.8f, 0.22f, 0.2f),
        28 to floatArrayOf(0.38f, 0.44f, 0.42f, 0.33f),
        29 to floatArrayOf(0.5f, 0.56f, 0.15f, 0.33f),
        30 to floatArrayOf(0.5f, 0.14f, 0.16f, 0.71f),
        31 to floatArrayOf(0f, 0.5f, 0.4f, 0.5f),
        32 to floatArrayOf(0.33f, 0.76f, 0.33f, 0.63f),
        33 to floatArrayOf(0f, 0.5f, 0f, 0.6f),
        34 to floatArrayOf(0.3f, 0.55f, 0.2f, 0.6f),
        35 to floatArrayOf(0.4f, 0.4f, 0.3f, 0.6f),
        36 to floatArrayOf(0.4f, 0.5f, 0.5f, 0.6f),
        37 to floatArrayOf(0.4f, 0.5f, 0f, 0.6f),
        38 to floatArrayOf(0.4f, 0.3f, 0.25f, 0.6f),
        39 to floatArrayOf(0.3f, 0.3f, 0.5f, 0.6f),
        40 to floatArrayOf(0.65f, 0.5f, 0.6f, 0.6f),
        41 to floatArrayOf(0.35f, 0.35f, 0.4f, 0.6f),
        42 to floatArrayOf(0.35f, 0.5f, 0.6f),
        43 to floatArrayOf(0.3f, 0.7f, 0.2f, 0.6f),
        44 to floatArrayOf(0.45f, 0.5f, 0.55f),
        45 to floatArrayOf(0.4f, 0.55f, 0.5f),
        0 to floatArrayOf(0.27f, 0.42f, 0.5f, 0.5f),
        1 to floatArrayOf(0.5f, 0.6f, 0.5f, 0.5f),
        2 to floatArrayOf(0.33f, 0.5f, 0.5f, 0.5f),
        3 to floatArrayOf(0.5f, 0.5f, 0f, 0.5f),
        4 to floatArrayOf(0.5f, 0f, 0.5f, 0.5f),
        5 to floatArrayOf(0.5f, 0.33f, 0.5f, 0.5f),
        6 to floatArrayOf(0.5f, 0.33f, 0.5f, 0.5f),
        7 to floatArrayOf(0.5f, 0f, 0.5f, 0.5f),
        8 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        9 to floatArrayOf(0.25f, 0.2f, 0f, 0.5f),
        10 to floatArrayOf(1f, 0f, 0.5f, 0.5f),
        11 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        12 to floatArrayOf(0.5f, 0.5f, 1f, 0.5f),
        13 to floatArrayOf(0.25f, 0f, 0.5f, 0.5f),
        14 to floatArrayOf(0.5f, 1f, 0.5f, 0.5f),
        15 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        16 to floatArrayOf(0.5f, 0.5f, 0f, 0.5f),
        17 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        18 to floatArrayOf(0.5f, 0.5f, 0f, 0.5f),
        19 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        20 to floatArrayOf(0.45f, 0.6f, 0.5f, 0.7f),
        21 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        22 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        23 to floatArrayOf(0.25f, 0.5f, 0.5f, 0.5f),
        24 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        25 to floatArrayOf(0.5f, 0.5f, 0f, 0.5f),
        26 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        46 to floatArrayOf(0.5f, 0f, 0.5f, 0.5f),
        51 to floatArrayOf(0.5f, 0.5f, 0.2f, 0.5f),
        52 to floatArrayOf(0.5f, 0.5f, 0.33f, 0.5f),
        53 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        58 to floatArrayOf(0.5f, 0f, 0.5f, 0.5f),
        59 to floatArrayOf(0.5f, 0f, 0.5f, 0.5f),
        60 to floatArrayOf(0.5f, 0f, 0.5f, 0.5f),
        61 to floatArrayOf(0.5f, 0.35f, 0.5f, 0.5f),
        62 to floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f),
        69 to floatArrayOf(0.5f, 0.35f, 0.3f, 0.6f),
        70 to floatArrayOf(0.5f, 0.4f, 0.7f, 0.6f),
        71 to floatArrayOf(0.5f, 0.35f, 0.25f, 0.6f),
        75 to floatArrayOf(0.35f, 0.5f, 0.3f, 0.5f),
        76 to floatArrayOf(0.4f, 0.5f, 0.3f, 0.5f),
        77 to floatArrayOf(0.5f, 0.25f, 0.4f, 0.6f),
        78 to floatArrayOf(0.5f, 0.35f, 0.4f, 0.5f),
        79 to floatArrayOf(0.4f, 0.25f, 0.4f, 0.6f),
        80 to floatArrayOf(0.5f, 0.5f, 0.3f, 0.5f),
        81 to floatArrayOf(0.5f, 0.3f, 0.5f, 0.5f),
        82 to floatArrayOf(0.5f, 0.4f, 0.3f, 0.5f),
        83 to floatArrayOf(0.5f, 0.4f, 0.4f, 0.5f)
    )
    private var sendPadBank = -1
    private var sendPadAt = 0L
    private var sendComboStart = true
    private var muteComboAt = 0L
    private var miniMixMode = false
    private val sampGroupVol = FloatArray(4) { 1f }
    private val fxBankLevel = FloatArray(9) { 1f }
    private var powLevel = 1f
    private var sampleMoveMode = false
    private var sosMode = false
    private var globalSaveArmed = false
    private var groupMode = false
    private var activeGroupSel = 1
    private var muteComboCount = 0
    private var axisMuteCombo = false
    private var lastAxisMuteAt = 0L
    private var lastFuseAt = 0L
    private var fuseDelayMs = 1000
    private val fuseHandler = Handler(Looper.getMainLooper())
    private var fusePending: Runnable? = null
    private var fzFusedAt = 0L
    private var globalSaveAt = 0L
    private val bankFxMask = Array(8) { BooleanArray(84) }
    private val bankOn = BooleanArray(8)
    private val rackBackup = BooleanArray(84)
    private var rackBackedUp = false
    private var heldFxSlot = -1
    private var fxPickBank = -1
    private var heldBankPad = -1
    private val presetBtns = ArrayList<Button>()
    private var arpBtn: Button? = null
    private val bankFxSend = BooleanArray(8) { true }
    private var fHeld = false
    private var zHeld = false
    private val menuProjButtons = ArrayList<Button>()
    private var morphCurve = 0
    private var fingerModeOpt = 0
    private var bendFreezeOpt = false
    private var bendAmtOpt = 1f
    private var bendRateOpt = 1f
    private var bendXyOn = true
    private var bendMacroOn = true
    private var bendZuluOn = true
    private var bendZuluMode = -1
    private var bendXyMode = 0
    private var bendMacroMode = -1
    private var synWaveIdx = 0
    private var synAtkIdx = 0
    private var synDecIdx = 1
    private var synOctIdx = 2
    private var synPitIdx = 2
    private var synTunIdx = 1
    private val lfoShapes = Array(2) { FloatArray(64) { 0.5f } }
    private val lfoOn = BooleanArray(2)
    private val lfoRateIdxArr = IntArray(2) { 2 }
    private val lfoDepthIdxArr = IntArray(2) { 2 }
    private val lfoAmountIdxArr = IntArray(2) { 3 }
    private val lfoTargets = IntArray(2) { -1 }
    private val lfoWaves = IntArray(2) { 0 }
    private var lfoSel = 0
    private var lfoDraw: LfoDrawView? = null
    private var arpTargetTg = -1
    private var arpAmountIdx = 1
    private var lfoBrowserFor = 0
    private var lfoBrowserSec = -1
    private var lfoBrowserBox: LinearLayout? = null
    private var importTargetBank = -1
    private val reqImport = 4242
    private val reqCapture = 4243
    private var appCaptureOn = false
    private var captureProj: MediaProjection? = null
    private var captureRec: AudioRecord? = null
    @Volatile private var captureRun = false

    private data class UserPreset(
        val filter: Int,
        val scale: Int,
        val root: Int,
        val arpOn: Boolean,
        val arpMode: Int,
        val arpRate: Int,
        val arpOct: Int,
        val arpGate: Int,
        val arpOctJump: Boolean,
        val arpSlices: Int,
        val assignX: Int,
        val assignY: Int,
        val fxOn: BooleanArray,
        val fxParams: Array<FloatArray>,
        val fxAssign: Array<IntArray>,
        val lfoTg: IntArray,
        var gen: Int = -1
    )
    private val fxButtons = ArrayList<Button>()
    private val fxButtonCols = ArrayList<Int>()

    private val fxCatCols = listOf(
        Color.rgb(0, 240, 255),
        Color.rgb(185, 110, 255),
        Color.rgb(70, 255, 130),
        Color.rgb(255, 190, 0),
        Color.rgb(255, 70, 205),
        Color.rgb(236, 224, 196),
        Color.rgb(235, 255, 40),
        Color.rgb(255, 120, 80),
        Color.rgb(0, 255, 170),
        Color.rgb(190, 190, 255)
    )

    private val fxSections = listOf(
        "FILTRE" to listOf("LPF", "HPF", "BPF", "NOTCH", "PHASER"),
        "MODULATION" to listOf("TREMOLO", "AUTOPAN", "FLANGER", "CHORUS", "RING", "TALK", "WARP"),
        "TEMPS" to listOf("DELAY", "ECHO", "DUB", "REVERB", "FREEZE", "STOP", "REPEAT", "REVERSE", "SCRATCH", "TEMPO", "PING", "PLATE", "SHIM", "GATE"),
        "TEXTURE" to listOf("CRUSH", "DRIVE", "LO-FI", "TAPE", "GATER", "FOLD", "MEGA", "FUZZ", "DISTO"),
        "RESONANCE" to listOf("RESO", "SPRING", "RINGS"),
        "GRANULAIRE" to listOf("GRAIN", "JUNGLE"),
        "TRANSITIONS" to listOf("TRANSIT", "COMBUSTOR", "TURNADO", "FALL", "SWELL", "PUMP"),
        "SYNTH" to listOf("SAW", "SQUARE", "FM", "NOISE", "303", "KICK", "SNARE", "HAT", "PERC", "VOX", "PLUK", "TIDE", "KIK8", "VOY", "MAL", "BELL", "SIR1", "SIR2", "SIR3", "SIR4", "HARP1", "HARP2", "HARP3"),
        "LECTURE SAMPLE" to listOf("SPLY", "GRAN", "JGL", "RND", "SLICR", "CLOUD", "FRZ"),
        "STUDIO" to listOf("EQ3", "COMP", "WARM", "CLEAN")
    )

    private val fxCodes = listOf(
        "F0", "F1", "F2", "F3", "X2",
        "X3", "X4", "X5", "X6", "X7", "X31", "X30",
        "X0", "X1", "X27", "P2", "X10", "X12", "X13", "X14", "X18",
        "X46", "X47", "X48", "X49", "X50",
        "P3", "P7", "X8", "X29", "X9", "X17", "X28", "X44", "X45",
        "X15", "X16", "X32",
        "X19", "X20",
        "X21", "X22", "X23", "X24", "X25", "X26",
        "G0", "G13", "G14", "G12", "G15", "G5", "G6", "G7", "G16", "G17", "G18", "G19",
        "G20", "G21", "G22", "G23", "G25", "G26", "G27", "G28", "G29", "G30", "G31",
        "X33", "X34", "X35", "X36", "X37", "X38", "X39",
        "X40", "X41", "X42", "X43"
    )
    private val fxActive = BooleanArray(84)

    private fun fxSlot(id: Int): Int = if (id < 46) id else 75 + (id - 46)

    private fun midiOut(key: String, v: Float) {
        val rec = midiLoopRec.indexOfFirst { it }
        if (rec >= 0) {
            midiLoopEvents[rec].add(Triple(System.currentTimeMillis() - midiLoopStart[rec], key, v.coerceIn(0f, 1f)))
        }
        if (ZuluMidi.enabled || ZuluMidi.teach) ZuluMidi.control(key, v.coerceIn(0f, 1f))
    }
    private fun genSlot(num: Int): Int = when (num) {
        17 -> 69
        18 -> 70
        19 -> 71
        else -> if (num < 20) 46 + num else 80 + (num - 20)
    }
    private val paramActive = BooleanArray(8)
    private val paramDefaults = floatArrayOf(0f, 0f, 0.35f, 0.3f, 0f, 0f, 0f, 0.4f)
    private var genIdx = -1

    private val yellow = Color.rgb(235, 255, 40)
    private val pink = Color.rgb(255, 70, 205)
    private val green = Color.rgb(70, 255, 130)
    private val cream = Color.rgb(236, 224, 196)
    private val silk = Color.rgb(165, 172, 185)
    private val amber = Color.rgb(255, 190, 0)
    private val turquoise = Color.rgb(0, 240, 255)
    private val violet = Color.rgb(185, 110, 255)
    private val red = Color.rgb(255, 70, 55)

    private var playing = false
    private var simulLock = false
    private var sceneMode = 0
    private var bSnapBtn: Button? = null
    private var bMotionBtn: Button? = null
    private var verticalUi = false
    private val menuOrientButtons = ArrayList<Button>()
    private val menuSnapButtons = ArrayList<Button>()
    private val menuInButtons = ArrayList<Button>()
    private var inProfileSel = 0
    private val menuInFButtons = ArrayList<Button>()
    private val menuOutFButtons = ArrayList<Button>()
    private val menuCompButtons = ArrayList<Button>()
    private val menuVolButtons = ArrayList<Button>()
    private val menuCapButtons = ArrayList<Button>()
    private val menuMidiOutButtons = ArrayList<Button>()
    private var menuMidiChButton: Button? = null
    private var menuMidiTeachButton: Button? = null
    private var menuMidiCntButton: Button? = null
    private var masterCompSel = 0
    private var inFSel = 1
    private var outFSel = 1
    private var inFilVal = 0f
    private var outFilVal = 0f
    private var sirenPrevFilter = 0f
    private var sirenPrevFxDub = false
    private var sirenPrevDubParams = FloatArray(8)
    private var sirenType = 0
    private var sirenWave = 0
    private var catColorSel = -1
    private lateinit var sirenPad: SirenPadView
    private val bankCols = intArrayOf(
        Color.rgb(255, 150, 50), Color.rgb(0, 229, 255), Color.rgb(190, 120, 255),
        Color.rgb(70, 255, 130), Color.rgb(255, 70, 205), Color.rgb(255, 220, 60),
        Color.rgb(255, 90, 70), Color.rgb(90, 140, 255), Color.rgb(198, 42, 140)
    )
    private var lastParamK = 0
    private var lastFxTapAt = 0L
    private var volSel = 1
    private val menuEqButtons = ArrayList<Button>()
    private var masterEqOn = false
    private val menuBoostInButtons = ArrayList<Button>()
    private val menuBoostOutButtons = ArrayList<Button>()
    private var inBoostSel = 0
    private var outBoostSel = 0
    private val menuLoudButtons = ArrayList<Button>()
    private var outProfSel = 0
    private val menuNetButtons = ArrayList<Button>()
    private var netModeSel = 0
    private val menuNetChaosButtons = ArrayList<Button>()
    private var netChaosSel = 1
    private val menuNetModButtons = ArrayList<Button>()
    private var netModSel = 1
    private val menuNetAliasButtons = ArrayList<Button>()
    private var netAliasSel = 0
    private val menuNetSpeedButtons = ArrayList<Button>()
    private var netSpeedSel = 1
    private val masterFx = HashSet<Int>()
    private val snapFxOverride = HashMap<Int, Boolean>()
    private val projNames = HashMap<Int, String>()
    private val projCols = HashMap<Int, Int>()
    private var projPage = 0
    private var projPageLabel: TextView? = null
    private val autoSlot = 99
    private var skipSessionSave = false
    private var lastProjTapSlot = -1
    private var lastProjTapAt = 0L
    private val bankLabels = Array(8) { "" }
    private val bankRecAt = LongArray(8)
    private var showSampleLog = true
    private var sampleLogView: TextView? = null
    private val menuLogButtons = ArrayList<Button>()
    private val menuNameButtons = ArrayList<Button>()
    private val axisMulti = Array(4) { ArrayList<Int>() }
    private val axisCurve = Array(4) { IntArray(10) }
    private var assignArm = -1
    private var assignBlinkOn = false
    private val curveNames = listOf("NORM", "INV", "S&H", "BOUILLON")
    private var snapTransp = true
    private var snapHold = false
    private val snapHoldOld = HashMap<String, Float>()
    private val snapHoldFx = HashMap<Int, FloatArray>()
    private var fRecTap = false
    private var zRecTap = false
    private var sceneSide = 0
    private var toolPage = 0
    private var playerBank = 0
    private var recArmed = false
    private var recArmPad = -1
    private var recSnapSide = -1
    private var recActive = false
    private var recMotion = false
    private var motionSrcA = false
    private var motionSrcB = false
    private var motionPlaying = -1
    private var motionAmp = 1f
    private var motionPhase = 0f
    private var playAx = 0.5f
    private var playAy = 0.5f
    private var playBx = 0.5f
    private var playBy = 0.5f
    private val motA = ArrayList<Float>()
    private val motB = ArrayList<Float>()
    private val motionHandler = Handler(Looper.getMainLooper())
    private val seqHandler = Handler(Looper.getMainLooper())
    private val midiLoops = Array(8) { ArrayList<FloatArray>() }
    private val midiPlaying = BooleanArray(8)
    private val midiStartMs = LongArray(8)
    private val midiLastPos = FloatArray(8) { -1f }
    private var recMidiEngines = true
    private var recStartMs = 0L
    private var recLenMs = 0L
    private var recResampleFx = false
    private var lenEntry = false
    private var lenFirst = 0
    private var masterVol = 1f
    private var inVol = 1f
    private val lenHandler = Handler(Looper.getMainLooper())
    private val vuOverlayHandler = Handler(Looper.getMainLooper())
    private val punchLatched = BooleanArray(9)
    private var lastEditBank = -1
    private var lastEditAt = 0L
    private lateinit var drumGrid: DrumGridView
    private lateinit var miniKeys: MiniKeysView
    private lateinit var keysWrap: LinearLayout
    private lateinit var audioConsole: FrameLayout
    private lateinit var timeSigConsole: FrameLayout
    private var timeSigBeats = 4
    private val menuTimeSigButtons = ArrayList<Button>()
    private val consoleInProfileBtns = ArrayList<Button>()
    private val consoleCompBtns = ArrayList<Button>()
    private val consoleEqBtns = ArrayList<Button>()
    private var keysAtkScale = 1f
    private var keysDecScale = 1f
    private var keysOctShift = 0
    private var keysGlide = 0f
    private var keysFilterFree = false
    private var keysGlideTarget = -1f
    private var keysGlideCur = 0f
    private val keysGlideHandler = Handler(Looper.getMainLooper())
    private val keysOctBtns = ArrayList<Button>()
    private lateinit var toolsMenu: FrameLayout
    private val keysBanks = HashSet<Int>()
    private val keysGens = HashSet<Int>()
    private val keysHeld = HashSet<Int>()
    private val modeBtnArray = ArrayList<Button>()
    private val midiLoopEvents = Array(8) { ArrayList<Triple<Long, String, Float>>() }
    private val midiLoopOn = BooleanArray(8)
    private val midiLoopRec = BooleanArray(8)
    private val midiLoopStart = LongArray(8)
    private val midiLoopLen = LongArray(8)
    private val midiLoopIdx = IntArray(8)
    private val midiLoopLastT = LongArray(8)
    private val midiLoopKeys = Array(8) { ArrayList<String>() }
    private val midiLoopCells = Array(8) { Array(6) { FloatArray(16) { -1f } } }
    private lateinit var midiSeqView: MidiSeqView
    private lateinit var midiSeqWrap: LinearLayout
    private var midiSeqSel = 0
    private val midiLoopBtns = ArrayList<Button>()
    private lateinit var drumSeqView: DrumSeqView
    private lateinit var drumSeqWrap: LinearLayout
    private var drumSeqRunning = false
    private var drumSeqStartMs = 0L
    private var drumSeqLastCol = -1
    private var drumSeqRunBtn: Button? = null
    private var drumLiveRec = false
    private var drumLiveRecBtn: Button? = null
    private var drumMuted = false
    private var drumFxForce = true
    private var drumsDownAt = 0L
    private var fxDownAt = 0L
    private var drumFxSimulAt = 0L
    private val drumTargetNames = listOf("DRM LOUD", "DRM AIR", "DRM DRIVE", "DRM FILTRE", "DRM DECAY")
    private val drumGlitchOn = BooleanArray(7) { true }
    private val drumMutOn = BooleanArray(7) { true }
    private var drumGlitchAmt = 0f
    private var drumMutAmt = 0f
    private var drumSeqBtn: Button? = null
    private var drumSeqLastFrac = 0f
    private val drumSeqRowCol = IntArray(7) { -1 }
    private val drumSeqGens = intArrayOf(5, 6, 7, 16, 20, 22, 24)
    private val drumVoiceSlots = intArrayOf(51, 52, 53, 62, 80, 82, 83)
    private var drumVoiceSel = 0
    private val drumVoiceBtns = ArrayList<Button>()
    private val drumVoiceSeeks = ArrayList<SeekBar>()
    private val drumVoiceLbls = ArrayList<TextView>()
    private val drumVoiceMacSeeks = ArrayList<SeekBar>()
    private val drumDecDepth = FloatArray(7) { 1f }
    private val drumDriveDepth = FloatArray(7) { 1f }
    private lateinit var keysSoundPanel: LinearLayout
    private val keysEngineBtns = ArrayList<Button>()
    private val keysBankBtns = ArrayList<Button>()
    private var keysSoundLbl: TextView? = null
    private lateinit var inKnob: LineInKnob
    private val menuDrumButtons = ArrayList<Button>()
    private val menuFuseDelayBtns = ArrayList<Button>()
    private val menuMidiButtons = ArrayList<Button>()
    private var resampling = false
    private var muteOn = false
    private val soloGroups = booleanArrayOf(false, false, false, false, false, false, false, false)
    private var shiftHeld = false
    private var shiftLocked = false
    private var recHeld = false
    private var blinkOn = true
    private var axisAOn = true
    private var axisBOn = true
    private var axisAHold = false
    private var axisBHold = false
    private var fDownAt = 0L
    private var zDownAt = 0L
    private var mirrored = false
    private lateinit var content: LinearLayout
    private var lastShiftUp = 0L

    private val blinkHandler = Handler(Looper.getMainLooper())
    private val blinkRunnable = object : Runnable {
        override fun run() {
            blinkOn = !blinkOn
            refreshBlink()
            blinkHandler.postDelayed(this, 400)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ZuluMidi.onTeach = { k, cc ->
            runOnUiThread {
                bubble("MIDI TEACH : $k \u2192 CC$cc")
                refreshMenu()
            }
        }
        verticalUi = getSharedPreferences("zulu", MODE_PRIVATE).getBoolean("verticalUi", false)
        snapTransp = getSharedPreferences("zulu", MODE_PRIVATE).getBoolean("snapTransp", true)
        bendAmtOpt = getSharedPreferences("zulu", MODE_PRIVATE).getFloat("bendAmt", 1f)
        bendRateOpt = getSharedPreferences("zulu", MODE_PRIVATE).getFloat("bendRate", 1f)
        bendXyOn = getSharedPreferences("zulu", MODE_PRIVATE).getBoolean("bendXyOn", true)
        bendMacroOn = getSharedPreferences("zulu", MODE_PRIVATE).getBoolean("bendMacroOn", true)
        bendZuluOn = getSharedPreferences("zulu", MODE_PRIVATE).getBoolean("bendZuluOn", true)
        bendZuluMode = getSharedPreferences("zulu", MODE_PRIVATE).getInt("bendZuluMode", -1)
        bendXyMode = getSharedPreferences("zulu", MODE_PRIVATE).getInt("bendXyMode", 0)
        engine.nativeSetBendMode(bendXyMode)
        bendMacroMode = getSharedPreferences("zulu", MODE_PRIVATE).getInt("bendMacroMode", -1)
        engine.nativeSetBendParams(bendAmtOpt, bendRateOpt)
        showSampleLog = getSharedPreferences("zulu", MODE_PRIVATE).getBoolean("sampleLog", true)
        val zp = getSharedPreferences("zulu", MODE_PRIVATE)
        val stIdx = zp.getInt("styleIdx", 0)
        ZuluTheme.retro = stIdx == 1
        ZuluTheme.gameboy = stIdx == 2
        ZuluTheme.electribe = stIdx == 3
        ZuluTheme.caustic = stIdx == 4
        ZuluTheme.bgColor = zp.getInt("bgColor", ZuluTheme.bgColor)
        ZuluTheme.fontId = zp.getInt("fontId", 0)
        timeSigBeats = zp.getInt("timeSig", 4)
        ZuluTheme.bgDots = zp.getBoolean("bgDots", false)
        ZuluTheme.zebraWidthDp = zp.getFloat("zebraWidth", 12f)
        ZuluTheme.dotSizeDp = zp.getFloat("dotSize", 3f)
        ZuluTheme.zebraColor = zp.getInt("zebraColor", Color.WHITE)
        sirenType = zp.getInt("sirenType", 0)
        sirenWave = zp.getInt("sirenWave", 0)
        catColorSel = zp.getInt("catColor", -1)
        ZuluTheme.xFiles = zp.getBoolean("xFiles", false)
        xyHelpOpt = zp.getBoolean("xyHelp", true)
        macroMode = zp.getBoolean("macroMode", false)
        fuseDelayMs = zp.getInt("fuseDelay", 1000)
        customScaleMask = zp.getInt("customScaleMask", 4095)
        engine.nativeSetCustomScaleMask(customScaleMask)
        genScaleOn = zp.getBoolean("genScaleOn", true)
        gridScaleOn = zp.getBoolean("gridScaleOn", false)
        engine.nativeSetGenScaleOn(genScaleOn)
        engine.nativeSetGridScaleOn(gridScaleOn)
        val mfStr = zp.getString("macroFaders", null)
        if (mfStr != null) {
            mfStr.split("|").forEachIndexed { f, row ->
                if (f >= 6) return@forEachIndexed
                macroFaderTargets[f].clear()
                row.split(";").forEach { cell ->
                    if (cell.isEmpty()) return@forEach
                    val tk = cell.split(",")
                    if (tk.size == 3) {
                        macroFaderTargets[f].add(Triple(tk[0].toInt(), tk[1].toInt(), tk[2] == "1"))
                    }
                }
            }
        }
        inProfileSel = zp.getInt("inProfile", 0)
        outProfSel = zp.getInt("outProfile", 0)
        inFilVal = zp.getFloat("inFilVal", 0f)
        outFilVal = zp.getFloat("outFilVal", 0f)
        inFSel = if (inFilVal < -0.02f) 0 else if (inFilVal > 0.02f) 2 else 1
        outFSel = if (outFilVal < -0.02f) 0 else if (outFilVal > 0.02f) 2 else 1
        masterVol = zp.getFloat("masterVol", 1f)
        inVol = zp.getFloat("inVol", 1f)
        requestedOrientation =
            if (verticalUi) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val d = resources.displayMetrics.density
        val margin = (24 * d).toInt()

        pad = ZuluPad(this)
        vuMeter = BarVuView(this)
        bpmLcd = BpmLcdView(this)
        padGrid = PadGridView(this)
        padGrid.bankTints = bankCols
        padGrid.padCount = 16
        zuLuFader = VFaderView(this, "ZU/LU", blendMorph = true)
        flyFader = FlyFaderView(this)
        mixFader = VFaderView(this, "MIX", thumbColor = Color.rgb(70, 255, 130))
        mixFader.value = 1f
        glitchScope = GlitchScopeView(this)
        glitchScope.visibility = View.GONE

        pad.waveform = placeholderWaveform()
        pad.onAxisChange = { ax, ay, bx, by ->
            lastAxX = ax
            lastAxY = ay
            midiOut("XYX", ax)
            midiOut("XYY", ay)
            if (!(snapHold && snapTransp)) {
                if (pad.aLive()) {
                    engine.nativeSetAx(ax)
                    engine.nativeSetAy(ay)
                    if (arpOn && arpMode == 4) engine.nativeSetArpXy(ax, true)
                }
                if (pad.bLive()) {
                    engine.nativeSetBx(bx)
                    engine.nativeSetBy(by)
                }
            }
            if (padTouchDown && (genIdx == 5 || genIdx == 6 || genIdx == 7 || genIdx == 16 ||
                    genIdx == 20 || genIdx == 22 || genIdx == 23)) {
                val col = (ax.coerceIn(0f, 0.999f) * 3f).toInt()
                val row = (ay.coerceIn(0f, 0.999f) * 3f).toInt()
                val zone = row * 3 + col
                if (zone != lastDrumZone) {
                    lastDrumZone = zone
                    engine.nativeGenHit(genIdx, ax, ay)
                }
            }
            padAxisX = ax
        }
        pad.onPadTouchState = { t ->
            padTouchDown = t
            engine.nativeSetGenTouch(t)
            if (!t) {
                engine.nativeSetArpXy(padAxisX, false)
                lastDrumZone = -1
            }
        }
        pad.onBendTouch = { down ->
            val bend = down && shiftLocked && bendXyOn
            pad.bendActive = bend
            pad.bendDying = bend && bendXyMode == 1
            if (bend) {
                if (activeFx < 0) {
                    val r = (0..3).random()
                    activeFilter = r
                    engine.nativeSetFilterType(r)
                    activeFx = r
                    assignXIdx = (0..6).random()
                    assignYIdx = (0..6).random()
                    engine.nativeSetAssign(0, assignXIdx)
                    engine.nativeSetAssign(1, assignYIdx)
                    refreshAssignLabels()
                    refreshFxHighlight()
                }
                glitchScope.visibility = View.VISIBLE
                glitchScope.start()
            } else {
                engine.nativeSetBend(false, 0.5f, 0.5f)
                glitchScope.stop()
            }
        }
        pad.onBendPos = { x, y ->
            if (pad.bendActive) engine.nativeSetBend(true, x, y)
        }
        pad.onMomOffA = {
            engine.nativeSetAx(0.5f)
            engine.nativeSetAy(0.5f)
        }
        pad.onMomOffB = {
            engine.nativeSetBx(0.5f)
            engine.nativeSetBy(0.5f)
        }
        pad.shiftCheck = { shiftActive() }
        pad.onFrictionCycle = {
            pad.billardFriction = (pad.billardFriction + 1) % 5
            val lbl = listOf("\u221e", "0.5 s", "1 s", "2 s", "4 s")[pad.billardFriction]
            bubble("INERTIE $lbl")
            refreshMenu()
        }

        btnMute = makeRoundButton("MUTE", silk, 10f)
        btnMute.setTextColor(Color.rgb(14, 16, 20))
        btnMute.background = axisSolidBg(amber, false)
        btnShift = makeRoundButton("SHIFT", silk, 10f)
        btnShift.setTextColor(Color.rgb(14, 16, 20))
        btnShift.background = axisSolidBg(Color.rgb(80, 170, 255), false)
        btnFx = makeRoundButton("FX", amber, 13f)
        btnF = makeButton("\u03a8", yellow)
        btnZ = makeButton("\u039e", pink)
        btnRec = makeRoundButton("REC", red, 12f)
        btnRec.setTextColor(Color.rgb(255, 240, 235))
        btnRec.background = axisSolidBg(red, false)
        btnZu = makeButton("ZU", turquoise)
        btnZu.setTextColor(Color.rgb(14, 16, 20))
        btnZu.background = axisSolidBg(turquoise, true)
        btnLu = makeButton("LU", violet)
        btnLu.setTextColor(Color.rgb(14, 16, 20))
        btnLu.background = axisSolidBg(violet, true)
        btnUndo = makeRoundButton("UNDO", turquoise, 12f)
        btnUndo.setOnClickListener {
            if (sosHint("UNDO : ANNULE LA DERNI\u00c8RE ENREGISTREMENT DE SAMPLE.")) return@setOnClickListener
            engine.nativeUndo()
            bubble("UNDO REC")
        }
        btnArp = makeRoundButton("TOOLS", pink, 12f)
        btnArp.setOnClickListener {
            if (sosHint("TOOLS : BO\u00cete \u00c0 OUTILS \u2014 CLAVIER, GLIDEBOARD, SCALE, LOOPS MIDI...")) return@setOnClickListener
            arpTap()
        }
        btnPlay = makeRoundButton("PLAY", green, 13f)
        btnPlay.text = ""
        playShape = PlayShapeDrawable()
        btnPlay.background = playShape
        btnPlay.setOnClickListener {
            if (sosHint("PLAY/STOP : LANCE OU ARR\u00caTE LE MOTEUR AUDIO.")) return@setOnClickListener
            togglePlay()
        }

        btnZu.setOnTouchListener { _, e ->
            if (sosMode && e.actionMasked == MotionEvent.ACTION_DOWN) {
                sosHint("ZU : CAPTURE ET RAM\u00c8NE LA SC\u00c8NE VERS LE C\u00d4T\u00c9 TURQUOISE DU FADER.")
                return@setOnTouchListener true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    zuDownAt = System.currentTimeMillis()
                    btnZu.text = "ZU\u00b7CAP"
                    if (recArmed) {
                        recSnapSide = 0
                        recArmPad = -1
                        padGrid.recPad = -1
                        bubble("SNAPSHOT ZU : CHOISIR PAD")
                    } else {
                        sceneSide = 0
                        refreshSceneSide()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    btnZu.text = "ZU"
                    if (!recArmed && System.currentTimeMillis() - zuDownAt > 500) {
                        val idx = activeZu
                        if (idx >= 0) {
                            captureSnap(0, idx)
                        } else {
                            bubble("ZU : TAP UNE BANQUE D'ABORD")
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> btnZu.text = "ZU"
            }
            true
        }
        btnLu.setOnTouchListener { _, e ->
            if (sosMode && e.actionMasked == MotionEvent.ACTION_DOWN) {
                sosHint("LU : CAPTURE ET RAM\u00c8NE LA SC\u00c8NE VERS LE C\u00d4T\u00c9 VIOLET DU FADER.")
                return@setOnTouchListener true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    luDownAt = System.currentTimeMillis()
                    btnLu.text = "LU\u00b7CAP"
                    if (recArmed) {
                        recSnapSide = 1
                        recArmPad = -1
                        padGrid.recPad = -1
                        bubble("SNAPSHOT LU : CHOISIR PAD")
                    } else {
                        sceneSide = 1
                        refreshSceneSide()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    btnLu.text = "LU"
                    if (!recArmed && System.currentTimeMillis() - luDownAt > 500) {
                        val idx = activeLu
                        if (idx >= 0) {
                            captureSnap(1, idx)
                        } else {
                            bubble("LU : TAP UNE BANQUE D'ABORD")
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> btnLu.text = "LU"
            }
            true
        }

        btnFx.setOnClickListener {
            if (sosHint("FX : RACK D'EFFETS ET BANQUES. APPUI LONG SUR UNE BANQUE = SON MENU. MAINTENU + TAP BANQUE = SAUVEGARDE.")) return@setOnClickListener
            if (toggleDrumFxSimul(drumsDownAt)) return@setOnClickListener
            if (axisFlowStage > 0 || macroFlowStage > 0) {
                axisFlowStage = 0
                axisFlowAxis = -1
                macroFlowStage = 0
                macroFlowFader = -1
                closeParamPage()
                bubble("FLOW ANNUL\u00c9")
                return@setOnClickListener
            }
            if (paramView?.visibility == View.VISIBLE) {
                closeParamPage()
                if (fxOpen) closeFx()
            } else if (fxConsumed) {
                fxConsumed = false
            } else if (fxOpen) {
                closeFx()
            } else {
                openFx()
            }
        }
        btnFx.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fxDownAt = System.currentTimeMillis()
                    blinkHandler.removeCallbacks(fxHeldClear)
                    fxHeld = true
                    fxConsumed = false
                    fxDownY = e.y
                    fxSwiped = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.y - fxDownY
                    if (!fxSwiped && dy < -(16 * d)) {
                        fxSwiped = true
                        fxConsumed = true
                        if (macroPanel.visibility == View.VISIBLE) hideMacroPanel() else showMacroPanel()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    blinkHandler.postDelayed(fxHeldClear, 1500)
            }
            false
        }

        vuMeter.onPressed = { _ -> checkSimulStart() }
        bpmLcd.onPressed = { _ -> checkSimulStart() }

        bpmLcd.onBpmChange = { b -> engine.nativeSetBpm(b); midiOut("BPM", (b - 40f) / 160f) }

        btnShift.setOnTouchListener { _, e ->
            if (sosMode && e.actionMasked == MotionEvent.ACTION_DOWN) {
                sosHint("SHIFT : COMBO \u2014 SHIFT+PAD = AFFECTATIONS, SHIFT+GRILLER = R\u00c9GLAGES, MAINTENU = FONCTIONS SECONDES.")
                return@setOnTouchListener true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (shiftLocked) {
                        shiftLocked = false
                    } else {
                        shiftHeld = true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (shiftHeld) {
                        val now = System.currentTimeMillis()
                        if (now - lastShiftUp < 300) {
                            if (heldBankPad in 1..8 && engine.nativeBankHasLoop(heldBankPad - 1)) {
                                val b = heldBankPad - 1
                                bankRev[b] = !bankRev[b]
                                engine.nativeSetBankRev(b, bankRev[b])
                                bubble("B${b + 1} " + if (bankRev[b]) "REVERSE" else "NORMAL")
                            } else {
                                shiftLocked = true
                            }
                        }
                        lastShiftUp = now
                    }
                    shiftHeld = false
                }
                MotionEvent.ACTION_CANCEL -> shiftHeld = false
            }
            refreshShift()
            true
        }

        btnMute.setOnTouchListener { _, e ->
            if (sosMode && e.actionMasked == MotionEvent.ACTION_DOWN) {
                sosHint("MUTE : MAINTENU = COUPE LE MASTER. SHIFT+MUTE = MINI-MIXEUR. EN MODE GROUPE = SAUVEGARDE.")
                return@setOnTouchListener true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (shiftActive()) {
                        if (groupMode) {
                            val now = System.currentTimeMillis()
                            muteComboCount = if (now - muteComboAt < 700) muteComboCount + 1 else 1
                            muteComboAt = now
                            if (muteComboCount == 1) {
                                saveProject(activeGroupSel, global = true)
                                bpmLcd.groupLabel = "G$activeGroupSel"
                                refreshModeBtns()
                                bubble("GROUPE $activeGroupSel SAUV\u00c9")
                            } else {
                                muteComboCount = 0
                                val g = nextFreeGlobal()
                                activeGroupSel = g
                                saveProject(g, global = true)
                                bpmLcd.groupLabel = "G$g"
                                refreshModeBtns()
                                bubble("GROUPE $g SAUV\u00c9 (LIBRE)")
                            }
                        } else {
                            val now = System.currentTimeMillis()
                            muteComboCount = if (now - muteComboAt < 700) muteComboCount + 1 else 1
                            muteComboAt = now
                            if (muteComboCount == 1) {
                                miniMixMode = !miniMixMode
                                if (!miniMixMode) sampleMoveMode = false
                                padGrid.miniMixMode = miniMixMode
                                padGrid.moveMode = false
                                bubble(if (miniMixMode) "MINI-MIXEUR ON" else "MINI-MIXEUR OFF")
                            } else {
                                muteComboCount = 0
                                sampleMoveMode = miniMixMode && !sampleMoveMode
                                padGrid.moveMode = sampleMoveMode
                                bubble(if (sampleMoveMode) "D\u00c9PLACEMENT SAMPLES" else "MINI-MIXEUR")
                            }
                        }
                    } else if (fHeld || zHeld) {
                        axisMuteCombo = true
                        lastAxisMuteAt = System.currentTimeMillis()
                        if (fHeld) toggleAxis(true)
                        if (zHeld) toggleAxis(false)
                    } else {
                        muteOn = true
                        btnMute.text = "MUTE \u25cf"
                        engine.nativeSetMute(true)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!axisMuteCombo) {
                        muteOn = false
                        btnMute.text = "MUTE"
                        engine.nativeSetMute(false)
                    }
                    axisMuteCombo = false
                }
            }
            true
        }

        btnF.setOnTouchListener { _, e ->
            if (sosMode && e.actionMasked == MotionEvent.ACTION_DOWN) {
                sosHint("F : AXE TURQUOISE DE L'\u00c9CRAN XY. TAP = HOLD/MOMENTAN\u00c9. F+MUTE = MUTE DE L'AXE.")
                return@setOnTouchListener true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fHeld = true
                    if (recArmed) {
                        if (padGrid.padMode == 1) {
                            motionSrcA = !motionSrcA
                            bubble("MOTION AXE F " + (if (motionSrcA) "ON" else "OFF"))
                        } else {
                            loopWetA = !loopWetA
                            bubble("REC WET F " + (if (loopWetA) "ON" else "OFF"))
                        }
                        fRecTap = true
                        refreshBlink()
                    } else if (fxOpen) {
                        fRecTap = true
                        if (assignArm == 0) exitAssignMode() else armAssign(0)
                        refreshBlink()
                    } else {
                        fRecTap = false
                        if (shiftActive() && axisFlowStage == 0) {
                            axisFlowAxis = 0
                            axisFlowStage = 1
                            if (!axisAOn) {
                                axisAOn = true
                                pad.stateA = if (axisAHold) ZuluPad.AxisState.HOLD else ZuluPad.AxisState.MOM
                                updateAxisButtons()
                            }
                            bubble("\u03a8 : TAP LA BANQUE (1-9)", true)
                        }
                        if (shiftActive() && sendPadBank >= 0 &&
                            System.currentTimeMillis() - sendPadAt < 900
                        ) {
                            applySendCombo(sendPadBank, 1)
                        }
                        fDownAt = System.currentTimeMillis()
                        if (fxHeld) loopWetA = true
                        scheduleFuseToggle()
                        refreshBlink()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    fHeld = false
                    loopWetA = false
                    fusePending?.let { fuseHandler.removeCallbacks(it) }
                    fusePending = null
                    if (!fxHeld && !fRecTap && axisFlowStage == 0 &&
                        System.currentTimeMillis() - lastAxisMuteAt > 500 &&
                        System.currentTimeMillis() - fzFusedAt > 800
                    ) {
                        axisAOn = true
                        axisAHold = !axisAHold
                        pad.stateA = if (axisAHold) ZuluPad.AxisState.HOLD else ZuluPad.AxisState.MOM
                        updateAxisButtons()
                    }
                    refreshBlink()
                }
            }
            true
        }
        btnZ.setOnTouchListener { _, e ->
            if (sosMode && e.actionMasked == MotionEvent.ACTION_DOWN) {
                sosHint("Z : AXE JAUNE DE L'\u00c9CRAN XY. TAP = HOLD/MOMENTAN\u00c9. Z+MUTE = MUTE DE L'AXE.")
                return@setOnTouchListener true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    zHeld = true
                    if (recArmed) {
                        if (padGrid.padMode == 1) {
                            motionSrcB = !motionSrcB
                            bubble("MOTION AXE Z " + (if (motionSrcB) "ON" else "OFF"))
                        } else {
                            loopWetB = !loopWetB
                            bubble("REC WET Z " + (if (loopWetB) "ON" else "OFF"))
                        }
                        zRecTap = true
                        refreshBlink()
                    } else if (fxOpen) {
                        zRecTap = true
                        if (assignArm == 2) exitAssignMode() else armAssign(2)
                        refreshBlink()
                    } else {
                        zRecTap = false
                        if (shiftActive() && !fHeld && axisFlowStage == 0) {
                            axisFlowAxis = 2
                            axisFlowStage = 1
                            if (!axisBOn) {
                                axisBOn = true
                                pad.stateB = if (axisBHold) ZuluPad.AxisState.HOLD else ZuluPad.AxisState.MOM
                                updateAxisButtons()
                            }
                            bubble("\u039e : TAP LA BANQUE (1-9)", true)
                        }
                        if (shiftActive() && sendPadBank >= 0 &&
                            System.currentTimeMillis() - sendPadAt < 900
                        ) {
                            applySendCombo(sendPadBank, 2)
                        }
                        zDownAt = System.currentTimeMillis()
                        if (fxHeld) loopWetB = true
                        scheduleFuseToggle()
                        refreshBlink()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    zHeld = false
                    loopWetB = false
                    fusePending?.let { fuseHandler.removeCallbacks(it) }
                    fusePending = null
                    if (!fxHeld && !zRecTap && axisFlowStage == 0 &&
                        System.currentTimeMillis() - lastAxisMuteAt > 500 &&
                        System.currentTimeMillis() - fzFusedAt > 800
                    ) {
                        axisBOn = true
                        axisBHold = !axisBHold
                        pad.stateB = if (axisBHold) ZuluPad.AxisState.HOLD else ZuluPad.AxisState.MOM
                        updateAxisButtons()
                    }
                    refreshBlink()
                }
            }
            true
        }
        btnRec.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> recHeld = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    recHeld = false
                    if (e.actionMasked == MotionEvent.ACTION_UP) recTap()
                }
            }
            refreshBlink()
            false
        }

        padGrid.onPadTouch = { n ->
            when {
                sosMode -> {
                    sosHint("GRILLE : PADS 1-8 = BANQUES, 9-16 = SOLO PAR RANG\u00c9E. SHIFT+MUTE = MINI-MIXEUR.")
                }
                sampleMoveMode -> {
                }
                heldFxSlot >= 0 && n in 1..8 -> {
                    assignBankFx(n - 1)
                }
                padGrid.padMode == 4 && shiftActive() && n in 1..8 -> {
                    val mi = n - 1
                    midiLoopOn[mi] = false
                    midiLoopRec[mi] = false
                    midiLoopEvents[mi].clear()
                    midiLoopKeys[mi].clear()
                    for (r in 0 until 6) java.util.Arrays.fill(midiLoopCells[mi][r], -1f)
                    rebuildMidiSeq()
                    bubble("LOOP MIDI $n EFFAC\u00c9")
                }
                padGrid.padMode == 4 && !shiftActive() && n in 1..8 -> {
                    toggleMidiLoop(n - 1)
                }
                fxHeld -> {
                    fxConsumed = true
                    loopLenSteps = n
                    padGrid.activeBank = n
                }
                lenEntry && n in 1..9 -> lenDigit(n)
                recActive && recMotion && n == recArmPad -> recStop()
                recActive && !recMotion && n == recArmPad -> recStop()
                recArmed && n >= 1 && n <= min(padGrid.padCount, 14) -> armRecDest(n)
                groupMode && n in 1..16 -> {
                    activeGroupSel = n
                    loadProject(n, true)
                    bpmLcd.groupLabel = "G$n"
                    refreshModeBtns()
                    bubble("GROUPE $n CHARG\u00c9")
                }
                !shiftActive() && padGrid.sampEdit && n in 1..padGrid.sampN -> {
                    val b = sampleEditBank
                    val now = System.currentTimeMillis()
                    if (n == lastSlotTapPad && now - lastSlotTapAt < 350) {
                        lastSlotTapPad = -1
                        val bit = 1 shl (n - 1)
                        bankSlotMask[b] = bankSlotMask[b] xor bit
                        bankSlotRev[b] = bankSlotRev[b] xor bit
                        engine.nativeSetBankSlots(b, bankSlotMask[b])
                        engine.nativeSetBankSlotRev(b, bankSlotRev[b])
                        padGrid.sampMask = bankSlotMask[b]
                        padGrid.sampRev = bankSlotRev[b]
                        bubble("SLOT $n : " + if (bankSlotRev[b] and bit != 0) "REVERSE" else "NORMAL")
                    } else {
                        lastSlotTapPad = n
                        lastSlotTapAt = now
                        val bit = 1 shl (n - 1)
                        bankSlotMask[b] = bankSlotMask[b] xor bit
                        engine.nativeSetBankSlots(b, bankSlotMask[b])
                        padGrid.sampMask = bankSlotMask[b]
                        val on = bankSlotMask[b] and bit != 0
                        bubble("B${b + 1} SLOT $n : " + if (on) "ON" else "OFF")
                    }
                }
                shiftActive() && padGrid.padMode == 2 && n in 1..8 -> {
                    fxConsumed = true
                    openSamplePage(n - 1)
                }
                shiftActive() && padGrid.padMode == 1 && n in 1..14 -> {
                    fxConsumed = true
                    openMotionPage(n - 1)
                }
                shiftActive() -> {
                    fxConsumed = true
                    val now = System.currentTimeMillis()
                    if (n == lastShiftPad && now - lastShiftPadAt < 450) {
                        lastShiftPad = -1
                        shiftRecPending?.let { shiftHandler.removeCallbacks(it) }
                        shiftRecPending = null
                        clearPadData(n - 1)
                    } else {
                        lastShiftPad = n
                        lastShiftPadAt = now
                        sendPadBank = n - 1
                        sendComboStart = bankFxSend[sendPadBank]
                        sendPadAt = now
                        val bank = n
                        shiftRecPending = Runnable { startLoopRecording(bank) }
                        shiftHandler.postDelayed(shiftRecPending!!, 450)
                    }
                }
                !groupMode && n in 9..16 -> toggleSolo(n - 9)
                padGrid.padMode != 2 && n - 1 < 14 && snapPads[n - 1] != null -> recallSnap(n - 1)
                toolPage == 3 && n <= 8 -> {
                    val b = n - 1
                    if (!engine.nativeBankHasLoop(b)) {
                        bubble("B$n VIDE")
                    } else if (keysBanks.contains(b)) {
                        keysBanks.remove(b)
                        if (!bankOn[b]) {
                            engine.nativeToggleBank(b)
                            bankOn[b] = true
                        }
                        bubble("B$n D\u00c9TACH\u00c9E \u2014 BOUCLE R\u00c9ANIM\u00c9E")
                    } else {
                        if (bankOn[b]) {
                            engine.nativeToggleBank(b)
                            bankOn[b] = false
                        }
                        keysBanks.add(b)
                        bubble("CLAVIER \u2192 B$n (LOOP COUP\u00c9E)")
                    }
                }
                n <= 8 -> {
                    playerBank = n - 1
                    engine.nativeSetKitMode(playerBank, drumGrid.drumMode == 3)
                    drumGrid.kitMask = engine.nativeKitPadMask(playerBank)
                    if (engine.nativeBankHasLoop(n - 1)) {
                        engine.nativeToggleBank(n - 1)
                        engine.nativeSetPlayerBank(n - 1)
                        bankOn[n - 1] = !bankOn[n - 1]
                        refreshBankFx()
                        pushSampleLog("B$n \u00b7 ${bankDisplayName(n - 1)}")
                    } else if (midiLoops[n - 1].isNotEmpty()) {
                        midiPlaying[n - 1] = !midiPlaying[n - 1]
                        bankOn[n - 1] = midiPlaying[n - 1]
                        if (midiPlaying[n - 1]) {
                            midiStartMs[n - 1] = System.currentTimeMillis()
                            midiLastPos[n - 1] = -1f
                        }
                    } else {
                        startLoopRecording(n)
                        bankOn[n - 1] = true
                    }
                }
                else -> engine.nativeJumpSlice(n, padGrid.padCount)
            }
        }

        padGrid.onPadLongTouch = { n ->
            if (!sampleMoveMode && !shiftActive() && !fxHeld && !padGrid.sampEdit && n in 1..8 && engine.nativeBankHasLoop(n - 1)) {
                openStepEditor(n - 1)
            }
        }
        padGrid.onSwapPads = { a, b ->
            engine.nativeSwapBanks(a, b)
            swapBankUi(a, b)
            bubble("B${a + 1} \u2194 B${b + 1}")
        }
        padGrid.onMergePads = { a, b ->
            bubbleConfirm("FUSIONNER B${a + 1} + B${b + 1} ?", {
                if (engine.nativeMergeBanks(a, b)) {
                    engine.nativeSetKitMode(b, false)
                    drumGrid.kitMask = 0
                    refreshPresetRow()
                    bubble("SAMPLES FUSIONN\u00c9S DANS B${b + 1}")
                } else {
                    bubble("FUSION IMPOSSIBLE (M\u00c9MOIRE PLEINE)")
                }
            }, {
                bubble("FUSION ANNUL\u00c9E")
            })
        }
        padGrid.onSlotParam = { s, p, v ->
            val b = sampleEditBank
            when (p) {
                0 -> { bankSlotVol[b][s] = v; engine.nativeSetBankSlotParam(b, s, v, bankSlotProb[b][s]) }
                1 -> { bankSlotProb[b][s] = v; engine.nativeSetBankSlotParam(b, s, bankSlotVol[b][s], v) }
                2 -> { bankSlotPitch[b][s] = v; engine.nativeSetBankSlotSeq(b, s, v, bankSlotPan[b][s], bankSlotToff[b][s]) }
                3 -> { bankSlotPan[b][s] = v; engine.nativeSetBankSlotSeq(b, s, bankSlotPitch[b][s], v, bankSlotToff[b][s]) }
                4 -> { bankSlotToff[b][s] = v; engine.nativeSetBankSlotSeq(b, s, bankSlotPitch[b][s], bankSlotPan[b][s], v) }
                5 -> {
                    sampleParamVals[b][8] = v
                    engine.nativeSetSampleParam(b, 8, v)
                    paramView?.invalidate()
                }
            }
        }
        padGrid.onPadDown = { n ->
            heldBankPad = n
            if (recArmed && !recActive && n == recArmPad) {
                lenHandler.postDelayed({
                    if (heldBankPad == n && recArmed && !recActive && !lenEntry) {
                        lenEntry = true
                        lenFirst = 0
                        bubble("LONGUEUR ? PADS 1-9 (1 puis 4 = 14, max 19)")
                    }
                }, 2000)
            }
        }
        padGrid.onPadUp = { heldBankPad = -1 }

        val vuModule = FrameLayout(this)
        vuModule.clipChildren = false
        vuModule.addView(
            bpmLcd,
            FrameLayout.LayoutParams((160 * d).toInt(), (48 * d).toInt(), Gravity.CENTER_HORIZONTAL)
        )

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.gravity = Gravity.CENTER_VERTICAL
        row1.clipChildren = false
        inKnob = LineInKnob(this)
        inKnob.onValueChange = { v ->
            inVol = v
            engine.nativeSetInGain(v)
        }
        val ctrlBtnW = LinearLayout.LayoutParams(0, (46 * d).toInt(), 1f)
        ctrlBtnW.setMargins((2 * d).toInt(), 0, (2 * d).toInt(), 0)
        row1.addView(btnFx, LinearLayout.LayoutParams(ctrlBtnW))
        row1.addView(btnArp, LinearLayout.LayoutParams(ctrlBtnW))
        val btnMenu = makeRoundButton("MENU", violet, 12f)
        btnMenu.setOnClickListener {
            if (sosHint("MENU : R\u00c9GLAGES, PROJETS, AUDIO \u2014 ET LE MODE SOS EN HAUT.")) return@setOnClickListener
            openMenuPanel()
        }
        row1.addView(btnMenu, LinearLayout.LayoutParams(ctrlBtnW))
        row1.addView(btnUndo, LinearLayout.LayoutParams(ctrlBtnW))
        val playParams = LinearLayout.LayoutParams(0, (46 * d).toInt(), 1f)
        playParams.setMargins((2 * d).toInt(), 0, (2 * d).toInt(), 0)
        row1.addView(btnPlay, playParams)
        val vuWrap = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        vuWrap.setMargins((3 * d).toInt(), 0, (0 * d).toInt(), 0)
        row1.addView(vuModule, vuWrap)

        val zuLuBtns = LinearLayout(this)
        zuLuBtns.orientation = LinearLayout.HORIZONTAL
        val zuParams = LinearLayout.LayoutParams(
            (76 * d).toInt(), (32 * d).toInt()
        )
        zuParams.setMargins((2 * d).toInt(), (4 * d).toInt(), 0, 0)
        zuLuBtns.addView(btnZu, zuParams)
        val modeRow = LinearLayout(this)
        modeRow.orientation = LinearLayout.HORIZONTAL
        val modeLabels = listOf("SCN", "MOT", "SMP", "GRP", "MID")
        val modeBtns = ArrayList<Button>()
        for (m in modeLabels.indices) {
            val mb = Button(this)
            mb.text = modeLabels[m]
            mb.textSize = 9f
            mb.isAllCaps = false
            mb.minHeight = 0
            mb.minimumHeight = 0
            mb.minWidth = 0
            mb.minimumWidth = 0
            mb.setPadding(0, 0, 0, 0)
            mb.typeface = Typeface.SANS_SERIF
            mb.background = buttonBackground(false)
            mb.setOnClickListener { selectPadMode(m) }
            modeBtnArray.add(mb)
            modeBtns.add(mb)
        }
        for (m in listOf(0, 1, 2, 4, 3)) {
            val mp = LinearLayout.LayoutParams(0, (36 * d).toInt(), 1f)
            mp.setMargins((2 * d).toInt(), 0, (2 * d).toInt(), 0)
            modeRow.addView(modeBtns[m], mp)
        }
        val modeRowParams = LinearLayout.LayoutParams(0, (36 * d).toInt(), 1.3f)
        modeRowParams.setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), 0)
        zuLuBtns.addView(modeRow, modeRowParams)
        val luParams = LinearLayout.LayoutParams(
            (76 * d).toInt(), (36 * d).toInt()
        )
        luParams.setMargins(0, (4 * d).toInt(), (2 * d).toInt(), 0)
        zuLuBtns.addView(btnLu, luParams)
        refreshModeBtns()

        val fzRow = LinearLayout(this)
        fzRow.orientation = LinearLayout.HORIZONTAL
        val fzParams = LinearLayout.LayoutParams(0, (34 * d).toInt(), 1f)
        fzParams.setMargins((8 * d).toInt(), (2 * d).toInt(), (10 * d).toInt(), 0)
        fzRow.addView(btnF, LinearLayout.LayoutParams(fzParams))
        val recMidParams = LinearLayout.LayoutParams(0, (34 * d).toInt(), 2.2f)
        recMidParams.setMargins((4 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), 0)
        fzRow.addView(btnRec, recMidParams)
        val fzParams2 = LinearLayout.LayoutParams(0, (34 * d).toInt(), 1f)
        fzParams2.setMargins((10 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), 0)
        fzRow.addView(btnZ, LinearLayout.LayoutParams(fzParams2))

        wireFaders()

        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding((6 * d).toInt(), (3 * d).toInt(), (10 * d).toInt(), (2 * d).toInt())
        gridParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        gridParams.setMargins(0, (4 * d).toInt(), 0, 0)
        column.addView(padGrid, gridParams)
        val slog = TextView(this)
        slog.setTextColor(silk)
        slog.textSize = 9f
        slog.typeface = Typeface.SANS_SERIF
        slog.maxLines = 1
        slog.ellipsize = android.text.TextUtils.TruncateAt.START
        slog.setPadding((4 * d).toInt(), 0, (4 * d).toInt(), 0)
        slog.visibility = if (showSampleLog) View.VISIBLE else View.GONE
        sampleLogView = slog
        column.addView(
            slog,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        column.addView(
            zuLuBtns,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (2 * d).toInt() }
        )
        val faderWrap = LinearLayout(this)
        faderWrap.orientation = LinearLayout.HORIZONTAL
        faderWrap.gravity = Gravity.CENTER_HORIZONTAL
        faderWrap.addView(
            zuLuFader,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        column.addView(
            faderWrap,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (40 * d).toInt()
            )
        )
        val faderRow = LinearLayout(this)
        faderRow.orientation = LinearLayout.HORIZONTAL
        val flyParams = LinearLayout.LayoutParams(0, (40 * d).toInt(), 3f)
        flyParams.setMargins((3 * d).toInt(), 0, (3 * d).toInt(), 0)
        faderRow.addView(flyFader, flyParams)
        val mixParams = LinearLayout.LayoutParams(0, (40 * d).toInt(), 2f)
        mixParams.setMargins((3 * d).toInt(), 0, (3 * d).toInt(), 0)
        faderRow.addView(mixFader, mixParams)
        val faderRowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (40 * d).toInt()
        )
        faderRowParams.topMargin = (4 * d).toInt()
        column.addView(faderRow, faderRowParams)

        content = LinearLayout(this)
        content.orientation = LinearLayout.HORIZONTAL
        content.background = ZebraBgDrawable()
        val padParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

        padPane = FrameLayout(this)

        val presetRow = LinearLayout(this)
        presetRow.orientation = LinearLayout.HORIZONTAL
        for (i in 0 until 9) {
            val b = makeButton((i + 1).toString(), cream)
            b.textSize = 10f
            b.setOnClickListener { presetTap(i) }
            b.setOnLongClickListener { presetLongTap(i); true }
            b.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> heldFxSlot = i
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> heldFxSlot = -1
                }
                false
            }
            presetBtns.add(b)
            val bp = LinearLayout.LayoutParams(0, (26 * d).toInt(), 1f)
            bp.setMargins((2 * d).toInt(), 0, (2 * d).toInt(), 0)
            presetRow.addView(b, bp)
        }
        val pb = makeButton("POW", yellow)
        pb.textSize = 10f
        pb.setOnClickListener {
            if (sosHint("POW : MODE PUNCH \u2014 COLLECTION RAPIDE DE PRESETS PAR PAD.")) return@setOnClickListener
            setPunchMode(!punchMode)
        }
        btnPunch = pb
        val pbp = LinearLayout.LayoutParams(0, (26 * d).toInt(), 1f)
        pbp.setMargins((2 * d).toInt(), 0, (2 * d).toInt(), 0)
        presetRow.addView(pb, pbp)
        val db = makeButton("DRUMS", pink)
        db.textSize = 9f
        drumSeqBtn = db
        db.setOnClickListener {
            if (sosHint("DRUMS : BO\u00cete \u00c0 RYTHME \u2014 S\u00c9QUENCEUR 32 PAS. SHIFT+DRUMS = MUTE. DRUMS+FX SIMULTAN\u00c9S = ENGAGE LES EFFETS.")) return@setOnClickListener
            if (toggleDrumFxSimul(fxDownAt)) return@setOnClickListener
            if (shiftActive()) {
                drumMuted = !drumMuted
                bubble("BO\u00ceTE \u00c0 RYTHME " + (if (drumMuted) "MUET" else "SONORE"))
            } else if (toolPage == 4) {
                toolPage = 0
                showToolPage()
            } else {
                toolsMenu.visibility = View.GONE
                toolPage = 4
                showToolPage()
            }
        }
        db.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) drumsDownAt = System.currentTimeMillis()
            false
        }
        val dbp = LinearLayout.LayoutParams(0, (26 * d).toInt(), 1f)
        dbp.setMargins((2 * d).toInt(), 0, (2 * d).toInt(), 0)
        presetRow.addView(db, dbp)

        val leftColumn = LinearLayout(this)
        leftColumn.orientation = LinearLayout.VERTICAL
        val presetRowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        presetRowParams.setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), 0)
        leftColumn.addView(presetRow, presetRowParams)
        val padPaneParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        padPaneParams.setMargins(0, (14 * d).toInt(), 0, (10 * d).toInt())

        vuMeter.onLongPress = {
            if (!simulLock) openMenuPanel()
        }

        vuMeter.onDoubleTap = { toggleAudioConsole() }

        bpmLcd.onLongPress = { toggleTimeSigConsole() }

        bpmLcd.catBodyColor = catColorSel
        bpmLcd.shiftCheck = { shiftActive() }
        var sirenRunning = false
        fun updateSirenCtl(nx: Float, ny: Float) {
            engine.nativeSetGen(when (sirenType) {
                1 -> 10
                2 -> 11
                else -> if (ny < 0.5f) 11 else 10
            })
            engine.nativeSetFxParam(27, 0, nx)
            engine.nativeSetFxParam(27, 1, 0.2f + 0.6f * ny)
            engine.nativeSetSiren(true, 0.4f + 0.6f * ny, nx, 0.2f + 0.6f * ny)
            outFilVal = (-0.9f * ny).coerceIn(-1f, 1f)
            engine.nativeSetMasterFilter(outFilVal)
            vuMeter.outFilter = outFilVal
            bpmLcd.outFilter = outFilVal
            sirenPad.nx = nx
            sirenPad.ny = ny
            sirenPad.invalidate()
        }
        fun stopSiren() {
            if (!sirenRunning) return
            sirenRunning = false
            engine.nativeSetSiren(false, 0f, 0f, 0f)
            engine.nativeSetGenTouch(false)
            engine.nativeSetGen(genIdx)
            if (sirenPrevFxDub) {
                engine.nativeSetFx(27, true)
                for (k in 0 until 8) {
                    fxParamVals[27][k] = sirenPrevDubParams[k]
                    engine.nativeSetFxParam(27, k, sirenPrevDubParams[k])
                }
            } else {
                engine.nativeSetFx(27, false)
            }
            outFilVal = sirenPrevFilter
            engine.nativeSetMasterFilter(outFilVal)
            vuMeter.outFilter = outFilVal
            bpmLcd.outFilter = outFilVal
            sirenPad.visibility = View.GONE
        }
        fun startSiren(nx: Float, ny: Float) {
            if (sirenRunning) {
                updateSirenCtl(nx, ny)
                return
            }
            sirenRunning = true
            sirenPrevFilter = outFilVal
            sirenPrevFxDub = fxActive[27]
            sirenPrevDubParams = fxParamVals[27].copyOf()
            engine.nativeSetSirenWave(sirenWave)
            engine.nativeSetGenTouch(true)
            engine.nativeSetFx(27, true)
            engine.nativeSetFxWet(27, 0.25f)
            sirenPad.visibility = View.VISIBLE
            bubble("SIRENE DUB !")
            updateSirenCtl(nx, ny)
        }
        bpmLcd.onSirenTouch = { down, nx, ny ->
            if (down) {
                startSiren(nx, ny)
            } else {
                stopSiren()
            }
        }

        vuMeter.onVolDelta = { dv ->
            masterVol = (masterVol + dv).coerceIn(0f, 2f)
            midiOut("VOL", masterVol / 2f)
            engine.nativeSetMasterVolume(masterVol)
            showVuOverlay("VOL " + ((masterVol * 50).toInt().coerceIn(0, 100)).toString() + "%")
        }
        vuMeter.onInVolDelta = { dv ->
            inVol = (inVol + dv).coerceIn(0f, 2f)
            midiOut("IN", inVol / 2f)
            engine.nativeSetInGain(inVol)
            inKnob.setVal(inVol)
            showVuOverlay("IN " + ((inVol * 50).toInt().coerceIn(0, 100)).toString() + "%")
        }
        vuMeter.onOutFilterDelta = { v ->
            outFilVal = (outFilVal + v).coerceIn(-1f, 1f)
            engine.nativeSetMasterFilter(outFilVal)
            outFSel = if (outFilVal < -0.02f) 0 else if (outFilVal > 0.02f) 2 else 1
            vuMeter.outFilter = outFilVal
            bpmLcd.outFilter = outFilVal
            showVuOverlay(if (outFilVal < -0.02f) "OUT LP" else if (outFilVal > 0.02f) "OUT HP" else "OUT FILT 0")
        }
        vuMeter.onInFilterDelta = { v ->
            inFilVal = (inFilVal + v).coerceIn(-1f, 1f)
            engine.nativeSetInFilter(inFilVal)
            inFSel = if (inFilVal < -0.02f) 0 else if (inFilVal > 0.02f) 2 else 1
            vuMeter.inFilter = inFilVal
            bpmLcd.inFilter = inFilVal
            showVuOverlay(if (inFilVal < -0.02f) "IN LP" else if (inFilVal > 0.02f) "IN HP" else "IN FILT 0")
        }
        vuMeter.onOutFilterReset = {
            outFilVal = 0f
            engine.nativeSetMasterFilter(0f)
            outFSel = 1
            vuMeter.outFilter = 0f
            bpmLcd.outFilter = 0f
            showVuOverlay("OUT FILT 0")
        }
        vuMeter.onInFilterReset = {
            inFilVal = 0f
            engine.nativeSetInFilter(0f)
            inFSel = 1
            vuMeter.inFilter = 0f
            bpmLcd.inFilter = 0f
            showVuOverlay("IN FILT 0")
        }
        bpmLcd.onOutVolDelta = vuMeter.onVolDelta
        bpmLcd.onInVolDelta = vuMeter.onInVolDelta
        bpmLcd.onOutFilterDelta = vuMeter.onOutFilterDelta
        bpmLcd.onInFilterDelta = vuMeter.onInFilterDelta
        bpmLcd.onOutFilterReset = vuMeter.onOutFilterReset
        bpmLcd.onInFilterReset = vuMeter.onInFilterReset
        bpmLcd.onInLongPress = { if (!simulLock) openMenuPanel() }
        bpmLcd.onOutLongPress = { toggleAudioConsole() }
        bpmLcd.onCatTap = { toggleAudioConsole() }

        val edgeColumn = LinearLayout(this)
        edgeColumn.orientation = LinearLayout.VERTICAL
        edgeColumn.setPadding((4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt(), (6 * d).toInt())
        fun edgeGap(h: Int) = LinearLayout.LayoutParams(0, (h * d).toInt())
        edgeColumn.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        edgeColumn.addView(btnMute, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (52 * d).toInt()))
        edgeColumn.addView(View(this), edgeGap(24))
        edgeColumn.addView(btnShift, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (52 * d).toInt()))
        edgeColumn.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        padPane.addView(
            pad,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        macroPanel = MacroPanelView(this)
        macroPanel.visibility = View.GONE
        macroPanel.onFaderChange = { i, v ->
            if (shiftActive()) {
                if (macroFlowStage > 0 && macroFlowFader == i) {
                    macroFlowStage = 0
                    macroFlowFader = -1
                    closeParamPage()
                    bubble("AFFECTATION M${i + 1} TERMIN\u00c9E")
                } else if (macroFlowStage == 0) {
                    macroFlowFader = i
                    macroFlowStage = 1
                    bubble("M${i + 1} : TAPER UNE BANQUE PUIS DES PARAMS (JUSQU'\u00c0 8 CIBLES, RE-TAPER UN PARAM = INVERSER SA COURSE) \u00b7 FIN PAR SHIFT+M${i + 1} OU FX", true)
                }
            } else {
                midiOut("MAC${i + 1}", v)
                for (tg in macroFaderTargets[i]) {
                    engine.nativeSetFxParam(tg.first, tg.second, if (tg.third) 1f - v else v)
                }
            }
        }
        macroPanel.onHFaderChange = { v -> midiOut("MACH", v) }
        macroPanel.shiftCheck = { shiftActive() && bendMacroOn }
        macroPanel.bendModeSelect = bendMacroMode
        macroPanel.onBendChange = { i, v ->
            midiOut("MAC${i + 1}", v)
            for (tg in macroFaderTargets[i]) {
                engine.nativeSetFxParam(tg.first, tg.second, if (tg.third) 1f - v else v)
            }
        }
        macroPanel.onButtonTap = { i, on ->
            bubble("MACRO B${i + 1} " + if (on) "ON" else "OFF")
        }
        padPane.addView(
            macroPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        if (macroMode) {
            macroPanel.visibility = View.VISIBLE
            pad.visibility = View.INVISIBLE
        }
        for (f in 0 until 6) refreshMacroFaderLbl(f)
        val scopeParams = FrameLayout.LayoutParams(
            (110 * d).toInt(), (64 * d).toInt(), Gravity.TOP or Gravity.RIGHT
        )
        scopeParams.setMargins(0, (26 * d).toInt(), (10 * d).toInt(), 0)
        padPane.addView(glitchScope, scopeParams)

        val punch = PunchPadView(this)
        punch.visibility = View.GONE
        punch.onPunchDown = { z ->
            if (shiftActive()) {
                val fx = punchFx[punchCol][z]
                if (fx >= 0) {
                    openParamPage(fx)
                } else {
                    bubble("PAS D'\u00c9DIT ICI")
                }
            } else {
                punchApply(z, true)
            }
        }
        punch.onPunchUp = { z -> punchApply(z, false) }
        punch.onZoneLongPress = { z -> openPunchZoneEditor(z) }
        punch.onExit = { setPunchMode(false) }
        punch.shiftCheck = { shiftActive() }
        punch.onCollectionChange = { c -> setPunchCollection(c) }
        punchView = punch
        patchBay = PatchBayView(this)
        patchBay.visibility = View.GONE
        patchBay.onTap = { patchBay.visibility = View.GONE }
        padPane.addView(
            punch,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        padPane.addView(
            patchBay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        miniKeys = MiniKeysView(this)
        miniKeys.onKey = { k, down -> keyEvent(k, down) }
        val keyWrap = LinearLayout(this)
        keyWrap.orientation = LinearLayout.VERTICAL
        keyWrap.visibility = View.GONE
        val kRow = LinearLayout(this)
        kRow.orientation = LinearLayout.HORIZONTAL
        kRow.setBackgroundColor(Color.argb(210, 10, 11, 15))
        fun kSeek(label: String, init: Float, onChange: (Float) -> Unit) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.text = label
            lab.setTextColor(silk)
            lab.textSize = 8f
            lab.typeface = Typeface.SANS_SERIF
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.progress = (init * 100).toInt()
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onChange(p / 100f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            col.addView(sb)
            kRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        kSeek("FILTRE", 1f) {
            if (!keysFilterFree) {
                keysFilterFree = true
                engine.nativeSetAssign(0, -1)
            }
            engine.nativeSetParam(0, it * 0.95f + 0.05f, true)
        }
        kSeek("ATQ", 0.2f) {
            keysAtkScale = 0.25f + it * 3.75f
            engine.nativeSetGenEnv(keysAtkScale, keysDecScale)
        }
        kSeek("DEC", 0.2f) {
            keysDecScale = 0.25f + it * 3.75f
            engine.nativeSetGenEnv(keysAtkScale, keysDecScale)
        }
        kSeek("GLIDE", 0f) {
            keysGlide = it
            miniKeys.glideMode = it > 0.01f
        }
        run {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.text = "OCTAVE"
            lab.setTextColor(silk)
            lab.textSize = 8f
            lab.typeface = Typeface.SANS_SERIF
            col.addView(lab)
            val octRow = LinearLayout(this)
            octRow.orientation = LinearLayout.HORIZONTAL
            for (o in -2..2) {
                val ob = Button(this)
                ob.text = if (o > 0) "+$o" else "$o"
                ob.tag = o
                ob.textSize = 9f
                ob.setPadding(0, 0, 0, 0)
                ob.minWidth = 0
                ob.minimumWidth = 0
                ob.setBackgroundColor(Color.TRANSPARENT)
                ob.setOnClickListener {
                    keysOctShift = o
                    miniKeys.baseOctave = 3 + o
                    refreshKeysOct()
                }
                keysOctBtns.add(ob)
                octRow.addView(ob, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            }
            col.addView(octRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            kRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.4f))
        }
        refreshKeysOct()
        run {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.text = "SCALE"
            lab.setTextColor(silk)
            lab.textSize = 8f
            lab.typeface = Typeface.SANS_SERIF
            col.addView(lab)
            val sb = Button(this)
            sb.textSize = 9f
            sb.isAllCaps = false
            sb.minWidth = 0
            sb.minimumWidth = 0
            sb.setPadding(0, 0, 0, 0)
            sb.setBackgroundColor(Color.TRANSPARENT)
            sb.setOnClickListener {
                genScaleOn = !genScaleOn
                engine.nativeSetGenScaleOn(genScaleOn)
                saveUiPrefs()
                refreshScaleIndicators()
            }
            keysScaleBtn = sb
            col.addView(sb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            kRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.7f))
        }
        refreshScaleIndicators()
        val kSounds = Button(this)
        kSounds.text = "SONS"
        kSounds.textSize = 9f
        kSounds.setTextColor(turquoise)
        kSounds.isAllCaps = false
        kSounds.minWidth = 0
        kSounds.minimumWidth = 0
        kSounds.setPadding(0, 0, 0, 0)
        kSounds.setBackgroundColor(Color.TRANSPARENT)
        kSounds.setOnClickListener {
            keysSoundPanel.visibility =
                if (keysSoundPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refreshKeysSounds()
        }
        kRow.addView(kSounds, LinearLayout.LayoutParams((36 * d).toInt(), LinearLayout.LayoutParams.MATCH_PARENT))
        val kClose = Button(this)
        kClose.text = "\u00d7"
        kClose.textSize = 13f
        kClose.setTextColor(silk)
        kClose.isAllCaps = false
        kClose.minWidth = 0
        kClose.minimumWidth = 0
        kClose.setPadding(0, 0, 0, 0)
        kClose.setBackgroundColor(Color.TRANSPARENT)
        kClose.setOnClickListener {
            toolPage = 0
            showToolPage()
        }
        kRow.addView(kClose, LinearLayout.LayoutParams((30 * d).toInt(), LinearLayout.LayoutParams.MATCH_PARENT))
        buildKeysSoundPanel()
        keyWrap.addView(
            keysSoundPanel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        keyWrap.addView(
            kRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (34 * d).toInt()
            )
        )
        keyWrap.addView(
            miniKeys,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        keysWrap = keyWrap
        padPane.addView(
            keyWrap,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        buildAudioConsole()
        buildTimeSigConsole()

        toolsMenu = FrameLayout(this)
        toolsMenu.visibility = View.GONE
        toolsMenu.setBackgroundColor(Color.argb(215, 8, 9, 13))
        toolsMenu.setOnClickListener { toolsMenu.visibility = View.GONE }
        val tmBox = LinearLayout(this)
        tmBox.orientation = LinearLayout.VERTICAL
        tmBox.gravity = Gravity.CENTER
        val tmGrid = LinearLayout(this)
        tmGrid.orientation = LinearLayout.VERTICAL
        run {
            val r1 = LinearLayout(this)
            r1.orientation = LinearLayout.HORIZONTAL
            val r2 = LinearLayout(this)
            r2.orientation = LinearLayout.HORIZONTAL
            val wp = LinearLayout.LayoutParams((170 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            for (r in listOf(r1, r2)) tmGrid.addView(r, wp)
            val tp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            for ((label, col, pg) in listOf(
                Triple("ARP", turquoise, 1),
                Triple("GRID", green, 2)
            )) {
                val b = makeButton(label, col)
                b.textSize = 11f
                b.typeface = markerFont(this)
                b.setOnClickListener {
                    toolsMenu.visibility = View.GONE
                    toolPage = if (toolPage == pg) 0 else pg
                    showToolPage()
                }
                r1.addView(b, tp)
            }
            for ((label, col, pg) in listOf(
                Triple("CLAVIER", amber, 3),
                Triple("COMM", pink, -1)
            )) {
                val b = makeButton(label, col)
                b.textSize = 11f
                b.typeface = markerFont(this)
                b.setOnClickListener {
                    toolsMenu.visibility = View.GONE
                    if (pg >= 0) {
                        toolPage = if (toolPage == pg) 0 else pg
                        showToolPage()
                    } else {
                        openCommDialog()
                    }
                }
                r2.addView(b, tp)
            }
            val r3 = LinearLayout(this)
            r3.orientation = LinearLayout.HORIZONTAL
            tmGrid.addView(r3, wp)
            val tp3 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            for ((label, col, act) in listOf(
                Triple("LOG", Color.rgb(255, 140, 0), 0),
                Triple("ENV", Color.rgb(120, 220, 120), 1),
                Triple("PATCH", Color.rgb(190, 120, 255), 2)
            )) {
                val b = makeButton(label, col)
                b.textSize = 10f
                b.typeface = markerFont(this)
                b.setOnClickListener {
                    toolsMenu.visibility = View.GONE
                    when (act) {
                        0 -> openLogicDialog()
                        1 -> openEnvDialog()
                        else -> openPatchBay()
                    }
                }
                r3.addView(b, tp3)
            }
        }
        tmBox.addView(tmGrid)
        toolsMenu.addView(
            tmBox,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val tmClose = Button(this)
        tmClose.text = "\u00d7"
        tmClose.setTextColor(silk)
        tmClose.textSize = 16f
        tmClose.minHeight = 0
        tmClose.minimumHeight = 0
        tmClose.setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
        tmClose.typeface = markerFont(this)
        tmClose.background = buttonBackground(false)
        tmClose.setOnClickListener { toolsMenu.visibility = View.GONE }
        toolsMenu.addView(
            tmClose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )
        padPane.addView(
            toolsMenu,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val ppage = ParamPageView(this)
        ppage.visibility = View.GONE
        ppage.onParam = { k, v ->
            lastParamK = k
            midiOut(if (paramFx == -2) "S${sampleEditBank}:$k" else "F${paramFx}:$k", v)
            if (envTeach || logTeach) {
                val tt = if (paramFx == -2) when {
                    k < 6 -> (63 + k) * 8 + k
                    k in 6..8 -> (66 + k) * 8
                    k == 9 -> 74 * 8 + 1
                    else -> 74 * 8 + 2
                } else fxSlot(paramFx) * 8 + k
                if (envTeach) {
                    envTeach = false
                    envTarget = 8 + tt
                    applyEnv()
                    bubble("ENV \u2192 ${linkParamName(envTarget)}")
                }
                if (logTeach) {
                    logTeach = false
                    logTarget = 8 + tt
                    bubble("LOGIQUE \u2192 ${linkParamName(logTarget)}")
                }
            }
            val fx = paramFx
            if (fx == -2) {
                sampleParamVals[sampleEditBank][k] = v
                engine.nativeSetSampleParam(sampleEditBank, k, v)
                if (k == 9) {
                    val b = sampleEditBank
                    val sn = if (v < 1f / 3f) 8 else if (v < 2f / 3f) 10 else 12
                    if (sn != bankSlotN[b]) {
                        bankSlotN[b] = sn
                        if (sn > 8) {
                            bankSlotMask[b] = bankSlotMask[b] or (((1 shl sn) - 1) xor 255)
                            engine.nativeSetBankSlots(b, bankSlotMask[b])
                        }
                        padGrid.sampN = sn
                        padGrid.sampMask = bankSlotMask[b]
                        bubble("GRILLE : $sn PAS")
                    }
                }
                if (k == 10) {
                    padGrid.seqMode = v >= 0.5f
                    updateSampModeLabel()
                    if (v >= 0.5f) bubble("ONE-SHOT : S\u00c9QUENCEUR")
                }
            } else if (fx >= 0) {
                if (snapHold && snapQuietFor(fx)) {
                    if (!snapHoldFx.containsKey(fx)) snapHoldFx[fx] = fxParamVals[fx].copyOf()
                    fxParamVals[fx][k] = v
                } else {
                    fxParamVals[fx][k] = v
                    engine.nativeSetFxParam(fx, k, v)
                }
            }
        }
        ppage.onAssignCycle = { k ->
            if (macroFlowStage == 2 && paramFx >= 0) macroFlowParamTap(k)
            else if (axisFlowStage in 2..3 && paramFx >= 0) axisFlowParamTap(k) else cycleParamAssign(k)
        }
        ppage.onParamLongPress = { k ->
            val fx = paramFx
            if (fx >= 0) openCommDialog(fx, k)
        }
        ppage.onExit = { closeParamPage() }
        ppage.shiftCheck = { shiftActive() }
        ppage.onMasterToggle = {
            val fx = paramFx
            if (fx >= 0) {
                if (masterFx.contains(fx)) {
                    masterFx.remove(fx)
                    bubble("FX RETIRE DU MASTER")
                } else {
                    masterFx.add(fx)
                    bubble("FX ROUTE AU MASTER")
                }
                ppage.masterOn = masterFx.contains(fx)
                ppage.refresh()
                refreshFxHighlight()
            }
        }
        ppage.onSnapToggle = {
            val fx = paramFx
            if (fx >= 0) {
                val next = when (snapFxOverride[fx]) { null -> 1; true -> 2; else -> 0 }
                if (next == 0) snapFxOverride.remove(fx) else snapFxOverride[fx] = (next == 1)
                ppage.snapMode = next
                ppage.refresh()
                bubble("SNAP : " + if (next == 1) "SILENCE" else if (next == 2) "SON" else "AUTO (MENU)")
            }
        }
        ppage.bankCols = bankCols
        ppage.onBankTap = { b ->
            if (paramFx == -2) openSamplePage(b) else presetTap(b)
        }
        ppage.onCommTap = { if (paramFx >= 0) openCommDialog(paramFx, lastParamK) }
        ppage.onPatchTap = { openPatchBay() }
        ppage.onMapTab = {
            if (paramFx == -2) {
                ppage.mapMode = !ppage.mapMode
                if (ppage.mapMode) {
                    val b = sampleEditBank
                    ppage.mapVals = arrayOf(
                        bankSlotVol[b], bankSlotProb[b], bankSlotPitch[b], bankSlotPan[b], bankSlotToff[b]
                    )
                    ppage.mapMask = bankSlotMask[b]
                    ppage.mapCols = bankSlotN[b].coerceIn(1, 16)
                }
                ppage.refresh()
            } else {
                bubble("MAP : \u00c9DITION DE BANQUE UNIQUEMENT")
            }
        }
        ppage.onMapCell = { s, p, v -> padGrid.onSlotParam?.invoke(s, p, v) }
        paramView = ppage
        padPane.addView(
            ppage,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        drumGrid = DrumGridView(this)
        drumGrid.visibility = View.GONE
        drumGrid.onDrum = { n -> drumHit(n) }
        drumGrid.onModeChange = { m ->
            engine.nativeSetKitMode(playerBank, m == 3)
            if (m == 3) drumGrid.kitMask = engine.nativeKitPadMask(playerBank)
        }
        drumGrid.onDrumRecStart = { pad ->
            engine.nativeKitRecordPad(playerBank, pad)
            bubble("REC PAD ${pad + 1} \u00b7 REL\u00c2CHE POUR STOP")
        }
        drumGrid.onDrumRecStop = {
            engine.nativeKitStopRec()
            drumGrid.kitMask = engine.nativeKitPadMask(playerBank)
        }
        drumGrid.onExit = {
            toolPage = 0
            drumGrid.visibility = View.GONE
        }
        padPane.addView(
            drumGrid,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        drumSeqView = DrumSeqView(this)
        for (i in drumSeqGens.indices) {
            drumSeqView.rowNames[i] =
                if (drumSeqGens[i] == 24) "TABLA"
                else fxNameBySlot[genSlot(drumSeqGens[i])]?.take(7) ?: "D${i + 1}"
        }
        drumSeqView.onSwipe = { right ->
            if (right && drumSeqView.page == 0) {
                drumSeqView.doubleTo32()
            } else if (!right && drumSeqView.page == 1) {
                drumSeqView.page = 0
                drumSeqView.invalidate()
            }
        }
        drumSeqWrap = LinearLayout(this)
        drumSeqWrap.orientation = LinearLayout.VERTICAL
        drumSeqWrap.visibility = View.GONE
        drumSeqWrap.setBackgroundColor(Color.argb(225, 10, 11, 15))
        drumSeqWrap.setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
        val dsTop = LinearLayout(this)
        dsTop.orientation = LinearLayout.HORIZONTAL
        dsTop.gravity = Gravity.CENTER_VERTICAL
        val dsRun = makeButton("RUN", green)
        dsRun.textSize = 11f
        dsRun.setOnClickListener { toggleDrumSeqRun() }
        drumSeqRunBtn = dsRun
        dsTop.addView(dsRun, LinearLayout.LayoutParams((56 * d).toInt(), (32 * d).toInt()))
        val dsProba = makeButton("PROBA", silk)
        dsProba.textSize = 9f
        dsTop.addView(dsProba, LinearLayout.LayoutParams((50 * d).toInt(), (32 * d).toInt()))
        val dsRoll = makeButton("ROLL", silk)
        dsRoll.textSize = 9f
        dsTop.addView(dsRoll, LinearLayout.LayoutParams((50 * d).toInt(), (32 * d).toInt()))
        val dsLen = makeButton("LEN", silk)
        dsLen.textSize = 9f
        dsTop.addView(dsLen, LinearLayout.LayoutParams((44 * d).toInt(), (32 * d).toInt()))
        val dsPan = makeButton("PAN", silk)
        dsPan.textSize = 9f
        dsTop.addView(dsPan, LinearLayout.LayoutParams((44 * d).toInt(), (32 * d).toInt()))
        val dsGlitch = makeButton("GLITCH", silk)
        dsGlitch.textSize = 9f
        dsTop.addView(dsGlitch, LinearLayout.LayoutParams((50 * d).toInt(), (32 * d).toInt()))
        val dsMut = makeButton("MUT", silk)
        dsMut.textSize = 9f
        dsTop.addView(dsMut, LinearLayout.LayoutParams((40 * d).toInt(), (32 * d).toInt()))
        val dsUndo = makeButton("UNDO", silk)
        dsUndo.textSize = 9f
        dsUndo.setOnClickListener { drumSeqView.undo() }
        dsTop.addView(dsUndo, LinearLayout.LayoutParams((52 * d).toInt(), (32 * d).toInt()))
        val dsRec = makeButton("REC", red)
        dsRec.textSize = 9f
        drumLiveRecBtn = dsRec
        dsRec.setOnClickListener {
            drumLiveRec = !drumLiveRec
            dsRec.setTextColor(if (drumLiveRec) amber else red)
        }
        dsTop.addView(dsRec, LinearLayout.LayoutParams((46 * d).toInt(), (32 * d).toInt()))
        fun dsModeBtn(btn: Button, isOn: () -> Boolean, others: List<Button>) {
            btn.setTextColor(if (isOn()) amber else silk)
            for (o in others) o.setTextColor(silk)
        }
        dsProba.setOnClickListener {
            drumSeqView.probaMode = !drumSeqView.probaMode
            if (drumSeqView.probaMode) {
                drumSeqView.rollMode = false
                drumSeqView.lenMode = false
                drumSeqView.panMode = false
                drumSeqView.glitchMode = false
                drumSeqView.mutMode = false
            }
            dsModeBtn(dsProba, { drumSeqView.probaMode }, listOf(dsRoll, dsLen, dsPan, dsGlitch, dsMut))
        }
        dsRoll.setOnClickListener {
            drumSeqView.rollMode = !drumSeqView.rollMode
            if (drumSeqView.rollMode) {
                drumSeqView.probaMode = false
                drumSeqView.lenMode = false
                drumSeqView.panMode = false
                drumSeqView.glitchMode = false
                drumSeqView.mutMode = false
            }
            dsModeBtn(dsRoll, { drumSeqView.rollMode }, listOf(dsProba, dsLen, dsPan, dsGlitch, dsMut))
        }
        dsLen.setOnClickListener {
            drumSeqView.lenMode = !drumSeqView.lenMode
            if (drumSeqView.lenMode) {
                drumSeqView.probaMode = false
                drumSeqView.rollMode = false
                drumSeqView.panMode = false
                drumSeqView.glitchMode = false
                drumSeqView.mutMode = false
            }
            dsModeBtn(dsLen, { drumSeqView.lenMode }, listOf(dsProba, dsRoll, dsPan, dsGlitch, dsMut))
        }
        dsPan.setOnClickListener {
            drumSeqView.panMode = !drumSeqView.panMode
            if (drumSeqView.panMode) {
                drumSeqView.probaMode = false
                drumSeqView.rollMode = false
                drumSeqView.lenMode = false
                drumSeqView.glitchMode = false
                drumSeqView.mutMode = false
            }
            dsModeBtn(dsPan, { drumSeqView.panMode }, listOf(dsProba, dsRoll, dsLen, dsGlitch, dsMut))
        }
        dsGlitch.setOnClickListener {
            drumSeqView.glitchMode = !drumSeqView.glitchMode
            if (drumSeqView.glitchMode) {
                drumSeqView.probaMode = false
                drumSeqView.rollMode = false
                drumSeqView.lenMode = false
                drumSeqView.panMode = false
                drumSeqView.mutMode = false
            }
            dsModeBtn(dsGlitch, { drumSeqView.glitchMode }, listOf(dsProba, dsRoll, dsLen, dsPan, dsMut))
        }
        dsMut.setOnClickListener {
            drumSeqView.mutMode = !drumSeqView.mutMode
            if (drumSeqView.mutMode) {
                drumSeqView.probaMode = false
                drumSeqView.rollMode = false
                drumSeqView.lenMode = false
                drumSeqView.panMode = false
                drumSeqView.glitchMode = false
            }
            dsModeBtn(dsMut, { drumSeqView.mutMode }, listOf(dsProba, dsRoll, dsLen, dsPan, dsGlitch))
        }
        val dsProc = LinearLayout(this)
        dsProc.orientation = LinearLayout.HORIZONTAL
        fun dsSeek(label: String, onChange: (Float) -> Unit) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.text = label
            lab.setTextColor(silk)
            lab.textSize = 8f
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onChange(p / 100f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            col.addView(sb)
            dsProc.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        dsSeek("LOUDNESS") { engine.nativeSetDrumLoud(it) }
        dsSeek("AIR") { engine.nativeSetDrumAir(it) }
        dsSeek("GLITCH") { drumGlitchAmt = it }
        dsSeek("MUTATION") { drumMutAmt = it }
        val dsHint = TextView(this)
        dsHint.text = "TAP = PAS \u00b7 GLISSER UN PAS ACTIF = D\u00c9CALAGE"
        dsHint.setTextColor(silk)
        dsHint.textSize = 8f
        dsTop.addView(dsHint, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val dsClose = Button(this)
        dsClose.text = "\u00d7"
        dsClose.textSize = 14f
        dsClose.setTextColor(silk)
        dsClose.isAllCaps = false
        dsClose.minWidth = 0
        dsClose.minimumWidth = 0
        dsClose.setPadding(0, 0, 0, 0)
        dsClose.setBackgroundColor(Color.TRANSPARENT)
        dsClose.setOnClickListener {
            drumSeqRunning = false
            toolPage = 0
            showToolPage()
        }
        dsTop.addView(dsClose, LinearLayout.LayoutParams((30 * d).toInt(), (32 * d).toInt()))
        drumSeqWrap.addView(dsTop, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        drumSeqWrap.addView(dsProc, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val dsMaster = LinearLayout(this)
        dsMaster.orientation = LinearLayout.HORIZONTAL
        fun dsMSeek(label: String, init: Float, onChange: (Float) -> Unit): SeekBar {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.text = label
            lab.setTextColor(silk)
            lab.textSize = 8f
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.progress = (init * 100).toInt()
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pv: Int, fromUser: Boolean) {
                    if (fromUser) onChange(pv / 100f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            col.addView(sb)
            dsMaster.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            return sb
        }
        dsMSeek("MACRO A \u00b7 DECAY", 0.31f) { engine.nativeSetDrumDec(0.25f + 2.25f * it) }
        dsMSeek("MACRO B \u00b7 DRIVE", 0f) { engine.nativeSetDrumDrive(it) }
        dsMSeek("FILTRE LP\u2194HP", 0.5f) { engine.nativeSetDrumFil(it * 2f - 1f) }
        drumSeqWrap.addView(dsMaster, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val dsFxRow = LinearLayout(this)
        dsFxRow.orientation = LinearLayout.HORIZONTAL
        val dsFxBtnF = makeButton("F \u2192 MACROS", turquoise)
        dsFxBtnF.textSize = 9f
        dsFxBtnF.setOnClickListener { toggleDrumAxis(0) }
        dsFxRow.addView(dsFxBtnF, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val dsFxBtnZ = makeButton("Z \u2192 MACROS", amber)
        dsFxBtnZ.textSize = 9f
        dsFxBtnZ.setOnClickListener { toggleDrumAxis(1) }
        dsFxRow.addView(dsFxBtnZ, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        drumSeqWrap.addView(dsFxRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val dsVoicePanel = LinearLayout(this)
        dsVoicePanel.orientation = LinearLayout.VERTICAL
        dsVoicePanel.visibility = View.GONE
        val dsVoiceRow = LinearLayout(this)
        dsVoiceRow.orientation = LinearLayout.HORIZONTAL
        for (i in drumSeqGens.indices) {
            val vb = makeButton(drumSeqView.rowNames[i].take(6), silk)
            vb.textSize = 9f
            vb.setOnClickListener {
                drumVoiceSel = i
                refreshDrumVoicePanel()
            }
            drumVoiceBtns.add(vb)
            dsVoiceRow.addView(vb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        dsVoicePanel.addView(dsVoiceRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val dsVoiceSeekRow = LinearLayout(this)
        dsVoiceSeekRow.orientation = LinearLayout.HORIZONTAL
        for (k in 0 until 4) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.setTextColor(silk)
            lab.textSize = 8f
            drumVoiceLbls.add(lab)
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.progress = 50
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pv: Int, fromUser: Boolean) {
                    if (fromUser) engine.nativeSetFxParam(drumVoiceSlots[drumVoiceSel], k, pv / 100f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            drumVoiceSeeks.add(sb)
            col.addView(sb)
            dsVoiceSeekRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        dsVoicePanel.addView(dsVoiceSeekRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val dsMacRow = LinearLayout(this)
        dsMacRow.orientation = LinearLayout.HORIZONTAL
        fun dsVMacSeek(label: String, onVal: (Float) -> Unit): SeekBar {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.text = label
            lab.setTextColor(amber)
            lab.textSize = 8f
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.progress = 100
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pv: Int, fromUser: Boolean) {
                    if (fromUser) onVal(pv / 100f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            col.addView(sb)
            dsMacRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            return sb
        }
        drumVoiceMacSeeks.add(dsVMacSeek("MACRO A \u00b7 DECAY") {
            drumDecDepth[drumVoiceSel] = it
            engine.nativeSetDrumVoiceMacro(drumVoiceSel, 0, it)
        })
        drumVoiceMacSeeks.add(dsVMacSeek("MACRO B \u00b7 DRIVE") {
            drumDriveDepth[drumVoiceSel] = it
            engine.nativeSetDrumVoiceMacro(drumVoiceSel, 1, it)
        })
        dsVoicePanel.addView(dsMacRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        drumSeqWrap.addView(dsVoicePanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val dsVoice = makeButton("KIT", silk)
        dsVoice.textSize = 9f
        dsVoice.setOnClickListener {
            dsVoicePanel.visibility =
                if (dsVoicePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refreshDrumVoicePanel()
        }
        dsTop.addView(dsVoice, LinearLayout.LayoutParams((48 * d).toInt(), (32 * d).toInt()))
        drumSeqWrap.addView(
            drumSeqView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        val dsPads = LinearLayout(this)
        dsPads.orientation = LinearLayout.HORIZONTAL
        for (i in drumSeqGens.indices) {
            val pb = makeButton(drumSeqView.rowNames[i].take(5), cream)
            pb.textSize = 9f
            pb.setOnClickListener {
                if (drumSeqView.glitchMode || drumSeqView.mutMode) {
                    if (drumSeqView.glitchMode) drumGlitchOn[i] = !drumGlitchOn[i] else drumMutOn[i] = !drumMutOn[i]
                    val on = if (drumSeqView.glitchMode) drumGlitchOn[i] else drumMutOn[i]
                    pb.setTextColor(if (on) amber else silk)
                    bubble((if (drumSeqView.glitchMode) "GLITCH " else "MUTATION ") + drumSeqView.rowNames[i] + (if (on) " ON" else " OFF"))
                    return@setOnClickListener
                }
                if (!drumMuted) {
                    engine.nativeSetGenHitVel(1f)
                    engine.nativeSetGenHitPan(0.5f)
                    engine.nativeGenHit(drumSeqGens[i], i / 6f, 0.6f)
                }
                if (drumLiveRec && drumSeqRunning) {
                    val st = drumSeqView.playStep % drumSeqView.rowLen[i].coerceAtLeast(1)
                    drumSeqView.steps[i][st] = true
                    drumSeqView.invalidate()
                }
            }
            dsPads.addView(pb, LinearLayout.LayoutParams(0, (38 * d).toInt(), 1f))
        }
        drumSeqWrap.addView(
            dsPads,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (38 * d).toInt()
            )
        )
        padPane.addView(
            drumSeqWrap,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        midiSeqView = MidiSeqView(this)
        midiSeqView.onCellChange = { r, c, v ->
            val i = midiSeqSel
            if (midiLoopLen[i] <= 0) {
                midiLoopLen[i] = (240000f / bpmLcd.bpm.coerceAtLeast(40f)).toLong()
                midiLoopStart[i] = System.currentTimeMillis()
                midiLoopIdx[i] = -1
            }
            if (v >= 0f && !midiLoopOn[i] && !midiLoopRec[i]) {
                midiLoopStart[i] = System.currentTimeMillis()
                midiLoopIdx[i] = -1
                midiLoopOn[i] = true
            }
            midiLoopCells[i][r][c] = v
            if (v < 0f) {
                var empty = true
                outer@ for (rr in 0 until 6) for (cc in 0 until 16) {
                    if (midiLoopCells[i][rr][cc] >= 0f) {
                        empty = false
                        break@outer
                    }
                }
                if (empty) {
                    midiLoopOn[i] = false
                    midiLoopKeys[i].clear()
                }
            }
            refreshMidiLoopBtns()
        }
        midiSeqWrap = LinearLayout(this)
        midiSeqWrap.orientation = LinearLayout.VERTICAL
        midiSeqWrap.visibility = View.GONE
        midiSeqWrap.setBackgroundColor(Color.argb(225, 10, 11, 15))
        midiSeqWrap.setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt())
        val msTop = LinearLayout(this)
        msTop.orientation = LinearLayout.HORIZONTAL
        msTop.gravity = Gravity.CENTER_VERTICAL
        for (i in 0 until 8) {
            val lb = makeButton("L${i + 1}", silk)
            lb.textSize = 9f
            lb.isAllCaps = false
            val idx = i
            lb.setOnClickListener {
                midiSeqSel = idx
                rebuildMidiSeq()
            }
            midiLoopBtns.add(lb)
            msTop.addView(lb, LinearLayout.LayoutParams(0, (32 * d).toInt(), 1f))
        }
        val msHint = TextView(this)
        msHint.text = "TAP = PAS \u00b7 GLISSER = D\u00c9PLACER / VALEUR"
        msHint.setTextColor(silk)
        msHint.textSize = 8f
        msTop.addView(msHint, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))
        val msClose = Button(this)
        msClose.text = "\u00d7"
        msClose.textSize = 14f
        msClose.setTextColor(silk)
        msClose.isAllCaps = false
        msClose.minWidth = 0
        msClose.minimumWidth = 0
        msClose.setPadding(0, 0, 0, 0)
        msClose.setBackgroundColor(Color.TRANSPARENT)
        msClose.setOnClickListener { selectPadMode(0) }
        msTop.addView(msClose, LinearLayout.LayoutParams((30 * d).toInt(), (32 * d).toInt()))
        midiSeqWrap.addView(msTop, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        midiSeqWrap.addView(
            midiSeqView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        padPane.addView(
            midiSeqWrap,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        if (verticalUi) {
            content.orientation = LinearLayout.VERTICAL
            content.addView(
                padPane,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.1f
                )
            )
            content.addView(
                row1,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            content.addView(
                fzRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            val lower = LinearLayout(this)
            lower.orientation = LinearLayout.HORIZONTAL
            val lowerCol = LinearLayout(this)
            lowerCol.orientation = LinearLayout.VERTICAL
            lowerCol.setPadding((6 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            lowerCol.addView(presetRow, presetRowParams)
            lowerCol.addView(padGrid, gridParams)
            lowerCol.addView(zuLuBtns)
            val faderWrapV = LinearLayout(this)
            faderWrapV.orientation = LinearLayout.HORIZONTAL
            faderWrapV.gravity = Gravity.CENTER_HORIZONTAL
            faderWrapV.addView(
                zuLuFader,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
            lowerCol.addView(
                faderWrapV,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (40 * d).toInt()
                )
            )
            lowerCol.addView(
                View(this),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            )
            lowerCol.addView(faderRow, faderRowParams)
            lower.addView(
                lowerCol,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
            lower.addView(
                edgeColumn,
                LinearLayout.LayoutParams((50 * d).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
            )
            content.addView(
                lower,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.4f
                )
            )
        } else {
            content.orientation = LinearLayout.HORIZONTAL
            column.addView(fzRow, 0)
            leftColumn.addView(padPane, padPaneParams)
            val rightSide = LinearLayout(this)
            rightSide.orientation = LinearLayout.VERTICAL
            rightSide.clipChildren = false
            rightSide.addView(
                row1,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            val rightMain = LinearLayout(this)
            rightMain.orientation = LinearLayout.HORIZONTAL
            rightMain.addView(
                edgeColumn,
                LinearLayout.LayoutParams((50 * d).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
            )
            rightMain.addView(
                column,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
            rightSide.addView(
                rightMain,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            )
            content.addView(leftColumn, padParams)
            content.addView(
                rightSide,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
        }

        buildFxPanel(d)
        buildScalePanel(d)
        buildArpPanel(d)
        buildProjPanel(d)
        buildStepPanel(d)
        buildMenuPanel(d)
        refreshPresetRow()
        engine.nativeSetInGain(inKnob.value)
        refreshSceneSide()
        seqHandler.postDelayed(seqTick, 20)
        padPane.addView(
            arpOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        padPane.addView(
            stepOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        phyl = PhylactereView(this)
        phyl.onClose = {
            phyl.visibility = View.GONE
            phylHandler.removeCallbacksAndMessages(null)
        }
        phyl.visibility = View.GONE
        val phylParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        phylParams.topMargin = (54 * d).toInt()
        vuModule.addView(phyl, phylParams)

        val root = FrameLayout(this)
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            fxOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            scaleOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            projOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            menuOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        sirenPad = SirenPadView(this)
        sirenPad.visibility = View.GONE
        sirenPad.onCtl = { nx, ny -> if (sirenRunning) updateSirenCtl(nx, ny) }
        sirenPad.onStop = { stopSiren() }
        root.addView(
            sirenPad,
            FrameLayout.LayoutParams(0, 0, Gravity.TOP or Gravity.RIGHT)
        )
        root.post {
            val side = (root.height * 0.75f).toInt()
            val lp = sirenPad.layoutParams as FrameLayout.LayoutParams
            lp.width = side
            lp.height = side
            sirenPad.layoutParams = lp
        }

        val edgeL = View(this)
        val edgeR = View(this)
        root.addView(
            edgeL,
            FrameLayout.LayoutParams(margin, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT)
        )
        root.addView(
            edgeR,
            FrameLayout.LayoutParams(margin, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.RIGHT)
        )

        if (Build.VERSION.SDK_INT >= 29) {
            val applyExclusion = {
                val h = min(root.height, (200 * d).toInt())
                val top = (root.height - h) / 2
                edgeL.systemGestureExclusionRects = listOf(Rect(0, top, margin, top + h))
                edgeR.systemGestureExclusionRects =
                    listOf(Rect(root.width - margin, top, root.width, top + h))
            }
            root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyExclusion() }
            root.post { applyExclusion() }
        }

        root.setOnApplyWindowInsetsListener { v, insets ->
            val cut = if (Build.VERSION.SDK_INT >= 28) insets.displayCutout else null
            val dl = cut?.safeInsetLeft ?: 0
            val dr = cut?.safeInsetRight ?: 0
            val dt = cut?.safeInsetTop ?: 0
            val db = cut?.safeInsetBottom ?: 0
            val lp = content.layoutParams as FrameLayout.LayoutParams
            lp.setMargins(
                max(margin, dl), max((4 * d).toInt(), dt),
                max(margin, dr), max((4 * d).toInt(), db)
            )
            content.layoutParams = lp
            insets
        }

        setContentView(root)
        blinkHandler.post(blinkRunnable)
        vuHandler.post(vuRunnable)
        registerReceiver(larsenReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        playing = engine.nativeStart(false)
        if (!playing) {
            bubble("AUDIO : demarrage impossible", true)
        }
        for (f in 0 until 84) {
            val d = fxParamDefaults[f] ?: continue
            for (k in d.indices) engine.nativeSetFxParam(f, k, d[k])
        }
        refreshPlayBtn()
        applyAudioPrefs()
        if (globalFile(0).exists()) {
            loadProject(0, global = true)
        }
        if (projFile(autoSlot).exists()) {
            loadProject(autoSlot)
            bubble("SESSION RESTAUREE")
        }
        seedFactoryPresets()
        silenceAtStartup()
        safeSnapHandler.postDelayed(safeSnapRunnable, 2000L)
        phylHandler.postDelayed({ showWelcome() }, 900L)
    }

    private fun applyAudioPrefs() {
        engine.nativeSetInProfile(inProfileSel)
        engine.nativeSetOutLoud(outProfSel)
        engine.nativeSetInFilter(inFilVal)
        engine.nativeSetMasterFilter(outFilVal)
        engine.nativeSetMasterVolume(masterVol)
        engine.nativeSetInGain(inVol)
        vuMeter.outFilter = outFilVal
        bpmLcd.outFilter = outFilVal
        vuMeter.inFilter = inFilVal
        bpmLcd.inFilter = inFilVal
    }

    private fun saveUiPrefs() {
        val stIdx = if (ZuluTheme.retro) 1 else if (ZuluTheme.gameboy) 2 else if (ZuluTheme.electribe) 3 else if (ZuluTheme.caustic) 4 else 0
        getSharedPreferences("zulu", MODE_PRIVATE).edit()
            .putInt("styleIdx", stIdx)
            .putInt("bgColor", ZuluTheme.bgColor)
            .putInt("fontId", ZuluTheme.fontId)
            .putInt("timeSig", timeSigBeats)
            .putBoolean("bgDots", ZuluTheme.bgDots)
            .putFloat("zebraWidth", ZuluTheme.zebraWidthDp)
            .putFloat("dotSize", ZuluTheme.dotSizeDp)
            .putInt("zebraColor", ZuluTheme.zebraColor)
            .putInt("sirenType", sirenType)
            .putInt("sirenWave", sirenWave)
            .putInt("catColor", catColorSel)
            .putBoolean("xFiles", ZuluTheme.xFiles)
            .putBoolean("xyHelp", xyHelpOpt)
            .putBoolean("macroMode", macroMode)
            .putString(
                "macroFaders",
                (0 until 6).joinToString("|") { f ->
                    macroFaderTargets[f].joinToString(";") { tg ->
                        "${tg.first},${tg.second},${if (tg.third) 1 else 0}"
                    }
                }
            )
            .putInt("inProfile", inProfileSel)
            .putInt("outProfile", outProfSel)
            .putFloat("inFilVal", inFilVal)
            .putFloat("outFilVal", outFilVal)
            .putFloat("masterVol", masterVol)
            .putFloat("inVol", inVol)
            .putBoolean("verticalUi", verticalUi)
            .putBoolean("snapTransp", snapTransp)
            .putBoolean("sampleLog", showSampleLog)
            .putInt("fuseDelay", fuseDelayMs)
            .putInt("customScaleMask", customScaleMask)
            .putBoolean("genScaleOn", genScaleOn)
            .putBoolean("gridScaleOn", gridScaleOn)
            .apply()
    }

    private fun silenceAtStartup() {
        recEndHandler.removeCallbacksAndMessages(null)
        motionHandler.removeCallbacksAndMessages(null)
        if (recActive) engine.nativeStopRec()
        recActive = false
        recMotion = false
        recArmed = false
        recSnapSide = -1
        recArmPad = -1
        fRecTap = false
        zRecTap = false
        padGrid.recArmed = false
        padGrid.recPad = -1
        stopMotionPlayback()
        motionPlaying = -1
        arpOn = false
        pad.arpOn = false
        pushArp()
        genIdx = -1
        engine.nativeSetGen(-1)
        engine.nativeSetGenTouch(false)
        for (i in 0 until 2) {
            lfoOn[i] = false
            engine.nativeSetCustomLfo(
                i, lfoShapes[i], false, lfoRateIdxArr[i],
                lfoDepthIdxArr[i].toFloat(), lfoAmountIdxArr[i].toFloat(),
                lfoTargets[i], lfoWaves[i]
            )
        }
        for (b in 0 until 8) {
            midiPlaying[b] = false
            if (bankOn[b]) {
                if (engine.nativeBankHasLoop(b)) engine.nativeToggleBank(b)
                bankOn[b] = false
            }
            if (bankRev[b]) {
                bankRev[b] = false
                engine.nativeSetBankRev(b, false)
            }
        }
        punchLatched.fill(false)
        punchView?.releaseAll()
        for (g in 0 until 8) soloGroups[g] = false
        engine.nativeSetSoloMask(0)
        padGrid.soloMask = 0
        if (muteOn) {
            muteOn = false
            btnMute.text = "MUTE"
        }
        engine.nativeSetMute(false)
        for (a in 0 until 4) {
            axisMulti[a].clear()
            pushAxisMulti(a)
        }
        exitAssignMode()
        snapHold = false
        snapHoldOld.clear()
        snapHoldFx.clear()
        snapFxOverride.clear()
        activeZu = -1
        activeLu = -1
        refreshSnapGrid()
        refreshBlink()
    }

    private var swipeX0 = 0f
    private var swipeY0 = 0f

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchIsDown = true
                crossDownX = e.rawX
                crossDownY = e.rawY
                crossDownAt = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                touchIsDown = false
                val dx = e.rawX - crossDownX
                val dy = e.rawY - crossDownY
                val sw = resources.displayMetrics.widthPixels.toFloat()
                val sh = resources.displayMetrics.heightPixels.toFloat()
                val startLeft = crossDownX < sw * 0.12f
                val startTop = crossDownY < sh * 0.2f
                val endLeft = e.rawX < sw * 0.12f
                val endTop = e.rawY < sh * 0.2f
                val bigCross = Math.abs(dx) > sw * 0.75f && Math.abs(dy) > sh * 0.65f
                val fast = System.currentTimeMillis() - crossDownAt < 1600
                val now = System.currentTimeMillis()
                if (bigCross && fast && startLeft != endLeft && startTop != endTop) {
                    crossArmedAt = now
                    bubble("CROIX \u2014 SWIPEZ POUR ME PARLER", true)
                } else if (crossArmedAt > 0L) {
                    val swipe = Math.abs(dx) > sw * 0.35f && Math.abs(dx) > Math.abs(dy) * 1.5f &&
                        now - crossDownAt < 800
                    if (swipe && now - crossArmedAt < 3000L) {
                        crossArmedAt = 0L
                        restoreSafeSnap()
                        showMachineTalk()
                    } else if (now - crossArmedAt >= 3000L) {
                        crossArmedAt = 0L
                    }
                }
            }
        }
        if (e.actionMasked == MotionEvent.ACTION_CANCEL) touchIsDown = false
        if (fxOpen || paramView?.visibility == View.VISIBLE) {
            val loc = IntArray(2)
            btnFx.getLocationOnScreen(loc)
            val dd = resources.displayMetrics.density
            if (e.actionMasked == MotionEvent.ACTION_DOWN &&
                e.rawX >= loc[0] - 6 * dd && e.rawX <= loc[0] + btnFx.width + 6 * dd &&
                e.rawY >= loc[1] - 6 * dd && e.rawY <= loc[1] + btnFx.height + 6 * dd) {
                closeParamPage()
                if (fxOpen) closeFx()
                return true
            }
        }
        if (::projOverlay.isInitialized && projOverlay.visibility == View.VISIBLE) {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                swipeX0 = e.x
                swipeY0 = e.y
            } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                val dx = e.x - swipeX0
                val dy = e.y - swipeY0
                val dd = resources.displayMetrics.density
                if (Math.abs(dx) > 80 * dd && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    if (dx < 0 && projPage < 3) {
                        projPage++
                        refreshProjSlots()
                    } else if (dx > 0 && projPage > 0) {
                        projPage--
                        refreshProjSlots()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(e)
    }

    private fun metaFile(): File = File(projDir(), "meta.json")

    private fun saveProjMeta() {
        try {
            val json = JSONObject()
            val names = JSONObject()
            for ((k, v) in projNames) names.put(k.toString(), v)
            val cols = JSONObject()
            for ((k, v) in projCols) cols.put(k.toString(), v)
            json.put("names", names)
            json.put("cols", cols)
            metaFile().writeText(json.toString())
        } catch (e: Exception) {
        }
    }

    private fun loadProjMeta() {
        try {
            val f = metaFile()
            if (!f.exists()) return
            val json = JSONObject(f.readText())
            json.optJSONObject("names")?.let { nm ->
                for (key in nm.keys()) projNames[key.toInt()] = nm.getString(key)
            }
            json.optJSONObject("cols")?.let { cl ->
                for (key in cl.keys()) projCols[key.toInt()] = cl.getInt(key)
            }
        } catch (e: Exception) {
        }
    }

    private fun bankDisplayName(b: Int): String {
        val custom = bankLabels[b]
        if (custom.isNotEmpty()) return custom
        val frames = engine.nativeBankFrames(b)
        if (frames <= 0) return ""
        val dur = String.format(java.util.Locale.US, "%.1f", frames / 48000f)
        val t = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US).format(java.util.Date(bankRecAt[b]))
        return "REC $t \u00b7 ${dur}s"
    }

    private fun pushSampleLog(msg: String) {
        sampleLogView?.text = msg
        if (msg.startsWith("B")) {
            val name = msg.substringAfter("\u00b7 ", "").trim()
            if (name.isNotEmpty()) bpmLcd.trackName = name
        }
    }

    private fun askSampleName(b: Int) {
        val dd = resources.displayMetrics.density
        val et = android.widget.EditText(this)
        et.setText(bankLabels[b])
        et.setSingleLine(true)
        val wrap = android.widget.FrameLayout(this)
        wrap.setPadding((48 * dd).toInt(), (16 * dd).toInt(), (48 * dd).toInt(), 0)
        wrap.addView(et)
        android.app.AlertDialog.Builder(this)
            .setTitle("NOM SAMPLE B${b + 1}")
            .setView(wrap)
            .setPositiveButton("OK") { _, _ ->
                bankLabels[b] = et.text.toString().trim()
                pushSampleLog("B${b + 1} \u00b7 ${bankDisplayName(b)}")
            }
            .setNegativeButton("ANNULER", null)
            .show()
    }

    private fun askProjName(slot: Int) {
        val dd = resources.displayMetrics.density
        val et = android.widget.EditText(this)
        et.setText(projNames[slot] ?: "")
        et.setSingleLine(true)
        val cols = intArrayOf(cream, yellow, green, turquoise, pink, violet)
        var sel = projCols[slot] ?: 0
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding((48 * dd).toInt(), (16 * dd).toInt(), (48 * dd).toInt(), 0)
        row.addView(et)
        val swatches = ArrayList<Button>()
        val swRow = LinearLayout(this)
        for (c in cols) {
            val sb = Button(this)
            sb.text = "\u25a0"
            sb.setTextColor(c)
            sb.background = null
            sb.setOnClickListener {
                sel = c
                for (i2 in 0 until swatches.size) swatches[i2].alpha = if (cols[i2] == sel) 1f else 0.4f
            }
            swatches.add(sb)
            swRow.addView(sb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        for (i2 in 0 until swatches.size) swatches[i2].alpha = if (cols[i2] == sel) 1f else 0.4f
        row.addView(swRow)
        android.app.AlertDialog.Builder(this)
            .setTitle("PROJET $slot")
            .setView(row)
            .setPositiveButton("OK") { _, _ ->
                val nm = et.text.toString().trim()
                if (nm.isEmpty()) projNames.remove(slot) else projNames[slot] = nm
                projCols[slot] = sel
                saveProjMeta()
                refreshProjSlots()
            }
            .setNegativeButton("ANNULER", null)
            .show()
    }

    private fun panicReset() {
        recEndHandler.removeCallbacksAndMessages(null)
        motionHandler.removeCallbacksAndMessages(null)
        if (recActive) engine.nativeStopRec()
        recActive = false
        recMotion = false
        recArmed = false
        recSnapSide = -1
        recArmPad = -1
        padGrid.recArmed = false
        padGrid.recPad = -1
        stopMotionPlayback()
        for (b in 0 until 8) {
            if (bankOn[b]) {
                bankOn[b] = false
                engine.nativeToggleBank(b)
            }
            midiPlaying[b] = false
        }
        for (f in 0 until 84) {
            fxActive[f] = false
            engine.nativeSetFx(f, false)
        }
        genIdx = -1
        engine.nativeSetGen(-1)
        engine.nativeSetGenTouch(false)
        punchLatched.fill(false)
        punchView?.releaseAll()
        for (f in 0 until 84) fxParamInit[f] = false
        for (f in 0 until 84) {
            val df = fxParamDefaults[f]
            for (k in 0 until 8) engine.nativeSetFxParam(f, k, if (df != null && k < df.size) df[k] else 0f)
        }
        paramActive.fill(false)
        for (pp in 0 until 8) engine.nativeSetParam(pp, 0f, false)
        activeFilter = 0
        engine.nativeSetFilterType(0)
        assignXIdx = 0
        assignYIdx = 1
        engine.nativeSetAssign(0, 0)
        engine.nativeSetAssign(1, 1)
        refreshAssignLabels()
        for (a in 0 until 4) {
            axisMulti[a].clear()
            pushAxisMulti(a)
        }
        exitAssignMode()
        punchLatched.fill(false)
        for (g in 0 until 8) soloGroups[g] = false
        engine.nativeSetSoloMask(0)
        padGrid.soloMask = 0
        if (muteOn) {
            muteOn = false
            btnMute.text = "MUTE"
        }
        engine.nativeSetMute(false)
        arpOn = false
        pushArp()
        for (i in 0 until 2) {
            lfoOn[i] = false
            engine.nativeSetCustomLfo(
                i, lfoShapes[i], false, lfoRateIdxArr[i],
                lfoDepthIdxArr[i].toFloat(), lfoAmountIdxArr[i].toFloat(),
                lfoTargets[i], lfoWaves[i]
            )
        }
        activeZu = -1
        activeLu = -1
        refreshSnapGrid()
        refreshBlink()
        refreshFxHighlight()
        refreshBankFx()
        refreshPlayBtn()
        bubble("PANIQUE : TOUT EST CALME")
    }

    private fun wrapParams(marginStart: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.setMargins(marginStart, 0, 0, 0)
        return p
    }

    private fun buttonBackground(round: Boolean): Drawable {
        return ThemeButtonDrawable(round)
    }

    private fun makeButton(label: String, color: Int): Button {
        val d = resources.displayMetrics.density
        val b = Button(this)
        b.text = label
        b.setTextColor(color)
        b.textSize = 10f
        b.isAllCaps = false
        b.minHeight = 0
        b.minimumHeight = 0
        b.minimumWidth = 0
        b.setPadding((7 * d).toInt(), (5 * d).toInt(), (7 * d).toInt(), (5 * d).toInt())
        b.typeface = Typeface.SANS_SERIF
        b.background = buttonBackground(false)
        return b
    }

    private fun makeRoundButton(label: String, color: Int, textSize: Float): Button {
        val b = Button(this)
        b.text = label
        b.setTextColor(color)
        b.textSize = textSize * 0.8f
        b.isAllCaps = false
        b.minHeight = 0
        b.minimumHeight = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.typeface = Typeface.SANS_SERIF
        b.background = buttonBackground(true)
        return b
    }

    private fun toggleAxis(axisA: Boolean) {
        if (axisA) {
            axisAOn = !axisAOn
            pad.stateA = when {
                !axisAOn -> ZuluPad.AxisState.OFF
                axisAHold -> ZuluPad.AxisState.HOLD
                else -> ZuluPad.AxisState.MOM
            }
        } else {
            axisBOn = !axisBOn
            pad.stateB = when {
                !axisBOn -> ZuluPad.AxisState.OFF
                axisBHold -> ZuluPad.AxisState.HOLD
                else -> ZuluPad.AxisState.MOM
            }
        }
        updateAxisButtons()
    }

    private fun axisSolidBg(color: Int, dim: Boolean): GradientDrawable {
        val d = resources.displayMetrics.density
        val f = if (dim) 0.45f else 1f
        fun ch(c: Int) = (c * f).toInt()
        val gd = GradientDrawable()
        gd.cornerRadius = 8f * d
        gd.setColor(
            Color.argb(255, ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
        )
        gd.setStroke((1.5f * d).toInt(), Color.rgb(20, 22, 28))
        return gd
    }

    private fun updateAxisButtons() {
        btnF.text = when {
            !axisAOn -> "\u03a8 OFF"
            axisAHold -> "\u03a8 HOLD"
            else -> "\u03a8 MOM"
        }
        btnF.textSize = 9f
        btnF.setTextColor(Color.rgb(14, 16, 20))
        btnF.background = axisSolidBg(yellow, false)
        btnZ.text = when {
            !axisBOn -> "\u039e OFF"
            axisBHold -> "\u039e HOLD"
            else -> "\u039e MOM"
        }
        btnZ.textSize = 9f
        btnZ.setTextColor(Color.rgb(14, 16, 20))
        btnZ.background = axisSolidBg(pink, false)
    }

    private fun shiftActive() = shiftHeld || shiftLocked

    private fun checkSimulStart() {
        if (!simulLock && vuMeter.pressed && bpmLcd.pressed) {
            simulLock = true
            bpmLcd.inputEnabled = false
            togglePlay()
        } else if (simulLock && !vuMeter.pressed && !bpmLcd.pressed) {
            simulLock = false
            bpmLcd.inputEnabled = true
        }
    }

    private fun nextFreeGlobal(): Int {
        for (g in 1..16) if (!globalFile(g).exists()) return g
        return 16
    }

    private fun bubble(msg: String, long: Boolean = false) {
        if (!this::phyl.isInitialized) return
        phyl.confirmMode = false
        phyl.scaleX = 1f
        phyl.scaleY = 1f
        phyl.alpha = 1f
        phyl.message = msg
        phyl.visibility = View.VISIBLE
        phylHandler.removeCallbacksAndMessages(null)
        phylHandler.postDelayed({ phyl.visibility = View.GONE }, if (long) 4200L else 2400L)
    }

    private fun bubbleConfirm(
        msg: String, yes: () -> Unit, no: () -> Unit,
        yesLbl: String = "OUI", noLbl: String = "NON"
    ) {
        if (!this::phyl.isInitialized) return
        phyl.confirmMode = true
        phyl.scaleX = 1f
        phyl.scaleY = 1f
        phyl.alpha = 1f
        phyl.yesLabel = yesLbl
        phyl.noLabel = noLbl
        phyl.message = msg
        phyl.onYes = { phyl.visibility = View.GONE; yes() }
        phyl.onNo = { phyl.visibility = View.GONE; no() }
        phyl.visibility = View.VISIBLE
        phylHandler.removeCallbacksAndMessages(null)
    }

    private fun sosHint(t: String): Boolean {
        if (!sosMode) return false
        bubble(t, true)
        return true
    }

    private fun showWelcome() {
        bubbleConfirm(
            "BONJOUR FLYING ZULU ! ON REPREND LÀ OÙ ON S'ÉTAIT ARRÊTÉ ?",
            { bubble("DE RETOUR DANS LE PROJET, FLYING ZULU !") },
            {
                bubbleConfirm(
                    "NOUVEAU PROJET : TOUT NEUF OU EN CHARGER UN EXISTANT ?",
                    {
                        panicReset()
                        bubble("PROJET TOUT NEUF PRÊT. BONNE CRÉATION !", true)
                    },
                    {
                        openProjPanel()
                        bubble("CHOISIS UN PROJET DANS LA LISTE.", true)
                    },
                    "TOUT NEUF", "EXISTANT"
                )
            },
            "REPRENDRE", "NOUVEAU"
        )
    }

    private val machineTips = arrayOf(
        "BONJOUR FLYING ZULU. JE SUIS LA MACHINE. TU AS TROUV\u00c9 MON CODE SECRET.",
        "SHIFT+TAP SUR UNE BANQUE FX = L'ACTIVE OU L'\u00c9TEINT.",
        "APPUI LONG SUR UNE BANQUE FX = SON MENU. VIDE ? ELLE PROPOSE SES EFFETS.",
        "UNE BANQUE VIDE EST UN PAD MUET. C'EST \u00c0 TOI DE LA REMPLIR.",
        "SHIFT+MUTE EN MODE SAMPLE = MINI-MIXEUR. LES PADS 9-16 METTENT UNE RANG\u00c9E EN SOLO.",
        "SHIFT+DOUBLE TAP MUTE = D\u00c9PLACER LES SAMPLES. MAINTIENS SUR LA CIBLE POUR FUSIONNER.",
        "MODE KIT : APPUI LONG 600MS SUR UN PAD = ENREGISTRER DEDANS.",
        "LA BOU\u00c9E ROUGE DANS LE MENU, C'EST LE MODE SOS. CHAQUE COMMANDE S'EXPLIQUE.",
        "DRUMS+FX ENSEMBLE = FORCER OU D\u00c9FORCER LES EFFETS DE LA BO\u00ceTE \u00c0 RYTHME.",
        "SHIFT+DRUMS = MUTE LA BO\u00ceTE \u00c0 RYTHME. LE BOUTON CLIGNOTE.",
        "SWIPE \u00c0 DROITE DANS LA BO\u00ceTE \u00c0 RYTHME = 32 PAS.",
        "GLITCH ET MUTATION SE S\u00c9QUENCENT PAS PAR PAS. LE PATTERN VIT SA VIE.",
        "ZOU EST TURQUOISE \u00c0 GAUCHE, LOU EST VIOLET \u00c0 DROITE. LA GRILLE TE LE RAPPELLE.",
        "DOUBLE TAP SUR LE VU-M\u00c8TRE = LA CONSOLE AUDIO.",
        "JE GARDE TON \u00c9TAT PROPRE : LA CROIX N'EFFRAIE NI LES POINTS NI LES FADERS.",
        "REVIENS ME VOIR QUAND TU VEUX. SWIPE ET JE PARLE. \u00c0 BIENT\u00d4T, FLYING ZULU."
    )
    private var machineTipIdx = 0

    private fun showMachineTalk() {
        val msg = machineTips[machineTipIdx % machineTips.size]
        machineTipIdx++
        bubbleConfirm(
            msg,
            { showMachineTalk() },
            { },
            "ENCORE", "FERMER"
        )
    }

    private fun showGoodbye() {
        bubbleConfirm(
            "AU REVOIR FLYING ZULU. VOUS AVEZ PENSÉ À SAUVEGARDER ?",
            {
                saveProject(activeGroupSel, global = true)
                bubble("PROJET SAUVÉ. À BIENTÔT, FLYING ZULU !", true)
                phylHandler.postDelayed({ moveTaskToBack(true) }, 1800L)
            },
            { bubble("RESTONS ENCORE UN PEU, ALORS !") },
            "SAUVER & QUITTER", "RESTER"
        )
    }

    private var crossArmedAt = 0L
    private var crossDownX = 0f
    private var crossDownY = 0f
    private var crossDownAt = 0L
    private var touchIsDown = false
    private val safeSnap = FloatArray(7)
    private var safeSnapAt = 0L
    private val safeSnapHandler = Handler(Looper.getMainLooper())
    private val safeSnapRunnable = object : Runnable {
        override fun run() {
            takeSafeSnap()
            safeSnapHandler.postDelayed(this, 2000L)
        }
    }

    private fun takeSafeSnap() {
        if (touchIsDown) return
        safeSnap[0] = pad.ax
        safeSnap[1] = pad.ay
        safeSnap[2] = pad.bx
        safeSnap[3] = pad.by
        safeSnap[4] = flyFader.value
        safeSnap[5] = mixFader.value
        safeSnap[6] = zuLuFader.value
        safeSnapAt = System.currentTimeMillis()
    }

    private fun restoreSafeSnap() {
        if (safeSnapAt <= 0L) return
        pad.setCursor(safeSnap[0], safeSnap[1], safeSnap[2], safeSnap[3])
        engine.nativeSetAx(safeSnap[0])
        engine.nativeSetAy(safeSnap[1])
        engine.nativeSetBx(safeSnap[2])
        engine.nativeSetBy(safeSnap[3])
        flyFader.setValue(safeSnap[4])
        engine.nativeSetFly(flyFader.value)
        mixFader.value = safeSnap[5]
        engine.nativeSetMix(mixFader.value)
        zuLuFader.value = safeSnap[6]
        applyMorph(zuLuFader.value)
        safeSnapAt = 0L
    }

    private fun toggleSolo(group: Int) {
        soloGroups[group] = !soloGroups[group]
        var mask = 0
        for (g in 0 until 8) if (soloGroups[g]) mask = mask or (1 shl g)
        engine.nativeSetSoloMask(mask)
        padGrid.soloMask = mask
        bpmLcd.groupLabel = "G${group + 1}"
    }

    private fun startResampleUI() {
        val bank = (0 until 8).firstOrNull { !engine.nativeBankHasLoop(it) } ?: return
        engine.nativeResample(bank, loopLenSteps)
        resetSteps(bank)
        recActive = true
        recMotion = false
        recArmPad = bank + 1
        padGrid.recPad = bank
        val ms = (loopLenSteps * 60000f / bpmLcd.bpm.coerceAtLeast(40f)).toLong()
        recEndHandler.removeCallbacksAndMessages(null)
        recEndHandler.postDelayed({ padGrid.recPad = -1 }, ms + 200)
    }

    private fun togglePlay() {
        if (!playing) {
            playing = engine.nativeStart(false)
            bubble(if (playing) "MOTEUR ON" else "MOTEUR : ECHEC")
        } else {
            engine.nativeStop()
            playing = false
            vuMeter.levelL = 0f
            vuMeter.levelR = 0f
            bubble("MOTEUR OFF")
        }
        refreshPlayBtn()
    }

    private fun refreshPlayBtn() {
        playShape.playing = playing
        playShape.invalidateSelf()
        btnPlay.invalidate()
    }

    private fun refreshShift() {
        padGrid.shiftMode = shiftActive()
        if (!shiftActive()) {
            btnShift.alpha = 1f
            pad.bendActive = false
            glitchScope.stop()
        }
    }

    private fun buildFxPanel(d: Float) {
        fxOverlay = FrameLayout(this)
        fxOverlay.setBackgroundColor(Color.argb(190, 8, 9, 12))
        fxOverlay.visibility = View.GONE
        fxOverlay.setOnClickListener { closeFx() }

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        val panelBg = GradientDrawable()
        panelBg.setColor(Color.rgb(24, 26, 32))
        panelBg.setStroke((1 * d).toInt(), Color.rgb(75, 82, 98))
        panelBg.cornerRadius = 8 * d
        panel.background = panelBg
        panel.setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())

        val titleRow = LinearLayout(this)
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "FX SELECT \u00b7 v${versionLabel()}"
        title.setTextColor(Color.rgb(236, 224, 196))
        title.textSize = 16f
        title.typeface = markerFont(this)
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(this)
        close.text = "\u00d7"
        close.setTextColor(silk)
        close.textSize = 16f
        close.minHeight = 0
        close.minimumHeight = 0
        close.minimumWidth = 0
        close.setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
        close.typeface = markerFont(this)
        close.background = buttonBackground(false)
        close.setOnClickListener { closeFx() }
        titleRow.addView(close)
        panel.addView(titleRow)

        val editB = makeButton("EDIT", silk)
        editB.textSize = 11f
        editB.setOnClickListener {
            editFxMode = !editFxMode
            editB.setTextColor(if (editFxMode) amber else silk)
        }
        panel.addView(
            editB,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * d).toInt() }
        )

        val scroll = android.widget.ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        scroll.addView(list)

        var globalIdx = 0
        var catIdx = 0
        for ((section, effects) in fxSections) {
            val catCol = fxCatCols[catIdx % fxCatCols.size]
            catIdx++
            val header = TextView(this)
            header.text = section
            header.setTextColor(catCol)
            header.textSize = 11f
            header.typeface = markerFont(this)
            header.setPadding(0, (10 * d).toInt(), 0, (4 * d).toInt())
            list.addView(header)

            var row: LinearLayout? = null
            for (fx in effects) {
                if (row == null || row.childCount == 4) {
                    row = LinearLayout(this)
                    list.addView(row)
                }
                val b = makeButton(fx, catCol)
                val bg = GradientDrawable()
                bg.setColor(Color.rgb(16, 18, 24))
                bg.setStroke((1 * d).toInt(), catCol)
                bg.cornerRadius = 6 * d
                b.background = bg
                val idx = globalIdx
                fxButtonCols.add(catCol)
                b.setOnClickListener {
                    activeFx = idx
                    val code = fxCodes.getOrNull(idx) ?: ""
                    if (code.length >= 2) {
                        val num = code.substring(1).toIntOrNull() ?: 0
                        when (code[0]) {
                            'F' -> {
                                activeFilter = num
                                if (!(snapHold && snapTransp)) engine.nativeSetFilterType(num)
                            }
                            'X' -> {
                                val slot = fxSlot(num)
                                if ((shiftActive() || editFxMode) && fxParamDefs.containsKey(slot)) {
                                    openParamPage(slot)
                                } else if (fxPickBank >= 0) {
                                    if (userPresets[fxPickBank] == null) saveUserPreset(fxPickBank)
                                    val pr = userPresets[fxPickBank]
                                    if (pr != null && num < pr.fxOn.size) {
                                        pr.fxOn[num] = !pr.fxOn[num]
                                        fxActive[num] = pr.fxOn[num]
                                        if (!(snapHold && snapQuietFor(num))) engine.nativeSetFx(num, fxActive[num])
                                        bubble("${fx} \u2192 BANQUE ${fxPickBank + 1} " + (if (pr.fxOn[num]) "ON" else "OFF"))
                                        refreshPresetRow()
                                    }
                                } else {
                                    fxActive[num] = !fxActive[num]
                                    if (!(snapHold && snapQuietFor(num))) engine.nativeSetFx(num, fxActive[num])
                                }
                            }
                            'P' -> {
                                paramActive[num] = !paramActive[num]
                                if (!(snapHold && snapTransp)) engine.nativeSetParam(
                                    num,
                                    if (paramActive[num]) paramDefaults[num] else 0f,
                                    paramActive[num]
                                )
                            }
                            'G' -> {
                                if ((shiftActive() || editFxMode) && fxParamDefs.containsKey(genSlot(num))) {
                                    openParamPage(genSlot(num))
                                } else if (fxPickBank >= 0) {
                                    if (userPresets[fxPickBank] == null) saveUserPreset(fxPickBank)
                                    val pr = userPresets[fxPickBank]
                                    if (pr != null) {
                                        pr.gen = num
                                        genIdx = num
                                        engine.nativeSetGen(num)
                                        bubble("SYNTH \u2192 BANQUE ${fxPickBank + 1}")
                                        refreshPresetRow()
                                    }
                                } else {
                                    genIdx = num
                                    engine.nativeSetGen(num)
                                }
                                if (num >= 29 && num <= 31) {
                                    arpOn = true
                                    arpMode = if (num == 29) 0 else if (num == 30) 2 else 4
                                    arpRateIdx = if (num == 29) 2 else if (num == 30) 3 else 1
                                    arpOctIdx = if (num == 29) 1 else 2
                                    arpOctJump = num == 31
                                    pushArp()
                                    bubble("HARPE : ARPEGIATEUR PRET A TESTER")
                                }
                            }
                        }
                    }
                    refreshFxHighlight()
                }
                fxButtons.add(b)
                val gp = LinearLayout.LayoutParams(0, (34 * d).toInt(), 1f)
                gp.setMargins((3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt(), (3 * d).toInt())
                row.addView(b, gp)
                globalIdx++
            }
        }

        val hint = TextView(this)
        hint.text = "TAP = ON/OFF \u00b7 SHIFT+TAP = R\u00c9GLAGES \u00b7 SYNTH = JOUER SUR LE PAD"
        hint.setTextColor(Color.rgb(120, 128, 146))
        hint.textSize = 10f
        hint.typeface = Typeface.SANS_SERIF
        hint.setPadding(0, (8 * d).toInt(), 0, 0)
        panel.addView(hint)

        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (300 * d).toInt()
            ).apply { topMargin = (4 * d).toInt() }
        )

        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        )
        panelParams.setMargins((6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
        fxOverlay.addView(panel, panelParams)
    }

    private fun refreshFxHighlight() {
        for ((i, b) in fxButtons.withIndex()) {
            val code = fxCodes.getOrNull(i) ?: ""
            val num = if (code.length >= 2) code.substring(1).toIntOrNull() ?: -1 else -1
            val on = when (code.getOrNull(0)) {
                'F' -> activeFilter == num
                'X' -> num in fxActive.indices && fxActive[num]
                'P' -> num in paramActive.indices && paramActive[num]
                'G' -> genIdx == num
                else -> false
            }
            val master = code.getOrNull(0) == 'X' && num in masterFx
            b.setTextColor(if (on) Color.rgb(14, 16, 20) else if (master) violet else fxButtonCols.getOrElse(i) { silk })
            val bg = b.background as? ThemeButtonDrawable
            if (bg != null) {
                val want = when {
                    on -> amber
                    master -> violet
                    else -> 0
                }
                if (bg.fillColor != want) {
                    bg.fillColor = want
                    bg.invalidateSelf()
                }
            }
        }
        refreshPresetRow()
    }

    private fun refreshAssignLabels() {
        btnAssignX?.text = "X : ${assignNames[assignXIdx]}"
        btnAssignY?.text = "Y : ${assignNames[assignYIdx]}"
    }

    private fun showMacroPanel() {
        macroMode = true
        macroPanel.visibility = View.VISIBLE
        pad.visibility = View.INVISIBLE
        saveUiPrefs()
        bubble("MODE MACROS")
    }

    private fun hideMacroPanel() {
        macroMode = false
        macroPanel.visibility = View.GONE
        pad.visibility = View.VISIBLE
        saveUiPrefs()
        bubble("MODE XY")
    }

    private fun openFx() {
        fxOpen = true
        refreshFxHighlight()
        fxOverlay.visibility = View.VISIBLE
    }

    private fun closeFx() {
        fxOpen = false
        fxPickBank = -1
        fxOverlay.visibility = View.GONE
        exitAssignMode()
    }

    private fun buildScalePanel(d: Float) {
        scaleOverlay = FrameLayout(this)
        scaleOverlay.setBackgroundColor(Color.argb(190, 8, 9, 12))
        scaleOverlay.visibility = View.GONE
        scaleOverlay.setOnClickListener { closeScalePanel() }

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        val panelBg = GradientDrawable()
        panelBg.setColor(Color.rgb(24, 26, 32))
        panelBg.setStroke((1 * d).toInt(), Color.rgb(75, 82, 98))
        panelBg.cornerRadius = 8 * d
        panel.background = panelBg
        panel.setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())

        val titleRow = LinearLayout(this)
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "GAMME"
        title.setTextColor(Color.rgb(236, 224, 196))
        title.textSize = 16f
        title.typeface = markerFont(this)
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(this)
        close.text = "\u00d7"
        close.setTextColor(silk)
        close.textSize = 16f
        close.minHeight = 0
        close.minimumHeight = 0
        close.minimumWidth = 0
        close.setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
        close.typeface = markerFont(this)
        close.background = buttonBackground(false)
        close.setOnClickListener { closeScalePanel() }
        titleRow.addView(close)
        panel.addView(titleRow)

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        panel.addView(list)

        val scales = listOf("MIN PENTA", "MAJ PENTA", "HIRAJOSHI", "IN SEN", "IWATO", "CHROM", "CUSTOM")
        val scaleHeader = TextView(this)
        scaleHeader.text = "GAMME"
        scaleHeader.setTextColor(amber)
        scaleHeader.textSize = 11f
        scaleHeader.typeface = markerFont(this)
        scaleHeader.setPadding(0, (10 * d).toInt(), 0, (4 * d).toInt())
        list.addView(scaleHeader)
        var row: LinearLayout? = null
        for ((i, name) in scales.withIndex()) {
            if (row == null || row.childCount == 3) {
                row = LinearLayout(this)
                list.addView(row)
            }
            val b = makeButton(name, silk)
            b.setOnClickListener {
                activeScale = i
                engine.nativeSetScale(i)
                refreshScaleHighlight()
            }
            row.addView(b, wrapParams((4 * d).toInt()))
        }

        val customHeader = TextView(this)
        customHeader.text = "CUSTOM \u00b7 NOTES DE LA GAMME"
        customHeader.setTextColor(amber)
        customHeader.textSize = 11f
        customHeader.typeface = markerFont(this)
        customHeader.setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
        list.addView(customHeader)
        val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        var noteRow: LinearLayout? = null
        for (i in noteNames.indices) {
            if (noteRow == null || noteRow.childCount == 6) {
                noteRow = LinearLayout(this)
                list.addView(noteRow)
            }
            val nb = makeButton(noteNames[i], silk)
            nb.textSize = 9f
            nb.setOnClickListener {
                val bit = 1 shl i
                val wasOn = customScaleMask and bit != 0
                var newMask = if (wasOn) customScaleMask and bit.inv() else customScaleMask or bit
                if (newMask == 0) newMask = bit
                customScaleMask = newMask
                engine.nativeSetCustomScaleMask(customScaleMask)
                if (!wasOn) auditionScaleNote(i)
                saveUiPrefs()
                refreshScaleHighlight()
            }
            noteRow.addView(nb, wrapParams((4 * d).toInt()))
        }

        val roots = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val rootHeader = TextView(this)
        rootHeader.text = "FONDAMENTALE"
        rootHeader.setTextColor(amber)
        rootHeader.textSize = 11f
        rootHeader.typeface = markerFont(this)
        rootHeader.setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
        list.addView(rootHeader)
        var rootRow: LinearLayout? = null
        for ((i, name) in roots.withIndex()) {
            if (rootRow == null || rootRow.childCount == 4) {
                rootRow = LinearLayout(this)
                list.addView(rootRow)
            }
            val b = makeButton(name, silk)
            b.setOnClickListener {
                activeRoot = i
                engine.nativeSetRoot(i)
                refreshScaleHighlight()
            }
            rootRow.addView(b, wrapParams((4 * d).toInt()))
        }

        val applyHeader = TextView(this)
        applyHeader.text = "APPLIQU\u00c9E \u00c0"
        applyHeader.setTextColor(amber)
        applyHeader.textSize = 11f
        applyHeader.typeface = markerFont(this)
        applyHeader.setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
        list.addView(applyHeader)
        val applyRow = LinearLayout(this)
        val kbBtn = makeButton("CLAVIER", silk)
        kbBtn.setOnClickListener {
            genScaleOn = !genScaleOn
            engine.nativeSetGenScaleOn(genScaleOn)
            saveUiPrefs()
            refreshScaleIndicators()
        }
        applyRow.addView(kbBtn, wrapParams((4 * d).toInt()))
        val gdBtn = makeButton("GRID 16", silk)
        gdBtn.setOnClickListener {
            gridScaleOn = !gridScaleOn
            engine.nativeSetGridScaleOn(gridScaleOn)
            saveUiPrefs()
            refreshScaleIndicators()
        }
        applyRow.addView(gdBtn, wrapParams((4 * d).toInt()))
        list.addView(applyRow)

        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        )
        panelParams.setMargins((6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
        scaleOverlay.addView(panel, panelParams)
    }

    private fun refreshScaleIndicators() {
        keysScaleBtn?.text = if (genScaleOn) "ON" else "OFF"
        keysScaleBtn?.setTextColor(if (genScaleOn) amber else silk)
        padGrid.scaleHint = gridScaleOn
    }

    private fun toggleMidiLoop(i: Int) {
        if (midiLoopRec[i]) {
            midiLoopRec[i] = false
            val len = System.currentTimeMillis() - midiLoopStart[i]
            if (len < 250) {
                midiLoopEvents[i].clear()
                midiLoopOn[i] = false
                bubble("LOOP MIDI ${i + 1} VIDE")
            } else {
                midiLoopLen[i] = len
                midiLoopOn[i] = true
                midiLoopIdx[i] = -1
                midiLoopLastT[i] = 0
                quantizeMidiLoop(i)
                bubble("LOOP MIDI ${i + 1} : " + "%.1f".format(len / 1000f) + "S")
            }
            if (padGrid.padMode == 4 && this::midiSeqView.isInitialized) rebuildMidiSeq()
        } else {
            midiLoopEvents[i].clear()
            midiLoopOn[i] = false
            midiLoopRec[i] = true
            midiLoopStart[i] = System.currentTimeMillis()
            midiLoopLastT[i] = 0
            bubble("REC LOOP MIDI ${i + 1} \u2014 JOUE ET RE-TAP POUR FIXER", true)
        }
    }

    private fun quantizeMidiLoop(i: Int) {
        val len = midiLoopLen[i]
        if (len <= 0) return
        val keys = ArrayList<String>()
        val cells = Array(6) { FloatArray(16) { -1f } }
        midiLoopEvents[i].forEach { e ->
            val r = keys.indexOf(e.second)
            val row = if (r >= 0) r else {
                if (keys.size >= 6) return@forEach
                keys.add(e.second)
                keys.size - 1
            }
            val step = ((e.first * 16f) / len.toFloat()).toInt().coerceIn(0, 15)
            cells[row][step] = e.third.coerceIn(0f, 1f)
        }
        midiLoopKeys[i].clear()
        midiLoopKeys[i].addAll(keys)
        for (r in 0 until 6) midiLoopCells[i][r] = cells[r].copyOf()
        midiLoopEvents[i].clear()
    }

    private fun rebuildMidiSeq() {
        if (!this::midiSeqView.isInitialized) return
        val i = midiSeqSel
        for (r in 0 until midiSeqView.rows) {
            midiSeqView.rowNames[r] = midiLoopKeys[i].getOrNull(r) ?: ""
            for (c in 0 until midiSeqView.cols) midiSeqView.cells[r][c] = midiLoopCells[i][r][c]
        }
        midiSeqView.invalidate()
        refreshMidiLoopBtns()
    }

    private fun refreshMidiLoopBtns() {
        for (b in midiLoopBtns.indices) {
            val btn = midiLoopBtns[b]
            val active = b == midiSeqSel
            val playing = midiLoopOn[b]
            btn.setTextColor(
                when {
                    active && playing -> Color.rgb(150, 90, 255)
                    active -> amber
                    playing -> silk
                    else -> Color.rgb(90, 96, 110)
                }
            )
            btn.text = if (midiLoopRec[b]) "\u25cf" else "L${b + 1}"
        }
    }

    private fun selectPadMode(m: Int) {
        if (sosHint("MODES : SCN = SCENES ZU/LU, MOT = MOTION, SMP = SAMPLE, GRP = GROUPES, MID = LOOPS MIDI.")) return
        if (m != 2) {
            miniMixMode = false
            sampleMoveMode = false
            padGrid.miniMixMode = false
            padGrid.moveMode = false
        }
        padGrid.padMode = m
        sceneMode = if (m == 1) 1 else 0
        groupMode = m == 3
        padGrid.groupMode = groupMode
        if (groupMode) padGrid.groupFlashAt = System.currentTimeMillis()
        flashModeOnGrid(m)
        refreshModeBtns()
        refreshMenu()
        if (this::midiSeqWrap.isInitialized) {
            midiSeqWrap.visibility = if (m == 4) View.VISIBLE else View.GONE
            if (m == 4) rebuildMidiSeq()
        }
    }

    private fun refreshModeBtns() {
        val cols = intArrayOf(
            Color.rgb(255, 190, 0), Color.rgb(0, 240, 255),
            Color.rgb(70, 255, 130), Color.rgb(0, 229, 255),
            Color.rgb(150, 90, 255)
        )
        val labels = listOf("SCN", "MOT", "SMP", "", "MID")
        for (m in modeBtnArray.indices) {
            val b = modeBtnArray[m]
            val active = padGrid.padMode == m
            b.setTextColor(if (active) cols[m] else silk)
            b.text = when (m) {
                3 -> if (groupMode && activeGroupSel > 0) "G$activeGroupSel" else "GRP"
                else -> labels[m]
            }
        }
    }

    private fun flashModeOnGrid(m: Int) {
        padGrid.modeFlash = when (m) {
            1 -> "MOTION"
            2 -> "SAMPLES"
            3 -> "GROUPE"
            4 -> "MIDI"
            else -> "SC\u00c8NE"
        }
        padGrid.modeFlashCol = when (m) {
            1 -> Color.rgb(0, 240, 255)
            2 -> Color.rgb(70, 255, 130)
            3 -> Color.rgb(0, 229, 255)
            4 -> Color.rgb(150, 90, 255)
            else -> Color.rgb(255, 190, 0)
        }
        padGrid.modeFlashAt = System.currentTimeMillis()
        padGrid.invalidate()
    }

    private fun refreshScaleHighlight() {
        val panel = scaleOverlay.getChildAt(0) as? LinearLayout ?: return
        val list = panel.getChildAt(1) as? LinearLayout ?: return
        var idx = 0
        for (r in 0 until list.childCount) {
            val rowView = list.getChildAt(r) as? LinearLayout ?: continue
            for (c in 0 until rowView.childCount) {
                val b = rowView.getChildAt(c) as? Button ?: continue
                when {
                    idx < 7 -> b.setTextColor(if (idx == activeScale) amber else silk)
                    idx < 19 -> {
                        val n = idx - 7
                        val on = customScaleMask and (1 shl n) != 0
                        b.setTextColor(
                            when {
                                on && activeScale == 6 -> amber
                                on -> green
                                else -> silk
                            }
                        )
                    }
                    else -> {
                        val n = idx - 19
                        when {
                            n < 12 -> b.setTextColor(if (n == activeRoot) amber else silk)
                            n == 12 -> b.setTextColor(if (genScaleOn) amber else silk)
                            else -> b.setTextColor(if (gridScaleOn) amber else silk)
                        }
                    }
                }
                idx++
            }
        }
    }

    private fun auditionScaleNote(note: Int) {
        var deg = 0
        for (i in 0 until note) if (customScaleMask and (1 shl i) != 0) deg++
        val axv = deg / 25f
        engine.nativeSetGen(18)
        engine.nativeSetGenOct(0)
        engine.nativeSetAx(axv)
        engine.nativeGenHit(18, axv, 0.5f)
    }

    private fun openScalePanel() {
        refreshScaleHighlight()
        refreshScaleIndicators()
        scaleOverlay.visibility = View.VISIBLE
    }

    private fun closeScalePanel() {
        scaleOverlay.visibility = View.GONE
    }

    private fun projDir(): File = File(filesDir, "projects")

    private fun projFile(slot: Int): File = File(projDir(), "proj_$slot.json")

    private fun globalFile(i: Int): File = File(projDir(), "global_$i.json")

    private fun stateFile(slot: Int, global: Boolean): File =
        if (global) globalFile(slot) else projFile(slot)

    private fun bankFile(slot: Int, bank: Int, global: Boolean = false): File =
        File(projDir(), if (global) "global_${slot}_b$bank.pcm" else "proj_${slot}_b$bank.pcm")

    private fun buildProjPanel(d: Float) {
        loadProjMeta()
        projOverlay = FrameLayout(this)
        projOverlay.setBackgroundColor(Color.argb(190, 8, 9, 12))
        projOverlay.visibility = View.GONE
        projOverlay.setOnClickListener { closeProjPanel() }

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        val panelBg = GradientDrawable()
        panelBg.setColor(Color.rgb(24, 26, 32))
        panelBg.setStroke((1 * d).toInt(), Color.rgb(75, 82, 98))
        panelBg.cornerRadius = 8 * d
        panel.background = panelBg
        panel.setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())

        val titleRow = LinearLayout(this)
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "PROJET"
        title.setTextColor(Color.rgb(236, 224, 196))
        title.textSize = 16f
        title.typeface = markerFont(this)
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(this)
        close.text = "\u00d7"
        close.setTextColor(silk)
        close.textSize = 16f
        close.minHeight = 0
        close.minimumHeight = 0
        close.setPadding(0, 0, 0, 0)
        close.background = null
        close.setOnClickListener { closeProjPanel() }
        val prevB = Button(this)
        prevB.text = "\u2039"
        prevB.setTextColor(silk)
        prevB.textSize = 16f
        prevB.minHeight = 0
        prevB.minimumHeight = 0
        prevB.setPadding(0, 0, 0, 0)
        prevB.background = null
        prevB.setOnClickListener { if (projPage > 0) { projPage--; refreshProjSlots() } }
        titleRow.addView(prevB, LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt()))
        val pageLbl = TextView(this)
        pageLbl.setTextColor(Color.rgb(120, 128, 146))
        pageLbl.textSize = 10f
        pageLbl.typeface = markerFont(this)
        projPageLabel = pageLbl
        titleRow.addView(pageLbl, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val nextB = Button(this)
        nextB.text = "\u203a"
        nextB.setTextColor(silk)
        nextB.textSize = 16f
        nextB.minHeight = 0
        nextB.minimumHeight = 0
        nextB.setPadding(0, 0, 0, 0)
        nextB.background = null
        nextB.setOnClickListener { if (projPage < 3) { projPage++; refreshProjSlots() } }
        titleRow.addView(nextB, LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt()))
        titleRow.addView(close, LinearLayout.LayoutParams((30 * d).toInt(), (30 * d).toInt()))
        panel.addView(titleRow)

        val hint = TextView(this)
        hint.text = "TAP = CHARGER \u00b7 APUI LONG = SAUVER \u00b7 DOUBLE-TAP = NOMMER \u00b7 SWIPE = PAGE"
        hint.setTextColor(Color.rgb(120, 128, 146))
        hint.textSize = 10f
        panel.addView(hint)

        for (s in 1..8) {
            val b = Button(this)
            b.text = "PROJ $s"
            b.setTextColor(silk)
            b.textSize = 14f
            b.minHeight = 0
            b.minimumHeight = 0
            b.setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt())
            val bg = GradientDrawable()
            bg.setColor(Color.rgb(16, 18, 24))
            bg.setStroke((1 * d).toInt(), Color.rgb(60, 66, 82))
            bg.cornerRadius = 6 * d
            b.background = bg
            b.setOnClickListener {
                val slot = projPage * 8 + s
                val now = System.currentTimeMillis()
                if (lastProjTapSlot == slot && now - lastProjTapAt < 400) {
                    lastProjTapAt = 0
                    askProjName(slot)
                } else {
                    lastProjTapSlot = slot
                    lastProjTapAt = now
                    loadProject(slot)
                }
            }
            b.setOnLongClickListener { saveProject(projPage * 8 + s); true }
            projSlotButtons.add(b)
            panel.addView(
                b,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (6 * d).toInt() }
            )
        }

        projOverlay.addView(
            panel,
            FrameLayout.LayoutParams(
                (240 * d).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    private fun refreshProjSlots() {
        for (i in 0 until 8) {
            val slot = projPage * 8 + i + 1
            val b = projSlotButtons[i]
            val exists = projFile(slot).exists()
            val name = projNames[slot]
            b.text = (if (exists) "\u25cf " else "") + (name ?: "PROJ $slot")
            val col = projCols[slot]
            b.setTextColor(when {
                col != null -> col
                exists -> amber
                else -> silk
            })
        }
        projPageLabel?.text = "PAGE ${projPage + 1}/4"
    }

    private fun openProjPanel() {
        refreshProjSlots()
        projOverlay.visibility = View.VISIBLE
    }

    private fun closeProjPanel() {
        projOverlay.visibility = View.GONE
    }

    private fun saveProject(slot: Int, global: Boolean = false) {
        val dir = projDir()
        dir.mkdirs()
        try {
            val json = JSONObject()
            json.put("version", 1)
            json.put("bpm", bpmLcd.bpm)
            json.put("padCount", padGrid.padCount)
            json.put("loopLenSteps", loopLenSteps)
            json.put("filter", activeFilter)
            json.put("scale", activeScale)
            json.put("root", activeRoot)
            json.put("assignX", assignXIdx)
            json.put("assignY", assignYIdx)
            json.put("mix", mixFader.value)
            json.put("fly", flyFader.value)
            json.put("soloMask", soloGroups.fold(0) { m, on -> (m shl 1) or if (on) 1 else 0 })
            val presets = JSONArray()
            for (p in userPresets) {
                if (p == null) {
                    presets.put(JSONObject.NULL)
                } else {
                    presets.put(
                        JSONObject()
                            .put("filter", p.filter)
                            .put("scale", p.scale)
                            .put("root", p.root)
                            .put("arpOn", p.arpOn)
                            .put("arpMode", p.arpMode)
                            .put("arpRate", p.arpRate)
                            .put("arpOct", p.arpOct)
                            .put("arpGate", p.arpGate)
                            .put("arpOctJump", p.arpOctJump)
                            .put("arpSlices", p.arpSlices)
                            .put("assignX", p.assignX)
                            .put("assignY", p.assignY)
                            .put("fxOn", JSONArray(p.fxOn.map { if (it) 1 else 0 }))
                            .put("fxP", JSONArray(p.fxParams.map { JSONArray(it.toList()) }))
                            .put("fxA", JSONArray(p.fxAssign.map { JSONArray(it.toList()) }))
                            .put("lfoTg", JSONArray(p.lfoTg.toList()))
                            .put("gen", p.gen)
                    )
                }
            }
            json.put("presets", presets)
            val linksJ = JSONArray()
            for (g in 0 until 9) {
                val ga = JSONArray()
                for (lk in bankLinks[g]) {
                    ga.put(JSONArray(listOf(lk.src, lk.dst, lk.amount, lk.prob, lk.chop, lk.delay, lk.soft)))
                }
                linksJ.put(ga)
            }
            json.put("links", linksJ)
            val banks = JSONArray()
            for (b in 0 until 8) {
                val frames = engine.nativeBankFrames(b)
                banks.put(frames)
                bankFile(slot, b, global).delete()
                if (frames > 0) {
                    val data = FloatArray(frames * 2)
                    if (engine.nativeCopyBank(b, data) > 0) {
                        val bb = ByteBuffer.allocate(frames * 8).order(ByteOrder.LITTLE_ENDIAN)
                        for (f in data) bb.putFloat(f)
                        bankFile(slot, b, global).writeBytes(bb.array())
                    }
                }
            }
            json.put("banks", banks)
            val stepsArr = JSONArray()
            for (b in 0 until 8) {
                stepsArr.put(
                    JSONObject()
                        .put("count", bankSteps[b])
                        .put("pitch", JSONArray(stepPitch[b].toList()))
                        .put("vel", JSONArray(stepVel[b].toList()))
                )
            }
            json.put("steps", stepsArr)
            val snaps = JSONArray()
            for (s in snapPads) {
                if (s == null) {
                    snaps.put(JSONObject.NULL)
                } else {
                    val o = JSONObject()
                        .put("ax", s.ax.toDouble())
                        .put("ay", s.ay.toDouble())
                        .put("bx", s.bx.toDouble())
                        .put("by", s.by.toDouble())
                        .put("mix", s.mix.toDouble())
                        .put("filter", s.filter)
                        .put("assignX", s.assignX)
                        .put("assignY", s.assignY)
                        .put("punchCol", s.punchCol2)
                    if (s.punch != null) {
                        o.put("punch", JSONArray(s.punch.map { if (it) 1 else 0 }))
                    }
                    if (s.motA != null) o.put("motA", JSONArray(s.motA.map { it.toDouble() }))
                    if (s.motB != null) o.put("motB", JSONArray(s.motB.map { it.toDouble() }))
                    if (s.motLen > 0) o.put("motLen", s.motLen)
                    if (s.motMode != 0) o.put("motMode", s.motMode)
                    if (s.motAmt != 0.5f) o.put("motAmt", s.motAmt.toDouble())
                    snaps.put(o)
                }
            }
            json.put("snaps", snaps)
            val mloops = JSONArray()
            for (b in 0 until 8) {
                val arr = JSONArray()
                for (e in midiLoops[b]) arr.put(JSONArray(e.toList()))
                mloops.put(arr)
            }
            json.put("midiloops", mloops)
            json.put("recMidi", recMidiEngines)
            val lfos = JSONArray()
            for (i in 0 until 2) {
                lfos.put(
                    JSONObject()
                        .put("on", lfoOn[i])
                        .put("rate", lfoRateIdxArr[i])
                        .put("depth", lfoDepthIdxArr[i])
                        .put("amount", lfoAmountIdxArr[i])
                        .put("target", lfoTargets[i])
                        .put("wave", lfoWaves[i])
                        .put("shape", JSONArray(lfoShapes[i].toList()))
                )
            }
            json.put("lfos", lfos)
            json.put("arpTarget", arpTargetTg)
            json.put("arpAmount", arpAmountIdx)
            json.put("activeZu", activeZu)
            json.put("activeLu", activeLu)
            json.put("recFlavor", recFlavor)
            json.put("masterComp", masterCompSel)
            json.put("midiCh", ZuluMidi.globalChannel)
            val mo = JSONObject()
            for ((k, m) in ZuluMidi.outMap) mo.put(k, m.first * 128 + (m.second + 1))
            json.put("midiMap", mo)
            json.put("envFollow", JSONObject().put("on", envOn).put("target", envTarget).put("amt", envAmt.toDouble()).put("gain", envGain.toDouble()))
            json.put("logic", JSONObject().put("on", logOn).put("a", logSrcA).put("b", logSrcB).put("gate", logGate).put("target", logTarget).put("depth", logDepth.toDouble()))
            json.put("punchCol", punchCol)
            val pfxA = JSONArray()
            val pprA = JSONArray()
            val pvlA = JSONArray()
            for (c in 0 until 10) for (z in 0 until 9) {
                pfxA.put(punchFx[c][z]); pprA.put(punchParam[c][z]); pvlA.put((punchVal[c][z] * 100).toInt())
            }
            json.put("punchFx", pfxA)
            json.put("punchParam", pprA)
            json.put("punchVal", pvlA)
            json.put("fxSend", JSONArray(bankFxSend.toList()))
            json.put("stretch", JSONArray(bankStretch.toList()))
            json.put("slotMask", JSONArray(bankSlotMask.toList()))
            json.put("slotRev", JSONArray(bankSlotRev.toList()))
            val svArr = JSONArray()
            val sp2Arr = JSONArray()
            val spiArr = JSONArray()
            val spaArr = JSONArray()
            val stoArr = JSONArray()
            for (b in 0 until 8) {
                svArr.put(JSONArray(bankSlotVol[b].toList()))
                sp2Arr.put(JSONArray(bankSlotProb[b].toList()))
                spiArr.put(JSONArray(bankSlotPitch[b].toList()))
                spaArr.put(JSONArray(bankSlotPan[b].toList()))
                stoArr.put(JSONArray(bankSlotToff[b].toList()))
            }
            json.put("slotVol", svArr)
            json.put("slotProb", sp2Arr)
            json.put("slotPitch", spiArr)
            json.put("slotPan", spaArr)
            json.put("slotToff", stoArr)
            val spArr = JSONArray()
            for (b in 0 until 8) spArr.put(JSONArray(sampleParamVals[b].toList()))
            json.put("sampleParams", spArr)
            json.put("bankNames", JSONArray(bankLabels.toList()))
            projFile(slot).writeText(json.toString())
            refreshProjSlots()
        } catch (e: Exception) {
        }
    }

    private fun loadProject(slot: Int, global: Boolean = false) {
        val f = stateFile(slot, global)
        if (!f.exists()) return
        try {
            val json = JSONObject(f.readText())
            bpmLcd.bpm = json.getDouble("bpm").toFloat()
            engine.nativeSetBpm(bpmLcd.bpm)
            applyPadCount(json.optInt("padCount", 12))
            loopLenSteps = json.optInt("loopLenSteps", 8)
            padGrid.activeBank = loopLenSteps
            activeFilter = json.optInt("filter", 0)
            engine.nativeSetFilterType(activeFilter)
            activeScale = json.optInt("scale", 0)
            activeRoot = json.optInt("root", 9)
            engine.nativeSetScale(activeScale)
            engine.nativeSetRoot(activeRoot)
            recFlavor = json.optInt("recFlavor", 0)
            engine.nativeSetRecFlavor(recFlavor)
            masterCompSel = json.optInt("masterComp", 0)
            engine.nativeSetMasterComp(masterCompSel)
            ZuluMidi.globalChannel = json.optInt("midiCh", 0).coerceIn(0, 15)
            val mo = json.optJSONObject("midiMap")
            ZuluMidi.outMap.clear()
            if (mo != null) {
                for (k in mo.keys()) {
                    val e = mo.optInt(k, -1)
                    if (e >= 0) ZuluMidi.outMap[k] = (e / 128) to (e % 128 - 1)
                }
            }
            val ef = json.optJSONObject("envFollow")
            if (ef != null) {
                envOn = ef.optBoolean("on", false)
                envTarget = ef.optInt("target", -1)
                envAmt = ef.optDouble("amt", 0.5).toFloat()
                envGain = ef.optDouble("gain", 4.0).toFloat()
                applyEnv()
            }
            val lg = json.optJSONObject("logic")
            if (lg != null) {
                logOn = lg.optBoolean("on", false)
                logSrcA = lg.optInt("a", 0)
                logSrcB = lg.optInt("b", 1)
                logGate = lg.optInt("gate", 0)
                logTarget = lg.optInt("target", -1)
                logDepth = lg.optDouble("depth", 1.0).toFloat()
            }
            val fsArr = json.optJSONArray("fxSend")
            if (fsArr != null) {
                for (b in 0 until min(8, fsArr.length())) {
                    bankFxSend[b] = fsArr.getBoolean(b)
                    engine.nativeSetBankFxSend(b, bankFxSend[b])
                }
            }
            val stArr = json.optJSONArray("stretch")
            if (stArr != null) {
                for (b in 0 until min(8, stArr.length())) {
                    bankStretch[b] = stArr.optInt(b, 0).coerceIn(0, 3)
                    engine.nativeSetStretchMode(b, bankStretch[b])
                    sampleParamVals[b][6] = bankStretch[b] / 3f
                    engine.nativeSetSampleParam(b, 6, bankStretch[b] / 3f)
                }
            }
            val smArr = json.optJSONArray("slotMask")
            if (smArr != null) {
                for (b in 0 until min(8, smArr.length())) {
                    bankSlotMask[b] = smArr.optInt(b, 255).coerceIn(0, 255)
                    engine.nativeSetBankSlots(b, bankSlotMask[b])
                }
            }
            val rvArr = json.optJSONArray("slotRev")
            if (rvArr != null) {
                for (b in 0 until min(8, rvArr.length())) {
                    bankSlotRev[b] = rvArr.optInt(b, 0)
                    engine.nativeSetBankSlotRev(b, bankSlotRev[b])
                }
            }
            val svArr = json.optJSONArray("slotVol")
            val sp2Arr = json.optJSONArray("slotProb")
            val spiArr = json.optJSONArray("slotPitch")
            val spaArr = json.optJSONArray("slotPan")
            val stoArr = json.optJSONArray("slotToff")
            if (svArr != null && sp2Arr != null) {
                for (b in 0 until min(8, min(svArr.length(), sp2Arr.length()))) {
                    val va = svArr.optJSONArray(b)
                    val pa = sp2Arr.optJSONArray(b)
                    val pi = spiArr?.optJSONArray(b)
                    val pn = spaArr?.optJSONArray(b)
                    val to = stoArr?.optJSONArray(b)
                    if (va != null && pa != null) {
                        for (s in 0 until min(12, min(va.length(), pa.length()))) {
                            bankSlotVol[b][s] = va.optDouble(s, 1.0).toFloat().coerceIn(0f, 1f)
                            bankSlotProb[b][s] = pa.optDouble(s, 1.0).toFloat().coerceIn(0f, 1f)
                            engine.nativeSetBankSlotParam(b, s, bankSlotVol[b][s], bankSlotProb[b][s])
                            if (pi != null && s < pi.length()) bankSlotPitch[b][s] = pi.optDouble(s, 0.5).toFloat().coerceIn(0f, 1f)
                            if (pn != null && s < pn.length()) bankSlotPan[b][s] = pn.optDouble(s, 0.5).toFloat().coerceIn(0f, 1f)
                            if (to != null && s < to.length()) bankSlotToff[b][s] = to.optDouble(s, 0.5).toFloat().coerceIn(0f, 1f)
                            engine.nativeSetBankSlotSeq(b, s, bankSlotPitch[b][s], bankSlotPan[b][s], bankSlotToff[b][s])
                        }
                    }
                }
            }
            val spArr = json.optJSONArray("sampleParams")
            if (spArr != null) {
                for (b in 0 until min(8, spArr.length())) {
                    val sa = spArr.optJSONArray(b)
                    if (sa != null) {
                        for (k in 0 until min(11, sa.length())) {
                            val v = sa.optDouble(k, 0.0).toFloat().coerceIn(0f, 1f)
                            sampleParamVals[b][k] = v
                            engine.nativeSetSampleParam(b, k, v)
                        }
                    }
                }
            }
            for (b in 0 until 8) {
                val g = sampleParamVals[b].getOrNull(9) ?: 0f
                bankSlotN[b] = if (g < 1f / 3f) 8 else if (g < 2f / 3f) 10 else 12
            }
            val bnArr = json.optJSONArray("bankNames")
            if (bnArr != null) {
                for (b in 0 until min(8, bnArr.length())) bankLabels[b] = bnArr.getString(b)
            }
            for (b in 0 until 8) bankRecAt[b] = System.currentTimeMillis()
            punchCol = json.optInt("punchCol", 0).coerceIn(0, punchLabels.size - 1)
            val pfxA = json.optJSONArray("punchFx")
            val pprA = json.optJSONArray("punchParam")
            val pvlA = json.optJSONArray("punchVal")
            if (pfxA != null && pprA != null && pvlA != null) {
                for (c in 0 until 10) for (z in 0 until 9) {
                    val i = c * 9 + z
                    if (i < pfxA.length()) punchFx[c][z] = pfxA.optInt(i, punchFx[c][z]).coerceIn(-1, 45)
                    if (i < pprA.length()) punchParam[c][z] = pprA.optInt(i, punchParam[c][z]).coerceIn(-1, 7)
                    if (i < pvlA.length()) punchVal[c][z] = pvlA.optInt(i, (punchVal[c][z] * 100).toInt()).coerceIn(0, 100) / 100f
                }
            }
            punchView?.setCollectionIndex(punchCol)
            punchView?.setLabels(punchLabels[punchCol])
            assignXIdx = json.optInt("assignX", 0)
            assignYIdx = json.optInt("assignY", 1)
            engine.nativeSetAssign(0, assignXIdx)
            engine.nativeSetAssign(1, assignYIdx)
            refreshAssignLabels()
            mixFader.value = json.getDouble("mix").toFloat()
            engine.nativeSetMix(mixFader.value)
            flyFader.setValue(json.getDouble("fly").toFloat())
            engine.nativeSetFly(flyFader.value)
            val sm = json.optInt("soloMask", 0)
            for (g in 0 until 8) soloGroups[g] = (sm and (1 shl g)) != 0
            engine.nativeSetSoloMask(sm)
            padGrid.soloMask = sm
            val presets = json.getJSONArray("presets")
            for (i in 0 until min(9, presets.length())) {
                if (presets.isNull(i)) {
                    userPresets[i] = null
                    pad.userPresets[i] = false
                } else {
                    val o = presets.getJSONObject(i)
                    userPresets[i] = UserPreset(
                        filter = o.getInt("filter"),
                        scale = o.getInt("scale"),
                        root = o.getInt("root"),
                        arpOn = o.getBoolean("arpOn"),
                        arpMode = o.getInt("arpMode"),
                        arpRate = o.getInt("arpRate"),
                        arpOct = o.getInt("arpOct"),
                        arpGate = o.getInt("arpGate"),
                        arpOctJump = o.getBoolean("arpOctJump"),
                        arpSlices = o.optInt("arpSlices", 2),
                        assignX = o.optInt("assignX", 0),
                        assignY = o.optInt("assignY", 1),
                        fxOn = BooleanArray(84) { f ->
                            val a = o.optJSONArray("fxOn")
                            a != null && f < a.length() && a.optInt(f, 0) == 1
                        },
                        fxParams = Array(84) { f ->
                            val a = o.optJSONArray("fxP")
                            FloatArray(8) { k ->
                                if (a != null && f < a.length())
                                    a.optJSONArray(f)?.optDouble(k, 0.0)?.toFloat() ?: 0f
                                else 0f
                            }
                        },
                        fxAssign = Array(84) { f ->
                            val a = o.optJSONArray("fxA")
                            IntArray(8) { k ->
                                if (a != null && f < a.length())
                                    a.optJSONArray(f)?.optInt(k, 0) ?: 0
                                else 0
                            }
                        },
                        lfoTg = IntArray(2) { l ->
                            val a = o.optJSONArray("lfoTg")
                            if (a != null && l < a.length()) a.optInt(l, -1) else -1
                        },
                        gen = o.optInt("gen", -1)
                    )
                    pad.userPresets[i] = true
                }
            }
            val linksJ = json.optJSONArray("links")
            for (g in 0 until 9) {
                bankLinks[g].clear()
                val ga = linksJ?.optJSONArray(g) ?: continue
                for (j in 0 until ga.length()) {
                    val a = ga.optJSONArray(j) ?: continue
                    if (a.length() < 7) continue
                    bankLinks[g].add(ParamLink(
                        a.optInt(0), a.optInt(1), a.optDouble(2, 0.5).toFloat(),
                        a.optDouble(3, 1.0).toFloat(), a.optInt(4, 0),
                        a.optDouble(5, 0.0).toFloat(), a.optDouble(6, 0.0).toFloat()
                    ))
                }
            }
            pushLinks()
            pad.invalidate()
            pushArp()
            refreshScaleHighlight()
            val banks = json.getJSONArray("banks")
            for (b in 0 until min(8, banks.length())) {
                val frames = banks.getInt(b)
                if (frames <= 0) continue
                val bf = bankFile(slot, b, global)
                if (!bf.exists() || bf.length() < frames * 8L) continue
                val bytes = bf.readBytes()
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val data = FloatArray(frames * 2)
                for (i in data.indices) data[i] = bb.float
                engine.nativeLoadBank(b, data, frames)
            }
            val stepsArr = json.optJSONArray("steps")
            if (stepsArr != null) {
                for (b in 0 until min(8, stepsArr.length())) {
                    val o = stepsArr.getJSONObject(b)
                    bankSteps[b] = o.optInt("count", 0)
                    val pa = o.optJSONArray("pitch")
                    val va = o.optJSONArray("vel")
                    for (s in 0 until 14) {
                        if (pa != null && s < pa.length()) stepPitch[b][s] = pa.getDouble(s).toFloat()
                        if (va != null && s < va.length()) stepVel[b][s] = va.getDouble(s).toFloat()
                    }
                    if (bankSteps[b] > 0) {
                        engine.nativeSetStepCount(b, bankSteps[b])
                        for (s in 0 until 14) {
                            engine.nativeSetStep(b, s, stepPitch[b][s], stepVel[b][s])
                        }
                    }
                }
            }
            val snaps = json.optJSONArray("snaps")
            if (snaps != null) {
                for (i in 0 until min(14, snaps.length())) {
                    if (snaps.isNull(i)) {
                        snapPads[i] = null
                    } else {
                        val o = snaps.getJSONObject(i)
                        val pa = o.optJSONArray("punch")
                        val pArr = if (pa != null) BooleanArray(pa.length()) { k -> pa.optInt(k, 0) == 1 } else null
                        fun farr(name: String): FloatArray? {
                            val a2 = o.optJSONArray(name) ?: return null
                            return FloatArray(a2.length()) { k -> a2.getDouble(k).toFloat() }
                        }
                        snapPads[i] = Snap(
                            ax = o.getDouble("ax").toFloat(),
                            ay = o.getDouble("ay").toFloat(),
                            bx = o.getDouble("bx").toFloat(),
                            by = o.getDouble("by").toFloat(),
                            mix = o.getDouble("mix").toFloat(),
                            filter = o.getInt("filter"),
                            assignX = o.optInt("assignX", 0),
                            assignY = o.optInt("assignY", 1),
                            punch = pArr,
                            punchCol2 = o.optInt("punchCol", 0),
                            motA = farr("motA"),
                            motB = farr("motB"),
                            motLen = o.optInt("motLen", 0),
                            motMode = o.optInt("motMode", 0),
                            motAmt = o.optDouble("motAmt", 0.5).toFloat().coerceIn(0f, 1f)
                        )
                    }
                }
            }
            val mloops = json.optJSONArray("midiloops")
            if (mloops != null) {
                for (b in 0 until min(8, mloops.length())) {
                    midiLoops[b].clear()
                    val arr = mloops.optJSONArray(b) ?: continue
                    for (k in 0 until arr.length()) {
                        val e = arr.optJSONArray(k) ?: continue
                        if (e.length() >= 5) {
                            midiLoops[b].add(FloatArray(5) { t -> e.getDouble(t).toFloat() })
                        }
                    }
                }
            }
            recMidiEngines = json.optBoolean("recMidi", true)
            val lfos = json.optJSONArray("lfos")
            if (lfos != null) {
                for (i in 0 until min(2, lfos.length())) {
                    val o = lfos.getJSONObject(i)
                    lfoOn[i] = o.optBoolean("on", false)
                    lfoRateIdxArr[i] = o.optInt("rate", 2)
                    lfoDepthIdxArr[i] = o.optInt("depth", 2)
                    lfoAmountIdxArr[i] = o.optInt("amount", 3)
                    lfoTargets[i] = o.optInt("target", -1)
                    lfoWaves[i] = o.optInt("wave", 0)
                    val sh = o.optJSONArray("shape")
                    if (sh != null && sh.length() == 64) {
                        for (j in 0 until 64) lfoShapes[i][j] = sh.getDouble(j).toFloat()
                    }
                }
                pushLfo(0)
                pushLfo(1)
                lfoDraw?.setShape(lfoShapes[lfoSel])
            }
            arpTargetTg = json.optInt("arpTarget", -1)
            arpAmountIdx = json.optInt("arpAmount", 1).coerceIn(0, 3)
            activeZu = json.optInt("activeZu", -1)
            activeLu = json.optInt("activeLu", -1)
            refreshSnapGrid()
            applyMorph(zuLuFader.value)
            closeProjPanel()
        } catch (e: Exception) {
        }
    }

    private fun applyPadCount(n: Int) {
        padGrid.padCount = 16
        drumGrid.padCount = 16
        padGrid.requestLayout()
    }

    private fun buildArpPanel(d: Float) {
        arpOverlay = FrameLayout(this)
        arpOverlay.setBackgroundColor(Color.argb(225, 8, 9, 12))
        arpOverlay.visibility = View.GONE

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        val panelBg = GradientDrawable()
        panelBg.setColor(Color.rgb(24, 26, 32))
        panelBg.setStroke((1 * d).toInt(), Color.rgb(75, 82, 98))
        panelBg.cornerRadius = 8 * d
        panel.background = panelBg
        panel.setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())

        val titleRow = LinearLayout(this)
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "ARPEGGIATEUR"
        title.setTextColor(Color.rgb(236, 224, 196))
        title.textSize = 16f
        title.typeface = markerFont(this)
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(this)
        close.text = "\u00d7"
        close.setTextColor(silk)
        close.textSize = 16f
        close.minHeight = 0
        close.minimumHeight = 0
        close.minimumWidth = 0
        close.setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
        close.typeface = markerFont(this)
        close.background = buttonBackground(false)
        close.setOnClickListener { closeArpPanel() }
        titleRow.addView(close)
        panel.addView(titleRow)

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val listScroll = ScrollView(this)
        listScroll.addView(list)

        fun header(t: String) {
            val h = TextView(this)
            h.text = t
            h.setTextColor(amber)
            h.textSize = 11f
            h.typeface = Typeface.SANS_SERIF
            h.setPadding(0, (10 * d).toInt(), 0, (4 * d).toInt())
            list.addView(h)
        }

        fun row(labels: List<String>, kind: String, onClick: (Int) -> Unit) {
            val r = LinearLayout(this)
            for ((i, l) in labels.withIndex()) {
                val b = makeButton(l, silk)
                b.tag = kind to i
                b.setOnClickListener { onClick(i) }
                arpButtons.add(b)
                r.addView(b, wrapParams((4 * d).toInt()))
            }
            list.addView(r)
        }

        row(listOf("ON"), "on") { _ ->
            arpOn = !arpOn
            pad.arpOn = arpOn
            pad.invalidate()
            pushArp()
        }
        header("MODE")
        row(listOf("UP", "DOWN", "UP-DN", "RND", "XY"), "mode") { i ->
            arpMode = i
            pushArp()
        }
        header("VITESSE")
        row(listOf("1/4", "1/8", "1/16", "1/32"), "rate") { i ->
            arpRateIdx = i
            pushArp()
        }
        header("OCTAVES")
        row(listOf("x1", "x2", "x3", "x4"), "oct") { i ->
            arpOctIdx = i
            pushArp()
        }
        header("GATE")
        row(listOf("25%", "50%", "75%", "100%"), "gate") { i ->
            arpGateIdx = i
            pushArp()
        }
        header("COMMUNICATION")
        row(listOf("TIRETTES"), "comm") { _ -> openCommDialog() }
        header("SLICES (BOUCLES)")
        row(listOf("2", "4", "8", "12", "16"), "slices") { i ->
            arpSlicesIdx = i
            pushArp()
        }
        header("BONUS")
        row(listOf("OCT JUMP"), "jump") { _ ->
            arpOctJump = !arpOctJump
            pushArp()
        }
        header("DESTINATION (TAPER POUR ROUTER)")
        for (l in 0..2) {
            val who = if (l < 2) "LFO " + ('A' + l) else "ARP"
            val r = LinearLayout(this)
            val lab = makeButton(who, silk)
            lab.setOnClickListener {
                if (l < 2) {
                    lfoSel = l
                    lfoDraw?.setShape(lfoShapes[l])
                }
                lfoBrowserFor = l
                lfoBrowserSec = -1
                showLfoBrowser()
                refreshArpHighlight()
            }
            if (l < 2) arpButtons.add(lab)
            r.addView(lab, wrapParams((4 * d).toInt()))
            val dest = makeButton("RIEN", green)
            dest.tag = "ldest" to l
            dest.setOnClickListener {
                if (l < 2) {
                    lfoSel = l
                    lfoDraw?.setShape(lfoShapes[l])
                }
                lfoBrowserFor = l
                lfoBrowserSec = -1
                showLfoBrowser()
                refreshArpHighlight()
            }
            arpButtons.add(dest)
            r.addView(dest, wrapParams((4 * d).toInt()))
            list.addView(r)
        }
        val lfoBox = LinearLayout(this)
        lfoBox.orientation = LinearLayout.VERTICAL
        lfoBox.visibility = View.GONE
        lfoBrowserBox = lfoBox
        list.addView(lfoBox)
        header("MONTANT ARP")
        row(listOf("25%", "50%", "75%", "100%"), "aramt") { i ->
            arpAmountIdx = i
            pushArp()
        }
        header("SYNTH\u00c9 \u00b7 TONE")
        row(listOf("SUIT", "SAW", "SQR", "SIN", "TRI"), "swave") { i ->
            synWaveIdx = i
            pushSynth()
        }
        header("SYNTH\u00c9 \u00b7 ATTAQUE (MS)")
        row(listOf("0", "25", "50", "100", "250"), "satk") { i ->
            synAtkIdx = i
            pushSynth()
        }
        header("SYNTH\u00c9 \u00b7 D\u00c9CLIN")
        row(listOf("x0.5", "x1", "x2", "x4"), "sdec") { i ->
            synDecIdx = i
            pushSynth()
        }
        header("SYNTH\u00c9 \u00b7 OCTAVE")
        row(listOf("-2", "-1", "0", "+1", "+2"), "soct") { i ->
            synOctIdx = i
            pushSynth()
        }
        header("SYNTH\u00c9 \u00b7 PITCH (DEMIS-TONS)")
        row(listOf("-12", "-5", "0", "+5", "+12"), "spit") { i ->
            synPitIdx = i
            pushSynth()
        }
        header("SYNTH\u00c9 \u00b7 TUNE (CENTS)")
        row(listOf("-50", "0", "+50"), "stun") { i ->
            synTunIdx = i
            pushSynth()
        }

        header("LFO CUSTOM")
        val lfoTabs = LinearLayout(this)
        for ((i, l) in listOf("LFO A", "LFO B").withIndex()) {
            val b = makeButton(l, silk)
            b.tag = "ltab" to i
            b.setOnClickListener {
                lfoSel = i
                lfoDraw?.setShape(lfoShapes[i])
                refreshArpHighlight()
            }
            arpButtons.add(b)
            lfoTabs.addView(b, wrapParams((4 * d).toInt()))
        }
        list.addView(lfoTabs)

        header("FORME")
        row(listOf("SIN", "TRI", "SAW", "SQR", "DESSIN"), "lfw") { i ->
            lfoWaves[lfoSel] = i
            pushLfo(lfoSel)
        }

        val draw = LfoDrawView(this)
        draw.setShape(lfoShapes[lfoSel])
        draw.onShapeChange = { s ->
            lfoShapes[lfoSel] = s
            pushLfo(lfoSel)
        }
        lfoDraw = draw
        list.addView(draw, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (140 * d).toInt()))

        header("VITESSE (HZ)")
        row(listOf("0.25", "0.5", "1", "2", "4", "8"), "lfov") { i ->
            lfoRateIdxArr[lfoSel] = i
            pushLfo(lfoSel)
        }
        header("PROF")
        row(listOf("25%", "50%", "75%", "100%"), "lfod") { i ->
            lfoDepthIdxArr[lfoSel] = i
            pushLfo(lfoSel)
        }
        header("MONTANT")
        row(listOf("25%", "50%", "75%", "100%"), "lfoa") { i ->
            lfoAmountIdxArr[lfoSel] = i
            pushLfo(lfoSel)
        }
        header("CIBLE")
        fun lfoTargetRow(labels: List<String>, targets: List<Int>) {
            val r = LinearLayout(this)
            for (t in labels.withIndex()) {
                val b = makeButton(t.value, silk)
                b.tag = "lfot" to targets[t.index]
                b.setOnClickListener {
                    lfoTargets[lfoSel] = targets[t.index]
                    pushLfo(lfoSel)
                }
                arpButtons.add(b)
                r.addView(b, wrapParams((4 * d).toInt()))
            }
            list.addView(r)
        }
        lfoTargetRow(listOf("RIEN", "CUT", "RES", "REV"), listOf(-1, 0, 1, 2))
        lfoTargetRow(listOf("BIT", "VIT", "L-V", "L-P", "DRI"), listOf(3, 4, 5, 6, 7))

        val lfoRow = LinearLayout(this)
        val bLfoOn = makeButton("ACTIF", silk)
        bLfoOn.tag = "lfoon" to 0
        bLfoOn.setOnClickListener {
            lfoOn[lfoSel] = !lfoOn[lfoSel]
            pushLfo(lfoSel)
        }
        val bFlat = makeButton("PLAT", silk)
        bFlat.setOnClickListener {
            lfoShapes[lfoSel] = FloatArray(64) { 0.5f }
            lfoDraw?.setShape(lfoShapes[lfoSel])
            pushLfo(lfoSel)
        }
        lfoRow.addView(bLfoOn, wrapParams((4 * d).toInt()))
        lfoRow.addView(bFlat, wrapParams((4 * d).toInt()))
        list.addView(lfoRow)

        panel.addView(listScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER
        )
        panelParams.setMargins((10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt())
        arpOverlay.addView(panel, panelParams)
        val aClose = Button(this)
        aClose.text = "\u00d7"
        aClose.textSize = 15f
        aClose.setTextColor(silk)
        aClose.isAllCaps = false
        aClose.minWidth = 0
        aClose.minimumWidth = 0
        aClose.setPadding(0, 0, 0, 0)
        aClose.setBackgroundColor(Color.argb(160, 20, 22, 28))
        aClose.setOnClickListener {
            toolPage = 0
            showToolPage()
        }
        val acp = FrameLayout.LayoutParams((34 * d).toInt(), (34 * d).toInt(), Gravity.TOP or Gravity.END)
        acp.setMargins(0, (10 * d).toInt(), (10 * d).toInt(), 0)
        arpOverlay.addView(aClose, acp)
    }

    private val globalParamNames = listOf("CUT", "RES", "REV", "BIT", "VIT", "L-V", "L-P", "DRI")

    private val sampleParamNames = listOf("PITCH", "ATTAQUE", "DECAY", "TONE", "D\u00c9BUT", "FIN")

    private val fxNameBySlot: Map<Int, String> by lazy {
        val m = HashMap<Int, String>()
        var gi = 0
        for ((_, effects) in fxSections) {
            for (fx in effects) {
                val code = fxCodes.getOrNull(gi) ?: ""
                gi++
                if (code.length < 2) continue
                val num = code.substring(1).toIntOrNull() ?: -1
                when (code[0]) {
                    'X' -> if (num in fxActive.indices) m[fxSlot(num)] = fx
                    'G' -> if (num >= 0) m[genSlot(num)] = fx
                }
            }
        }
        m
    }

    private fun toggleDrumFxSimul(otherDownAt: Long): Boolean {
        val now = System.currentTimeMillis()
        if (now - drumFxSimulAt < 600) {
            drumFxSimulAt = 0L
            return true
        }
        if (now - otherDownAt < 400) {
            drumFxSimulAt = now
            drumFxForce = !drumFxForce
            engine.nativeSetDrumFxOn(drumFxForce)
            bubble("EFFETS BO\u00ceTE \u00c0 RYTHME " + (if (drumFxForce) "ENGAG\u00c9S" else "D\u00c9SENGAG\u00c9S (R\u00c9GLAGES KIT)"))
            return true
        }
        return false
    }

    private fun targetName(tg: Int): String {
        if (tg < 0) return "RIEN"
        if (tg >= 92) return drumTargetNames[(tg - 92).coerceIn(0, 4)]
        if (tg < 8) return globalParamNames[tg.coerceIn(0, 7)]
        val slot = tg - 8
        if (slot in 63..68) return "SAMPLE \u00b7 " + sampleParamNames[slot - 63]
        fxNameBySlot[slot]?.let { return it }
        return "SLOT $slot"
    }

    private fun pushLfo(idx: Int) {
        engine.nativeSetCustomLfo(
            idx, lfoShapes[idx], lfoOn[idx], lfoRateIdxArr[idx],
            floatArrayOf(0.25f, 0.5f, 0.75f, 1f)[lfoDepthIdxArr[idx]],
            floatArrayOf(0.25f, 0.5f, 0.75f, 1f)[lfoAmountIdxArr[idx]],
            lfoTargets[idx], lfoWaves[idx]
        )
        refreshArpHighlight()
    }

    private fun pushArp() {
        engine.nativeSetArp(arpOn, arpMode, arpRateIdx, arpOctIdx + 1, arpGateIdx, arpOctJump)
        engine.nativeSetArpSlices(intArrayOf(2, 4, 8, 12, 16)[arpSlicesIdx])
        engine.nativeSetArpTarget(arpTargetTg, floatArrayOf(0.25f, 0.5f, 0.75f, 1f)[arpAmountIdx])
        refreshArpHighlight()
    }

    private fun pushSynth() {
        engine.nativeSetSynth(
            synWaveIdx - 1,
            floatArrayOf(0f, 0.05f, 0.1f, 0.2f, 0.5f)[synAtkIdx],
            floatArrayOf(0.5f, 1f, 2f, 4f)[synDecIdx],
            synOctIdx - 2,
            floatArrayOf(-12f, -5f, 0f, 5f, 12f)[synPitIdx],
            floatArrayOf(-0.5f, 0f, 0.5f)[synTunIdx]
        )
        refreshArpHighlight()
    }

    private fun refreshArpHighlight() {
        for (b in arpButtons) {
            val tag = b.tag as? Pair<*, *> ?: continue
            val kind = tag.first as? String ?: continue
            val idx = (tag.second as? Int) ?: continue
            val lit = when (kind) {
                "on" -> arpOn
                "mode" -> arpOn && idx == arpMode
                "rate" -> idx == arpRateIdx
                "oct" -> idx == arpOctIdx
                "gate" -> idx == arpGateIdx
                "slices" -> idx == arpSlicesIdx
                "jump" -> arpOctJump
                "swave" -> idx == synWaveIdx
                "satk" -> idx == synAtkIdx
                "sdec" -> idx == synDecIdx
                "soct" -> idx == synOctIdx
                "spit" -> idx == synPitIdx
                "stun" -> idx == synTunIdx
                "ltab" -> idx == lfoSel
                "lfw" -> idx == lfoWaves[lfoSel]
                "lfov" -> idx == lfoRateIdxArr[lfoSel]
                "lfod" -> idx == lfoDepthIdxArr[lfoSel]
                "lfoa" -> idx == lfoAmountIdxArr[lfoSel]
                "ldest" -> {
                    val tg = if (idx < 2) lfoTargets[idx] else arpTargetTg
                    b.text = (if (idx == 0) "A \u2192 " else if (idx == 1) "B \u2192 " else "ARP \u2192 ") + targetName(tg)
                    tg >= 0
                }
                "aramt" -> idx == arpAmountIdx
                "lfot" -> idx == lfoTargets[lfoSel]
                "lfoon" -> lfoOn[lfoSel]
                "lfooff" -> !lfoOn[lfoSel]
                else -> false
            }
            b.setTextColor(if (lit) amber else silk)
        }
    }

    private fun openArpPanel() {
        refreshArpHighlight()
        arpOverlay.visibility = View.VISIBLE
    }

    private fun closeArpPanel() {
        arpOverlay.visibility = View.GONE
        lfoBrowserBox?.visibility = View.GONE
    }

    private fun showLfoBrowser() {
        val box = lfoBrowserBox ?: return
        val dd = resources.displayMetrics.density
        box.removeAllViews()
        box.visibility = View.VISIBLE
        if (lfoBrowserSec < 0) {
            val r0 = LinearLayout(this)
            val bNone = makeButton("RIEN (D\u00c9PATCHER)", silk)
            bNone.setOnClickListener {
                if (lfoBrowserFor < 2) {
                    lfoTargets[lfoBrowserFor] = -1
                    pushLfo(lfoBrowserFor)
                } else {
                    arpTargetTg = -1
                    pushArp()
                }
                box.visibility = View.GONE
                refreshArpHighlight()
            }
            r0.addView(bNone)
            box.addView(r0)
            var secIdx = 0
            var rowL: LinearLayout? = null
            for ((sec, _) in fxSections) {
                if (rowL == null || rowL.childCount == 3) {
                    rowL = LinearLayout(this)
                    box.addView(rowL)
                }
                val b = makeButton(sec, fxCatCols[secIdx % fxCatCols.size])
                val si = secIdx
                b.setOnClickListener {
                    lfoBrowserSec = si
                    showLfoBrowser()
                }
                rowL.addView(b, wrapParams((4 * dd).toInt()))
                secIdx++
            }
            if (rowL == null || rowL.childCount == 3) {
                rowL = LinearLayout(this)
                box.addView(rowL)
            }
            val bS = makeButton("SAMPLE", pink)
            bS.setOnClickListener {
                lfoBrowserSec = fxSections.size
                showLfoBrowser()
            }
            rowL.addView(bS, wrapParams((4 * dd).toInt()))
            if (rowL == null || rowL.childCount == 3) {
                rowL = LinearLayout(this)
                box.addView(rowL)
            }
            val bD = makeButton("RYTHME", amber)
            bD.setOnClickListener {
                lfoBrowserSec = fxSections.size + 1
                showLfoBrowser()
            }
            rowL.addView(bD, wrapParams((4 * dd).toInt()))
        } else {
            val back = makeButton("\u2190 RETOUR", silk)
            back.setOnClickListener {
                lfoBrowserSec = -1
                showLfoBrowser()
            }
            box.addView(back)
            val entries = ArrayList<Pair<String, Int>>()
            if (lfoBrowserSec == fxSections.size + 1) {
                for (k in 0 until 5) entries.add(drumTargetNames[k] to 92 + k)
            } else if (lfoBrowserSec == fxSections.size) {
                for (k in 0 until 6) entries.add("SAMPLE \u00b7 " + sampleParamNames[k] to 71 + k)
            } else {
                var gi = 0
                for ((si, entry) in fxSections.withIndex()) {
                    val effects = entry.second
                    for (fx in effects) {
                        val code = fxCodes.getOrNull(gi) ?: ""
                        gi++
                        if (si != lfoBrowserSec || code.length < 2) continue
                        val num = code.substring(1).toIntOrNull() ?: -1
                        val slot = when (code[0]) {
                            'X' -> fxSlot(num)
                            'G' -> genSlot(num)
                            'P' -> -2
                            else -> -1
                        }
                        if (slot == -2) {
                            if (num in 0..7) entries.add(fx to num)
                            continue
                        }
                        if (slot < 0) continue
                        val defs = fxParamDefs[slot] ?: continue
                        for (k in defs.indices) entries.add(fx + " \u00b7 " + defs[k] to 8 + slot * 8 + k)
                    }
                }
            }
            var rowL: LinearLayout? = null
            for ((label, tg) in entries) {
                if (rowL == null || rowL.childCount == 2) {
                    rowL = LinearLayout(this)
                    box.addView(rowL)
                }
                val b = makeButton(label, green)
                b.setOnClickListener {
                    if (lfoBrowserFor < 2) {
                        lfoTargets[lfoBrowserFor] = tg
                        pushLfo(lfoBrowserFor)
                    } else {
                        arpTargetTg = tg
                        pushArp()
                    }
                    box.visibility = View.GONE
                    refreshArpHighlight()
                }
                rowL.addView(b, wrapParams((4 * dd).toInt()))
            }
        }
    }

    private fun seedFactoryPresets() {
        val combos = listOf(
            listOf(1, 27),
            listOf(3, 4, 6),
            listOf(13, 14, 18),
            listOf(8, 15, 29),
            listOf(22, 38, 78),
            listOf(40, 41, 42)
        )
        for (i in 0 until 6) {
            if (userPresets[i] != null) continue
            val on = BooleanArray(84)
            for (f in combos[i]) on[f] = true
            val params = Array(84) { FloatArray(8) }
            if (i == 0) params[27] = floatArrayOf(0.4f, 0.5f, 0.5f, 0.4f, 0f, 0f, 0f, 0f)
            userPresets[i] = UserPreset(
                filter = 0, scale = activeScale, root = activeRoot,
                arpOn = false, arpMode = 0, arpRate = 1, arpOct = 0, arpGate = 1,
                arpOctJump = false, arpSlices = 2, assignX = 0, assignY = 1,
                fxOn = on, fxParams = params,
                fxAssign = Array(84) { IntArray(8) },
                lfoTg = intArrayOf(-1, -1), gen = -1
            )
            pad.userPresets[i] = true
            factorySlots[i] = true
        }
        val synthCombos = listOf(
            Triple(15, listOf(19, 29), false),
            Triple(18, listOf(27, 77), false),
            Triple(29, listOf(6, 1), true)
        )
        for (j in 0 until 3) {
            val i = 6 + j
            if (userPresets[i] != null) continue
            val (g, fxs, arp) = synthCombos[j]
            val on = BooleanArray(84)
            for (f in fxs) on[f] = true
            val params = Array(84) { FloatArray(8) }
            if (g == 18) params[27] = floatArrayOf(0.4f, 0.5f, 0.5f, 0.4f, 0f, 0f, 0f, 0f)
            userPresets[i] = UserPreset(
                filter = 0, scale = activeScale, root = activeRoot,
                arpOn = arp, arpMode = 0, arpRate = 2, arpOct = if (arp) 1 else 0, arpGate = 1,
                arpOctJump = false, arpSlices = 2, assignX = 0, assignY = 1,
                fxOn = on, fxParams = params,
                fxAssign = Array(84) { IntArray(8) },
                lfoTg = intArrayOf(-1, -1), gen = g
            )
            pad.userPresets[i] = true
            factorySlots[i] = true
        }
        pad.invalidate()
    }

    private fun saveUserPreset(slot: Int) {
        if (slot < 0 || slot > 8) return
        userPresets[slot] = UserPreset(
            filter = activeFilter,
            scale = activeScale,
            root = activeRoot,
            arpOn = arpOn,
            arpMode = arpMode,
            arpRate = arpRateIdx,
            arpOct = arpOctIdx,
            arpGate = arpGateIdx,
            arpOctJump = arpOctJump,
            arpSlices = arpSlicesIdx,
            assignX = assignXIdx,
            assignY = assignYIdx,
            fxOn = fxActive.copyOf(),
            fxParams = Array(84) { fxParamVals[it].copyOf() },
            fxAssign = Array(84) { fxParamAssign[it].copyOf() },
            lfoTg = lfoTargets.copyOf(),
            gen = genIdx
        )
        pad.userPresets[slot] = true
        factorySlots[slot] = false
        pad.invalidate()
    }

    private fun applyUserPreset(p: UserPreset) {
        activeFilter = p.filter
        engine.nativeSetFilterType(p.filter)
        activeScale = p.scale
        activeRoot = p.root
        engine.nativeSetScale(p.scale)
        engine.nativeSetRoot(p.root)
        arpOn = p.arpOn
        arpMode = p.arpMode
        arpRateIdx = p.arpRate
        arpOctIdx = p.arpOct
        arpGateIdx = p.arpGate
        arpOctJump = p.arpOctJump
        arpSlicesIdx = p.arpSlices
        assignXIdx = p.assignX
        assignYIdx = p.assignY
        engine.nativeSetAssign(0, p.assignX)
        engine.nativeSetAssign(1, p.assignY)
        for (f in 0 until 84) {
            if (f in 63..68) continue
            if (f < fxActive.size && f < p.fxOn.size && fxActive[f] != p.fxOn[f]) {
                fxActive[f] = p.fxOn[f]
                engine.nativeSetFx(f, fxActive[f])
            }
            for (k in 0 until 8) {
                if (f < p.fxParams.size) fxParamVals[f][k] = p.fxParams[f][k]
                engine.nativeSetFxParam(f, k, fxParamVals[f][k])
            }
        }
        for (f in 0 until 84) for (k in 0 until 8)
            if (f < p.fxAssign.size) fxParamAssign[f][k] = p.fxAssign[f][k]
        if (p.gen >= 0) {
            genIdx = p.gen
            engine.nativeSetGen(p.gen)
        }
        var xT = -1
        val lT = intArrayOf(-1, -1)
        for (f in 0 until 84) for (k in 0 until 8) {
            if (f in 63..68) continue
            if (f >= p.fxAssign.size) continue
            when (p.fxAssign[f][k]) {
                1 -> if (xT < 0) xT = 8 + f * 8 + k
                2 -> if (lT[0] < 0) lT[0] = 8 + f * 8 + k
                3 -> if (lT[1] < 0) lT[1] = 8 + f * 8 + k
            }
        }
        if (xT >= 0) engine.nativeSetAssign(0, xT)
        for (l in 0..1) {
            if (lT[l] >= 0) {
                lfoTargets[l] = lT[l]
                pushLfo(l)
            }
        }
        pad.arpOn = arpOn
        pushArp()
        refreshScaleHighlight()
        refreshAssignLabels()
        refreshFxHighlight()
        paramView?.refresh()
    }

    private fun versionLabel(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    private fun placeholderWaveform(n: Int = 256): FloatArray {
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val beat = (t * 8) % 1f
            val env = Math.pow((1.0 - beat).toDouble(), 3.0).toFloat()
            out[i] = env * sin(t * 40f * Math.PI.toFloat()) * 0.8f
        }
        return out
    }

    private fun refreshBlink() {
        padGrid.blinkOn = blinkOn
        padGrid.recLive = recActive
        updateXyHint()
        btnRec.background = if (recActive && !blinkOn) axisSolidBg(Color.rgb(110, 28, 22), false) else axisSolidBg(red, false)
        btnRec.setTextColor(if ((recHeld || recArmed) && !blinkOn) Color.rgb(110, 28, 22) else Color.rgb(255, 240, 235))
        drumSeqBtn?.setTextColor(if (drumMuted && blinkOn) Color.WHITE else pink)
        arpBtn?.setTextColor(if (toolPage != 0 || toolsMenu.visibility == View.VISIBLE) Color.rgb(80, 170, 255) else if (arpOn) amber else silk)
        if (shiftLocked) btnShift.alpha = if (blinkOn) 1f else 0.55f
        if (toolPage == 2 || toolPage == 3) {
            for (i in 0 until 9) {
                val b = presetBtns.getOrNull(i) ?: continue
                val g = userPresets[i]?.gen ?: -1
                if (g >= 0) {
                    b.alpha = if (blinkOn) 1f else 0.5f
                    b.setTextColor(if (keysGens.contains(g)) Color.rgb(80, 170, 255) else silk)
                } else {
                    b.alpha = 0.35f
                }
            }
            val glow = IntArray(padGrid.padCount)
            for (i in 0 until min(8, padGrid.padCount)) {
                if (engine.nativeBankHasLoop(i)) {
                    glow[i] = if (keysBanks.contains(i)) 2 else 1
                }
            }
            padGrid.toolGlow = glow
        } else {
            padGrid.toolGlow = IntArray(0)
            for (i in 0 until 9) presetBtns.getOrNull(i)?.alpha = 1f
        }
    }

    private val recEndRunnable = Runnable {
        if (recActive) {
            if (recMotion) finalizeMotionRec() else {
                recActive = false
                val b = recArmPad - 1
                if (b in 0 until 8) pushSampleLog("B${b + 1} \u00b7 ${bankDisplayName(b)}")
            }
            recMotion = false
            padGrid.recPad = -1
            refreshBlink()
        }
    }

    private fun startLoopRecording(bank: Int) {
        val wet = loopWetA || loopWetB || recResampleFx
        recResampleFx = false
        engine.nativeRecordBank(bank - 1, loopLenSteps, wet)
        resetSteps(bank - 1)
        recActive = true
        recMotion = false
        recArmPad = bank
        recStartMs = System.currentTimeMillis()
        bankRecAt[bank - 1] = recStartMs
        recLenMs = (loopLenSteps * 60000f / bpmLcd.bpm.coerceAtLeast(40f)).toLong()
        padGrid.recPad = bank - 1
        recEndHandler.removeCallbacksAndMessages(null)
        recEndHandler.postDelayed(recEndRunnable, recLenMs + 200)
        refreshBlink()
    }

    private fun recTap() {
        if (sosHint("REC : ARMER UN ENREGISTREMENT SUR UN PAD (LANCER LA MANIP DE REC).")) return
        if (lenEntry) {
            lenEntry = false
            lenFirst = 0
            lenHandler.removeCallbacksAndMessages(null)
        }
        when {
            recActive -> {
                if (recMotion) finalizeMotionRec() else stopSampleRec()
                recArmed = false
                recSnapSide = -1
                recArmPad = -1
                padGrid.recArmed = false
            }
            recSnapSide >= 0 && recArmPad > 0 -> {
                captureSnap(recSnapSide, recArmPad - 1)
                endSnapHold()
                recArmed = false
                recSnapSide = -1
                recArmPad = -1
                padGrid.recArmed = false
                padGrid.recPad = -1
            }
            recArmPad > 0 -> {
                recArmed = false
                padGrid.recArmed = false
                if (padGrid.padMode == 1) startMotionRec() else startLoopRecording(recArmPad)
            }
            else -> {
                recArmed = !recArmed
                recArmPad = -1
                recSnapSide = -1
                endSnapHold()
                motionSrcA = false
                motionSrcB = false
                recResampleFx = recArmed && shiftActive()
                padGrid.recArmed = recArmed
                padGrid.recPad = -1
                bubble(if (recArmed) "REC : " + when {
                    recResampleFx -> "RESAMPLING FX \u2014 PAD + LONGUEUR"
                    padGrid.padMode == 1 -> "F/Z + PAD"
                    else -> "PAD (F/Z = WET)"
                } else "REC OFF")
            }
        }
        refreshBlink()
    }

    private fun armRecDest(n: Int) {
        if (recSnapSide >= 0) {
            val n2 = min(padGrid.padCount, 14)
            val zone = if (recSnapSide == 0) 0 until n2 / 2 else n2 / 2 until n2
            if (n - 1 in zone) {
                recArmPad = n
                padGrid.recPad = n - 1
                beginSnapHold()
                bubble("SNAPSHOT PAD $n \u2014 REC = CAPTURER")
            } else {
                bubble("C\u00d4T\u00c9 " + (if (recSnapSide == 0) "ZU" else "LU") + " UNIQUEMENT")
            }
        } else {
            recArmPad = n
            padGrid.recPad = n - 1
            val src = when {
                padGrid.padMode == 1 -> "MOTION"
                loopWetA || loopWetB -> "WET"
                else -> "DRY"
            }
            bubble("PAD $n ARM\u00c9 ($src) \u2014 REC = LANCER")
        }
        refreshBlink()
    }

    private var quantRec = true

    data class ParamLink(
        val src: Int, val dst: Int, val amount: Float, val prob: Float,
        val chop: Int, val delay: Float, val soft: Float
    )
    private val bankLinks = Array(9) { ArrayList<ParamLink>() }
    private var activeGroup = 0
    private var envOn = false
    private var envTarget = -1
    private var envAmt = 0.5f
    private var envGain = 4f
    private var envTeach = false
    private var logOn = false
    private var logSrcA = 0
    private var logSrcB = 1
    private var logGate = 0
    private var logTarget = -1
    private var logDepth = 1f
    private var logTeach = false
    private var lastAxX = 0.5f
    private var lastAxY = 0.5f
    private lateinit var patchBay: PatchBayView

    private fun pushLinks() {
        for (idx in 0 until 16) {
            engine.nativeSetLink(idx, -1, -1, 0f, 1f, 0, 0f, 0f, false)
        }
        val ls = bankLinks[activeGroup]
        for ((idx, lk) in ls.withIndex()) {
            if (idx >= 16) break
            engine.nativeSetLink(
                idx, lk.src, lk.dst, lk.amount, lk.prob, lk.chop, lk.delay, lk.soft, true
            )
        }
    }

    private fun groupParamTargets(): List<Int> {
        val out = ArrayList<Int>()
        val p = userPresets[activeGroup] ?: return out
        for (f in 0 until min(84, p.fxOn.size)) {
            if (!p.fxOn[f]) continue
            val slot = fxSlot(f)
            val defs = fxParamDefs[slot] ?: continue
            for (k in defs.indices) out.add(8 + slot * 8 + k)
        }
        return out
    }

    private val chopNames = listOf("OFF", "1/4", "1/8", "1/16", "1/32", "1/8T")

    private fun openCommDialog(focusSlot: Int = -1, focusK: Int = -1) {
        val dd = resources.displayMetrics.density
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val pad = (10 * dd).toInt()
        box.setPadding(pad, pad, pad, pad)
        scroll.addView(box)

        val focusT = if (focusSlot >= 0) 8 + focusSlot * 8 + focusK else -1
        val ls = bankLinks[activeGroup]

        fun rebuild() {
            box.removeAllViews()
            val title = TextView(this)
            title.text = "COMMUNICATION \u00b7 GROUPE ${activeGroup + 1}"
            title.setTextColor(amber)
            title.textSize = 13f
            title.typeface = markerFont(this)
            box.addView(title)
            val hint = TextView(this)
            hint.text = "CONNEXIONS ENTRE TIRETTES DU GROUPE \u00b7 MAINTENIR UNE TIRETTE POUR VOIR"
            hint.setTextColor(silk)
            hint.textSize = 9f
            box.addView(hint)

            var shown = 0
            for ((li, lk) in ls.withIndex()) {
                if (focusT >= 0 && lk.src != focusT && lk.dst != focusT) continue
                shown++
                val row = LinearLayout(this)
                row.orientation = LinearLayout.VERTICAL
                val bg = GradientDrawable()
                bg.setColor(Color.rgb(16, 18, 24))
                bg.setStroke((1 * dd).toInt(), silk)
                bg.cornerRadius = 6 * dd
                row.background = bg
                row.setPadding(pad, pad, pad, pad)

                val name = TextView(this)
                name.text = linkParamName(lk.src) + "  \u2192  " + linkParamName(lk.dst)
                name.setTextColor(Color.rgb(236, 224, 196))
                name.textSize = 11f
                name.typeface = markerFont(this)
                row.addView(name)

                fun slider(label: String, init: Float, onChange: (Float) -> Unit): SeekBar {
                    val lab = TextView(this)
                    lab.text = label
                    lab.setTextColor(silk)
                    lab.textSize = 9f
                    row.addView(lab)
                    val sb = SeekBar(this)
                    sb.max = 100
                    sb.progress = (init * 100f).toInt().coerceIn(0, 100)
                    sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                            if (fromUser) onChange(v / 100f)
                        }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })
                    row.addView(sb, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (26 * dd).toInt()))
                    return sb
                }

                slider("INTENSIT\u00c9", lk.amount) { v ->
                    ls[li] = lk.copy(amount = v)
                    pushLinks()
                }
                slider("PROBABILIT\u00c9", lk.prob) { v ->
                    ls[li] = lk.copy(prob = v)
                    pushLinks()
                }
                slider("RETARD", lk.delay) { v ->
                    ls[li] = lk.copy(delay = v)
                    pushLinks()
                }
                slider("DOUCEUR", lk.soft) { v ->
                    ls[li] = lk.copy(soft = v)
                    pushLinks()
                }

                val btnRow = LinearLayout(this)
                val chopB = makeButton("SACCADE : " + chopNames[lk.chop.coerceIn(0, 5)], silk)
                chopB.setOnClickListener {
                    val nc = (lk.chop + 1) % 6
                    ls[li] = lk.copy(chop = nc)
                    chopB.text = "SACCADE : " + chopNames[nc]
                    pushLinks()
                    rebuild()
                }
                btnRow.addView(chopB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                val delB = makeButton("SUPPR", red)
                delB.setOnClickListener {
                    ls.removeAt(li)
                    pushLinks()
                    rebuild()
                }
                btnRow.addView(delB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(btnRow)
                box.addView(row)
            }
            if (shown == 0 && focusT < 0) {
                val empty = TextView(this)
                empty.text = "AUCUNE CONNEXION DANS CE GROUPE"
                empty.setTextColor(silk)
                empty.textSize = 10f
                box.addView(empty)
            }

            if (focusT < 0) {
                val addTitle = TextView(this)
                addTitle.text = "NOUVELLE CONNEXION"
                addTitle.setTextColor(green)
                addTitle.textSize = 11f
                addTitle.typeface = markerFont(this)
                box.addView(addTitle)
                val targets = groupParamTargets()
                if (targets.size < 2) {
                    val emp = TextView(this)
                    emp.text = "GROUPE VIDE \u2014 SAUVEGARDER AU MOINS UN EFFET DANS LE SLOT ${activeGroup + 1}"
                    emp.setTextColor(silk)
                    emp.textSize = 9f
                    box.addView(emp)
                } else {
                    var srcIdx = 0
                    var dstIdx = 1
                    val srcB = makeButton(linkParamName(targets[0]), turquoise)
                    srcB.setOnClickListener {
                        srcIdx = (srcIdx + 1) % targets.size
                        srcB.text = linkParamName(targets[srcIdx])
                    }
                    val dstB = makeButton(linkParamName(targets[1]), violet)
                    dstB.setOnClickListener {
                        dstIdx = (dstIdx + 1) % targets.size
                        dstB.text = linkParamName(targets[dstIdx])
                    }
                    val selRow = LinearLayout(this)
                    selRow.addView(srcB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    selRow.addView(dstB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    box.addView(selRow)
                    val addB = makeButton("AJOUTER", green)
                    addB.setOnClickListener {
                        if (ls.size < 16 && targets[srcIdx] != targets[dstIdx]) {
                            ls.add(ParamLink(targets[srcIdx], targets[dstIdx], 0.5f, 1f, 0, 0f, 0f))
                            pushLinks()
                            rebuild()
                        }
                    }
                    box.addView(addB)
                }
            }
        }
        rebuild()

        android.app.AlertDialog.Builder(this)
            .setView(scroll)
            .setNegativeButton("FERMER") { d, _ -> d.dismiss() }
            .show()
    }

    private fun linkParamName(t: Int): String {
        if (t < 8) return "?"
        val slot = t - 8
        val k = slot % 8
        val s = slot / 8
        val fxName = fxNameBySlot[s] ?: "S"
        val defs = fxParamDefs[s]
        val paramName = defs?.getOrNull(k) ?: "P$k"
        return "$fxName \u00b7 $paramName"
    }

    private fun stopSampleRec() {
        engine.nativeStopRec()
        recActive = false
        recMotion = false
        padGrid.recPad = -1
        recEndHandler.removeCallbacksAndMessages(null)
        val b = recArmPad - 1
        if (quantRec && b in 0 until 8) {
            val msPerStep = 60000f / bpmLcd.bpm.coerceAtLeast(40f)
            val steps = (((System.currentTimeMillis() - recStartMs) / msPerStep)
                .toInt() + 1).coerceIn(1, 19)
            engine.nativeResample(b, steps)
            bubble("REC STOP \u00b7 QUANT ${steps} T")
        } else {
            bubble("REC STOP")
        }
        if (b in 0 until 8) pushSampleLog("B${b + 1} \u00b7 ${bankDisplayName(b)}")
        refreshBlink()
    }

    private fun recStop() {
        if (recMotion) finalizeMotionRec() else stopSampleRec()
        recArmed = false
        recSnapSide = -1
        endSnapHold()
        recArmPad = -1
        padGrid.recArmed = false
    }

    private fun startMotionRec() {
        if (!motionSrcA && !motionSrcB) {
            motionSrcA = true
            motionSrcB = true
        }
        motA.clear()
        motB.clear()
        recActive = true
        recMotion = true
        recStartMs = System.currentTimeMillis()
        recLenMs = (loopLenSteps * 60000f / bpmLcd.bpm.coerceAtLeast(40f)).toLong()
        padGrid.recPad = recArmPad - 1
        recEndHandler.removeCallbacksAndMessages(null)
        recEndHandler.postDelayed(recEndRunnable, recLenMs + 200)
        motionHandler.postDelayed(motionCaptureTick, 40)
        refreshBlink()
    }

    private val motionCaptureTick = object : Runnable {
        override fun run() {
            if (!recActive || !recMotion) return
            if (motionSrcA) {
                motA.add(pad.ax)
                motA.add(pad.ay)
            }
            if (motionSrcB) {
                motB.add(pad.bx)
                motB.add(pad.by)
            }
            motionHandler.postDelayed(this, 40)
        }
    }

    private fun finalizeMotionRec() {
        val idx = recArmPad - 1
        if (idx in snapPads.indices) {
            val a = if (motionSrcA && motA.size >= 4) motA.toFloatArray() else null
            val b = if (motionSrcB && motB.size >= 4) motB.toFloatArray() else null
            snapPads[idx] = liveSnap().copy(motA = a, motB = b, motLen = loopLenSteps)
            val n = min(padGrid.padCount, 14)
            if (idx < n / 2) activeZu = idx else activeLu = idx
            refreshSnapGrid()
        }
        recActive = false
        recMotion = false
        motionSrcA = false
        motionSrcB = false
        motionHandler.removeCallbacks(motionCaptureTick)
        bubble("MOTION ENREGISTR\u00c9E")
        refreshBlink()
    }

    private fun startMotionPlayback(idx: Int) {
        val s = snapPads[idx] ?: return
        motionPlaying = idx
        motionPhase = 0f
        motionAmp = 1f
        playAx = s.ax
        playAy = s.ay
        playBx = s.bx
        playBy = s.by
        motionHandler.removeCallbacks(motionPlayTick)
        motionHandler.postDelayed(motionPlayTick, 40)
    }

    private fun stopMotionPlayback() {
        motionPlaying = -1
        motionHandler.removeCallbacks(motionPlayTick)
    }

    private val motionPlayTick = object : Runnable {
        override fun run() {
            if (motionPlaying < 0) return
            val s = snapPads[motionPlaying]
            if (s == null || ((s.motA == null || s.motA!!.size < 4) && (s.motB == null || s.motB!!.size < 4))) {
                motionPlaying = -1
                return
            }
            val durMs = s.motLen.coerceAtLeast(1) * 60000f / bpmLcd.bpm.coerceAtLeast(40f)
            val adv = 40f / durMs
            motionPhase += adv
            val mMode = s.motMode.coerceIn(0, 2)
            val mAmt = s.motAmt.coerceIn(0f, 1f)
            if (mMode == 1 && Math.random() < mAmt * 0.2) {
                motionPhase = Math.random().toFloat()
            }
            if (mMode == 2) {
                motionAmp += ((Math.random() - 0.5) * 0.02 * (0.2 + mAmt)).toFloat()
                motionAmp = motionAmp.coerceIn(1f - mAmt, 1f + mAmt)
            } else {
                motionAmp += (1f - motionAmp) * 0.1f
            }
            val a = s.motA
            if (a != null && a.size >= 4) {
                val pts = a.size / 2
                val ph = motionPhase % 1f * pts
                val i = ph.toInt().coerceAtMost(pts - 1)
                val j = (i + 1) % pts
                val f = ph - i
                playAx = (0.5f + ((a[i * 2] + (a[j * 2] - a[i * 2]) * f).coerceIn(0f, 1f) - 0.5f) * motionAmp).coerceIn(0f, 1f)
                playAy = (0.5f + ((a[i * 2 + 1] + (a[j * 2 + 1] - a[i * 2 + 1]) * f).coerceIn(0f, 1f) - 0.5f) * motionAmp).coerceIn(0f, 1f)
                engine.nativeSetAx(playAx)
                engine.nativeSetAy(playAy)
            }
            val b = s.motB
            if (b != null && b.size >= 4) {
                val pts = b.size / 2
                val ph = motionPhase % 1f * pts
                val i = ph.toInt().coerceAtMost(pts - 1)
                val j = (i + 1) % pts
                val f = ph - i
                playBx = (0.5f + ((b[i * 2] + (b[j * 2] - b[i * 2]) * f).coerceIn(0f, 1f) - 0.5f) * motionAmp).coerceIn(0f, 1f)
                playBy = (0.5f + ((b[i * 2 + 1] + (b[j * 2 + 1] - b[i * 2 + 1]) * f).coerceIn(0f, 1f) - 0.5f) * motionAmp).coerceIn(0f, 1f)
                engine.nativeSetBx(playBx)
                engine.nativeSetBy(playBy)
            }
            pad.setCursor(playAx, playAy, playBx, playBy)
            motionHandler.postDelayed(this, 40)
        }
    }

    private fun bankLenMs(b: Int): Long {
        val steps = if (bankSteps[b] > 0) bankSteps[b] else loopLenSteps
        return (steps * 60000f / bpmLcd.bpm.coerceAtLeast(40f)).toLong()
    }

    private val seqTick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (logOn && logTarget >= 8) {
                val arr = FloatArray(1)
                engine.nativeGetInLevels(arr)
                val a = when (logSrcA) { 0 -> lastAxX; 1 -> lastAxY; else -> (arr[0] * 4f).coerceIn(0f, 1f) }
                val b = when (logSrcB) { 0 -> lastAxX; 1 -> lastAxY; else -> (arr[0] * 4f).coerceIn(0f, 1f) }
                val out = when (logGate) {
                    0 -> if (a > 0.5f && b > 0.5f) 1f else 0f
                    1 -> if (a > 0.5f || b > 0.5f) 1f else 0f
                    2 -> if ((a > 0.5f) xor (b > 0.5f)) 1f else 0f
                    else -> if (a <= 0.5f) 1f else 0f
                }
                val t = logTarget - 8
                engine.nativeSetFxParam(t / 8, t % 8, out * logDepth)
            }
            for (i in 0 until 8) {
                if (!midiLoopOn[i] || midiLoopLen[i] <= 0) continue
                val lt = (now - midiLoopStart[i]) % midiLoopLen[i]
                val step = ((lt * 16f) / midiLoopLen[i].toFloat()).toInt()
                if (step != midiLoopIdx[i]) {
                    midiLoopIdx[i] = step
                    for (r in midiLoopKeys[i].indices) {
                        val v = midiLoopCells[i][r][step]
                        if (v >= 0f) midiOut(midiLoopKeys[i][r], v)
                    }
                }
                if (padGrid.padMode == 4 && i == midiSeqSel && this@MainActivity::midiSeqView.isInitialized) {
                    midiSeqView.playStep = step
                    midiSeqView.invalidate()
                }
            }
            if (drumSeqRunning && this@MainActivity::drumSeqView.isInitialized) {
                val stepMs = 15000f / bpmLcd.bpm.coerceAtLeast(40f)
                val elapsed = (now - drumSeqStartMs).toFloat()
                val stepTotal = (elapsed / stepMs).toInt()
                val frac = (elapsed - stepTotal * stepMs) / stepMs
                drumSeqView.playStep = stepTotal
                drumSeqView.invalidate()
                for (r in 0 until drumSeqView.rows) {
                    val rc = stepTotal % drumSeqView.rowLen[r]
                    if (rc != drumSeqRowCol[r]) {
                        drumSeqRowCol[r] = rc
                        for (c in 0 until drumSeqView.steps[r].size) {
                            if (drumSeqView.steps[r][c] &&
                                Math.round(c + drumSeqView.slides[r][c]) == rc &&
                                (drumSeqView.prob[r][c] >= 1f || Math.random() < drumSeqView.prob[r][c])
                            ) {
                                if (!drumMuted) {
                                    engine.nativeSetGenHitVel(drumSeqView.vel[r][c])
                                    engine.nativeSetGenHitPan(drumSeqView.pan[r][c])
                                    engine.nativeGenHit(drumSeqGens[r], r / 6f, 0.6f)
                                    if (drumGlitchOn[r] && drumGlitchAmt > 0f &&
                                        drumSeqView.glitch[r][c] && Math.random() < drumGlitchAmt
                                    ) {
                                        val stepMs = 150000f / bpmLcd.bpm.coerceAtLeast(40f)
                                        val rr = r
                                        val cc = c
                                        blinkHandler.postDelayed({
                                            if (!drumMuted) {
                                                engine.nativeSetGenHitVel(drumSeqView.vel[rr][cc])
                                                engine.nativeSetGenHitPan(drumSeqView.pan[rr][cc])
                                                engine.nativeGenHit(drumSeqGens[rr], rr / 6f, 0.6f)
                                            }
                                        }, (stepMs * 0.5f).toLong())
                                        if (Math.random() < drumGlitchAmt) {
                                            blinkHandler.postDelayed({
                                                if (!drumMuted) {
                                                    engine.nativeSetGenHitVel(drumSeqView.vel[rr][cc])
                                                    engine.nativeSetGenHitPan(drumSeqView.pan[rr][cc])
                                                    engine.nativeGenHit(drumSeqGens[rr], rr / 6f, 0.6f)
                                                }
                                            }, (stepMs * 0.75f).toLong())
                                        }
                                    }
                                }
                            }
                        }
                        if (drumMutOn[r] && drumMutAmt > 0f &&
                            drumSeqView.mut[r][rc] && Math.random() < drumMutAmt
                        ) {
                            drumSeqView.steps[r][rc] = !drumSeqView.steps[r][rc]
                            drumSeqView.invalidate()
                        }
                    }
                    if (drumSeqView.steps[r][rc]) {
                        val rn = drumSeqView.roll[r][rc]
                        for (k in 1..rn) {
                            val th = k.toFloat() / (rn + 1)
                            if (drumSeqLastFrac < th && frac >= th &&
                                (drumSeqView.prob[r][rc] >= 1f || Math.random() < drumSeqView.prob[r][rc])
                            ) {
                                if (!drumMuted) {
                                    engine.nativeSetGenHitVel(drumSeqView.vel[r][rc])
                                    engine.nativeSetGenHitPan(drumSeqView.pan[r][rc])
                                    engine.nativeGenHit(drumSeqGens[r], r / 6f, 0.6f)
                                }
                            }
                        }
                    }
                }
                drumSeqLastFrac = frac
            }
            for (b in 0 until 8) {
                if (!midiPlaying[b]) continue
                val loop = midiLoops[b]
                if (loop.isEmpty()) {
                    midiPlaying[b] = false
                    continue
                }
                val len = bankLenMs(b).coerceAtLeast(200).toFloat()
                val pos = ((now - midiStartMs[b]) % len.toLong()).toFloat()
                val last = midiLastPos[b]
                for (e in loop) {
                    val ep = e[0]
                    val hit = last < 0f || (ep > last && ep <= pos) || (pos < last && (ep > last || ep <= pos))
                    if (hit) {
                        when (e[1].toInt()) {
                            0 -> engine.nativeDrumHit(b, -1, 0, e[3])
                            1 -> engine.nativeDrumHit(b, e[2].toInt(), e[4].toInt(), 0f)
                            3 -> engine.nativeDrumHit(b, e[2].toInt(), 0, e[3])
                            else -> engine.nativeDrumHit(b, -1, e[4].toInt(), e[3])
                        }
                    }
                }
                midiLastPos[b] = pos
            }
            seqHandler.postDelayed(this, 20)
        }
    }

    private fun drumHit(n: Int) {
        val count = drumGrid.padCount
        val pitch = n - count / 2f + drumGrid.octShift * 12f
        if (drumGrid.drumMode == 3) engine.nativeSetKitMode(playerBank, true)
        when (drumGrid.drumMode) {
            0 -> engine.nativeDrumHit(playerBank, -1, 0, pitch)
            1 -> engine.nativeDrumHit(playerBank, n, count, 0f)
            3 -> engine.nativeDrumHit(playerBank, n, 0, pitch)
            else -> engine.nativeDrumHit(playerBank, -1, count, pitch)
        }
        if (recActive && !recMotion && recMidiEngines && recArmPad in 1..8 && recLenMs > 0) {
            val step = recLenMs / loopLenSteps.coerceAtLeast(1) / 4f
            val pos = (System.currentTimeMillis() - recStartMs) % recLenMs
            val q = (Math.round(pos / step) * step).toFloat() % recLenMs.toFloat()
            midiLoops[recArmPad - 1].add(
                floatArrayOf(q, drumGrid.drumMode.toFloat(), n.toFloat(), pitch, count.toFloat())
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    override fun onBackPressed() {
        android.app.AlertDialog.Builder(this)
            .setTitle("SAUVER AVANT DE QUITTER ?")
            .setMessage("La session sera restoree au prochain demarrage")
            .setPositiveButton("SAUVER") { _, _ -> finish() }
            .setNegativeButton("QUITTER") { _, _ -> skipSessionSave = true; finish() }
            .setNeutralButton("ANNULER", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        saveUiPrefs()
    }

    override fun onDestroy() {
        if (!skipSessionSave) saveProject(autoSlot)
        super.onDestroy()
        try {
            unregisterReceiver(larsenReceiver)
        } catch (e: Exception) {
        }
        blinkHandler.removeCallbacks(blinkRunnable)
        vuHandler.removeCallbacks(vuRunnable)
        recEndHandler.removeCallbacksAndMessages(null)
        motionHandler.removeCallbacksAndMessages(null)
        seqHandler.removeCallbacksAndMessages(null)
        if (playing) {
            engine.nativeStop()
            playing = false
        }
    }

    private fun wireFaders() {
        mixFader.onValueChange = { v -> midiOut("F:MIX", v); if (!(snapHold && snapTransp)) engine.nativeSetMix(v) }
        flyFader.onValueChange = { v, st ->
            midiOut("F:FLY", v)
            engine.nativeSetFly(v)
            engine.nativeSetStutter(flyFader.stutterAmount)
        }
        zuLuFader.onValueChange = { v -> midiOut("F:ZULU", v); applyMorph(v) }
        zuLuFader.shiftCheck = { shiftActive() && bendZuluOn }
        zuLuFader.bendModeSelect = bendZuluMode
    }

    private fun resetSteps(b: Int) {
        if (b < 0 || b > 7) return
        bankSteps[b] = loopLenSteps
        stepPitch[b] = FloatArray(14)
        stepVel[b] = FloatArray(14) { 1f }
        engine.nativeSetStepCount(b, loopLenSteps)
    }

    private fun liveSnap() = Snap(
        ax = pad.ax, ay = pad.ay, bx = pad.bx, by = pad.by,
        mix = mixFader.value,
        filter = activeFilter, assignX = assignXIdx, assignY = assignYIdx,
        punch = punchLatched.copyOf(), punchCol2 = punchCol,
        axMulti = if (axisMulti[0].isEmpty()) null else ArrayList(axisMulti[0]),
        ayMulti = if (axisMulti[1].isEmpty()) null else ArrayList(axisMulti[1]),
        axCurve = if (axisMulti[0].isEmpty()) null else axisCurve[0].copyOf(),
        ayCurve = if (axisMulti[1].isEmpty()) null else axisCurve[1].copyOf(),
        bxMulti = if (axisMulti[2].isEmpty()) null else ArrayList(axisMulti[2]),
        byMulti = if (axisMulti[3].isEmpty()) null else ArrayList(axisMulti[3]),
        bxCurve = if (axisMulti[2].isEmpty()) null else axisCurve[2].copyOf(),
        byCurve = if (axisMulti[3].isEmpty()) null else axisCurve[3].copyOf(),
        masterFxList = if (masterFx.isEmpty()) null else ArrayList(masterFx)
    )

    private fun beginSnapHold() {
        if (snapHold) return
        snapHold = true
        snapHoldOld.clear()
        snapHoldFx.clear()
        snapHoldOld["ax"] = pad.ax
        snapHoldOld["ay"] = pad.ay
        snapHoldOld["bx"] = pad.bx
        snapHoldOld["by"] = pad.by
        snapHoldOld["mix"] = mixFader.value
        snapHoldOld["filter"] = activeFilter.toFloat()
        snapHoldOld["axi"] = assignXIdx.toFloat()
        snapHoldOld["ayi"] = assignYIdx.toFloat()
    }

    private fun endSnapHold() {
        if (!snapHold) return
        snapHold = false
        snapHoldOld["ax"]?.let { pad.ax = it }
        snapHoldOld["ay"]?.let { pad.ay = it }
        snapHoldOld["bx"]?.let { pad.bx = it }
        snapHoldOld["by"]?.let { pad.by = it }
        padAxisX = pad.ax
        pad.invalidate()
        snapHoldOld["mix"]?.let {
            mixFader.value = it
            mixFader.invalidate()
        }
        snapHoldOld["filter"]?.let {
            activeFilter = it.toInt()
            refreshFxHighlight()
        }
        snapHoldOld["axi"]?.let {
            assignXIdx = it.toInt()
            refreshAssignLabels()
        }
        snapHoldOld["ayi"]?.let {
            assignYIdx = it.toInt()
            refreshAssignLabels()
        }
        for ((fx, arr) in snapHoldFx) {
            fxParamVals[fx] = arr
            val pv = paramView
            val defs = fxParamDefs[fx]
            if (paramFx == fx && pv != null && defs != null && pv.visibility == View.VISIBLE) {
                pv.setContent(defs, fxParamVals[fx], fxParamAssign[fx])
            }
        }
        snapHoldFx.clear()
        snapHoldOld.clear()
    }

    private fun captureSnap(side: Int, idx: Int) {
        if (idx !in snapPads.indices) return
        snapPads[idx] = liveSnap()
        if (side == 0) activeZu = idx else activeLu = idx
        refreshSnapGrid()
        applyMorph(zuLuFader.value)
        bubble("SNAPSHOT " + (if (side == 0) "ZU " else "LU ") + (idx + 1) + " CAPTUR\u00c9")
    }

    private fun recallSnap(idx: Int) {
        val n = min(padGrid.padCount, 14)
        val isZu = idx < n / 2
        if (motionPlaying == idx) {
            stopMotionPlayback()
            return
        }
        if (isZu) activeZu = idx else activeLu = idx
        val s = snapPads[idx]
        refreshSnapGrid()
        applyMorph(zuLuFader.value)
        if (s != null) {
            applyRouting(s)
            applyPunch(s)
            if ((s.motA != null && s.motA!!.size >= 4) || (s.motB != null && s.motB!!.size >= 4)) {
                startMotionPlayback(idx)
            }
        }
    }

    private fun applyPunch(s: Snap) {
        val pch = s.punch ?: return
        val col = s.punchCol2.coerceIn(0, punchLabels.size - 1)
        for (z in 0 until 9) {
            if (z < pch.size && punchLatched[z] != pch[z]) {
                val saved = punchCol
                punchCol = col
                punchApply(z, pch[z])
                punchCol = saved
            }
        }
    }

    private fun applyRouting(s: Snap) {
        val am = s.axMulti
        if (am != null) {
            s.axCurve?.let { axisCurve[0] = it.copyOf() }
            axisMulti[0].clear()
            axisMulti[0].addAll(am)
            pushAxisMulti(0)
        }
        val ay = s.ayMulti
        if (ay != null) {
            s.ayCurve?.let { axisCurve[1] = it.copyOf() }
            axisMulti[1].clear()
            axisMulti[1].addAll(ay)
            pushAxisMulti(1)
        }
        val bm = s.bxMulti
        if (bm != null) {
            s.bxCurve?.let { axisCurve[2] = it.copyOf() }
            axisMulti[2].clear()
            axisMulti[2].addAll(bm)
            pushAxisMulti(2)
        }
        val bn = s.byMulti
        if (bn != null) {
            s.byCurve?.let { axisCurve[3] = it.copyOf() }
            axisMulti[3].clear()
            axisMulti[3].addAll(bn)
            pushAxisMulti(3)
        }
        s.masterFxList?.let {
            masterFx.clear()
            masterFx.addAll(it)
        }
    }

    private fun refreshSceneSide() {
        btnZu.setTextColor(Color.rgb(14, 16, 20))
        btnZu.background = axisSolidBg(turquoise, sceneSide != 0)
        btnLu.setTextColor(Color.rgb(14, 16, 20))
        btnLu.background = axisSolidBg(violet, sceneSide != 1)
    }

    private fun setVerticalUi(v: Boolean) {
        verticalUi = v
        getSharedPreferences("zulu", MODE_PRIVATE).edit().putBoolean("verticalUi", v).apply()
        requestedOrientation =
            if (v) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        recreate()
    }

    private fun refreshSnapGrid() {
        val n = min(padGrid.padCount, 14)
        val sides = IntArray(14)
        for (i in 0 until n) {
            if (snapPads[i] != null) sides[i] = if (i < n / 2) 1 else 2
        }
        padGrid.snapSides = sides
        padGrid.activeSnap = intArrayOf(activeZu, activeLu)
        pad.ghostZu = activeZu.takeIf { it >= 0 }?.let { snapPads[it] }
            ?.let { floatArrayOf(it.ax, it.ay) }
        pad.ghostLu = activeLu.takeIf { it >= 0 }?.let { snapPads[it] }
            ?.let { floatArrayOf(it.bx, it.by) }
        pad.invalidate()
    }

    private fun applyMorph(tIn: Float) {
        val tRaw = tIn.coerceIn(0f, 1f)
        val t = when (morphCurve) {
            1 -> sqrt(tRaw)
            2 -> tRaw * tRaw
            3 -> tRaw * tRaw * (3f - 2f * tRaw)
            else -> tRaw
        }
        val zu = activeZu.takeIf { it >= 0 }?.let { snapPads[it] } ?: autoZu
        val lu = activeLu.takeIf { it >= 0 }?.let { snapPads[it] } ?: autoLu
        if (zu == null && lu == null) return
        val ax: Float
        val ay: Float
        val bx: Float
        val by: Float
        val mx: Float
        val src: Snap
        if (zu == null) {
            val l = lu ?: return
            if (tRaw < 0.5f) return
            src = l
            ax = l.ax; ay = l.ay; bx = l.bx; by = l.by; mx = l.mix
        } else if (lu == null) {
            if (tRaw > 0.5f) return
            src = zu
            ax = zu.ax; ay = zu.ay; bx = zu.bx; by = zu.by; mx = zu.mix
        } else {
            fun l(a: Float, b: Float) = a + (b - a) * t
            ax = l(zu.ax, lu.ax)
            ay = l(zu.ay, lu.ay)
            bx = l(zu.bx, lu.bx)
            by = l(zu.by, lu.by)
            mx = l(zu.mix, lu.mix)
            src = if (t >= 0.5f) lu else zu
        }
        engine.nativeSetAx(ax)
        engine.nativeSetAy(ay)
        engine.nativeSetBx(bx)
        engine.nativeSetBy(by)
        engine.nativeSetMix(mx)
        pad.setCursor(ax, ay, bx, by)
        mixFader.value = mx
        activeFilter = src.filter
        engine.nativeSetFilterType(activeFilter)
        assignXIdx = src.assignX
        assignYIdx = src.assignY
        engine.nativeSetAssign(0, assignXIdx)
        engine.nativeSetAssign(1, assignYIdx)
        refreshAssignLabels()
        refreshFxHighlight()
    }

    private fun buildMenuPanel(d: Float) {
        menuOverlay = FrameLayout(this)
        menuOverlay.setBackgroundColor(Color.argb(190, 8, 9, 12))
        menuOverlay.visibility = View.GONE
        menuOverlay.setOnClickListener { menuOverlay.visibility = View.GONE }

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        val panelBg = GradientDrawable()
        panelBg.setColor(Color.rgb(24, 26, 32))
        panelBg.setStroke((1 * d).toInt(), Color.rgb(75, 82, 98))
        panelBg.cornerRadius = 8 * d
        panel.background = panelBg
        panel.setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())

        val titleRow = LinearLayout(this)
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "MENU \u00b7 v${versionLabel()}"
        title.setTextColor(Color.rgb(236, 224, 196))
        title.textSize = 16f
        title.typeface = markerFont(this)
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(this)
        close.text = "\u00d7"
        close.setTextColor(silk)
        close.textSize = 16f
        close.minHeight = 0
        close.minimumHeight = 0
        close.minimumWidth = 0
        close.setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
        close.typeface = markerFont(this)
        close.background = buttonBackground(false)
        close.setOnClickListener { menuOverlay.visibility = View.GONE }
        titleRow.addView(close)
        panel.addView(titleRow)

        val sosBtn = Button(this)
        sosBtn.text = "\u2693 SOS"
        sosBtn.isAllCaps = false
        sosBtn.setTextColor(Color.rgb(255, 70, 55))
        sosBtn.textSize = 13f
        sosBtn.minHeight = 0
        sosBtn.minimumHeight = 0
        sosBtn.background = buttonBackground(false)
        sosBtn.setOnClickListener {
            sosMode = !sosMode
            sosBtn.setTextColor(if (sosMode) Color.WHITE else Color.rgb(255, 70, 55))
            menuOverlay.visibility = View.GONE
            bubble(
                if (sosMode) "MODE BOU\u00c9E SOS ACTIF : TOUCHE LES COMMANDES, ELLES S'EXPLIQUENT. SOS DANS LE MENU POUR SORTIR"
                else "MODE SOS D\u00c9SACTIV\u00c9",
                true
            )
        }
        panel.addView(sosBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        fun header(t: String) {
            val h = TextView(this)
            h.text = t
            h.setTextColor(amber)
            h.textSize = 11f
            h.typeface = Typeface.SANS_SERIF
            h.setPadding(0, (10 * d).toInt(), 0, (4 * d).toInt())
            panel.addView(h)
        }

        header("CIRCUIT BENDING")
        val bendSurfRow = LinearLayout(this)
        fun bendToggle(label: String, init: Boolean, set: (Boolean) -> Unit): Button {
            val b = makeButton("$label : " + if (init) "ON" else "OFF", silk)
            b.setOnClickListener {
                val nv = !(b.text.toString().endsWith("ON"))
                b.text = "$label : " + if (nv) "ON" else "OFF"
                b.setTextColor(if (nv) amber else silk)
                set(nv)
            }
            if (init) b.setTextColor(amber)
            bendSurfRow.addView(b, wrapParams((4 * d).toInt()))
            return b
        }
        bendToggle("XY", bendXyOn) { v ->
            bendXyOn = v
            getSharedPreferences("zulu", MODE_PRIVATE).edit().putBoolean("bendXyOn", v).apply()
        }
        bendToggle("MACRO", bendMacroOn) { v ->
            bendMacroOn = v
            getSharedPreferences("zulu", MODE_PRIVATE).edit().putBoolean("bendMacroOn", v).apply()
        }
        bendToggle("ZULU", bendZuluOn) { v ->
            bendZuluOn = v
            getSharedPreferences("zulu", MODE_PRIVATE).edit().putBoolean("bendZuluOn", v).apply()
        }
        panel.addView(bendSurfRow)
        val bendXyModeRow = LinearLayout(this)
        val xyModeNames = listOf("GLITCH", "LOW BAT")
        val bxMode = makeButton("MODE XY : ${xyModeNames[bendXyMode]}", silk)
        bxMode.setOnClickListener {
            bendXyMode = (bendXyMode + 1) % 2
            engine.nativeSetBendMode(bendXyMode)
            bxMode.text = "MODE XY : ${xyModeNames[bendXyMode]}"
            getSharedPreferences("zulu", MODE_PRIVATE).edit().putInt("bendXyMode", bendXyMode).apply()
        }
        if (bendXyMode == 1) bxMode.setTextColor(amber)
        bendXyModeRow.addView(bxMode, wrapParams((4 * d).toInt()))
        panel.addView(bendXyModeRow)
        val macModeNames = listOf("AL\u00c9A", "ERRANCE", "ONDULE", "SACCADE", "TREMBLE", "GLISSE")
        val bmMode = makeButton("MODE MACRO : ${macModeNames[bendMacroMode + 1]}", silk)
        bmMode.setOnClickListener {
            bendMacroMode = (bendMacroMode + 2) % 6 - 1
            macroPanel.bendModeSelect = bendMacroMode
            bmMode.text = "MODE MACRO : ${macModeNames[bendMacroMode + 1]}"
            getSharedPreferences("zulu", MODE_PRIVATE).edit().putInt("bendMacroMode", bendMacroMode).apply()
        }
        bendXyModeRow.addView(bmMode, wrapParams((4 * d).toInt()))
        val bendZuluRow = LinearLayout(this)
        val zuluModeNames = listOf("AL\u00c9A", "ERRANCE", "ONDULE", "SACCADE", "TREMBLE", "GLISSE")
        val bzMode = makeButton("ZULU : ${zuluModeNames[bendZuluMode + 1]}", silk)
        bzMode.setOnClickListener {
            bendZuluMode = (bendZuluMode + 2) % 6 - 1
            zuLuFader.bendModeSelect = bendZuluMode
            bzMode.text = "ZULU : ${zuluModeNames[bendZuluMode + 1]}"
            getSharedPreferences("zulu", MODE_PRIVATE).edit().putInt("bendZuluMode", bendZuluMode).apply()
        }
        bendZuluRow.addView(bzMode, wrapParams((4 * d).toInt()))
        val bFreeze = makeButton("JEU FIG\u00c9", silk)
        bFreeze.setOnClickListener {
            bendFreezeOpt = !bendFreezeOpt
            pad.bendFreeze = bendFreezeOpt
            bFreeze.setTextColor(if (bendFreezeOpt) amber else silk)
        }
        menuFreezeBtn = bFreeze
        if (bendFreezeOpt) bFreeze.setTextColor(amber)
        bendZuluRow.addView(bFreeze, wrapParams((4 * d).toInt()))
        panel.addView(bendZuluRow)
        val bendAmtRow = LinearLayout(this)
        fun bendSeek(label: String, init: Float, set: (Float) -> Unit) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.text = "$label : ${(init * 100).toInt()}%"
            lab.setTextColor(silk)
            lab.textSize = 8f
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.progress = (init * 100f).toInt()
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        lab.text = "$label : $p%"
                        set(p / 100f)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            col.addView(sb)
            bendAmtRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        bendSeek("QUANTIT\u00c9", bendAmtOpt) { v ->
            bendAmtOpt = v
            engine.nativeSetBendParams(bendAmtOpt, bendRateOpt)
            getSharedPreferences("zulu", MODE_PRIVATE).edit().putFloat("bendAmt", v).apply()
        }
        bendSeek("VITESSE", bendRateOpt) { v ->
            bendRateOpt = v
            engine.nativeSetBendParams(bendAmtOpt, bendRateOpt)
            getSharedPreferences("zulu", MODE_PRIVATE).edit().putFloat("bendRate", v).apply()
        }
        panel.addView(bendAmtRow)

        val panRow = LinearLayout(this)
        val pB = makeButton("PANIQUE \u00b7 TOUT REMETRE A ZERO", red)
        pB.setOnClickListener { panicReset() }
        panRow.addView(pB, wrapParams((4 * d).toInt()))
        panel.addView(panRow)

        header("FUSION XY \u00b7 D\u00c9LAI F+Z MAINTENUS")
        val fuseDelayRow = LinearLayout(this)
        for ((i, ms) in listOf(500, 1000, 1500, 2000).withIndex()) {
            val b = makeButton(if (ms == 500) "0.5s" else "${ms / 1000}s", silk)
            b.setOnClickListener {
                fuseDelayMs = ms
                saveUiPrefs()
                refreshMenu()
            }
            menuFuseDelayBtns.add(b)
            fuseDelayRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(fuseDelayRow)

        header("PROJETS \u00b7 TAP = CHARGER \u00b7 LONG = SAUVER")
        for (r in 0 until 2) {
            val projRow = LinearLayout(this)
            for (c in 0 until 4) {
                val s = r * 4 + c + 1
                val b = makeButton("$s", silk)
                b.setOnClickListener {
                    loadProject(s)
                    refreshProjSlots()
                    refreshMenu()
                }
                b.setOnLongClickListener {
                    saveProject(s)
                    refreshMenu()
                    true
                }
                menuProjButtons.add(b)
                projRow.addView(
                    b,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginStart = (4 * d).toInt() }
                )
            }
            panel.addView(projRow)
        }

        val bImport = makeButton("IMPORTER \u00b7 WAV / KOALA", silk)
        bImport.setOnClickListener { pickSampleFile(-1) }
        panel.addView(bImport, wrapParams((4 * d).toInt()))

        header("LIVE")
        val liveRow = LinearLayout(this)
        val bScale = makeButton("GAMME", silk)
        bScale.setOnClickListener { menuOverlay.visibility = View.GONE; openScalePanel() }
        val bMirror = makeButton("MIROIR", silk)
        bMirror.setOnClickListener {
            mirrored = !mirrored
            content.layoutDirection =
                if (mirrored) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            refreshMenu()
        }
        menuMirrorBtn = bMirror
        val bPunch = makeButton("PUNCH 9", silk)
        bPunch.setOnClickListener {
            setPunchMode(!punchMode)
            menuOverlay.visibility = View.GONE
        }
        menuPunchBtn = bPunch
        liveRow.addView(bScale, wrapParams((4 * d).toInt()))
        liveRow.addView(bMirror, wrapParams((4 * d).toInt()))
        liveRow.addView(bPunch, wrapParams((4 * d).toInt()))
        panel.addView(liveRow)

        header("PUNCH 9 \u00b7 MAINTENIR = EFFET \u00b7 SHIFT+TAP = SORTIR")

        header("TEMPS LOOP (REC 1-19)")
        val loopRow = LinearLayout(this)
        val bHalf = makeButton("\u00f72", silk)
        bHalf.setOnClickListener { loopMul(0); refreshMenu() }
        val bDbl = makeButton("\u00d72", silk)
        bDbl.setOnClickListener { loopMul(1); refreshMenu() }
        val bLoopLbl = makeButton("8", silk)
        bLoopLbl.setOnClickListener {
            loopLenSteps = 8
            padGrid.activeBank = 8
            refreshMenu()
        }
        menuLoopLbl = bLoopLbl
        loopRow.addView(bHalf, wrapParams((4 * d).toInt()))
        loopRow.addView(bLoopLbl, wrapParams((4 * d).toInt()))
        loopRow.addView(bDbl, wrapParams((4 * d).toInt()))
        panel.addView(loopRow)

        header("REC \u00b7 SAVEUR LO-FI")
        val lofiRow = LinearLayout(this)
        for ((i, lbl) in listOf("OFF", "SP", "K7", "M\u00c9TAL").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                recFlavor = i
                engine.nativeSetRecFlavor(recFlavor)
                refreshMenu()
            }
            menuLofiButtons.add(b)
            lofiRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(lofiRow)

        header("STRETCH SAMPLES \u00b7 B" + (playerBank + 1))
        val stRow = LinearLayout(this)
        for ((i, lbl) in listOf("OFF", "CLEAN", "BEAT", "RETRO").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                bankStretch[playerBank] = i
                engine.nativeSetStretchMode(playerBank, i)
                sampleParamVals[playerBank][6] = i / 3f
                engine.nativeSetSampleParam(playerBank, 6, i / 3f)
                refreshMenu()
            }
            menuStretchButtons.add(b)
            stRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(stRow)

        header("SC\u00c8NES")
        val scRow = LinearLayout(this)
        val bSnap = makeButton("SNAPSHOT", amber)
        bSnap.setOnClickListener {
            selectPadMode(0)
        }
        val bMotion = makeButton("MOTION", turquoise)
        bMotion.setOnClickListener {
            selectPadMode(1)
        }
        bSnapBtn = bSnap
        bMotionBtn = bMotion
        scRow.addView(bSnap, wrapParams((4 * d).toInt()))
        scRow.addView(bMotion, wrapParams((4 * d).toInt()))
        panel.addView(scRow)

        header("BILLARD")
        val billardRow = LinearLayout(this)
        for ((i, lbl) in listOf("\u221e", "0.5", "1", "2", "4").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener { pad.billardFriction = i; refreshMenu() }
            menuBillardButtons.add(b)
            billardRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(billardRow)

        header("FOND VIVANT (BATTERIE)")
        val artRow = LinearLayout(this)
        for ((i, lbl) in listOf("AUCUN", "LENT", "NORM", "RAPIDE", "MANDALA").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                bgArtOpt = i
                pad.bgArt = i
                pad.invalidate()
                refreshMenu()
            }
            menuArtButtons.add(b)
            artRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(artRow)

        header("STYLE")
        val styleRow = LinearLayout(this)
        for ((i, lbl) in listOf("84", "RETRO", "GAME BOY", "ELECTRIBE", "CAUSTIC").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                ZuluTheme.retro = i == 1
                ZuluTheme.gameboy = i == 2
                ZuluTheme.electribe = i == 3
                ZuluTheme.caustic = i == 4
                ZuluTheme.bgColor = when (i) {
                    1 -> Color.rgb(46, 40, 31)
                    2 -> Color.rgb(13, 27, 18)
                    3 -> Color.rgb(28, 28, 30)
                    4 -> Color.rgb(21, 26, 33)
                    else -> Color.rgb(16, 18, 23)
                }
                invalidateTree(content)
                refreshMenu()
                saveUiPrefs()
            }
            menuStyleButtons.add(b)
            styleRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(styleRow)

        header("POLICE")
        val fontRow = LinearLayout(this)
        for ((i, lbl) in listOf("PIXEL", "FORMEL", "MARQUEUR").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                ZuluTheme.fontId = i
                applyFontTree(content)
                refreshMenu()
                saveUiPrefs()
            }
            menuFontButtons.add(b)
            fontRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(fontRow)

        val xFilesB = makeButton("X-FILES", silk)
        xFilesB.setOnClickListener {
            ZuluTheme.xFiles = !ZuluTheme.xFiles
            invalidateTree(content)
            refreshMenu()
            saveUiPrefs()
        }
        menuXFilesButton = xFilesB
        styleRow.addView(xFilesB, wrapParams((4 * d).toInt()))

        val helpB = makeButton("AIDE XY", silk)
        helpB.setOnClickListener {
            xyHelpOpt = !xyHelpOpt
            if (!xyHelpOpt) bpmLcd.hintText = ""
            refreshMenu()
        }
        menuHelpButtons.add(helpB)
        styleRow.addView(helpB, wrapParams((4 * d).toInt()))

        val bgRow = LinearLayout(this)
        for ((i, pair) in bgColors.withIndex()) {
            val b = makeButton(pair.first, silk)
            b.setOnClickListener {
                ZuluTheme.bgColor = pair.second
                invalidateTree(content)
                refreshMenu()
                saveUiPrefs()
            }
            menuBgButtons.add(b)
            bgRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(bgRow)

        header("MOTIF DU FOND")
        val patternRow = LinearLayout(this)
        for ((i, lbl) in listOf("Z\u00c9BRURES", "POIS").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                ZuluTheme.bgDots = i == 1
                invalidateTree(content)
                refreshMenu()
                saveUiPrefs()
            }
            menuPatternButtons.add(b)
            patternRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(patternRow)

        val bgSizeRow = LinearLayout(this)
        fun bgSizeSeek(label: String, value: Float, lo: Float, hi: Float, set: (Float) -> Unit) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.text = label
            lab.setTextColor(silk)
            lab.textSize = 8f
            lab.typeface = Typeface.SANS_SERIF
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.progress = (((value - lo) / (hi - lo)) * 100f).toInt().coerceIn(0, 100)
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        set(lo + (hi - lo) * p / 100f)
                        invalidateTree(content)
                        saveUiPrefs()
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            col.addView(sb)
            bgSizeRow.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        bgSizeSeek("Z\u00c9BRURES", ZuluTheme.zebraWidthDp, 2f, 22f) { ZuluTheme.zebraWidthDp = it }
        bgSizeSeek("POIS", ZuluTheme.dotSizeDp, 1f, 8f) { ZuluTheme.dotSizeDp = it }
        panel.addView(bgSizeRow)

        val zcRow = LinearLayout(this)
        for ((i, pair) in zebraColors.withIndex()) {
            val b = makeButton(pair.first, silk)
            b.setOnClickListener {
                ZuluTheme.zebraColor = pair.second
                invalidateTree(content)
                refreshMenu()
                saveUiPrefs()
            }
            menuZebraColorButtons.add(b)
            zcRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(zcRow)

        header("CHAT")
        val catColRow = LinearLayout(this)
        for ((i, pair) in catColors.withIndex()) {
            val b = makeButton(pair.first, silk)
            b.setOnClickListener {
                catColorSel = pair.second
                bpmLcd.catBodyColor = pair.second
                bpmLcd.invalidate()
                refreshMenu()
                saveUiPrefs()
            }
            menuCatColorButtons.add(b)
            catColRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(catColRow)

        val sirenWaveRow = LinearLayout(this)
        for ((i, lbl) in listOf("CLASSIQUE", "CHOP", "POLICE", "LASER").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                sirenWave = i
                engine.nativeSetSirenWave(sirenWave)
                refreshMenu()
                saveUiPrefs()
            }
            menuSirenWaveButtons.add(b)
            sirenWaveRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(sirenWaveRow)

        val sirenModeRow = LinearLayout(this)
        for ((i, lbl) in listOf("SIRENE AUTO", "SIRENE LENTE", "SIRENE RAPIDE").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                sirenType = i
                refreshMenu()
                saveUiPrefs()
            }
            menuSirenButtons.add(b)
            sirenModeRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(sirenModeRow)

        header("MODE ECO (BATTERIE)")
        val ecoRow = LinearLayout(this)
        for ((i, lbl) in listOf("OFF", "ON").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                ecoOpt = i == 1
                engine.nativeSetEco(ecoOpt)
                if (ecoOpt && pad.bgArt != 0) {
                    bgArtEcoPrev = pad.bgArt
                    pad.bgArt = 0
                    bgArtOpt = 0
                } else if (!ecoOpt && bgArtEcoPrev > 0) {
                    pad.bgArt = bgArtEcoPrev
                    bgArtOpt = bgArtEcoPrev
                    bgArtEcoPrev = 0
                }
                pad.invalidate()
                refreshMenu()
            }
            menuEcoButtons.add(b)
            ecoRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(ecoRow)

        header("DRUM PADS (NOTES = 32 = 2 OCTAVES)")
        val drumRow = LinearLayout(this)
        val drumCounts = listOf(8, 12, 16, 32)
        for ((i, lbl) in listOf("8", "12", "16", "32").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                drumGrid.padCount = drumCounts[i]
                refreshMenu()
            }
            menuDrumButtons.add(b)
            drumRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(drumRow)

        header("REC ENGINES (DRUMS/SYNTH)")
        val midiRow = LinearLayout(this)
        for ((i, lbl) in listOf("MIDI", "AUDIO").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                recMidiEngines = i == 0
                refreshMenu()
            }
            menuMidiButtons.add(b)
            midiRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(midiRow)

        header("ENTREE (LINE-IN)")
        val inRow = LinearLayout(this)
        for ((i, lbl) in listOf("TRANSP", "INSTRU", "VOIX", "BBOX").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                inProfileSel = i
                engine.nativeSetInProfile(i)
                refreshMenu()
            }
            menuInButtons.add(b)
            inRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(inRow)

        header("MICRO / ANTI-LARSEN")
        val micRow = LinearLayout(this)
        for ((i, lbl) in listOf("MIC OFF", "MIC ON").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                if (i == 1) requestMic() else restartEngineMic(false)
            }
            menuMicButtons.add(b)
            micRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(micRow)

        header("EQUALISEUR (ENTREE + SORTIE)")
        val eqRow = LinearLayout(this)
        for ((i, lbl) in listOf("OFF", "ON").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                masterEqOn = i == 1
                engine.nativeSetMasterEq(masterEqOn)
                refreshMenu()
            }
            menuEqButtons.add(b)
            eqRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(eqRow)

        header("AUDIO \u00b7 CONSOLE")
        val afInRow = LinearLayout(this)
        val afInVals = listOf(-0.6f, 0f, 0.6f)
        for ((i, lbl) in listOf("IN LP", "IN NEUTRE", "IN HP").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                inFSel = i
                inFilVal = afInVals[i]
                engine.nativeSetInFilter(afInVals[i])
                vuMeter.inFilter = afInVals[i]
                bpmLcd.inFilter = afInVals[i]
                refreshMenu()
            }
            menuInFButtons.add(b)
            afInRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(afInRow)
        val afOutRow = LinearLayout(this)
        val afOutVals = listOf(-0.6f, 0f, 0.6f)
        for ((i, lbl) in listOf("OUT LP", "OUT NEUTRE", "OUT HP").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                outFSel = i
                outFilVal = afOutVals[i]
                engine.nativeSetMasterFilter(afOutVals[i])
                vuMeter.outFilter = afOutVals[i]
                bpmLcd.outFilter = afOutVals[i]
                refreshMenu()
            }
            menuOutFButtons.add(b)
            afOutRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(afOutRow)
        val compRow = LinearLayout(this)
        for ((i, lbl) in listOf("COMP OFF", "DOUX", "PUNCH", "DUR").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                masterCompSel = i
                engine.nativeSetMasterComp(i)
                refreshMenu()
            }
            menuCompButtons.add(b)
            compRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(compRow)
        val volRow = LinearLayout(this)
        val volVals = listOf(0.5f, 1f, 1.5f, 2f)
        for ((i, lbl) in listOf("VOL x0.5", "x1", "x1.5", "x2").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                masterVol = volVals[i]
                volSel = i
                engine.nativeSetMasterVolume(masterVol)
                refreshMenu()
            }
            menuVolButtons.add(b)
            volRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(volRow)
        val capRow = LinearLayout(this)
        for ((i, lbl) in listOf("CAPTURE APPS OFF", "CAPTURE APPS ON").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                if (i == 1) startAppCapture() else stopAppCapture()
            }
            menuCapButtons.add(b)
            capRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(capRow)

        header("MIDI")
        val midiOutRow = LinearLayout(this)
        for ((i, lbl) in listOf("OUT OFF", "OUT ON").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                if (i == 1) ZuluMidi.start(this) else ZuluMidi.stop()
                refreshMenu()
            }
            menuMidiOutButtons.add(b)
            midiOutRow.addView(b, wrapParams((4 * d).toInt()))
        }
        val midiChB = makeButton("CANAL G : ${ZuluMidi.globalChannel + 1}", silk)
        midiChB.setOnClickListener {
            ZuluMidi.globalChannel = (ZuluMidi.globalChannel + 1) % 16
            refreshMenu()
        }
        menuMidiChButton = midiChB
        midiOutRow.addView(midiChB, wrapParams((4 * d).toInt()))
        val midiTeachB = makeButton("TEACH", silk)
        midiTeachB.setOnClickListener {
            ZuluMidi.teach = !ZuluMidi.teach
            if (ZuluMidi.teach && !ZuluMidi.enabled) ZuluMidi.start(this)
            bubble(if (ZuluMidi.teach) "MIDI TEACH : BOUGE UN CONTR\u00d4LE POUR LUI ASSIGNER UN CC" else "MIDI TEACH OFF", true)
            refreshMenu()
        }
        menuMidiTeachButton = midiTeachB
        midiOutRow.addView(midiTeachB, wrapParams((4 * d).toInt()))
        panel.addView(midiOutRow)
        val midiRow2 = LinearLayout(this)
        val midiClrB = makeButton("EFFACER MAP", silk)
        midiClrB.setOnClickListener {
            ZuluMidi.clear()
            bubble("MIDI MAP EFFAC\u00c9E")
            refreshMenu()
        }
        midiRow2.addView(midiClrB, wrapParams((4 * d).toInt()))
        val midiInfoB = makeButton("APPAREILS", silk)
        midiInfoB.setOnClickListener {
            val names = ZuluMidi.deviceNames(this)
            bubble(if (names.isEmpty()) "AUCUN APPAREIL MIDI\n(KOALA, USB...)" else "MIDI : " + names.joinToString(", "), true)
        }
        midiRow2.addView(midiInfoB, wrapParams((4 * d).toInt()))
        val midiCntB = makeButton("MAP : ${ZuluMidi.outMap.size}", silk)
        menuMidiCntButton = midiCntB
        midiRow2.addView(midiCntB, wrapParams((4 * d).toInt()))
        panel.addView(midiRow2)

        header("BOOST")
        val biRow = LinearLayout(this)
        for ((i, lbl) in listOf("IN x1", "IN x2", "IN x4").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                inBoostSel = i
                engine.nativeSetInBoost(if (i == 0) 1f else if (i == 1) 2f else 4f)
                refreshMenu()
            }
            menuBoostInButtons.add(b)
            biRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(biRow)
        val boRow = LinearLayout(this)
        for ((i, lbl) in listOf("OUT x1", "OUT x2", "OUT x3").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                outBoostSel = i
                engine.nativeSetOutBoost(if (i == 0) 1f else if (i == 1) 2f else 3f)
                refreshMenu()
            }
            menuBoostOutButtons.add(b)
            boRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(boRow)

        header("PROFIL SORTIE")
        val ldRow = LinearLayout(this)
        for ((i, lbl) in listOf("STUDIO", "LOUDNESS", "COULEUR").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                outProfSel = i
                engine.nativeSetOutLoud(i)
                refreshMenu()
            }
            menuLoudButtons.add(b)
            ldRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(ldRow)

        header("RESEAU (FX QUI SE PARLENT)")
        val netRow = LinearLayout(this)
        for ((i, lbl) in listOf(
            "OFF", "MYCELIUM", "HUMUS", "NET VINTAGE",
            "CHAOS", "OISEAUX", "INSECTES", "MAREE"
        ).withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                netModeSel = i
                engine.nativeSetNetworkMode(i)
                refreshMenu()
            }
            menuNetButtons.add(b)
            netRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(netRow)

        header("RESEAU : REGLAGES")
        val nchRow = LinearLayout(this)
        for ((i, lbl) in listOf("CHAOS DOUX", "CHAOS MOYEN", "CHAOS FORT").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                netChaosSel = i
                pushNetParams()
                refreshMenu()
            }
            menuNetChaosButtons.add(b)
            nchRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(nchRow)
        val nmdRow = LinearLayout(this)
        for ((i, lbl) in listOf("MOD LEGER", "MOD MOYEN", "MOD FORT").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                netModSel = i
                pushNetParams()
                refreshMenu()
            }
            menuNetModButtons.add(b)
            nmdRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(nmdRow)
        val nalRow = LinearLayout(this)
        for ((i, lbl) in listOf("ALIAS OFF", "ALIAS LEGER", "ALIAS FORT").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                netAliasSel = i
                pushNetParams()
                refreshMenu()
            }
            menuNetAliasButtons.add(b)
            nalRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(nalRow)
        val nspRow = LinearLayout(this)
        for ((i, lbl) in listOf("VITESSE LENTE", "VITESSE NORMALE", "VITESSE RAPIDE", "VITESSE FRENETIQUE").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                netSpeedSel = i
                pushNetParams()
                refreshMenu()
            }
            menuNetSpeedButtons.add(b)
            nspRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(nspRow)

        header("TEMPLATE DE BASE (AU DEMARRAGE)")
        val tplRow = LinearLayout(this)
        val tS = makeButton("SAUVER L'ETAT ACTUEL", silk)
        tS.setOnClickListener {
            saveProject(0, global = true)
            bubble("TEMPLATE DE BASE SAUVEGARDE")
        }
        tplRow.addView(tS, wrapParams((4 * d).toInt()))
        val tE = makeButton("EFFACER", silk)
        tE.setOnClickListener {
            globalFile(0).delete()
            for (b in 0 until 8) bankFile(0, b, true).delete()
            bubble("TEMPLATE EFFACE")
        }
        tplRow.addView(tE, wrapParams((4 * d).toInt()))
        panel.addView(tplRow)

        header("ENREGISTREMENT")
        val qRow = LinearLayout(this)
        val qB = makeButton(if (quantRec) "BOUCLES QUANTISÉES : ON" else "BOUCLES QUANTISÉES : OFF", silk)
        qB.setOnClickListener {
            quantRec = !quantRec
            qB.text = if (quantRec) "BOUCLES QUANTISÉES : ON" else "BOUCLES QUANTISÉES : OFF"
            bubble("QUANT REC " + (if (quantRec) "ON" else "OFF"))
        }
        qRow.addView(qB, wrapParams((4 * d).toInt()))
        panel.addView(qRow)

        header("LOGS SAMPLES")
        val logRow = LinearLayout(this)
        for ((i, lbl) in listOf("OFF", "ON").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                showSampleLog = i == 1
                getSharedPreferences("zulu", MODE_PRIVATE).edit().putBoolean("sampleLog", showSampleLog).apply()
                sampleLogView?.visibility = if (showSampleLog) View.VISIBLE else View.GONE
                refreshMenu()
            }
            menuLogButtons.add(b)
            logRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(logRow)

        header("NOMS DES SAMPLES (BANQUES 1-8)")
        val nmRow = LinearLayout(this)
        for (b in 1..8) {
            val bt = makeButton("B$b", silk)
            bt.setOnClickListener { askSampleName(b - 1) }
            menuNameButtons.add(bt)
            nmRow.addView(bt, wrapParams((4 * d).toInt()))
        }
        panel.addView(nmRow)

        header("ORIENTATION (REDEMARRE)")
        val orientRow = LinearLayout(this)
        for ((i, lbl) in listOf("HORIZ", "VERT").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener { setVerticalUi(i == 1) }
            menuOrientButtons.add(b)
            orientRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(orientRow)

        header("SNAPSHOT")
        val snapRow = LinearLayout(this)
        for ((i, lbl) in listOf("TRANSPARENT", "DIRECT").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                snapTransp = i == 0
                getSharedPreferences("zulu", MODE_PRIVATE).edit().putBoolean("snapTransp", snapTransp).apply()
                refreshMenu()
            }
            menuSnapButtons.add(b)
            snapRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(snapRow)

        header("FINITIONS")
        val curveRow = LinearLayout(this)
        for ((i, lbl) in listOf("LIN", "D\u00c9BUT", "FIN", "S").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener { morphCurve = i; refreshMenu() }
            menuCurveButtons.add(b)
            curveRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(curveRow)

        val fingerRow = LinearLayout(this)
        for ((i, lbl) in listOf("ORDRE", "INVERS\u00c9", "ZONES").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                fingerModeOpt = i
                pad.fingerMode = fingerModeOpt
                refreshMenu()
            }
            menuFingerButtons.add(b)
            fingerRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(fingerRow)



        val scroll = android.widget.ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(panel)
        val menuScrollParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        menuScrollParams.setMargins((6 * d).toInt(), (8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt())
        menuOverlay.addView(scroll, menuScrollParams)
    }

    private val punchLabels = listOf(
        listOf("WRP", "RNG", "PHZ", "JGL", "CUT", "BIT", "FLD", "PNG", "CHO"),
        listOf("DUB", "ECH", "FLG", "RSO", "GAT", "SWL", "TRM", "LPF", "WET"),
        listOf("DIS", "FUZ", "CRS", "STP", "RPT", "PMP", "TLK", "DRI", "TDO"),
        listOf("LOF", "K7", "CMP", "WRM", "LPF", "SPR", "SCR", "FRZ", "DRI"),
        listOf("K7", "MEG", "RNG", "TLK", "AUT", "PHS", "CMB", "GRN", "WET"),
        listOf("RPT", "GAT", "SPL", "REV", "FLD", "BIT", "SLC", "PMP", "JGL"),
        listOf("CHO", "FLG", "SPR", "EQ3", "CMP", "CLN", "SWL", "AUT", "LPF"),
        listOf("LOF", "ECH", "DRI", "GAT", "TRM", "WRM", "RSO", "MEG", "WET"),
        listOf("DIS", "EQ3", "FUZ", "STP", "SPL", "RNG", "REV", "WRP", "RES"),
        listOf("SCR", "FRZ", "GRN", "CMB", "RNG", "DLY", "TDO", "TRS", "FAL")
    )
    private val punchFx = arrayOf(
        intArrayOf(30, 7, 2, 20, -1, -1, 17, 0, 6),
        intArrayOf(27, 1, 5, 15, 9, 25, 3, -1, -1),
        intArrayOf(45, 44, -1, 12, 13, 26, 31, -1, 23),
        intArrayOf(8, 29, 41, 42, -1, 16, 18, 10, -1),
        intArrayOf(29, 28, 32, 31, 4, 2, 22, 19, -1),
        intArrayOf(13, 9, 33, 14, 17, -1, 37, 26, 20),
        intArrayOf(6, 5, 16, 40, 41, 43, 25, 4, -1),
        intArrayOf(8, 1, -1, 9, 3, 42, 15, 28, -1),
        intArrayOf(45, 40, 44, 12, 33, 7, 14, 30, -1),
        intArrayOf(18, 10, 19, 22, 32, 0, 23, 21, 24)
    )
    private val punchParam = arrayOf(
        intArrayOf(0, 0, 0, 1, 0, 3, 0, 0, 0),
        intArrayOf(1, 0, 0, -1, 1, -1, 0, 0, 2),
        intArrayOf(0, 0, 3, -1, 0, 0, 0, 7, 0),
        intArrayOf(0, 0, -1, -1, 0, -1, 0, -1, 7),
        intArrayOf(1, 2, -1, 1, 0, 0, 1, 0, 2),
        intArrayOf(2, 2, -1, 0, 2, 3, -1, 1, 3),
        intArrayOf(1, 2, -1, 0, 1, -1, -1, 1, 0),
        intArrayOf(1, 1, 7, 0, 1, -1, 1, 1, 2),
        intArrayOf(1, 2, 0, 1, 2, 0, 1, 1, 1),
        intArrayOf(1, 1, 1, 0, 1, 1, 1, 0, 0)
    )
    private val punchVal = arrayOf(
        floatArrayOf(0.7f, 0.8f, 0.85f, 0.9f, 0.12f, 0.85f, 0.8f, 0.2f, 0.25f),
        floatArrayOf(0.78f, 0.45f, 0.3f, 0f, 0.35f, 0f, 0.35f, 0.3f, 0.7f),
        floatArrayOf(0.9f, 0.85f, 1f, 0f, 0.15f, 1f, 0.8f, 0.9f, 0.9f),
        floatArrayOf(0.7f, 0.75f, 0f, 0f, 0.38f, 0f, 0.3f, 0f, 0.3f),
        floatArrayOf(0.8f, 0.8f, 0f, 0.5f, 0.3f, 0.25f, 0.7f, 0.5f, 0.85f),
        floatArrayOf(0.6f, 0.9f, 0f, 0.6f, 0.8f, 0.95f, 0f, 0.8f, 0.85f),
        floatArrayOf(0.4f, 0.35f, 0f, 0.3f, 0.5f, 0f, 0f, 0.4f, 0.5f),
        floatArrayOf(0.75f, 0.5f, 0.5f, 0.5f, 0.5f, 0f, 0.7f, 0.6f, 0.5f),
        floatArrayOf(0.7f, 0.6f, 0.6f, 0.6f, 0.6f, 0.6f, 0.7f, 0.6f, 0.8f),
        floatArrayOf(0.7f, 0.7f, 0.7f, 0.5f, 0.7f, 0.5f, 0.7f, 0.5f, 0.6f)
    )

    private fun assignBankFx(bank: Int) {
        val m = bankFxMask[bank]
        if (m.contentEquals(fxActive)) {
            m.fill(false)
            bubble("BANQUE ${bank + 1} : SANS FX")
        } else {
            fxActive.copyInto(m)
            bubble("FX \u2192 BANQUE ${bank + 1}")
        }
        if (bankOn[bank]) refreshBankFx()
    }

    private fun applyBankFx(b: Int) {
        if (b >= 0) {
            val mask = bankFxMask[b]
            if (!rackBackedUp) {
                fxActive.copyInto(rackBackup)
                rackBackedUp = true
            }
            for (f in 0 until 84) {
                if (masterFx.contains(f)) continue
                if (fxActive[f] != mask[f]) {
                    fxActive[f] = mask[f]
                    engine.nativeSetFx(f, fxActive[f])
                }
            }
        } else if (rackBackedUp) {
            for (f in 0 until 84) {
                if (masterFx.contains(f)) continue
                if (fxActive[f] != rackBackup[f]) {
                    fxActive[f] = rackBackup[f]
                    engine.nativeSetFx(f, fxActive[f])
                }
            }
            rackBackedUp = false
        }
        refreshFxHighlight()
    }

    private fun refreshBankFx() {
        val active = (0 until 8).lastOrNull { bankOn[it] && bankFxMask[it].any { v -> v } }
        applyBankFx(active ?: -1)
    }

    private val axisNames = listOf("\u03a8 X", "\u03a8 Y", "\u039e X", "\u039e Y")
    private var axisFlowAxis = -1
    private var axisFlowStage = 0
    private var axisFlowBank = -1

    private fun firstActiveFxOfBank(b: Int): Int {
        val p = userPresets[b] ?: return -1
        for (f in 0 until min(84, p.fxOn.size)) if (p.fxOn[f]) return f
        return -1
    }

    private fun openBankFxPage(b: Int) {
        val fx = firstActiveFxOfBank(b)
        if (fx < 0) {
            bubble("BANQUE ${b + 1} VIDE \u2014 APPUI LONG POUR Y METTRE DES EFFETS", true)
        } else {
            activeGroup = b
            openParamPage(fx)
        }
    }

    private fun axisFlowParamTap(k: Int) {
        val fx = paramFx
        if (fx < 0) return
        val slot = 8 + fxSlot(fx) * 8 + k
        val ax = axisFlowAxis + (axisFlowStage - 2)
        val cur = axisMulti[ax]
        if (!cur.contains(slot)) {
            if (cur.size >= 8) {
                bubble("MAX 8 CIBLES PAR AXE")
                return
            }
            cur.add(slot)
        }
        val lbl = fxParamDefs[fx]?.getOrNull(k) ?: "PARAM $k"
        if (axisFlowStage == 2) {
            axisFlowStage = 3
            bubble("${axisNames[ax]} \u2192 $lbl \u00b7 MAINTENANT ${axisNames[ax + 1]} : CHOISIR LE PARAM", true)
        } else {
            bubble("${axisNames[ax]} \u2192 $lbl")
            axisFlowStage = 0
            axisFlowAxis = -1
            closeParamPage()
            pushAxisMulti(ax)
        }
    }

    private fun applyMacroFader(f: Int) {
        if (f !in 0..5) return
        val v = macroPanel.faderVals[f]
        for (tg in macroFaderTargets[f]) {
            engine.nativeSetFxParam(tg.first, tg.second, if (tg.third) 1f - v else v)
        }
    }

    private fun refreshMacroFaderLbl(f: Int) {
        if (f !in 0..5) return
        val list = macroFaderTargets[f]
        macroPanel.faderTargetLbl[f] = when {
            list.isEmpty() -> null
            list.size == 1 -> {
                val nm = fxParamDefs[list[0].first]?.getOrNull(list[0].second) ?: "P${list[0].second}"
                if (list[0].third) "$nm\u2193" else nm
            }
            else -> "${list.size} CIBLES"
        }
        macroPanel.invalidate()
    }

    private fun macroFlowParamTap(k: Int) {
        val fx = paramFx
        if (fx < 0 || macroFlowFader !in 0..5) return
        val slot = fxSlot(fx)
        val f = macroFlowFader
        val lbl = fxParamDefs[fx]?.getOrNull(k) ?: "PARAM $k"
        val list = macroFaderTargets[f]
        val existing = list.indexOfFirst { it.first == slot && it.second == k }
        val msg: String
        if (existing >= 0) {
            val cur = list[existing]
            list[existing] = Triple(slot, k, !cur.third)
            msg = "$lbl " + (if (!cur.third) "COURSE INVERS\u00c9E (0 EN HAUT)" else "COURSE NORMALE")
        } else if (list.size >= 8) {
            bubble("MAX 8 CIBLES PAR FADER")
            return
        } else {
            list.add(Triple(slot, k, false))
            msg = "M${f + 1} \u2192 $lbl" +
                " \u00b7 CONTINUER (AUTRE PARAM) OU SHIFT+M${f + 1}/FX POUR FINIR"
        }
        applyMacroFader(f)
        refreshMacroFaderLbl(f)
        saveUiPrefs()
        bubble(msg, true)
    }

    private fun presetTap(i: Int) {
        if (sosHint("BANQUES 1-9 : SHIFT+TAP = ACTIVE/\u00c9TEINT. APPUI LONG = MENU DE LA BANQUE. VIDE = APPUI LONG POUR CHOISIR DES EFFETS.")) return
        if (shiftActive() && macroFlowStage == 0 && axisFlowStage == 0 && !lenEntry &&
            !(recArmed && recArmPad > 0 && !punchMode) && toolPage != 3 &&
            heldBankPad !in 1..8 && assignArm < 0 && !punchMode) {
            val p = userPresets[i]
            if (p == null) {
                bubble("BANQUE ${i + 1} VIDE \u2014 APPUI LONG POUR Y METTRE DES EFFETS", true)
                return
            }
            if (pad.activePreset == i) {
                for (f in 0 until 84) {
                    if (f < p.fxOn.size && p.fxOn[f] && fxActive[f]) {
                        fxActive[f] = false
                        engine.nativeSetFx(f, false)
                    }
                }
                genIdx = -1
                engine.nativeSetGen(-1)
                pad.activePreset = -1
                bubble("BANQUE ${i + 1} \u00c9TEINTE")
            } else {
                applyUserPreset(p)
                applyBankLevel(i)
                pad.activePreset = i
                activeGroup = i
                pushLinks()
                bubble(if (sosMode && factorySlots[i]) "BANQUE ${i + 1} : ${bankDescs[i]}" else "BANQUE ${i + 1} ACTIVE")
            }
            pad.invalidate()
            refreshPresetRow()
            return
        }
        if (macroFlowStage == 1) {
            if (userPresets[i] == null) {
                bubble("BANQUE ${i + 1} VIDE \u2014 APPUI LONG POUR Y METTRE DES EFFETS", true)
                return
            }
            activeGroup = i
            openBankFxPage(i)
            macroFlowStage = 2
            bubble("M${macroFlowFader + 1} : CHOISIR LE PARAM", true)
            return
        }
        if (axisFlowStage == 1) {
            if (userPresets[i] == null) {
                bubble("BANQUE ${i + 1} VIDE \u2014 APPUI LONG POUR Y METTRE DES EFFETS", true)
                return
            }
            axisFlowBank = i
            axisFlowStage = 2
            activeGroup = i
            openBankFxPage(i)
            bubble("${axisNames[axisFlowAxis]} : CHOISIR LE PARAM", true)
            return
        }
        if (axisFlowStage == 3 && i != axisFlowBank) {
            axisFlowBank = i
            activeGroup = i
            openBankFxPage(i)
            bubble("${axisNames[axisFlowAxis + 1]} : CHOISIR LE PARAM", true)
            return
        }
        if (lenEntry && i in 0..8) {
            lenDigit(i + 1)
            return
        }
        if (recArmed && recArmPad > 0 && !punchMode && i in 0..8) {
            commitLen(i + 1)
            return
        }
        if (toolPage == 3) {
            val p = userPresets[i]
            if (p != null && p.gen >= 0) {
                if (keysGens.contains(p.gen)) {
                    keysGens.remove(p.gen)
                    bubble("SLOT ${i + 1} D\u00c9TACH\u00c9 DU CLAVIER")
                } else {
                    keysGens.add(p.gen)
                    bubble("CLAVIER \u2192 SLOT ${i + 1}")
                }
                refreshBlink()
                return
            }
        }
        if (heldBankPad in 1..8) {
            assignBankFx(heldBankPad - 1)
            return
        }
        if (assignArm >= 0) {
            toggleAxisBank(assignArm, i)
            return
        }
        if (punchMode) {
            setPunchCollection(i)
            return
        }
        if (paramView?.visibility == View.VISIBLE) {
            val now = System.currentTimeMillis()
            val second = lastEditBank == i && now - lastEditAt < 450
            lastEditBank = i
            lastEditAt = now
            val list = userPresets[i]?.fxOn
                ?.withIndex()?.filter { it.value }?.map { it.index }
                ?: emptyList()
            if (list.isEmpty()) {
                bubble("BANQUE VIDE")
                return
            }
            openParamPage(if (second) list.getOrElse(1) { list[0] } else list[0])
            return
        }
        if (fxHeld) {
            fxConsumed = true
            saveUserPreset(i)
            bubble("PRESET ${i + 1} SAUV\u00c9")
        } else {
            bubble("SHIFT+BANQUE = ACTIVER/\u00c9TEINDRE", true)
        }
        pad.invalidate()
        refreshPresetRow()
    }

    private fun presetLongTap(i: Int) {
        if (userPresets[i] != null) {
            openBankFxPage(i)
        } else {
            fxPickBank = i
            openFx()
            bubble("BANQUE ${i + 1} : TAPE DES EFFETS POUR LES Y METTRE", true)
        }
    }

    private fun lenDigit(n: Int) {
        if (lenFirst == 0) {
            if (n == 1) {
                lenFirst = 1
                lenHandler.postDelayed({ if (lenEntry && lenFirst == 1) commitLen(1) }, 2000)
                bubble("1_ ? (2e chiffre)")
            } else {
                commitLen(n)
            }
        } else {
            commitLen(10 + n)
        }
    }

    private fun commitLen(v: Int) {
        lenHandler.removeCallbacksAndMessages(null)
        lenEntry = false
        lenFirst = 0
        loopLenSteps = v.coerceIn(1, 19)
        padGrid.activeBank = loopLenSteps
        bubble("LONGUEUR : " + loopLenSteps + " TEMPS")
        refreshBlink()
    }

    private fun showVuOverlay(t: String) {
        bpmLcd.statusText = t
    }

    private fun arpTap() {
        if (shiftActive()) {
            if (toolPage == 2) {
                drumGrid.drumMode = (drumGrid.drumMode + 1) % 4
                engine.nativeSetKitMode(playerBank, drumGrid.drumMode == 3)
                if (drumGrid.drumMode == 3) drumGrid.kitMask = engine.nativeKitPadMask(playerBank)
                drumGrid.invalidate()
            } else {
                toolPage = 2
                showToolPage()
            }
        } else {
            if (toolPage != 0) {
                toolPage = 0
                toolsMenu.visibility = View.GONE
                showToolPage()
            } else {
                toolsMenu.visibility =
                    if (toolsMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        refreshPresetRow()
    }

    private fun refreshDrumVoicePanel() {
        if (drumVoiceSel < 0 || drumVoiceSel >= drumVoiceSlots.size) return
        val slot = drumVoiceSlots[drumVoiceSel]
        for ((i, b) in drumVoiceBtns.withIndex()) {
            b.setTextColor(if (i == drumVoiceSel) amber else silk)
        }
        val defs = fxParamDefs[slot] ?: return
        val dfl = fxParamDefaults[slot]
        for (k in 0 until 4) {
            drumVoiceLbls.getOrNull(k)?.text = defs.getOrNull(k) ?: ""
            drumVoiceSeeks.getOrNull(k)?.progress =
                (((dfl?.getOrNull(k)) ?: 0.5f) * 100f).toInt()
        }
        drumVoiceMacSeeks.getOrNull(0)?.progress = (drumDecDepth[drumVoiceSel] * 100f).toInt()
        drumVoiceMacSeeks.getOrNull(1)?.progress = (drumDriveDepth[drumVoiceSel] * 100f).toInt()
    }

    private fun toggleDrumSeqRun() {
        drumSeqRunning = !drumSeqRunning
        if (drumSeqRunning) {
            drumSeqStartMs = System.currentTimeMillis()
            drumSeqLastCol = -1
        }
        drumSeqRunBtn?.setTextColor(if (drumSeqRunning) amber else green)
        bubble("BO\u00ceTE \u00c0 RYTHME " + (if (drumSeqRunning) "START" else "STOP"))
    }

    private fun buildKeysSoundPanel() {
        val d = resources.displayMetrics.density
        keysSoundPanel = LinearLayout(this)
        keysSoundPanel.orientation = LinearLayout.VERTICAL
        keysSoundPanel.visibility = View.GONE
        keysSoundPanel.setBackgroundColor(Color.argb(210, 10, 11, 15))
        keysSoundPanel.setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        keysSoundLbl = TextView(this)
        keysSoundLbl?.setTextColor(cream)
        keysSoundLbl?.textSize = 9f
        keysSoundPanel.addView(
            keysSoundLbl,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        val bankRow = LinearLayout(this)
        bankRow.orientation = LinearLayout.HORIZONTAL
        for (i in 0 until 8) {
            val b = makeButton("B${i + 1}", silk)
            b.textSize = 9f
            b.setOnClickListener {
                if (!engine.nativeBankHasLoop(i)) {
                    bubble("B${i + 1} VIDE")
                } else if (keysBanks.contains(i)) {
                    keysBanks.remove(i)
                    if (!bankOn[i]) {
                        engine.nativeToggleBank(i)
                        bankOn[i] = true
                    }
                    bubble("B${i + 1} D\u00c9TACH\u00c9E")
                } else {
                    if (bankOn[i]) {
                        engine.nativeToggleBank(i)
                        bankOn[i] = false
                    }
                    keysBanks.add(i)
                    bubble("CLAVIER \u2192 B${i + 1}")
                }
                refreshKeysSounds()
                refreshPresetRow()
            }
            keysBankBtns.add(b)
            bankRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        keysSoundPanel.addView(
            bankRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        var gi = 0
        var engRow: LinearLayout? = null
        for ((_, effects) in fxSections) {
            for (fx in effects) {
                val code = fxCodes.getOrNull(gi) ?: ""
                gi++
                if (code.length < 2 || code[0] != 'G') continue
                val num = code.substring(1).toIntOrNull() ?: -1
                if (num !in 0..23) continue
                if (engRow == null || engRow!!.childCount == 4) {
                    engRow = LinearLayout(this)
                    engRow!!.orientation = LinearLayout.HORIZONTAL
                    keysSoundPanel.addView(
                        engRow,
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    )
                }
                val b = makeButton(fx, silk)
                b.textSize = 8f
                b.tag = num
                b.setOnClickListener {
                    val gnum = b.tag as Int
                    if (keysGens.contains(gnum)) keysGens.remove(gnum) else keysGens.add(gnum)
                    refreshKeysSounds()
                    refreshPresetRow()
                }
                keysEngineBtns.add(b)
                engRow!!.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
    }

    private fun refreshKeysSounds() {
        for ((i, b) in keysBankBtns.withIndex()) {
            b.setTextColor(if (keysBanks.contains(i)) Color.rgb(80, 170, 255) else silk)
        }
        for (b in keysEngineBtns) {
            b.setTextColor(if (keysGens.contains(b.tag as Int)) Color.rgb(80, 170, 255) else silk)
        }
        val parts = ArrayList<String>()
        for (i in 0 until 8) if (keysBanks.contains(i)) parts.add("B${i + 1}")
        for (g in keysGens) parts.add(fxNameBySlot[genSlot(g)] ?: "G${g + 1}")
        keysSoundLbl?.text = if (parts.isEmpty()) "AUCUN SON \u2014 TAP SONS POUR EN CHOISIR" else parts.joinToString(" \u00b7 ")
    }

    private fun buildAudioConsole() {
        val d = resources.displayMetrics.density
        audioConsole = FrameLayout(this)
        audioConsole.visibility = View.GONE
        audioConsole.setBackgroundColor(Color.argb(225, 8, 9, 13))
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(Color.argb(238, 12, 14, 19))
        panel.setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
        panel.setOnClickListener {}
        val titleRow = LinearLayout(this)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "AUDIO \u00b7 CONSOLE"
        title.setTextColor(cream)
        title.textSize = 12f
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(this)
        close.text = "\u00d7"
        close.textSize = 14f
        close.setTextColor(silk)
        close.isAllCaps = false
        close.minWidth = 0
        close.minimumWidth = 0
        close.setPadding(0, 0, 0, 0)
        close.setBackgroundColor(Color.TRANSPARENT)
        close.setOnClickListener { audioConsole.visibility = View.GONE }
        titleRow.addView(close, LinearLayout.LayoutParams((30 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(titleRow)
        fun colSeek(parent: LinearLayout, label: String, init: Float, onChange: (Float) -> Unit) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val lab = TextView(this)
            lab.text = label
            lab.setTextColor(silk)
            lab.textSize = 8f
            col.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.progress = (init * 100).toInt().coerceIn(0, 100)
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) onChange(p / 100f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            col.addView(sb)
            parent.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val inCol = LinearLayout(this)
        inCol.orientation = LinearLayout.VERTICAL
        val inTitle = TextView(this)
        inTitle.text = "ENTR\u00c9E"
        inTitle.setTextColor(Color.rgb(0, 255, 200))
        inTitle.textSize = 9f
        inCol.addView(inTitle)
        colSeek(inCol, "VOLUME", inVol / 2f) {
            inVol = it * 2f
            engine.nativeSetInGain(inVol)
            inKnob.setVal(inVol)
        }
        colSeek(inCol, "FILTRE LP\u2190\u2192HP", (inFilVal + 1f) / 2f) {
            inFilVal = it * 2f - 1f
            engine.nativeSetInFilter(inFilVal)
            vuMeter.inFilter = inFilVal
            bpmLcd.inFilter = inFilVal
            inFSel = if (inFilVal < -0.02f) 0 else if (inFilVal > 0.02f) 2 else 1
        }
        val outCol = LinearLayout(this)
        outCol.orientation = LinearLayout.VERTICAL
        val outTitle = TextView(this)
        outTitle.text = "SORTIE"
        outTitle.setTextColor(Color.rgb(57, 255, 20))
        outTitle.textSize = 9f
        outCol.addView(outTitle)
        colSeek(outCol, "VOLUME", masterVol / 2f) {
            masterVol = it * 2f
            engine.nativeSetMasterVolume(masterVol)
            volSel = if (masterVol < 0.75f) 0 else if (masterVol < 1.25f) 1 else if (masterVol < 1.75f) 2 else 3
        }
        colSeek(outCol, "FILTRE LP\u2190\u2192HP", (outFilVal + 1f) / 2f) {
            outFilVal = it * 2f - 1f
            engine.nativeSetMasterFilter(outFilVal)
            vuMeter.outFilter = outFilVal
            bpmLcd.outFilter = outFilVal
            outFSel = if (outFilVal < -0.02f) 0 else if (outFilVal > 0.02f) 2 else 1
        }
        val cols = LinearLayout(this)
        cols.orientation = LinearLayout.HORIZONTAL
        cols.addView(inCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        cols.addView(outCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(cols)
        val mixTitle = TextView(this)
        mixTitle.text = "MIXETTE \u00b7 SAMPLER SG1-4 \u00b7 BANQUES FX/SYNTH 1-9 \u00b7 POW"
        mixTitle.setTextColor(cream)
        mixTitle.textSize = 8f
        panel.addView(mixTitle)
        val mixRow = LinearLayout(this)
        mixRow.orientation = LinearLayout.HORIZONTAL
        fun tirette(txt: String, col: Int, init: Float, onChange: (Float) -> Unit) {
            val f = MixFaderView(this)
            f.label = txt
            f.fill = col
            f.value = init
            f.onVal = onChange
            mixRow.addView(f, LinearLayout.LayoutParams(0, (104 * d).toInt(), 1f))
        }
        for (g in 0 until 4) tirette("SG${g + 1}", Color.rgb(0, 240, 215), sampGroupVol[g]) { v ->
            sampGroupVol[g] = v
            engine.nativeSetSampGroupVol(g, v)
        }
        for (b in 0 until 9) tirette("${b + 1}", Color.rgb(255, 150, 60), fxBankLevel[b]) { v ->
            fxBankLevel[b] = v
            applyBankLevel(b)
        }
        tirette("POW", yellow, powLevel) { v -> powLevel = v }
        panel.addView(mixRow)
        fun btnRow(labels: List<String>, sink: ArrayList<Button>, onTap: (Int) -> Unit) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for ((i, lbl) in labels.withIndex()) {
                val b = makeButton(lbl, silk)
                b.textSize = 9f
                b.setOnClickListener { onTap(i) }
                sink.add(b)
                row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            panel.addView(row)
        }
        btnRow(listOf("TRANSP", "INSTRU", "VOIX", "BBOX"), consoleInProfileBtns) {
            inProfileSel = it
            engine.nativeSetInProfile(it)
            refreshConsole()
        }
        btnRow(listOf("COMP OFF", "DOUX", "PUNCH", "DUR"), consoleCompBtns) {
            masterCompSel = it
            engine.nativeSetMasterComp(it)
            refreshConsole()
        }
        btnRow(listOf("EQ OFF", "EQ ON"), consoleEqBtns) {
            masterEqOn = it == 1
            engine.nativeSetMasterEq(masterEqOn)
            refreshConsole()
        }
        val consW = min((440 * d).toInt(), resources.displayMetrics.widthPixels - (16 * d).toInt())
        val consScroll = ScrollView(this)
        consScroll.addView(panel, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        audioConsole.addView(
            consScroll,
            FrameLayout.LayoutParams(consW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        audioConsole.setOnClickListener { audioConsole.visibility = View.GONE }
        padPane.addView(
            audioConsole,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun refreshConsole() {
        for ((i, b) in consoleInProfileBtns.withIndex()) b.setTextColor(if (i == inProfileSel) amber else silk)
        for ((i, b) in consoleCompBtns.withIndex()) b.setTextColor(if (i == masterCompSel) amber else silk)
        for ((i, b) in consoleEqBtns.withIndex()) b.setTextColor(if ((i == 1) == masterEqOn) amber else silk)
    }

    private fun buildTimeSigConsole() {
        val d = resources.displayMetrics.density
        timeSigConsole = FrameLayout(this)
        timeSigConsole.visibility = View.GONE
        timeSigConsole.setBackgroundColor(Color.argb(225, 8, 9, 13))
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(Color.argb(238, 12, 14, 19))
        panel.setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
        panel.setOnClickListener {}
        panel.setOnTouchListener { _, _ ->
            scheduleTimeSigHide()
            false
        }
        val titleRow = LinearLayout(this)
        titleRow.orientation = LinearLayout.HORIZONTAL
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "MESURE \u00b7 TEMPO"
        title.setTextColor(cream)
        title.textSize = 12f
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(this)
        close.text = "\u00d7"
        close.textSize = 14f
        close.setTextColor(silk)
        close.isAllCaps = false
        close.minWidth = 0
        close.minimumWidth = 0
        close.setPadding(0, 0, 0, 0)
        close.setBackgroundColor(Color.TRANSPARENT)
        close.setOnClickListener {
            timeSigHandler.removeCallbacks(timeSigHide)
            timeSigConsole.visibility = View.GONE
        }
        titleRow.addView(close, LinearLayout.LayoutParams((30 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(titleRow)
        val lab = TextView(this)
        lab.text = "SIGNATURE (TEMPS PAR MESURE)"
        lab.setTextColor(silk)
        lab.textSize = 8f
        panel.addView(lab)
        val sigRow = LinearLayout(this)
        for ((i, lbl) in listOf("2/4", "3/4", "4/4", "5/4", "6/4", "7/4").withIndex()) {
            val b = makeButton(lbl, silk)
            b.setOnClickListener {
                applyTimeSig(i + 2)
                refreshTimeSig()
                scheduleTimeSigHide()
            }
            menuTimeSigButtons.add(b)
            sigRow.addView(b, wrapParams((4 * d).toInt()))
        }
        panel.addView(sigRow)
        val hintLab = TextView(this)
        hintLab.text = "TRIO = 3/4 \u00b7 LA LONGUEUR DES LOOPS PASSE A TEMPS \u00d7 4 PAS"
        hintLab.setTextColor(silk)
        hintLab.textSize = 7f
        panel.addView(hintLab)
        timeSigConsole.addView(
            panel,
            FrameLayout.LayoutParams((250 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        timeSigConsole.setOnClickListener { timeSigConsole.visibility = View.GONE }
        padPane.addView(
            timeSigConsole,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun applyTimeSig(beats: Int) {
        timeSigBeats = beats
        loopLenSteps = beats * 4
        for (b in 0 until 8) {
            bankSteps[b] = loopLenSteps
            engine.nativeSetStepCount(b, loopLenSteps)
        }
        bpmLcd.statusText = "MESURE $beats/4"
        saveUiPrefs()
    }

    private fun scheduleTimeSigHide() {
        timeSigHandler.removeCallbacks(timeSigHide)
        timeSigHandler.postDelayed(timeSigHide, 3000L)
    }

    private fun refreshTimeSig() {
        for ((i, b) in menuTimeSigButtons.withIndex()) {
            b.setTextColor(if (i + 2 == timeSigBeats) amber else silk)
        }
    }

    private fun toggleTimeSigConsole() {
        if (this::timeSigConsole.isInitialized) {
            if (timeSigConsole.visibility == View.VISIBLE) timeSigConsole.visibility = View.GONE
            else {
                refreshTimeSig()
                timeSigConsole.visibility = View.VISIBLE
                bubble("SIGNATURE TEMPO")
                scheduleTimeSigHide()
            }
        }
    }

    private fun applyBankLevel(i: Int) {
        val p = userPresets.getOrNull(i) ?: return
        if (p.gen >= 0) {
            if (genIdx == p.gen) {
                val slot = genSlot(p.gen)
                fxParamVals[slot][3] = fxBankLevel[i]
                engine.nativeSetFxParam(slot, 3, fxBankLevel[i])
            }
        } else {
            for (f in 0 until 51) {
                if (p.fxOn.size > f && p.fxOn[f] && fxActive[f]) engine.nativeSetFxWet(f, fxBankLevel[i])
            }
        }
    }

    private inner class MixFaderView(context: Context) : View(context) {
        var value = 1f
        var label = ""
        var fill = Color.rgb(255, 150, 60)
        var onVal: ((Float) -> Unit)? = null
        private val fp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: android.graphics.Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            fp.style = android.graphics.Paint.Style.FILL
            fp.color = Color.rgb(20, 22, 28)
            c.drawRoundRect(0f, 0f, w, h, 6f, 6f, fp)
            val top = h * 0.16f
            val bot = h * 0.94f
            val railL = w * 0.30f
            val railR = w * 0.70f
            fp.color = Color.rgb(12, 13, 17)
            c.drawRoundRect(railL, top, railR, bot, 4f, 4f, fp)
            val hy = top + (bot - top) * (1f - value)
            fp.color = fill
            c.drawRoundRect(railL, hy, railR, bot, 4f, 4f, fp)
            fp.color = Color.rgb(236, 224, 196)
            c.drawCircle(w / 2f, hy, w * 0.24f, fp)
            fp.color = Color.rgb(165, 172, 185)
            fp.textSize = (w * 0.30f).coerceAtMost(13f)
            fp.textAlign = android.graphics.Paint.Align.CENTER
            c.drawText(label, w / 2f, h * 0.11f, fp)
            c.drawText((value * 100f).toInt().toString(), w / 2f, bot + h * 0.045f, fp)
            fp.textAlign = android.graphics.Paint.Align.LEFT
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val top = height * 0.16f
            val bot = height * 0.94f
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

    private fun toggleAudioConsole() {
        if (this::audioConsole.isInitialized) {
            if (audioConsole.visibility == View.VISIBLE) audioConsole.visibility = View.GONE
            else {
                refreshConsole()
                audioConsole.visibility = View.VISIBLE
                bubble("AUDIO CONSOLE")
            }
        }
    }

    private fun showToolPage() {
        arpOverlay.visibility = if (toolPage == 1) View.VISIBLE else View.GONE
        drumGrid.visibility = if (toolPage == 2) View.VISIBLE else View.GONE
        if (toolPage == 2) {
            engine.nativeSetKitMode(playerBank, drumGrid.drumMode == 3)
            drumGrid.kitMask = engine.nativeKitPadMask(playerBank)
        }
        keysWrap.visibility = if (toolPage == 3) View.VISIBLE else View.GONE
        drumSeqWrap.visibility = if (toolPage == 4) View.VISIBLE else View.GONE
        if (toolPage != 4) drumSeqRunning = false
        if (toolPage == 3) refreshKeysSounds()
        if (toolPage != 3) {
            miniKeys.releaseAll()
            keysHeld.clear()
            for (b in keysBanks) {
                if (!bankOn[b]) {
                    engine.nativeToggleBank(b)
                    bankOn[b] = true
                }
            }
            keysBanks.clear()
            keysGens.clear()
        }
        refreshPresetRow()
        refreshBlink()
    }

    private fun scheduleFuseToggle() {
        fusePending?.let { fuseHandler.removeCallbacks(it) }
        fusePending = null
        if (!fHeld || !zHeld || recArmed || fxOpen || axisFlowStage != 0) return
        val r = Runnable {
            fusePending = null
            if (!fHeld || !zHeld) return@Runnable
            lastFuseAt = System.currentTimeMillis()
            fzFusedAt = System.currentTimeMillis()
            pad.fuseToggle()
            bubble(if (pad.padMerged()) "POINTS LI\u00c9S" else "POINTS D\u00c9LI\u00c9S")
        }
        fusePending = r
        fuseHandler.postDelayed(r, fuseDelayMs.toLong())
    }

    private fun keyEvent(k: Int, down: Boolean) {
        if (down) {
            for (b in keysBanks) engine.nativeDrumHit(b, -1, 1, (k - 12).toFloat())
        }
        if (keysGens.isEmpty()) return
        val drumGens = keysGens.any {
            it == 5 || it == 6 || it == 7 || it == 16 || it == 20 || it == 22 || it == 23
        }
        if (down && drumGens) {
            for (g in keysGens) {
                if (g == 5 || g == 6 || g == 7 || g == 16 || g == 20 || g == 22 || g == 23) {
                    engine.nativeGenHit(g, (k % 12) / 11.99f, 0.5f)
                }
            }
        }
        val melodic = keysGens.any {
            !(it == 5 || it == 6 || it == 7 || it == 16 || it == 20 || it == 22 || it == 23)
        }
        if (melodic) {
            if (down) keysHeld.add(k) else keysHeld.remove(k)
            if (keysHeld.isNotEmpty()) {
                val top = keysHeld.max()
                engine.nativeSetGenOct(keysOctShift + top / 25)
                val target = (top % 25) / 25f
                if (keysGlide > 0.01f) {
                    if (keysGlideTarget < 0f) keysGlideCur = lastAxX
                    keysGlideTarget = target
                    keysGlideHandler.removeCallbacks(keysGlideTick)
                    keysGlideHandler.post(keysGlideTick)
                } else {
                    keysGlideTarget = -1f
                    engine.nativeSetAx(target)
                }
                engine.nativeSetGenTouch(true)
            } else {
                keysGlideTarget = -1f
                engine.nativeSetGenTouch(false)
            }
        }
    }

    private val keysGlideTick: Runnable = object : Runnable {
        override fun run() {
            if (keysGlideTarget < 0f) return
            val d = keysGlideTarget - keysGlideCur
            if (Math.abs(d) < 0.004f) {
                keysGlideCur = keysGlideTarget
                engine.nativeSetAx(keysGlideTarget)
                keysGlideTarget = -1f
                return
            }
            keysGlideCur += d * (0.08f + keysGlide * 0.35f)
            engine.nativeSetAx(keysGlideCur)
            keysGlideHandler.postDelayed(this, 30)
        }
    }

    private fun refreshKeysOct() {
        for (ob in keysOctBtns) {
            ob.setTextColor(if ((ob.tag as? Int) == keysOctShift) amber else silk)
        }
    }

    private fun refreshPresetRow() {
        for (i in 0 until 9) {
            val b = presetBtns.getOrNull(i) ?: continue
            val lit = punchMode && punchCol == i || pad.activePreset == i
            b.setTextColor(if (lit) Color.rgb(14, 16, 20) else if (userPresets[i] != null) cream else silk)
            val assigned = toolPage == 3 &&
                (keysBanks.contains(i) || userPresets[i]?.gen?.let { keysGens.contains(it) } == true)
            val bg = b.background as? ThemeButtonDrawable
            if (bg != null) {
                val want = when {
                    lit -> amber
                    assigned -> Color.rgb(30, 90, 160)
                    else -> 0
                }
                if (bg.fillColor != want) {
                    bg.fillColor = want
                    bg.invalidateSelf()
                }
            }
        }
        arpBtn?.setTextColor(if (toolPage != 0 || toolsMenu.visibility == View.VISIBLE) Color.rgb(80, 170, 255) else if (arpOn) amber else silk)
        btnPunch?.setTextColor(if (punchMode) amber else yellow)
        padGrid.bankHas = BooleanArray(8) { userPresets[it] != null || engine.nativeBankHasLoop(it) }
    }

    private fun swapBankUi(a: Int, b: Int) {
        var t = bankOn[a]; bankOn[a] = bankOn[b]; bankOn[b] = t
        var ti = bankSlotMask[a]; bankSlotMask[a] = bankSlotMask[b]; bankSlotMask[b] = ti
        ti = bankSlotRev[a]; bankSlotRev[a] = bankSlotRev[b]; bankSlotRev[b] = ti
        ti = bankSlotN[a]; bankSlotN[a] = bankSlotN[b]; bankSlotN[b] = ti
        var tf = bankSlotVol[a]; bankSlotVol[a] = bankSlotVol[b]; bankSlotVol[b] = tf
        tf = bankSlotProb[a]; bankSlotProb[a] = bankSlotProb[b]; bankSlotProb[b] = tf
        tf = bankSlotPitch[a]; bankSlotPitch[a] = bankSlotPitch[b]; bankSlotPitch[b] = tf
        tf = bankSlotPan[a]; bankSlotPan[a] = bankSlotPan[b]; bankSlotPan[b] = tf
        tf = bankSlotToff[a]; bankSlotToff[a] = bankSlotToff[b]; bankSlotToff[b] = tf
        engine.nativeSetBankSlots(a, bankSlotMask[a])
        engine.nativeSetBankSlots(b, bankSlotMask[b])
        engine.nativeSetBankSlotRev(a, bankSlotRev[a])
        engine.nativeSetBankSlotRev(b, bankSlotRev[b])
        refreshPresetRow()
    }

    private fun toggleInMute() {
        if (inVol > 0.01f) {
            inMuteSaved = inVol
            inVol = 0f
            engine.nativeSetInGain(0f)
            inKnob.setVal(0f)
            showVuOverlay("IN MUET")
            bubble("LINE-IN MUET (2 swipes pour remettre)")
        } else {
            inVol = if (inMuteSaved > 0.01f) inMuteSaved else 1f
            engine.nativeSetInGain(inVol)
            inKnob.setVal(inVol)
            showVuOverlay("IN " + ((inVol * 50).toInt().coerceIn(0, 100)).toString() + "%")
            bubble("LINE-IN ACTIF")
        }
    }

    private fun requestMic() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            restartEngineMic(true)
        } else {
            pendingMicRequest = true
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 4242)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4242 && pendingMicRequest) {
            pendingMicRequest = false
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                restartEngineMic(true)
            } else {
                bubble("PERMISSION MICRO REFUS\u00c9E")
            }
        }
    }

    private fun restartEngineMic(useMic: Boolean) {
        if (playing) {
            engine.nativeStop()
            playing = false
        }
        playing = engine.nativeStart(useMic)
        micOn = useMic && playing
        bubble(if (micOn) "MIC ON" else if (useMic) "MIC INDISPONIBLE" else "MIC OFF")
        refreshMenu()
    }

    private fun startAppCapture() {
        if (appCaptureOn) return
        if (Build.VERSION.SDK_INT < 29) {
            bubble("CAPTURE APPS : ANDROID 10 REQUIS")
            return
        }
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), reqCapture)
    }

    private fun beginCapture(resultCode: Int, data: Intent?) {
        if (Build.VERSION.SDK_INT < 29) return
        val proj = (getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .getMediaProjection(resultCode, data ?: return) ?: return
        val minBuf = AudioRecord.getMinBufferSize(48000, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBuf <= 0) {
            bubble("CAPTURE INDISPONIBLE")
            return
        }
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(48000)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val cfg = AudioPlaybackCaptureConfiguration.Builder(proj)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        val rec = try {
            AudioRecord.Builder()
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(minBuf * 2)
                .setAudioPlaybackCaptureConfig(cfg)
                .build()
        } catch (e: Exception) {
            bubble("CAPTURE REFUS\u00c9E")
            return
        }
        captureProj = proj
        captureRec = rec
        appCaptureOn = true
        engine.nativeSetCaptureMode(true)
        rec.startRecording()
        captureRun = true
        bubble("CAPTURE APPS ON (KOALA, MUSIQUE...)")
        thread {
            val buf = FloatArray(2048)
            while (captureRun) {
                val n = rec.read(buf, 0, 2048, AudioRecord.READ_BLOCKING)
                if (n > 0) engine.nativePushCapture(buf, n)
            }
        }
        refreshMenu()
    }

    private fun stopAppCapture() {
        if (!appCaptureOn) return
        captureRun = false
        appCaptureOn = false
        engine.nativeSetCaptureMode(false)
        try { captureRec?.stop() } catch (e: Exception) {}
        captureRec?.release()
        captureRec = null
        captureProj?.stop()
        captureProj = null
        bubble("CAPTURE APPS OFF")
        refreshMenu()
    }

    private fun openSamplePage(bank: Int) {
        val pv = paramView ?: return
        sampleEditBank = bank
        paramFx = -2
        playerBank = bank
        engine.nativeSetPlayerBank(bank)
        padGrid.sampMask = bankSlotMask[bank]
        padGrid.sampN = bankSlotN[bank]
        padGrid.slotVol = bankSlotVol[bank]
        padGrid.slotProb = bankSlotProb[bank]
        padGrid.slotPitch = bankSlotPitch[bank]
        padGrid.slotPan = bankSlotPan[bank]
        padGrid.slotToff = bankSlotToff[bank]
        padGrid.seqMode = sampleParamVals[bank][10] >= 0.5f
        padGrid.sampRev = bankSlotRev[bank]
        padGrid.sampOffset = sampleParamVals[bank][8]
        padGrid.sampEdit = true
        updateSampModeLabel()
        refreshBankFx()
        pv.setContent(sampleParamDefs, sampleParamVals[bank], sampleParamAssign)
        pv.accentColor = bankCols[bank]
        pv.bankIdx = bank
        pv.masterOn = false
        pv.snapMode = 0
        pv.mapAvailable = true
        pv.mapMode = false
        pv.mapVals = arrayOf(
            bankSlotVol[bank], bankSlotProb[bank], bankSlotPitch[bank], bankSlotPan[bank], bankSlotToff[bank]
        )
        pv.mapMask = bankSlotMask[bank]
        pv.mapCols = bankSlotN[bank].coerceIn(1, 16)
        pv.visibility = View.VISIBLE
    }

    private fun clearPadData(idx: Int) {
        if (idx !in snapPads.indices) return
        if (motionPlaying == idx) stopMotionPlayback()
        snapPads[idx] = null
        if (activeZu == idx) activeZu = -1
        if (activeLu == idx) activeLu = -1
        refreshSnapGrid()
        bubble("PAD ${idx + 1} EFFAC\u00c9")
    }

    private fun clearMotion(idx: Int) {
        val s = snapPads.getOrNull(idx)
        if (s == null) {
            bubble("PAD VIDE")
            return
        }
        if (motionPlaying == idx) stopMotionPlayback()
        snapPads[idx] = s.copy(motA = null, motB = null, motLen = 0)
        refreshSnapGrid()
        bubble("MOTION EFFAC\u00c9E")
    }

    private fun halveMotion(idx: Int) {
        val s = snapPads.getOrNull(idx)
        if (s == null || s.motLen <= 0) {
            bubble("PAS DE MOTION SUR CE PAD")
            return
        }
        val nl = ((s.motLen + 1) / 2).coerceAtLeast(1)
        snapPads[idx] = s.copy(motLen = nl)
        bubble("MOTION \u00f72 : $nl TEMPS")
    }

    private val motionModeNames = listOf("NORMAL", "CHAOS", "INTENSIT\u00c9")

    private fun openMotionPage(idx: Int) {
        val s = snapPads.getOrNull(idx)
        if (s == null || ((s.motA == null || s.motA!!.size < 4) && (s.motB == null || s.motB!!.size < 4))) {
            bubble("PAD ${idx + 1} SANS MOTION")
            return
        }
        val dd = resources.displayMetrics.density
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val pad = (10 * dd).toInt()
        box.setPadding(pad, pad, pad, pad)
        val title = TextView(this)
        title.text = "MOTION \u00b7 PAD ${idx + 1}"
        title.setTextColor(amber)
        title.textSize = 13f
        title.typeface = markerFont(this)
        box.addView(title)

        fun upd(ns: Snap) {
            snapPads[idx] = ns
        }

        val lenRow = LinearLayout(this)
        val lenB = makeButton("LONGUEUR : ${s.motLen.coerceAtLeast(1)} TEMPS", silk)
        lenB.setOnClickListener {
            val cur = snapPads[idx]!!.motLen.coerceAtLeast(1)
            upd(snapPads[idx]!!.copy(motLen = cur % 19 + 1))
            lenB.text = "LONGUEUR : ${snapPads[idx]!!.motLen} TEMPS"
        }
        lenRow.addView(lenB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val dblB = makeButton("\u00d72", silk)
        dblB.setOnClickListener { doubleMotion(idx); lenB.text = "LONGUEUR : ${snapPads[idx]!!.motLen.coerceAtLeast(1)} TEMPS" }
        lenRow.addView(dblB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val hlvB = makeButton("\u00f72", silk)
        hlvB.setOnClickListener { halveMotion(idx); lenB.text = "LONGUEUR : ${snapPads[idx]!!.motLen.coerceAtLeast(1)} TEMPS" }
        lenRow.addView(hlvB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(lenRow)

        val modeB = makeButton("MODE : ${motionModeNames[snapPads[idx]!!.motMode.coerceIn(0, 2)]}", turquoise)
        modeB.setOnClickListener {
            val nm = (snapPads[idx]!!.motMode + 1) % 3
            upd(snapPads[idx]!!.copy(motMode = nm))
            modeB.text = "MODE : ${motionModeNames[nm]}"
        }
        box.addView(modeB)

        val amtLab = TextView(this)
        amtLab.text = "FORCE DU HASARD : ${(snapPads[idx]!!.motAmt * 100).toInt()}%"
        amtLab.setTextColor(silk)
        amtLab.textSize = 9f
        box.addView(amtLab)
        val amtSb = SeekBar(this)
        amtSb.max = 100
        amtSb.progress = (snapPads[idx]!!.motAmt * 100f).toInt()
        amtSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, fromUser: Boolean) {
                if (fromUser) {
                    upd(snapPads[idx]!!.copy(motAmt = v / 100f))
                    amtLab.text = "FORCE DU HASARD : $v%"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        box.addView(amtSb, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (26 * dd).toInt()))

        val dlg = android.app.AlertDialog.Builder(this).setView(box).create()

        val btnRow = LinearLayout(this)
        val clrB = makeButton("EFFACER", red)
        clrB.setOnClickListener { clearMotion(idx); dlg.dismiss() }
        btnRow.addView(clrB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val okB = makeButton("FERMER", amber)
        okB.setOnClickListener { dlg.dismiss() }
        btnRow.addView(okB, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(btnRow)

        dlg.show()
    }

    private fun doubleMotion(idx: Int) {
        val s = snapPads.getOrNull(idx)
        if (s == null || s.motLen <= 0) {
            bubble("PAS DE MOTION SUR CE PAD")
            return
        }
        val nl = (s.motLen * 2).coerceAtMost(19)
        snapPads[idx] = s.copy(motLen = nl)
        bubble("MOTION \u00d72 : $nl TEMPS")
    }

    private fun openParamPage(fx: Int) {
        val pv = paramView ?: return
        val defs = fxParamDefs[fx] ?: return
        paramFx = fx
        if (!fxParamInit[fx]) {
            fxParamDefaults[fx]?.copyInto(fxParamVals[fx])
            fxParamInit[fx] = true
        }
        for (k in 0 until 8) engine.nativeSetFxParam(fx, k, fxParamVals[fx][k])
        pv.setContent(defs, fxParamVals[fx], fxParamAssign[fx])
        pv.accentColor = bankCols[activeGroup]
        pv.bankIdx = activeGroup
        pv.masterOn = masterFx.contains(fx)
        pv.snapMode = when (snapFxOverride[fx]) { null -> 0; true -> 1; else -> 2 }
        pv.mapAvailable = false
        pv.mapMode = false
        pv.visibility = View.VISIBLE
    }

    private fun closeParamPage() {
        paramView?.visibility = View.GONE
        paramView?.mapMode = false
        paramFx = -1
        padGrid.sampEdit = false
        pad.modeLabel = ""
    }

    private var xyHelpOpt = true
    private var macroMode = false
    private val macroFaderTargets = Array(6) { ArrayList<Triple<Int, Int, Boolean>>() }
    private var macroFlowFader = -1
    private var macroFlowStage = 0
    private var fxDownY = 0f
    private var fxSwiped = false
    private val menuHelpButtons = ArrayList<Button>()
    private val menuFontButtons = ArrayList<Button>()

    private fun invalidateTree(v: View) {
        v.invalidate()
        if (v is ViewGroup) for (i in 0 until v.childCount) invalidateTree(v.getChildAt(i))
    }

    private fun applyFontTree(v: View) {
        if (v is TextView && v.textSize >= 14f * resources.displayMetrics.scaledDensity)
            v.typeface = markerFont(this)
        v.invalidate()
        if (v is ViewGroup) for (i in 0 until v.childCount) applyFontTree(v.getChildAt(i))
    }

    private fun updateXyHint() {
        if (!xyHelpOpt) {
            if (bpmLcd.hintText.isNotEmpty()) bpmLcd.hintText = ""
            return
        }
        val txt = when {
            menuOverlay.visibility == View.VISIBLE -> "MENU"
            fxOverlay.visibility == View.VISIBLE -> "FX RACK \u00b7 G${activeGroup + 1}"
            paramFx == -2 -> "SAMPLE B${sampleEditBank + 1}"
            paramFx >= 0 && paramView?.visibility == View.VISIBLE -> "\u00c9DITION PARAMS \u00b7 G${activeGroup + 1}"
            recActive -> "REC B${recArmPad}..." + if (recMotion) " MOTION" else ""
            lenEntry -> "LONGUEUR ? PADS 1-9"
            recArmed -> "REC \u2192 DESTINATION"
            toolsMenu.visibility == View.VISIBLE -> "TOOLS"
            toolPage == 1 -> "TOOLS \u00b7 GRID"
            toolPage == 2 -> "TOOLS \u00b7 ARP"
            toolPage == 3 -> "TOOLS \u00b7 CLAVIER"
            toolPage == 4 -> "BO\u00ceTE \u00c0 RYTHME"
            punchMode -> "POW"
            arpOn -> "ARP"
            shiftActive() -> "SHIFT"
            else -> "SC\u00c8NE G${activeGroup + 1}"
        }
        if (txt != bpmLcd.hintText) bpmLcd.hintText = txt
    }

    private fun updateSampModeLabel() {
        if (!padGrid.sampEdit) return
        val one = sampleParamVals[sampleEditBank][10] >= 0.5f
        pad.modeLabel = "B${sampleEditBank + 1} \u00b7 " + if (one) "ONE-SHOT" else "LOOP"
        pad.modeLabelColor = if (one) Color.rgb(255, 150, 60) else Color.rgb(178, 102, 255)
    }

    private fun armAssign(axis: Int) {
        assignArm = axis
        bubble("AXE " + axisNames[axis] + " : CHOISIR BANQUES (SHIFT+BANQUE = COURBE, FX = SORTIR)", true)
        startAssignBlink()
    }

    private fun exitAssignMode() {
        if (assignArm < 0) return
        assignArm = -1
        stopAssignBlink()
    }

    private val assignBlinkRunnable = object : Runnable {
        override fun run() {
            if (assignArm < 0) return
            assignBlinkOn = !assignBlinkOn
            val b = if (assignArm == 0) btnF else btnZ
            b.setTextColor(Color.rgb(14, 16, 20))
            b.background = axisSolidBg(if (assignArm == 0) yellow else pink, !assignBlinkOn)
            motionHandler.postDelayed(this, 280)
        }
    }

    private fun startAssignBlink() {
        motionHandler.removeCallbacks(assignBlinkRunnable)
        motionHandler.post(assignBlinkRunnable)
    }

    private fun stopAssignBlink() {
        motionHandler.removeCallbacks(assignBlinkRunnable)
        assignBlinkOn = false
        updateAxisButtons()
    }

    private fun pushAxisMulti(axis: Int) {
        val list = axisMulti[axis]
        val n = min(list.size, 8)
        val idxs = FloatArray(n)
        val modes = FloatArray(n)
        for (i in 0 until n) {
            idxs[i] = list[i].toFloat()
            modes[i] = axisCurve[axis][((list[i] - 8) / 8).coerceIn(0, 9)].toFloat()
        }
        engine.nativeSetAxisTargets(axis, idxs, modes)
    }

    private fun toggleDrumAxis(axis: Int) {
        val an = axisNames[axis]
        val cur = axisMulti[axis]
        val idxs = (92..96).toList()
        val had = idxs.all { cur.contains(it) }
        if (had) {
            cur.removeAll(idxs)
            bubble("MACROS RYTHME \u2192 $an OFF")
        } else {
            for (t in idxs) if (!cur.contains(t) && cur.size < 8) cur.add(t)
            bubble("MACROS RYTHME \u2192 $an ON (" + cur.size + ")")
        }
        pushAxisMulti(axis)
    }

    private fun toggleAxisBank(axis: Int, bank: Int) {
        val an = axisNames[axis]
        if (shiftActive()) {
            axisCurve[axis][bank] = (axisCurve[axis][bank] + 1) % 4
            bubble("BANQUE ${bank + 1} \u2192 $an : " + curveNames[axisCurve[axis][bank]])
            pushAxisMulti(axis)
            return
        }
        val p = userPresets[bank]
        if (p == null) {
            bubble("BANQUE ${bank + 1} VIDE")
            return
        }
        val idxs = ArrayList<Int>()
        for (f in 0 until min(84, p.fxOn.size)) {
            if (!p.fxOn[f]) continue
            for (k in 0 until 8) if (fxParamAssign[f][k] == 1) idxs.add(8 + f * 8 + k)
        }
        if (idxs.isEmpty()) {
            bubble("AUCUN PARAM XY DANS LA BANQUE ${bank + 1}")
            return
        }
        val cur = axisMulti[axis]
        val had = idxs.all { cur.contains(it) }
        if (had) {
            cur.removeAll(idxs)
            bubble("BANQUE ${bank + 1} \u2192 $an OFF")
        } else {
            for (t in idxs) if (!cur.contains(t) && cur.size < 8) cur.add(t)
            if (cur.size >= 8) bubble("MAX 8 CIBLES PAR AXE")
            bubble("BANQUE ${bank + 1} \u2192 $an ON (" + cur.size + ")")
        }
        pushAxisMulti(axis)
    }

    private fun cycleParamAssign(k: Int) {
        val fx = paramFx
        val isSamp = fx == -2
        if (fx < 0 && !isSamp) return
        val assigns = if (isSamp) sampleParamAssign else fxParamAssign[fx]
        val prev = assigns[k]
        val next = (prev + 1) % 4
        assigns[k] = next
        val target = if (isSamp) (if (k == 9) 8 + 74 * 8 + 1 else if (k == 10) 8 + 74 * 8 + 2 else 8 + (if (k < 6) 63 + k else 66 + k) * 8) else 8 + fx * 8 + k
        if (prev == 1) engine.nativeSetAssign(0, assignXIdx)
        if (prev == 2 || prev == 3) {
            val l = prev - 2
            if (lfoTargets[l] == target) {
                lfoTargets[l] = -1
                pushLfo(l)
            }
        }
        when (next) {
            1 -> engine.nativeSetAssign(0, target)
            2, 3 -> {
                val l = next - 2
                lfoTargets[l] = target
                pushLfo(l)
            }
        }
        paramView?.refresh()
    }

    private fun applySendCombo(bank: Int, mode: Int) {
        if (bank !in 0..7) return
        val both = if (mode == 1) zHeld else fHeld
        val target = if (both) !sendComboStart else (mode == 1)
        bankFxSend[bank] = target
        engine.nativeSetBankFxSend(bank, target)
        engine.nativeCancelRec()
        engine.nativeUndo()
        sendPadBank = -1
        bubble("FX SEND B${bank + 1} " + (if (target) "ON" else "OFF"))
    }

    private val punchCombo = mapOf(0 to intArrayOf(8, 14))

    private fun punchApply(zone: Int, on: Boolean) {
        val fx = punchFx[punchCol][zone]
        val pr = punchParam[punchCol][zone]
        if (!(snapHold && snapTransp)) {
            if (fx >= 0) {
                engine.nativeSetFx(fx, on)
                val slot = fxSlot(fx)
                for (k in 0 until 8) {
                    engine.nativeSetFxParam(slot, k, fxParamVals[slot][k])
                }
                if (on) engine.nativeSetFxWet(fx, powLevel)
                val pk = punchParam[punchCol][zone]
                if (pk >= 0) engine.nativeSetFxParam(slot, pk, punchVal[punchCol][zone])
            }
            else if (pr >= 0) engine.nativeSetParam(pr, punchVal[punchCol][zone], on)
            val combo = punchCombo[punchCol * 9 + zone]
            if (combo != null) {
                for (cfx in combo) {
                    engine.nativeSetFx(cfx, on)
                    if (on) engine.nativeSetFxWet(cfx, powLevel)
                }
                if (on) {
                    engine.nativeSetFilterType(1)
                    engine.nativeSetParam(0, 0.22f, true)
                } else {
                    engine.nativeSetFilterType(activeFilter)
                    engine.nativeSetParam(0, 0f, false)
                }
            }
        }
        punchLatched[zone] = on
        punchView?.setActive(zone, on)
    }

    private val fxFlatNames = fxSections.flatMap { it.second }

    private fun openPunchZoneEditor(zone: Int) {
        val c = punchCol
        val dd = resources.displayMetrics.density
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val padI = (10 * dd).toInt()
        box.setPadding(padI, padI, padI, padI)
        val title = TextView(this)
        title.text = "PUNCH \u00b7 ${punchLabels[c][zone]} \u00b7 G${c + 1}"
        title.setTextColor(amber)
        title.textSize = 13f
        title.typeface = markerFont(this)
        box.addView(title)

        fun fxName(id: Int) = if (id < 0) "PARAM SEUL" else (fxFlatNames.getOrNull(id) ?: "FX $id")

        val fxB = makeButton("FX : ${fxName(punchFx[c][zone])}", silk)
        fxB.setOnClickListener {
            punchFx[c][zone] = if (punchFx[c][zone] >= 45) -1 else punchFx[c][zone] + 1
            fxB.text = "FX : ${fxName(punchFx[c][zone])}"
            if (punchLatched[zone]) punchApply(zone, true)
        }
        box.addView(fxB)

        val prB = makeButton("PARAM : ${if (punchParam[c][zone] < 0) "AUCUN" else punchParam[c][zone].toString()}", silk)
        prB.setOnClickListener {
            punchParam[c][zone] = if (punchParam[c][zone] >= 7) -1 else punchParam[c][zone] + 1
            prB.text = "PARAM : ${if (punchParam[c][zone] < 0) "AUCUN" else punchParam[c][zone].toString()}"
            if (punchLatched[zone]) punchApply(zone, true)
        }
        box.addView(prB)

        val vRow = LinearLayout(this)
        val vLbl = TextView(this)
        vLbl.text = "VALEUR : ${(punchVal[c][zone] * 100).toInt()}%"
        vLbl.setTextColor(silk)
        vLbl.typeface = markerFont(this)
        vRow.addView(vLbl, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(vRow)
        val seek = SeekBar(this)
        seek.progress = (punchVal[c][zone] * 100).toInt().coerceIn(0, 100)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                punchVal[c][zone] = p / 100f
                vLbl.text = "VALEUR : $p%"
                if (punchLatched[zone]) punchApply(zone, true)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(seek)

        val closeB = makeButton("FERMER", silk)
        closeB.setOnClickListener { punchEditorDlg?.dismiss() }
        box.addView(closeB)
        punchEditorDlg = android.app.AlertDialog.Builder(this).setView(box).show()
    }

    private fun applyEnv() {
        engine.nativeSetEnvFollow(envOn && envTarget >= 0, envTarget, envAmt, envGain)
    }

    private fun envTargetName(): String =
        if (envTarget < 0) "RIEN" else if (envTarget < 8) assignNames[envTarget] else linkParamName(envTarget)

    private fun openEnvDialog() {
        val dd = resources.displayMetrics.density
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val padI = (10 * dd).toInt()
        box.setPadding(padI, padI, padI, padI)
        val title = TextView(this)
        title.text = "ENVELOPPE FOLLOWER"
        title.setTextColor(Color.rgb(120, 220, 120))
        title.textSize = 13f
        title.typeface = markerFont(this)
        box.addView(title)
        val hint = TextView(this)
        hint.text = "LE NIVEAU D'ENTR\u00c9E PILOTE UN PARAM\u00c8TRE"
        hint.setTextColor(silk)
        hint.textSize = 9f
        box.addView(hint)

        val onB = makeButton(if (envOn) "ON" else "OFF", silk)
        onB.setOnClickListener {
            envOn = !envOn
            onB.text = if (envOn) "ON" else "OFF"
            applyEnv()
        }
        box.addView(onB)

        val tgB = makeButton("CIBLE : ${envTargetName()}", silk)
        tgB.setOnClickListener {
            envTarget = when {
                envTarget < 0 -> 0
                envTarget < 7 -> envTarget + 1
                else -> -1
            }
            tgB.text = "CIBLE : ${envTargetName()}"
            applyEnv()
        }
        box.addView(tgB)

        val teachB = makeButton("APPRENDRE TIRETTE", silk)
        teachB.setOnClickListener {
            envTeach = true
            envDlg?.dismiss()
            bubble("BOUGE UNE TIRETTE...")
        }
        box.addView(teachB)

        fun seek(label: String, init: Float, max: Float, onChange: (Float) -> Unit): SeekBar {
            val lab = TextView(this)
            lab.text = "$label : ${(init * 100).toInt()}%"
            lab.setTextColor(silk)
            lab.textSize = 9f
            box.addView(lab)
            val sb = SeekBar(this)
            sb.max = 100
            sb.progress = (init / max * 100f).toInt().coerceIn(0, 100)
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val v = p / 100f * max
                        onChange(v)
                        lab.text = "$label : $p%"
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            box.addView(sb)
            return sb
        }
        seek("INTENSIT\u00c9", envAmt, 1f) { envAmt = it; applyEnv() }
        seek("SENSIBILIT\u00c9", envGain / 12f, 1f) { envGain = (it * 12f).coerceAtLeast(0.5f); applyEnv() }

        val closeB = makeButton("FERMER", silk)
        closeB.setOnClickListener { envDlg?.dismiss() }
        box.addView(closeB)
        envDlg = android.app.AlertDialog.Builder(this).setView(box).show()
    }

    private fun openLogicDialog() {
        val dd = resources.displayMetrics.density
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val padI = (10 * dd).toInt()
        box.setPadding(padI, padI, padI, padI)
        val title = TextView(this)
        title.text = "LOGIQUE MODULAIRE"
        title.setTextColor(Color.rgb(255, 140, 0))
        title.textSize = 13f
        title.typeface = markerFont(this)
        box.addView(title)
        val hint = TextView(this)
        hint.text = "2 SOURCES + 1 PORTE \u2192 UN PARAM\u00c8TRE"
        hint.setTextColor(silk)
        hint.textSize = 9f
        box.addView(hint)

        val srcNames = listOf("XY X", "XY Y", "ENTR\u00c9E")
        val gateNames = listOf("AND", "OR", "XOR", "NOT-A")

        val onB = makeButton(if (logOn) "ON" else "OFF", silk)
        onB.setOnClickListener {
            logOn = !logOn
            onB.text = if (logOn) "ON" else "OFF"
        }
        box.addView(onB)

        val saB = makeButton("SRC A : ${srcNames[logSrcA]}", silk)
        saB.setOnClickListener {
            logSrcA = (logSrcA + 1) % 3
            saB.text = "SRC A : ${srcNames[logSrcA]}"
        }
        box.addView(saB)
        val sbB = makeButton("SRC B : ${srcNames[logSrcB]}", silk)
        sbB.setOnClickListener {
            logSrcB = (logSrcB + 1) % 3
            sbB.text = "SRC B : ${srcNames[logSrcB]}"
        }
        box.addView(sbB)
        val gtB = makeButton("PORTE : ${gateNames[logGate]}", silk)
        gtB.setOnClickListener {
            logGate = (logGate + 1) % 4
            gtB.text = "PORTE : ${gateNames[logGate]}"
        }
        box.addView(gtB)

        val tgB = makeButton("CIBLE : ${if (logTarget >= 8) linkParamName(logTarget) else "RIEN"}", silk)
        tgB.setOnClickListener { }
        box.addView(tgB)
        val teachB = makeButton("APPRENDRE TIRETTE", silk)
        teachB.setOnClickListener {
            logTeach = true
            logDlg?.dismiss()
            bubble("BOUGE UNE TIRETTE...")
        }
        box.addView(teachB)

        val lab = TextView(this)
        lab.text = "INTENSIT\u00c9 : ${(logDepth * 100).toInt()}%"
        lab.setTextColor(silk)
        lab.textSize = 9f
        box.addView(lab)
        val sb = SeekBar(this)
        sb.max = 100
        sb.progress = (logDepth * 100).toInt()
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    logDepth = p / 100f
                    lab.text = "INTENSIT\u00c9 : $p%"
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(sb)

        val closeB = makeButton("FERMER", silk)
        closeB.setOnClickListener { logDlg?.dismiss() }
        box.addView(closeB)
        logDlg = android.app.AlertDialog.Builder(this).setView(box).show()
    }

    private fun buildPatchRows(): List<Triple<String, String, Int>> {
        val rows = ArrayList<Triple<String, String, Int>>()
        rows.add(Triple("AXE X", assignNames[assignXIdx], turquoise))
        rows.add(Triple("AXE Y", assignNames[assignYIdx], turquoise))
        for (i in 0 until 2) {
            val t = lfoTargets[i]
            if (t >= 0) {
                val nm = if (t < 8) assignNames[t] else linkParamName(t)
                rows.add(Triple("LFO ${i + 1}", nm, green))
            }
        }
        if (envOn && envTarget >= 0) rows.add(Triple("ENV", envTargetName(), Color.rgb(120, 220, 120)))
        if (logOn && logTarget >= 8) {
            rows.add(Triple("LOGIQUE", linkParamName(logTarget), Color.rgb(255, 140, 0)))
        }
        for (lk in bankLinks[activeGroup]) {
            if (lk.amount != 0f) rows.add(Triple(linkParamName(lk.src), linkParamName(lk.dst), pink))
        }
        for ((k, m) in ZuluMidi.outMap) {
            val ch = if (m.second < 0) ZuluMidi.globalChannel else m.second
            rows.add(Triple(k, "CC${m.first} CH${ch + 1}", Color.rgb(80, 170, 255)))
        }
        return rows
    }

    private fun openPatchBay() {
        patchBay.rows = buildPatchRows()
        patchBay.visibility = View.VISIBLE
        patchBay.invalidate()
    }

    private fun setPunchCollection(c: Int) {
        punchView?.releaseAll()
        punchCol = c
        punchView?.setCollectionIndex(c)
        punchView?.setLabels(punchLabels[c])
        for (z in 0 until 9) if (punchLatched[z]) punchApply(z, true)
        refreshPresetRow()
    }

    private fun setPunchMode(on: Boolean) {
        punchMode = on
        if (!on) {
            punchView?.releaseAll()
            for (z in 0 until 9) if (punchLatched[z]) punchApply(z, true)
        }
        punchView?.visibility = if (on) View.VISIBLE else View.GONE
        menuPunchBtn?.setTextColor(if (on) amber else silk)
        refreshPresetRow()
    }

    private fun snapQuietFor(fx: Int): Boolean = snapFxOverride[fx] ?: snapTransp

    private fun pushNetParams() {
        val chaos = floatArrayOf(0.25f, 0.5f, 0.9f)[netChaosSel]
        val mod = floatArrayOf(0.25f, 0.5f, 0.85f)[netModSel]
        val alias = floatArrayOf(0f, 0.4f, 0.9f)[netAliasSel]
        val speed = floatArrayOf(0.15f, 0.5f, 0.8f, 1f)[netSpeedSel]
        engine.nativeSetNetworkParams(chaos, mod, alias, speed)
    }

    private fun loopMul(dir: Int) {
        loopLenSteps =
            if (dir == 1) (loopLenSteps * 2).coerceAtMost(19)
            else (loopLenSteps / 2).coerceAtLeast(1)
        padGrid.activeBank = loopLenSteps
        bubble("LOOP : " + loopLenSteps + " TEMPS")
    }

    private fun refreshMenu() {
        menuLoopLbl?.text = loopLenSteps.toString()
        for ((i, ms) in listOf(500, 1000, 1500, 2000).withIndex()) {
            menuFuseDelayBtns.getOrNull(i)?.setTextColor(if (ms == fuseDelayMs) amber else silk)
        }
        val counts = listOf(8, 10, 12, 14)
        for ((i, b) in menuPadButtons.withIndex()) {
            b.setTextColor(if (counts[i] == padGrid.padCount) amber else silk)
        }
        menuMirrorBtn?.setTextColor(if (mirrored) amber else silk)
        for ((i, b) in menuCurveButtons.withIndex()) {
            b.setTextColor(if (i == morphCurve) amber else silk)
        }
        for ((i, b) in menuFingerButtons.withIndex()) {
            b.setTextColor(if (i == fingerModeOpt) amber else silk)
        }
        menuFreezeBtn?.setTextColor(if (bendFreezeOpt) amber else silk)
        bSnapBtn?.setTextColor(if (sceneMode == 0) amber else silk)
        bMotionBtn?.setTextColor(if (sceneMode == 1) turquoise else silk)
        for ((i, b) in menuBillardButtons.withIndex()) {
            b.setTextColor(if (i == pad.billardFriction) amber else silk)
        }
        for ((i, b) in menuArtButtons.withIndex()) {
            b.setTextColor(if (i == bgArtOpt) amber else silk)
        }
        for ((i, b) in menuStyleButtons.withIndex()) {
            val sel = (i == 0 && !ZuluTheme.retro && !ZuluTheme.gameboy && !ZuluTheme.electribe && !ZuluTheme.caustic) ||
                (i == 1 && ZuluTheme.retro) || (i == 2 && ZuluTheme.gameboy) ||
                (i == 3 && ZuluTheme.electribe) || (i == 4 && ZuluTheme.caustic)
            b.setTextColor(if (sel) amber else silk)
        }
        menuXFilesButton?.setTextColor(if (ZuluTheme.xFiles) turquoise else silk)
        for ((i, b) in menuBgButtons.withIndex()) {
            b.setTextColor(if (bgColors[i].second == ZuluTheme.bgColor) amber else silk)
        }
        for ((i, b) in menuPatternButtons.withIndex()) {
            b.setTextColor(if ((i == 1) == ZuluTheme.bgDots) amber else silk)
        }
        for ((i, b) in menuZebraColorButtons.withIndex()) {
            b.setTextColor(if (zebraColors[i].second == ZuluTheme.zebraColor) amber else silk)
        }
        for ((i, b) in menuCatColorButtons.withIndex()) {
            b.setTextColor(if (catColors[i].second == catColorSel) amber else silk)
        }
        for ((i, b) in menuSirenButtons.withIndex()) {
            b.setTextColor(if (i == sirenType) amber else silk)
        }
        for ((i, b) in menuSirenWaveButtons.withIndex()) {
            b.setTextColor(if (i == sirenWave) amber else silk)
        }
        for (b in menuHelpButtons) b.setTextColor(if (xyHelpOpt) amber else silk)
        for ((i, b) in menuFontButtons.withIndex()) b.setTextColor(if (i == ZuluTheme.fontId) amber else silk)
        for ((i, b) in menuInFButtons.withIndex()) b.setTextColor(if (i == inFSel) amber else silk)
        for ((i, b) in menuOutFButtons.withIndex()) b.setTextColor(if (i == outFSel) amber else silk)
        for ((i, b) in menuCompButtons.withIndex()) b.setTextColor(if (i == masterCompSel) amber else silk)
        for ((i, b) in menuVolButtons.withIndex()) {
            val nearest = listOf(0.5f, 1f, 1.5f, 2f).withIndex().minByOrNull { kotlin.math.abs(it.value - masterVol) }?.index ?: 1
            b.setTextColor(if (i == nearest) amber else silk)
        }
        for ((i, b) in menuCapButtons.withIndex()) b.setTextColor(if ((i == 1) == appCaptureOn) amber else silk)
        for ((i, b) in menuMidiOutButtons.withIndex()) b.setTextColor(if ((i == 1) == ZuluMidi.enabled) amber else silk)
        menuMidiChButton?.text = "CANAL G : ${ZuluMidi.globalChannel + 1}"
        menuMidiTeachButton?.setTextColor(if (ZuluMidi.teach) amber else silk)
        menuMidiCntButton?.text = "MAP : ${ZuluMidi.outMap.size}"
        for ((i, b) in menuEcoButtons.withIndex()) {
            b.setTextColor(if ((i == 1) == ecoOpt) amber else silk)
        }
        for ((i, b) in menuLofiButtons.withIndex()) {
            b.setTextColor(if (i == recFlavor) amber else silk)
        }
        for ((i, b) in menuStretchButtons.withIndex()) {
            b.setTextColor(if (i == bankStretch[playerBank]) amber else silk)
        }
        for ((i, b) in menuDrumButtons.withIndex()) {
            b.setTextColor(if (listOf(8, 12, 16, 32)[i] == drumGrid.padCount) amber else silk)
        }
        for ((i, b) in menuMidiButtons.withIndex()) {
            b.setTextColor(if ((i == 0) == recMidiEngines) amber else silk)
        }
        for ((i, b) in menuInButtons.withIndex()) {
            b.setTextColor(if (i == inProfileSel) amber else silk)
        }
        for ((i, b) in menuMicButtons.withIndex()) {
            b.setTextColor(if ((i == 1) == micOn) amber else silk)
        }
        for ((i, b) in menuEqButtons.withIndex()) {
            b.setTextColor(if ((i == 1) == masterEqOn) amber else silk)
        }
        for ((i, b) in menuBoostInButtons.withIndex()) {
            b.setTextColor(if (i == inBoostSel) amber else silk)
        }
        for ((i, b) in menuBoostOutButtons.withIndex()) {
            b.setTextColor(if (i == outBoostSel) amber else silk)
        }
        for ((i, b) in menuLoudButtons.withIndex()) {
            b.setTextColor(if (i == outProfSel) amber else silk)
        }
        for ((i, b) in menuNetButtons.withIndex()) {
            b.setTextColor(if (i == netModeSel) amber else silk)
        }
        for ((i, b) in menuNetChaosButtons.withIndex()) {
            b.setTextColor(if (i == netChaosSel) amber else silk)
        }
        for ((i, b) in menuNetModButtons.withIndex()) {
            b.setTextColor(if (i == netModSel) amber else silk)
        }
        for ((i, b) in menuNetAliasButtons.withIndex()) {
            b.setTextColor(if (i == netAliasSel) amber else silk)
        }
        for ((i, b) in menuNetSpeedButtons.withIndex()) {
            b.setTextColor(if (i == netSpeedSel) amber else silk)
        }
        for ((i, b) in menuLogButtons.withIndex()) {
            b.setTextColor(if ((i == 1) == showSampleLog) amber else silk)
        }
        for ((i, b) in menuOrientButtons.withIndex()) {
            b.setTextColor(if ((i == 1) == verticalUi) amber else silk)
        }
        for ((i, b) in menuSnapButtons.withIndex()) {
            b.setTextColor(if ((i == 0) == snapTransp) amber else silk)
        }
        for (s in 1..8) {
            menuProjButtons[s - 1].setTextColor(if (projFile(s).exists()) amber else silk)
        }
    }

    private fun openMenuPanel() {
        refreshMenu()
        menuOverlay.visibility = View.VISIBLE
    }

    private fun buildStepPanel(d: Float) {
        stepOverlay = FrameLayout(this)
        stepOverlay.setBackgroundColor(Color.argb(225, 8, 9, 12))
        stepOverlay.visibility = View.GONE

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        val panelBg = GradientDrawable()
        panelBg.setColor(Color.rgb(24, 26, 32))
        panelBg.setStroke((1 * d).toInt(), Color.rgb(75, 82, 98))
        panelBg.cornerRadius = 8 * d
        panel.background = panelBg
        panel.setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())

        val titleRow = LinearLayout(this)
        titleRow.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = "PAS"
        title.setTextColor(Color.rgb(236, 224, 196))
        title.textSize = 16f
        title.typeface = markerFont(this)
        stepTitle = title
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(this)
        close.text = "\u00d7"
        close.setTextColor(silk)
        close.textSize = 16f
        close.minHeight = 0
        close.minimumHeight = 0
        close.minimumWidth = 0
        close.setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
        close.typeface = markerFont(this)
        close.background = buttonBackground(false)
        close.setOnClickListener { stepOverlay.visibility = View.GONE }
        titleRow.addView(close)
        panel.addView(titleRow)

        val hint = TextView(this)
        hint.text = "HAUT = PITCH \u00b7 BAS = VELOCITE"
        hint.setTextColor(Color.rgb(120, 128, 146))
        hint.textSize = 10f
        panel.addView(hint)

        val bImp = makeButton("IMPORTER SAMPLE", silk)
        bImp.setOnClickListener {
            if (stepEditBank >= 0) pickSampleFile(stepEditBank)
        }
        panel.addView(
            bImp,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (8 * d).toInt() }
        )

        val editor = StepEditorView(this)
        editor.onStepChange = { s, p, v ->
            if (stepEditBank >= 0) engine.nativeSetStep(stepEditBank, s, p, v)
        }
        stepEditor = editor
        panel.addView(
            editor,
            LinearLayout.LayoutParams((320 * d).toInt(), (230 * d).toInt()).apply {
                topMargin = (8 * d).toInt()
            }
        )

        stepOverlay.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    private fun openStepEditor(bank: Int) {
        val e = stepEditor ?: return
        stepEditBank = bank
        e.bank = bank
        e.stepCount = bankSteps[bank].coerceAtLeast(1)
        e.pitch = stepPitch[bank]
        e.vel = stepVel[bank]
        e.invalidate()
        stepTitle?.text = "PAS \u00b7 BANQUE ${bank + 1}"
        stepOverlay.visibility = View.VISIBLE
    }

    private fun pickSampleFile(targetBank: Int) {
        importTargetBank = targetBank
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "application/zip", "application/octet-stream"))
        }
        startActivityForResult(intent, reqImport)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqCapture) {
            if (resultCode == RESULT_OK) beginCapture(resultCode, data)
            else bubble("CAPTURE ANNUL\u00c9E")
            return
        }
        if (requestCode != reqImport || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        bubble("IMPORT...")
        thread {
            val msg = importSamples(uri, importTargetBank)
            runOnUiThread {
                bubble(msg, true)
                refreshMenu()
            }
        }
    }

    private fun importSamples(uri: Uri, targetBank: Int): String {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return "FICHIER ILLISIBLE"
            val name = queryName(uri).lowercase()
            val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
            val wavs: List<ByteArray> = if (isZip || name.endsWith(".koala")) koalaWavs(bytes) else listOf(bytes)
            if (wavs.isEmpty()) return "AUCUN WAV TROUVE"
            var loaded = 0
            var bank = targetBank
            for (w in wavs) {
                val pcm = decodeWav(w) ?: continue
                if (pcm.size < 9600) continue
                if (bank !in 0..7) {
                    bank = (0..7).firstOrNull { !engine.nativeBankHasLoop(it) } ?: break
                }
                resetSteps(bank)
                engine.nativeLoadBank(bank, pcm, pcm.size / 2)
                loaded++
                bank = -1
            }
            if (loaded == 0) "ECHEC IMPORT (FORMAT ?)" else "IMPORTE $loaded BANQUE(S)"
        } catch (e: Exception) {
            "ERREUR IMPORT"
        }
    }

    private fun queryName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) ?: "sample" else "sample"
            } ?: "sample"
        } catch (e: Exception) {
            "sample"
        }
    }

    private fun koalaWavs(bytes: ByteArray): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        try {
            val z = ZipInputStream(ByteArrayInputStream(bytes))
            var e = z.nextEntry
            while (e != null && out.size < 8) {
                if (!e.isDirectory && e.name.lowercase().endsWith(".wav")) {
                    val buf = ByteArrayOutputStream()
                    z.copyTo(buf)
                    out.add(buf.toByteArray())
                }
                e = z.nextEntry
            }
            z.close()
        } catch (e: Exception) {
        }
        return out
    }

    private fun le16(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)

    private fun le16s(b: ByteArray, p: Int): Int {
        val v = le16(b, p)
        return if (v >= 0x8000) v - 0x10000 else v
    }

    private fun le32(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
            ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)

    private fun decodeWav(b: ByteArray): FloatArray? {
        if (b.size < 44) return null
        if (!(b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
                b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte())
        ) return null
        var fmt = -1
        var ch = 0
        var sr = 0
        var bits = 0
        var dataStart = -1
        var dataLen = 0
        var pos = 12
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val len = le32(b, pos + 4)
            if (len < 0) break
            when (id) {
                "fmt " -> {
                    if (pos + 24 <= b.size) {
                        fmt = le16(b, pos + 8)
                        ch = le16(b, pos + 10)
                        sr = le32(b, pos + 12)
                        bits = le16(b, pos + 22)
                    }
                }
                "data" -> {
                    dataStart = pos + 8
                    dataLen = min(len, b.size - dataStart)
                }
            }
            pos += 8 + len + (len and 1)
        }
        if (dataStart < 0 || ch < 1 || sr < 8000 || bits == 0) return null
        val bytesPer = bits / 8
        val frames = min(dataLen / (bytesPer * ch), 2880000)
        if (frames <= 0) return null
        val st = FloatArray(frames * 2)
        for (f in 0 until frames) {
            for (c in 0 until 2) {
                val src = if (c < ch) c else 0
                val p = dataStart + (f * ch + src) * bytesPer
                val v = when {
                    fmt == 3 && bits == 32 -> Float.fromBits(le32(b, p))
                    bits == 16 -> le16s(b, p) / 32768f
                    bits == 24 -> {
                        val x = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
                            ((b[p + 2].toInt() and 0xFF) shl 16)
                        val s = if (x and 0x800000 != 0) x or (0xFF shl 24) else x
                        s / 8388608f
                    }
                    bits == 32 -> le32(b, p) / 2147483648f
                    bits == 8 -> ((b[p].toInt() and 0xFF) - 128) / 128f
                    else -> return null
                }
                st[f * 2 + c] = v
            }
        }
        if (sr == 48000) return st
        val nOut = ((frames.toLong() * 48000L) / sr).toInt()
        val out = FloatArray(nOut * 2)
        for (i in 0 until nOut) {
            val t = i.toDouble() * sr / 48000.0
            val i0 = t.toInt().coerceIn(0, frames - 1)
            val i1 = min(i0 + 1, frames - 1)
            val fr = (t - i0).toFloat()
            out[i * 2] = st[i0 * 2] + (st[i1 * 2] - st[i0 * 2]) * fr
            out[i * 2 + 1] = st[i0 * 2 + 1] + (st[i1 * 2 + 1] - st[i0 * 2 + 1]) * fr
        }
        return out
    }
}


