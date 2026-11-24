"""Persistencia SQL de solicitudes e idempotencia de vinculación."""

import asyncio
import time
import uuid
from datetime import datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from ..common import db
from ..common.errors import ApiError
from . import models  # noqa: F401
from .logic import ESTADOS, enmascarar_email, enmascarar_telefono
from .models import HistorialSolicitud, IdempotenciaSolicitud, Solicitud

TTL_IDEMPOTENCIA = 24 * 3600
# Vida corta de una reserva sin solicitud: si el proceso muere a mitad de camino,
# la clave se libera sola en vez de bloquearla 24 h.
LEASE_RESERVA = 60

TIPOS_COLABORACION = [
    {"codigo": "DONACION", "nombre": "Donación", "activo": True},
    {"codigo": "ALIANZA_RSE", "nombre": "Alianza RSE", "activo": True},
    {"codigo": "VOLUNTARIADO", "nombre": "Voluntariado", "activo": True},
    {"codigo": "COMPRA", "nombre": "Compra de productos o servicios", "activo": True},
    {"codigo": "CAPACITACION", "nombre": "Capacitación", "activo": True},
    {"codigo": "OTRO", "nombre": "Otra forma de colaborar", "activo": True},
]


def ahora() -> datetime:
    return datetime.now(timezone.utc).replace(microsecond=0)


def _a_dict(fila) -> dict:
    datos = {c.name: getattr(fila, c.name) for c in fila.__table__.columns}
    for campo in ("fecha_creacion", "fecha_actualizacion"):
        valor = datos.get(campo)
        if valor is not None and valor.tzinfo is None:
            datos[campo] = valor.replace(tzinfo=timezone.utc)
    datos.setdefault("detalles", [])
    datos.setdefault("observaciones_internas", [])
    return datos


class Almacen:
    def __init__(self) -> None:
        from ..common.config import settings
        if settings.entorno.lower() not in ("prod", "produccion", "production"):
            db.crear_tablas("vinculacion")

    def crear(
        self,
        datos: dict,
        folio: str,
        referencia_no_verificada: bool,
        programa_titulo: str | None,
        producto_titulo: str | None,
        clave_idempotencia: str,
    ) -> dict:
        momento = ahora()
        solicitud = Solicitud(
            id=f"sol-{uuid.uuid4().hex}",
            folio=folio,
            estado="RECIBIDA",
            nombre_contacto=datos["nombre_contacto"],
            organizacion=datos.get("organizacion"),
            email=datos.get("email"),
            telefono=datos.get("telefono"),
            mensaje=datos["mensaje"],
            programa_id=datos.get("programa_id"),
            producto_id=datos.get("producto_id"),
            programa_titulo=programa_titulo,
            producto_titulo=producto_titulo,
            referencia_no_verificada=referencia_no_verificada,
            detalles=datos["detalles"],
            observaciones_internas=[],
            fecha_creacion=momento,
            fecha_actualizacion=momento,
        )
        # Solicitud, historial y confirmación de la reserva en un solo commit: no
        # existe ventana en la que la clave apunte a una solicitud inexistente.
        with db.sesion("vinculacion") as sesion:
            reserva = sesion.get(IdempotenciaSolicitud, clave_idempotencia)
            if reserva is None:
                raise ApiError(
                    409,
                    "IDEMPOTENCY_PENDING",
                    "La reserva de idempotencia expiró; reintenta el envío.",
                )
            sesion.add(solicitud)
            sesion.flush()
            sesion.add(
                HistorialSolicitud(
                    solicitud_id=solicitud.id,
                    estado_anterior=None,
                    estado_nuevo="RECIBIDA",
                    observacion=None,
                    autor_id=None,
                    fecha=momento,
                )
            )
            reserva.solicitud_id = solicitud.id
            reserva.expira = time.time() + TTL_IDEMPOTENCIA
            sesion.flush()
            return _a_dict(solicitud)

    def obtener(self, id_: str) -> dict | None:
        with db.sesion("vinculacion") as sesion:
            fila = sesion.get(Solicitud, id_)
            return _a_dict(fila) if fila else None

    def por_folio(self, folio: str) -> dict | None:
        with db.sesion("vinculacion") as sesion:
            fila = sesion.execute(select(Solicitud).where(Solicitud.folio == folio)).scalar_one_or_none()
            return _a_dict(fila) if fila else None

    def listar(self) -> list[dict]:
        with db.sesion("vinculacion") as sesion:
            filas = sesion.execute(select(Solicitud).order_by(Solicitud.fecha_creacion.desc())).scalars().all()
            return [_a_dict(fila) for fila in filas]

    def publico(self, solicitud: dict) -> dict:
        return {
            "folio": solicitud["folio"],
            "estado": solicitud["estado"],
            "nombre_contacto": solicitud["nombre_contacto"],
            "organizacion": solicitud["organizacion"],
            "email": enmascarar_email(solicitud["email"]),
            "telefono": enmascarar_telefono(solicitud["telefono"]),
            "detalles": solicitud["detalles"],
            "fecha_creacion": solicitud["fecha_creacion"],
            "fecha_actualizacion": solicitud["fecha_actualizacion"],
        }

    def cambiar_estado(self, solicitud: dict, nuevo: str, observacion: str | None, autor_id: str) -> None:
        momento = ahora()
        with db.sesion("vinculacion") as sesion:
            fila = sesion.get(Solicitud, solicitud["id"])
            anterior = fila.estado
            fila.estado = nuevo
            fila.fecha_actualizacion = momento
            sesion.add(
                HistorialSolicitud(
                    solicitud_id=fila.id,
                    estado_anterior=anterior,
                    estado_nuevo=nuevo,
                    observacion=observacion,
                    autor_id=autor_id,
                    fecha=momento,
                )
            )
            sesion.flush()
            actualizado = _a_dict(fila)
        solicitud.clear()
        solicitud.update(actualizado)

    def agregar_observacion(self, solicitud: dict, texto: str, autor_id: str) -> None:
        momento = ahora()
        with db.sesion("vinculacion") as sesion:
            fila = sesion.get(Solicitud, solicitud["id"])
            fila.observaciones_internas = [*fila.observaciones_internas, texto]
            fila.fecha_actualizacion = momento
            sesion.add(
                HistorialSolicitud(
                    solicitud_id=fila.id,
                    estado_anterior=fila.estado,
                    estado_nuevo=fila.estado,
                    observacion=texto,
                    autor_id=autor_id,
                    fecha=momento,
                )
            )
            sesion.flush()
            actualizado = _a_dict(fila)
        solicitud.clear()
        solicitud.update(actualizado)

    def reservar_idempotencia(self, clave: str, huella: str) -> tuple[bool, str | None]:
        """Reserva la clave en su propia transacción, antes de la verificación cruzada.

        Devuelve `(True, None)` si esta llamada obtuvo la reserva y `(False, id)`
        si la clave ya existía con la misma huella (`id` puede ser `None` mientras
        el proceso ganador aún no crea la solicitud).
        """
        for _ in range(3):
            with db.sesion("vinculacion") as sesion:
                existente = sesion.get(IdempotenciaSolicitud, clave)
                if existente is not None and existente.expira >= time.time():
                    if existente.huella != huella:
                        raise ApiError(
                            409,
                            "IDEMPOTENCY_CONFLICT",
                            "La clave de idempotencia ya se usó con otro contenido.",
                        )
                    return False, existente.solicitud_id
                if existente is not None:
                    # Reserva expirada o abandonada: se puede reutilizar la clave.
                    sesion.delete(existente)
                    sesion.flush()
                sesion.add(
                    IdempotenciaSolicitud(
                        clave=clave,
                        huella=huella,
                        solicitud_id=None,
                        expira=time.time() + LEASE_RESERVA,
                    )
                )
                try:
                    sesion.flush()
                except IntegrityError:
                    # Otro proceso insertó la misma clave primero: reintentar la lectura.
                    sesion.rollback()
                    continue
            return True, None
        raise ApiError(
            409,
            "IDEMPOTENCY_PENDING",
            "La clave está siendo procesada por otra solicitud.",
        )

    async def esperar_confirmacion(self, clave: str, timeout: float = 2.0) -> str | None:
        """Espera a que el proceso ganador confirme la solicitud de la misma clave."""
        limite = time.monotonic() + timeout
        while time.monotonic() < limite:
            with db.sesion("vinculacion") as sesion:
                registro = sesion.get(IdempotenciaSolicitud, clave)
                if registro is not None and registro.solicitud_id:
                    return registro.solicitud_id
            # `asyncio.sleep` cede el event loop: el request ganador debe poder
            # crear y confirmar la solicitud mientras este espera.
            await asyncio.sleep(0.02)
        return None

    def liberar_idempotencia(self, clave: str) -> None:
        with db.sesion("vinculacion") as sesion:
            registro = sesion.get(IdempotenciaSolicitud, clave)
            if registro and registro.solicitud_id is None:
                sesion.delete(registro)

    def historial(self, solicitud_id: str) -> list[dict]:
        with db.sesion("vinculacion") as sesion:
            filas = sesion.execute(
                select(HistorialSolicitud)
                .where(HistorialSolicitud.solicitud_id == solicitud_id)
                .order_by(HistorialSolicitud.fecha)
            ).scalars().all()
            return [
                {
                    "estado_anterior": fila.estado_anterior,
                    "estado_nuevo": fila.estado_nuevo,
                    "observacion": fila.observacion,
                    "autor_id": fila.autor_id,
                    "fecha": fila.fecha.replace(tzinfo=timezone.utc) if fila.fecha.tzinfo is None else fila.fecha,
                }
                for fila in filas
            ]

    def limpiar_idempotencia_expirada(self) -> int:
        limite = time.time()
        with db.sesion("vinculacion") as sesion:
            filas = sesion.execute(
                select(IdempotenciaSolicitud).where(IdempotenciaSolicitud.expira < limite)
            ).scalars().all()
            for fila in filas:
                sesion.delete(fila)
            return len(filas)

    def metricas(self) -> dict:
        solicitudes = self.listar()
        por_estado = {estado: 0 for estado in ESTADOS}
        por_tipo = {tipo["codigo"]: 0 for tipo in TIPOS_COLABORACION}
        desde = ahora() - timedelta(days=30)
        recientes = 0
        for solicitud in solicitudes:
            por_estado[solicitud["estado"]] += 1
            for detalle in solicitud["detalles"]:
                por_tipo[detalle["tipo_colaboracion_codigo"]] += 1
            if solicitud["fecha_creacion"] >= desde:
                recientes += 1
        return {
            "total": len(solicitudes),
            "por_estado": por_estado,
            "por_tipo": por_tipo,
            "ultimos_30_dias": recientes,
        }


almacen = Almacen()
