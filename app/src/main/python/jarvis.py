"""
Jarvis — Phase 0 minimal agent.

Pure-stdlib Telegram long-poll bot, designed to run embedded under Chaquopy
inside the AgentOS Android app. No Google, no external services beyond the
Telegram Bot API (HTTPS). Phase 1 will graft the full Jarvis core and wire the
hardware layer to the AndroidBridge.

Public API called from Kotlin (AgentService):
    start(token) -> str   # spawns the poll loop on a daemon thread
    stop()       -> str   # signals the loop to exit
    get_logs()   -> str   # last ~50 log lines, for the in-app Logs view
"""

import json
import threading
import time
import urllib.parse
import urllib.request

_stop = threading.Event()
_thread = None
_log = []
_lock = threading.Lock()


def _logmsg(msg):
    line = time.strftime("%H:%M:%S ") + str(msg)
    with _lock:
        _log.append(line)
        if len(_log) > 200:
            del _log[: len(_log) - 200]
    print("[jarvis]", line)


def get_logs():
    with _lock:
        return "\n".join(_log[-50:]) if _log else "(sin logs todavía)"


def _api(token, method, params=None, timeout=40):
    url = "https://api.telegram.org/bot%s/%s" % (token, method)
    data = urllib.parse.urlencode(params or {}).encode("utf-8")
    req = urllib.request.Request(url, data=data)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _handle(text):
    t = (text or "").strip()
    if t.startswith("/start"):
        return (
            "👋 AgentOS vivo en tu Android (Fase 0).\n"
            "Soy Jarvis, embebido con Python/Chaquopy, sin Google.\n"
            "Escríbeme algo y te lo repito. Prueba /ping."
        )
    if t.startswith("/ping"):
        return "pong 🏓"
    if not t:
        return "(mensaje vacío)"
    return "🔁 " + t


def _loop(token):
    _logmsg("Bot iniciado, long-polling…")
    try:
        me = _api(token, "getMe")
        uname = me.get("result", {}).get("username", "?")
        _logmsg("Conectado como @%s" % uname)
    except Exception as e:
        _logmsg("Error getMe: %s" % e)

    offset = 0
    while not _stop.is_set():
        try:
            resp = _api(token, "getUpdates", {"offset": offset, "timeout": 30})
            for upd in resp.get("result", []):
                offset = upd["update_id"] + 1
                msg = upd.get("message") or {}
                chat = msg.get("chat", {}).get("id")
                text = msg.get("text", "")
                if chat is None:
                    continue
                _logmsg("<- %s" % text)
                reply = _handle(text)
                _api(token, "sendMessage", {"chat_id": chat, "text": reply})
                _logmsg("-> %s" % reply)
        except Exception as e:
            _logmsg("loop err: %s" % e)
            time.sleep(3)
    _logmsg("Bot detenido.")


def start(token):
    global _thread
    if _thread is not None and _thread.is_alive():
        return "already-running"
    _stop.clear()
    _thread = threading.Thread(target=_loop, args=(token,), daemon=True)
    _thread.start()
    return "started"


def stop():
    _stop.set()
    return "stopping"
