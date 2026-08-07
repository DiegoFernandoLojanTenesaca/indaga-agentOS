"""Lo que cada agente sabe de lo suyo.

El agente de notas debería poder hablar de tus notas, el de listas de tus
listas. Los datos ya están en la base del agente; esto los resume en texto
para meterlos en el prompt del chat local.

Abre la base directamente en vez de importar `jarvis_core`: leer cuatro filas
no debería obligar a levantar el agente entero (que arranca hilos, red y
skills). Si la base no existe todavía, se devuelve cadena vacía.
"""

import os
import sqlite3

# Tope deliberadamente bajo: en un modelo de 1B cada línea de contexto se paga
# en segundos de prefill. Es un resumen para situarlo, no un volcado.
MAX_LINEAS = 8
MAX_CHARS = 700


def _ruta(home=None):
    home = home or os.environ.get("AGENTOS_HOME")
    if not home:
        return None
    ruta = os.path.join(home, "jarvis.db")
    return ruta if os.path.isfile(ruta) else None


def _consultar(sql, args=(), home=None):
    ruta = _ruta(home)
    if not ruta:
        return []
    try:
        con = sqlite3.connect("file:%s?mode=ro" % ruta, uri=True, timeout=5)
        try:
            return con.execute(sql, args).fetchall()
        finally:
            con.close()
    except Exception:
        return []


def _recortar(lineas):
    texto = "\n".join(lineas[:MAX_LINEAS])
    return texto[:MAX_CHARS]


def notas(home=None):
    filas = _consultar("SELECT t, ts FROM notas ORDER BY id DESC LIMIT ?", (MAX_LINEAS,), home=home)
    return _recortar(["- %s" % (t or "").strip() for t, _ in filas if t])


def listas(home=None):
    filas = _consultar("SELECT nombre, items FROM listas ORDER BY id DESC LIMIT ?", (MAX_LINEAS,), home=home)
    out = []
    for nombre, items in filas:
        trozos = [i for i in (items or "").split("\n") if i.strip()]
        out.append("- %s (%d elementos): %s" % (nombre, len(trozos), ", ".join(trozos[:5])))
    return _recortar(out)


def agenda(home=None):
    filas = _consultar(
        "SELECT texto, due FROM recordatorios WHERE hecho=0 ORDER BY due LIMIT ?",
        (MAX_LINEAS,), home=home)
    import datetime
    out = []
    for texto, due in filas:
        try:
            cuando = datetime.datetime.fromtimestamp(due).strftime("%d/%m %H:%M")
        except Exception:
            cuando = "?"
        out.append("- %s (%s)" % (texto, cuando))
    return _recortar(out)


def diario(home=None):
    filas = _consultar("SELECT fecha, texto FROM diario ORDER BY id DESC LIMIT 4", home=home)
    return _recortar(["- %s: %s" % (f, (t or "")[:120]) for f, t in filas])


# Qué tabla mira cada agente. Un agente que no esté aquí simplemente no lleva
# datos en el prompt; no es un error.
FUENTES = {
    "notas": ("tus notas guardadas", notas),
    "listas": ("tus listas", listas),
    "agenda": ("tus recordatorios pendientes", agenda),
    "diario": ("tu diario", diario),
}


def contexto(agente, home=None):
    """Resumen de los datos de ese agente, listo para el prompt. "" si no hay."""
    ficha = FUENTES.get((agente or "").lower())
    if not ficha:
        return ""
    etiqueta, fn = ficha
    try:
        cuerpo = fn(home)
    except Exception:
        return ""
    if not cuerpo.strip():
        return ""
    return "Esto es %s ahora mismo:\n%s" % (etiqueta, cuerpo)


if __name__ == "__main__":
    # Sin base de datos, todo devuelve vacío sin lanzar.
    os.environ.pop("AGENTOS_HOME", None)
    assert _ruta() is None
    assert notas() == "" and listas() == "" and agenda() == ""
    assert contexto("notas") == ""
    assert contexto("no-existe") == ""

    # Con una base de prueba, resume de verdad.
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        os.environ["AGENTOS_HOME"] = tmp
        con = sqlite3.connect(os.path.join(tmp, "jarvis.db"))
        con.execute("CREATE TABLE notas(id INTEGER PRIMARY KEY, chat TEXT, t TEXT, ts TEXT)")
        con.execute("INSERT INTO notas(chat,t,ts) VALUES('1','comprar pan','hoy')")
        con.execute("CREATE TABLE listas(id INTEGER PRIMARY KEY, chat TEXT, nombre TEXT, items TEXT, ts TEXT)")
        con.execute("INSERT INTO listas(chat,nombre,items,ts) VALUES('1','compras','pan\nleche','hoy')")
        con.commit(); con.close()
        assert "comprar pan" in contexto("notas"), contexto("notas")
        # Y también con la ruta pasada a mano, que es como la llama la app.
        os.environ.pop("AGENTOS_HOME")
        assert "comprar pan" in contexto("notas", home=tmp)
        assert contexto("notas") == ""   # sin ruta y sin variable, nada
        os.environ["AGENTOS_HOME"] = tmp
        assert "2 elementos" in contexto("listas"), contexto("listas")
        assert contexto("agenda") == ""   # tabla ausente, no revienta
    print("ok")
