"""Esquemas de `ms-talento` (§4.6, alcance acotado)."""

from typing import Literal

from pydantic import Field

from ..common.schemas import Modelo, Pagina, UtcDatetime  # noqa: F401  (Pagina se usa en las rutas)


class CandidatoOut(Modelo):
    codigo_publico: str
    iniciales: str
    resumen: str
    apoyos: list[str]
    publicado: bool
    eliminado: Literal[False] = False
    fecha_actualizacion: UtcDatetime


class ApoyoOut(Modelo):
    codigo: str
    nombre: str
    descripcion: str


class TareaIn(Modelo):
    apoyo_codigo: str = Field(min_length=3, max_length=20)
    descripcion: str | None = Field(default=None, max_length=300)


class TareaOut(Modelo):
    apoyo_codigo: str
    descripcion: str | None = None


class PuestoIn(Modelo):
    titulo: str = Field(min_length=3, max_length=120)
    descripcion: str | None = Field(default=None, max_length=1000)
    tareas: list[TareaIn] = Field(min_length=1, max_length=10)


class PuestoOut(Modelo):
    id: str
    titulo: str
    descripcion: str | None = None
    tareas: list[TareaOut]
    fecha_creacion: UtcDatetime


class ResultadoCompatibilidad(Modelo):
    codigo_publico: str
    puntaje: int
    explicacion: str
    apoyos_faltantes: list[str]


class CandidatoOmitido(Modelo):
    codigo_publico: str
    motivo: str


class CompatibilidadOut(Modelo):
    puesto_id: str
    resultados: list[ResultadoCompatibilidad]
    omitidos: list[CandidatoOmitido]


class DiagnosticoIn(Modelo):
    apoyos_interes: list[str] = Field(min_length=1, max_length=10)


class DiagnosticoOut(Modelo):
    id: str
    sugerencias: list[str]
    fecha_creacion: UtcDatetime
