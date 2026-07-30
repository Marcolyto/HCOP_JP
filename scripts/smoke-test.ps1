$ErrorActionPreference = "Stop"
$baseUrl = if ($env:HCOP_TEST_URL) { $env:HCOP_TEST_URL.TrimEnd("/") } else { "http://127.0.0.1:5180" }
$health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get
if ($health.status -ne "UP") {
  throw "El servicio no esta saludable."
}
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$testPassword = [string]$env:HCOP_BOOTSTRAP_PASSWORD
if ([string]::IsNullOrWhiteSpace($testPassword)) {
  throw "Defina HCOP_BOOTSTRAP_PASSWORD para ejecutar la prueba."
}
$loginBody = @{
  username = if ($env:HCOP_BOOTSTRAP_USERNAME) { $env:HCOP_BOOTSTRAP_USERNAME } else { "marcolyto" }
  password = $testPassword
} | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $loginBody -WebSession $session
if (-not $login.ok) {
  throw "No se pudo iniciar sesion."
}
$status = Invoke-RestMethod -Uri "$baseUrl/api/clinical/status" -Method Get -WebSession $session
if (-not $status.ok) {
  throw "El nucleo clinico no respondio."
}

$templates = Invoke-RestMethod -Uri "$baseUrl/api/study-templates?scope=all&includeInactive=1" -Method Get -WebSession $session
if (-not $templates.ok -or $templates.bundledCount -lt 1 -or $templates.total -lt $templates.bundledCount) {
  throw "La biblioteca de plantillas anatomicas no esta disponible."
}

$protocols = Invoke-RestMethod -Uri "$baseUrl/api/clinical/protocols?includeArchived=1&includeCatalog=1" -Method Get -WebSession $session
if (-not $protocols.ok -or $protocols.catalogCount -lt 1 -or $protocols.total -lt $protocols.catalogCount) {
  throw "El catalogo de protocolos no esta disponible."
}
$prescriptionSchemes = Invoke-RestMethod -Uri "$baseUrl/api/clinical/schemes" -Method Get -WebSession $session
$breastPrescriptionSchemes = @($prescriptionSchemes.schemes |
    Where-Object { [string]$_.nombre -match "(?i)mama|breast" })
$breastConfigurationProtocols = @($protocols.protocols |
    Where-Object { [string]$_.name -match "(?i)mama|breast" })
if (-not $prescriptionSchemes.ok -or
    $prescriptionSchemes.total -le 200 -or
    $breastPrescriptionSchemes.Count -lt 1 -or
    $breastConfigurationProtocols.Count -lt 1) {
  throw "El catalogo se trunco o no publica protocolos de mama en Prescripcion y Configuracion."
}

$dayHospital = Invoke-RestMethod -Uri "$baseUrl/api/clinical/configuration/day-hospital-settings" -Method Get -WebSession $session
$slotMinutes = [int]$dayHospital.items[0].definition.slotMinutes
if ($slotMinutes -notin @(5, 10, 15, 20, 30)) {
  throw "El intervalo del turnero de Hospital de dia no es valido."
}

$pages = @(
  @{ Path = "/configuration/"; Marker = "Centro de configuracion"; Name = "Centro de configuracion" },
  @{ Path = "/protocol-admin/"; Marker = "Administrador de protocolos"; Name = "Administrador de protocolos" },
  @{ Path = "/herramientas/"; Marker = "tool-list"; Name = "Herramientas clinicas" }
)
foreach ($page in $pages) {
  $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl$($page.Path)" -Method Get -WebSession $session
  if ($response.StatusCode -ne 200 -or $response.Content -notmatch $page.Marker) {
    throw "$($page.Name) no esta disponible."
  }
}

$openApi = Invoke-RestMethod -Uri "$baseUrl/v3/api-docs" -Method Get -WebSession $session
if ([string]::IsNullOrWhiteSpace([string]$openApi.openapi) -or $null -eq $openApi.paths) {
  throw "La documentacion OpenAPI no esta disponible."
}

Write-Host "HCOP JP operativo: salud, autenticacion, nucleo clinico, configuracion y OpenAPI verificados."
