"""Esquemas de `ms-vinculacion` (§4.4)."""

from typing import Literal

from pydantic import Field, model_validator

from ..common.errors import ApiError
from ..common.schemas import Modelo, Pagina, UtcDatetime  # noqa: F401  (Pagina se usa en las rutas)

# Mismas reglas y mensajes que ContactValidator.kt del cliente Android.
EMAIL = r"^[A-Za-z0-9_%+\-]+(?:\.[A-Za-z0-9_%+\-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9\-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}$"
TELEFONO = r"^(?:\+56\s?)?[2-9]\d{8}$"

Estado = Literal["RECIBIDA", "EN_REVISION", "CONTACTADO", "CONCRETADO", "DESCARTADO"]
TipoColaboracion = Literal["DONACION", "ALIANZA_RSE", "VOLUNTARIADO", "COMPRA", "CAPACITACION", "OTRO"]


class TipoColaboracionOut(Modelo):
    codigo: str
    nombre: str
    activo: bool


class DetalleIn(Modelo):
    tipo_colaboracion_codigo: TipoColaboracion
    organizacion: str | None = Field(default=None, max_length=120)
    descripcion: str | None = Field(default=None, max_length=500)


class SolicitudIn(Modelo):
    nombre_contacto: str = Field(min_length=2, max_length=120)
    organizacion: str | None = Field(default=None, max_length=120)
    email: str | None = Field(default=None, pattern=EMAIL)
    telefono: str | None = Field(default=None, pattern=TELEFONO)
    mensaje: str = Field(min_length=1, max_length=2000)
    acepta_privacidad: bool
    programa_id: str | None = None
    producto_id: str | None = None
    detalles: list[DetalleIn] = Field(min_length=1, max_length=6)

    @model_validator(mode="after")
    def _reglas_cruzadas(self):
        if not self.acepta_privacidad:
            raise ApiError(
                422,
                "VALIDACION",
                "Debes aceptar la política de privacidad.",
                errors=[{"field": "acepta_privacidad", "code": "requerido", "message": "Debes aceptar la política de privacidad."}],
            )
        if not self.email and not self.telefono:
            raise ApiError(
                422,
                "VALIDACION",
                "Indica al menos un medio de contacto.",
                errors=[{"field": "email", "code": "requerido", "message": "Indica correo o teléfono."}],
            )
        tipos = [detalle.tipo_colaboracion_codigo for detalle in self.detalles]
        if len(set(tipos)) != len(tipos):
            raise ApiError(
                422,
                "VALIDACION",
                "No repitas el mismo tipo de colaboración.",
                errors=[{"field": "detalles", "code": "duplicado", "message": "No repitas el mismo tipo de colaboración."}],
            )
        return self


class DetalleOut(Modelo):
    tipo_colaboracion_codigo: str
    organizacion: str | None = None
    descripcion: str | None = None


class SolicitudPublicaOut(Modelo):
    folio: str
    estado: str
    nombre_contacto: str
    organizacion: str | None = None
    email: str | None = None
    telefono: str | None = None
    detalles: list[DetalleOut]
    fecha_creacion: UtcDatetime
    fecha_actualizacion: UtcDatetime


class SolicitudGestorOut(SolicitudPublicaOut):
    id: str
    mensaje: str
    programa_id: str | None = None
    producto_id: str | None = None
    programa_titulo: str | None = None
    producto_titulo: str | None = None
    referencia_no_verificada: bool = False
    observaciones_internas: list[str] = Field(default_factory=list)


class CambioEstadoIn(Modelo):
    estado: Estado
    observacion_interna: str | None = Field(default=None, max_length=1000)

    @model_validator(mode="after")
    def _observacion_obligatoria(self):
        if self.estado == "DESCARTADO" and not (self.observacion_interna or "").strip():
            raise ApiError(
                422,
                "VALIDACION",
                "Para descartar una solicitud debes registrar una observación interna.",
                errors=[
                    {
                        "field": "observacion_interna",
                        "code": "requerido",
                        "message": "Para descartar una solicitud debes registrar una observación interna.",
                    }
                ],
            )
        return self


class ObservacionIn(Modelo):
    texto: str = Field(min_length=1, max_length=1000)


class HistorialOut(Modelo):
    estado_anterior: str | None = None
    estado_nuevo: str
    observacion: str | None = None
    autor_id: str | None = None
    fecha: UtcDatetime


class MetricasOut(Modelo):
    total: int
    por_estado: dict[str, int]
    por_tipo: dict[str, int]
    ultimos_30_dias: int
