"""Comprobación del token de Telegram.

Guardar un token y descubrir tres arranques después que estaba mal es la peor
forma de empezar. `getMe` responde en un segundo y dice el @usuario del bot.
"""

import json
import urllib.error
import urllib.request


def verificar(token, timeout=15):
    """Devuelve {ok, usuario, nombre, id, detalle}."""
    token = (token or "").strip()
    if not token:
        return {"ok": False, "detalle": "sin token"}
    if ":" not in token:
        return {"ok": False, "detalle": "formato raro: falta el ':'"}

    url = "https://api.telegram.org/bot%s/getMe" % token
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            d = json.loads(r.read().decode())
        u = d.get("result", {})
        return {"ok": bool(d.get("ok")), "usuario": u.get("username", ""),
                "nombre": u.get("first_name", ""), "id": u.get("id", 0),
                "detalle": ""}
    except urllib.error.HTTPError as e:
        if e.code == 401:
            return {"ok": False, "detalle": "token rechazado por Telegram"}
        return {"ok": False, "detalle": "HTTP %s" % e.code}
    except Exception as e:
        return {"ok": False, "detalle": "%s: %s" % (type(e).__name__, e)}


if __name__ == "__main__":
    assert verificar("")["detalle"] == "sin token"
    assert verificar("noesuntoken")["detalle"].startswith("formato")
    print("ok")
