param(
  [string]$BaseUrl = "http://127.0.0.1:5180",
  [string]$Username = "marcolyto",
  [string]$Password = $env:HCOP_BOOTSTRAP_PASSWORD
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
if ([string]::IsNullOrWhiteSpace($Password)) {
  throw "Defina HCOP_BOOTSTRAP_PASSWORD o informe -Password."
}

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw $Message }
}

function Invoke-Json {
  param(
    [ValidateSet("GET", "POST", "PUT", "DELETE")][string]$Method = "GET",
    [string]$Path,
    [object]$Body = $null
  )
  $parameters = @{
    Uri = "$BaseUrl$Path"
    Method = $Method
    WebSession = $script:Session
    Headers = @{ Accept = "application/json" }
  }
  if ($null -ne $Body) {
    $parameters.ContentType = "application/json; charset=utf-8"
    $json = $Body | ConvertTo-Json -Depth 50 -Compress
    $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
  }
  Invoke-RestMethod @parameters
}

function Invoke-ExpectedError {
  param(
    [scriptblock]$Operation,
    [int]$ExpectedStatus,
    [string]$ExpectedCode
  )
  try {
    & $Operation | Out-Null
    throw "La solicitud debia fallar con estado $ExpectedStatus."
  } catch {
    $response = $_.Exception.Response
    if ($null -eq $response) { throw }
    $status = [int]$response.StatusCode
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
    Assert-True ($status -eq $ExpectedStatus) "Estado inesperado: $status. $detail"
    $payload = $detail | ConvertFrom-Json
    Assert-True ([string]$payload.code -eq $ExpectedCode) "Codigo inesperado: $detail"
  }
}

$script:Session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$login = Invoke-Json -Method POST -Path "/api/auth/login" -Body @{
  username = $Username
  password = $Password
}
Assert-True ([bool]$login.authenticated) "No se pudo iniciar sesion."

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$fileName = "guia-contrato-$suffix.pdf"
$encodedName = [Uri]::EscapeDataString($fileName)
$pdf = [System.Text.Encoding]::ASCII.GetBytes("%PDF-1.4`n% HCOP guide contract`n")

$upload = Invoke-RestMethod -Uri "$BaseUrl/api/guides/import?name=$encodedName" `
  -Method Put -ContentType "application/pdf" -Body $pdf -WebSession $script:Session
Assert-True ([bool]$upload.ok) "No se pudo cargar la guia."
Assert-True ([string]$upload.name -eq $fileName) "Cambio el nombre del archivo."
Assert-True ([long]$upload.size -eq $pdf.Length) "Cambio el contenido cargado."

$created = Invoke-Json -Method POST -Path "/api/clinical/configuration/guide" -Body @{
  key = "guide:contract-$suffix"
  name = "Guia contrato $suffix"
  description = "Biblioteca de prueba"
  active = $true
  definition = @{
    fileName = $fileName
    category = "Torax"
    audience = "Oncologia"
    source = "Contrato local"
    version = "2026.1"
    tags = @("pulmon", "prueba")
  }
}
Assert-True ([bool]$created.ok) "No se pudieron guardar los metadatos."

$listed = Invoke-Json -Path "/api/guides?includeInactive=0"
$guide = @($listed.guides | Where-Object { $_.name -eq $fileName }) | Select-Object -First 1
Assert-True ($null -ne $guide) "La guia cargada no aparece en la biblioteca."
Assert-True ([string]$guide.title -eq "Guia contrato $suffix") "No se aplico el titulo versionado."
Assert-True ([string]$guide.site -eq "Torax") "No se aplico la categoria."
Assert-True (@($guide.tags).Count -eq 2) "No se conservaron las etiquetas."
Assert-True ([string]$guide.configurationId -eq [string]$created.item.id) `
  "No se vinculo el archivo con su configuracion."

$download = Join-Path ([System.IO.Path]::GetTempPath()) "hcop-guide-contract-$suffix.pdf"
try {
  Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/guides/file?name=$encodedName" `
    -WebSession $script:Session -OutFile $download
  $downloaded = [System.IO.File]::ReadAllBytes($download)
  Assert-True (
    [Convert]::ToBase64String($downloaded) -eq [Convert]::ToBase64String($pdf)
  ) "La descarga no coincide byte a byte."
} finally {
  if (Test-Path -LiteralPath $download) { Remove-Item -LiteralPath $download -Force }
}

$updated = Invoke-Json -Method PUT `
  -Path "/api/clinical/configuration/guide/$($created.item.id)" `
  -Body @{
    name = $created.item.name
    description = $created.item.description
    active = $false
    expectedRevision = [long]$created.item.revision
    definition = $created.item.definition
  }
Assert-True (-not [bool]$updated.item.active) "No se desactivo la guia."

$activeList = Invoke-Json -Path "/api/guides?includeInactive=0"
$fullList = Invoke-Json -Path "/api/guides?includeInactive=1"
Assert-True (@($activeList.guides | Where-Object { $_.name -eq $fileName }).Count -eq 0) `
  "La guia inactiva aparece en Herramientas."
$inactiveGuide = @($fullList.guides | Where-Object { $_.name -eq $fileName }) | Select-Object -First 1
Assert-True ($null -ne $inactiveGuide -and -not [bool]$inactiveGuide.active) `
  "La guia inactiva no se recupera en Configuracion."
Assert-True ([long]$inactiveGuide.configurationRevision -gt [long]$created.item.revision) `
  "La revision esperada enviada por la interfaz no se aplico."

Invoke-ExpectedError -ExpectedStatus 400 -ExpectedCode "INVALID_GUIDE" -Operation {
  Invoke-RestMethod -Uri "$BaseUrl/api/guides/import?name=contenido-invalido.pdf" `
    -Method Put -ContentType "application/pdf" `
    -Body ([System.Text.Encoding]::ASCII.GetBytes("not-pdf")) `
    -WebSession $script:Session
}
Invoke-ExpectedError -ExpectedStatus 400 -ExpectedCode "INVALID_GUIDE" -Operation {
  Invoke-RestMethod -Uri "$BaseUrl/api/guides/import?name=guia.txt" `
    -Method Put -ContentType "application/pdf" -Body $pdf -WebSession $script:Session
}
Invoke-ExpectedError -ExpectedStatus 404 -ExpectedCode "GUIDE_NOT_FOUND" -Operation {
  Invoke-RestMethod -Uri "$BaseUrl/api/guides/file?name=no-existe-$suffix.pdf" `
    -WebSession $script:Session
}

@{
  ok = $true
  fileName = $fileName
  size = $pdf.Length
  configurationId = [string]$created.item.id
  initialRevision = [long]$created.item.revision
  finalRevision = [long]$inactiveGuide.configurationRevision
  inactive = (-not [bool]$inactiveGuide.active)
  byteExactDownload = $true
} | ConvertTo-Json -Compress
