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

Ejecuta todo desde `AlbaConecta/backend`. Docker Compose es igual en Windows y Linux; sólo cambia cómo se generan las claves y el `.env`. Requiere Docker Desktop en Windows o Docker Engine + Compose en Linux.

### Linux / macOS (bash)

```bash
cd AlbaConecta/backend

# 0. Opcional y DESTRUCTIVO: borra los volúmenes (las 4 bases) e imágenes locales
docker compose down -v --rmi local

# 1. Claves RSA (una sola vez; secrets/ y .env están gitignored)
mkdir -p secrets
openssl genrsa -out secrets/jwt_private.pem 2048
openssl rsa -in secrets/jwt_private.pem -pubout -out secrets/jwt_public.pem

# 2. Variables de entorno (ajusta SECRETO y JWT_KID)
cat > .env <<'EOF'
ENTORNO=production
SECRETO=pon-aqui-un-secreto-largo-aleatorio-de-32-chars-minimo
JWT_PRIVATE_KEY=./secrets/jwt_private.pem
JWT_PUBLIC_KEY=./secrets/jwt_public.pem
JWT_KID=albaconecta-2026-11
DEMO_PASSWORD=cambia-esta-clave-de-demo
CORS_ORIGENES=http://localhost:8080
EOF

# 3. Levantar todo (PostgreSQL + 4 servicios + Traefik)
docker compose up -d --build

# 4. Verificar salud (desde host)
for u in contenidos vinculacion identidad talento; do
  echo "[$u]"; curl -sS http://127.0.0.1:8080/api/v1/$u/health/live | jq
done

# 5. Verificar migraciones y tablas
for db in bd_contenidos bd_vinculacion bd_identidad bd_talento; do
  echo "[$db]"
  docker compose exec -T postgres psql -U albaconecta -d "$db" -Atc "select tablename from pg_tables where schemaname='public' order by 1"
done
```

### Windows (PowerShell)

```powershell
cd AlbaConecta\backend

# 0. Opcional y DESTRUCTIVO: borra volúmenes (las 4 bases)
docker compose down -v --rmi local

# 1. Claves RSA: 'openssl' no viene en PowerShell; usa Git Bash (Git for Windows) o WSL
mkdir secrets
openssl genrsa -out secrets/jwt_private.pem 2048
openssl rsa -in secrets/jwt_private.pem -pubout -out secrets/jwt_public.pem

# 2. Variables de entorno (here-string literal)
@'
ENTORNO=production
SECRETO=pon-aqui-un-secreto-largo-aleatorio-de-32-chars-minimo
JWT_PRIVATE_KEY=./secrets/jwt_private.pem
JWT_PUBLIC_KEY=./secrets/jwt_public.pem
JWT_KID=albaconecta-2026-11
DEMO_PASSWORD=cambia-esta-clave-de-demo
CORS_ORIGENES=http://localhost:8080
'@ | Set-Content -Path .env -Encoding utf8

# 3. Levantar todo
docker compose up -d --build

# 4. Salud: en PowerShell 'curl' es un alias de Invoke-WebRequest y no hay jq
foreach ($u in @('contenidos','vinculacion','identidad','talento')) {
  Write-Host "[$u]"
  Invoke-RestMethod "http://127.0.0.1:8080/api/v1/$u/health/live" | ConvertTo-Json
}

# 5. Tablas por base
foreach ($db in @('bd_contenidos','bd_vinculacion','bd_identidad','bd_talento')) {
  Write-Host "[$db]"
  docker compose exec -T postgres psql -U albaconecta -d $db -Atc "select tablename from pg_tables where schemaname='public' order by 1"
}
```

> Con Git Bash en Windows sirven los comandos bash de arriba. `docker compose logs -f`, `ps` y `down` son idénticos en ambos sistemas.

## Conectar la app Android (Windows y Linux)

Traefik publica el gateway en `:8080`. La app debug lee `ALBA_HOST_IP` en `app/build.gradle.kts` y compila `API_BASE_URL = http://<IP>:8080/`; sin esa variable Gradle falla con `Define ALBA_HOST_IP=<IP del host> antes de compilar la app`. El dispositivo debe estar en la misma red y el firewall debe permitir TCP 8080 (`sudo ufw allow 8080/tcp` en Linux; regla de entrada en Windows Defender).

### Windows (PowerShell)

```powershell
ipconfig    # copia la IPv4 del adaptador Wi-Fi, p. ej. 192.168.1.10
# Añade la IP a CORS en backend\.env:
# CORS_ORIGENES=http://localhost:8080,http://192.168.1.10:8080
# Si el contenedor ya estaba arriba: docker compose up -d --build
cd "AlbaConecta"
$env:ALBA_HOST_IP = "192.168.1.10"
# Permanente: [Environment]::SetEnvironmentVariable("ALBA_HOST_IP","192.168.1.10","User")
.\gradlew.bat :app:installDebug
```

### Linux / macOS

```bash
hostname -I    # p. ej. 192.168.1.10
# Añade la IP a CORS en backend/.env y vuelve a levantar si hace falta
cd "AlbaConecta"
export ALBA_HOST_IP="192.168.1.10"
sh gradlew :app:installDebug
```

### Variante USB (opcional, igual en ambos sistemas)

Define `ALBA_HOST_IP=127.0.0.1` y ejecuta `adb reverse tcp:8080 tcp:8080` tras cada arranque del emulador/dispositivo. Con la IP LAN no hace falta el túnel. No uses `10.0.2.2` ni `localhost` en un teléfono físico: no apuntan al host Docker.

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

Cualquier sistema (recomendado, usa el Python del contenedor):

```bash
docker compose exec -T ms-talento python -m pytest -q
docker compose exec -T ms-talento python scripts/verificar_concurrencia_vinculacion.py
```

Desde el host:

```bash
# Linux/macOS
./.venv/bin/python -m pytest -q
./.venv/bin/python scripts/verificar_concurrencia_vinculacion.py
cd ../ && export ALBA_HOST_IP="192.168.1.10" && sh gradlew :app:testDebugUnitTest :app:assembleDebug && sh gradlew :app:lintDebug
```

```powershell
# Windows (PowerShell)
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe scripts/verificar_concurrencia_vinculacion.py
cd ..; $env:ALBA_HOST_IP = "192.168.1.10"; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug; .\gradlew.bat :app:lintDebug
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
| App Android “Connection refused” | `ALBA_HOST_IP` ausente/errónea o firewall sin 8080 | Define `ALBA_HOST_IP` con la IP LAN y abre el TCP 8080; por USB usa `adb reverse tcp:8080 tcp:8080` |
| `lintDebug` falla | SDK 37 no instalado | Verifica `android-37` en Android SDK Manager |
| `pytest` 0 tests | `DATABASE_URL` apunta a SQLite vacía sin migraciones | Exporta `DATABASE_URL=postgresql://...` o usa Compose |

## Referencias

- OpenAPI exportado: `docs/openapi/{contenidos,vinculacion,identidad,talento}.json`