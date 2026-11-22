"""Configuración única leída de variables de entorno (Fase 0)."""

import os
from dataclasses import dataclass
from datetime import timedelta


def _lista(valor: str) -> tuple[str, ...]:
    return tuple(x.strip() for x in valor.split(",") if x.strip())


@dataclass(frozen=True)
class Settings:
    entorno: str = os.getenv("ENTORNO", "dev")
    # Fase 3 reemplaza esto por clave privada RS256; aquí firma el token provisional.
    secreto: str = os.getenv("SECRETO", "")
    demo_password: str = os.getenv("DEMO_PASSWORD", "")
    jwt_private_key: str = os.getenv("JWT_PRIVATE_KEY", "")
    jwt_public_key: str = os.getenv("JWT_PUBLIC_KEY", "")
    jwt_kid: str = os.getenv("JWT_KID", "albaconecta-dev")
    access_ttl: int = int(os.getenv("ACCESS_TTL", "900"))
    refresh_ttl: int = int(os.getenv("REFRESH_TTL", str(30 * 24 * 3600)))
    # ADR-012: base URL interna de ms-contenidos; vacía = no se valida la referencia.
    contenidos_url: str = os.getenv("CONTENIDOS_BASE_URL", "")
    cors_origenes: tuple[str, ...] = _lista(os.getenv("CORS_ORIGENES", "*"))
    sync_pagina: int = int(os.getenv("SYNC_PAGINA", "25"))
    # SQLite local por defecto; en desarrollo DATABASE_URL puede actuar como fallback único.
    database_url: str = os.getenv("DATABASE_URL", "sqlite:///./albaconecta.db")
    database_url_contenidos: str = os.getenv("DATABASE_URL_CONTENIDOS", "")
    database_url_vinculacion: str = os.getenv("DATABASE_URL_VINCULACION", "")
    database_url_identidad: str = os.getenv("DATABASE_URL_IDENTIDAD", "")
    database_url_talento: str = os.getenv("DATABASE_URL_TALENTO", "")

    @property
    def es_dev(self) -> bool:
        return self.entorno == "dev"

    @property
    def access_ttl_delta(self) -> timedelta:
        return timedelta(seconds=self.access_ttl)


settings = Settings()


def validar_secreto(settings: Settings) -> None:
    """Valida que el secreto HMAC sea seguro para el entorno actual."""
    secreto = settings.secreto
    entorno = settings.entorno.strip().lower()
    if entorno not in ("dev", "test", "prod", "produccion", "production"):
        raise RuntimeError("ENTORNO inválido: usa dev, test o production")

    if entorno in ("dev", "test"):
        if entorno == "dev" and not settings.demo_password:
            raise RuntimeError("DEMO_PASSWORD no configurada: define la variable de entorno en desarrollo")
        if not secreto:
            # En dev, permitir secreto vacío y usar uno por defecto solo en memoria
            return
        if secreto == "dev-secreto-cambiar-en-produccion" and len(secreto) < 32:
            # Advertencia en dev si usan el valor conocido
            import logging
            logging.warning("Usando secreto HMAC por defecto inseguro; no usar en producción")
    else:
        # En producción: exigir secreto explícito, rechazar conocido y cortos
        if not secreto:
            raise RuntimeError("SECRETO no configurado: define la variable de entorno SECRETO en producción")
        if secreto == "dev-secreto-cambiar-en-produccion":
            raise RuntimeError("SECRETO inseguro: el valor por defecto no está permitido en producción")
        if len(secreto) < 32:
            raise RuntimeError("SECRETO demasiado corto: mínimo 32 caracteres en producción")


def url_base_datos(settings: Settings, dominio: str) -> str:
    """Resuelve la URL de un dominio; falla en producción si falta su variable."""
    entorno = settings.entorno.strip().lower()
    urls = {
        "contenidos": settings.database_url_contenidos,
        "vinculacion": settings.database_url_vinculacion,
        "identidad": settings.database_url_identidad,
        "talento": settings.database_url_talento,
    }
    if dominio not in urls:
        raise RuntimeError(f"Dominio de datos desconocido: {dominio}")

    if entorno in ("prod", "produccion", "production"):
        if not urls[dominio]:
            raise RuntimeError(
                f"DATABASE_URL_{dominio.upper()} no configurada: falta la base de datos de {dominio}"
            )
        return urls[dominio]

    return urls[dominio] or settings.database_url


def validar_claves_rsa(settings: Settings) -> None:
    """Valida formato PEM y correspondencia RSA privada/pública."""
    from cryptography.hazmat.primitives import serialization

    # Import local: security importa config a nivel de módulo (evita ciclo).
    from .security import _leer_pem

    if not settings.jwt_private_key or not settings.jwt_public_key:
        return

    # D-004 admite PEM literal o ruta a archivo montado.
    privada_pem = _leer_pem(settings.jwt_private_key)
    publica_pem = _leer_pem(settings.jwt_public_key)
    try:
        privada = serialization.load_pem_private_key(privada_pem.encode(), password=None)
        publica = serialization.load_pem_public_key(publica_pem.encode())
    except (ValueError, TypeError):
        raise RuntimeError("Las claves JWT deben tener formato PEM RSA válido") from None

    if getattr(privada, "key_size", 0) < 2048:
        raise RuntimeError("La clave privada RSA debe tener al menos 2048 bits")
    if privada.public_key().public_numbers() != publica.public_numbers():
        raise RuntimeError("JWT_PRIVATE_KEY y JWT_PUBLIC_KEY no corresponden al mismo par RSA")


def validar_configuracion(settings: Settings, exige_clave_privada: bool = True) -> None:
    """Valida configuración crítica común a todos los servicios.

    Solo identidad firma tokens: los demás consumidores verifican con la clave
    pública y nunca reciben la privada (§7).
    """
    validar_secreto(settings)

    entorno = settings.entorno.strip().lower()
    if entorno in ("prod", "produccion", "production"):
        if "*" in settings.cors_origenes:
            raise RuntimeError("CORS_ORIGENES no puede usar '*' en producción")
        if exige_clave_privada and not settings.jwt_private_key:
            raise RuntimeError("JWT_PRIVATE_KEY no configurada: define la clave privada RS256")
        if not settings.jwt_public_key:
            raise RuntimeError("JWT_PUBLIC_KEY no configurada: define la clave pública RS256")
        if not settings.jwt_kid or settings.jwt_kid == "albaconecta-dev":
            raise RuntimeError("JWT_KID no configurado con un identificador de clave seguro")
        validar_claves_rsa(settings)
