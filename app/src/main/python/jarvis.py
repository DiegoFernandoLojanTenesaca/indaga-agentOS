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
