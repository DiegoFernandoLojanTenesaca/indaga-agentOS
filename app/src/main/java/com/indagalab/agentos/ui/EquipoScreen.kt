package com.indagalab.agentos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronDown
import androidx.compose.material3.OutlinedButton
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.Users
import com.indagalab.agentos.ui.pixel.CAMISETAS_DISPONIBLES
import com.indagalab.agentos.ui.pixel.PELOS_DISPONIBLES
import com.indagalab.agentos.ui.pixel.PIELES_DISPONIBLES
import com.indagalab.agentos.ui.pixel.Oficio
import com.indagalab.agentos.ui.pixel.Personaje
import com.indagalab.agentos.ui.pixel.SkinAgente
import com.indagalab.agentos.ui.pixel.Skins
import com.indagalab.agentos.ui.pixel.drawSprite

/**
 * Editor del equipo: cada agente de la sala se puede renombrar, darle un rol y
 * elegirle camiseta, piel y pelo.
 *
 * La personalización es del usuario y vive aparte del skill (`skins.json`), así
 * que reinstalar o actualizar un skill no se lleva por delante el aspecto que
 * le hayas puesto.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun EquipoScreen(nombres: List<String>) {
    val ctx = LocalContext.current
    var skins by remember { mutableStateOf(Skins.cargar(ctx)) }
    var propios by remember { mutableStateOf(Skins.propios(ctx)) }
    var nombreNuevo by remember { mutableStateOf("") }

    fun refrescar() {
        skins = Skins.cargar(ctx)
        propios = Skins.propios(ctx)
    }

    ColumnaModulo {
        PixelWindow("El equipo", icon = Lucide.Users) {
            Text(
                "Ponle nombre, rol y aspecto a cada agente. Lo que elijas se guarda " +
                    "aparte del skill, así que actualizarlo no borra su cara.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Puedes crear los que quieras: aparecen en la sala y tienen su " +
                    "propio chat con su historial.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = nombreNuevo,
                onValueChange = { nombreNuevo = it },
                label = { Text("Nombre del nuevo agente") },
                placeholder = { Text("Cocina, Gimnasio, Trabajo…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    Skins.crear(ctx, nombreNuevo, System.currentTimeMillis())
                    nombreNuevo = ""
                    refrescar()
                },
                enabled = nombreNuevo.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Lucide.Plus, null, Modifier.size(16.dp))
                Text("  Crear agente")
            }
        }

        // Los tuyos primero, y con opción de borrarlos: los del núcleo y los
        // de skills no se pueden borrar desde aquí porque no los creaste tú.
        (propios.map { it.id } + nombres.filterNot { id -> propios.any { it.id == id } })
            .forEach { id ->
                EditorAgente(
                    id = id,
                    inicial = skins[id] ?: SkinAgente(id = id),
                    onGuardar = { nuevo ->
                        Skins.guardarUno(ctx, nuevo)
                        refrescar()
                    },
                    onBorrar = if (propios.any { it.id == id }) {
                        { Skins.eliminar(ctx, id); refrescar() }
                    } else null,
                    editable = propios.any { it.id == id },
                )
            }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditorAgente(
    id: String,
    inicial: SkinAgente,
    onGuardar: (SkinAgente) -> Unit,
    onBorrar: (() -> Unit)? = null,
    editable: Boolean = true,
) {
    var alias by remember(id) { mutableStateOf(inicial.alias) }
    var rol by remember(id) { mutableStateOf(inicial.rol) }
    var camiseta by remember(id) { mutableStateOf(inicial.camiseta) }
    var piel by remember(id) { mutableStateOf(inicial.piel) }
    var pelo by remember(id) { mutableStateOf(inicial.pelo) }
    var oficio by remember(id) { mutableStateOf(inicial.oficioReal()) }
    var guardado by remember(id) { mutableStateOf(false) }

    val skinActual = SkinAgente(id, alias, rol, camiseta, piel, pelo, oficio.name, inicial.propio)

    PixelWindow(id, icon = Lucide.Users) {
        if (!editable) {
            Text(
                "Viene de fábrica. Está aquí como ejemplo de cómo se configura " +
                    "un agente: crea uno tuyo para editar todo esto.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Vista previa en vivo: se redibuja con cada color que tocas.
            RetratoPixel(skinActual, Modifier.size(86.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it; guardado = false },
                    label = { Text("Nombre") },
                    placeholder = { Text(id) },
                    singleLine = true,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rol,
                    onValueChange = { rol = it; guardado = false },
                    label = { Text("Rol") },
                    placeholder = { Text("Cámara y GPS") },
                    singleLine = true,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Qué objeto sostiene: es lo que hace que se le reconozca el oficio.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Objeto", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Oficio.entries.forEach { of ->
                    val sel = of == oficio
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                            .clickable(enabled = editable) { oficio = of; guardado = false }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        of.sprite?.let { sp ->
                            androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                                val e = (size.width / sp.w).toInt().coerceAtLeast(1)
                                drawSprite(
                                    sp,
                                    androidx.compose.ui.geometry.Offset(
                                        (size.width - sp.w * e) / 2f,
                                        (size.height - sp.h * e) / 2f,
                                    ), e,
                                )
                            }
                        }
                        Text(
                            of.etiqueta,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        FichaAgente(id, editable)

        Paleta("Camiseta", CAMISETAS_DISPONIBLES, camiseta, editable) { camiseta = it; guardado = false }
        Paleta("Piel", PIELES_DISPONIBLES, piel, editable) { piel = it; guardado = false }
        Paleta("Pelo", PELOS_DISPONIBLES, pelo, editable) { pelo = it; guardado = false }

        if (editable) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onGuardar(skinActual); guardado = true },
                modifier = Modifier.weight(1f).heightIn(min = 46.dp),
            ) {
                Icon(if (guardado) Lucide.Check else Lucide.Save, null, Modifier.size(16.dp))
                Text(if (guardado) "  Guardado" else "  Guardar")
            }
            onBorrar?.let {
                OutlinedButton(onClick = it, modifier = Modifier.heightIn(min = 46.dp)) {
                    Icon(Lucide.Trash2, "Borrar agente", Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Paleta(
    titulo: String,
    colores: List<Color>,
    elegido: Int,
    editable: Boolean = true,
    onElegir: (Int) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        // FlowRow y no Row: con 12 camisetas de 30dp los últimos colores se
        // salían de pantalla y no había forma de tocarlos.
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            colores.forEachIndexed { i, col ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(col)
                        .border(
                            width = if (i == elegido) 3.dp else 0.dp,
                            color = if (i == elegido) c.primary else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable(enabled = editable) { onElegir(i) }
                )
            }
        }
    }
}

/** Retrato del personaje dibujado con el motor de pixel art. */
@Composable
private fun RetratoPixel(skin: SkinAgente, modifier: Modifier = Modifier) {
    val personaje = remember(skin.camiseta, skin.piel, skin.pelo, skin.id) {
        Personaje(
            camiseta = Skins.camisetaDe(skin, skin.id),
            piel = Skins.pielDe(skin),
            pelo = Skins.peloDe(skin),
        )
    }
    androidx.compose.foundation.Canvas(modifier) {
        val sprite = personaje.workA
        val esc = (size.width / sprite.w).toInt().coerceAtLeast(1)
        drawSprite(
            sprite,
            androidx.compose.ui.geometry.Offset(
                (size.width - sprite.w * esc) / 2f,
                (size.height - sprite.h * esc) / 2f,
            ),
            esc,
        )
    }
}


/**
 * De qué sabe este agente: sus instrucciones, sus fuentes de datos y sus
 * documentos. Es lo que el chat local le pasa al modelo cuando hablas con él.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FichaAgente(id: String, editable: Boolean = true) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    var ficha by remember(id) { mutableStateOf(Fichas.cargar(ctx, id)) }
    var instrucciones by remember(id) { mutableStateOf(ficha.instrucciones) }
    var aviso by remember(id) { mutableStateOf("") }
    var abierto by remember(id) { mutableStateOf(false) }

    // El selector de archivos del sistema: no hace falta permiso de
    // almacenamiento, el usuario elige y Android nos da acceso a ese archivo.
    val elegir = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val nombre = ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                val i = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && cur.moveToFirst()) cur.getString(i) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "documento"
            Fichas.adjuntar(ctx, id, uri, nombre).fold(
                onSuccess = { ficha = it; aviso = "" },
                onFailure = { aviso = it.message ?: "no se pudo leer" },
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .clickable { abierto = !abierto }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "De qué sabe" + Fichas.resumen(ctx, id).let { if (it.isBlank()) "" else " · $it" },
                style = MaterialTheme.typography.labelMedium,
                color = c.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (abierto) Lucide.ChevronDown else Lucide.ChevronRight, null,
                Modifier.size(16.dp), tint = c.primary,
            )
        }

        if (abierto) {
            OutlinedTextField(
                value = instrucciones,
                onValueChange = {
                    instrucciones = it
                    ficha.instrucciones = it
                    Fichas.guardar(ctx, ficha)
                },
                label = { Text("Instrucciones") },
                placeholder = { Text("Cómo debe responder, qué tono, qué evitar…") },
                maxLines = 4,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Datos que puede consultar", style = MaterialTheme.typography.labelMedium,
                color = c.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Fichas.FUENTES.forEach { fuente ->
                    val puesta = fuente in ficha.fuentes
                    Text(
                        fuente,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (puesta) c.primary else c.onSurface,
                        modifier = Modifier.clip(RoundedCornerShape(9.dp))
                            .background(
                                if (puesta) c.primary.copy(alpha = 0.16f)
                                else c.surfaceVariant.copy(alpha = 0.35f)
                            )
                            .clickable(enabled = editable) {
                                if (puesta) ficha.fuentes.remove(fuente)
                                else ficha.fuentes.add(fuente)
                                Fichas.guardar(ctx, ficha)
                                ficha = Fichas.cargar(ctx, id)
                            }
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
            }

            Text("Documentos", style = MaterialTheme.typography.labelMedium,
                color = c.onSurfaceVariant)
            ficha.documentos.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                        .background(c.surfaceVariant.copy(alpha = 0.30f))
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.nombre, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text("${d.palabras} palabras",
                            style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                    }
                    Icon(
                        Lucide.Trash2, "Quitar",
                        Modifier.size(15.dp).clickable(enabled = editable) {
                            ficha = Fichas.quitarDocumento(ctx, id, d.nombre)
                        },
                        tint = c.onSurfaceVariant,
                    )
                }
            }
            if (editable) OutlinedButton(
                onClick = { elegir.launch(arrayOf("application/pdf", "text/*")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) {
                Icon(Lucide.Plus, null, Modifier.size(15.dp))
                Text("  Añadir documento")
            }
            if (aviso.isNotBlank()) {
                Text(aviso, style = MaterialTheme.typography.labelSmall, color = c.error)
            }
        }
    }
}