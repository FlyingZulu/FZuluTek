/* SPDX-License-Identifier: GPL-3.0-or-later — Copyright (C) 2026 FlyingZulu
 *
 * ZtfCodec — couche protocolaire PURE (aucun import Android) de la liaison « ZTF » APK <-> FM-1.
 *
 * Deux framings sur le même câble USB-MIDI (voir patches/0010-zulutek-link.patch côté firmware
 * et fm1-base/tools/fm1_companion.py comme référence host) :
 *
 *  1. companion fm1-base (pack7) :  F0 pack7(00 59 cmd len24 body... sum8(body)) F7
 *     cmd 0x60 capability / 0x61 files / 0x62 params. Une requête à la fois, <=640 o entre F0/F7.
 *  2. commandes propres ZTF (brut, PAS de pack7) :  F0 7D 'Z' 'T' cmd ... F7
 *     PING/param/page/notes/play/load — voir ztek_link.c (zlk_sysex).
 *
 * Formats de fichiers échangés (little-endian explicite, identiques au firmware) :
 *  - ZSM1 : 'ZSM1' sr u32, len u32, fmt u8 (0=int16, 1=µ-law), data (len échantillons)
 *  - ZLS1 : 'ZLS1' pitch i8, pos u16, mode/mut/src/fx/vol u8 x5, len u16, len_set u8, on u8,
 *           n_note u16, notes x5 {t u16, note u8, vel u8, dur u8}, n_par u16,
 *           pars x8 {t u16, key u8, pad u8, v f32}
 *  - ZMC1 : 'ZMC1' + 16 x {dst i16, val f32, used u8}
 *  - ZTP1 : blob preset opaque (ztek_preset_export côté moteur, poussé/tiré tel quel)
 *
 * La spec ZuluLink (réseau Zulu-to-Zulu) reste CONFIDENTIELLE et n'est PAS ici : ZTF est un
 * protocole séparé, minimal, interopérable MIDI.
 */
package com.zulutek.preview

import java.util.zip.CRC32

/** Un lien brut : envoi/réception de trames SysEx complètes (F0...F7). */
interface ZtfLink {
    fun send(pkt: ByteArray): Boolean
    /** trame complète suivante (F0..F7) ou null si timeout */
    fun read(timeoutMs: Long): ByteArray?
    fun close()
}

object ZtfCodec {

    // ---- framing companion (pack7) ------------------------------------------------

    const val CMD_HELLO = 0x60
    const val CMD_FILES = 0x61
    const val CMD_PARAMS = 0x62

    const val MAX_FRAME = 640          // octets max entre F0 et F7 (hors pack7)
    const val WRITE_CHUNK = 512        // octets de data max par écriture fichier
    const val READ_CHUNK = 256         // octets demandés par lecture (comme fm1_companion.py)

    // ops fichiers (0x61)
    const val OP_LIST = 0
    const val OP_READ = 1
    const val OP_SUM = 2
    const val OP_BEGIN = 3
    const val OP_DATA = 4
    const val OP_COMMIT = 5
    const val OP_DELETE = 6
    // statuts
    const val ST_OK = 0
    const val ST_BUSY = 1
    const val ST_NONE = 2
    const val ST_ORDER = 7
    // kinds
    const val FK_OTHER = 0
    const val FK_PROJECT = 1
    const val FK_PRESET = 2
    const val FK_SAMPLE = 3
    const val FK_BANK = 4
    const val FK_SETTINGS = 6

    // ops params (0x62)
    const val PQ_INFO = 0
    const val PQ_LABELS = 1
    const val PQ_GROUP = 2
    const val PQ_GET = 3
    const val PQ_SET = 4
    const val PQ_WATCH = 5
    const val PQ_CHANGED = 6

    /** 8 octets -> 7 octets 7-bit, little-endian (comme fm1_install.pack7 / ota.c) */
    fun pack7(data: ByteArray): ByteArray {
        val out = ByteArray((data.size * 8 + 6) / 7)
        var acc = 0
        var nb = 0
        var oi = 0
        for (b in data) {
            acc = acc or ((b.toInt() and 0xFF) shl nb)
            nb += 8
            while (nb >= 7) {
                out[oi++] = (acc and 0x7F).toByte()
                acc = acc ushr 7
                nb -= 7
            }
        }
        if (nb > 0) out[oi++] = (acc and 0x7F).toByte()
        return out
    }

    /** 7 octets 7-bit -> 8 octets (fm1_install.unpack7) */
    fun unpack7(s: ByteArray): ByteArray {
        val out = ByteArray((s.size * 7 + 7) / 8)
        var acc = 0
        var nb = 0
        var oi = 0
        for (b in s) {
            acc = acc or ((b.toInt() and 0x7F) shl nb)
            nb += 7
            while (nb >= 8) {
                out[oi++] = (acc and 0xFF).toByte()
                acc = acc ushr 8
                nb -= 8
            }
        }
        return out
    }

    /** trame companion : F0 pack7(00 59 cmd len24 body sum8) F7 */
    fun frame(cmd: Int, payload: ByteArray): ByteArray {
        val body = ByteArray(6 + payload.size + 1)
        body[0] = 0x00
        body[1] = 0x59
        body[2] = cmd.toByte()
        body[3] = (payload.size and 0xFF).toByte()
        body[4] = ((payload.size shr 8) and 0xFF).toByte()
        body[5] = 0
        System.arraycopy(payload, 0, body, 6, payload.size)
        var sum = 0
        for (i in 6 until 6 + payload.size) sum = (sum + (body[i].toInt() and 0xFF))
        body[6 + payload.size] = ((sum.inv()) and 0xFF).toByte()
        val p = pack7(body)
        val out = ByteArray(p.size + 2)
        out[0] = 0xF0.toByte()
        System.arraycopy(p, 0, out, 1, p.size)
        out[out.size - 1] = 0xF7.toByte()
        return out
    }

    /** trame reçue -> (cmd, body) ou null si ce n'est pas le framing companion ou si checksum faux */
    fun parseFrame(pkt: ByteArray): Pair<Int, ByteArray>? {
        if (pkt.size < 9 || pkt[0] != 0xF0.toByte() || pkt[pkt.size - 1] != 0xF7.toByte()) return null
        val u = unpack7(pkt.copyOfRange(1, pkt.size - 1))
        if (u.size < 7 || u[0] != 0x00.toByte() || u[1] != 0x59.toByte()) return null
        val n = (u[3].toInt() and 0xFF) or ((u[4].toInt() and 0xFF) shl 8) or ((u[5].toInt() and 0xFF) shl 16)
        if (u.size < n + 7) return null
        var sum = 0
        for (i in 6 until 6 + n) sum = (sum + (u[i].toInt() and 0xFF))
        if (((sum.inv()) and 0xFF) != (u[6 + n].toInt() and 0xFF)) return null
        return (u[2].toInt() and 0xFF) to u.copyOfRange(6, 6 + n)
    }

    /** vrai si la trame est une frame ZTF (F0 7D 'Z' 'T' ...) plutôt que companion */
    fun isZtf(pkt: ByteArray): Boolean =
        pkt.size >= 5 && pkt[0] == 0xF0.toByte() && pkt[1] == 0x7D.toByte() &&
            pkt[2] == 'Z'.code.toByte() && pkt[3] == 'T'.code.toByte()

    // ---- frames ZTF (brut, manufacturer 0x7D) ----------------------------------------

    /** trame ZTF : F0 7D 'Z' 'T' cmd args... F7 (PAS de pack7 ; <=32 o de body côté réponse) */
    fun ztfFrame(cmd: Char, args: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteArray(5 + args.size + 1)
        out[0] = 0xF0.toByte()
        out[1] = 0x7D.toByte()
        out[2] = 'Z'.code.toByte()
        out[3] = 'T'.code.toByte()
        out[4] = cmd.code.toByte()
        System.arraycopy(args, 0, out, 5, args.size)
        out[out.size - 1] = 0xF7.toByte()
        return out
    }

    /** trame ZTF reçue -> (cmd, args) ; args = octets après le cmd, sans le F7 final */
    fun parseZtf(pkt: ByteArray): Pair<Char, ByteArray>? {
        if (!isZtf(pkt) || pkt.size < 6 || pkt[pkt.size - 1] != 0xF7.toByte()) return null
        return pkt[4].toInt().toChar() to pkt.copyOfRange(5, pkt.size - 1)
    }

    // ---- petits helpers little-endian -------------------------------------------------

    fun put16(p: ByteArray, off: Int, v: Int) {
        p[off] = (v and 0xFF).toByte()
        p[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    fun put32(p: ByteArray, off: Int, v: Int) {
        p[off] = (v and 0xFF).toByte()
        p[off + 1] = ((v shr 8) and 0xFF).toByte()
        p[off + 2] = ((v shr 16) and 0xFF).toByte()
        p[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    fun get16(p: ByteArray, off: Int): Int =
        (p[off].toInt() and 0xFF) or ((p[off + 1].toInt() and 0xFF) shl 8)

    fun get32(p: ByteArray, off: Int): Int =
        (p[off].toInt() and 0xFF) or ((p[off + 1].toInt() and 0xFF) shl 8) or
            ((p[off + 2].toInt() and 0xFF) shl 16) or ((p[off + 3].toInt() and 0xFF) shl 24)

    fun putF32(p: ByteArray, off: Int, v: Float) = put32(p, off, java.lang.Float.floatToRawIntBits(v))
    fun getF32(p: ByteArray, off: Int): Float = java.lang.Float.intBitsToFloat(get32(p, off))

    fun crc32(data: ByteArray): Long {
        val c = CRC32()
        c.update(data)
        return c.value
    }

    /** k chaînes NUL-terminées -> (liste, reste) */
    fun cstrings(b: ByteArray, k: Int, off: Int = 0): Pair<List<String>, Int> {
        val out = ArrayList<String>(k)
        var i = off
        repeat(k) {
            var z = i
            while (z < b.size && b[z] != 0.toByte()) z++
            out.add(String(b, i, z - i, Charsets.UTF_8))
            i = if (z < b.size) z + 1 else b.size
        }
        return out to i
    }

    // ---- µ-law (G.711) pour ZSM1 fmt=1 -------------------------------------------------

    private val ULAW_BIAS = 0x84
    private val ULAW_CLIP = 32635

    /** G.711 µ-law, exactement comme ml_ulaw_enc (dsp/midi_looper.c) : ~(sign|exp<<4|mant), sign=0x80 si négatif */
    fun linearToUlaw(pcm16: Int): Int {
        var sample = pcm16
        val sign = (sample shr 8) and 0x80
        if (sign != 0) sample = -sample
        if (sample > ULAW_CLIP) sample = ULAW_CLIP
        sample += ULAW_BIAS
        var exp = 7
        var mask = 0x4000
        while (exp > 0 && (sample and mask) == 0) { mask = mask shr 1; exp-- }
        val mant = (sample shr (exp + 3)) and 0x0F
        return ((sign or (exp shl 4) or mant).inv()) and 0xFF
    }

    /** inverse de ml_ulaw_dec ; résultat à l'échelle int16 (sample/32768 côté float) */
    fun ulawToLinear(u: Int): Int {
        val uu = u.inv() and 0xFF
        var t = ((uu and 0x0F) shl 3) + 0x84
        t = t shl ((uu shr 4) and 0x07)
        return if (uu and 0x80 != 0) 0x84 - t else t - 0x84
    }

    // ---- formats de fichiers -----------------------------------------------------------

    /** ZSM1 : 'ZSM1' sr u32, len u32, fmt u8, data. fmt 0 = int16 LE x len, fmt 1 = µ-law x len */
    fun encodeZsm1(sampleRate: Int, fmt: Int, samples: ShortArray): ByteArray {
        val len = samples.size
        val payload = if (fmt == 1) {
            val d = ByteArray(len)
            for (i in 0 until len) d[i] = linearToUlaw(samples[i].toInt()).toByte()
            d
        } else {
            val d = ByteArray(len * 2)
            for (i in 0 until len) put16(d, i * 2, samples[i].toInt())
            d
        }
        val out = ByteArray(13 + payload.size)
        out[0] = 'Z'.code.toByte(); out[1] = 'S'.code.toByte(); out[2] = 'M'.code.toByte(); out[3] = '1'.code.toByte()
        put32(out, 4, sampleRate)
        put32(out, 8, len)
        out[12] = fmt.toByte()
        System.arraycopy(payload, 0, out, 13, payload.size)
        return out
    }

    data class Zsm(val sampleRate: Int, val fmt: Int, val samples: ShortArray) {
        val len: Int get() = samples.size
    }

    fun decodeZsm(b: ByteArray): Zsm? {
        if (b.size < 13 || b[0] != 'Z'.code.toByte() || b[1] != 'S'.code.toByte() ||
            b[2] != 'M'.code.toByte() || b[3] != '1'.code.toByte()) return null
        val sr = get32(b, 4)
        val len = get32(b, 8)
        val fmt = b[12].toInt() and 0xFF
        val need = if (fmt == 1) len else len * 2
        if (b.size < 13 + need || len < 0) return null
        val s = ShortArray(len)
        if (fmt == 1) {
            for (i in 0 until len) s[i] = ulawToLinear(b[13 + i].toInt() and 0xFF).toShort()
        } else {
            for (i in 0 until len) s[i] = get16(b, 13 + i * 2).toShort()
        }
        return Zsm(sr, fmt, s)
    }

    data class ZlsNote(var t: Int, var note: Int, var vel: Int, var dur: Int)
    data class ZlsPar(var t: Int, var key: Int, var v: Float)
    data class Zls(var pitch: Int, var pos: Int, var mode: Int, var mut: Int, var src: Int,
                   var fx: Int, var vol: Int, var len: Int, var lenSet: Boolean, var on: Boolean,
                   val notes: MutableList<ZlsNote>, val pars: MutableList<ZlsPar>)

    /** ZLS1 little-endian explicite — identique à zml_slot_save (ztek_loop.c, patch 0010) */
    fun encodeZls1(z: Zls): ByteArray {
        val n = 18 + z.notes.size * 5 + 2 + z.pars.size * 8
        val b = ByteArray(n)
        b[0] = 'Z'.code.toByte(); b[1] = 'L'.code.toByte(); b[2] = 'S'.code.toByte(); b[3] = '1'.code.toByte()
        b[4] = (z.pitch and 0xFF).toByte()
        put16(b, 5, z.pos)
        b[7] = z.mode.toByte(); b[8] = z.mut.toByte(); b[9] = z.src.toByte(); b[10] = z.fx.toByte(); b[11] = z.vol.toByte()
        put16(b, 12, z.len)
        b[14] = if (z.lenSet) 1 else 0
        b[15] = if (z.on) 1 else 0
        put16(b, 16, z.notes.size)
        var p = 18
        for (e in z.notes) {
            put16(b, p, e.t); b[p + 2] = e.note.toByte(); b[p + 3] = e.vel.toByte(); b[p + 4] = e.dur.toByte()
            p += 5
        }
        put16(b, p, z.pars.size); p += 2
        for (e in z.pars) {
            put16(b, p, e.t); b[p + 2] = e.key.toByte(); b[p + 3] = 0
            putF32(b, p + 4, e.v)
            p += 8
        }
        return b
    }

    fun decodeZls1(b: ByteArray): Zls? {
        if (b.size < 18 || b[0] != 'Z'.code.toByte() || b[1] != 'L'.code.toByte() ||
            b[2] != 'S'.code.toByte() || b[3] != '1'.code.toByte()) return null
        val z = Zls(b[4].toInt(), get16(b, 5), b[7].toInt() and 0xFF, b[8].toInt() and 0xFF,
            b[9].toInt() and 0xFF, b[10].toInt() and 0xFF, b[11].toInt() and 0xFF,
            get16(b, 12), b[14].toInt() != 0, b[15].toInt() != 0, ArrayList(), ArrayList())
        val nNote = get16(b, 16)
        if (b.size < 18 + nNote * 5 + 2) return null
        var p = 18
        repeat(nNote) {
            z.notes.add(ZlsNote(get16(b, p), b[p + 2].toInt() and 0xFF, b[p + 3].toInt() and 0xFF, b[p + 4].toInt() and 0xFF))
            p += 5
        }
        val nPar = get16(b, p); p += 2
        if (b.size < 18 + nNote * 5 + 2 + nPar * 8) return null
        repeat(nPar) {
            z.pars.add(ZlsPar(get16(b, p), b[p + 2].toInt() and 0xFF, getF32(b, p + 4)))
            p += 8
        }
        return z
    }

    data class ZmacSlot(var dst: Int, var v: Float, var used: Boolean)

    /** ZMC1 : 'ZMC1' + 16 x {dst i16, val f32, used u8} — identique à zmac_save (ztek_macro.c) */
    fun encodeZmc1(slots: List<ZmacSlot>): ByteArray {
        val b = ByteArray(4 + slots.size * 10)
        b[0] = 'Z'.code.toByte(); b[1] = 'M'.code.toByte(); b[2] = 'C'.code.toByte(); b[3] = '1'.code.toByte()
        var p = 4
        for (s in slots) {
            put16(b, p, s.dst and 0xFFFF)
            putF32(b, p + 2, s.v)
            b[p + 6] = if (s.used) 1 else 0
            p += 10
        }
        return b
    }

    fun decodeZmc1(b: ByteArray): List<ZmacSlot>? {
        if (b.size < 4 + 16 * 10 || b[0] != 'Z'.code.toByte() || b[1] != 'M'.code.toByte() ||
            b[2] != 'C'.code.toByte() || b[3] != '1'.code.toByte()) return null
        val out = ArrayList<ZmacSlot>(16)
        var p = 4
        repeat(16) {
            val dst = get16(b, p).toShort().toInt()
            val v = getF32(b, p + 2)
            val used = b[p + 6].toInt() != 0
            out.add(ZmacSlot(dst, v, used))
            p += 10
        }
        return out
    }
}
