param(
  [string]$BaseUrl = "http://127.0.0.1:5180",
  [string]$Username = "marcolyto",
  [string]$Password = $env:HCOP_BOOTSTRAP_PASSWORD
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Password)) {
  throw "Defina HCOP_BOOTSTRAP_PASSWORD o informe -Password para ejecutar la prueba."
}

function Assert-True {
  param([bool]$Condition, [string]$Message)
  if (-not $Condition) { throw $Message }
}

function Get-ApplicationComponentKey {
  param([object]$Drug, [int]$Ordinal)
  $explicitKey = [string]$Drug.sourceItemRef
  if ([string]::IsNullOrWhiteSpace($explicitKey)) {
    $explicitKey = [string]$Drug.componentKey
  }
  if ([string]::IsNullOrWhiteSpace($explicitKey) -and $null -ne $Drug.source) {
    $explicitKey = [string]$Drug.source.sourceItemRef
    if ([string]::IsNullOrWhiteSpace($explicitKey)) {
      $explicitKey = [string]$Drug.source.id
    }
  }
  if (-not [string]::IsNullOrWhiteSpace($explicitKey)) {
    return $explicitKey.Trim()
  }

  $stem = [string]$Drug.drugId
  if ([string]::IsNullOrWhiteSpace($stem)) {
    $stem = [string]$Drug.drugName
    $stem = $stem.Normalize([System.Text.NormalizationForm]::FormD)
    $stem = [regex]::Replace($stem, '\p{M}+', '')
    $stem = [regex]::Replace($stem.ToLowerInvariant(), '[^a-z0-9]+', '-').Trim('-')
    if ([string]::IsNullOrWhiteSpace($stem)) { $stem = "component" }
  }
  return "$($stem.Trim())-$Ordinal"
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
  try {
    Invoke-RestMethod @parameters
  } catch {
    $response = $_.Exception.Response
    $status = if ($null -eq $response) { "sin estado HTTP" } else { [int]$response.StatusCode }
    $detail = [string]$_.ErrorDetails.Message
    if ([string]::IsNullOrWhiteSpace($detail) -and $null -ne $response) {
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
    throw "$Method $Path fallo ($status). $detail"
  }
}

$script:WebSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health"
Assert-True ($health.status -eq "UP") "El servicio no esta saludable."

$runtime = Invoke-RestMethod -Uri "$BaseUrl/api/runtime/status"
Assert-True ($runtime.engine -eq "java-postgresql") "El servidor no usa Java y PostgreSQL."

$login = Invoke-HcopJson -Method POST -Path "/api/auth/login" -Body @{
  username = $Username
  password = $Password
}
Assert-True ($login.authenticated -eq $true) "No se pudo iniciar sesion."

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$testDni = "T$suffix"
$patient = Invoke-HcopJson -Method POST -Path "/api/clinical/patients" -Body @{
  firstName = "Prueba"
  lastName = "Integral $suffix"
  dni = $testDni
  medicalRecord = "E2E-$suffix"
  birthDate = "1980-01-01"
  sex = "Masculino"
  insurance = "Cobertura de prueba"
  affiliateNumber = "TEST-$suffix"
}
$patientId = [string]$patient.patientId
Assert-True (-not [string]::IsNullOrWhiteSpace($patientId)) "No se creo el paciente."

$history = Invoke-HcopJson -Path "/api/hc"
$diagnosisId = "diag-e2e-$suffix"
$today = (Get-Date).ToString("yyyy-MM-dd")
$diagnosis = [pscustomobject]@{
  id = $diagnosisId
  date = $today
  datePrecision = "day"
  diagnosis = "Neoplasia maligna de pulmon"
  diagnostico = "Neoplasia maligna de pulmon"
  topography = "Pulmon"
  histology = "Carcinoma pulmonar de prueba"
  stage = "IV"
  diagnosticClassifications = @{
    ajcc = @{
      system = "AJCC"
      code = "lung"
      display = "Pulmon"
    }
    snomed = @{
      system = "SNOMED CT"
      code = "363358000"
      display = "Neoplasia maligna de pulmon"
    }
    cie10 = @{
      system = "CIE-10"
      code = "C34.9"
      display = "Tumor maligno de bronquio o pulmon, no especificado"
    }
  }
  tnm = @{
    siteId = "lung"
    siteDisplay = "Pulmon"
    prefix = "c"
    date = $today
    t = "T2"
    n = "N2"
    m = "M1"
    stage = "IV"
  }
  archived = $false
}
$history.oncology | Add-Member -NotePropertyName diagnosisRecords -NotePropertyValue @($diagnosis) -Force
$history.oncology | Add-Member -NotePropertyName diagnosticClassifications -NotePropertyValue $diagnosis.diagnosticClassifications -Force
$history.oncology | Add-Member -NotePropertyName tnm -NotePropertyValue $diagnosis.tnm -Force
$history.oncology.diagnosis = $diagnosis.diagnosis
$history.oncology | Add-Member -NotePropertyName diagnosisDate -NotePropertyValue $today -Force
$history.oncology | Add-Member -NotePropertyName diagnosisDatePrecision -NotePropertyValue "day" -Force
$history.oncology | Add-Member -NotePropertyName topography -NotePropertyValue $diagnosis.topography -Force
$history.oncology | Add-Member -NotePropertyName histology -NotePropertyValue $diagnosis.histology -Force
$history.oncology.stage = $diagnosis.stage
$savedHistory = Invoke-HcopJson -Method PUT -Path "/api/hc" -Body $history
Assert-True ($savedHistory.unified.persisted -eq $true) "No se guardo el diagnostico."
$linkedDiagnosis = Invoke-HcopJson -Method PUT -Path "/api/clinical/patients/$patientId/diagnosis" -Body @{
  expectedRevision = [long]$savedHistory.unified.revision
  diagnosisEntryId = $diagnosisId
}
Assert-True ($linkedDiagnosis.linked -eq $true) "El diagnostico no quedo vinculado."

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

$firstCycle = (Get-Date).ToString("yyyy-MM-dd")
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
  creatinina = "0.9"
  tfg = "90"
  targetAUC = "5"
  requirementsConfirmed = $true
}
$treatmentId = [string]$createdTreatment.treatment.id
Assert-True (-not [string]::IsNullOrWhiteSpace($treatmentId)) "No se creo el tratamiento."

$pharmacyQueue = Invoke-HcopJson -Path "/api/clinical/application-workflows?queue=pharmacy&q=$([uri]::EscapeDataString($testDni))"
$workflow = @($pharmacyQueue.items) |
  Where-Object {
    [string]$_.patientId -eq $patientId -and
    [string]$_.treatmentId -eq $treatmentId -and
    [int]$_.cycleNumber -eq 1
  } |
  Sort-Object { [int]$_.applicationDay } |
  Select-Object -First 1
Assert-True ($null -ne $workflow) "No se materializo la primera aplicacion del tratamiento."

$cycleNumber = [int]$workflow.cycleNumber
$applicationDay = [int]$workflow.applicationDay
$workflowPath = "/api/clinical/application-workflows/$patientId/$treatmentId/$cycleNumber/$applicationDay"

$pharmacyValidation = Invoke-HcopJson -Method POST -Path "$workflowPath/pharmacy-validation" -Body @{
  expectedRevision = [long]$workflow.revision
  idempotencyKey = "e2e.pharmacy.$suffix.$cycleNumber.$applicationDay"
  validated = $true
  medicationSource = "center_stock"
  notes = "Dosis, via, intervalo y premedicacion verificados."
}
$workflow = $pharmacyValidation.workflow
Assert-True ($workflow.pharmacyValidationStatus -eq "approved") "Farmacia no aprobo la orden."

$stockComponents = @()
$stockOrdinal = 0
foreach ($drug in @($workflow.applicationDrugs)) {
  $stockOrdinal++
  $drugName = [string]$drug.drugName
  $unit = [string]$drug.doseUnit
  $doseText = [string]$drug.calculatedDoseText
  if ([string]::IsNullOrWhiteSpace($doseText)) {
    $doseText = [string]$drug.prescribedDoseText
  }
  $doseMatch = [regex]::Match($doseText, '[+-]?\d+(?:[\.,]\d+)?')
  Assert-True (-not [string]::IsNullOrWhiteSpace($drugName)) "La orden contiene una droga sin nombre."
  Assert-True (-not [string]::IsNullOrWhiteSpace($unit)) "La orden contiene una droga sin unidad."
  Assert-True ($doseMatch.Success) "La orden contiene una dosis no cuantificable para $drugName."
  $quantity = [decimal]::Parse(
    $doseMatch.Value.Replace(',', '.'),
    [System.Globalization.CultureInfo]::InvariantCulture
  )
  $drugId = [string]$drug.drugId
  $componentKey = Get-ApplicationComponentKey -Drug $drug -Ordinal $stockOrdinal
  $stockComponents += @{
    componentKey = $componentKey
    drugId = $drugId
    drugName = $drugName
    requestedQuantity = $quantity
    requestedQuantityText = "$quantity $unit"
    unit = $unit
    inventoryLotId = $null
  }
}
Assert-True ($stockComponents.Count -ge 2) "La prueba de seguridad requiere un protocolo multidroga."

$stockReservation = Invoke-HcopJson -Method POST -Path "$workflowPath/stock-reservation" -Body @{
  expectedRevision = [long]$workflow.revision
  idempotencyKey = "e2e.stock.$suffix.$cycleNumber.$applicationDay"
  reserved = $true
  medicationSource = "center_stock"
  verificationMethod = "manual"
  notes = "Disponibilidad fisica verificada en Farmacia."
  components = $stockComponents
}
$workflow = $stockReservation.workflow
Assert-True ($workflow.stockReservationStatus -eq "reserved") "No se reservo el stock."

$durationForSchedule = if ([int]$workflow.durationMinutes -gt 0) {
  [int]$workflow.durationMinutes
} else {
  60
}
$settingsPayload = Invoke-HcopJson -Path "/api/clinical/configuration/day-hospital-settings"
$settings = @($settingsPayload.items | Where-Object { $_.active -ne $false } | Select-Object -First 1)[0].definition
$chairCount = if ([int]$settings.chairCount -gt 0) { [int]$settings.chairCount } else { 6 }
$slotMinutes = if ([int]$settings.slotMinutes -gt 0) { [int]$settings.slotMinutes } else { 10 }
$startParts = ([string]$(if ($settings.startTime) { $settings.startTime } else { "08:00" })).Split(":")
$endParts = ([string]$(if ($settings.endTime) { $settings.endTime } else { "16:00" })).Split(":")
$startMinute = ([int]$startParts[0] * 60) + [int]$startParts[1]
$endMinute = ([int]$endParts[0] * 60) + [int]$endParts[1]
$clinicalTimeZone = $null
foreach ($timeZoneId in @("America/Argentina/Buenos_Aires", "Argentina Standard Time")) {
  try {
    $clinicalTimeZone = [TimeZoneInfo]::FindSystemTimeZoneById($timeZoneId)
    break
  } catch {
    $clinicalTimeZone = $null
  }
}
Assert-True ($null -ne $clinicalTimeZone) "No se encontro la zona horaria clinica de Argentina."
$nowUtc = [DateTimeOffset]::UtcNow
$todayInClinicalTimeZone = [TimeZoneInfo]::ConvertTime($nowUtc, $clinicalTimeZone).Date
$localScheduledAt = $null
$selectedChair = ""

# La prueba reproduce una jornada clínica completa en la fecha operativa actual.
# Puede ejecutarse después del cierre, por lo que no exige que el casillero sea futuro.
for ($dayOffset = 0; $dayOffset -le 0 -and $null -eq $localScheduledAt; $dayOffset++) {
  $scheduleDate = $todayInClinicalTimeZone.AddDays($dayOffset)
  $existingPayload = Invoke-HcopJson -Path "/api/clinical/infusions?date=$($scheduleDate.ToString('yyyy-MM-dd'))"
  $existing = @($existingPayload.infusions | Where-Object {
    [string]$_.clinicalStatus -ne "cancelled" -and
    -not [string]::IsNullOrWhiteSpace([string]$_.scheduledAt)
  })
  for ($chairNumber = 1; $chairNumber -le $chairCount -and $null -eq $localScheduledAt; $chairNumber++) {
    for ($minute = $startMinute; $minute + $durationForSchedule -le $endMinute; $minute += $slotMinutes) {
      $candidateLocal = [DateTime]::SpecifyKind(
        $scheduleDate.AddMinutes($minute),
        [DateTimeKind]::Unspecified
      )
      $candidateStart = [DateTimeOffset]::new(
        $candidateLocal,
        $clinicalTimeZone.GetUtcOffset($candidateLocal)
      )
      $candidateEnd = $candidateStart.AddMinutes($durationForSchedule)
      $overlap = @($existing | Where-Object {
        if ([string]$_.chair -ne [string]$chairNumber) { return $false }
        $existingStart = [DateTimeOffset]::Parse([string]$_.scheduledAt)
        $existingDuration = if ([int]$_.durationMinutes -gt 0) { [int]$_.durationMinutes } else { $slotMinutes }
        $existingEnd = $existingStart.AddMinutes($existingDuration)
        return $candidateStart -lt $existingEnd -and $existingStart -lt $candidateEnd
      }).Count -gt 0
      if (-not $overlap) {
        $localScheduledAt = $candidateStart
        $selectedChair = [string]$chairNumber
        break
      }
    }
  }
}
Assert-True ($null -ne $localScheduledAt) "No se encontro un espacio libre de prueba en la jornada operativa actual."
$scheduledAt = ([DateTimeOffset]$localScheduledAt).ToUniversalTime().ToString("o")
$createdInfusion = Invoke-HcopJson -Method POST -Path "/api/clinical/infusions" -Body @{
  patientId = $patientId
  treatmentId = $treatmentId
  cycleNumber = $cycleNumber
  applicationDay = $applicationDay
  scheduledAt = $scheduledAt
  chair = $selectedChair
  durationMinutes = $durationForSchedule
  clinicalStatus = "planned"
  pharmacyStatus = "pending"
  administrationStatus = "not_started"
  appointmentConfirmed = $true
  sourceRef = @{ scheduler = @{ prescriptionConfirmed = $true; medicationReceived = $false } }
}
$infusion = $createdInfusion.infusion
Assert-True (-not [string]::IsNullOrWhiteSpace([string]$infusion.id)) "No se creo el turno."

$workflow = (Invoke-HcopJson -Path $workflowPath).workflow
$triage = Invoke-HcopJson -Method POST -Path "$workflowPath/clinical-authorization" -Body @{
  expectedRevision = [long]$workflow.revision
  idempotencyKey = "e2e.triage.$suffix.$cycleNumber.$applicationDay"
  decision = "PASS"
  laboratory = @{
    date = $today
    neutrophils = 4000
    platelets = 200000
    creatinine = 0.9
    hepaticFunction = "Sin alteraciones relevantes"
  }
  vitalSigns = @{
    weightKg = 75
    bloodPressure = "120/80"
    temperatureC = 36.5
    heartRate = 75
    oxygenSaturation = 98
  }
  toxicity = @{
    grade = 0
    ecog = 0
    notes = "Sin toxicidad limitante"
  }
  reason = ""
  rescheduledDate = $null
}
$workflow = $triage.workflow
Assert-True ($workflow.clinicalAuthorizationStatus -eq "passed") "El triaje no emitio PASS."

$preparationUsers = Invoke-HcopJson -Path "/api/clinical/users?capability=application.preparation.manage"
$preparationVerifier = @($preparationUsers.items) |
  Where-Object {
    $_.active -eq $true -and
    [string]$_.id -ne [string]$login.user.id
  } |
  Select-Object -First 1
Assert-True ($null -ne $preparationVerifier) "No existe un segundo profesional para verificar la preparacion."

$preparationStarted = Invoke-HcopJson -Method POST -Path "$workflowPath/preparation/start" -Body @{
  expectedRevision = [long]$workflow.revision
  idempotencyKey = "e2e.prep-start.$suffix.$cycleNumber.$applicationDay"
  notes = "Inicio de preparacion integral de prueba."
}
$workflow = $preparationStarted.workflow
Assert-True ($workflow.preparationStatus -eq "in_preparation") "No se inicio la preparacion."

$preparations = @()
$ordinal = 0
foreach ($drug in @($workflow.applicationDrugs)) {
  $ordinal++
  $drugName = [string]$drug.drugName
  $componentKey = Get-ApplicationComponentKey -Drug $drug -Ordinal $ordinal
  $reservation = @($workflow.stockReservations) |
    Where-Object {
      [string]$_.status -eq "reserved" -and
      [string]$_.componentKey -eq $componentKey
    } |
    Select-Object -First 1
  Assert-True ($null -ne $reservation) "No se encontro la reserva de $drugName ($componentKey)."
  $unit = if ([string]::IsNullOrWhiteSpace([string]$reservation.unit)) { "mg" } else { [string]$reservation.unit }
  $quantity = if ($null -eq $reservation.reservedQuantity) { [decimal]1 } else { [decimal]$reservation.reservedQuantity }
  $quantityText = if ([string]::IsNullOrWhiteSpace([string]$reservation.requestedQuantityText)) {
    "$quantity $unit"
  } else {
    [string]$reservation.requestedQuantityText
  }
  $preparations += @{
    drugName = $drugName
    lot = "E2E-$suffix-$ordinal"
    expiryDate = (Get-Date).AddYears(1).ToString("yyyy-MM-dd")
    quantity = $quantity
    quantityText = $quantityText
    unit = $unit
    diluent = "Solucion fisiologica"
    finalVolume = "250 ml"
    concentration = "Concentracion de prueba"
    ttlMinutes = 240
    reservationId = [string]$reservation.id
    inventoryLotId = $null
  }
}
Assert-True ($preparations.Count -ge 2) "La prueba de seguridad requiere al menos dos drogas preparables."

$preparationCompleted = Invoke-HcopJson -Method POST -Path "$workflowPath/preparation/complete" -Body @{
  expectedRevision = [long]$workflow.revision
  idempotencyKey = "e2e.prepared.$suffix.$cycleNumber.$applicationDay"
  verifiedBy = [string]$preparationVerifier.id
  preparations = $preparations
  notes = "Preparacion trazable verificada."
}
$workflow = $preparationCompleted.workflow
Assert-True ($workflow.preparationStatus -eq "prepared") "No se completo la preparacion."

$preparationReleased = Invoke-HcopJson -Method POST -Path "$workflowPath/preparation/release" -Body @{
  expectedRevision = [long]$workflow.revision
  idempotencyKey = "e2e.release.$suffix.$cycleNumber.$applicationDay"
  notes = "Mezcla liberada a sala."
}
$workflow = $preparationReleased.workflow
Assert-True ($workflow.preparationStatus -eq "released") "La mezcla no fue liberada."

$qrHtml = (Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/clinical/patients/$patientId/treatments/$treatmentId/documents/qr?cycle=$cycleNumber&applicationDay=$applicationDay" -WebSession $script:WebSession -Headers @{ Accept = "text/html" }).Content
$qrMatch = [regex]::Match($qrHtml, '<p class="code">([^<]+)</p>')
Assert-True ($qrMatch.Success) "No se genero el QR."
$scan = Invoke-HcopJson -Method POST -Path "/api/clinical/qr-scans" -Body @{
  code = [System.Net.WebUtility]::HtmlDecode($qrMatch.Groups[1].Value)
  operationId = "e2e-$suffix"
}
Assert-True ([string]$scan.infusion.id -eq [string]$infusion.id) "El QR no abrio el turno esperado."

$administrationUsers = Invoke-HcopJson -Path "/api/clinical/users?capability=application.administration.manage"
$administrationVerifier = @($administrationUsers.items) |
  Where-Object {
    $_.active -eq $true -and
    [string]$_.id -ne [string]$login.user.id
  } |
  Select-Object -First 1
Assert-True ($null -ne $administrationVerifier) "No existe un segundo profesional para la administracion."

$administrationStarted = Invoke-HcopJson -Method POST -Path "$workflowPath/administration/start" -Body @{
  expectedRevision = [long]$workflow.revision
  idempotencyKey = "e2e.admin-start.$suffix.$cycleNumber.$applicationDay"
  patientVerified = $true
  labelVerified = $true
  doubleCheckBy = [string]$administrationVerifier.id
  startedAt = [DateTimeOffset]::UtcNow.ToString("o")
  notes = "Paciente y etiqueta verificados."
}
$workflow = $administrationStarted.workflow
Assert-True ($workflow.administrationStatus -eq "in_progress") "No se inicio la administracion."

$interruptedDrug = [string](@($workflow.applicationDrugs)[1].drugName)
$partialDose = "${interruptedDrug}: 50% de la dosis prescripta"
$administrationInterrupted = Invoke-HcopJson -Method POST -Path "$workflowPath/administration/interrupt" -Body @{
  expectedRevision = [long]$workflow.revision
  idempotencyKey = "e2e.admin-interrupt.$suffix.$cycleNumber.$applicationDay"
  interruptedAt = [DateTimeOffset]::UtcNow.ToString("o")
  reason = "Rubor transitorio durante la infusion de prueba."
  actualDose = $partialDose
  measures = "Pausa de la infusion, control de signos vitales y reevaluacion."
  patientCondition = "Estable y sin compromiso respiratorio."
  disposition = "observation"
}
$workflow = $administrationInterrupted.workflow
Assert-True ($workflow.administrationStatus -eq "withheld") "La reaccion no interrumpio la administracion."
Assert-True ($workflow.administrationData.interruptionPending -eq $true) "La interrupcion no quedo pendiente de resolver."
Assert-True ([string]$workflow.administrationData.actualDoseAtInterruption -eq $partialDose) "No se conservo la droga y dosis parcial."
Assert-True (@($workflow.administrationData.interruptions).Count -ge 1) "No se conservo el historial de interrupciones."

$administrationResumed = Invoke-HcopJson -Method POST -Path "$workflowPath/administration/resolve" -Body @{
  expectedRevision = [long]$workflow.revision
  idempotencyKey = "e2e.admin-resume.$suffix.$cycleNumber.$applicationDay"
  resolvedAt = [DateTimeOffset]::UtcNow.ToString("o")
  decision = "resume"
  notes = "Reevaluacion favorable; se reinicia a menor velocidad bajo observacion."
  actualDose = ""
  patientCondition = "Estable, asintomatico y con signos vitales conservados."
}
$workflow = $administrationResumed.workflow
Assert-True ($workflow.administrationStatus -eq "in_progress") "No se reanudo la administracion."
Assert-True ($workflow.administrationData.interruptionPending -eq $false) "La interrupcion continuo pendiente tras reanudar."

$finalDoseSummary = (@($workflow.applicationDrugs | ForEach-Object {
  $dose = [string]$_.calculatedDoseText
  if ([string]::IsNullOrWhiteSpace($dose)) { $dose = [string]$_.prescribedDoseText }
  "$([string]$_.drugName) $dose".Trim()
}) -join " + ")
$administrationCompleted = Invoke-HcopJson -Method POST -Path "$workflowPath/administration/complete" -Body @{
  expectedRevision = [long]$workflow.revision
  idempotencyKey = "e2e.admin-complete.$suffix.$cycleNumber.$applicationDay"
  completedAt = [DateTimeOffset]::UtcNow.ToString("o")
  actualDose = $finalDoseSummary
  reactionOccurred = $true
  reactionDescription = "Rubor transitorio durante la infusion; resuelto tras pausa, control y reevaluacion."
  observation = "Aplicacion completada luego de reanudar a menor velocidad, con paciente estable."
}
$workflow = $administrationCompleted.workflow
Assert-True ($workflow.administrationStatus -eq "completed") "No se finalizo la administracion."
Assert-True ($workflow.workflowStatus -eq "completed") "El flujo no quedo completado."
Assert-True ($workflow.administrationData.reactionOccurred -eq $true) "El cierre perdio la reaccion documentada."
Assert-True (-not [string]::IsNullOrWhiteSpace([string]$workflow.administrationData.reactionDescription)) "El cierre perdio la descripcion de la reaccion."

$detail = Invoke-HcopJson -Path "/api/clinical/patients/$patientId/treatments/$treatmentId/detail"
$cycleOne = @($detail.detail.cycles) | Where-Object { [int]$_.number -eq 1 } | Select-Object -First 1
Assert-True (@($cycleOne.drugs).Count -ge 1) "El detalle del ciclo no incorporo las drogas del protocolo."
Assert-True (@($cycleOne.applications).Count -eq 1) "El detalle no incorporo el turno real."

$sheet = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/clinical/patients/$patientId/treatments/$treatmentId/documents/treatment-sheet?cycle=1" -WebSession $script:WebSession -Headers @{ Accept = "text/html" }
Assert-True ($sheet.StatusCode -eq 200 -and $sheet.Content -match "Hoja de tratamiento") "No se genero la hoja de tratamiento."

$finalHistory = Invoke-HcopJson -Path "/api/hc"
Assert-True (@($finalHistory.evolutions).Count -ge 3) "Los actos clinicos no quedaron documentados."

$sessionBeforeConfiguration = Invoke-HcopJson -Path "/api/auth/me"
Assert-True ([string]$sessionBeforeConfiguration.activePatientId -eq $patientId) "La sesion no conservo el paciente abierto."
$configurationPage = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/configuration/" -WebSession $script:WebSession
Assert-True ($configurationPage.StatusCode -eq 200) "No se pudo navegar a Configuracion."
$sessionAfterConfiguration = Invoke-HcopJson -Path "/api/auth/me"
Assert-True ([string]$sessionAfterConfiguration.activePatientId -eq $patientId) "Configuracion libero el paciente activo."

$closedPatient = Invoke-HcopJson -Method PUT -Path "/api/auth/active-patient" -Body @{ patientId = $null }
Assert-True ([string]::IsNullOrWhiteSpace([string]$closedPatient.activePatientId)) "No se cerro el paciente activo."
$blankHistory = Invoke-HcopJson -Path "/api/hc"
Assert-True ([string]::IsNullOrWhiteSpace([string]$blankHistory.patient.fullName)) "La hoja no quedo en blanco al cerrar el paciente."
$reopenedPatient = Invoke-HcopJson -Method POST -Path "/api/clinical/patients/$patientId/activate" -Body @{}
Assert-True ([string]$reopenedPatient.state.meta.liraImport.patientId -eq $patientId) "No se pudo volver a abrir el paciente cerrado."

[pscustomobject]@{
  ok = $true
  engine = $runtime.engine
  patientId = $patientId
  treatmentId = $treatmentId
  infusionId = [string]$infusion.id
  cycleNumber = $cycleNumber
  applicationDay = $applicationDay
  workflowStatus = [string]$workflow.workflowStatus
  drugCount = @($workflow.applicationDrugs).Count
  interruptedDrug = $interruptedDrug
  partialDose = $partialDose
  interruptionResolution = [string]$workflow.administrationData.interruptionResolution
  evolutions = @($finalHistory.evolutions).Count
  protocol = [string]$scheme.nombre
} | ConvertTo-Json -Compress
