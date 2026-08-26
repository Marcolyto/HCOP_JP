# 08 · Preparar Docker, CI y despliegue

## Tres Dockerfiles multi-stage

`backend/Dockerfile` y `bff/Dockerfile` (mismo patrón):

Etapa de build:

- imagen Maven/JDK fijada;
- copia primero `pom.xml`;
- cache de dependencias;
- copia `src`;
- corre `mvn test` + `package` (sin socket de Docker disponible dentro del
  build — Testcontainers no puede correr acá, sólo tests que mockean).

Etapa runtime:

- JRE 21, no JDK/Maven;
- usuario no root;
- sólo el JAR (y catálogos, en el caso del backend);
- directorio storage con propietario correcto (sólo backend);
- healthcheck contra `/actuator/health`;
- límites de memoria;
- entrada `java -jar`.

`frontend/Dockerfile` es distinto: etapa de build con Node (`npm ci && npm
run build`), etapa runtime `nginx:alpine` sirviendo el `dist/` compilado más
`nginx.conf` (upstream fijo `bff:8080`, no configurable por entorno — si
falta el servicio `bff` en el compose, nginx no arranca: `emerg host not
found in upstream "bff"`, un error real que aparece si se lo olvida).

No copie `.git`, `.env`, dumps, pacientes, storage local, `target/`,
`node_modules/` ni `dist/` del host. Mantenga `.dockerignore` en cada uno de
los tres servicios.

## Docker Compose

Cinco servicios:

- `database`: PostgreSQL 18.4, red interna, volumen persistente y healthcheck;
- `redis`: caché de sesión del BFF — efímero a propósito, sin
  `--appendonly` ni volumen (perderlo obliga a un re-login, no pierde datos
  clínicos);
- `backend`: depende de `database` saludable;
- `bff`: depende de `backend` y `redis` saludables;
- `frontend`: depende de `bff` saludable, único que publica puerto al host
  (`5180:8080`).

Volúmenes separados:

```text
hcop_jp_postgres
hcop_jp_storage
```

Sólo `backend` y `database` tienen volumen — `bff` y `frontend` son
stateless. No monte el código fuente en producción. No publique el puerto
5432 ni 6379 salvo una necesidad administrativa temporal y restringida.

## Configuración

Use `.env` fuera de Git:

```dotenv
HCOP_PORT=5180
HCOP_DB_NAME=hcop_jp
HCOP_DB_USER=hcop
HCOP_DB_PASSWORD=<secreto>
HCOP_BOOTSTRAP_USERNAME=<administrador>
HCOP_BOOTSTRAP_PASSWORD=<secreto-inicial>
HCOP_QR_SECRET=<aleatorio>
HCOP_ENCRYPTION_SECRET=<aleatorio-distinto>
HCOP_JWT_SECRET=<aleatorio-de-al-menos-32-bytes>
HCOP_PUBLIC_BASE_URL=https://hcop.example
```

En producción, los placeholders débiles deben causar una advertencia o rechazo.

## Health y readiness

El contenedor se considera listo cuando:

- Java inició;
- datasource responde;
- Flyway terminó;
- `/actuator/health` devuelve `UP`.

Compose espera esa condición antes de pruebas o exposición.

## GitHub Actions

Un solo workflow (`verify.yml`) con jobs independientes que corren en
paralelo, más un job final de publicación que depende de todos:

- `java`: `mvn verify` del backend;
- `bff`: `mvn verify` del bff (pom propio, no se sube al `verify` del
  backend);
- `frontend`: `npm ci && npm test && npm run build`;
- `docker`: `docker compose up --build --wait` con los 5 servicios reales,
  smoke test, snapshot OpenAPI (`-Check`, bloqueante — es el guardián real
  del contrato, no `generate-api-docs.ps1 -Check`, que es una proyección
  *lossy* sin schemas), flujo clínico integral, backup/restore;
- `browser`: Playwright contra el stack Docker real;
- `launcher`: valida el script de instalación en Windows;
- `publish` (`needs: [java, bff, frontend, docker, browser, launcher]`):
  matriz con una entrada por imagen (`backend`, `bff`, `frontend`) —
  **las tres**, no sólo dos; un servicio nuevo que se olvida acá se
  construye y prueba en CI pero nunca se publica, y la instalación desde
  GHCR queda rota en silencio hasta que alguien la prueba de verdad.

Para cada imagen de la matriz: login a GHCR con `GITHUB_TOKEN`, metadata/tag,
cache BuildKit, push sólo desde ramas/tags autorizados, digest visible.

`publish` depende de todos los demás jobs — no hay forma de publicar una
imagen sin que la verificación completa haya pasado primero.

## Versionado

- versión de aplicación en Maven;
- tag Git para releases;
- etiqueta OCI;
- imagen `:version`;
- `:latest` sólo como alias cómodo;
- digest para despliegues reproducibles.

## Actualización segura

1. backup de base y storage;
2. descargar imagen por versión;
3. revisar migraciones;
4. iniciar;
5. esperar health;
6. ejecutar smoke test;
7. conservar imagen anterior;
8. rollback de aplicación sólo si el esquema sigue siendo compatible.

No “retroceda” una base aplicando SQL inverso improvisado. Diseñe migraciones
compatibles por etapas para cambios grandes.

## Observabilidad

Mínimo:

- health/readiness;
- métricas JVM, pool y HTTP;
- logs estructurados con request ID;
- alertas por errores, pool agotado y storage;
- rotación/retención;
- sin PHI ni secretos.

## Hito de aceptación

Una máquina sin Java/Maven, pero con Docker, debe poder iniciar:

```powershell
docker compose up --build --detach --wait
```

Los datos deben sobrevivir a `docker compose down` y actualización de imagen.
Sólo `down --volumes` elimina el entorno de prueba.
