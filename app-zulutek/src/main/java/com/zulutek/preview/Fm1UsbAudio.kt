/* SPDX-License-Identifier: GPL-3.0-or-later — Copyright (C) 2026 FlyingZulu
 *
 * Fm1UsbAudio — pont audio hybride USB entre l'APK et le FM-1 (docs/FM1-APK-LINK.md §1) :
 *  - FM-1 -> téléphone : le FM-1 envoie son render sur l'hôte USB (usb_audio_in = true,
 *    uac_tap) ; l'APK enregistre via AudioRecord sur le device UAC (miroir/capture Koala-like).
 *  - téléphone -> FM-1 : l'APK joue via AudioTrack sur le device UAC-out ; le FM-1 mixe ça
 *    dans sa sortie (usb_audio_out = true, uac_play) et le hook app_usb_in (patch 0010
 *    jumeau fm1-base/felucca-bsp) alimente le ring resample du looper -> « le FM-1 sample
 *    via l'APK » en live.
 *  - le jack du FM-1 reste la sortie d'écoute analogique (monitoring).
 *
 * Note Android : pas d'API isochrone en host brut -> on passe par AudioRecord/AudioTrack
 * avec setPreferredDevice(device UAC) (API 23+). Tout est gardé en try/catch : si le
 * téléphone ne route pas l'audio USB, on retombe sur « pas de pont audio » sans crash.
 */
package com.zulutek.preview

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import kotlin.concurrent.thread

object Fm1UsbAudio {

    @Volatile var captureOn = false
        private set
    @Volatile var playOn = false
        private set
    @Volatile var usbDeviceName: String = ""
        private set

    /** callback de capture : PCM float mono [-1,1] venant du FM-1 (usb_audio_in) */
    var onCapture: ((FloatArray) -> Unit)? = null
    var onStatus: (String) -> Unit = {}

    private var capThread: Thread? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var playThread: Thread? = null
    private var playFile: File? = null

    private fun findUsbDevice(ctx: Context): AudioDeviceInfo? {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devs = am.getDevices(AudioManager.GET_DEVICES_ALL)
        return devs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }?.also {
            val pn = it.productName?.toString() ?: ""
            usbDeviceName = if (pn.isEmpty()) "USB audio" else pn
        }
    }

    private fun chanConfig(ch: Int) = if (ch == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
    private fun chanOut(ch: Int) = if (ch == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

    /** démarre la capture FM-1 -> APK ; sr = 44100 par défaut (ring FM-1 en 44,1 kHz) */
    fun startCapture(ctx: Context, sr: Int = 44100): Boolean {
        if (captureOn) return true
        return try {
            val dev = findUsbDevice(ctx)
            val useSr = sr
            val min = AudioRecord.getMinBufferSize(useSr, chanConfig(1), AudioFormat.ENCODING_PCM_FLOAT)
            if (min <= 0) { onStatus("capture: min buffer 0"); return false }
            val rec = AudioRecord(MediaRecorder.AudioSource.DEFAULT, useSr, chanConfig(1),
                AudioFormat.ENCODING_PCM_FLOAT, min * 2)
            if (Build.VERSION.SDK_INT >= 23 && dev != null) {
                if (!rec.setPreferredDevice(dev)) onStatus("capture: preferred device refusé (on tente quand même)")
            }
            rec.startRecording()
            record = rec
            captureOn = true
            capThread = thread(name = "fm1-uac-rx", start = true) {
                val buf = FloatArray(1024)
                while (captureOn) {
                    val n = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                    if (n > 0) {
                        val cb = onCapture
                        if (cb != null) cb(buf.copyOf(n)) else Unit
                    }
                }
                try { rec.stop() } catch (_: Exception) {}
            }
            onStatus("capture USB ${usbDeviceName.ifEmpty { "?" }} @ ${useSr} Hz")
            true
        } catch (e: Exception) {
            onStatus("capture: ${e.message}")
            false
        }
    }

    fun stopCapture() {
        captureOn = false
        val rec = record
        // AudioRecord.read(READ_BLOCKING) peut attendre : stop() le débloque.
        try { rec?.stop() } catch (_: Exception) {}
        try { capThread?.join(1000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        try { rec?.release() } catch (_: Exception) {}
        capThread = null
        record = null
    }

    /** joue un sample local (WAV) vers le FM-1 via UAC-out ; boucle jusqu'à stopPlay() */
    fun playSample(ctx: Context, f: File, sr: Int = 44100): Boolean {
        if (playOn) stopPlay()
        return try {
            val pcm = SampleLibrary.decodeMono(f, sr) ?: run { onStatus("play: WAV illisible"); return false }
            val dev = findUsbDevice(ctx)
            val min = AudioTrack.getMinBufferSize(sr, chanOut(1), AudioFormat.ENCODING_PCM_FLOAT)
            if (min <= 0) { onStatus("play: min buffer 0"); return false }
            val tr = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(chanOut(1)).build())
                .setBufferSizeInBytes(min * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            if (Build.VERSION.SDK_INT >= 23 && dev != null) {
                if (!tr.setPreferredDevice(dev)) onStatus("play: preferred device refusé (on tente quand même)")
            }
            tr.play()
            track = tr
            playFile = f
            playOn = true
            playThread = thread(name = "fm1-uac-tx", start = true) {
                val chunk = 2048
                var pos = 0
                val buf = FloatArray(chunk)
                while (playOn) {
                    var w = 0
                    while (w < chunk) {
                        val i = pos + w
                        buf[w] = if (i < pcm.size) pcm[i] else 0f
                        w++
                        pos++
                        if (pos >= pcm.size) pos = 0   // boucle le sample
                    }
                    tr.write(buf, 0, chunk, AudioTrack.WRITE_BLOCKING)
                }
                try { tr.stop() } catch (_: Exception) {}
            }
            onStatus("play -> FM-1 (${f.name})")
            true
        } catch (e: Exception) {
            onStatus("play: ${e.message}")
            false
        }
    }

    fun stopPlay() {
        playOn = false
        playThread?.join(300)
        playThread = null
        track?.release()
        track = null
        playFile = null
    }

    fun release() {
        stopCapture()
        stopPlay()
    }
}
