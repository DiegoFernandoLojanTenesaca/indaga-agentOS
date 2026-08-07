package com.indagalab.agentos.ui.pixel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import android.graphics.Bitmap

/**
 * Motor mínimo de pixel art para Compose Canvas.
 *
 * Dos decisiones que sostienen todo lo demás:
 *
 * 1. **Los sprites se escriben como texto.** Un carácter = un color de la paleta.
 *    Se editan a mano sin herramientas y se leen en el diff.
 *
 * 2. **Cada sprite se convierte a ImageBitmap UNA vez y se cachea.** Dibujar
 *    32×32 con drawRect son 1024 llamadas por sprite y por frame; con 8
 *    personajes a 8 fps eso es 65.000 llamadas por segundo y el Kirin 810 no
 *    está para eso. Un drawImage escalado es una sola operación.
 *
 * `FilterQuality.None` es lo que mantiene el píxel duro: sin ella, Android
 * interpola al escalar y el pixel art se ve borroso.
 */
class PixelSprite(
    val w: Int,
    val h: Int,
    private val argb: IntArray,
) {
    /** Bitmap cacheado. Se crea la primera vez que se dibuja. */
    val image: ImageBitmap by lazy {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(argb, 0, w, 0, 0, w, h)
        bmp.asImageBitmap()
    }

    companion object {
        /**
         * Construye un sprite desde texto. Cada carácter mira a [palette];
         * lo que no esté en el mapa (por convención, '.') queda transparente.
         *
         *     val sprite = PixelSprite.of("""
         *         ..XX..
         *         .XXXX.
         *     """, mapOf('X' to Color.Red))
         */
        fun of(art: String, palette: Map<Char, Color>): PixelSprite {
            val filas = art.trimIndent().lines().filter { it.isNotBlank() }
            val h = filas.size
            val w = filas.maxOf { it.length }
            val px = IntArray(w * h)  // 0 = transparente
            for (y in 0 until h) {
                val fila = filas[y]
                for (x in fila.indices) {
                    palette[fila[x]]?.let { px[y * w + x] = it.toArgbInt() }
                }
            }
            return PixelSprite(w, h, px)
        }

        private fun Color.toArgbInt(): Int {
            val a = (alpha * 255f + 0.5f).toInt() shl 24
            val r = (red * 255f + 0.5f).toInt() shl 16
            val g = (green * 255f + 0.5f).toInt() shl 8
            val b = (blue * 255f + 0.5f).toInt()
            return a or r or g or b
        }
    }
}

/** Dibuja el sprite con su esquina superior izquierda en [pos], escalado ×[scale]. */
fun DrawScope.drawSprite(sprite: PixelSprite, pos: Offset, scale: Int) {
    drawImage(
        image = sprite.image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(sprite.w, sprite.h),
        dstOffset = IntOffset(pos.x.toInt(), pos.y.toInt()),
        dstSize = IntSize(sprite.w * scale, sprite.h * scale),
        filterQuality = FilterQuality.None,   // sin esto se ve borroso
    )
}

/** Dibuja centrado horizontalmente y apoyado por su base en [pos] (los pies). */
fun DrawScope.drawSpriteFooted(sprite: PixelSprite, pos: Offset, scale: Int) {
    drawSprite(
        sprite,
        Offset(pos.x - sprite.w * scale / 2f, pos.y - sprite.h * scale),
        scale,
    )
}

/**
 * Proyección isométrica 2:1, la clásica de los juegos de rol.
 *
 * El suelo es una rejilla de celdas [tileW]×[tileH] en píxeles de pantalla.
 * Devuelve el CENTRO de la celda (gx, gy), que es donde se planta un personaje.
 */
fun isoToScreen(gx: Float, gy: Float, tileW: Float, tileH: Float, origin: Offset): Offset =
    Offset(
        x = origin.x + (gx - gy) * (tileW / 2f),
        y = origin.y + (gx + gy) * (tileH / 2f),
    )

/**
 * Clave de profundidad: en isométrica, lo que tiene mayor (x+y) va delante.
 * Ordenar por esto antes de pintar hace que las oclusiones salgan solas, sin
 * z-buffer ni nada parecido.
 */
fun depth(gx: Float, gy: Float): Float = gx + gy

/** Rombo del suelo de una celda, para pintar tiles sin sprite. */
fun tileDiamond(center: Offset, tileW: Float, tileH: Float): List<Offset> = listOf(
    Offset(center.x, center.y - tileH / 2f),
    Offset(center.x + tileW / 2f, center.y),
    Offset(center.x, center.y + tileH / 2f),
    Offset(center.x - tileW / 2f, center.y),
)
