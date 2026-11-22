"""`ms-contenidos` (puerto 8001): información institucional, programas y catálogo."""

from ..common.app import crear_app
from .routes import router

app = crear_app("ms-contenidos", router, "ms-contenidos", "contenidos")
