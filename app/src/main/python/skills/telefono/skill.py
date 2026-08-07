"""Skill 'telefono': comandos de hardware. Ver SKILL.md."""

import os
import json

import jarvis_core as jc
from core import registry

SRC = "skill:telefono"


@registry.command("bateria", owner=True, source=SRC,
                  desc="Estado de la batería", usage="/bateria")
def _bateria(ctx):
    try:
        j = json.loads(jc.sh("termux-battery-status"))
        jc.send(ctx["chat"], "🔋 %s%% | %s | %s°C" % (
            j.get("percentage"), j.get("status"), round(j.get("temperature", 0), 1)))
    except Exception as e:
        jc.send(ctx["chat"], "bateria: %s" % e)


@registry.command("linterna", owner=True, source=SRC,
                  desc="Enciende o apaga la linterna", usage="/linterna on|off")
def _linterna(ctx):
    arg = ctx["arg"]
    jc.sh("termux-torch %s" % ("on" if arg == "on" else "off"))
    jc.send(ctx["chat"], "🔦 %s" % (arg or "off"))


@registry.command("foto", "selfie", owner=True, source=SRC,
                  desc="Toma una foto (trasera o frontal)", usage="/foto | /selfie")
def _foto(ctx):
    chat = ctx["chat"]
    cam = "1" if ctx["cmd"] == "selfie" else "0"
    p = os.path.join(jc.HERE, "cam.jpg")
    r = jc.sh("termux-camera-photo -c %s %s" % (cam, p), timeout=40)
    if os.path.isfile(p) and os.path.getsize(p) > 0:
        jc.send_photo(chat, jc.shrink(p), "📸")
    else:
        jc.send(chat, "no pude (permiso camara?): %s" % r)


@registry.command("ubicacion", owner=True, source=SRC,
                  desc="Ubicación GPS actual", usage="/ubicacion")
def _ubicacion(ctx):
    chat = ctx["chat"]
    jc.send(chat, "📍 obteniendo GPS…")
    r = jc.sh("termux-location -p network", timeout=45) or jc.sh("termux-location", timeout=45)
    try:
        j = json.loads(r)
        jc.send(chat, "📍 https://maps.google.com/?q=%s,%s" % (j.get("latitude"), j.get("longitude")))
    except Exception:
        jc.send(chat, "ubicacion: %s" % (r or "sin datos (activa GPS)"))


@registry.command("copia", owner=True, source=SRC,
                  desc="Copia texto al portapapeles", usage="/copia <texto>")
def _copia(ctx):
    jc.sh("termux-clipboard-set %s" % json.dumps(ctx["arg"], ensure_ascii=False))
    jc.send(ctx["chat"], "📋 copiado")


@registry.command("pega", owner=True, source=SRC,
                  desc="Lee el portapapeles", usage="/pega")
def _pega(ctx):
    jc.send(ctx["chat"], jc.sh("termux-clipboard-get") or "(vacio)")
