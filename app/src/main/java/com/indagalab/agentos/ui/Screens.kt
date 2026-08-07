package com.indagalab.agentos.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.List
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Zap
import com.indagalab.agentos.BuildConfig
import com.indagalab.agentos.R
import com.indagalab.agentos.data.ConfigStore
import com.indagalab.agentos.service.AgentService
import com.indagalab.agentos.service.AgentState
import com.indagalab.agentos.ui.pixel.ACENTO
import com.indagalab.agentos.ui.pixel.AgentRoom
import com.indagalab.agentos.ui.pixel.Agente
import com.indagalab.agentos.ui.pixel.Estado
import com.indagalab.agentos.ui.pixel.PARED
import com.indagalab.agentos.ui.pixel.SPRITE_ROBOT
import com.indagalab.agentos.ui.pixel.SUELO_B
import com.indagalab.agentos.ui.pixel.Sala
import com.indagalab.agentos.ui.pixel.agentesDesde
import com.indagalab.agentos.ui.pixel.drawSprite
import com.indagalab.agentos.ui.pixel.drawSpriteFooted
import com.indagalab.agentos.ui.pixel.textoPixel
import com.indagalab.agentos.ui.pixel.tileDiamond
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val Green = Color(0xFF16A34A)
private val CONNECTED = Regex("Conectado como (@\\S+)")

internal data class Capability(val icon: ImageVector, val label: String, val ready: Boolean, val desc: String)

internal val CAPABILITIES = listOf(
    Capability(Lucide.Bot, "Chat con IA", true, "Conversa con la IA por Telegram: preguntas, redacción, traducción y código. Tú eliges el proveedor (Groq, Gemini, Cohere…) y el modo (normal, profesor, coder)."),
    Capability(Lucide.Bell, "Recordatorios", true, "Pídele «recuérdame en 30 min sacar la ropa» y te avisa a tiempo. Soporta minutos, horas o una hora puntual."),
    Capability(Lucide.List, "Listas", true, "Listas con casillas marcables (compras, tareas, lo que sea), editables desde el chat con botones."),
    Capability(Lucide.BookOpen, "Diario", true, "A las 22 h te pregunta cómo fue tu día y lo guarda. Pide resúmenes por semana o mes cuando quieras."),
    Capability(Lucide.Cloud, "Clima", true, "Clima actual de tu ciudad y un resumen matutino con clima y las 3 noticias locales del día."),
    Capability(Lucide.Globe, "Búsqueda web", true, "Busca en internet y te resume citando fuentes. También resume cualquier URL o video que le envíes."),
    Capability(Lucide.FileText, "Leer PDFs", true, "Envíale un PDF por Telegram y lo lee para responder preguntas sobre su contenido."),
    Capability(Lucide.Camera, "Cámara", true, "Toma fotos o selfies con la cámara del teléfono y la IA describe lo que ve. Incluye vigilancia y antirrobo con reconocimiento."),
    Capability(Lucide.MapPin, "Ubicación", true, "Te da la ubicación GPS del teléfono con enlace a mapas. Clave si lo pierdes o te lo roban."),
    Capability(Lucide.MessageSquare, "SMS", true, "Lee y envía SMS desde el chat, y te reenvía automáticamente los códigos OTP que llegan a tu SIM."),
    Capability(Lucide.Mic, "Voz", true, "Envíale notas de voz (las transcribe con IA) y puede responderte hablando por el altavoz del teléfono."),
)

private data class Provider(val name: String, val envKey: String)

private val PROVIDERS = listOf(
    Provider("Groq", "GROQ_API_KEY"),
    Provider("Cerebras", "CEREBRAS_API_KEY"),
    Provider("Mistral", "MISTRAL_API_KEY"),
    Provider("Nvidia", "NVIDIA_API_KEY"),
    Provider("SambaNova", "SAMBANOVA_API_KEY"),
    Provider("Gemini", "GOOGLE_API_KEY"),
    Provider("OpenRouter", "OPENROUTER_API_KEY"),
    Provider("Cohere", "COHERE_API_KEY"),
    Provider("AI21", "AI21_API_KEY"),
    Provider("Chutes", "CHUTES_API_KEY"),
    Provider("Z.ai", "ZAI_API_KEY"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold() {
    val ctx = LocalContext.current
    val store = remember { ConfigStore(ctx) }
    var onboarded by remember { mutableStateOf(store.onboarded) }
    if (!onboarded) {
        WelcomeScreen(onStart = { store.onboarded = true; onboarded = true })
        return
    }

    // Navegación por módulos con panel lateral derecho. Antes eran 5 pestañas
    // abajo y cada una amontonaba temas distintos; ahora un módulo = una idea.
    var modulo by remember { mutableStateOf(Modulo.SALA) }
    val drawer = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed)
    val scopeUi = androidx.compose.runtime.rememberCoroutineScope()

    var token by remember { mutableStateOf(store.token) }
    var env by remember { mutableStateOf(store.envBlob) }
    var logs by remember { mutableStateOf("—") }
    var info by remember { mutableStateOf("") }
    val running = AgentState.running.value
    val botUser = CONNECTED.find(logs)?.groupValues?.getOrNull(1)

    LaunchedEffect(Unit) {
        while (true) {
            val py = try { Python.getInstance().getModule("jarvis") } catch (e: Exception) { null }
            logs = try {
                py?.callAttr("get_logs")?.toString() ?: "(agente aún no iniciado)"
            } catch (e: Exception) {
                "(agente aún no iniciado)"
            }
            info = try { py?.callAttr("info")?.toString() ?: "" } catch (e: Exception) { "" }
            delay(1000)
        }
    }

    DrawerDerecho(
        estado = drawer,
        panel = {
            PanelModulos(actual = modulo) { m ->
                modulo = m
                scopeUi.launch { drawer.close() }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(modulo.titulo, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        // Vuelta directa a la sala: con navegación por panel, sin
                        // esto hay que abrir el menú cada vez para volver a casa.
                        if (modulo != Modulo.SALA) {
                            IconButton(onClick = { modulo = Modulo.SALA }) {
                                Icon(Lucide.House, contentDescription = "Ir a la sala",
                                    tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    },
                    actions = { BotonMenu { scopeUi.launch { drawer.open() } } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                AnimatedContent(
                    targetState = modulo,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { it / 14 }) togetherWith fadeOut(tween(140))
                    },
                    label = "modulo",
                ) { m ->
                    when (m) {
                        Modulo.SALA -> HomeScreen(
                            running, token.isNotBlank(), botUser, info,
                            { startAgent(ctx) }, { stopAgent(ctx) },
                            { modulo = Modulo.BOT }, { modulo = Modulo.FUNCIONES },
                        )
                        Modulo.REGISTRO -> ModRegistro(logs)
                        Modulo.EQUIPO -> EquipoScreen(
                            agentesDeLaSala(running, ctx).map { it.nombre })
                        Modulo.ACTIVIDAD -> ModActividad(info, running)
                        Modulo.CHAT -> ChatLocalScreen()
                        Modulo.PALS -> PalsScreen()
                        Modulo.MODELO_LOCAL -> ModModeloLocal()
                        Modulo.PROVEEDORES -> ModProveedores(env, running, info, { env = it }) { store.envBlob = env.trim() }
                        Modulo.BOT -> ModBot(token, { token = it }) { store.token = token.trim() }
                        Modulo.CLAVES -> ModClaves(env, { env = it }) { store.envBlob = env.trim() }
                        Modulo.DISPOSITIVO -> ModDispositivo(running)
                        Modulo.VEINTICUATRO_SIETE -> ModVeinticuatroSiete()
                        Modulo.FUNCIONES -> ModFunciones()
                        Modulo.ACERCA -> ModAcerca()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    running: Boolean,
    configured: Boolean,
    botUser: String?,
    info: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onGoConfig: () -> Unit,
    onGoFunciones: () -> Unit,
) {
    // La sala manda: ocupa lo que puede y el resto es una franja de acción.
    // Antes competía con una card de "Funciones" y un bloque de stats que
    // empujaban la escena a un tercio de la pantalla.
    val ctxSala = LocalContext.current
    val skinsGuardados = remember { com.indagalab.agentos.ui.pixel.Skins.cargar(ctxSala) }
    // La sala se repinta cada pocos segundos: si le hablas a un agente desde
    // el chat, se le ve subir a su puesto sin tener que salir y volver.
    var tic by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            tic = System.currentTimeMillis()
            kotlinx.coroutines.delay(4000)
        }
    }
    val agentesDemo = remember(running, tic / 4000) {
        agentesDeLaSala(running, ctxSala)
    }

    // El edificio, planta a planta. Seis por planta: con más, los nombres se
    // pisan y la escena deja de leerse. La mayoría son oficinas; abajo del todo
    // están las salas a las que bajan los que no tienen tarea.
    val porPlanta = 6
    val plantas = remember(agentesDemo) {
        // Las oficinas llevan a TODOS: quien está abajo en la piscina deja su
        // silla vacía arriba. Es lo que hace que el edificio se lea como un
        // sitio con gente moviéndose y no como dos listas separadas.
        val descansando = agentesDemo.filter { it.estado == Estado.DURMIENDO }
        val ocio = listOf(
            "Descanso" to Sala.DESCANSO,
            "Piscina" to Sala.PISCINA,
            "Aseos" to Sala.ASEOS,
        )
        buildList {
            agentesDemo.chunked(porPlanta).forEachIndexed { i, grupo ->
                add(Triple("Planta ${i + 1}", grupo, Sala.OFICINA))
            }
            descansando.chunked(porPlanta).forEachIndexed { i, grupo ->
                val (nombre, tipo) = ocio[i % ocio.size]
                val vuelta = i / ocio.size
                add(Triple(if (vuelta == 0) nombre else "$nombre ${vuelta + 1}", grupo, tipo))
            }
        }.ifEmpty { listOf(Triple("Planta 1", emptyList(), Sala.OFICINA)) }
    }

    val stats = remember(info) { runCatching { JSONObject(info) }.getOrNull() }
    val proveedor = stats?.optString("provider")?.ifBlank { null }

    val pager = androidx.compose.foundation.pager.rememberPagerState { plantas.size }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Carrusel: se desliza de planta en planta como un álbum de fotos.
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
            ) { pagina ->
            val (titulo, deLaPlanta, tipoSala) = plantas[pagina]
            AgentRoom(
                agentes = deLaPlanta,
                proveedor = if (running) proveedor ?: "local" else null,
                modifier = Modifier.fillMaxSize(),
                skins = skinsGuardados,
                estado = if (running) botUser?.let { "en marcha · $it" } ?: "en marcha"
                         else "detenido",
                activo = running,
                piso = pagina,
                sala = tipoSala,
                totales = agentesDemo.size,
                titulo = titulo,
                // Los que acaban de recibir un mensaje entran andando.
                reciénLlegados = remember(tic / 4000) {
                    Actividad.leer(ctxSala)
                        .filterValues { System.currentTimeMillis() - it < 12_000L }
                        .keys
                },
            )
            }

            // Puntos del carrusel: dónde estás y cuántas plantas hay.
            if (plantas.size > 1) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    plantas.indices.forEach { i ->
                        val aqui = i == pager.currentPage
                        Box(
                            Modifier.size(if (aqui) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (aqui) MaterialTheme.colorScheme.primary
                                    else Color(0xFF6B5B4C)
                                )
                        )
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (running) {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Icon(Lucide.Square, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                    Text("Detener agente")
                }
            } else if (configured) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Icon(Lucide.Play, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                    Text("Iniciar agente")
                }
            } else {
                Button(onClick = onGoConfig, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Icon(Lucide.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                    Text("Configura tu bot para empezar")
                }
            }
            if (running && stats != null && stats.optBoolean("running", false)) {
                StatsCard(stats)
            }
        }
    }
}

@Composable
private fun AgentHero(running: Boolean, botUser: String?) {
    val infinite = rememberInfiniteTransition(label = "hero")
    val glow by infinite.animateFloat(
        initialValue = if (running) 0.30f else 0.16f,
        targetValue = if (running) 0.08f else 0.12f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "glow",
    )
    val ring by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (running) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "ring",
    )
    val floatY by infinite.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2300), RepeatMode.Reverse),
        label = "floatY",
    )
    // parpadeo: el robot "cierra los ojos" un instante cada ~3.4s (squash vertical)
    val blink by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3400
                1f at 0
                1f at 3000
                0.12f at 3150
                1f at 3300
            },
        ),
        label = "blink",
    )
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(116.dp).scale(ring).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = glow)),
                )
                Box(
                    Modifier.size(82.dp).offset(y = floatY.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Lucide.Bot, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp).scale(scaleX = 1f, scaleY = blink),
                    )
                }
            }
            StatusPill(running)
            if (running && botUser != null) {
                Text("Conectado como $botUser", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                if (running) "Activo y atento, trabajando para ti las 24 horas."
                else "Inteligencia artificial real, dentro de tu teléfono.\nSin nube, sin Google.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureCard(cap: Capability) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(cap.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cap.label, style = MaterialTheme.typography.titleSmall)
                Text(cap.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ModVeinticuatroSiete() {
    val ctx = LocalContext.current
    val store = remember { ConfigStore(ctx) }
    var ignoringBatt by remember { mutableStateOf(isIgnoringBattery(ctx)) }
    var autostart by remember { mutableStateOf(store.autostart) }
    LaunchedEffect(Unit) { ignoringBatt = isIgnoringBattery(ctx) }

    ColumnaModulo {
        SectionCard("Funcionar 24/7", Lucide.Zap) {
            Text(
                "Para que el agente no muera en segundo plano: exonéralo del ahorro de " +
                    "batería y habilita su autoarranque (clave en Huawei/Xiaomi/Oppo/Vivo).",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Reiniciar tras encender el teléfono", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = autostart, onCheckedChange = { autostart = it; store.autostart = it })
            }
            if (ignoringBatt) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Lucide.Check, null, tint = Green, modifier = Modifier.size(18.dp))
                    Text("Optimización de batería desactivada", color = Green, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Button(
                    onClick = { requestIgnoreBattery(ctx) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) {
                    Icon(Lucide.ShieldCheck, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                    Text("Quitar ahorro de batería")
                }
            }
            OutlinedButton(
                onClick = { openAutostartSettings(ctx) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) {
                Icon(Lucide.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                Text("Ajustes de autoarranque")
            }
            OutlinedButton(
                onClick = { openUrl(ctx, "https://dontkillmyapp.com/${Build.MANUFACTURER.lowercase()}") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) { Text("Guía anti-cierre (dontkillmyapp)") }
        }
    }
}

@Composable
internal fun ModModeloLocal() {
    LocalModelSection()
}

/** Envoltorio común: scroll y márgenes iguales en todos los módulos. */
@Composable
internal fun ColumnaModulo(contenido: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { contenido() }
}

@Composable
private fun StatusPill(running: Boolean) {
    val c = if (running) Green else MaterialTheme.colorScheme.outline
    Surface(color = c.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
            Text(if (running) "ACTIVO" else "DETENIDO", color = c, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatsCard(s: JSONObject) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            StatItem(s.optString("provider", "—").ifBlank { "—" }, "Proveedor")
            StatItem(duracion(s.optLong("uptime_s", 0)), "Activo")
            StatItem(s.optInt("tokens", 0).toString(), "Tokens")
            StatItem(s.optInt("requests", 0).toString(), "Pedidos")
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    PixelWindow(title = title, icon = icon, content = content)
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onStart() }
    val infinite = rememberInfiniteTransition(label = "welcome")
    val flota by infinite.animateFloat(
        initialValue = 0f, targetValue = -1f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "flota",
    )
    val brillo by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "brillo",
    )
    val feats = listOf(
        Triple(Lucide.Bot, "Agentes que trabajan solos", "Notas, listas, agenda y avisos, en su oficina"),
        Triple(Lucide.ShieldCheck, "Sin Google y sin nube obligatoria", "El modelo puede correr dentro del propio teléfono"),
        Triple(Lucide.Zap, "Con los sentidos del teléfono", "Cámara, ubicación, SMS, batería y llamadas"),
    )

    Box(
        Modifier.fillMaxSize().background(
            // El mismo degradado de la sala: la bienvenida y la pantalla de
            // inicio tienen que parecer el mismo sitio.
            Brush.verticalGradient(listOf(PARED, MaterialTheme.colorScheme.background)),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            AnimatedVisibility(show, enter = fadeIn(tween(800)) + scaleIn(initialScale = 0.75f, animationSpec = tween(800))) {
                PortadaPixel(flota, brillo, Modifier.fillMaxWidth().height(232.dp))
            }
            AnimatedVisibility(show, enter = fadeIn(tween(700, 250))) {
                Text(
                    "Convierte este teléfono en un equipo de agentes que trabaja " +
                        "solo. Tú le hablas por Telegram.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                )
            }
            Spacer(Modifier.size(6.dp))
            feats.forEachIndexed { i, (icon, t, s) ->
                AnimatedVisibility(show, enter = fadeIn(tween(600, 400 + i * 150)) + slideInHorizontally { it / 3 }) {
                    WelcomeFeature(icon, t, s)
                }
            }
            Spacer(Modifier.height(26.dp))
            AnimatedVisibility(show, enter = fadeIn(tween(700, 900)) + slideInVertically { it / 2 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Al comenzar te pedirá permisos (cámara, ubicación, SMS, micrófono): " +
                            "son los sentidos del agente y tú decides cuáles le das. " +
                            "Puedes cambiarlos después.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Button(
                        onClick = {
                            val perms = buildList {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                add(Manifest.permission.CAMERA)
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                add(Manifest.permission.SEND_SMS)
                                add(Manifest.permission.READ_SMS)
                                add(Manifest.permission.CALL_PHONE)
                                add(Manifest.permission.RECORD_AUDIO)
                                add(Manifest.permission.READ_CONTACTS)
                            }.toTypedArray()
                            permLauncher.launch(perms)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Abrir la oficina", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "by Indaga Lab",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

/**
 * La portada: el robot de la sala sobre su plataforma y el rótulo AGENTOS.
 *
 * Se dibuja con los mismos sprites que la oficina en vez de con el PNG del
 * icono. Así la primera pantalla ya enseña de qué va la app y no hay una
 * imagen suelta que se quede vieja cuando el arte cambie.
 */
@Composable
private fun PortadaPixel(flota: Float, brillo: Float, modifier: Modifier) {
    val rotulo = remember { textoPixel("AGENTOS", ACENTO) }
    Canvas(modifier) {
        val cx = size.width / 2f
        val esc = ((size.height * 0.40f) / SPRITE_ROBOT.h).toInt().coerceAtLeast(2)
        val altoRobot = SPRITE_ROBOT.h * esc
        val baseY = size.height * 0.60f
        val subida = flota * (esc * 1.5f)

        // Plataforma isométrica: el suelo de la oficina, visto de cerca.
        val tileW = SPRITE_ROBOT.w * esc * 1.9f
        val tileH = tileW / 2f
        val centro = Offset(cx, baseY + tileH * 0.30f)
        drawPath(
            Path().apply {
                val p = tileDiamond(centro, tileW, tileH)
                moveTo(p[0].x, p[0].y); p.drop(1).forEach { lineTo(it.x, it.y) }; close()
            },
            SUELO_B,
        )
        drawPath(
            Path().apply {
                val p = tileDiamond(centro, tileW, tileH)
                moveTo(p[0].x, p[0].y); p.drop(1).forEach { lineTo(it.x, it.y) }; close()
            },
            ACENTO.copy(alpha = 0.20f + brillo * 0.18f),
            style = Stroke(width = esc.toFloat()),
        )

        // Sombra: encoge cuando el robot sube. Es lo que vende el flote.
        drawOval(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(cx - tileW * (0.24f + flota * 0.05f), centro.y - tileH * 0.14f),
            size = Size(tileW * (0.48f + flota * 0.10f), tileH * 0.28f),
        )

        drawSpriteFooted(SPRITE_ROBOT, Offset(cx, baseY + subida), esc)

        // Dos chispas que orbitan al robot, del color de la marca.
        listOf(-1f, 1f).forEachIndexed { i, lado ->
            val y = baseY - altoRobot * (0.55f + 0.35f * if (i == 0) brillo else 1f - brillo)
            drawRect(
                ACENTO.copy(alpha = 0.25f + brillo * 0.5f),
                topLeft = Offset(cx + lado * tileW * 0.34f, y),
                size = Size(esc.toFloat(), esc.toFloat()),
            )
        }

        // Rótulo, con el mismo alfabeto de píxeles que el letrero de la sala.
        rotulo?.let {
            val escT = ((size.width * 0.62f) / it.w).toInt().coerceAtLeast(1)
            drawSprite(
                it,
                Offset(cx - it.w * escT / 2f, size.height - it.h * escT - esc * 2f),
                escT,
            )
        }
    }
}

@Composable
private fun WelcomeFeature(icon: ImageVector, title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Cuadrado con borde, no círculo: la app entera es pixel art y un
        // círculo de Material aquí desentona.
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- acciones del servicio ----------
private fun startAgent(ctx: Context) {
    ContextCompat.startForegroundService(ctx, Intent(ctx, AgentService::class.java))
}

private fun stopAgent(ctx: Context) {
    ctx.stopService(Intent(ctx, AgentService::class.java))
    AgentState.running.value = false
}

// ---------- 24/7: batería y autoarranque por fabricante ----------
private fun isIgnoringBattery(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

@android.annotation.SuppressLint("BatteryLife")
private fun requestIgnoreBattery(ctx: Context) {
    val direct = Intent(
        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${ctx.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(direct) }.onFailure {
        runCatching {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun openAutostartSettings(ctx: Context) {
    // Pantallas de autoarranque conocidas por fabricante; con fallback a los detalles de la app.
    val candidates = listOf(
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
    )
    for ((pkg, cls) in candidates) {
        val ok = runCatching {
            ctx.startActivity(
                Intent().setComponent(android.content.ComponentName(pkg, cls))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
        if (ok) return
    }
    runCatching {
        ctx.startActivity(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${ctx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ---------- guía: de dónde saco las API keys ----------
private data class KeyGuide(
    val provider: String,
    val envKey: String,
    val where: String,
    val limits: String,
    val free: Boolean,
)

private val KEY_GUIDES = listOf(
    KeyGuide("Groq", "GROQ_API_KEY", "console.groq.com/keys", "30 RPM · 14.400 req/día · muy rápido", true),
    KeyGuide("Cerebras", "CEREBRAS_API_KEY", "cloud.cerebras.ai", "30 RPM · 1.000.000 tokens/día", true),
    KeyGuide("Mistral", "MISTRAL_API_KEY", "console.mistral.ai", "60 RPM · 1.000M tokens/mes", true),
    KeyGuide("NVIDIA NIM", "NVIDIA_API_KEY", "build.nvidia.com", "40 RPM", true),
    KeyGuide("SambaNova", "SAMBANOVA_API_KEY", "cloud.sambanova.ai", "crédito $5 · 3 meses", true),
    KeyGuide("Google Gemini", "GOOGLE_API_KEY", "aistudio.google.com/apikey", "~10 RPM · ~1.000 req/día", true),
    KeyGuide("OpenRouter", "OPENROUTER_API_KEY", "openrouter.ai/keys", "20 RPM · 50 req/día (1.000 con $10)", true),
    KeyGuide("Cohere", "COHERE_API_KEY", "dashboard.cohere.com/api-keys", "20 RPM · 1.000 req/mes", true),
    KeyGuide("AI21", "AI21_API_KEY", "studio.ai21.com", "crédito $10 · 3 meses", true),
    KeyGuide("Chutes", "CHUTES_API_KEY", "chutes.ai", "requiere saldo (TAO/fiat)", false),
    KeyGuide("Z.ai (GLM)", "ZAI_API_KEY", "z.ai", "requiere crédito de la cuenta", false),
)

@Composable
private fun KeysGuideCard(env: String, onEnvChange: (String) -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Lucide.KeyRound, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    "¿De dónde saco las API keys?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (open) Lucide.ChevronDown else Lucide.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) {
                Text(
                    "Toca un proveedor para abrir su web y crear la key gratis. \"Usar\" agrega la línea " +
                        "al recuadro de arriba para que pegues tu key. El agente alterna solo entre los gratuitos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KEY_GUIDES.forEach { g ->
                    KeyGuideRow(g, ctx) { line ->
                        val base = env.trimEnd()
                        onEnvChange(if (base.isEmpty()) line else base + "\n" + line)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyGuideRow(g: KeyGuide, ctx: Context, onUse: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { openUrl(ctx, "https://" + g.where) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(g.provider, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val tagColor = if (g.free) Green else MaterialTheme.colorScheme.tertiary
            Surface(color = tagColor.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
                Text(
                    if (g.free) "FREE" else "saldo",
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = tagColor,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(
                onClick = { onUse(g.envKey + "=") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("Usar") }
        }
        Text(g.limits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(g.where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}


/**
 * Quién sale en la sala.
 *
 * Los skills instalados (los lee el loader sin ejecutarlos) más las capacidades
 * que trae el núcleo. Antes era una lista fija de seis nombres inventados: la
 * sala enseñaba lo mismo con el agente en marcha que parado.
 */
private val NUCLEO = listOf("telefono", "agenda", "web", "antirrobo", "notas", "listas")

internal fun agentesDeLaSala(running: Boolean, ctx: Context? = null): List<Agente> {
    // Quien acaba de recibir un mensaje está en su puesto, esté el bot en
    // marcha o no: la conversación con él ES su tarea.
    val conRecado = ctx?.let { Actividad.activos(it, System.currentTimeMillis()) }.orEmpty()
    val instalados = runCatching {
        val txt = Python.getInstance().getModule("jarvis").callAttr("skills").toString()
        val arr = org.json.JSONArray(txt)
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .map { it.optString("id").ifBlank { it.optString("name") } to it.optString("status") }
    }.getOrDefault(emptyList())

    // Los que el usuario se ha creado a mano. Van primero: son suyos.
    //
    // Uno recién creado no tiene nada que hacer todavía: hasta que no le pongas
    // datos, documentos o instrucciones, se queda en la zona de descanso. Así
    // de un vistazo se ve quién está configurado y quién no, y las oficinas
    // salen con unos trabajando y otros con la silla vacía.
    val propios = ctx?.let { c ->
        com.indagalab.agentos.ui.pixel.Skins.propios(c).map { s ->
            val ficha = Fichas.cargar(c, s.id)
            val tieneTarea = ficha.fuentes.isNotEmpty() || ficha.documentos.isNotEmpty() ||
                ficha.skills.isNotEmpty() || ficha.instrucciones.isNotBlank()
            s.id to if (tieneTarea) "ok" else "skipped"
        }
    }.orEmpty()

    val vistos = (instalados + propios).map { it.first }.toSet()
    // El núcleo no se instala ni falla: o está trabajando o está durmiendo.
    val nucleo = NUCLEO.filterNot { it in vistos }.map { it to "ok" }
    val todos = propios + instalados + nucleo
    return agentesDesde(
        todos.map { (id, estado) -> id to if (id in conRecado) "ok" else estado },
        agenteCorriendo = running,
    ).map { a ->
        // agentesDesde duerme a todos si el bot está parado; el que tiene
        // recado se queda despierto igualmente.
        if (a.nombre in conRecado && a.estado == Estado.DURMIENDO)
            a.copy(estado = Estado.TRABAJANDO) else a
    }
}
