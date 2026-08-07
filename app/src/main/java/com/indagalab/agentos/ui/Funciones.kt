package com.indagalab.agentos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.app.Activity
import com.chaquo.python.Python
import com.indagalab.agentos.data.ConfigStore
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Search
import com.indagalab.agentos.ui.pixel.SPRITE_ROBOT
import com.indagalab.agentos.ui.pixel.drawSprite
import org.json.JSONArray

private data class SkillInfo(
    val nombre: String,
    val estado: String,
    val motivo: String,
    val comandos: List<String>,
    val descripcion: String,
    val origen: String,
)

private fun leerSkills(): List<SkillInfo> = runCatching {
    val arr = JSONArray(Python.getInstance().getModule("jarvis").callAttr("skills").toString())
    (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
        val cmds = o.optJSONArray("commands")
        SkillInfo(
            nombre = o.optString("name"),
            estado = o.optString("status"),
            motivo = o.optString("reason"),
            comandos = (0 until (cmds?.length() ?: 0)).map { cmds!!.getString(it) },
            descripcion = o.optString("desc"),
            origen = o.optString("source"),
        )
    }
}.getOrDefault(emptyList())

// ------------------------------------------------------------- funciones ---

@Composable
internal fun ModFunciones() {
    var busca by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf<List<SkillInfo>>(emptyList()) }
    LaunchedEffect(Unit) { skills = leerSkills() }

    val capacidades = remember(busca) {
        CAPABILITIES.filter {
            busca.isBlank() || it.label.contains(busca, true) || it.desc.contains(busca, true)
        }
    }
    val skillsVisibles = remember(skills, busca) {
        skills.filter {
            busca.isBlank() || it.nombre.contains(busca, true) ||
                it.descripcion.contains(busca, true) ||
                it.comandos.any { c -> c.contains(busca, true) }
        }
    }

    ColumnaModulo {
        PixelWindow("Lo que sabe hacer", icon = Lucide.LayoutGrid) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Numero("Capacidades", "${CAPABILITIES.size}", Modifier.weight(1f))
                Numero("Skills", "${skills.size}", Modifier.weight(1f))
                Numero("Comandos", "${skills.sumOf { it.comandos.size }}", Modifier.weight(1f))
            }
            Text(
                "Todo se maneja desde el chat de Telegram. Los skills se pueden añadir " +
                    "sin tocar la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                label = { Text("Buscar") },
                leadingIcon = { Icon(Lucide.Search, null, Modifier.size(17.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (skillsVisibles.isNotEmpty()) {
            PixelWindow("Skills instalados", icon = Lucide.Package) {
                skillsVisibles.forEach { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.nombre, style = MaterialTheme.typography.titleSmall)
                            val (txt, col) = when (s.estado) {
                                "ok" -> "cargado" to Color(0xFF6FBF4A)
                                "error" -> "falló" to MaterialTheme.colorScheme.error
                                "skipped" -> "apagado" to MaterialTheme.colorScheme.onSurfaceVariant
                                else -> "en reposo" to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(txt, style = MaterialTheme.typography.labelSmall, color = col)
                        }
                        if (s.descripcion.isNotBlank()) {
                            Text(s.descripcion, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (s.motivo.isNotBlank()) {
                            Text(s.motivo, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (s.comandos.isNotEmpty()) {
                            Text(
                                s.comandos.joinToString("  ") { "/$it" },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        capacidades.forEach { cap -> TarjetaCapacidad(cap) }

        if (capacidades.isEmpty() && skillsVisibles.isEmpty()) {
            PixelWindow("Sin resultados", icon = Lucide.Search) {
                Text("Nada coincide con \"$busca\".",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TarjetaCapacidad(cap: Capability) {
    PixelWindow(cap.label, icon = cap.icon) {
        Text(cap.desc, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- acerca ---

private val CREDITOS = listOf(
    "llama.cpp" to "https://github.com/ggml-org/llama.cpp",
    "Chaquopy" to "https://chaquo.com/chaquopy/",
    "SeekerClaw" to "https://github.com/sepivip/SeekerClaw",
    "PalsHub" to "https://palshub.ai",
)

@Composable
internal fun ModAcerca() {
    val ctx = LocalContext.current
    var skills by remember { mutableStateOf(0) }
    var proveedores by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        skills = leerSkills().size
        // Contado, no escrito a mano: la lista de proveedores ya ha crecido dos
        // veces y el número de esta pantalla se quedó viejo las dos.
        proveedores = runCatching {
            org.json.JSONArray(
                Python.getInstance().getModule("jarvis").callAttr("proveedores", "").toString()
            ).length()
        }.getOrDefault(0)
    }

    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }

    ColumnaModulo {
        PixelWindow("AgentOS", icon = Lucide.Info) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Canvas(Modifier.size(64.dp)) {
                    val e = (size.width / SPRITE_ROBOT.w).toInt().coerceAtLeast(1)
                    drawSprite(
                        SPRITE_ROBOT,
                        Offset((size.width - SPRITE_ROBOT.w * e) / 2f,
                            (size.height - SPRITE_ROBOT.h * e) / 2f),
                        e,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("AgentOS", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text("versión $version", style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("por Indaga Lab", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Convierte un teléfono viejo en un agente que trabaja solo y responde " +
                    "por Telegram. Sin servicios de Google y sin servidor: el cerebro " +
                    "puede correr en el propio teléfono.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        PixelWindow("En este teléfono", icon = Lucide.Package) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Numero("Skills", "$skills", Modifier.weight(1f))
                Numero("Capacidades", "${CAPABILITIES.size}", Modifier.weight(1f))
                Numero("Proveedores", "$proveedores", Modifier.weight(1f))
            }
            OutlinedButton(
                // Vuelve a la portada: es donde están explicados los permisos,
                // y sin esto no hay forma de leerla otra vez salvo reinstalar.
                onClick = {
                    ConfigStore(ctx).onboarded = false
                    (ctx as? Activity)?.recreate()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
            ) {
                Icon(Lucide.Sparkles, null, Modifier.size(15.dp))
                Text("  Ver la bienvenida otra vez")
            }
        }

        PixelWindow("Hecho con", icon = Lucide.ExternalLink) {
            Text(
                "Software libre de otros que hace posible esto:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CREDITOS.forEach { (nombre, url) ->
                OutlinedButton(
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url),
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                ) {
                    Icon(Lucide.ExternalLink, null, Modifier.size(15.dp))
                    Text("  $nombre")
                }
            }
        }
    }
}

@Composable
private fun Numero(titulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp))
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
