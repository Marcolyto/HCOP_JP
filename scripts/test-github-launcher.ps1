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
  "Write-Info",
  "Write-AtomicUtf8",
  "ConvertTo-EnvLiteral",
  "ConvertFrom-EnvLiteral",
  "Get-EnvironmentValues",
  "Set-EnvironmentValue",
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
} finally {
  if (Test-Path -LiteralPath $environmentTestRoot) {
    Remove-Item -LiteralPath $environmentTestRoot -Recurse -Force
  }
}

$validation = & $launcherPath -Mode ValidateOnly | ConvertFrom-Json
if ($validation.ok -ne $true) {
  throw "La validación estática del lanzador no fue satisfactoria."
}

[pscustomobject]@{
  ok = $true
  powershell = $PSVersionTable.PSVersion.ToString()
  expectedNativeExitCode = $capture.ExitCode
  nativeErrorCaptured = $true
  successfulProgressCaptured = $true
  environmentSecretRoundTrip = $true
  shortPasswordRepair = $true
  staticValidation = $validation.ok
} | ConvertTo-Json -Depth 5
