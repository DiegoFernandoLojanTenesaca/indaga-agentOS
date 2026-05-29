"""
Cliente del AndroidBridge — capa que reemplaza los comandos `termux-*`.

FASE 1a (actual): STUB. El bridge Kotlin (HTTP en localhost:8765) todavía no
existe, así que el hardware del teléfono se reporta como "disponible en breve".

FASE 1b: `termux_shim()` parseará cada comando termux-* y llamará al endpoint
correspondiente (ver mapeo en ARCHITECTURE.md §5):
    termux-camera-photo -> POST /camera     termux-location   -> GET /gps
    termux-battery-status-> GET  /battery    termux-tts-speak  -> POST /tts
    termux-torch         -> POST /torch      termux-sensor     -> GET /sensors
    termux-sms-send/list -> POST/GET /sms    termux-telephony-call -> POST /call
    termux-vibrate       -> POST /vibrate    termux-volume     -> POST /volume
    termux-clipboard-*   -> /clipboard       termux-microphone-record -> POST /mic
    termux-notification  -> POST /notify
"""

BRIDGE_URL = "http://127.0.0.1:8765"


def termux_shim(cmd, timeout=120):
    """Fase 1a: reporta el comando como no disponible (sin romper el flujo)."""
    tool = cmd.strip().split()[0] if cmd.strip() else "termux"
    return "[AgentOS] '%s' estará disponible en breve (capa de hardware, Fase 1b)." % tool
