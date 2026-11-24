"""Acceso a datos de `ms-identidad` (Fase 2: PostgreSQL/MySQL vía SQLAlchemy).

Usuarios y refresh tokens viven en la base de datos; se consultan bajo demanda.
Las contraseñas se siguen guardando con PBKDF2-SHA256.
Los refresh tokens se almacenan como hash SHA-256 (Fase 1.2).

`shortcut: hash PBKDF2 y JWT HS256 provisionales; Fase 3 pasa a argon2 (pwdlib) y
RS256 + JWKS, sin cambiar el contrato.`
"""

import hashlib
import os
import secrets
import time
from datetime import datetime, timezone, timedelta

from sqlalchemy import select
from pwdlib import PasswordHash

from ..common import db
from ..common.config import settings, validar_configuracion
from ..common.errors import ApiError
from . import models  # noqa: F401  (registra las tablas en Base.metadata)
from .models import RefreshToken, Usuario

# Validar configuración crítica al importar (falla rápido en producción)
validar_configuracion(settings)

if settings.entorno.lower() not in ("prod", "produccion", "production"):
    db.crear_tablas("identidad")

ITERACIONES = 200_000
_PASSWORD_HASH = PasswordHash.recommended()


def ahora() -> datetime:
    return datetime.now(timezone.utc).replace(microsecond=0)


def hashear(password: str) -> str:
    return _PASSWORD_HASH.hash(password)


def verificar(password: str, almacenado: str) -> bool:
    if almacenado.startswith("$argon2"):
        return _PASSWORD_HASH.verify(password, almacenado)
    try:
        _, iteraciones, sal_hex, clave_hex = almacenado.split("$")
        esperado = bytes.fromhex(clave_hex)
        calculado = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(sal_hex), int(iteraciones))
    except ValueError:
        return False
    return secrets.compare_digest(esperado, calculado)


# Comparación contra un hash ficticio cuando el correo no existe: evita filtrar
# qué cuentas están registradas por diferencia de tiempo de respuesta.
HASH_FICTICIO = hashear("contraseña-ficticia-de-referencia")


def _hash_refresh(token: str) -> str:
    """Calcula el hash SHA-256 del refresh token para almacenamiento."""
    return hashlib.sha256(token.encode()).hexdigest()


def _a_dict(fila) -> dict:
    datos = {c.name: getattr(fila, c.name) for c in fila.__table__.columns}
    valor = datos.get("fecha_creacion")
    if valor is not None and valor.tzinfo is None:
        datos["fecha_creacion"] = valor.replace(tzinfo=timezone.utc)
    return datos


class Almacen:
    """Acceso a usuarios y refresh tokens consultando la BD bajo demanda.
    
    No mantiene caché en memoria de usuarios ni tokens: cada operación lee/escribe
    directamente la base de datos. Esto garantiza consistencia entre múltiples
    procesos/workers apuntando a la misma BD.
    """

    def __init__(self) -> None:
        self._secuencia = 0
        # Inicializar secuencia desde BD si existe
        with db.sesion("identidad") as s:
            max_id = s.execute(select(Usuario.id)).scalars().all()
            for id_ in max_id:
                self._secuencia = max(self._secuencia, _numero(id_))

    # --- Usuarios (lectura bajo demanda) ---

    def por_email(self, email: str) -> dict | None:
        with db.sesion("identidad") as s:
            fila = s.execute(select(Usuario).where(Usuario.email == email)).scalar_one_or_none()
            return _a_dict(fila) if fila else None

    def obtener(self, id_: str) -> dict | None:
        with db.sesion("identidad") as s:
            fila = s.get(Usuario, id_)
            return _a_dict(fila) if fila else None

    def crear(self, nombre: str, email: str, password: str, tipo: str, rol: str, organizacion: str | None) -> dict:
        self._secuencia += 1
        usuario = {
            "id": f"usr-{self._secuencia}",
            "nombre": nombre.strip(),
            "email": email,
            "password_hash": hashear(password),
            "tipo": tipo,
            "rol": rol,
            "organizacion": organizacion,
            "activo": True,
            "fecha_creacion": ahora(),
        }
        self.guardar(usuario)
        return usuario

    def guardar(self, usuario: dict) -> None:
        columnas = {c.name for c in Usuario.__table__.columns}
        with db.sesion("identidad") as s:
            s.merge(Usuario(**{k: v for k, v in usuario.items() if k in columnas}))

    def listar_todos(self) -> list[dict]:
        """Lista todos los usuarios (para endpoints de administración)."""
        with db.sesion("identidad") as s:
            filas = s.execute(select(Usuario).order_by(Usuario.fecha_creacion)).scalars().all()
            return [_a_dict(f) for f in filas]

    # --- Refresh tokens (hash SHA-256 en BD, ADR-017) ---

    def emitir_refresh(self, usuario_id: str) -> str:
        """Emite un nuevo refresh token y guarda su hash en BD."""
        token = secrets.token_urlsafe(32)
        token_hash = _hash_refresh(token)
        expira = time.time() + settings.refresh_ttl
        with db.sesion("identidad") as s:
            s.merge(RefreshToken(
                token=token_hash,
                usuario_id=usuario_id,
                expira=expira,
                revocado_en=None,
            ))
        return token

    def revocar(self, token: str) -> None:
        """Revoca un refresh token por su valor plano (hashea antes de buscar)."""
        token_hash = _hash_refresh(token)
        with db.sesion("identidad") as s:
            fila = s.get(RefreshToken, token_hash)
            if fila:
                fila.revocado_en = ahora()

    def revocar_todos(self, usuario_id: str) -> None:
        """Revoca todos los refresh tokens activos de un usuario."""
        with db.sesion("identidad") as s:
            filas = s.execute(
                select(RefreshToken).where(
                    RefreshToken.usuario_id == usuario_id,
                    RefreshToken.revocado_en.is_(None),
                )
            ).scalars().all()
            for fila in filas:
                fila.revocado_en = ahora()

    def canjear_refresh(self, token: str) -> dict:
        """Rotación con detección de replay (caso borde 4).
        
        Busca el token por su hash. Si no existe, fue revocado o expiró, rechaza.
        En caso de replay (token ya revocado), revoca toda la cadena del usuario.
        """
        token_hash = _hash_refresh(token)
        with db.sesion("identidad") as s:
            fila = s.get(RefreshToken, token_hash)
            if fila is None:
                raise ApiError(401, "TOKEN_INVALIDO", "El refresh token no es válido.")
            if fila.revocado_en is not None:
                # Reutilización: se revoca toda la cadena del usuario.
                self.revocar_todos(fila.usuario_id)
                raise ApiError(401, "TOKEN_INVALIDO", "El refresh token ya fue utilizado.")
            if fila.expira < time.time():
                raise ApiError(401, "TOKEN_EXPIRADO", "El refresh token expiró, inicia sesión nuevamente.")
            
            usuario_fila = s.get(Usuario, fila.usuario_id)
            if usuario_fila is None:
                raise ApiError(401, "TOKEN_INVALIDO", "El refresh token no es válido.")
            if not usuario_fila.activo:
                raise ApiError(403, "CUENTA_INACTIVA", "La cuenta está desactivada.")
            
            # Revocar el token usado y emitir uno nuevo (rotación)
            fila.revocado_en = ahora()
            usuario = _a_dict(usuario_fila)
        
        return usuario

    def limpiar_expirados(self) -> int:
        """Elimina refresh tokens expirados o revocados hace más de 30 días.
        
        Tarea de mantenimiento para evitar crecimiento indefinido.
        """
        limite = ahora() - timedelta(days=30)
        with db.sesion("identidad") as s:
            filas = s.execute(
                select(RefreshToken).where(
                    (RefreshToken.expira < time.time()) | 
                    (RefreshToken.revocado_en.is_not(None) & (RefreshToken.revocado_en < limite))
                )
            ).scalars().all()
            count = len(filas)
            for fila in filas:
                s.delete(fila)
            return count


def _numero(id_: str) -> int:
    try:
        return int(id_.rsplit("-", 1)[1])
    except (IndexError, ValueError):
        return 0


almacen = Almacen()


def _password_demo() -> str:
    """Obtiene la contraseña demo desde variable de entorno; falla en dev si no está."""
    pwd = settings.demo_password
    if settings.es_dev and not pwd:
        raise RuntimeError("DEMO_PASSWORD no configurada: define la variable de entorno en desarrollo")
    return pwd


def sembrar_privilegiados() -> None:
    """ADR-013: GESTOR y ADMIN_CONTENIDOS solo por seed, nunca por registro público.
    
    En producción: no crear cuentas privilegiadas automáticamente.
    En desarrollo: crear solo si la BD está vacía y DEMO_PASSWORD está configurado.
    """
    if not settings.es_dev:
        # En producción no sembrar cuentas demo ni privilegiadas
        return
    
    # Verificar si ya hay usuarios en BD
    with db.sesion("identidad") as s:
        if s.execute(select(Usuario.id)).first() is not None:
            return
    
    pwd = _password_demo()
    almacen.crear("Administradora de contenidos", "admin@albaconecta.cl", pwd, "COLABORADOR", "ADMIN_CONTENIDOS", None)
    almacen.crear("Gestor de vinculación", "gestor@albaconecta.cl", pwd, "COLABORADOR", "GESTOR", None)
    almacen.crear("Empresa Demo", "empresa@demo.cl", pwd, "EMPRESA", "EMPRESA", "Empresa Demo SpA")
    almacen.crear("Colaborador Demo", "colaborador@demo.cl", pwd, "COLABORADOR", "COLABORADOR", None)


# Solo sembrar en desarrollo; en producción se requiere procedimiento manual
if settings.es_dev:
    sembrar_privilegiados()
