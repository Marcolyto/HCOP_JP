param(
  [string]$BaseUrl = "http://127.0.0.1:5180",
  [string]$Username = "marcolyto",
  [string]$Password = $env:HCOP_BOOTSTRAP_PASSWORD
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
if ([string]::IsNullOrWhiteSpace($Password)) {
  throw "Defina HCOP_BOOTSTRAP_PASSWORD o informe -Password para ejecutar la prueba."
}

function Assert-True {
  param([bool]$Condition, [string]$Message)
  if (-not $Condition) { throw $Message }
}

function Invoke-ConfigurationJson {
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
    [ValidateSet("GET", "POST", "PUT", "DELETE")][string]$Method,
    [string]$Path,
    [object]$Body,
    [int]$ExpectedStatus,
    [string]$ExpectedCode = ""
  )
  try {
    Invoke-ConfigurationJson -Method $Method -Path $Path -Body $Body | Out-Null
    throw "La solicitud debía fallar con estado $ExpectedStatus."
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
    if (-not [string]::IsNullOrWhiteSpace($ExpectedCode)) {
      $payload = $detail | ConvertFrom-Json
      Assert-True ($payload.code -eq $ExpectedCode) "Código de error inesperado: $detail"
    }
  }
}

$script:Session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$login = Invoke-ConfigurationJson -Method POST -Path "/api/auth/login" -Body @{
  username = $Username
  password = $Password
}
Assert-True ($login.authenticated -eq $true) "No se pudo iniciar sesión."

$defaults = Invoke-ConfigurationJson -Path "/api/clinical/configuration/day-hospital-settings"
Assert-True ($defaults.ok -eq $true -and $defaults.total -eq 1) "No se obtuvo la configuración predeterminada."
$default = $defaults.items[0]
Assert-True ([string]$default.id -eq "" -and [int]$default.revision -eq 0) "El valor predeterminado aparenta estar persistido."
Assert-True ([int]$default.definition.slotMinutes -eq 10) "El intervalo predeterminado cambió."
Assert-True ($null -eq $default.PSObject.Properties["createdAt"]) "El contrato predeterminado agregó createdAt."
Assert-True ($null -eq $default.PSObject.Properties["itemKind"]) "El contrato predeterminado agregó itemKind."

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$key = "contract-calculator-$suffix"
$created = Invoke-ConfigurationJson -Method POST -Path "/api/clinical/configuration/calculator" -Body @{
  key = $key
  name = "Calculadora contrato $suffix"
  description = "Verificacion hexagonal"
  active = $true
  expression = "peso / talla"
  variables = @("peso", "talla")
}
Assert-True ($created.ok -eq $true) "No se creó la configuración."
$item = $created.item
Assert-True ([int]$item.revision -eq 1) "La creación no comenzó en revisión 1."
Assert-True ($item.definition.expression -eq "peso / talla") "La definición dinámica no se conservó."
Assert-True ($null -eq $item.definition.PSObject.Properties["name"]) "Los metadatos se mezclaron dentro de la definición."

$updated = Invoke-ConfigurationJson -Method PUT -Path "/api/clinical/configuration/calculator/$($item.id)" -Body @{
  revision = $item.revision
  name = "Calculadora contrato actualizada $suffix"
}
Assert-True ([int]$updated.item.revision -eq 2) "La actualización no incrementó la revisión."
Assert-True ($updated.item.description -eq "Verificacion hexagonal") "Se perdió un campo omitido en la actualización."
Assert-True ($updated.item.definition.expression -eq "peso / talla") "Se perdió la definición al actualizar."

Invoke-ExpectedError `
  -Method PUT `
  -Path "/api/clinical/configuration/calculator/$($item.id)" `
  -Body @{ revision = $item.revision; name = "Revisión obsoleta" } `
  -ExpectedStatus 409 `
  -ExpectedCode "VERSION_CONFLICT"

$versions = Invoke-ConfigurationJson -Path "/api/clinical/configuration/calculator/$($item.id)/versions"
Assert-True ($versions.total -eq 2) "El historial no contiene creación y actualización."
Assert-True ([int]$versions.versions[0].revision -eq 2) "El historial no está ordenado desde la última revisión."

$archived = Invoke-ConfigurationJson -Method DELETE -Path "/api/clinical/configuration/calculator/$($item.id)"
Assert-True ($archived.item.active -eq $false) "La configuración no quedó archivada."
Assert-True ([int]$archived.item.revision -eq 3) "El archivado no generó una revisión."

$active = Invoke-ConfigurationJson -Path "/api/clinical/configuration/calculator"
Assert-True (@($active.items | Where-Object { $_.id -eq $item.id }).Count -eq 0) "El elemento archivado aparece como activo."
$all = Invoke-ConfigurationJson -Path "/api/clinical/configuration/calculator?includeInactive=1"
Assert-True (@($all.items | Where-Object { $_.id -eq $item.id }).Count -eq 1) "El histórico no permite recuperar el elemento archivado."

Invoke-ExpectedError `
  -Method GET `
  -Path "/api/clinical/configuration/desconocida" `
  -Body $null `
  -ExpectedStatus 404

[ordered]@{
  ok = $true
  configurationId = [string]$item.id
  key = $key
  finalRevision = [int]$archived.item.revision
  versions = [int]$versions.total + 1
  archived = $true
} | ConvertTo-Json -Compress
