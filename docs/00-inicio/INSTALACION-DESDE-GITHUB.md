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
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest -UseBasicParsing "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript
```

El repositorio y la imagen `ghcr.io/marcolyto/hcop_jp:latest` son públicos. La
descarga no requiere iniciar sesión en GitHub ni proporcionar tokens.

El ejecutor:

- usa `ghcr.io/marcolyto/hcop_jp:latest` y `postgres:18.4-alpine`;
- guarda `compose.yaml`, `.env` y registros en
  `%LOCALAPPDATA%\HCOP_JP-Docker`;
- solicita puerto, usuario administrador y contraseña inicial; confirma la
  contraseña sin mostrarla y exige al menos 10 caracteres;
- genera los secretos internos una sola vez y nunca los rota al reiniciar o
  actualizar;
- detecta una contraseña inicial antigua de menos de 10 caracteres y permite
  reemplazar únicamente ese valor, sin modificar la base ni los demás secretos;
- restringe `.env` al usuario actual, SYSTEM y Administradores cuando Windows
  lo permite, y muestra una advertencia no fatal si no puede ajustar la ACL;
- en el inicio usa las imágenes locales y sólo descarga si todavía falta alguna;
- en la actualización descarga explícitamente `latest`;
- espera la salud de PostgreSQL y HCOP JP;
- abre `http://localhost:<puerto-elegido>/` únicamente cuando la aplicación está
  lista;
- conserva pacientes y documentos en los volúmenes `hcop_jp_postgres` y
  `hcop_jp_storage`.

La raíz es la entrada recomendada y siempre entrega Angular. `/index.html` y
los aliases `/configuration`, `/protocol-admin` y `/herramientas` también
conducen al frontend Angular nativo. `/app/` es su ubicación interna canónica.
Ninguna de esas entradas usa iframe ni ejecuta el runtime JavaScript anterior.
Swagger, OpenAPI, Actuator y `/docs/` conservan rutas independientes.

Respalde `.env`, `hcop_jp_postgres` y `hcop_jp_storage` como una sola unidad. Si
la base ya existe pero falta `.env`, el ejecutor se detiene en vez de generar
otra contraseña que dejaría PostgreSQL inaccesible.

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

## Instalación administrada

1. Abra el repositorio público `Marcolyto/HCOP_JP`.
2. Descargue `INSTALAR-DESDE-GITHUB.bat`.
3. Haga doble clic sobre el archivo descargado.
4. Acepte la autorización de administrador únicamente si Windows necesita
   habilitar WSL 2.
5. Si el instalador indica **Reinicio pendiente**, reinicie Windows y vuelva a
   ejecutar el mismo archivo. La instalación continúa sin perder lo realizado.
6. Ingrese puerto, usuario y contraseña. Puede presionar Enter para usar los
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

## Paciente de ejemplo en una instalación nueva

HCOP JP incorpora por defecto una ficha demostrativa completamente sintética
para poder recorrer la hoja clínica apenas termina la instalación. La controla:

```dotenv
HCOP_SEED_EXAMPLE_PATIENT=true
```

El valor predeterminado es `true` tanto en Docker Compose como en los
instaladores. La ficha se reconoce por el nombre
`Test Savatierra, Tomas Alejandro`, DNI `99000002` y un caso compuesto ficticio
de cáncer de colon y melanoma. El recurso distribuido **jamás contiene datos de
una persona real** y no debe reemplazarse por una historia clínica verdadera.
No deriva de una historia real anonimizada o pseudonimizada: identidad,
cronología, hallazgos y recorrido clínico fueron creados desde cero.

El arranque crea como máximo una ficha con la clave interna
`hcop-default-test-savatierra-v1`. Reiniciar, reparar o actualizar no la duplica.
La hoja distribuida usa `meta.demoContentVersion=3` y
`meta.demoManagedRevision`: la misma versión es un no-op, y una versión más
nueva del recurso sólo refresca una hoja demostrativa que permanezca intacta,
con su revisión actual igual a la última revisión administrada por el seed. Si
alguien editó la ficha, la revisión deja de coincidir y el arranque **nunca pisa
esa edición humana**.

El seed es best-effort y nunca impide iniciar HCOP JP. Una colisión del DNI o de
la HC reservados, o la ausencia de un usuario habilitado para auditar la carga,
produce una advertencia y omite el ejemplo. Ante una carrera de actualización
optimista relee y acepta la versión ganadora; si no puede confirmarla, advierte
y no modifica nada. Un recurso empaquetado inválido se considera un defecto de
release que debe impedir la publicación del artefacto, no un dato que el
operador deba reparar.

El ejemplo tampoco se convierte en paciente activo: después de iniciar sesión
la hoja permanece sin paciente hasta que el usuario abra una ficha de forma
explícita.

Para no crear el ejemplo, establezca:

```dotenv
HCOP_SEED_EXAMPLE_PATIENT=false
```

En una copia local se modifica el `.env` situado junto a `compose.yaml`. En la
ejecución directa desde GitHub está en
`%LOCALAPPDATA%\HCOP_JP-Docker\.env`; en la instalación administrada está en
`%LOCALAPPDATA%\HCOP_JP\.env`. Detenga la aplicación, cambie el valor y vuelva a
iniciarla. Para excluir la ficha desde una base vacía, configure `false` antes
del primer arranque de la aplicación. El valor `false` evita creaciones o
reparaciones o actualizaciones administradas futuras, pero no elimina una ficha
demostrativa que ya exista.

## Accesos directos

La instalación crea accesos separados:

- **HCOP JP** o **Iniciar HCOP JP**: arranca la versión estable local y funciona
  sin volver a descargar el proyecto;
- **Actualizar HCOP JP**: busca una versión nueva, la prueba y recién entonces
  la convierte en estable;
- **Reparar HCOP JP**: repara WSL/Docker, intenta la versión estable, recupera
  la anterior si es necesario y descarga nuevamente sólo como último recurso;
- **Respaldar HCOP JP**: crea una copia verificada de PostgreSQL y de los
  archivos clínicos, sin incluir ni mostrar `.env`;
- **Restaurar HCOP JP**: solicita la carpeta del backup y la palabra explícita
  `RESTAURAR`, crea primero un backup de seguridad y recién entonces reemplaza
  los datos;
- **Detener HCOP JP**: apaga los contenedores sin borrar datos.

El inicio diario no depende de Internet. Para actualizar sí se necesita acceso a
GitHub y a los registros de contenedores. Backup y restauración usan la versión
estable que ya está instalada y tampoco descargan código.

## Versiones, recuperación y datos

El instalador conserva:

- `current.txt`: versión estable verificada;
- `previous.txt`: versión anterior disponible para recuperación;
- `versions\...`: código de ambas versiones;
- `.env`: configuración y secretos del equipo;
- `backups\...`: copias verificadas creadas por **Respaldar HCOP JP** y copias
  de seguridad previas a una restauración;
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
$hcopScript = Join-Path $env:TEMP "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"; Invoke-WebRequest -UseBasicParsing "https://raw.githubusercontent.com/Marcolyto/HCOP_JP/codex/angular-full-parity-v2/EJECUTAR-DOCKER-DESDE-GITHUB.ps1" -OutFile $hcopScript; powershell.exe -NoProfile -ExecutionPolicy Bypass -File $hcopScript -Channel Migration
```

El primer inicio solicita puerto, usuario y contraseña. Después se abre
`http://localhost:<puerto-elegido>/` y se usan base, archivos e imagen
independientes. La explicación completa está en
[Probar la rama Angular y hexagonal](PRUEBA-RAMA-ANGULAR-HEXAGONAL.md).
