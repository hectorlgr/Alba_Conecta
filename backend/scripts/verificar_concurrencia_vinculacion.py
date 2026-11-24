"""Verifica idempotencia real mediante dos requests HTTP paralelos."""

import concurrent.futures
import uuid

import httpx


URL = "http://127.0.0.1:8080/api/v1/vinculacion/solicitudes"
PAYLOAD = {
    "nombre_contacto": "Persona Demo",
    "organizacion": "Organización Demo",
    "email": "demo@example.org",
    "telefono": "+56900000000",
    "mensaje": "Solicitud concurrente de demostración.",
    "acepta_privacidad": True,
    "detalles": [{"tipo_colaboracion_codigo": "VOLUNTARIADO"}],
}


def enviar():
    clave = enviar.clave
    respuesta = httpx.post(URL, json=PAYLOAD, headers={"Idempotency-Key": clave}, timeout=10)
    respuesta.raise_for_status()
    return respuesta.status_code, respuesta.json()["folio"]


def main() -> None:
    enviar.clave = str(uuid.uuid4())
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        resultados = list(pool.map(lambda _: enviar(), range(2)))

    folios = {folio for _, folio in resultados}
    if sorted(status for status, _ in resultados) not in ([200, 201], [201, 200]):
        raise SystemExit(f"Estados inesperados: {resultados}")
    if len(folios) != 1:
        raise SystemExit(f"Se crearon folios distintos: {folios}")
    print(f"OK: una solicitud, folio {folios.pop()}, respuestas {sorted(status for status, _ in resultados)}")


if __name__ == "__main__":
    main()
