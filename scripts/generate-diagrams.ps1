<#
  Regenera los PNG en docs/diagrams/png/ a partir de las fuentes .mmd en
  docs/diagrams/src/ usando @mermaid-js/mermaid-cli (vía npx, sin instalar
  nada globalmente).

  Uso:
    pwsh scripts/generate-diagrams.ps1
    pwsh scripts/generate-diagrams.ps1 -Scale 4 -Only 04-modelo-de-datos-er
#>
param(
  [string]$SrcDir = "docs/diagrams/src",
  [string]$OutDir = "docs/diagrams/png",
  [int]$Scale = 3,
  [string]$Theme = "neutral",
  [string]$Only = ""
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $OutDir)) {
  New-Item -ItemType Directory -Path $OutDir | Out-Null
}

$puppeteerConfig = Join-Path (Split-Path $SrcDir -Parent) "puppeteer-config.json"

$files = Get-ChildItem -Path $SrcDir -Filter "*.mmd" | Sort-Object Name
if ($Only -ne "") {
  $files = $files | Where-Object { $_.BaseName -eq $Only }
  if ($files.Count -eq 0) {
    throw "No se encontró '$Only.mmd' en $SrcDir"
  }
}

foreach ($file in $files) {
  $outPng = Join-Path $OutDir ($file.BaseName + ".png")
  Write-Host "Generando $outPng ..."
  npx --yes @mermaid-js/mermaid-cli `
    -i $file.FullName `
    -o $outPng `
    -t $Theme `
    -b white `
    -s $Scale `
    -p $puppeteerConfig
}

Write-Host "Listo. PNG en $OutDir"
