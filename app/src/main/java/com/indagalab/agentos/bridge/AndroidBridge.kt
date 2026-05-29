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
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
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
                "/gps" -> ok(gpsJson())
                "/sms" -> {
                    val to = p("to"); val msg = p("message")
                    if (to != null && msg != null) { sendSms(to, msg); ok("""{"ok":true}""") }
                    else ok(smsListJson(p("limit")?.toIntOrNull() ?: 10))
                }
                "/call" -> { call(p("number").orEmpty()); ok("""{"ok":true}""") }
                "/volume" -> {
                    val s = p("stream"); val l = p("level")?.toIntOrNull()
                    if (s != null && l != null) setVol(s, l)
                    ok(volJson())
                }
                "/camera" -> {
                    val facing = if (p("facing") == "front") CameraCharacteristics.LENS_FACING_FRONT
                    else CameraCharacteristics.LENS_FACING_BACK
                    val path = p("path") ?: (ctx.filesDir.absolutePath + "/jarvis/cam.jpg")
                    ok(CameraCapture.capture(ctx, facing, path))
                }
                "/logs" -> ok(
                    JSONObject().put(
                        "logs",
                        runCatching {
                            com.chaquo.python.Python.getInstance().getModule("jarvis").callAttr("get_logs").toString()
                        }.getOrDefault(""),
                    ).toString(),
                )
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

    private fun has(perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun gpsJson(): String {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION) && !has(Manifest.permission.ACCESS_COARSE_LOCATION))
            return """{"error":"sin permiso de ubicación"}"""
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            ?: runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        return if (loc == null) """{"error":"sin ubicación reciente"}"""
        else JSONObject().put("latitude", loc.latitude).put("longitude", loc.longitude)
            .put("accuracy", loc.accuracy).put("provider", loc.provider).toString()
    }

    @Suppress("DEPRECATION")
    private fun sendSms(to: String, msg: String) {
        if (!has(Manifest.permission.SEND_SMS)) return
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()
        sm.sendTextMessage(to, null, msg, null, null)
    }

    private fun smsListJson(limit: Int): String {
        if (!has(Manifest.permission.READ_SMS)) return """{"error":"sin permiso de SMS"}"""
        val arr = JSONArray()
        ctx.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body", "date"), null, null, "date DESC",
        )?.use { c ->
            var n = 0
            while (c.moveToNext() && n < limit) {
                arr.put(
                    JSONObject().put("number", c.getString(0)).put("body", c.getString(1)).put("received", c.getLong(2)),
                )
                n++
            }
        }
        return arr.toString()
    }

    private fun call(number: String) {
        if (!has(Manifest.permission.CALL_PHONE) || number.isBlank()) return
        ctx.startActivity(
            Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun volJson(): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return JSONObject()
            .put("music", am.getStreamVolume(AudioManager.STREAM_MUSIC))
            .put("max", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            .toString()
    }

    private fun setVol(stream: String, level: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val s = when (stream) {
            "alarm" -> AudioManager.STREAM_ALARM
            "ring" -> AudioManager.STREAM_RING
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        am.setStreamVolume(s, level, 0)
    }

    fun shutdown() {
        runCatching { tts.stop(); tts.shutdown() }
        runCatching { stop() }
    }
}
