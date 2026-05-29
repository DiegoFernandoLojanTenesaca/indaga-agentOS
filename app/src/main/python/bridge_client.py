"""
Cliente del AndroidBridge — reemplaza los comandos `termux-*` por llamadas HTTP
al puente Kotlin en 127.0.0.1:8765 (ver bridge/AndroidBridge.kt).

Fase 1b-1 implementado: battery, torch, vibrate, tts, notify, clipboard.
Fase 1b-2 (pendiente): camera, location, sms, telephony-call, microphone, sensor, volume.
"""

import json
import shlex
import urllib.parse
import urllib.request

BRIDGE = "http://127.0.0.1:8765"


def _call(path, params=None, timeout=20):
    url = BRIDGE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return r.read().decode("utf-8")


def termux_shim(cmd, timeout=120):
    try:
        parts = shlex.split(cmd)
    except Exception:
        parts = cmd.split()
    if not parts:
        return ""
    tool, args = parts[0], parts[1:]

    try:
        if tool == "termux-battery-status":
            return _call("/battery")

        if tool == "termux-torch":
            return _call("/torch", {"on": "true" if "on" in args else "false"})

        if tool == "termux-vibrate":
            ms = "500"
            if "-d" in args:
                i = args.index("-d")
                if i + 1 < len(args):
                    ms = args[i + 1]
            _call("/vibrate", {"ms": ms})
            return ""

        if tool == "termux-tts-speak":
            # descartar flags con valor (-s STREAM, -r rate, -p pitch) y quedarnos con el texto
            text_parts, skip = [], False
            for a in args:
                if skip:
                    skip = False
                    continue
                if a.startswith("-"):
                    skip = True
                    continue
                text_parts.append(a)
            _call("/tts", {"text": " ".join(text_parts)})
            return ""

        if tool == "termux-notification":
            title = content = ""
            if "--title" in args:
                i = args.index("--title")
                title = args[i + 1] if i + 1 < len(args) else ""
            if "--content" in args:
                i = args.index("--content")
                content = args[i + 1] if i + 1 < len(args) else ""
            _call("/notify", {"title": title, "content": content})
            return ""

        if tool == "termux-clipboard-get":
            raw = _call("/clipboard")
            try:
                return json.loads(raw).get("text", "")
            except Exception:
                return raw

        if tool == "termux-clipboard-set":
            _call("/clipboard", {"set": " ".join(args)})
            return ""

    except Exception as e:
        return "[bridge error: %s]" % e

    # cámara/gps/sms/llamada/mic/sensor/volumen → Fase 1b-2
    return "[AgentOS] '%s' estará disponible en breve (capa de hardware, Fase 1b-2)." % tool
