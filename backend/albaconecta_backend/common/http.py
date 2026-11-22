"""Utilidades HTTP transversales: X-Request-Id, paginación y rate limit (ADR-014)."""

import time
import uuid
from collections import defaultdict
from typing import Annotated, Any

from fastapi import FastAPI, Query, Request
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import Response

from . import db
from .errors import ApiError

Pagina = Annotated[int, Query(ge=1, description="Página, base 1")]
Limite = Annotated[int, Query(ge=1, le=50, description="Elementos por página (máx. 50)")]


class RequestIdMiddleware(BaseHTTPMiddleware):
    """Respeta el X-Request-Id entrante o genera uno; siempre lo devuelve."""

    async def dispatch(self, request: Request, call_next):
        request.state.request_id = request.headers.get("X-Request-Id") or uuid.uuid4().hex
        response: Response = await call_next(request)
        response.headers["X-Request-Id"] = request.state.request_id
        return response


def paginar(items: list[Any], page: int, limit: int) -> dict[str, Any]:
    total = len(items)
    inicio = (page - 1) * limit
    return {
        "items": items[inicio : inicio + limit],
        "total": total,
        "page": page,
        "limit": limit,
        "has_more": inicio + limit < total,
    }


_historial: dict[str, list[float]] = defaultdict(list)


def rate_limit(maximo: int, ventana: int = 60, nombre: str = "general"):
    """Límite en memoria por proceso: válido para una réplica del MVP."""

    def dependencia(request: Request) -> None:
        # shortcut: clave solo por IP; login por IP+email llega en Fase 5
        clave = f"{nombre}:{request.client.host if request.client else 'sin-ip'}"
        ahora = time.monotonic()
        marcas = [t for t in _historial[clave] if ahora - t < ventana]
        if len(marcas) >= maximo:
            espera = int(ventana - (ahora - marcas[0])) + 1
            raise ApiError(
                429,
                "LIMITE_EXCEDIDO",
                "Demasiadas peticiones, intenta más tarde.",
                headers={"Retry-After": str(espera)},
            )
        marcas.append(ahora)
        _historial[clave] = marcas

    return dependencia


def registrar_salud(app: FastAPI, servicio: str, dominio: str | None = None) -> None:
    """`live` siempre responde; `ready` comprueba la base del dominio (Fase 5).

    Se publican en la raíz y bajo el prefijo real del servicio, porque Traefik
    solo enruta `/api/v1/<dominio>/*` (§15 del plan).
    """

    def vivo() -> dict[str, str]:
        return {"servicio": servicio, "estado": "vivo"}

    def listo(respuesta: Response) -> dict[str, str]:
        if dominio is not None:
            try:
                # `def`, no `async def`: FastAPI lo corre en el threadpool y el
                # SELECT no bloquea el event loop.
                with db.motor(dominio).connect() as conexion:
                    conexion.execute(text("SELECT 1"))
            except SQLAlchemyError:
                respuesta.status_code = 503
                return {"servicio": servicio, "estado": "no_listo"}
        return {"servicio": servicio, "estado": "listo"}

    rutas = ["/health"] + ([f"/api/v1/{dominio}/health"] if dominio else [])
    for base in rutas:
        app.add_api_route(f"{base}/live", vivo, tags=["salud"])
        app.add_api_route(f"{base}/ready", listo, tags=["salud"])
