# Alba Conecta 🤝

Aplicación **Android (Kotlin + Jetpack Compose)** y backend **FastAPI** para el curso **DSY1105 – Desarrollo de Aplicaciones Móviles**, en el contexto de [CORPALIV](https://corpaliv.cl/) (*"Comprometidos con la discapacidad múltiple"*).

La app visibiliza iniciativas de inclusión social y laboral, muestra el impacto de la fundación y permite manifestar interés en colaborar, incluso **sin conexión**: el formulario se guarda en Room y se envía solo al volver la red.

> **Versión actual (noviembre 2026):** cierre de las fases 7–8 (caché local, outbox, WorkManager y detección de red) sobre el backend distribuido con Docker Compose + Traefik en `:8080`.

## Integrantes

- [Hector Gutierrez]
- [Braulio Gomez]

## Funcionalidades

| Pantalla | Descripción |
|----------|-------------|
| **Inicio** | Bienvenida, propósito y programas de `/api/v1/contenidos/programas`. Lee caché Room primero y sincroniza en segundo plano. |
| **Impacto** | Indicadores e historias desde `/api/v1/contenidos/indicadores`, también con caché Room. |
| **Talento Alba** | Catálogo público desde `/api/v1/talento/candidatos` y `/apoyos`; compatibilidad y puestos siguen en memoria (MVP). |
| **Colaborar** | Formulario validado con foto (cámara) y dictado (`es-CL`) → `POST /api/v1/vinculacion/solicitudes` con `Idempotency-Key`. Sin red queda en la cola outbox. |
| **Iniciar sesión** | `POST /api/v1/identidad/auth/login` → tokens RS256. |
| **Registro** | `POST /api/v1/identidad/auth/register` (solo COLABORADOR). |
| **Perfil** | `GET /api/v1/identidad/auth/me`, alta de huella y "Eliminar datos de este dispositivo" (borra Room, sesión y credencial biométrica; **no** borra la cuenta del servidor). |

### Reglas de negocio

- RUT chileno con dígito verificador (`12.345.678-9`).
- Teléfono `+56` de 9 dígitos.
- Registro solo para mayores de edad.
- Correo normalizado (sin espacios, en minúsculas).

## Arquitectura

```
Dispositivo Android
  UI (Compose) → ViewModels (StateFlow: flujo de estado observable)
               → Repositorios + Retrofit (cliente HTTP)
               → Room (BD local SQLite: ficha, caché y outbox) + DataStore (sesión)
                            │ HTTP/JSON
                            ▼
Traefik :8080  ──► /api/v1/contenidos   → ms-contenidos   → bd_contenidos
               ──► /api/v1/vinculacion  → ms-vinculacion  → bd_vinculacion
               ──► /api/v1/identidad    → ms-identidad    → bd_identidad
               ──► /api/v1/talento      → ms-talento      → bd_talento
                     (cada servicio aplica migraciones Alembic al arrancar)
```

Puntos que no son obvios:

- `startDestination` es siempre `HOME`; Login/Registro se alcanzan desde la TopBar y `PROFILE` queda fuera del `MainScaffold`.
- No hay framework de inyección de dependencias: cada ViewModel construye las suyas y los repositorios reciben el cliente HTTP como lambda.
- La sesión (access/refresh token y datos mínimos del usuario) vive en DataStore. La huella usa un **refresh token reservado** distinto al de la sesión.
- Auth con **RS256 + JWKS**; los errores del backend llegan como `application/problem+json`.
- La cámara usa `TakePicture` + `FileProvider` sobre `filesDir/contact_photos/`; no se piden permisos `CAMERA`/`RECORD_AUDIO` enruntime y la foto **no** se adjunta al backend en esta versión.

### Funcionamiento sin conexión

- `Conectividad` (`util/Conectividad.kt`) observa la red con `ConnectivityManager` y expone un `StateFlow<Boolean>` global.
- `AppViewModel` muestra un banner en `MainScaffold`: `Sin conexión · N solicitudes por enviar` / `N solicitudes pendientes de envío`.
- Inicio e Impacto leen Room; si no hay red ni caché muestran error, no "sin contenidos".
- `ContactViewModel.submit()` valida, guarda en la tabla `solicitudes_outbox` con estado `PENDIENTE` y devuelve `Encolada` de inmediato.
- `OutboxWorker` (WorkManager: envío inmediato + periódico cada 15 min con backoff) reintenta con la **misma Idempotency-Key** y guarda el folio. Estados: `PENDIENTE → ENVIANDO → ENVIADA | FALLIDA | RECHAZADA`.
- Al reconectar, `AppViewModel` encola un envío inmediato; si hay pendientes al arrancar la app también se encolan.

**Deuda conocida:** el backend expone `GET /api/v1/contenidos/sync` con lápidas, pero la app aún no lo consume; Talento, tipos y historias son 100 % remotos.

## Requisitos

- Android Studio con SDK 37 y JDK 25 para el daemon de Gradle. `gradlew` no tiene permiso de ejecución: usar `sh gradlew`.
- Docker + Docker Compose para el backend completo (o Python 3.13 para pruebas con SQLite).
- `adb` y un dispositivo/emulador para probar la app.

## 1. Backend

```bash
cd "AlbaConecta/backend"

# Claves RSA de desarrollo (una sola vez; secrets/ y .env están gitignored)
mkdir -p secrets
openssl genrsa -out secrets/jwt_private.pem 2048
openssl rsa -in secrets/jwt_private.pem -pubout -out secrets/jwt_public.pem

# Copia el ejemplo y ajusta SECRETO, DEMO_PASSWORD y JWT_KID
cp .env.example .env

# Opcional: limpieza total antes de levantar
docker compose down -v --rmi local
docker compose up -d --build

# Salud de los cuatro servicios vía Traefik
for u in contenidos vinculacion identidad talento; do
  curl -sS "http://127.0.0.1:8080/api/v1/$u/health/live"
done

# Pruebas de contrato (45) y concurrencia real
./.venv/bin/python -m pytest -q
./.venv/bin/python scripts/verificar_concurrencia_vinculacion.py
```

Alternativa rápida sin Docker: `uvicorn albaconecta_backend.gateway:app --port 8000` usa SQLite por defecto (`backend/albaconecta.db`). El código de debug de la app apunta a `:8080`; para usar el gateway cambia `API_BASE_URL` en `app/build.gradle.kts`.

## 2. Conectar la app

El debug ya **no** usa `127.0.0.1` ni `adb reverse`: `app/build.gradle.kts` lee la variable de entorno **`ALBA_HOST_IP`** y la escribe en `API_BASE_URL` (`http://<IP>:8080/`). Sin esa variable cualquier comando Gradle falla con `Define ALBA_HOST_IP=<IP del host> antes de compilar la app`.

El dispositivo/emulador debe estar en la **misma Wi-Fi/red** que el host y el backend escucha en `:8080` (Traefik publica el puerto en todas las interfaces de Docker).

### 2.1. Obtener la IP del host

- **Windows (PowerShell):** `ipconfig` → copia la **Dirección IPv4** del adaptador Wi-Fi (p. ej. `192.168.1.10`).
- **Linux/macOS:** `hostname -I` (o `ip addr`) → la IP de la interfaz de red.

### 2.2. Ampliar CORS en el backend

En `backend/.env` agrega la IP a los orígenes permitidos (separados por coma; recuerda `docker compose up -d --build` si los contenedores ya estaban arriba):

```dotenv
CORS_ORIGENES=http://localhost:8080,http://192.168.1.10:8080
```

### 2.3. Definir `ALBA_HOST_IP` e instalar

**Windows (PowerShell):**

```powershell
$env:ALBA_HOST_IP = "192.168.1.10"    # solo la sesión actual
# Permanente (nuevas terminales):
# [Environment]::SetEnvironmentVariable("ALBA_HOST_IP", "192.168.1.10", "User")
cd "AlbaConecta"
.\gradlew.bat :app:installDebug
```

**Linux/macOS:**

```bash
export ALBA_HOST_IP="192.168.1.10"
cd "AlbaConecta"
sh gradlew :app:installDebug
```

> La alternativa por USB sigue sirviendo: define `ALBA_HOST_IP=127.0.0.1` y ejecuta `adb reverse tcp:8080 tcp:8080` tras cada arranque del emulador/dispositivo. Con la IP LAN no hace falta el túnel.

## 3. Compilar y probar Android

Con `ALBA_HOST_IP` ya definido (§2.3):

```bash
cd "AlbaConecta"
sh gradlew --offline :app:testDebugUnitTest :app:assembleDebug   # 59 tests, 0 fallos
sh gradlew :app:lintDebug
```

En Windows, los mismos comandos con `.\gradlew.bat`. `--offline` sirve para tests y `assembleDebug`, pero no para `lintDebug` ni `assembleRelease`.

## 4. Probar el modo offline (formulario → Room → envío automático)

> Los pasos con `adb reverse` asumen la variante USB (§2.3). Si conectaste por IP LAN, omite esos comandos y solo activa/desactiva el modo avión para cortar/restaurar la red.

1. Con backend y `adb reverse tcp:8080 tcp:8080` activos, inicia sesión.
2. Corta el túnel y la radio: `adb reverse --remove tcp:8080` y `adb shell cmd connectivity airplane-mode enable`.
3. Envía el formulario. Debes ver "Gracias! … El folio aparecerá aquí cuando se confirme el envío" y el banner `Sin conexión · 1 solicitud por enviar`.
4. Consulta la outbox (se copian tres archivos porque SQLite usa WAL):

   ```bash
   mkdir -p /tmp/alba_offline
   for f in alba_database alba_database-wal alba_database-shm; do
     adb exec-out run-as com.albaconecta.app cat databases/$f > /tmp/alba_offline/$f
   done
   sqlite3 /tmp/alba_offline/alba_database \
     "select id, idempotencyKey, estado, intentos, folioRemoto from solicitudes_outbox order by id desc limit 3;"
   ```

   Estado esperado: `FALLIDA` (o `ENVIANDO` si lo miras en el instante), `folioRemoto` nulo y la misma `idempotencyKey`.
5. Restaura primero el túnel y después la red:

   ```bash
   adb reverse tcp:8080 tcp:8080
   adb shell cmd connectivity airplane-mode disable
   ```

   En segundos: fila `ENVIADA` con folio `AC-…`, banner oculto y folio visible en la pantalla.

## Estructura del repositorio

```
AlbaConecta/
├── app/
│   ├── src/main/java/com/albaconecta/app/
│   │   ├── data/local/      Room (AlbaDatabase, ficha, caché, outbox, OutboxWorker)
│   │   ├── data/remote/     Retrofit (AlbaApi, ApiClient) y DTOs
│   │   ├── data/repository/ Auth, User, Contenidos, Solicitud
│   │   ├── navigation/      NavHost + Routes
│   │   ├── ui/              Compose: screens, components, theme
│   │   ├── util/            Conectividad, cámara, biometría, notificaciones
│   │   ├── validation/      Validadores puros
│   │   └── viewmodel/       App, Login, Register, Profile, Contact, Contenidos
│   ├── schemas/             Esquemas Room exportados (v1, v2)
│   └── build.gradle.kts     API_BASE_URL por build type
├── backend/
│   ├── albaconecta_backend/ common + 4 dominios FastAPI + gateway.py
│   ├── alembic/             Migraciones por dominio
│   ├── docker/              Entrypoint, initdb y Traefik
│   ├── docs/ (scripts/)     Verificación de concurrencia
│   ├── tests/               Pruebas de contrato
│   ├── .env.example         Plantilla sin secretos
│   └── docker-compose.yml   Postgres + 4 servicios + Traefik
├── docs/
│   └── openapi/             Contratos exportados (JSON)
└── README.md
```

## Cuentas semilla (solo desarrollo)

La contraseña es el valor de `DEMO_PASSWORD` definido en tu `.env` (no incluido en el repositorio).

| Correo | Rol |
|--------|-----|
| `admin@albaconecta.cl` | ADMIN_CONTENIDOS |
| `gestor@albaconecta.cl` | GESTOR |
| `empresa@demo.cl` | EMPRESA |
| `colaborador@demo.cl` | COLABORADOR |

## Endpoints clave (vía Traefik :8080)

| Servicio | Health live | Swagger | Notas |
|----------|-------------|---------|-------|
| Contenidos | `/api/v1/contenidos/health/live` | `/api/v1/contenidos/docs` | Programas, indicadores, historias, sync |
| Vinculación | `/api/v1/vinculacion/health/live` | `/api/v1/vinculacion/docs` | Solicitudes, idempotencia y estados |
| Identidad | `/api/v1/identidad/health/live` | `/api/v1/identidad/docs` | Login, refresh, JWKS, registro |
| Talento | `/api/v1/talento/health/live` | `/api/v1/talento/docs` | Candidatos, apoyos, compatibilidad |

JWKS público: `GET /api/v1/identidad/.well-known/jwks.json`.

## Secretos y archivos excluidos

Nunca se suben: `.env`, `backend/secrets/*.pem`, `local.properties`, `*.db`, APK/AAB y directorios `build/`, `.gradle/`, `.venv/`, `.idea/`. Todos están en `.gitignore`.

La base SQLite de desarrollo (`backend/albaconecta.db`) estuvo versionada en commits anteriores y ahora se dejó de versionar; el archivo local se conserva. Si el repositorio remoto ya la publicó, purga el historial o rota las credenciales demo antes de hacerlo público.

## Referencias

- OpenAPI exportado: `docs/openapi/{contenidos,vinculacion,identidad,talento}.json`.
- Detalle del backend: `backend/README.md`.
