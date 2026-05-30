package com.indagalab.agentos.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.indagalab.agentos.data.ConfigStore

/**
 * Re-arranca AgentService tras reiniciar el teléfono, para cumplir el 24/7.
 *
 * Requiere el permiso RECEIVE_BOOT_COMPLETED (declarado en el manifest) y que el
 * usuario ya haya configurado el bot (token presente) con `autostart` activo.
 * Arrancar un Foreground Service desde BOOT_COMPLETED es una excepción permitida
 * a las restricciones de inicio en segundo plano.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON", // algunos OEM (HTC/Huawei) usan este
            -> {
                val store = ConfigStore(context)
                if (store.token.isBlank() || !store.autostart) return
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AgentService::class.java),
                )
            }
        }
    }
}
