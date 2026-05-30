# AgentOS — Arquitectura

> **Repo técnico:** `indaga-agentos` · **Producto comercial:** *AgentOS* (paraguas Indaga Lab / Indaga Chatbots)
> **Estado:** Fase 0-1 implementadas (scaffold Gradle + Jarvis embebido + puente de hardware completo). Fase 2 parcial: UI lista; faltan autostart-en-boot, watchdog, exención de batería, cifrado en Keystore, setup por QR y loader de skills. · **Fecha:** 2026-05-30

---

## 0. Qué es y para quién

Un **agente de IA que vive 24/7 dentro del celular** y se controla por Telegram (luego Discord). Empaquetado como **app Android nativa instalable** (`.apk`), no como "instala Termux + scripts".

**Nicho diferencial — Android SIN Google.** No competimos con SeekerClaw en su terreno (Solana Seeker, cripto, Play Store). Apuntamos al parque que **nadie atiende**: Huawei post-2019 (como el P40 Lite), ROMs de-Googled (LineageOS / GrapheneOS / e-OS sin GApps), celulares chinos sin GMS. Enorme en LATAM.

> El ángulo NO es "para los que no tienen Android" — el P40 Lite **sí tiene Android 10**. Es **"para los que no tienen Google"**.

**Origen.** Fork conceptual de [SeekerClaw](https://github.com/sepivip/SeekerClaw) (MIT, derivado de OpenClaw). Reusamos su **cáscara Android** (Kotlin + Compose) pero **cambiamos el runtime del agente**: en vez de Node.js embebido (`nodejs-mobile`), corremos **Python embebido (Chaquopy)** para reusar el bot **Jarvis** ya probado (~1504 líneas) que hoy vive en Termux en el P40 Lite.

---

## 1. Principios de diseño (no negociables)

1. **Cero Google.** Ninguna dependencia de GMS / Firebase / Play Services. Telegram por *long-polling* no necesita FCM. Analítica (si la hay) local o self-hosted.
2. **Core mínimo + todo lo demás como plugin.** El núcleo solo orquesta. Cada capacidad es un **skill** (Markdown + YAML) o una **MCP tool**. Objetivo explícito de Diego: *poder injertar features de OpenClaw u otros repos sin tocar el core ni depender de un solo upstream*.
3. **Config 100% desde la app.** Token del bot, provider + modelo + API key, y **whitelist de usuarios de Telegram autorizados** se gestionan desde la UI (no editando archivos). Un no-técnico debe poder configurarlo.
4. **24/7 de verdad.** Foreground service + watchdog + arranque en boot + **guía de exención de batería por fabricante** (el verdadero enemigo, no la versión de Android).
5. **Privacidad y local-first.** Credenciales en Android Keystore. Todo corre en el device; nada obligatorio en la nube salvo el LLM elegido.

---

## 2. Stack

| Capa | Tecnología | Origen |
|---|---|---|
| Cáscara / UI | Kotlin + Jetpack Compose (Material 3) | copiada de SeekerClaw |
| Servicio 24/7 | Foreground Service `START_STICKY` + wake lock + Watchdog + BootReceiver | copiado de SeekerClaw |
| Runtime del agente | **Python via Chaquopy** (CPython embebido en el APK) | **reemplaza** nodejs-mobile |
| Lógica del agente | **Jarvis** (`jarvis.py`) — LLM multi-provider, Telegram, SQLite, scheduler, 12 features | reusado del P40 |
| Puente HW | `AndroidBridge` HTTP en `localhost:8765` | base de SeekerClaw + endpoints nuevos |
| Seguridad | Android Keystore (credenciales), QR para setup | copiado de SeekerClaw |
| minSdk / target | **26 (Android 8)** / 35 | bajado desde el 34 de SeekerClaw |
| Distribución | APK directo · AppGallery · F-Droid | **no** Play Store |

**Por qué Chaquopy:** gratis y OSS desde 2021, no depende de Google, minSdk 21+, soporta `armeabi-v7a` + `arm64-v8a`. Jarvis usa `urllib`/`sqlite3`/`requests`/`threading` — compatible sin drama.

**Por qué minSdk 26 y no 34:** el `minSdk=34` de SeekerClaw es decisión de **producto** (apunta al Seeker / Solana MWA / Seed Vault), **no** un límite técnico. nodejs-mobile y Chaquopy corren desde Android 7. Al quitar el módulo Solana, bajar a 26 es directo.

---

## 3. Estructura del repo

```
indaga-agentos/
├── ARCHITECTURE.md              # este documento
├── README.md
├── build.gradle.kts             # (Fase 0) plugin Chaquopy, minSdk 26, sin Solana/Firebase
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/indagalab/agentos/
│       │   ├── MainActivity.kt
│       │   ├── service/         # AgentService (FGS) · Watchdog · BootReceiver
│       │   ├── bridge/          # AndroidBridge (HTTP localhost:8765) → cámara/GPS/SMS/TTS/...
│       │   ├── ui/              # Setup · Dashboard · Logs · Settings (Compose M3)
│       │   └── config/          # ConfigManager (Keystore) · QrParser · UserWhitelist
│       └── python/              # ← Jarvis vive aquí (Chaquopy)
│           ├── jarvis.py        #   core (LLM/Telegram/SQLite/scheduler/12 features)
│           ├── bridge_client.py #   reemplaza los `termux-*` por requests a localhost:8765
│           └── skills/          #   (Fase 2) loader de skills Markdown+YAML
├── docs/
└── design/
```

Package: `com.indagalab.agentos` (dominio `indagalab.com`).

---

## 4. Runtime: cómo conviven Kotlin y Python

```
┌─────────────────────────────────────────────────────────────┐
│  App Android (proceso único)                                  │
│                                                               │
│  ┌──────────────┐   inicia/vigila   ┌───────────────────┐    │
│  │ AgentService │ ───────────────▶  │ Chaquopy / Python │    │
│  │  (FGS,sticky)│ ◀─── heartbeat ── │   jarvis.py       │    │
│  └──────┬───────┘                   └─────────┬─────────┘    │
│         │                                     │  HTTP        │
│         │ BootReceiver (autostart)            │  localhost   │
│         │ Watchdog (ping 30s / restart 60s)   ▼  :8765       │
│         │                          ┌───────────────────────┐ │
│         └────────────────────────▶ │ AndroidBridge (Kotlin)│ │
│                                     │ cámara·GPS·SMS·TTS·…   │ │
│                                     └───────────────────────┘ │
│  Config en Android Keystore  ·  UI Compose (Setup/Dash/Logs)  │
└───────────────────────────────────────────────────────────────┘
            ▲ Telegram (long-polling, sin Google)
```

- El **FGS** arranca el intérprete Python (Chaquopy) y mantiene vivo a `jarvis.py`.
- **Jarvis ↔ AndroidBridge** hablan por HTTP en `localhost:8765` (mismo patrón que SeekerClaw; agnóstico del lenguaje).
- **Watchdog** reinicia Python si no responde. **BootReceiver** lo levanta tras reboot.

---

## 5. AndroidBridge — mapeo `termux-* → endpoint`

El trabajo central de la Fase 1: cada comando `termux-*` que Jarvis usa hoy se reescribe como una llamada HTTP al bridge Kotlin. Buena parte de estos endpoints **ya existen en SeekerClaw** (su README lista batería/GPS/cámara/SMS/llamadas/clipboard/TTS) — se reusan; el resto se implementa.

| Feature Jarvis | Comando actual (Termux) | Endpoint nuevo | ¿En SeekerClaw? | Notas |
|---|---|---|---|---|
| `/foto` `/selfie` | `termux-camera-photo` | `POST /camera {lens}` | sí (CameraX) | reusar |
| `/ubicacion` | `termux-location` | `GET /gps` | sí | `LocationManager`, sin Google FusedLocation |
| `/bateria` | `termux-battery-status` | `GET /battery` | sí | `BatteryManager` |
| `/diga` (TTS) | `termux-tts-speak` | `POST /tts {text}` | sí | `android.speech.tts` (offline) |
| `/llamar` | `termux-telephony-call` | `POST /call {num}` | prob. sí | `Intent.ACTION_CALL` |
| `/sms` `/enviarsms` | `termux-sms-list` / `-send` | `GET /sms` · `POST /sms` | prob. sí | `SmsManager` + `ContentResolver` |
| OTP auto-forward | `termux-sms-list` (scheduler) | reusa `GET /sms` | — | regex en Python, sin cambios |
| `/linterna` | `termux-torch` | `POST /torch {on}` | **falta** | `CameraManager.setTorchMode` |
| heartbeat antirrobo | `termux-sensor` (accel) | `GET /sensors/accel` | **falta** | `SensorManager` |
| `/escucha` | `termux-microphone-record` | `POST /mic/record` | **falta** | grabar audio; STT por API (Whisper), **no** Google STT |
| clipboard | `termux-clipboard-*` | `GET/POST /clipboard` | prob. sí | reusar |
| notificación local | `termux-notification` | `POST /notify` | prob. sí | `NotificationManager` |

> Las rutas exactas de SeekerClaw se confirman leyendo su `AndroidBridge` al inicio de Fase 1. **`/torch`, `/sensors/accel`, `/mic/record`** son los que sabemos que hay que escribir desde cero.

**Lo que NO cambia en Jarvis:** LLM multi-provider + failover, loop de Telegram, SQLite, scheduler, y la lógica de las 12 features. Solo se sustituye la capa que tocaba hardware (`subprocess termux-*` → `bridge_client.py`).

---

## 6. Configuración y multi-usuario (desde la app)

Requisito de Diego: *configurar el bot y dar permisos a usuarios de Telegram desde la app*.

- **Setup screen:** token del bot · provider (Claude/OpenAI/OpenRouter/Groq/…) + modelo + API key · nombre del agente. Importable por **QR** (`QrParser`) o a mano.
- **UserWhitelist:** lista de `chat_id` de Telegram autorizados, editable en la UI. Jarvis la lee y rechaza a cualquiera fuera de ella. Roles **owner** (todos los comandos, incl. cámara/SMS/llamada) vs **invitado** (subset configurable).
- **Env Vars:** pantalla para inyectar API keys arbitrarias que skills/tools leen vía `os.environ` (patrón tomado de SeekerClaw). Skills pueden exigir `requires.env` y bloquearse limpio si falta la clave.
- Todo lo sensible cifrado en **Android Keystore**.

---

## 7. Extensibilidad (injertar cosas de otros repos)

Para no depender de un solo upstream:

- **Skills (Markdown + YAML):** una capacidad = un archivo en `python/skills/`. Loader en Python que registra comandos/tools al arrancar. Formato compatible-conceptual con OpenClaw para poder portar sus skills.
- **MCP remote tools:** conectar herramientas externas por el protocolo MCP (igual que SeekerClaw).
- **Regla:** si una feature se puede expresar como skill, **no** va al core.

---

## 8. Plan por fases

### Fase 0 — Scaffold que arranca *(siguiente paso tras aprobar este doc)*
- [ ] Copiar la cáscara de SeekerClaw (service / ui / config / bridge).
- [ ] **Quitar** módulo Solana (MWA, sol4k, bouncycastle) y **Firebase/Analytics**.
- [ ] Bajar `minSdk` 34 → 26; quitar `foregroundServiceType` exclusivos de A14 o condicionarlos.
- [ ] **Cambiar `nodejs-mobile` por Chaquopy**; configurar `build.gradle.kts`.
- [ ] "Hola mundo": un `jarvis.py` mínimo que responde en Telegram desde la app.
- [ ] **Meta:** instalar el APK en el P40 Lite y que sobreviva en background.

### Fase 1 — Jarvis adentro
- [ ] Mover `jarvis.py` completo a `app/src/main/python/`.
- [ ] Escribir `bridge_client.py` y **reconectar la capa de hardware** (tabla §5).
- [ ] Implementar endpoints faltantes: `/torch`, `/sensors/accel`, `/mic/record`.
- [ ] Validar las 12 features end-to-end en el P40.

### Fase 2 — Diferenciadores
- [ ] UI Dashboard con estado/logs (+ heatmap de actividad estilo SeekerClaw).
- [ ] **Guía de exención de batería por fabricante** (dontkillmyapp) en el onboarding.
- [ ] Setup por **QR** + `UserWhitelist` en la UI.
- [ ] **Loader de skills** Markdown+YAML en Python.
- [ ] **Self-diagnosis** (el bot revisa sus logs y reporta qué está roto y cómo arreglarlo).

### Fase 3 — Producto
- [ ] Onboarding para terceros, multi-usuario robusto.
- [ ] Hardening anti prompt-injection.
- [ ] Distribución: APK firmado · AppGallery · F-Droid.

---

## 9. Naming

- **Repo técnico:** `indaga-agentos` (convención `indaga-*`, conserva "AgentOS").
- **Comercial:** *AgentOS* bajo el paraguas **Indaga Lab / Indaga Chatbots** — a afinar con branding.
- **Package:** `com.indagalab.agentos`.

---

## 10. Riesgos / "esto no es un fin de semana"

- **Build Chaquopy + NDK** — APK con CPython embebido pesa ~30-60 MB y el primer build pelea con ABIs/wheels.
- **Reconexión de la capa de hardware** (§5) — trabajo acotado pero hay que tocar cada `termux-*`.
- **Battery killers por OEM** (EMUI/MIUI/Samsung) — el verdadero reto del 24/7. Se mitiga con FGS + guía de exención, no se elimina.
- **`edge-tts`/`aiohttp` en Chaquopy** — verificar que haya wheels; si no, caer al `/tts` nativo del bridge.
- **`/escucha` sin Google STT** — `SpeechRecognizer` suele depender de Google; plan: grabar audio y transcribir por API (Whisper).
- **ABI** — el P40 Lite es `arm64-v8a` (OK con Chaquopy). Para celus armv7 antiguos, incluir ambos ABIs encarece el APK.
