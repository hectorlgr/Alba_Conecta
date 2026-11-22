"""Pruebas de contrato y de las reglas de borde críticas (§5 del plan)."""

import re
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from albaconecta_backend.common import db, http as http_comun
from albaconecta_backend.common.security import emitir_access
from albaconecta_backend.contenidos.app import app as app_contenidos
from albaconecta_backend.contenidos import routes as rutas_contenidos
from albaconecta_backend.identidad.app import app as app_identidad
from albaconecta_backend.identidad.data import hashear, verificar
from albaconecta_backend.talento.app import app as app_talento
from albaconecta_backend.vinculacion.app import app as app_vinculacion
from albaconecta_backend.vinculacion.data import Almacen
from albaconecta_backend.vinculacion.models import IdempotenciaSolicitud, Solicitud


@pytest.fixture(autouse=True)
def _limpiar_rate_limit():
    http_comun._historial.clear()


@pytest.fixture
def contenidos() -> TestClient:
    return TestClient(app_contenidos)


@pytest.fixture
def vinculacion() -> TestClient:
    return TestClient(app_vinculacion)


@pytest.fixture
def identidad() -> TestClient:
    return TestClient(app_identidad)


@pytest.fixture
def talento() -> TestClient:
    return TestClient(app_talento)


def admin_contenidos() -> dict[str, str]:
    return {"Authorization": f"Bearer {emitir_access('usr-1', 'ADMIN_CONTENIDOS')}"}


SOLICITUD = {
    "nombre_contacto": "Ana Pérez",
    "email": "ana@correo.cl",
    "telefono": "+56912345678",
    "mensaje": "Quiero colaborar con el programa",
    "acepta_privacidad": True,
    "detalles": [{"tipo_colaboracion_codigo": "DONACION", "descripcion": "Aporte mensual"}],
}


# --- Transversal ---


def test_apps_por_dominio_publican_health_bajo_prefijo_real():
    aplicaciones = (
        (app_contenidos, "/api/v1/contenidos/health/live", "/api/v1/contenidos/health/ready"),
        (app_vinculacion, "/api/v1/vinculacion/health/live", "/api/v1/vinculacion/health/ready"),
        (app_identidad, "/api/v1/identidad/health/live", "/api/v1/identidad/health/ready"),
        (app_talento, "/api/v1/talento/health/live", "/api/v1/talento/health/ready"),
    )
    for app, live, ready in aplicaciones:
        rutas = {ruta.path for ruta in app.routes if hasattr(ruta, "path")}
        assert live in rutas
        assert ready in rutas


def test_request_id_y_problem_json(contenidos: TestClient):
    respuesta = contenidos.get("/api/v1/contenidos/programas/no-existe", headers={"X-Request-Id": "abc123"})
    assert respuesta.status_code == 404
    assert respuesta.headers["X-Request-Id"] == "abc123"
    assert respuesta.headers["content-type"].startswith("application/problem+json")
    cuerpo = respuesta.json()
    assert cuerpo["code"] == "NO_ENCONTRADO"
    assert cuerpo["request_id"] == "abc123"
    assert cuerpo["instance"] == "/api/v1/contenidos/programas/no-existe"


def test_validacion_devuelve_errors_por_campo(vinculacion: TestClient):
    respuesta = vinculacion.post(
        "/api/v1/vinculacion/solicitudes",
        json={**SOLICITUD, "email": "no-es-correo"},
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    assert respuesta.status_code == 422
    assert respuesta.json()["code"] == "VALIDACION"
    assert any(error["field"] == "email" for error in respuesta.json()["errors"])


# --- ms-contenidos (casos 8 y 9) ---


def test_sync_rechaza_since_futuro(contenidos: TestClient):
    respuesta = contenidos.get("/api/v1/contenidos/sync", params={"since": "2099-01-01T00:00:00Z"})
    assert respuesta.status_code == 422
    assert respuesta.json()["code"] == "SINCRONIZACION_FUTURA"


def test_sync_entrega_lapida_al_despublicar(contenidos: TestClient):
    assert contenidos.patch(
        "/api/v1/contenidos/programas/prg-1/publicacion", json={"publicado": False}, headers=admin_contenidos()
    ).status_code == 200

    cuerpo = contenidos.get("/api/v1/contenidos/sync", params={"since": "2000-01-01T00:00:00Z"}).json()
    lapidas = [p for p in cuerpo["cambios"]["programas"] if p.get("eliminado") is True]
    assert any(lapida["id"] == "prg-1" for lapida in lapidas)


def test_sync_pagina_con_cursor(contenidos: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(rutas_contenidos, "settings", SimpleNamespace(sync_pagina=2))
    primera = contenidos.get("/api/v1/contenidos/sync").json()
    assert primera["next_cursor"] is not None

    segunda = contenidos.get("/api/v1/contenidos/sync", params={"cursor": primera["next_cursor"]}).json()
    assert segunda["next_cursor"] != primera["next_cursor"]
    assert segunda["cambios"] != primera["cambios"]


def test_historia_sin_autorizacion_no_es_publica(contenidos: TestClient):
    assert contenidos.get("/api/v1/contenidos/historias/his-4").status_code == 404
    respuesta = contenidos.patch(
        "/api/v1/contenidos/historias/his-4/publicacion", json={"publicado": True}, headers=admin_contenidos()
    )
    assert respuesta.status_code == 409
    assert respuesta.json()["code"] == "REGLA_NEGOCIO"


# --- ms-identidad (casos 3, 4 y 12) ---


def test_login_y_perfil(identidad: TestClient):
    respuesta = identidad.post(
        "/api/v1/identidad/auth/login", json={"email": "Gestor@AlbaConecta.cl", "password": "Demo1234"}
    )
    assert respuesta.status_code == 200
    datos = respuesta.json()
    assert datos["usuario"]["rol"] == "GESTOR"
    assert datos["expires_in"] > 0

    perfil = identidad.get("/api/v1/identidad/auth/me", headers={"Authorization": f"Bearer {datos['access_token']}"})
    assert perfil.status_code == 200
    assert perfil.json()["email"] == "gestor@albaconecta.cl"
    partes_access = datos["access_token"].split(".")
    assert len(partes_access) == 3


def test_jwks_publica_clave_rs256(identidad: TestClient):
    respuesta = identidad.get("/api/v1/identidad/.well-known/jwks.json")
    assert respuesta.status_code == 200
    clave = respuesta.json()["keys"][0]
    assert clave["kty"] == "RSA"
    assert clave["alg"] == "RS256"
    assert clave["use"] == "sig"
    assert clave["kid"]
    assert clave["n"]
    assert clave["e"]


def test_login_credenciales_invalidas(identidad: TestClient):
    respuesta = identidad.post(
        "/api/v1/identidad/auth/login", json={"email": "gestor@albaconecta.cl", "password": "Incorrecta1"}
    )
    assert respuesta.status_code == 401
    assert respuesta.json()["code"] == "NO_AUTENTICADO"


def test_hash_argon2_verifica_y_acepta_hash_legacy():
    hash_argon = hashear("Demo1234")
    assert hash_argon.startswith("$argon2")
    assert verificar("Demo1234", hash_argon)
    assert not verificar("Incorrecta1", hash_argon)

    hash_legacy = "pbkdf2_sha256$1$1234$1234"
    assert not verificar("Demo1234", hash_legacy)


def test_registro_no_permite_roles_privilegiados(identidad: TestClient):
    respuesta = identidad.post(
        "/api/v1/identidad/auth/register",
        json={"nombre": "Intruso", "email": "intruso@demo.cl", "password": "Demo1234", "tipo": "GESTOR"},
    )
    assert respuesta.status_code == 422
    assert respuesta.json()["code"] == "VALIDACION"


def test_refresh_reutilizado_revoca_la_cadena(identidad: TestClient):
    login = identidad.post(
        "/api/v1/identidad/auth/login", json={"email": "colaborador@demo.cl", "password": "Demo1234"}
    ).json()
    primero = login["refresh_token"]

    rotado = identidad.post("/api/v1/identidad/auth/refresh", json={"refresh_token": primero})
    assert rotado.status_code == 200
    segundo = rotado.json()["refresh_token"]

    # Replay del token ya canjeado: 401 y se revoca también el token vigente.
    assert identidad.post("/api/v1/identidad/auth/refresh", json={"refresh_token": primero}).status_code == 401
    assert identidad.post("/api/v1/identidad/auth/refresh", json={"refresh_token": segundo}).status_code == 401


def test_gestor_no_modifica_cuentas_privilegiadas(identidad: TestClient):
    token = identidad.post(
        "/api/v1/identidad/auth/login", json={"email": "gestor@albaconecta.cl", "password": "Demo1234"}
    ).json()["access_token"]
    respuesta = identidad.patch(
        "/api/v1/identidad/usuarios/usr-1/estado",
        json={"activo": False},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert respuesta.status_code == 403
    assert respuesta.json()["code"] == "PROHIBIDO"


# --- ms-vinculacion (casos 5, 6, 7, 10 y 11) ---


def test_solicitud_crea_folio_y_es_idempotente(vinculacion: TestClient):
    clave = str(uuid.uuid4())
    primera = vinculacion.post(
        "/api/v1/vinculacion/solicitudes", json=SOLICITUD, headers={"Idempotency-Key": clave}
    )
    assert primera.status_code == 201
    folio = primera.json()["folio"]
    assert re.match(r"^AC-[A-Z0-9]{8}$", folio)
    assert primera.json()["estado"] == "RECIBIDA"

    repetida = vinculacion.post(
        "/api/v1/vinculacion/solicitudes", json=SOLICITUD, headers={"Idempotency-Key": clave}
    )
    assert repetida.status_code == 200
    assert repetida.json()["folio"] == folio

    distinta = vinculacion.post(
        "/api/v1/vinculacion/solicitudes",
        json={**SOLICITUD, "mensaje": "Otro mensaje"},
        headers={"Idempotency-Key": clave},
    )
    assert distinta.status_code == 409
    assert distinta.json()["code"] == "IDEMPOTENCY_CONFLICT"


def test_solicitud_persistida_sobrevive_a_recrear_almacen(vinculacion: TestClient):
    respuesta = vinculacion.post(
        "/api/v1/vinculacion/solicitudes",
        json=SOLICITUD,
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    assert respuesta.status_code == 201
    folio = respuesta.json()["folio"]

    nuevo_almacen = Almacen()
    solicitud = nuevo_almacen.por_folio(folio)
    assert solicitud is not None
    assert solicitud["folio"] == folio


def test_solicitudes_concurrentes_con_misma_clave_crean_un_solo_folio():
    clave = str(uuid.uuid4())

    def enviar(_):
        # Un TestClient por hilo: event loop y sesiones SQL independientes,
        # lo más parecido a dos procesos atendiendo la misma clave.
        return TestClient(app_vinculacion).post(
            "/api/v1/vinculacion/solicitudes",
            json=SOLICITUD,
            headers={"Idempotency-Key": clave},
        )

    with ThreadPoolExecutor(max_workers=2) as ejecutor:
        respuestas = list(ejecutor.map(enviar, range(2)))

    assert sorted(respuesta.status_code for respuesta in respuestas) == [200, 201]
    folios = {respuesta.json()["folio"] for respuesta in respuestas}
    assert len(folios) == 1

    # Una sola fila de solicitud y una sola reserva, ya confirmada.
    with db.sesion("vinculacion") as sesion:
        solicitudes = sesion.execute(
            select(Solicitud).where(Solicitud.folio == folios.pop())
        ).scalars().all()
        reserva = sesion.get(IdempotenciaSolicitud, clave)
    assert len(solicitudes) == 1
    assert reserva is not None
    assert reserva.solicitud_id == solicitudes[0].id


def test_claves_distintas_crean_solicitudes_independientes(vinculacion: TestClient):
    primera = vinculacion.post(
        "/api/v1/vinculacion/solicitudes", json=SOLICITUD, headers={"Idempotency-Key": str(uuid.uuid4())}
    )
    segunda = vinculacion.post(
        "/api/v1/vinculacion/solicitudes", json=SOLICITUD, headers={"Idempotency-Key": str(uuid.uuid4())}
    )
    assert (primera.status_code, segunda.status_code) == (201, 201)
    assert primera.json()["id"] != segunda.json()["id"]
    assert primera.json()["folio"] != segunda.json()["folio"]


def test_reserva_abandonada_libera_la_clave(vinculacion: TestClient):
    """Si el proceso murió después de reservar, la clave se puede reutilizar."""
    clave = str(uuid.uuid4())
    with db.sesion("vinculacion") as sesion:
        sesion.add(
            IdempotenciaSolicitud(
                clave=clave,
                huella="huella-de-un-intento-abandonado",
                solicitud_id=None,
                expira=time.time() - 1,
            )
        )

    respuesta = vinculacion.post(
        "/api/v1/vinculacion/solicitudes", json=SOLICITUD, headers={"Idempotency-Key": clave}
    )
    assert respuesta.status_code == 201
    assert respuesta.json()["folio"].startswith("AC-")


def test_solicitud_sin_idempotency_key(vinculacion: TestClient):
    respuesta = vinculacion.post("/api/v1/vinculacion/solicitudes", json=SOLICITUD)
    assert respuesta.status_code == 422
    assert respuesta.json()["code"] == "VALIDACION"


def test_referencia_inexistente_no_bloquea(vinculacion: TestClient):
    respuesta = vinculacion.post(
        "/api/v1/vinculacion/solicitudes",
        json={**SOLICITUD, "programa_id": "prg-inexistente"},
        headers={"Idempotency-Key": str(uuid.uuid4())},
    )
    assert respuesta.status_code == 201
    assert respuesta.json()["referencia_no_verificada"] is True
    assert respuesta.json()["programa_titulo"] is None


def test_consulta_por_folio_enmascara_pii(vinculacion: TestClient):
    folio = vinculacion.post(
        "/api/v1/vinculacion/solicitudes", json=SOLICITUD, headers={"Idempotency-Key": str(uuid.uuid4())}
    ).json()["folio"]

    cuerpo = vinculacion.get(f"/api/v1/vinculacion/solicitudes/folio/{folio.lower()}").json()
    assert cuerpo["email"] == "a***@correo.cl"
    assert cuerpo["telefono"].endswith("78")
    assert cuerpo["telefono"].startswith("*")
    assert "mensaje" not in cuerpo


def test_maquina_de_estados(vinculacion: TestClient):
    cuerpo = vinculacion.post(
        "/api/v1/vinculacion/solicitudes", json=SOLICITUD, headers={"Idempotency-Key": str(uuid.uuid4())}
    ).json()
    id_ = cuerpo["id"]
    gestor = {"Authorization": f"Bearer {emitir_access('usr-2', 'GESTOR')}"}

    salto = vinculacion.patch(f"/api/v1/vinculacion/solicitudes/{id_}/estado", json={"estado": "CONCRETADO"}, headers=gestor)
    assert salto.status_code == 409
    assert salto.json()["code"] == "TRANSICION_INVALIDA"

    sin_observacion = vinculacion.patch(
        f"/api/v1/vinculacion/solicitudes/{id_}/estado", json={"estado": "DESCARTADO"}, headers=gestor
    )
    assert sin_observacion.status_code == 422

    descartada = vinculacion.patch(
        f"/api/v1/vinculacion/solicitudes/{id_}/estado",
        json={"estado": "DESCARTADO", "observacion_interna": "Fuera de alcance"},
        headers=gestor,
    )
    assert descartada.status_code == 200
    assert descartada.json()["estado"] == "DESCARTADO"

    historial = vinculacion.get(f"/api/v1/vinculacion/solicitudes/{id_}/historial", headers=gestor).json()
    assert [paso["estado_nuevo"] for paso in historial] == ["RECIBIDA", "DESCARTADO"]


def test_solicitudes_exigen_gestor(vinculacion: TestClient):
    respuesta = vinculacion.get("/api/v1/vinculacion/solicitudes")
    assert respuesta.status_code == 401
    assert respuesta.json()["code"] == "NO_AUTENTICADO"

    otro_rol = {"Authorization": f"Bearer {emitir_access('usr-4', 'COLABORADOR')}"}
    assert vinculacion.get("/api/v1/vinculacion/solicitudes", headers=otro_rol).status_code == 403


# --- ms-talento (casos 13 y 14) ---


def test_compatibilidad_excluye_sin_apoyos(talento: TestClient):
    empresa = {"Authorization": f"Bearer {emitir_access('usr-3', 'EMPRESA')}"}
    puesto = talento.post(
        "/api/v1/talento/puestos",
        json={
            "titulo": "Operario de línea",
            "tareas": [{"apoyo_codigo": "AP-01"}, {"apoyo_codigo": "AP-03"}],
        },
        headers=empresa,
    )
    assert puesto.status_code == 201

    ranking = talento.post(f"/api/v1/talento/puestos/{puesto.json()['id']}/compatibilidad", headers=empresa).json()
    assert ranking["resultados"][0]["codigo_publico"] == "C-004"
    assert ranking["resultados"][0]["puntaje"] == 100
    assert [omitido["codigo_publico"] for omitido in ranking["omitidos"]] == ["C-003"]


def test_puesto_ajeno_responde_404(talento: TestClient):
    empresa = {"Authorization": f"Bearer {emitir_access('usr-3', 'EMPRESA')}"}
    puesto = talento.post(
        "/api/v1/talento/puestos",
        json={"titulo": "Puesto privado", "tareas": [{"apoyo_codigo": "AP-02"}]},
        headers=empresa,
    ).json()

    otra = {"Authorization": f"Bearer {emitir_access('usr-99', 'EMPRESA')}"}
    respuesta = talento.get(f"/api/v1/talento/puestos/{puesto['id']}", headers=otra)
    assert respuesta.status_code == 404
    assert respuesta.json()["code"] == "NO_ENCONTRADO"
