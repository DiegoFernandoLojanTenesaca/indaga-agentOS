package com.indagalab.agentos.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.BatteryCharging
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Wifi
import org.json.JSONObject
import java.time.LocalDate

// ---------------------------------------------------------------- lectura ---

private data class Hardware(
    val bateria: Int,
    val cargando: Boolean,
    val temperatura: Float,
    val ramLibre: Long,
    val ramTotal: Long,
    val discoLibre: Long,
    val discoTotal: Long,
    val red: String,
)

private fun leerHardware(ctx: Context): Hardware {
    val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val nivel = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val escala = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val estado = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

    val st = StatFs(Environment.getDataDirectory().path)
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)

    return Hardware(
        bateria = if (nivel >= 0) nivel * 100 / escala else -1,
        cargando = estado == BatteryManager.BATTERY_STATUS_CHARGING ||
            estado == BatteryManager.BATTERY_STATUS_FULL,
        temperatura = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
        ramLibre = mem.availMem,
        ramTotal = mem.totalMem,
        discoLibre = st.availableBytes,
        discoTotal = st.totalBytes,
        red = when {
            caps == null -> "sin conexión"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "datos móviles"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "conectado"
        },
    )
}

private fun gb(bytes: Long): String =
    if (bytes >= 1_073_741_824L) "%.1f GB".format(bytes / 1_073_741_824.0)
    else "%.0f MB".format(bytes / 1_048_576.0)

/** Permisos que el agente necesita para sus capacidades. */
private val PERMISOS = listOf(
    "Cámara" to android.Manifest.permission.CAMERA,
    "Ubicación" to android.Manifest.permission.ACCESS_FINE_LOCATION,
    "Micrófono" to android.Manifest.permission.RECORD_AUDIO,
    "SMS" to android.Manifest.permission.READ_SMS,
    "Contactos" to android.Manifest.permission.READ_CONTACTS,
    "Llamadas" to android.Manifest.permission.CALL_PHONE,
)

// ------------------------------------------------------------ dispositivo ---

@Composable
internal fun ModDispositivo(running: Boolean) {
    val ctx = LocalContext.current
    var hw by remember { mutableStateOf<Hardware?>(null) }
    var uptimeAgente by remember { mutableStateOf(0L) }

    // Se refresca solo: un panel de estado que miente sobre la batería no sirve.
    LaunchedEffect(Unit) {
        while (true) {
            hw = runCatching { leerHardware(ctx) }.getOrNull()
            uptimeAgente = runCatching {
                JSONObject(Python.getInstance().getModule("jarvis").callAttr("info").toString())
                    .optLong("uptime_s")
            }.getOrDefault(0L)
            kotlinx.coroutines.delay(4000)
        }
    }

    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }
    val pyVer = remember {
        runCatching {
            Python.getInstance().getModule("sys").get("version").toString().substringBefore(" ")
        }.getOrNull() ?: "—"
    }

    ColumnaModulo {
        PixelWindow("Estado ahora", icon = Lucide.Gauge) {
            val h = hw
            if (h == null) {
                Text("Leyendo el teléfono…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Medidor(
                    "Batería",
                    if (h.bateria < 0) "—" else "${h.bateria}%" +
                        (if (h.cargando) " · cargando" else "") +
                        (if (h.temperatura > 0) " · ${"%.0f".format(h.temperatura)}°C" else ""),
                    h.bateria / 100f,
                    when {
                        h.cargando -> Color(0xFF6FBF4A)
                        h.bateria < 20 -> Color(0xFFB03A3A)
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Medidor(
                    "Memoria",
                    "${gb(h.ramTotal - h.ramLibre)} de ${gb(h.ramTotal)}",
                    if (h.ramTotal > 0) 1f - h.ramLibre.toFloat() / h.ramTotal else 0f,
                    MaterialTheme.colorScheme.primary,
                )
                Medidor(
                    "Almacenamiento",
                    "${gb(h.discoTotal - h.discoLibre)} de ${gb(h.discoTotal)}",
                    if (h.discoTotal > 0) 1f - h.discoLibre.toFloat() / h.discoTotal else 0f,
                    MaterialTheme.colorScheme.primary,
                )
                Fila("Red", h.red, Lucide.Wifi)
                Fila(
                    "Agente",
                    if (running) "en marcha · ${duracion(uptimeAgente)}" else "detenido",
                    Lucide.Activity,
                )
            }
        }

        PixelWindow("Permisos", icon = Lucide.ShieldCheck) {
            Text(
                "Cada permiso que falta apaga una capacidad. Sin cámara no hay fotos " +
                    "ni vigilancia; sin ubicación no hay rastreo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PERMISOS.forEach { (etiqueta, permiso) ->
                val ok = ctx.checkSelfPermission(permiso) == PackageManager.PERMISSION_GRANTED
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(etiqueta, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (ok) "concedido" else "falta",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ok) Color(0xFF6FBF4A) else MaterialTheme.colorScheme.error,
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Abrir permisos de la app") }
        }

        PixelWindow("Ficha técnica", icon = Lucide.Smartphone) {
            Fila("Teléfono", "${Build.MANUFACTURER} ${Build.MODEL}", null)
            Fila("Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}", null)
            Fila("Arquitectura", Build.SUPPORTED_ABIS.firstOrNull() ?: "—", null)
            Fila("Núcleos", Runtime.getRuntime().availableProcessors().toString(), null)
            Fila("Encendido", duracion(SystemClock.elapsedRealtime() / 1000), null)
            Fila("AgentOS", version, null)
            Fila("Python", pyVer, null)
        }
    }
}

// --------------------------------------------------------------- actividad --

@Composable
internal fun ModActividad(info: String, running: Boolean) {
    var heat by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var uso by remember { mutableStateOf<List<Triple<String, Int, Long>>>(emptyList()) }

    LaunchedEffect(Unit) {
        val py = runCatching { Python.getInstance().getModule("jarvis") }.getOrNull()
        heat = runCatching {
            val o = JSONObject(py!!.callAttr("heatmap").toString())
            o.keys().asSequence().associateWith { o.optInt(it) }
        }.getOrDefault(emptyMap())
        uso = runCatching {
            val o = JSONObject(py!!.callAttr("usage").toString())
            o.keys().asSequence().map { k ->
                val v = o.getJSONObject(k)
                Triple(k, v.optInt("req"), v.optLong("in") + v.optLong("out"))
            }.sortedByDescending { it.second }.toList()
        }.getOrDefault(emptyList())
    }

    val hoy = remember { LocalDate.now() }
    val total = heat.values.sum()
    val ultimos30 = remember(heat) {
        (0 until 30).sumOf { heat[hoy.minusDays(it.toLong()).toString()] ?: 0 }
    }
    val racha = remember(heat) {
        var n = 0
        // Hoy puede llevar 0 aún: la racha se cuenta desde ayer para no romperse
        // cada madrugada.
        var d = if ((heat[hoy.toString()] ?: 0) > 0) hoy else hoy.minusDays(1)
        while ((heat[d.toString()] ?: 0) > 0) { n++; d = d.minusDays(1) }
        n
    }
    val mejor = heat.maxByOrNull { it.value }

    ColumnaModulo {
        PixelWindow("Resumen", icon = Lucide.Gauge) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Contador("Últimos 30 días", ultimos30.toString(), Modifier.weight(1f))
                Contador("Racha", if (racha == 0) "—" else "$racha d", Modifier.weight(1f))
                Contador("Total", total.toString(), Modifier.weight(1f))
            }
            mejor?.let {
                Fila("Día más movido", "${it.key} · ${it.value} peticiones", null)
            }
            val st = remember(info) { runCatching { JSONObject(info) }.getOrNull() }
            if (running && st != null) {
                Fila("Proveedor", st.optString("provider").ifBlank { "—" }, null)
                Fila("Modelo", st.optString("model").ifBlank { "—" }, null)
                Fila("En marcha desde hace", duracion(st.optLong("uptime_s")), null)
                Fila("Tokens de esta sesión", st.optLong("tokens").toString(), null)
            }
        }

        PixelWindow("Días", icon = Lucide.Activity) {
            Text(
                "Peticiones por día en las últimas 26 semanas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Heatmap(heat, hoy)
            if (total == 0) {
                Text(
                    "Todavía no hay nada que contar. Arranca el agente y escríbele.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uso.isNotEmpty()) {
            PixelWindow("Consumo por proveedor", icon = Lucide.Cpu) {
                val maxReq = uso.maxOf { it.second }.coerceAtLeast(1)
                uso.forEach { (nombre, req, tokens) ->
                    Medidor(nombre, "$req peticiones · $tokens tokens",
                        req.toFloat() / maxReq, MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ piezas --

@Composable
private fun Medidor(titulo: String, valor: String, fraccion: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium)
            Text(valor, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(fraccion.coerceIn(0f, 1f)).height(9.dp)
                    .clip(RoundedCornerShape(3.dp)).background(color)
            )
        }
    }
}

@Composable
private fun Contador(titulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(valor, style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
        Text(titulo, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Fila(titulo: String, valor: String, icono: androidx.compose.ui.graphics.vector.ImageVector?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            icono?.let {
                androidx.compose.material3.Icon(it, null, Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(titulo, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Heatmap(map: Map<String, Int>, hoy: LocalDate) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (semana in 0 until 26) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (dia in 0 until 7) {
                    val i = semana * 7 + dia
                    val fecha = hoy.minusDays((181 - i).toLong()).toString()
                    val n = map[fecha] ?: 0
                    val color = when {
                        n <= 0 -> MaterialTheme.colorScheme.surfaceVariant
                        n < 3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                        n < 8 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        n < 20 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(color))
                }
            }
        }
    }
}

/** "2 h 14 min" a partir de segundos. */
internal fun duracion(segundos: Long): String = when {
    segundos <= 0 -> "—"
    segundos < 60 -> "$segundos s"
    segundos < 3600 -> "${segundos / 60} min"
    segundos < 86400 -> "${segundos / 3600} h ${(segundos % 3600) / 60} min"
    else -> "${segundos / 86400} d ${(segundos % 86400) / 3600} h"
}
