package com.indagalab.agentos.service

import androidx.compose.runtime.mutableStateOf

/**
 * Estado compartido del agente, observable desde Compose. Vive en el mismo
 * proceso que el servicio y la UI, así que un MutableState basta.
 */
object AgentState {
    val running = mutableStateOf(false)
}
