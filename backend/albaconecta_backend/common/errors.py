"""Errores uniformes RFC 7807 (`application/problem+json`) para los cuatro servicios."""

import logging
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException

logger = logging.getLogger("albaconecta")

# Catálogo de `code` (§4.2 del plan): estable para el mapeo en la app Android.
TITULOS: dict[str, str] = {
    "PETICION_INVALIDA": "Petición inválida",
    "NO_AUTENTICADO": "No autenticado",
    "TOKEN_EXPIRADO": "Token expirado",
    "TOKEN_INVALIDO": "Token inválido",
    "PROHIBIDO": "Prohibido",
    "CUENTA_INACTIVA": "Cuenta inactiva",
    "NO_ENCONTRADO": "No encontrado",
    "TRANSICION_INVALIDA": "Transición de estado inválida",
    "IDEMPOTENCY_CONFLICT": "Conflicto de idempotencia",
    "REGLA_NEGOCIO": "Regla de negocio",
    "CORREO_YA_REGISTRADO": "Correo ya registrado",
    "VALIDACION": "Datos inválidos",
    "SINCRONIZACION_FUTURA": "Sincronización futura",
    "LIMITE_EXCEDIDO": "Límite de peticiones excedido",
    "ERROR_INTERNO": "Error interno",
    "DEPENDENCIA_NO_DISPONIBLE": "Dependencia no disponible",
}

CODIGO_POR_ESTADO = {
    400: "PETICION_INVALIDA",
    401: "NO_AUTENTICADO",
    403: "PROHIBIDO",
    404: "NO_ENCONTRADO",
    405: "PETICION_INVALIDA",
    422: "VALIDACION",
    429: "LIMITE_EXCEDIDO",
}


class ApiError(Exception):
    """Error de negocio con `code` propio; se serializa como problem+json."""

    def __init__(
        self,
        status: int,
        code: str,
        detail: str,
        headers: dict[str, str] | None = None,
        errors: list[dict[str, Any]] | None = None,
    ) -> None:
        super().__init__(detail)
        self.status = status
        self.code = code
        self.detail = detail
        self.headers = headers
        self.errors = errors


def problema(
    request: Request,
    status: int,
    code: str,
    detail: str,
    errors: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    cuerpo: dict[str, Any] = {
        "type": f"https://albaconecta.cl/errors/{code.lower()}",
        "title": TITULOS.get(code, "Error"),
        "status": status,
        "detail": detail,
        "instance": request.url.path,
        "code": code,
        "request_id": getattr(request.state, "request_id", ""),
    }
    if errors is not None:
        cuerpo["errors"] = errors
    return cuerpo


def _errores_pydantic(exc: Exception) -> list[dict[str, Any]]:
    """Normaliza errores de Pydantic a `{field, code, message}`."""
    crudos = exc.errors() if hasattr(exc, "errors") else []
    resultado = []
    for error in crudos:
        ubicacion = error.get("loc", ())
        campo = ".".join(str(p) for p in ubicacion if p not in ("body", "query", "header", "path", "cookie"))
        resultado.append({"field": campo, "code": error.get("type", "invalido"), "message": error.get("msg", "")})
    return resultado


def registrar_manejadores(app: FastAPI) -> None:
    def responder(
        request: Request,
        status: int,
        code: str,
        detail: str,
        errors: list[dict[str, Any]] | None = None,
        headers: dict[str, str] | None = None,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=status,
            content=problema(request, status, code, detail, errors),
            media_type="application/problem+json",
            headers=headers,
        )

    @app.exception_handler(ApiError)
    async def _api_error(request: Request, exc: ApiError) -> JSONResponse:
        return responder(request, exc.status, exc.code, exc.detail, exc.errors, exc.headers)

    @app.exception_handler(RequestValidationError)
    async def _validacion_fastapi(request: Request, exc: RequestValidationError) -> JSONResponse:
        return responder(request, 422, "VALIDACION", "Revisa los datos enviados.", _errores_pydantic(exc))

    @app.exception_handler(ValidationError)
    async def _validacion_pydantic(request: Request, exc: ValidationError) -> JSONResponse:
        # Se lanza al validar cuerpos tipados como dict (admin de contenidos).
        return responder(request, 422, "VALIDACION", "Revisa los datos enviados.", _errores_pydantic(exc))

    @app.exception_handler(StarletteHTTPException)
    async def _http(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        code = CODIGO_POR_ESTADO.get(exc.status_code, "ERROR_INTERNO" if exc.status_code >= 500 else "PETICION_INVALIDA")
        return responder(request, exc.status_code, code, str(exc.detail), headers=getattr(exc, "headers", None))

    @app.exception_handler(Exception)
    async def _inesperado(request: Request, exc: Exception) -> JSONResponse:
        logger.exception("error no controlado path=%s request_id=%s", request.url.path, getattr(request.state, "request_id", ""))
        return responder(request, 500, "ERROR_INTERNO", "Ocurrió un error inesperado.")
