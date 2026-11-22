"""Aísla la base de datos por sesión de pytest.

Se ejecuta antes de importar la app, así que `settings.database_url` ya apunta a un
archivo temporal y cada corrida empieza con las tablas vacías y su seed.
"""

import os
import pathlib
import tempfile

_directorio = pathlib.Path(tempfile.mkdtemp(prefix="albaconecta-tests-"))
# Si defines DATABASE_URL (p. ej. Postgres) los tests corren contra esa base.
os.environ.setdefault("DATABASE_URL", f"sqlite:///{_directorio / 'test.db'}")
# Configuración para tests: entorno dev con password demo
os.environ.setdefault("ENTORNO", "dev")
os.environ.setdefault("DEMO_PASSWORD", "Demo1234")
os.environ.setdefault("SECRETO", "test-secreto-solo-para-tests-de-integracion")
