"""Modelos SQL persistentes de vinculación."""

from datetime import datetime

from sqlalchemy import JSON, Boolean, DateTime, Float, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from ..common.db import BaseVinculacion as Base


class Solicitud(Base):
    __tablename__ = "solicitud"

    id: Mapped[str] = mapped_column(String(40), primary_key=True)
    folio: Mapped[str] = mapped_column(String(16), unique=True, index=True, nullable=False)
    estado: Mapped[str] = mapped_column(String(30), nullable=False)
    nombre_contacto: Mapped[str] = mapped_column(String(120), nullable=False)
    organizacion: Mapped[str | None] = mapped_column(String(120), default=None)
    email: Mapped[str | None] = mapped_column(String(200), default=None)
    telefono: Mapped[str | None] = mapped_column(String(30), default=None)
    mensaje: Mapped[str] = mapped_column(String(2000), nullable=False)
    programa_id: Mapped[str | None] = mapped_column(String(32), default=None)
    producto_id: Mapped[str | None] = mapped_column(String(32), default=None)
    programa_titulo: Mapped[str | None] = mapped_column(String(200), default=None)
    producto_titulo: Mapped[str | None] = mapped_column(String(200), default=None)
    referencia_no_verificada: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    detalles: Mapped[list] = mapped_column(JSON, default=list, nullable=False)
    observaciones_internas: Mapped[list] = mapped_column(JSON, default=list, nullable=False)
    fecha_creacion: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    fecha_actualizacion: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class HistorialSolicitud(Base):
    __tablename__ = "historial_solicitud"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    solicitud_id: Mapped[str] = mapped_column(
        ForeignKey("solicitud.id", ondelete="CASCADE"),
        index=True,
        nullable=False,
    )
    estado_anterior: Mapped[str | None] = mapped_column(String(30), default=None)
    estado_nuevo: Mapped[str] = mapped_column(String(30), nullable=False)
    observacion: Mapped[str | None] = mapped_column(String(1000), default=None)
    autor_id: Mapped[str | None] = mapped_column(String(32), default=None)
    fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class IdempotenciaSolicitud(Base):
    __tablename__ = "idempotencia_solicitud"

    clave: Mapped[str] = mapped_column(String(36), primary_key=True)
    huella: Mapped[str] = mapped_column(String(64), nullable=False)
    solicitud_id: Mapped[str | None] = mapped_column(
        ForeignKey("solicitud.id", ondelete="SET NULL"),
        index=True,
        default=None,
    )
    expira: Mapped[float] = mapped_column(Float, nullable=False)
