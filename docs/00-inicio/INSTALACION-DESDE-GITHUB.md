# Instalación desde GitHub

## Sólo Docker Desktop, sin clonar el repositorio

Esta alternativa es la más corta cuando Docker Desktop ya está instalado,
abierto y actualizado. Requiere Docker Compose 2.20 o posterior, pero no
requiere Git, Java, Maven ni descargar el proyecto completo.

Descargue únicamente `EJECUTAR-DOCKER-DESDE-GITHUB.ps1` y ejecútelo. Esta línea
es deliberadamente segura: primero guarda el script en `%TEMP%` y después lo
ejecuta como archivo; no usa `irm | iex`.

```powershell
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript
```

Si el repositorio es privado, el enlace directo no puede descargar el archivo
sin autorización. En ese caso, inicie sesión en GitHub, abra el archivo
`EJECUTAR-DOCKER-DESDE-GITHUB.ps1`, use **Download raw file** y ejecútelo desde
Descargas.

El ejecutor:

- usa `ghcr.io/marcolyto/hcop_jp:latest` y `postgres:18.4-alpine`;
- guarda `compose.yaml`, `.env` y registros en
  `%LOCALAPPDATA%\HCOP_JP-Docker`;
- solicita la contraseña inicial sin mostrarla;
- genera los secretos internos una sola vez y nunca los rota al reiniciar o
  actualizar;
- restringe `.env` al usuario actual, SYSTEM y Administradores cuando Windows
  lo permite, y muestra una advertencia no fatal si no puede ajustar la ACL;
- en el inicio usa las imágenes locales y sólo descarga si todavía falta alguna;
- en la actualización descarga explícitamente `latest`;
- espera la salud de PostgreSQL y HCOP JP;
- abre <http://localhost:5180> únicamente cuando la aplicación está lista;
- conserva pacientes y documentos en los volúmenes `hcop_jp_postgres` y
  `hcop_jp_storage`.

Respalde `.env`, `hcop_jp_postgres` y `hcop_jp_storage` como una sola unidad. Si
la base ya existe pero falta `.env`, el ejecutor se detiene en vez de generar
otra contraseña que dejaría PostgreSQL inaccesible.

Si GHCR solicita autenticación y GitHub CLI está disponible, el ejecutor abre
la autorización y solicita `read:packages`. Si GitHub CLI no está instalado,
explica cómo usar un PAT clásico sin escribirlo en la línea de comandos.

Modos:

```powershell
# Iniciar con las imágenes locales; descarga sólo si faltan (predeterminado)
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Start

# Descargar latest e incorporarla conservando los datos
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Update

# Consultar contenedores y salud
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Status

# Detener sin eliminar base ni archivos
powershell.exe -File .\EJECUTAR-DOCKER-DESDE-GITHUB.ps1 -Mode Stop
```

El archivo `EJECUTAR-DOCKER-DESDE-GITHUB.bat` aporta doble clic cuando se
descarga junto al `.ps1`; sin argumentos inicia HCOP JP y también acepta
`Update`, `Status` o `Stop`.

## Opción recomendada: autoinstalador

1. Abra el repositorio `Marcolyto/HCOP_JP`.
2. Inicie sesión con una cuenta autorizada y descargue
   `INSTALAR-DESDE-GITHUB.bat`.
3. Haga doble clic sobre el archivo descargado.
4. Acepte la autorización de administrador únicamente si Windows necesita
   habilitar WSL 2.
5. Si el instalador indica **Reinicio pendiente**, reinicie Windows y vuelva a
   ejecutar el mismo archivo. La instalación continúa sin perder lo realizado.
6. Ingrese usuario, contraseña y puerto. Puede presionar Enter para usar los
   valores sugeridos.
7. Espere la validación de salud, interfaz y estado funcional. El navegador se
   abre solamente después de que las tres comprobaciones terminan bien.

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
- valida el acceso al repositorio y al paquete privado;
- crea `.env` con valores entre comillas literales y secretos aleatorios;
- descarga exactamente el commit actual de `main`;
- prefiere la imagen `ghcr.io/marcolyto/hcop_jp:sha-<commit>`;
- si esa imagen exacta no existe, construye el mismo código descargado en lugar
  de usar una imagen `latest` potencialmente antigua;
- inicia PostgreSQL y aplica Flyway;
- comprueba salud, página principal y estado funcional;
- promueve la nueva versión como estable únicamente después de esas pruebas.

El repositorio y la imagen pueden ser privados. En el primer equipo, el
asistente instala GitHub CLI si hace falta, abre el navegador para autorizar la
cuenta y solicita el alcance `read:packages`. No hay que copiar tokens.

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

## Si algo falla

1. Lea el mensaje final.
2. Abra el archivo indicado después de `Detalle:`. Los registros quedan en
   `%LOCALAPPDATA%\HCOP_JP\logs`.
3. Si aparece `REINICIO-PENDIENTE.txt`, reinicie Windows.
4. Si el puerto está ocupado, cambie `HCOP_PORT` en `.env`.
5. Si venció el acceso de GitHub, complete la ventana de autorización.
6. Ejecute **Reparar HCOP JP**.

Para verificar manualmente después de iniciar:

- `http://localhost:5180/actuator/health`: debe mostrar `UP`;
- `http://localhost:5180`: aplicación;
- `http://localhost:5180/swagger-ui.html`: API documentada.

Si eligió otro puerto, reemplácelo en esas direcciones.
