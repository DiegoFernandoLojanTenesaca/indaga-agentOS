# -*- coding: utf-8 -*-
"""Jarvis v5 - bot Telegram con superpoderes (portado a AgentOS / Chaquopy, sin Google)."""
import os, sys, time, json, re, glob, base64, sqlite3, subprocess, threading, hashlib, datetime
import urllib.request, urllib.parse, urllib.error
import html as htmllib

# AGENTOS_HOME lo inyecta la app (filesDir) como directorio escribible para
# jarvis.db, fotos y audios. En Termux/escritorio cae al dir del módulo.
HERE = os.environ.get("AGENTOS_HOME") or os.path.dirname(os.path.abspath(__file__))
try: os.makedirs(HERE, exist_ok=True)
except Exception: pass
UA = "Mozilla/5.0 (AgentOS-Jarvis)"
DB = os.path.join(HERE, "jarvis.db")
_STOP = threading.Event()  # lo limpia/levanta el wrapper jarvis.py (start/stop)

def load_env():
    env = dict(os.environ)
    p = os.path.join(HERE, ".env")
    if os.path.isfile(p):
        for line in open(p):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, _, v = line.partition("="); env[k.strip()] = v.strip()
    return env

ENV = load_env()
TG_TOKEN = ENV.get("TELEGRAM_TOKEN", "")
OWNER_ID = ENV.get("OWNER_ID", "").strip()
TTS_VOICE = ENV.get("TTS_VOICE", "es-ES-AlvaroNeural")
CITY = ENV.get("CITY", "Quito")
BRIEF_HOUR = int(ENV.get("BRIEF_HOUR", "7"))

def allowed_ids():
    s = set()
    if OWNER_ID: s.add(OWNER_ID)
    for x in ENV.get("ALLOWED_IDS", "").split(","):
        if x.strip(): s.add(x.strip())
    return s

# comandos que solo el dueno puede usar (controlan el telefono fisico)
OWNER_ONLY = {"run", "foto", "selfie", "ubicacion", "bateria", "linterna", "diga", "escucha",
              "vigilancia", "alarma", "antirobo", "copia", "pega", "baja", "agente"}

PROVIDERS = {
    "groq":      {"url": "https://api.groq.com/openai/v1",      "key": "GROQ_API_KEY",      "model": "llama-3.3-70b-versatile"},
    "cerebras":  {"url": "https://api.cerebras.ai/v1",          "key": "CEREBRAS_API_KEY",  "model": "llama3.1-8b"},
    "mistral":   {"url": "https://api.mistral.ai/v1",           "key": "MISTRAL_API_KEY",   "model": "mistral-small-latest"},
    "nvidia":    {"url": "https://integrate.api.nvidia.com/v1", "key": "NVIDIA_API_KEY",    "model": "meta/llama-3.3-70b-instruct"},
    "sambanova": {"url": "https://api.sambanova.ai/v1",         "key": "SAMBANOVA_API_KEY", "model": "Meta-Llama-3.3-70B-Instruct"},
    "gemini":    {"url": "https://generativelanguage.googleapis.com/v1beta/openai", "key": "GOOGLE_API_KEY", "model": "gemini-2.0-flash"},
    "openrouter":{"url": "https://openrouter.ai/api/v1",        "key": "OPENROUTER_API_KEY","model": "meta-llama/llama-3.3-70b-instruct:free"},
}
FALLBACK = ["groq", "cerebras", "mistral", "nvidia", "sambanova"]
VISION = {"provider": "groq", "model": "meta-llama/llama-4-scout-17b-16e-instruct"}
MODES = {
    "normal": "Eres Jarvis, asistente personal conciso, directo y util. Espanol salvo que pidan otro idioma.",
    "profe":  "Eres un profesor paciente. Explicas con ejemplos claros, paso a paso, en espanol.",
    "coder":  "Eres un ingeniero de software senior. Codigo correcto y breve.",
    "lab":    "Eres instructor de ciberseguridad para un laboratorio educativo AUTORIZADO. Ensenas de forma etica y legal.",
    "conciso":"Responde siempre en 1-2 frases, directo.",
}
STATE = {"provider": "groq", "model": PROVIDERS["groq"]["model"], "mode": "normal",
         "voz": False, "last": "groq", "agente": False, "traduce": "off",
         "ausente": "", "silencio": False, "pomodoro": None}
HIST, USAGE, DOCS, START = {}, {}, {}, time.time()
VIG = {"on": False}; ANTIROBO = {"on": False}; MON = {"batt": 0, "temp": 0, "ram": 0}
SMS_LAST_ID = {"v": 0}  # tracking del último SMS forwardeado
SILENCIO_QUEUE = []  # mensajes acumulados durante modo silencioso

def db():
    c = sqlite3.connect(DB, timeout=10)
    c.execute("CREATE TABLE IF NOT EXISTS notas(id INTEGER PRIMARY KEY AUTOINCREMENT, chat TEXT, t TEXT, ts TEXT)")
    c.execute("CREATE TABLE IF NOT EXISTS recordatorios(id INTEGER PRIMARY KEY AUTOINCREMENT, chat TEXT, texto TEXT, due REAL, hecho INT)")
    c.execute("CREATE TABLE IF NOT EXISTS vigias(id INTEGER PRIMARY KEY AUTOINCREMENT, chat TEXT, url TEXT, palabra TEXT, last_hash TEXT)")
    c.execute("CREATE TABLE IF NOT EXISTS inbox_ausente(id INTEGER PRIMARY KEY AUTOINCREMENT, chat TEXT, from_id TEXT, from_name TEXT, text TEXT, ts TEXT)")
    c.execute("CREATE TABLE IF NOT EXISTS programados(id INTEGER PRIMARY KEY AUTOINCREMENT, canal TEXT, destino TEXT, texto TEXT, due REAL, hecho INT)")
    c.execute("CREATE TABLE IF NOT EXISTS diario(id INTEGER PRIMARY KEY AUTOINCREMENT, chat TEXT, fecha TEXT, texto TEXT, ts TEXT)")
    c.execute("CREATE TABLE IF NOT EXISTS listas(id INTEGER PRIMARY KEY AUTOINCREMENT, chat TEXT, nombre TEXT, items TEXT, ts TEXT)")
    c.execute("CREATE TABLE IF NOT EXISTS memoria_lp(id INTEGER PRIMARY KEY AUTOINCREMENT, chat TEXT, role TEXT, content TEXT, ts REAL)")
    return c

# ---------- LLM ----------
def _post(url, key, payload, timeout=90):
    req = urllib.request.Request(url, data=json.dumps(payload).encode(),
            headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json", "User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read())

def call_raw(provider, messages, tools=None, model=None, max_tokens=1024):
    p = PROVIDERS[provider]; key = ENV.get(p["key"], "")
    if not key: raise RuntimeError(f"falta {p['key']}")
    payload = {"model": model or p["model"], "messages": messages, "max_tokens": max_tokens, "temperature": 0.5}
    if tools: payload["tools"] = tools
    d = _post(p["url"] + "/chat/completions", key, payload)
    u = d.get("usage") or {}
    a = USAGE.setdefault(provider, {"req": 0, "in": 0, "out": 0})
    a["req"] += 1; a["in"] += u.get("prompt_tokens", 0); a["out"] += u.get("completion_tokens", 0)
    return d

def call(provider, messages, model=None, max_tokens=1024):
    return call_raw(provider, messages, None, model, max_tokens)["choices"][0]["message"]["content"]

def llm(messages):
    order = [STATE["provider"]] + [p for p in FALLBACK if p != STATE["provider"]]
    err = None
    for prov in order:
        if not ENV.get(PROVIDERS[prov]["key"]): continue
        try:
            ans = call(prov, messages, STATE["model"] if prov == STATE["provider"] else None)
            STATE["last"] = prov; return ans
        except Exception as e:
            err = f"{prov}: {e}"
    raise RuntimeError(f"todos fallaron ({err})")

def sysprompt(): return MODES.get(STATE["mode"], MODES["normal"])

# ---------- Telegram ----------
def tg(method, **params):
    req = urllib.request.Request(f"https://api.telegram.org/bot{TG_TOKEN}/{method}",
            data=json.dumps(params).encode(), headers={"Content-Type": "application/json", "User-Agent": UA})
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.loads(r.read())

def _multipart(fields, files):
    b = "----jarvis%d" % int(time.time() * 1000); body = b""
    for k, v in fields.items():
        body += ("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n" % (b, k, v)).encode()
    for name, fn, data, ct in files:
        body += ("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n" % (b, name, fn, ct)).encode()
        body += data + b"\r\n"
    body += ("--%s--\r\n" % b).encode()
    return b, body

def tg_file(method, fields, files):
    b, body = _multipart(fields, files)
    req = urllib.request.Request(f"https://api.telegram.org/bot{TG_TOKEN}/{method}", data=body,
            headers={"Content-Type": "multipart/form-data; boundary=" + b, "User-Agent": UA})
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.loads(r.read())

# ---------- inline keyboards / callback helpers ----------
PENDING = {}  # {chat_id_str: "city" | "brief_hour" | "tts_voice" | "diario"} para flujos ForceReply
DIARIO_LAST = {"date": None}  # tracking: si ya preguntó el diario hoy
WIZARDS = {}  # {chat_id_str: {"step": str, "canal": str, "destino": str, "due": float, "texto": str, "msg_id": int}}

def normalizar_telefono(num):
    """Normaliza un número de teléfono a formato internacional (+593...).
    Acepta: 0985195879 (EC local), 985195879, +593985195879, 593985195879."""
    n = re.sub(r'\D', '', num)  # solo dígitos
    if not n: return None
    if num.strip().startswith("+"): return "+" + n  # ya tenía +
    if n.startswith("593") and len(n) >= 11: return "+" + n
    if n.startswith("0") and len(n) == 10: return "+593" + n[1:]  # EC local 09xxxxxxxx
    if len(n) == 9 and n.startswith("9"): return "+593" + n  # EC sin 0 inicial
    return "+" + n  # asume internacional

def kb(rows):
    """Arma reply_markup inline_keyboard. rows = [[(text, callback_data), ...], ...]"""
    return {"inline_keyboard": [[{"text": t, "callback_data": d} for t, d in row] for row in rows]}

def send_kb(chat, text, rows, parse_mode=None):
    p = {"chat_id": chat, "text": text[:4000], "reply_markup": kb(rows)}
    if parse_mode: p["parse_mode"] = parse_mode
    try: return tg("sendMessage", **p)
    except Exception as e: print("send_kb:", e)

def edit_kb(chat, msg_id, text, rows, parse_mode=None):
    p = {"chat_id": chat, "message_id": msg_id, "text": text[:4000], "reply_markup": kb(rows)}
    if parse_mode: p["parse_mode"] = parse_mode
    try: return tg("editMessageText", **p)
    except Exception as e: print("edit_kb:", e)

def answer_cb(cb_id, text=None, alert=False):
    p = {"callback_query_id": cb_id}
    if text: p["text"] = text[:200]
    if alert: p["show_alert"] = True
    try: tg("answerCallbackQuery", **p)
    except Exception as e:
        # error 400 = callback expirado (timing); no contaminar logs
        if "400" not in str(e): print("answer_cb:", e)

def force_reply(chat, text, placeholder=""):
    rm = {"force_reply": True, "selective": True}
    if placeholder: rm["input_field_placeholder"] = placeholder[:64]
    try: tg("sendMessage", chat_id=chat, text=text, reply_markup=rm)
    except Exception as e: print("force_reply:", e)

def send(chat, text):
    text = text or "(vacio)"
    for i in range(0, len(text), 4000):
        try: tg("sendMessage", chat_id=chat, text=text[i:i+4000])
        except Exception as e: print("send:", e)

def esc(s): return str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

def sendf(chat, html):  # mensajes "bonitos" con formato HTML (solo texto que yo controlo)
    try: tg("sendMessage", chat_id=chat, text=html[:4000], parse_mode="HTML")
    except Exception as e: print("sendf:", e); send(chat, re.sub('<[^>]+>', '', html))

def send_photo(chat, path, cap=""):
    tg_file("sendPhoto", {"chat_id": str(chat), "caption": cap[:1000]}, [("photo", "img.jpg", open(path, "rb").read(), "image/jpeg")])

def send_audio(chat, path, cap=""):
    tg_file("sendAudio", {"chat_id": str(chat), "caption": cap[:1000]}, [("audio", os.path.basename(path), open(path, "rb").read(), "audio/mpeg")])

def dl_tg_file(file_id):
    fp = tg("getFile", file_id=file_id)["result"]["file_path"]
    return urllib.request.urlopen(f"https://api.telegram.org/file/bot{TG_TOKEN}/{fp}", timeout=60).read()

# ---------- utilidades ----------
def sh(cmd, timeout=120):
    # En AgentOS no hay shell Termux: los comandos termux-* se enrutan al
    # AndroidBridge (Fase 1b). Por ahora se reportan como no disponibles.
    if "termux-" in cmd:
        try:
            import bridge_client
            return bridge_client.termux_shim(cmd, timeout=timeout)
        except Exception as e:
            return "[hardware no disponible aún (Fase 1b): %s]" % e
    o = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    return (o.stdout + o.stderr).strip()

def notify(title, content):
    try: sh("termux-notification --title %s --content %s" % (json.dumps(title), json.dumps(content)), timeout=15)
    except Exception as e: print("notify:", e)

def shrink(path, maxpx=1024, q=72):
    # comprime imagen para que quepa en el limite de la vision API (<4MB)
    try:
        from PIL import Image
        im = Image.open(path); im.thumbnail((maxpx, maxpx))
        out = os.path.join(HERE, "_s.jpg"); im.convert("RGB").save(out, "JPEG", quality=q)
        return out
    except Exception:
        return path

def speak(chat, text):
    # La voz (edge-tts) se hará vía AndroidBridge /tts (Fase 1b). Por ahora
    # devolvemos False para que reply() responda en texto.
    return False

def reply(chat, text):
    if STATE["voz"] and speak(chat, text): return
    send(chat, text)

def transcribe(audio, fname="audio.ogg", ctype="audio/ogg"):
    key = ENV.get("GROQ_API_KEY", "")
    b, body = _multipart({"model": "whisper-large-v3", "language": "es", "response_format": "json"}, [("file", fname, audio, ctype)])
    req = urllib.request.Request("https://api.groq.com/openai/v1/audio/transcriptions", data=body,
            headers={"Authorization": f"Bearer {key}", "Content-Type": "multipart/form-data; boundary=" + b, "User-Agent": UA})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read()).get("text", "")

def web_search(q, n=4):
    page = urllib.request.urlopen(urllib.request.Request(
        "https://html.duckduckgo.com/html/?q=" + urllib.parse.quote(q), headers={"User-Agent": UA}), timeout=20).read().decode("utf-8", "ignore")
    titles = re.findall(r'class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>', page, re.S)
    snips = re.findall(r'class="result__snippet"[^>]*>(.*?)</a>', page, re.S)
    out = []
    for i, (href, title) in enumerate(titles[:n]):
        u = href; m = re.search(r'uddg=([^&]+)', href)
        if m: u = urllib.parse.unquote(m.group(1))
        t = htmllib.unescape(re.sub('<.*?>', '', title)).strip()
        s = htmllib.unescape(re.sub('<.*?>', '', snips[i])).strip() if i < len(snips) else ""
        out.append((t, s, u))
    return out

def fetch_text(url, limit=6000):
    page = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=25).read().decode("utf-8", "ignore")
    page = re.sub(r'<(script|style)[^>]*>.*?</\1>', ' ', page, flags=re.S | re.I)
    return re.sub(r'\s+', ' ', htmllib.unescape(re.sub('<[^>]+>', ' ', page))).strip()[:limit]

def weather(city):
    try:
        u = "https://wttr.in/%s?format=%%l:+%%c+%%t+(sens+%%f),+hum+%%h,+viento+%%w&lang=es" % urllib.parse.quote(city)
        return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "curl/8"}), timeout=15).read().decode("utf-8", "ignore").strip()
    except Exception as e:
        return f"(clima n/d: {e})"

def memoria_lp_save(chat, role, content):
    """[11] persistir turno de conversación en SQLite si vale la pena (texto largo / no triviales)."""
    if len(content or "") < 80: return  # ignorar respuestas/preguntas cortas
    try:
        c = db(); c.execute("INSERT INTO memoria_lp(chat,role,content,ts) VALUES(?,?,?,?)",
                            (str(chat), role, content[:4000], time.time())); c.commit(); c.close()
    except Exception as e: print("memoria_lp:", e)

def chat_answer(chat, text):
    sysmsg = sysprompt()
    if DOCS.get(chat): sysmsg += "\n\nDOCUMENTO del usuario (responde basandote en el):\n" + DOCS[chat][:8000]
    h = HIST.setdefault(chat, [{"role": "system", "content": sysmsg}])
    h[0] = {"role": "system", "content": sysmsg}
    h.append({"role": "user", "content": text}); HIST[chat] = h[:1] + h[-20:]
    ans = llm(HIST[chat]); HIST[chat].append({"role": "assistant", "content": ans})
    # [11] persistir turnos largos en memoria a largo plazo
    memoria_lp_save(chat, "user", text)
    memoria_lp_save(chat, "assistant", ans)
    return ans

def translate(text):
    return llm([{"role": "system", "content": f"Traduce el texto al {STATE['traduce']}. Responde SOLO con la traduccion."}, {"role": "user", "content": text}])

def gen_image(prompt, out):
    url = "https://image.pollinations.ai/prompt/" + urllib.parse.quote(prompt) + "?width=768&height=768&nologo=true"
    open(out, "wb").write(urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=150).read()); return out

LOJA_NEWS_SOURCES = [
    ("La Hora Loja", "https://lahora.com.ec/loja"),
    ("Diario Crónica", "https://cronica.com.ec/"),
    ("Diario Centinela", "https://diariocentinela.com.ec/"),
    ("Ecuavisa", "https://www.ecuavisa.com/noticias"),
]

# Patrones que NO son titulares reales (navegación, headers, etc)
_TITLE_BLACKLIST = re.compile(
    r'(?i)^(noticias?|loja|inicio|home|men[uú]|m[áa]s\s+leid|categor|secci|portada|últimas|tendencia|opini[oó]n)\s*([-–|]|de\s+loja|ecuador)?\s*$|'
    r'cookie|publicidad|suscr[ií]b|seguir\s+leyendo|leer\s+m[áa]s|comentarios?$'
)

def scrape_titulares(url, max_n=10):
    """Extrae candidatos a titulares (h1-h3) de una home de noticias."""
    try:
        page = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=20).read().decode("utf-8", "ignore")
    except Exception as e:
        return [(f"(no se pudo cargar {url}: {e})", "")]
    # OJO: NO incluir 'header' — muchos CMS envuelven titulares en <header class="entry-header">
    page = re.sub(r'<(script|style|nav|footer|aside|form)[^>]*>.*?</\1>', ' ', page, flags=re.S | re.I)
    titles = []
    seen = set()
    # patrón simple: capturar TODO el contenido entre <h1-3> y </h1-3>, luego limpiar tags
    for m in re.finditer(r'<h[1-3][^>]*>(.{1,800}?)</h[1-3]>', page, re.S | re.I):
        raw = m.group(1)
        # extraer href si hay <a> adentro
        href_m = re.search(r'<a[^>]*href="([^"]+)"', raw)
        href = href_m.group(1) if href_m else ""
        # limpiar todos los tags y normalizar espacios
        t = htmllib.unescape(re.sub('<[^>]+>', ' ', raw)).strip()
        t = re.sub(r'\s+', ' ', t)
        # filtros: longitud, blacklist, duplicados
        if not (20 < len(t) < 220): continue
        if _TITLE_BLACKLIST.search(t): continue
        key = t.lower()[:50]
        if key in seen: continue
        seen.add(key)
        titles.append((t, href))
        if len(titles) >= max_n: break
    return titles or [("(sin titulares detectados)", "")]

def briefing_loja_news(max_n=3):
    """Junta titulares de varias fuentes locales, LLM elige y resume top 3."""
    pool = []
    for label, url in LOJA_NEWS_SOURCES:
        for t, _ in scrape_titulares(url, 6):
            if t.startswith("(") or "no se pudo" in t: continue
            pool.append(f"[{label}] {t}")
        if len(pool) >= 20: break
    if not pool: return "(no logré bajar noticias locales)"
    raw = "\n".join(f"- {t}" for t in pool[:25])
    try:
        ans = llm([
            {"role": "system", "content": "Elegí los 3 titulares MÁS RELEVANTES de Loja, Ecuador. Para cada uno: 1 línea corta. Numerá 1/2/3. Cero relleno. Solo español."},
            {"role": "user", "content": "Titulares candidatos:\n" + raw}
        ])
        return ans
    except Exception as e:
        return raw[:1000]

def do_briefing(chat):
    w = weather(CITY)
    news = briefing_loja_news()
    msg = f"☀️ <b>BUENOS DÍAS</b> — {datetime.datetime.now().strftime('%A %d/%m')}\n\n🌤️ <b>Clima ({esc(CITY)})</b>\n{esc(w)}\n\n📰 <b>Top 3 noticias locales</b>\n{esc(news)}"
    sendf(chat, msg)

def parse_delay(s):
    s = s.strip().lower()
    m = re.match(r'^(\d+)\s*([smhd])$', s)
    if m: return int(m.group(1)) * {"s": 1, "m": 60, "h": 3600, "d": 86400}[m.group(2)]
    m = re.match(r'^(\d{1,2}):(\d{2})$', s)
    if m:
        now = datetime.datetime.now(); t = now.replace(hour=int(m.group(1)), minute=int(m.group(2)), second=0, microsecond=0)
        if t <= now: t += datetime.timedelta(days=1)
        return int((t - now).total_seconds())
    return None

# ---------- AGENTE (tool-calling) ----------
TOOLS = [
    {"type": "function", "function": {"name": "buscar_web", "description": "Busca informacion actual en internet", "parameters": {"type": "object", "properties": {"q": {"type": "string"}}, "required": ["q"]}}},
    {"type": "function", "function": {"name": "tomar_foto", "description": "Toma una foto con la camara trasera y la envia", "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "bateria", "description": "Estado de la bateria del telefono", "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "ubicacion", "description": "Ubicacion GPS del telefono", "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "clima", "description": "Clima actual", "parameters": {"type": "object", "properties": {"ciudad": {"type": "string"}}}}},
    {"type": "function", "function": {"name": "recordatorio", "description": "Crea un recordatorio", "parameters": {"type": "object", "properties": {"minutos": {"type": "integer"}, "texto": {"type": "string"}}, "required": ["minutos", "texto"]}}},
    {"type": "function", "function": {"name": "generar_imagen", "description": "Genera y envia una imagen", "parameters": {"type": "object", "properties": {"prompt": {"type": "string"}}, "required": ["prompt"]}}},
]

def run_tool(chat, name, args):
    try:
        if name == "buscar_web":
            return "\n".join(f"- {t}: {s}" for t, s, _ in web_search(args.get("q", ""), 4)) or "sin resultados"
        if name == "tomar_foto":
            p = os.path.join(HERE, "cam.jpg"); sh(f"termux-camera-photo -c 0 {p}", timeout=40)
            if not (os.path.isfile(p) and os.path.getsize(p) > 0):
                return "no se pudo tomar la foto (permiso de camara?)"
            s = shrink(p)
            try: send_photo(chat, s, "📸")
            except Exception as e: print("send foto:", e)
            try:
                b64 = base64.b64encode(open(s, "rb").read()).decode()
                desc = call(VISION["provider"], [{"role": "user", "content": [
                    {"type": "text", "text": "Describe esta imagen en espanol, breve."},
                    {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + b64}}]}], VISION["model"])
                return "Foto enviada al usuario. Esto se ve: " + desc
            except Exception as e:
                return "Foto enviada (no pude analizar: %s)" % e
        if name == "bateria":
            return sh("termux-battery-status")
        if name == "ubicacion":
            return sh("termux-location -p network", timeout=40) or "sin GPS"
        if name == "clima":
            return weather(args.get("ciudad", CITY))
        if name == "recordatorio":
            c = db(); c.execute("INSERT INTO recordatorios(chat,texto,due,hecho) VALUES(?,?,?,0)", (str(chat), args.get("texto", ""), time.time() + int(args.get("minutos", 1)) * 60)); c.commit(); c.close()
            return "recordatorio creado"
        if name == "generar_imagen":
            send_photo(chat, gen_image(args.get("prompt", ""), os.path.join(HERE, "img.jpg")), "🎨"); return "imagen enviada"
    except Exception as e:
        return f"error en {name}: {e}"
    return "tool desconocida"

def agent_run(chat, text):
    msgs = [{"role": "system", "content": "Eres Jarvis, asistente con acceso REAL a este telefono. PUEDES y DEBES usar tus herramientas: tomar fotos (y las analizas), ver GPS/ubicacion, bateria, buscar en internet, generar imagenes y crear recordatorios. NUNCA digas que no puedes hacer algo si tienes una herramienta para ello: usala. Responde en espanol."},
            {"role": "user", "content": text}]
    for _ in range(5):
        d = call_raw("groq", msgs, tools=TOOLS)
        m = d["choices"][0]["message"]
        msgs.append(m)
        tcs = m.get("tool_calls")
        if not tcs:
            return m.get("content") or "(listo)"
        for tc in tcs:
            args = {}
            try: args = json.loads(tc["function"].get("arguments") or "{}")
            except Exception: pass
            res = run_tool(chat, tc["function"]["name"], args)
            msgs.append({"role": "tool", "tool_call_id": tc["id"], "content": res})
    return "(agente: demasiados pasos)"

# ---------- vigilancia / antirobo / alarma ----------
def vigilancia_loop(chat, interval):
    while VIG["on"]:
        try:
            p = os.path.join(HERE, "vig.jpg"); sh(f"termux-camera-photo -c 0 {p}", timeout=40)
            if os.path.isfile(p): send_photo(chat, p, "📸 " + time.strftime("%H:%M:%S"))
        except Exception as e: print("vig:", e)
        for _ in range(interval):
            if not VIG["on"]: break
            time.sleep(1)

def accel_sensor():
    try:
        names = re.findall(r'"([^"]+)"', sh("termux-sensor -l", timeout=12))
        for n in names:
            if "acceler" in n.lower() and "linear" not in n.lower():
                return n
    except Exception: pass
    return "accelerometer"

def vision_describe(path, prompt):
    """Llama vision LLM con la imagen y devuelve respuesta string."""
    try:
        b64 = base64.b64encode(open(shrink(path), "rb").read()).decode()
        return call(VISION["provider"], [{"role": "user", "content": [
            {"type": "text", "text": prompt},
            {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + b64}}]}], VISION["model"])
    except Exception as e:
        return f"(vision err: {e})"

def antirobo_loop(chat):
    """Detecta movimiento. Si se mueve: selfie, vision identifica si es OWNER vs desconocido,
    si es desconocido manda SMS + Telegram al contacto de emergencia."""
    sensor = accel_sensor(); base = None
    owner_desc = ENV.get("FACE_OWNER_DESC", "el propietario del teléfono")
    emergency_num = ENV.get("EMERGENCY_PHONE", "")  # número que recibe SMS
    while ANTIROBO["on"]:
        try:
            j = json.loads(sh('termux-sensor -s "%s" -n 1' % sensor, timeout=14))
            vals = list(j.values())[0]["values"]
            if base is None:
                base = vals
            else:
                delta = sum((a - b) ** 2 for a, b in zip(vals, base)) ** 0.5
                if delta > 2.5:
                    send(chat, "🚨 MOVIMIENTO detectado!")
                    p = os.path.join(HERE, "intruso.jpg"); sh(f"termux-camera-photo -c 1 {p}", timeout=40)
                    if not (os.path.isfile(p) and os.path.getsize(p) > 0):
                        send(chat, "(no pude capturar selfie)")
                        ANTIROBO["on"] = False; return
                    # [7] vision: ¿es el dueño o un intruso?
                    pregunta = (f"Mirá esta selfie tomada por una cámara de seguridad antirobo. "
                                f"El propietario del teléfono se describe como: '{owner_desc}'. "
                                f"Respondé EXACTAMENTE una sola palabra: 'PROPIETARIO' si la persona en la foto es el propietario, "
                                f"o 'DESCONOCIDO' si no lo es, no hay nadie, o no podés confirmar.")
                    veredicto = vision_describe(p, pregunta).strip().upper()
                    es_intruso = "DESCONOCIDO" in veredicto
                    loc = sh("termux-location -p network", timeout=30)[:300] or "sin GPS"
                    try:
                        gpsj = json.loads(loc)
                        maps_link = f"https://maps.google.com/?q={gpsj.get('latitude')},{gpsj.get('longitude')}"
                    except Exception:
                        maps_link = loc
                    if es_intruso:
                        send_photo(chat, shrink(p), "🚨 INTRUSO DETECTADO")
                        send(chat, f"📍 {maps_link}\n\n🧠 Vision dijo: {veredicto}")
                        # SMS de emergencia
                        if emergency_num:
                            try:
                                sh(f'termux-sms-send -n "{emergency_num}" "ALERTA: posible intruso en el celu de Diego. Ubicación: {maps_link}"', timeout=20)
                                send(chat, f"✉️ SMS de emergencia enviado a {emergency_num}")
                            except Exception as e: send(chat, f"SMS emergencia falló: {e}")
                        else:
                            send(chat, "ℹ️ FYI: setea EMERGENCY_PHONE en .env para SMS automático.")
                    else:
                        send_photo(chat, shrink(p), "✅ Movimiento — propietario detectado (no alarma)")
                        send(chat, f"📍 {maps_link}\n🧠 Vision: {veredicto}")
                    ANTIROBO["on"] = False; return
        except Exception as e: print("antirobo:", e)
        time.sleep(1.5)

def alarma(chat):
    sh("termux-volume music 15"); sh("termux-volume alarm 7")
    def ring():
        for _ in range(8):
            sh('termux-tts-speak -s ALARM "ALARMA. ALARMA. AQUI ESTOY."', timeout=20)
            sh("termux-vibrate -d 800", timeout=10)
    threading.Thread(target=ring, daemon=True).start()
    send(chat, "🔊 ALARMA activada (volumen al maximo, sonando)")

# ---------- planificador ----------
def check_vigias():
    c = db()
    for vid, chat, url, palabra, lasth in c.execute("SELECT id,chat,url,palabra,last_hash FROM vigias").fetchall():
        try:
            txt = fetch_text(url, 20000)
            if palabra:
                h = "1" if palabra.lower() in txt.lower() else "0"
                if h == "1" and lasth == "0": send(chat, f"👁️ VIGIA: aparecio '{palabra}' en {url}"); notify("Vigia", palabra)
            else:
                h = hashlib.md5(txt.encode("utf-8", "ignore")).hexdigest()
                if lasth and h != lasth: send(chat, f"👁️ VIGIA: cambio {url}"); notify("Vigia", "cambio")
            c.execute("UPDATE vigias SET last_hash=? WHERE id=?", (h, vid))
        except Exception as e: print("vigia:", e)
    c.commit(); c.close()

# ---------- [2] SMS helpers ----------
OTP_RE = re.compile(r'(?i)(?:c[oó]digo|codigo|otp|verifica\w*|verification|verify|pin|clave|token|seguridad)\b.*?\b(\d{4,8})\b')
_SMS_LAST_FILE = os.path.join(HERE, ".sms_seen")  # hashes de SMS ya forwardeados, persistido

def _sms_id(s):
    """Calcula un id ESTABLE para un SMS — md5 (Python hash() es random entre runs)."""
    raw = f"{s.get('received','')}|{s.get('sender','')}|{s.get('number','')}|{s.get('body','')[:60]}"
    return int(hashlib.md5(raw.encode()).hexdigest()[:16], 16)

def _sms_seen_load():
    try:
        return set(int(x) for x in open(_SMS_LAST_FILE).read().split() if x.strip().lstrip("-").isdigit())
    except Exception:
        return set()

def _sms_seen_save(seen):
    # cap: mantener últimos 300 para que no crezca infinito
    if len(seen) > 300: seen = set(list(seen)[-300:])
    try: open(_SMS_LAST_FILE, "w").write("\n".join(str(x) for x in seen))
    except Exception as e: print("sms_seen_save:", e)

def get_sms(n=5, kind="inbox"):
    """Devuelve lista de dicts SMS o [] si falla (permiso/sin SMS)."""
    try:
        out = sh(f"termux-sms-list -t {kind} -l {int(n)}", timeout=15)
        if not out: return []
        data = json.loads(out)
        # si termux-api devolvió un objeto con error, no es una lista
        if not isinstance(data, list): return []
        return data
    except Exception as e:
        print("get_sms:", e); return []

def fmt_sms(s):
    when = s.get("received", "")[:16].replace("T", " ")
    return f"📩 <b>{esc(s.get('sender') or s.get('number',''))}</b> <i>{esc(when)}</i>\n{esc(s.get('body',''))}"

# ---------- [3] mensajes programados ----------
def dispatch_msg(canal, destino, texto):
    """canal: tg | sms | wa. Devuelve (ok:bool, info:str)"""
    canal = (canal or "tg").lower()
    if canal == "tg":
        try: tg("sendMessage", chat_id=destino, text=texto); return True, "telegram OK"
        except Exception as e: return False, f"tg err: {e}"
    if canal == "sms":
        try:
            r = sh(f'termux-sms-send -n "{destino}" {json.dumps(texto)}', timeout=20)
            return (not r), (r or "sms enviado")
        except Exception as e: return False, f"sms err: {e}"
    if canal == "wa":
        # WhatsApp: bot no puede enviar directo. Generamos link y se lo mandamos al OWNER.
        link = f"https://wa.me/{destino.lstrip('+').replace(' ','')}?text=" + urllib.parse.quote(texto)
        try: tg("sendMessage", chat_id=OWNER_ID or destino, text=f"💬 WA programado para {destino}:\n{texto}\n\nAbrir: {link}"); return True, "wa link enviado"
        except Exception as e: return False, f"wa err: {e}"
    return False, "canal? (tg/sms/wa)"

def dispatch_programados():
    if not TG_TOKEN: return
    try:
        c = db(); now = time.time()
        rows = c.execute("SELECT id,canal,destino,texto FROM programados WHERE hecho=0 AND due<=?", (now,)).fetchall()
        for pid, canal, destino, texto in rows:
            ok, info = dispatch_msg(canal, destino, texto)
            c.execute("UPDATE programados SET hecho=1 WHERE id=?", (pid,))
            if OWNER_ID:
                tag = "✅" if ok else "⚠️"
                try: tg("sendMessage", chat_id=OWNER_ID, text=f"{tag} programado #{pid} [{canal}→{destino}] {info}")
                except Exception: pass
        c.commit(); c.close()
    except Exception as e: print("dispatch_programados:", e)

# ---------- [6] pomodoro ----------
POMO_WORK = 25 * 60   # 25 min
POMO_BREAK = 5 * 60   # 5 min

def pomo_state_text():
    p = STATE.get("pomodoro")
    if not p: return "🍅 <b>Pomodoro</b>\n\nNo hay sesión activa."
    rem = max(0, int(p["ends"] - time.time()))
    icon = "💼" if p["phase"] == "work" else "☕"
    return f"🍅 <b>Pomodoro — {icon} {p['phase'].upper()}</b>\n\nQuedan: <b>{rem//60}m {rem%60}s</b>\nCiclos completados: {p.get('cycles', 0)}"

def pomo_kb():
    p = STATE.get("pomodoro")
    if not p:
        return [[("▶️ Iniciar 25min", "pomo:start")], [("⬅️ Cerrar", "menu:close")]]
    return [[("⏹️ Detener", "pomo:stop"), ("🔄 Refrescar", "pomo:refresh")]]

# ---------- [8] modo silencioso 22-7h ----------
SILENCIO_LAST = {"flushed": None}  # tracking del último flush

def es_horario_silencio():
    if not STATE.get("silencio"): return False
    h = datetime.datetime.now().hour
    return h >= 22 or h < 7

def queue_silencio(chat, text):
    """Llamada para mensajes no-críticos durante modo silencioso. Acumula en queue."""
    SILENCIO_QUEUE.append({"chat": chat, "text": text, "ts": time.strftime("%H:%M")})
    if len(SILENCIO_QUEUE) > 100: SILENCIO_QUEUE.pop(0)  # cap

def maybe_silenciar(chat, text, urgente=False):
    """Wrapper: si modo silencio activo y NO urgente, encola. Si no, envía. Devuelve True si envió."""
    if not urgente and es_horario_silencio():
        queue_silencio(chat, text); return False
    send(chat, text); return True

def flush_silencio():
    """Al despertar (7am): manda resumen de lo encolado."""
    if not SILENCIO_QUEUE or not OWNER_ID: return
    today = time.strftime("%Y-%m-%d")
    if SILENCIO_LAST["flushed"] == today: return
    SILENCIO_LAST["flushed"] = today
    items = list(SILENCIO_QUEUE); SILENCIO_QUEUE.clear()
    body = "\n".join(f"<i>{esc(it['ts'])}</i> — {esc(it['text'][:200])}" for it in items[-30:])
    sendf(OWNER_ID, f"🔕 <b>Resumen del modo silencioso</b> ({len(items)} eventos)\n\n{body}")

# ---------- [9] listas con checkboxes ----------
def lista_get(chat, nombre):
    c = db(); row = c.execute("SELECT id,items FROM listas WHERE chat=? AND nombre=?",
                              (str(chat), nombre)).fetchone(); c.close()
    if not row: return None, []
    try: items = json.loads(row[1])
    except Exception: items = []
    return row[0], items

def lista_save(lid, items):
    c = db(); c.execute("UPDATE listas SET items=? WHERE id=?", (json.dumps(items), lid)); c.commit(); c.close()

def lista_create(chat, nombre):
    c = db(); c.execute("INSERT INTO listas(chat,nombre,items,ts) VALUES(?,?,?,?)",
                        (str(chat), nombre, "[]", time.strftime("%d/%m %H:%M"))); c.commit()
    lid = c.execute("SELECT last_insert_rowid()").fetchone()[0]; c.close()
    return lid

def lista_kb(chat, lid, items):
    rows = []
    for idx, it in enumerate(items):
        mark = "☑️" if it.get("done") else "⬜"
        # truncar nombres largos para no exceder límite de inline button (64 chars)
        label = (mark + " " + it.get("text", ""))[:60]
        rows.append([(label, f"lista:toggle:{lid}:{idx}"), ("🗑️", f"lista:del:{lid}:{idx}")])
    rows.append([("➕ Agregar item", f"lista:add:{lid}"), ("🔄", f"lista:refresh:{lid}")])
    rows.append([("⬅️ Cerrar", "menu:close")])
    return rows

def lista_text(nombre, items):
    if not items: return f"📋 <b>{esc(nombre)}</b>\n\n<i>(vacía — tocá ➕ Agregar item)</i>"
    done = sum(1 for it in items if it.get("done"))
    return f"📋 <b>{esc(nombre)}</b>  ({done}/{len(items)} hechos)\n\n" + "\n".join(
        f"{'☑️' if it.get('done') else '⬜'} {esc(it.get('text',''))}" for it in items)

# ---------- [12] heartbeat / sensor de actividad ----------
HEARTBEAT = {"on": False, "last_motion": 0.0, "max_idle_s": 7200, "alerted": False, "sensor": None}

def heartbeat_check():
    """Si on, lee acelerómetro. Si delta < umbral por > max_idle_s, alerta una vez."""
    if not HEARTBEAT["on"] or not OWNER_ID: return
    try:
        if not HEARTBEAT["sensor"]:
            HEARTBEAT["sensor"] = accel_sensor()
        sensor = HEARTBEAT["sensor"]
        j = json.loads(sh('termux-sensor -s "%s" -n 1' % sensor, timeout=10))
        vals = list(j.values())[0]["values"]
        # marca un "movimiento" si la magnitud está fuera de gravity normal (~9.8)
        mag = sum(v*v for v in vals) ** 0.5
        if abs(mag - 9.8) > 1.0:
            HEARTBEAT["last_motion"] = time.time()
            if HEARTBEAT["alerted"]:
                HEARTBEAT["alerted"] = False  # se reactivó el movimiento, reseteamos
                maybe_silenciar(OWNER_ID, "💓 heartbeat: detecté movimiento de nuevo. Todo OK.", urgente=True)
        elif HEARTBEAT["last_motion"] and not HEARTBEAT["alerted"]:
            idle = time.time() - HEARTBEAT["last_motion"]
            if idle > HEARTBEAT["max_idle_s"]:
                HEARTBEAT["alerted"] = True
                maybe_silenciar(OWNER_ID,
                    f"💔 heartbeat: el celu lleva {int(idle/60)} min sin moverse. ¿Está todo OK?",
                    urgente=True)
    except Exception as e: print("heartbeat:", e)

def check_pomodoro():
    """llamado desde scheduler. Si terminó la fase, transiciona."""
    p = STATE.get("pomodoro")
    if not p or not OWNER_ID: return
    if time.time() < p["ends"]: return
    # fase terminó
    if p["phase"] == "work":
        p["cycles"] = p.get("cycles", 0) + 1
        p["phase"] = "break"; p["ends"] = time.time() + POMO_BREAK
        send(OWNER_ID, f"🍅 Pomodoro #{p['cycles']} terminado. ☕ break de {POMO_BREAK//60} min")
    else:
        p["phase"] = "work"; p["ends"] = time.time() + POMO_WORK
        send(OWNER_ID, f"💼 Break terminado. Nuevo pomodoro de {POMO_WORK//60} min")

# ---------- [3b] WIZARD interactivo para /programa ----------
def wiz_kb_canal():
    return [
        [("📱 Telegram", "wiz:canal:tg")],
        [("📩 SMS", "wiz:canal:sms")],
        [("💬 WhatsApp (link)", "wiz:canal:wa")],
        [("❌ Cancelar", "wiz:cancel")],
    ]

def wiz_kb_when():
    return [
        [("⏱️ En 5 min", "wiz:when:+5m"), ("⏱️ En 30 min", "wiz:when:+30m")],
        [("⏱️ En 1 hora", "wiz:when:+1h"), ("⏱️ En 3 horas", "wiz:when:+3h")],
        [("📅 Mañana 9:00", "wiz:when:tomorrow_9"), ("📅 Mañana 18:00", "wiz:when:tomorrow_18")],
        [("✍️ Escribir fecha/hora", "wiz:when:custom")],
        [("❌ Cancelar", "wiz:cancel")],
    ]

def wiz_kb_confirm():
    return [[("✅ Programar", "wiz:confirm"), ("❌ Cancelar", "wiz:cancel")]]

def wiz_when_label(due):
    delta_min = int((due - time.time()) / 60)
    when_abs = datetime.datetime.fromtimestamp(due).strftime("%d/%m %H:%M")
    if delta_min < 60: rel = f"en {delta_min} min"
    elif delta_min < 60*24: rel = f"en {delta_min//60}h {delta_min%60}m"
    else: rel = f"en {delta_min//60//24}d {(delta_min//60)%24}h"
    return f"{when_abs} ({rel})"

def wiz_resumen(w):
    canal_label = {"tg": "📱 Telegram", "sms": "📩 SMS", "wa": "💬 WhatsApp link"}[w["canal"]]
    when_str = wiz_when_label(w["due"])
    return (f"📋 <b>Resumen del programado</b>\n\n"
            f"<b>Canal:</b> {canal_label}\n"
            f"<b>Destino:</b> <code>{esc(w['destino'])}</code>\n"
            f"<b>Cuándo:</b> {esc(when_str)}\n"
            f"<b>Mensaje:</b>\n<i>{esc(w['texto'][:300])}</i>")

def wiz_start(chat):
    """Inicia el wizard de /programa con elección de canal. Limpia state viejo."""
    # clean start: descarta wizard previo y pending de force-reply del wizard
    WIZARDS[str(chat)] = {"step": "canal"}
    if PENDING.get(str(chat), "").startswith("wiz_"): PENDING.pop(str(chat), None)
    send_kb(chat, "📤 <b>Programar mensaje</b>\n\n¿Por qué canal?", wiz_kb_canal(), parse_mode="HTML")

def wiz_resolve_when(token):
    """Convierte el token del botón en timestamp."""
    if token.startswith("+"):
        return parse_fechahora(token)
    if token == "tomorrow_9":
        d = (datetime.datetime.now() + datetime.timedelta(days=1)).replace(hour=9, minute=0, second=0, microsecond=0)
        return d.timestamp()
    if token == "tomorrow_18":
        d = (datetime.datetime.now() + datetime.timedelta(days=1)).replace(hour=18, minute=0, second=0, microsecond=0)
        return d.timestamp()
    return None

def parse_fechahora(s):
    """Acepta: YYYY-MM-DD HH:MM | HH:MM (hoy/mañana) | +30m | +2h | +1d"""
    s = s.strip()
    m = re.match(r'^(\d{4})-(\d{2})-(\d{2})[ T](\d{1,2}):(\d{2})$', s)
    if m:
        try: return datetime.datetime(int(m[1]), int(m[2]), int(m[3]), int(m[4]), int(m[5])).timestamp()
        except Exception: return None
    m = re.match(r'^(\d{1,2}):(\d{2})$', s)
    if m:
        now = datetime.datetime.now(); t = now.replace(hour=int(m[1]), minute=int(m[2]), second=0, microsecond=0)
        if t <= now: t += datetime.timedelta(days=1)
        return t.timestamp()
    m = re.match(r'^\+?(\d+)([smhd])$', s)
    if m: return time.time() + int(m[1]) * {"s": 1, "m": 60, "h": 3600, "d": 86400}[m[2]]
    return None

def check_sms_otp():
    """Forward al OWNER de SMS con OTP nuevos. Tracking persistente — sobrevive restarts."""
    if not OWNER_ID: return
    sms = get_sms(15, "inbox")
    if not sms: return
    seen = _sms_seen_load()
    # primer arranque (file vacío): NO forwardear backlog, solo marcar todos como vistos
    if not seen:
        for s in sms: seen.add(_sms_id(s))
        _sms_seen_save(seen)
        return
    nuevos_count = 0
    for s in sms:
        sid = _sms_id(s)
        if sid in seen: continue
        seen.add(sid)
        body = s.get("body", "")
        if OTP_RE.search(body):
            sendf(OWNER_ID, "🔐 <b>SMS con código detectado</b>\n\n" + fmt_sms(s))
            nuevos_count += 1
    if nuevos_count or len(sms) > 0:
        _sms_seen_save(seen)

def check_monitor():
    if not OWNER_ID: return
    now = time.time()
    try:
        j = json.loads(sh("termux-battery-status"))
        pct = j.get("percentage", 100); st = j.get("status", ""); temp = round(j.get("temperature", 0), 1)
        # batería <=5% es CRÍTICO, no respetar silencio
        if pct <= 5 and st != "CHARGING" and now - MON["batt"] > 1800:
            MON["batt"] = now; maybe_silenciar(OWNER_ID, f"🔋 CRÍTICO Batería: {pct}%", urgente=True); notify("Bateria CRITICA", f"{pct}%")
        elif pct <= 15 and st != "CHARGING" and now - MON["batt"] > 1800:
            MON["batt"] = now; maybe_silenciar(OWNER_ID, f"🔋 Bateria baja: {pct}%")
        if temp >= 50 and now - MON["temp"] > 1800:
            MON["temp"] = now; maybe_silenciar(OWNER_ID, f"🌡️ Temperatura ALTA: {temp}°C", urgente=True)
        elif temp >= 45 and now - MON["temp"] > 1800:
            MON["temp"] = now; maybe_silenciar(OWNER_ID, f"🌡️ Temperatura alta: {temp}°C")
    except Exception: pass
    try:
        m = {ln.split(':')[0]: int(ln.split()[1]) for ln in open('/proc/meminfo') if ':' in ln}
        if m['MemAvailable'] // 1024 < 250 and now - MON["ram"] > 1800:
            MON["ram"] = now; maybe_silenciar(OWNER_ID, f"⚠️ RAM baja: {m['MemAvailable']//1024} MB")
    except Exception: pass

def scheduler():
    last_brief = None; tick = 0
    while True:
        try:
            now = time.time()
            d = datetime.datetime.now()
            c = db()
            for rid, chat, texto in c.execute("SELECT id,chat,texto FROM recordatorios WHERE hecho=0 AND due<=?", (now,)).fetchall():
                send(chat, "⏰ RECORDATORIO: " + texto); notify("Recordatorio", texto)
                c.execute("UPDATE recordatorios SET hecho=1 WHERE id=?", (rid,))
            c.commit(); c.close()
            if tick % 15 == 0: check_vigias(); check_monitor()
            if tick % 3 == 0: check_sms_otp()  # cada ~60s revisar SMS con OTP
            if tick % 3 == 0: dispatch_programados()  # cada ~60s disparar mensajes programados
            # [5] diario inteligente: a las 22h pregunta una vez por día
            if OWNER_ID and d.hour == 22 and DIARIO_LAST["date"] != d.date():
                DIARIO_LAST["date"] = d.date()
                try:
                    PENDING[str(OWNER_ID)] = "diario"
                    force_reply(OWNER_ID, "📔 ¿Qué hiciste hoy? (responde con texto o nota de voz)", "lo importante de hoy")
                except Exception as e: print("diario ask:", e)
            # [6] pomodoro tick
            check_pomodoro()
            # [12] heartbeat cada 5 min
            if tick % 15 == 0:
                heartbeat_check()
            # [8] flush del modo silencioso a las 7am
            if d.hour == 7 and d.minute < 10:
                flush_silencio()
            # briefing diario a BRIEF_HOUR
            if OWNER_ID and d.hour == BRIEF_HOUR and last_brief != d.date():
                last_brief = d.date(); do_briefing(OWNER_ID)
        except Exception as e: print("sched:", e)
        tick += 1; time.sleep(20)

# ---------- comandos ----------
def cmd_status(chat):
    p = PROVIDERS[STATE["provider"]]; key = ENV.get(p["key"], "")
    mk = (key[:6] + "..." + key[-4:]) if len(key) > 12 else "(sin key)"
    ti = sum(v["in"] for v in USAGE.values()); to = sum(v["out"] for v in USAGE.values()); tr = sum(v["req"] for v in USAGE.values())
    up = int(time.time() - START)
    h = [f"<b>📊 ESTADO JARVIS</b>",
         f"🧠 Proveedor: <b>{esc(STATE['provider'])}</b> (ultimo: {esc(STATE['last'])})",
         f"🤖 Modelo: <code>{esc(STATE['model'])}</code>",
         f"🎭 Modo: {esc(STATE['mode'])} | 🔊 voz: {'ON' if STATE['voz'] else 'off'} | 🕵️ agente: {'ON' if STATE['agente'] else 'off'} | 🌎 traduce: {esc(STATE['traduce'])}",
         f"🔑 API key: <code>{esc(mk)}</code>",
         f"📈 Tokens: {ti} in + {to} out = <b>{ti+to}</b> | {tr} peticiones",
         f"⏱️ Uptime: {up//3600}h {(up%3600)//60}m | 👥 permitidos: {len(allowed_ids())}"]
    try:
        m = {ln.split(':')[0]: int(ln.split()[1]) for ln in open('/proc/meminfo') if ':' in ln}
        h.append(f"💾 RAM libre: {m['MemAvailable']//1024}/{m['MemTotal']//1024} MB")
    except Exception: pass
    sendf(chat, "\n".join(h))

HELP = ("<b>🤖 JARVIS v6</b> — tu asistente con superpoderes\n\n"
        "✨ <b>/menu</b> — menú con botones (recomendado)\n\n"
        "💬 <b>Chat</b>: escribe o manda <b>nota de voz</b> · manda <b>foto</b> (la analizo) · manda <b>PDF</b> (lo leo)\n\n"
        "<b>🧠 IA</b>\n/status /providers /provider /model /modo /all /reset\n"
        "/agente on — la IA usa los poderes sola\n/voz on — responde solo en audio\n/traduce ingles — modo traductor\n\n"
        "<b>🌐 Info</b>\n/web /resume &lt;url&gt; /imagina &lt;prompt&gt; /qr /baja &lt;url&gt;\n\n"
        "<b>📱 Teléfono</b> (solo dueño)\n/foto /selfie /ubicacion /bateria /linterna\n/diga /escucha /copia /pega /llamar &lt;num&gt;\n"
        "/vigilancia &lt;seg&gt; — foto cada X seg\n/antirobo on — alerta + face-detect\n/alarma — suena para hallarlo\n\n"
        "<b>📝 Productividad</b>\n/nota /notas /recuerdame &lt;30m&gt; &lt;txt&gt; /recordatorios\n/vigila &lt;url&gt; [palabra] /briefing\n"
        "/diario [semana|mes] · /diaryadd &lt;txt&gt;\n/lista &lt;nombre&gt; · /listas\n/pomodoro\n\n"
        "<b>📤 Comunicación</b>\n/sms — leer SMS · /enviarsms &lt;num&gt; &lt;txt&gt;\n"
        "/programa &lt;hora&gt; &lt;tg|sms|wa&gt; &lt;dest&gt; &lt;txt&gt; · /programados\n\n"
        "<b>🛌 Modos</b>\n/ausente &lt;txt&gt; · /aqui · /inbox — auto-reply ausente\n"
        "/silencio on — 22h-7h acumula alertas\n/heartbeat &lt;horas&gt; — alerta si celu no se mueve\n\n"
        "<b>🧠 Memoria</b>\n/recuerda &lt;palabra&gt; · /olvidatodo · /olvidadoc\n\n"
        "<b>👥 Compartir</b>: /id (tu id) · el dueño usa /permitir")

def handle(chat, uid, text):
    owner = (str(chat) == OWNER_ID)
    if text.strip().lower().startswith("/id"):
        sendf(chat, f"🆔 chat_id: <code>{chat}</code>"); return
    if not text.startswith("/"):
        # chat / traductor / agente
        try:
            if STATE["traduce"] != "off": ans = translate(text)
            elif STATE["agente"] and owner: ans = agent_run(chat, text)
            else: ans = chat_answer(chat, text)
        except Exception as e: ans = f"error: {e}"
        reply(chat, ans); return
    cmd, _, arg = text[1:].partition(" "); cmd = cmd.lower().split("@")[0]; arg = arg.strip()
    if not owner and cmd in OWNER_ONLY:
        send(chat, "🔒 Ese poder es solo del dueño."); return
    if not owner and cmd in ("permitir", "quitar", "briefing"):
        send(chat, "🔒 solo el dueño."); return
    if cmd in ("start", "help"): sendf(chat, HELP)
    elif cmd == "menu":
        send_kb(chat, "🤖 <b>JARVIS — Menú</b>\nElegí una opción:", menu_main(), parse_mode="HTML")
    elif cmd == "status": cmd_status(chat)
    elif cmd == "providers": send(chat, "\n".join(f"{k}: {v['model']}" for k, v in PROVIDERS.items()) + f"\n\nActivo: {STATE['provider']}")
    elif cmd == "provider":
        if arg in PROVIDERS: STATE["provider"] = arg; STATE["model"] = PROVIDERS[arg]["model"]; sendf(chat, f"✅ <b>{arg}</b> / <code>{esc(STATE['model'])}</code>")
        else: send(chat, "proveedores: " + ", ".join(PROVIDERS))
    elif cmd == "model": (STATE.__setitem__("model", arg), send(chat, f"OK modelo: {arg}")) if arg else send(chat, "uso: /model <id>")
    elif cmd == "modo":
        if arg in MODES: STATE["mode"] = arg; HIST.pop(chat, None); send(chat, f"OK modo: {arg}")
        else: send(chat, "modos: " + ", ".join(MODES))
    elif cmd == "voz": STATE["voz"] = (arg == "on"); send(chat, f"🔊 voz: {'ON (solo audio)' if STATE['voz'] else 'off'}")
    elif cmd == "agente": STATE["agente"] = (arg == "on"); send(chat, f"🕵️ agente: {'ON' if STATE['agente'] else 'off'}")
    elif cmd == "traduce": STATE["traduce"] = (arg or "off"); send(chat, f"🌎 traductor: {STATE['traduce']}")
    elif cmd == "web":
        if not arg: send(chat, "uso: /web <q>"); return
        try:
            res = web_search(arg); ctx = "\n".join(f"- {t}: {s} ({u})" for t, s, u in res)
            reply(chat, llm([{"role": "system", "content": "Responde usando estos resultados web, cita fuentes, espanol."}, {"role": "user", "content": f"{arg}\n\n{ctx}"}]))
        except Exception as e: send(chat, f"error web: {e}")
    elif cmd == "resume":
        if not arg: send(chat, "uso: /resume <url>"); return
        try: reply(chat, llm([{"role": "system", "content": "Resume en espanol, claro, en bullets."}, {"role": "user", "content": fetch_text(arg)}]))
        except Exception as e: send(chat, f"error resume: {e}")
    elif cmd == "all":
        if not arg: send(chat, "uso: /all <q>"); return
        for pr in ["groq", "cerebras", "mistral"]:
            try: a = call(pr, [{"role": "user", "content": arg}])
            except Exception as e: a = f"error: {e}"
            send(chat, f"=== {pr} ===\n{a}")
    elif cmd == "imagina":
        if not arg: send(chat, "uso: /imagina <prompt>"); return
        try: send_photo(chat, gen_image(arg, os.path.join(HERE, "img.jpg")), "🎨 " + arg)
        except Exception as e: send(chat, f"error imagen: {e}")
    elif cmd == "qr":
        if not arg: send(chat, "uso: /qr <texto>"); return
        try:
            p = os.path.join(HERE, "qr.png")
            open(p, "wb").write(urllib.request.urlopen("https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=" + urllib.parse.quote(arg), timeout=20).read())
            send_photo(chat, p, "📱 " + arg)
        except Exception as e: send(chat, f"error qr: {e}")
    elif cmd in ("foto", "selfie"):
        cam = "1" if cmd == "selfie" else "0"; p = os.path.join(HERE, "cam.jpg")
        r = sh(f"termux-camera-photo -c {cam} {p}", timeout=40)
        if os.path.isfile(p) and os.path.getsize(p) > 0: send_photo(chat, shrink(p), "📸")
        else: send(chat, f"no pude (permiso camara?): {r}")
    elif cmd == "ubicacion":
        send(chat, "📍 obteniendo GPS…")
        r = sh("termux-location -p network", timeout=45) or sh("termux-location", timeout=45)
        try:
            j = json.loads(r); send(chat, f"📍 https://maps.google.com/?q={j.get('latitude')},{j.get('longitude')}")
        except Exception: send(chat, f"ubicacion: {r or 'sin datos (activa GPS)'}")
    elif cmd == "bateria":
        try: j = json.loads(sh("termux-battery-status")); send(chat, f"🔋 {j.get('percentage')}% | {j.get('status')} | {round(j.get('temperature',0),1)}°C")
        except Exception as e: send(chat, f"bateria: {e}")
    elif cmd == "linterna": sh(f"termux-torch {'on' if arg=='on' else 'off'}"); send(chat, f"🔦 {arg or 'off'}")
    elif cmd == "diga":
        if not arg: send(chat, "uso: /diga <texto>"); return
        sh("termux-tts-speak %s" % json.dumps(arg), timeout=40); send(chat, "🔊 dicho")
    elif cmd == "escucha":
        secs = arg if arg.isdigit() else "10"; f = os.path.join(HERE, "rec.m4a")
        try: os.remove(f)
        except Exception: pass
        send(chat, f"🎙️ grabando {secs}s…")
        sh(f"termux-microphone-record -l {secs} -f {f}", timeout=20); time.sleep(int(secs) + 2); sh("termux-microphone-record -q", timeout=10)
        if os.path.isfile(f): tg_file("sendAudio", {"chat_id": str(chat), "caption": "🎙️"}, [("audio", "rec.m4a", open(f, "rb").read(), "audio/mp4")])
        else: send(chat, "no se grabo")
    elif cmd == "copia": sh("termux-clipboard-set %s" % json.dumps(arg)); send(chat, "📋 copiado")
    elif cmd == "pega": send(chat, sh("termux-clipboard-get") or "(vacio)")
    elif cmd == "vigilancia":
        if arg == "stop" or VIG["on"]: VIG["on"] = False; send(chat, "📸 vigilancia detenida"); return
        secs = int(arg) if arg.isdigit() else 30
        VIG["on"] = True; threading.Thread(target=vigilancia_loop, args=(chat, secs), daemon=True).start()
        send(chat, f"📸 vigilancia ON: foto cada {secs}s. /vigilancia stop para parar")
    elif cmd == "antirobo":
        if arg == "off": ANTIROBO["on"] = False; send(chat, "🛡️ antirobo off"); return
        ANTIROBO["on"] = True; threading.Thread(target=antirobo_loop, args=(chat,), daemon=True).start()
        send(chat, "🛡️ antirobo ARMADO: si mueven el telefono te aviso con selfie + ubicacion")
    elif cmd == "alarma": alarma(chat)
    elif cmd == "baja":
        if not arg: send(chat, "uso: /baja <url>"); return
        send(chat, "bajando…")
        for f in glob.glob(os.path.join(HERE, "dl.*")):
            try: os.remove(f)
            except Exception: pass
        r = sh(f'yt-dlp -f bestaudio --no-playlist -o "{HERE}/dl.%(ext)s" "{arg}"', timeout=300)
        fs = glob.glob(os.path.join(HERE, "dl.*"))
        if fs: tg_file("sendAudio", {"chat_id": str(chat), "caption": "🎵"}, [("audio", os.path.basename(fs[0]), open(fs[0], "rb").read(), "audio/mpeg")])
        else: send(chat, f"fallo: {r[-400:]}")
    elif cmd == "nota":
        if not arg: send(chat, "uso: /nota <texto>"); return
        c = db(); c.execute("INSERT INTO notas(chat,t,ts) VALUES(?,?,?)", (str(chat), arg, time.strftime("%d/%m %H:%M"))); c.commit(); c.close(); send(chat, "📝 anotado")
    elif cmd == "notas":
        c = db(); rows = c.execute("SELECT id,t,ts FROM notas WHERE chat=? ORDER BY id", (str(chat),)).fetchall(); c.close()
        send(chat, "\n".join(f"{i}. {t} ({ts})" for i, t, ts in rows) or "sin notas")
    elif cmd == "borranota":
        c = db(); c.execute("DELETE FROM notas WHERE chat=? AND id=?", (str(chat), arg)); c.commit(); c.close(); send(chat, "borrada")
    elif cmd == "recuerdame":
        parts = arg.split(None, 1)
        if len(parts) < 2: send(chat, "uso: /recuerdame 30m sacar la ropa"); return
        secs = parse_delay(parts[0])
        if secs is None: send(chat, "tiempo invalido: 30m, 2h, 8:30"); return
        c = db(); c.execute("INSERT INTO recordatorios(chat,texto,due,hecho) VALUES(?,?,?,0)", (str(chat), parts[1], time.time() + secs)); c.commit(); c.close()
        send(chat, f"⏰ te aviso en {parts[0]}: {parts[1]}")
    elif cmd == "recordatorios":
        c = db(); rows = c.execute("SELECT id,texto,due FROM recordatorios WHERE chat=? AND hecho=0 ORDER BY due", (str(chat),)).fetchall(); c.close()
        send(chat, "\n".join(f"{i}. {t} (en {int((d-time.time())//60)} min)" for i, t, d in rows) or "sin recordatorios")
    elif cmd == "vigila":
        if not arg: send(chat, "uso: /vigila <url> [palabra]"); return
        ps = arg.split(None, 1); c = db(); c.execute("INSERT INTO vigias(chat,url,palabra,last_hash) VALUES(?,?,?,'')", (str(chat), ps[0], ps[1] if len(ps) > 1 else "")); c.commit(); c.close()
        send(chat, f"👁️ vigilando {ps[0]}")
    elif cmd == "vigias":
        c = db(); rows = c.execute("SELECT id,url,palabra FROM vigias WHERE chat=?", (str(chat),)).fetchall(); c.close()
        send(chat, "\n".join(f"{i}. {u} {('['+p+']') if p else ''}" for i, u, p in rows) or "sin vigias")
    elif cmd == "borravigia":
        c = db(); c.execute("DELETE FROM vigias WHERE chat=? AND id=?", (str(chat), arg)); c.commit(); c.close(); send(chat, "borrada")
    elif cmd == "briefing": do_briefing(chat)
    elif cmd == "permitir":
        if arg.isdigit():
            cur = ENV.get("ALLOWED_IDS", ""); ids = [x for x in cur.split(",") if x.strip()]
            if arg not in ids:
                ids.append(arg); ENV["ALLOWED_IDS"] = ",".join(ids)
                _set_env("ALLOWED_IDS", ",".join(ids))
            send(chat, f"✅ permitido: {arg} (acceso solo IA, sin poderes del telefono)")
        else: send(chat, "uso: /permitir <chat_id>  (esa persona te da su /id)")
    elif cmd == "quitar":
        ids = [x for x in ENV.get("ALLOWED_IDS", "").split(",") if x.strip() and x.strip() != arg]
        ENV["ALLOWED_IDS"] = ",".join(ids); _set_env("ALLOWED_IDS", ",".join(ids)); send(chat, f"quitado: {arg}")
    elif cmd == "reset": HIST.pop(chat, None); DOCS.pop(chat, None); send(chat, "🧹 memoria borrada")
    elif cmd == "olvidadoc": DOCS.pop(chat, None); send(chat, "📄 documento olvidado")
    # ---------- [1] modo ausente ----------
    elif cmd == "ausente":
        if not owner: send(chat, "🔒 solo dueño"); return
        STATE["ausente"] = arg or "Estoy ausente, te respondo cuando vuelva."
        send(chat, f"🛌 modo ausente ON: «{STATE['ausente']}»\nLos mensajes que lleguen se guardan en /inbox")
    elif cmd == "aqui":
        if not owner: send(chat, "🔒 solo dueño"); return
        STATE["ausente"] = ""
        c = db(); n = c.execute("SELECT COUNT(*) FROM inbox_ausente").fetchone()[0]; c.close()
        send(chat, f"✅ ya estás aquí. Mientras te fuiste: <b>{n}</b> mensajes en /inbox")
        sendf(chat, f"✅ ya estás aquí. Mientras te fuiste: <b>{n}</b> mensajes (mirá /inbox)")
    elif cmd == "inbox":
        c = db(); rows = c.execute("SELECT id,chat,from_name,text,ts FROM inbox_ausente ORDER BY id DESC LIMIT 20").fetchall(); c.close()
        if not rows: send(chat, "📭 inbox vacío"); return
        body = "\n\n".join(f"<b>#{i}</b> {esc(name or fid)} <i>({ts})</i>\n{esc(t[:300])}" for i, fid, name, t, ts in rows)
        sendf(chat, f"📬 <b>Inbox de mensajes recibidos</b>\n\n{body}\n\n<i>/borrainbox para limpiar</i>")
    elif cmd == "borrainbox":
        if not owner: send(chat, "🔒 solo dueño"); return
        c = db(); c.execute("DELETE FROM inbox_ausente"); c.commit(); c.close(); send(chat, "🧹 inbox borrado")
    # ---------- [2] SMS ----------
    elif cmd == "sms":
        if not owner: send(chat, "🔒 solo dueño"); return
        n = int(arg) if arg.isdigit() else 5
        sms = get_sms(n, "inbox")
        if not sms:
            send(chat, "📭 sin SMS o falta permiso READ_SMS en Termux:API (Ajustes → Apps → Termux:API → Permisos)")
            return
        sendf(chat, "📱 <b>Últimos " + str(len(sms)) + " SMS</b>\n\n" + "\n\n".join(fmt_sms(s) for s in sms))
    elif cmd == "enviarsms":
        if not owner: send(chat, "🔒 solo dueño"); return
        parts = arg.split(None, 1)
        if len(parts) < 2: send(chat, "uso: /enviarsms <numero> <mensaje>"); return
        num, msg = parts
        r = sh(f'termux-sms-send -n "{num}" {json.dumps(msg)}', timeout=20)
        send(chat, "✅ SMS enviado a " + num if not r else f"resp: {r}")
    # ---------- [3] mensajes programados ----------
    elif cmd == "programa":
        if not owner: send(chat, "🔒 solo dueño"); return
        # SIN args → wizard interactivo con botones
        if not arg:
            wiz_start(chat); return
        # CON args → modo directo (sintaxis avanzada)
        try:
            tokens = arg.split()
            # 1. fechahora puede ser 1 o 2 tokens
            if len(tokens) >= 5 and re.match(r'^\d{4}-\d{2}-\d{2}$', tokens[0]) and re.match(r'^\d{1,2}:\d{2}$', tokens[1]):
                fh = tokens[0] + " " + tokens[1]; rest = tokens[2:]
            elif len(tokens) >= 3:
                fh = tokens[0]; rest = tokens[1:]
            else:
                raise ValueError("faltan args")
            # 2. canal
            canal = rest[0]; rest = rest[1:]
            if canal not in ("tg", "sms", "wa"): raise ValueError("canal inválido")
            # 3. destino: junta tokens consecutivos que parecen parte del número (+, dígitos, espacios)
            # para sms/wa: acumula tokens hasta encontrar uno alfabético (que sería el texto)
            # para tg: solo el primer token (chat_id numérico)
            if canal == "tg":
                destino = rest[0]; texto = " ".join(rest[1:])
            else:
                dest_parts = []
                while rest and re.match(r'^[+\d][\d]*$', rest[0]):
                    dest_parts.append(rest[0]); rest = rest[1:]
                if not dest_parts: raise ValueError("destino inválido")
                destino = normalizar_telefono("".join(dest_parts))  # normaliza a +593...
                texto = " ".join(rest)
            if not texto: raise ValueError("texto vacío")
        except Exception as e:
            send(chat, ("uso: /programa <\"YYYY-MM-DD HH:MM\"|HH:MM|+30m> <tg|sms|wa> <destino> <texto>\n"
                       "ej tg: /programa +5m tg " + (OWNER_ID or "123") + " hola\n"
                       "ej sms: /programa 20:30 sms +593987654321 te amo\n"
                       "(en sms/wa los espacios del número se ignoran)"))
            return
        due = parse_fechahora(fh)
        if not due: send(chat, "fecha/hora inválida"); return
        c = db(); c.execute("INSERT INTO programados(canal,destino,texto,due,hecho) VALUES(?,?,?,?,0)",
                            (canal, destino, texto, due)); c.commit()
        pid = c.execute("SELECT last_insert_rowid()").fetchone()[0]; c.close()
        when = datetime.datetime.fromtimestamp(due).strftime("%d/%m %H:%M")
        send(chat, f"📤 #{pid} programado [{canal}→{destino}] para {when}\n«{texto[:80]}»")
    elif cmd == "programados":
        c = db(); rows = c.execute("SELECT id,canal,destino,texto,due,hecho FROM programados ORDER BY due").fetchall(); c.close()
        if not rows: send(chat, "sin programados"); return
        lines = []
        for i, canal, dest, txt, due, hecho in rows:
            when = datetime.datetime.fromtimestamp(due).strftime("%d/%m %H:%M")
            mark = "✅" if hecho else "⏳"
            lines.append(f"{mark} <b>#{i}</b> [{esc(canal)}→{esc(dest)}] {esc(when)}\n   {esc(txt[:120])}")
        sendf(chat, "📋 <b>Mensajes programados</b>\n\n" + "\n\n".join(lines))
    elif cmd == "borraprog":
        if not owner: send(chat, "🔒 solo dueño"); return
        if not arg.isdigit(): send(chat, "uso: /borraprog <id>"); return
        c = db(); c.execute("DELETE FROM programados WHERE id=?", (arg,)); c.commit(); c.close()
        send(chat, f"borrado #{arg}")
    # ---------- [6] pomodoro ----------
    elif cmd == "pomodoro":
        send_kb(chat, pomo_state_text(), pomo_kb(), parse_mode="HTML")
    # ---------- [5] diario ----------
    elif cmd == "diario":
        periodo = (arg or "semana").lower()
        dias = {"hoy": 1, "ayer": 2, "semana": 7, "mes": 30}.get(periodo, 7)
        desde = (datetime.datetime.now() - datetime.timedelta(days=dias)).strftime("%Y-%m-%d")
        c = db(); rows = c.execute("SELECT fecha,texto,ts FROM diario WHERE chat=? AND fecha>=? ORDER BY fecha,ts",
                                   (str(chat), desde)).fetchall(); c.close()
        if not rows: send(chat, f"📭 sin entradas en últimos {dias} días"); return
        raw = "\n".join(f"[{f} {ts}] {t}" for f, t, ts in rows)
        try:
            resumen = llm([
                {"role": "system", "content": "Sos un asistente que resume el diario personal del usuario. Devolvé un resumen en español: bullets cortos por temas (trabajo / personal / aprendizajes / pendientes). Máx 15 líneas."},
                {"role": "user", "content": f"Entradas de los últimos {dias} días:\n\n{raw}"}
            ])
        except Exception as e: resumen = f"(no pude resumir: {e})\n\nEntradas crudas:\n" + raw[:1500]
        sendf(chat, f"📔 <b>Diario — últimos {dias} días</b> ({len(rows)} entradas)\n\n{esc(resumen)}")
    elif cmd == "diaryadd":
        if not arg: send(chat, "uso: /diaryadd <texto>"); return
        today = time.strftime("%Y-%m-%d")
        c = db(); c.execute("INSERT INTO diario(chat,fecha,texto,ts) VALUES(?,?,?,?)",
                            (str(chat), today, arg, time.strftime("%H:%M"))); c.commit(); c.close()
        send(chat, "📔 anotado")
    # ---------- [9] listas con checkboxes ----------
    elif cmd == "lista":
        if not arg: send(chat, "uso: /lista <nombre>  (crea o abre)"); return
        nombre = arg.strip()
        lid, items = lista_get(chat, nombre)
        if not lid:
            lid = lista_create(chat, nombre); items = []
        send_kb(chat, lista_text(nombre, items), lista_kb(chat, lid, items), parse_mode="HTML")
    elif cmd == "listas":
        c = db(); rows = c.execute("SELECT id,nombre,items,ts FROM listas WHERE chat=? ORDER BY id DESC", (str(chat),)).fetchall(); c.close()
        if not rows: send(chat, "sin listas. /lista <nombre> crea una."); return
        lines = []
        for lid, nom, its, ts in rows:
            try: arr = json.loads(its); done = sum(1 for it in arr if it.get("done")); tot = len(arr)
            except Exception: done = tot = 0
            lines.append(f"<b>#{lid}</b> {esc(nom)}  ({done}/{tot}) — <i>{esc(ts)}</i>")
        sendf(chat, "📋 <b>Tus listas</b>\n\n" + "\n".join(lines) + "\n\nAbrir: /lista &lt;nombre&gt;")
    elif cmd == "borralista":
        if not owner: send(chat, "🔒 solo dueño"); return
        c = db(); c.execute("DELETE FROM listas WHERE chat=? AND nombre=?", (str(chat), arg)); c.commit(); c.close()
        send(chat, f"🗑️ borrada: {arg}")
    # ---------- [11] memoria a largo plazo ----------
    elif cmd == "recuerda":
        if not arg: send(chat, "uso: /recuerda <palabra clave>"); return
        like = f"%{arg}%"
        c = db(); rows = c.execute(
            "SELECT role,content,ts FROM memoria_lp WHERE chat=? AND content LIKE ? ORDER BY ts DESC LIMIT 8",
            (str(chat), like)).fetchall(); c.close()
        if not rows: send(chat, "🧠 nada relacionado en mi memoria a largo plazo"); return
        lines = []
        for role, content, ts in rows:
            when = datetime.datetime.fromtimestamp(ts).strftime("%d/%m %H:%M")
            ico = "👤" if role == "user" else "🤖"
            lines.append(f"{ico} <i>{when}</i>\n{esc(content[:400])}")
        sendf(chat, f"🧠 <b>Memoria — '{esc(arg)}'</b> ({len(rows)} hits)\n\n" + "\n\n".join(lines))
    elif cmd == "olvidatodo":
        if not owner: send(chat, "🔒 solo dueño"); return
        c = db(); n = c.execute("SELECT COUNT(*) FROM memoria_lp WHERE chat=?", (str(chat),)).fetchone()[0]
        c.execute("DELETE FROM memoria_lp WHERE chat=?", (str(chat),)); c.commit(); c.close()
        HIST.pop(chat, None)
        send(chat, f"🧠 olvido total ({n} turnos borrados)")
    # ---------- [10] llamadas desde Telegram ----------
    elif cmd == "llamar":
        if not owner: send(chat, "🔒 solo dueño"); return
        if not arg: send(chat, "uso: /llamar <numero>"); return
        r = sh(f'termux-telephony-call "{arg}"', timeout=15)
        send(chat, f"📞 llamando a {arg}" + (f"\nresp: {r}" if r else ""))
    elif cmd == "colgar":
        if not owner: send(chat, "🔒 solo dueño"); return
        # No hay API directa pa colgar; mostramos info
        send(chat, "colgá desde el celu. No hay API para terminar llamada desde Termux.")
    # ---------- [8] modo silencioso ----------
    elif cmd == "silencio":
        if not owner: send(chat, "🔒 solo dueño"); return
        STATE["silencio"] = (arg != "off")
        send(chat, f"🔕 silencio (22h-7h): {'ON' if STATE['silencio'] else 'off'}\nDurante 22-7h las alertas no críticas se acumulan y te las resumo a las 7am.")
    # ---------- [12] heartbeat ----------
    elif cmd == "heartbeat":
        if not owner: send(chat, "🔒 solo dueño"); return
        if arg == "off":
            HEARTBEAT["on"] = False; send(chat, "💔 heartbeat: off"); return
        # arg puede ser cantidad de horas para max_idle (default 2)
        try: hrs = float(arg) if arg else 2
        except Exception: hrs = 2
        HEARTBEAT["on"] = True
        HEARTBEAT["last_motion"] = time.time()
        HEARTBEAT["max_idle_s"] = int(hrs * 3600)
        HEARTBEAT["alerted"] = False
        send(chat, f"💓 heartbeat ON: te aviso si el celu no se mueve por más de {hrs}h\n(/heartbeat off para apagar)")
    elif cmd == "run":
        if not arg: send(chat, "uso: /run <cmd>"); return
        send(chat, sh(arg)[:3900] or "(sin salida)")
    else: send(chat, "comando? /help")

def _set_env(key, val):
    p = os.path.join(HERE, ".env"); lines = []
    found = False
    for ln in open(p):
        if ln.strip().startswith(key + "="): lines.append(f"{key}={val}\n"); found = True
        else: lines.append(ln)
    if not found: lines.append(f"{key}={val}\n")
    open(p, "w").writelines(lines)

# ---------- menús inline + callback dispatcher ----------
def menu_main():
    return [
        [("🧠 Cambiar IA", "menu:ia"), ("🎭 Modo", "menu:modo")],
        [("📸 Foto", "act:foto"), ("🤳 Selfie", "act:selfie")],
        [("📍 Ubicación", "act:ubicacion"), ("🔋 Batería", "act:bateria")],
        [("🔦 Linterna", "menu:linterna"), ("📝 Notas", "act:notas")],
        [("🔧 Toggles", "menu:toggles"), ("⚙️ Config", "menu:config")],
        [("📊 Estado", "act:status"), ("❌ Cerrar", "menu:close")],
    ]

def menu_ia():
    rows = [[(("✅ " if p == STATE["provider"] else "▫️ ") + p, f"set:provider:{p}")] for p in PROVIDERS]
    rows.append([("⬅️ Volver", "menu:main")])
    return rows

def menu_modo():
    rows = [[(("✅ " if m == STATE["mode"] else "▫️ ") + m, f"set:modo:{m}")] for m in MODES]
    rows.append([("⬅️ Volver", "menu:main")])
    return rows

def menu_linterna():
    return [[("🔦 ON", "act:linterna_on"), ("⬛ OFF", "act:linterna_off")],
            [("⬅️ Volver", "menu:main")]]

def menu_toggles():
    def tog(name, label, on):
        return (f"{'✅' if on else '⬜'} {label}", f"toggle:{name}")
    return [
        [tog("voz", "Voz TTS", STATE["voz"])],
        [tog("agente", "Agente IA", STATE["agente"])],
        [tog("vigilancia", "Vigilancia (foto/30s)", VIG["on"])],
        [tog("antirobo", "Antirobo (acelerómetro)", ANTIROBO["on"])],
        [("⬅️ Volver", "menu:main")],
    ]

def menu_config():
    return [
        [(f"🏙️ Ciudad: {CITY}", "config:city")],
        [(f"⏰ Briefing: {BRIEF_HOUR}:00", "config:brief_hour")],
        [(f"🗣️ Voz TTS: {TTS_VOICE}", "config:tts_voice")],
        [("⬅️ Volver", "menu:main")],
    ]

def handle_callback(cb):
    chat = cb["message"]["chat"]["id"]
    msg_id = cb["message"]["message_id"]
    data = cb.get("data", "")
    cb_id = cb["id"]
    owner = (str(chat) == OWNER_ID)
    allow = (not OWNER_ID) or (str(chat) in allowed_ids())
    if not allow:
        answer_cb(cb_id, "🔒 sin acceso", True); return
    try:
        # ---- navegación de menús ----
        if data == "menu:main":
            edit_kb(chat, msg_id, "🤖 <b>JARVIS — Menú</b>\nElegí una opción:", menu_main(), parse_mode="HTML")
            answer_cb(cb_id)
        elif data == "menu:ia":
            edit_kb(chat, msg_id, "🧠 <b>Proveedor IA</b> (✅ = activo)", menu_ia(), parse_mode="HTML")
            answer_cb(cb_id)
        elif data == "menu:modo":
            edit_kb(chat, msg_id, "🎭 <b>Modo / personalidad</b>", menu_modo(), parse_mode="HTML")
            answer_cb(cb_id)
        elif data == "menu:linterna":
            edit_kb(chat, msg_id, "🔦 <b>Linterna</b>", menu_linterna(), parse_mode="HTML")
            answer_cb(cb_id)
        elif data == "menu:toggles":
            edit_kb(chat, msg_id, "🔧 <b>Toggles</b>", menu_toggles(), parse_mode="HTML")
            answer_cb(cb_id)
        elif data == "menu:config":
            edit_kb(chat, msg_id, "⚙️ <b>Config</b> (tocá para editar)", menu_config(), parse_mode="HTML")
            answer_cb(cb_id)
        elif data == "menu:close":
            try: tg("deleteMessage", chat_id=chat, message_id=msg_id)
            except Exception: pass
            answer_cb(cb_id, "cerrado")
        # ---- set provider / modo ----
        elif data.startswith("set:provider:"):
            p = data.split(":", 2)[2]
            if p in PROVIDERS:
                STATE["provider"] = p; STATE["model"] = PROVIDERS[p]["model"]
                edit_kb(chat, msg_id, f"🧠 <b>Activo: {esc(p)}</b>\n<code>{esc(STATE['model'])}</code>", menu_ia(), parse_mode="HTML")
                answer_cb(cb_id, f"✅ {p}")
        elif data.startswith("set:modo:"):
            m = data.split(":", 2)[2]
            if m in MODES:
                STATE["mode"] = m; HIST.pop(chat, None)
                edit_kb(chat, msg_id, f"🎭 <b>Modo: {esc(m)}</b>", menu_modo(), parse_mode="HTML")
                answer_cb(cb_id, f"✅ {m}")
        # ---- toggles ----
        elif data.startswith("toggle:"):
            if not owner: answer_cb(cb_id, "🔒 solo dueño", True); return
            name = data.split(":", 1)[1]
            if name == "voz":
                STATE["voz"] = not STATE["voz"]
            elif name == "agente":
                STATE["agente"] = not STATE["agente"]
            elif name == "vigilancia":
                if VIG["on"]:
                    VIG["on"] = False
                else:
                    VIG["on"] = True
                    threading.Thread(target=vigilancia_loop, args=(chat, 30), daemon=True).start()
            elif name == "antirobo":
                if ANTIROBO["on"]:
                    ANTIROBO["on"] = False
                else:
                    ANTIROBO["on"] = True
                    threading.Thread(target=antirobo_loop, args=(chat,), daemon=True).start()
            edit_kb(chat, msg_id, "🔧 <b>Toggles</b>", menu_toggles(), parse_mode="HTML")
            answer_cb(cb_id, "OK")
        # ---- acciones rápidas ----
        elif data == "act:foto":
            if not owner: answer_cb(cb_id, "🔒", True); return
            answer_cb(cb_id, "📸 tomando…")
            p = os.path.join(HERE, "cam.jpg")
            sh(f"termux-camera-photo -c 0 {p}", timeout=40)
            if os.path.isfile(p) and os.path.getsize(p) > 0: send_photo(chat, shrink(p), "📸")
            else: send(chat, "no pude tomar foto (permiso?)")
        elif data == "act:selfie":
            if not owner: answer_cb(cb_id, "🔒", True); return
            answer_cb(cb_id, "🤳 tomando…")
            p = os.path.join(HERE, "cam.jpg")
            sh(f"termux-camera-photo -c 1 {p}", timeout=40)
            if os.path.isfile(p) and os.path.getsize(p) > 0: send_photo(chat, shrink(p), "🤳")
            else: send(chat, "no pude tomar selfie (permiso?)")
        elif data == "act:ubicacion":
            if not owner: answer_cb(cb_id, "🔒", True); return
            answer_cb(cb_id, "📍 GPS…")
            r = sh("termux-location -p network", timeout=45) or sh("termux-location", timeout=45)
            try:
                j = json.loads(r); send(chat, f"📍 https://maps.google.com/?q={j.get('latitude')},{j.get('longitude')}")
            except Exception: send(chat, f"ubicacion: {r or 'sin datos (activa GPS)'}")
        elif data == "act:bateria":
            try:
                j = json.loads(sh("termux-battery-status"))
                answer_cb(cb_id, f"🔋 {j.get('percentage')}% | {j.get('status')} | {round(j.get('temperature',0),1)}°C", True)
            except Exception as e: answer_cb(cb_id, f"err: {e}", True)
        elif data == "act:notas":
            c = db(); rows = c.execute("SELECT id,t,ts FROM notas WHERE chat=? ORDER BY id DESC LIMIT 10", (str(chat),)).fetchall(); c.close()
            body = "\n".join(f"<b>{i}.</b> {esc(t)} <i>({ts})</i>" for i, t, ts in rows) or "<i>sin notas</i>"
            edit_kb(chat, msg_id, "📝 <b>Últimas 10 notas</b>\n\n" + body, [[("⬅️ Volver", "menu:main")]], parse_mode="HTML")
            answer_cb(cb_id)
        elif data == "act:status":
            answer_cb(cb_id)
            cmd_status(chat)
        elif data == "act:linterna_on":
            if not owner: answer_cb(cb_id, "🔒", True); return
            sh("termux-torch on"); answer_cb(cb_id, "🔦 ON")
        elif data == "act:linterna_off":
            if not owner: answer_cb(cb_id, "🔒", True); return
            sh("termux-torch off"); answer_cb(cb_id, "⬛ OFF")
        # ---------- [6] pomodoro callbacks ----------
        elif data == "pomo:start":
            if not owner: answer_cb(cb_id, "🔒", True); return
            STATE["pomodoro"] = {"phase": "work", "ends": time.time() + POMO_WORK, "cycles": 0}
            edit_kb(chat, msg_id, pomo_state_text(), pomo_kb(), parse_mode="HTML")
            answer_cb(cb_id, "▶️ pomodoro iniciado")
        elif data == "pomo:stop":
            if not owner: answer_cb(cb_id, "🔒", True); return
            STATE["pomodoro"] = None
            edit_kb(chat, msg_id, pomo_state_text(), pomo_kb(), parse_mode="HTML")
            answer_cb(cb_id, "⏹️ detenido")
        elif data == "pomo:refresh":
            edit_kb(chat, msg_id, pomo_state_text(), pomo_kb(), parse_mode="HTML")
            answer_cb(cb_id, "🔄")
        # ---------- [9] listas callbacks ----------
        elif data.startswith("lista:toggle:"):
            _, _, lid, idx = data.split(":"); lid = int(lid); idx = int(idx)
            c = db(); row = c.execute("SELECT nombre,items FROM listas WHERE id=?", (lid,)).fetchone(); c.close()
            if not row: answer_cb(cb_id, "?"); return
            nombre, its = row[0], json.loads(row[1] or "[]")
            if 0 <= idx < len(its):
                its[idx]["done"] = not its[idx].get("done", False)
                lista_save(lid, its)
            edit_kb(chat, msg_id, lista_text(nombre, its), lista_kb(chat, lid, its), parse_mode="HTML")
            answer_cb(cb_id)
        elif data.startswith("lista:del:"):
            _, _, lid, idx = data.split(":"); lid = int(lid); idx = int(idx)
            c = db(); row = c.execute("SELECT nombre,items FROM listas WHERE id=?", (lid,)).fetchone(); c.close()
            if not row: answer_cb(cb_id, "?"); return
            nombre, its = row[0], json.loads(row[1] or "[]")
            if 0 <= idx < len(its): its.pop(idx); lista_save(lid, its)
            edit_kb(chat, msg_id, lista_text(nombre, its), lista_kb(chat, lid, its), parse_mode="HTML")
            answer_cb(cb_id, "🗑️")
        elif data.startswith("lista:add:"):
            lid = int(data.split(":")[2])
            PENDING[str(chat)] = f"lista_add:{lid}"
            answer_cb(cb_id)
            force_reply(chat, "➕ Nuevo item:", "ej: comprar pan")
        elif data.startswith("lista:refresh:"):
            lid = int(data.split(":")[2])
            c = db(); row = c.execute("SELECT nombre,items FROM listas WHERE id=?", (lid,)).fetchone(); c.close()
            if not row: answer_cb(cb_id, "?"); return
            nombre, its = row[0], json.loads(row[1] or "[]")
            edit_kb(chat, msg_id, lista_text(nombre, its), lista_kb(chat, lid, its), parse_mode="HTML")
            answer_cb(cb_id, "🔄")
        # ---------- [3b] WIZARD /programa callbacks ----------
        elif data == "wiz:cancel":
            WIZARDS.pop(str(chat), None)
            if PENDING.get(str(chat), "").startswith("wiz_"): PENDING.pop(str(chat), None)
            edit_kb(chat, msg_id, "❌ Cancelado.", [], parse_mode="HTML")
            answer_cb(cb_id, "cancelado")
        elif data.startswith("wiz:canal:"):
            if not owner: answer_cb(cb_id, "🔒", True); return
            canal = data.split(":")[2]
            w = WIZARDS.setdefault(str(chat), {})
            w["canal"] = canal; w["step"] = "destino"
            answer_cb(cb_id, canal)
            # IMPORTANTE: reemplazar botones (sino los del paso previo siguen tocables)
            edit_kb(chat, msg_id,
                    f"✅ Canal: <b>{canal}</b>\n\n📞 Ahora respondé el mensaje siguiente con el destino…",
                    [[("❌ Cancelar", "wiz:cancel")]], parse_mode="HTML")
            PENDING[str(chat)] = "wiz_destino"
            if canal == "tg":
                force_reply(chat, "📱 Chat ID de Telegram (numérico):\n\nTu chat_id es: " + str(chat), str(chat))
            else:
                force_reply(chat, "📞 Número de teléfono\n\nAcepta:\n  • 0985195879 (local Ecuador)\n  • +593985195879 (internacional)\n  • con espacios: '098 519 5879'", "0985195879")
        elif data.startswith("wiz:when:"):
            if not owner: answer_cb(cb_id, "🔒", True); return
            w = WIZARDS.get(str(chat))
            # tolerar step previo a "when" si tiene canal+destino (sea reinicio o lag)
            if not w or not w.get("canal") or not w.get("destino"):
                answer_cb(cb_id, "wizard expiró — /programa de nuevo", True); return
            token = data[len("wiz:when:"):]
            if token == "custom":
                PENDING[str(chat)] = "wiz_when_custom"
                answer_cb(cb_id)
                # quitar botones para que no se pueda tocar otro "cuándo" otra vez
                edit_kb(chat, msg_id,
                        "📅 Respondé el mensaje siguiente con la fecha/hora…",
                        [[("❌ Cancelar", "wiz:cancel")]], parse_mode="HTML")
                force_reply(chat, "📅 ¿Cuándo? Formatos válidos:\n  • '+30m' (en 30 minutos)\n  • '+2h'\n  • '+1d' (en 1 día)\n  • '14:30' (hoy/mañana a esa hora)\n  • '2026-06-15 09:00'", "+30m")
                return
            due = wiz_resolve_when(token)
            if not due:
                answer_cb(cb_id, "hora inválida", True); return
            w["due"] = due; w["step"] = "texto"
            answer_cb(cb_id, "✓")
            # IMPORTANTE: reemplazar botones para que no se toque otro "cuándo" después
            edit_kb(chat, msg_id,
                    f"✅ Cuándo: <b>{esc(wiz_when_label(due))}</b>\n\n✏️ Ahora respondé con el mensaje…",
                    [[("❌ Cancelar", "wiz:cancel")]], parse_mode="HTML")
            PENDING[str(chat)] = "wiz_texto"
            force_reply(chat, "✏️ Escribí el mensaje a enviar:", "tu mensaje")
        elif data == "wiz:confirm":
            if not owner: answer_cb(cb_id, "🔒", True); return
            w = WIZARDS.pop(str(chat), None)
            if not w or not all(k in w for k in ("canal", "destino", "due", "texto")):
                answer_cb(cb_id, "wizard incompleto", True); return
            c = db(); c.execute("INSERT INTO programados(canal,destino,texto,due,hecho) VALUES(?,?,?,?,0)",
                                (w["canal"], w["destino"], w["texto"], w["due"])); c.commit()
            pid = c.execute("SELECT last_insert_rowid()").fetchone()[0]; c.close()
            try: tg("editMessageText", chat_id=chat, message_id=msg_id,
                    text=f"✅ <b>#{pid} programado</b>\n\n" + wiz_resumen(w),
                    parse_mode="HTML")
            except Exception: pass
            answer_cb(cb_id, "✅ programado")
        # ---- config (ForceReply) ----
        elif data == "config:city":
            if not owner: answer_cb(cb_id, "🔒", True); return
            PENDING[str(chat)] = "city"
            answer_cb(cb_id)
            force_reply(chat, "🏙️ Nueva ciudad (ej: Loja, Quito):", "Loja")
        elif data == "config:brief_hour":
            if not owner: answer_cb(cb_id, "🔒", True); return
            PENDING[str(chat)] = "brief_hour"
            answer_cb(cb_id)
            force_reply(chat, "⏰ Hora del briefing diario (0-23):", "7")
        elif data == "config:tts_voice":
            if not owner: answer_cb(cb_id, "🔒", True); return
            PENDING[str(chat)] = "tts_voice"
            answer_cb(cb_id)
            force_reply(chat, "🗣️ Voz Edge TTS (ej: es-ES-AlvaroNeural, es-MX-DaliaNeural, es-EC-LuisNeural):", "es-ES-AlvaroNeural")
        else:
            answer_cb(cb_id, "?")
    except Exception as e:
        print("cb:", e)
        try: answer_cb(cb_id, f"err: {e}", True)
        except Exception: pass

def handle_force_reply(chat, txt):
    """Procesa la respuesta a un ForceReply pendiente. True = consumido, False = pasar al handler normal."""
    key = PENDING.pop(str(chat), None)
    if not key: return False
    val = txt.strip()
    if key == "city":
        globals()["CITY"] = val
        _set_env("CITY", val); send(chat, f"✅ Ciudad: {val}")
    elif key == "brief_hour":
        try:
            h = int(val); assert 0 <= h <= 23
            globals()["BRIEF_HOUR"] = h
            _set_env("BRIEF_HOUR", str(h)); send(chat, f"✅ Briefing: {h}:00")
        except Exception: send(chat, "valor inválido (0-23)")
    elif key == "tts_voice":
        globals()["TTS_VOICE"] = val
        _set_env("TTS_VOICE", val); send(chat, f"✅ Voz TTS: {val}")
    elif key == "diario":
        # [5] respuesta al "¿qué hiciste hoy?"
        today = time.strftime("%Y-%m-%d")
        c = db(); c.execute("INSERT INTO diario(chat,fecha,texto,ts) VALUES(?,?,?,?)",
                            (str(chat), today, val, time.strftime("%H:%M"))); c.commit(); c.close()
        send(chat, "📔 anotado en el diario. /diario para resumen.")
    elif key.startswith("lista_add:"):
        # [9] agregar item a una lista
        lid = int(key.split(":")[1])
        c = db(); row = c.execute("SELECT nombre,items FROM listas WHERE id=?", (lid,)).fetchone(); c.close()
        if not row: send(chat, "lista no encontrada"); return True
        nombre, its = row[0], json.loads(row[1] or "[]")
        its.append({"text": val, "done": False})
        lista_save(lid, its)
        send_kb(chat, lista_text(nombre, its), lista_kb(chat, lid, its), parse_mode="HTML")
    # ---------- [3b] WIZARD /programa: responses a ForceReply ----------
    elif key == "wiz_destino":
        w = WIZARDS.get(str(chat))
        if not w: send(chat, "wizard expirado, empezá de nuevo: /programa"); return True
        if w["canal"] == "tg":
            # destino tg: debe ser numérico
            num = re.sub(r"\D", "", val)
            if not num: send(chat, "chat_id inválido. Empezá de nuevo: /programa"); WIZARDS.pop(str(chat), None); return True
            w["destino"] = num
        else:
            norm = normalizar_telefono(val)
            if not norm: send(chat, "número inválido. Empezá de nuevo: /programa"); WIZARDS.pop(str(chat), None); return True
            w["destino"] = norm
        w["step"] = "when"
        send_kb(chat, f"✅ Destino: <code>{esc(w['destino'])}</code>\n\n📅 ¿Cuándo lo mando?", wiz_kb_when(), parse_mode="HTML")
    elif key == "wiz_when_custom":
        w = WIZARDS.get(str(chat))
        if not w: send(chat, "wizard expirado, empezá de nuevo: /programa"); return True
        due = parse_fechahora(val)
        if not due: send(chat, "fecha inválida. Reintenta: /programa"); WIZARDS.pop(str(chat), None); return True
        w["due"] = due; w["step"] = "texto"
        PENDING[str(chat)] = "wiz_texto"
        force_reply(chat, f"✅ Cuándo: {wiz_when_label(due)}\n\n✏️ Ahora el mensaje a enviar:", "tu mensaje")
    elif key == "wiz_texto":
        w = WIZARDS.get(str(chat))
        if not w: send(chat, "wizard expirado, empezá de nuevo: /programa"); return True
        w["texto"] = val; w["step"] = "confirm"
        send_kb(chat, wiz_resumen(w), wiz_kb_confirm(), parse_mode="HTML")
    return True

def handle_photo(chat, file_id, caption):
    try:
        p = os.path.join(HERE, "in.jpg"); open(p, "wb").write(dl_tg_file(file_id))
        b64 = base64.b64encode(open(shrink(p), "rb").read()).decode()
        ans = call(VISION["provider"], [{"role": "user", "content": [
            {"type": "text", "text": caption or "Describe esta imagen en detalle, en espanol."},
            {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + b64}}]}], VISION["model"])
    except Exception as e:
        ans = f"error vision: {e}"
    send(chat, ans)

def handle_voice(chat, file_id):
    try: txt = transcribe(dl_tg_file(file_id))
    except Exception as e: send(chat, f"error transcripcion: {e}"); return
    if not STATE["voz"]: send(chat, "🎤 " + txt)
    try:
        if STATE["traduce"] != "off": ans = translate(txt)
        elif STATE["agente"] and str(chat) == OWNER_ID: ans = agent_run(chat, txt)
        else: ans = chat_answer(chat, txt)
    except Exception as e: ans = f"error: {e}"
    reply(chat, ans)

def handle_document(chat, file_id, fname):
    try:
        import pypdf, io
        data = dl_tg_file(file_id)
        reader = pypdf.PdfReader(io.BytesIO(data))
        txt = " ".join((p.extract_text() or "") for p in reader.pages)
        DOCS[chat] = re.sub(r'\s+', ' ', txt)[:12000]
        send(chat, f"📄 leí '{fname}' ({len(reader.pages)} pag). Pregúntame sobre él. /olvidadoc para borrar.")
    except Exception as e:
        send(chat, f"no pude leer el PDF: {e}")

def setup_bot_commands():
    """Registra el menú nativo de Telegram (los comandos que aparecen al tocar [/])."""
    cmds = [
        {"command": "menu",         "description": "🤖 Menú con botones"},
        {"command": "status",       "description": "📊 Estado de Jarvis"},
        {"command": "ausente",      "description": "🛌 Activar auto-reply"},
        {"command": "aqui",         "description": "✅ Volver de ausente"},
        {"command": "inbox",        "description": "📬 Ver msgs recibidos en ausente"},
        {"command": "sms",          "description": "📩 Últimos SMS del celu"},
        {"command": "programa",     "description": "📤 Programar msg (tg/sms/wa)"},
        {"command": "programados",  "description": "📋 Ver programados"},
        {"command": "briefing",     "description": "☀️ Briefing Loja"},
        {"command": "diario",       "description": "📔 Diario personal"},
        {"command": "lista",        "description": "📋 Lista con checkboxes"},
        {"command": "pomodoro",     "description": "🍅 Pomodoro"},
        {"command": "foto",         "description": "📸 Foto cámara"},
        {"command": "recuerda",     "description": "🧠 Buscar en memoria"},
        {"command": "help",         "description": "❓ Ayuda completa"},
    ]
    try: tg("setMyCommands", commands=cmds); print("setMyCommands OK")
    except Exception as e: print("setMyCommands:", e)

def main():
    if not TG_TOKEN: print("FALTA TELEGRAM_TOKEN"); return
    print("Bot activo: @" + tg("getMe").get("result", {}).get("username", "?"))
    setup_bot_commands()
    threading.Thread(target=scheduler, daemon=True).start()
    offset = None
    while not _STOP.is_set():
        try:
            params = {"timeout": 30, "allowed_updates": ["message", "edited_message", "callback_query"]}
            if offset: params["offset"] = offset
            for u in tg("getUpdates", **params).get("result", []):
                offset = u["update_id"] + 1
                # callback_query (botones inline tocados)
                if u.get("callback_query"):
                    cb = u["callback_query"]
                    cb_chat = cb["message"]["chat"]["id"]
                    cb_allow = (not OWNER_ID) or (str(cb_chat) in allowed_ids())
                    print(f"[CB] from={cb_chat} data={cb.get('data')!r} allow={cb_allow}")
                    if cb_allow: handle_callback(cb)
                    else: answer_cb(cb["id"], "🔒 sin acceso", True)
                    continue
                m = u.get("message") or u.get("edited_message")
                if not m: continue
                chat = m["chat"]["id"]; uid = m.get("from", {}).get("id")
                txt = m.get("text", "")
                allow = (not OWNER_ID) or (str(chat) in allowed_ids())
                if not allow and not txt.startswith("/id"):
                    send(chat, "🔒 Bot privado. Tu /id: " + str(chat)); continue
                # ---------- [1] modo ausente: auto-reply para NO-owner ----------
                if STATE["ausente"] and str(chat) != OWNER_ID and txt and not txt.startswith("/"):
                    try:
                        fn = (m.get("from", {}).get("first_name") or "") + " " + (m.get("from", {}).get("last_name") or "")
                        fn = fn.strip() or m.get("from", {}).get("username", "")
                        c = db()
                        c.execute("INSERT INTO inbox_ausente(chat,from_id,from_name,text,ts) VALUES(?,?,?,?,?)",
                                  (str(chat), str(uid), fn, txt, time.strftime("%d/%m %H:%M")))
                        c.commit(); c.close()
                        send(chat, "🛌 " + STATE["ausente"])
                    except Exception as e: print("ausente:", e)
                    continue
                # respuesta a un ForceReply pendiente (config) — consumir antes del handler normal
                if txt and m.get("reply_to_message") and handle_force_reply(chat, txt): continue
                if m.get("voice"): handle_voice(chat, m["voice"]["file_id"])
                elif m.get("photo"): handle_photo(chat, m["photo"][-1]["file_id"], m.get("caption", ""))
                elif m.get("document"): handle_document(chat, m["document"]["file_id"], m["document"].get("file_name", "doc"))
                elif txt: handle(chat, uid, txt)
        except urllib.error.URLError as e:
            print("net:", e); time.sleep(3)
        except Exception as e:
            print("loop:", e); time.sleep(2)

if __name__ == "__main__":
    main()
