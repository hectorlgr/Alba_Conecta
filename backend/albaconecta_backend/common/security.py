"""JWT RS256 y JWKS de identidad."""

import base64
import json
import os
import time
from pathlib import Path
from typing import Annotated

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .config import settings
from .errors import ApiError

_bearer = HTTPBearer(auto_error=False)

ROLES = ("COLABORADOR", "EMPRESA", "GESTOR", "ADMIN_CONTENIDOS")

# Secreto efectivo: en dev sin SECRETO configurado, usar uno por defecto solo en memoria
def _b64(datos: bytes) -> str:
    return base64.urlsafe_b64encode(datos).decode().rstrip("=")


def _unb64(texto: str) -> bytes:
    return base64.urlsafe_b64decode(texto + "=" * (-len(texto) % 4))


def _b64entero(numero: int) -> str:
    longitud = max(1, (numero.bit_length() + 7) // 8)
    return _b64(numero.to_bytes(longitud, "big"))


def _leer_pem(valor: str) -> str:
    if not valor:
        return ""
    ruta = Path(valor)
    if ruta.is_file():
        return ruta.read_text(encoding="utf-8")
    return valor.replace("\\n", "\n")


def _crear_claves():
    privada_pem = _leer_pem(settings.jwt_private_key)
    publica_pem = _leer_pem(settings.jwt_public_key)

    if settings.es_dev and not privada_pem:
        privada = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        privada_pem = privada.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        ).decode()
        publica_pem = privada.public_key().public_bytes(
            serialization.Encoding.PEM,
            serialization.PublicFormat.SubjectPublicKeyInfo,
        ).decode()

    if not privada_pem or not publica_pem:
        raise RuntimeError("JWT_PRIVATE_KEY y JWT_PUBLIC_KEY son obligatorias fuera de desarrollo")

    privada = serialization.load_pem_private_key(privada_pem.encode(), password=None)
    publica = serialization.load_pem_public_key(publica_pem.encode())
    if not isinstance(privada, rsa.RSAPrivateKey) or not isinstance(publica, rsa.RSAPublicKey):
        raise RuntimeError("Las claves JWT deben ser RSA")
    return privada, publica


_PRIVADA, _PUBLICA = _crear_claves()


def _firmar(contenido: str) -> bytes:
    return _PRIVADA.sign(contenido.encode(), padding.PKCS1v15(), hashes.SHA256())


def emitir_access(usuario_id: str, rol: str, ttl: int | None = None) -> str:
    header = {"alg": "RS256", "typ": "JWT", "kid": settings.jwt_kid}
    payload = {"sub": usuario_id, "rol": rol, "exp": int(time.time()) + (ttl or settings.access_ttl)}
    encabezado_b64 = _b64(json.dumps(header, separators=(",", ":")).encode())
    payload_b64 = _b64(json.dumps(payload, separators=(",", ":")).encode())
    contenido = f"{encabezado_b64}.{payload_b64}"
    return f"{contenido}.{_b64(_firmar(contenido))}"


def decodificar(token: str) -> dict:
    try:
        encabezado_b64, payload_b64, firma_b64 = token.split(".", 2)
    except ValueError:
        raise ApiError(401, "TOKEN_INVALIDO", "El token no tiene el formato esperado.") from None
    try:
        header = json.loads(_unb64(encabezado_b64))
        payload = json.loads(_unb64(payload_b64))
        firma = _unb64(firma_b64)
    except (json.JSONDecodeError, ValueError):
        raise ApiError(401, "TOKEN_INVALIDO", "El token no tiene un formato válido.") from None
    if header.get("alg") != "RS256" or header.get("kid") != settings.jwt_kid:
        raise ApiError(401, "TOKEN_INVALIDO", "El algoritmo o kid del token no son válidos.")
    try:
        _PUBLICA.verify(firma, f"{encabezado_b64}.{payload_b64}".encode(), padding.PKCS1v15(), hashes.SHA256())
    except (InvalidSignature, ValueError):
        raise ApiError(401, "TOKEN_INVALIDO", "La firma del token no es válida.")
    if payload.get("exp", 0) < time.time():
        raise ApiError(401, "TOKEN_EXPIRADO", "El token expiró, renueva la sesión.")
    return payload


def jwks() -> dict:
    numeros = _PUBLICA.public_numbers()
    return {
        "keys": [
            {
                "kty": "RSA",
                "use": "sig",
                "alg": "RS256",
                "kid": settings.jwt_kid,
                "n": _b64entero(numeros.n),
                "e": _b64entero(numeros.e),
            }
        ]
    }


def usuario_actual(
    credenciales: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)] = None,
) -> dict:
    if credenciales is None:
        raise ApiError(401, "NO_AUTENTICADO", "Falta el encabezado Authorization: Bearer.")
    return decodificar(credenciales.credentials)


def requiere(*roles: str):
    """Dependencia que exige uno de los roles indicados (§4.5)."""

    def dependencia(usuario: Annotated[dict, Depends(usuario_actual)]) -> dict:
        if roles and usuario.get("rol") not in roles:
            raise ApiError(403, "PROHIBIDO", "No tienes permisos para esta operación.")
        return usuario

    return dependencia
