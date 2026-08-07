package com.indagalab.agentos.ui

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Quién ha hecho algo hace poco.
 *
 * Es lo que conecta el chat con la sala: si le escribes al agente de notas,
 * sube a su escritorio y se pone a trabajar; cuando pasa un rato sin que nadie
 * le diga nada, se baja a la zona de descanso.
 *
 * Un solo JSON con `agente -> instante`. No hace falta más: son unas pocas
 * marcas de tiempo que se leen enteras cada vez que se pinta la sala.
 */
object Actividad {

    /** Cuánto se queda en su puesto tras el último recado. */
    const val VENTANA_MS = 5 * 60_000L

    private fun archivo(ctx: Context) = File(ctx.filesDir, "actividad.json")

    fun marcar(ctx: Context, agente: String, ahora: Long) {
        val datos = leer(ctx).toMutableMap()
        datos[agente] = ahora
        // Sólo interesa lo reciente: sin esto el archivo crecería para siempre
        // guardando agentes que ya no existen.
        val vivos = datos.filterValues { ahora - it < VENTANA_MS * 4 }
        runCatching {
            archivo(ctx).writeText(JSONObject(vivos.mapValues { it.value }).toString())
        }
    }

    fun leer(ctx: Context): Map<String, Long> = runCatching {
        val f = archivo(ctx)
        if (!f.isFile) return emptyMap()
        val o = JSONObject(f.readText())
        o.keys().asSequence().associateWith { o.optLong(it) }
    }.getOrDefault(emptyMap())

    /** Los que están trabajando ahora mismo porque han tenido recado hace poco. */
    fun activos(ctx: Context, ahora: Long): Set<String> =
        leer(ctx).filterValues { ahora - it < VENTANA_MS }.keys

    /** Hace cuánto le hablaron, o null si nunca. Para pintar "llega ahora". */
    fun desdeUltimo(ctx: Context, agente: String, ahora: Long): Long? =
        leer(ctx)[agente]?.let { ahora - it }
}
