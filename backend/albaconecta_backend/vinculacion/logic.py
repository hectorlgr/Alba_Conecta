"""Reglas puras de `ms-vinculacion`: estados, folio, enmascarado y referencias."""

import hashlib
import json
import secrets
import string

import httpx

from ..common.config import settings
from ..common.errors import ApiError

ESTADOS = ("RECIBIDA", "EN_REVISION", "CONTACTADO", "CONCRETADO", "DESCARTADO")

# §4.4: cadena hacia adelante y salida a DESCARTADO desde las tres primeras.
TRANSICIONES: dict[str, set[str]] = {
    "RECIBIDA": {"EN_REVISION", "DESCARTADO"},
    "EN_REVISION": {"CONTACTADO", "DESCARTADO"},
    "CONTACTADO": {"CONCRETADO", "DESCARTADO"},
    "CONCRETADO": set(),
    "DESCARTADO": set(),
}

ALFABETO_FOLIO = string.ascii_uppercase + string.digits


def transicion_valida(actual: str, nuevo: str) -> bool:
    return nuevo in TRANSICIONES.get(actual, set())


def generar_folio(existe) -> str:
    """`AC-` + 8 caracteres alfanuméricos únicos (§4.16 / ADR-016)."""
    for _ in range(5):
        folio = "AC-" + "".join(secrets.choice(ALFABETO_FOLIO) for _ in range(8))
        if not existe(folio):
            return folio
    raise ApiError(500, "ERROR_INTERNO", "No fue posible generar un folio único.")


def normalizar_folio(folio: str) -> str:
    return folio.strip().upper()


def enmascarar_email(email: str | None) -> str | None:
    if not email:
        return None
    usuario, _, dominio = email.partition("@")
    if not dominio:
        return "*" * len(email)
    return f"{usuario[0]}***@{dominio}"


def enmascarar_telefono(telefono: str | None) -> str | None:
    if not telefono:
        return None
    limpio = telefono.strip()
    return "*" * max(len(limpio) - 2, 0) + limpio[-2:]


def huella_payload(payload: dict) -> str:
    """Hash del payload normalizado para detectar la misma clave con otro cuerpo (§4.1)."""
    normalizado = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(normalizado.encode()).hexdigest()


async def verificar_referencia(
    programa_id: str | None, producto_id: str | None
) -> tuple[bool, str | None, str | None]:
    """ADR-012: una referencia inválida nunca bloquea la creación de la solicitud.

    Devuelve `(no_verificada, titulo_programa, titulo_producto)`. Si no hay
    `CONTENIDOS_BASE_URL` configurada o la llamada falla, se marca no verificada.
    """
    pedidos = [("programas", "titulo", programa_id), ("productos", "nombre", producto_id)]
    if not programa_id and not producto_id:
        return False, None, None

    base = settings.contenidos_url.rstrip("/")
    if not base:
        return True, None, None

    titulos: dict[str, str | None] = {}
    # Timeout 800 ms con hasta 2 reintentos en error de transporte (ADR-012).
    transporte = httpx.AsyncHTTPTransport(retries=2)
    async with httpx.AsyncClient(timeout=0.8, transport=transporte) as cliente:
        for ruta, campo, identificador in pedidos:
            if not identificador:
                continue
            try:
                respuesta = await cliente.get(f"{base}/api/v1/contenidos/{ruta}/{identificador}")
                titulos[ruta] = respuesta.json().get(campo) if respuesta.status_code == 200 else None
            except httpx.HTTPError:
                titulos[ruta] = None

    titulo_programa = titulos.get("programas")
    titulo_producto = titulos.get("productos")
    no_verificada = (bool(programa_id) and titulo_programa is None) or (bool(producto_id) and titulo_producto is None)
    return no_verificada, titulo_programa, titulo_producto
