# Docker

Docker ejecuta HCOP JP en cinco contenedores coordinados: `database`
(PostgreSQL), `redis` (caché de sesión), `backend` (dominio clínico, JWT),
`bff` (Token Handler de la sesión) y `frontend` (Angular + nginx, único
puerto publicado). No necesita instalar Java, Node.js ni PostgreSQL en
Windows.

## Componentes del despliegue

- **imagen**: programa empaquetado — hay una por servicio (`backend`,
  `bff`, `frontend`), más las de terceros (`postgres`, `redis`);
- **contenedor**: instancia que está ejecutándose;
- **volumen**: disco persistente — sólo `database` y `backend` (storage de
  archivos clínicos) lo tienen; `redis`, `bff` y `frontend` son stateless;
- **compose**: archivo que inicia todo junto.

## Ejecución directa desde GitHub

Cuando Docker Desktop ya está instalado, copie y pegue esta línea completa en
Windows PowerShell:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest -UseBasicParsing "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript
```

El lanzador mantiene `compose.yaml`, `.env` y los registros operativos en
`%LOCALAPPDATA%\HCOP_JP-Docker`. Conserva la base y los documentos entre
reinicios y actualizaciones.

En una base nueva también crea, por defecto, un paciente de ejemplo totalmente
sintético con un caso compuesto de colon y melanoma.
`HCOP_SEED_EXAMPLE_PATIENT=true` lo habilita y
`HCOP_SEED_EXAMPLE_PATIENT=false` lo desactiva. El arranque es idempotente: no
duplica la ficha ni la selecciona como paciente activo. Una versión nueva del
recurso sólo actualiza la hoja si conserva la revisión administrada; cualquier
edición humana la deja fuera de futuras actualizaciones automáticas. Cambiar a
`false` no borra una ficha ya creada. Consulte los detalles y ubicaciones de
`.env` en
[Instalación desde GitHub](../00-inicio/INSTALACION-DESDE-GITHUB.md#paciente-de-ejemplo-en-una-instalación-nueva).

El seed nunca bloquea el contenedor: una colisión de identidad, falta de actor
de auditoría o conflicto concurrente no resuelto registra una advertencia y
continúa sin crear o modificar el demo. La invalidez del recurso empaquetado es
un defecto que deben detectar las pruebas de release.

## Canal aislado de migración (histórico)

> La migración Angular ya terminó y es la versión estable en `main` — este
> canal no hace falta para probarla. Se conserva porque sigue funcionando en
> el lanzador para quien necesite ese punto exacto de la migración.

La rama `codex/angular-full-parity-v2` se prueba sin reemplazar la versión
estable. Copie esta línea completa:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest -UseBasicParsing "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/codex/angular-full-parity-v2/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration
```

El canal usa:

- imagen `ghcr.io/marcolyto/hcop_jp:angular-full-parity-v2`;
- puerto elegido en el primer inicio; se sugiere 5181;
- proyecto Compose `hcop-ahjp`;
- base `hcop_ahjp`;
- volúmenes `hcop_ahjp_postgres` y `hcop_ahjp_storage`;
- carpeta `%LOCALAPPDATA%\HCOP_AHJP-Docker`.

El primer inicio solicita puerto, usuario administrador y contraseña. La
aplicación Angular y Swagger quedan en `http://localhost:<puerto-elegido>/` y
`http://localhost:<puerto-elegido>/swagger-ui.html`. La versión estable y el
canal migratorio anterior conservan sus propias carpetas, bases y volúmenes; sus
datos no se comparten. Consulte la
[guía de prueba de la rama](../00-inicio/PRUEBA-RAMA-ANGULAR-HEXAGONAL.md).

## Comandos desde un checkout del repositorio

Los comandos siguientes se ejecutan únicamente dentro de una copia local de
`HCOP_JP`, donde existen `compose.yaml` y `.env`. No sustituyen la línea de
ejecución directa anterior.

Atajo con diagnóstico incluido: `.\iniciar.bat` (Windows) o `./iniciar.sh`
(macOS/Linux) en la raíz del repo — cada uno acepta `detener`/`reiniciar`
como argumento. Ver [Actualización](ACTUALIZACION.md#actualización-desde-un-checkout-de-desarrollo).

Iniciar:

```powershell
docker compose up --detach --wait
```

Ver estado:

```powershell
docker compose ps
```

Ver logs (elija el servicio: `database`, `redis`, `backend`, `bff` o
`frontend`):

```powershell
docker compose logs --follow backend
```

Detener conservando datos:

```powershell
docker compose down
```

No use `docker compose down --volumes` en una instalación con pacientes: esa
opción elimina la base.

## Archivos del proyecto

- `backend/Dockerfile`, `bff/Dockerfile`, `frontend/Dockerfile`: construyen
  cada servicio;
- `compose.yaml`: desarrollo/construcción local (build desde código);
- `compose.github.yaml`: usa las imágenes publicadas en GHCR;
- `compose.e2e.yaml`, `compose.dev.yaml`, `compose.validation.yaml`:
  variantes para CI/E2E, debug local y validación de scripts;
- `.env`: secretos locales, nunca se sube a GitHub.

La interfaz Angular es un servicio propio (`frontend/`), servida por nginx —
no vive dentro del `.jar` de Java ni comparte proceso con `backend`. nginx
enruta `/api/` y el resto de la API hacia `bff`, no hacia `backend` directo.
La raíz y los aliases operativos ingresan a Angular; no se instala un segundo
frontend, no hay iframe y no se ejecuta runtime JavaScript legacy. Tampoco es
necesario conservar una copia de `HCOP_lira`.
