package com.zulu.linktest

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.zulu.ZuluLink
import com.zulu.ZuluLink.Role
import kotlin.random.Random

/**
 * ZuluLinkTest — le compagnon M1 : deux Zulu sur le même Wi-Fi se voient.
 * Mini-app volontairement indépendante de ZuluFx (philosophie : compagnons
 * légers, totalement autonomes). minSdk 21 : tourne aussi sur le yuga (5.1).
 *
 * CONNEXION AUTO : le réseau démarre tout seul au lancement.
 * Lancer l'app sur deux appareils du même Wi-Fi, hop, bam, c'est bon.
 */
class LinkTestActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var multicastLock: WifiManager.MulticastLock? = null
    private lateinit var statusView: TextView
    private lateinit var peersView: TextView
    private lateinit var btnOnOff: Button
    private lateinit var btnRole: Button
    private lateinit var nameEdit: EditText

    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Le verrou multicast aide certains téléphones à recevoir les broadcast
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        multicastLock = wifi?.createMulticastLock("zululink")?.apply { setReferenceCounted(false); acquire() }

        buildUi()

        // --- CONNEXION AUTOMATIQUE : zéro manipulation ---
        // Le réseau démarre tout seul, avec le nom généré et le rôle MODULE.
        startLink(ZuluLink.role)

        render()
        handler.postDelayed(refresh, 700)
    }

    @SuppressLint("SetTextI18n")
    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.rgb(16, 18, 23))
        root.setPadding(48, 64, 48, 48)

        val title = TextView(this)
        title.text = "ZULU LINK · TEST M1"
        title.setTextColor(Color.rgb(236, 224, 196))
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        root.addView(title)
        val sub = TextView(this)
        sub.text = "Connexion automatique — deux Zulu sur le même Wi-Fi se voient."
        sub.setTextColor(Color.rgb(120, 128, 146))
        sub.textSize = 13f
        root.addView(sub)

        val nameRow = LinearLayout(this)
        nameRow.orientation = LinearLayout.HORIZONTAL
        val nameLbl = TextView(this)
        nameLbl.text = "NOM : "
        nameLbl.setTextColor(Color.rgb(236, 224, 196))
        nameLbl.textSize = 15f
        nameLbl.gravity = Gravity.CENTER_VERTICAL
        nameRow.addView(nameLbl)
        nameEdit = EditText(this)
        nameEdit.setText("zulu-" + Random.nextInt(1000, 9999))
        nameEdit.setTextColor(Color.rgb(255, 190, 0))
        nameEdit.textSize = 15f
        nameRow.addView(nameEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(nameRow)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        btnRole = Button(this)
        btnRole.text = "ROLE : MODULE"
        btnRole.setTextColor(Color.rgb(165, 172, 185))
        btnRole.textSize = 13f
        btnRole.setOnClickListener { toggleRole() }
        row.addView(btnRole, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        btnOnOff = Button(this)
        btnOnOff.text = "ARRETER"
        btnOnOff.setTextColor(Color.rgb(220, 60, 50))
        btnOnOff.textSize = 13f
        btnOnOff.setOnClickListener { toggleOnOff() }
        row.addView(btnOnOff, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        statusView = TextView(this)
        statusView.text = "RÉSEAU : OFF"
        statusView.setTextColor(Color.rgb(120, 128, 146))
        statusView.textSize = 14f
        root.addView(statusView)

        peersView = TextView(this)
        peersView.text = "(aucun pair connu)"
        peersView.setTextColor(Color.rgb(236, 224, 196))
        peersView.textSize = 15f
        peersView.typeface = Typeface.MONOSPACE
        root.addView(
            peersView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
    }

    private fun startLink(asRole: Role) {
        val nm = nameEdit.text.toString().trim().ifEmpty { "zulu" }
        ZuluLink.start(nm, asRole)
    }

    private fun toggleRole() {
        // rôle modifiable en live : on relance le réseau avec l'autre rôle
        ZuluLink.stop()
        startLink(if (ZuluLink.role == Role.CONSOLE) Role.MODULE else Role.CONSOLE)
        render()
    }

    private fun toggleOnOff() {
        if (ZuluLink.running.get()) ZuluLink.stop() else startLink(ZuluLink.role)
        render()
    }

    @SuppressLint("SetTextI18n")
    private fun render() {
        val on = ZuluLink.running.get()
        btnOnOff.text = if (on) "ARRETER" else "DEMARER"
        btnOnOff.setTextColor(if (on) Color.rgb(220, 60, 50) else Color.rgb(70, 220, 120))
        btnRole.text = "ROLE : " + if (ZuluLink.role == Role.CONSOLE) "CONSOLE" else "MODULE"
        btnRole.setTextColor(if (ZuluLink.role == Role.CONSOLE) Color.rgb(255, 190, 0) else Color.rgb(165, 172, 185))
        statusView.text = "RÉSEAU : " + if (on) "ON (" + ZuluLink.localName + ")" else "OFF"

        if (!on) {
            peersView.text = "(réseau éteint)"
            return
        }
        val peers = ZuluLink.peersSnapshot()
        if (peers.isEmpty()) {
            peersView.text = "(aucun pair connu — attends 2-3 s)"
            return
        }
        val sb = StringBuilder()
        for (p in peers) {
            sb.append(if (p.lost) "~ " else "* ")
            sb.append(p.name)
            sb.append("  [").append(if (p.role == Role.CONSOLE) "CONSOLE" else "MODULE").append("]")
            sb.append("  ").append(String.format("%.1f", p.tempo)).append(" BPM")
            sb.append("  @").append(p.address)
            sb.append("\n")
        }
        sb.append("\n").append(peers.size).append(" pair(s) vivant(s)")
        peersView.text = sb.toString()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        ZuluLink.stop()
        try { multicastLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
