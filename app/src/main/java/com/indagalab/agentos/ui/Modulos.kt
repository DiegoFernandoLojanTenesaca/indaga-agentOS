package com.indagalab.agentos.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Cpu
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.Zap

/**
 * Los módulos de AgentOS.
 *
 * Antes había 5 pestañas abajo y cada una amontonaba de todo: "Sistema" metía
 * en la misma pantalla el hardware, el 24/7, el modelo local, los proveedores
 * de nube y el mapa de actividad. Cinco temas distintos con scroll infinito.
 *
 * Ahora cada asunto es su propio módulo y se llega por el panel lateral. Regla:
 * **un módulo, una idea**. Si una pantalla necesita scroll largo para
 * entenderse, es que son dos módulos.
 */
enum class Modulo(
    val titulo: String,
    val grupo: String,
    val icono: ImageVector,
    val descripcion: String,
) {
    SALA("La sala", "Agente", Lucide.House,
        "Tus agentes trabajando"),
    REGISTRO("Registro", "Agente", Lucide.ScrollText,
        "Qué ha hecho el agente"),
    EQUIPO("El equipo", "Agente", Lucide.Users,
        "Nombre, rol y aspecto de cada uno"),
    ACTIVIDAD("Actividad", "Agente", Lucide.Activity,
        "Uso por día"),

    CHAT("Chat local", "Inteligencia", Lucide.MessageSquare,
        "Habla con el modelo del teléfono"),
    PALS("Pals", "Inteligencia", Lucide.Users,
        "Personalidades de la comunidad"),
    MODELO_LOCAL("Modelo local", "Inteligencia", Lucide.Cpu,
        "El modelo que corre en el teléfono"),
    PROVEEDORES("Proveedores", "Inteligencia", Lucide.Cloud,
        "Groq, Gemini, Mistral…"),

    BOT("Bot de Telegram", "Configuración", Lucide.Bot,
        "Token y conexión"),
    CLAVES("Claves y variables", "Configuración", Lucide.KeyRound,
        "API keys y ajustes"),

    DISPOSITIVO("Dispositivo", "Sistema", Lucide.Smartphone,
        "Modelo, Android, arquitectura"),
    VEINTICUATRO_SIETE("Funcionar 24/7", "Sistema", Lucide.Zap,
        "Batería y autoarranque"),

    FUNCIONES("Funciones", "Ayuda", Lucide.LayoutGrid,
        "Todo lo que sabe hacer"),
    ACERCA("Acerca de", "Ayuda", Lucide.Info,
        "Qué es AgentOS"),
    ;

    companion object {
        /** Módulos agrupados, en el orden en que se pintan en el panel. */
        val porGrupo: List<Pair<String, List<Modulo>>> by lazy {
            entries.groupBy { it.grupo }
                .toList()
                .sortedBy { (g, _) ->
                    listOf("Agente", "Inteligencia", "Configuración", "Sistema", "Ayuda")
                        .indexOf(g).let { if (it < 0) 99 else it }
                }
        }
    }
}
