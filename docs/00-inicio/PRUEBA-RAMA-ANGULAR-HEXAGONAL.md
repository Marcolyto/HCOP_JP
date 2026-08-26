# Probar la rama Angular nativa

> **Histórico:** la migración a Angular nativo ya terminó y es la versión
> estable en `main` — no hace falta este canal aislado para probarla. Se
> conserva porque el canal `Migration` (imagen `angular-full-parity-v2`)
> sigue funcionando en `EJECUTAR-DOCKER-DESDE-GITHUB.ps1` para quien
> necesite ese punto exacto de la migración. Para instalar la versión
> estable use [Instalar desde GitHub](INSTALACION-DESDE-GITHUB.md).

Esta guía inicia el canal migratorio directamente desde GitHub, sin clonar el
repositorio y sin reemplazar la instalación estable.

## Inicio en un comando

Requisitos: Windows 10/11, Docker Desktop iniciado y acceso a Internet. Copie
la línea completa en Windows PowerShell:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest -UseBasicParsing "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/codex/angular-full-parity-v2/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration
```

En el primer inicio se solicita el puerto, el usuario administrador y una
contraseña de al menos 10 caracteres. El sistema genera el resto de los secretos
y los conserva localmente. Puede presionar Enter para aceptar los valores
sugeridos de puerto y usuario.

Cuando los contenedores estén saludables:

- aplicación Angular: `http://localhost:<puerto-elegido>/`;
- Swagger: `http://localhost:<puerto-elegido>/swagger-ui.html`;
- salud: `http://localhost:<puerto-elegido>/actuator/health`.

## Aislamiento respecto de la versión estable

| Recurso | Estable | Migración |
|---|---|---|
| Rama | `main` | `codex/angular-full-parity-v2` |
| Imagen | `ghcr.io/marcolyto/hcop_jp:latest` | `ghcr.io/marcolyto/hcop_jp:angular-full-parity-v2` |
| Puerto | 5180 | Elegido al iniciar; sugerido 5181 |
| Proyecto Compose | `hcop-jp` | `hcop-ahjp` |
| Base | `hcop_jp` | `hcop_ahjp` |
| Volumen PostgreSQL | `hcop_jp_postgres` | `hcop_ahjp_postgres` |
| Volumen de archivos | `hcop_jp_storage` | `hcop_ahjp_storage` |
| Carpeta del lanzador | `%LOCALAPPDATA%\HCOP_JP-Docker` | `%LOCALAPPDATA%\HCOP_AHJP-Docker` |

La rama v2 no lee ni copia pacientes de la versión estable ni del canal
migratorio anterior. Es un entorno nuevo y separado para verificar la evolución
técnica y funcional y solicitar sus propias credenciales y puerto.

## Operaciones posteriores

El archivo descargado en `%TEMP%` acepta estos modos:

```powershell
# Iniciar
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Start

# Descargar y aplicar la versión más reciente de la rama
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Update

# Consultar contenedores y salud
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Status

# Detener sin borrar la base ni los archivos
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Stop
```

No use `docker compose down --volumes`: eliminaría los datos del canal elegido.

## Alcance de este corte

Angular gobierna toda entrada operativa: `/`, `/index.html` y los aliases de
Configuración, Protocolos y Herramientas terminan en el mismo frontend. La
aplicación no usa iframe ni ejecuta `static/app.js`; los estilos y activos
visuales compartidos no trasladan reglas de negocio al navegador.

El backend es Java 21 con Spring MVC. Los módulos migrados separan dominio,
aplicación e infraestructura mediante puertos hexagonales, PostgreSQL conserva
la información operacional y Flyway instala o actualiza el esquema. La paridad
funcional, visual, de permisos y de errores se controla por recorridos
completos. Swagger continúa en `/swagger-ui.html`, OpenAPI en `/v3/api-docs` y
la salud en `/actuator/health`.

La matriz vigente se encuentra en
[Matriz de paridad](../09-migracion-angular-hexagonal/MATRIZ-DE-PARIDAD.md).
