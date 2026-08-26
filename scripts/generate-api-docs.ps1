param(
  [string]$BaseUrl = "http://127.0.0.1:5180",
  [string]$MarkdownPath = "docs/02-arquitectura/ENDPOINTS.md",
  [string]$HtmlPath = "frontend/public/docs/api-endpoints.html",
  [switch]$Check
)

$ErrorActionPreference = "Stop"
$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$Tick = [char]96
$HttpMethods = @("get", "post", "put", "patch", "delete")
$TagOrder = @(
  "Autenticación",
  "Pacientes e historia",
  "Tratamientos",
  "Hospital de Día",
  "Flujos clínicos",
  "Configuración",
  "Catálogos",
  "Archivos clínicos",
  "Administración",
  "Integraciones",
  "Estado"
)

function Escape-Markdown([string]$Value) {
  if ($null -eq $Value) { $Value = "" }
  return $Value.Replace("|", "\|").Replace("`r", "").Trim()
}

function Escape-Html([string]$Value) {
  if ($null -eq $Value) { $Value = "" }
  return [System.Net.WebUtility]::HtmlEncode($Value)
}

function Canonical-Tag($Operation) {
  foreach ($candidate in $TagOrder) {
    if (@($Operation.tags) -contains $candidate) { return $candidate }
  }
  if (@($Operation.tags).Count -gt 0) { return [string]$Operation.tags[0] }
  return "Sin grupo"
}

function Parameter-Text($Parameter) {
  $required = if ($Parameter.required) { "obligatorio" } else { "opcional" }
  $description = if ($Parameter.description) { [string]$Parameter.description } else { "Sin descripción adicional." }
  return "$Tick$($Parameter.name)$Tick ($($Parameter.in), $required): $description"
}

function Content-Types($RequestBody) {
  if ($null -eq $RequestBody -or $null -eq $RequestBody.content) { return "Sin cuerpo." }
  $types = @($RequestBody.content.PSObject.Properties.Name)
  if ($types.Count -eq 0) { return "Sin cuerpo." }
  return ($types | ForEach-Object { "$Tick$_$Tick" }) -join ", "
}

function Response-Text($Responses) {
  if ($null -eq $Responses) { return "Sin respuestas declaradas." }
  $values = foreach ($response in $Responses.PSObject.Properties | Sort-Object Name) {
    $description = if ($response.Value.description) { [string]$response.Value.description } else { "Respuesta sin descripción." }
    "$Tick$($response.Name)$Tick $description"
  }
  return $values -join "; "
}

function Relative-Path([string]$Path) {
  if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
  return Join-Path (Get-Location) $Path
}

function Comparable-Content([string]$Path, [string]$Content) {
  $normalized = $Content.Replace("`r`n", "`n").TrimEnd()
  $lines = [System.Collections.Generic.List[string]]::new()
  foreach ($line in $normalized.Split("`n")) {
    $trimmed = $line.TrimEnd()
    if (-not [string]::IsNullOrWhiteSpace($trimmed)) { $lines.Add($trimmed) }
  }
  $lines.Sort([System.StringComparer]::Ordinal)
  return $lines -join "`n"
}

function Save-Or-Check([string]$Path, [string]$Content) {
  $resolved = Relative-Path $Path
  if ($Check) {
    if (-not (Test-Path -LiteralPath $resolved)) {
      throw "Falta el archivo generado: $Path"
    }
    $current = [System.IO.File]::ReadAllText($resolved, [System.Text.Encoding]::UTF8)
    if ((Comparable-Content $Path $current) -ne (Comparable-Content $Path $Content)) {
      throw "$Path está desactualizado. Ejecute scripts/generate-api-docs.ps1 con HCOP JP iniciado."
    }
    return
  }
  $directory = Split-Path -Parent $resolved
  [System.IO.Directory]::CreateDirectory($directory) | Out-Null
  [System.IO.File]::WriteAllText($resolved, $Content, $Utf8NoBom)
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
$operations = @()
foreach ($pathProperty in $specification.paths.PSObject.Properties) {
  foreach ($methodProperty in $pathProperty.Value.PSObject.Properties) {
    if ($methodProperty.Name -notin $HttpMethods) { continue }
    $operation = $methodProperty.Value
    $permissionProperty = $operation.PSObject.Properties["x-hcop-permission"]
    $controllerProperty = $operation.PSObject.Properties["x-hcop-controller"]
    $operations += [pscustomobject]@{
      Method = $methodProperty.Name.ToUpperInvariant()
      Path = $pathProperty.Name
      Summary = [string]$operation.summary
      Description = [string]$operation.description
      Tag = Canonical-Tag $operation
      Permission = if ($permissionProperty) { [string]$permissionProperty.Value } else { "authenticated" }
      Controller = if ($controllerProperty) { [string]$controllerProperty.Value } else { "" }
      Parameters = @($operation.parameters)
      RequestBody = $operation.requestBody
      Responses = $operation.responses
      OperationId = [string]$operation.operationId
    }
  }
}

$methodOrder = @{ GET = 1; POST = 2; PUT = 3; PATCH = 4; DELETE = 5 }
$operations = @($operations | Sort-Object @{ Expression = { [array]::IndexOf($TagOrder, $_.Tag) } },
  Path, @{ Expression = { $methodOrder[$_.Method] } })

$markdown = [System.Text.StringBuilder]::new()
[void]$markdown.AppendLine("# Catálogo completo de endpoints")
[void]$markdown.AppendLine()
[void]$markdown.AppendLine("> Archivo generado desde el OpenAPI real de HCOP JP. No editar a mano.")
[void]$markdown.AppendLine()
[void]$markdown.AppendLine('- Especificación: `GET /v3/api-docs/hcop-jp-completa`')
[void]$markdown.AppendLine('- Swagger UI: `GET /swagger-ui.html`')
[void]$markdown.AppendLine("- Versión declarada: $Tick$($specification.info.version)$Tick")
[void]$markdown.AppendLine("- Operaciones documentadas: **$($operations.Count)**")
[void]$markdown.AppendLine('- Autenticación: Bearer JWT (`Authorization: Bearer <accessToken>`); las operaciones públicas se identifican expresamente.')
[void]$markdown.AppendLine()
[void]$markdown.AppendLine('Los permisos se validan en el servidor. `authenticated` significa que la ruta exige una sesión activa pero no aplica un permiso granular adicional en el controlador.')
[void]$markdown.AppendLine()

foreach ($tag in $TagOrder) {
  $tagOperations = @($operations | Where-Object Tag -eq $tag)
  if ($tagOperations.Count -eq 0) { continue }
  [void]$markdown.AppendLine("## $tag")
  [void]$markdown.AppendLine()
  foreach ($operation in $tagOperations) {
    [void]$markdown.AppendLine("### $Tick$($operation.Method) $($operation.Path)$Tick - $(Escape-Markdown $operation.Summary)")
    [void]$markdown.AppendLine()
    [void]$markdown.AppendLine("$(Escape-Markdown $operation.Description)")
    [void]$markdown.AppendLine()
    [void]$markdown.AppendLine("- **Controlador MVC:** $Tick$($operation.Controller)$Tick")
    [void]$markdown.AppendLine("- **Operación Java/OpenAPI:** $Tick$($operation.OperationId)$Tick")
    [void]$markdown.AppendLine("- **Acceso requerido:** $Tick$($operation.Permission)$Tick")
    $parameters = @($operation.Parameters |
      Where-Object { $null -ne $_ -and -not [string]::IsNullOrWhiteSpace([string]$_.name) } |
      ForEach-Object { Parameter-Text $_ })
    [void]$markdown.AppendLine("- **Parámetros:** $(if ($parameters.Count) { $parameters -join '; ' } else { 'Ninguno.' })")
    [void]$markdown.AppendLine("- **Cuerpo:** $(Content-Types $operation.RequestBody)")
    [void]$markdown.AppendLine("- **Respuestas:** $(Response-Text $operation.Responses)")
    [void]$markdown.AppendLine()
  }
}

$markdownContent = $markdown.ToString().Replace("`r`n", "`n").TrimEnd() + "`n"

$rows = foreach ($operation in $operations) {
  $description = Escape-Html $operation.Description
  @"
              <tr data-filter-item>
                <td><code>$($operation.Method)</code></td>
                <td class="path"><code>$(Escape-Html $operation.Path)</code></td>
                <td><strong>$(Escape-Html $operation.Summary)</strong><small>$description</small></td>
                <td>$(Escape-Html $operation.Tag)</td>
                <td><code>$(Escape-Html $operation.Permission)</code></td>
                <td><code>$(Escape-Html $operation.Controller)</code></td>
              </tr>
"@
}

$htmlContent = @"
<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Endpoints y Swagger · HCOP JP</title>
  <meta name="description" content="Catálogo completo y buscable de endpoints de HCOP JP, generado desde OpenAPI.">
  <link rel="stylesheet" href="./documentacion.css">
</head>
<body>
  <header class="doc-topbar">
    <a class="doc-brand" href="./index.html"><span class="doc-brand-mark">HC</span><div><strong>Endpoints</strong><small>Contrato OpenAPI de HCOP JP</small></div></a>
    <div class="doc-top-actions">
      <a class="doc-button" href="./index.html">Índice</a>
      <a class="doc-button" href="./referencia-tecnica.html">Referencia técnica</a>
      <a class="doc-button primary" href="../swagger-ui.html">Probar en Swagger</a>
    </div>
  </header>
  <main class="doc-main" style="max-width:1500px;margin:0 auto;min-height:calc(100vh - 64px)">
    <section class="doc-hero">
      <span class="doc-kicker">OpenAPI $($specification.info.version) · $($operations.Count) operaciones</span>
      <h1>Todos los endpoints, permisos y controladores</h1>
      <p>Referencia generada desde el servidor Java. Swagger permite ejecutar las rutas con la misma cookie de sesión del sistema; nunca evita permisos ni validaciones clínicas.</p>
      <div class="doc-meta"><span class="doc-tag">Spring MVC</span><span class="doc-tag">Cookie HttpOnly</span><span class="doc-tag">RBAC</span><span class="doc-tag">OpenAPI 3</span></div>
    </section>
    <section class="doc-section">
      <h2>Catálogo buscable</h2>
      <div class="doc-searchbar no-print"><span>Buscar</span><input type="search" placeholder="Ruta, acción, permiso, módulo o controlador" data-doc-search="#endpointRows" data-doc-counter="#endpointCount"><span id="endpointCount"></span></div>
      <div class="doc-table-wrap">
        <table class="doc-table">
          <thead><tr><th>Método</th><th>Ruta</th><th>Finalidad</th><th>Módulo</th><th>Permiso</th><th>Controller</th></tr></thead>
          <tbody id="endpointRows">
$($rows -join "")
          </tbody>
        </table>
      </div>
      <div class="doc-callout"><strong>Fuente de verdad:</strong> <a href="../v3/api-docs/hcop-jp-completa"><code>/v3/api-docs/hcop-jp-completa</code></a>. Para parámetros, cuerpos, respuestas y ejecución interactiva use <a href="../swagger-ui.html">Swagger UI</a>.</div>
    </section>
  </main>
  <script src="./documentacion.js"></script>
</body>
</html>
"@
$htmlContent = $htmlContent.Replace("`r`n", "`n")

Save-Or-Check $MarkdownPath $markdownContent
Save-Or-Check $HtmlPath $htmlContent

[pscustomobject]@{
  ok = $true
  check = [bool]$Check
  operations = $operations.Count
  markdown = $MarkdownPath
  html = $HtmlPath
} | ConvertTo-Json -Compress
