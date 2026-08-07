package com.indagalab.agentos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// -------------------------------------------------------------------- bot ---

private data class Bot(val ok: Boolean, val usuario: String, val nombre: String, val detalle: String)

@Composable
internal fun ModBot(token: String, onTokenChange: (String) -> Unit, onSave: () -> Unit) {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    var guardado by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var pasos by remember { mutableStateOf(false) }
    var bot by remember { mutableStateOf<Bot?>(null) }
    var comprobando by remember { mutableStateOf(false) }

    fun comprobar() {
        comprobando = true
        bot = null
        alcance.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    JSONObject(
                        Python.getInstance().getModule("jarvis")
                            .callAttr("verificar_bot", token.trim()).toString()
                    )
                }.getOrNull()
            }
            bot = r?.let {
                Bot(it.optBoolean("ok"), it.optString("usuario"),
                    it.optString("nombre"), it.optString("detalle"))
            } ?: Bot(false, "", "", "no se pudo comprobar")
            comprobando = false
        }
    }

    ColumnaModulo {
        PixelWindow("Bot de Telegram", icon = Lucide.Bot) {
            OutlinedTextField(
                value = token,
                onValueChange = { onTokenChange(it); guardado = false; bot = null },
                label = { Text("Token del bot") },
                placeholder = { Text("123456789:AAE...") },
                singleLine = true,
                visualTransformation =
                    if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Lucide.EyeOff else Lucide.Eye,
                            if (visible) "Ocultar" else "Ver",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Comprobar antes de guardar: Telegram dice en un segundo si el
            // token vale, en vez de descubrirlo tras arrancar el agente.
            bot?.let { b ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        if (b.ok) Lucide.Check else Lucide.X, null, Modifier.size(16.dp),
                        tint = if (b.ok) Color(0xFF6FBF4A) else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        if (b.ok) "es @${b.usuario} · ${b.nombre}" else b.detalle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (b.ok) Color(0xFF6FBF4A) else MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { comprobar() },
                    enabled = token.isNotBlank() && !comprobando,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    if (comprobando) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        Text("  Probando")
                    } else {
                        Text("Comprobar")
                    }
                }
                Button(
                    onClick = { onSave(); guardado = true },
                    enabled = token.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Lucide.Save, null, Modifier.size(16.dp))
                    Text("  Guardar")
                }
            }
            if (guardado) {
                Text("Guardado. Ya puedes iniciar el agente desde la sala.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        PixelWindow("Cómo se consigue", icon = Lucide.Settings) {
            Desplegable("Los cuatro pasos", pasos) { pasos = !pasos }
            if (pasos) {
                listOf(
                    "Abre @BotFather en Telegram.",
                    "Envía /newbot y elige nombre y @usuario.",
                    "Copia el token que te da y pégalo arriba.",
                    "Escríbele a tu bot: el primero que le habla queda de dueño.",
                ).forEachIndexed { i, texto ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary)
                        Text(texto, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedButton(
                onClick = { abrir(ctx, "https://t.me/BotFather") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Lucide.ExternalLink, null, Modifier.size(16.dp))
                Text("  Abrir @BotFather")
            }
            bot?.takeIf { it.ok && it.usuario.isNotBlank() }?.let {
                OutlinedButton(
                    onClick = { abrir(ctx, "https://t.me/${it.usuario}") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Lucide.ExternalLink, null, Modifier.size(16.dp))
                    Text("  Hablar con @${it.usuario}")
                }
            }
        }
    }
}

// ----------------------------------------------------------------- claves ---

/** Variables que no son API keys pero se usan a diario. */
private val AJUSTES = listOf(
    Triple("OWNER_ID", "Tu ID de Telegram", "se aprende solo al escribirle"),
    Triple("CITY", "Ciudad para el clima", "Loja, EC"),
)

@Composable
internal fun ModClaves(env: String, onEnvChange: (String) -> Unit, onSave: () -> Unit) {
    var proveedores by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var avanzado by remember { mutableStateOf(false) }
    var guardado by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        proveedores = withContext(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray(
                    Python.getInstance().getModule("jarvis").callAttr("proveedores").toString()
                )
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .map { it.optString("env") to it.optString("nombre") }
            }.getOrDefault(emptyList())
        }
    }

    // Un textarea con KEY=VALOR obliga a saber el nombre exacto de cada
    // variable. Se edita por campos y el blob se reconstruye conservando
    // cualquier línea que la pantalla no conozca.
    val mapa = remember(env) { leerEnv(env) }
    val conocidas = remember(proveedores) {
        (proveedores.map { it.first } + AJUSTES.map { it.first }).toSet()
    }
    val puestas = proveedores.count { mapa[it.first].orEmpty().isNotBlank() }

    fun poner(clave: String, valor: String) {
        onEnvChange(escribirEnv(env, clave, valor))
        guardado = false
    }

    ColumnaModulo {
        PixelWindow("Claves", icon = Lucide.KeyRound) {
            Text(
                "Con una sola basta para arrancar. Se guardan cifradas en este " +
                    "teléfono y no salen de aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$puestas de ${proveedores.size} proveedores con clave",
                style = MaterialTheme.typography.labelMedium,
                color = if (puestas > 0) Color(0xFF6FBF4A)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onSave(); guardado = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) {
                Icon(if (guardado) Lucide.Check else Lucide.Save, null, Modifier.size(17.dp))
                Text(if (guardado) "  Guardado" else "  Guardar todo")
            }
        }

        PixelWindow("Ajustes", icon = Lucide.Settings) {
            AJUSTES.forEach { (clave, etiqueta, pista) ->
                OutlinedTextField(
                    value = mapa[clave].orEmpty(),
                    onValueChange = { poner(clave, it) },
                    label = { Text(etiqueta) },
                    placeholder = { Text(pista) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        PixelWindow("API keys", icon = Lucide.KeyRound) {
            proveedores.forEach { (clave, nombre) ->
                CampoClave(nombre, clave, mapa[clave].orEmpty()) { poner(clave, it) }
            }
        }

        PixelWindow("Avanzado", icon = Lucide.Settings) {
            Desplegable("Editar como texto", avanzado) { avanzado = !avanzado }
            if (avanzado) {
                OutlinedTextField(
                    value = env,
                    onValueChange = { onEnvChange(it); guardado = false },
                    label = { Text("KEY=VALOR por línea") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
                val extras = mapa.keys.filterNot { it in conocidas }
                if (extras.isNotEmpty()) {
                    Text("Otras variables: ${extras.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CampoClave(nombre: String, clave: String, valor: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = onChange,
        label = { Text(nombre) },
        placeholder = { Text(clave) },
        singleLine = true,
        visualTransformation =
            if (visible || valor.isBlank()) VisualTransformation.None
            else PasswordVisualTransformation(),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (valor.isNotBlank()) {
                    Icon(Lucide.Check, null, Modifier.size(16.dp), tint = Color(0xFF6FBF4A))
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Lucide.EyeOff else Lucide.Eye, null,
                            Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Desplegable(texto: String, abierto: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Icon(
            if (abierto) Lucide.ChevronDown else Lucide.ChevronRight, null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

// ------------------------------------------------------------------ utils ---

internal fun leerEnv(env: String): Map<String, String> =
    env.lineSequence().mapNotNull {
        val p = it.split("=", limit = 2)
        if (p.size == 2 && p[0].isNotBlank()) p[0].trim() to p[1].trim() else null
    }.toMap()

/** Cambia una clave conservando el resto del texto tal cual estaba. */
internal fun escribirEnv(env: String, clave: String, valor: String): String {
    val lineas = env.lines().toMutableList()
    val i = lineas.indexOfFirst { it.split("=", limit = 2).firstOrNull()?.trim() == clave }
    return when {
        i >= 0 && valor.isBlank() -> lineas.apply { removeAt(i) }
        i >= 0 -> lineas.apply { this[i] = "$clave=$valor" }
        valor.isBlank() -> lineas
        else -> lineas.apply { add("$clave=$valor") }
    }.filter { it.isNotBlank() }.joinToString("\n")
}

private fun abrir(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
