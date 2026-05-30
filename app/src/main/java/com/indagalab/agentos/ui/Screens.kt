package com.indagalab.agentos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import org.json.JSONObject

private val Green = Color(0xFF16A34A)
private val CONNECTED = Regex("Conectado como (@\\S+)")

private data class Capability(val icon: ImageVector, val label: String, val ready: Boolean, val desc: String)

private val CAPABILITIES = listOf(
    Capability(Lucide.Bot, "Chat con IA", true, "Conversá con la IA por Telegram: preguntas, redacción, traducción y código. Vos elegís el proveedor (Groq, Gemini, Cohere…) y el modo (normal, profe, coder)."),
    Capability(Lucide.Bell, "Recordatorios", true, "Pedile «recuérdame en 30m sacar la ropa» y te avisa a tiempo. Soporta minutos, horas o una hora puntual."),
    Capability(Lucide.List, "Listas", true, "Listas con casillas marcables (compras, tareas, lo que sea), editables desde el chat con botones."),
    Capability(Lucide.BookOpen, "Diario", true, "A las 22h te pregunta cómo fue tu día y lo guarda. Pedí resúmenes por semana o mes cuando quieras."),
    Capability(Lucide.Cloud, "Clima", true, "Clima actual de tu ciudad y un briefing matutino con clima + las 3 noticias locales del día."),
    Capability(Lucide.Globe, "Búsqueda web", true, "Busca en internet y te resume citando fuentes. También resume cualquier URL o video que le pases."),
    Capability(Lucide.FileText, "Leer PDFs", true, "Mandale un PDF por Telegram y lo lee para responder preguntas sobre su contenido."),
    Capability(Lucide.Camera, "Cámara", true, "Toma fotos o selfies con la cámara del teléfono y la IA describe lo que ve. Incluye vigilancia y antirrobo con reconocimiento."),
    Capability(Lucide.MapPin, "Ubicación", true, "Te da la ubicación GPS del teléfono con link a mapas — clave si lo perdés o te lo roban."),
    Capability(Lucide.MessageSquare, "SMS", true, "Lee y envía SMS desde el chat, y te reenvía automáticamente los códigos OTP que llegan a tu SIM."),
    Capability(Lucide.Mic, "Voz", true, "Mandale notas de voz (las transcribe con IA) y puede responderte hablando por el altavoz del teléfono."),
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
    var tab by remember { mutableStateOf(0) }
    var prevTab by remember { mutableStateOf(0) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(if (tab == 5) "Acerca" else "AgentOS", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    if (tab == 5) {
                        IconButton(onClick = { tab = prevTab }) {
                            Icon(Lucide.ArrowLeft, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                },
                actions = {
                    if (tab != 5) {
                        IconButton(onClick = { prevTab = tab; tab = 5 }) {
                            Icon(Lucide.Info, contentDescription = "Acerca", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Lucide.House, null) }, label = { Text("Inicio", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }, alwaysShowLabel = false)
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Lucide.LayoutGrid, null) }, label = { Text("Funciones", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }, alwaysShowLabel = false)
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Lucide.Settings, null) }, label = { Text("Config", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }, alwaysShowLabel = false)
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Lucide.Smartphone, null) }, label = { Text("Sistema", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }, alwaysShowLabel = false)
                NavigationBarItem(selected = tab == 4, onClick = { tab = 4 }, icon = { Icon(Lucide.ScrollText, null) }, label = { Text("Logs", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) }, alwaysShowLabel = false)
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 14 }) togetherWith fadeOut(tween(140))
                },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> HomeScreen(running, token.isNotBlank(), botUser, info, { startAgent(ctx) }, { stopAgent(ctx) }, { tab = 2 }, { tab = 1 })
                    1 -> FuncionesScreen()
                    2 -> ConfigScreen(token, env, { token = it }, { env = it }) {
                        store.token = token.trim(); store.envBlob = env.trim()
                    }
                    3 -> SystemScreen(env, running, info)
                    4 -> LogsScreen(logs)
                    else -> AboutScreen()
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
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        AgentHero(running, botUser)
        val stats = remember(info) { runCatching { JSONObject(info) }.getOrNull() }
        if (running && stats != null && stats.optBoolean("running", false)) {
            StatsCard(stats)
        }

        if (running) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Icon(Lucide.Square, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Detener agente")
            }
        } else if (configured) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Icon(Lucide.Play, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Iniciar agente")
            }
        } else {
            FilledTonalButton(onClick = onGoConfig, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Icon(Lucide.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Configurá tu bot primero")
            }
        }

        Card(
            Modifier.fillMaxWidth().clickable { onGoFunciones() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.LayoutGrid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("Funciones", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Chat, fotos, GPS, SMS, recordatorios y más — tocá para verlas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Lucide.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
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
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (running) 1.10f else 1.03f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "scale",
    )
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(116.dp).scale(scale).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = glow)),
                )
                Box(
                    Modifier.size(82.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.Bot, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) }
            }
            StatusPill(running)
            if (running && botUser != null) {
                Text("Conectado como $botUser", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                if (running) "Activo y atento, trabajando para vos las 24 horas."
                else "Inteligencia artificial real, dentro de tu teléfono.\nSin nube, sin Google.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FuncionesScreen() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text("Lo que puede hacer", style = MaterialTheme.typography.titleLarge)
        Text(
            "Tu agente con superpoderes. Todo se controla por Telegram.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        CAPABILITIES.forEach { cap -> FeatureCard(cap) }
        Spacer(Modifier.height(8.dp))
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
private fun ConfigScreen(
    token: String,
    env: String,
    onTokenChange: (String) -> Unit,
    onEnvChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    var saved by remember { mutableStateOf(false) }
    var tokenVisible by remember { mutableStateOf(false) }
    var howOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Bot de Telegram", Lucide.Bot) {
            OutlinedTextField(
                value = token,
                onValueChange = { onTokenChange(it); saved = false },
                label = { Text("Bot Token") },
                singleLine = true,
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            if (tokenVisible) Lucide.EyeOff else Lucide.Eye,
                            contentDescription = if (tokenVisible) "Ocultar token" else "Ver token",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { howOpen = !howOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "¿Cómo consigo el token?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (howOpen) Lucide.ChevronDown else Lucide.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
            }
            if (howOpen) {
                Text(
                    "1) En Telegram abrí @BotFather.\n" +
                        "2) Enviá /newbot y seguí los pasos (nombre + @usuario del bot).\n" +
                        "3) Te da un token tipo 123456789:AAE… — pegalo arriba.\n" +
                        "4) Escribíle a tu bot: el primer chat que escribe queda como dueño.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Lo da @BotFather en Telegram.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionCard("Variables y API keys", Lucide.KeyRound) {
            OutlinedTextField(
                value = env,
                onValueChange = { onEnvChange(it); saved = false },
                label = { Text("KEY=VALOR por línea") },
                placeholder = { Text("OWNER_ID=...\nGROQ_API_KEY=...\nCITY=Loja") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("OWNER_ID, GROQ_API_KEY, CITY… una por línea.", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { onSave(); saved = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Lucide.Save, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Guardar configuración")
        }
        if (saved) {
            Text("Guardado. Detén e inicia el agente para aplicar.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        Text("Las claves se guardan solo en este dispositivo (cifradas).", style = MaterialTheme.typography.bodySmall)
        KeysGuideCard(env, onEnvChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SystemScreen(env: String, running: Boolean, info: String) {
    val ctx = LocalContext.current
    val store = remember { ConfigStore(ctx) }
    var ignoringBatt by remember { mutableStateOf(isIgnoringBattery(ctx)) }
    var autostart by remember { mutableStateOf(store.autostart) }
    LaunchedEffect(Unit) { ignoringBatt = isIgnoringBattery(ctx) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Dispositivo", Lucide.Smartphone) {
            DetailRow("Modelo", "${Build.MANUFACTURER} ${Build.MODEL}")
            DetailRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            DetailRow("Arquitectura", Build.SUPPORTED_ABIS.firstOrNull() ?: "—")
            DetailRow("Estado agente", if (running) "Activo" else "Detenido")
        }

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

        SectionCard("Modelos de IA", Lucide.Zap) {
            val active = remember(info) { runCatching { JSONObject(info).optString("provider") }.getOrNull().orEmpty() }
            Text("Verde = key configurada. El activo se resalta cuando el agente corre.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PROVIDERS.forEach { p ->
                    val on = env.lineSequence().any {
                        val l = it.trim(); l.startsWith("${p.envKey}=") && l.substringAfter("=").isNotBlank()
                    }
                    val isActive = running && active.isNotBlank() &&
                        p.name.lowercase().replace(".", "").replace(" ", "") == active
                    ProviderChip(p.name, on, isActive)
                }
            }
        }
    }
}

@Composable
private fun LogsScreen(logs: String) {
    val scroll = rememberScrollState()
    val clip = LocalClipboardManager.current
    LaunchedEffect(logs) { scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Lucide.ScrollText, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Actividad", style = MaterialTheme.typography.titleMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { clip.setText(AnnotatedString(logs)) }) {
                    Text("Copiar", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = {
                    try { Python.getInstance().getModule("jarvis").callAttr("clear_logs") } catch (_: Exception) {}
                }) { Icon(Lucide.Trash2, contentDescription = "Limpiar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxSize()) {
            Text(
                logs,
                modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scroll),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun AboutScreen() {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)),
            )
            Text("AgentOS", style = MaterialTheme.typography.headlineSmall)
            Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                Text(
                    "Creado por Indaga Lab",
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        SectionCard("Qué es", Lucide.Bot) {
            Text(
                "AgentOS es tu asistente de IA personal viviendo DENTRO del teléfono. Lo controlás " +
                    "100% por Telegram: te responde, recuerda, organiza, lee PDFs, toma fotos, ve tu " +
                    "ubicación, lee y envía SMS, y automatiza tu día — 24/7, sin depender de la nube " +
                    "de nadie más que el modelo de IA que vos elijas.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SectionCard("Por qué es diferente", Lucide.ShieldCheck) {
            FeatureLine(Lucide.Ban, "Cero Google", "Corre en Huawei, ROMs de-Googled y cualquier Android 8+, sin Play Services ni Firebase.")
            FeatureLine(Lucide.Lock, "Privado", "Tus claves y datos viven cifrados en el teléfono (Android Keystore). Nada obligatorio en la nube.")
            FeatureLine(Lucide.Zap, "24/7 de verdad", "Servicio en segundo plano con auto-arranque al encender y watchdog que lo revive si se cae.")
            FeatureLine(Lucide.Layers, "Multi-IA gratis", "Elegís entre Groq, Gemini, Cohere, Mistral y más — con failover automático entre ellos.")
        }
        SectionCard("Tecnología", Lucide.Cpu) {
            DetailRow("Motor", "Python 3.13 (Chaquopy)")
            DetailRow("App", "Kotlin · Jetpack Compose")
            DetailRow("Datos", "SQLite local")
            DetailRow("Paquete", "com.indagalab.agentos")
            DetailRow("Android mínimo", "8 (API 26)")
        }
        FilledTonalButton(
            onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DiegoFernandoLojanTenesaca/indaga-agentOS"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Ver en GitHub") }
    }
}

// ---------- componentes ----------
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
private fun CapabilityChip(cap: Capability, onClick: () -> Unit) {
    val tint = if (cap.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(cap.icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(cap.label, style = MaterialTheme.typography.bodyMedium)
            if (!cap.ready) {
                Surface(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
                    Text(
                        "Pronto",
                        Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(name: String, on: Boolean, active: Boolean = false) {
    val c = if (on) Green else MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = if (active) accent.copy(alpha = 0.20f) else c.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = if (active) BorderStroke(1.dp, accent) else null,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (active) accent else c))
            Text(name, color = if (active) accent else c, style = MaterialTheme.typography.labelLarge)
            if (active) Text("· activo", color = accent, style = MaterialTheme.typography.labelSmall)
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
            StatItem(fmtUptime(s.optInt("uptime_s", 0)), "Activo")
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

private fun fmtUptime(s: Int): String = when {
    s <= 0 -> "—"
    s < 60 -> "${s}s"
    s < 3600 -> "${s / 60}m"
    else -> "${s / 3600}h ${(s % 3600) / 60}m"
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FeatureLine(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- bienvenida / onboarding ----------
@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }
    val infinite = rememberInfiniteTransition(label = "float")
    val floatY by infinite.animateFloat(
        initialValue = 0f, targetValue = -12f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "y",
    )
    val feats = listOf(
        Triple(Lucide.Bot, "IA conversacional", "Las mejores IAs, gratis"),
        Triple(Lucide.ShieldCheck, "Privado · sin Google", "Corre en tu propio dispositivo"),
        Triple(Lucide.Zap, "Superpoderes", "Cámara, GPS, recordatorios y más"),
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                ),
            ),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(44.dp))
            AnimatedVisibility(show, enter = fadeIn(tween(800)) + scaleIn(initialScale = 0.6f, animationSpec = tween(800))) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(118.dp).offset(y = floatY.dp).clip(RoundedCornerShape(30.dp)),
                )
            }
            AnimatedVisibility(show, enter = fadeIn(tween(700, 200)) + slideInVertically { it / 3 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AgentOS", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Tu agente de IA personal,\n24/7 en tu teléfono, por Telegram.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            feats.forEachIndexed { i, (icon, t, s) ->
                AnimatedVisibility(show, enter = fadeIn(tween(600, 350 + i * 160)) + slideInHorizontally { it / 3 }) {
                    WelcomeFeature(icon, t, s)
                }
            }
            Spacer(Modifier.height(44.dp))
            AnimatedVisibility(show, enter = fadeIn(tween(700, 850)) + slideInVertically { it / 2 }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Comenzar", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "by Indaga Lab",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }
            }
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
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column {
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
                    "Tocá un proveedor para abrir su web y crear la key gratis. \"Usar\" agrega la línea " +
                        "al recuadro de arriba para que pegues tu key. El agente alterna entre los free solo.",
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
