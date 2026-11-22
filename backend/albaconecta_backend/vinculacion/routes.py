"""Rutas de `ms-vinculacion` (§4.4)."""

import uuid
from datetime import datetime
from typing import Annotated

from fastapi import APIRouter, Depends, Header, Response

from ..common.errors import ApiError
from ..common.http import Limite as LimiteQ
from ..common.http import Pagina as PaginaQ
from ..common.http import paginar, rate_limit
from ..common.security import requiere
from . import logic
from .data import TIPOS_COLABORACION, almacen
from .schemas import (
    CambioEstadoIn,
    Estado,
    HistorialOut,
    MetricasOut,
    ObservacionIn,
    Pagina,
    SolicitudGestorOut,
    SolicitudIn,
    SolicitudPublicaOut,
    TipoColaboracionOut,
)

router = APIRouter(prefix="/api/v1/vinculacion", tags=["vinculacion"])


def _exigir_uuid4(clave: str) -> None:
    try:
        valor = uuid.UUID(clave)
    except ValueError:
        valor = None
    if valor is None or valor.version != 4:
        raise ApiError(
            422,
            "VALIDACION",
            "El encabezado Idempotency-Key debe ser un UUID v4.",
            errors=[
                {
                    "field": "Idempotency-Key",
                    "code": "formato",
                    "message": "El encabezado Idempotency-Key debe ser un UUID v4.",
                }
            ],
        )


def _solicitud_o_404(id_: str) -> dict:
    solicitud = almacen.obtener(id_)
    if solicitud is None:
        raise ApiError(404, "NO_ENCONTRADO", f"No existe la solicitud {id_}.")
    return solicitud


@router.get("/tipos-colaboracion", response_model=list[TipoColaboracionOut])
async def tipos_colaboracion(solo_activos: bool = True):
    return [tipo for tipo in TIPOS_COLABORACION if tipo["activo"] or not solo_activos]


@router.post(
    "/solicitudes",
    status_code=201,
    response_model=SolicitudGestorOut,
    dependencies=[Depends(rate_limit(30, 60, "solicitudes"))],
)
async def crear_solicitud(
    datos: SolicitudIn,
    respuesta: Response,
    clave_idempotencia: Annotated[str, Header(alias="Idempotency-Key")],
):
    """Caso borde 5, 6 y 10: la referencia inválida no bloquea; la clave sí deduplica.
    
    La reserva de la clave de idempotencia es persistente y única en la base de datos.
    """
    _exigir_uuid4(clave_idempotencia)
    huella = logic.huella_payload(datos.model_dump(mode="json"))

    # 1. Reservar la clave atómicamente ANTES de verificar la referencia
    reservada, solicitud_id = almacen.reservar_idempotencia(clave_idempotencia, huella)

    if not reservada:
        # Clave ya existía con misma huella: devolver solicitud existente
        if solicitud_id is None:
            solicitud_id = await almacen.esperar_confirmacion(clave_idempotencia)
        if solicitud_id is None:
            raise ApiError(
                409,
                "IDEMPOTENCY_PENDING",
                "La clave está siendo procesada por otra solicitud.",
            )
        respuesta.status_code = 200
        return _solicitud_o_404(solicitud_id)

    try:
        # 2. Verificar referencia (puede ser lento, pero la clave ya está reservada)
        no_verificada, programa_titulo, producto_titulo = await logic.verificar_referencia(
            datos.programa_id, datos.producto_id
        )
        folio = logic.generar_folio(lambda candidato: almacen.por_folio(candidato) is not None)
        # 3. Crear la solicitud y confirmar la reserva en el mismo commit
        return almacen.crear(
            datos.model_dump(mode="json"),
            folio,
            no_verificada,
            programa_titulo,
            producto_titulo,
            clave_idempotencia,
        )
    except Exception:
        # Si falla cualquier cosa después de la reserva, liberar la clave
        almacen.liberar_idempotencia(clave_idempotencia)
        raise


@router.get(
    "/solicitudes/folio/{folio}",
    response_model=SolicitudPublicaOut,
    dependencies=[Depends(rate_limit(10, 60, "folio"))],
)
async def consultar_folio(folio: str):
    solicitud = almacen.por_folio(logic.normalizar_folio(folio))
    if solicitud is None:
        raise ApiError(404, "NO_ENCONTRADO", "No existe una solicitud con ese folio.")
    return almacen.publico(solicitud)


@router.get("/solicitudes", response_model=Pagina[SolicitudGestorOut])
async def listar_solicitudes(
    usuario: Annotated[dict, Depends(requiere("GESTOR"))],
    estado: Estado | None = None,
    tipo: str | None = None,
    desde: datetime | None = None,
    hasta: datetime | None = None,
    page: PaginaQ = 1,
    limit: LimiteQ = 20,
):
    items = almacen.listar()
    if estado:
        items = [s for s in items if s["estado"] == estado]
    if tipo:
        items = [s for s in items if any(d["tipo_colaboracion_codigo"] == tipo for d in s["detalles"])]
    if desde:
        items = [s for s in items if s["fecha_creacion"] >= desde]
    if hasta:
        items = [s for s in items if s["fecha_creacion"] <= hasta]
    items.sort(key=lambda s: s["fecha_creacion"], reverse=True)
    return paginar(items, page, limit)


@router.get("/metricas/resumen", response_model=MetricasOut)
async def resumen(usuario: Annotated[dict, Depends(requiere("GESTOR"))]):
    return almacen.metricas()


@router.get("/solicitudes/{id_}", response_model=SolicitudGestorOut)
async def detalle_solicitud(id_: str, usuario: Annotated[dict, Depends(requiere("GESTOR"))]):
    return _solicitud_o_404(id_)


@router.patch("/solicitudes/{id_}/estado", response_model=SolicitudGestorOut)
async def cambiar_estado(
    id_: str,
    datos: CambioEstadoIn,
    usuario: Annotated[dict, Depends(requiere("GESTOR"))],
):
    """Caso borde 11: transición repetida o fuera de la máquina → 409, sin historial."""
    solicitud = _solicitud_o_404(id_)
    if not logic.transicion_valida(solicitud["estado"], datos.estado):
        raise ApiError(
            409,
            "TRANSICION_INVALIDA",
            f"No se puede pasar de {solicitud['estado']} a {datos.estado}.",
        )
    almacen.cambiar_estado(solicitud, datos.estado, datos.observacion_interna, usuario["sub"])
    return solicitud


@router.post("/solicitudes/{id_}/observaciones", status_code=201, response_model=SolicitudGestorOut)
async def agregar_observacion(
    id_: str,
    datos: ObservacionIn,
    usuario: Annotated[dict, Depends(requiere("GESTOR"))],
):
    solicitud = _solicitud_o_404(id_)
    almacen.agregar_observacion(solicitud, datos.texto, usuario["sub"])
    return solicitud


@router.get("/solicitudes/{id_}/historial", response_model=list[HistorialOut])
async def historial(id_: str, usuario: Annotated[dict, Depends(requiere("GESTOR"))]):
    _solicitud_o_404(id_)
    return almacen.historial(id_)
