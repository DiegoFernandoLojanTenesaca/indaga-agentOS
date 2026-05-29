package com.indagalab.agentos.data

import android.content.Context

/**
 * Persistencia de la configuración del agente (token + variables de entorno).
 * Guardado en SharedPreferences privado de la app (solo accesible por la app
 * en un dispositivo sin root).
 *
 * TODO Fase 2b: migrar a EncryptedSharedPreferences (Android Keystore) para
 * cifrar las API keys en reposo.
 */
class ConfigStore(context: Context) {
    private val p = context.applicationContext
        .getSharedPreferences("agentos_cfg", Context.MODE_PRIVATE)

    var token: String
        get() = p.getString("token", "").orEmpty()
        set(v) { p.edit().putString("token", v).apply() }

    var envBlob: String
        get() = p.getString("env", "").orEmpty()
        set(v) { p.edit().putString("env", v).apply() }

    val configured: Boolean get() = token.isNotBlank()

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }
}
