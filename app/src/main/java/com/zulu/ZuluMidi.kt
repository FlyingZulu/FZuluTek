package com.zulu

import android.content.Context
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiOutputPort

object ZuluMidi {
    @Volatile var enabled = false
    @Volatile var teach = false
    @Volatile var globalChannel = 0
    @Volatile var nextCc = 20
    val outMap = HashMap<String, Pair<Int, Int>>()
    var onTeach: ((String, Int) -> Unit)? = null

    private val inputs = ArrayList<MidiInputPort>()
    private val outputs = ArrayList<MidiOutputPort>()

    fun start(ctx: Context) {
        enabled = true
        refresh(ctx)
    }

    fun stop() {
        enabled = false
        for (p in inputs) try { p.close() } catch (e: Exception) {}
        for (p in outputs) try { p.close() } catch (e: Exception) {}
        inputs.clear()
        outputs.clear()
    }

    fun refresh(ctx: Context) {
        for (p in inputs) try { p.close() } catch (e: Exception) {}
        for (p in outputs) try { p.close() } catch (e: Exception) {}
        inputs.clear()
        outputs.clear()
        val mgr = ctx.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return
        for (info in mgr.devices) {
            mgr.openDevice(info, { device ->
                if (device == null) return@openDevice
                for (i in 0 until info.inputPortCount) {
                    device.openInputPort(i)?.let { inputs.add(it) }
                }
            }, null)
        }
    }

    fun deviceNames(ctx: Context): List<String> {
        val mgr = ctx.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return emptyList()
        return mgr.devices.map { it.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "?" }
    }

    fun sendCc(cc: Int, ch: Int, v: Int) {
        val msg = byteArrayOf((0xB0 or (ch and 15)).toByte(), (cc and 127).toByte(), (v and 127).toByte())
        for (p in inputs) try { p.send(msg, 0, msg.size) } catch (e: Exception) {}
    }

    fun control(key: String, v: Float) {
        if (teach) {
            if (!outMap.containsKey(key)) {
                val cc = nextCc
                nextCc = (nextCc + 1).coerceAtMost(119)
                outMap[key] = cc to -1
                onTeach?.invoke(key, cc)
            }
            teach = false
            return
        }
        val m = outMap[key] ?: return
        if (!enabled) return
        sendCc(m.first, if (m.second < 0) globalChannel else m.second, (v * 127).toInt().coerceIn(0, 127))
    }

    fun clear() {
        outMap.clear()
        nextCc = 20
    }
}
