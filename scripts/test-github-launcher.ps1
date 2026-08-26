$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$launcherPath = Join-Path $projectRoot "EJECUTAR-DOCKER-DESDE-GITHUB.ps1"

$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile(
  $launcherPath,
  [ref]$tokens,
  [ref]$parseErrors)
if ($parseErrors.Count -gt 0) {
  throw "El lanzador contiene errores de sintaxis: $($parseErrors.Message -join '; ')"
}

foreach ($name in @(
  "Write-Ok",
  "Write-Info",
  "Write-Warn",
  "Write-AtomicUtf8",
  "New-RandomHex",
  "ConvertTo-EnvLiteral",
  "ConvertFrom-EnvLiteral",
  "Read-InitialPort",
  "New-InitialEnvironmentContent",
  "Get-EnvironmentValues",
  "Set-EnvironmentValue",
  "Ensure-Environment",
  "ConvertTo-ProcessArgument",
  "Invoke-ProcessWithInput",
  "Invoke-NativeCapture",
  "Invoke-NativeLogged"
)) {
  $definition = $ast.Find({
    param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
      $node.Name -eq $name
  }, $true)
  if ($null -eq $definition) {
    throw "No se encontró la función requerida $name."
  }
  Invoke-Expression $definition.Extent.Text
}

if ($IsWindows -or $env:OS -eq "Windows_NT") {
  $nativeProgram = $env:ComSpec
  $nativeArguments = @(
    "/d",
    "/s",
    "/c",
    "echo expected native error 1>&2 & exit /b 7")
  $progressArguments = @(
    "/d",
    "/s",
    "/c",
    "echo expected native progress 1>&2 & exit /b 0")
} else {
  $nativeProgram = "/bin/sh"
  $nativeArguments = @(
    "-c",
    "printf 'expected native error\n' >&2; exit 7")
  $progressArguments = @(
    "-c",
    "printf 'expected native progress\n' >&2; exit 0")
}

$capture = Invoke-NativeCapture $nativeProgram $nativeArguments
$capturedText = $capture.Output -join " "
if ($capture.ExitCode -ne 7) {
  throw "La captura nativa devolvió $($capture.ExitCode); se esperaba 7."
}
if ($capturedText -notmatch "expected native error") {
  throw "La captura nativa no conservó la salida de error esperada."
}

$logged = Invoke-NativeLogged `
  $nativeProgram `
  $progressArguments `
  "Verificando progreso nativo emitido por stderr"
$loggedText = $logged.Output -join " "
if ($logged.ExitCode -ne 0) {
  throw "La ejecución con progreso devolvió $($logged.ExitCode); se esperaba 0."
}
if ($loggedText -notmatch "expected native progress") {
  throw "La ejecución no conservó el progreso emitido por stderr."
}

$sampleSecret = "ten\chars'plus"
$encodedSecret = ConvertTo-EnvLiteral $sampleSecret
$decodedSecret = ConvertFrom-EnvLiteral $encodedSecret
if ($decodedSecret -ne $sampleSecret) {
  throw "La codificación de secretos de .env no conserva barras y comillas."
}

$script:DefaultHostPort = 5181
$script:PortAnswers = [Collections.Generic.Queue[string]]::new()
$script:PortAnswers.Enqueue("")
function Read-Host {
  param([string]$Prompt)
  if ($script:PortAnswers.Count -eq 0) {
    throw "La prueba agotó las respuestas simuladas para Read-Host."
  }
  return $script:PortAnswers.Dequeue()
}
$defaultPort = Read-InitialPort
if ($defaultPort -ne 5181) {
  throw "El puerto vacío no seleccionó el valor predeterminado del canal."
}

$script:PortAnswers.Enqueue("0")
$script:PortAnswers.Enqueue("65536")
$script:PortAnswers.Enqueue("no-es-puerto")
$script:PortAnswers.Enqueue("6123")
$selectedPort = Read-InitialPort
if ($selectedPort -ne 6123) {
  throw "La selección de puerto no rechazó valores fuera de 1..65535."
}
Remove-Item Function:\Read-Host -Force

$environmentTestRoot = Join-Path `
  ([IO.Path]::GetTempPath()) `
  ("hcop-launcher-env-test-" + [guid]::NewGuid().ToString("N"))
$environmentTestPath = Join-Path $environmentTestRoot ".env"
try {
  New-Item -ItemType Directory -Path $environmentTestRoot -Force | Out-Null
  [IO.File]::WriteAllText(
    $environmentTestPath,
    "HCOP_BOOTSTRAP_PASSWORD='short123'`r`nHCOP_PORT=5180`r`n",
    (New-Object Text.UTF8Encoding($false)))
  $beforeRepair = Get-EnvironmentValues $environmentTestPath
  if (([string]$beforeRepair["HCOP_BOOTSTRAP_PASSWORD"]).Length -ge 10) {
    throw "La prueba no detectó la contraseña corta preparada."
  }

  $replacementSecret = "corrected-10-plus"
  Set-EnvironmentValue `
    $environmentTestPath `
    "HCOP_BOOTSTRAP_PASSWORD" `
    (ConvertTo-EnvLiteral $replacementSecret)
  $afterRepair = Get-EnvironmentValues $environmentTestPath
  if ($afterRepair["HCOP_BOOTSTRAP_PASSWORD"] -ne $replacementSecret) {
    throw "La reparación no guardó la nueva contraseña inicial."
  }
  if ($afterRepair["HCOP_PORT"] -ne "5180") {
    throw "La reparación alteró otra variable del archivo .env."
  }

  $script:DatabaseName = "hcop_ahjp"
  $generatedEnvironmentPath = Join-Path $environmentTestRoot "generated.env"
  $generatedEnvironment = New-InitialEnvironmentContent `
    "admin_prueba" `
    "clave-prueba-segura" `
    6123
  [IO.File]::WriteAllText(
    $generatedEnvironmentPath,
    $generatedEnvironment,
    (New-Object Text.UTF8Encoding($false)))
  $generatedValues = Get-EnvironmentValues $generatedEnvironmentPath
  if ($generatedValues["HCOP_PORT"] -ne "6123" -or
      $generatedValues["HCOP_PUBLIC_BASE_URL"] -ne "http://localhost:6123") {
    throw "El puerto elegido no se aplicó a HCOP_PORT y HCOP_PUBLIC_BASE_URL."
  }
  if ($generatedValues["HCOP_BOOTSTRAP_USERNAME"] -ne "admin_prueba" -or
      $generatedValues["HCOP_BOOTSTRAP_PASSWORD"] -ne "clave-prueba-segura") {
    throw "La configuración inicial no conservó las credenciales elegidas."
  }

  $existingEnvironmentPath = Join-Path $environmentTestRoot "existing.env"
  $existingEnvironment = @(
    "HCOP_PORT=6199",
    "HCOP_DB_NAME=hcop_ahjp",
    "HCOP_DB_USER=hcop",
    "HCOP_DB_PASSWORD='db-secret-test'",
    "HCOP_BOOTSTRAP_USERNAME='admin_existente'",
    "HCOP_BOOTSTRAP_PASSWORD='clave-existente-segura'",
    "HCOP_BOOTSTRAP_SECOND_USERNAME=marcolyto2",
    "HCOP_QR_SECRET='qr-secret-test'",
    "HCOP_ENCRYPTION_SECRET='encryption-secret-test'",
    "HCOP_JWT_SECRET='jwt-secret-test-at-least-32-bytes-long'",
    "HCOP_PUBLIC_BASE_URL=http://localhost:6199"
  ) -join "`r`n"
  [IO.File]::WriteAllText(
    $existingEnvironmentPath,
    $existingEnvironment + "`r`n",
    (New-Object Text.UTF8Encoding($false)))
  $existingBefore = [IO.File]::ReadAllText($existingEnvironmentPath)
  $script:UnexpectedPrompt = $false
  function Read-InitialPort {
    $script:UnexpectedPrompt = $true
    throw "Una instalación existente no debe volver a pedir puerto."
  }
  function Read-InitialCredentials {
    $script:UnexpectedPrompt = $true
    throw "Una instalación existente no debe volver a pedir credenciales."
  }
  function Protect-EnvironmentFile { param([string]$Path) }
  Ensure-Environment $existingEnvironmentPath "docker-no-utilizado"
  $existingAfter = [IO.File]::ReadAllText($existingEnvironmentPath)
  if ($script:UnexpectedPrompt -or $existingAfter -cne $existingBefore) {
    throw "Una instalación existente no conservó su archivo .env sin preguntar."
  }
} finally {
  if (Test-Path -LiteralPath $environmentTestRoot) {
    Remove-Item -LiteralPath $environmentTestRoot -Recurse -Force
  }
}

$managedInstallerPath = Join-Path $projectRoot "scripts\instalar-desde-github.ps1"
$managedTokens = $null
$managedParseErrors = $null
$managedAst = [Management.Automation.Language.Parser]::ParseFile(
  $managedInstallerPath,
  [ref]$managedTokens,
  [ref]$managedParseErrors)
if ($managedParseErrors.Count -gt 0) {
  throw "El instalador administrado contiene errores de sintaxis: $($managedParseErrors.Message -join '; ')"
}

foreach ($name in @("Write-DataLauncherFile")) {
  $definition = $managedAst.Find({
    param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
      $node.Name -eq $name
  }, $true)
  if ($null -eq $definition) {
    throw "No se encontró la función administrada requerida $name."
  }
  Invoke-Expression $definition.Extent.Text
}

$writeLaunchersDefinition = $managedAst.Find({
  param($node)
  $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
    $node.Name -eq "Write-Launchers"
}, $true)
if ($null -eq $writeLaunchersDefinition -or
    $writeLaunchersDefinition.Extent.Text -notmatch 'Respaldar HCOP JP\.bat' -or
    $writeLaunchersDefinition.Extent.Text -notmatch 'Restaurar HCOP JP\.bat') {
  throw "La instalación administrada no crea los accesos de backup y restauración."
}

$dataLauncherTestRoot = Join-Path `
  ([IO.Path]::GetTempPath()) `
  ("hcop-data-launcher-test-" + [guid]::NewGuid().ToString("N"))
try {
  New-Item -ItemType Directory -Path $dataLauncherTestRoot -Force | Out-Null
  $backupLauncherPath = Join-Path $dataLauncherTestRoot "Respaldar HCOP JP.bat"
  $restoreLauncherPath = Join-Path $dataLauncherTestRoot "Restaurar HCOP JP.bat"
  Write-DataLauncherFile $backupLauncherPath "Backup" "Backup completado."
  Write-DataLauncherFile $restoreLauncherPath "Restore" "Restauración completada."
  $backupLauncher = [IO.File]::ReadAllText($backupLauncherPath)
  $restoreLauncher = [IO.File]::ReadAllText($restoreLauncherPath)
  if ($backupLauncher -notmatch '-Mode Backup' -or
      $restoreLauncher -notmatch '-Mode Restore' -or
      $backupLauncher -notmatch '-InstallDir "%~dp0"' -or
      $restoreLauncher -notmatch '-InstallDir "%~dp0"') {
    throw "Los accesos de datos no delegan en el instalador y la carpeta instalados."
  }
  $launcherText = "$backupLauncher`n$restoreLauncher"
  if ($launcherText -match '(?i)HCOP_(?:DB_PASSWORD|QR_SECRET|ENCRYPTION_SECRET)|\.env') {
    throw "Un acceso de backup o restauración expone secretos o referencia .env."
  }
} finally {
  if (Test-Path -LiteralPath $dataLauncherTestRoot) {
    Remove-Item -LiteralPath $dataLauncherTestRoot -Recurse -Force
  }
}

$managedValidation = & $managedInstallerPath -Mode ValidateOnly | ConvertFrom-Json
if ($managedValidation.ok -ne $true) {
  throw "La validación estática del instalador administrado no fue satisfactoria."
}

$validation = & $launcherPath -Mode ValidateOnly | ConvertFrom-Json
if ($validation.ok -ne $true) {
  throw "La validación estática del lanzador no fue satisfactoria."
}
$migrationValidation = & $launcherPath -Mode ValidateOnly -Channel Migration | ConvertFrom-Json
if ($migrationValidation.ok -ne $true) {
  throw "La validación estática del canal de migración no fue satisfactoria."
}
if ($migrationValidation.backendImage -ne
    "ghcr.io/marcolyto/hcop_jp-backend:angular-full-parity-v2") {
  throw "El canal de migración no seleccionó su imagen aislada."
}
if ([int]$migrationValidation.defaultPort -ne 5181 -or
    $migrationValidation.applicationEntryUrl -ne "http://localhost:5181" -or
    $migrationValidation.projectName -ne "hcop-ahjp" -or
    $migrationValidation.databaseName -ne "hcop_ahjp" -or
    (Split-Path -Leaf $migrationValidation.dataDirectory) -ne "HCOP_AHJP-Docker" -or
    $migrationValidation.postgresVolume -ne "hcop_ahjp_postgres" -or
    $migrationValidation.storageVolume -ne "hcop_ahjp_storage") {
  throw "El canal de migración no aisló puerto y volúmenes."
}

[pscustomobject]@{
  ok = $true
  powershell = $PSVersionTable.PSVersion.ToString()
  expectedNativeExitCode = $capture.ExitCode
  nativeErrorCaptured = $true
  successfulProgressCaptured = $true
  environmentSecretRoundTrip = $true
  shortPasswordRepair = $true
  defaultPortSelection = $defaultPort
  customPortSelection = $selectedPort
  selectedPortStoredConsistently = $true
  existingEnvironmentPreserved = $true
  staticValidation = $validation.ok
  migrationStaticValidation = $migrationValidation.ok
  migrationIsolation = $true
  managedInstallerValidation = $managedValidation.ok
  managedBackupRestoreLaunchers = $true
  dataLaunchersDoNotExposeEnvironment = $true
} | ConvertTo-Json -Depth 5
