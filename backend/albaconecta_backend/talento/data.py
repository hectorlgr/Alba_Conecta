"""Datos de talento: catálogo público persistido; puestos/diagnóstico permanecen MVP en memoria."""

from datetime import datetime, timezone

from sqlalchemy import select

from ..common import db
from ..common.config import settings
from . import models  # noqa: F401
from .models import Apoyo, Candidato, CandidatoApoyo


def ahora() -> datetime:
    return datetime.now(timezone.utc).replace(microsecond=0)


APOYOS_SEED = [
    {"codigo": "AP-01", "nombre": "Lector de pantalla", "descripcion": "Software de apoyo para baja visión."},
    {"codigo": "AP-02", "nombre": "Intérprete de señas", "descripcion": "Acompañamiento en lengua de señas."},
    {"codigo": "AP-03", "nombre": "Adecuación de puesto", "descripcion": "Ajustes físicos del espacio de trabajo."},
    {"codigo": "AP-04", "nombre": "Transporte adaptado", "descripcion": "Traslado puerta a puerta."},
]

CANDIDATOS_SEED = [
    ("C-001", "M.S.", "Operaria de producción con experiencia en líneas de ensamblaje.", ["AP-01", "AP-02"]),
    ("C-002", "J.P.", "Auxiliar administrativo con formación técnica en contabilidad.", ["AP-02"]),
    ("C-003", "R.T.", "Perfil en evaluación, sin apoyos declarados todavía.", []),
    ("C-004", "L.G.", "Técnica en logística y control de inventario.", ["AP-01", "AP-03"]),
]


def semilla_catalogo() -> None:
    with db.sesion("talento") as sesion:
        if sesion.scalar(select(Apoyo).limit(1)) is None:
            sesion.add_all(Apoyo(**datos) for datos in APOYOS_SEED)
        if sesion.scalar(select(Candidato).limit(1)) is None:
            momento = ahora()
            for codigo, iniciales, resumen, apoyos in CANDIDATOS_SEED:
                sesion.add(Candidato(codigo_publico=codigo, iniciales=iniciales, resumen=resumen,
                                     publicado=True, eliminado=False, fecha_actualizacion=momento))
                sesion.add_all(CandidatoApoyo(candidato_codigo=codigo, apoyo_codigo=apoyo) for apoyo in apoyos)


def apoyos_publicos() -> list[dict]:
    with db.sesion("talento") as sesion:
        filas = sesion.scalars(select(Apoyo).order_by(Apoyo.codigo)).all()
        return [{"codigo": fila.codigo, "nombre": fila.nombre, "descripcion": fila.descripcion} for fila in filas]


def candidatos_publicos() -> list[dict]:
    with db.sesion("talento") as sesion:
        filas = sesion.scalars(
            select(Candidato).where(Candidato.publicado.is_(True), Candidato.eliminado.is_(False))
            .order_by(Candidato.codigo_publico)
        ).all()
        resultado = []
        for fila in filas:
            codigos = sesion.scalars(
                select(CandidatoApoyo.apoyo_codigo).where(CandidatoApoyo.candidato_codigo == fila.codigo_publico)
                .order_by(CandidatoApoyo.apoyo_codigo)
            ).all()
            resultado.append({
                "codigo_publico": fila.codigo_publico,
                "iniciales": fila.iniciales,
                "resumen": fila.resumen,
                "apoyos": list(codigos),
                "publicado": fila.publicado,
                "eliminado": False,
                "fecha_actualizacion": fila.fecha_actualizacion,
            })
        return resultado


if settings.entorno.lower() not in ("prod", "produccion", "production"):
    db.crear_tablas("talento")
semilla_catalogo()


class Almacen:
    def __init__(self) -> None:
        self.puestos: dict[str, dict] = {}
        self.diagnosticos: dict[str, dict] = {}
        self._secuencia_puesto = 0
        self._secuencia_diagnostico = 0

    def crear_puesto(self, empresa_id: str, datos: dict) -> dict:
        self._secuencia_puesto += 1
        puesto = {"id": f"pue-{self._secuencia_puesto}", "empresa_id": empresa_id,
                  "titulo": datos["titulo"], "descripcion": datos.get("descripcion"),
                  "tareas": datos["tareas"], "fecha_creacion": ahora()}
        self.puestos[puesto["id"]] = puesto
        return puesto

    def crear_diagnostico(self, apoyos_interes: list[str]) -> dict:
        self._secuencia_diagnostico += 1
        diagnostico = {"id": f"dia-{self._secuencia_diagnostico}", "apoyos_interes": apoyos_interes,
                       "fecha_creacion": ahora()}
        self.diagnosticos[diagnostico["id"]] = diagnostico
        return diagnostico


almacen = Almacen()
