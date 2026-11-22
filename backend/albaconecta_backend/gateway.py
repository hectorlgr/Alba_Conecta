"""Gateway de desarrollo (puerto 8000): los cuatro servicios en una sola app.

Sirve para que la app Android use una única baseUrl (`http://10.0.2.2:8000/`), igual que
el gateway Traefik del plan. En producción cada servicio sigue en su puerto.
"""

from fastapi import APIRouter

from .common.app import crear_app
from .contenidos.routes import router as contenidos
from .identidad.routes import router as identidad
from .talento.routes import router as talento
from .vinculacion.routes import router as vinculacion

_gateway = APIRouter()
for _router in (contenidos, vinculacion, identidad, talento):
    _gateway.include_router(_router)

app = crear_app("Alba Conecta — gateway de desarrollo", _gateway, "ms-identidad", dominio=None)
