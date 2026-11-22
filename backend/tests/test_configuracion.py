import pytest

from albaconecta_backend.common.config import (
    Settings,
    url_base_datos,
    validar_configuracion,
    validar_secreto,
)


def test_desarrollo_requiere_password_demo():
    with pytest.raises(RuntimeError, match="DEMO_PASSWORD"):
        validar_configuracion(Settings(entorno="dev", demo_password=""))


def test_fase2_produccion_no_permite_create_all():
    from albaconecta_backend.common.db import crear_tablas

    with pytest.raises(RuntimeError, match="create_all"):
        crear_tablas("contenidos", Settings(entorno="production"))


def test_fase1_metadatos_sql_estan_separados_por_dominio():
    from albaconecta_backend.common.db import (
        BaseContenidos,
        BaseIdentidad,
        BaseVinculacion,
    )
    from albaconecta_backend.contenidos import models as contenidos  # noqa: F401
    from albaconecta_backend.identidad import models as identidad  # noqa: F401
    from albaconecta_backend.vinculacion import models as vinculacion  # noqa: F401

    assert set(BaseContenidos.metadata.tables) == {
        "categoria", "programa", "historia", "indicador", "producto"
    }
    assert set(BaseVinculacion.metadata.tables) == {
        "solicitud", "historial_solicitud", "idempotencia_solicitud"
    }
    assert set(BaseIdentidad.metadata.tables) == {"usuario", "refresh_token"}


def _par_rsa_prueba():
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import rsa

    privada = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem_privada = privada.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    ).decode()
    pem_publica = privada.public_key().public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    ).decode()
    return pem_privada, pem_publica


def test_fase3_claves_rsa_correspondentes():
    from albaconecta_backend.common.config import validar_claves_rsa

    pem_privada, pem_publica = _par_rsa_prueba()
    validar_claves_rsa(Settings(jwt_private_key=pem_privada, jwt_public_key=pem_publica))


def test_fase3_acepta_claves_rsa_desde_archivo(tmp_path):
    """D-004: la clave privada se monta como archivo, no como variable gigante."""
    from albaconecta_backend.common.config import validar_claves_rsa

    pem_privada, pem_publica = _par_rsa_prueba()
    ruta_privada = tmp_path / "private.pem"
    ruta_publica = tmp_path / "public.pem"
    ruta_privada.write_text(pem_privada, encoding="utf-8")
    ruta_publica.write_text(pem_publica, encoding="utf-8")

    validar_claves_rsa(
        Settings(jwt_private_key=str(ruta_privada), jwt_public_key=str(ruta_publica))
    )


def test_fase3_rechaza_claves_rsa_no_correspondientes():
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from albaconecta_backend.common.config import validar_claves_rsa

    privada = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    otra = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem_privada = privada.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    ).decode()
    pem_publica = otra.public_key().public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    ).decode()

    with pytest.raises(RuntimeError, match="no corresponden"):
        validar_claves_rsa(Settings(jwt_private_key=pem_privada, jwt_public_key=pem_publica))


def test_produccion_consumidor_no_exige_clave_privada():
    pem_privada, pem_publica = _par_rsa_prueba()
    validar_configuracion(
        Settings(
            entorno="production",
            secreto="secreto-seguro-para-produccion-123456789",
            cors_origenes=("https://albaconecta.cl",),
            jwt_private_key=pem_privada,
            jwt_public_key=pem_publica,
            jwt_kid="albaconecta-production-2026",
        ),
        exige_clave_privada=False,
    )


def test_fase1_fallback_desarrollo_usa_database_url():
    settings = Settings(entorno="dev", database_url="sqlite:///./test.db")
    assert url_base_datos(settings, "contenidos") == "sqlite:///./test.db"


def test_fase1_produccion_exige_url_por_dominio():
    settings = Settings(entorno="production", database_url_vinculacion="postgresql://bd")
    assert url_base_datos(settings, "vinculacion") == "postgresql://bd"
    with pytest.raises(RuntimeError, match="DATABASE_URL_CONTENIDOS"):
        url_base_datos(settings, "contenidos")


def test_entorno_desconocido_falla():
    with pytest.raises(RuntimeError, match="ENTORNO inválido"):
        validar_secreto(Settings(entorno="staging", secreto="secreto-seguro-para-pruebas-123456789"))


def test_produccion_no_exige_password_demo():
    pem_privada, pem_publica = _par_rsa_prueba()
    validar_configuracion(
        Settings(
            entorno="production",
            secreto="secreto-seguro-para-produccion-123456789",
            demo_password="",
            cors_origenes=("https://albaconecta.cl",),
            jwt_private_key=pem_privada,
            jwt_public_key=pem_publica,
            jwt_kid="albaconecta-production-2026",
        )
    )


def test_produccion_requiere_secreto():
    with pytest.raises(RuntimeError, match="SECRETO no configurado"):
        validar_secreto(Settings(entorno="production", secreto=""))


def test_produccion_rechaza_secreto_conocido():
    with pytest.raises(RuntimeError, match="SECRETO inseguro"):
        validar_secreto(
            Settings(entorno="production", secreto="dev-secreto-cambiar-en-produccion")
        )


def test_produccion_rechaza_secreto_corto():
    with pytest.raises(RuntimeError, match="SECRETO demasiado corto"):
        validar_secreto(Settings(entorno="production", secreto="demasiado-corto"))


def test_produccion_rechaza_cors_global():
    with pytest.raises(RuntimeError, match="CORS_ORIGENES"):
        validar_configuracion(
            Settings(
                entorno="production",
                secreto="secreto-seguro-para-produccion-123456789",
                cors_origenes=("*",),
            )
        )


def test_produccion_acepta_secreto_y_origen_explicitos():
    pem_privada, pem_publica = _par_rsa_prueba()
    validar_configuracion(
        Settings(
            entorno="production",
            secreto="secreto-seguro-para-produccion-123456789",
            cors_origenes=("https://albaconecta.cl",),
            jwt_private_key=pem_privada,
            jwt_public_key=pem_publica,
            jwt_kid="albaconecta-production-2026",
        )
    )
