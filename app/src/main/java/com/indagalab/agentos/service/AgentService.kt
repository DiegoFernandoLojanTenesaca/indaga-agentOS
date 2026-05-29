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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra(EXTRA_TOKEN).orEmpty()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        acquireWakeLock()

        if (token.isNotBlank()) {
            try {
                Python.getInstance().getModule("jarvis").callAttr("start", token)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to start jarvis: ${e.message}", e)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            Python.getInstance().getModule("jarvis").callAttr("stop")
        } catch (_: Exception) { /* runtime may already be gone */ }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
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

    companion object {
        const val EXTRA_TOKEN = "token"
        private const val TAG = "AgentService"
        private const val CHANNEL_ID = "agentos_service"
        private const val NOTIF_ID = 1
    }
}
