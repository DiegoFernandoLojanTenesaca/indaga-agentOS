# AgentOS — "La sala de agentes" (UI pixel art isométrica)

> Propuesta de rediseño. **Nada implementado aún.** Fecha: 2026-08-06
> Referencia visual: el pixel art isométrico de Relevance AI (Agent OS).

---

## 1. La idea

La pantalla principal deja de ser una tarjeta con un robot y un texto, y pasa a ser
una **oficina isométrica en pixel art donde cada elemento del sistema es un personaje
o un objeto que se ve trabajar**.

No es decoración: **la escena ES el dashboard**. Lo que hoy se lee en texto ("agente
detenido", "7 comandos", "proveedor groq") se mira de un vistazo.

```
        ┌─ monitor grande al fondo = proveedor LLM activo
        │   (su color, su nombre, parpadea al responder)
        │
   ▓▓▓▓▓▓▓▓▓▓▓▓
   ▓  ░▄░  ░▄░ ▓   ← cada escritorio con personaje = un SKILL cargado
   ▓  ▐█▌  ▐█▌ ▓      teclea si está activo, duerme si está apagado
   ▓▓▓▓▓▓▓▓▓▓▓▓
     ╱╱╱╱╱╱╱╱      ← suelo isométrico (tiles)
```

**La paleta se queda**: naranja Indaga, dorado, crema, piedra. Nada de púrpura —
la referencia es de otra marca; copiamos la técnica, no la identidad.

## 2. Qué representa cada cosa

Esto es lo que hace que valga la pena, y todo sale de datos que **ya existen**:

| En la escena | Dato real | De dónde sale |
|---|---|---|
| Un personaje en su escritorio | un skill cargado | `registry.specs()` agrupado por `source` |
| Personaje tecleando / dormido | agente corriendo o detenido | `AgentState` |
| Silla vacía | skill desactivado por `requires-env` | `loader.LOADED` (status `skipped`) |
| Escritorio en llamas 🔥 | skill que falló al cargar | `loader.LOADED` (status `error`) |
| Monitor del fondo | proveedor LLM activo | `STATE["provider"]` |
| Parpadeo del monitor | llamada al LLM en curso | evento del bridge |
| Lámpara encendida | servicio en primer plano vivo | `AgentService` |
| Personaje con gorra de vigilante | antirrobo armado | `ANTIROBO["on"]` |
| Cajas apiladas en un rincón | mensajes en cola / modo silencio | tabla `inbox_ausente` |
| Termómetro en la pared | batería y temperatura | bridge `/battery` |

**Consecuencia de diseño:** cuando instales un skill nuevo, **aparece un personaje
nuevo en la sala**. Esa es la recompensa visual de que el sistema sea injertable.

## 3. Cómo se dibuja (técnica)

- **Compose Canvas**, sin motor de juego ni dependencias nuevas.
- Sprites en baja resolución (32×32 o 48×48) escalados ×4/×6 con
  `FilterQuality.None`, que es lo que mantiene el píxel duro sin desenfoque.
- Proyección isométrica 2:1 clásica: `screenX = (x - y) * w/2`, `screenY = (x + y) * h/2`.
  Orden de pintado por `x + y` para que la profundidad salga sola.
- Animación por frames a 4-8 fps (el pixel art no necesita 60): un
  `LaunchedEffect` con un contador y un `withFrameMillis` para el tecleo, el
  parpadeo del monitor y el vapor del café.
- **Todo estático salvo lo que cambia.** Se redibuja solo la capa de personajes,
  el suelo va en una capa cacheada.

## 4. ⚠️ El problema real: de dónde salen los sprites

Aquí está el cuello de botella, y conviene decirlo claro: **yo no puedo dibujar
sprites con la calidad de la referencia**. Genero código, no arte. Tres caminos:

**A. Sprites por código (matrices de color).** Cada sprite es una matriz de índices
de paleta escrita en Kotlin. Cero assets, cero peso en el APK, y permite generar
variaciones automáticas (cada skill hereda un color de camiseta según su nombre).
→ Se ve **coherente y limpio, pero más simple** que la referencia: personajes de
32×32 con 6-8 colores, no las sombras y el detalle de la imagen que mandaste.
→ Lo puedo hacer entero yo.

**B. Assets CC0 de terceros** (itch.io, OpenGameArt, Kenney). Calidad profesional
inmediata, licencia libre. → Hay que elegir el pack, y los personajes no se
adaptan solos a "un skill = un personaje"; habría que recolorear por código.

**C. Sprites generados aparte** (tú los generas con la herramienta que quieras y
me los pasas como PNG). Máxima calidad y control de identidad. → Depende de ti.

**Recomendación honesta:** empezar por **A** para tener la sala viva y funcionando
en poco tiempo, con el motor preparado para cargar PNG. Si luego llegan sprites de
B o C, se sustituyen sin tocar la lógica: el motor dibuja "un sprite", le da igual
de dónde venga.

## ✅ Estado (2026-08-06): F1 y F2 hechos

Archivos: `ui/pixel/PixelArt.kt` (motor), `ui/pixel/Sprites.kt` (arte),
`ui/pixel/AgentRoom.kt` (escena). Sustituye al `AgentHero` en la pantalla Inicio.

**Rendimiento medido en el P40 (Kirin 810): 60 fps con 6 agentes animados.**
El motor va sobrado; no hace falta bajar a 8 fps de momento.

**Tres errores cometidos y corregidos, por si vuelven a aparecer:**

1. **Píxeles absolutos en pantalla de 480 dpi.** Con `tileW=64` y `escala=3`
   fijos, la escena salía diminuta en una esquina. Ahora el tile se calcula
   desde el ancho del lienzo y la escala del sprite se deriva del tile.
2. **Personaje y escritorio como sprites separados**: el muñeco tapaba su
   propia mesa. Ahora el puesto (silla + persona + portátil) es **un solo
   sprite de 22×26**.
3. **Contorno negro alrededor de cada parte** del cuerpo: los volvía manchas.
   Ahora el contorno es solo la silueta exterior y cada color lleva su tono de
   sombra (`Color.sombra()`).

Además: los nombres y las burbujas se pintan en un **segundo pase** sobre toda
la escena — dentro del pase de profundidad, el escritorio del vecino de delante
los tapaba y la burbuja parecía de otro personaje.

## 5. Fases

**F1 — Motor (sin arte).** `PixelCanvas`: dibujar una matriz escalada sin
interpolación, proyección isométrica, orden de profundidad, tiles de suelo.
Verificación: una sala vacía de 4×4 tiles a 60 fps en el P40.

**F2 — Primer personaje.** Un sprite por código, sentado, con 2 frames de tecleo.
Verificación: se ve nítido a ×4 y la animación no come batería.

**F3 — La sala real.** Escritorios generados según `registry.specs()`, layout
automático en rejilla, colores derivados del nombre del skill.
Verificación: instalar un skill → aparece un personaje sin tocar la UI.

**F4 — Estados vivos.** Corriendo/detenido, skill roto, proveedor en el monitor,
antirrobo. Verificación: parar el agente y ver a la gente dormirse.

**F5 — El resto de la app.** Con la identidad ya definida, rehacer Config,
Sistema y Logs en la misma línea (marcos tipo ventana pixel, iconos 16×16).

## 6. Riesgos

- **Rendimiento sin medir.** Kirin 810 + Mali-G52, y ya sabemos que esa GPU no
  acelera llama.cpp. Compose Canvas sí usa aceleración normal, pero **hay que
  medirlo en F1 antes de invertir en F2-F5**, no al final.
- **Batería.** Una animación continua en la pantalla principal compite con el
  servicio 24/7. Regla: animar **solo cuando la pantalla está visible**, y parar
  el bucle en `onPause`.
- **Escala.** Con 20 skills la sala se llena. Habrá que decidir: scroll,
  varias salas o agrupar por categoría. Se resuelve en F3, no antes.
- **Accesibilidad.** Una escena dibujada no la lee un lector de pantalla. Hay que
  dejar el texto equivalente en `contentDescription` y mantener una vista lista
  como alternativa.
- **Es mucho más trabajo que lo hecho hasta ahora.** F1-F4 no es una tarde.

## 7. Lo que NO haría

- Motor de juego (libGDX, Godot) para dibujar 20 sprites: Canvas sobra.
- Animación a 60 fps: el pixel art se ve mejor a 6-8 fps y gasta la décima parte.
- Tirar la UI actual antes de que la nueva funcione: la sala entra **solo en la
  pantalla Inicio** (F1-F4) y el resto sigue igual hasta F5.
- Copiar la paleta púrpura de la referencia: es la identidad de otra marca.
