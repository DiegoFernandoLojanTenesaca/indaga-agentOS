"""
Loader de skills de AgentOS.

Un skill es una carpeta con un `SKILL.md` (frontmatter YAML + documentación) y,
opcionalmente, un `skill.py` que registra comandos vía @registry.command.

    skills/telefono/
    ├── SKILL.md      # metadatos + doc que puede leer el LLM
    └── skill.py      # código, opcional

El formato del frontmatter es el de los Agent Skills (Claude Code / OpenClaw),
para poder portar skills de fuera sin traducirlos:

    ---
    name: telefono
    description: Cámara, GPS y batería del dispositivo
    role: owner              # owner | invitado   (extensión nuestra)
    requires-env: [GROQ_API_KEY]   # si falta alguna, el skill se desactiva
    ---
    Cuerpo en markdown.

Reglas de diseño:
- Un skill roto NUNCA tumba el arranque: se salta y se anota el motivo.
- Faltan sus env vars ⇒ se desactiva limpio, no a medias.
- El YAML se parsea a mano: PyYAML no está en el bloque pip de Chaquopy y para
  cuatro claves planas no merece la pena arrastrar una dependencia.
"""

import os
import sys
import importlib.util

SKILLS_DIR = os.path.dirname(os.path.abspath(__file__))

# Resultado de la última carga: lista de dicts con qué se cargó y qué no.
LOADED = []


def parse_frontmatter(text):
    """Devuelve (meta, cuerpo). Soporta `k: v`, `k: [a, b]` y listas con `- `.

    Deliberadamente NO es YAML completo: solo lo que un SKILL.md necesita.
    Si el archivo no empieza por '---', meta va vacío y todo es cuerpo.
    """
    meta, lines = {}, text.splitlines()
    if not lines or lines[0].strip() != "---":
        return meta, text

    end = None
    for i in range(1, len(lines)):
        if lines[i].strip() == "---":
            end = i
            break
    if end is None:                      # frontmatter sin cerrar
        return meta, text

    clave_lista = None
    for raw in lines[1:end]:
        linea = raw.rstrip()
        if not linea.strip() or linea.strip().startswith("#"):
            continue
        if linea.lstrip().startswith("- ") and clave_lista:
            meta[clave_lista].append(_valor(linea.lstrip()[2:]))
            continue
        if ":" not in linea:
            continue
        k, _, v = linea.partition(":")
        k, v = k.strip(), v.strip()
        if not v:                        # lista en las líneas siguientes
            clave_lista = k
            meta[k] = []
        elif v.startswith("[") and v.endswith("]"):
            interior = v[1:-1].strip()
            meta[k] = [_valor(x) for x in interior.split(",") if x.strip()]
            clave_lista = None
        else:
            meta[k] = _valor(v)
            clave_lista = None

    return meta, "\n".join(lines[end + 1:]).lstrip("\n")


def _valor(s):
    s = s.strip().strip('"').strip("'")
    bajo = s.lower()
    if bajo in ("true", "yes"):
        return True
    if bajo in ("false", "no"):
        return False
    return s


def user_skills_dir():
    """Directorio escribible para skills que instale el usuario sin recompilar.

    En Android es filesDir (AGENTOS_HOME); en escritorio, ~/.agentos/skills.
    """
    home = os.environ.get("AGENTOS_HOME")
    if not home:
        home = os.path.join(os.path.expanduser("~"), ".agentos")
    return os.path.join(home, "skills")


def _listar(base):
    """Nombres de subcarpetas de `base`. [] si no se puede leer.

    En Chaquopy los assets del APK se extraen bajo demanda, así que un
    os.listdir() sobre el paquete puede devolver vacío aunque los archivos
    existan dentro del .imy. Por eso _dirs_del_paquete() tiene un plan B.
    """
    try:
        return sorted(os.listdir(base))
    except OSError:
        return []


def _dirs_del_paquete():
    """Skills empaquetados. Usa importlib.resources si el filesystem no los ve.

    ponytail: dos vías porque Chaquopy extrae bajo demanda; si algún día
    os.listdir() basta en Android, la segunda rama sobra y se borra.
    """
    nombres = _listar(SKILLS_DIR)
    if nombres:
        return [os.path.join(SKILLS_DIR, n) for n in nombres]
    try:
        from importlib import resources
        raiz = resources.files("skills")
        return [str(p) for p in sorted(raiz.iterdir(), key=lambda p: p.name)]
    except Exception:
        return []


def discover(base=None):
    """Carpetas con un SKILL.md, ordenadas. Sin `base`, busca en los dos sitios:
    los skills que vienen en el APK y los que el usuario haya instalado."""
    if base is not None:
        candidatos = [os.path.join(base, n) for n in _listar(base)]
    else:
        candidatos = _dirs_del_paquete()
        externos = user_skills_dir()
        candidatos += [os.path.join(externos, n) for n in _listar(externos)]

    out, vistos = [], set()
    for carpeta in candidatos:
        nombre = os.path.basename(carpeta.rstrip("/"))
        if nombre.startswith((".", "_")) or nombre in vistos:
            continue
        if not os.path.isdir(carpeta):
            continue
        if os.path.isfile(os.path.join(carpeta, "SKILL.md")):
            vistos.add(nombre)   # un skill del usuario no puede pisar uno del APK
            out.append(carpeta)
    return out


def load_one(carpeta, env=None):
    """Carga un skill. Devuelve dict con name/status/reason.

    status: "ok" | "skipped" | "error"
    Nunca lanza: el arranque del agente no puede depender de un skill de terceros.
    """
    env = os.environ if env is None else env
    nombre = os.path.basename(carpeta.rstrip("/"))
    res = {"name": nombre, "path": carpeta, "status": "error",
           "reason": "", "commands": [], "meta": {}}

    try:
        with open(os.path.join(carpeta, "SKILL.md"), encoding="utf-8") as f:
            meta, cuerpo = parse_frontmatter(f.read())
    except Exception as e:
        res["reason"] = "SKILL.md ilegible: %s" % e
        return res

    meta.setdefault("name", nombre)
    res["meta"], res["doc"] = meta, cuerpo

    if meta.get("enabled") is False:
        res["status"], res["reason"] = "skipped", "enabled: false"
        return res

    faltan = [k for k in (meta.get("requires-env") or []) if not env.get(k)]
    if faltan:
        res["status"] = "skipped"
        res["reason"] = "faltan env: %s" % ", ".join(faltan)
        return res

    py = os.path.join(carpeta, "skill.py")
    if not os.path.isfile(py):
        # Un skill puede ser solo documentación (prompt para el LLM, sin código).
        res["status"] = "ok"
        return res

    from core import registry
    antes = set(registry.COMMANDS)
    try:
        spec = importlib.util.spec_from_file_location(
            "agentos_skill_%s" % meta["name"], py)
        mod = importlib.util.module_from_spec(spec)
        mod.SKILL_META = meta
        mod.SKILL_DIR = carpeta
        sys.modules[spec.name] = mod
        spec.loader.exec_module(mod)
    except Exception as e:
        res["reason"] = "%s: %s" % (type(e).__name__, e)
        # Deja el registro como estaba: un skill a medias es peor que ninguno.
        for c in set(registry.COMMANDS) - antes:
            registry.COMMANDS.pop(c, None)
        return res

    res["commands"] = sorted(set(registry.COMMANDS) - antes)
    res["status"] = "ok"
    return res


def load_all(base=None, env=None):
    """Carga todos los skills. Devuelve la lista de resultados."""
    global LOADED
    LOADED = [load_one(c, env=env) for c in discover(base)]
    return LOADED


def escanear(base=None):
    """Lista los skills SIN importar su código. Para la UI: enseñar qué hay
    instalado no debe ejecutar nada de terceros ni depender del agente.

    Si ya se cargaron de verdad, devuelve ese estado real en su lugar.
    """
    if LOADED:
        return LOADED
    out = []
    for carpeta in discover(base):
        nombre = os.path.basename(carpeta.rstrip("/"))
        item = {"name": nombre, "path": carpeta, "status": "idle",
                "reason": "", "commands": [], "meta": {}}
        try:
            with open(os.path.join(carpeta, "SKILL.md"), encoding="utf-8") as f:
                meta, cuerpo = parse_frontmatter(f.read())
            meta.setdefault("name", nombre)
            item["meta"], item["doc"] = meta, cuerpo
            if meta.get("enabled") is False:
                item["status"], item["reason"] = "skipped", "enabled: false"
        except Exception as e:
            item["status"], item["reason"] = "error", "SKILL.md ilegible: %s" % e
        out.append(item)
    return out


def summary(resultados=None):
    """Resumen de una línea por skill, para el log de arranque."""
    r = LOADED if resultados is None else resultados
    marcas = {"ok": "✓", "skipped": "–", "error": "✗"}
    out = []
    for s in r:
        linea = "%s %s" % (marcas.get(s["status"], "?"), s["name"])
        if s["commands"]:
            linea += " (%s)" % ", ".join("/" + c for c in s["commands"])
        if s["reason"]:
            linea += " — %s" % s["reason"]
        out.append(linea)
    return out
