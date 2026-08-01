# Probar la rama Angular y hexagonal

Esta guÃ­a inicia el canal migratorio directamente desde GitHub, sin clonar el
repositorio y sin reemplazar la instalaciÃ³n estable.

## Inicio en un comando

Requisitos: Windows 10/11, Docker Desktop iniciado y acceso a Internet. Copie
la lÃ­nea completa en Windows PowerShell:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Start -HostPort 5181
```

En el primer inicio se solicita el usuario administrador y una contraseÃ±a de
al menos 10 caracteres. El sistema genera el resto de los secretos y los
conserva localmente.

Cuando los contenedores estÃ©n saludables:

- aplicaciÃ³n: <http://localhost:5181>;
- Swagger: <http://localhost:5181/swagger-ui.html>;
- salud: <http://localhost:5181/actuator/health>.

## Aislamiento respecto de la versiÃ³n estable

| Recurso | Estable | MigraciÃ³n |
|---|---|---|
| Rama | `main` | `codex/angular-hexagonal-migration` |
| Imagen | `ghcr.io/marcolyto/hcop_jp:latest` | `ghcr.io/marcolyto/hcop_jp:angular-hexagonal-migration` |
| Puerto | 5180 | 5181 |
| Proyecto Compose | `hcop-jp` | `hcop-ajp` |
| Base | `hcop_jp` | `hcop_ajp` |
| Volumen PostgreSQL | `hcop_jp_postgres` | `hcop_ajp_postgres` |
| Volumen de archivos | `hcop_jp_storage` | `hcop_ajp_storage` |
| Carpeta del lanzador | `%LOCALAPPDATA%\HCOP_JP-Docker` | `%LOCALAPPDATA%\HCOP_AJP-Docker` |

La rama migratoria no lee ni copia pacientes de la versiÃ³n estable. Es un
entorno separado para verificar la evoluciÃ³n tÃ©cnica y funcional.

## Operaciones posteriores

El archivo descargado en `%TEMP%` acepta estos modos:

```powershell
# Iniciar
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Start

# Descargar y aplicar la versiÃ³n mÃ¡s reciente de la rama
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Update

# Consultar contenedores y salud

# Detener sin borrar la base ni los archivos
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Stop
```

No use `docker compose down --volumes`: eliminarÃ­a los datos del canal elegido.

## Alcance de este corte

El corte conserva la interfaz clÃ­nica vigente y el flujo completo de HCOP JP.
El backend de ConfiguraciÃ³n, Protocolos y GuÃ­as ya estÃ¡ delimitado por puertos
hexagonales y se valida contra PostgreSQL real. Angular todavÃ­a no reemplaza la
interfaz; su incorporaciÃ³n serÃ¡ progresiva y sÃ³lo avanzarÃ¡ cuando cada
recorrido alcance paridad funcional, visual, de permisos y de errores.

La matriz vigente se encuentra en
[Matriz de paridad](../09-migracion-angular-hexagonal/MATRIZ-DE-PARIDAD.md).

