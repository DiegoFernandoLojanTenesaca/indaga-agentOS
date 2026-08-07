package com.indagalab.agentos.pals

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de PalsHub (palshub.ai), el catálogo de personalidades de PocketPal.
 *
 * Un "Pal" es un system prompt + un modelo recomendado. Es decir: exactamente
 * un `SKILL.md` de los nuestros. Importar uno = escribir esa carpeta en
 * `filesDir/skills/`, que es donde el loader de Python ya busca los skills
 * instalados por el usuario. Sin recompilar nada.
 *
 * ## Reglas que este código respeta y no se tocan
 *
 * 1. **Sólo se importan los gratuitos** (`is_free` / `price_cents == 0`). Los de
 *    pago son el sustento de sus autores; además la API no entrega su
 *    `system_prompt` sin comprarlos, y aunque lo hiciera, no se toca.
 * 2. **Se conserva la autoría**: el creador y el enlace al Pal original quedan
 *    escritos en el SKILL.md importado.
 * 3. Es una API **no documentada**: puede cambiar o cerrarse sin aviso. Todo
 *    fallo se devuelve como lista vacía o null; nunca revienta la app.
 */
object PalsHub {

    private const val TAG = "PalsHub"
    private const val BASE = "https://palshub.ai/api"
    const val WEB = "https://palshub.ai"

    data class Pal(
        val id: String,
        val titulo: String,
        val descripcion: String,
        val autor: String,
        val categorias: List<String>,
        val gratis: Boolean,
        val miniatura: String?,
    )

    data class PalDetalle(
        val pal: Pal,
        val systemPrompt: String,
        val modeloRepo: String?,
        val modeloArchivo: String?,
        val modeloBytes: Long,
    )

    private fun get(url: String, timeoutMs: Int = 15000): String? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AgentOS/0.3 (IndagaLab)")
            if (responseCode in 200..299) inputStream.bufferedReader().readText()
            else { Log.w(TAG, "HTTP $responseCode en $url"); null }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "fallo pidiendo $url: ${t.message}")
        null
    }

    /** Catálogo, ya filtrado a los gratuitos. */
    fun listarGratis(limite: Int = 40): List<Pal> {
        val raw = get("$BASE/pals?limit=$limite") ?: return emptyList()
        return try {
            val arr = JSONObject(raw).optJSONArray("pals") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val gratis = o.optInt("price_cents", -1) == 0 || o.optBoolean("is_free", false)
                if (!gratis) return@mapNotNull null       // ← el filtro, aquí y en el detalle
                val cats = o.optJSONArray("categories")
                Pal(
                    id = o.optString("id"),
                    titulo = o.optString("title"),
                    descripcion = o.optString("description").trim(),
                    autor = o.optJSONObject("creator")?.optString("display_name").orEmpty(),
                    categorias = (0 until (cats?.length() ?: 0)).mapNotNull {
                        cats?.optJSONObject(it)?.optString("name")
                    },
                    gratis = true,
                    miniatura = o.optString("thumbnail_url").ifBlank { null },
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "JSON inesperado en el listado: ${t.message}")
            emptyList()
        }
    }

    /** Detalle con el system prompt. Devuelve null si es de pago o falla. */
    fun detalle(id: String): PalDetalle? {
        val raw = get("$BASE/pals/$id") ?: return null
        return try {
            val o = JSONObject(raw)
            val gratis = o.optBoolean("is_free", false) || o.optInt("price_cents", -1) == 0
            if (!gratis) {
                Log.i(TAG, "pal de pago, no se importa: $id")
                return null
            }
            val prompt = o.optString("system_prompt").trim()
            if (prompt.isBlank()) return null
            val mr = o.optJSONObject("model_reference")
            PalDetalle(
                pal = Pal(
                    id = id,
                    titulo = o.optString("title"),
                    descripcion = o.optString("description").trim(),
                    autor = o.optJSONObject("creator")?.optString("display_name").orEmpty(),
                    categorias = emptyList(),
                    gratis = true,
                    miniatura = o.optString("thumbnail_url").ifBlank { null },
                ),
                systemPrompt = prompt,
                modeloRepo = mr?.optString("repo_id")?.ifBlank { null },
                modeloArchivo = mr?.optString("filename")?.ifBlank { null },
                modeloBytes = mr?.optLong("size") ?: 0L,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "no se pudo leer el detalle de $id: ${t.message}")
            null
        }
    }

    /** Carpeta de skills del usuario. La misma que lee skills/loader.py. */
    fun skillsDir(ctx: Context): File = File(ctx.filesDir, "skills").apply { mkdirs() }

    private fun slug(s: String): String =
        s.lowercase()
            .replace(Regex("[áàä]"), "a").replace(Regex("[éèë]"), "e")
            .replace(Regex("[íìï]"), "i").replace(Regex("[óòö]"), "o")
            .replace(Regex("[úùü]"), "u").replace("ñ", "n")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .take(40).ifBlank { "pal" }

    /**
     * Escribe el Pal como skill. Devuelve la carpeta creada, o null si falla.
     *
     * El SKILL.md resultante lo lee tal cual `skills/loader.py`: mismo formato
     * de frontmatter que los skills propios, con `kind: pal` para distinguirlo.
     */
    fun importar(ctx: Context, d: PalDetalle): File? = try {
        val dir = File(skillsDir(ctx), slug(d.pal.titulo)).apply { mkdirs() }
        val yamlSafe = { s: String -> s.replace("\"", "'").replace("\n", " ").trim() }

        val md = buildString {
            appendLine("---")
            appendLine("name: ${slug(d.pal.titulo)}")
            appendLine("description: \"${yamlSafe(d.pal.descripcion).take(220)}\"")
            appendLine("kind: pal")
            appendLine("role: invitado")
            d.modeloRepo?.let { appendLine("model-repo: \"$it\"") }
            d.modeloArchivo?.let { appendLine("model-file: \"$it\"") }
            if (d.modeloBytes > 0) appendLine("model-size-mb: ${d.modeloBytes / 1024 / 1024}")
            appendLine("source: palshub")
            appendLine("source-id: ${d.pal.id}")
            appendLine("source-url: \"$WEB/pals/${d.pal.id}\"")
            appendLine("author: \"${yamlSafe(d.pal.autor)}\"")
            appendLine("license: gratuito en PalsHub")
            appendLine("---")
            appendLine()
            appendLine("# ${d.pal.titulo}")
            appendLine()
            appendLine("> Importado de PalsHub. Creado por **${d.pal.autor.ifBlank { "anónimo" }}**.")
            appendLine("> Original: $WEB/pals/${d.pal.id}")
            appendLine()
            if (d.pal.descripcion.isNotBlank()) {
                appendLine(d.pal.descripcion)
                appendLine()
            }
            appendLine("## System prompt")
            appendLine()
            appendLine(d.systemPrompt)
        }
        File(dir, "SKILL.md").writeText(md)
        Log.i(TAG, "importado '${d.pal.titulo}' en ${dir.absolutePath}")
        dir
    } catch (t: Throwable) {
        Log.e(TAG, "no se pudo importar: ${t.message}")
        null
    }

    /** Pals ya importados (carpetas con SKILL.md que declaran source: palshub). */
    fun importados(ctx: Context): Set<String> =
        skillsDir(ctx).listFiles()?.mapNotNull { d ->
            val f = File(d, "SKILL.md")
            if (!f.isFile) return@mapNotNull null
            Regex("source-id:\\s*(\\S+)").find(f.readText())?.groupValues?.get(1)
        }?.toSet() ?: emptySet()

    fun borrar(ctx: Context, id: String): Boolean =
        skillsDir(ctx).listFiles()?.firstOrNull { d ->
            File(d, "SKILL.md").let { it.isFile && it.readText().contains("source-id: $id") }
        }?.deleteRecursively() ?: false
}
