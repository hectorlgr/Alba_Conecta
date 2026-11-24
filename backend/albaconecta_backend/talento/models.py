"""Modelos SQL del catálogo público de talento."""

from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from ..common.db import BaseTalento


class Apoyo(BaseTalento):
    __tablename__ = "apoyo"

    codigo: Mapped[str] = mapped_column(String(20), primary_key=True)
    nombre: Mapped[str] = mapped_column(String(120), nullable=False)
    descripcion: Mapped[str] = mapped_column(String(500), nullable=False)


class Candidato(BaseTalento):
    __tablename__ = "candidato"

    codigo_publico: Mapped[str] = mapped_column(String(20), primary_key=True)
    iniciales: Mapped[str] = mapped_column(String(20), nullable=False)
    resumen: Mapped[str] = mapped_column(String(500), nullable=False)
    publicado: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    eliminado: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    fecha_actualizacion: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class CandidatoApoyo(BaseTalento):
    __tablename__ = "candidato_apoyo"

    candidato_codigo: Mapped[str] = mapped_column(
        String(20), ForeignKey("candidato.codigo_publico", ondelete="CASCADE"), primary_key=True
    )
    apoyo_codigo: Mapped[str] = mapped_column(
        String(20), ForeignKey("apoyo.codigo", ondelete="CASCADE"), primary_key=True
    )
