package com.indagalab.agentos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import com.indagalab.agentos.pals.PalsHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Catálogo de Pals de PalsHub.
 *
 * Sólo se listan y se importan los **gratuitos**. Los de pago son el trabajo de
 * sus autores; ni se muestran como "casi tuyos" ni se intenta sacarles el
 * prompt. Al importar se guarda la autoría y el enlace al original dentro del
 * propio SKILL.md.
 */
@Composable
fun PalsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme

    val pals = remember { mutableStateListOf<PalsHub.Pal>() }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var instalados by remember { mutableStateOf(emptySet<String>()) }
    var trabajando by remember { mutableStateOf<String?>(null) }

    suspend fun refrescar() {
        instalados = withContext(Dispatchers.IO) { PalsHub.importados(ctx) }
    }

    LaunchedEffect(Unit) {
        cargando = true
        val lista = withContext(Dispatchers.IO) { PalsHub.listarGratis() }
        pals.clear(); pals.addAll(lista)
        error = if (lista.isEmpty()) "No se pudo cargar el catálogo. Puede ser la red o que la API haya cambiado." else null
        refrescar()
        cargando = false
    }

    ColumnaModulo {
        PixelWindow("Pals gratuitos", icon = Lucide.Users) {
            Text(
                "Personalidades listas para tu agente, creadas por la comunidad de " +
                    "PocketPal. Al importar una se guarda como skill y aparece en la sala.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Solo se muestran los gratuitos. Los de pago son el trabajo de sus " +
                    "autores y se compran en palshub.ai.",
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
            )
            if (cargando) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Buscando…", style = MaterialTheme.typography.bodySmall)
                }
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.error)
            }
            if (!cargando && pals.isNotEmpty()) {
                Text("${pals.size} disponibles · ${instalados.size} instalados",
                    style = MaterialTheme.typography.labelMedium, color = c.primary)
            }
        }

        pals.forEach { p ->
            val puesto = p.id in instalados
            PixelWindow(p.titulo, icon = Lucide.Users) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // La miniatura es lo que hace reconocible a un Pal de un
                    // vistazo; sin ella todas las tarjetas se ven iguales.
                    p.miniatura?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (p.autor.isNotBlank()) {
                            Text("por ${p.autor}", style = MaterialTheme.typography.labelMedium,
                                color = c.primary)
                        }
                        if (p.categorias.isNotEmpty()) {
                            Text(p.categorias.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    p.descripcion.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(260),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (puesto) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Lucide.Check, null, Modifier.size(16.dp), tint = c.primary)
                            Text("Instalado", color = c.primary,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { PalsHub.borrar(ctx, p.id) }
                                    refrescar()
                                }
                            },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) { Icon(Lucide.Trash2, null, Modifier.size(15.dp)); Text("  Quitar") }
                    }
                } else {
                    Button(
                        onClick = {
                            trabajando = p.id
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    PalsHub.detalle(p.id)?.let { PalsHub.importar(ctx, it) } != null
                                }
                                if (!ok) error = "No se pudo importar ${p.titulo}."
                                refrescar()
                                trabajando = null
                            }
                        },
                        enabled = trabajando == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    ) {
                        if (trabajando == p.id) {
                            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                            Text("  Importando…")
                        } else {
                            Text("Importar como skill")
                        }
                    }
                }
            }
        }
    }
}
