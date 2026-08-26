# Entorno local

## Con Docker (recomendado)

Levanta los 5 servicios (`database`, `redis`, `backend`, `bff`, `frontend`)
buildeando desde el código del repositorio:

```powershell
Copy-Item .env.example .env
docker compose up --build --wait
docker compose logs --follow backend    # o bff / frontend / database / redis
```

Parar sin perder datos: `docker compose down` (nunca `--volumes` con
pacientes reales — borra la base).

## Sin Docker

Cada servicio corre por separado. Necesita PostgreSQL y Redis instalados o
accesibles.

### `backend/`

```text
HCOP_DB_URL=jdbc:postgresql://127.0.0.1:5432/hcop_jp
HCOP_DB_USER=hcop
HCOP_DB_PASSWORD=...
HCOP_JWT_SECRET=<al menos 32 bytes>
HCOP_RUNTIME_ROOT=./runtime
```

```powershell
mvn -f backend/pom.xml spring-boot:run
```

Flyway aplica las 14 migraciones automáticamente.

### `bff/`

```text
BACKEND_URL=http://127.0.0.1:5180
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
```

```powershell
mvn -f bff/pom.xml spring-boot:run
```

Necesita `backend` y Redis ya corriendo.

### `frontend/`

```powershell
cd frontend
npm ci
npm start
```

`ng serve` no trae un proxy configurado hacia el `bff` — la app llama rutas
relativas (`/api/...`) porque en Docker nginx las resuelve. Para iterar
sobre Angular con datos reales, agregue un `proxy.conf.json` propio
(`"/api": { "target": "http://localhost:5180" }`, apuntando al `bff`
levantado con Docker) o, más simple, levante todo con Docker
(`docker compose up --build --wait`) y edite sobre el bundle compilado con
`npm run build --watch` si sólo necesita iterar CSS/markup sin recompilar
Docker en cada cambio.

## Carpetas que no se versionan

- `.env`;
- `target/`, `.angular/`, `dist/`, `node_modules/`;
- `runtime/storage`;
- datos de PostgreSQL/Redis;
- logs y PID.

Los catálogos clínicos (`backend/runtime/catalogs/`) sí se versionan porque
forman parte de la versión funcional del producto.
