param(
  [string]$BaseUrl = "http://127.0.0.1:5180"
)

$ErrorActionPreference = "Stop"
$RepositoryRoot = Split-Path -Parent $PSScriptRoot
$HttpMethods = @("get", "post", "put", "patch", "delete")
$CanonicalTags = @(
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
$documentationFiles = @(
  Get-Item -LiteralPath (Join-Path $RepositoryRoot "README.md")
  Get-ChildItem -LiteralPath (Join-Path $RepositoryRoot "docs") -Filter "*.md" -Recurse
)
$brokenLinks = [System.Collections.Generic.List[string]]::new()

foreach ($file in $documentationFiles) {
  $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
  foreach ($match in [regex]::Matches($content, '\[[^\]]+\]\((?<target>[^)]+)\)')) {
    $target = $match.Groups["target"].Value.Trim().Trim("<", ">")
    if ($target -match '^(?:https?://|mailto:|#|/)') { continue }
    $pathPart = ($target -split '#', 2)[0]
    if ([string]::IsNullOrWhiteSpace($pathPart)) { continue }
    $decoded = [System.Uri]::UnescapeDataString($pathPart)
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $file.DirectoryName $decoded))
    if (-not (Test-Path -LiteralPath $candidate)) {
      $relativeFile = $file.FullName.Substring($RepositoryRoot.Length + 1)
      $brokenLinks.Add("$relativeFile -> $target")
    }
  }
}

if ($brokenLinks.Count -gt 0) {
  throw "Enlaces Markdown rotos:`n$($brokenLinks -join "`n")"
}

$base = $BaseUrl.TrimEnd("/")
$publicPaths = @(
  "/actuator/health",
  "/docs/",
  "/docs/manual-usuario.html",
  "/docs/referencia-tecnica.html",
  "/docs/api-endpoints.html",
  "/swagger-ui.html",
  "/v3/api-docs/hcop-jp-completa"
)
foreach ($path in $publicPaths) {
  $response = Invoke-WebRequest -UseBasicParsing -Uri "$base$path" -TimeoutSec 20
  if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 400) {
    throw "$path respondió HTTP $($response.StatusCode)"
  }
}

$webClient = [System.Net.WebClient]::new()
$webClient.Encoding = [System.Text.Encoding]::UTF8
try {
  $openApi = ($webClient.DownloadString("$base/v3/api-docs/hcop-jp-completa") |
      ConvertFrom-Json)
} finally {
  $webClient.Dispose()
}

$operations = @()
$operationIds = [System.Collections.Generic.HashSet[string]]::new(
  [System.StringComparer]::OrdinalIgnoreCase)
foreach ($pathProperty in $openApi.paths.PSObject.Properties) {
  foreach ($methodProperty in $pathProperty.Value.PSObject.Properties) {
    if ($methodProperty.Name -notin $HttpMethods) { continue }
    $operation = $methodProperty.Value
    $operations += $operation
    $operationLabel = "$($methodProperty.Name.ToUpperInvariant()) $($pathProperty.Name)"
    if ([string]::IsNullOrWhiteSpace([string]$operation.operationId)) {
      throw "$operationLabel no tiene operationId."
    }
    if (-not $operationIds.Add([string]$operation.operationId)) {
      throw "operationId duplicado: $($operation.operationId)"
    }
    $tags = @($operation.tags)
    if ($tags.Count -ne 1 -or $tags[0] -notin $CanonicalTags) {
      throw "$operationLabel no tiene exactamente una etiqueta funcional canónica."
    }
    foreach ($parameter in @($operation.parameters)) {
      if ($null -eq $parameter -or
          [string]::IsNullOrWhiteSpace([string]$parameter.name)) { continue }
      if ([string]::IsNullOrWhiteSpace([string]$parameter.description)) {
        throw "$operationLabel tiene el parámetro '$($parameter.name)' sin descripción."
      }
    }
    if ($null -ne $operation.requestBody) {
      if ([string]::IsNullOrWhiteSpace([string]$operation.requestBody.description)) {
        throw "$operationLabel tiene cuerpo sin descripción."
      }
      $bodyTypes = @($operation.requestBody.content.PSObject.Properties)
      if ($bodyTypes.Count -eq 0) {
        throw "$operationLabel declara cuerpo pero no su tipo de contenido."
      }
      foreach ($bodyType in $bodyTypes) {
        if ($null -eq $bodyType.Value.schema) {
          throw "$operationLabel no documenta el esquema de $($bodyType.Name)."
        }
      }
    }
    $responseProperties = @($operation.responses.PSObject.Properties)
    if (@($responseProperties.Name | Where-Object { $_ -match '^2\d\d$' }).Count -eq 0) {
      throw "$operationLabel no documenta una respuesta exitosa."
    }
    foreach ($responseProperty in $responseProperties) {
      if ([string]::IsNullOrWhiteSpace([string]$responseProperty.Value.description)) {
        throw "$operationLabel tiene la respuesta $($responseProperty.Name) sin descripción."
      }
      if ($responseProperty.Name -match '^[45]\d\d$') {
        $errorSchema = $responseProperty.Value.content.'application/json'.schema
        $isProtectedUnauthorized =
          $responseProperty.Name -eq "401" -and
          [string]$operation.'x-hcop-authentication' -ne "public"
        $expectedErrorSchema = if ($isProtectedUnauthorized) {
          "#/components/schemas/AuthenticationRequired"
        } else {
          "#/components/schemas/ApiError"
        }
        if ([string]$errorSchema.'$ref' -ne $expectedErrorSchema) {
          throw "$operationLabel no usa $expectedErrorSchema en la respuesta $($responseProperty.Name)."
        }
      }
    }
  }
}

if ($operations.Count -eq 0) { throw "OpenAPI no contiene operaciones." }
foreach ($operation in $operations) {
  if ([string]::IsNullOrWhiteSpace([string]$operation.summary)) {
    throw "Existe una operación OpenAPI sin resumen."
  }
  if ([string]::IsNullOrWhiteSpace([string]$operation.description)) {
    throw "Existe una operación OpenAPI sin descripción."
  }
  if ($null -eq $operation.PSObject.Properties["x-hcop-controller"]) {
    throw "Existe una operación OpenAPI sin controlador MVC documentado."
  }
  if ($null -eq $operation.PSObject.Properties["x-hcop-permission"]) {
    throw "Existe una operación OpenAPI sin permiso documentado."
  }
  $authentication = [string]$operation.'x-hcop-authentication'
  if ($authentication -ne "public" -and @($operation.security).Count -eq 0) {
    throw "Existe una operación protegida sin esquema de seguridad."
  }
}

if ($null -eq $openApi.components.securitySchemes.sessionCookie) {
  throw "OpenAPI no publica el esquema de autenticación sessionCookie."
}
if ($null -eq $openApi.components.schemas.ApiError) {
  throw "OpenAPI no publica el esquema uniforme ApiError."
}
if ($null -eq $openApi.components.schemas.AuthenticationRequired) {
  throw "OpenAPI no publica el esquema AuthenticationRequired."
}

$createdOperations = @(
  "/api/admin/roles",
  "/api/admin/users",
  "/api/clinical/configuration/{kind}",
  "/api/clinical/infusions",
  "/api/clinical/patients",
  "/api/clinical/patients/{patientId}/treatments",
  "/api/clinical/protocols",
  "/api/clinical/treatment-workflow-requests",
  "/api/media/images",
  "/api/media/studies",
  "/api/study-templates"
)
foreach ($path in $createdOperations) {
  $post = $openApi.paths.$path.post
  if ($null -eq $post -or $null -eq $post.responses.'201') {
    throw "POST $path debe documentar la creación con HTTP 201."
  }
}

$webClient = [System.Net.WebClient]::new()
$webClient.Encoding = [System.Text.Encoding]::UTF8
try {
  $clinicalOpenApi = ($webClient.DownloadString("$base/v3/api-docs/clinica") |
      ConvertFrom-Json)
  $administrationOpenApi = ($webClient.DownloadString("$base/v3/api-docs/administracion") |
      ConvertFrom-Json)
} finally {
  $webClient.Dispose()
}

foreach ($path in @(
    "/api/clinical/patients",
    "/api/diagnosis-catalogs/search",
    "/api/ajcc8",
    "/api/tnm",
    "/api/media/studies",
    "/api/llm/status")) {
  if ($null -eq $clinicalOpenApi.paths.PSObject.Properties[$path]) {
    throw "El grupo Clínica no incluye $path."
  }
}
foreach ($path in @(
    "/api/admin/users",
    "/api/config",
    "/api/llm/test",
    "/api/clinical/configuration/{kind}",
    "/api/clinical/protocols",
    "/api/study-templates")) {
  if ($null -eq $administrationOpenApi.paths.PSObject.Properties[$path]) {
    throw "El grupo Administración no incluye $path."
  }
}

[pscustomobject]@{
  ok = $true
  markdownFiles = $documentationFiles.Count
  checkedPublicUrls = $publicPaths.Count
  openApiOperations = $operations.Count
  uniqueOperationIds = $operationIds.Count
  canonicalTags = $CanonicalTags.Count
  clinicalPaths = @($clinicalOpenApi.paths.PSObject.Properties).Count
  administrationPaths = @($administrationOpenApi.paths.PSObject.Properties).Count
} | ConvertTo-Json -Compress
