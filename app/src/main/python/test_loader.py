"""
Check del loader de skills. `python3 test_loader.py`.

Lo que de verdad importa aquí: que un skill de terceros mal escrito NO pueda
tumbar el arranque del agente ni dejar el registro a medias.
"""

import sys
import os
import shutil
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from skills import loader
from core import registry


def escribir(base, nombre, md, py=None):
    d = os.path.join(base, nombre)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "SKILL.md"), "w", encoding="utf-8") as f:
        f.write(md)
    if py is not None:
        with open(os.path.join(d, "skill.py"), "w", encoding="utf-8") as f:
            f.write(py)
    return d


def test_frontmatter_formatos():
    meta, cuerpo = loader.parse_frontmatter(
        "---\n"
        "name: demo\n"
        "description: Un skill de prueba\n"
        "role: owner\n"
        "enabled: true\n"
        "requires-env: [A_KEY, B_KEY]\n"
        "tags:\n"
        "  - uno\n"
        "  - dos\n"
        "---\n\n"
        "# Cuerpo\ntexto\n"
    )
    assert meta["name"] == "demo"
    assert meta["description"] == "Un skill de prueba"
    assert meta["enabled"] is True, "true debe ser booleano, no string"
    assert meta["requires-env"] == ["A_KEY", "B_KEY"], meta["requires-env"]
    assert meta["tags"] == ["uno", "dos"], meta["tags"]
    assert cuerpo.startswith("# Cuerpo"), repr(cuerpo[:20])


def test_frontmatter_ausente_o_roto():
    meta, cuerpo = loader.parse_frontmatter("solo texto, sin frontmatter")
    assert meta == {} and "solo texto" in cuerpo
    # frontmatter sin cerrar: no debe reventar
    meta, cuerpo = loader.parse_frontmatter("---\nname: x\nsin cierre")
    assert meta == {}, "un frontmatter sin cerrar no debe parsearse a medias"


def test_carga_ok_y_registra():
    registry.clear()
    base = tempfile.mkdtemp()
    try:
        escribir(base, "saluda",
                 "---\nname: saluda\ndescription: saluda\n---\ncuerpo",
                 "from core import registry\n"
                 "@registry.command('saluda', source='skill:saluda')\n"
                 "def _s(ctx):\n"
                 "    ctx['out'].append('hola')\n")
        res = loader.load_all(base=base)
        assert len(res) == 1 and res[0]["status"] == "ok", res
        assert res[0]["commands"] == ["saluda"], res[0]["commands"]
        salida = []
        assert registry.run("saluda", {"owner": True, "out": salida}) is True
        assert salida == ["hola"]
    finally:
        shutil.rmtree(base, ignore_errors=True)
        registry.clear()


def test_skill_roto_no_tumba_ni_ensucia():
    registry.clear()
    base = tempfile.mkdtemp()
    try:
        escribir(base, "malo",
                 "---\nname: malo\n---\ncuerpo",
                 "from core import registry\n"
                 "@registry.command('a_medias', source='skill:malo')\n"
                 "def _a(ctx):\n    pass\n"
                 "raise RuntimeError('explota al importar')\n")
        escribir(base, "sano",
                 "---\nname: sano\n---\ncuerpo",
                 "from core import registry\n"
                 "@registry.command('sano', source='skill:sano')\n"
                 "def _b(ctx):\n    pass\n")
        res = loader.load_all(base=base)
        estados = {r["name"]: r["status"] for r in res}
        assert estados["malo"] == "error", estados
        assert "explota al importar" in [r for r in res if r["name"] == "malo"][0]["reason"]
        # el sano se carga igual: un skill roto no bloquea a los demás
        assert estados["sano"] == "ok", estados
        # y el roto no deja comandos huérfanos registrados
        assert not registry.has("a_medias"), "quedó un comando de un skill que falló"
        assert registry.has("sano")
    finally:
        shutil.rmtree(base, ignore_errors=True)
        registry.clear()


def test_requires_env_desactiva_limpio():
    registry.clear()
    base = tempfile.mkdtemp()
    try:
        escribir(base, "necesita",
                 "---\nname: necesita\nrequires-env: [NO_EXISTE_ESTA_KEY]\n---\ncuerpo",
                 "from core import registry\n"
                 "@registry.command('jamas', source='skill:necesita')\n"
                 "def _n(ctx):\n    pass\n")
        res = loader.load_all(base=base, env={})
        assert res[0]["status"] == "skipped", res
        assert "NO_EXISTE_ESTA_KEY" in res[0]["reason"], res[0]["reason"]
        assert not registry.has("jamas"), "registró comandos pese a faltarle la env"

        # con la env presente, sí carga
        res = loader.load_all(base=base, env={"NO_EXISTE_ESTA_KEY": "x"})
        assert res[0]["status"] == "ok", res
        assert registry.has("jamas")
    finally:
        shutil.rmtree(base, ignore_errors=True)
        registry.clear()


def test_enabled_false_y_solo_doc():
    registry.clear()
    base = tempfile.mkdtemp()
    try:
        escribir(base, "apagado", "---\nname: apagado\nenabled: false\n---\nx",
                 "raise RuntimeError('no debería importarse')\n")
        escribir(base, "solodoc", "---\nname: solodoc\ndescription: solo prompt\n---\ncuerpo")
        res = {r["name"]: r for r in loader.load_all(base=base)}
        assert res["apagado"]["status"] == "skipped", res["apagado"]
        assert res["solodoc"]["status"] == "ok", "un skill sin skill.py es válido"
        assert res["solodoc"]["commands"] == []
    finally:
        shutil.rmtree(base, ignore_errors=True)
        registry.clear()


def test_discover_ignora_basura():
    base = tempfile.mkdtemp()
    try:
        os.makedirs(os.path.join(base, "_privado"))
        os.makedirs(os.path.join(base, "sin_skill_md"))
        escribir(base, "bueno", "---\nname: bueno\n---\nx")
        encontrados = [os.path.basename(p) for p in loader.discover(base)]
        assert encontrados == ["bueno"], encontrados
    finally:
        shutil.rmtree(base, ignore_errors=True)


def test_skills_del_usuario_sin_recompilar():
    """Un skill puesto en AGENTOS_HOME/skills se carga sin tocar el APK.

    Es la mitad del 'injertable': poder añadir capacidades sin rebuild.
    """
    registry.clear()
    home = tempfile.mkdtemp()
    previo = os.environ.get("AGENTOS_HOME")
    os.environ["AGENTOS_HOME"] = home
    try:
        externos = loader.user_skills_dir()
        os.makedirs(externos, exist_ok=True)
        escribir(externos, "delusuario",
                 "---\nname: delusuario\ndescription: instalado en caliente\n---\nx",
                 "from core import registry\n"
                 "@registry.command('miskill', source='skill:delusuario')\n"
                 "def _m(ctx):\n    pass\n")
        rutas = [os.path.basename(p) for p in loader.discover()]
        assert "delusuario" in rutas, "no encontró el skill del usuario: %s" % rutas
        # y los del APK siguen apareciendo
        assert "telefono" in rutas, "se perdieron los skills empaquetados: %s" % rutas
    finally:
        if previo is None:
            os.environ.pop("AGENTOS_HOME", None)
        else:
            os.environ["AGENTOS_HOME"] = previo
        shutil.rmtree(home, ignore_errors=True)
        registry.clear()


def test_usuario_no_pisa_los_del_apk():
    """Un skill del usuario con el mismo nombre no debe suplantar al del APK."""
    registry.clear()
    home = tempfile.mkdtemp()
    previo = os.environ.get("AGENTOS_HOME")
    os.environ["AGENTOS_HOME"] = home
    try:
        externos = loader.user_skills_dir()
        os.makedirs(externos, exist_ok=True)
        escribir(externos, "telefono", "---\nname: telefono\n---\nimpostor")
        rutas = loader.discover()
        cuantos = [os.path.basename(p) for p in rutas].count("telefono")
        assert cuantos == 1, "aparece %d veces, debería ser 1" % cuantos
        # el que gana es el del paquete, no el del usuario
        elegido = [p for p in rutas if os.path.basename(p) == "telefono"][0]
        assert home not in elegido, "el skill del usuario suplantó al del APK"
    finally:
        if previo is None:
            os.environ.pop("AGENTOS_HOME", None)
        else:
            os.environ["AGENTOS_HOME"] = previo
        shutil.rmtree(home, ignore_errors=True)
        registry.clear()


def test_skill_telefono_real():
    """El skill que vive en el repo debe cargar y registrar sus comandos."""
    registry.clear()
    os.environ.setdefault("AGENTOS_HOME", "/tmp/agentos_test")
    import jarvis_core  # dispara load_skills() al final del módulo

    for c in ("foto", "selfie", "ubicacion", "bateria", "linterna", "copia", "pega"):
        assert registry.has(c), "el skill 'telefono' no registró /%s" % c
    assert registry.get("foto") is registry.get("selfie"), "alias roto"
    assert registry.get("foto")["owner"] is True, "los de hardware son de dueño"
    assert registry.get("foto")["source"] == "skill:telefono", registry.get("foto")["source"]


if __name__ == "__main__":
    fallos = 0
    for nombre, fn in sorted(globals().items()):
        if nombre.startswith("test_") and callable(fn):
            try:
                fn()
                print("  ok   %s" % nombre)
            except AssertionError as e:
                fallos += 1
                print("  FALLA %s: %s" % (nombre, e))
            except Exception as e:
                fallos += 1
                print("  ERROR %s: %s: %s" % (nombre, type(e).__name__, e))
    print("\n%s" % ("TODO OK" if not fallos else "%d FALLO(S)" % fallos))
    sys.exit(1 if fallos else 0)
