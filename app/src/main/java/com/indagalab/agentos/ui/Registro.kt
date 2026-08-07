package com.indagalab.agentos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2

private enum class Nivel { NORMAL, AVISO, ERROR }

/** El log es stdout capturado, sin niveles. Se deducen del texto. */
private fun nivelDe(linea: String): Nivel {
    val t = linea.lowercase()
    return when {
        "error" in t || "traceback" in t || "exception" in t || "✗" in t ||
            "failed" in t || "falló" in t -> Nivel.ERROR
        "warn" in t || "aviso" in t || "⚠" in t || "reintent" in t -> Nivel.AVISO
        else -> Nivel.NORMAL
    }
}

@Composable
internal fun ModRegistro(logs: String) {
    var filtro by remember { mutableStateOf("") }
    var soloProblemas by remember { mutableStateOf(false) }
    val portapapeles = LocalClipboardManager.current
    val estadoLista = rememberLazyListState()

    val lineas = remember(logs) {
        // Python devuelve un texto de relleno cuando no hay nada; contarlo como
        // línea dejaba el panel diciendo "1 línea" con el log vacío.
        if (logs.startsWith("(sin logs")) emptyList()
        else logs.lines().filter { it.isNotBlank() }.map { it to nivelDe(it) }
    }
    val errores = lineas.count { it.second == Nivel.ERROR }
    val avisos = lineas.count { it.second == Nivel.AVISO }

    val visibles = remember(lineas, filtro, soloProblemas) {
        lineas.filter { (texto, nivel) ->
            (!soloProblemas || nivel != Nivel.NORMAL) &&
                (filtro.isBlank() || texto.contains(filtro, ignoreCase = true))
        }
    }

    // Al fondo, que es donde está lo último que pasó.
    LaunchedEffect(visibles.size) {
        if (visibles.isNotEmpty()) estadoLista.scrollToItem(visibles.lastIndex)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixelWindow("Registro", icon = Lucide.ScrollText) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dato("Líneas", "${lineas.size}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Dato("Errores", "$errores",
                    if (errores > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                Dato("Avisos", "$avisos",
                    if (avisos > 0) Color(0xFFD9A441)
                    else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
            OutlinedTextField(
                value = filtro,
                onValueChange = { filtro = it },
                label = { Text("Buscar") },
                leadingIcon = { Icon(Lucide.Search, null, Modifier.size(17.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Filtro("Solo problemas", soloProblemas) { soloProblemas = !soloProblemas }
                Row {
                    IconButton(onClick = {
                        portapapeles.setText(AnnotatedString(visibles.joinToString("\n") { it.first }))
                    }) { Icon(Lucide.Copy, "Copiar", Modifier.size(18.dp)) }
                    IconButton(onClick = {
                        runCatching {
                            Python.getInstance().getModule("jarvis").callAttr("clear_logs")
                        }
                    }) { Icon(Lucide.Trash2, "Limpiar", Modifier.size(18.dp)) }
                }
            }
        }

        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            if (visibles.isEmpty()) {
                Text(
                    if (lineas.isEmpty()) "Todavía no hay nada. Arranca el agente."
                    else "Nada coincide con el filtro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                )
            } else {
                LazyColumn(
                    state = estadoLista,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(visibles) { (texto, nivel) ->
                        LineaLog(texto, nivel)
                    }
                }
            }
        }
    }
}

@Composable
private fun LineaLog(texto: String, nivel: Nivel) {
    // La hora va delante en gris y el mensaje en su color: leer un log de 400
    // líneas todas iguales es imposible.
    val hora = texto.take(8).takeIf { it.count { c -> c == ':' } == 2 }
    val resto = if (hora != null) texto.drop(9) else texto
    val color = when (nivel) {
        Nivel.ERROR -> MaterialTheme.colorScheme.error
        Nivel.AVISO -> Color(0xFFD9A441)
        Nivel.NORMAL -> MaterialTheme.colorScheme.onSurface
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hora != null) {
            Text(hora, style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(resto, style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace, color = color)
    }
}

@Composable
private fun Filtro(texto: String, activo: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(9.dp))
            .background(
                if (activo) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            texto, style = MaterialTheme.typography.labelMedium,
            color = if (activo) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Dato(titulo: String, valor: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(valor, style = MaterialTheme.typography.titleLarge,
            color = color, fontFamily = FontFamily.Monospace)
        Text(titulo, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
