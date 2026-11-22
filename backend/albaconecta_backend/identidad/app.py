"""`ms-identidad` (puerto 8003): registro, login, tokens y administración de cuentas."""

from ..common.app import crear_app
from .routes import router

app = crear_app("ms-identidad", router, "ms-identidad", "identidad")
