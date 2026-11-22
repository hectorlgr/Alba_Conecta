"""Rutas de `ms-contenidos` (§4.3)."""

import base64
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any, Callable

from fastapi import APIRouter, Depends, Response
from pydantic import BaseModel

from ..common.config import settings
from ..common.errors import ApiError
from ..common.http import Limite as LimiteQ
from ..common.http import Pagina as PaginaQ
from ..common.http import paginar, rate_limit
from ..common.security import requiere
from . import data
from .data import Coleccion, ahora
from .schemas import (
    Cambios,
    Categoria,
    CategoriaIn,
    CategoriaPatch,
    Historia,
    HistoriaIn,
    HistoriaPatch,
    Indicador,
    IndicadorIn,
    IndicadorPatch,
    Pagina,
    Programa,
    ProgramaIn,
    ProgramaPatch,
    Producto,
    ProductoIn,
    ProductoPatch,
    PublicacionIn,
    SyncRespuesta,
)

router = APIRouter(prefix="/api/v1/contenidos", tags=["contenidos"])


def _salida(item: dict, modelo: type[BaseModel]) -> dict:
    """Recorta el registro interno a los campos del contrato."""
    datos = {k: v for k, v in item.items() if k in modelo.model_fields}
    datos["eliminado"] = item.get("eliminado_en") is not None
    return datos


def _texto(item: dict) -> str:
    return " ".join(str(v) for v in item.values() if isinstance(v, str)).lower()


def _coincide(item: dict, q: str | None) -> bool:
    return q is None or q.lower() in _texto(item)


# --- Lectura pública ---


@router.get("/categorias", response_model=Pagina[Categoria])
async def listar_categorias(tipo: str | None = None, page: PaginaQ = 1, limit: LimiteQ = 20):
    items = [_salida(c, Categoria) for c in data.categorias.publicos() if tipo is None or c["tipo"] == tipo]
    return paginar(items, page, limit)


@router.get("/programas", response_model=Pagina[Programa])
async def listar_programas(
    categoria_id: str | None = None,
    q: str | None = None,
    page: PaginaQ = 1,
    limit: LimiteQ = 20,
):
    items = [
        _salida(p, Programa)
        for p in data.programas.publicos()
        if (categoria_id is None or p["categoria_id"] == categoria_id) and _coincide(p, q)
    ]
    items.sort(key=lambda x: x["fecha_actualizacion"], reverse=True)
    return paginar(items, page, limit)


@router.get("/programas/{id_}", response_model=Programa)
async def detalle_programa(id_: str):
    item = data.programas.visible(id_)
    if item is None:
        raise ApiError(404, "NO_ENCONTRADO", "El programa no existe o no está publicado.")
    return _salida(item, Programa)


@router.get("/historias", response_model=Pagina[Historia])
async def listar_historias(q: str | None = None, page: PaginaQ = 1, limit: LimiteQ = 20):
    # Regla §4.3: una historia solo es pública si está autorizada.
    items = [_salida(h, Historia) for h in data.historias.publicos() if h["autorizada"] and _coincide(h, q)]
    return paginar(items, page, limit)


@router.get("/historias/{id_}", response_model=Historia)
async def detalle_historia(id_: str):
    item = data.historias.visible(id_)
    if item is None or not item["autorizada"]:
        raise ApiError(404, "NO_ENCONTRADO", "La historia no existe o no está publicada.")
    return _salida(item, Historia)


@router.get("/indicadores", response_model=Pagina[Indicador])
async def listar_indicadores(page: PaginaQ = 1, limit: LimiteQ = 20):
    items = [_salida(i, Indicador) for i in data.indicadores.publicos()]
    return paginar(items, page, limit)


@router.get("/productos", response_model=Pagina[Producto])
async def listar_productos(
    categoria_id: str | None = None,
    disponible: bool | None = None,
    page: PaginaQ = 1,
    limit: LimiteQ = 20,
):
    items = [
        _salida(p, Producto)
        for p in data.productos.publicos()
        if (categoria_id is None or p["categoria_id"] == categoria_id)
        and (disponible is None or p["disponible"] == disponible)
    ]
    return paginar(items, page, limit)


@router.get("/productos/{id_}", response_model=Producto)
async def detalle_producto(id_: str):
    item = data.productos.visible(id_)
    if item is None:
        raise ApiError(404, "NO_ENCONTRADO", "El producto no existe o no está publicado.")
    return _salida(item, Producto)


# --- Sincronización incremental (casos borde 8 y 9) ---


def _codificar_cursor(desde: int) -> str:
    return base64.urlsafe_b64encode(str(desde).encode()).decode().rstrip("=")


def _leer_cursor(cursor: str) -> int:
    try:
        valor = int(base64.urlsafe_b64decode(cursor + "=" * (-len(cursor) % 4)).decode())
        if valor < 0:
            raise ValueError
    except Exception:
        raise ApiError(422, "VALIDACION", "El cursor no es válido.") from None
    return valor


@router.get(
    "/sync",
    response_model=SyncRespuesta,
    dependencies=[Depends(rate_limit(30, 60, "sync"))],
)
async def sincronizar(since: datetime | None = None, cursor: str | None = None):
    momento = ahora()
    if since is not None:
        marca = since if since.tzinfo is not None else since.replace(tzinfo=timezone.utc)
        if marca > momento + timedelta(minutes=5):
            raise ApiError(422, "SINCRONIZACION_FUTURA", "El parámetro `since` está en el futuro.")
        since = marca

    desde = _leer_cursor(cursor) if cursor else 0
    plano = [(nombre, item) for nombre, col in data.COLECCIONES.items() for item in col.cambios(since)]
    lote = plano[desde : desde + settings.sync_pagina]
    cambios: dict[str, list[dict]] = {nombre: [] for nombre in data.COLECCIONES}
    for nombre, item in lote:
        cambios[nombre].append(item)

    siguiente = _codificar_cursor(desde + settings.sync_pagina) if desde + settings.sync_pagina < len(plano) else None
    return {"server_time": momento, "next_cursor": siguiente, "cambios": Cambios(**cambios)}


# --- Gestión editorial (ADMIN_CONTENIDOS) ---


@dataclass(frozen=True)
class Recurso:
    ruta: str
    coleccion: Coleccion
    salida: type[BaseModel]
    entrada: type[BaseModel]
    parche: type[BaseModel]
    validar: Callable[[dict], None] | None = None
    antes_de_publicar: Callable[[dict], None] | None = None


def _validar_categoria(datos: dict) -> None:
    categoria_id = datos.get("categoria_id")
    if categoria_id and data.categorias.obtener(categoria_id) is None:
        raise ApiError(
            422,
            "VALIDACION",
            "La categoría indicada no existe.",
            errors=[{"field": "categoria_id", "code": "no_existe", "message": "La categoría indicada no existe."}],
        )


def _exigir_autorizada(item: dict) -> None:
    if not item.get("autorizada"):
        raise ApiError(409, "REGLA_NEGOCIO", "La historia requiere autorización firmada antes de publicarse.")


def _obtener_vivo(coleccion: Coleccion, id_: str, ruta: str) -> dict:
    item = coleccion.obtener(id_)
    if item is None or item["eliminado_en"] is not None:
        raise ApiError(404, "NO_ENCONTRADO", f"No existe {ruta}/{id_}.")
    return item


def registrar_escritura(recurso: Recurso) -> None:
    """Registra POST/PATCH/DELETE/publicación; los cinco recursos comparten la forma."""
    ruta = recurso.ruta
    col = recurso.coleccion
    protegido = [Depends(requiere("ADMIN_CONTENIDOS"))]

    @router.post(f"/{ruta}", status_code=201, response_model=recurso.salida, dependencies=protegido, tags=["admin"])
    async def crear(cuerpo: dict[str, Any]):
        datos = recurso.entrada.model_validate(cuerpo).model_dump()
        if recurso.validar:
            recurso.validar(datos)
        return _salida(col.agregar(datos), recurso.salida)

    @router.patch(f"/{ruta}/{{id_}}", response_model=recurso.salida, dependencies=protegido, tags=["admin"])
    async def actualizar(id_: str, cuerpo: dict[str, Any]):
        item = _obtener_vivo(col, id_, ruta)
        cambios = recurso.parche.model_validate(cuerpo).model_dump(exclude_unset=True)
        fusionado = {**item, **cambios}
        # Se valida el estado final completo, no solo el parche.
        recurso.entrada.model_validate({k: fusionado[k] for k in recurso.entrada.model_fields})
        if recurso.validar:
            recurso.validar(fusionado)
        item.update({**cambios, "fecha_actualizacion": ahora()})
        col.guardar(item)
        return _salida(item, recurso.salida)

    @router.delete(f"/{ruta}/{{id_}}", status_code=204, dependencies=protegido, tags=["admin"])
    async def eliminar(id_: str):
        item = _obtener_vivo(col, id_, ruta)
        item.update(eliminado_en=ahora(), publicado=False, fecha_actualizacion=ahora())
        col.guardar(item)
        return Response(status_code=204)

    @router.patch(f"/{ruta}/{{id_}}/publicacion", response_model=recurso.salida, dependencies=protegido, tags=["admin"])
    async def publicacion(id_: str, cuerpo: PublicacionIn):
        item = _obtener_vivo(col, id_, ruta)
        if cuerpo.publicado and recurso.antes_de_publicar:
            recurso.antes_de_publicar(item)
        item.update(publicado=cuerpo.publicado, fecha_actualizacion=ahora())
        col.guardar(item)
        return _salida(item, recurso.salida)


for _recurso in (
    Recurso("categorias", data.categorias, Categoria, CategoriaIn, CategoriaPatch),
    Recurso("programas", data.programas, Programa, ProgramaIn, ProgramaPatch, validar=_validar_categoria),
    Recurso("historias", data.historias, Historia, HistoriaIn, HistoriaPatch, antes_de_publicar=_exigir_autorizada),
    Recurso("indicadores", data.indicadores, Indicador, IndicadorIn, IndicadorPatch),
    Recurso("productos", data.productos, Producto, ProductoIn, ProductoPatch, validar=_validar_categoria),
):
    registrar_escritura(_recurso)
