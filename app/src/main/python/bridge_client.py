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

        if tool == "termux-location":
            return _call("/gps")

        if tool == "termux-sms-send":
            num, text_parts, i = "", [], 0
            while i < len(args):
                if args[i] == "-n":
                    num = args[i + 1] if i + 1 < len(args) else ""
                    i += 2
                    continue
                text_parts.append(args[i])
                i += 1
            _call("/sms", {"to": num, "message": " ".join(text_parts)})
            return ""

        if tool == "termux-sms-list":
            limit = "10"
            if "-l" in args:
                j = args.index("-l")
                limit = args[j + 1] if j + 1 < len(args) else "10"
            return _call("/sms", {"limit": limit})

        if tool == "termux-telephony-call":
            _call("/call", {"number": args[0] if args else ""})
            return ""

        if tool == "termux-volume":
            if len(args) >= 2:
                _call("/volume", {"stream": args[0], "level": args[1]})
                return ""
            return _call("/volume")

        if tool == "termux-camera-photo":
            # termux-camera-photo -c <0|1> <ruta>  (0=trasera, 1=frontal/selfie)
            facing, path, i = "back", "", 0
            while i < len(args):
                if args[i] == "-c":
                    facing = "front" if (i + 1 < len(args) and args[i + 1] == "1") else "back"
                    i += 2
                    continue
                path = args[i]
                i += 1
            if not path:
                return "[bridge] falta la ruta de la foto"
            return _call("/camera", {"facing": facing, "path": path}, timeout=15)

        if tool == "termux-microphone-record":
            if "-q" in args:  # detener: grabamos síncrono, así que es no-op
                return ""
            secs, path = "5", ""
            if "-l" in args:
                k = args.index("-l"); secs = args[k + 1] if k + 1 < len(args) else "5"
            if "-f" in args:
                k = args.index("-f"); path = args[k + 1] if k + 1 < len(args) else ""
            return _call("/mic", {"seconds": secs, "path": path}, timeout=int(secs) + 15)

        if tool == "termux-sensor":
            if "-l" in args:
                return _call("/sensors", {"list": "1"})
            name = "accelerometer"
            if "-s" in args:
                k = args.index("-s"); name = args[k + 1] if k + 1 < len(args) else "accelerometer"
            return _call("/sensors", {"name": name})

    except Exception as e:
        return "[bridge error: %s]" % e

    # cámara/gps/sms/llamada/mic/sensor/volumen → Fase 1b-2
    return "[AgentOS] '%s' estará disponible en breve (capa de hardware, Fase 1b-2)." % tool
