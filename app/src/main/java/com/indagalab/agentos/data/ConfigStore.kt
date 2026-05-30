package com.indagalab.agentos.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persistencia de la configuración del agente (token + variables de entorno).
 *
 * Cifrada en reposo con EncryptedSharedPreferences: clave maestra AES-256-GCM
 * en el Android Keystore (respaldado por hardware en la mayoría de equipos).
 * Migra automáticamente los valores del store plano anterior ("agentos_cfg") y
 * lo limpia. Si el Keystore del dispositivo falla (algunos OEM tienen bugs),
 * cae a SharedPreferences en claro para no dejar la app inutilizable.
 */
class ConfigStore(context: Context) {
    private val app = context.applicationContext
    private val p: SharedPreferences = openPrefs(app)

    private fun openPrefs(ctx: Context): SharedPreferences = try {
        val key = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val enc = EncryptedSharedPreferences.create(
            ctx,
            ENC_PREFS,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        migrateFromPlain(ctx, enc)
        enc
    } catch (e: Exception) {
        Log.e("ConfigStore", "Keystore no disponible, uso prefs en claro: ${e.message}", e)
        ctx.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE)
    }

    /** Copia token/env/flags del store plano viejo al cifrado una sola vez, y limpia el plano. */
    private fun migrateFromPlain(ctx: Context, enc: SharedPreferences) {
        if (enc.getBoolean(MIGRATED, false)) return
        val plain = ctx.getSharedPreferences(PLAIN_PREFS, Context.MODE_PRIVATE)
        val e = enc.edit()
        plain.getString("token", null)?.let { e.putString("token", it) }
        plain.getString("env", null)?.let { e.putString("env", it) }
        if (plain.contains("onboarded")) e.putBoolean("onboarded", plain.getBoolean("onboarded", false))
        if (plain.contains("autostart")) e.putBoolean("autostart", plain.getBoolean("autostart", true))
        e.putBoolean(MIGRATED, true).apply()
        plain.edit().clear().apply() // borra los secretos en claro
    }

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

    /** Si el agente debe re-arrancar solo tras reiniciar el teléfono (BootReceiver). */
    var autostart: Boolean
        get() = p.getBoolean("autostart", true)
        set(v) { p.edit().putBoolean("autostart", v).apply() }

    companion object {
        private const val ENC_PREFS = "agentos_cfg_enc"
        private const val PLAIN_PREFS = "agentos_cfg"
        private const val MIGRATED = "_migrated_from_plain"
    }
}
