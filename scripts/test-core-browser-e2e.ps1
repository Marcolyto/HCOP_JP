[CmdletBinding()]
param(
  [ValidateRange(1024, 65535)]
  [int]$Port = 5183,
  [switch]$SkipBuild,
  [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$frontend = Join-Path $root 'frontend'
$composeFile = Join-Path $root 'compose.e2e.yaml'
$runId = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$project = "hcop-ajp-core-browser-e2e-$runId"
$image = "hcop-jp-core-browser-e2e:$runId"
$useBundledBrowser = $env:OS -ne 'Windows_NT'

function New-HcopBrowserE2eSecret {
  $bytes = New-Object byte[] 32
  $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
  try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
  ([Convert]::ToBase64String($bytes)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

if (-not $SkipInstall) {
  Write-Host 'Preparando Playwright y su navegador reproducible...'
  Push-Location $frontend
  try {
    & npm ci
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron instalar las dependencias E2E.' }
    if ($useBundledBrowser) {
      & npx playwright install chromium
      if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar Chromium para Playwright.' }
    }
  } finally {
    Pop-Location
  }
}

$env:HCOP_E2E_PORT = [string]$Port
$env:HCOP_E2E_BASE_URL = "http://127.0.0.1:$Port"
$env:HCOP_E2E_USERNAME = 'qa_browser'
$env:HCOP_E2E_SECOND_USERNAME = 'qa_browser_2'
$env:HCOP_E2E_PASSWORD = New-HcopBrowserE2eSecret
$env:HCOP_E2E_DB_PASSWORD = New-HcopBrowserE2eSecret
$env:HCOP_E2E_QR_SECRET = New-HcopBrowserE2eSecret
$env:HCOP_E2E_ENCRYPTION_SECRET = New-HcopBrowserE2eSecret
$env:HCOP_E2E_JWT_SECRET = New-HcopBrowserE2eSecret
$env:HCOP_E2E_SEED_EXAMPLE_PATIENT = 'true'
if ($useBundledBrowser) {
  $env:HCOP_E2E_USE_BUNDLED_BROWSER = 'true'
} else {
  $env:HCOP_E2E_BROWSER_CHANNEL = 'chrome'
  Remove-Item Env:HCOP_E2E_USE_BUNDLED_BROWSER -ErrorAction SilentlyContinue
}
$env:HCOP_E2E_APP_IMAGE = $image

$compose = @('compose', '--project-name', $project, '--file', $composeFile)
$failure = $null
$cleanupFailure = $null
try {
  Write-Host "Iniciando entorno descartable $project en 127.0.0.1:$Port..."
  $up = @($compose + @('up', '--detach', '--wait'))
  if (-not $SkipBuild) { $up += '--build' }
  & docker @up
  if ($LASTEXITCODE -ne 0) { throw 'Docker no pudo iniciar el entorno E2E.' }

  Push-Location $frontend
  try {
    & npm run test:e2e:core
    if ($LASTEXITCODE -ne 0) { throw 'La prueba E2E de circuitos esenciales falló.' }
  } finally {
    Pop-Location
  }
} catch {
  $failure = $_
  Write-Host 'Diagnóstico del entorno descartable:'
  & docker @compose logs --no-color
} finally {
  Write-Host 'Eliminando base, archivos, contenedores y redes sintéticos...'
  try {
    & docker @compose down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0) { throw "Docker no pudo limpiar el entorno $project." }
    & docker image rm $image 2>$null | Out-Null
  } catch {
    $cleanupFailure = $_
  }
  @(
    'HCOP_E2E_PASSWORD', 'HCOP_E2E_DB_PASSWORD', 'HCOP_E2E_QR_SECRET',
    'HCOP_E2E_ENCRYPTION_SECRET', 'HCOP_E2E_JWT_SECRET', 'HCOP_E2E_SEED_EXAMPLE_PATIENT',
    'HCOP_E2E_USE_BUNDLED_BROWSER', 'HCOP_E2E_BROWSER_CHANNEL', 'HCOP_E2E_APP_IMAGE'
  ) | ForEach-Object { Remove-Item "Env:$_" -ErrorAction SilentlyContinue }
}

if ($cleanupFailure) {
  if ($failure) {
    throw "$($failure.Exception.Message) Además falló la limpieza: $($cleanupFailure.Exception.Message)"
  }
  throw $cleanupFailure
}
if ($failure) { throw $failure }
