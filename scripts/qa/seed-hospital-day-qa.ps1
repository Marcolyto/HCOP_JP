param(
  [string]$BaseUrl = "http://127.0.0.1:5181",
  [System.Management.Automation.PSCredential]$Credential,
  [string]$Username = "",
  [string]$Password = "",
  [switch]$SeedTriage,
  [ValidateRange(0, 2000)][int]$SyntheticPharmacyRows = 0,
  [switch]$ConfirmDisposableQa,
  [switch]$AllowAlternateQaPort
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Assert-QaTarget {
  param([string]$Url)
  $parsed = $null
  if (-not [uri]::TryCreate($Url, [System.UriKind]::Absolute, [ref]$parsed)) {
    throw "BaseUrl no es una URL absoluta valida: $Url"
  }
  if ($parsed.Scheme -notin @("http", "https")) {
    throw "BaseUrl debe usar http o https."
  }
  if ($parsed.Port -eq 5180) {
    throw "ABORTADO POR SEGURIDAD: 5180 es la instancia principal y nunca recibe semillas QA."
  }
  if ($parsed.Port -ne 5181 -and -not $AllowAlternateQaPort) {
    throw "ABORTADO POR SEGURIDAD: use 5181 o confirme otro puerto QA con -AllowAlternateQaPort."
  }
  if ($parsed.Host -notin @("127.0.0.1", "localhost", "::1")) {
    throw "ABORTADO POR SEGURIDAD: las semillas solo se permiten sobre una instancia local."
  }
  if ($SyntheticPharmacyRows -gt 0 -and -not $ConfirmDisposableQa) {
    throw "La carga FAR-24 requiere -ConfirmDisposableQa porque crea datos sinteticos persistentes."
  }
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
    [ValidateSet("GET", "POST", "PUT", "PATCH")][string]$Method = "GET",
    [Parameter(Mandatory = $true)][string]$Path,
    [object]$Body = $null
  )
  $parameters = @{
    Uri = Join-QaUrl $Path
    Method = $Method
    WebSession = $script:WebSession
    Headers = @{ Accept = "application/json" }
    TimeoutSec = 120
  }
  if ($null -ne $Body) {
    $parameters.ContentType = "application/json; charset=utf-8"
    $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes(
      ($Body | ConvertTo-Json -Depth 100 -Compress)
    )
  }
  try {
    return Invoke-RestMethod @parameters
  } catch {
    $response = $_.Exception.Response
    $status = if ($null -eq $response) { "sin estado HTTP" } else { [int]$response.StatusCode }
    $detail = ""
    if ($null -ne $response) {
      try {
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        $detail = $reader.ReadToEnd()
        $reader.Dispose()
      } catch {
        $detail = ""
      }
    }
    throw "$Method $Path fallo ($status). $detail"
  }
}

function Get-ExactPatient {
  param([string]$Dni)
  $payload = Invoke-QaJson -Path "/api/clinical/patients?q=$([uri]::EscapeDataString($Dni))"
  return @($payload.patients) |
    Where-Object { [string]$_.numeroDocumento -eq $Dni } |
    Select-Object -First 1
}

function Ensure-QaPatient {
  param(
    [string]$Key,
    [string]$FirstName,
    [string]$LastName
  )
  $dni = "QA-$Key"
  $patient = Get-ExactPatient $dni
  if ($null -eq $patient) {
    $created = Invoke-QaJson -Method POST -Path "/api/clinical/patients" -Body @{
      firstName = $FirstName
      lastName = $LastName
      dni = $dni
      medicalRecord = "HC-$Key"
      birthDate = "1970-01-01"
      sex = "Sin especificar"
      insurance = "Cobertura sintetica QA"
      affiliateNumber = "AF-$Key"
    }
    $patient = [pscustomobject]@{
      id = [string]$created.patientId
      fullName = "$LastName, $FirstName"
      numeroDocumento = $dni
      numeroHC = "HC-$Key"
    }
    Write-Host "Paciente QA creado: $dni"
  } else {
    Write-Host "Paciente QA reutilizado: $dni"
  }
  return $patient
}

function Ensure-QaDiagnosis {
  param(
    [string]$PatientId,
    [string]$DiagnosisId
  )
  Invoke-QaJson -Method POST -Path "/api/clinical/patients/$PatientId/activate" -Body @{} | Out-Null
  $options = Invoke-QaJson -Path "/api/clinical/patients/$PatientId/treatment-options"
  $found = @($options.options.diagnoses) |
    Where-Object { [string]$_.id -eq $DiagnosisId } |
    Select-Object -First 1
  if ($null -ne $found) {
    return $DiagnosisId
  }

  $history = Invoke-QaJson -Path "/api/hc"
  if ($null -eq $history.oncology) {
    $history | Add-Member -NotePropertyName oncology -NotePropertyValue ([pscustomobject]@{}) -Force
  }
  $records = @($history.oncology.diagnosisRecords)
  if (@($records | Where-Object { [string]$_.id -eq $DiagnosisId }).Count -eq 0) {
    $today = (Get-Date).ToString("yyyy-MM-dd")
    $diagnosis = [pscustomobject]@{
      id = $DiagnosisId
      date = $today
      datePrecision = "day"
      diagnosis = "Anemia ferropenica para semilla QA"
      diagnostico = "Anemia ferropenica para semilla QA"
      topography = "Sistema hematologico"
      histology = ""
      stage = "No aplica"
      diagnosticClassifications = @{
        cie10 = @{
          system = "CIE-10"
          code = "D50.9"
          display = "Anemia por deficiencia de hierro, no especificada"
        }
      }
      archived = $false
    }
    $history.oncology |
      Add-Member -NotePropertyName diagnosisRecords -NotePropertyValue @($records + $diagnosis) -Force
    $history.oncology.diagnosis = $diagnosis.diagnosis
    $history.oncology |
      Add-Member -NotePropertyName diagnosisDate -NotePropertyValue $today -Force
    $history.oncology |
      Add-Member -NotePropertyName diagnosisDatePrecision -NotePropertyValue "day" -Force
    $saved = Invoke-QaJson -Method PUT -Path "/api/hc" -Body $history
    $revision = [long]$saved.unified.revision
    Invoke-QaJson -Method PUT -Path "/api/clinical/patients/$PatientId/diagnosis" -Body @{
      expectedRevision = $revision
      diagnosisEntryId = $DiagnosisId
    } | Out-Null
    Write-Host "Diagnostico QA agregado a paciente $PatientId"
  }
  return $DiagnosisId
}

function Ensure-QaTreatment {
  param(
    [string]$PatientId,
    [string]$DiagnosisId,
    [string]$FirstCycleDate,
    [int]$CycleCount,
    [string]$SeedKey
  )
  $existing = Invoke-QaJson -Path "/api/clinical/patients/$PatientId/treatments"
  $treatment = @($existing.treatments) |
    Where-Object {
      [string]$_.schemeId -eq "153" -and
      [int]$_.cycleCount -eq $CycleCount -and
      [string]$_.firstCycleDate -eq $FirstCycleDate
    } |
    Select-Object -First 1
  if ($null -ne $treatment) {
    Write-Host "Tratamiento QA reutilizado: $SeedKey"
    return $treatment
  }

  $created = Invoke-QaJson -Method POST -Path "/api/clinical/patients/$PatientId/treatments" -Body @{
    diagnostico = $DiagnosisId
    esquema = "153"
    cantidadCiclos = $CycleCount
    cicloInicial = 1
    duracionCiclo = 1
    fechaPrimerCiclo = $FirstCycleDate
    tipoOncologico = "Tratamiento intravenoso"
    caracter = "Soporte"
    estadoConsentimiento = "No requerido"
    requirementsConfirmed = $true
    protocolMismatchConfirmed = $true
    protocolMismatchReason = "Semilla sintetica determinista de control QA."
    clinicalEntryId = "qa-seed-$SeedKey"
  }
  Write-Host "Tratamiento QA creado: $SeedKey ($CycleCount ciclo/s)"
  return $created.treatment
}

function Get-Workflow {
  param(
    [string]$Dni,
    [string]$TreatmentId,
    [int]$CycleNumber = 1,
    [int]$ApplicationDay = 1
  )
  $queue = Invoke-QaJson -Path "/api/clinical/application-workflows?queue=pharmacy&q=$([uri]::EscapeDataString($Dni))"
  return @($queue.items) |
    Where-Object {
      [string]$_.treatmentId -eq $TreatmentId -and
      [int]$_.cycleNumber -eq $CycleNumber -and
      [int]$_.applicationDay -eq $ApplicationDay
    } |
    Select-Object -First 1
}

function Ensure-PatientMedicationReady {
  param([object]$Workflow)
  if ([string]$Workflow.pharmacyValidationStatus -eq "approved" -and
      [string]$Workflow.medicationSource -eq "patient_has_medication") {
    return $Workflow
  }
  $path = "/api/clinical/application-workflows/$($Workflow.patientId)/$($Workflow.treatmentId)/$($Workflow.cycleNumber)/$($Workflow.applicationDay)/pharmacy-validation"
  $result = Invoke-QaJson -Method POST -Path $path -Body @{
    expectedRevision = [long]$Workflow.revision
    idempotencyKey = "qa.seed.pharmacy.$($Workflow.patientId).$($Workflow.treatmentId).$($Workflow.cycleNumber).$($Workflow.applicationDay)"
    validated = $true
    medicationSource = "patient_has_medication"
    notes = "Semilla QA: medicacion declarada en poder del paciente."
  }
  return $result.workflow
}

function Get-ActiveScheduleSettings {
  $payload = Invoke-QaJson -Path "/api/clinical/configuration/day-hospital-settings"
  $item = @($payload.items) |
    Where-Object { $_.active -ne $false } |
    Select-Object -First 1
  Require-True ($null -ne $item) "No existe configuracion activa de Hospital de dia."
  return $item.definition
}

function Find-FreeAppointment {
  param(
    [datetime]$Date,
    [int]$DurationMinutes,
    [int]$NotBeforeMinute = -1
  )
  $settings = Get-ActiveScheduleSettings
  $chairCount = [int]$settings.chairCount
  $slotMinutes = [int]$settings.slotMinutes
  $startParts = ([string]$settings.startTime).Split(":")
  $endParts = ([string]$settings.endTime).Split(":")
  $startMinute = ([int]$startParts[0] * 60) + [int]$startParts[1]
  $endMinute = ([int]$endParts[0] * 60) + [int]$endParts[1]
  $occupiedMinutes = [math]::Ceiling($DurationMinutes / $slotMinutes) * $slotMinutes
  $existingPayload = Invoke-QaJson -Path "/api/clinical/infusions?date=$($Date.ToString('yyyy-MM-dd'))"
  $existing = @($existingPayload.infusions) |
    Where-Object {
      [string]$_.clinicalStatus -ne "cancelled" -and
      -not [string]::IsNullOrWhiteSpace([string]$_.scheduledAt)
    }

  for ($minute = $startMinute; $minute + $occupiedMinutes -le $endMinute; $minute += $slotMinutes) {
    if ($minute -le $NotBeforeMinute) { continue }
    foreach ($chair in 1..$chairCount) {
      $candidateStart = $Date.Date.AddMinutes($minute)
      $candidateEnd = $candidateStart.AddMinutes($occupiedMinutes)
      $overlap = @($existing | Where-Object {
        if ([string]$_.chair -ne [string]$chair) { return $false }
        $existingStart = ([DateTimeOffset]::Parse([string]$_.scheduledAt)).LocalDateTime
        $existingDuration = if ([int]$_.durationMinutes -gt 0) {
          [int]$_.durationMinutes
        } else {
          $slotMinutes
        }
        $existingEnd = $existingStart.AddMinutes(
          [math]::Ceiling($existingDuration / $slotMinutes) * $slotMinutes
        )
        return $candidateStart -lt $existingEnd -and $existingStart -lt $candidateEnd
      }).Count -gt 0
      if (-not $overlap) {
        return [pscustomobject]@{
          Local = $candidateStart
          Minute = $minute
          Chair = [string]$chair
          SlotMinutes = $slotMinutes
        }
      }
    }
  }
  throw "No existe un espacio libre hoy para una aplicacion QA de $DurationMinutes minutos."
}

function Ensure-TriageAppointment {
  param(
    [object]$Patient,
    [object]$Treatment,
    [int]$NotBeforeMinute = -1
  )
  $today = (Get-Date).ToString("yyyy-MM-dd")
  $existing = Invoke-QaJson -Path "/api/clinical/infusions?date=$today"
  $appointment = @($existing.infusions) |
    Where-Object {
      [string]$_.patientId -eq [string]$Patient.id -and
      [string]$_.treatmentId -eq [string]$Treatment.id -and
      [int]$_.cycleNumber -eq 1 -and
      [int]$_.applicationDay -eq 1 -and
      [string]$_.clinicalStatus -ne "cancelled"
    } |
    Select-Object -First 1
  if ($null -ne $appointment) {
    $local = ([DateTimeOffset]::Parse([string]$appointment.scheduledAt)).LocalDateTime
    Write-Host "Turno QA reutilizado: $($Patient.numeroDocumento) $($local.ToString('HH:mm'))"
    return [pscustomobject]@{
      Appointment = $appointment
      Minute = ($local.Hour * 60) + $local.Minute
    }
  }

  $workflow = Get-Workflow -Dni ([string]$Patient.numeroDocumento) `
    -TreatmentId ([string]$Treatment.id)
  Require-True ($null -ne $workflow) "No se materializo la aplicacion QA de $($Patient.numeroDocumento)."
  $workflow = Ensure-PatientMedicationReady $workflow
  $duration = if ([int]$workflow.durationMinutes -gt 0) {
    [int]$workflow.durationMinutes
  } else {
    40
  }
  $slot = Find-FreeAppointment -Date (Get-Date).Date `
    -DurationMinutes $duration -NotBeforeMinute $NotBeforeMinute
  $scheduledAt = ([DateTimeOffset]$slot.Local).ToUniversalTime().ToString("o")
  $created = Invoke-QaJson -Method POST -Path "/api/clinical/infusions" -Body @{
    patientId = [string]$Patient.id
    treatmentId = [string]$Treatment.id
    cycleNumber = 1
    applicationDay = 1
    scheduledAt = $scheduledAt
    chair = [string]$slot.Chair
    durationMinutes = $duration
    clinicalStatus = "planned"
    pharmacyStatus = "pending"
    administrationStatus = "not_started"
    appointmentConfirmed = $true
    notes = "Semilla determinista de triaje QA."
    sourceRef = @{
      scheduler = @{
        prescriptionConfirmed = $true
        medicationWithPatient = $true
        medicationReceived = $false
      }
    }
  }
  Write-Host "Turno QA creado: $($Patient.numeroDocumento) $($slot.Local.ToString('HH:mm')) sillon $($slot.Chair)"
  return [pscustomobject]@{
    Appointment = $created.infusion
    Minute = [int]$slot.Minute
  }
}

function Seed-TriageData {
  $today = (Get-Date).ToString("yyyy-MM-dd")
  $minute = -1
  $expectedDnis = @()
  foreach ($definition in @(
    @{ Key = "TRIAGE-$($today.Replace('-', ''))-A"; First = "Alfa"; Last = "QA Triaje" },
    @{ Key = "TRIAGE-$($today.Replace('-', ''))-B"; First = "Beta"; Last = "QA Triaje" }
  )) {
    $patient = Ensure-QaPatient -Key $definition.Key `
      -FirstName $definition.First -LastName $definition.Last
    $diagnosisId = "diag-qa-$($definition.Key.ToLowerInvariant())"
    Ensure-QaDiagnosis -PatientId ([string]$patient.id) -DiagnosisId $diagnosisId | Out-Null
    $treatment = Ensure-QaTreatment -PatientId ([string]$patient.id) `
      -DiagnosisId $diagnosisId -FirstCycleDate $today -CycleCount 1 -SeedKey $definition.Key
    $scheduled = Ensure-TriageAppointment -Patient $patient `
      -Treatment $treatment -NotBeforeMinute $minute
    $minute = [int]$scheduled.Minute
    $expectedDnis += [string]$patient.numeroDocumento
  }

  $queue = Invoke-QaJson -Path "/api/clinical/application-workflows?queue=triage&date=$today"
  $rows = @($queue.items | Where-Object {
    [string]$_.patientDni -in $expectedDnis
  })
  Require-True ($rows.Count -eq 2) "La cola de triaje no contiene las dos semillas QA."
  $times = @($rows | ForEach-Object { [string]$_.appointment.scheduledAt })
  Require-True (($times -join "|") -eq (@($times | Sort-Object) -join "|")) `
    "Las semillas de triaje no quedaron ordenadas cronologicamente."
  Write-Host "Semilla de triaje verificada: 2 filas reales, buscables y ordenadas."
}

function Seed-PharmacyLoadData {
  param([int]$RequestedRows)
  if ($RequestedRows -eq 0) { return }
  Require-True (($RequestedRows % 500) -eq 0) `
    "SyntheticPharmacyRows debe ser multiplo de 500 para mantener una semilla determinista."
  $patientCount = [int]($RequestedRows / 500)
  Require-True ($patientCount -le 4) "La carga QA admite como maximo 2000 filas."

  foreach ($index in 1..$patientCount) {
    $key = "FAR24-{0:D2}" -f $index
    $patient = Ensure-QaPatient -Key $key `
      -FirstName ("Carga {0:D2}" -f $index) -LastName "QA Farmacia"
    $diagnosisId = "diag-qa-$($key.ToLowerInvariant())"
    Ensure-QaDiagnosis -PatientId ([string]$patient.id) -DiagnosisId $diagnosisId | Out-Null
    $firstCycle = (Get-Date "2010-01-01").AddDays($index - 1).ToString("yyyy-MM-dd")
    Ensure-QaTreatment -PatientId ([string]$patient.id) `
      -DiagnosisId $diagnosisId -FirstCycleDate $firstCycle `
      -CycleCount 500 -SeedKey $key | Out-Null
  }

  $watch = [System.Diagnostics.Stopwatch]::StartNew()
  $queue = Invoke-QaJson -Path "/api/clinical/application-workflows?queue=pharmacy"
  $watch.Stop()
  Require-True ([int]$queue.total -ge $RequestedRows) `
    "La cola solo materializo $($queue.total) de $RequestedRows filas solicitadas."
  Write-Host ("Carga FAR-24 verificada: {0} filas visibles; cola completa en {1} ms." -f `
    $queue.total, $watch.ElapsedMilliseconds)
}

Assert-QaTarget $BaseUrl
$script:QaBaseUrl = $BaseUrl.TrimEnd("/")
$script:WebSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
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

$runTriage = $SeedTriage -or $SyntheticPharmacyRows -eq 0
if ($runTriage) {
  Seed-TriageData
}
if ($SyntheticPharmacyRows -gt 0) {
  Seed-PharmacyLoadData -RequestedRows $SyntheticPharmacyRows
}

Write-Host "Semillas QA finalizadas sin tocar la instancia 5180."
