---
name: telefono
description: Control del hardware del dispositivo — cámara, GPS, batería, linterna y portapapeles. Úsalo cuando el usuario pida una foto, dónde está el teléfono, cuánta batería queda o encender la linterna.
role: owner
requires-env: []
---

# Teléfono

Comandos que tocan el hardware a través del AndroidBridge (`127.0.0.1:8765`).
Todos son de **dueño**: exponen cámara y ubicación, no se dan a invitados.

| Comando | Qué hace |
|---|---|
| `/foto` | Foto con la cámara trasera |
| `/selfie` | Foto con la frontal |
| `/ubicacion` | Coordenadas GPS + enlace a mapa |
| `/bateria` | Porcentaje, estado de carga y temperatura |
| `/linterna on\|off` | Enciende o apaga el flash |
| `/copia <texto>` | Escribe en el portapapeles |
| `/pega` | Lee el portapapeles |

## Notas

Las llamadas `termux-*` no ejecutan Termux: `jarvis_core.sh()` las intercepta y
las enruta al bridge Kotlin (ver `bridge_client.termux_shim`). Se mantiene esa
forma porque es la que ya usa el resto del código; el Paso 5 del plan las
sustituye por llamadas directas a `core/hardware.py`.

La cámara puede tardar hasta 40 s en arrancar si la app estaba dormida.
