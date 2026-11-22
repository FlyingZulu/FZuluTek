/* SPDX-License-Identifier: GPL-3.0-or-later — Copyright (C) 2026 FlyingZulu
 * Vendored from the app module (app/src/main/java/com/zulu/ZuluLink.kt), same author/licence.
 * ZuluLink protocol: CONFIDENTIAL — spec lives in Flying Zulu private Vibe KB, never publish.
 * Discovery M1: HELLO broadcast every 2 s on 53333, direct messages on 53334.
 */
package com.zulutek.preview

import kotlin.concurrent.thread
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ZuluLink — le réseau Zulu-to-Zulu (spécification docs/ZULULINK.md, v0.4.1).
 *
 * Jalon M1 : la découverte. Chaque Zulu émet un HELLO en broadcast toutes les
 * 2 s, écoute ceux des autres, et tient la liste des pairs vivants.
 * Les encodeurs/décodeurs de tous les messages (CC, CLOCK, START/STOP, TAP,
 * SNAP, PING/PONG, BYE) sont déjà là : M2 et M3 branchent dessus sans rien
 * réécrire.
 *
 * Design : thread dédié, zéro dépendance Android (java.net pur), file
 * d'entrée thread-safe — l'UI lit, le réseau écrit, personne ne se bloque.
 * Tolérant par défaut : paquet inconnu ou trop court = ignoré en silence.
 */
object ZuluLink {

    // ---- Constantes du protocole ------------------------------------------

    const val DISCOVERY_PORT = 53333   // broadcast HELLO
    const val DIRECT_PORT = 53334       // messages directs entre pairs
    const val NAME_MAX = 32            // noms tronqués/paddés à 32 octets

    private val MAGIC = byteArrayOf('Z'.code.toByte(), 'L'.code.toByte(), 'N'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1

    // Types de messages (1 octet chacun)
    const val T_HELLO: Byte = 0
    const val T_BYE: Byte = 1
    const val T_CC: Byte = 2
    const val T_CLOCK: Byte = 3
    const val T_START: Byte = 4
    const val T_STOP: Byte = 5
    const val T_TAP: Byte = 6
    const val T_SNAP: Byte = 7
    const val T_PING: Byte = 8
    const val T_PONG: Byte = 9

    enum class Role { CONSOLE, MODULE }

    // Cadence et expiration des pairs (spécification section 1)
    private const val HELLO_PERIOD_MS = 2000L
    private const val LOST_AFTER_MS = 6000L   // marqué "perdu"
    private const val GONE_AFTER_MS = 10000L  // retiré de la liste

    // ---- État -------------------------------------------------------------

    data class Peer(
        val name: String,
        val address: String,
        @Volatile var role: Role = Role.MODULE,
        @Volatile var tempo: Double = 120.0,
        @Volatile var lastSeen: Long = 0L,
        @Volatile var lastPingMs: Long = -1L
    ) {
        val lost: Boolean get() = System.currentTimeMillis() - lastSeen > LOST_AFTER_MS
    }

    /** Message entrant, déjà décodé. Les champs ne sont remplis que selon le type. */
    data class RxMessage(
        val type: Byte,
        val fromName: String,
        val fromAddress: String,
        val cc: Int = -1, val ch: Int = -1, val value: Int = -1,
        val tempo: Double = 120.0, val beat: Double = 0.0, val stamp: Long = 0L
    )

    @Volatile var localName = "zulu"
    @Volatile var role = Role.MODULE
    @Volatile var tempo = 120.0

    /** Appelé (depuis le thread réseau) à chaque changement de la liste des pairs. */
    var onPeersChanged: (() -> Unit)? = null
    /** Appelé (depuis le thread réseau) pour chaque message direct reçu. */
    var onMessage: ((RxMessage) -> Unit)? = null

    val running = AtomicBoolean(false)
    private val peers = ConcurrentHashMap<String, Peer>()
    private var discoverySocket: DatagramSocket? = null
    private var directSocket: DatagramSocket? = null
    private var announceThread: Thread? = null
    private var listenThread: Thread? = null
    private var directListenThread: Thread? = null

    /** Instantané thread-safe des pairs connus (les vivants d'abord). */
    fun peersSnapshot(): List<Peer> =
        peers.values.sortedWith(compareBy({ it.lost }, { it.name }))

    // ---- Cycle de vie -----------------------------------------------------

    fun start(name: String, asRole: Role) {
        if (!running.compareAndSet(false, true)) return
        localName = name.take(NAME_MAX)
        role = asRole
        peers.clear()

        // Socket de découverte : écoute le broadcast, accepte d'émettre en broadcast
        var ds: DatagramSocket? = null
        var dds: DatagramSocket? = null
        try {
            val discovery = DatagramSocket(DISCOVERY_PORT)
            ds = discovery
            discovery.broadcast = true
            val direct = DatagramSocket(DIRECT_PORT)
            dds = direct
            discoverySocket = discovery
            directSocket = direct

            listenThread = thread("zulu-link-listen") { listenLoop(discovery) }
            directListenThread = thread("zulu-link-direct") { directListenLoop(direct) }
            announceThread = thread("zulu-link-hello") { announceLoop() }
        } catch (e: Exception) {
            running.set(false)
            ds?.close()
            dds?.close()
            discoverySocket = null
            directSocket = null
            throw e
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { sendBye() } catch (_: Exception) {}
        discoverySocket?.close(); directSocket?.close()
        discoverySocket = null; directSocket = null
        peers.clear()
        onPeersChanged?.invoke()
    }

    // ---- Boucles réseau ---------------------------------------------------

    private fun announceLoop() {
        val hello = encodeHello()
        while (running.get()) {
            try {
                val addr = InetAddress.getByName("255.255.255.255")
                discoverySocket?.send(DatagramPacket(hello, hello.size, addr, DISCOVERY_PORT))
                prunePeers()
            } catch (e: Exception) {
                // socket fermée ou réseau absent : on retentera au prochain tour
            }
            sleepQuiet(HELLO_PERIOD_MS)
        }
    }

    private fun listenLoop(socket: DatagramSocket) {
        val buf = ByteArray(256)
        while (running.get()) {
            val packet = DatagramPacket(buf, buf.size)
            try {
                socket.receive(packet)
                if (packet.length >= 6) handleIncoming(buf, packet.length, packet.address.hostAddress ?: continue)
            } catch (e: Exception) {
                if (!running.get()) return
            }
        }
    }

    private fun directListenLoop(socket: DatagramSocket) {
        val buf = ByteArray(512)
        while (running.get()) {
            val packet = DatagramPacket(buf, buf.size)
            try {
                socket.receive(packet)
                if (packet.length >= 6) handleDirect(buf, packet.length, packet.address.hostAddress ?: continue)
            } catch (e: Exception) {
                if (!running.get()) return
            }
        }
    }

    /** Traite un paquet de découverte (HELLO/BYE venant du port broadcast). */
    private fun handleIncoming(b: ByteArray, len: Int, fromAddr: String) {
        if (!validHeader(b, len)) return
        when (b[4]) {
            T_HELLO -> {
                val (name, role, tempo) = decodeHello(b, len) ?: return
                if (name == localName) return          // c'est nous (notre propre broadcast)
                val known = peers[name]
                if (known == null) {
                    peers[name] = Peer(name, fromAddr, role, tempo, System.currentTimeMillis())
                    onPeersChanged?.invoke()
                } else {
                    known.role = role; known.tempo = tempo
                    known.lastSeen = System.currentTimeMillis()
                }
            }
            T_BYE -> {
                val name = decodeName(b, len, 6) ?: return
                if (peers.remove(name) != null) onPeersChanged?.invoke()
            }
            else -> Unit // HELLO et BYE seulement sur ce port ; le reste : ignorer en silence
        }
    }

    /** Traite un message direct (CC, CLOCK, START/STOP, TAP, SNAP, PING/PONG, BYE). */
    private fun handleDirect(b: ByteArray, len: Int, fromAddr: String) {
        if (!validHeader(b, len)) return
        val peer = peers.values.firstOrNull { it.address == fromAddr }
        val from = peer?.name ?: "?"   // un inconnu peut nous parler ; on écoute quand même
        when (b[4]) {
            T_CC -> {
                val (target, cc, ch, v) = decodeCc(b, len) ?: return
                if (target != localName) return     // pas pour nous : ignorer (le broadcast UDP direct ne rebondit pas, mais par prudence)
                onMessage?.invoke(RxMessage(T_CC, from, fromAddr, cc = cc, ch = ch, value = v))
            }
            T_CLOCK -> {
                val (tempo, beat, stamp) = decodeClock(b, len) ?: return
                onMessage?.invoke(RxMessage(T_CLOCK, from, fromAddr, tempo = tempo, beat = beat, stamp = stamp))
            }
            T_START, T_STOP -> onMessage?.invoke(RxMessage(b[4], from, fromAddr))
            T_TAP -> onMessage?.invoke(RxMessage(T_TAP, from, fromAddr))
            T_PING -> { replyPong(fromAddr, b, len); onMessage?.invoke(RxMessage(T_PING, from, fromAddr)) }
            T_PONG -> {
                val stamp = decodeStamp(b, len)
                if (peer != null && stamp > 0L) {
                    peer.lastPingMs = System.currentTimeMillis() - stamp
                }
                onMessage?.invoke(RxMessage(T_PONG, from, fromAddr, stamp = stamp))
            }
            else -> Unit // inconnu : tolérance, on ignore en silence
        }
    }

    /** Retire les pairs expirés (perdu > 6 s, disparu > 10 s). */
    private fun prunePeers() {
        val now = System.currentTimeMillis()
        val before = peers.size
        peers.values.removeIf { now - it.lastSeen > GONE_AFTER_MS }
        if (peers.size != before) onPeersChanged?.invoke()
    }

    // ---- Envoi des messages (M2/M3 branchent ici) ---------------------------

    /** Envoie un CC à un pair (cible = son nom, ou "TOUS"). */
    fun sendCc(target: String, cc: Int, ch: Int, value: Int) {
        val pkt = encodeCc(target, cc, ch, value)
        if (target.equals("TOUS", ignoreCase = true)) {
            for (p in peers.values) sendDirect(pkt, p)
        } else {
            peers[target]?.let { sendDirect(pkt, it) }
        }
    }

    /** Envoie une horloge (réservé CONSOLE) : tempo + position + horodatage. */
    fun sendClock(tempo: Double, beat: Double, stamp: Long) {
        val pkt = encodeClock(tempo, beat, stamp)
        for (p in peers.values) sendDirect(pkt, p)
    }

    fun sendStart(target: String) = sendTransport(T_START, target)
    fun sendStop(target: String) = sendTransport(T_STOP, target)
    fun sendTap() = broadcastDirect(T_TAP)
    fun sendPing(target: String) {
        val pkt = encodeStampMsg(T_PING, System.currentTimeMillis())
        peers[target]?.let { sendDirect(pkt, it) }
    }

    private fun sendTransport(type: Byte, target: String) {
        // START/STOP : cible obligatoire (règle v0.2/v0.3 : jamais d'arrêt implicite)
        if (target.equals("TOUS", ignoreCase = true)) {
            broadcastDirect(type)
        } else {
            peers[target]?.let { sendDirect(encodeBare(type), it) }
        }
    }

    private fun broadcastDirect(type: Byte) {
        val pkt = encodeBare(type)
        for (p in peers.values) sendDirect(pkt, p)
    }

    private fun sendBye() {
        val pkt = encodeNameMsg(T_BYE, localName)
        val addr = InetAddress.getByName("255.255.255.255")
        try { discoverySocket?.send(DatagramPacket(pkt, pkt.size, addr, DISCOVERY_PORT)) } catch (_: Exception) {}
    }

    private fun replyPong(toAddr: String, ping: ByteArray, len: Int) {
        val stamp = decodeStamp(ping, len)
        val pkt = encodeStampMsg(T_PONG, stamp)
        try {
            val addr = InetAddress.getByName(toAddr)
            directSocket?.send(DatagramPacket(pkt, pkt.size, addr, DIRECT_PORT))
        } catch (_: Exception) {}
    }

    private fun sendDirect(pkt: ByteArray, peer: Peer) {
        try {
            val addr = InetAddress.getByName(peer.address)
            directSocket?.send(DatagramPacket(pkt, pkt.size, addr, DIRECT_PORT))
        } catch (_: Exception) {}
    }

    /** Rejette les datagrammes hors protocole avant toute action réseau ou transport. */
    private fun validHeader(b: ByteArray, len: Int): Boolean =
        len >= 6 && b[0] == MAGIC[0] && b[1] == MAGIC[1] &&
            b[2] == MAGIC[2] && b[3] == MAGIC[3] && b[5] == VERSION

    // ---- Encodage binaire (petit, fixe, comme la spécification) -------------

    private fun header(type: Byte, size: Int): ByteBuffer {
        val bb = ByteBuffer.allocate(size)
        bb.put(MAGIC); bb.put(type); bb.put(VERSION)
        return bb
    }

    private fun putName(bb: ByteBuffer, name: String) {
        val raw = name.toByteArray(Charsets.UTF_8).copyOf(NAME_MAX)
        bb.put(raw)
    }

    private fun encodeHello(): ByteArray {
        val bb = header(T_HELLO, 6 + NAME_MAX + 1 + 8 + 4)
        putName(bb, localName)
        bb.put(if (role == Role.CONSOLE) 1 else 0)
        bb.putDouble(tempo)
        bb.putInt(DIRECT_PORT)
        return bb.array()
    }

    private fun decodeHello(b: ByteArray, len: Int): Triple<String, Role, Double>? {
        if (len < 6 + NAME_MAX + 1 + 8) return null
        val name = decodeName(b, len, 6) ?: return null
        val role = if (b[6 + NAME_MAX].toInt() == 1) Role.CONSOLE else Role.MODULE
        val tempo = ByteBuffer.wrap(b, 6 + NAME_MAX + 1, 8).double
        return Triple(name, role, tempo)
    }

    private fun encodeCc(target: String, cc: Int, ch: Int, value: Int): ByteArray {
        val bb = header(T_CC, 6 + NAME_MAX + 3)
        putName(bb, target)
        bb.put(cc.toByte()); bb.put(ch.toByte()); bb.put(value.toByte())
        return bb.array()
    }

    private fun decodeCc(b: ByteArray, len: Int): Quad? {
        if (len < 6 + NAME_MAX + 3) return null
        val target = decodeName(b, len, 6) ?: return null
        return Quad(target, b[6 + NAME_MAX].toInt() and 0xFF, b[6 + NAME_MAX + 1].toInt() and 0xFF, b[6 + NAME_MAX + 2].toInt() and 0xFF)
    }
    private data class Quad(val target: String, val cc: Int, val ch: Int, val v: Int)

    private fun encodeClock(tempo: Double, beat: Double, stamp: Long): ByteArray {
        val bb = header(T_CLOCK, 6 + 8 + 8 + 8)
        bb.putDouble(tempo); bb.putDouble(beat); bb.putLong(stamp)
        return bb.array()
    }

    private fun decodeClock(b: ByteArray, len: Int): Triple<Double, Double, Long>? {
        if (len < 6 + 8 + 8 + 8) return null
        val tempo = ByteBuffer.wrap(b, 6, 8).double
        val beat = ByteBuffer.wrap(b, 14, 8).double
        val stamp = ByteBuffer.wrap(b, 22, 8).long
        return Triple(tempo, beat, stamp)
    }

    private fun encodeBare(type: Byte): ByteArray = header(type, 6).array()

    private fun encodeNameMsg(type: Byte, name: String): ByteArray {
        val bb = header(type, 6 + NAME_MAX)
        putName(bb, name)
        return bb.array()
    }

    private fun encodeStampMsg(type: Byte, stamp: Long): ByteArray {
        val bb = header(type, 6 + 8)
        bb.putLong(stamp)
        return bb.array()
    }

    private fun decodeStamp(b: ByteArray, len: Int): Long =
        if (len >= 6 + 8) ByteBuffer.wrap(b, 6, 8).long else 0L

    private fun decodeName(b: ByteArray, len: Int, offset: Int): String? {
        if (len < offset + NAME_MAX) return null
        val raw = b.copyOfRange(offset, offset + NAME_MAX)
        var end = raw.indexOf(0.toByte())
        if (end < 0) end = NAME_MAX
        return String(raw, 0, end, Charsets.UTF_8).trim().ifEmpty { null }
    }

    private fun sleepQuiet(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    private fun thread(name: String, body: () -> Unit): Thread =
        Thread(body, name).apply { isDaemon = true; start() }
}
