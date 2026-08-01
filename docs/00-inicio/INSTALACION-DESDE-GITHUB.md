# Instalación desde GitHub

## Ejecución directa con Docker Desktop

Este procedimiento se utiliza cuando Docker Desktop ya está instalado, abierto
y actualizado. Requiere Docker Compose 2.20 o posterior; no requiere Git, Java,
Maven ni una copia local del repositorio.

### Línea de ejecución

Copie y pegue la línea completa en Windows PowerShell. El comando guarda el
script en `%TEMP%` y después lo ejecuta como archivo; no canaliza código remoto
directamente al intérprete.

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript
```

Si ese comando devuelve 404, copie y pegue estas tres lineas (misma direccion directa):

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"
Invoke-WebRequest "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript -UseBasicParsing
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Start -HostPort 5181
```

El repositorio y la imagen `ghcr.io/marcolyto/hcop_jp:latest` son públicos. La
descarga no requiere iniciar sesión en GitHub ni proporcionar tokens.

El ejecutor:

- usa `ghcr.io/marcolyto/hcop_jp:latest` y `postgres:18.4-alpine`;
- guarda `compose.yaml`, `.env` y registros en
  `%LOCALAPPDATA%\HCOP_JP-Docker`;
- solicita y confirma la contraseña inicial sin mostrarla; exige al menos 10
  caracteres;
- en el primer inicio permite elegir el puerto web; al presionar Enter conserva
  el sugerido (`5180` para estable y `5181` para la rama de migración);
- genera los secretos internos una sola vez y nunca los rota al reiniciar o
  actualizar;
- detecta una contraseña inicial antigua de menos de 10 caracteres y permite
  reemplazar únicamente ese valor, sin modificar la base ni los demás secretos;
- restringe `.env` al usuario actual, SYSTEM y Administradores cuando Windows
  lo permite, y muestra una advertencia no fatal si no puede ajustar la ACL;
- en el inicio usa las imágenes locales y sólo descarga si todavía falta alguna;
- en la actualización descarga explícitamente `latest`;
- espera la salud de PostgreSQL y HCOP JP;
- abre la dirección elegida, por ejemplo <http://localhost:5180>, únicamente
  cuando la aplicación está lista;
- conserva pacientes y documentos en los volúmenes `hcop_jp_postgres` y
  `hcop_jp_storage`.

Respalde `.env`, `hcop_jp_postgres` y `hcop_jp_storage` como una sola unidad. Si
la base ya existe pero falta `.env`, el ejecutor se detiene en vez de generar
otra contraseña que dejaría PostgreSQL inaccesible.

Modos:

```powershell
# Iniciar con las imágenes locales; descarga sólo si faltan (predeterminado)
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Start

# Descargar latest e incorporarla conservando los datos
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Update

# Detener sin eliminar base ni archivos
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Stop
```

Para una instalación automatizada o para elegirlo sin esperar la pregunta,
indique el puerto una sola vez en el primer inicio:

```powershell
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -HostPort 5190
```

La elección queda guardada en `%LOCALAPPDATA%\HCOP_JP-Docker\.env`. No se
modifica en actualizaciones ni reinicios.

El archivo `EJECUTAR-DOCKER-DESDE-GITHUB.bat` aporta doble clic cuando se
descarga junto al `.ps1`; sin argumentos inicia HCOP JP y también acepta
`Update`, `Status` o `Stop`.

## Instalación administrada

1. Abra el repositorio público `Marcolyto/HCOP_JP`.
2. Descargue `INSTALAR-DESDE-GITHUB.bat`.
3. Haga doble clic sobre el archivo descargado.
4. Acepte la autorización de administrador únicamente si Windows necesita
   habilitar WSL 2.
5. Si el instalador indica **Reinicio pendiente**, reinicie Windows y vuelva a
   ejecutar el mismo archivo. La instalación continúa sin perder lo realizado.
6. Ingrese usuario, contraseña y puerto. Puede presionar Enter para usar los
   valores sugeridos.
7. Espere la validación de salud, interfaz y estado funcional. El navegador se
   abre solamente después de que las tres comprobaciones terminan bien.

La pregunta **Puerto web HTTP [5180]** aparece durante la instalación inicial.
El instalador valida que el valor esté entre 1 y 65535 y que no esté ocupado;
si elige un puerto en uso, vuelve a solicitarlo. También puede indicarlo sin
interacción al ejecutar el `.bat` desde una consola:

```powershell
.\INSTALAR-DESDE-GITHUB.bat 5190
```

El valor queda guardado como `HCOP_PORT` en `%LOCALAPPDATA%\HCOP_JP\.env` y se
mantiene durante las actualizaciones, reparaciones y reinicios.

El programa se instala por defecto en:

```text
%LOCALAPPDATA%\HCOP_JP
```

## Qué prepara el instalador

El asistente realiza un preflight real:

- comprueba que WSL sea 2.1.5 o posterior;
- habilita o actualiza WSL 2 cuando es viable;
- deja `REINICIO-PENDIENTE.txt` si Windows necesita reiniciarse;
- localiza Docker tanto en `PATH` como en las instalaciones por usuario o para
  todos los usuarios;
- instala e inicia Docker Desktop si falta;
- comprueba Docker Engine y Docker Compose 2.20 o posterior;
- verifica que el puerto elegido no pertenezca a otro programa;
- valida el acceso al repositorio y al paquete Docker publicado;
- crea `.env` con valores entre comillas literales y secretos aleatorios;
- descarga exactamente el commit actual de `main`;
- prefiere la imagen `ghcr.io/marcolyto/hcop_jp:sha-<commit>`;
- si esa imagen exacta no existe, construye el mismo código descargado en lugar
  de usar una imagen `latest` potencialmente antigua;
- inicia PostgreSQL y aplica Flyway;
- comprueba salud, página principal y estado funcional;
- promueve la nueva versión como estable únicamente después de esas pruebas.

El repositorio y la imagen publicados son públicos. La instalación estándar no
requiere GitHub CLI, autorización por navegador ni tokens.

## Accesos directos

La instalación crea accesos separados:

- **HCOP JP** o **Iniciar HCOP JP**: arranca la versión estable local y funciona
  sin volver a descargar el proyecto;
- **Actualizar HCOP JP**: busca una versión nueva, la prueba y recién entonces
  la convierte en estable;
- **Reparar HCOP JP**: repara WSL/Docker, intenta la versión estable, recupera
  la anterior si es necesario y descarga nuevamente sólo como último recurso;
- **Detener HCOP JP**: apaga los contenedores sin borrar datos.

El inicio diario no depende de Internet. Para actualizar sí se necesita acceso a
GitHub y a los registros de contenedores.

## Versiones, recuperación y datos

El instalador conserva:

- `current.txt`: versión estable verificada;
- `previous.txt`: versión anterior disponible para recuperación;
- `versions\...`: código de ambas versiones;
- `.env`: configuración y secretos del equipo;
- `logs\...`: registro completo de cada instalación, inicio o reparación.

`current.txt` no cambia si la versión candidata falla. El instalador vuelve a
levantar la versión anterior y muestra `docker compose ps` y los últimos logs.
Después de una actualización correcta elimina versiones temporales antiguas y
conserva sólo la estable y la anterior.

Los datos clínicos no viven dentro de una versión:

- `hcop_jp_postgres`: pacientes, tratamientos, turnos y configuración;
- `hcop_jp_storage`: estudios, imágenes y documentos.

Actualizar o ejecutar **Detener HCOP JP** no borra esos volúmenes. Nunca use
`docker compose down --volumes` en una instalación con información clínica.

## Preflight sin instalar

El diagnóstico no modifica el equipo, no descarga archivos y termina aunque
Docker todavía no exista:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\instalar-desde-github.ps1 `
  -Mode Preflight `
  -InstallDir "$env:LOCALAPPDATA\HCOP_JP"
```

La validación estática tampoco requiere Docker ni conexión:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\instalar-desde-github.ps1 `
  -Mode ValidateOnly
```

## Diagnóstico y recuperación

1. Revise el mensaje final.
2. Abra el archivo indicado después de `Detalle:`. La ejecución directa guarda
   registros en `%LOCALAPPDATA%\HCOP_JP-Docker\logs`; la instalación administrada
   los guarda en `%LOCALAPPDATA%\HCOP_JP\logs`.
3. Si aparece `REINICIO-PENDIENTE.txt`, reinicie Windows.
4. Si el puerto está ocupado, cambie `HCOP_PORT` en `.env`.
5. Vuelva a ejecutar la misma línea de PowerShell. El lanzador reutiliza las
   imágenes descargadas, conserva los volúmenes y repara configuraciones
   iniciales incompatibles.
6. En una instalación administrada, ejecute **Reparar HCOP JP**.

No ejecute `docker compose up` desde una carpeta que no contenga un archivo
Compose. Para una instalación directa desde GitHub, utilice siempre la línea de
ejecución indicada al comienzo de este documento.

Para verificar manualmente después de iniciar:

- `http://localhost:5180/actuator/health`: debe mostrar `UP`;
- `http://localhost:5180`: aplicación;
- `http://localhost:5180/swagger-ui.html`: API documentada.

Si eligió otro puerto, reemplácelo en esas direcciones.

## Probar el canal Angular y hexagonal

La rama migratoria tiene un canal Docker propio para poder evaluarla sin
actualizar la instalación estable:

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"
Invoke-WebRequest "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript -UseBasicParsing
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration -Mode Start -HostPort 5181
```

Se abre en <http://localhost:5181> y usa base, archivos e imagen independientes.
La explicación completa está en
[Probar la rama Angular y hexagonal](PRUEBA-RAMA-ANGULAR-HEXAGONAL.md).
