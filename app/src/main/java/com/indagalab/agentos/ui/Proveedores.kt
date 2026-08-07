package com.indagalab.agentos.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class Proveedor(
    val id: String,
    val nombre: String,
    val env: String,
    val modelo: String,
    val enlace: String,
    val nota: String,
    val url: String,
    val urlEnv: String,
    val modeloEnv: String,
    val dialecto: String,
)

private data class Prueba(val ok: Boolean, val ms: Int, val detalle: String)

/** Lee `CLAVE=valor` del blob de variables. */
private fun claveDe(env: String, nombre: String): String =
    env.lineSequence().mapNotNull {
        val p = it.split("=", limit = 2)
        if (p.size == 2 && p[0].trim() == nombre) p[1].trim() else null
    }.firstOrNull().orEmpty()

@Composable
internal fun ModProveedores(
    env: String,
    running: Boolean,
    info: String,
    onEnvChange: (String) -> Unit = {},
    onSave: () -> Unit = {},
) {
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<Proveedor>>(emptyList()) }
    val pruebas = remember { mutableStateMapOf<String, Prueba?>() }
    val probando = remember { mutableStateMapOf<String, Boolean>() }
    val modelos = remember { mutableStateMapOf<String, List<String>>() }
    val motivoModelos = remember { mutableStateMapOf<String, String>() }
    val buscando = remember { mutableStateMapOf<String, Boolean>() }

    // Se relee con el env: el modelo y la URL que el usuario escribe salen del
    // catálogo ya resueltos, en vez de duplicar esa lógica en Kotlin.
    LaunchedEffect(env) {
        lista = withContext(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray(
                    Python.getInstance().getModule("jarvis")
                        .callAttr("proveedores", env).toString()
                )
                (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                    Proveedor(
                        it.optString("id"), it.optString("nombre"), it.optString("env"),
                        it.optString("modelo"), it.optString("enlace"), it.optString("nota"),
                        it.optString("url"), it.optString("url_env"),
                        it.optString("modelo_env"), it.optString("dialecto"),
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    val activo = remember(info) {
        runCatching { JSONObject(info).optString("provider") }.getOrNull().orEmpty()
    }
    val conClave = lista.count { claveDe(env, it.env).isNotBlank() }

    fun probar(p: Proveedor) {
        val key = claveDe(env, p.env)
        probando[p.id] = true
        alcance.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    JSONObject(
                        Python.getInstance().getModule("jarvis")
                            .callAttr("probar_proveedor", p.id, key, env).toString()
                    )
                }.getOrNull()
            }
            pruebas[p.id] = r?.let {
                Prueba(it.optBoolean("ok"), it.optInt("ms"), it.optString("detalle"))
            } ?: Prueba(false, 0, "no se pudo probar")
            probando[p.id] = false
        }
    }

    fun buscarModelos(p: Proveedor) {
        buscando[p.id] = true
        alcance.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    JSONObject(
                        Python.getInstance().getModule("jarvis")
                            .callAttr("modelos_de", p.id, claveDe(env, p.env), env).toString()
                    )
                }.getOrNull()
            }
            val arr = r?.optJSONArray("modelos")
            modelos[p.id] = (0 until (arr?.length() ?: 0)).map { arr!!.getString(it) }
            motivoModelos[p.id] = r?.optString("detalle").orEmpty()
            buscando[p.id] = false
        }
    }

    ColumnaModulo {
        PixelWindow("Proveedores", icon = Lucide.Cloud) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Cifra("Con clave", "$conClave", Modifier.weight(1f))
                Cifra("Disponibles", "${lista.size}", Modifier.weight(1f))
                Cifra("Probados", "${pruebas.count { it.value?.ok == true }}", Modifier.weight(1f))
            }
            Text(
                if (running && activo.isNotBlank())
                    "Ahora mismo responde $activo."
                else
                    "Con varias claves puestas, si una falla el agente salta a la siguiente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { lista.filter { claveDe(env, it.env).isNotBlank() }.forEach { probar(it) } },
                enabled = conClave > 0,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Lucide.Play, null, Modifier.size(16.dp))
                Text("  Probar las $conClave que tienen clave")
            }
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Guardar cambios") }
        }

        lista.forEach { p ->
            TarjetaProveedor(
                p = p,
                clave = claveDe(env, p.env),
                esActivo = running && p.id == activo,
                prueba = pruebas[p.id],
                probando = probando[p.id] == true,
                onProbar = { probar(p) },
                onVariable = { clave, valor -> onEnvChange(escribirEnv(env, clave, valor)) },
                modelos = modelos[p.id].orEmpty(),
                motivoModelos = motivoModelos[p.id].orEmpty(),
                buscandoModelos = buscando[p.id] == true,
                onBuscarModelos = { buscarModelos(p) },
            )
        }
    }
}

@Composable
private fun TarjetaProveedor(
    p: Proveedor,
    clave: String,
    esActivo: Boolean,
    prueba: Prueba?,
    probando: Boolean,
    onProbar: () -> Unit,
    onVariable: (String, String) -> Unit,
    modelos: List<String>,
    motivoModelos: String,
    buscandoModelos: Boolean,
    onBuscarModelos: () -> Unit,
) {
    val ctx = LocalContext.current
    val tieneClave = clave.isNotBlank()
    var menuAbierto by remember { mutableStateOf(false) }

    PixelWindow(p.nombre, icon = Lucide.Cloud) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.nota, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.dialecto != "openai") {
                    Text("API propia de ${p.nombre}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Etiqueta(
                when {
                    esActivo -> "en uso"
                    tieneClave -> "clave puesta"
                    else -> "sin clave"
                },
                when {
                    esActivo -> MaterialTheme.colorScheme.primary
                    tieneClave -> Color(0xFF6FBF4A)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        // El resultado de la prueba, si la hubo. Latencia incluida: es lo que
        // decide qué proveedor merece la pena aunque los dos "funcionen".
        prueba?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    if (it.ok) Lucide.Check else Lucide.X, null, Modifier.size(15.dp),
                    tint = if (it.ok) Color(0xFF6FBF4A) else MaterialTheme.colorScheme.error,
                )
                Text(
                    if (it.ok) "responde en ${it.ms} ms" else it.detalle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.ok) Color(0xFF6FBF4A) else MaterialTheme.colorScheme.error,
                )
            }
        }

        // Los modelos no se escriben a mano ni se queman en el código: se le
        // preguntan al proveedor, que es el único que sabe cuáles tiene hoy y
        // a cuáles llega esta cuenta.
        Box {
            OutlinedTextField(
                value = p.modelo,
                onValueChange = { onVariable(p.modeloEnv, it) },
                label = { Text("Modelo") },
                placeholder = { Text(p.modeloEnv) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = { if (modelos.isEmpty()) onBuscarModelos() else menuAbierto = true },
                        enabled = !buscandoModelos,
                    ) {
                        if (buscandoModelos) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (modelos.isEmpty()) Lucide.RefreshCw else Lucide.ChevronDown,
                                if (modelos.isEmpty()) "Buscar modelos" else "Elegir modelo",
                                Modifier.size(18.dp),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(
                expanded = menuAbierto,
                onDismissRequest = { menuAbierto = false },
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                modelos.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Text(m, style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = if (m == p.modelo) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                        },
                        onClick = { onVariable(p.modeloEnv, m); menuAbierto = false },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    buscandoModelos -> "Preguntando al proveedor…"
                    modelos.isNotEmpty() -> "${modelos.size} modelos disponibles"
                    motivoModelos == "sin clave" -> "Pon la clave para ver sus modelos"
                    motivoModelos.isNotBlank() -> motivoModelos
                    else -> "Toca el icono para traer sus modelos"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (modelos.isNotEmpty()) Color(0xFF6FBF4A)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (modelos.isNotEmpty()) {
                IconButton(onClick = onBuscarModelos, enabled = !buscandoModelos) {
                    Icon(Lucide.RefreshCw, "Actualizar", Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (p.urlEnv.isNotBlank()) {
            OutlinedTextField(
                value = p.url,
                onValueChange = { onVariable(p.urlEnv, it) },
                label = { Text("URL de la API") },
                placeholder = { Text("https://tu-servidor/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onProbar,
                enabled = tieneClave && !probando,
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
            ) {
                if (probando) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    Text("  Probando")
                } else {
                    Icon(Lucide.Play, null, Modifier.size(15.dp))
                    Text("  Probar")
                }
            }
            if (p.enlace.isNotBlank()) {
                OutlinedButton(
                    onClick = { abrirEnlace(ctx, p.enlace) },
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                ) {
                    Icon(Lucide.ExternalLink, null, Modifier.size(15.dp))
                    Text(if (tieneClave) "  Panel" else "  Obtener", maxLines = 1)
                }
            }
        }
        Text(
            p.env,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Etiqueta(texto: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(texto, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun Cifra(titulo: String, valor: String, modifier: Modifier = Modifier) {
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

private fun abrirEnlace(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
