import os
from logging.config import fileConfig

from alembic import context
from sqlalchemy import engine_from_config, pool

from albaconecta_backend.common.config import Settings, url_base_datos

config = context.config
if config.config_file_name:
    fileConfig(config.config_file_name)

DOMINIO = os.getenv("MIGRACION_DOMINIO", "")
BASES = {}

if DOMINIO == "contenidos":
    from albaconecta_backend.common.db import BaseContenidos
    from albaconecta_backend.contenidos import models  # noqa: F401
    BASES[DOMINIO] = BaseContenidos
elif DOMINIO == "vinculacion":
    from albaconecta_backend.common.db import BaseVinculacion
    from albaconecta_backend.vinculacion import models  # noqa: F401
    BASES[DOMINIO] = BaseVinculacion
elif DOMINIO == "identidad":
    from albaconecta_backend.common.db import BaseIdentidad
    from albaconecta_backend.identidad import models  # noqa: F401
    BASES[DOMINIO] = BaseIdentidad
elif DOMINIO == "talento":
    from albaconecta_backend.common.db import BaseTalento
    from albaconecta_backend.talento import models  # noqa: F401
    BASES[DOMINIO] = BaseTalento
else:
    raise RuntimeError("MIGRACION_DOMINIO debe ser contenidos, vinculacion, identidad o talento")

config.set_main_option("sqlalchemy.url", url_base_datos(Settings(), DOMINIO).replace("%", "%%"))
target_metadata = BASES[DOMINIO].metadata


def run_migrations_offline() -> None:
    context.configure(url=config.get_main_option("sqlalchemy.url"), target_metadata=target_metadata,
                      literal_binds=True, dialect_opts={"paramstyle": "named"})
    with context.begin_transaction():
        context.run_migrations()


def run_migrations_online() -> None:
    connectable = engine_from_config(config.get_section(config.config_ini_section, {}),
                                     prefix="sqlalchemy.", poolclass=pool.NullPool)
    with connectable.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
