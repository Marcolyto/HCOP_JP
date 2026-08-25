[CmdletBinding()]
param(
  [ValidateRange(1024, 65535)]
  [int]$Port = 5182,
  [switch]$SkipBuild,
  [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$composeFile = Join-Path $root 'compose.e2e.yaml'
$runId = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$project = "hcop-ajp-conflict-e2e-$runId"

if (-not $SkipInstall) {
  Write-Host 'Preparando dependencias locales de la prueba de navegador...'
  Push-Location (Join-Path $root 'frontend')
  try {
    $env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
    & npm ci
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron instalar las dependencias E2E.' }
  } finally {
    Pop-Location
    Remove-Item Env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD -ErrorAction SilentlyContinue
  }
}

function New-HcopE2eSecret {
  $bytes = New-Object byte[] 32
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
  ([Convert]::ToBase64String($bytes)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$env:HCOP_E2E_PORT = [string]$Port
$env:HCOP_E2E_BASE_URL = "http://127.0.0.1:$Port"
$env:HCOP_E2E_USERNAME = 'qa_conflict'
$env:HCOP_E2E_PASSWORD = New-HcopE2eSecret
$env:HCOP_E2E_DB_PASSWORD = New-HcopE2eSecret
$env:HCOP_E2E_QR_SECRET = New-HcopE2eSecret
$env:HCOP_E2E_ENCRYPTION_SECRET = New-HcopE2eSecret
$env:HCOP_E2E_JWT_SECRET = New-HcopE2eSecret

$compose = @('compose', '--project-name', $project, '--file', $composeFile)
$failure = $null
$cleanupFailure = $null
try {
  Write-Host "Preparando entorno clínico descartable $project en 127.0.0.1:$Port..."
  $up = @($compose + @('up', '--detach', '--wait'))
  if (-not $SkipBuild) { $up += '--build' }
  & docker @up
  if ($LASTEXITCODE -ne 0) { throw 'Docker no pudo iniciar el entorno E2E.' }

  $healthUrl = "$($env:HCOP_E2E_BASE_URL)/actuator/health"
  $ready = $false
  for ($attempt = 0; $attempt -lt 60; $attempt += 1) {
    try {
      $health = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 3
      if ($health.status -eq 'UP') { $ready = $true; break }
    } catch {
      Start-Sleep -Milliseconds 500
    }
  }
  if (-not $ready) { throw "La aplicación no respondió UP en $healthUrl." }

  Push-Location (Join-Path $root 'frontend')
  try {
    & npm run test:e2e:conflict
    if ($LASTEXITCODE -ne 0) { throw 'La prueba E2E de concurrencia clínica falló.' }
  } finally {
    Pop-Location
  }
} catch {
  $failure = $_
} finally {
  Write-Host 'Eliminando el paciente, la base y el almacenamiento sintéticos...'
  try {
    & docker @compose down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0) { throw "Docker no pudo limpiar el entorno $project." }
  } catch {
    $cleanupFailure = $_
  }
  Remove-Item Env:HCOP_E2E_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:HCOP_E2E_DB_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:HCOP_E2E_QR_SECRET -ErrorAction SilentlyContinue
  Remove-Item Env:HCOP_E2E_ENCRYPTION_SECRET -ErrorAction SilentlyContinue
  Remove-Item Env:HCOP_E2E_JWT_SECRET -ErrorAction SilentlyContinue
}

if ($cleanupFailure) {
  if ($failure) {
    throw "$($failure.Exception.Message) Además falló la limpieza: $($cleanupFailure.Exception.Message)"
  }
  throw $cleanupFailure
}
if ($failure) { throw $failure }
