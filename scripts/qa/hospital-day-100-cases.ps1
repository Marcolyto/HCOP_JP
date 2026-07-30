param(
  [string]$BaseUrl = "http://127.0.0.1:5181",
  [System.Management.Automation.PSCredential]$Credential,
  [string]$Username = "",
  [string]$Password = "",
  [string]$OutputDirectory = "",
  [switch]$AllowAlternateQaPort,
  [switch]$AllowRemoteQa,
  [switch]$NoFailExit
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Assert-QaTarget {
  param([string]$Url)
  $parsed = $null
  if (-not [uri]::TryCreate($Url, [System.UriKind]::Absolute, [ref]$parsed)) {
    throw "BaseUrl no es una URL absoluta valida: $Url"
  }
  if ($parsed.Scheme -notin @("http", "https")) { throw "BaseUrl debe usar http o https." }
  if ($parsed.Port -eq 5180) {
    throw "ABORTADO POR SEGURIDAD: el puerto 5180 es la instancia principal. Este arnes solo puede consultar QA."
  }
  if ($parsed.Port -ne 5181 -and -not $AllowAlternateQaPort) {
    throw "ABORTADO POR SEGURIDAD: use el puerto QA 5181 o confirme otro puerto con -AllowAlternateQaPort."
  }
  if ($parsed.Host -notin @("127.0.0.1", "localhost", "::1") -and -not $AllowRemoteQa) {
    throw "ABORTADO POR SEGURIDAD: el destino no es local. Use -AllowRemoteQa solo para una instancia QA conocida."
  }
}

function Get-Property {
  param([object]$Value, [string]$Name)
  if ($null -eq $Value) { return $null }
  $property = $Value.PSObject.Properties[$Name]
  if ($null -eq $property) { return $null }
  return $property.Value
}

function Require-True {
  param([bool]$Condition, [string]$Message)
  if (-not $Condition) { throw $Message }
}

function Join-QaUrl {
  param([string]$Path)
  return "$($script:QaBaseUrl)$Path"
}

function Invoke-QaJson {
  param(
    [ValidateSet("GET", "POST")][string]$Method = "GET",
    [Parameter(Mandatory = $true)][string]$Path,
    [object]$Body = $null
  )
  if ($Method -ne "GET" -and $Path -ne "/api/auth/login") {
    throw "El arnes no permite mutaciones. Metodo rechazado: $Method $Path"
  }
  $parameters = @{
    Uri = Join-QaUrl $Path
    Method = $Method
    WebSession = $script:WebSession
    Headers = @{ Accept = "application/json" }
    TimeoutSec = 30
  }
  if ($null -ne $Body) {
    $parameters.ContentType = "application/json; charset=utf-8"
    $json = $Body | ConvertTo-Json -Depth 20 -Compress
    $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
  }
  return Invoke-RestMethod @parameters
}

function Get-QaText {
  param([Parameter(Mandatory = $true)][string]$Path)
  if (-not $script:AssetCache.ContainsKey($Path)) {
    $response = Invoke-WebRequest -UseBasicParsing -Uri (Join-QaUrl $Path) `
      -WebSession $script:WebSession -TimeoutSec 30
    $script:AssetCache[$Path] = [string]$response.Content
  }
  return [string]$script:AssetCache[$Path]
}

function Get-QaLocalTestText {
  param([Parameter(Mandatory = $true)][string]$Path)
  $projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
  $relativePath = $Path.TrimStart("/", "\").Replace("/", [System.IO.Path]::DirectorySeparatorChar)
  $resolved = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $relativePath))
  if (-not $resolved.StartsWith(
      $projectRoot + [System.IO.Path]::DirectorySeparatorChar,
      [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "La evidencia de prueba debe permanecer dentro del proyecto."
  }
  if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
    throw "No existe la evidencia de prueba local: $Path"
  }
  return [System.IO.File]::ReadAllText($resolved)
}

function Test-ObjectProperties {
  param([object]$Value, [string[]]$Names)
  foreach ($name in $Names) {
    if ($null -eq $Value.PSObject.Properties[$name]) { throw "Falta la propiedad '$name'." }
  }
}

function Test-ContainsAll {
  param([string]$Text, [string[]]$Patterns)
  $missing = @()
  foreach ($pattern in $Patterns) {
    if ($Text.IndexOf($pattern, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) {
      $missing += $pattern
    }
  }
  if ($missing.Count -gt 0) { throw "No se encontraron: $($missing -join ', ')" }
}

function Get-WorkflowIdentity {
  param([object]$Item)
  return @(
    [string](Get-Property $Item "patientId"),
    [string](Get-Property $Item "treatmentId"),
    [string](Get-Property $Item "cycleNumber"),
    [string](Get-Property $Item "applicationDay")
  ) -join "|"
}

function Get-QueueResult {
  param([string]$Path)
  $payload = Invoke-QaJson -Path $Path
  Test-ObjectProperties $payload @("ok", "items", "total")
  Require-True ([bool](Get-Property $payload "ok")) "La cola respondio ok=false."
  return $payload
}

function Get-ProbeValue {
  param([object]$Item, [string]$ProbeField)
  switch ($ProbeField) {
    "patientName" { return [string](Get-Property $Item "patientName") }
    "patientDni" { return [string](Get-Property $Item "patientDni") }
    "medicalRecord" { return [string](Get-Property $Item "medicalRecord") }
    "scheme" { return [string](Get-Property $Item "scheme") }
    "diagnosis" { return [string](Get-Property $Item "diagnosis") }
    "drugScheme" { return [string](Get-Property $Item "drugScheme") }
    "cycleDay" {
      return "ciclo $([int](Get-Property $Item 'cycleNumber')) dia $([int](Get-Property $Item 'applicationDay'))"
    }
    "plannedDateIso" { return [string](Get-Property $Item "plannedDate") }
    "plannedDateLocal" {
      $raw = [string](Get-Property $Item "plannedDate")
      $date = [datetime]::MinValue
      if ([datetime]::TryParse($raw, [ref]$date)) { return $date.ToString("dd/MM/yyyy") }
      return ""
    }
    default { throw "ProbeField desconocido: $ProbeField" }
  }
}

function Test-QueueProbe {
  param([string]$Queue, [string]$ProbeField)
  $baseline = Get-QueueResult "/api/clinical/application-workflows?queue=$Queue"
  $rows = @($baseline.items)
  if ($rows.Count -eq 0) {
    return @{ Status = "NO_DATA"; Evidence = "La cola respondio correctamente, pero no hay aplicaciones QA para comprobar coincidencias." }
  }
  $item = $rows | Where-Object {
    -not [string]::IsNullOrWhiteSpace((Get-ProbeValue $_ $ProbeField))
  } | Select-Object -First 1
  if ($null -eq $item) {
    return @{ Status = "NO_DATA"; Evidence = "Hay aplicaciones QA, pero ninguna tiene valor util en $ProbeField." }
  }
  $probe = Get-ProbeValue $item $ProbeField
  $filtered = Get-QueueResult "/api/clinical/application-workflows?queue=$Queue&q=$([uri]::EscapeDataString($probe))"
  $identity = Get-WorkflowIdentity $item
  # Preserve the collection wrapper when a filter returns exactly one row.
  # Windows PowerShell 5.1 otherwise exposes a scalar without a reliable Count.
  $found = @(@($filtered.items) | Where-Object { (Get-WorkflowIdentity $_) -eq $identity })
  Require-True ($found.Count -gt 0) "La busqueda por '$probe' no devolvio la aplicacion de origen."
  return @{ Status = "PASS"; Evidence = "Consulta real por '$probe': $(@($filtered.items).Count) coincidencia(s)." }
}

function Test-CandidateProbe {
  $baseline = Invoke-QaJson -Path "/api/clinical/infusion-candidates?includeScheduled=false&onlySchedulingEligible=false"
  Test-ObjectProperties $baseline @("ok", "candidates", "total")
  $rows = @($baseline.candidates)
  if ($rows.Count -eq 0) {
    return @{ Status = "NO_DATA"; Evidence = "El endpoint respondio correctamente, pero no hay candidatos QA." }
  }
  $item = $rows | Where-Object {
    -not [string]::IsNullOrWhiteSpace([string](Get-Property $_ "patientName"))
  } | Select-Object -First 1
  if ($null -eq $item) {
    return @{ Status = "NO_DATA"; Evidence = "Los candidatos QA no contienen un nombre util para buscar." }
  }
  $probe = [string](Get-Property $item "patientName")
  $filtered = Invoke-QaJson -Path "/api/clinical/infusion-candidates?q=$([uri]::EscapeDataString($probe))&includeScheduled=false&onlySchedulingEligible=false"
  Test-ObjectProperties $filtered @("ok", "candidates", "total")
  Require-True (@($filtered.candidates).Count -gt 0) "La busqueda del turnero no encontro '$probe'."
  return @{ Status = "PASS"; Evidence = "Busqueda real de candidato por '$probe': $(@($filtered.candidates).Count) resultado(s)." }
}

function Test-QueueOrder {
  param([string]$Queue)
  $today = Get-Date -Format "yyyy-MM-dd"
  $payload = Get-QueueResult "/api/clinical/application-workflows?queue=$Queue&date=$today"
  $dates = @($payload.items) | ForEach-Object {
    $appointment = Get-Property $_ "appointment"
    [string](Get-Property $appointment "scheduledAt")
  } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
  if ($dates.Count -lt 2) {
    return @{ Status = "NO_DATA"; Evidence = "La cola de hoy tiene menos de dos turnos; no alcanza para demostrar el orden." }
  }
  $expected = @($dates | Sort-Object)
  Require-True (($dates -join "|") -eq ($expected -join "|")) "La cola no esta ordenada cronologicamente."
  return @{ Status = "PASS"; Evidence = "$($dates.Count) turnos verificados en orden ascendente." }
}

function Test-PharmacyLargeQueueSearch {
  $baselineWatch = [System.Diagnostics.Stopwatch]::StartNew()
  $baseline = Get-QueueResult "/api/clinical/application-workflows?queue=pharmacy"
  $baselineWatch.Stop()
  $rows = @($baseline.items)
  if ($rows.Count -lt 2000) {
    return @{
      Status = "NO_DATA"
      Evidence = "La cola contiene $($rows.Count) filas; FAR-24 requiere la semilla sintetica de 2000."
    }
  }
  Require-True ($baselineWatch.ElapsedMilliseconds -lt 10000) `
    "La cola de 2000 filas demoro $($baselineWatch.ElapsedMilliseconds) ms."

  $target = $rows |
    Where-Object {
      -not [string]::IsNullOrWhiteSpace([string](Get-Property $_ "patientName")) -and
      -not [string]::IsNullOrWhiteSpace([string](Get-Property $_ "patientDni")) -and
      -not [string]::IsNullOrWhiteSpace([string](Get-Property $_ "medicalRecord")) -and
      -not [string]::IsNullOrWhiteSpace([string](Get-Property $_ "plannedDate"))
    } |
    Select-Object -Last 1
  Require-True ($null -ne $target) "No hay una fila completa para medir las cinco busquedas."
  $identity = Get-WorkflowIdentity $target
  $measurements = @()
  foreach ($probeField in @(
    "patientName", "patientDni", "medicalRecord", "cycleDay", "plannedDateLocal"
  )) {
    $probe = Get-ProbeValue $target $probeField
    Require-True (-not [string]::IsNullOrWhiteSpace($probe)) `
      "La fila FAR-24 no tiene valor para $probeField."
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $filtered = Get-QueueResult `
      "/api/clinical/application-workflows?queue=pharmacy&q=$([uri]::EscapeDataString($probe))"
    $watch.Stop()
    $found = @(@($filtered.items) | Where-Object {
      (Get-WorkflowIdentity $_) -eq $identity
    })
    Require-True ($found.Count -gt 0) `
      "La busqueda FAR-24 por $probeField ('$probe') no encontro la fila objetivo."
    Require-True ($watch.ElapsedMilliseconds -lt 10000) `
      "La busqueda FAR-24 por $probeField demoro $($watch.ElapsedMilliseconds) ms."
    $measurements += "$probeField=$($watch.ElapsedMilliseconds)ms"
  }
  return @{
    Status = "PASS"
    Evidence = "2000 filas; carga=$($baselineWatch.ElapsedMilliseconds)ms; $($measurements -join ', ')."
  }
}

function Test-OpenApiOperation {
  param([string]$Path, [string]$Method)
  $pathProperty = $script:OpenApi.paths.PSObject.Properties[$Path]
  Require-True ($null -ne $pathProperty) "Swagger no documenta $Path."
  $operation = $pathProperty.Value.PSObject.Properties[$Method.ToLowerInvariant()]
  Require-True ($null -ne $operation) "Swagger no documenta $Method $Path."
  return "Swagger contiene $($Method.ToUpperInvariant()) $Path."
}

function Add-QaCase {
  param(
    [string]$Id,
    [ValidateSet("Farmacia", "Enfermeria", "Oncologia", "Turnos")][string]$Role,
    [ValidateSet("REAL", "CONTRACT", "MANUAL")][string]$Mode,
    [string]$Title,
    [string]$Expected,
    [string]$Check,
    [string]$Path = "",
    [string]$ProbeField = "",
    [string[]]$Patterns = @(),
    [string]$ManualSteps = ""
  )
  $script:Cases.Add([pscustomobject]@{
    Id = $Id; Role = $Role; Mode = $Mode; Title = $Title; Expected = $Expected
    Check = $Check; Path = $Path; ProbeField = $ProbeField
    Patterns = @($Patterns); ManualSteps = $ManualSteps
  })
}

function Invoke-QaCase {
  param([object]$Case)
  $started = Get-Date
  $status = "PASS"
  $evidence = ""
  try {
    switch ($Case.Check) {
      "queue" {
        $payload = Get-QueueResult $Case.Path
        $evidence = "HTTP real: $([int]$payload.total) fila(s), contrato ok/items/total valido."
      }
      "queue-shape" {
        $payload = Get-QueueResult $Case.Path
        $rows = @($payload.items)
        if ($rows.Count -eq 0) {
          $status = "NO_DATA"; $evidence = "La cola responde, pero no contiene filas QA para validar campos."
        } else {
          Test-ObjectProperties $rows[0] $Case.Patterns
          $evidence = "Primera fila contiene: $($Case.Patterns -join ', ')."
        }
      }
      "queue-probe" {
        $outcome = Test-QueueProbe $Case.Path $Case.ProbeField
        $status = [string]$outcome.Status; $evidence = [string]$outcome.Evidence
      }
      "queue-order" {
        $outcome = Test-QueueOrder $Case.Path
        $status = [string]$outcome.Status; $evidence = [string]$outcome.Evidence
      }
      "candidates" {
        $payload = Invoke-QaJson -Path $Case.Path
        Test-ObjectProperties $payload @("ok", "candidates", "total")
        Require-True ([bool]$payload.ok) "Candidatos respondio ok=false."
        $evidence = "HTTP real: $([int]$payload.total) candidato(s), contrato ok/candidates/total valido."
      }
      "candidate-probe" {
        $outcome = Test-CandidateProbe
        $status = [string]$outcome.Status; $evidence = [string]$outcome.Evidence
      }
      "pharmacy-load-search" {
        $outcome = Test-PharmacyLargeQueueSearch
        $status = [string]$outcome.Status; $evidence = [string]$outcome.Evidence
      }
      "json-shape" {
        $payload = Invoke-QaJson -Path $Case.Path
        Test-ObjectProperties $payload $Case.Patterns
        $evidence = "HTTP real con propiedades: $($Case.Patterns -join ', ')."
      }
      "static" {
        $text = Get-QaText $Case.Path
        Test-ContainsAll $text $Case.Patterns
        $evidence = "Contrato estatico encontrado en $($Case.Path): $($Case.Patterns -join ', ')."
      }
      "test-source" {
        $text = Get-QaLocalTestText $Case.Path
        Test-ContainsAll $text $Case.Patterns
        $evidence = "Prueba automatizada presente en $($Case.Path): $($Case.Patterns -join ', ')."
      }
      "openapi" {
        $parts = $Case.Path.Split("|", 2)
        $evidence = Test-OpenApiOperation $parts[0] $parts[1]
      }
      "manual" { $status = "MANUAL"; $evidence = $Case.ManualSteps }
      default { throw "Tipo de comprobacion desconocido: $($Case.Check)" }
    }
  } catch {
    $status = "FAIL"; $evidence = $_.Exception.Message
  }
  return [pscustomobject]@{
    id = $Case.Id; role = $Case.Role; mode = $Case.Mode; status = $status
    title = $Case.Title; expected = $Case.Expected; evidence = $evidence
    elapsedMs = [math]::Round(((Get-Date) - $started).TotalMilliseconds)
  }
}

function Escape-MarkdownCell {
  param([object]$Value)
  return ([string]$Value).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Write-QaReports {
  param([object[]]$Results, [string]$Directory)
  if ([string]::IsNullOrWhiteSpace($Directory)) {
    $Directory = Join-Path $PSScriptRoot "..\..\docs\08-auditoria\resultados"
  }
  $resolved = [System.IO.Path]::GetFullPath($Directory)
  [System.IO.Directory]::CreateDirectory($resolved) | Out-Null
  $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $jsonPath = Join-Path $resolved "hospital-dia-100-casos-$stamp.json"
  $markdownPath = Join-Path $resolved "hospital-dia-100-casos-$stamp.md"
  $summary = [ordered]@{
    total = $Results.Count
    pass = @($Results | Where-Object status -eq "PASS").Count
    fail = @($Results | Where-Object status -eq "FAIL").Count
    noData = @($Results | Where-Object status -eq "NO_DATA").Count
    manual = @($Results | Where-Object status -eq "MANUAL").Count
  }
  $roleSummary = @()
  foreach ($role in @("Farmacia", "Enfermeria", "Oncologia", "Turnos")) {
    $rows = @($Results | Where-Object role -eq $role)
    $roleSummary += [ordered]@{
      role = $role; total = $rows.Count
      pass = @($rows | Where-Object status -eq "PASS").Count
      fail = @($rows | Where-Object status -eq "FAIL").Count
      noData = @($rows | Where-Object status -eq "NO_DATA").Count
      manual = @($rows | Where-Object status -eq "MANUAL").Count
    }
  }
  $report = [ordered]@{
    generatedAt = (Get-Date).ToString("o"); baseUrl = $script:QaBaseUrl
    safety = [ordered]@{
      readOnly = $true; forbiddenPort = 5180; defaultQaPort = 5181
      onlyNonGetRequest = "POST /api/auth/login"
    }
    summary = $summary; roles = $roleSummary; results = $Results
  }
  $utf8 = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($jsonPath, ($report | ConvertTo-Json -Depth 20), $utf8)
  # Windows PowerShell 5.1 has an argument binder bug with generic List[T].
  # A native string array avoids it both here and in File.WriteAllLines.
  $lines = @()
  $lines += "# Auditoria Hospital de dia - 100 casos"
  $lines += ""
  $lines += "- Fecha: $($report.generatedAt)"
  $lines += "- Instancia: ``$($script:QaBaseUrl)``"
  $lines += "- Seguridad: solo lectura; el unico POST es el inicio de sesion."
  $lines += "- Total evaluado: **$($summary.total)**"
  $lines += "- PASS: **$($summary.pass)** - FAIL: **$($summary.fail)** - SIN DATOS: **$($summary.noData)** - MANUAL: **$($summary.manual)**"
  $lines += ""
  $lines += "## Resumen por rol"
  $lines += ""
  $lines += "| Rol | Total | PASS | FAIL | Sin datos | Manual |"
  $lines += "|---|---:|---:|---:|---:|---:|"
  foreach ($row in $roleSummary) {
    $lines += "| $($row.role) | $($row.total) | $($row.pass) | $($row.fail) | $($row.noData) | $($row.manual) |"
  }
  $lines += ""
  $lines += "## Resultado detallado"
  $lines += ""
  $lines += "| ID | Rol | Modo | Estado | Caso | Evidencia |"
  $lines += "|---|---|---|---|---|---|"
  foreach ($row in $Results) {
    $lines += "| $(Escape-MarkdownCell $row.id) | $(Escape-MarkdownCell $row.role) | $(Escape-MarkdownCell $row.mode) | $(Escape-MarkdownCell $row.status) | $(Escape-MarkdownCell $row.title) | $(Escape-MarkdownCell $row.evidence) |"
  }
  $lines += ""
  $lines += "## Interpretacion"
  $lines += ""
  $lines += "- **REAL** consulta la aplicacion QA en ejecucion sin cambiar datos."
  $lines += "- **CONTRACT** comprueba que la interfaz o Swagger expongan el control esperado."
  $lines += "- **MANUAL** exige interaccion humana segura; nunca se informa como aprobado automaticamente."
  $lines += "- **NO_DATA** indica que el contrato respondio, pero falta semilla QA para demostrar el comportamiento con filas reales."
  [System.IO.File]::WriteAllLines($markdownPath, [string[]]$lines, $utf8)
  return [pscustomobject]@{ Json = $jsonPath; Markdown = $markdownPath; Summary = $summary }
}

Assert-QaTarget $BaseUrl
$script:QaBaseUrl = $BaseUrl.TrimEnd("/")
$script:WebSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$script:AssetCache = @{}
$script:Cases = New-Object System.Collections.Generic.List[object]
if ($null -eq $Credential) {
  if (-not [string]::IsNullOrWhiteSpace($Username) -and
      -not [string]::IsNullOrWhiteSpace($Password)) {
    $securePassword = ConvertTo-SecureString $Password -AsPlainText -Force
    $Credential = New-Object System.Management.Automation.PSCredential($Username, $securePassword)
  } else {
    $Credential = Get-Credential -UserName "marcolyto" -Message "Credenciales de la instancia QA HCOP JP"
  }
}
Require-True ($null -ne $Credential) "Se requieren credenciales de QA."

$health = Invoke-RestMethod -Uri (Join-QaUrl "/actuator/health") -TimeoutSec 20
Require-True ($health.status -eq "UP") "La instancia QA no esta saludable."
$runtime = Invoke-RestMethod -Uri (Join-QaUrl "/api/runtime/status") -TimeoutSec 20
Require-True ($runtime.engine -eq "java-postgresql") "La instancia no informa Java + PostgreSQL."
$login = Invoke-QaJson -Method POST -Path "/api/auth/login" -Body @{
  username = $Credential.UserName
  password = $Credential.GetNetworkCredential().Password
}
Require-True ([bool]$login.authenticated) "No se pudo iniciar sesion en QA."
$script:OpenApi = Invoke-QaJson -Path "/v3/api-docs"

# FARMACIA: 25 casos
Add-QaCase "FAR-01" "Farmacia" "REAL" "Abrir cola completa de Farmacia" "Respuesta ok/items/total sin error 500." "queue" "/api/clinical/application-workflows?queue=pharmacy"
Add-QaCase "FAR-02" "Farmacia" "REAL" "Buscar por nombre de paciente" "La fila de origen aparece al buscar su nombre." "queue-probe" "pharmacy" "patientName"
Add-QaCase "FAR-03" "Farmacia" "REAL" "Buscar por DNI" "La fila de origen aparece al buscar su DNI." "queue-probe" "pharmacy" "patientDni"
Add-QaCase "FAR-04" "Farmacia" "REAL" "Buscar por historia clinica" "La fila de origen aparece al buscar su HC." "queue-probe" "pharmacy" "medicalRecord"
Add-QaCase "FAR-05" "Farmacia" "REAL" "Buscar por esquema" "La fila de origen aparece al buscar su esquema." "queue-probe" "pharmacy" "scheme"
Add-QaCase "FAR-06" "Farmacia" "REAL" "Buscar por diagnostico" "La fila de origen aparece al buscar su diagnostico." "queue-probe" "pharmacy" "diagnosis"
Add-QaCase "FAR-07" "Farmacia" "REAL" "Buscar por droga" "La fila de origen aparece al buscar su droga o resumen de drogas." "queue-probe" "pharmacy" "drugScheme"
Add-QaCase "FAR-08" "Farmacia" "REAL" "Buscar por ciclo y dia" "La consulta ciclo N dia N ubica la aplicacion." "queue-probe" "pharmacy" "cycleDay"
Add-QaCase "FAR-09" "Farmacia" "REAL" "Buscar por fecha ISO" "La fecha yyyy-mm-dd ubica la aplicacion." "queue-probe" "pharmacy" "plannedDateIso"
Add-QaCase "FAR-10" "Farmacia" "REAL" "Buscar por fecha local" "La fecha dd/mm/aaaa ubica la aplicacion." "queue-probe" "pharmacy" "plannedDateLocal"
Add-QaCase "FAR-11" "Farmacia" "REAL" "Filtrar quien debe traer medicacion" "El filtro patient_to_bring responde como cola valida." "queue" "/api/clinical/application-workflows?queue=pharmacy&medicationSource=patient_to_bring"
Add-QaCase "FAR-12" "Farmacia" "CONTRACT" "Prioridad temporal visible" "Existen Hoy, vencidas +7, vencidas +30 y todas." "static" "/index.html" "" @("careSchedulePharmacyDateScope", 'value="today"', 'value="next-7"', 'value="next-30"', 'value="all"')
Add-QaCase "FAR-13" "Farmacia" "CONTRACT" "Filtros de estado completos" "Se distinguen pendiente, rechazada, paciente, centro y reserva." "static" "/index.html" "" @("pending-validation", 'value="rejected"', 'value="patient"', 'value="patient-has"', 'value="received-center"', 'value="pending-stock"', 'value="reserved"')
Add-QaCase "FAR-14" "Farmacia" "CONTRACT" "Listado agrupado por fecha" "La tabla inserta cabeceras por fecha con cantidad." "static" "/app.js" "" @("carePharmacyGroupLabel", "care-pharmacy-date-group", "groupCounts")
Add-QaCase "FAR-15" "Farmacia" "CONTRACT" "Rechazar una orden inicialmente pendiente" "El modal ofrece Rechazar orden antes de aprobar." "static" "/app.js" "" @("Rechazar orden", 'data-validated="false"', "pharmacyActionReason")
Add-QaCase "FAR-16" "Farmacia" "CONTRACT" "Validar o revalidar la orden" "El modal expone validacion y revalidacion." "static" "/app.js" "" @("Validar orden", "Revalidar orden", 'data-validated="true"')
Add-QaCase "FAR-17" "Farmacia" "CONTRACT" "Procedencias de medicacion no ambiguas" "Se distinguen stock, debe traer, la tiene, recibida y proveedor." "static" "/app.js" "" @("center_stock", "patient_to_bring", "patient_has_medication", "received_center", "pending_supplier")
Add-QaCase "FAR-18" "Farmacia" "CONTRACT" "Reserva y liberacion de stock documentadas" "Swagger expone el comando de reserva/liberacion." "openapi" "/api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/stock-reservation|post"
Add-QaCase "FAR-19" "Farmacia" "REAL" "Fila con datos farmaceuticos esenciales" "La cola informa drogas, procedencia, validacion, reserva y fecha." "queue-shape" "/api/clinical/application-workflows?queue=pharmacy" "" @("applicationDrugs", "medicationSource", "pharmacyValidationStatus", "stockReservationStatus", "plannedDate")
Add-QaCase "FAR-20" "Farmacia" "CONTRACT" "Alerta de aprobacion heredada sin traza" "Una validacion migrada sin actor/fecha se marca para revision." "static" "/app.js" "" @("pharmacyValidationTraceable", "is-untraceable", "sin traza")
Add-QaCase "FAR-21" "Farmacia" "CONTRACT" "Historial auditable de la aplicacion" "El modal representa auditTrail con actor y revision." "static" "/app.js" "" @("careApplicationWorkflowAuditMarkup", "auditTrail", "resultingRevision")
Add-QaCase "FAR-22" "Farmacia" "CONTRACT" "Carga incremental para listados extensos" "La interfaz limita y permite cargar 250 filas adicionales." "static" "/app.js" "" @("careSchedulePharmacyVisibleLimit", "+= 250", "data-care-pharmacy-load-more")
Add-QaCase "FAR-23" "Farmacia" "CONTRACT" "QR por aplicacion disponible en Farmacia" "Cada fila habilitada puede abrir el QR del ciclo y dia." "static" "/app.js" "" @("care-pharmacy-qr", "documents/qr?cycle=", "applicationDay=")
Add-QaCase "FAR-24" "Farmacia" "REAL" "Encontrar un paciente en una lista de 2000 filas" "Nombre, DNI, HC, ciclo/dia y fecha deben resolverse en menos de 10 segundos." "pharmacy-load-search"
Add-QaCase "FAR-25" "Farmacia" "CONTRACT" "Dos farmaceuticos intentan reservar el mismo stock" "Solo una reserva se consolida; el segundo intento no puede sobre-reservar y la aplicacion se serializa por revision." "test-source" "/src/test/java/ar/com/hexium/hcop/infusion/HospitalDayConcurrencySafetyTest.java" "" @("far25TwoPharmacistsCannotOverReserveTheSameInventoryLot", "far25ApplicationLockSerializesTwoPharmacistsBeforeCheckingRevision", "containsExactlyInAnyOrder(true, false)", "FOR UPDATE OF w")

# ENFERMERIA: 25 casos
Add-QaCase "ENF-01" "Enfermeria" "REAL" "Abrir cola de triaje de hoy" "Respuesta ok/items/total filtrada por la fecha operativa." "queue" "/api/clinical/application-workflows?queue=triage&date=$((Get-Date).ToString('yyyy-MM-dd'))"
Add-QaCase "ENF-02" "Enfermeria" "REAL" "Buscar paciente en triaje" "Nombre de una fila real vuelve a encontrar la aplicacion." "queue-probe" "triage" "patientName"
Add-QaCase "ENF-03" "Enfermeria" "REAL" "Orden cronologico de triaje" "Los pacientes de hoy aparecen por hora de turno." "queue-order" "triage"
Add-QaCase "ENF-04" "Enfermeria" "REAL" "Abrir cola de preparacion" "Respuesta valida para el trabajo esteril." "queue" "/api/clinical/application-workflows?queue=preparation&date=$((Get-Date).ToString('yyyy-MM-dd'))"
Add-QaCase "ENF-05" "Enfermeria" "REAL" "Abrir cola de administracion" "Respuesta valida para sala de hoy." "queue" "/api/clinical/application-workflows?queue=administration&date=$((Get-Date).ToString('yyyy-MM-dd'))"
Add-QaCase "ENF-06" "Enfermeria" "CONTRACT" "Buscador de sala" "Sala permite buscar por paciente, DNI, esquema o sillon." "static" "/index.html" "" @("careRoomSearch", "Buscar aplicaciones en sala")
Add-QaCase "ENF-07" "Enfermeria" "CONTRACT" "Buscador de triaje" "Triaje permite buscar por paciente, DNI, esquema o sillon." "static" "/index.html" "" @("careTriageSearch", "Buscar pacientes para triaje")
Add-QaCase "ENF-08" "Enfermeria" "CONTRACT" "Filtro de triaje" "Se distinguen todos, pendientes, aptos y postergados." "static" "/index.html" "" @("careTriageFilter", 'value="pending"', 'value="pass"', 'value="fail"')
Add-QaCase "ENF-09" "Enfermeria" "REAL" "Turno listo para operar hoy" "La fila informa turno, confirmacion y evaluacion clinica." "queue-shape" "/api/clinical/application-workflows?queue=triage&date=$((Get-Date).ToString('yyyy-MM-dd'))" "" @("appointment", "clinicalAuthorizationStatus", "clinicalAssessment")
Add-QaCase "ENF-10" "Enfermeria" "CONTRACT" "Laboratorio pretratamiento" "Fecha, neutrofilos, plaquetas, creatinina y funcion hepatica estan disponibles." "static" "/app.js" "" @('name="labDate"', 'name="neutrophils"', 'name="platelets"', 'name="creatinine"', 'name="hepaticFunction"')
Add-QaCase "ENF-11" "Enfermeria" "CONTRACT" "Signos vitales obligatorios" "Peso, presion y temperatura forman parte del PASS." "static" "/app.js" "" @('name="weightKg"', 'name="bloodPressure"', 'name="temperatureC"', "data-required-for-pass")
Add-QaCase "ENF-12" "Enfermeria" "CONTRACT" "Frecuencia cardiaca visible" "El formulario y el payload incluyen heartRate." "static" "/app.js" "" @('name="heartRate"', 'heartRate: careApplicationFormNumber("heartRate")')
Add-QaCase "ENF-13" "Enfermeria" "CONTRACT" "Saturacion visible" "El formulario y el payload incluyen oxygenSaturation." "static" "/app.js" "" @('name="oxygenSaturation"', 'oxygenSaturation: careApplicationFormNumber("oxygenSaturation")')
Add-QaCase "ENF-14" "Enfermeria" "CONTRACT" "Toxicidad y ECOG" "Se registran ECOG 0-4 y toxicidad 0-5." "static" "/app.js" "" @('name="ecog"', 'name="toxicityGrade"', "[0,1,2,3,4,5]")
Add-QaCase "ENF-15" "Enfermeria" "CONTRACT" "Alertas clinicas de seguridad" "Se advierten neutropenia, plaquetopenia, fiebre, hipoxemia y toxicidad." "static" "/app.js" "" @("neutrophils < 1000", "platelets < 75000", "temperature >= 38", "saturation < 92", "toxicity >= 3")
Add-QaCase "ENF-16" "Enfermeria" "CONTRACT" "Override clinico documentado" "Un PASS con alerta exige justificacion de al menos 10 caracteres." "static" "/app.js" "" @("clinicalOverrideReason", "override.required = alerts.length > 0", "length < 10")
Add-QaCase "ENF-17" "Enfermeria" "CONTRACT" "FAIL con motivo y nueva fecha" "La postergacion registra motivo y fecha propuesta." "static" "/app.js" "" @('name="failReason"', 'name="rescheduledDate"', 'data-decision="FAIL"')
Add-QaCase "ENF-18" "Enfermeria" "CONTRACT" "Revocar un PASS antes de preparar" "Existe accion explicita Revocar PASS y postergar." "static" "/app.js" "" @("Revocar PASS y postergar", 'data-revoking-pass="true"', "window.confirm")
Add-QaCase "ENF-19" "Enfermeria" "CONTRACT" "Trazabilidad de cada mezcla" "Lote, vencimiento, cantidad, diluyente, volumen, concentracion y TTL son obligatorios." "static" "/app.js" "" @('name="lot"', 'name="expiryDate"', 'name="quantity"', 'name="diluent"', 'name="finalVolume"', 'name="concentration"', 'name="ttlMinutes"')
Add-QaCase "ENF-20" "Enfermeria" "CONTRACT" "Segundo control de preparacion" "Se declara otro profesional habilitado y la interfaz aclara que la seleccion no reemplaza una cofirma." "static" "/app.js" "" @('name="verifiedBy"', "Segundo profesional que controló", "no reemplaza la cofirma")
Add-QaCase "ENF-21" "Enfermeria" "CONTRACT" "Mezcla vencida y reinicio" "Swagger expone el descarte/reinicio sin borrar trazabilidad." "openapi" "/api/clinical/application-workflows/{patientId}/{treatmentId}/{cycleNumber}/{applicationDay}/preparation/restart|post"
Add-QaCase "ENF-22" "Enfermeria" "CONTRACT" "Doble chequeo a pie de cama" "Paciente, etiqueta y segundo profesional son controles separados." "static" "/app.js" "" @('name="patientVerified"', 'name="labelVerified"', 'name="doubleCheckBy"', "Doble chequeo a pie de cama")
Add-QaCase "ENF-23" "Enfermeria" "CONTRACT" "Inicio y cierre reales" "Se registran horas, dosis real, reaccion y observacion." "static" "/app.js" "" @('name="startedAt"', 'name="completedAt"', 'name="actualDose"', 'name="reactionOccurred"', 'name="administrationObservation"')
Add-QaCase "ENF-24" "Enfermeria" "CONTRACT" "QR como control de identidad" "Sala permite leer el QR y abrir la ficha operativa canonica." "static" "/app.js" "" @("careQrScannerModal", "Abrir ficha de administración", 'openCareApplicationWorkflowModal("administration"')
Add-QaCase "ENF-25" "Enfermeria" "CONTRACT" "Reaccion aguda o administracion parcial" "Debe poder detener, documentar droga/dosis parcial, medidas y escalar sin cerrar todo como completado." "test-source" "/scripts/integration-test.ps1" "" @("La prueba de seguridad requiere un protocolo multidroga", 'administration/interrupt', 'actualDose = $partialDose', "actualDoseAtInterruption", "interruptionPending", 'administration/resolve')

# ONCOLOGIA: 25 casos
Add-QaCase "ONC-01" "Oncologia" "CONTRACT" "Nuevo tratamiento es el primer paso" "La primera pestana del Hospital de dia es Nuevo tratamiento." "static" "/index.html" "" @('data-care-hospital-tab="new-treatment"', "Nuevo tratamiento")
Add-QaCase "ONC-02" "Oncologia" "CONTRACT" "Contexto de paciente activo" "El modal distingue claramente paciente activo o ausencia de paciente." "static" "/index.html" "" @("careHospitalPatientContext", "careHospitalPatientName", "Sin paciente activo")
Add-QaCase "ONC-03" "Oncologia" "CONTRACT" "Diagnostico obligatorio" "El alta de tratamiento exige elegir un diagnostico guardado." "static" "/index.html" "" @('id="careTreatmentDiagnosis"', 'name="diagnostico"', "required")
Add-QaCase "ONC-04" "Oncologia" "CONTRACT" "Caracter terapeutico obligatorio" "Se puede elegir curativo, adyuvante, neoadyuvante, paliativo o soporte." "static" "/index.html" "" @("careTreatmentCharacter", "curative", "adjuvant", "neoadjuvant", "palliative", "supportive")
Add-QaCase "ONC-05" "Oncologia" "CONTRACT" "Tipo oncologico obligatorio" "Quimio, inmuno, dirigida, hormona y soporte estan disponibles." "static" "/index.html" "" @("careTreatmentType", "chemotherapy", "immunotherapy", "targeted", "hormone")
Add-QaCase "ONC-06" "Oncologia" "CONTRACT" "Selector de esquema" "El formulario selecciona esquema, no estadio." "static" "/index.html" "" @('id="careTreatmentScheme"', 'name="esquema"', "Seleccione...")
Add-QaCase "ONC-07" "Oncologia" "REAL" "Catalogo de protocolos accesible" "El catalogo responde sin modificar configuracion." "json-shape" "/api/clinical/protocols" "" @("ok")
Add-QaCase "ONC-08" "Oncologia" "REAL" "Catalogo de esquemas prescribibles" "Los esquemas disponibles responden desde la base local." "json-shape" "/api/clinical/schemes" "" @("ok")
Add-QaCase "ONC-09" "Oncologia" "CONTRACT" "Opciones por paciente documentadas" "Swagger expone diagnosticos y esquemas elegibles." "openapi" "/api/clinical/patients/{patientId}/treatment-options|get"
Add-QaCase "ONC-10" "Oncologia" "CONTRACT" "Requisitos del esquema documentados" "Swagger expone los requisitos previos por esquema." "openapi" "/api/clinical/patients/{patientId}/treatment-requirements/{schemeId}|get"
Add-QaCase "ONC-11" "Oncologia" "CONTRACT" "Incompatibilidad diagnostico-protocolo" "La excepcion exige confirmacion y motivo clinico." "static" "/index.html" "" @("careTreatmentProtocolWarning", "careTreatmentProtocolMismatchConfirmed", "careTreatmentProtocolMismatchReason", 'minlength="10"')
Add-QaCase "ONC-12" "Oncologia" "CONTRACT" "Cantidad de ciclos acotada" "Ciclos previstos admite 1 a 50." "static" "/index.html" "" @('id="careTreatmentCycles"', 'min="1"', 'max="50"')
Add-QaCase "ONC-13" "Oncologia" "CONTRACT" "Ciclo inicial explicito" "Se puede iniciar o reanudar desde un numero de ciclo valido." "static" "/index.html" "" @('id="careTreatmentInitialCycle"', 'name="cicloInicial"', 'value="1"')
Add-QaCase "ONC-14" "Oncologia" "CONTRACT" "Fecha del primer ciclo" "La fecha es obligatoria y alimenta la proyeccion." "static" "/index.html" "" @('id="careTreatmentFirstCycleDate"', 'name="fechaPrimerCiclo"', "required")
Add-QaCase "ONC-15" "Oncologia" "CONTRACT" "Proyeccion previa de ciclos" "Antes de guardar se muestran fechas calculadas." "static" "/index.html" "" @("careTreatmentProjection", "Seleccione un esquema y una fecha para proyectar los ciclos")
Add-QaCase "ONC-16" "Oncologia" "CONTRACT" "Estado de consentimiento" "Pendiente, firmado o no requerido son opciones explicitas." "static" "/index.html" "" @("careTreatmentConsent", 'value="pending"', 'value="signed"', 'value="not-required"')
Add-QaCase "ONC-17" "Oncologia" "CONTRACT" "Requisitos confirmados antes de guardar" "Los datos dinamicos del protocolo requieren confirmacion." "static" "/app.js" "" @("careTreatmentRequirementsConfirmed", "data-care-requirements-confirm", "Datos verificados")
Add-QaCase "ONC-18" "Oncologia" "REAL" "Aplicacion conserva drogas del dia" "La cola informa applicationDrugs, ciclo y dia." "queue-shape" "/api/clinical/application-workflows?queue=pharmacy" "" @("applicationDrugs", "cycleNumber", "applicationDay")
Add-QaCase "ONC-19" "Oncologia" "REAL" "Aplicaciones reales por ciclo y dia" "Cada fila incluye fecha prevista, duracion y fuente del calculo." "queue-shape" "/api/clinical/application-workflows?queue=pharmacy" "" @("plannedDate", "durationMinutes", "durationSource", "totalCycles")
Add-QaCase "ONC-20" "Oncologia" "CONTRACT" "Alta y listado de tratamientos" "Swagger documenta GET y POST de tratamientos del paciente." "openapi" "/api/clinical/patients/{patientId}/treatments|post"
Add-QaCase "ONC-21" "Oncologia" "CONTRACT" "Detalle ciclo-dia-aplicacion" "Swagger expone el detalle completo del tratamiento." "openapi" "/api/clinical/patients/{patientId}/treatments/{treatmentId}/detail|get"
Add-QaCase "ONC-22" "Oncologia" "CONTRACT" "Suspension documentada" "Swagger expone la suspension del tratamiento." "openapi" "/api/clinical/treatments/{patientId}/{treatmentId}/suspend|post"
Add-QaCase "ONC-23" "Oncologia" "CONTRACT" "Reanudacion documentada" "Swagger expone la reanudacion desde un ciclo coherente." "openapi" "/api/clinical/treatments/{patientId}/{treatmentId}/resume|post"
Add-QaCase "ONC-24" "Oncologia" "CONTRACT" "Evolucion clinica al prescribir" "El frontend construye y agrega una evolucion del tratamiento." "static" "/app.js" "" @("treatmentEvolution", "append", "fechaPrimerCiclo", "cantidadCiclos")
Add-QaCase "ONC-25" "Oncologia" "CONTRACT" "Documentos del tratamiento" "La interfaz enlaza hoja de tratamiento, QR y consentimiento." "static" "/app.js" "" @('"treatment-sheet"', "documents/qr?cycle=", "/consent")

# TURNOS: 25 casos
Add-QaCase "TUR-01" "Turnos" "REAL" "Abrir candidatos del turnero" "Respuesta ok/candidates/total sin alterar turnos." "candidates" "/api/clinical/infusion-candidates?includeScheduled=false&onlySchedulingEligible=false"
Add-QaCase "TUR-02" "Turnos" "REAL" "Buscar candidato por paciente" "El buscador encuentra una fila real cuando existe semilla QA." "candidate-probe"
Add-QaCase "TUR-03" "Turnos" "REAL" "Excluir ya programados de espera" "includeScheduled=false responde como contrato valido." "candidates" "/api/clinical/infusion-candidates?includeScheduled=false&onlySchedulingEligible=false"
Add-QaCase "TUR-04" "Turnos" "REAL" "Mostrar tambien bloqueados para gestion" "onlySchedulingEligible=false permite explicar por que no entran." "candidates" "/api/clinical/infusion-candidates?includeScheduled=false&onlySchedulingEligible=false"
Add-QaCase "TUR-05" "Turnos" "REAL" "Agenda del dia" "Lista de infusiones por fecha responde sin cambios." "json-shape" "/api/clinical/infusions?date=$((Get-Date).ToString('yyyy-MM-dd'))" "" @("ok", "infusions", "total")
Add-QaCase "TUR-06" "Turnos" "CONTRACT" "Filtros operativos de espera" "Todos, prescriptos, falta receta, falta medicacion, recibida y paciente." "static" "/index.html" "" @("careScheduleCandidateFilter", "prescription-confirmed", "missing-prescription", "missing-medication", "medication-received", "medication-with-patient")
Add-QaCase "TUR-07" "Turnos" "CONTRACT" "Prioridad cronologica de espera" "El listado usa un comparador por ciclo/fecha y no orden de carga." "static" "/app.js" "" @("careScheduleCandidateCompare", ".sort(", "suggestedDate")
Add-QaCase "TUR-08" "Turnos" "CONTRACT" "Fecha en formato local y dia de semana" "La cabecera tiene dd/mm/aaaa y nombre del dia." "static" "/index.html" "" @("careScheduleDate", "dd/mm/aaaa", "careScheduleWeekday")
Add-QaCase "TUR-09" "Turnos" "CONTRACT" "Calendario y navegacion diaria" "Existen calendario, anterior, hoy y siguiente." "static" "/index.html" "" @("careScheduleCalendarDate", "careSchedulePreviousDayBtn", "careScheduleTodayBtn", "careScheduleNextDayBtn")
Add-QaCase "TUR-10" "Turnos" "REAL" "Configuracion de Hospital de dia disponible" "La definicion activa se recupera desde PostgreSQL." "json-shape" "/api/clinical/configuration/day-hospital-settings" "" @("ok", "items", "total")
Add-QaCase "TUR-11" "Turnos" "CONTRACT" "Fracciones permitidas" "La grilla admite 5, 10, 15, 20 y 30 minutos." "static" "/app.js" "" @("[5, 10, 15, 20, 30]", "careScheduleSupportedSlotMinutes")
Add-QaCase "TUR-12" "Turnos" "CONTRACT" "Cantidad de sillones y jornada configurables" "La agenda consume chairCount, startTime y endTime." "static" "/app.js" "" @("chairCount", "startTime", "endTime", "careScheduleSettings")
Add-QaCase "TUR-13" "Turnos" "CONTRACT" "Zoom de sillones" "Acercar y alejar cambian la cantidad visible." "static" "/app.js" "" @("careScheduleZoomInBtn", "careScheduleZoomOutBtn", "zoomCareScheduleChairViewport")
Add-QaCase "TUR-14" "Turnos" "CONTRACT" "Paginado horizontal de sillones" "Anterior/siguiente desplazan el rango sin perder turnos." "static" "/index.html" "" @("careSchedulePreviousChairsBtn", "careScheduleNextChairsBtn", "careScheduleChairRange")
Add-QaCase "TUR-15" "Turnos" "CONTRACT" "Arrastrar y soltar" "La grilla escucha dragover y drop sobre el mismo objetivo." "static" "/app.js" "" @('$("#careScheduleGrid")?.addEventListener("dragover"', '$("#careScheduleGrid")?.addEventListener("drop"', "dropCareScheduleItem")
Add-QaCase "TUR-16" "Turnos" "CONTRACT" "Vista previa solo en posiciones validas" "El dropEffect es move solo cuando target.valid." "static" "/app.js" "" @('target.valid ? "move" : "none"', "handleCareScheduleDragOver")
Add-QaCase "TUR-17" "Turnos" "CONTRACT" "Prevencion de superposicion" "El calculo compara inicio/fin contra cada turno existente." "static" "/app.js" "" @("itemStart", "itemEnd", "placementItem", "target.valid")
Add-QaCase "TUR-18" "Turnos" "CONTRACT" "Duracion ocupa casilleros completos" "El span usa ceil(duracion/slotMinutes)." "static" "/app.js" "" @("Math.ceil(careScheduleItemDuration", "layout.slotMinutes", "span")
Add-QaCase "TUR-19" "Turnos" "CONTRACT" "Franja horaria visible" "El bloque muestra desde inicio hasta el ultimo minuto ocupado." "static" "/app.js" "" @("occupiedRange", "careScheduleClock(minutes)", "slotMinutes - 1")
Add-QaCase "TUR-20" "Turnos" "CONTRACT" "Turno confirmado y no confirmado distinguibles" "La tarjeta usa estados y colores separados." "static" "/app.js" "" @("is-appointment-confirmed", "is-appointment-pending", "appointmentConfirmed")
Add-QaCase "TUR-21" "Turnos" "CONTRACT" "Mover turno asignado" "La logica de drop conserva la aplicacion y actualiza su ubicacion." "static" "/app.js" "" @("dropCareScheduleItem", "previousCandidates", "scheduledAt", "chair")
Add-QaCase "TUR-22" "Turnos" "CONTRACT" "Quitar turno y devolver a espera" "Quitar la ubicacion no falsifica los estados historicos de Farmacia o Administracion." "static" "/app.js" "" @('scheduledAt: null', 'chair: null', 'clinicalStatus: "cancelled"', "loadCareSchedule")
Add-QaCase "TUR-23" "Turnos" "CONTRACT" "Modal de datos del turno" "Paciente, DNI, esquema, diagnostico, medicacion y confirmacion estan visibles." "static" "/app.js" "" @("careScheduleDetail", "patientDni", "scheme", "diagnosis", "medicationWithPatient", "appointmentConfirmed")
Add-QaCase "TUR-24" "Turnos" "CONTRACT" "API de alta de turno documentada" "Swagger expone POST /api/clinical/infusions y su conflicto de agenda." "openapi" "/api/clinical/infusions|post"
Add-QaCase "TUR-25" "Turnos" "CONTRACT" "Drop rapido, borde de jornada y conflicto concurrente" "Un solo turno se consolida, el otro recibe 409 claro y los limites 08:00-16:00 se validan al minuto." "test-source" "/src/test/java/ar/com/hexium/hcop/infusion/HospitalDayConcurrencySafetyTest.java" "" @("tur25SimultaneousDropsYieldOneAppointmentAndOneClearConflict", "tur25DatabaseSerializesAChairAndRejectsDuplicateActiveApplications", "tur25AcceptsExactWorkdayEdgesAndRejectsTheFirstOverflowingSlot", "CHAIR_SCHEDULE_CONFLICT", "OUTSIDE_DAY_HOSPITAL_HOURS")

Require-True ($script:Cases.Count -eq 100) "La matriz debe contener exactamente 100 casos; contiene $($script:Cases.Count)."
foreach ($role in @("Farmacia", "Enfermeria", "Oncologia", "Turnos")) {
  $count = @($script:Cases | Where-Object Role -eq $role).Count
  Require-True ($count -eq 25) "El rol $role debe tener 25 casos; contiene $count."
}

$results = @()
$position = 0
foreach ($case in $script:Cases) {
  $position += 1
  Write-Host ("[{0}/100] {1} - {2}" -f $position, $case.Id, $case.Title)
  $results += Invoke-QaCase $case
}
$written = Write-QaReports -Results ([object[]]$results) -Directory $OutputDirectory
Write-Host ""
Write-Host "Auditoria finalizada: 100 casos (25 por rol)."
Write-Host "JSON: $($written.Json)"
Write-Host "Markdown: $($written.Markdown)"
Write-Host ("PASS={0} FAIL={1} NO_DATA={2} MANUAL={3}" -f `
  $written.Summary.pass, $written.Summary.fail, $written.Summary.noData, $written.Summary.manual)
if ($written.Summary.fail -gt 0 -and -not $NoFailExit) {
  throw "La auditoria encontro $($written.Summary.fail) fallas automaticas. Los reportes ya fueron guardados."
}
