# Backend Alba Conecta — Fase 0 + identidad segura

Implementación Python del plan `docs/PLAN_BACKEND_FASTAPI.md`: esqueleto desplegable
(Fase 0) y contrato congelado con datos semilla en memoria (Fase 1). **Todavía sin base
de datos**: los modelos, migraciones, JWT RS256 y Redis llegan en las fases 2–4.

## Estructura

```text
backend/
├── albaconecta_backend/
│   ├── common/            # config, errores RFC 7807, request-id, rate limit, auth, schemas
│   ├── contenidos/        # :8001  /api/v1/contenidos
│   ├── vinculacion/       # :8002  /api/v1/vinculacion
│   ├── identidad/         # :8003  /api/v1/identidad
│   └── talento/           # :8004  /api/v1/talento
├── tests/                 # pruebas de contrato y de casos borde
└── pyproject.toml
```

Cada servicio expone `app` y se levanta con su propio uvicorn; comparten `common` para
no duplicar errores, seguridad ni paginación. `gateway.py` monta los cuatro routers en una
sola app (:8000) como el gateway Traefik del plan.

## Levantar

```bash
cd backend
python3 -m venv .venv
./.venv/bin/pip install "fastapi>=0.115" "uvicorn[standard]" httpx pytest "cryptography>=44" "pwdlib[argon2]>=0.2"

# Todo junto en http://localhost:8000 (lo que usa la app Android)
./.venv/bin/uvicorn albaconecta_backend.gateway:app --port 8000 --reload

./.venv/bin/uvicorn albaconecta_backend.contenidos.app:app  --port 8001 --reload
./.venv/bin/uvicorn albaconecta_backend.vinculacion.app:app --port 8002 --reload
./.venv/bin/uvicorn albaconecta_backend.identidad.app:app   --port 8003 --reload
./.venv/bin/uvicorn albaconecta_backend.talento.app:app     --port 8004 --reload
```

Swagger de cada servicio en `http://localhost:800X/docs`, salud en `/health/live` y `/health/ready`.
Pruebas: `./.venv/bin/python -m pytest`.

## Variables de entorno

| Variable | Defecto | Uso |
|---|---|---|
| `ENTORNO` | `dev` | entorno lógico (`dev` o `prod`) |
| `SECRETO` | *(vacío)* | Validación de arranque de compatibilidad. **Obligatorio y seguro en producción**; mínimo 32 caracteres. |
| `JWT_PRIVATE_KEY` | *(dev genera clave)* | Clave privada PEM para RS256. Obligatoria en producción; puede ser PEM o ruta a archivo. |
| `JWT_PUBLIC_KEY` | *(dev deriva clave)* | Clave pública PEM publicada en JWKS. Obligatoria en producción; puede ser PEM o ruta a archivo. |
| `JWT_KID` | `albaconecta-dev` | Identificador estable de la clave activa. Obligatorio con valor propio en producción. |
| `DEMO_PASSWORD` | *(vacío)* | **Obligatorio en desarrollo**. Contraseña para las cuentas demo. No usar en producción. |
| `ACCESS_TTL` | `900` | segundos de vida del access token |
| `REFRESH_TTL` | `2592000` | segundos de vida del refresh token |
| `CONTENIDOS_BASE_URL` | vacío | si está vacío, toda referencia a programa/producto queda `referencia_no_verificada=true` |
| `CORS_ORIGENES` | `*` | lista separada por comas |
| `SYNC_PAGINA` | `25` | elementos por página de `/sync` |
| `DATABASE_URL` | `sqlite:///./albaconecta.db` | URL de conexión SQLAlchemy |

## Cuentas semilla (solo en desarrollo)

En desarrollo (`ENTORNO=dev`), si la base de datos está vacía y `DEMO_PASSWORD` está configurado, se crean:

| Correo | Rol |
|---|---|
| `admin@albaconecta.cl` | ADMIN_CONTENIDOS |
| `gestor@albaconecta.cl` | GESTOR |
| `empresa@demo.cl` | EMPRESA |
| `colaborador@demo.cl` | COLABORADOR |

**En producción (`ENTORNO=prod`) no se crean cuentas automáticamente.** El primer administrador debe crearse mediante un procedimiento controlado (ver sección "Primer administrador en producción").

### Primer administrador en producción

1. Desplegar el backend con `ENTORNO=prod`, `SECRETO` (mín. 32 chars) y `DATABASE_URL` (PostgreSQL).
2. Ejecutar migraciones (Fase 2+).
3. Crear el primer usuario `ADMIN_CONTENIDOS` directamente en la base de datos o mediante script administrativo:
   ```sql
   INSERT INTO usuario (id, nombre, email, password_hash, tipo, rol, organizacion, activo, fecha_creacion)
   VALUES ('usr-1', 'Admin Inicial', 'admin@produccion.cl', '<hash_pbkdf2>', 'COLABORADOR', 'ADMIN_CONTENIDOS', NULL, true, NOW());
   ```
   El hash se genera con `pbkdf2_sha256$200000$<sal>$<clave>` (ver `identidad/data.py:hashear`).

## Decisiones provisionales que hay que conocer

- **Token**: firmado con RS256 (`sub`, `rol`, `exp`) y publicado en `/.well-known/jwks.json`.
  En desarrollo se genera una clave RSA temporal por proceso; producción exige claves PEM configuradas.
- **Persistencia**: identidad y solicitudes usan SQLAlchemy; refresh, usuarios, solicitudes,
  historial e idempotencia sobreviven reinicios sobre SQLite/PostgreSQL. Falta Alembic y separar las cuatro bases.
- **Hash de contraseña**: `pbkdf2_sha256` de la stdlib; Fase 3 pasa a argon2 (`pwdlib`).
- **Un rol por usuario** en vez de `usuario_rol` N:M.
- **`/sync`**: cursor opaco = desplazamiento codificado, no un sello de tiempo.

## Pendiente por fase

- Fase 2: SQLAlchemy async + Alembic, CRUD real y `/sync` sobre base de datos.
- Fase 3: JWT RS256, JWKS real, argon2, `usuario_rol`.
- Fase 4: tablas de vinculación, idempotencia persistente y llamada HTTP a contenidos.
- Fase 5: rate limit por IP+email en login, CORS por entorno, pruebas de contrato.
