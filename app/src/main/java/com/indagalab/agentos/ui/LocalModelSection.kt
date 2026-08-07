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
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import com.indagalab.agentos.llm.Catalogo
import com.indagalab.agentos.llm.LocalLlm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Modelo local: qué modelos hay, traerse otros, probarlos y medirlos.
 *
 * Llama a [LocalLlm] DIRECTAMENTE, sin pasar por el bridge HTTP. El bridge
 * existe para que Python hable con Kotlin; la UI ya está en Kotlin y meterle
 * un salto por localhost sólo serviría para exigir que el agente esté en
 * marcha —y entonces no podrías ni mirar qué modelos tienes sin configurar
 * antes un bot de Telegram.
 */
@Composable
fun LocalModelSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var instalados by remember { mutableStateOf<List<File>>(emptyList()) }
    var enUso by remember { mutableStateOf<File?>(null) }
    var cargado by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }

    var prompt by remember { mutableStateOf("Tu codigo de verificacion es 4821") }
    var medida by remember { mutableStateOf<LocalLlm.Medida?>(null) }
    var errorPrueba by remember { mutableStateOf("") }
    var benchmark by remember { mutableStateOf("") }

    fun refrescar() {
        instalados = Catalogo.instalados(ctx)
        enUso = LocalLlm.modeloDisponible(ctx)
        cargado = LocalLlm.estaListo()
    }
    LaunchedEffect(Unit) { refrescar() }

    ColumnaModulo {
        PixelWindow("Modelo en uso", icon = Lucide.Cpu) {
            val m = enUso
            if (m == null) {
                Text(
                    "No hay ningún modelo todavía. Con el de abajo y unos " +
                        "minutos, este teléfono responde sin internet ni API key.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(m.name, style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Marcador("Tamaño", Catalogo.peso(m.length()), Modifier.weight(1f))
                    Marcador("Estado", if (cargado) "en RAM" else "en disco", Modifier.weight(1f))
                    Marcador("Instalados", "${instalados.size}", Modifier.weight(1f))
                }
            }
        }

        if (enUso != null) {
            PixelWindow("Prueba", icon = Lucide.Play) {
                // Sin maxLines, un campo multilínea dentro de una columna con
                // scroll se mide con altura infinita y Compose lanza.
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Texto de prueba") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            ocupado = true; errorPrueba = ""; medida = null
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    LocalLlm.medir(
                                        ctx,
                                        "Responde en una frase corta y en español.",
                                        prompt,
                                    )
                                }
                                medida = r
                                if (r == null) errorPrueba = "El modelo no se pudo cargar."
                                ocupado = false; refrescar()
                            }
                        },
                        enabled = !ocupado,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        if (ocupado) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Lucide.Play, null, Modifier.size(16.dp))
                        Text("  Responder")
                    }
                    OutlinedButton(
                        onClick = {
                            ocupado = true; benchmark = ""
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    LocalLlm.bench(ctx, pp = 64, tg = 32)
                                }
                                benchmark = r ?: "No se pudo medir."
                                ocupado = false; refrescar()
                            }
                        },
                        enabled = !ocupado,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Lucide.Gauge, null, Modifier.size(16.dp))
                        Text("  Medir")
                    }
                }

                // Carga y generación se muestran por separado a propósito: la
                // primera respuesta siempre parece lentísima porque incluye
                // subir el modelo a RAM, y eso no es la velocidad del modelo.
                medida?.let { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Marcador("Carga", "${r.cargaMs} ms", Modifier.weight(1f))
                        Marcador("1.er token", "${r.primerTokenMs} ms", Modifier.weight(1f))
                        Marcador("Velocidad", "%.1f t/s".format(r.tokensPorSegundo), Modifier.weight(1f))
                    }
                    if (r.texto.isNotBlank()) {
                        Text(r.texto, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(12.dp))
                    }
                }
                if (errorPrueba.isNotBlank()) {
                    Text(errorPrueba, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (benchmark.isNotBlank()) {
                    Text(benchmark, style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(12.dp))
                }
                if (cargado) {
                    OutlinedButton(
                        onClick = { LocalLlm.descargar(); refrescar() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    ) {
                        Icon(Lucide.Trash2, null, Modifier.size(15.dp))
                        Text("  Sacar de la RAM")
                    }
                }
            }
        }

        if (instalados.size > 1) {
            PixelWindow("En el teléfono", icon = Lucide.HardDrive) {
                instalados.forEach { f ->
                    val activo = f.name == enUso?.name
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { LocalLlm.elegir(ctx, f.name); refrescar() }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = if (activo) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                            Text(Catalogo.peso(f.length()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (activo) {
                            Icon(Lucide.Check, "En uso", Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            if (activo) LocalLlm.descargar()
                            f.delete(); refrescar()
                        }) {
                            Icon(Lucide.Trash2, "Borrar", Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        BuscadorModelos(onCambio = { refrescar() })
    }
}

// -------------------------------------------------------------- catálogo ---

@Composable
private fun BuscadorModelos(onCambio: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var busca by remember { mutableStateOf("") }
    var repos by remember { mutableStateOf<List<Catalogo.Repo>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var abierto by remember { mutableStateOf<String?>(null) }
    var archivos by remember { mutableStateOf<List<Catalogo.Archivo>>(emptyList()) }
    var descargando by remember { mutableStateOf<String?>(null) }
    var progreso by remember { mutableStateOf(0f) }
    var recibido by remember { mutableStateOf(0L) }
    var aviso by remember { mutableStateOf("") }
    var cancelar by remember { mutableStateOf(false) }
    var urlManual by remember { mutableStateOf("") }

    fun buscar() {
        buscando = true; abierto = null; archivos = emptyList()
        scope.launch {
            repos = withContext(Dispatchers.IO) { Catalogo.buscar(busca) }
            buscando = false
            if (repos.isEmpty()) aviso = "Sin resultados. ¿Hay internet?"
        }
    }
    LaunchedEffect(Unit) { buscar() }

    fun bajar(url: String, nombre: String) {
        descargando = nombre; progreso = 0f; recibido = 0L; aviso = ""; cancelar = false
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                Catalogo.descargar(ctx, url, nombre, cancelado = { cancelar }) { hechos, total ->
                    recibido = hechos
                    if (total > 0) progreso = hechos.toFloat() / total
                }
            }
            aviso = r.fold(
                onSuccess = { "Listo: ${it.name}" },
                onFailure = { "No se pudo descargar: ${it.message}" },
            )
            descargando = null
            onCambio()
        }
    }

    // El recomendado va primero y aparte: sin esto, la primera pantalla que ve
    // alguien es una lista de 12 repos de Hugging Face con nombres como
    // "Qwen_Qwen3-0.6B-IQ4_XS" y ninguna pista de cuál funciona en un teléfono.
    if (!Catalogo.tieneRecomendado(ctx)) {
        val rec = Catalogo.Recomendado
        val dePago = remember { Catalogo.redDePago(ctx) }
        PixelWindow("El modelo probado", icon = Lucide.Sparkles) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(rec.TITULO, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(Catalogo.peso(rec.archivo.bytes),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace)
            }
            Text(rec.MEDIDO, style = MaterialTheme.typography.bodySmall)
            Text(rec.DONDE, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (dePago) {
                Text(
                    "Estás en una red que se paga por megas. Son " +
                        "${Catalogo.peso(rec.archivo.bytes)}: mejor con wifi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // El progreso se pinta aquí mismo mientras sea este modelo: mandarlo
            // a la ventana de abajo obligaría a buscar dónde salió la barra.
            if (descargando == rec.archivo.nombre) {
                BarraDescarga(descargando!!, progreso, recibido) { cancelar = true }
            } else {
                Button(
                    onClick = { bajar(rec.archivo.url, rec.archivo.nombre) },
                    enabled = descargando == null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Lucide.Download, null, Modifier.size(17.dp))
                    Text("  Descargar y activar")
                }
                Text(
                    "Se activa solo al terminar. Si se corta, no deja nada a medias.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    PixelWindow("Traer otro", icon = Lucide.Download) {
        Text(
            "Modelos GGUF de Hugging Face, ordenados por descargas. En un " +
                "teléfono, cualquier cosa por encima de 2 GB va a ir a tirones: " +
                "mira el peso antes que el nombre.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            label = { Text("Buscar en Hugging Face") },
            placeholder = { Text("qwen, gemma, llama…") },
            leadingIcon = { Icon(Lucide.Search, null, Modifier.size(17.dp)) },
            trailingIcon = {
                IconButton(onClick = { buscar() }, enabled = !buscando) {
                    if (buscando) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    else Icon(Lucide.Search, "Buscar", Modifier.size(17.dp))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        descargando?.let {
            BarraDescarga(it, progreso, recibido) { cancelar = true }
        }
        if (aviso.isNotBlank() && descargando == null) {
            Text(aviso, style = MaterialTheme.typography.bodySmall,
                color = if (aviso.startsWith("Listo")) Color(0xFF6FBF4A)
                        else MaterialTheme.colorScheme.error)
        }

        repos.take(12).forEach { r ->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (abierto == r.id) {
                                abierto = null; archivos = emptyList()
                            } else {
                                abierto = r.id; archivos = emptyList()
                                scope.launch {
                                    archivos = withContext(Dispatchers.IO) { Catalogo.archivos(r.id) }
                                }
                            }
                        }
                        .padding(vertical = 9.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.nombre, style = MaterialTheme.typography.bodyMedium,
                            color = if (abierto == r.id) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                        Text("${r.autor} · ${descargasCortas(r.descargas)} descargas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Lucide.Download, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (abierto == r.id) {
                    if (archivos.isEmpty()) {
                        Text("Mirando qué archivos tiene…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
                    }
                    archivos.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .clickable(enabled = descargando == null) { bajar(a.url, a.nombre) }
                                .padding(start = 14.dp, top = 7.dp, bottom = 7.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.cuantizacion.ifBlank { a.nombre.take(24) },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(a.nombre, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1)
                            }
                            Text(
                                Catalogo.peso(a.bytes),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (a.bytes in 1..2_147_483_648L) Color(0xFF6FBF4A)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    PixelWindow("Desde una URL", icon = Lucide.Download) {
        Text(
            "Si el modelo está en otro sitio (GitHub, tu servidor, un enlace " +
                "directo), pega aquí la URL del .gguf.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = urlManual,
            onValueChange = { urlManual = it },
            label = { Text("URL del .gguf") },
            placeholder = { Text("https://…/modelo.gguf") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                val nombre = urlManual.trim().substringAfterLast('/').substringBefore('?')
                    .ifBlank { "modelo.gguf" }
                bajar(urlManual.trim(), if (nombre.endsWith(".gguf", true)) nombre else "$nombre.gguf")
            },
            enabled = urlManual.contains("http") && descargando == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Lucide.Download, null, Modifier.size(16.dp))
            Text("  Descargar")
        }
    }
}

/** Progreso de la descarga en curso. Aparece en la tarjeta del recomendado o
 *  en el buscador, según de dónde haya salido. */
@Composable
private fun BarraDescarga(
    nombre: String,
    progreso: Float,
    recibido: Long,
    onCancelar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(nombre, style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            // Sin Content-Length no hay porcentaje posible: se enseña lo que
            // lleva bajado, que al menos demuestra que avanza.
            Text(
                if (progreso > 0f) "${(progreso * 100).toInt()}%" else Catalogo.peso(recibido),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(progreso.coerceIn(0f, 1f)).height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        OutlinedButton(
            onClick = onCancelar,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        ) {
            Icon(Lucide.X, null, Modifier.size(15.dp))
            Text("  Cancelar")
        }
    }
}

private fun descargasCortas(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%dk".format(n / 1_000)
    else -> n.toString()
}

@Composable
private fun Marcador(titulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 9.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(valor, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
        Text(titulo, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
