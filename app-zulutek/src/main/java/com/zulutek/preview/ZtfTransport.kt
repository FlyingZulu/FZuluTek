/* SPDX-License-Identifier: GPL-3.0-or-later — Copyright (C) 2026 FlyingZulu
 *
 * ZtfTransport — client de la liaison ZTF au-dessus d'un ZtfLink brut (USB-MIDI côté Android,
 * fake côté tests host). Une seule requête à la fois (le device companion exige
 * « répondre avant la suivante »). Les notifications de params (0x62 op CHANGED) arrivant
 * pendant une requête sont mises de côté et exposées via pollNotes().
 */
package com.zulutek.preview

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ZtfException(msg: String, val code: Int = 1) : Exception(msg) {
    companion object {
        const val SILENT = 4   // pas de réponse
        const val BUSY = 5
        const val NOFILE = 6
    }
}

data class Fm1File(val index: Int, val name: String, val kind: Int, val flags: Int,
                   val size: Long, val room: Long)

data class Fm1Param(val index: Int, val id: Int, val name: String, val type: Int,
                    val group: Int, val min: Int, val max: Int, val def: Int, val unit: String)

data class Fm1Hello(val version: Int, val services: Long, val readMax: Int, val writeMax: Int,
                    val nFiles: Int, val nParams: Int, val identity: String, val name: String,
                    val appVersion: String, val extras: Map<String, String>)

class ZtfTransport(private val link: ZtfLink, private val timeoutMs: Long = 1000L, private val tries: Int = 3) {

    private val lock = ReentrantLock()
    private val notes = ConcurrentLinkedQueue<Pair<Int, Int>>()   // (param id, valeur) reçues en live

    /** notifications de params reçues depuis le dernier appel (0x62 PQ_CHANGED) */
    fun pollNotes(): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        while (true) { val n = notes.poll() ?: break; out.add(n) }
        return out
    }

    /** lit les trames arrivées pendant timeoutMs et retourne les notifications de params
     *  (0x62 PQ_CHANGED envoyées spontanément par le FM-1 quand watch est actif) — miroir live mode A */
    fun poll(timeoutMs: Long): List<Pair<Int, Int>> {
        lock.withLock {
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end) {
                val pkt = link.read(end - System.currentTimeMillis()) ?: break
                val a = ZtfCodec.parseFrame(pkt) ?: continue
                if (a.first == ZtfCodec.CMD_PARAMS && a.second.isNotEmpty() &&
                    a.second[0].toInt() == ZtfCodec.PQ_CHANGED) noteChanged(a.second)
            }
        }
        return pollNotes()
    }

    // ---- bas niveau : une requête companion -> body de la réponse --------------------

    private fun request(cmd: Int, payload: ByteArray, match: ((ByteArray) -> Boolean)? = null): ByteArray {
        lock.withLock {
            repeat(tries) {
                if (!link.send(ZtfCodec.frame(cmd, payload)))
                    throw ZtfException("lien perdu (send)", ZtfException.SILENT)
                val end = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < end) {
                    val pkt = link.read(end - System.currentTimeMillis()) ?: break
                    if (ZtfCodec.isZtf(pkt)) continue          // frame ZTF : on ignore ici
                    val a = ZtfCodec.parseFrame(pkt) ?: continue
                    if (a.first == ZtfCodec.CMD_PARAMS && a.second.isNotEmpty() &&
                        a.second[0].toInt() == ZtfCodec.PQ_CHANGED) {
                        noteChanged(a.second); continue
                    }
                    if (a.first != cmd) continue
                    if (match == null || match(a.second)) return a.second
                }
            }
        }
        throw ZtfException("pas de réponse à 0x${cmd.toString(16)}", ZtfException.SILENT)
    }

    private fun noteChanged(body: ByteArray) {
        if (body.size < 3) return
        val n = body[2].toInt() and 0xFF
        var p = 3
        repeat(n) {
            if (p + 6 > body.size) return
            notes.add(ZtfCodec.get16(body, p) to ZtfCodec.get32(body, p + 2))
            p += 6
        }
    }

    // ---- ZTF brut (F0 7D 'Z' 'T' ...) -------------------------------------------------

    private fun zrequest(cmd: Char, args: ByteArray = ByteArray(0)): Pair<Char, ByteArray> {
        lock.withLock {
            repeat(tries) {
                if (!link.send(ZtfCodec.ztfFrame(cmd, args)))
                    throw ZtfException("lien perdu (send ZTF)", ZtfException.SILENT)
                val end = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < end) {
                    val pkt = link.read(end - System.currentTimeMillis()) ?: break
                    if (!ZtfCodec.isZtf(pkt)) continue
                    // on dépile aussi les notifs companion en passant
                    val a = ZtfCodec.parseZtf(pkt) ?: continue
                    return a
                }
            }
        }
        throw ZtfException("pas de réponse ZTF '$cmd'", ZtfException.SILENT)
    }

    /** PING -> infos du FM-1 (nom, version ZTF, espace data libre) ; null si pas de ZTF */
    fun ztfPing(): Triple<String, Int, Long>? {
        val (c, b) = zrequest('P')
        if (c != 'P' || b.size < 12) return null
        val name = String(b, 0, 7, Charsets.UTF_8)
        val ver = b[7].toInt() and 0xFF
        val free = ZtfCodec.get32(b, 8).toLong() and 0xFFFFFFFFL
        return Triple(name, ver, free)
    }

    fun ztfParamGet(idx: Int): Int {
        val (_, b) = zrequest('R', byteArrayOf(idx.toByte()))
        if (b.size < 5) throw ZtfException("R court", ZtfException.SILENT)
        return ZtfCodec.get32(b, 1)
    }

    fun ztfParamSet(idx: Int, v: Int): Int {
        val a = ByteArray(5)
        a[0] = idx.toByte()
        ZtfCodec.put32(a, 1, v)
        val (_, b) = zrequest('W', a)
        if (b.size < 5) throw ZtfException("W court", ZtfException.SILENT)
        return ZtfCodec.get32(b, 1)
    }

    fun ztfPageGet(): Int { val (_, b) = zrequest('G'); return if (b.isNotEmpty()) b[0].toInt() and 0xFF else -1 }
    fun ztfPageSet(p: Int) { zrequest('S', byteArrayOf(p.toByte())) }
    fun ztfNoteOn(note: Int, vel: Int) { zrequest('T', byteArrayOf(note.toByte(), vel.toByte())) }
    fun ztfNoteOff(note: Int) { zrequest('O', byteArrayOf(note.toByte())) }
    fun ztfPlay() { zrequest('Y') }
    /** charge (applique) le fichier i depuis la flash du FM-1 ; true si ok */
    fun ztfLoad(i: Int): Boolean {
        val (_, b) = zrequest('L', byteArrayOf(i.toByte()))
        return b.size >= 3 && b[2].toInt() == 0
    }

    // ---- companion 0x60 : capability ---------------------------------------------------

    fun hello(): Fm1Hello {
        val b = request(ZtfCodec.CMD_HELLO, ByteArray(0))
        if (b.size < 13) throw ZtfException("hello court", ZtfException.SILENT)
        val ver = b[0].toInt() and 0xFF
        val svc = ZtfCodec.get32(b, 1).toLong() and 0xFFFFFFFFL
        val rmax = ZtfCodec.get16(b, 5)
        val wmax = ZtfCodec.get16(b, 7)
        val nf = ZtfCodec.get16(b, 9)
        val np = ZtfCodec.get16(b, 11)
        val (strs, _) = ZtfCodec.cstrings(b, 4, 13)
        val extras = HashMap<String, String>()
        if (strs.size >= 4 && strs[3].isNotEmpty()) {
            for (kv in strs[3].split(';')) {
                val eq = kv.indexOf('=')
                if (eq > 0) extras[kv.substring(0, eq)] = kv.substring(eq + 1) else extras[kv] = ""
            }
        }
        return Fm1Hello(ver, svc, rmax, wmax, nf, np,
            strs.getOrElse(0) { "" }, strs.getOrElse(1) { "" }, strs.getOrElse(2) { "" }, extras)
    }

    // ---- companion 0x61 : fichiers -----------------------------------------------------

    private fun fileOp(op: Int, i: Int, body: ByteArray = ByteArray(0), allowOrder: Boolean = false): ByteArray {
        val req = ByteArray(3 + body.size)
        req[0] = op.toByte()
        ZtfCodec.put16(req, 1, i)
        System.arraycopy(body, 0, req, 3, body.size)
        val b = request(ZtfCodec.CMD_FILES, req) { a ->
            a.size >= 4 && a[0].toInt() == op && ZtfCodec.get16(a, 2) == i
        }
        val st = b[1].toInt() and 0xFF
        if (st != ZtfCodec.ST_OK && !(allowOrder && st == ZtfCodec.ST_ORDER))
            throw ZtfException("file op $op idx $i: status $st",
                if (st == ZtfCodec.ST_BUSY) ZtfException.BUSY else if (st == ZtfCodec.ST_NONE) ZtfException.NOFILE else 1)
        return b.copyOfRange(4, b.size)
    }

    /** liste complète des fichiers du FM-1 */
    fun files(): List<Fm1File> {
        val out = ArrayList<Fm1File>()
        var i = 0
        var count = 1
        while (i < count && i < 4096) {
            val b = fileOp(ZtfCodec.OP_LIST, i)
            if (b.size < 12) break
            count = ZtfCodec.get16(b, 0)
            val kind = b[2].toInt() and 0xFF
            val flags = b[3].toInt() and 0xFF
            val size = ZtfCodec.get32(b, 4).toLong() and 0xFFFFFFFFL
            val room = ZtfCodec.get32(b, 8).toLong() and 0xFFFFFFFFL
            val (names, _) = ZtfCodec.cstrings(b, 1, 12)
            out.add(Fm1File(i, names.getOrElse(0) { "?" }, kind, flags, size, room))
            i++
        }
        return out
    }

    fun fileIndexOf(name: String): Fm1File {
        for (f in files()) if (f.name == name) return f
        throw ZtfException("pas de fichier $name sur le FM-1", ZtfException.NOFILE)
    }

    /** (size, crc32) du fichier i côté device */
    fun fileSum(i: Int): Pair<Long, Long> {
        val b = fileOp(ZtfCodec.OP_SUM, i)
        if (b.size < 8) throw ZtfException("sum court", ZtfException.SILENT)
        return (ZtfCodec.get32(b, 0).toLong() and 0xFFFFFFFFL) to (ZtfCodec.get32(b, 4).toLong() and 0xFFFFFFFFL)
    }

    /** lit le fichier i en entier, vérifié contre le CRC-32 du device ; progress(done,total) */
    fun fileRead(i: Int, progress: ((Long, Long) -> Unit)? = null): ByteArray {
        val (size, crc) = fileSum(i)
        if (size > Int.MAX_VALUE) throw ZtfException("fichier trop volumineux: $size octets")
        val data = ByteArray(size.toInt())
        var off = 0L
        while (off < size) {
            val req = ByteArray(6)
            ZtfCodec.put32(req, 0, off.toInt())
            ZtfCodec.put16(req, 4, ZtfCodec.READ_CHUNK)
            val b = fileOp(ZtfCodec.OP_READ, i, req)
            if (b.size < 4) throw ZtfException("read vide @$off", ZtfException.SILENT)
            val roff = ZtfCodec.get32(b, 0).toLong() and 0xFFFFFFFFL
            if (roff != off) throw ZtfException("read off $roff != $off", ZtfException.SILENT)
            val n = minOf(b.size - 4, (size - off).toInt())
            if (n <= 0) throw ZtfException("lecture sans progression @$off")
            System.arraycopy(b, 4, data, off.toInt(), n)
            off += n
            progress?.invoke(off, size)
        }
        if (ZtfCodec.crc32(data) != crc) throw ZtfException("CRC-32 différent de celui du FM-1", 1)
        return data
    }

    /** écrit le fichier i en entier : begin (reprend où on s'était arrêté) + data + commit.
     *  Retourne true si tout a été commité. */
    fun fileWrite(i: Int, data: ByteArray, chunk: Int = ZtfCodec.WRITE_CHUNK,
                  progress: ((Long, Long) -> Unit)? = null): Boolean {
        val crc = ZtfCodec.crc32(data)
        val req = ByteArray(8)
        ZtfCodec.put32(req, 0, data.size)
        ZtfCodec.put32(req, 4, crc.toInt())
        val b = fileOp(ZtfCodec.OP_BEGIN, i, req)
        if (b.size < 4) throw ZtfException("begin court", ZtfException.SILENT)
        var next = ZtfCodec.get32(b, 0).toLong() and 0xFFFFFFFFL   // reprise si coupure
        if (next > data.size.toLong()) throw ZtfException("reprise hors fichier: $next")
        while (next < data.size) {
            val n = minOf(chunk, data.size - next.toInt())
            val d = ByteArray(4 + n)
            ZtfCodec.put32(d, 0, next.toInt())
            System.arraycopy(data, next.toInt(), d, 4, n)
            val rb = fileOp(ZtfCodec.OP_DATA, i, d, allowOrder = true)   // ST_ORDER = reprise acceptée
            if (rb.size < 4) throw ZtfException("data @ $next: réponse courte", ZtfException.SILENT)
            val ack = ZtfCodec.get32(rb, 0).toLong() and 0xFFFFFFFFL
            if (ack <= next || ack > data.size.toLong())
                throw ZtfException("progression écriture invalide: $next -> $ack")
            next = ack
            progress?.invoke(next, data.size.toLong())
        }
        fileOp(ZtfCodec.OP_COMMIT, i)
        return true
    }

    fun fileDelete(i: Int) { fileOp(ZtfCodec.OP_DELETE, i) }

    // ---- companion 0x62 : paramètres ---------------------------------------------------

    private fun paramOp(op: Int, body: ByteArray = ByteArray(0)): ByteArray {
        val req = ByteArray(1 + body.size)
        req[0] = op.toByte()
        System.arraycopy(body, 0, req, 1, body.size)
        val b = request(ZtfCodec.CMD_PARAMS, req) { a -> a.size >= 2 && a[0].toInt() == op }
        val st = b[1].toInt() and 0xFF
        if (st != ZtfCodec.ST_OK) throw ZtfException("param op $op: status $st",
            if (st == ZtfCodec.ST_NONE) ZtfException.NOFILE else 1)
        return b.copyOfRange(2, b.size)
    }

    fun paramGroups(): List<String> {
        val out = ArrayList<String>()
        var g = 0
        var count = 1
        while (g < count && g < 256) {
            val b = paramOp(ZtfCodec.PQ_GROUP, byteArrayOf(g.toByte()))
            if (b.size < 2) break
            count = b[1].toInt() and 0xFF
            val (names, _) = ZtfCodec.cstrings(b, 1, 2)
            out.add(names.getOrElse(0) { "?" })
            g++
        }
        return out
    }

    /** liste des paramètres : [{index, id, name, type, group, min, max, def, unit}] */
    fun params(): List<Fm1Param> {
        val out = ArrayList<Fm1Param>()
        var i = 0
        var count = 1
        while (i < count && i < 1024) {
            val req = ByteArray(2)
            ZtfCodec.put16(req, 0, i)
            val b = paramOp(ZtfCodec.PQ_INFO, req)
            if (b.size < 23) break
            count = ZtfCodec.get16(b, 2)
            val pid = ZtfCodec.get16(b, 4)
            val type = b[6].toInt() and 0xFF
            val grp = b[7].toInt() and 0xFF
            val mn = ZtfCodec.get32(b, 9)
            val mx = ZtfCodec.get32(b, 13)
            val df = ZtfCodec.get32(b, 17)
            val (names, _) = ZtfCodec.cstrings(b, 2, 23)
            out.add(Fm1Param(i, pid, names.getOrElse(0) { "?" }, type, grp, mn, mx, df, names.getOrElse(1) { "" }))
            i++
        }
        return out
    }

    /** {id: valeur} pour les ids demandés */
    fun paramGet(ids: IntArray): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        var k = 0
        while (k < ids.size) {
            val n = minOf(32, ids.size - k)
            val req = ByteArray(n * 2)
            for (j in 0 until n) ZtfCodec.put16(req, j * 2, ids[k + j])
            val b = paramOp(ZtfCodec.PQ_GET, req)
            if (b.isEmpty()) break
            val cnt = b[0].toInt() and 0xFF
            var p = 1
            repeat(cnt) {
                if (p + 6 > b.size) return@repeat
                out[ZtfCodec.get16(b, p)] = ZtfCodec.get32(b, p + 2)
                p += 6
            }
            k += n
        }
        return out
    }

    /** set -> valeur effective (clampée par le device) */
    fun paramSet(id: Int, v: Int): Int {
        val req = ByteArray(6)
        ZtfCodec.put16(req, 0, id)
        ZtfCodec.put32(req, 2, v)
        val b = paramOp(ZtfCodec.PQ_SET, req)
        if (b.size < 6) throw ZtfException("set court", ZtfException.SILENT)
        return ZtfCodec.get32(b, 2)
    }

    /** s'abonner aux notifications de changement (miroir live, mode A) */
    fun paramWatch(on: Boolean): Boolean {
        val b = paramOp(ZtfCodec.PQ_WATCH, byteArrayOf(if (on) 1 else 0))
        return b.isNotEmpty() && b[0].toInt() != 0
    }
}
