package com.indagalab.agentos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquarePlus
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Trash2
import com.indagalab.agentos.llm.LocalLlm
import com.indagalab.agentos.ui.pixel.Personaje
import com.indagalab.agentos.ui.pixel.SkinAgente
import com.indagalab.agentos.ui.pixel.Skins
import com.indagalab.agentos.ui.pixel.drawSprite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quita lo que se le escapa al modelo de su propia plantilla de chat.
 *
 * Los modelos pequeños cuantizados sueltan a menudo los marcadores de rol
 * (`<|im_start|>assistant`, `system`, …) al principio de la respuesta. No es un
 * error del motor y el usuario no tiene por qué verlos.
 */
private fun limpiar(bruto: String): String {
    var t = bruto
    t = t.replace(Regex("<\\|[^|>]*\\|>"), " ")
    t = t.replace(Regex("</?s>"), " ")
    t = t.replace(Regex("(?i)\\[/?(INST|SYS)]"), " ")
    t = t.trim().replace(Regex("(?i)^(system|assistant|user|ai)\\b[:\\s]*"), "")
    return t.trim()
}

/**
 * Chat local, en tres niveles: **agentes → sus conversaciones → la conversación**.
 *
 * Antes había una fila de agentes arriba y la lista de hilos colgando debajo
 * del selector. Con dos agentes se aguantaba; con ocho y varias conversaciones
 * cada uno, el chat quedaba enterrado bajo su propia navegación. Cada nivel
 * ocupa ahora la pantalla entera, que es lo que hace cualquier app de mensajes
 * y lo que permite que un agente tenga las conversaciones que quiera.
 */
@Composable
fun ChatLocalScreen() {
    val ctx = LocalContext.current
    var agente by remember { mutableStateOf<String?>(null) }
    var hilo by remember { mutableStateOf<Conversaciones.Hilo?>(null) }

    // Cada nivel se cierra con el botón atrás del sistema, en orden.
    BackHandler(enabled = agente != null) {
        if (hilo != null) hilo = null else agente = null
    }

    val a = agente
    val h = hilo
    when {
        a == null -> Bandeja(onAbrir = { agente = it })
        h == null -> Conversaciones(
            agente = a,
            onAtras = { agente = null },
            onAbrir = { hilo = it },
        )
        else -> Conversacion(
            agente = a,
            hiloInicial = h,
            onAtras = { hilo = null },
        )
    }
}

// ------------------------------------------------------------- bandeja -----

/** Nivel 1: con quién quieres hablar. */
@Composable
private fun Bandeja(onAbrir: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val skins = remember { Skins.cargar(ctx) }
    val agentes = remember { agentesDeLaSala(true, ctx).map { it.nombre } }
    var conteos by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var ultimas by remember { mutableStateOf<Map<String, Conversaciones.Hilo>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val todas = Conversaciones.listar(ctx)
        conteos = todas.groupingBy { it.agente }.eachCount()
        ultimas = todas.groupBy { it.agente }.mapValues { (_, v) -> v.first() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Cada agente tiene sus propias conversaciones y sabe de lo suyo.",
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
            )
        }
        items(agentes) { nombre ->
            val skin = skins[nombre]
            val n = conteos[nombre] ?: 0
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surfaceVariant.copy(alpha = 0.30f))
                    .clickable { onAbrir(nombre) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Retrato(nombre, skin, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(skin?.nombreVisible() ?: nombre,
                        style = MaterialTheme.typography.titleSmall, color = c.onSurface)
                    Text(
                        when {
                            n == 0 -> skin?.rol?.ifBlank { null } ?: "Sin conversaciones"
                            else -> ultimas[nombre]?.titulo?.take(42) ?: "$n conversaciones"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (n > 0) {
                    Text("$n", style = MaterialTheme.typography.labelMedium, color = c.primary)
                }
                Icon(Lucide.ChevronRight, null, Modifier.size(17.dp), tint = c.onSurfaceVariant)
            }
        }
    }
}

// -------------------------------------------------------- conversaciones ---

/** Nivel 2: los hilos de ese agente. */
@Composable
private fun Conversaciones(
    agente: String,
    onAtras: () -> Unit,
    onAbrir: (Conversaciones.Hilo) -> Unit,
) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val skins = remember { Skins.cargar(ctx) }
    var hilos by remember { mutableStateOf<List<Conversaciones.Hilo>>(emptyList()) }

    fun refrescar() { hilos = Conversaciones.listar(ctx, agente) }
    LaunchedEffect(agente) { refrescar() }

    Column(Modifier.fillMaxSize()) {
        Cabecera(agente, skins[agente], onAtras)

        OutlinedButton(
            onClick = { onAbrir(Conversaciones.nuevo(agente, System.currentTimeMillis())) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(50.dp),
        ) {
            Icon(Lucide.MessageSquarePlus, null, Modifier.size(16.dp))
            Text("  Nueva conversación")
        }

        if (hilos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Todavía no has hablado con ${skins[agente]?.nombreVisible() ?: agente}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(hilos) { h ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surfaceVariant.copy(alpha = 0.28f))
                            .clickable { onAbrir(h) }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(h.titulo, style = MaterialTheme.typography.bodyMedium,
                                color = c.onSurface, maxLines = 1)
                            Text(
                                "${h.mensajes.size} mensajes · ${haceCuanto(h.actualizado)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { Conversaciones.borrar(ctx, h.id); refrescar() }) {
                            Icon(Lucide.Trash2, "Borrar", Modifier.size(16.dp),
                                tint = c.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------- conversación ---

/**
 * Nivel 3: hablar.
 *
 * **Sin historial en el prompt, a propósito.** La conversación se guarda y se
 * relee, pero cada mensaje se manda suelto: medido en este aparato, el mismo
 * prompt tarda 730 ms sin contexto y 19.494 ms con tres turnos acumulados,
 * porque el prefill reprocesa toda la conversación cada vez.
 */
@Composable
private fun Conversacion(
    agente: String,
    hiloInicial: Conversaciones.Hilo,
    onAtras: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    val teclado = LocalSoftwareKeyboardController.current
    val skins = remember { Skins.cargar(ctx) }

    val hilo = remember(hiloInicial.id) { hiloInicial }
    val mensajes = remember(hiloInicial.id) {
        mutableStateListOf<Conversaciones.Mensaje>().also { it.addAll(hiloInicial.mensajes) }
    }
    var entrada by remember { mutableStateOf("") }
    var generando by remember { mutableStateOf(false) }
    var parcial by remember { mutableStateOf("") }
    var trabajo by remember { mutableStateOf<Job?>(null) }
    var contexto by remember { mutableStateOf("") }
    val lista = rememberLazyListState()

    val hayModelo = remember { LocalLlm.modeloDisponible(ctx) != null }

    // Todo lo que le has dado a este agente: sus instrucciones, las tablas que
    // puede consultar y sus documentos. Sale de su ficha, no de una regla fija.
    LaunchedEffect(agente) {
        contexto = withContext(Dispatchers.IO) {
            val ficha = runCatching { Fichas.contexto(ctx, agente) }.getOrDefault("")
            // Los agentes del núcleo traen su tabla puesta de fábrica aunque
            // nadie haya tocado su ficha: el de notas sabe de notas sin que
            // tengas que configurárselo.
            val porDefecto = runCatching {
                Python.getInstance().getModule("jarvis")
                    .callAttr("contexto_agente", agente, ctx.filesDir.absolutePath).toString()
            }.getOrDefault("")
            listOf(ficha, porDefecto).filter { it.isNotBlank() }
                .distinct().joinToString("\n\n").take(2000)
        }
    }

    LaunchedEffect(mensajes.size, parcial) {
        if (mensajes.isNotEmpty()) lista.animateScrollToItem(mensajes.size)
    }

    fun enviar() {
        val txt = entrada.trim()
        if (txt.isEmpty() || generando) return
        teclado?.hide()
        // Que le hablen es su tarea: sube a su escritorio en la sala.
        Actividad.marcar(ctx, agente, System.currentTimeMillis())
        mensajes.add(Conversaciones.Mensaje(true, txt))
        entrada = ""
        generando = true
        parcial = ""

        val skin = skins[agente]
        val rol = skin?.rol?.takeIf { it.isNotBlank() }
        val comoSeLlama = skin?.nombreVisible() ?: agente

        trabajo = scope.launch {
            val t0 = System.currentTimeMillis()
            val system = buildString {
                append("Te llamas $comoSeLlama")
                if (rol != null) append(" y te encargas de $rol")
                append(". Respondes en español, breve y directo, sin repetir la pregunta.")
                if (contexto.isNotBlank()) append("\n\n").append(contexto)
            }
            val flujo = LocalLlm.generarFlow(ctx, system = system, user = txt, maxTokens = 200)
            if (flujo == null) {
                mensajes.add(
                    Conversaciones.Mensaje(
                        false,
                        "No hay modelo local. Ve a Modelo local y descarga uno.",
                    )
                )
            } else {
                val sb = StringBuilder()
                runCatching {
                    flujo.collect { tok -> sb.append(tok); parcial = sb.toString() }
                }.onFailure { e ->
                    // El motor termina cancelando su propio Flow. Eso llegaba
                    // aquí como "StandaloneCoroutine was cancelled" y se
                    // pintaba como si la respuesta se hubiera roto.
                    if (e !is CancellationException) sb.append("\n(se cortó: ${e.message})")
                }
                val limpio = limpiar(sb.toString())
                mensajes.add(
                    Conversaciones.Mensaje(
                        false,
                        limpio.ifBlank { "(no devolvió nada; prueba a preguntarlo de otra forma)" },
                        System.currentTimeMillis() - t0,
                    )
                )
            }
            parcial = ""
            generando = false
            hilo.mensajes.clear()
            hilo.mensajes.addAll(mensajes)
            hilo.actualizado = System.currentTimeMillis()
            Conversaciones.guardar(ctx, hilo)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Cabecera(agente, skins[agente], onAtras)

        Box(Modifier.weight(1f)) {
            if (mensajes.isEmpty() && !generando) {
                Vacio(agente, skins[agente], hayModelo, contexto) { entrada = it }
            } else {
                LazyColumn(
                    state = lista,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(mensajes) { m -> Burbuja(m) }
                    if (parcial.isNotEmpty()) {
                        item { Burbuja(Conversaciones.Mensaje(false, parcial)) }
                    } else if (generando) {
                        item {
                            Text("pensando…", style = MaterialTheme.typography.bodySmall,
                                color = c.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = entrada,
                onValueChange = { entrada = it },
                placeholder = { Text("Escribe…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                enabled = hayModelo,
            )
            IconButton(
                onClick = { if (generando) { trabajo?.cancel(); generando = false } else enviar() },
                enabled = hayModelo && (generando || entrada.isNotBlank()),
            ) {
                Icon(
                    if (generando) Lucide.Square else Lucide.Send,
                    contentDescription = if (generando) "Parar" else "Enviar",
                    tint = c.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------- piezas -----

@Composable
private fun Cabecera(agente: String, skin: SkinAgente?, onAtras: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = onAtras) {
            Icon(Lucide.ChevronLeft, "Atrás", Modifier.size(20.dp), tint = c.onSurfaceVariant)
        }
        Retrato(agente, skin, 30.dp)
        Column {
            Text(skin?.nombreVisible() ?: agente,
                style = MaterialTheme.typography.titleSmall, color = c.onSurface)
            skin?.rol?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Retrato(nombre: String, skin: SkinAgente?, tam: androidx.compose.ui.unit.Dp) {
    val personaje = remember(nombre, skin) {
        Personaje(
            camiseta = Skins.camisetaDe(skin, nombre),
            piel = Skins.pielDe(skin),
            pelo = Skins.peloDe(skin),
        )
    }
    Canvas(Modifier.size(tam)) {
        val sp = personaje.workA
        val e = (size.width / sp.w).toInt().coerceAtLeast(1)
        drawSprite(sp, Offset((size.width - sp.w * e) / 2f, (size.height - sp.h * e) / 2f), e)
    }
}

@Composable
private fun Vacio(
    agente: String,
    skin: SkinAgente?,
    hayModelo: Boolean,
    contexto: String,
    onSugerencia: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Retrato(agente, skin, 64.dp)
        Text(
            when {
                !hayModelo -> "Ve a Modelo local, busca uno en Hugging Face y descárgalo."
                contexto.isNotBlank() -> "Conoce tus datos y responde dentro del teléfono, " +
                    "sin internet."
                else -> "Responde dentro del teléfono, sin internet. Cada mensaje va " +
                    "suelto, sin recordar los anteriores."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
        if (hayModelo) {
            // Un modelo de 1B no adivina qué se le puede pedir; las sugerencias
            // cambian según el agente porque cada uno sabe de lo suyo.
            sugerenciasDe(agente).forEach { s ->
                Text(
                    s,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.primary,
                    modifier = Modifier.padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.primary.copy(alpha = 0.10f))
                        .clickable { onSugerencia(s) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

private fun sugerenciasDe(agente: String): List<String> = when (agente.lowercase()) {
    "notas" -> listOf("¿Qué tengo apuntado?", "Resume mis notas en una frase")
    "listas" -> listOf("¿Qué me falta comprar?", "¿Cuántas cosas tengo pendientes?")
    "agenda" -> listOf("¿Qué tengo pendiente?", "¿Qué es lo más urgente?")
    "telefono" -> listOf("¿Esto es urgente? Tu codigo es 4821", "Resume esto en una frase: …")
    else -> listOf("Resume esto en una frase: …", "Traduce al inglés: mañana llego tarde")
}

/** "hace 5 min", "ayer". Una fecha completa no dice nada en una lista de chats. */
private fun haceCuanto(ms: Long): String {
    val d = (System.currentTimeMillis() - ms) / 1000
    return when {
        d < 60 -> "ahora"
        d < 3600 -> "hace ${d / 60} min"
        d < 86400 -> "hace ${d / 3600} h"
        d < 172800 -> "ayer"
        else -> "hace ${d / 86400} días"
    }
}

@Composable
private fun Burbuja(m: Conversaciones.Mensaje) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.mio) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (m.mio) 16.dp else 4.dp,
                        bottomEnd = if (m.mio) 4.dp else 16.dp,
                    )
                )
                .background(if (m.mio) c.primary.copy(alpha = 0.18f) else c.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(m.texto, style = MaterialTheme.typography.bodyMedium, color = c.onSurface)
            if (m.ms > 0) {
                Text(
                    "%.1f s".format(m.ms / 1000f),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant,
                )
            }
        }
    }
}
