param(
  [string]$BaseUrl = "http://127.0.0.1:5180",
  [string]$Username = "marcolyto",
  [string]$Password = $env:HCOP_BOOTSTRAP_PASSWORD
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Password)) {
  throw "Defina HCOP_BOOTSTRAP_PASSWORD o informe -Password."
}

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw $Message }
}

function Invoke-ExpectedError {
  param(
    [scriptblock]$Operation,
    [int]$Status,
    [string]$Code
  )
  try {
    & $Operation
    throw "La operacion debio devolver HTTP $Status."
  } catch {
    $response = $_.Exception.Response
    if ($null -eq $response -or [int]$response.StatusCode -ne $Status) { throw }
    $detail = [string]$_.ErrorDetails.Message
    if ([string]::IsNullOrWhiteSpace($detail)) {
      try {
        if ($null -ne $response.Content) {
          $detail = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        } else {
          $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
          try { $detail = $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
      } catch {
        $detail = ""
      }
    }
    Assert-True (-not [string]::IsNullOrWhiteSpace($detail)) "La respuesta de error no contiene JSON."
    $payload = $detail | ConvertFrom-Json
    Assert-True ([string]$payload.code -eq $Code) "Se esperaba $Code y se obtuvo $($payload.code)."
    return $payload
  }
}

$base = $BaseUrl.TrimEnd("/")
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post `
  -ContentType "application/json" `
  -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) `
  -WebSession $session
Assert-True ([bool]$login.ok) "No se pudo iniciar sesion."

$catalog = Invoke-RestMethod -Uri "$base/api/clinical/coir-catalog" -WebSession $session
Assert-True ([bool]$catalog.ok) "El catalogo COIR no respondio."
Assert-True ([int]$catalog.total -gt 200) "El catalogo COIR esta incompleto."
$coir = @($catalog.catalog | Where-Object { $_.coirSchemeId -eq "347" }) | Select-Object -First 1
Assert-True ($null -ne $coir) "No se encontro el esquema COIR 347 de referencia."

$coirDetail = Invoke-RestMethod -Uri "$base/api/clinical/protocols/coir-347" -WebSession $session
Assert-True ([bool]$coirDetail.ok) "No se pudo abrir el detalle COIR."
Assert-True ([bool]$coirDetail.protocol.catalogOnly) "El detalle COIR no se identifico como catalogo."
Assert-True (@($coirDetail.protocol.components).Count -gt 0) "El detalle COIR no contiene drogas."

$key = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$createBody = @{
  name = "Contrato FOLFIRI $key"
  category = "Colon"
  description = "Verificacion hexagonal"
  cycleDays = 14
  durationMinutes = 120
  coirSchemeId = "347"
  active = $true
  components = @(
    @{
      drugId = "101"
      drugName = "Irinotecan"
      day = "1"
      prescribedDoseText = "180"
      doseUnit = "mg/m2"
      doseCalculationMethod = "Superficie corporal"
      route = "Endovenosa"
      administrationTime = "90 min"
      dayHospital = $true
    }
  )
  preparations = @()
} | ConvertTo-Json -Depth 12

$created = Invoke-RestMethod -Uri "$base/api/clinical/protocols" -Method Post `
  -ContentType "application/json" -Body $createBody -WebSession $session
Assert-True ([bool]$created.ok) "No se pudo crear el protocolo."
$protocol = $created.protocol
Assert-True (-not [bool]$protocol.catalogOnly) "El protocolo local se marco como catalogo."
Assert-True ([string]$protocol.coirSchemeId -eq "347") "No se conservo el vinculo COIR."
Assert-True ([int]$protocol.componentCount -eq 1) "No se conservo la droga."
Assert-True ([string]$protocol.durationText -eq "2 h") "La duracion no se represento correctamente."

$merged = Invoke-RestMethod -Uri "$base/api/clinical/protocols?includeArchived=1&includeCatalog=1" `
  -WebSession $session
$localRows = @($merged.protocols | Where-Object { [string]$_.id -eq [string]$protocol.id })
$duplicateCatalogRows = @($merged.protocols | Where-Object { [string]$_.id -eq "coir-347" })
Assert-True ($localRows.Count -eq 1) "El protocolo local no aparece una sola vez."
Assert-True ($duplicateCatalogRows.Count -eq 0) "El protocolo vinculado tambien aparece como COIR sin vincular."

$updateBody = @{
  name = $protocol.name
  category = "Colon"
  description = "Actualizado por contrato"
  cycleDays = 14
  durationMinutes = 125
  coirSchemeId = "347"
  active = $true
  components = $protocol.components
  preparations = @()
  revision = [long]$protocol.revision
} | ConvertTo-Json -Depth 20
$updated = Invoke-RestMethod -Uri "$base/api/clinical/protocols/$($protocol.id)" -Method Put `
  -ContentType "application/json" -Body $updateBody -WebSession $session
Assert-True ([int]$updated.protocol.durationMinutes -eq 125) "La actualizacion no se persistio."
Assert-True ([long]$updated.protocol.revision -gt [long]$protocol.revision) "La revision no avanzo."

Invoke-ExpectedError -Status 409 -Code "VERSION_CONFLICT" -Operation {
  Invoke-RestMethod -Uri "$base/api/clinical/protocols/$($protocol.id)" -Method Put `
    -ContentType "application/json" -Body $updateBody -WebSession $session
} | Out-Null

$drugResult = Invoke-RestMethod -Uri "$base/api/clinical/drugs?q=irinotecan" -WebSession $session
Assert-True (@($drugResult.drugs).Count -gt 0) "La busqueda de drogas no devolvio Irinotecan."

$archived = Invoke-RestMethod -Uri "$base/api/clinical/protocols/$($protocol.id)" -Method Delete `
  -WebSession $session
Assert-True (-not [bool]$archived.protocol.active) "El protocolo no se archivo."

$active = Invoke-RestMethod -Uri "$base/api/clinical/protocols?includeArchived=0&includeCatalog=0" `
  -WebSession $session
$inactive = Invoke-RestMethod -Uri "$base/api/clinical/protocols?includeArchived=1&includeCatalog=0" `
  -WebSession $session
Assert-True (@($active.protocols | Where-Object { [string]$_.id -eq [string]$protocol.id }).Count -eq 0) `
  "El protocolo archivado aparece entre los activos."
Assert-True (@($inactive.protocols | Where-Object { [string]$_.id -eq [string]$protocol.id }).Count -eq 1) `
  "El protocolo archivado no se recupera."

Invoke-ExpectedError -Status 404 -Code "PROTOCOL_NOT_FOUND" -Operation {
  Invoke-RestMethod -Uri "$base/api/clinical/protocols/coir-347" -Method Delete -WebSession $session
} | Out-Null

@{
  ok = $true
  protocolId = [string]$protocol.id
  linkedCoirScheme = "347"
  initialRevision = [long]$protocol.revision
  finalRevision = [long]$archived.protocol.revision
  components = [int]$protocol.componentCount
  catalogTotal = [int]$catalog.total
  archived = (-not [bool]$archived.protocol.active)
} | ConvertTo-Json -Compress
