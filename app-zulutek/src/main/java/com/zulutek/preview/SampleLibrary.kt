/* SPDX-License-Identifier: GPL-3.0-or-later — Copyright (C) 2026 FlyingZulu
 *
 * SampleLibrary — bibliothèque de samples côté téléphone (mode B « extension » du FM-1) :
 *  - import WAV (ex. export Koala Sampler) via SAF ACTION_OPEN_DOCUMENT, type « audio » + wildcard,
 *  - stockage sous filesDir/ztek/samples/,
 *  - décodage WAV (PCM 8/16/24/32 bits + float32), resample mono 44,1 kHz,
 *  - encodage ZSM1 (fmt 0 int16 / fmt 1 µ-law) pour push vers un slot ZSMPxx du FM-1,
 *  - les fichiers tirés du FM-1 (pull) vont sous filesDir/ztek/pull/ et sont réexportables en WAV.
 *
 * Contrainte flash FM-1 (patch 0010) : fichier ZTF max 0x1000 - 32 (PLAT_FILE_HEAD) = 4064 o,
 * donc payload ZSM1 <= 4051 octets -> ~2025 éch. int16 (~46 ms) ou ~4051 éch. µ-law (~92 ms).
 * La bibliothèque complète reste sur le téléphone ; on ne pousse que des extraits courts.
 */
package com.zulutek.preview

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

object SampleLibrary {

    const val FM1_FILE_MAX = 4064          // ZLK_FN (0x1000) - PLAT_FILE_HEAD (32)
    const val ZSM1_PAYLOAD_MAX = FM1_FILE_MAX - 13
    const val FM1_SR = 44100              // le ring resample du FM-1 attend du 44,1 kHz (fmt µ-law/int16)

    fun samplesDir(ctx: Context): File = File(ctx.filesDir, "ztek/samples").apply { mkdirs() }
    fun pullDir(ctx: Context): File = File(ctx.filesDir, "ztek/pull").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        (samplesDir(ctx).listFiles { f -> f.isFile } ?: emptyArray()).sortedBy { it.name }

    /** copie l'URI choisie (SAF) dans la bibliothèque ; retourne le fichier local ou null */
    fun importUri(ctx: Context, uri: Uri): File? {
        return try {
            val cr = ctx.contentResolver
            val name = cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "sample_${System.currentTimeMillis()}.wav"
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "sample.wav" }
            val dest = File(samplesDir(ctx), safe)
            cr.openInputStream(uri)?.use { inp -> dest.outputStream().use { out -> inp.copyTo(out) } }
            if (dest.length() > 44) dest else { dest.delete(); null }
        } catch (e: Exception) {
            null
        }
    }

    data class Wav(val sampleRate: Int, val channels: Int, val bits: Int, val format: Int,
                   val dataOff: Long, val dataLen: Long, val file: File)

    /** lit juste l'en-tête WAV (RIFF/WAVE, fmt, data) */
    fun wavInfo(f: File): Wav? {
        return try {
            RandomAccessFile(f, "r").use { r ->
                val h = ByteArray(12)
                if (r.read(h) != 12 || String(h, 0, 4) != "RIFF" || String(h, 8, 4) != "WAVE") return null
                var fmt: Int = -1; var ch = 0; var sr = 0; var bits = 0
                var dataOff = -1L; var dataLen = 0L
                while (r.filePointer < r.length() - 8) {
                    val id = ByteArray(4)
                    if (r.read(id) != 4) break
                    val sz = Integer.reverseBytes(r.readInt()).toLong() and 0xFFFFFFFFL
                    when (String(id)) {
                        "fmt " -> {
                            fmt = r.readShort().toInt() and 0xFFFF
                            ch = r.readShort().toInt() and 0xFFFF
                            sr = Integer.reverseBytes(r.readInt())
                            r.skipBytes(6)
                            bits = r.readShort().toInt() and 0xFFFF
                            if (sz > 16) r.skipBytes((sz - 16).toInt())
                        }
                        "data" -> { dataOff = r.filePointer; dataLen = sz; r.skipBytes(sz.toInt()); break }
                        else -> r.skipBytes(sz.toInt() + (if (sz % 2 == 1L) 1 else 0).toInt())
                    }
                }
                if (dataOff < 0) null else Wav(sr, ch, bits, fmt, dataOff, dataLen, f)
            }
        } catch (e: Exception) { null }
    }

    /** décode le WAV en mono float [-1,1], resamplé à FM1_SR ; null si format non géré */
    fun decodeMono(f: File, targetSr: Int = FM1_SR): FloatArray? {
        val w = wavInfo(f) ?: return null
        if (w.format != 1 && w.format != 3) return null     // PCM ou IEEE float seulement
        return try {
            RandomAccessFile(f, "r").use { r ->
                r.seek(w.dataOff)
                val nBytes = w.dataLen.toInt().coerceAtMost((r.length() - w.dataOff).toInt())
                val raw = ByteArray(nBytes)
                r.readFully(raw)
                val bytesPerSample = w.bits / 8
                if (bytesPerSample <= 0) return null
                val frameBytes = bytesPerSample * w.channels
                val nFrames = nBytes / frameBytes
                val mono = FloatArray(nFrames)
                for (i in 0 until nFrames) {
                    var acc = 0f
                    for (c in 0 until w.channels) {
                        val o = i * frameBytes + c * bytesPerSample
                        val v = when {
                            w.format == 3 && w.bits == 32 -> java.lang.Float.intBitsToFloat(
                                (raw[o].toInt() and 0xFF) or ((raw[o + 1].toInt() and 0xFF) shl 8) or
                                    ((raw[o + 2].toInt() and 0xFF) shl 16) or ((raw[o + 3].toInt() and 0xFF) shl 24))
                            w.bits == 8 -> (raw[o].toInt() and 0xFF) / 128f - 1f
                            w.bits == 16 -> ((raw[o].toInt() and 0xFF) or ((raw[o + 1].toInt() and 0xFF) shl 8)).toShort() / 32768f
                            w.bits == 24 -> {
                                val v = (raw[o].toInt() and 0xFF) or ((raw[o + 1].toInt() and 0xFF) shl 8) or
                                    ((raw[o + 2].toInt() and 0xFF) shl 16)
                                (if (v and 0x800000 != 0) v or -0x1000000 else v) / 8388608f
                            }
                            w.bits == 32 -> ((raw[o].toInt() and 0xFF) or ((raw[o + 1].toInt() and 0xFF) shl 8) or
                                ((raw[o + 2].toInt() and 0xFF) shl 16) or ((raw[o + 3].toInt() and 0xFF) shl 24)) / 2147483648f
                            else -> 0f
                        }
                        acc += v
                    }
                    mono[i] = (acc / w.channels).coerceIn(-1f, 1f)
                }
                // resample linéaire mono -> targetSr
                if (w.sampleRate == targetSr) mono else {
                    val outN = (nFrames.toLong() * targetSr / w.sampleRate).toInt()
                    val out = FloatArray(outN)
                    for (i in 0 until outN) {
                        val pos = i.toDouble() * w.sampleRate / targetSr
                        val i0 = pos.toInt().coerceIn(0, nFrames - 1)
                        val i1 = (i0 + 1).coerceIn(0, nFrames - 1)
                        val fr = (pos - i0).toFloat()
                        out[i] = mono[i0] * (1f - fr) + mono[i1] * fr
                    }
                    out
                }
            }
        } catch (e: Exception) { null }
    }

    /** float [-1,1] -> int16 */
    fun toInt16(x: FloatArray): ShortArray {
        val s = ShortArray(x.size)
        for (i in x.indices) s[i] = (x[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
        return s
    }

    /** encode ZSM1 pour push FM-1, en tronquant pour tenir dans FM1_FILE_MAX ;
     *  fmt 0 = int16, fmt 1 = µ-law (plus long : ~2x plus d'échantillons dans la même place) */
    fun encodeZsm1ForFm1(pcm: FloatArray, sr: Int = FM1_SR, fmt: Int = 1): ByteArray {
        val s16 = toInt16(pcm)
        val maxSamples = if (fmt == 1) ZSM1_PAYLOAD_MAX else ZSM1_PAYLOAD_MAX / 2
        val n = minOf(s16.size, maxSamples).coerceAtLeast(64)
        return ZtfCodec.encodeZsm1(sr, fmt, s16.copyOf(n))
    }

    /** durée max poussable vers le FM-1 (secondes), selon le format */
    fun maxSeconds(fmt: Int): Float = (if (fmt == 1) ZSM1_PAYLOAD_MAX else ZSM1_PAYLOAD_MAX / 2) / FM1_SR.toFloat()

    /** réexporte un blob ZSM1 tiré du FM-1 en WAV PCM16 (pour écouter/partager) */
    fun zsm1ToWav(z: ZtfCodec.Zsm, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out ->
                val dataLen = z.samples.size * 2
                fun w32(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
                fun w16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
                out.write("RIFF".toByteArray()); w32(36 + dataLen); out.write("WAVE".toByteArray())
                out.write("fmt ".toByteArray()); w32(16); w16(1); w16(1); w32(z.sampleRate)
                w32(z.sampleRate * 2); w16(2); w16(16)
                out.write("data".toByteArray()); w32(dataLen)
                for (s in z.samples) w16(s.toInt())
            }
            true
        } catch (e: Exception) { false }
    }

    /** nom de fichier de pull pour un nom FM-1 (ZSMP00 -> ZSMP00.zsm1, etc.) */
    fun pullName(fm1Name: String): String = "$fm1Name.zlk"
}
