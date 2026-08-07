package com.indagalab.agentos.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Conversaciones del chat local, guardadas por agente.
 *
 * Un hilo por conversación, en `filesDir/chats/<id>.json`. Un JSON por hilo y
 * no una base de datos: son ficheros pequeños, se listan por fecha de
 * modificación y borrar una conversación es borrar un archivo.
 *
 * Guardar el historial **no** significa reenviarlo al modelo. Medido en este
 * teléfono, el prefill con tres turnos acumulados multiplica por 26 el tiempo
 * de respuesta, así que cada mensaje sigue yendo suelto: lo que se conserva es
 * lo que tú ves, no lo que el modelo procesa.
 */
object Conversaciones {

    data class Mensaje(val mio: Boolean, val texto: String, val ms: Long = 0)

    data class Hilo(
        val id: String,
        val agente: String,
        var titulo: String,
        var actualizado: Long,
        val mensajes: MutableList<Mensaje> = mutableListOf(),
    )

    private fun dir(ctx: Context) = File(ctx.filesDir, "chats").apply { mkdirs() }

    private fun archivo(ctx: Context, id: String) = File(dir(ctx), "$id.json")

    /** Hilos de un agente (o todos si [agente] es null), del más reciente al más viejo. */
    fun listar(ctx: Context, agente: String? = null): List<Hilo> =
        dir(ctx).listFiles()?.filter { it.extension == "json" }
            ?.mapNotNull { leerArchivo(it) }
            ?.filter { agente == null || it.agente == agente }
            ?.sortedByDescending { it.actualizado }
            ?: emptyList()

    fun cargar(ctx: Context, id: String): Hilo? = leerArchivo(archivo(ctx, id))

    private fun leerArchivo(f: File): Hilo? = runCatching {
        val o = JSONObject(f.readText())
        val arr = o.optJSONArray("mensajes") ?: JSONArray()
        Hilo(
            id = o.optString("id", f.nameWithoutExtension),
            agente = o.optString("agente"),
            titulo = o.optString("titulo"),
            actualizado = o.optLong("actualizado", f.lastModified()),
            mensajes = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                Mensaje(it.optBoolean("mio"), it.optString("texto"), it.optLong("ms"))
            }.toMutableList(),
        )
    }.getOrNull()

    fun guardar(ctx: Context, hilo: Hilo) {
        // Un hilo sin mensajes no se escribe, pero TAMPOCO borra nada: que
        // guardar pueda borrar convierte cualquier recomposición en pérdida de
        // datos. Borrar es sólo cosa de borrar().
        if (hilo.mensajes.isEmpty()) return
        // El título es la primera cosa que preguntaste, recortada. Es lo que
        // permite reconocer la conversación en la lista sin abrirla.
        if (hilo.titulo.isBlank()) {
            hilo.titulo = hilo.mensajes.firstOrNull { it.mio }?.texto
                ?.take(48)?.replace('\n', ' ').orEmpty().ifBlank { "Sin título" }
        }
        val o = JSONObject().apply {
            put("id", hilo.id)
            put("agente", hilo.agente)
            put("titulo", hilo.titulo)
            put("actualizado", hilo.actualizado)
            put("mensajes", JSONArray().apply {
                hilo.mensajes.forEach {
                    put(JSONObject().apply {
                        put("mio", it.mio); put("texto", it.texto); put("ms", it.ms)
                    })
                }
            })
        }
        runCatching { archivo(ctx, hilo.id).writeText(o.toString()) }
    }

    fun borrar(ctx: Context, id: String) {
        archivo(ctx, id).delete()
    }

    /** Cuántas conversaciones tiene cada agente. Para pintarlo en la lista. */
    fun conteos(ctx: Context): Map<String, Int> =
        listar(ctx).groupingBy { it.agente }.eachCount()

    fun nuevo(agente: String, reloj: Long): Hilo =
        Hilo(id = "c$reloj", agente = agente, titulo = "", actualizado = reloj)
}
