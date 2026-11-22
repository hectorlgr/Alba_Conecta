# Imagen única para los 4 servicios: el módulo se elige con APP_MODULE (§15).
FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# El paquete entra junto con las dependencias: `pip install .` las resuelve del pyproject.
COPY pyproject.toml ./
COPY albaconecta_backend ./albaconecta_backend
RUN pip install --no-cache-dir . \
 && useradd --create-home --uid 10001 alba

COPY alembic.ini ./
COPY alembic ./alembic
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

USER alba
EXPOSE 8000
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
