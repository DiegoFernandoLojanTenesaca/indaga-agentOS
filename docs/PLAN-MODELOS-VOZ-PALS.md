# AgentOS — Gestor de modelos, voz y Pals

> Propuesta. Fecha: 2026-08-06. Inspirado en PocketPal AI + PalsHub.
> Datos verificados en HuggingFace, no de memoria.

---

## 1. Qué es cada cosa (en español)

### Los "Pals" de PocketPal / PalsHub

Un **Pal** no es un modelo: es una **configuración de personalidad** que se
monta encima de un modelo. Lleva tres cosas:

1. Un **system prompt** que define quién es y cómo responde.
2. Un **modelo recomendado** con su cuantización (y aviso de RAM mínima).
3. **Parámetros** de generación: tamaño de contexto, temperatura, etc.

Ejemplos reales de PalsHub, para entender el patrón:

| Pal | Qué hace | Modelo que usa |
|---|---|---|
| Cyberdeck Companion | Copiloto de hardware/firmware: ESP32, Python, RF | Qwen2.5-Coder 3B + SmolVLM2 para ver fotos de protoboards |
| Lingua | Traduce y explica pronunciación y formalidad, 30+ idiomas | Tiny Aya Global (2,14 GB) |
| Draven | Copiloto Android/Termux/Linux, respuestas compactas | Qwen2.5-Coder 7B |
| SketchPal | Le pides "un pomodoro" y escupe HTML/CSS/JS que se ve en el chat | Gemma 4 E2B / Qwen3 1.7B |

Su modelo de negocio: los venden sueltos (2-3 €) o gratis, y se sincronizan
con la app. Categorías: Productividad, Educación, Escritura, Roleplay, Código,
Negocios, Salud, Entretenimiento.

> **Lo importante para nosotros: un Pal es exactamente un `SKILL.md`.**
> Ya tenemos el loader (Paso 3): carpeta + frontmatter + cuerpo markdown. Un
> Pal es un skill **sin `skill.py`**, sólo documentación — algo que el loader
> ya soporta y que hoy no usa nadie. Sólo hay que añadir al frontmatter qué
> modelo quiere y sus parámetros.

### Los modelos de voz (TTS)

| Modelo | Tamaño | Licencia | ¿Español? | Nota |
|---|---|---|---|---|
| **Kitten TTS nano** | 15M params, **<25 MB** | Apache 2.0 | ❓ sin confirmar | El más pequeño con diferencia. Corre en CPU sin GPU. |
| **Kokoro-82M** | 82M params | Apache 2.0 | ✅ **3 voces**: `ef_dora` (f), `em_alex` (m), `em_santa` (m) | 9 idiomas, 41 voces. El más equilibrado. |
| **Supertonic** | 99M params | OpenRAIL-M (modelo) / MIT (código) | ❓ 31 idiomas, español sin confirmar | Corre en ONNX Runtime. |

**Ojo con la licencia de Supertonic**: OpenRAIL-M **no es libre sin
condiciones** — trae restricciones de uso. Kokoro y Kitten son Apache 2.0,
que sí es comercial-safe. Para un producto, eso descarta a Supertonic salvo
que se lea la licencia con calma.

---

## 2. ¿Merece la pena la voz?

**AgentOS YA habla**, con el TTS nativo de Android: offline, sin Google, 0 MB
de APK y sin licencias. Lo usa `jarvis_core.speak()` a través del bridge.

Entonces, ¿para qué Kokoro?

| | TTS nativo Android | Kokoro-82M |
|---|---|---|
| Peso | 0 MB | ~90 MB (int8) a ~330 MB (fp32) |
| Calidad | robótica, la de fábrica | muy superior, natural |
| Español | sí | sí, 3 voces |
| Licencia | — | Apache 2.0 |
| Trabajo | ya hecho | ONNX Runtime + fonemizador + JNI |

**Veredicto honesto: la voz es la mejora de menor prioridad.** Ya funciona algo
que cumple, y sustituirlo cuesta ~90 MB y un runtime nuevo. Tiene sentido si la
voz pasa a ser un rasgo del producto (que los Pals tengan voces distintas), no
antes.

Para **STT** (escuchar) la cuenta es distinta: hoy depende de la API de Groq, o
sea internet + cuota. Ahí un whisper.cpp local sí quita una dependencia real.
Y `libllama` ya está compilado: whisper.cpp es del mismo autor y comparte ggml.

---

## 3. Lo que sí falta y es prioritario

### 3.1 Gestor de modelos (lo que hace PocketPal y AgentOS no)

Ahora mismo el modelo se mete a mano con `adb`. Hace falta, en la pantalla:

- **Listar** los `.gguf` de `filesDir/models/` con tamaño y estado.
- **Descargar** desde HuggingFace por URL, con barra de progreso y opción de
  cancelar (el Qwen3 0.6B son 484 MB: hay que avisar y no bajarlo por datos móviles).
- **Cargar / descargar de RAM**: medio giga ocupado importa en un teléfono de 6 GB.
- **Borrar**.
- Catálogo corto y curado, ya medido en el P40:

| Modelo | Tamaño | Medido |
|---|---|---|
| **Qwen3 0.6B** | 484 MB | ✅ 17-30 tok/s · TTFT 730 ms · español correcto |
| Llama 3.2 1B | 771 MB | 9,4 tok/s · español correcto |
| LFM2.5 350M | 229 MB | sin medir |
| Gemma 3 1B | 806 MB | sin medir |
| ~~Bonsai 1.7B~~ | 242 MB | ❌ responde en vietnamita a prompts en español |

### 3.2 Benchmark integrado

El módulo `llama` **ya expone `bench(pp, tg, pl, nr)`** — está en la interfaz
`InferenceEngine` y no cuesta nada exponerlo. Sirve para lo que hicimos a mano
con PocketPal: comparar modelos en el propio teléfono en vez de fiarse de
benchmarks de otros dispositivos.

⚠️ Y algo que aprendimos: el benchmark de PocketPal **aborta** en este teléfono
con `GPU Layers: 99`, porque llama.cpp no tiene backend para la **Mali-G52**.
Todo va por CPU (6 hilos). Nuestro benchmark debe fijar `GPU layers = 0` o
fallará igual.

### 3.2.b ✅ PalsHub TIENE API pública (verificado 2026-08-07)

`GET https://palshub.ai/api/pals` → HTTP 200, JSON con `{pals: [...], pagination}`.
20 por página. Cada entrada trae `title`, `description`, `thumbnail_url`,
`price_cents`, `creator`, `categories`, `tags`, `allow_fork`.

**Y el detalle trae lo que de verdad importa:**

`GET https://palshub.ai/api/pals/{id}` →

```json
{
  "is_free": true,
  "system_prompt": "You are an elite hardware-first AI copilot...",
  "model_reference": {
    "repo_id": "bocalan/Qwen2.5-Coder-3B-Instruct-Q4_K_M-GGUF",
    "size": 1929903072
  }
}
```

**`system_prompt` + `model_reference` = exactamente nuestro `SKILL.md`.** Un Pal
gratuito se puede importar como skill y descargar su GGUF de HuggingFace, todo con
la API pública y sin login.

**Reglas que hay que respetar (no negociables):**
- Importar **sólo** los que traen `is_free: true` / `price_cents: 0`. Los de pago
  (2-3 €) son el negocio de sus autores; su `system_prompt` no viene en la API
  sin comprarlos, y aunque viniera no se toca.
- Conservar y mostrar la **autoría** (`creator.display_name`) y enlazar al Pal
  original. Es trabajo de otra gente.
- Respetar `allow_fork` si se ofrece editar una copia.
- Los `model_reference` apuntan a modelos grandes (el de ejemplo son **1,93 GB**):
  avisar del peso y no descargar por datos móviles.

⚠️ Es una API **no documentada**: puede cambiar o cerrarse sin aviso. El importador
debe fallar limpio y dejar siempre la opción de crear Pals propios a mano.

### 3.3 Pals = skills de personalidad

Extensión mínima del frontmatter que ya parsea el loader:

```yaml
---
name: lingua
description: Traduce y explica pronunciación y formalidad
kind: pal                    # ← nuevo: es personalidad, no comandos
model: qwen3-0.6b            # ← nuevo: qué modelo quiere
context: 2048                # ← nuevo
temperature: 0.3             # ← nuevo
---
Eres un traductor. Para cada frase das: traducción, pronunciación de las
palabras clave, nivel de formalidad y cómo lo diría un nativo.
```

Sin `skill.py`, sin código. El loader ya lo carga como "skill de sólo
documentación". Sólo hay que leer esos cuatro campos y aplicarlos.

**Y encaja con la sala:** cada Pal es un personaje más en la oficina. Instalas
un Pal → aparece alguien nuevo trabajando.

---

## 4. Orden propuesto

1. **Terminar la inferencia local** (falta probarla en el teléfono y enganchar
   `router.py`, que sigue sin que nadie lo llame).
2. **Gestor de modelos** en la UI: sin esto, lo local no lo usa nadie que no
   tenga `adb`.
3. **Benchmark**: casi gratis, `bench()` ya existe. Con GPU layers a 0.
4. **Pals**: extender el frontmatter + pantalla para elegir el Pal activo.
5. **STT local** (whisper.cpp): quita la dependencia de Groq para las notas de voz.
6. **TTS Kokoro**: sólo si la voz pasa a ser rasgo de producto. Hoy el nativo cumple.
