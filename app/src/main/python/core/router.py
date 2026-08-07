"""
Router local vs nube.

La regla NO es "simple vs complejo" ni "rápido vs lento": es
**stateless vs conversacional**. Sale de medir Qwen3 0.6B en el P40 Lite
(Kirin 810, CPU 6 hilos, sin GPU utilizable):

    prompt corto, sin historial ....  730 ms TTFT · 17,6 tok/s  → ~1,4 s total
    mismo prompt, 3 turnos de chat  19.494 ms TTFT · 13,1 tok/s → inusable

El TTFT es *prefill*: reprocesa todo el contexto en cada llamada. Con historial
se multiplica por 26. Por eso el modelo local aquí no es un chat, es una
**función**: entra un texto corto, sale un dato corto, y no recuerda nada.

Además, la investigación previa refutó (3 votos) que un modelo <8B sostenga
tool-calling multi-paso. Todo lo agéntico va a la nube.
"""

# Tareas que SÍ van al modelo local: entrada corta, salida corta, sin historial.
# Cada una trae su propio system prompt con la regla de decisión explícita,
# porque un 0.6B no tiene criterio propio: si no le dices qué es "urgente",
# el mismo SMS te sale URGENTE una vez y NORMAL la siguiente (medido).
TAREAS_LOCALES = {
    "clasificar_sms": {
        "system": (
            "Clasificas SMS. Responde EXACTAMENTE una palabra: URGENTE o NORMAL.\n"
            "URGENTE solo si: es una alerta de seguridad, un cobro no reconocido, "
            "o pide acción inmediata de una persona.\n"
            "NORMAL para todo lo demás, incluidos códigos de verificación, "
            "promociones y avisos automáticos."
        ),
        "max_tokens": 4,
    },
    "extraer_otp": {
        "system": (
            "Extraes el código de verificación de un SMS. Responde SOLO los "
            "dígitos, sin texto. Si no hay código, responde NADA."
        ),
        "max_tokens": 12,
    },
    "extraer_fecha": {
        "system": (
            "Extraes fecha y hora de un texto en español. Responde SOLO en "
            "formato YYYY-MM-DD HH:MM. Si no hay fecha clara, responde NADA."
        ),
        "max_tokens": 20,
    },
    "es_urgente": {
        "system": (
            "Decides si una notificación merece despertar al usuario. "
            "Responde EXACTAMENTE SI o NO."
        ),
        "max_tokens": 4,
    },
    "resumir_corto": {
        "system": "Resume en una sola frase, en español, sin preámbulo.",
        "max_tokens": 60,
    },
}

# Límite de entrada para lo local. Por encima, el prefill se dispara y deja de
# compensar: mejor pagar la llamada a la nube.
MAX_CHARS_LOCAL = 600


def destino(tarea, texto="", hay_internet=True, local_disponible=False):
    """Devuelve "local" o "nube" y el motivo, para poder registrarlo.

    Es una función pura: sin ella, la decisión acabaría repartida en ifs por
    todo jarvis_core y nadie sabría por qué se eligió cada cosa.
    """
    if not hay_internet:
        if local_disponible and tarea in TAREAS_LOCALES:
            return "local", "sin internet"
        return "nube", "sin internet y sin modelo local: no hay nada que hacer"

    if tarea not in TAREAS_LOCALES:
        return "nube", "tarea conversacional o agéntica"

    if not local_disponible:
        return "nube", "modelo local no cargado"

    if len(texto) > MAX_CHARS_LOCAL:
        return "nube", "entrada de %d chars (>%d): el prefill no compensa" % (
            len(texto), MAX_CHARS_LOCAL)

    return "local", "stateless, corta y privada"


def prompt_para(tarea, texto):
    """Mensajes para una tarea local. SIEMPRE dos: system + user, sin historial.

    Que no acepte historial es deliberado: es lo que mantiene el TTFT en ~730 ms.
    """
    spec = TAREAS_LOCALES.get(tarea)
    if spec is None:
        raise KeyError("tarea local desconocida: %s" % tarea)
    return [
        {"role": "system", "content": spec["system"]},
        {"role": "user", "content": texto[:MAX_CHARS_LOCAL]},
    ], spec["max_tokens"]
