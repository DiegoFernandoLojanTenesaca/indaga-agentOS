package com.indagalab.agentos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.List
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

private val Green = Color(0xFF16A34A)
private val CONNECTED = Regex("Conectado como (@\\S+)")

private data class Capability(val icon: ImageVector, val label: String, val ready: Boolean)

private val CAPABILITIES = listOf(
    Capability(Lucide.Bot, "Chat con IA", true),
    Capability(Lucide.Bell, "Recordatorios", true),
    Capability(Lucide.List, "Listas", true),
    Capability(Lucide.BookOpen, "Diario", true),
    Capability(Lucide.Cloud, "Clima", true),
    Capability(Lucide.Globe, "Búsqueda web", true),
    Capability(Lucide.FileText, "Leer PDFs", true),
    Capability(Lucide.Camera, "Cámara", true),
    Capability(Lucide.MapPin, "Ubicación", true),
    Capability(Lucide.MessageSquare, "SMS", true),
    Capability(Lucide.Mic, "Voz", true),
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
    var token by remember { mutableStateOf(store.token) }
    var env by remember { mutableStateOf(store.envBlob) }
    var logs by remember { mutableStateOf("—") }
    val running = AgentState.running.value
    val botUser = CONNECTED.find(logs)?.groupValues?.getOrNull(1)

    LaunchedEffect(Unit) {
        while (true) {
            logs = try {
                Python.getInstance().getModule("jarvis").callAttr("get_logs").toString()
            } catch (e: Exception) {
                "(agente aún no iniciado)"
            }
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("AgentOS")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Lucide.House, null) }, label = { Text("Inicio") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Lucide.Settings, null) }, label = { Text("Config") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Lucide.Smartphone, null) }, label = { Text("Sistema") })
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Lucide.ScrollText, null) }, label = { Text("Logs") })
                NavigationBarItem(tab == 4, { tab = 4 }, { Icon(Lucide.Info, null) }, label = { Text("Acerca") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> HomeScreen(running, token.isNotBlank(), botUser, { startAgent(ctx) }, { stopAgent(ctx) })
                1 -> ConfigScreen(token, env, { token = it }, { env = it }) {
                    store.token = token.trim(); store.envBlob = env.trim()
                }
                2 -> SystemScreen(env, running)
                3 -> LogsScreen(logs)
                else -> AboutScreen()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    running: Boolean,
    configured: Boolean,
    botUser: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Bot, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                }
                StatusPill(running)
                if (running && botUser != null) {
                    Text("Conectado como $botUser", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    if (running) "Tu asistente personal está activo y atento."
                    else "Un asistente brillante en tu bolsillo,\nlisto para ayudarte 24/7.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (running) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Lucide.Square, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text("Detener agente")
            }
        } else {
            Button(onClick = onStart, enabled = configured, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Icon(Lucide.Play, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                Text(if (configured) "Iniciar agente" else "Configura tu bot primero")
            }
        }

        Text("Lo que puede hacer", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CAPABILITIES.forEach { CapabilityChip(it) }
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
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Bot de Telegram", Lucide.Bot) {
            OutlinedTextField(
                value = token,
                onValueChange = { onTokenChange(it); saved = false },
                label = { Text("Bot Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Lo da @BotFather en Telegram.", style = MaterialTheme.typography.bodySmall)
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
        Text("Las claves se guardan solo en este dispositivo.", style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SystemScreen(env: String, running: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Dispositivo", Lucide.Smartphone) {
            DetailRow("Modelo", "${Build.MANUFACTURER} ${Build.MODEL}")
            DetailRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            DetailRow("Arquitectura", Build.SUPPORTED_ABIS.firstOrNull() ?: "—")
            DetailRow("Estado agente", if (running) "Activo" else "Detenido")
        }
        SectionCard("Modelos de IA", Lucide.Zap) {
            Text("Proveedores soportados (verde = key configurada):", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PROVIDERS.forEach { p ->
                    val on = env.lineSequence().any {
                        val l = it.trim(); l.startsWith("${p.envKey}=") && l.substringAfter("=").isNotBlank()
                    }
                    ProviderChip(p.name, on)
                }
            }
        }
    }
}

@Composable
private fun LogsScreen(logs: String) {
    val scroll = rememberScrollState()
    LaunchedEffect(logs) { scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Lucide.ScrollText, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Actividad", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = {
                try { Python.getInstance().getModule("jarvis").callAttr("clear_logs") } catch (_: Exception) {}
            }) { Icon(Lucide.Trash2, contentDescription = "Limpiar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
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
                "Un asistente de IA personal que vive en tu teléfono y te ayuda 24/7 por Telegram: " +
                    "responde, recuerda, organiza y automatiza tu día.",
                style = MaterialTheme.typography.bodyMedium,
            )
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
private fun CapabilityChip(cap: Capability) {
    val tint = if (cap.ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
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
private fun ProviderChip(name: String, on: Boolean) {
    val c = if (on) Green else MaterialTheme.colorScheme.outline
    Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c))
            Text(name, color = c, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
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

// ---------- bienvenida / onboarding ----------
@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)),
        )
        Text("AgentOS", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Tu agente de IA personal,\n24/7 en tu teléfono, por Telegram.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )
        Spacer(Modifier.size(18.dp))
        WelcomeFeature(Lucide.Bot, "IA conversacional", "Las mejores IAs, gratis")
        WelcomeFeature(Lucide.ShieldCheck, "Privado · sin Google", "Corre en tu propio dispositivo")
        WelcomeFeature(Lucide.Zap, "Superpoderes", "Cámara, GPS, recordatorios y más")
        Spacer(Modifier.weight(1f))
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
