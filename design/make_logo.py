#!/usr/bin/env python3
"""Genera el logo de marca y los iconos legacy a partir del icono adaptativo.

El adaptativo (mipmap-anydpi-v26 + ic_launcher_foreground + el color de fondo)
es la única fuente de verdad. Todo lo demás se deriva de aquí, así el README y
el launcher no se separan cuando cambia el sprite del robot.

    python3 design/make_logo.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

RAIZ = Path(__file__).resolve().parent.parent
RES = RAIZ / "app/src/main/res"
FOREGROUND = RES / "mipmap-xxxhdpi/ic_launcher_foreground.png"
FONDO = "#FFF7ED"  # el mismo de res/values/ic_launcher_background.xml
ESCALA = 3  # entera y sólo entera: con interpolación el pixel art se emborrona

# Los legacy sólo los usan launchers viejos y algunas herramientas; con
# minSdk 26 el adaptativo cubre el caso normal. Se mantienen por coherencia.
DENSIDADES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def robot() -> Image.Image:
    im = Image.open(FOREGROUND).convert("RGBA")
    im = im.crop(im.split()[-1].getbbox())
    return im.resize((im.width * ESCALA, im.height * ESCALA), Image.Resampling.NEAREST)


def componer(lado: int, sprite: Image.Image, redondeo: float) -> Image.Image:
    """redondeo: 0 = cuadrado, 0.22 = squircle de launcher, 0.5 = círculo."""
    lienzo = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    mascara = Image.new("L", (lado, lado), 0)
    ImageDraw.Draw(mascara).rounded_rectangle(
        (0, 0, lado - 1, lado - 1), radius=int(lado * redondeo), fill=255
    )
    lienzo.paste(Image.new("RGBA", (lado, lado), FONDO), mask=mascara)
    # El sprite ocupa el 60% del alto: más y toca los bordes al recortarse.
    alto = int(lado * 0.6)
    s = sprite.resize((round(sprite.width * alto / sprite.height), alto), Image.Resampling.NEAREST)
    lienzo.paste(s, ((lado - s.width) // 2, (lado - s.height) // 2), s)
    return lienzo


def main() -> None:
    sprite = robot()

    marca = RAIZ / "design/agentos-icon-1024.png"
    componer(1024, sprite, 0.22).save(marca)
    print(f"{marca.relative_to(RAIZ)} · 1024")

    for densidad, lado in DENSIDADES.items():
        for nombre, redondeo in (("ic_launcher", 0.22), ("ic_launcher_round", 0.5)):
            destino = RES / f"mipmap-{densidad}/{nombre}.png"
            componer(lado, sprite, redondeo).save(destino)
        print(f"mipmap-{densidad} · {lado}px")


if __name__ == "__main__":
    main()
