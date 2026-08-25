param(
  [Parameter(Mandatory = $true)][string]$BackupDirectory,
  [string]$ProjectRoot = "",
  [string]$ProjectName = "hcop-jp",
  [switch]$ConfirmRestore,
  [switch]$SkipSafetyBackup
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "hcop-data-common.ps1")

if (-not $ConfirmRestore) {
  throw "La restauración reemplaza la base y los archivos actuales. Repita con -ConfirmRestore."
}
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { $ProjectRoot = $repositoryRoot }
$deployment = Resolve-HcopDeployment -ProjectRoot $ProjectRoot -ProjectName $ProjectName
$backupRoot = [System.IO.Path]::GetFullPath($BackupDirectory)
if (-not (Test-Path -LiteralPath $backupRoot -PathType Container)) { throw "El backup no existe: $backupRoot" }
$manifestPath = Join-Path $backupRoot "manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "El backup no contiene manifest.json." }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ([int]$manifest.schemaVersion -ne 1) { throw "La versión del manifiesto de backup no es compatible." }

$databaseFile = Join-Path $backupRoot ([string]$manifest.files.database.name)
$storageFile = Join-Path $backupRoot ([string]$manifest.files.storage.name)
foreach ($file in @($databaseFile, $storageFile)) {
  $resolved = [System.IO.Path]::GetFullPath($file)
  $allowedPrefix = $backupRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
  if (-not $resolved.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
      -not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
    throw "El manifiesto referencia un archivo fuera del backup."
  }
}
if ((Get-FileHash -LiteralPath $databaseFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne [string]$manifest.files.database.sha256 -or
    (Get-FileHash -LiteralPath $storageFile -Algorithm SHA256).Hash.ToLowerInvariant() -ne [string]$manifest.files.storage.sha256) {
  throw "El backup está incompleto o fue modificado; los checksums no coinciden."
}

$operationRoot = Join-Path $deployment.Root "backups"
New-Item -ItemType Directory -Path $operationRoot -Force | Out-Null
$lock = New-HcopExclusiveLock (Join-Path $operationRoot ".hcop-restore.lock")
$applicationWasRunning = $false
try {
  Invoke-HcopCompose $deployment @("up", "--detach", "--wait", "database")
  try { $applicationContainer = Get-HcopServiceContainer $deployment "backend" } catch {
    Invoke-HcopCompose $deployment @("create", "backend")
    $applicationContainer = Get-HcopServiceContainer $deployment "backend"
  }
  $databaseContainer = Get-HcopServiceContainer $deployment "database"
  $applicationWasRunning = Test-HcopContainerRunning $applicationContainer

  if (-not $SkipSafetyBackup) {
    Write-Host "Creando backup de seguridad del estado que será reemplazado..."
    & (Join-Path $PSScriptRoot "backup-hcop.ps1") `
      -ProjectRoot $deployment.Root `
      -ProjectName $deployment.ProjectName `
      -OutputDirectory (Join-Path $operationRoot "pre-restore") | Out-Host
  }
  if (Test-HcopContainerRunning $applicationContainer) {
    Invoke-HcopCompose $deployment @("stop", "backend")
  }

  $databaseName = Get-HcopContainerValue $databaseContainer "POSTGRES_DB"
  $databaseUser = Get-HcopContainerValue $databaseContainer "POSTGRES_USER"
  if ($databaseName -in @("postgres", "template0", "template1")) {
    throw "Se rechazó restaurar sobre una base reservada de PostgreSQL."
  }
  $storageVolume = Get-HcopStorageVolume $applicationContainer
  $databaseImage = Get-HcopContainerImage $databaseContainer
  $remoteDump = "/tmp/hcop-restore-$([Guid]::NewGuid().ToString('N')).dump"
  try {
    Write-Host "Restaurando PostgreSQL..."
    Invoke-HcopNative -Executable "docker" -Arguments @("cp", $databaseFile, "${databaseContainer}:$remoteDump")
    Invoke-HcopNative -Executable "docker" -Arguments @(
      "exec", $databaseContainer, "dropdb", "--username=$databaseUser", "--maintenance-db=postgres",
      "--force", "--if-exists", $databaseName)
    Invoke-HcopNative -Executable "docker" -Arguments @(
      "exec", $databaseContainer, "createdb", "--username=$databaseUser", "--maintenance-db=postgres",
      "--owner=$databaseUser", $databaseName)
    Invoke-HcopNative -Executable "docker" -Arguments @(
      "exec", $databaseContainer, "pg_restore", "--username=$databaseUser", "--dbname=$databaseName",
      "--no-owner", "--no-privileges", "--exit-on-error", $remoteDump)
  } finally {
    try { Invoke-HcopNative -Executable "docker" -Arguments @("exec", $databaseContainer, "rm", "-f", $remoteDump) } catch { }
  }

  Write-Host "Restaurando archivos clínicos en el volumen verificado $storageVolume..."
  Invoke-HcopNative -Executable "docker" -Arguments @(
    "run", "--rm", "--entrypoint", "sh",
    "--mount", "type=volume,src=$storageVolume,dst=/data",
    "--mount", "type=bind,src=$backupRoot,dst=/backup,readonly",
    $databaseImage, "-c",
    "set -eu; test -f /backup/$([string]$manifest.files.storage.name); find /data -mindepth 1 -maxdepth 1 -exec rm -rf -- '{}' '+'; tar -C /data -xzf /backup/$([string]$manifest.files.storage.name)")

  Write-Host "Iniciando y verificando HCOP JP..."
  Invoke-HcopCompose $deployment @("up", "--detach", "--wait", "backend")
  Write-Host "Restauración completada y servicio saludable."
} catch {
  Write-Warning "La restauración no terminó. HCOP JP permanecerá detenido para evitar usar un estado parcial."
  try { Invoke-HcopCompose $deployment @("stop", "backend") } catch { }
  throw
} finally {
  if ($lock) { $lock.Dispose() }
}
