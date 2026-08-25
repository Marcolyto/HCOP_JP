# Backup y restauración

HCOP JP respalda como una sola unidad:

- la base PostgreSQL;
- el volumen de archivos clínicos, guías e imágenes;
- un manifiesto con versión, tamaños y SHA-256 de ambos archivos.

La aplicación se detiene durante la copia para que la base y los archivos
representen el mismo instante. PostgreSQL permanece activo y HCOP JP vuelve a
iniciarse y comprobar salud automáticamente.

## Crear un backup

### Instalación administrada desde GitHub

Haga doble clic en **Respaldar HCOP JP.bat**, dentro de:

```text
%LOCALAPPDATA%\HCOP_JP
```

El acceso usa exclusivamente la versión indicada por `current.txt`, valida sus
scripts y conserva la ventana abierta para mostrar la carpeta final. No lee ni
muestra contraseñas. El resultado queda, por defecto, en
`%LOCALAPPDATA%\HCOP_JP\backups`.

La misma operación puede ejecutarse desde PowerShell:

```powershell
$root = Join-Path $env:LOCALAPPDATA "HCOP_JP"
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File (Join-Path $root "instalar-desde-github.ps1") `
  -Mode Backup `
  -InstallDir $root
```

### Copia local del repositorio

Desde una copia local del repositorio:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup-hcop.ps1
```

Por defecto se crea una carpeta `backups\hcop-backup-AAAAMMDD-HHMMSS` con:

- `database.dump`;
- `storage.tar.gz`;
- `manifest.json`.

Puede elegir otro destino con `-OutputDirectory`. Guarde ese destino en un
disco distinto del equipo que ejecuta HCOP JP.

## Restaurar

La restauración reemplaza la base y los archivos actuales. Por eso exige la
confirmación explícita `-ConfirmRestore` y, antes de modificar nada, crea otro
backup dentro de `backups\pre-restore`.

En una instalación administrada, haga doble clic en **Restaurar HCOP JP.bat**,
pegue la ruta completa de la carpeta `hcop-backup-*` y escriba exactamente
`RESTAURAR`. Si deja la ruta vacía o no confirma esa palabra, termina sin
modificar datos. El acceso comprueba el manifiesto y sus SHA-256 antes de
detener HCOP JP.

La alternativa no interactiva es:

```powershell
$root = Join-Path $env:LOCALAPPDATA "HCOP_JP"
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File (Join-Path $root "instalar-desde-github.ps1") `
  -Mode Restore `
  -InstallDir $root `
  -BackupDirectory "D:\Backups\HCOP\hcop-backup-20260824-120000" `
  -ConfirmRestore
```

Desde una copia local del repositorio también puede invocar el script de datos
directamente:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\restore-hcop.ps1 `
  -BackupDirectory "D:\Backups\HCOP\hcop-backup-20260824-120000" `
  -ConfirmRestore
```

El script verifica los checksums antes de detener la aplicación, resuelve el
volumen exacto desde Docker y rechaza rutas o manifiestos que salgan de la
carpeta autorizada. Si algo falla, mantiene HCOP JP detenido para impedir el
uso de un estado parcial.

## Secretos de la instalación

El backup no copia `.env`: contiene contraseñas y las claves con las que HCOP
JP descifra integraciones como Gemini. Conserve una copia cifrada de `.env` en
un gestor de secretos o bóveda independiente. Base, storage y `.env` deben
pertenecer a la misma instalación.

Nunca suba `.env` ni una carpeta de backup a Git.

## Prueba de restauración

El repositorio incluye una prueba destructiva aislada que crea sus propios
contenedores y volúmenes efímeros. No toca la instalación clínica:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-backup-restore.ps1
```

La prueba crea un dato en PostgreSQL y otro en storage, genera el backup, los
modifica, restaura y exige recuperar exactamente los valores originales. Al
terminar elimina únicamente su proyecto Docker temporal.
