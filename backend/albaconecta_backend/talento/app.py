"""`ms-talento` (puerto 8004): puente laboral acotado con datos anonimizados."""

from ..common.app import crear_app
from .routes import router

app = crear_app("ms-talento", router, "ms-talento", "talento")
