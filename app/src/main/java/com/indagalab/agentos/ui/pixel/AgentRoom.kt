package com.indagalab.agentos.ui.pixel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt

/**
 * Qué clase de sala es. La mayoría del edificio son oficinas; el resto son las
 * plantas a las que bajan los agentes cuando no tienen tarea.
 */
enum class Sala { OFICINA, DESCANSO, PISCINA, ASEOS }

/**
 * A qué se dedica un agente que no tiene tarea.
 *
 * No es decoración: sin esto todos aparecían sentados en la misma silla en la
 * sala de descanso, que es justo lo que hace que una escena parezca rota.
 * Cada actividad trae su postura y su sitio en la sala.
 */
private enum class Ocupacion(val celda: Offset) {
    ARCADE_IZQ(Offset(0.2f, 0.2f)),
    ARCADE_DER(Offset(1.3f, -0.4f)),
    TELE(Offset(2.7f, 0.5f)),
    SOFA(Offset(4.0f, 0.2f)),          // exactamente sobre el sofá
    NEVERA(Offset(4.5f, 1.7f)),
    MESA(Offset(-0.2f, 2.1f)),
    BAILA_IZQ(Offset(1.7f, 1.1f)),
    BAILA_DER(Offset(2.6f, 1.8f)),
    AGUA_A(Offset(1.5f, 0.5f)),
    AGUA_B(Offset(3.4f, 0.6f)),
    AGUA_C(Offset(2.4f, 1.7f)),
    TUMBONA_IZQ(Offset(-0.6f, 1.5f)),  // exactamente sobre la tumbona
    TUMBONA_DER(Offset(5.2f, 1.8f)),
    SOMBRA(Offset(0.1f, -0.2f)),
    CABINA_A(Offset(0.1f, -0.1f)),
    CABINA_B(Offset(1.6f, -0.7f)),
    ESPEJO_A(Offset(3.7f, -0.4f)),
    ESPEJO_B(Offset(4.9f, 0.4f)),
    COLA(Offset(2.1f, 1.3f)),
}

/** El reparto de cada sala. Se recorre en orden, así que dos agentes seguidos
 *  nunca acaban haciendo lo mismo en el mismo sitio. */
private val REPARTO = mapOf(
    Sala.DESCANSO to listOf(
        Ocupacion.ARCADE_IZQ, Ocupacion.BAILA_IZQ, Ocupacion.SOFA,
        Ocupacion.ARCADE_DER, Ocupacion.BAILA_DER, Ocupacion.MESA,
    ),
    Sala.PISCINA to listOf(
        Ocupacion.AGUA_A, Ocupacion.TUMBONA_IZQ, Ocupacion.AGUA_B,
        Ocupacion.TUMBONA_DER, Ocupacion.AGUA_C, Ocupacion.SOMBRA,
    ),
    Sala.ASEOS to listOf(
        Ocupacion.CABINA_A, Ocupacion.ESPEJO_A, Ocupacion.COLA,
        Ocupacion.CABINA_B, Ocupacion.ESPEJO_B, Ocupacion.COLA,
    ),
)

/** Qué está haciendo un agente. Se refleja en su sprite y en su burbuja. */
enum class Estado { TRABAJANDO, DURMIENDO, MUERTO, PENSANDO }

/** Un skill representado en la sala. */
data class Agente(
    val nombre: String,
    val estado: Estado,
    val comandos: Int = 0,
)

/** Estado interno de animación de cada personaje (posición, paseo, burbuja). */
private class Actor(
    val agente: Agente,
    val casa: Offset,
    val skin: SkinAgente? = null,
    val ocupacion: Ocupacion? = null,
) {
    val sprites = Personaje(
        camiseta = Skins.camisetaDe(skin, agente.nombre),
        piel = Skins.pielDe(skin),
        pelo = Skins.peloDe(skin),
    )
    val etiqueta: String = skin?.nombreVisible() ?: agente.nombre
    val oficio: Oficio = skin?.oficioReal() ?: oficioPorNombre(agente.nombre)
    var pos = casa                 // celda actual (float para interpolar)
    var destino: Offset? = null    // a dónde camina, null = está en su sitio
    var esperaMs = (agente.nombre.hashCode() % 4000).toLong().let { if (it < 0) -it else it }
    /** Desfase propio para que no todos animen en el mismo fotograma. */
    val fase = (agente.nombre.hashCode() % 1000).toLong().let { if (it < 0) -it else it }
    var burbujaHastaMs = 0L
}

/**
 * La sala de agentes.
 *
 * Un personaje por skill. Trabajan sentados, se levantan a estirar las piernas
 * de vez en cuando, duermen si el agente está parado y se les cae la silla si
 * el skill falló al cargar.
 *
 * [mostrarFps] pinta el contador de frames — sirve para medir en el dispositivo
 * y se apaga en producción.
 */
@Composable
fun AgentRoom(
    agentes: List<Agente>,
    proveedor: String?,
    modifier: Modifier = Modifier,
    mostrarFps: Boolean = false,
    skins: Map<String, SkinAgente> = emptyMap(),
    estado: String? = null,
    activo: Boolean = false,
    piso: Int = 0,
    sala: Sala = Sala.OFICINA,
    totales: Int = 0,
    titulo: String = "",
    reciénLlegados: Set<String> = emptySet(),
) {
    val medidor = rememberTextMeasurer()

    // Colocación: 2 columnas, creciendo hacia el fondo. Las celdas van muy
    // separadas (×2) para que los escritorios no se pisen entre sí.
    val columnas = if (agentes.size <= 4) 2 else 3
    val reparto = REPARTO[sala]
    val actores = remember(agentes, skins, sala) {
        agentes.mapIndexed { i, a ->
            val sitio = reparto?.getOrNull(i % reparto.size)
            val casa = sitio?.celda ?: Offset((i % columnas) * 2f, (i / columnas) * 2.4f)
            Actor(a, casa, skins[a.nombre], sitio).also { actor ->
                // Llega por la esquina de la sala y va andando a su puesto: sin
                // esto aparecía sentado de golpe y no se entendía que acababa
                // de subir del descanso.
                if (a.nombre in reciénLlegados && sitio == null) {
                    actor.pos = Offset(casa.x - 2.2f, casa.y - 2.2f)
                    actor.destino = casa
                }
            }
        }
    }
    val filas = (agentes.size + columnas - 1) / columnas

    // Reloj de animación. Se para cuando la pantalla no está visible: una
    // animación continua compitiendo con el servicio 24/7 se come la batería.
    var ahoraMs by remember { mutableLongStateOf(0L) }
    var fps by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> visible = true
                Lifecycle.Event.ON_PAUSE -> visible = false
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(visible, actores) {
        if (!visible) return@LaunchedEffect
        var ultimo = 0L
        var frames = 0
        var acumulado = 0L
        while (true) {
            withFrameMillis { t ->
                val dt = if (ultimo == 0L) 16L else (t - ultimo)
                ultimo = t
                ahoraMs += dt
                avanzar(actores, dt, ahoraMs)

                frames++
                acumulado += dt
                if (acumulado >= 1000L) {
                    fps = frames
                    frames = 0
                    acumulado = 0L
                }
            }
        }
    }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            dibujarSala(actores, columnas, filas, proveedor, ahoraMs, medidor, estado, activo,
                sala, if (totales > 0) totales else actores.size, piso, titulo)
            if (mostrarFps) {
                drawText(
                    medidor,
                    "$fps fps · ${actores.size} agentes",
                    topLeft = Offset(12f, 12f),
                    style = TextStyle(color = Color(0xFF7FD4E8), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/** Física del paseo: esperar sentado, levantarse, caminar, volver. */
private fun avanzar(actores: List<Actor>, dtMs: Long, ahoraMs: Long) {
    for (a in actores) {
        if (a.agente.estado != Estado.TRABAJANDO || a.ocupacion != null) continue

        val destino = a.destino
        if (destino == null) {
            a.esperaMs -= dtMs
            if (a.esperaMs <= 0) {
                // Paseo corto y determinista: nadie necesita pathfinding para
                // levantarse a estirar las piernas.
                val dx = if ((ahoraMs / 1000L + a.agente.nombre.length) % 2L == 0L) 1.1f else -1.1f
                a.destino = Offset(a.casa.x + dx, a.casa.y + 0.6f)
                a.burbujaHastaMs = ahoraMs + 2500L
            }
        } else {
            val v = 0.0016f * dtMs           // celdas por ms
            val d = destino - a.pos
            val dist = kotlin.math.hypot(d.x, d.y)
            if (dist < 0.05f) {
                a.pos = destino
                a.destino = if (destino == a.casa) null else a.casa
                if (a.destino == null) a.esperaMs = 4000L + (a.agente.nombre.hashCode() % 5000).let {
                    (if (it < 0) -it else it).toLong()
                }
            } else {
                a.pos += Offset(d.x / dist * v, d.y / dist * v)
            }
        }
    }
}

private val LETREROS by lazy {
    mapOf(
        Sala.OFICINA to textoPixel("AGENTOS", ACENTO),
        Sala.DESCANSO to textoPixel("DESCANSO", ACENTO),
        Sala.PISCINA to textoPixel("PISCINA", ACENTO),
        Sala.ASEOS to textoPixel("ASEOS", ACENTO),
    )
}

private fun DrawScope.dibujarSala(
    actores: List<Actor>,
    columnas: Int,
    filas: Int,
    proveedor: String?,
    ahoraMs: Long,
    medidor: TextMeasurer,
    estado: String?,
    activo: Boolean,
    sala: Sala,
    totales: Int,
    piso: Int,
    titulo: String,
) {
    // TODO proporcional al lienzo: con valores fijos en px, en una pantalla de
    // 480 dpi la escena salía diminuta. El tile se calcula para que la rejilla
    // ocupe el ancho disponible, y la escala del sprite se deriva del tile.
    val celdasX = (columnas - 1) * 2 + 1      // ancho real de la rejilla usada
    val celdasY = ((filas - 1) * 2.4f + 1).toInt()

    // Se reserva 1,5 celdas de margen a cada lado para el mobiliario (plantas,
    // rack, cafetera), que antes quedaba cortado por el borde del lienzo.
    val margen = 1.1f
    val anchoCeldas = (celdasX + celdasY) + margen * 4f    // diagonal total en iso

    // El tile se ajusta por ancho Y por alto. Antes sólo miraba el ancho: en
    // una card baja funcionaba, pero a pantalla completa la escena quedaba
    // pequeña y descolgada hacia abajo.
    // Arriba se reserva una banda fija para el letrero. La sala se calcula y se
    // centra SÓLO en lo que queda: así el título nunca se le monta encima y el
    // hueco negro de abajo se lo come la propia escena al crecer.
    val banda = size.height * 0.17f
    val pie = size.height * 0.14f
    val altoUtil = size.height - banda - pie

    val porAncho = (size.width * 1.06f) / (anchoCeldas / 2f)
    val porAlto = (altoUtil * 0.98f) / (anchoCeldas / 4f + 1.7f)
    val tileW = minOf(porAncho, porAlto)
    val tileH = tileW / 2f
    val escala = (tileW / 24f).roundToInt().coerceIn(2, 18)

    // Centrado real: se calcula el rectángulo que ocupa la escena y se centra
    // ese rectángulo, en vez de apuntar a un porcentaje fijo de la altura.
    val cx = (celdasX - 1) / 2f
    val cy = (celdasY - 1) / 2f
    val altoSprite = 26f * escala
    // El centro visual de la escena no es el centro del suelo: los sprites se
    // levantan sobre él. Se compensa bajando el origen un cuarto de sprite.
    val origen = Offset(
        x = size.width / 2f - (cx - cy) * (tileW / 2f),
        y = banda + altoUtil * 0.56f - (cx + cy) * (tileH / 2f) + altoSprite * 0.25f,
    )

    // Fondo: degradado cálido en vez de negro plano, para que la sala tenga aire.
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to PARED,
            0.55f to ZOCALO,
            1f to PARED,
        )
    )

    // Paredes. Un rombo isométrico mide la mitad de alto que de ancho, así que
    // en una pantalla 2:1 SIEMPRE sobra hueco arriba por mucho que se escale.
    // La solución no es agrandar el suelo: es que la sala tenga paredes.
    run {
        val vArriba = isoToScreen(-1.5f, -1.5f, tileW, tileH, origen)
        val vDer = isoToScreen(celdasX + 0.5f, -1.5f, tileW, tileH, origen)
        val vIzq = isoToScreen(-1.5f, celdasY + 0.5f, tileW, tileH, origen)
        val alturaMuro = (vIzq.y - banda * 0.55f).coerceAtLeast(tileH * 3f)

        fun muro(a: Offset, b: Offset, color: Color) {
            drawPath(Path().apply {
                moveTo(a.x, a.y); lineTo(b.x, b.y)
                lineTo(b.x, b.y - alturaMuro); lineTo(a.x, a.y - alturaMuro); close()
            }, color)
        }

        // Dos tonos: la luz entra por la derecha. Sin esa diferencia las dos
        // paredes se leen como una sola mancha y se pierde la esquina.
        muro(vArriba, vIzq, Color(0xFF241C18))
        muro(vArriba, vDer, Color(0xFF2E241E))

        // Paneles sobre cada pared, siguiendo su inclinación.
        fun paneles(a: Offset, b: Offset, alto: Float, sube: Float, color: Color, n: Int) {
            for (i in 0 until n) {
                val t0 = 0.16f + i * (0.68f / n)
                val t1 = t0 + (0.68f / n) * 0.62f
                val p0 = Offset(a.x + (b.x - a.x) * t0, a.y + (b.y - a.y) * t0)
                val p1 = Offset(a.x + (b.x - a.x) * t1, a.y + (b.y - a.y) * t1)
                drawPath(Path().apply {
                    moveTo(p0.x, p0.y - sube); lineTo(p1.x, p1.y - sube)
                    lineTo(p1.x, p1.y - sube - alto); lineTo(p0.x, p0.y - sube - alto); close()
                }, color)
            }
        }
        // Ventanas a la derecha, estanterías a la izquierda.
        paneles(vArriba, vDer, tileH * 1.5f, tileH * 1.6f, Color(0xFF2C3A42), 3)
        paneles(vArriba, vIzq, tileH * 0.34f, tileH * 1.3f, Color(0xFF322721), 3)
        paneles(vArriba, vIzq, tileH * 0.34f, tileH * 2.1f, Color(0xFF322721), 3)

        // Rodapié: la línea donde muro y suelo se encuentran. Es lo que hace
        // que se lea como una habitación y no como un decorado recortado.
        val grosor = escala * 0.8f
        listOf(vIzq, vDer).forEach { v ->
            drawPath(Path().apply {
                moveTo(vArriba.x, vArriba.y); lineTo(v.x, v.y)
                lineTo(v.x, v.y - grosor); lineTo(vArriba.x, vArriba.y - grosor); close()
            }, ACENTO.copy(alpha = 0.30f))
        }
    }

    // Sombra bajo el suelo: cierra la escena por abajo y evita que el damero
    // termine en un corte seco contra el fondo.
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to Color.Transparent, 1f to Color(0x66000000),
            startY = size.height * 0.72f, endY = size.height,
        )
    )

    // Los huecos de arriba y abajo eran negro muerto y hacían parecer pequeña
    // la escena. Arriba va el nombre en píxel; abajo, una línea que cierra la
    // composición y ancla la sala en lugar de dejarla flotando.
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to PARED, 0.62f to PARED, 1f to Color.Transparent,
            startY = 0f, endY = banda * 1.15f,
        ),
        size = androidx.compose.ui.geometry.Size(size.width, banda * 1.15f),
    )

    LETREROS[sala]?.let { letrero ->
        val escLet = ((size.width * 0.44f) / letrero.w).toInt().coerceIn(2, 18)
        val lx = (size.width - letrero.w * escLet) / 2f
        val anchoLet = letrero.w * escLet
        val altoLet = letrero.h * escLet
        val ly = banda * 0.30f

        // Halo detrás, no encima: da cuerpo al título sin lavar el naranja.
        drawOval(
            color = ACENTO.copy(alpha = 0.07f),
            topLeft = Offset(size.width / 2f - anchoLet * 0.62f, ly - altoLet * 0.45f),
            size = androidx.compose.ui.geometry.Size(anchoLet * 1.24f, altoLet * 1.9f),
        )
        drawSprite(letrero, Offset(lx, ly), escLet)

        // Reglas a los lados del título: cierran el bloque y lo hacen cabecera.
        val yr = ly + altoLet / 2f
        val hueco = anchoLet / 2f + escLet * 4f
        listOf(
            size.width * 0.14f to size.width / 2f - hueco,
            size.width / 2f + hueco to size.width * 0.86f,
        ).forEach { (x0, x1) ->
            if (x1 > x0) drawRect(
                color = ACENTO.copy(alpha = 0.28f),
                topLeft = Offset(x0, yr - escLet * 0.5f),
                size = androidx.compose.ui.geometry.Size(x1 - x0, escLet.toFloat()),
            )
        }

    }

    // Suelo en damero, del tamaño justo de la rejilla ocupada (+1 de margen)
    for (gx in -1..celdasX) for (gy in -1..celdasY) {
        val c = isoToScreen(gx.toFloat(), gy.toFloat(), tileW, tileH, origen)
        val p = Path().apply {
            val d = tileDiamond(c, tileW, tileH)
            moveTo(d[0].x, d[0].y)
            d.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(p, if ((gx + gy) % 2 == 0) SUELO_A else SUELO_B)
    }

    // Pista de baile: los cuadros cambian de color en bucle. Es lo que hace
    // que dos muñecos moviendo los brazos se lean como "están bailando" y no
    // como "el dibujo se ha roto".
    if (sala == Sala.DESCANSO) {
        val luces = listOf(
            Color(0xFF8C3FA8), Color(0xFF3F6BA8), Color(0xFF3FA88C),
            Color(0xFFA8823F), Color(0xFFA83F5E),
        )
        for (gx in 1..3) for (gy in 1..2) {
            val c = isoToScreen(gx.toFloat(), gy.toFloat(), tileW, tileH, origen)
            val p = Path().apply {
                val d = tileDiamond(c, tileW, tileH)
                moveTo(d[0].x, d[0].y)
                d.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val i = ((gx * 2 + gy * 3 + (ahoraMs / 380L).toInt()) % luces.size)
            drawPath(p, luces[i].copy(alpha = 0.55f))
        }
        // La bola cuelga sobre el centro de la pista y da vueltas despacio.
        val centro = isoToScreen(2f, 1.5f, tileW, tileH, origen)
        val giro = kotlin.math.sin(ahoraMs / 500.0).toFloat() * escala * 1.5f
        drawSpriteFooted(
            SPRITE_BOLA,
            Offset(centro.x + giro, centro.y - tileH * 3.2f),
            escala,
        )
    }

    // El agua se pinta sobre el damero: un sprite de piscina no encajaría con
    // la rejilla isométrica, y así el bordillo queda a la vista.
    if (sala == Sala.PISCINA) {
        for (gx in 1..celdasX - 1) for (gy in 0..celdasY - 1) {
            val c = isoToScreen(gx.toFloat(), gy.toFloat(), tileW, tileH, origen)
            val p = Path().apply {
                val d = tileDiamond(c, tileW, tileH)
                moveTo(d[0].x, d[0].y)
                d.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val brilla = ((gx + gy + (ahoraMs / 900L).toInt()) % 3) == 0
            drawPath(p, if (brilla) Color(0xFF4E96B8) else Color(0xFF3E86A8))
        }
    }

    // Borde del suelo en el naranja de marca: enmarca la sala y ata la escena
    // a la identidad de la app en vez de dejarla flotando en negro.
    run {
        val esquinas = listOf(
            isoToScreen(-1.5f, -1.5f, tileW, tileH, origen),
            isoToScreen(celdasX + 0.5f, -1.5f, tileW, tileH, origen),
            isoToScreen(celdasX + 0.5f, celdasY + 0.5f, tileW, tileH, origen),
            isoToScreen(-1.5f, celdasY + 0.5f, tileW, tileH, origen),
        )
        val p = Path().apply {
            moveTo(esquinas[0].x, esquinas[0].y)
            esquinas.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(p, ACENTO.copy(alpha = 0.22f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = escala * 0.9f))
    }

    // Mobiliario en los bordes: da escala a la sala y la hace parecer un sitio
    // de trabajo, no una rejilla con muñecos.
    when (sala) {
        Sala.DESCANSO -> {
            drawSpriteFooted(SPRITE_ARCADE, isoToScreen(-0.5f, -0.5f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_ARCADE, isoToScreen(0.6f, -1.1f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_TELE, isoToScreen(celdasX - 1.4f, -1.2f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_SOFA, isoToScreen(celdasX - 1.0f, 0.2f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_NEVERA, isoToScreen(celdasX + 0.2f, celdasY - 1.0f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_MESA_OCIO, isoToScreen(-0.9f, celdasY - 1.4f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_PLANTA, isoToScreen(celdasX + 0.3f, 0.4f, tileW, tileH, origen), escala)
        }
        Sala.PISCINA -> {
            drawSpriteFooted(SPRITE_SOMBRILLA, isoToScreen(-0.7f, -0.6f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_TUMBONA, isoToScreen(-0.6f, celdasY - 1.5f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_TUMBONA, isoToScreen(celdasX + 0.2f, celdasY - 1.2f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_SOMBRILLA, isoToScreen(celdasX + 0.3f, 0.2f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_PLANTA, isoToScreen(celdasX - 1.6f, -1.2f, tileW, tileH, origen), escala)
        }
        Sala.ASEOS -> {
            drawSpriteFooted(SPRITE_CABINA, isoToScreen(-0.6f, -0.8f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_CABINA, isoToScreen(0.9f, -1.4f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_LAVABO, isoToScreen(celdasX - 1.2f, -1.2f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_LAVABO, isoToScreen(celdasX + 0.2f, -0.4f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_PLANTA, isoToScreen(-0.8f, celdasY - 1.3f, tileW, tileH, origen), escala)
        }
        Sala.OFICINA -> {
            // El mobiliario rota con la planta: seis oficinas idénticas
            // parecerían un error de dibujado, no un edificio.
            drawSpriteFooted(SPRITE_PLANTA, isoToScreen(-0.4f, -0.4f, tileW, tileH, origen), escala)
            drawSpriteFooted(SPRITE_CAFE, isoToScreen(celdasX + 0.1f, celdasY - 0.6f, tileW, tileH, origen), escala)
            when (piso % 3) {
                0 -> {
                    drawSpriteFooted(SPRITE_RACK, isoToScreen(-1.0f, celdasY - 1.2f, tileW, tileH, origen), escala)
                    drawSpriteFooted(SPRITE_PIZARRA, isoToScreen(celdasX - 1.6f, -1.2f, tileW, tileH, origen), escala)
                }
                1 -> {
                    drawSpriteFooted(SPRITE_ESTANTERIA, isoToScreen(-1.0f, celdasY - 1.2f, tileW, tileH, origen), escala)
                    drawSpriteFooted(SPRITE_RELOJ, isoToScreen(celdasX - 1.5f, -1.4f, tileW, tileH, origen), escala)
                }
                else -> {
                    drawSpriteFooted(SPRITE_MONITOR, isoToScreen(-1.0f, celdasY - 1.2f, tileW, tileH, origen), escala)
                    drawSpriteFooted(SPRITE_ESTANTERIA, isoToScreen(celdasX - 1.6f, -1.2f, tileW, tileH, origen), escala)
                }
            }
            drawSpriteFooted(SPRITE_PLANTA, isoToScreen(celdasX + 0.2f, 0.3f, tileW, tileH, origen), escala)
        }
    }

    // El robot de AgentOS preside la sala desde el fondo, flotando despacio.
    if (sala == Sala.OFICINA) run {
        val flote = kotlin.math.sin(ahoraMs / 700.0).toFloat() * escala * 0.9f
        val c = isoToScreen(cx, -2.2f, tileW, tileH, origen)
        // Halo cálido debajo: lo despega del fondo y trae el naranja de marca.
        drawOval(
            color = ACENTO.copy(alpha = 0.13f),
            topLeft = Offset(c.x - tileW * 0.30f, c.y - tileH * 0.18f),
            size = androidx.compose.ui.geometry.Size(tileW * 0.60f, tileH * 0.42f),
        )
        drawSpriteFooted(SPRITE_ROBOT, Offset(c.x, c.y + flote), escala)
    }

    // Monitor del proveedor, al fondo de la sala. Parpadea: está "vivo".
    if (proveedor != null) {
        val c = isoToScreen(cx + 2.2f, -1.6f, tileW, tileH, origen)
        drawSpriteFooted(SPRITE_MONITOR, c, escala)
        val parpadeo = ((ahoraMs / 600L) % 2L) == 0L
        val txt = proveedor.uppercase()
        val m = medidor.measure(txt, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold))
        drawText(
            medidor, txt,
            topLeft = Offset(c.x - m.size.width / 2f, c.y - SPRITE_MONITOR.h * escala - m.size.height - 6f),
            style = TextStyle(
                color = if (parpadeo) Color(0xFF7FD4E8) else Color(0xFF4E8C9B),
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
            ),
        )
    }

    // PASE 1 — escena, por profundidad (lo de delante se pinta después).
    // El puesto (silla + persona + portátil) es UN solo sprite: antes iban por
    // separado y el personaje tapaba su propia mesa.
    val ordenados = actores.sortedBy { depth(it.pos.x, it.pos.y) }
    for (a in ordenados) {
        val enCasa = a.destino == null && a.pos == a.casa
        val casaPx = isoToScreen(a.casa.x, a.casa.y, tileW, tileH, origen)
        val posPx = isoToScreen(a.pos.x, a.pos.y, tileW, tileH, origen)

        val alterna = ((ahoraMs + a.fase) / 340L) % 2L == 0L
        // Su sitio existe aunque él no esté: la silla vacía cuenta que anda
        // por otra planta, y eso es media gracia del edificio.
        val fuera = sala == Sala.OFICINA && a.agente.estado == Estado.DURMIENDO
        if (fuera) {
            drawSpriteFooted(SPRITE_ESCRITORIO, posPx, escala)
            continue
        }
        val sprite = when {
            a.agente.estado == Estado.MUERTO -> a.sprites.dead
            a.ocupacion != null -> when (a.ocupacion) {
                Ocupacion.ARCADE_IZQ, Ocupacion.ARCADE_DER, Ocupacion.TELE ->
                    if (alterna) a.sprites.juegaA else a.sprites.juegaB
                Ocupacion.SOFA, Ocupacion.TUMBONA_IZQ, Ocupacion.TUMBONA_DER ->
                    a.sprites.tumbado
                Ocupacion.BAILA_IZQ, Ocupacion.BAILA_DER ->
                    if (alterna) a.sprites.bailaA else a.sprites.bailaB
                Ocupacion.AGUA_A, Ocupacion.AGUA_B, Ocupacion.AGUA_C ->
                    if (alterna) a.sprites.nadaA else a.sprites.nadaB
                // En la cola del baño y frente al espejo se está de pie, y el
                // paseo sirve de animación: mueve las piernas.
                else -> if (((ahoraMs + a.fase) / 420L) % 2L == 0L) a.sprites.walkA else a.sprites.walkB
            }
            a.agente.estado == Estado.DURMIENDO -> a.sprites.sleep
            !enCasa -> if (((ahoraMs + a.fase) / 160L) % 2L == 0L) a.sprites.walkA else a.sprites.walkB
            else -> if (alterna) a.sprites.workA else a.sprites.workB
        }

        // Sombra elíptica en el suelo: ancla al personaje y quita la sensación
        // de que flota sobre el damero.
        val enAgua = a.ocupacion in listOf(Ocupacion.AGUA_A, Ocupacion.AGUA_B, Ocupacion.AGUA_C)
        if (!enAgua) {
            drawOval(
                color = Color(0x33000000),
                topLeft = Offset(posPx.x - tileW * 0.22f, posPx.y - tileH * 0.10f),
                size = androidx.compose.ui.geometry.Size(tileW * 0.44f, tileH * 0.28f),
            )
        }
        // El flotador va debajo del nadador: sin él, una cabeza asomando del
        // agua parece alguien ahogándose, no alguien pasándolo bien.
        if (enAgua) {
            val escFlot = (escala * 1.1f).roundToInt().coerceAtLeast(2)
            drawSprite(
                SPRITE_FLOTADOR,
                Offset(
                    posPx.x - SPRITE_FLOTADOR.w * escFlot / 2f,
                    posPx.y - SPRITE_FLOTADOR.h * escFlot * 0.62f,
                ),
                escFlot,
            )
        }
        val tumbado = a.ocupacion in listOf(
            Ocupacion.SOFA, Ocupacion.TUMBONA_IZQ, Ocupacion.TUMBONA_DER)
        drawSpriteFooted(
            sprite,
            // Un tumbado se apoya en el asiento, que está por encima del suelo.
            if (tumbado) Offset(posPx.x, posPx.y - tileH * 0.30f) else posPx,
            escala,
        )

        // El objeto de su oficio, apoyado en la mesa a su derecha: es lo que
        // distingue de un vistazo al de las listas del que lleva el teléfono.
        if (a.agente.estado != Estado.MUERTO && a.ocupacion == null) {
            a.oficio.sprite?.let { prop ->
                // Apoyado en el canto de la mesa y a su derecha. Antes iba
                // sobre la pantalla del portátil y parecía pegado a la cara.
                val escProp = (escala * 0.7f).roundToInt().coerceAtLeast(2)
                drawSpriteFooted(
                    prop,
                    Offset(posPx.x + tileW * 0.40f, posPx.y + tileH * 0.06f),
                    escProp,
                )
            }
        }

    }

    // Primer plano, después de los personajes para que los tape lo que está
    // delante de ellos. Da profundidad al borde inferior, que era damero pelado.
    drawSpriteFooted(SPRITE_PLANTA,
        isoToScreen(-0.6f, celdasY + 0.6f, tileW, tileH, origen), escala + 1)
    drawSpriteFooted(SPRITE_CAFE,
        isoToScreen(celdasX + 0.1f, celdasY + 0.5f, tileW, tileH, origen), escala + 1)

    // PASE 2 — burbujas, todas al final. Si se pintan dentro del pase 1, el
    // escritorio del vecino de delante las tapa y parecen de otro personaje.
    val estiloNombre = TextStyle(color = Color(0xFFD8CABA), fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold)
    for (a in ordenados) {
        val casaPx = isoToScreen(a.casa.x, a.casa.y, tileW, tileH, origen)
        val m = medidor.measure(a.etiqueta, estiloNombre)

        // Los estados permanentes (caído, dormido) van DENTRO de la etiqueta.
        // Como globo flotante estaban siempre en pantalla y siempre acababan
        // encima del nombre del vecino de delante; aquí no pueden solaparse
        // porque son el mismo elemento.
        val insignia = when {
            a.agente.estado == Estado.MUERTO -> ICONO_ERROR
            // En las salas de ocio ya se ve lo que hace cada uno; el zZz sólo
            // añadiría ruido encima de la escena.
            a.ocupacion != null -> null
            a.agente.estado == Estado.DURMIENDO -> ICONO_ZZZ
            else -> null
        }
        val escIns = (escala * 0.55f).roundToInt().coerceAtLeast(2)
        val anchoIns = insignia?.let { it.w * escIns + escala * 1.4f } ?: 0f

        val padX = escala * 2.2f
        val padY = escala * 1.2f
        val ancho = m.size.width + anchoIns
        val etqX = casaPx.x - ancho / 2f
        val etqY = casaPx.y + tileH * 0.16f
        drawRoundRect(
            color = PARED.copy(alpha = 0.96f),
            topLeft = Offset(etqX - padX, etqY - padY),
            size = androidx.compose.ui.geometry.Size(
                ancho + padX * 2f, m.size.height + padY * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(escala * 2f),
        )
        insignia?.let {
            // Los iconos están dibujados en oscuro porque nacieron para ir
            // dentro del globo blanco. Sobre el chip necesitan su fondo claro.
            val ix = etqX
            val iy = etqY + (m.size.height - it.h * escIns) / 2f
            drawRoundRect(
                color = Color(0xFFF6EFE6),
                topLeft = Offset(ix - escIns, iy - escIns),
                size = androidx.compose.ui.geometry.Size(
                    it.w * escIns + escIns * 2f, it.h * escIns + escIns * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(escIns * 1.5f),
            )
            drawSprite(it, Offset(ix, iy), escIns)
        }
        drawText(medidor, a.etiqueta, topLeft = Offset(etqX + anchoIns, etqY),
            style = estiloNombre.copy(
                color = if (a.agente.estado == Estado.MUERTO) Color(0xFF9A8880)
                        else Color(0xFFD8CABA)))

        // El globo queda sólo para lo momentáneo: pensar o levantarse. Son
        // pocos a la vez y duran segundos, así que ya no estorban.
        val enCasa = a.destino == null && a.pos == a.casa
        val icono = when {
            a.agente.estado == Estado.MUERTO || a.agente.estado == Estado.DURMIENDO -> null
            !enCasa -> ICONO_CHAT
            a.agente.estado == Estado.PENSANDO -> ICONO_IDEA
            ahoraMs < a.burbujaHastaMs -> ICONO_IDEA
            else -> null
        } ?: continue

        val posPx = isoToScreen(a.pos.x, a.pos.y, tileW, tileH, origen)
        val escBurbuja = (escala * 0.5f).roundToInt().coerceAtLeast(2)
        dibujarBurbuja(posPx.x, posPx.y - 31f * escala, icono, escBurbuja)
    }
    // Pie: estado y recuento. Iba pegado bajo el título y quedaba apretado
    // contra el letrero; aquí separa la escena del botón y llena el hueco.
    run {
        val caidos = actores.count { it.agente.estado == Estado.MUERTO }
        val sub = buildString {
            append(estado ?: "sin configurar")
            append("   ·   ")
            append(totales).append(if (totales == 1) " agente" else " agentes")
            append("   ·   ")
            append(titulo.ifBlank { "planta ${piso + 1}" })
            if (caidos > 0) append("   ·   ").append(caidos)
                .append(if (caidos == 1) " caído" else " caídos")
        }.uppercase()
        val estiloSub = TextStyle(
            color = if (activo) Color(0xFF9BC48A) else Color(0xFF9A8878),
            fontSize = 11.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.Medium,
        )
        val m = medidor.measure(sub, estiloSub)
        val r = escala * 1.5f
        val ancho = m.size.width + r * 3f
        val y = size.height - pie * 0.52f - m.size.height / 2f
        val x0 = (size.width - ancho) / 2f

        // Regla partida, igual que la del título: cierra la composición abajo.
        listOf(
            size.width * 0.08f to x0 - escala * 3f,
            x0 + ancho + escala * 3f to size.width * 0.92f,
        ).forEach { (a, b) ->
            if (b > a) drawRect(
                color = ACENTO.copy(alpha = 0.20f),
                topLeft = Offset(a, y + m.size.height / 2f - escala * 0.35f),
                size = androidx.compose.ui.geometry.Size(b - a, escala * 0.7f),
            )
        }
        drawCircle(
            color = if (activo) Color(0xFF6FBF4A) else Color(0xFF7A6656),
            radius = r / 2f,
            center = Offset(x0 + r / 2f, y + m.size.height / 2f),
        )
        drawText(medidor, sub, topLeft = Offset(x0 + r * 2f, y), style = estiloSub)
    }
}

/** Globo blanco con su cola y el icono dentro. */
private fun DrawScope.dibujarBurbuja(x: Float, y: Float, icono: PixelSprite, escala: Int) {
    val w = (icono.w + 6) * escala.toFloat()
    val h = (icono.h + 6) * escala.toFloat()
    val left = x - w / 2f
    val top = y - h

    drawRoundRect(
        color = Color(0xFFF6EFE6),
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * escala),
    )
    // cola
    val cola = Path().apply {
        moveTo(x - 3f * escala, top + h)
        lineTo(x + 3f * escala, top + h)
        lineTo(x, top + h + 4f * escala)
        close()
    }
    drawPath(cola, Color(0xFFF6EFE6))

    drawSprite(icono, Offset(x - icono.w * escala / 2f, top + 3f * escala), escala)
}

/** Traduce el estado real del sistema a lo que se ve en la sala. */
fun agentesDesde(
    skills: List<Pair<String, String>>,   // (nombre, status del loader)
    agenteCorriendo: Boolean,
): List<Agente> = skills.map { (nombre, status) ->
    Agente(
        nombre = nombre,
        estado = when {
            status == "error" -> Estado.MUERTO
            !agenteCorriendo -> Estado.DURMIENDO
            status == "skipped" -> Estado.DURMIENDO
            else -> Estado.TRABAJANDO
        },
    )
}
