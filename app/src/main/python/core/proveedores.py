"""Catálogo de proveedores LLM y prueba de conexión.

Vivía dentro de jarvis_core, así que la interfaz tenía que repetir la lista a
mano y las dos copias se desincronizaban. Aquí está una sola vez y lo usan los
dos lados.

Casi todos hablan el dialecto OpenAI (`/chat/completions` + Bearer). Anthropic
no: usa `/v1/messages` con cabecera `x-api-key` y `anthropic-version`. El campo
`dialecto` lo distingue.

Sin dependencias: urllib basta para un POST con cabecera.
"""

import json
import os
import time
import urllib.error
import urllib.request

# Versión de la API de mensajes de Anthropic. Es una cabecera obligatoria y
# fija; no es el modelo.
ANTHROPIC_VERSION = "2023-06-01"

PROVIDERS = {
    "groq":      {"url": "https://api.groq.com/openai/v1",      "key": "GROQ_API_KEY",      "model": "llama-3.3-70b-versatile"},
    "cerebras":  {"url": "https://api.cerebras.ai/v1",          "key": "CEREBRAS_API_KEY",  "model": "llama3.1-8b"},
    "mistral":   {"url": "https://api.mistral.ai/v1",           "key": "MISTRAL_API_KEY",   "model": "mistral-small-latest"},
    "nvidia":    {"url": "https://integrate.api.nvidia.com/v1", "key": "NVIDIA_API_KEY",    "model": "meta/llama-3.3-70b-instruct"},
    "sambanova": {"url": "https://api.sambanova.ai/v1",         "key": "SAMBANOVA_API_KEY", "model": "Meta-Llama-3.3-70B-Instruct"},
    "gemini":    {"url": "https://generativelanguage.googleapis.com/v1beta/openai", "key": "GOOGLE_API_KEY", "model": "gemini-2.0-flash"},
    "openrouter":{"url": "https://openrouter.ai/api/v1",        "key": "OPENROUTER_API_KEY","model": "meta-llama/llama-3.3-70b-instruct:free"},
    # --- OpenAI-compatibles agregados 2026-05-30 ---
    # cohere y ai21 verificados free; chutes y zai necesitan SALDO en la cuenta
    # (devuelven 402/429 sin crédito) — quedan listos para keys con saldo.
    "cohere":    {"url": "https://api.cohere.ai/compatibility/v1","key": "COHERE_API_KEY",  "model": "command-a-03-2025"},
    "ai21":      {"url": "https://api.ai21.com/studio/v1",       "key": "AI21_API_KEY",     "model": "jamba-mini"},
    "chutes":    {"url": "https://llm.chutes.ai/v1",             "key": "CHUTES_API_KEY",   "model": "deepseek-ai/DeepSeek-V3.2-TEE"},
    "zai":       {"url": "https://api.z.ai/api/paas/v4",         "key": "ZAI_API_KEY",      "model": "glm-4.6"},
    # --- De pago, para quien traiga su propia cuenta ---
    "openai":    {"url": "https://api.openai.com/v1",            "key": "OPENAI_API_KEY",   "model": "gpt-4o-mini"},
    "deepseek":  {"url": "https://api.deepseek.com",             "key": "DEEPSEEK_API_KEY", "model": "deepseek-chat"},
    "xai":       {"url": "https://api.x.ai/v1",                  "key": "XAI_API_KEY",      "model": "grok-3"},
    "moonshot":  {"url": "https://api.moonshot.ai/v1",           "key": "MOONSHOT_API_KEY", "model": "kimi-k2-0905-preview"},
    "qwen":      {"url": "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
                  "key": "DASHSCOPE_API_KEY", "model": "qwen-plus"},
    "github":    {"url": "https://models.github.ai/inference",   "key": "GITHUB_TOKEN",     "model": "openai/gpt-4o-mini"},
    # Anthropic habla otro dialecto: /v1/messages, x-api-key, anthropic-version.
    "anthropic": {"url": "https://api.anthropic.com",            "key": "ANTHROPIC_API_KEY",
                  "model": "claude-opus-5", "dialecto": "anthropic",
                  "sugeridos": ["claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5",
                                "claude-opus-4-8", "claude-sonnet-4-6"]},
    # El comodín: cualquier API compatible con OpenAI que el usuario quiera.
    "propio":    {"url": "", "key": "CUSTOM_API_KEY", "model": "", "url_env": "CUSTOM_API_URL"},
}

# Nombre bonito, dónde se saca la clave y una nota corta. Es información de
# pantalla, pero vive junto al catálogo para que añadir un proveedor sea tocar
# un solo sitio.
FICHAS = {
    "groq":      ("Groq", "https://console.groq.com/keys", "Gratis, muy rápido"),
    "cerebras":  ("Cerebras", "https://cloud.cerebras.ai", "Gratis, el más rápido"),
    "mistral":   ("Mistral", "https://console.mistral.ai/api-keys", "Gratis con límite"),
    "nvidia":    ("Nvidia NIM", "https://build.nvidia.com", "Gratis con cuenta"),
    "sambanova": ("SambaNova", "https://cloud.sambanova.ai/apis", "Gratis con límite"),
    "gemini":    ("Gemini", "https://aistudio.google.com/apikey", "Gratis, cuota diaria"),
    "openrouter":("OpenRouter", "https://openrouter.ai/keys", "Modelos :free"),
    "cohere":    ("Cohere", "https://dashboard.cohere.com/api-keys", "Gratis de prueba"),
    "ai21":      ("AI21", "https://studio.ai21.com/account/api-key", "Gratis de prueba"),
    "chutes":    ("Chutes", "https://chutes.ai", "Necesita saldo"),
    "zai":       ("Z.ai", "https://z.ai", "Necesita saldo"),
    "openai":    ("OpenAI", "https://platform.openai.com/api-keys", "De pago"),
    "deepseek":  ("DeepSeek", "https://platform.deepseek.com/api_keys", "De pago, barato"),
    "anthropic": ("Claude", "https://console.anthropic.com/settings/keys", "De pago"),
    "xai":       ("xAI Grok", "https://console.x.ai", "De pago"),
    "moonshot":  ("Kimi", "https://platform.moonshot.ai/console/api-keys", "De pago"),
    "qwen":      ("Qwen", "https://bailian.console.alibabacloud.com", "Gratis de prueba"),
    "github":    ("GitHub Models", "https://github.com/settings/tokens", "Gratis con tu token"),
    "propio":    ("El tuyo", "", "Cualquier API compatible con OpenAI"),
}


def url_de(pid, env=None):
    """URL base. `propio` la saca del entorno porque la pone el usuario."""
    env = os.environ if env is None else env
    p = PROVIDERS.get(pid) or {}
    if p.get("url_env"):
        return (env.get(p["url_env"]) or "").strip().rstrip("/")
    return p.get("url", "")


def modelo_de(pid, env=None):
    """Modelo a usar. Cualquiera se puede cambiar con `<PID>_MODEL` sin tocar
    el código: los identificadores de modelo caducan mucho antes que la app."""
    env = os.environ if env is None else env
    propio = (env.get("%s_MODEL" % pid.upper()) or "").strip()
    return propio or (PROVIDERS.get(pid) or {}).get("model", "")


def catalogo(env=None):
    """Lista para la interfaz: id, nombre, variable de entorno, modelo, enlace."""
    out = []
    for pid, p in PROVIDERS.items():
        nombre, enlace, nota = FICHAS.get(pid, (pid.title(), "", ""))
        out.append({"id": pid, "nombre": nombre, "env": p["key"],
                    "modelo": modelo_de(pid, env), "enlace": enlace, "nota": nota,
                    "url": url_de(pid, env),
                    "url_env": p.get("url_env", ""),
                    "modelo_env": "%s_MODEL" % pid.upper(),
                    "dialecto": p.get("dialecto", "openai")})
    return out


def peticion(pid, key, mensajes, modelo=None, max_tokens=1024, env=None):
    """Traduce una conversación al dialecto del proveedor.

    Devuelve (url, cabeceras, cuerpo). El resto del código habla en formato
    OpenAI y aquí se adapta, para que añadir un proveedor con otra API no
    obligue a tocar el agente.
    """
    p = PROVIDERS.get(pid)
    if p is None:
        raise ValueError("proveedor desconocido: %s" % pid)
    base = url_de(pid, env)
    if not base:
        raise ValueError("falta la URL de %s" % pid)
    modelo = modelo or modelo_de(pid, env)

    if p.get("dialecto") == "anthropic":
        # El system va en su propio campo, no como un mensaje más.
        sistema = "\n".join(m["content"] for m in mensajes if m.get("role") == "system")
        cuerpo = {
            "model": modelo,
            "max_tokens": max_tokens,
            "messages": [m for m in mensajes if m.get("role") != "system"],
        }
        if sistema:
            cuerpo["system"] = sistema
        return (base.rstrip("/") + "/v1/messages",
                {"x-api-key": key,
                 "anthropic-version": ANTHROPIC_VERSION,
                 "Content-Type": "application/json"},
                cuerpo)

    return (base.rstrip("/") + "/chat/completions",
            {"Authorization": "Bearer %s" % key, "Content-Type": "application/json"},
            {"model": modelo, "messages": mensajes, "max_tokens": max_tokens})


def a_formato_openai(pid, respuesta):
    """Respuesta de Anthropic con la forma que espera el resto del código."""
    if (PROVIDERS.get(pid) or {}).get("dialecto") != "anthropic":
        return respuesta
    texto = "".join(b.get("text", "") for b in respuesta.get("content", [])
                    if b.get("type") == "text")
    u = respuesta.get("usage") or {}
    return {
        "choices": [{"message": {"role": "assistant", "content": texto},
                     "finish_reason": respuesta.get("stop_reason", "stop")}],
        "usage": {"prompt_tokens": u.get("input_tokens", 0),
                  "completion_tokens": u.get("output_tokens", 0)},
    }


def listar_modelos(pid, key, timeout=20, env=None):
    """Los modelos que ese proveedor dice tener, preguntándoselo a él.

    Una lista escrita a mano caduca en semanas; `GET /models` devuelve lo que
    la cuenta puede usar hoy. Sin clave no hay lista: los identificadores
    dependen del plan contratado.
    """
    p = PROVIDERS.get(pid)
    if p is None:
        return {"ok": False, "modelos": [], "detalle": "proveedor desconocido"}
    base = url_de(pid, env)
    if not base:
        return {"ok": False, "modelos": [], "detalle": "falta la URL"}
    if not key:
        return {"ok": False, "modelos": p.get("sugeridos", []), "detalle": "sin clave"}

    if p.get("dialecto") == "anthropic":
        url = base.rstrip("/") + "/v1/models?limit=100"
        cab = {"x-api-key": key, "anthropic-version": ANTHROPIC_VERSION}
    else:
        url = base.rstrip("/") + "/models"
        cab = {"Authorization": "Bearer %s" % key}

    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=cab), timeout=timeout) as r:
            d = json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"ok": False, "modelos": p.get("sugeridos", []),
                "detalle": "HTTP %s" % e.code}
    except Exception as e:
        return {"ok": False, "modelos": p.get("sugeridos", []),
                "detalle": "%s: %s" % (type(e).__name__, e)}

    # Cada uno envuelve la lista a su manera; todos traen el id dentro.
    crudo = d.get("data") if isinstance(d, dict) else d
    if crudo is None and isinstance(d, dict):
        crudo = d.get("models") or d.get("body") or []
    modelos = []
    for m in crudo or []:
        mid = m.get("id") or m.get("name") or m.get("model") if isinstance(m, dict) else m
        if mid:
            modelos.append(str(mid))
    return {"ok": bool(modelos), "modelos": sorted(set(modelos)),
            "detalle": "" if modelos else "el proveedor no devolvió modelos"}


def probar(pid, key, timeout=20, env=None):
    """Un chat de un token contra el proveedor. Devuelve {ok, ms, detalle}.

    Es la única forma honesta de saber si una clave sirve: que el servidor
    conteste. Comprobar que la variable no está vacía no prueba nada.
    """
    if pid not in PROVIDERS:
        return {"ok": False, "ms": 0, "detalle": "proveedor desconocido"}
    if not key:
        return {"ok": False, "ms": 0, "detalle": "sin clave"}

    try:
        url, cabeceras, cuerpo = peticion(
            pid, key, [{"role": "user", "content": "ping"}], max_tokens=1, env=env)
    except ValueError as e:
        return {"ok": False, "ms": 0, "detalle": str(e)}

    req = urllib.request.Request(url, data=json.dumps(cuerpo).encode(), headers=cabeceras)
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            r.read(1)
        return {"ok": True, "ms": int((time.time() - t0) * 1000),
                "detalle": cuerpo.get("model", "")}
    except urllib.error.HTTPError as e:
        cuerpo_err = ""
        try:
            d = json.loads(e.read().decode())
            cuerpo_err = (d.get("error") or {}).get("message", "")[:90]
        except Exception:
            pass
        return {"ok": False, "ms": int((time.time() - t0) * 1000),
                "detalle": "%s %s" % (e.code, cuerpo_err or e.reason)}
    except Exception as e:
        return {"ok": False, "ms": int((time.time() - t0) * 1000),
                "detalle": "%s: %s" % (type(e).__name__, e)}


if __name__ == "__main__":
    assert catalogo()[0]["id"] == "groq"
    assert probar("groq", "")["detalle"] == "sin clave"
    assert probar("no-existe", "x")["ok"] is False

    # El dialecto de Anthropic: otro endpoint, otras cabeceras, system aparte.
    url, cab, cuerpo = peticion("anthropic", "k", [
        {"role": "system", "content": "eres un bot"},
        {"role": "user", "content": "hola"},
    ], max_tokens=7)
    assert url == "https://api.anthropic.com/v1/messages", url
    assert cab["x-api-key"] == "k" and cab["anthropic-version"] == ANTHROPIC_VERSION
    assert cuerpo["system"] == "eres un bot"
    assert cuerpo["messages"] == [{"role": "user", "content": "hola"}]
    assert cuerpo["max_tokens"] == 7

    # Y su respuesta se lee igual que la de cualquier otro.
    r = a_formato_openai("anthropic", {
        "content": [{"type": "text", "text": "hola"}],
        "usage": {"input_tokens": 3, "output_tokens": 1},
    })
    assert r["choices"][0]["message"]["content"] == "hola"
    assert r["usage"]["prompt_tokens"] == 3

    # El resto sigue siendo OpenAI puro.
    url, cab, cuerpo = peticion("groq", "k", [{"role": "user", "content": "hola"}])
    assert url.endswith("/chat/completions") and cab["Authorization"] == "Bearer k"

    # Modelo y URL sobreescribibles sin tocar código.
    e = {"GROQ_MODEL": "otro", "CUSTOM_API_URL": "https://mi.servidor/v1/"}
    assert modelo_de("groq", e) == "otro"
    assert url_de("propio", e) == "https://mi.servidor/v1"
    assert probar("propio", "k", env={})["detalle"].startswith("falta la URL")

    assert listar_modelos("groq", "")["detalle"] == "sin clave"
    assert listar_modelos("anthropic", "")["modelos"][0] == "claude-opus-5"
    assert listar_modelos("propio", "k", env={})["detalle"] == "falta la URL"

    print("ok", len(PROVIDERS), "proveedores")
