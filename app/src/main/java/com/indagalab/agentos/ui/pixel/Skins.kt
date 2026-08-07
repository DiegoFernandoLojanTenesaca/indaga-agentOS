package com.indagalab.agentos.ui.pixel

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.File

/**
 * Aspecto y datos de cada personaje de la sala.
 *
 * Los skills traen su nombre técnico ("telefono", "antirrobo") y un color
 * derivado del hash. Esto permite ponerles encima un alias, un rol y una
 * apariencia elegidos a mano, sin tocar el skill: la personalización es del
 * usuario, el skill es del programa.
 *
 * Se guarda en `filesDir/skins.json`. Un JSON plano de una decena de entradas
 * no necesita base de datos.
 */
data class SkinAgente(
    val id: String,               // nombre del skill: la clave
    val alias: String = "",       // cómo se le llama en pantalla
    val rol: String = "",         // "Cámara y GPS", "Vigilante"…
    val camiseta: Int = -1,       // índice en CAMISETAS_DISPONIBLES; -1 = automático
    val piel: Int = 0,
    val pelo: Int = 0,
    val oficio: String = "",      // nombre del enum Oficio; vacío = automático
    val propio: Boolean = false,  // creado por el usuario, no viene de un skill
) {
    fun nombreVisible(): String = alias.ifBlank { id }

    /** Objeto que sostiene. Si no se eligió, se deduce del nombre del skill. */
    fun oficioReal(): Oficio =
        runCatching { Oficio.valueOf(oficio) }.getOrNull() ?: oficioPorNombre(id)
}

/** Paletas elegibles. Todas dentro de la familia cálida de Indaga. */
val CAMISETAS_DISPONIBLES = listOf(
    Color(0xFFE07A3F), Color(0xFFD4AF6A), Color(0xFFA8763E), Color(0xFF8C9A5B),
    Color(0xFF4E8C7D), Color(0xFFC1553F), Color(0xFF6E7B8C), Color(0xFFB98A5E),
    Color(0xFF9A5BA8), Color(0xFF5B7FA8), Color(0xFF3E8C5A), Color(0xFFB03A3A),
)

val PIELES_DISPONIBLES = listOf(
    Color(0xFFF2CBA5), Color(0xFFE8B48C), Color(0xFFC98F66),
    Color(0xFF9C6644), Color(0xFF6F4529), Color(0xFF4A2E1C),
)

val PELOS_DISPONIBLES = listOf(
    Color(0xFF3A2A1E), Color(0xFF14110F), Color(0xFF7A5230),
    Color(0xFFC8A24E), Color(0xFFB03A3A), Color(0xFF7F8C99),
)

/**
 * Cómo viene cada agente del núcleo de fábrica.
 *
 * Prellenar rol, objeto y colores no es decoración: es el ejemplo de cómo se
 * configura un agente. Quien cree uno nuevo ve seis ya montados y sabe qué
 * poner en cada campo. En cuanto guardas, manda lo tuyo.
 */
val PREDETERMINADOS: Map<String, SkinAgente> = mapOf(
    "telefono" to SkinAgente("telefono", "Teléfono", "Llamadas, SMS y códigos",
        camiseta = 6, piel = 1, pelo = 0, oficio = "TELEFONO"),
    "agenda" to SkinAgente("agenda", "Agenda", "Recordatorios y citas",
        camiseta = 4, piel = 2, pelo = 2, oficio = "CALENDARIO"),
    "notas" to SkinAgente("notas", "Notas", "Apuntes y diario",
        camiseta = 5, piel = 0, pelo = 1, oficio = "LIBRETA"),
    "listas" to SkinAgente("listas", "Listas", "Compras y tareas",
        camiseta = 0, piel = 3, pelo = 3, oficio = "CARPETA"),
    "web" to SkinAgente("web", "Web", "Busca y resume en internet",
        camiseta = 9, piel = 1, pelo = 0, oficio = "MUNDO"),
    "antirrobo" to SkinAgente("antirrobo", "Antirrobo", "Vigila el teléfono",
        camiseta = 11, piel = 4, pelo = 1, oficio = "ESCUDO"),
)

/** Almacén de personalizaciones. */
object Skins {

    private const val ARCHIVO = "skins.json"

    private fun f(ctx: Context) = File(ctx.filesDir, ARCHIVO)

    /** Lo guardado por el usuario encima de lo que viene de fábrica. */
    fun cargar(ctx: Context): Map<String, SkinAgente> =
        PREDETERMINADOS + guardados(ctx)

    private fun guardados(ctx: Context): Map<String, SkinAgente> = try {
        val file = f(ctx)
        if (!file.isFile) emptyMap()
        else {
            val o = JSONObject(file.readText())
            o.keys().asSequence().mapNotNull { k ->
                val e = o.optJSONObject(k) ?: return@mapNotNull null
                k to SkinAgente(
                    id = k,
                    alias = e.optString("alias"),
                    rol = e.optString("rol"),
                    camiseta = e.optInt("camiseta", -1),
                    piel = e.optInt("piel", 0),
                    pelo = e.optInt("pelo", 0),
                    oficio = e.optString("oficio"),
                    propio = e.optBoolean("propio", false),
                )
            }.toMap()
        }
    } catch (_: Throwable) {
        emptyMap()   // un JSON corrupto no debe dejar la sala en blanco
    }

    fun guardar(ctx: Context, skins: Map<String, SkinAgente>) {
        val o = JSONObject()
        skins.forEach { (k, s) ->
            o.put(k, JSONObject().apply {
                put("alias", s.alias)
                put("rol", s.rol)
                put("camiseta", s.camiseta)
                put("piel", s.piel)
                put("pelo", s.pelo)
                put("oficio", s.oficio)
                put("propio", s.propio)
            })
        }
        runCatching { f(ctx).writeText(o.toString()) }
    }

    fun guardarUno(ctx: Context, skin: SkinAgente) {
        // Sobre lo guardado, no sobre la mezcla: si no, cada guardado copiaría
        // los predeterminados al archivo y dejarían de poder actualizarse.
        guardar(ctx, guardados(ctx).toMutableMap().apply { put(skin.id, skin) })
    }

    /** Agentes creados por el usuario, en el orden en que los creó. */
    fun propios(ctx: Context): List<SkinAgente> =
        guardados(ctx).values.filter { it.propio }.sortedBy { it.id }

    /**
     * Crea un agente nuevo. El id se deriva del nombre porque es también la
     * clave de sus conversaciones: si cambiara al renombrarlo, perdería su
     * historial.
     */
    fun crear(ctx: Context, nombre: String, reloj: Long): SkinAgente {
        val base = nombre.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "agente" }
        val usados = cargar(ctx).keys
        val id = if (base in usados) "$base-$reloj" else base
        val nuevo = SkinAgente(
            id = id,
            alias = nombre.trim(),
            camiseta = (usados.size * 5) % CAMISETAS_DISPONIBLES.size,
            propio = true,
        )
        guardarUno(ctx, nuevo)
        return nuevo
    }

    fun eliminar(ctx: Context, id: String) {
        guardar(ctx, guardados(ctx).toMutableMap().apply { remove(id) })
    }

    /** Color de camiseta final: el elegido, o el automático por nombre. */
    fun camisetaDe(skin: SkinAgente?, id: String): Color =
        skin?.camiseta?.takeIf { it in CAMISETAS_DISPONIBLES.indices }
            ?.let { CAMISETAS_DISPONIBLES[it] }
            ?: colorDeSkill(id)

    fun pielDe(skin: SkinAgente?): Color =
        PIELES_DISPONIBLES.getOrElse(skin?.piel ?: 1) { PIELES_DISPONIBLES[1] }

    fun peloDe(skin: SkinAgente?): Color =
        PELOS_DISPONIBLES.getOrElse(skin?.pelo ?: 0) { PELOS_DISPONIBLES[0] }
}
