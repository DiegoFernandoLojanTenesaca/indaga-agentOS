package com.indagalab.agentos.llm

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Modelos GGUF: buscarlos en Hugging Face y traérselos al teléfono.
 *
 * El catálogo no está escrito aquí: se le pregunta a Hugging Face, que es
 * quien sabe qué hay hoy y cuánto pesa. Una lista fija de modelos envejece
 * igual de mal que una lista fija de identificadores.
 *
 * Sin dependencias nuevas: HttpURLConnection y org.json ya vienen en Android.
 */
object Catalogo {

    private const val TAG = "Catalogo"
    private const val API = "https://huggingface.co/api/models"

    /** Un repositorio con modelos GGUF. */
    data class Repo(
        val id: String,
        val descargas: Int,
        val likes: Int,
    ) {
        val autor: String get() = id.substringBefore('/', "")
        val nombre: String get() = id.substringAfter('/')
    }

    /** Un fichero .gguf concreto dentro de un repo. */
    data class Archivo(
        val repo: String,
        val nombre: String,
        val bytes: Long,
    ) {
        val url: String get() = "https://huggingface.co/$repo/resolve/main/$nombre"

        /** q4_k_m, q8_0, f16… lo que diga el nombre del fichero. */
        val cuantizacion: String
            get() = Regex("(?i)(iq?\\d[_a-z0-9]*|q\\d[_a-z0-9]*|f16|bf16|f32)")
                .find(nombre.substringBeforeLast('.'))?.value?.uppercase().orEmpty()
    }

    private fun leer(url: String, timeout: Int = 20_000): String? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = timeout
            readTimeout = timeout
            setRequestProperty("User-Agent", "AgentOS")
            inputStream.bufferedReader().use { it.readText() }
        }
    } catch (e: Exception) {
        Log.w(TAG, "GET $url: $e")
        null
    }

    /**
     * Repos con GGUF, los más descargados primero.
     *
     * Con [busca] vacío devuelve el ranking general, que sirve de
     * "recomendados" sin que nadie tenga que mantener la lista a mano.
     */
    fun buscar(busca: String = "", limite: Int = 25): List<Repo> {
        val q = if (busca.isBlank()) "" else
            "&search=" + URLEncoder.encode(busca.trim(), "UTF-8")
        val txt = leer("$API?filter=gguf&sort=downloads&direction=-1&limit=$limite$q")
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(txt)
            (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                Repo(it.optString("id"), it.optInt("downloads"), it.optInt("likes"))
            }.filter { it.id.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    /**
     * Los .gguf de un repo, con su peso. Un repo suele traer el mismo modelo
     * en varias cuantizaciones y en un teléfono la diferencia entre una y otra
     * es que quepa o no en RAM, así que el tamaño va delante.
     */
    fun archivos(repo: String): List<Archivo> {
        val txt = leer("$API/$repo/tree/main") ?: return emptyList()
        return runCatching {
            val arr = JSONArray(txt)
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { it.optString("path").endsWith(".gguf", true) }
                .map { Archivo(repo, it.optString("path"), it.optLong("size")) }
                .sortedBy { it.bytes }
        }.getOrDefault(emptyList())
    }

    /** Los .gguf que ya están en el teléfono. */
    fun instalados(ctx: Context): List<File> =
        LocalLlm.modelsDir(ctx).listFiles()
            ?.filter { it.extension.equals("gguf", true) }
            ?.sortedBy { it.name } ?: emptyList()

    /**
     * Descarga con progreso. Escribe a `.parte` y renombra al final: así una
     * descarga cortada no deja un .gguf roto que el motor intente cargar.
     *
     * [avance] recibe (bytes recibidos, bytes totales); total es -1 si el
     * servidor no lo dice.
     */
    fun descargar(
        ctx: Context,
        url: String,
        nombre: String,
        cancelado: () -> Boolean = { false },
        avance: (Long, Long) -> Unit,
    ): Result<File> = runCatching {
        val destino = File(LocalLlm.modelsDir(ctx), nombre)
        val parcial = File(destino.parentFile, "$nombre.parte")
        parcial.delete()

        var conexion = URL(url).openConnection() as HttpURLConnection
        conexion.instanceFollowRedirects = true
        conexion.connectTimeout = 30_000
        conexion.readTimeout = 60_000
        conexion.setRequestProperty("User-Agent", "AgentOS")

        // Hugging Face responde con un redirect a su CDN, y de https a https
        // el redirect automático no siempre se sigue.
        var saltos = 0
        while (conexion.responseCode in 300..399 && saltos++ < 5) {
            val siguiente = conexion.getHeaderField("Location") ?: break
            conexion.disconnect()
            conexion = (URL(URL(url), siguiente).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "AgentOS")
            }
        }
        if (conexion.responseCode !in 200..299) {
            error("el servidor respondió ${conexion.responseCode}")
        }

        val total = conexion.contentLengthLong
        var recibido = 0L
        conexion.inputStream.use { entrada ->
            parcial.outputStream().use { salida ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    if (cancelado()) {
                        parcial.delete()
                        error("cancelada")
                    }
                    val n = entrada.read(buffer)
                    if (n < 0) break
                    salida.write(buffer, 0, n)
                    recibido += n
                    avance(recibido, total)
                }
            }
        }
        conexion.disconnect()

        // Un GGUF empieza por "GGUF". Si lo que llegó es una página de error
        // en HTML, mejor enterarse aquí que al cargar el modelo.
        val cabecera = parcial.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
        if (String(cabecera) != "GGUF") {
            parcial.delete()
            error("el archivo descargado no es un GGUF")
        }

        destino.delete()
        check(parcial.renameTo(destino)) { "no se pudo guardar el modelo" }
        destino
    }

    /** "770 MB", "1,4 GB". */
    fun peso(bytes: Long): String = when {
        bytes <= 0 -> "—"
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        else -> "%d MB".format(bytes / 1_048_576L)
    }
}
