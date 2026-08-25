$ErrorActionPreference = "Stop"

function Resolve-HcopDeployment {
  param(
    [Parameter(Mandatory = $true)][string]$ProjectRoot,
    [string]$ProjectName = "hcop-jp"
  )

  $root = [System.IO.Path]::GetFullPath($ProjectRoot)
  if (-not (Test-Path -LiteralPath $root -PathType Container)) {
    throw "La carpeta de HCOP JP no existe: $root"
  }

  $environmentFile = Join-Path $root ".env"
  $composeFiles = @()
  $releaseCommit = ""
  $currentPointer = Join-Path $root "current.txt"
  if (Test-Path -LiteralPath $currentPointer -PathType Leaf) {
    $versionPath = [System.IO.Path]::GetFullPath((Get-Content -LiteralPath $currentPointer -Raw).Trim())
    $versionsRoot = [System.IO.Path]::GetFullPath((Join-Path $root "versions"))
    $allowedPrefix = $versionsRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $versionPath.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
      throw "current.txt apunta fuera de la carpeta versions y fue rechazado."
    }
    $releasePath = Join-Path $versionPath "release.json"
    if (-not (Test-Path -LiteralPath $releasePath -PathType Leaf)) {
      throw "La versión instalada no contiene release.json."
    }
    $release = Get-Content -LiteralPath $releasePath -Raw | ConvertFrom-Json
    $releaseCommit = [string]$release.commit
    foreach ($relative in @($release.composeFiles)) {
      $composePath = [System.IO.Path]::GetFullPath((Join-Path $versionPath ([string]$relative)))
      if (-not $composePath.StartsWith($allowedPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
          -not (Test-Path -LiteralPath $composePath -PathType Leaf)) {
        throw "La versión instalada referencia un archivo Compose inválido."
      }
      $composeFiles += $composePath
    }
    $workingDirectory = $versionPath
  } else {
    $composePath = Join-Path $root "compose.yaml"
    if (-not (Test-Path -LiteralPath $composePath -PathType Leaf)) {
      throw "No se encontró compose.yaml ni una instalación versionada en $root."
    }
    $composeFiles = @([System.IO.Path]::GetFullPath($composePath))
    $workingDirectory = $root
  }

  if ($composeFiles.Count -eq 0) { throw "No hay archivos Compose para operar HCOP JP." }
  return [pscustomobject]@{
    Root = $root
    WorkingDirectory = $workingDirectory
    EnvironmentFile = if (Test-Path -LiteralPath $environmentFile -PathType Leaf) { $environmentFile } else { "" }
    ProjectName = $ProjectName
    ComposeFiles = $composeFiles
    ReleaseCommit = $releaseCommit
  }
}

function Get-HcopComposeArguments {
  param([Parameter(Mandatory = $true)]$Deployment, [string[]]$Arguments = @())
  $result = @("compose", "--project-name", $Deployment.ProjectName)
  if ($Deployment.EnvironmentFile) { $result += @("--env-file", $Deployment.EnvironmentFile) }
  foreach ($composeFile in $Deployment.ComposeFiles) { $result += @("-f", $composeFile) }
  return @($result + $Arguments)
}

function Invoke-HcopNative {
  param(
    [Parameter(Mandatory = $true)][string]$Executable,
    [Parameter(Mandatory = $true)][string[]]$Arguments,
    [string]$WorkingDirectory = "",
    [switch]$Capture
  )
  $previous = Get-Location
  try {
    if ($WorkingDirectory) { Set-Location -LiteralPath $WorkingDirectory }
    if ($Capture) {
      $output = @(& $Executable @Arguments 2>&1)
      $exitCode = $LASTEXITCODE
      if ($exitCode -ne 0) { throw "$Executable finalizó con código $exitCode.`n$($output -join [Environment]::NewLine)" }
      return ($output -join [Environment]::NewLine).Trim()
    }
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable finalizó con código $LASTEXITCODE." }
  } finally {
    Set-Location -LiteralPath $previous
  }
}

function Invoke-HcopCompose {
  param(
    [Parameter(Mandatory = $true)]$Deployment,
    [Parameter(Mandatory = $true)][string[]]$Arguments,
    [switch]$Capture
  )
  return Invoke-HcopNative -Executable "docker" `
    -Arguments (Get-HcopComposeArguments $Deployment $Arguments) `
    -WorkingDirectory $Deployment.WorkingDirectory `
    -Capture:$Capture
}

function Get-HcopServiceContainer {
  param([Parameter(Mandatory = $true)]$Deployment, [Parameter(Mandatory = $true)][string]$Service)
  $container = (Invoke-HcopCompose $Deployment @("ps", "-q", $Service) -Capture).Trim()
  if (-not $container) { throw "El servicio $Service no posee un contenedor creado." }
  if ($container -notmatch '^[0-9a-fA-F]{12,64}$') { throw "Docker devolvió un identificador inesperado para $Service." }
  return $container
}

function Get-HcopContainerValue {
  param([Parameter(Mandatory = $true)][string]$Container, [Parameter(Mandatory = $true)][string]$Variable)
  $value = Invoke-HcopNative -Executable "docker" -Arguments @("exec", $Container, "printenv", $Variable) -Capture
  if ([string]::IsNullOrWhiteSpace($value)) { throw "El contenedor no define $Variable." }
  return $value.Trim()
}

function Get-HcopStorageVolume {
  param([Parameter(Mandatory = $true)][string]$ApplicationContainer)
  $inspection = Invoke-HcopNative -Executable "docker" -Arguments @("inspect", $ApplicationContainer) -Capture | ConvertFrom-Json
  $mount = @($inspection[0].Mounts | Where-Object { [string]$_.Destination -eq "/opt/hcop/runtime/storage" })
  if ($mount.Count -ne 1 -or [string]$mount[0].Type -ne "volume") {
    throw "El contenedor no posee un único volumen clínico verificable."
  }
  $volume = [string]$mount[0].Name
  if ($volume -notmatch '^[A-Za-z0-9][A-Za-z0-9_.-]+$') {
    throw "No se pudo resolver de forma segura el volumen clínico de HCOP JP."
  }
  return $volume
}

function Get-HcopContainerImage {
  param([Parameter(Mandatory = $true)][string]$Container)
  $image = (Invoke-HcopNative -Executable "docker" -Arguments @("inspect", "--format", "{{.Config.Image}}", $Container) -Capture).Trim()
  if ([string]::IsNullOrWhiteSpace($image)) { throw "No se pudo determinar la imagen del contenedor." }
  return $image
}

function Test-HcopContainerRunning {
  param([Parameter(Mandatory = $true)][string]$Container)
  return (Invoke-HcopNative -Executable "docker" -Arguments @("inspect", "--format", "{{.State.Running}}", $Container) -Capture).Trim() -eq "true"
}

function Remove-HcopSafeDirectory {
  param([Parameter(Mandatory = $true)][string]$Path, [Parameter(Mandatory = $true)][string]$AllowedRoot)
  if (-not (Test-Path -LiteralPath $Path)) { return }
  $resolvedPath = [System.IO.Path]::GetFullPath($Path)
  $resolvedRoot = [System.IO.Path]::GetFullPath($AllowedRoot).TrimEnd([System.IO.Path]::DirectorySeparatorChar)
  $prefix = $resolvedRoot + [System.IO.Path]::DirectorySeparatorChar
  if (-not $resolvedPath.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Se rechazó eliminar una carpeta temporal fuera del destino autorizado."
  }
  Remove-Item -LiteralPath $resolvedPath -Recurse -Force
}

function New-HcopExclusiveLock {
  param([Parameter(Mandatory = $true)][string]$Path)
  try {
    return [System.IO.File]::Open($Path, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
  } catch {
    throw "Ya hay otra operación de backup o restauración en curso."
  }
}
