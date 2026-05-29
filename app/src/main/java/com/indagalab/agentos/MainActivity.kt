package com.indagalab.agentos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.indagalab.agentos.service.AgentService
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MaterialTheme { Screen() } }
    }

    @Composable
    private fun Screen() {
        var token by remember { mutableStateOf("") }
        var running by remember { mutableStateOf(false) }
        var logs by remember { mutableStateOf("—") }

        LaunchedEffect(Unit) {
            while (true) {
                logs = try {
                    Python.getInstance().getModule("jarvis").callAttr("get_logs").toString()
                } catch (e: Exception) {
                    "(python aún no iniciado)"
                }
                delay(1000)
            }
        }

        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(20.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(32.dp))
                Text("AgentOS — Fase 0", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Agente Python embebido (Chaquopy) por Telegram. Sin Google.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Telegram Bot Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val i = Intent(this@MainActivity, AgentService::class.java)
                                .putExtra(AgentService.EXTRA_TOKEN, token.trim())
                            ContextCompat.startForegroundService(this@MainActivity, i)
                            running = true
                        },
                        enabled = token.isNotBlank()
                    ) { Text("Iniciar") }
                    OutlinedButton(onClick = {
                        stopService(Intent(this@MainActivity, AgentService::class.java))
                        running = false
                    }) { Text("Detener") }
                }
                Text(
                    if (running) "● corriendo" else "○ detenido",
                    style = MaterialTheme.typography.labelLarge
                )
                HorizontalDivider()
                Text("Logs", style = MaterialTheme.typography.titleSmall)
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    Text(
                        logs,
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
