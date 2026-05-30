package com.indagalab.agentos.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python

/**
 * Foreground service that keeps the embedded Jarvis (Python) agent alive 24/7.
 * Phase 0: starts the Telegram long-poll loop from jarvis.py on a Python thread
 * and holds a partial wake lock. Watchdog / boot-restart come in later phases.
 */
class AgentService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var bridge: com.indagalab.agentos.bridge.AndroidBridge? = null
    private var watchdog: android.os.Handler? = null
    private var watchdogThread: android.os.HandlerThread? = null
    private var lastConfigJson: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // La config persistida (ConfigStore) es la fuente; los extras la sobreescriben si vienen.
        val store = com.indagalab.agentos.data.ConfigStore(this)
        val token = intent?.getStringExtra(EXTRA_TOKEN)?.takeIf { it.isNotBlank() } ?: store.token
        val envBlob = intent?.getStringExtra(EXTRA_ENV) ?: store.envBlob

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        acquireWakeLock()
        startBridge()

        if (token.isNotBlank()) {
            val cfg = buildConfigJson(token, envBlob)
            lastConfigJson = cfg
            try {
                Python.getInstance().getModule("jarvis").callAttr("start", cfg)
                AgentState.running.value = true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start jarvis: ${e.message}", e)
            }
            startWatchdog()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopWatchdog()
        try {
            Python.getInstance().getModule("jarvis").callAttr("stop")
        } catch (_: Exception) { /* runtime may already be gone */ }
        AgentState.running.value = false
        bridge?.shutdown(); bridge = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    /** JSON de config para jarvis.start(): AGENTOS_HOME + token + env vars (KEY=VALOR por línea). */
    private fun buildConfigJson(token: String, envBlob: String): String {
        val o = org.json.JSONObject()
        o.put("AGENTOS_HOME", java.io.File(filesDir, "jarvis").absolutePath)
        o.put("TELEGRAM_TOKEN", token)
        envBlob.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) return@forEach
            val k = line.substringBefore("=").trim()
            val v = line.substringAfter("=").trim()
            if (k.isNotEmpty() && k != "TELEGRAM_TOKEN") o.put(k, v)
        }
        return o.toString()
    }

    private fun startBridge() {
        if (bridge == null) {
            try {
                bridge = com.indagalab.agentos.bridge.AndroidBridge(applicationContext).also { it.start() }
                android.util.Log.i(TAG, "AndroidBridge escuchando en 127.0.0.1:8765")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "No se pudo iniciar el bridge: ${e.message}", e)
            }
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AgentOS::agent").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, "AgentOS", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Agente activo en segundo plano" }
        mgr.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AgentOS activo")
            .setContentText("Tu agente está corriendo 24/7")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    // ---------- Watchdog: revive a Jarvis si el hilo muere o se cuelga ----------
    private fun startWatchdog() {
        if (watchdogThread != null) return
        val t = android.os.HandlerThread("agentos-watchdog").also { it.start() }
        watchdogThread = t
        val h = android.os.Handler(t.looper)
        watchdog = h
        val tick = object : Runnable {
            override fun run() {
                try {
                    checkAgentHealth()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "watchdog: ${e.message}", e)
                }
                h.postDelayed(this, WATCHDOG_INTERVAL_MS)
            }
        }
        h.postDelayed(tick, WATCHDOG_INTERVAL_MS)
    }

    private fun stopWatchdog() {
        watchdog?.removeCallbacksAndMessages(null)
        watchdogThread?.quitSafely()
        watchdog = null
        watchdogThread = null
    }

    private fun checkAgentHealth() {
        if (!AgentState.running.value) return
        val py = Python.getInstance().getModule("jarvis")
        if (!py.callAttr("is_alive").toBoolean()) {
            android.util.Log.w(TAG, "watchdog: hilo Jarvis caído → restart suave")
            lastConfigJson?.let { py.callAttr("start", it) }
            return
        }
        val beat = py.callAttr("last_beat").toDouble()
        val idle = System.currentTimeMillis() / 1000.0 - beat
        if (beat > 0 && idle > WATCHDOG_STALE_SEC) {
            android.util.Log.w(TAG, "watchdog: Jarvis colgado (${idle.toInt()}s sin latido) → reinicio de proceso")
            // El hilo Python está vivo pero atascado; CPython no permite matarlo
            // limpio. Matamos el proceso: START_STICKY revive el servicio con un
            // runtime Python fresco (onStartCommand relee el token del ConfigStore).
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    companion object {
        const val EXTRA_TOKEN = "token"
        const val EXTRA_ENV = "env"
        private const val TAG = "AgentService"
        private const val CHANNEL_ID = "agentos_service"
        private const val NOTIF_ID = 1
        private const val WATCHDOG_INTERVAL_MS = 30_000L
        private const val WATCHDOG_STALE_SEC = 120.0
    }
}
