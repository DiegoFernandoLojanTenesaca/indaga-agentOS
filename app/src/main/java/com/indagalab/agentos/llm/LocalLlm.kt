package com.indagalab.agentos.llm

import android.content.Context
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Modelo de lenguaje corriendo dentro del teléfono (llama.cpp / GGUF).
 *
 * NO es un chat. Es una **función**: entra un prompt corto, sale un texto
 * corto, y no se guarda historial. Esa restricción no es pereza, está medida
 * en el P40 Lite (Kirin 810) con Qwen3 0.6B Q4:
 *
 *     prompt corto, sin historial ......   730 ms al primer token · 17,6 tok/s
 *     el mismo con 3 turnos de contexto  19.494 ms al primer token · 13,1 tok/s
 *
 * El TTFT es tiempo de *prefill*: reprocesa todo el contexto en cada llamada.
 * Con historial se multiplica por 26 y deja de servir. Por eso cada llamada
 * nace y muere: se fija el system prompt, se manda el user prompt y se olvida.
 *
 * El modelo se busca en `filesDir/models/`, cualquier fichero .gguf. (Ojo: no
 * escribir la ruta con comodín aquí — Kotlin anida los comentarios de bloque y
 * un `/` seguido de `*` dentro del KDoc se come el resto del archivo.)
 * Nunca se descarga sin que alguien lo pida: medio giga por la red móvil de
 * otro no se gasta a sus espaldas. Lo trae [Catalogo], desde la pantalla
 * Modelo local.
 */
object LocalLlm {

    private const val TAG = "LocalLlm"

    @Volatile private var motor: InferenceEngine? = null
    @Volatile private var rutaCargada: String? = null

    /** Carpeta donde buscar los .gguf. */
    fun modelsDir(ctx: Context): File = File(ctx.filesDir, "models").apply { mkdirs() }

    /** Preferencia del usuario cuando hay varios modelos instalados. */
    private fun marcador(ctx: Context) = File(modelsDir(ctx), ".elegido")

    /** El .gguf elegido, o el primero que haya, o null. */
    fun modeloDisponible(ctx: Context): File? {
        val todos = modelsDir(ctx).listFiles()
            ?.filter { it.extension.equals("gguf", true) }?.sortedBy { it.name }.orEmpty()
        val elegido = runCatching { marcador(ctx).readText().trim() }.getOrNull()
        return todos.firstOrNull { it.name == elegido } ?: todos.firstOrNull()
    }

    /** Fija qué modelo usar. Descarga el anterior para no dejarlo en RAM. */
    fun elegir(ctx: Context, nombre: String) {
        runCatching { marcador(ctx).writeText(nombre) }
        if (rutaCargada != null && File(rutaCargada!!).name != nombre) descargar()
    }

    /** Ruta del modelo cargado ahora mismo, o null. */
    fun rutaActual(): String? = rutaCargada

    fun estaListo(): Boolean = rutaCargada != null

    /**
     * Carga el modelo. Tarda unos segundos y bloquea: llamar fuera del hilo
     * principal (el bridge ya sirve cada petición en su propio hilo).
     */
    @Synchronized
    fun cargar(ctx: Context): Boolean {
        val f = modeloDisponible(ctx) ?: run {
            Log.w(TAG, "no hay ningún .gguf en ${modelsDir(ctx)}")
            return false
        }
        if (rutaCargada == f.absolutePath) return true

        return try {
            val e = motor ?: AiChat.getInferenceEngine(ctx).also { motor = it }
            runBlocking {
                // El motor carga la librería nativa en una corrutina de su
                // `init`, así que al volver de getInferenceEngine() el estado
                // todavía es Uninitialized. Llamar a loadModel() aquí revienta
                // con "Cannot load model in Initializing!". Hay que esperar.
                val listo = withTimeoutOrNull(20_000) {
                    e.state.first {
                        it is InferenceEngine.State.Initialized ||
                            it is InferenceEngine.State.ModelReady ||
                            it is InferenceEngine.State.Error
                    }
                }
                when {
                    listo == null ->
                        throw IllegalStateException("la librería nativa no arrancó en 20 s")
                    listo is InferenceEngine.State.Error ->
                        throw IllegalStateException("motor en error: ${listo.exception.message}")
                    else -> e.loadModel(f.absolutePath)
                }
            }
            rutaCargada = f.absolutePath
            Log.i(TAG, "modelo cargado: ${f.name} (${f.length() / 1024 / 1024} MB)")
            true
        } catch (t: Throwable) {
            // Un modelo corrupto o una arquitectura no soportada NO deben
            // tumbar el agente: se responde que no hay local y se tira de nube.
            Log.e(TAG, "no se pudo cargar ${f.name}: ${t.message}")
            rutaCargada = null
            false
        }
    }

    /**
     * Genera una respuesta. Devuelve null si el modelo no está disponible, para
     * que quien llame haga fallback a la nube sin tener que distinguir errores.
     *
     * @param system regla de decisión explícita: un 0.6B no tiene criterio
     *        propio y sin ella clasifica distinto la misma entrada dos veces.
     * @param maxTokens corto a propósito; la salida larga es lo que hace lenta
     *        la respuesta, no el prompt.
     */
    fun generar(ctx: Context, system: String, user: String, maxTokens: Int = 32): String? {
        if (!cargar(ctx)) return null
        val e = motor ?: return null
        return try {
            runBlocking {
                e.setSystemPrompt(system)
                e.sendUserPrompt(user, maxTokens).toList().joinToString("")
            }.trim()
        } catch (t: Throwable) {
            Log.e(TAG, "fallo generando: ${t.message}")
            null
        }
    }

    /**
     * Igual que [generar] pero devolviendo los tokens según salen.
     *
     * Para el chat es otra experiencia: con 17 tok/s, esperar a la respuesta
     * entera son 5-10 s mirando una pantalla quieta; viendo salir las palabras
     * se lee mientras genera.
     *
     * Devuelve null si no hay modelo (para caer a la nube sin distinguir errores).
     */
    fun generarFlow(
        ctx: Context,
        system: String,
        user: String,
        maxTokens: Int = 220,
    ): Flow<String>? {
        if (!cargar(ctx)) return null
        val e = motor ?: return null
        return flow {
            e.setSystemPrompt(system)
            emitAll(e.sendUserPrompt(user, maxTokens))
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Benchmark del motor: procesa [pp] tokens de prompt y genera [tg],
     * repetido [nr] veces. Devuelve la tabla que da llama.cpp, o null si el
     * modelo no carga. Bloquea varios segundos.
     */
    @Synchronized
    fun bench(ctx: Context, pp: Int = 64, tg: Int = 32, pl: Int = 1, nr: Int = 1): String? {
        if (!cargar(ctx)) return null
        val e = motor ?: return null
        return try {
            runBlocking { e.bench(pp, tg, pl, nr) }
        } catch (t: Throwable) {
            Log.e(TAG, "bench: ${t.message}")
            null
        }
    }

    /** Lo que tarda de verdad: carga, primer token y velocidad de salida. */
    data class Medida(
        val texto: String,
        val cargaMs: Long,
        val primerTokenMs: Long,
        val totalMs: Long,
        val tokens: Int,
    ) {
        val tokensPorSegundo: Float
            get() = if (totalMs > primerTokenMs && tokens > 1)
                (tokens - 1) * 1000f / (totalMs - primerTokenMs) else 0f
    }

    /**
     * Genera midiendo. Separa el tiempo de carga del de generación: la primera
     * respuesta siempre parece lentísima porque incluye subir 700 MB a RAM, y
     * mezclarlo con la velocidad real del modelo no dice nada.
     */
    fun medir(ctx: Context, system: String, user: String, maxTokens: Int = 48): Medida? {
        val t0 = System.currentTimeMillis()
        if (!cargar(ctx)) return null
        val cargaMs = System.currentTimeMillis() - t0
        val e = motor ?: return null
        return try {
            runBlocking {
                e.setSystemPrompt(system)
                val inicio = System.currentTimeMillis()
                var primero = 0L
                var trozos = 0
                val sb = StringBuilder()
                e.sendUserPrompt(user, maxTokens).collect { t ->
                    if (primero == 0L) primero = System.currentTimeMillis() - inicio
                    trozos++
                    sb.append(t)
                }
                Medida(sb.toString().trim(), cargaMs, primero,
                    System.currentTimeMillis() - inicio, trozos)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "medir: ${t.message}")
            null
        }
    }

    /** Libera la RAM del modelo (medio giga). Al parar el agente, conviene. */
    @Synchronized
    fun descargar() {
        try { motor?.cleanUp() } catch (_: Throwable) {}
        rutaCargada = null
    }

    /** Estado para la UI y para el endpoint de diagnóstico. */
    fun info(ctx: Context): String {
        val f = modeloDisponible(ctx)
        return when {
            f == null -> """{"available":false,"reason":"no hay .gguf en models/"}"""
            rutaCargada != null -> """{"available":true,"loaded":true,"model":"${f.name}"}"""
            else -> """{"available":true,"loaded":false,"model":"${f.name}"}"""
        }
    }
}
