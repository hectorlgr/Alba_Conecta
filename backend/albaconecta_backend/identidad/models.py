"""Modelos SQL de `ms-identidad` (§4.5)."""

from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from ..common.db import BaseIdentidad as Base


class Usuario(Base):
    __tablename__ = "usuario"

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    nombre: Mapped[str] = mapped_column(String(120), nullable=False)
    # Único a nivel de base de datos: además del chequeo en la ruta, la BD impide duplicados.
    email: Mapped[str] = mapped_column(String(200), unique=True, index=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(300), nullable=False)
    tipo: Mapped[str] = mapped_column(String(20), nullable=False)
    rol: Mapped[str] = mapped_column(String(30), nullable=False)
    organizacion: Mapped[str | None] = mapped_column(String(120), default=None)
    activo: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    fecha_creacion: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class RefreshToken(Base):
    """ADR-017: rotación con detección de reutilización."""

    __tablename__ = "refresh_token"

    token: Mapped[str] = mapped_column(String(64), primary_key=True)
    usuario_id: Mapped[str] = mapped_column(ForeignKey("usuario.id", ondelete="CASCADE"), index=True)
    # Epoch en segundos: mismo cálculo que la lógica original, sin conversiones de zona.
    expira: Mapped[float] = mapped_column(nullable=False)
    revocado_en: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), default=None)
