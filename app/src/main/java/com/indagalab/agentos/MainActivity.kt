package com.indagalab.agentos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.indagalab.agentos.ui.AppScaffold
import com.indagalab.agentos.ui.theme.AgentOSTheme

/**
 * Única Activity. NO pide permisos al arrancar (eso se hace explicado en el
 * onboarding, al tocar "Comenzar"). Solo monta la UI Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AgentOSTheme { AppScaffold() } }
    }
}
