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
  "ConvertTo-ProcessArgument",
  "Invoke-ProcessWithInput",
  "Invoke-NativeCapture"
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
} else {
  $nativeProgram = "/bin/sh"
  $nativeArguments = @(
    "-c",
    "printf 'expected native error\n' >&2; exit 7")
}

$capture = Invoke-NativeCapture $nativeProgram $nativeArguments
$capturedText = $capture.Output -join " "
if ($capture.ExitCode -ne 7) {
  throw "La captura nativa devolvió $($capture.ExitCode); se esperaba 7."
}
if ($capturedText -notmatch "expected native error") {
  throw "La captura nativa no conservó la salida de error esperada."
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
  staticValidation = $validation.ok
} | ConvertTo-Json -Depth 5
