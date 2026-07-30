param(
  [string]$MavenExecutable = "",
  [string]$JavaHome = "",
  [string]$MavenRepository = "",
  [string]$BuildDirectory = ""
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
if ([string]::IsNullOrWhiteSpace($BuildDirectory)) {
  $BuildDirectory = Join-Path $projectRoot "target-qa-concurrency"
}
$BuildDirectory = [System.IO.Path]::GetFullPath($BuildDirectory)

if ([string]::IsNullOrWhiteSpace($MavenExecutable)) {
  $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
  if ($null -eq $mavenCommand) {
    $mavenCommand = Get-Command mvn -ErrorAction SilentlyContinue
  }
  if ($null -ne $mavenCommand) {
    $MavenExecutable = $mavenCommand.Source
  } else {
    $knownMaven = Get-ChildItem "C:\Proyectos\VM\tools\maven" `
      -Filter "mvn.cmd" -Recurse -File -ErrorAction SilentlyContinue |
      Sort-Object FullName -Descending |
      Select-Object -First 1
    if ($null -ne $knownMaven) { $MavenExecutable = $knownMaven.FullName }
  }
}
if ([string]::IsNullOrWhiteSpace($MavenExecutable) -or
    -not (Test-Path -LiteralPath $MavenExecutable -PathType Leaf)) {
  throw "No se encontró Maven. Indíquelo con -MavenExecutable."
}

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
  if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME) -and
      (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $JavaHome = $env:JAVA_HOME
  } else {
    $knownJava = Get-ChildItem "C:\Proyectos\VM\tools\jdk-21" `
      -Filter "java.exe" -Recurse -File -ErrorAction SilentlyContinue |
      Where-Object { $_.FullName -match '\\bin\\java\.exe$' } |
      Sort-Object FullName -Descending |
      Select-Object -First 1
    if ($null -ne $knownJava) {
      $JavaHome = Split-Path -Parent (Split-Path -Parent $knownJava.FullName)
    }
  }
}
if ([string]::IsNullOrWhiteSpace($JavaHome) -or
    -not (Test-Path -LiteralPath (Join-Path $JavaHome "bin\java.exe"))) {
  throw "No se encontró Java 21. Indíquelo con -JavaHome."
}

if ([string]::IsNullOrWhiteSpace($MavenRepository)) {
  $knownRepository = "C:\Users\Marco\.m2\repository"
  if (Test-Path -LiteralPath $knownRepository -PathType Container) {
    $MavenRepository = $knownRepository
  }
}

$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:Path
try {
  $env:JAVA_HOME = $JavaHome
  $env:Path = "$(Join-Path $JavaHome 'bin');$previousPath"
  $arguments = @(
    "-o",
    "-Dhcop.build.directory=$BuildDirectory",
    "-Dtest=HospitalDayConcurrencySafetyTest",
    "test"
  )
  if (-not [string]::IsNullOrWhiteSpace($MavenRepository)) {
    $arguments = @("-Dmaven.repo.local=$MavenRepository") + $arguments
  }

  Write-Host "Pruebas aisladas FAR-25 / TUR-25. No se conecta a 5180 ni modifica datos."
  & $MavenExecutable @arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Las pruebas de concurrencia finalizaron con código $LASTEXITCODE."
  }

  $reportPath = Join-Path $BuildDirectory `
    "surefire-reports\TEST-ar.com.hexium.hcop.infusion.HospitalDayConcurrencySafetyTest.xml"
  if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
    throw "Maven terminó correctamente, pero no generó el reporte esperado."
  }
  [xml]$report = Get-Content -LiteralPath $reportPath -Raw
  $suite = $report.testsuite
  $summary = [ordered]@{
    suite = [string]$suite.name
    tests = [int]$suite.tests
    failures = [int]$suite.failures
    errors = [int]$suite.errors
    skipped = [int]$suite.skipped
    far25 = @(
      "far25TwoPharmacistsCannotOverReserveTheSameInventoryLot",
      "far25ApplicationLockSerializesTwoPharmacistsBeforeCheckingRevision"
    )
    tur25 = @(
      "tur25SimultaneousDropsYieldOneAppointmentAndOneClearConflict",
      "tur25DatabaseSerializesAChairAndRejectsDuplicateActiveApplications",
      "tur25AcceptsExactWorkdayEdgesAndRejectsTheFirstOverflowingSlot"
    )
    report = $reportPath
    productionPortTouched = $false
  }
  $summary | ConvertTo-Json -Depth 5
  if ($summary.failures -ne 0 -or $summary.errors -ne 0 -or $summary.tests -ne 5) {
    throw "El reporte no confirma las cinco pruebas esperadas."
  }
} finally {
  $env:JAVA_HOME = $previousJavaHome
  $env:Path = $previousPath
}
