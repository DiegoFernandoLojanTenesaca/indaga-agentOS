package com.indagalab.agentos.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.util.Locale

/**
 * Puente HTTP local (127.0.0.1:8765) que expone el hardware del teléfono al
 * agente Python. Sustituye a los comandos `termux-*`. El cliente Python
 * (bridge_client.py) habla con estos endpoints.
 *
 * Fase 1b-1: endpoints sin permisos peligrosos (batería, linterna, vibración,
 * TTS, notificación, portapapeles). Cámara/GPS/SMS/llamada/mic → Fase 1b-2.
 */
class AndroidBridge(private val ctx: Context, port: Int = 8765) : NanoHTTPD("127.0.0.1", port) {

    @Volatile private var ttsReady = false
    private var notifId = 1000
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("es", "ES")
    }

    override fun serve(session: IHTTPSession): Response {
        fun p(k: String): String? = session.parameters[k]?.firstOrNull()
        return try {
            when (session.uri.trimEnd('/')) {
                "/health" -> ok("""{"ok":true,"bridge":"AgentOS"}""")
                "/battery" -> ok(batteryJson())
                "/vibrate" -> { vibrate(p("ms")?.toLongOrNull() ?: 500L); ok("""{"ok":true}""") }
                "/torch" -> { torch(p("on") == "true"); ok("""{"ok":true}""") }
                "/tts" -> { speak(p("text").orEmpty()); ok("""{"ok":true}""") }
                "/notify" -> { notify(p("title").orEmpty(), p("content").orEmpty()); ok("""{"ok":true}""") }
                "/clipboard" -> {
                    val set = p("set")
                    if (set != null) { setClip(set); ok("""{"ok":true}""") }
                    else ok(JSONObject().put("text", getClip()).toString())
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"error":"not found"}""")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", """{"error":"${e.message}"}""")
        }
    }

    private fun ok(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", body)

    private fun batteryJson(): String {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val sticky = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val statusInt = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            else -> "UNKNOWN"
        }
        val plugged = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return JSONObject()
            .put("percentage", pct)
            .put("status", status)
            .put("temperature", temp)
            .put("plugged", if (plugged) "PLUGGED_AC" else "UNPLUGGED")
            .toString()
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun torch(on: Boolean) {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        cm.setTorchMode(id, on)
    }

    private fun speak(text: String) {
        if (ttsReady && text.isNotBlank()) {
            tts.speak(text.take(1500), TextToSpeech.QUEUE_FLUSH, null, "agentos")
        }
    }

    private fun notify(title: String, content: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = "agentos_bridge"
        nm.createNotificationChannel(
            NotificationChannel(ch, "Avisos del agente", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val n = Notification.Builder(ctx, ch)
            .setContentTitle(title.ifBlank { "AgentOS" })
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        nm.notify(notifId++, n)
    }

    private fun getClip(): String {
        val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
    }

    private fun setClip(text: String) {
        val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("agentos", text))
    }

    fun shutdown() {
        runCatching { tts.stop(); tts.shutdown() }
        runCatching { stop() }
    }
}
