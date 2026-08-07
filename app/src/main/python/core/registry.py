"""
Registro central de comandos de AgentOS.

Sustituye la cadena de 61 `elif cmd == "..."` de jarvis_core.handle() por un
lookup en un dict. Lo importante no es la velocidad (61 comparaciones no le
duelen a nadie), sino que **cualquiera pueda registrar un comando sin tocar el
core**: skills, MCP tools o módulos futuros. Es el principio #2 de
ARCHITECTURE.md, que hasta ahora estaba escrito pero no implementado.

Uso:

    from core import registry

    @registry.command("foto", "selfie", owner=True, desc="Toma una foto")
    def _foto(ctx):
        chat, arg, cmd = ctx["chat"], ctx["arg"], ctx["cmd"]
        ...

El módulo no importa nada de jarvis_core: las funciones registradas se definen
donde ya viven y siguen usando los helpers globales que tengan a mano. Así la
migración es gradual y reversible.
"""

# nombre de comando (sin "/") -> spec. Los alias apuntan al MISMO spec.
COMMANDS = {}


def command(*names, owner=False, desc="", usage="", source="core"):
    """Registra una función como comando de Telegram.

    names  : nombre y alias, con o sin "/" ("foto", "selfie")
    owner  : si True, solo el dueño puede ejecutarlo
    desc   : descripción corta (para /help y para que el LLM sepa que existe)
    usage  : ejemplo de uso ("/nota <texto>")
    source : quién lo registró — "core" o el nombre del skill. Sirve para
             saber a quién culpar cuando algo se pisa.
    """
    if not names:
        raise ValueError("command() necesita al menos un nombre")

    def deco(fn):
        clean = tuple(n.lstrip("/").lower() for n in names)
        spec = {
            "fn": fn, "names": clean, "owner": owner,
            "desc": desc, "usage": usage, "source": source,
        }
        for n in clean:
            prev = COMMANDS.get(n)
            # Un skill mal escrito no debe pisar un comando del core en silencio.
            if prev is not None and prev["fn"] is not fn:
                raise ValueError(
                    "comando duplicado /%s: lo registra '%s' y ya lo tenía '%s'"
                    % (n, source, prev["source"])
                )
            COMMANDS[n] = spec
        return fn

    return deco


def get(name):
    """Spec del comando, o None si no está registrado."""
    return COMMANDS.get(str(name).lstrip("/").lower())


def has(name):
    return get(name) is not None


def run(name, ctx):
    """Ejecuta el comando. Devuelve False si no existe o si falta permiso.

    No captura excepciones de la propia función: que suban a handle(), que ya
    tiene su try/except y sabe avisar al usuario.
    """
    spec = get(name)
    if spec is None:
        return False
    if spec["owner"] and not ctx.get("owner"):
        return False
    spec["fn"](ctx)
    return True


def names():
    """Nombres registrados, sin alias repetidos, ordenados."""
    return sorted({s["names"][0] for s in COMMANDS.values()})


def specs():
    """Specs únicos (un solo elemento por comando aunque tenga alias)."""
    out, seen = [], set()
    for s in COMMANDS.values():
        if id(s) not in seen:
            seen.add(id(s))
            out.append(s)
    return sorted(out, key=lambda s: s["names"][0])


def help_lines(owner=True):
    """Líneas '/cmd — desc' para /help. Oculta los de dueño si owner=False."""
    return [
        "/%s — %s" % (s["names"][0], s["desc"] or "(sin descripción)")
        for s in specs()
        if owner or not s["owner"]
    ]


def unregister(name):
    """Quita un comando y sus alias. Para tests y para descargar skills."""
    spec = get(name)
    if spec is None:
        return False
    for n in spec["names"]:
        COMMANDS.pop(n, None)
    return True


def clear(source=None):
    """Vacía el registro entero, o solo lo aportado por un source."""
    if source is None:
        COMMANDS.clear()
        return
    for s in specs():
        if s["source"] == source:
            unregister(s["names"][0])
