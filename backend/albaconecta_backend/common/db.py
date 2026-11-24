"""Motor SQL y sesiones (Fase 2).

La URL sale de `DATABASE_URL`. Sin variable configurada usa SQLite en el directorio
del backend: sirve para tests y para quien no tiene un servidor levantado. Para el
trabajo real se apunta a PostgreSQL (ver `docker-compose.yml`) o a MySQL:

    postgresql+psycopg://albaconecta:albaconecta@localhost:5432/albaconecta
    mysql+pymysql://albaconecta:albaconecta@localhost:3306/albaconecta
"""

from collections.abc import Iterator
from contextlib import contextmanager

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import settings

from .config import url_base_datos


class Base(DeclarativeBase):
    """Compatibilidad temporal con imports existentes."""


class BaseContenidos(DeclarativeBase):
    pass


class BaseVinculacion(DeclarativeBase):
    pass


class BaseIdentidad(DeclarativeBase):
    pass


class BaseTalento(DeclarativeBase):
    pass


_engines = {}
_fabricas = {}


def motor(dominio: str):
    """Obtiene el engine del dominio; crea la tabla solo al solicitarla desde el servicio."""
    if dominio not in _engines:
        url = url_base_datos(settings, dominio)
        connect_args = {"check_same_thread": False} if url.startswith("sqlite") else {}
        _engines[dominio] = create_engine(url, connect_args=connect_args, pool_pre_ping=True, future=True)
        _fabricas[dominio] = sessionmaker(
            bind=_engines[dominio], expire_on_commit=False, future=True
        )
    return _engines[dominio]


@contextmanager
def sesion(dominio: str) -> Iterator[Session]:
    """Una sesión por operación y por dominio; commit o rollback explícitos."""
    motor(dominio)
    s = _fabricas[dominio]()
    try:
        yield s
        s.commit()
    except Exception:
        s.rollback()
        raise
    finally:
        s.close()


def crear_tablas(dominio: str, settings_obj=None) -> None:
    """Solo desarrollo/test: crea tablas sin mezclar dominios; producción usa Alembic."""
    config = settings_obj or settings
    if config.entorno.strip().lower() in ("prod", "produccion", "production"):
        raise RuntimeError("create_all no está permitido en producción; ejecuta Alembic")

    bases = {
        "contenidos": BaseContenidos,
        "vinculacion": BaseVinculacion,
        "identidad": BaseIdentidad,
        "talento": BaseTalento,
    }
    if dominio not in bases:
        raise RuntimeError(f"El dominio {dominio} no tiene tablas SQL declaradas")
    bases[dominio].metadata.create_all(motor(dominio))
