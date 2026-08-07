"""
Check del registro de comandos. Sin frameworks: `python3 test_registry.py`.

Existe porque el repo no tenía ni un test y el Paso 2 del plan parte
jarvis_core.py (1854 líneas). Partir eso a ciegas es temerario.
"""

import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from core import registry


def fresh():
    registry.clear()


def test_registro_y_lookup():
    fresh()
    visto = []

    @registry.command("prueba", desc="comando de prueba")
    def _p(ctx):
        visto.append(ctx["arg"])

    assert registry.has("prueba"), "no quedó registrado"
    assert registry.has("/prueba"), "debe aceptar el nombre con barra"
    assert registry.has("PRUEBA"), "debe ser case-insensitive"
    assert registry.get("noexiste") is None

    ok = registry.run("prueba", {"arg": "hola", "owner": True})
    assert ok is True, "run() debió devolver True"
    assert visto == ["hola"], "no llegó el arg: %r" % visto


def test_alias_comparten_spec():
    fresh()
    llamadas = []

    @registry.command("foto", "selfie", desc="foto")
    def _f(ctx):
        llamadas.append(ctx["cmd"])

    assert registry.get("foto") is registry.get("selfie"), "alias con specs distintos"
    registry.run("foto", {"cmd": "foto", "owner": True})
    registry.run("selfie", {"cmd": "selfie", "owner": True})
    assert llamadas == ["foto", "selfie"]
    # names() no repite alias
    assert registry.names() == ["foto"], registry.names()


def test_owner_bloquea():
    fresh()
    ejecutado = []

    @registry.command("borrar", owner=True, desc="peligroso")
    def _b(ctx):
        ejecutado.append(1)

    assert registry.run("borrar", {"owner": False}) is False, "un invitado NO debe poder"
    assert ejecutado == [], "se ejecutó pese a no ser dueño"
    assert registry.run("borrar", {"owner": True}) is True
    assert ejecutado == [1]


def test_duplicado_falla_ruidoso():
    fresh()

    @registry.command("choque", source="core")
    def _a(ctx):
        pass

    try:
        @registry.command("choque", source="skill-malo")
        def _b(ctx):
            pass
    except ValueError as e:
        assert "skill-malo" in str(e) and "core" in str(e), str(e)
    else:
        raise AssertionError("un skill pisó un comando del core en silencio")


def test_comando_inexistente_no_revienta():
    fresh()
    assert registry.run("nada", {"owner": True}) is False


def test_excepcion_sube():
    """Si el comando falla, handle() debe enterarse; no la tragamos aquí."""
    fresh()

    @registry.command("rompe")
    def _r(ctx):
        raise RuntimeError("boom")

    try:
        registry.run("rompe", {"owner": True})
    except RuntimeError:
        pass
    else:
        raise AssertionError("la excepción no llegó a handle()")


def test_help_y_clear_por_source():
    fresh()

    @registry.command("uno", desc="el uno", source="core")
    def _1(ctx):
        pass

    @registry.command("dos", desc="el dos", owner=True, source="skill-x")
    def _2(ctx):
        pass

    assert len(registry.help_lines(owner=True)) == 2
    assert len(registry.help_lines(owner=False)) == 1, "no debe mostrar los de dueño"

    registry.clear(source="skill-x")
    assert registry.has("uno") and not registry.has("dos"), "clear por source falló"


def test_integracion_handle_enruta_al_registro():
    """El check que de verdad importa: /cmd en Telegram llega a la función.

    Importa jarvis_core de verdad (no un mock) y le sustituye los helpers que
    tocan red/hardware. Si este falla, el enganche de handle() está roto.
    """
    os.environ.setdefault("AGENTOS_HOME", "/tmp/agentos_test")
    import jarvis_core as jc

    enviados = []
    jc.send = lambda chat, txt: enviados.append(txt)
    jc.sendf = lambda chat, txt: enviados.append(txt)
    jc.sh = lambda cmd, timeout=120: '{"percentage": 87, "status": "CHARGING", "temperature": 30.0}'
    jc.OWNER_ID = "1"  # sin dueño configurado, todo comando de dueño se rechaza

    # /bateria está migrado al registro y es owner-only (lo era en OWNER_ONLY)
    enviados.clear()
    jc.handle("1", "1", "/bateria")
    assert enviados and "87" in enviados[0], "no llegó al comando migrado: %r" % enviados

    # el permiso del registro debe seguir vigente tras sacarlo de OWNER_ONLY
    assert "bateria" not in jc.OWNER_ONLY, "duplicado: el permiso vive en el registro"
    assert registry.get("bateria")["owner"] is True, "se perdió el owner-only al migrar"

    # un invitado no puede con los owner-only del registro
    enviados.clear()
    jc.handle("999999", "999999", "/linterna on")
    assert enviados and "dueño" in enviados[0].lower(), "invitado ejecutó owner-only: %r" % enviados

    # lo NO migrado sigue funcionando por la cadena de elif
    enviados.clear()
    jc.handle(jc.OWNER_ID or "1", "1", "/help")
    assert enviados, "la cadena de elif dejó de responder"


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
    fresh()
    print("\n%s" % ("TODO OK" if not fallos else "%d FALLO(S)" % fallos))
    sys.exit(1 if fallos else 0)
