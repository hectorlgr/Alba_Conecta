"""Esquemas de `ms-identidad` (§4.5)."""

import re
from typing import Literal

from pydantic import Field, field_validator, model_validator

from ..common.errors import ApiError
from ..common.schemas import Modelo, Pagina, UtcDatetime  # noqa: F401  (Pagina se reexporta)

# Mismas reglas que RegisterValidator.kt y ContactValidator.kt del cliente.
EMAIL = r"^[A-Za-z0-9_%+\-]+(?:\.[A-Za-z0-9_%+\-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9\-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,}$"
PASSWORD = r"^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).{8,}$"
MENSAJE_PASSWORD = "La contraseña debe tener al menos 8 caracteres, una minúscula, una mayúscula y un dígito"
_PASSWORD_RE = re.compile(PASSWORD)


class UsuarioOut(Modelo):
    id: str
    nombre: str
    email: str
    rol: str
    tipo: str
    organizacion: str | None = None
    activo: bool
    fecha_creacion: UtcDatetime


class RegistroIn(Modelo):
    nombre: str = Field(min_length=2, max_length=120)
    email: str = Field(pattern=EMAIL)
    password: str = Field(min_length=8, max_length=200)
    tipo: Literal["COLABORADOR", "EMPRESA"]
    organizacion: str | None = Field(default=None, max_length=120)

    @field_validator("email")
    @classmethod
    def _normalizar_email(cls, valor: str) -> str:
        return valor.strip().lower()

    @field_validator("password")
    @classmethod
    def _password_valida(cls, valor: str) -> str:
        # El motor de regex de Pydantic no soporta look-ahead; se valida con `re` de Python.
        if not _PASSWORD_RE.match(valor):
            raise ApiError(
                422,
                "VALIDACION",
                MENSAJE_PASSWORD,
                errors=[{"field": "password", "code": "formato", "message": MENSAJE_PASSWORD}],
            )
        return valor

    @model_validator(mode="after")
    def _empresa_requiere_organizacion(self):
        if self.tipo == "EMPRESA" and not (self.organizacion or "").strip():
            raise ApiError(
                422,
                "VALIDACION",
                "Una cuenta de empresa requiere organización.",
                errors=[{"field": "organizacion", "code": "requerido", "message": "Indica el nombre de la organización."}],
            )
        return self


class LoginIn(Modelo):
    email: str = Field(pattern=EMAIL)
    password: str = Field(min_length=1, max_length=200)

    @field_validator("email")
    @classmethod
    def _normalizar_email(cls, valor: str) -> str:
        return valor.strip().lower()


class RefreshIn(Modelo):
    refresh_token: str = Field(min_length=10)


class LogoutIn(Modelo):
    refresh_token: str | None = None


class TokenOut(Modelo):
    access_token: str
    refresh_token: str
    token_type: str = "Bearer"
    expires_in: int
    usuario: UsuarioOut


class PerfilPatch(Modelo):
    nombre: str | None = Field(default=None, min_length=2, max_length=120)
    organizacion: str | None = Field(default=None, max_length=120)


class EstadoIn(Modelo):
    activo: bool
