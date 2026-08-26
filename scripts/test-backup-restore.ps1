param([string]$BackendImage = "")

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "hcop-data-common.ps1")

if ([string]::IsNullOrWhiteSpace($BackendImage)) {
  foreach ($candidate in @("hcop-jp-backend:local", "hcop-pre-research:local", "hcop-jp:local")) {
    try {
      Invoke-HcopNative -Executable "docker" -Arguments @("image", "inspect", $candidate) -Capture | Out-Null
      $BackendImage = $candidate
      break
    } catch { }
  }
}
if ([string]::IsNullOrWhiteSpace($BackendImage)) {
  throw "No hay una imagen local del backend de HCOP JP para probar backup y restauración."
}

$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("hcop-backup-restore-" + [Guid]::NewGuid().ToString("N"))
$projectName = "hcop-backup-test-" + [Guid]::NewGuid().ToString("N").Substring(0, 10)
New-Item -ItemType Directory -Path $testRoot | Out-Null
$composePath = Join-Path $testRoot "compose.yaml"
$environmentPath = Join-Path $testRoot ".env"
$compose = @'
services:
  database:
    image: postgres:18.4-alpine
    environment:
      POSTGRES_DB: hcop_backup_test
      POSTGRES_USER: hcop_backup_test
      POSTGRES_PASSWORD: ${HCOP_TEST_DB_PASSWORD}
    volumes:
      - database_data:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U hcop_backup_test -d hcop_backup_test"]
      interval: 2s
      timeout: 3s
      retries: 30
  backend:
    image: ${HCOP_TEST_BACKEND_IMAGE}
    depends_on:
      database:
        condition: service_healthy
    environment:
      HCOP_DB_URL: jdbc:postgresql://database:5432/hcop_backup_test
      HCOP_DB_USER: hcop_backup_test
      HCOP_DB_PASSWORD: ${HCOP_TEST_DB_PASSWORD}
      HCOP_BOOTSTRAP_USERNAME: backup_test
      HCOP_BOOTSTRAP_PASSWORD: ${HCOP_TEST_LOGIN_PASSWORD}
      HCOP_BOOTSTRAP_SECOND_USERNAME: backup_test_2
      HCOP_SEED_EXAMPLE_PATIENT: "false"
      HCOP_QR_SECRET: ${HCOP_TEST_QR_SECRET}
      HCOP_ENCRYPTION_SECRET: ${HCOP_TEST_ENCRYPTION_SECRET}
      HCOP_JWT_SECRET: ${HCOP_TEST_JWT_SECRET}
      HCOP_BIND_ADDRESS: 0.0.0.0
      HCOP_PORT: 5180
    volumes:
      - storage_data:/opt/hcop/runtime/storage
    healthcheck:
      test: ["CMD", "bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/5180 && printf 'GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
      interval: 3s
      timeout: 5s
      retries: 30
      start_period: 20s
volumes:
  database_data:
  storage_data:
'@
$environment = @"
HCOP_TEST_BACKEND_IMAGE=$BackendImage
HCOP_TEST_DB_PASSWORD=backup-db-$([Guid]::NewGuid().ToString('N'))
HCOP_TEST_LOGIN_PASSWORD=Backup-test-2026!
HCOP_TEST_QR_SECRET=backup-qr-$([Guid]::NewGuid().ToString('N'))
HCOP_TEST_ENCRYPTION_SECRET=backup-encryption-$([Guid]::NewGuid().ToString('N'))
HCOP_TEST_JWT_SECRET=backup-jwt-$([Guid]::NewGuid().ToString('N'))-$([Guid]::NewGuid().ToString('N'))
"@
[System.IO.File]::WriteAllText($composePath, $compose, (New-Object System.Text.UTF8Encoding($false)))
[System.IO.File]::WriteAllText($environmentPath, $environment, (New-Object System.Text.UTF8Encoding($false)))
$deployment = Resolve-HcopDeployment -ProjectRoot $testRoot -ProjectName $projectName

try {
  Write-Host "Iniciando entorno efímero de backup/restauración..."
  Invoke-HcopCompose $deployment @("up", "--detach", "--wait")
  $database = Get-HcopServiceContainer $deployment "database"
  $backend = Get-HcopServiceContainer $deployment "backend"
  Invoke-HcopNative -Executable "docker" -Arguments @(
    "exec", $database, "psql", "--username=hcop_backup_test", "--dbname=hcop_backup_test",
    "--set=ON_ERROR_STOP=1", "--command=CREATE TABLE backup_restore_probe (id integer PRIMARY KEY, value text NOT NULL); INSERT INTO backup_restore_probe VALUES (1, 'original');")
  Invoke-HcopNative -Executable "docker" -Arguments @(
    "exec", $backend, "bash", "-c", "printf 'original-storage' > /opt/hcop/runtime/storage/backup-restore-probe.txt")

  $backupOutput = & (Join-Path $PSScriptRoot "backup-hcop.ps1") `
    -ProjectRoot $testRoot -ProjectName $projectName -OutputDirectory (Join-Path $testRoot "backups")
  $backupDirectory = @($backupOutput | Where-Object { Test-Path -LiteralPath ([string]$_) -PathType Container })[-1]
  if (-not $backupDirectory) { throw "La prueba no pudo identificar el backup generado." }

  Invoke-HcopNative -Executable "docker" -Arguments @(
    "exec", $database, "psql", "--username=hcop_backup_test", "--dbname=hcop_backup_test",
    "--set=ON_ERROR_STOP=1", "--command=UPDATE backup_restore_probe SET value='mutated' WHERE id=1; CREATE TABLE stale_after_backup (id integer PRIMARY KEY);")
  Invoke-HcopNative -Executable "docker" -Arguments @(
    "exec", $backend, "bash", "-c", "printf 'mutated-storage' > /opt/hcop/runtime/storage/backup-restore-probe.txt; printf 'stale' > /opt/hcop/runtime/storage/stale-after-backup.txt")

  & (Join-Path $PSScriptRoot "restore-hcop.ps1") `
    -BackupDirectory $backupDirectory `
    -ProjectRoot $testRoot `
    -ProjectName $projectName `
    -ConfirmRestore `
    -SkipSafetyBackup

  $database = Get-HcopServiceContainer $deployment "database"
  $backend = Get-HcopServiceContainer $deployment "backend"
  $databaseValue = Invoke-HcopNative -Executable "docker" -Arguments @(
    "exec", $database, "psql", "--username=hcop_backup_test", "--dbname=hcop_backup_test",
    "--tuples-only", "--no-align", "--command=SELECT value FROM backup_restore_probe WHERE id=1;") -Capture
  $staleDatabaseStatus = Invoke-HcopNative -Executable "docker" -Arguments @(
    "exec", $database, "psql", "--username=hcop_backup_test", "--dbname=hcop_backup_test",
    "--tuples-only", "--no-align", "--command=SELECT to_regclass('public.stale_after_backup') IS NULL;") -Capture
  $storageValue = Invoke-HcopNative -Executable "docker" -Arguments @(
    "exec", $backend, "bash", "-c", "cat /opt/hcop/runtime/storage/backup-restore-probe.txt") -Capture
  $staleStatus = Invoke-HcopNative -Executable "docker" -Arguments @(
    "exec", $backend, "bash", "-c", "test ! -e /opt/hcop/runtime/storage/stale-after-backup.txt && printf absent") -Capture
  if ($databaseValue.Trim() -ne "original" -or
      $staleDatabaseStatus.Trim() -ne "t" -or
      $storageValue.Trim() -ne "original-storage" -or
      $staleStatus.Trim() -ne "absent") {
    throw "La restauración no reprodujo exactamente la base y el almacenamiento respaldados."
  }
  Write-Host "Backup/restauración real verificados: PostgreSQL y storage coinciden."
} finally {
  try { Invoke-HcopCompose $deployment @("down", "--volumes", "--remove-orphans") } catch { Write-Warning $_.Exception.Message }
  Remove-HcopSafeDirectory -Path $testRoot -AllowedRoot ([System.IO.Path]::GetTempPath())
}
