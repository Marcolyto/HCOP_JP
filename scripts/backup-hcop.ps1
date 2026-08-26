param(
  [string]$ProjectRoot = "",
  [string]$ProjectName = "hcop-jp",
  [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "hcop-data-common.ps1")

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) { $ProjectRoot = $repositoryRoot }
$deployment = Resolve-HcopDeployment -ProjectRoot $ProjectRoot -ProjectName $ProjectName
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
  $OutputDirectory = Join-Path $deployment.Root "backups"
}
$outputRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$lock = New-HcopExclusiveLock (Join-Path $outputRoot ".hcop-backup.lock")
$temporary = ""
$applicationWasRunning = $false

try {
  $databaseContainer = Get-HcopServiceContainer $deployment "database"
  $applicationContainer = Get-HcopServiceContainer $deployment "backend"
  if (-not (Test-HcopContainerRunning $databaseContainer)) {
    throw "PostgreSQL debe estar iniciado para crear el backup."
  }
  $applicationWasRunning = Test-HcopContainerRunning $applicationContainer
  if ($applicationWasRunning) {
    Write-Host "Deteniendo temporalmente la aplicación para obtener una copia consistente..."
    Invoke-HcopCompose $deployment @("stop", "backend")
  }

  $databaseName = Get-HcopContainerValue $databaseContainer "POSTGRES_DB"
  $databaseUser = Get-HcopContainerValue $databaseContainer "POSTGRES_USER"
  $storageVolume = Get-HcopStorageVolume $applicationContainer
  $databaseImage = Get-HcopContainerImage $databaseContainer
  $applicationImage = Get-HcopContainerImage $applicationContainer
  $timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
  $finalName = "hcop-backup-$timestamp"
  $finalDirectory = Join-Path $outputRoot $finalName
  if (Test-Path -LiteralPath $finalDirectory) { throw "Ya existe $finalDirectory." }
  $temporary = Join-Path $outputRoot (".partial-" + [Guid]::NewGuid().ToString("N"))
  New-Item -ItemType Directory -Path $temporary | Out-Null

  $remoteDump = "/tmp/hcop-backup-$([Guid]::NewGuid().ToString('N')).dump"
  try {
    Write-Host "Respaldando PostgreSQL..."
    Invoke-HcopNative -Executable "docker" -Arguments @(
      "exec", $databaseContainer, "pg_dump", "--username=$databaseUser", "--dbname=$databaseName",
      "--format=custom", "--no-owner", "--no-privileges", "--file=$remoteDump")
    Invoke-HcopNative -Executable "docker" -Arguments @(
      "cp", "${databaseContainer}:$remoteDump", (Join-Path $temporary "database.dump"))
  } finally {
    try { Invoke-HcopNative -Executable "docker" -Arguments @("exec", $databaseContainer, "rm", "-f", $remoteDump) } catch { }
  }

  Write-Host "Respaldando archivos clínicos..."
  Invoke-HcopNative -Executable "docker" -Arguments @(
    "run", "--rm", "--entrypoint", "sh",
    "--mount", "type=volume,src=$storageVolume,dst=/data,readonly",
    "--mount", "type=bind,src=$temporary,dst=/backup",
    $databaseImage, "-c", "set -eu; tar -C /data -czf /backup/storage.tar.gz .")

  $databaseFile = Get-Item -LiteralPath (Join-Path $temporary "database.dump")
  $storageFile = Get-Item -LiteralPath (Join-Path $temporary "storage.tar.gz")
  if ($databaseFile.Length -le 0 -or $storageFile.Length -le 0) { throw "El backup generado está vacío." }
  $manifest = [ordered]@{
    schemaVersion = 1
    createdAt = (Get-Date).ToUniversalTime().ToString("o")
    projectName = $deployment.ProjectName
    releaseCommit = $deployment.ReleaseCommit
    databaseName = $databaseName
    databaseUser = $databaseUser
    databaseImage = $databaseImage
    applicationImage = $applicationImage
    storageVolume = $storageVolume
    environmentRequired = $true
    environmentNotice = "Conserve el archivo .env por separado en un lugar seguro; contiene las claves necesarias para descifrar integraciones."
    files = [ordered]@{
      database = [ordered]@{
        name = $databaseFile.Name
        length = $databaseFile.Length
        sha256 = (Get-FileHash -LiteralPath $databaseFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
      }
      storage = [ordered]@{
        name = $storageFile.Name
        length = $storageFile.Length
        sha256 = (Get-FileHash -LiteralPath $storageFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
      }
    }
  }
  [System.IO.File]::WriteAllText(
    (Join-Path $temporary "manifest.json"),
    ($manifest | ConvertTo-Json -Depth 8),
    (New-Object System.Text.UTF8Encoding($false)))
  Move-Item -LiteralPath $temporary -Destination $finalDirectory
  $temporary = ""
  Write-Host "Backup verificado: $finalDirectory"
  Write-Output $finalDirectory
} finally {
  if ($temporary) { Remove-HcopSafeDirectory -Path $temporary -AllowedRoot $outputRoot }
  if ($applicationWasRunning) {
    Write-Host "Reiniciando HCOP JP..."
    try { Invoke-HcopCompose $deployment @("up", "--detach", "--wait", "backend") } catch { Write-Warning $_.Exception.Message }
  }
  if ($lock) { $lock.Dispose() }
}
