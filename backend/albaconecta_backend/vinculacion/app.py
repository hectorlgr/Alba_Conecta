"""`ms-vinculacion` (puerto 8002): tipos de colaboración, solicitudes y su ciclo de vida."""

from ..common.app import crear_app
from .routes import router

app = crear_app("ms-vinculacion", router, "ms-vinculacion", "vinculacion")
