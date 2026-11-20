/* SPDX-License-Identifier: GPL-3.0-or-later — Copyright (C) 2026 FlyingZulu
 *
 * Fm1Usb — côté Android de la liaison ZTF : USB host vers le FM-1.
 *
 * Le FM-1 énumère en USB-MIDI (interface classe 0x01, sous-classe 0x03 MIDISTREAMING,
 * endpoints bulk IN/OUT) — on filtre par classe, pas de VID/PID. Chaque paquet USB-MIDI
 * fait 4 octets d'en-tête (CIN<<4 | câble) + jusqu'à 3 octets de données ; un SysEx est
 * découpé en blocs de 3 (CIN 0x4 continue, 0x5/0x6/0x7 fin à 1/2/3 octets) et réassemblé
 * à la réception. Au-dessus : ZtfTransport (companion 0x60/61/62 + commandes ZTF).
 *
 * Un seul thread worker exécute les requêtes (le device n'en accepte qu'une à la fois) ;
 * les callbacks sont appelés depuis ce thread — l'UI doit poster sur le main looper.
 */
package com.zulutek.preview

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import java.io.File
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** ZtfLink implémenté sur les endpoints bulk d'une interface USB-MIDI */
private class UsbMidiLink(
    private val conn: UsbDeviceConnection,
    private val epOut: UsbEndpoint,
    private val epIn: UsbEndpoint
) : ZtfLink {

    private val queue: BlockingQueue<ByteArray> = LinkedBlockingQueue()
    @Volatile private var running = true
    private val reader = thread(name = "fm1-midi-rx", start = true) { readLoop() }

    /** découpe un SysEx F0..F7 en paquets USB-MIDI 4 octets et les envoie */
    override fun send(pkt: ByteArray): Boolean {
        if (pkt.size < 2 || pkt[0] != 0xF0.toByte()) return false
        var off = 0
        val n = pkt.size
        while (off < n) {
            val rem = n - off
            val take = minOf(3, rem)
            val last = off + take >= n
            val cin = if (!last) 0x4 else when (take) { 3 -> 0x7; 2 -> 0x6; else -> 0x5 }
            val buf = ByteArray(4 + take)
            buf[0] = ((cin shl 4) or 0).toByte()   // câble 0
            System.arraycopy(pkt, off, buf, 4, take)
            val r = conn.bulkTransfer(epOut, buf, buf.size, 250)
            if (r < 0) return false
            off += take
        }
        return true
    }

    private fun readLoop() {
        val buf = ByteArray(64)
        val sx = ByteArray(4096)
        var sxLen = 0
        while (running) {
            val r = conn.bulkTransfer(epIn, buf, buf.size, 200)
            if (r <= 0) continue
            var p = 0
            while (p + 4 <= r) {
                val cin = (buf[p].toInt() ushr 4) and 0xF
                val cable = buf[p].toInt() and 0xF
                if (cable != 0) { p += 4; continue }
                when (cin) {
                    0x4, 0x5, 0x6, 0x7 -> {
                        val nb = when (cin) { 0x4, 0x7 -> 3; 0x6 -> 2; else -> 1 }
                        for (k in 0 until nb) {
                            if (p + 4 + k < r && sxLen < sx.size) sx[sxLen++] = buf[p + 4 + k]
                        }
                        if (cin != 0x4) {   // fin de SysEx -> trame complète
                            queue.offer(sx.copyOf(sxLen))
                            sxLen = 0
                        }
                    }
                    else -> { /* messages courts (notes/CC) : ignorés en v1 */ }
                }
                p += 4
            }
        }
    }

    override fun read(timeoutMs: Long): ByteArray? = queue.poll(timeoutMs, TimeUnit.MILLISECONDS)

    override fun close() {
        running = false
        reader.interrupt()
        queue.clear()
    }
}

/** Façade haut niveau : scan, permission, connexion, opérations ZTF sur thread worker. */
object Fm1Usb {

    const val ACTION_USB_PERMISSION = "com.zulutek.preview.USB_PERMISSION"

    @Volatile var connected = false
        private set
    @Volatile var deviceName: String = ""
        private set

    /** callbacks appelés depuis le thread worker — poster sur le looper UI si besoin */
    var onStatus: (String) -> Unit = {}
    var onFiles: (List<Fm1File>) -> Unit = {}
    var onProgress: (String) -> Unit = {}

    private lateinit var appCtx: Context
    private var usbManager: UsbManager? = null
    private var device: UsbDevice? = null
    private var midiIf: UsbInterface? = null
    private var link: UsbMidiLink? = null
    var transport: ZtfTransport? = null
        private set

    private val worker = Executors.newSingleThreadExecutor()

    private val permReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION != intent.action) return
            val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && dev != null) {
                device = dev
                openNow()
            } else {
                onStatus("permission USB refusée")
            }
        }
    }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        usbManager = appCtx.getSystemService(Context.USB_SERVICE) as UsbManager
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33)
            appCtx.registerReceiver(permReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appCtx.registerReceiver(permReceiver, filter)
    }

    fun release() {
        try { appCtx.unregisterReceiver(permReceiver) } catch (_: Exception) {}
        close()
    }

    /** trouve l'interface MIDI du FM-1 (classe 0x01 sous-classe 0x03) */
    private fun findMidiInterface(dev: UsbDevice): UsbInterface? {
        for (i in 0 until dev.interfaceCount) {
            val itf = dev.getInterface(i)
            if (itf.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                itf.interfaceSubclass == 0x03) return itf
        }
        // repli : n'importe quelle interface audio avec un bulk IN et un bulk OUT
        for (i in 0 until dev.interfaceCount) {
            val itf = dev.getInterface(i)
            if (itf.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
            var bulkIn = false; var bulkOut = false
            for (e in 0 until itf.endpointCount) {
                val ep = itf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) bulkIn = true else bulkOut = true
                }
            }
            if (bulkIn && bulkOut) return itf
        }
        return null
    }

    /** scan les devices USB ; si le FM-1 est là, demande la permission puis ouvre */
    fun scan(): Boolean {
        val mgr = usbManager ?: return false
        for ((_, dev) in mgr.deviceList) {
            val itf = findMidiInterface(dev)
            if (itf != null) {
                device = dev
                midiIf = itf
                deviceName = dev.deviceName
                if (mgr.hasPermission(dev)) { openNow(); return true }
                val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val pi = PendingIntent.getBroadcast(appCtx, 0, Intent(ACTION_USB_PERMISSION).setPackage(appCtx.packageName), flags)
                mgr.requestPermission(dev, pi)
                onStatus("permission USB demandée…")
                return true
            }
        }
        onStatus("FM-1 non détecté (USB)")
        return false
    }

    private fun openNow() {
        val dev = device ?: return
        val itf = midiIf ?: return
        val mgr = usbManager ?: return
        worker.execute {
            try {
                val conn = mgr.openDevice(dev) ?: throw ZtfException("openDevice a échoué")
                if (!conn.claimInterface(itf, true)) throw ZtfException("claimInterface a échoué")
                var epIn: UsbEndpoint? = null
                var epOut: UsbEndpoint? = null
                for (e in 0 until itf.endpointCount) {
                    val ep = itf.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep else epOut = ep
                    }
                }
                if (epIn == null || epOut == null) throw ZtfException("endpoints bulk manquants")
                link?.close()
                val l = UsbMidiLink(conn, epOut, epIn)
                link = l
                transport = ZtfTransport(l)
                connected = true
                onStatus("FM-1 connecté ($deviceName)")
            } catch (e: Exception) {
                connected = false
                onStatus("connexion: ${e.message}")
            }
        }
    }

    fun close() {
        worker.execute {
            link?.close()
            link = null
            transport = null
            connected = false
        }
    }

    /** exécute une op sur le worker ; les exceptions deviennent un statut */
    fun run(label: String, op: () -> Unit) {
        worker.execute {
            if (!connected || transport == null) { onStatus("FM-1 non connecté"); return@execute }
            try { op() } catch (e: Exception) { onStatus("$label: ${e.message}") }
        }
    }

    fun ping(): Triple<String, Int, Long>? {
        var out: Triple<String, Int, Long>? = null
        run("ping") {
            val t = transport!!.ztfPing()
            if (t != null) {
                out = t
                onStatus("ZTF OK: ${t.first} v${t.second}, libre ${t.third / 1024} Ko")
            } else onStatus("pas de réponse ZTF (pas companion ?)")
        }
        return out
    }

    fun refreshFiles() {
        run("ls") {
            val fs = transport!!.files()
            onFiles(fs)
            onStatus("${fs.size} fichiers sur le FM-1")
        }
    }

    /** tire le fichier i du FM-1 vers un fichier local ; retourne la taille lue */
    fun pull(i: Int, dest: File, label: String = "PULL"): Long {
        var size = 0L
        run(label) {
            val data = transport!!.fileRead(i) { done, total ->
                onProgress("$label $done/$total")
            }
            dest.parentFile?.mkdirs()
            dest.writeBytes(data)
            size = data.size.toLong()
            onStatus("$label -> ${dest.name} (${data.size} o)")
        }
        return size
    }

    /** pousse des octets vers le fichier i du FM-1 (begin/data/commit, CRC-32, reprise) */
    fun push(i: Int, data: ByteArray, label: String = "PUSH"): Boolean {
        var ok = false
        run(label) {
            transport!!.fileWrite(i, data) { done, total ->
                onProgress("$label ${(done * 100 / total.coerceAtLeast(1))}%")
            }
            ok = true
            onStatus("$label OK (${data.size} o, CRC ok)")
        }
        return ok
    }

    /** pousse vers un fichier nommé (ZSMP00..07 / ZLUP00..07 / ZMAC) */
    fun pushNamed(name: String, data: ByteArray, label: String = "PUSH"): Boolean {
        var ok = false
        run(label) {
            val f = transport!!.fileIndexOf(name)
            transport!!.fileWrite(f.index, data) { done, total ->
                onProgress("$label ${(done * 100 / total.coerceAtLeast(1))}%")
            }
            ok = true
            onStatus("$label $name OK (${data.size} o)")
        }
        return ok
    }

    fun pullNamed(name: String, dest: File, label: String = "PULL"): Long {
        var size = 0L
        run(label) {
            val f = transport!!.fileIndexOf(name)
            val data = transport!!.fileRead(f.index) { done, total -> onProgress("$label $done/$total") }
            dest.parentFile?.mkdirs()
            dest.writeBytes(data)
            size = data.size.toLong()
        }
        return size
    }

    fun load(i: Int) {
        run("LOAD") {
            val ok = transport!!.ztfLoad(i)
            onStatus(if (ok) "LOAD fichier $i appliqué" else "LOAD $i a échoué")
        }
    }

    fun delete(i: Int) {
        run("RM") {
            transport!!.fileDelete(i)
            onStatus("fichier $i supprimé")
        }
    }

    fun hello() {
        run("hello") {
            val h = transport!!.hello()
            onStatus("${h.name} v${h.appVersion} — ${h.nFiles} fichiers, ${h.nParams} params, extras=${h.extras}")
        }
    }
}
