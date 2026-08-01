# Docker

Docker ejecuta HCOP JP y PostgreSQL en dos contenedores coordinados. No necesita
instalar Java ni PostgreSQL en Windows.

## Componentes del despliegue

- **imagen**: programa empaquetado;
- **contenedor**: instancia que está ejecutándose;
- **volumen**: disco persistente;
- **compose**: archivo que inicia todo junto.

## Ejecución directa desde GitHub

Cuando Docker Desktop ya está instalado, copie y pegue esta línea completa en
Windows PowerShell:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript
```

Si esa línea devuelve 404, use este bloque alternativo:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"
Invoke-WebRequest "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript -UseBasicParsing
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Start -HostPort 5181
```

El lanzador mantiene `compose.yaml`, `.env` y los registros operativos en
`%LOCALAPPDATA%\HCOP_JP-Docker`. Conserva la base y los documentos entre
reinicios y actualizaciones.

## Canal aislado de migración

La rama `codex/angular-hexagonal-migration` se prueba sin reemplazar la versión
estable. Copie esta línea completa:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"
Invoke-WebRequest "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript -UseBasicParsing
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Start -HostPort 5181
```

El canal usa:

- imagen `ghcr.io/marcolyto/hcop_jp:angular-hexagonal-migration`;
- puerto 5181;
- proyecto Compose `hcop-ajp`;
- base `hcop_ajp`;
- volúmenes `hcop_ajp_postgres` y `hcop_ajp_storage`;
- carpeta `%LOCALAPPDATA%\HCOP_AJP-Docker`.

La versión estable continúa en 5180 y sus datos no se comparten. Consulte la
[guía de prueba de la rama](../00-inicio/PRUEBA-RAMA-ANGULAR-HEXAGONAL.md).

## Comandos desde un checkout del repositorio

Los comandos siguientes se ejecutan únicamente dentro de una copia local de
`HCOP_JP`, donde existen `compose.yaml` y `.env`. No sustituyen la línea de
ejecución directa anterior.

Iniciar:

```powershell
docker compose up --detach --wait
```

Ver estado:

```powershell
docker compose ps
```

Ver logs:

```powershell
docker compose logs --follow application
```

Detener conservando datos:

```powershell
docker compose down
```

No use `docker compose down --volumes` en una instalación con pacientes: esa
opción elimina la base.

## Archivos del proyecto

- `Dockerfile`: construye Java;
- `compose.yaml`: desarrollo/construcción local;
- `compose.github.yaml`: usa la imagen publicada;
- `.env`: secretos locales, nunca se sube a GitHub.

La interfaz visible también está dentro de esta misma aplicación: Spring Boot
sirve `src/main/resources/static` desde el `.jar`. No hay que instalar ni
levantar un segundo front ni conservar una copia de `HCOP_lira`.
