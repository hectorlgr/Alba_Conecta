#!/bin/sh
# Migra el dominio antes de arrancar: si la migración falla, el servicio no sirve (§12.3).
set -e

if [ -n "${MIGRACION_DOMINIO}" ]; then
  alembic upgrade "${MIGRACION_DOMINIO}@head"
fi

exec uvicorn "${APP_MODULE:?APP_MODULE no configurado}" --host 0.0.0.0 --port 8000
