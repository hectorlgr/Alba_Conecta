"""Modelos SQL de `ms-contenidos`.

Un esquema por recurso (y no una tabla con JSON) para que el DDL sea legible en
PostgreSQL/MySQL y se pueda consultar con SQL normal.
"""

from datetime import datetime

from sqlalchemy import Boolean, DateTime, Float, String
from sqlalchemy.orm import Mapped, mapped_column

from ..common.db import BaseContenidos as Base


class Contenido(Base):
    """Campos comunes a los cinco recursos editoriales."""

    __abstract__ = True

    id: Mapped[str] = mapped_column(String(32), primary_key=True)
    publicado: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    eliminado_en: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), default=None)
    fecha_actualizacion: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class Categoria(Contenido):
    __tablename__ = "categoria"

    nombre: Mapped[str] = mapped_column(String(120), nullable=False)
    tipo: Mapped[str] = mapped_column(String(20), nullable=False)


class Programa(Contenido):
    __tablename__ = "programa"

    titulo: Mapped[str] = mapped_column(String(200), nullable=False)
    descripcion: Mapped[str] = mapped_column(String(2000), nullable=False)
    categoria_id: Mapped[str | None] = mapped_column(String(32), default=None)
    imagen_url: Mapped[str | None] = mapped_column(String(500), default=None)


class Historia(Contenido):
    __tablename__ = "historia"

    titulo: Mapped[str] = mapped_column(String(200), nullable=False)
    resumen: Mapped[str] = mapped_column(String(2000), nullable=False)
    autorizada: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)


class Indicador(Contenido):
    __tablename__ = "indicador"

    nombre: Mapped[str] = mapped_column(String(120), nullable=False)
    valor: Mapped[float] = mapped_column(Float, nullable=False)
    unidad: Mapped[str] = mapped_column(String(40), nullable=False)
    periodo: Mapped[str] = mapped_column(String(20), nullable=False)
    fuente: Mapped[str] = mapped_column(String(200), nullable=False)


class Producto(Contenido):
    __tablename__ = "producto"

    nombre: Mapped[str] = mapped_column(String(120), nullable=False)
    descripcion: Mapped[str] = mapped_column(String(2000), nullable=False)
    categoria_id: Mapped[str | None] = mapped_column(String(32), default=None)
    disponible: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
