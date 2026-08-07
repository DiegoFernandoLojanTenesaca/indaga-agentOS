"""
AgentOS — entrypoint del agente Jarvis (llamado desde AgentService en Kotlin).

API:
    start(config) -> str   # config = JSON/dict: AGENTOS_HOME, TELEGRAM_TOKEN, *_API_KEY, OWNER_ID, CITY...
    stop()        -> str
    get_logs()    -> str   # últimas líneas para la pantalla de Logs

Flujo: inyecta la config en os.environ ANTES de importar jarvis_core (que lee el
entorno al importarse), redirige stdout a un buffer en memoria (para la UI) y al
logcat, y corre el long-poll de Telegram en un hilo daemon.
"""
import os
import sys
import json
import time
import threading
from collections import deque

_log = deque(maxlen=400)
_thread = None
_core = None


class _Tee:
    """Duplica stdout: buffer en memoria (UI Logs) + stdout real (logcat)."""
    def __init__(self, real):
        self._real = real

    def write(self, s):
        try:
            if s and s.strip():
                _log.append(time.strftime("%H:%M:%S ") + s.rstrip("\n"))
        except Exception:
            pass
        try:
            if self._real:
                self._real.write(s)
        except Exception:
            pass

    def flush(self):
        try:
            if self._real:
                self._real.flush()
        except Exception:
            pass


def get_logs():
    return "\n".join(_log) if _log else "(sin logs todavía)"


def clear_logs():
    _log.clear()
    return "cleared"


def is_alive():
    return bool(_thread is not None and _thread.is_alive())


def last_beat():
    """Epoch (s) del último latido del agente; 0 si no arrancó. Lo usa el watchdog."""
    try:
        return float(_core.LAST_BEAT["t"]) if _core is not None else 0.0
    except Exception:
        return 0.0


def info():
    """Estado breve del agente en JSON para la UI (NO toca la red)."""
    try:
        if _core is None:
            return json.dumps({"running": False})
        st = _core.STATE
        usage = _core.USAGE
        tin = sum(v.get("in", 0) for v in usage.values())
        tout = sum(v.get("out", 0) for v in usage.values())
        treq = sum(v.get("req", 0) for v in usage.values())
        up = int(time.time() - _core.START)
        return json.dumps({
            "running": bool(_thread is not None and _thread.is_alive()),
            "provider": st.get("provider", ""),
            "model": st.get("model", ""),
            "uptime_s": up,
            "tokens": tin + tout,
            "requests": treq,
            "bot": _core.BOT_USERNAME["v"],
        })
    except Exception as e:
        return json.dumps({"running": False, "error": str(e)})


def skills():
    """Skills instalados, para pintar la sala. No ejecuta código de skills."""
    try:
        from skills import loader
        datos = loader.escanear()
        return json.dumps([{
            "name": s.get("meta", {}).get("name") or s["name"],
            "id": s["name"],
            "status": s["status"],
            "reason": s.get("reason", ""),
            "commands": s.get("commands", []),
            "desc": (s.get("meta", {}).get("description") or "")[:160],
            "source": s.get("meta", {}).get("source", "local"),
        } for s in datos])
    except Exception as e:
        return json.dumps([{"name": "loader", "id": "loader",
                            "status": "error", "reason": str(e),
                            "commands": [], "desc": "", "source": "core"}])


def verificar_bot(token):
    """Pregunta a Telegram si el token vale. Bloquea: fuera del hilo de UI."""
    try:
        from core.telegram import verificar
        return json.dumps(verificar(token))
    except Exception as e:
        return json.dumps({"ok": False, "detalle": str(e)})


def _vars(blob):
    """`CLAVE=valor` por línea -> dict. Con el agente parado las variables del
    usuario no están en os.environ todavía, así que la interfaz las manda."""
    out = {}
    for linea in (blob or "").splitlines():
        if "=" in linea:
            k, v = linea.split("=", 1)
            if k.strip():
                out[k.strip()] = v.strip()
    return out


def proveedores(env=""):
    """Catálogo de proveedores para la pantalla de ajustes."""
    try:
        from core.proveedores import catalogo
        return json.dumps(catalogo(_vars(env)))
    except Exception:
        return json.dumps([])


def leer_documento(ruta):
    """Texto plano de un archivo, para dárselo a un agente como fuente.

    PDF con pypdf (ya viene instalado para /pdf del bot); lo demás se lee como
    texto. Devuelve "" si no se puede: un adjunto ilegible no debe romper nada.
    """
    import os
    try:
        if ruta.lower().endswith(".pdf"):
            from pypdf import PdfReader
            lector = PdfReader(ruta)
            # 12 páginas es de sobra: lo que entra en el prompt de un modelo
            # pequeño es mucho menos que eso.
            trozos = [(p.extract_text() or "") for p in lector.pages[:12]]
            return "\n".join(t.strip() for t in trozos if t.strip())[:20000]
        with open(ruta, "rb") as f:
            crudo = f.read(200000)
        return crudo.decode("utf-8", errors="replace").strip()
    except Exception as e:
        _log.append("documento ilegible %s: %s" % (os.path.basename(ruta), e))
        return ""


def contexto_agente(agente, home=""):
    """Lo que ese agente sabe de sus propios datos, para el chat local.

    `home` es el directorio de datos de la app: sin él, con el agente parado no
    se sabría dónde está la base y las fuentes leerían vacío.
    """
    try:
        from core.datos import contexto
        return contexto(agente, home=home or None)
    except Exception:
        return ""


def modelos_de(pid, key, env=""):
    """Modelos reales del proveedor. Bloquea: fuera del hilo de interfaz."""
    try:
        from core.proveedores import listar_modelos
        return json.dumps(listar_modelos(pid, key, env=_vars(env)))
    except Exception as e:
        return json.dumps({"ok": False, "modelos": [], "detalle": str(e)})


def probar_proveedor(pid, key, env=""):
    """Prueba real de conexión. Bloquea: llamar fuera del hilo de interfaz."""
    try:
        from core.proveedores import probar
        return json.dumps(probar(pid, key, env=_vars(env)))
    except Exception as e:
        return json.dumps({"ok": False, "ms": 0, "detalle": str(e)})


def usage():
    """Consumo por proveedor: {proveedor: {in, out, req}}."""
    try:
        return json.dumps(dict(_core.USAGE)) if _core is not None else json.dumps({})
    except Exception:
        return json.dumps({})


def heatmap():
    """Dict {fecha: n} de actividad de los últimos ~182 días (26 semanas) en JSON."""
    try:
        return json.dumps(_core.activity_map(182)) if _core is not None else json.dumps({})
    except Exception:
        return json.dumps({})


def _run():
    try:
        _core.main()
    except Exception as e:
        print("jarvis fatal:", e)


def start(config):
    global _thread, _core
    if _thread is not None and _thread.is_alive():
        return "already-running"

    # 1) Config (token + API keys + AGENTOS_HOME) -> os.environ
    try:
        cfg = json.loads(config) if isinstance(config, str) else dict(config)
    except Exception as e:
        cfg = {}
        _log.append("config inválida: %s" % e)
    for k, v in cfg.items():
        if v is not None and str(v) != "":
            os.environ[str(k)] = str(v)

    # 2) Capturar stdout/stderr para la pantalla de Logs
    if not isinstance(sys.stdout, _Tee):
        sys.stdout = _Tee(sys.__stdout__)
        sys.stderr = _Tee(sys.__stderr__)

    # 3) Importar el core DESPUÉS de poblar el entorno (lee env al importar)
    import importlib
    if _core is None:
        _core = importlib.import_module("jarvis_core")
    else:
        importlib.reload(_core)

    _core._STOP.clear()
    _thread = threading.Thread(target=_run, daemon=True)
    _thread.start()
    return "started"


def stop():
    try:
        if _core is not None:
            _core._STOP.set()
    except Exception:
        pass
    return "stopping"
