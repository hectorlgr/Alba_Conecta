# Backend Alba Conecta — Compose + 4 servicios + PostgreSQL + Traefik

Implementación completa de los cuatro microservicios FastAPI con bases de datos PostgreSQL separadas, migraciones Alembic, autenticación RS256/JWKS y gateway Traefik en puerto 8080. Corresponde al cierre del plan (§12.2–§12.4 y §12.8).

## Arquitectura en ejecución

```
                    ┌─────────────────────────────────────┐
                    │        Traefik :8080 (LAN)          │
                    │  /api/v1/contenidos   → ms-contenidos │
                    │  /api/v1/vinculacion  → ms-vinculacion│
                    │  /api/v1/identidad    → ms-identidad  │
                    │  /api/v1/talento      → ms-talento    │
                    └─────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼                   ▼
   ┌────────────┐      ┌────────────┐      ┌────────────┐      ┌────────────┐
   │bd_contenidos│      │bd_vinculacion│    │bd_identidad│      │bd_talento  │
   │(PostgreSQL) │      │(PostgreSQL)  │    │(PostgreSQL)│      │(PostgreSQL)│
   └────────────┘      └────────────┘      └────────────┘      └────────────┘
          │                   │                   │                   │
   ┌──────┴──────┐     ┌──────┴──────┐     ┌──────┴──────┐     ┌──────┴──────┐
   │categoria   │     │solicitud    │     │usuario      │     │candidato    │
   │programa    │     │historial    │     │refresh_token│     │apoyo        │
   │indicador   │     │idempotencia │     │             │     │candidato_apoyo│
   │historia    │     │_solicitud   │     │             │     │             │
   └────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
```

Cada servicio es un contenedor independiente con su propia base de datos física. El entrypoint ejecuta `alembic upgrade <dominio>@head` antes de arrancar Uvicorn.

## Levantar desde cero (reproducible)

```bash
cd AlbaConecta/backend

# 1. Limpieza total (volúmenes, imágenes, bases)
docker compose down -v --rmi local

# 2. Generar claves RSA (una sola vez; guarda las rutas en .env)
mkdir -p secrets
openssl genrsa -out secrets/jwt_private.pem 2048
openssl rsa -in secrets/jwt_private.pem -pubout -out secrets/jwt_public.pem

# 3. Variables de entorno (ajusta SECRETO y JWT_KID)
cat > .env <<'EOF'
ENTORNO=production
SECRETO=pon-aqui-un-secreto-largo-aleatorio-de-32-chars-minimo
JWT_PRIVATE_KEY=./secrets/jwt_private.pem
JWT_PUBLIC_KEY=./secrets/jwt_public.pem
JWT_KID=albaconecta-2026-11
DEMO_PASSWORD=cambia-esta-clave-de-demo
CORS_ORIGENES=http://localhost:8080
EOF

# 4. Levantar todo (PostgreSQL + 4 servicios + Traefik)
docker compose up -d --build

# 5. Verificar salud (desde host)
for u in contenidos vinculacion identidad talento; do
  echo "[$u]"; curl -sS http://127.0.0.1:8080/api/v1/$u/health/live | jq
done

# 6. Verificar migraciones y tablas
for db in bd_contenidos bd_vinculacion bd_identidad bd_talento; do
  echo "[$db]"
  docker compose exec -T postgres psql -U albaconecta -d "$db" -Atc "select tablename from pg_tables where schemaname='public' order by 1"
done

# 7. Pruebas backend
docker compose exec -T ms-talento python -m pytest -q
# o desde host con el venv local
./.venv/bin/python -m pytest -q
```

## Conexión desde dispositivo Android físico (teléfono externo)

La app Android en modo debug usa `API_BASE_URL = http://127.0.0.1:8080/` (gateway Traefik). Para que el teléfono en la misma red LAN llegue al backend:

### Opción A: `adb reverse` (teléfono por USB, **recomendado para desarrollo**)

```bash
# En la máquina donde corre Docker (host)
adb reverse tcp:8080 tcp:8080

# Instala y abre la app
sh gradlew :app:installDebug
```

`adb reverse` crea un túnel TCP del puerto 8080 del teléfono al puerto 8080 del host. La app ve `http://127.0.0.1:8080` y funciona igual que en emulador.

### Opción B: IP LAN del host (teléfono en la misma Wi-Fi, **sin USB**)

1. Averigua la IP LAN de la máquina host:
   ```bash
   ip -4 addr show scope global | awk '/inet /{print $2}' | cut -d/ -f1
   # ej.: 192.168.1.47
   ```

2. En `AlbaConecta/app/src/main/java/com/albaconecta/app/data/remote/ApiClient.kt` (o `BuildConfig`), cambia `API_BASE_URL` de debug a:
   ```kotlin
   const val API_BASE_URL = "http://192.168.1.47:8080/"
   ```
   (reemplaza `192.168.1.47` por tu IP real).

3. Recompila e instala:
   ```bash
   sh gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

**Notas importantes:**
- Traefik escucha en `0.0.0.0:8080` (puerto publicado en host). El firewall del host debe permitir entrada en 8080 desde la red LAN.
- En release la app exige `API_BASE_URL` configurada (vacío → error claro). No uses `10.0.2.2` ni `localhost` en dispositivo físico: no resuelven al host Docker.
- CORS está configurado con `CORS_ORIGENES=http://localhost:8080` en Compose. Si usas IP LAN, añádela: `CORS_ORIGENES=http://localhost:8080,http://192.168.1.47:8080`.

## Variables de entorno (producción)

| Variable | Requerida | Descripción |
|---|---|---|
| `ENTORNO` | sí | `production` (desactiva generación de claves y seed demo) |
| `SECRETO` | sí | Mín. 32 chars aleatorios; firma compatibilidad HS256 residual |
| `JWT_PRIVATE_KEY` | sí (identidad) | Ruta a PEM PKCS#8 (`/run/secrets/jwt_private.pem` en Compose) |
| `JWT_PUBLIC_KEY` | sí | Ruta a PEM SPKI (`/run/secrets/jwt_public.pem` en Compose) |
| `JWT_KID` | sí | Identificador estable de la clave (ej. `albaconecta-2026-11`) |
| `DEMO_PASSWORD` | **no** | Solo desarrollo; **nunca** en producción |
| `CORS_ORIGENES` | sí | Lista separada por comas de orígenes permitidos |
| `DATABASE_URL_<DOMINIO>` | sí | Una por servicio; inyectada por Compose |
| `MIGRACION_DOMINIO` | sí | `contenidos`, `vinculacion`, `identidad` o `talento` (entrypoint) |
| `ACCESS_TTL` / `REFRESH_TTL` | no | Segundos; default 900 / 2592000 |

## Endpoints clave (vía Traefik :8080)

| Servicio | Health live | Health ready | Swagger |
|---|---|---|---|
| Contenidos | `/api/v1/contenidos/health/live` | `/api/v1/contenidos/health/ready` | `/api/v1/contenidos/docs` |
| Vinculación | `/api/v1/vinculacion/health/live` | `/api/v1/vinculacion/health/ready` | `/api/v1/vinculacion/docs` |
| Identidad | `/api/v1/identidad/health/live` | `/api/v1/identidad/health/ready` | `/api/v1/identidad/docs` |
| Talento | `/api/v1/talento/health/live` | `/api/v1/talento/health/ready` | `/api/v1/talento/docs` |

JWKS pública: `GET /api/v1/identidad/.well-known/jwks.json`

## Pruebas

```bash
# Backend (45 pruebas de contrato)
./.venv/bin/python -m pytest -q

# Concurrencia real (dos hilos, misma Idempotency-Key → un solo folio)
./.venv/bin/python scripts/verificar_concurrencia_vinculacion.py

# Android
sh gradlew :app:testDebugUnitTest      # 59 tests unitarios
sh gradlew :app:assembleDebug          # APK debug
sh gradlew :app:lintDebug              # Lint limpio
```

## Decisiones cerradas

- **Persistencia completa**: 4 bases físicas, Alembic con branch labels, migraciones por dominio.
- **Talento**: catálogo público (candidato, apoyo, candidato_apoyo) en PostgreSQL; puestos/diagnóstico siguen en memoria (MVP).
- **Auth**: RS256 + JWKS real. Identidad firma con clave privada; consumidores validan con pública.
- **Idempotencia**: tabla `idempotencia_solicitud` con reserva + confirmación atómica; test de concurrencia pasa.
- **Traefik**: proveedor estático `file` (evita API Docker antigua), entrypoint interno `:8088`, publicado `:8080`.
- **Seed**: se inserta una sola vez al estar vacía la tabla (comprobación `select ... limit 1`).

## Solución de problemas

| Síntoma | Causa típica | Solución |
|---|---|---|
| Traefik `Exited (1)` | Puerto 8080 ocupado en host | `ss -ltnp | grep :8080` y libera; Compose usa 8080→8088 |
| `422 Unprocessable Entity` en POST `/solicitudes` | Falta `acepta_privacidad: true` o email/teléfono | Revisa payload contra `SolicitudIn` |
| `docker compose up` lento primera vez | Descarga imágenes + toolchain Gradle | Normal; segundo arranque usa caché |
| App Android “Connection refused” | `adb reverse` no ejecutado o IP LAN errónea | Ejecuta `adb reverse tcp:8080 tcp:8080` o corrige `API_BASE_URL` |
| `lintDebug` falla | SDK 37 no instalado | Verifica `android-37` en Android SDK Manager |
| `pytest` 0 tests | `DATABASE_URL` apunta a SQLite vacía sin migraciones | Exporta `DATABASE_URL=postgresql://...` o usa Compose |

## Referencias

- OpenAPI exportado: `docs/openapi/{contenidos,vinculacion,identidad,talento}.json`