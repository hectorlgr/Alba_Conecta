"""Rutas de `ms-identidad` (§4.5)."""

from typing import Annotated

from fastapi import APIRouter, Depends, Response

from ..common.config import settings
from ..common.errors import ApiError
from ..common.http import Limite as LimiteQ
from ..common.http import Pagina as PaginaQ
from ..common.http import paginar, rate_limit
from ..common.security import emitir_access, jwks as generar_jwks, requiere, usuario_actual
from .data import HASH_FICTICIO, almacen, verificar
from .schemas import (
    EstadoIn,
    LoginIn,
    LogoutIn,
    Pagina,
    PerfilPatch,
    RefreshIn,
    RegistroIn,
    TokenOut,
    UsuarioOut,
)

router = APIRouter(prefix="/api/v1/identidad", tags=["identidad"])


def _salida(usuario: dict) -> dict:
    """Recorta el registro interno (nunca expone `password_hash`)."""
    return {k: v for k, v in usuario.items() if k in UsuarioOut.model_fields}


def _usuario_del_token(usuario: dict) -> dict:
    registro = almacen.obtener(usuario["sub"])
    if registro is None:
        raise ApiError(401, "TOKEN_INVALIDO", "El token no corresponde a un usuario existente.")
    return registro


def _token(usuario: dict) -> dict:
    return {
        "access_token": emitir_access(usuario["id"], usuario["rol"]),
        "refresh_token": almacen.emitir_refresh(usuario["id"]),
        "token_type": "Bearer",
        "expires_in": settings.access_ttl,
        "usuario": _salida(usuario),
    }


@router.post(
    "/auth/register",
    status_code=201,
    response_model=UsuarioOut,
    dependencies=[Depends(rate_limit(5, 60, "registro"))],
)
async def registrar(datos: RegistroIn):
    """ADR-013: solo COLABORADOR o EMPRESA; los roles privilegiados vienen por seed."""
    if almacen.por_email(datos.email) is not None:
        raise ApiError(409, "CORREO_YA_REGISTRADO", "Ya existe una cuenta con ese correo.")
    usuario = almacen.crear(
        datos.nombre,
        datos.email,
        datos.password,
        datos.tipo,
        datos.tipo,
        datos.organizacion,
    )
    return _salida(usuario)


@router.post(
    "/auth/login",
    response_model=TokenOut,
    dependencies=[Depends(rate_limit(5, 60, "login"))],
)
async def login(datos: LoginIn):
    usuario = almacen.por_email(datos.email)
    if usuario is None:
        # Se compara igual contra un hash ficticio para no filtrar cuentas por tiempo.
        verificar(datos.password, HASH_FICTICIO)
        raise ApiError(401, "NO_AUTENTICADO", "Correo o contraseña incorrectos.")
    if not verificar(datos.password, usuario["password_hash"]):
        raise ApiError(401, "NO_AUTENTICADO", "Correo o contraseña incorrectos.")
    if not usuario["activo"]:
        raise ApiError(403, "CUENTA_INACTIVA", "La cuenta está desactivada.")
    return _token(usuario)


@router.post(
    "/auth/refresh",
    response_model=TokenOut,
    dependencies=[Depends(rate_limit(30, 60, "refresh"))],
)
async def refrescar(datos: RefreshIn):
    usuario = almacen.canjear_refresh(datos.refresh_token)
    return _token(usuario)


@router.post("/auth/logout", status_code=204)
async def logout(
    usuario: Annotated[dict, Depends(usuario_actual)],
    datos: LogoutIn | None = None,
):
    if datos and datos.refresh_token:
        almacen.revocar(datos.refresh_token)
    else:
        almacen.revocar_todos(usuario["sub"])
    return Response(status_code=204)


@router.get("/auth/me", response_model=UsuarioOut)
async def perfil(usuario: Annotated[dict, Depends(usuario_actual)]):
    return _salida(_usuario_del_token(usuario))


@router.patch("/usuarios/me", response_model=UsuarioOut)
async def actualizar_perfil(datos: PerfilPatch, usuario: Annotated[dict, Depends(usuario_actual)]):
    registro = _usuario_del_token(usuario)
    registro.update({k: v for k, v in datos.model_dump(exclude_unset=True).items() if v is not None})
    almacen.guardar(registro)
    return _salida(registro)


@router.get("/usuarios", response_model=Pagina[UsuarioOut])
async def listar_usuarios(
    usuario: Annotated[dict, Depends(requiere("GESTOR"))],
    page: PaginaQ = 1,
    limit: LimiteQ = 20,
):
    items = almacen.listar_todos()
    return paginar([_salida(u) for u in items], page, limit)


@router.patch("/usuarios/{id_}/estado", response_model=UsuarioOut)
async def cambiar_estado(
    id_: str,
    datos: EstadoIn,
    usuario: Annotated[dict, Depends(requiere("GESTOR"))],
):
    """Casos borde 3 y 12: cuentas privilegiadas y último admin activo."""
    actor = _usuario_del_token(usuario)
    objetivo = almacen.obtener(id_)
    if objetivo is None:
        raise ApiError(404, "NO_ENCONTRADO", f"No existe el usuario {id_}.")

    if actor["id"] == objetivo["id"]:
        raise ApiError(403, "PROHIBIDO", "No puedes desactivar tu propia cuenta.")
    if actor["rol"] == "GESTOR" and objetivo["rol"] in ("GESTOR", "ADMIN_CONTENIDOS"):
        raise ApiError(403, "PROHIBIDO", "Un GESTOR no puede modificar cuentas privilegiadas.")
    # Defensa en profundidad (caso 12): hoy inalcanzable porque quien tiene permiso
    # para desactivar admins sería el propio admin (403 arriba), pero protege la invariante.
    if not datos.activo and objetivo["rol"] == "ADMIN_CONTENIDOS":
        otros = [
            u
            for u in almacen.listar_todos()
            if u["rol"] == "ADMIN_CONTENIDOS" and u["activo"] and u["id"] != objetivo["id"]
        ]
        if not otros:
            raise ApiError(409, "REGLA_NEGOCIO", "No se puede dejar el sistema sin ADMIN_CONTENIDOS activo.")

    objetivo["activo"] = datos.activo
    almacen.guardar(objetivo)
    if not datos.activo:
        almacen.revocar_todos(objetivo["id"])
    return _salida(objetivo)


@router.get("/.well-known/jwks.json")
async def jwks():
    return generar_jwks()
