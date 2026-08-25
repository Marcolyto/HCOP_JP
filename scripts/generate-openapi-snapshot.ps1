# F3.0.4: snapshot versionado del OpenAPI completo, con diff normalizado (claves ordenadas
# recursivamente, sin depender de jq) para que un cambio real de contrato (agregar/quitar un
# campo de un schema, cambiar un tipo) rompa CI de forma bloqueante. generate-api-docs.ps1 -Check
# NO alcanza para esto: ENDPOINTS.md es una proyección lossy del spec (sin los schemas de
# request/response), así que un cambio en un ObjectSchema pasaría ese check y rompería el
# frontend en silencio.
param(
  [string]$BaseUrl = "http://127.0.0.1:5180",
  [string]$SnapshotPath = "docs/02-arquitectura/openapi-snapshot.json",
  [switch]$Check
)

$ErrorActionPreference = "Stop"
$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)

function Sort-JsonKeysDeep($Value) {
  if ($Value -is [System.Collections.IEnumerable] -and $Value -isnot [string] -and $Value -is [array]) {
    return @($Value | ForEach-Object { Sort-JsonKeysDeep $_ })
  }
  if ($Value -is [System.Management.Automation.PSCustomObject]) {
    $names = @($Value.PSObject.Properties.Name) | Sort-Object
    $ordered = [ordered]@{}
    foreach ($name in $names) {
      $ordered[$name] = Sort-JsonKeysDeep $Value.$name
    }
    return [pscustomobject]$ordered
  }
  return $Value
}

function Relative-Path([string]$Path) {
  if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
  return Join-Path (Get-Location) $Path
}

$webClient = [System.Net.WebClient]::new()
$webClient.Encoding = [System.Text.Encoding]::UTF8
try {
  $specificationJson = $webClient.DownloadString(
    "$($BaseUrl.TrimEnd('/'))/v3/api-docs/hcop-jp-completa")
} finally {
  $webClient.Dispose()
}

$specification = $specificationJson | ConvertFrom-Json
$sorted = Sort-JsonKeysDeep $specification
$normalized = ($sorted | ConvertTo-Json -Depth 100 -Compress:$false) + "`n"

$resolved = Relative-Path $SnapshotPath
if ($Check) {
  if (-not (Test-Path -LiteralPath $resolved)) {
    throw "Falta el snapshot: $SnapshotPath. Ejecute scripts/generate-openapi-snapshot.ps1 con HCOP JP iniciado."
  }
  $current = [System.IO.File]::ReadAllText($resolved, [System.Text.Encoding]::UTF8).Replace("`r`n", "`n")
  if ($current -ne $normalized) {
    throw "$SnapshotPath está desactualizado respecto del OpenAPI real. Ejecute scripts/generate-openapi-snapshot.ps1 y revise el diff — un cambio de contrato real no debería pasar en un commit de refactor puro (F3)."
  }
  Write-Host "OK - $SnapshotPath coincide con el OpenAPI real."
  exit 0
}

$directory = Split-Path -Parent $resolved
[System.IO.Directory]::CreateDirectory($directory) | Out-Null
[System.IO.File]::WriteAllText($resolved, $normalized, $Utf8NoBom)
Write-Host "Snapshot escrito en $SnapshotPath."
