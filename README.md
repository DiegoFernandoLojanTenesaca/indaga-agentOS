# AgentOS

> Un agente de IA 24/7 dentro de tu celular, controlado por Telegram — **para Android sin Google**.

Producto del paraguas **Indaga Lab / Indaga Chatbots**. App Android nativa (Kotlin + Compose) con un agente **Python (Chaquopy)** embebido, basado en el bot **Jarvis**.

## El nicho

No es para los que tienen un Solana Seeker. Es para el parque que **nadie atiende**: Huawei post-2019, ROMs de-Googled (LineageOS / GrapheneOS / e-OS), celulares sin Google Mobile Services. Grande, sobre todo en LATAM.

- ✅ **Cero dependencias de Google** (sin GMS / Firebase / Play Services).
- ✅ Corre en **Android 8+** (minSdk 26).
- ✅ Telegram por long-polling (no necesita push de Google).
- ✅ Config del bot y permisos de usuarios **desde la app**.
- ✅ Extensible con **skills** (Markdown+YAML) y **MCP tools**.

## Estado

🚧 **Blueprint.** Ver [`ARCHITECTURE.md`](./ARCHITECTURE.md) para el diseño completo, el mapeo de hardware y el plan por fases.

## Créditos

Cáscara Android inspirada en [SeekerClaw](https://github.com/sepivip/SeekerClaw) (MIT) / OpenClaw. Runtime Python via [Chaquopy](https://chaquo.com/chaquopy/).
