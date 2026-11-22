"""Esquemas compartidos por los cuatro servicios (§4.1 y §4.2 del plan)."""

from datetime import datetime, timezone
from typing import Annotated, Generic, TypeVar

from pydantic import BaseModel, ConfigDict, PlainSerializer


def iso_utc(valor: datetime) -> str:
    """Contrato §4.1: fechas ISO-8601 UTC terminadas en Z."""
    return valor.astimezone(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


UtcDatetime = Annotated[datetime, PlainSerializer(iso_utc, return_type=str, when_used="json")]

T = TypeVar("T")


class Modelo(BaseModel):
    # extra="forbid": un campo mal escrito en el cliente falla con 422 en vez de ignorarse.
    model_config = ConfigDict(extra="forbid")


class Pagina(Modelo, Generic[T]):
    items: list[T]
    total: int
    page: int
    limit: int
    has_more: bool
