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

🚧 **Fase 0-1 funcionando** (scaffold + Jarvis embebido + puente de hardware sin Termux). Fase 2 en curso. Ver [`ARCHITECTURE.md`](./ARCHITECTURE.md) para el diseño completo, el mapeo de hardware y el plan por fases.

## Proveedores LLM soportados

El agente habla con cualquier proveedor **OpenAI-compatible**. Configurá las keys desde la app (pestaña **Config**, formato `KEY=VALOR`). Cada quien saca su propia key gratis de:

| Proveedor | Variable | Dónde sacar la key | Free |
|---|---|---|---|
| Groq | `GROQ_API_KEY` | console.groq.com/keys | ✅ |
| Cerebras | `CEREBRAS_API_KEY` | cloud.cerebras.ai | ✅ |
| Mistral | `MISTRAL_API_KEY` | console.mistral.ai | ✅ |
| NVIDIA NIM | `NVIDIA_API_KEY` | build.nvidia.com | ✅ |
| SambaNova | `SAMBANOVA_API_KEY` | cloud.sambanova.ai | ✅ |
| Google Gemini | `GOOGLE_API_KEY` | aistudio.google.com/apikey | ✅ |
| OpenRouter | `OPENROUTER_API_KEY` | openrouter.ai/keys | ✅ |
| Cohere | `COHERE_API_KEY` | dashboard.cohere.com/api-keys | ✅ |
| AI21 | `AI21_API_KEY` | studio.ai21.com | ✅ (crédito) |
| Chutes | `CHUTES_API_KEY` | chutes.ai | 💲 saldo |
| Z.ai (GLM) | `ZAI_API_KEY` | z.ai | 💲 saldo |

> El proveedor activo se cambia con `/provider <nombre>` por Telegram; `/providers` los lista. El agente falla-over automático entre los free si uno devuelve error.

## Créditos

Cáscara Android inspirada en [SeekerClaw](https://github.com/sepivip/SeekerClaw) (MIT) / OpenClaw. Runtime Python via [Chaquopy](https://chaquo.com/chaquopy/).
