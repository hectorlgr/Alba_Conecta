"""Fábrica común de aplicaciones FastAPI (Fase 0): CORS, request-id, errores y salud."""

from fastapi import APIRouter, FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .config import settings, validar_configuracion
from .errors import registrar_manejadores
from .http import RequestIdMiddleware, registrar_salud


def crear_app(titulo: str, router: APIRouter, servicio: str, dominio: str | None = None) -> FastAPI:
    # Solo identidad posee la clave privada; los demás servicios usan la pública.
    validar_configuracion(settings, exige_clave_privada=servicio == "ms-identidad")
    # Traefik sólo enruta /api/v1/<dominio>; los docs por defecto (/docs) quedan fuera.
    prefijo = f"/api/v1/{dominio or servicio.removeprefix('ms-')}"
    app = FastAPI(
        title=titulo,
        version="1.0.0",
        docs_url=f"{prefijo}/docs",
        openapi_url=f"{prefijo}/openapi.json",
        redoc_url=None,
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=list(settings.cors_origenes),
        allow_methods=["*"],
        allow_headers=["*"],
    )
    # Se agrega al final para quedar como middleware más externo y cubrir también los errores.
    app.add_middleware(RequestIdMiddleware)
    registrar_manejadores(app)
    registrar_salud(app, servicio, dominio)
    app.include_router(router)
    return app
