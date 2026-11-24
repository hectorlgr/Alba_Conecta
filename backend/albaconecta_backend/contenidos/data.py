"""Acceso a datos de `ms-contenidos` (Fase 2: PostgreSQL/MySQL vía SQLAlchemy).

Los registros se mantienen en memoria como caché de lectura (son pocos y se leen
mucho) y cada escritura se persiste con `guardar()`. El contrato no cambia: las rutas
siguen trabajando con dicts.

`shortcut: un solo proceso (uvicorn sin --workers); con varios workers cada caché
vería datos distintos. Al escalar, cambiar a consultas por request.`
"""

from datetime import datetime, timezone

from sqlalchemy import select

from ..common import db
from ..common.config import settings
from . import models  # noqa: F401  (registra las tablas en Base.metadata)
from .models import Categoria, Historia, Indicador, Programa, Producto

if settings.entorno.lower() not in ("prod", "produccion", "production"):
    db.crear_tablas("contenidos")

def ahora() -> datetime:
    return datetime.now(timezone.utc).replace(microsecond=0)


def _a_dict(fila) -> dict:
    datos = {c.name: getattr(fila, c.name) for c in fila.__table__.columns}
    # SQLite devuelve las fechas sin zona horaria; el contrato exige UTC explícito.
    for clave in ("fecha_actualizacion", "eliminado_en"):
        valor = datos.get(clave)
        if valor is not None and valor.tzinfo is None:
            datos[clave] = valor.replace(tzinfo=timezone.utc)
    return datos


class Coleccion:
    def __init__(self, prefijo: str, modelo: type) -> None:
        self.prefijo = prefijo
        self.modelo = modelo
        self.items: dict[str, dict] = {}
        self._secuencia = 0
        with db.sesion("contenidos") as s:
            for fila in s.scalars(select(modelo)):
                self.items[fila.id] = _a_dict(fila)
                self._secuencia = max(self._secuencia, _numero(fila.id))

    def nuevo_id(self) -> str:
        self._secuencia += 1
        return f"{self.prefijo}-{self._secuencia}"

    def agregar(self, datos: dict) -> dict:
        item = {
            "id": self.nuevo_id(),
            "publicado": False,
            "eliminado_en": None,
            "fecha_actualizacion": ahora(),
            **datos,
        }
        self.items[item["id"]] = item
        self.guardar(item)
        return item

    def guardar(self, item: dict) -> None:
        """Inserta o actualiza la fila con el contenido actual del dict."""
        columnas = {c.name for c in self.modelo.__table__.columns}
        with db.sesion("contenidos") as s:
            s.merge(self.modelo(**{k: v for k, v in item.items() if k in columnas}))

    def obtener(self, id_: str) -> dict | None:
        return self.items.get(id_)

    def visible(self, id_: str) -> dict | None:
        """Vista pública: publicado y no eliminado."""
        item = self.items.get(id_)
        if item and item["publicado"] and item["eliminado_en"] is None:
            return item
        return None

    def publicos(self) -> list[dict]:
        return [item for item in self.items.values() if item["publicado"] and item["eliminado_en"] is None]

    def cambios(self, since: datetime | None) -> list[dict]:
        """Creados/actualizados desde `since`, con lápida si ya no es visible (§4.3)."""
        resultado = []
        for item in self.items.values():
            marca = item["eliminado_en"] or item["fecha_actualizacion"]
            if since is not None and marca <= since:
                continue
            if item["publicado"] and item["eliminado_en"] is None:
                resultado.append(a_salida(item))
            else:
                resultado.append({"id": item["id"], "eliminado": True, "fecha_actualizacion": marca})
        return resultado


def _numero(id_: str) -> int:
    try:
        return int(id_.rsplit("-", 1)[1])
    except (IndexError, ValueError):
        return 0


def a_salida(item: dict) -> dict:
    """Convierte el registro interno al modelo de respuesta (sin `eliminado_en`)."""
    datos = {k: v for k, v in item.items() if k != "eliminado_en"}
    datos["eliminado"] = item.get("eliminado_en") is not None
    return datos


categorias = Coleccion("cat", Categoria)
programas = Coleccion("prg", Programa)
historias = Coleccion("his", Historia)
indicadores = Coleccion("ind", Indicador)
productos = Coleccion("prod", Producto)

COLECCIONES: dict[str, Coleccion] = {
    "programas": programas,
    "historias": historias,
    "indicadores": indicadores,
    "productos": productos,
    "categorias": categorias,
}


def _sembrar() -> None:
    """Datos de demostración solo si la tabla está vacía (la BD es la fuente de verdad)."""
    if categorias.items:
        return

    for datos in (
        {"nombre": "Educación", "tipo": "PROGRAMA"},
        {"nombre": "Inclusión laboral", "tipo": "PROGRAMA"},
        {"nombre": "Salud y bienestar", "tipo": "PROGRAMA"},
        {"nombre": "Medio ambiente", "tipo": "PRODUCTO"},
    ):
        categorias.agregar({**datos, "publicado": True})

    semillas_programas = [
        ("Taller de oficios inclusivos", "Formación técnica para personas con discapacidad.", "cat-2"),
        ("Escuela de liderazgo comunitario", "Acompañamiento a dirigentes sociales.", "cat-1"),
        ("Huerta comunitaria Alba", "Producción sustentable en sedes vecinales.", "cat-4"),
        ("Red de apoyo a cuidadores", "Acompañamiento psicosocial a cuidadores.", "cat-3"),
        ("Programa de becas técnicas", "Financiamiento de estudios técnicos.", "cat-1"),
        ("Aulas de inclusión digital", "Alfabetización digital para adultos mayores.", "cat-1"),
    ]
    for titulo, descripcion, categoria_id in semillas_programas:
        programas.agregar(
            {
                "titulo": titulo,
                "descripcion": descripcion,
                "categoria_id": categoria_id,
                "imagen_url": None,
                "publicado": True,
            }
        )

    for titulo, resumen, autorizada in (
        ("De voluntaria a coordinadora", "Carolina relata su paso por el voluntariado.", True),
        ("Una empresa que contrató inclusión", "Testimonio de Alianza RSE.", True),
        ("Tejido productivo en Valparaíso", "Mujeres que sostienen el barrio.", True),
        ("Historia pendiente de autorización", "Borrador sin consentimiento firmado.", False),
    ):
        historias.agregar({"titulo": titulo, "resumen": resumen, "autorizada": autorizada, "publicado": True})

    for nombre, valor, unidad, periodo, fuente in (
        ("Personas capacitadas", 1240, "personas", "2026", "Registro interno CORPALIV"),
        ("Empresas aliadas", 37, "empresas", "2026", "Registro interno CORPALIV"),
        ("Horas de voluntariado", 8600, "horas", "2026", "Registro interno CORPALIV"),
        ("Sedes activas", 12, "sedes", "2026", "Registro interno CORPALIV"),
    ):
        indicadores.agregar(
            {
                "nombre": nombre,
                "valor": float(valor),
                "unidad": unidad,
                "periodo": periodo,
                "fuente": fuente,
                "publicado": True,
            }
        )

    for nombre, descripcion, categoria_id, disponible in (
        ("Kit de huerto urbano", "Insumos para huerto en departamento.", "cat-4", True),
        ("Curso de oficios básicos", "Capacitación de 40 horas.", "cat-2", True),
        ("Asesoría en inclusión laboral", "Diagnóstico y plan de adaptación.", "cat-2", True),
        ("Taller de liderazgo", "Taller de 8 sesiones para organizaciones.", "cat-1", False),
        ("Servicio de catering inclusivo", "Catering con empleo inclusivo.", "cat-4", True),
    ):
        productos.agregar(
            {
                "nombre": nombre,
                "descripcion": descripcion,
                "categoria_id": categoria_id,
                "disponible": disponible,
                "publicado": True,
            }
        )


_sembrar()
