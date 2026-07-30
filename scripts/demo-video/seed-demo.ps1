[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "Medium")]
param(
    [Parameter(Mandatory = $true)]
    [System.Management.Automation.PSCredential]$Credential,

    [ValidateRange(1, 50)]
    [int]$CycleCount = 2,

    [ValidateNotNull()]
    [uri]$BaseUri = "http://localhost:5180"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Demo = [ordered]@{
    SeedKey          = "hcop-demo-seven-step-flow-v1"
    Dni              = "99000001"
    MedicalRecord    = "DEMO-FLUJO-99000001"
    FirstName        = "ANA"
    LastName         = "DEMO FLUJO"
    DiagnosisId      = "diagnosis-demo-colon-v1"
    ProtocolId       = "347"
    ApplicationDays  = @(1, 8, 15, 21)
}

if ($BaseUri.Host -notin @("localhost", "127.0.0.1", "::1")) {
    throw "Por seguridad, este seed solo admite un servidor local (localhost, 127.0.0.1 o ::1)."
}

$ApiRoot = $BaseUri.AbsoluteUri.TrimEnd("/")
$WebSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Get-HttpErrorDetails {
    param([System.Management.Automation.ErrorRecord]$ErrorRecord)

    $statusCode = 0
    $responseText = ""
    $response = $ErrorRecord.Exception.Response
    if ($null -ne $response) {
        try {
            $statusCode = [int]$response.StatusCode
        }
        catch {
            $statusCode = 0
        }

        try {
            if ($null -ne $response.Content) {
                $responseText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            }
            elseif ($null -ne $response.GetResponseStream()) {
                $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
                try {
                    $responseText = $reader.ReadToEnd()
                }
                finally {
                    $reader.Dispose()
                }
            }
        }
        catch {
            $responseText = ""
        }
    }
    if ([string]::IsNullOrWhiteSpace($responseText) -and $ErrorRecord.ErrorDetails) {
        $responseText = [string]$ErrorRecord.ErrorDetails.Message
    }

    return [pscustomobject]@{
        StatusCode = $statusCode
        Body       = $responseText
    }
}

function Invoke-HcopRequest {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("GET", "POST", "PUT", "PATCH")]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [AllowNull()]
        [object]$Body
    )

    $parameters = @{
        Uri         = "$ApiRoot$Path"
        Method      = $Method
        WebSession  = $WebSession
        Headers     = @{ Accept = "application/json" }
        ErrorAction = "Stop"
    }
    if ($PSBoundParameters.ContainsKey("Body") -and $null -ne $Body) {
        $parameters.ContentType = "application/json; charset=utf-8"
        $jsonBody = $Body | ConvertTo-Json -Depth 100 -Compress
        $parameters.Body = [System.Text.Encoding]::UTF8.GetBytes($jsonBody)
    }

    try {
        # Windows PowerShell 5.1 interpreta como ANSI algunos JSON UTF-8 si la
        # respuesta no declara charset. Leer los bytes evita corromper acentos
        # al recuperar y volver a guardar el documento clínico completo.
        $response = Invoke-WebRequest @parameters -UseBasicParsing
        $stream = $response.RawContentStream
        if ($null -eq $stream) {
            if ([string]::IsNullOrWhiteSpace([string]$response.Content)) {
                return $null
            }
            return ([string]$response.Content | ConvertFrom-Json)
        }
        if ($stream.CanSeek) {
            $stream.Position = 0
        }
        $buffer = New-Object System.IO.MemoryStream
        try {
            $stream.CopyTo($buffer)
            $responseText = [System.Text.Encoding]::UTF8.GetString(
                $buffer.ToArray())
        }
        finally {
            $buffer.Dispose()
        }
        if ([string]::IsNullOrWhiteSpace($responseText)) {
            return $null
        }
        return ($responseText | ConvertFrom-Json)
    }
    catch {
        $details = Get-HttpErrorDetails -ErrorRecord $_
        $message = "HCOP respondio con HTTP $($details.StatusCode) en $Method $Path"
        if (-not [string]::IsNullOrWhiteSpace($details.Body)) {
            try {
                $payload = $details.Body | ConvertFrom-Json
                $apiMessage = [string]$payload.error
                if ([string]::IsNullOrWhiteSpace($apiMessage)) {
                    $apiMessage = [string]$payload.message
                }
                if (-not [string]::IsNullOrWhiteSpace($apiMessage)) {
                    $message = "$message`: $apiMessage"
                }
            }
            catch {
                $message = "$message`: $($details.Body)"
            }
        }
        $exception = [System.InvalidOperationException]::new($message, $_.Exception)
        $exception.Data["StatusCode"] = $details.StatusCode
        $exception.Data["ResponseBody"] = $details.Body
        throw $exception
    }
}

function Get-PropertyValue {
    param(
        [AllowNull()]
        [object]$InputObject,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [AllowNull()]
        [object]$Default = $null
    )

    if ($null -eq $InputObject) {
        return $Default
    }
    $property = $InputObject.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        return $Default
    }
    return $property.Value
}

function Set-ObjectProperty {
    param(
        [Parameter(Mandatory = $true)]
        [object]$InputObject,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [AllowNull()]
        [object]$Value
    )

    $property = $InputObject.PSObject.Properties[$Name]
    if ($null -eq $property) {
        $InputObject | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    }
    else {
        $property.Value = $Value
    }
}

function Find-DemoPatient {
    $query = [uri]::EscapeDataString($Demo.Dni)
    $response = Invoke-HcopRequest -Method GET -Path "/api/clinical/patients?q=$query"
    $matches = @(
        @($response.patients) | Where-Object {
            [string](Get-PropertyValue $_ "numeroDocumento" "") -eq $Demo.Dni
        }
    )
    if ($matches.Count -gt 1) {
        throw "Hay mas de un paciente con el DNI ficticio $($Demo.Dni). No se modifico ninguno."
    }
    return $matches | Select-Object -First 1
}

function Get-ApplicationInfusion {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PatientId,
        [Parameter(Mandatory = $true)]
        [string]$TreatmentId
    )

    $response = Invoke-HcopRequest -Method GET `
        -Path "/api/clinical/infusions?patientId=$([uri]::EscapeDataString($PatientId))"
    return @($response.infusions) | Where-Object {
        [string]$_.treatmentId -eq $TreatmentId -and
        [int]$_.cycleNumber -eq 1 -and
        [int]$_.applicationDay -eq 1 -and
        [string]$_.clinicalStatus -ne "cancelled" -and
        [string]$_.administrationStatus -ne "cancelled"
    } | Select-Object -First 1
}

function ConvertTo-ChairKey {
    param([AllowNull()][object]$Chair)

    $text = [string]$Chair
    if ($text -match "(\d+)") {
        return $Matches[1]
    }
    return $text.Trim().ToLowerInvariant()
}

function Get-FreeAppointmentSlots {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Date,
        [Parameter(Mandatory = $true)]
        [int]$DurationMinutes
    )

    $chairCount = 6
    $slotMinutes = 10
    $startTime = "08:00"
    $endTime = "16:00"
    try {
        $settingsResponse = Invoke-HcopRequest -Method GET `
            -Path "/api/clinical/configuration/day-hospital-settings"
        $settings = @($settingsResponse.items) |
            Where-Object { [bool](Get-PropertyValue $_ "active" $true) } |
            Select-Object -First 1
        $definition = Get-PropertyValue $settings "definition" $null
        if ($null -ne $definition) {
            $chairCount = [int](Get-PropertyValue $definition "chairCount" $chairCount)
            $slotMinutes = [int](Get-PropertyValue $definition "slotMinutes" $slotMinutes)
            $startTime = [string](Get-PropertyValue $definition "startTime" `
                (Get-PropertyValue $definition "workdayStart" $startTime))
            $endTime = [string](Get-PropertyValue $definition "endTime" `
                (Get-PropertyValue $definition "workdayEnd" $endTime))
        }
    }
    catch {
        Write-Verbose "No se pudo leer la configuracion; se usan 6 sillones, turnos de 10 minutos y jornada 08:00-16:00."
    }

    if ($chairCount -lt 1) { $chairCount = 6 }
    if ($slotMinutes -notin @(5, 10, 15, 20, 30)) { $slotMinutes = 10 }

    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    $style = [System.Globalization.DateTimeStyles]::None
    $localStart = [datetime]::ParseExact("$Date $startTime", "yyyy-MM-dd HH:mm", $culture, $style)
    $localEnd = [datetime]::ParseExact("$Date $endTime", "yyyy-MM-dd HH:mm", $culture, $style)
    $localStart = [datetime]::SpecifyKind($localStart, [System.DateTimeKind]::Unspecified)
    $localEnd = [datetime]::SpecifyKind($localEnd, [System.DateTimeKind]::Unspecified)
    $offset = [timespan]::FromHours(-3)
    $dayStart = [System.DateTimeOffset]::new($localStart, $offset)
    $dayEnd = [System.DateTimeOffset]::new($localEnd, $offset)

    $dateQuery = [uri]::EscapeDataString($Date)
    $scheduleResponse = Invoke-HcopRequest -Method GET `
        -Path "/api/clinical/infusions?date=$dateQuery"
    $busy = @($scheduleResponse.infusions) | Where-Object {
        [string]$_.clinicalStatus -ne "cancelled" -and
        [string]$_.administrationStatus -ne "cancelled" -and
        $null -ne $_.scheduledAt
    }

    $slots = New-Object System.Collections.Generic.List[object]
    for ($candidate = $dayStart;
        $candidate.AddMinutes($DurationMinutes) -le $dayEnd;
        $candidate = $candidate.AddMinutes($slotMinutes)) {
        $candidateEnd = $candidate.AddMinutes($DurationMinutes)
        for ($chair = 1; $chair -le $chairCount; $chair++) {
            $chairKey = [string]$chair
            $overlap = $false
            foreach ($appointment in $busy) {
                if ((ConvertTo-ChairKey $appointment.chair) -ne $chairKey) {
                    continue
                }
                $busyStart = [datetimeoffset]::Parse([string]$appointment.scheduledAt, $culture)
                $busyDuration = [int](Get-PropertyValue $appointment "durationMinutes" 60)
                if ($busyDuration -lt 1) { $busyDuration = 60 }
                $busyEnd = $busyStart.AddMinutes($busyDuration)
                if ($candidate -lt $busyEnd -and $candidateEnd -gt $busyStart) {
                    $overlap = $true
                    break
                }
            }
            if (-not $overlap) {
                $slots.Add([pscustomobject]@{
                    ScheduledAt = $candidate.ToUniversalTime().ToString("o")
                    Chair       = $chairKey
                })
            }
        }
    }
    return $slots
}

if (-not $PSCmdlet.ShouldProcess(
        "$ApiRoot (paciente ficticio DNI $($Demo.Dni))",
        "Crear o reutilizar el caso demostrativo sin eliminar datos")) {
    [ordered]@{
        ok        = $true
        dryRun    = $true
        baseUri   = $ApiRoot
        seedKey   = $Demo.SeedKey
        patient   = [ordered]@{
            fullName = "$($Demo.LastName), $($Demo.FirstName)"
            dni      = $Demo.Dni
        }
        treatment = [ordered]@{
            protocolId      = $Demo.ProtocolId
            cycleCount      = $CycleCount
            applicationDays = $Demo.ApplicationDays
        }
    } | ConvertTo-Json -Depth 10
    return
}

$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
    $Credential.Password)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $login = Invoke-HcopRequest -Method POST -Path "/api/auth/login" -Body @{
        username = $Credential.UserName
        password = $plainPassword
    }
}
finally {
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    $plainPassword = $null
}
if (-not [bool](Get-PropertyValue $login "authenticated" $false)) {
    throw "HCOP no confirmo la sesion autenticada."
}

$patientStatus = "reused"
$patient = Find-DemoPatient
if ($null -eq $patient) {
    try {
        $createdPatient = Invoke-HcopRequest -Method POST -Path "/api/clinical/patients" -Body @{
            firstName       = $Demo.FirstName
            lastName        = $Demo.LastName
            dni             = $Demo.Dni
            medicalRecord   = $Demo.MedicalRecord
            birthDate       = "1980-01-01"
            sex             = "Femenino"
            insurance       = "COBERTURA DEMOSTRACION"
            affiliateNumber = "DEMO-99000001"
            phone           = ""
            email           = ""
            address         = ""
        }
        $patient = [pscustomobject]@{
            id              = [string]$createdPatient.patientId
            fullName        = [string]$createdPatient.patient.fullName
            numeroDocumento = $Demo.Dni
            numeroHC        = $Demo.MedicalRecord
        }
        $patientStatus = "created"
    }
    catch {
        # Recuperacion segura ante timeout o una creacion concurrente.
        $patient = Find-DemoPatient
        if ($null -eq $patient) {
            throw $_.Exception
        }
        $patientStatus = "reused-after-create-response-error"
    }
}
$patientId = [string]$patient.id

Invoke-HcopRequest -Method PUT -Path "/api/auth/active-patient" -Body @{
    patientId = [long]$patientId
} | Out-Null

$today = [datetime]::Today.ToString("yyyy-MM-dd")
$nowIso = [datetimeoffset]::Now.ToString("o")
$document = Invoke-HcopRequest -Method GET -Path "/api/hc"
if ($null -eq (Get-PropertyValue $document "oncology" $null)) {
    Set-ObjectProperty -InputObject $document -Name "oncology" -Value ([pscustomobject]@{})
}
$oncology = $document.oncology
$diagnosisRecords = @(Get-PropertyValue $oncology "diagnosisRecords" @())
$diagnosis = $diagnosisRecords | Where-Object {
    [string](Get-PropertyValue $_ "id" "") -eq $Demo.DiagnosisId
} | Select-Object -First 1
if ($null -eq $diagnosis) {
    $diagnosis = $diagnosisRecords | Where-Object {
        $classifications = Get-PropertyValue $_ "diagnosticClassifications" $null
        $ajcc = Get-PropertyValue $classifications "ajcc" $null
        $snomed = Get-PropertyValue $classifications "snomed" $null
        $cie10 = Get-PropertyValue $classifications "cie10" $null
        [string](Get-PropertyValue $ajcc "code" "") -eq "colon" -and
        [string](Get-PropertyValue $snomed "code" "") -eq "363406005" -and
        [string](Get-PropertyValue $cie10 "code" "") -eq "C18.9"
    } | Select-Object -First 1
}

$diagnosisStatus = "reused"
if ($null -eq $diagnosis) {
    $ajccResponse = Invoke-HcopRequest -Method GET `
        -Path "/api/diagnosis-catalogs/search?system=ajcc&q=colon&limit=20"
    $snomedResponse = Invoke-HcopRequest -Method GET `
        -Path "/api/diagnosis-catalogs/search?system=snomed&q=colon&limit=100"
    $cie10Response = Invoke-HcopRequest -Method GET `
        -Path "/api/diagnosis-catalogs/search?system=cie10&q=colon&limit=100"
    $ajcc = @($ajccResponse.items) |
        Where-Object { [string]$_.code -eq "colon" } |
        Select-Object -First 1
    $snomed = @($snomedResponse.items) |
        Where-Object { [string]$_.code -eq "363406005" } |
        Select-Object -First 1
    $cie10 = @($cie10Response.items) |
        Where-Object { [string]$_.code -eq "C18.9" } |
        Select-Object -First 1
    if ($null -eq $ajcc -or $null -eq $snomed -or $null -eq $cie10) {
        throw "El catalogo local no contiene el mapeo completo de colon (AJCC/SNOMED/CIE-10)."
    }

    $stageResponse = Invoke-HcopRequest -Method POST -Path "/api/ajcc8/stage" -Body @{
        id     = "colon"
        values = @{
            T              = "T3"
            N              = "N1"
            M              = "M0"
            Classification = "c"
            DescY          = "No"
            DescR          = "No"
            DescM          = "No"
        }
    }
    if ([string]::IsNullOrWhiteSpace([string]$stageResponse.stage)) {
        throw "AJCC no pudo calcular el estadio del caso ficticio cT3 cN1 cM0."
    }

    $diagnosis = [pscustomobject][ordered]@{
        id                        = $Demo.DiagnosisId
        date                      = $today
        datePrecision             = "day"
        diagnosis                 = [string]$snomed.display
        topography                = [string]$ajcc.display
        histology                 = "Adenocarcinoma de colon (caso ficticio)"
        stage                     = [string]$stageResponse.stage
        diagnosticClassifications = [pscustomobject][ordered]@{
            ajcc   = [pscustomobject][ordered]@{
                system          = "AJCC"
                freeText        = [string]$ajcc.display
                code            = [string]$ajcc.code
                display         = [string]$ajcc.display
                version         = [string]$ajcc.version
                source          = "Catalogo AJCC 8 local"
                sourceConceptId = ""
                sourceDisplay   = [string]$ajcc.display
                mapAdvice       = ""
            }
            snomed = [pscustomobject][ordered]@{
                system          = "SNOMED CT"
                freeText        = [string]$snomed.display
                code            = [string]$snomed.code
                display         = [string]$snomed.display
                version         = [string]$snomed.version
                source          = "Catalogo terminologico local"
                sourceConceptId = [string]$snomed.sourceConceptId
                sourceDisplay   = [string]$snomed.display
                mapAdvice       = ""
            }
            cie10   = [pscustomobject][ordered]@{
                system          = "CIE-10"
                freeText        = [string]$cie10.display
                code            = [string]$cie10.code
                display         = [string]$cie10.display
                version         = [string]$cie10.version
                source          = "Equivalencias iniciales HCOP JP"
                sourceConceptId = [string]$cie10.sourceConceptId
                sourceDisplay   = [string]$cie10.display
                mapAdvice       = [string](Get-PropertyValue $cie10 "mapAdvice" "")
            }
        }
        tnm                       = [pscustomobject][ordered]@{
            t            = "T3"
            n            = "N1"
            m            = "M0"
            stage        = [string]$stageResponse.stage
            substage     = ""
            siteId       = "colon"
            siteDisplay  = [string]$ajcc.display
            prefix       = "c"
            date         = $today
            edition      = "AJCC 8"
            source       = "Catalogo AJCC 8 local"
            guideVersion = ""
            sourceRow    = Get-PropertyValue $stageResponse "sourceRow" $null
            calculatedAt = $nowIso
            values       = [pscustomobject][ordered]@{
                T              = "T3"
                N              = "N1"
                M              = "M0"
                Classification = "c"
                DescY          = "No"
                DescR          = "No"
                DescM          = "No"
            }
        }
        legacyProjection          = $false
        audit                     = [pscustomobject][ordered]@{
            action   = "cargado"
            at       = $nowIso
            lastName = "Seed demostrativo"
        }
        createdAt                 = $nowIso
    }

    Set-ObjectProperty -InputObject $oncology -Name "diagnosisRecords" `
        -Value @($diagnosisRecords + $diagnosis)
    Set-ObjectProperty -InputObject $oncology -Name "diagnosticClassifications" `
        -Value $diagnosis.diagnosticClassifications
    Set-ObjectProperty -InputObject $oncology -Name "tnm" -Value $diagnosis.tnm
    Set-ObjectProperty -InputObject $oncology -Name "diagnosis" -Value $diagnosis.diagnosis
    Set-ObjectProperty -InputObject $oncology -Name "diagnosisDate" -Value $today
    Set-ObjectProperty -InputObject $oncology -Name "diagnosisDatePrecision" -Value "day"
    Set-ObjectProperty -InputObject $oncology -Name "topography" -Value $diagnosis.topography
    Set-ObjectProperty -InputObject $oncology -Name "histology" -Value $diagnosis.histology
    Set-ObjectProperty -InputObject $oncology -Name "stage" -Value $diagnosis.stage

    try {
        $savedDocument = Invoke-HcopRequest -Method PUT -Path "/api/hc" -Body $document
        $documentRevision = [long]$savedDocument.unified.revision
        $diagnosisStatus = "created"
    }
    catch {
        # Si se perdio solo la respuesta, el reintento no duplica el diagnostico.
        $document = Invoke-HcopRequest -Method GET -Path "/api/hc"
        $diagnosis = @(Get-PropertyValue $document.oncology "diagnosisRecords" @()) |
            Where-Object { [string]$_.id -eq $Demo.DiagnosisId } |
            Select-Object -First 1
        if ($null -eq $diagnosis) {
            throw $_.Exception
        }
        $documentRevision = [long]$document.meta.persistenceRevision
        $diagnosisStatus = "reused-after-save-response-error"
    }
}
else {
    $documentRevision = [long]$document.meta.persistenceRevision
}

# Windows PowerShell 5.1 puede interpretar de forma ambigua respuestas JSON sin
# BOM. Mantener estas leyendas demostrativas en ASCII evita datos dañados y
# normaliza automáticamente un seed previo creado con otra codificación.
$diagnosisSourcesChanged = $false
$diagnosisClassifications = Get-PropertyValue $diagnosis `
    "diagnosticClassifications" $null
foreach ($repair in @(
        @{ Key = "ajcc"; Source = "Catalogo AJCC 8 local" },
        @{ Key = "snomed"; Source = "Catalogo terminologico local" },
        @{ Key = "cie10"; Source = "Equivalencias iniciales HCOP JP" }
    )) {
    $classification = Get-PropertyValue $diagnosisClassifications $repair.Key $null
    if ($null -ne $classification -and
        [string](Get-PropertyValue $classification "source" "") -ne $repair.Source) {
        Set-ObjectProperty -InputObject $classification -Name "source" `
            -Value $repair.Source
        $diagnosisSourcesChanged = $true
    }
}
$diagnosisTnm = Get-PropertyValue $diagnosis "tnm" $null
if ($null -ne $diagnosisTnm -and
    [string](Get-PropertyValue $diagnosisTnm "source" "") -ne "Catalogo AJCC 8 local") {
    Set-ObjectProperty -InputObject $diagnosisTnm -Name "source" `
        -Value "Catalogo AJCC 8 local"
    $diagnosisSourcesChanged = $true
}
if ($diagnosisSourcesChanged) {
    Set-ObjectProperty -InputObject $oncology -Name "diagnosticClassifications" `
        -Value $diagnosisClassifications
    Set-ObjectProperty -InputObject $oncology -Name "tnm" -Value $diagnosisTnm
    $savedDocument = Invoke-HcopRequest -Method PUT -Path "/api/hc" -Body $document
    $documentRevision = [long]$savedDocument.unified.revision
    $diagnosisStatus = "normalized"
}
$diagnosisId = [string]$diagnosis.id

Invoke-HcopRequest -Method PUT `
    -Path "/api/clinical/patients/$patientId/diagnosis" `
    -Body @{
        expectedRevision = $documentRevision
        diagnosisEntryId = $diagnosisId
    } | Out-Null

$options = Invoke-HcopRequest -Method GET `
    -Path "/api/clinical/patients/$patientId/treatment-options"
$protocol = @($options.options.schemes) |
    Where-Object { [string]$_.id -eq $Demo.ProtocolId } |
    Select-Object -First 1
if ($null -eq $protocol) {
    throw "El protocolo 347 no esta disponible para prescribir."
}

$treatmentsResponse = Invoke-HcopRequest -Method GET `
    -Path "/api/clinical/patients/$patientId/treatments"
$treatment = @($treatmentsResponse.treatments) | Where-Object {
    [string](Get-PropertyValue $_ "demoSeedKey" "") -eq $Demo.SeedKey
} | Select-Object -First 1
$treatmentStatus = "reused"
if ($null -eq $treatment) {
    try {
        $createdTreatment = Invoke-HcopRequest -Method POST `
            -Path "/api/clinical/patients/$patientId/treatments" `
            -Body @{
                diagnostico              = $diagnosisId
                esquema                  = $Demo.ProtocolId
                cantidadCiclos           = $CycleCount
                cicloInicial             = 1
                duracionCiclo            = [int](Get-PropertyValue $protocol "cycleDays" 42)
                fechaCreacion            = $today
                fechaPrimerCiclo         = $today
                tipoOncologico           = "Quimioterapia"
                caracter                 = "Paliativo"
                estadoConsentimiento     = "Pendiente"
                consentAvailable         = $false
                peso                     = "70"
                talla                    = "170"
                supCorporal              = "1.82"
                requirementsConfirmed    = $true
                protocolMismatchConfirmed = $false
                observaciones            = "Caso clinico enteramente ficticio para demostrar el circuito de siete pasos."
                clinicalEntryId           = $Demo.SeedKey
                demoSeedKey              = $Demo.SeedKey
            }
        $treatment = $createdTreatment.treatment
        $treatmentStatus = "created"
    }
    catch {
        # POST no posee idempotency key: recuperar por la marca estable antes de reintentar.
        $treatmentsResponse = Invoke-HcopRequest -Method GET `
            -Path "/api/clinical/patients/$patientId/treatments"
        $treatment = @($treatmentsResponse.treatments) | Where-Object {
            [string](Get-PropertyValue $_ "demoSeedKey" "") -eq $Demo.SeedKey
        } | Select-Object -First 1
        if ($null -eq $treatment) {
            throw $_.Exception
        }
        $treatmentStatus = "reused-after-create-response-error"
    }
}
$treatmentId = [string]$treatment.id

# Repara de forma idempotente una evolución demostrativa creada por versiones
# tempranas del seed que enviaban JSON con la codificación predeterminada de
# Windows PowerShell. Solo toca la evolución marcada con el SeedKey de este
# caso ficticio; nunca modifica evoluciones clínicas ajenas.
$demoDocument = Invoke-HcopRequest -Method GET -Path "/api/hc"
$demoEvolution = @(Get-PropertyValue $demoDocument "evolutions" @()) |
    Where-Object {
        $sourceRef = Get-PropertyValue $_ "sourceRef" $null
        [string](Get-PropertyValue $sourceRef "clinicalEntryId" "") -eq $Demo.SeedKey
    } |
    Select-Object -First 1
if ($null -ne $demoEvolution) {
    $accentA = [char]0x00E1
    $accentI = [char]0x00ED
    $accentO = [char]0x00F3
    $middleDot = [char]0x00B7
    $superscriptTwo = [char]0x00B2
    $treatmentEvolutionReason = "Alta de tratamiento oncol${accentO}gico"
    $treatmentEvolutionSpecialty = "Oncolog${accentI}a"
    $firstCycleDate = [string](Get-PropertyValue $treatment `
        "firstCycleDate" $today)
    try {
        $firstCycleDateDisplay = [datetime]::ParseExact(
            $firstCycleDate,
            "yyyy-MM-dd",
            [Globalization.CultureInfo]::InvariantCulture
        ).ToString("dd/MM/yyyy")
    }
    catch {
        $firstCycleDateDisplay = $firstCycleDate
    }
    $cie10 = Get-PropertyValue `
        (Get-PropertyValue $diagnosis "diagnosticClassifications" $null) `
        "cie10" $null
    $expectedEvolutionText = @(
        "$treatmentEvolutionReason."
        "Diagn${accentO}stico: $([string]$diagnosis.diagnosis) $middleDot CIE-10 $([string](Get-PropertyValue $cie10 'code' '')) $middleDot Estadio $([string]$diagnosis.stage)"
        "Car${accentA}cter: Paliativo"
        "Tipo de tratamiento: Quimioterapia"
        "Esquema: $([string]$protocol.name)"
        "Ciclos previstos: $CycleCount"
        "Ciclo inicial: 1"
        "Fecha prevista del primer ciclo: $firstCycleDateDisplay"
        "Consentimiento: Pendiente"
        "Peso: 70 kg"
        "Talla: 170 cm"
        "Superficie corporal: 1.82 m$superscriptTwo"
        "Observaciones: Caso cl${accentI}nico enteramente ficticio para demostrar el circuito de siete pasos."
        "Datos requeridos verificados: S${accentI}"
    ) -join "`n"
    $evolutionChanged = (
        [string](Get-PropertyValue $demoEvolution "reason" "") -ne
            $treatmentEvolutionReason -or
        [string](Get-PropertyValue $demoEvolution "specialty" "") -ne
            $treatmentEvolutionSpecialty -or
        [string](Get-PropertyValue $demoEvolution "text" "") -ne
            $expectedEvolutionText
    )
    if ($evolutionChanged) {
        Set-ObjectProperty -InputObject $demoEvolution -Name "reason" `
            -Value $treatmentEvolutionReason
        Set-ObjectProperty -InputObject $demoEvolution -Name "specialty" `
            -Value $treatmentEvolutionSpecialty
        Set-ObjectProperty -InputObject $demoEvolution -Name "text" `
            -Value $expectedEvolutionText
        Invoke-HcopRequest -Method PUT -Path "/api/hc" -Body $demoDocument |
            Out-Null
    }
}

# Verifica el arbol real ciclo -> dia -> aplicacion generado desde el protocolo 347.
$workflowByDay = [ordered]@{}
foreach ($applicationDay in $Demo.ApplicationDays) {
    $response = Invoke-HcopRequest -Method GET `
        -Path "/api/clinical/application-workflows/$patientId/$treatmentId/1/$applicationDay"
    $workflowByDay[[string]$applicationDay] = $response.workflow
}

$workflow = $workflowByDay["1"]

# La evolución final también puede haber sido leída y regrabada por un seed
# antiguo con una codificación incorrecta. Reconstruirla desde el workflow
# auditado conserva las horas, la dosis real y el segundo control registrados.
$applicationEvolution = @(Get-PropertyValue $demoDocument "evolutions" @()) |
    Where-Object {
        $sourceRef = Get-PropertyValue $_ "sourceRef" $null
        [string](Get-PropertyValue $sourceRef "kind" "") -eq
            "application-workflow" -and
        [string](Get-PropertyValue $sourceRef "treatmentId" "") -eq
            $treatmentId -and
        [string](Get-PropertyValue $sourceRef "event" "") -like
            "application-administration-complete-*"
    } |
    Select-Object -First 1
if ($null -ne $applicationEvolution -and
    [string](Get-PropertyValue $workflow "administrationStatus" "") -eq
        "completed") {
    $accentI = [char]0x00ED
    $accentO = [char]0x00F3
    $middleDot = [char]0x00B7
    $administration = Get-PropertyValue $workflow "administrationData" $null
    $reactionOccurred = [bool](Get-PropertyValue $administration `
        "reactionOccurred" $false)
    $reactionText = if ($reactionOccurred) {
        [string](Get-PropertyValue $administration "reactionDescription" "")
    }
    else {
        "No"
    }
    $applicationEvolutionReason =
        "Administraci${accentO}n de tratamiento"
    $applicationEvolutionSpecialty =
        "Oncolog${accentI}a / Hospital de d${accentI}a"
    $expectedApplicationEvolutionText = @(
        "Aplicaci${accentO}n completada."
        "Esquema: $([string]$workflow.scheme)"
        "Ciclo 1 $middleDot D${accentI}a 1"
        "Dosis administrada: $([string](Get-PropertyValue $administration 'actualDose' ''))"
        "Inicio: $([string](Get-PropertyValue $workflow 'administrationStartedAt' ''))"
        "Finalizaci${accentO}n: $([string](Get-PropertyValue $workflow 'administrationCompletedAt' ''))"
        "Reacci${accentO}n: $reactionText"
        "Observaci${accentO}n: $([string](Get-PropertyValue $administration 'observation' ''))"
        "Administr${accentO}: $([string](Get-PropertyValue $applicationEvolution 'author' ''))"
        "Segundo control: $([string](Get-PropertyValue $administration 'doubleCheckDisplayName' ''))"
    ) -join "`n"
    $applicationEvolutionChanged = (
        [string](Get-PropertyValue $applicationEvolution "reason" "") -ne
            $applicationEvolutionReason -or
        [string](Get-PropertyValue $applicationEvolution "specialty" "") -ne
            $applicationEvolutionSpecialty -or
        [string](Get-PropertyValue $applicationEvolution "text" "") -ne
            $expectedApplicationEvolutionText
    )
    if ($applicationEvolutionChanged) {
        Set-ObjectProperty -InputObject $applicationEvolution -Name "reason" `
            -Value $applicationEvolutionReason
        Set-ObjectProperty -InputObject $applicationEvolution -Name "specialty" `
            -Value $applicationEvolutionSpecialty
        Set-ObjectProperty -InputObject $applicationEvolution -Name "text" `
            -Value $expectedApplicationEvolutionText
        Invoke-HcopRequest -Method PUT -Path "/api/hc" -Body $demoDocument |
            Out-Null
    }
}

$pharmacyStatus = "reused"
$medicationSource = [string]$workflow.medicationSource
$stockStatus = [string]$workflow.stockReservationStatus
$schedulingEligible = (
    [string]$workflow.pharmacyValidationStatus -eq "approved" -and (
        $medicationSource -in @("patient_to_bring", "patient_has_medication", "received_center") -or
        ($medicationSource -eq "center_stock" -and $stockStatus -eq "reserved")
    )
)
if (-not $schedulingEligible) {
    $pharmacyResponse = Invoke-HcopRequest -Method POST `
        -Path "/api/clinical/application-workflows/$patientId/$treatmentId/1/1/pharmacy-validation" `
        -Body @{
            expectedRevision = [long]$workflow.revision
            idempotencyKey   = "demo-pharmacy-$patientId-$treatmentId-1-1"
            validated        = $true
            medicationSource = "patient_to_bring"
            notes            = "Orden demo auditada. El paciente debe traer la medicacion."
        }
    $workflow = $pharmacyResponse.workflow
    $pharmacyStatus = if ([bool]$pharmacyResponse.idempotentReplay) {
        "idempotent-replay"
    }
    else {
        "approved-patient-to-bring"
    }
}

$appointment = Get-ApplicationInfusion -PatientId $patientId -TreatmentId $treatmentId
$appointmentStatus = "reused"
if ($null -eq $appointment) {
    $durationMinutes = [int](Get-PropertyValue $workflow "durationMinutes" `
        (Get-PropertyValue $treatment "durationMinutes" 120))
    if ($durationMinutes -lt 1) { $durationMinutes = 120 }
    $slots = Get-FreeAppointmentSlots -Date $today -DurationMinutes $durationMinutes
    if (@($slots).Count -eq 0) {
        throw "No existe un bloque libre hoy para una aplicacion de $durationMinutes minutos."
    }

    $lastScheduleError = $null
    foreach ($slot in $slots) {
        try {
            $createdAppointment = Invoke-HcopRequest -Method POST `
                -Path "/api/clinical/infusions" `
                -Body @{
                    patientId           = [long]$patientId
                    treatmentId         = $treatmentId
                    cycleNumber         = 1
                    applicationDay       = 1
                    scheduledAt         = [string]$slot.ScheduledAt
                    chair               = $slot.Chair
                    durationMinutes      = $durationMinutes
                    clinicalStatus       = "planned"
                    pharmacyStatus       = "pending"
                    administrationStatus = "not_started"
                    appointmentConfirmed = $false
                    notes                = "Turno ficticio creado por $($Demo.SeedKey)."
                    sourceRef            = @{
                        demoSeedKey = $Demo.SeedKey
                        scheduler   = @{
                            applicationDay       = 1
                            durationSource       = [string]$workflow.durationSource
                            drugScheme           = [string]$workflow.drugScheme
                            prescriptionConfirmed = $true
                            medicationReceived   = $false
                            medicationWithPatient = $false
                            appointmentConfirmed = $false
                        }
                    }
                    medications         = @($workflow.applicationDrugs)
                }
            $appointment = $createdAppointment.infusion
            $appointmentStatus = "created"
            break
        }
        catch {
            $lastScheduleError = $_
            # POST tampoco tiene idempotency key: comprobar si se creo pese a perder la respuesta.
            $appointment = Get-ApplicationInfusion `
                -PatientId $patientId -TreatmentId $treatmentId
            if ($null -ne $appointment) {
                $appointmentStatus = "reused-after-create-response-error"
                break
            }
            if ([int]$_.Exception.Data["StatusCode"] -ne 409) {
                throw $_.Exception
            }
            # Otro usuario ocupo el bloque entre la consulta y el alta: probar el siguiente.
        }
    }
    if ($null -eq $appointment) {
        throw $lastScheduleError
    }
}

$result = [ordered]@{
    ok        = $true
    dryRun    = $false
    baseUri   = $ApiRoot
    seedKey   = $Demo.SeedKey
    patient   = [ordered]@{
        id       = $patientId
        status   = $patientStatus
        fullName = "$($Demo.LastName), $($Demo.FirstName)"
        dni      = $Demo.Dni
    }
    diagnosis = [ordered]@{
        id       = $diagnosisId
        status   = $diagnosisStatus
        display  = [string]$diagnosis.diagnosis
        snomed   = [string]$diagnosis.diagnosticClassifications.snomed.code
        cie10    = [string]$diagnosis.diagnosticClassifications.cie10.code
        ajcc     = [string]$diagnosis.diagnosticClassifications.ajcc.code
        tnm      = "c$($diagnosis.tnm.t) $($diagnosis.tnm.n) $($diagnosis.tnm.m)"
        stage    = [string]$diagnosis.stage
        revision = $documentRevision
    }
    treatment = [ordered]@{
        id              = $treatmentId
        status          = $treatmentStatus
        protocolId      = $Demo.ProtocolId
        protocolName    = [string]$protocol.nombre
        cycleCount      = [int]$treatment.cycleCount
        applicationDays = $Demo.ApplicationDays
        durationMinutes = [int]$workflow.durationMinutes
    }
    pharmacy  = [ordered]@{
        status                   = $pharmacyStatus
        validation               = [string]$workflow.pharmacyValidationStatus
        medicationSource         = [string]$workflow.medicationSource
        patientMustBringMedication = [bool]$workflow.patientMustBringMedication
        revision                 = [long]$workflow.revision
    }
    appointment = [ordered]@{
        id          = [string]$appointment.id
        status      = $appointmentStatus
        requestedDate = $today
        scheduledAt = [string]$appointment.scheduledAt
        chair       = [string]$appointment.chair
        durationMinutes = [int]$appointment.durationMinutes
        confirmed   = [bool]$appointment.appointmentConfirmed
    }
}

$result | ConvertTo-Json -Depth 12
