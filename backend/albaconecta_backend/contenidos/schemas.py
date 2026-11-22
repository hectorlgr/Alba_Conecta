"""Esquemas de `ms-contenidos` (§4.3)."""

from typing import Literal

from pydantic import Field

from ..common.schemas import Modelo, Pagina, UtcDatetime


class Lapida(Modelo):
    """Elemento removido de la vista pública (§4.3 / caso borde 9)."""

    id: str
    eliminado: Literal[True] = True
    fecha_actualizacion: UtcDatetime


class Categoria(Modelo):
    id: str
    nombre: str
    tipo: str
    publicado: bool
    eliminado: Literal[False] = False
    fecha_actualizacion: UtcDatetime


class Programa(Modelo):
    id: str
    titulo: str
    descripcion: str
    categoria_id: str | None = None
    imagen_url: str | None = None
    publicado: bool
    eliminado: Literal[False] = False
    fecha_actualizacion: UtcDatetime


class Historia(Modelo):
    id: str
    titulo: str
    resumen: str
    autorizada: bool
    publicado: bool
    eliminado: Literal[False] = False
    fecha_actualizacion: UtcDatetime


class Indicador(Modelo):
    id: str
    nombre: str
    valor: float
    unidad: str
    periodo: str
    fuente: str
    publicado: bool
    eliminado: Literal[False] = False
    fecha_actualizacion: UtcDatetime


class Producto(Modelo):
    id: str
    nombre: str
    descripcion: str
    categoria_id: str | None = None
    disponible: bool
    publicado: bool
    eliminado: Literal[False] = False
    fecha_actualizacion: UtcDatetime


# --- Entradas de administración (ADMIN_CONTENIDOS) ---

class ProgramaIn(Modelo):
    titulo: str = Field(min_length=3, max_length=120)
    descripcion: str = Field(min_length=1, max_length=2000)
    categoria_id: str | None = None
    imagen_url: str | None = None


class ProgramaPatch(Modelo):
    titulo: str | None = Field(default=None, min_length=3, max_length=120)
    descripcion: str | None = Field(default=None, min_length=1, max_length=2000)
    categoria_id: str | None = None
    imagen_url: str | None = None


class HistoriaIn(Modelo):
    titulo: str = Field(min_length=3, max_length=120)
    resumen: str = Field(min_length=1, max_length=2000)
    autorizada: bool = False


class HistoriaPatch(Modelo):
    titulo: str | None = Field(default=None, min_length=3, max_length=120)
    resumen: str | None = Field(default=None, min_length=1, max_length=2000)
    autorizada: bool | None = None


class IndicadorIn(Modelo):
    nombre: str = Field(min_length=3, max_length=120)
    valor: float
    unidad: str = Field(min_length=1, max_length=40)
    periodo: str = Field(min_length=1, max_length=40)
    fuente: str = Field(min_length=1, max_length=120)


class IndicadorPatch(Modelo):
    nombre: str | None = Field(default=None, min_length=3, max_length=120)
    valor: float | None = None
    unidad: str | None = Field(default=None, min_length=1, max_length=40)
    periodo: str | None = Field(default=None, min_length=1, max_length=40)
    fuente: str | None = Field(default=None, min_length=1, max_length=120)


class ProductoIn(Modelo):
    nombre: str = Field(min_length=3, max_length=120)
    descripcion: str = Field(min_length=1, max_length=2000)
    categoria_id: str | None = None
    disponible: bool = True


class ProductoPatch(Modelo):
    nombre: str | None = Field(default=None, min_length=3, max_length=120)
    descripcion: str | None = Field(default=None, min_length=1, max_length=2000)
    categoria_id: str | None = None
    disponible: bool | None = None


class CategoriaIn(Modelo):
    nombre: str = Field(min_length=3, max_length=60)
    tipo: str = Field(min_length=2, max_length=40)


class CategoriaPatch(Modelo):
    nombre: str | None = Field(default=None, min_length=3, max_length=60)
    tipo: str | None = Field(default=None, min_length=2, max_length=40)


class PublicacionIn(Modelo):
    publicado: bool


class Cambios(Modelo):
    programas: list[Programa | Lapida]
    historias: list[Historia | Lapida]
    indicadores: list[Indicador | Lapida]
    productos: list[Producto | Lapida]
    categorias: list[Categoria | Lapida]


class SyncRespuesta(Modelo):
    server_time: UtcDatetime
    next_cursor: str | None
    cambios: Cambios
