# Guardián del corte de infraestructura (F0): antes estos redirects y este servicio de
# estáticos vivían en WebConfiguration.java (backend) y los probaba WebConfigurationRoutingTest.
# Desde el corte los sirve nginx (frontend/nginx.conf) y este script es la prueba de contrato.
$ErrorActionPreference = "Stop"
$baseUrl = if ($env:HCOP_TEST_URL) { $env:HCOP_TEST_URL.TrimEnd("/") } else { "http://127.0.0.1:5180" }

function Test-Redirect([string]$Path, [string]$ExpectedLocation) {
  try {
    Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl$Path" -Method Get -MaximumRedirection 0 | Out-Null
    throw "$Path no redirigio (se esperaba 302 a '$ExpectedLocation')."
  } catch {
    $webResponse = $_.Exception.Response
    if (-not $webResponse) { throw }
    $statusCode = [int]$webResponse.StatusCode
    if ($statusCode -ne 302) {
      throw "$Path debia responder 302, respondio $statusCode."
    }
    $location = [string]$webResponse.Headers.Location
    if ($location -ne $ExpectedLocation) {
      throw "$Path redirige a '$location', se esperaba '$ExpectedLocation'."
    }
  }
}

function Test-NotFound([string]$Path) {
  try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl$Path" -Method Get
    throw "El recurso legacy $Path sigue publicado con estado $($response.StatusCode)."
  } catch {
    $statusCode = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    if ($statusCode -ne 404) { throw }
  }
}

# Los 12 redirects legacy que antes vivian en WebConfiguration.ANGULAR_ENTRY_REDIRECTS.
Test-Redirect "/" "/app/"
Test-Redirect "/index.html" "/app/"
Test-Redirect "/app" "/app/"
Test-Redirect "/configuration" "/app/#/configuration"
Test-Redirect "/configuration/" "/app/#/configuration"
Test-Redirect "/configuration/index.html" "/app/#/configuration"
Test-Redirect "/protocol-admin" "/app/#/configuration?tab=protocols"
Test-Redirect "/protocol-admin/" "/app/#/configuration?tab=protocols"
Test-Redirect "/protocol-admin/index.html" "/app/#/configuration?tab=protocols"
Test-Redirect "/herramientas" "/app/#/herramientas"
Test-Redirect "/herramientas/" "/app/#/herramientas"
Test-Redirect "/herramientas/index.html" "/app/#/herramientas"
Test-Redirect "/docs" "/docs/index.html"
Test-Redirect "/docs/" "/docs/index.html"

# La SPA Angular responde 200 con el shell montado.
$app = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/app/" -Method Get
if ($app.StatusCode -ne 200 -or $app.Content -notmatch "<app-root") {
  throw "/app/ no entrego el shell Angular esperado."
}

# Un activo real de la biblioteca de plantillas, servido directo por nginx (no por el backend).
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $repositoryRoot "backend/runtime/catalogs/study-templates/manifest.json"
$manifest = Get-Content -Raw $manifestPath | ConvertFrom-Json
$thumbnail = $manifest.templates[0].thumbnail
$thumbnailResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/$thumbnail" -Method Get
if ($thumbnailResponse.StatusCode -ne 200 -or $thumbnailResponse.Headers["Content-Type"] -notmatch "image/webp") {
  throw "El activo $thumbnail no se sirvio como image/webp desde nginx."
}

# El video de ayuda soporta Range (necesario para seek en el reproductor).
$videoResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/help/media/circuito-hospital-dia-paso-a-paso.mp4" `
  -Method Get -Headers @{ Range = "bytes=0-1023" }
if ($videoResponse.StatusCode -ne 206) {
  throw "El video de ayuda no respondio 206 (Range) - status $($videoResponse.StatusCode)."
}

# Los paths legacy ejecutables no deben quedar publicados.
Test-NotFound "/app.js"
Test-NotFound "/configuration/configuration.js"
Test-NotFound "/protocol-admin/protocol-admin.js"
Test-NotFound "/herramientas/js/app.js"
Test-NotFound "/herramientas/pages/01-ecog-karnofsky.html"
Test-NotFound "/help/help.js"

Write-Host "OK - redirects, SPA, activos estaticos y 404 legacy verificados contra nginx."
