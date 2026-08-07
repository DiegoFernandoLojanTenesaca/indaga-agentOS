package com.indagalab.agentos.ui

import android.content.Context
import android.net.Uri
import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lo que sabe y usa cada agente.
 *
 * El skin dice qué cara tiene; la ficha dice de qué sabe: sus instrucciones,
 * qué skills lleva, de qué tablas del agente saca datos y qué documentos le
 * has dado. Es lo que convierte "un modelo con un nombre encima" en un agente
 * que responde de lo suyo.
 *
 * Un JSON por agente en `filesDir/agentes/<id>.json`, y sus documentos en
 * `filesDir/agentes/<id>/`.
 */
object Fichas {

    /** Un documento adjunto, ya convertido a texto. */
    data class Documento(
        val nombre: String,
        val palabras: Int,
        val texto: String,
    )

    data class Ficha(
        val id: String,
        var instrucciones: String = "",
        val skills: MutableList<String> = mutableListOf(),
        val fuentes: MutableList<String> = mutableListOf(),
        val documentos: MutableList<Documento> = mutableListOf(),
    )

    /** Tablas del agente que se pueden enchufar a cualquiera. */
    val FUENTES = listOf("notas", "listas", "agenda", "diario")

    /**
     * Fichas de fábrica de los agentes del núcleo.
     *
     * Vienen con instrucciones y fuentes puestas para que se vea cómo se
     * configura un agente: quien cree uno nuevo tiene seis ejemplos delante en
     * vez de una pantalla en blanco. Se pueden editar como cualquier otra.
     */
    private val PREDETERMINADAS: Map<String, Ficha> = mapOf(
        "notas" to Ficha(
            "notas",
            "Cuando te pregunten por lo apuntado, responde con lo que hay en las " +
                "notas y no te inventes ninguna. Si no hay nada, dilo.",
            fuentes = mutableListOf("notas", "diario"),
        ),
        "listas" to Ficha(
            "listas",
            "Responde con los elementos de las listas tal cual están. Si te piden " +
                "añadir algo, recuérdales que eso se hace por Telegram.",
            fuentes = mutableListOf("listas"),
        ),
        "agenda" to Ficha(
            "agenda",
            "Di qué hay pendiente y cuándo. Lo más cercano primero.",
            fuentes = mutableListOf("agenda"),
        ),
        "telefono" to Ficha(
            "telefono",
            "Te llegan SMS y avisos. Di en una frase si algo es urgente y por qué. " +
                "Un código de verificación es urgente; una promoción no.",
        ),
        "web" to Ficha(
            "web",
            "Resume en tres frases como mucho y no añadas nada que no esté en el texto.",
        ),
        "antirrobo" to Ficha(
            "antirrobo",
            "Vigilas el teléfono. Sé breve y directo: qué ha pasado y qué conviene hacer.",
        ),
    )

    private fun dir(ctx: Context) = File(ctx.filesDir, "agentes").apply { mkdirs() }

    private fun archivo(ctx: Context, id: String) = File(dir(ctx), "$id.json")

    fun cargar(ctx: Context, id: String): Ficha {
        val f = archivo(ctx, id)
        // Sin archivo propio manda la de fábrica; en cuanto guardas, la tuya.
        if (!f.isFile) return PREDETERMINADAS[id]?.copiar() ?: Ficha(id)
        return runCatching {
            val o = JSONObject(f.readText())
            Ficha(
                id = id,
                instrucciones = o.optString("instrucciones"),
                skills = o.optJSONArray("skills").aLista().toMutableList(),
                fuentes = o.optJSONArray("fuentes").aLista().toMutableList(),
                documentos = o.optJSONArray("documentos").let { arr ->
                    (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.map {
                        Documento(
                            it.optString("nombre"),
                            it.optInt("palabras"),
                            it.optString("texto"),
                        )
                    }.toMutableList()
                },
            )
        }.getOrDefault(Ficha(id))
    }

    private fun JSONArray?.aLista(): List<String> =
        (0 until (this?.length() ?: 0)).map { this!!.getString(it) }

    fun guardar(ctx: Context, ficha: Ficha) {
        val o = JSONObject().apply {
            put("instrucciones", ficha.instrucciones)
            put("skills", JSONArray(ficha.skills))
            put("fuentes", JSONArray(ficha.fuentes))
            put("documentos", JSONArray().apply {
                ficha.documentos.forEach {
                    put(JSONObject().apply {
                        put("nombre", it.nombre)
                        put("palabras", it.palabras)
                        put("texto", it.texto)
                    })
                }
            })
        }
        runCatching { archivo(ctx, ficha.id).writeText(o.toString()) }
    }

    /**
     * Añade un documento leyéndolo con Python (pypdf para PDF, texto plano
     * para el resto). Se guarda ya extraído: volver a abrir un PDF de 5 MB
     * cada vez que escribes un mensaje sería absurdo.
     */
    fun adjuntar(ctx: Context, id: String, uri: Uri, nombre: String): Result<Ficha> =
        runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("no se pudo leer el archivo")
            // Un documento entero no cabe en el contexto de un modelo pequeño.
            // Se guarda recortado y se avisa de cuánto se quedó fuera.
            val temporal = File(dir(ctx), "tmp-$nombre")
            temporal.writeBytes(bytes)
            val texto = try {
                Python.getInstance().getModule("jarvis")
                    .callAttr("leer_documento", temporal.absolutePath).toString()
            } finally {
                temporal.delete()
            }
            if (texto.isBlank()) error("no se pudo sacar texto de $nombre")

            val ficha = cargar(ctx, id)
            ficha.documentos.removeAll { it.nombre == nombre }
            ficha.documentos.add(
                Documento(nombre, texto.split(Regex("\\s+")).size, texto.take(4000))
            )
            guardar(ctx, ficha)
            ficha
        }

    fun quitarDocumento(ctx: Context, id: String, nombre: String): Ficha {
        val ficha = cargar(ctx, id)
        ficha.documentos.removeAll { it.nombre == nombre }
        guardar(ctx, ficha)
        return ficha
    }

    /**
     * El bloque de contexto que se le pasa al modelo: instrucciones propias,
     * datos de sus tablas y un extracto de sus documentos.
     *
     * [presupuesto] limita el total. En un modelo de 1B cada carácter extra se
     * paga en segundos de prefill, así que es mejor un resumen corto que un
     * volcado que haga la respuesta inusable.
     */
    fun contexto(ctx: Context, id: String, presupuesto: Int = 1800): String {
        val ficha = cargar(ctx, id)
        val partes = mutableListOf<String>()

        if (ficha.instrucciones.isNotBlank()) partes += ficha.instrucciones.trim()

        ficha.fuentes.forEach { fuente ->
            val datos = runCatching {
                Python.getInstance().getModule("jarvis")
                    .callAttr("contexto_agente", fuente, ctx.filesDir.absolutePath).toString()
            }.getOrDefault("")
            if (datos.isNotBlank()) partes += datos
        }

        ficha.documentos.forEach { d ->
            partes += "De «${d.nombre}»:\n${d.texto}"
        }

        // Se recorta al final, no por trozo: así las instrucciones (lo primero
        // y lo que más manda) nunca se pierden por culpa de un PDF largo.
        return partes.joinToString("\n\n").take(presupuesto)
    }

    /** Copia con listas propias: las de fábrica no se pueden mutar. */
    private fun Ficha.copiar() = Ficha(
        id = id,
        instrucciones = instrucciones,
        skills = skills.toMutableList(),
        fuentes = fuentes.toMutableList(),
        documentos = documentos.toMutableList(),
    )

    /** Resumen para la pantalla: "2 skills · notas · 1 documento". */
    fun resumen(ctx: Context, id: String): String {
        val f = cargar(ctx, id)
        val trozos = mutableListOf<String>()
        if (f.skills.isNotEmpty()) trozos += "${f.skills.size} skills"
        if (f.fuentes.isNotEmpty()) trozos += f.fuentes.joinToString(", ")
        if (f.documentos.isNotEmpty()) {
            trozos += if (f.documentos.size == 1) "1 documento"
            else "${f.documentos.size} documentos"
        }
        return trozos.joinToString(" · ")
    }
}
