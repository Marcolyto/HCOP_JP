param(
  [string]$BaseUrl = "http://127.0.0.1:5180",
  [string]$Username = "marcolyto",
  [string]$Password = "colarse2"
)

$ErrorActionPreference = "Stop"

function Assert-True {
  param([bool]$Condition, [string]$Message)
  if (-not $Condition) { throw $Message }
}

function Invoke-HcopJson {
  param(
    [ValidateSet("GET", "POST", "PUT", "PATCH", "DELETE")][string]$Method = "GET",
    [string]$Path,
    [object]$Body = $null
  )
  $parameters = @{
    Uri = "$BaseUrl$Path"
    Method = $Method
    WebSession = $script:WebSession
    Headers = @{ Accept = "application/json" }
  }
  if ($null -ne $Body) {
    $parameters.ContentType = "application/json; charset=utf-8"
    $json = $Body | ConvertTo-Json -Depth 100 -Compress
    $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
  }
  Invoke-RestMethod @parameters
}

$script:WebSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health"
Assert-True ($health.status -eq "UP") "El servicio no está saludable."

$runtime = Invoke-RestMethod -Uri "$BaseUrl/api/runtime/status"
Assert-True ($runtime.engine -eq "java-postgresql") "El servidor no usa Java y PostgreSQL."

$login = Invoke-HcopJson -Method POST -Path "/api/auth/login" -Body @{
  username = $Username
  password = $Password
}
Assert-True ($login.authenticated -eq $true) "No se pudo iniciar sesión."

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$patient = Invoke-HcopJson -Method POST -Path "/api/clinical/patients" -Body @{
  firstName = "Prueba"
  lastName = "Integral $suffix"
  dni = "T$suffix"
  medicalRecord = "E2E-$suffix"
  birthDate = "1980-01-01"
  sex = "Masculino"
  insurance = "Cobertura de prueba"
  affiliateNumber = "TEST-$suffix"
}
$patientId = [string]$patient.patientId
Assert-True (-not [string]::IsNullOrWhiteSpace($patientId)) "No se creó el paciente."

$history = Invoke-HcopJson -Path "/api/hc"
$diagnosisId = "diag-e2e-$suffix"
$diagnosis = [pscustomobject]@{
  id = $diagnosisId
  diagnosisEntryId = $diagnosisId
  diagnostico = "Carcinoma pulmonar de prueba"
  snomed = "Carcinoma de pulmón"
  cie10 = "C34.9"
  cie10Codigo = "C34.9"
  ajcc = "Pulmón"
  t = "T2"
  n = "N2"
  m = "M1"
  estadio = "IV"
  date = (Get-Date).ToString("yyyy-MM-dd")
  archived = $false
}
$history.oncology | Add-Member -NotePropertyName diagnosisRecords -NotePropertyValue @($diagnosis) -Force
$history.oncology.diagnosis = $diagnosis.diagnostico
$history.oncology.stage = $diagnosis.estadio
$savedHistory = Invoke-HcopJson -Method PUT -Path "/api/hc" -Body $history
Assert-True ($savedHistory.unified.persisted -eq $true) "No se guardó el diagnóstico."

$options = Invoke-HcopJson -Path "/api/clinical/patients/$patientId/treatment-options"
$scheme = @($options.options.schemes) |
  Where-Object { [string]$_.id -eq "238" } |
  Select-Object -First 1
if ($null -eq $scheme) {
  $scheme = @($options.options.schemes) |
    Where-Object {
      [string]$_.protocolGroup -eq "thoracic" -and
      [int]($_.durationMinutes) -gt 0
    } |
    Select-Object -First 1
}
if ($null -eq $scheme) { $scheme = @($options.options.schemes) | Select-Object -First 1 }
Assert-True ($null -ne $scheme) "No hay protocolos disponibles."

$firstCycle = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")
$createdTreatment = Invoke-HcopJson -Method POST -Path "/api/clinical/patients/$patientId/treatments" -Body @{
  diagnostico = $diagnosisId
  esquema = [string]$scheme.id
  cantidadCiclos = 2
  cicloInicial = 1
  duracionCiclo = $(if ([int]$scheme.cycleDays -gt 0) { [int]$scheme.cycleDays } else { 21 })
  fechaPrimerCiclo = $firstCycle
  tipoOncologico = "Quimioterapia"
  caracter = "Paliativo"
  estadoConsentimiento = "Pendiente"
  peso = "75"
  talla = "175"
  requirementsConfirmed = $true
}
$treatmentId = [string]$createdTreatment.treatment.id
Assert-True (-not [string]::IsNullOrWhiteSpace($treatmentId)) "No se creó el tratamiento."

$scheduledAt = (Get-Date).ToUniversalTime().Date.AddDays(1).AddHours(12).ToString("o")
$createdInfusion = Invoke-HcopJson -Method POST -Path "/api/clinical/infusions" -Body @{
  patientId = $patientId
  treatmentId = $treatmentId
  cycleNumber = 1
  scheduledAt = $scheduledAt
  chair = "E2E-$suffix"
  durationMinutes = $(if ([int]$scheme.durationMinutes -gt 0) { [int]$scheme.durationMinutes } else { 60 })
  clinicalStatus = "planned"
  pharmacyStatus = "pending"
  administrationStatus = "not_started"
  appointmentConfirmed = $true
  sourceRef = @{ scheduler = @{ prescriptionConfirmed = $true; medicationReceived = $false } }
}
$infusion = $createdInfusion.infusion
Assert-True (-not [string]::IsNullOrWhiteSpace([string]$infusion.id)) "No se creó el turno."

$qrHtml = (Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/clinical/patients/$patientId/treatments/$treatmentId/documents/qr?cycle=1" -WebSession $script:WebSession -Headers @{ Accept = "text/html" }).Content
$qrMatch = [regex]::Match($qrHtml, '<p class="code">([^<]+)</p>')
Assert-True ($qrMatch.Success) "No se generó el QR."
$scan = Invoke-HcopJson -Method POST -Path "/api/clinical/qr-scans" -Body @{
  code = [System.Net.WebUtility]::HtmlDecode($qrMatch.Groups[1].Value)
  operationId = "e2e-$suffix"
}
Assert-True ([string]$scan.infusion.id -eq [string]$infusion.id) "El QR no abrió el turno esperado."

$finalized = Invoke-HcopJson -Method POST -Path "/api/clinical/infusions/$($infusion.id)/finalize" -Body @{
  confirmed = $true
  expectedVersion = [long]$infusion.revision
  observation = "Administración integral de prueba finalizada."
}
Assert-True ($finalized.infusion.clinicalStatus -eq "completed") "No se finalizó la aplicación."

$detail = Invoke-HcopJson -Path "/api/clinical/patients/$patientId/treatments/$treatmentId/detail"
$cycleOne = @($detail.detail.cycles) | Where-Object { [int]$_.number -eq 1 } | Select-Object -First 1
Assert-True (@($cycleOne.drugs).Count -ge 1) "El detalle del ciclo no incorporó las drogas del protocolo."
Assert-True (@($cycleOne.applications).Count -eq 1) "El detalle no incorporó el turno real."

$sheet = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/clinical/patients/$patientId/treatments/$treatmentId/documents/treatment-sheet?cycle=1" -WebSession $script:WebSession -Headers @{ Accept = "text/html" }
Assert-True ($sheet.StatusCode -eq 200 -and $sheet.Content -match "Hoja de tratamiento") "No se generó la hoja de tratamiento."

$finalHistory = Invoke-HcopJson -Path "/api/hc"
Assert-True (@($finalHistory.evolutions).Count -ge 3) "Los actos clínicos no quedaron documentados."

$sessionBeforeConfiguration = Invoke-HcopJson -Path "/api/auth/me"
Assert-True ([string]$sessionBeforeConfiguration.activePatientId -eq $patientId) "La sesión no conservó el paciente abierto."
$configurationPage = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/configuration/" -WebSession $script:WebSession
Assert-True ($configurationPage.StatusCode -eq 200) "No se pudo navegar a Configuración."
$sessionAfterConfiguration = Invoke-HcopJson -Path "/api/auth/me"
Assert-True ([string]$sessionAfterConfiguration.activePatientId -eq $patientId) "Configuración liberó el paciente activo."

$closedPatient = Invoke-HcopJson -Method PUT -Path "/api/auth/active-patient" -Body @{ patientId = $null }
Assert-True ([string]::IsNullOrWhiteSpace([string]$closedPatient.activePatientId)) "No se cerró el paciente activo."
$blankHistory = Invoke-HcopJson -Path "/api/hc"
Assert-True ([string]::IsNullOrWhiteSpace([string]$blankHistory.patient.fullName)) "La hoja no quedó en blanco al cerrar el paciente."
$reopenedPatient = Invoke-HcopJson -Method POST -Path "/api/clinical/patients/$patientId/activate" -Body @{}
Assert-True ([string]$reopenedPatient.state.meta.liraImport.patientId -eq $patientId) "No se pudo volver a abrir el paciente cerrado."

[pscustomobject]@{
  ok = $true
  engine = $runtime.engine
  patientId = $patientId
  treatmentId = $treatmentId
  infusionId = [string]$infusion.id
  evolutions = @($finalHistory.evolutions).Count
  protocol = [string]$scheme.nombre
} | ConvertTo-Json -Compress
