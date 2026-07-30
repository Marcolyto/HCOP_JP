param(
  [string]$InstallDir = (Join-Path $env:LOCALAPPDATA "HCOP_JP"),
  [ValidateSet(
    "Install",
    "Start",
    "Update",
    "Repair",
    "Stop",
    "Preflight",
    "ValidateOnly",
    "SourceStart",
    "SourceStop",
    "SourceRestart")]
  [string]$Mode = "Install",
  [switch]$NoOpenBrowser,
  [switch]$Elevated
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$script:RepositoryZip = "https://github.com/Marcolyto/HCOP_JP/archive/refs/heads/main.zip"
$script:RepositoryArchiveApi = "https://api.github.com/repos/Marcolyto/HCOP_JP/zipball/main"
$script:RepositoryCommitApi = "https://api.github.com/repos/Marcolyto/HCOP_JP/commits/main"
$script:PublishedImage = "ghcr.io/marcolyto/hcop_jp"
$script:ProjectName = "hcop-jp"
$script:LogPath = $null
$script:TranscriptStarted = $false
$script:DockerPath = $null
$script:ActiveRelease = $null
$script:ExitCode = 1
$script:OperationMutex = $null

function Write-Step([string]$Message) {
  Write-Host ""
  Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Info([string]$Message) {
  Write-Host "    $Message"
}

function Write-Ok([string]$Message) {
  Write-Host "    $Message" -ForegroundColor Green
}

function Write-Warn([string]$Message) {
  Write-Warning $Message
}

function Start-InstallerLog([string]$Root, [bool]$SourceMode) {
  $logDirectory = if ($SourceMode) {
    Join-Path $Root "runtime\logs"
  } else {
    Join-Path $Root "logs"
  }
  New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
  $stamp = (Get-Date).ToString("yyyyMMdd-HHmmssfff")
  $script:LogPath = Join-Path $logDirectory ("hcop-installer-{0}-{1}.log" -f $Mode.ToLowerInvariant(), $stamp)
  try {
    Start-Transcript -LiteralPath $script:LogPath -Force | Out-Null
    $script:TranscriptStarted = $true
  } catch {
    [System.IO.File]::WriteAllText(
      $script:LogPath,
      "HCOP JP - $Mode - $(Get-Date -Format o)`r`n",
      (New-Object System.Text.UTF8Encoding($false)))
  }
  Write-Info "Registro: $script:LogPath"
}

function Stop-InstallerLog {
  if (-not $script:TranscriptStarted) { return }
  try { Stop-Transcript | Out-Null } catch {}
  $script:TranscriptStarted = $false
}

function Enter-OperationLock([string]$Root) {
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($Root.ToLowerInvariant())
  $hasher = [System.Security.Cryptography.SHA256]::Create()
  try {
    $hash = ([BitConverter]::ToString($hasher.ComputeHash($bytes))).Replace("-", "").Substring(0, 20)
  } finally {
    $hasher.Dispose()
  }
  $created = $false
  $mutex = New-Object System.Threading.Mutex($false, "Local\HCOPJP-$hash", [ref]$created)
  if (-not $mutex.WaitOne(0)) {
    $mutex.Dispose()
    throw "Ya hay otra operación de HCOP JP en curso. Espere a que termine y vuelva a intentar."
  }
  $script:OperationMutex = $mutex
}

function Exit-OperationLock {
  if ($null -eq $script:OperationMutex) { return }
  try { $script:OperationMutex.ReleaseMutex() } catch {}
  $script:OperationMutex.Dispose()
  $script:OperationMutex = $null
}

function New-RandomSecret([int]$Bytes = 36) {
  $buffer = New-Object byte[] $Bytes
  $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
  try { $generator.GetBytes($buffer) } finally { $generator.Dispose() }
  ([Convert]::ToBase64String($buffer)).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

function Get-PlainText([Security.SecureString]$SecureValue) {
  if ($null -eq $SecureValue) { return "" }
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
  try {
    return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  } finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
  }
}

function ConvertTo-DotEnvValue([string]$Value) {
  if ($null -eq $Value) { $Value = "" }
  if ($Value.IndexOfAny([char[]]"`r`n`0") -ge 0) {
    throw "Un valor de configuración contiene saltos de línea o caracteres no admitidos."
  }
  "'" + $Value.Replace("'", "\'") + "'"
}

function Read-DotEnv([string]$Path) {
  $values = @{}
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $values }
  foreach ($line in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) {
    if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { continue }
    $key = $matches[1]
    $value = $matches[2].Trim()
    if ($value.Length -ge 2 -and $value.StartsWith("'") -and $value.EndsWith("'")) {
      $value = $value.Substring(1, $value.Length - 2).Replace("\'", "'")
    } elseif ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
      $value = $value.Substring(1, $value.Length - 2)
    } else {
      $commentIndex = $value.IndexOf(" #", [StringComparison]::Ordinal)
      if ($commentIndex -ge 0) { $value = $value.Substring(0, $commentIndex).TrimEnd() }
    }
    $values[$key] = $value
  }
  return $values
}

function Write-DotEnv([string]$Path, [hashtable]$Values) {
  $order = @(
    "HCOP_PORT",
    "HCOP_DB_NAME",
    "HCOP_DB_USER",
    "HCOP_DB_PASSWORD",
    "HCOP_BOOTSTRAP_USERNAME",
    "HCOP_BOOTSTRAP_PASSWORD",
    "HCOP_BOOTSTRAP_SECOND_USERNAME",
    "HCOP_QR_SECRET",
    "HCOP_ENCRYPTION_SECRET",
    "HCOP_PUBLIC_BASE_URL"
  )
  $lines = [System.Collections.Generic.List[string]]::new()
  foreach ($key in $order) {
    if ($Values.ContainsKey($key)) {
      $lines.Add("$key=$(ConvertTo-DotEnvValue ([string]$Values[$key]))")
    }
  }
  foreach ($key in @($Values.Keys | Sort-Object)) {
    if ($key -notin $order) {
      $lines.Add("$key=$(ConvertTo-DotEnvValue ([string]$Values[$key]))")
    }
  }
  [System.IO.File]::WriteAllLines(
    $Path,
    $lines,
    (New-Object System.Text.UTF8Encoding($false)))
}

function Ensure-Environment([string]$Root) {
  $environmentFile = Join-Path $Root ".env"
  $values = Read-DotEnv $environmentFile
  $changed = -not (Test-Path -LiteralPath $environmentFile -PathType Leaf)

  if (-not $values.ContainsKey("HCOP_PORT")) {
    $port = Read-Host "Puerto web [5180]"
    if ([string]::IsNullOrWhiteSpace($port)) { $port = "5180" }
    $values["HCOP_PORT"] = $port
    $changed = $true
  }
  $portValue = [string]$values["HCOP_PORT"]
  if ($portValue -notmatch "^\d{1,5}$" -or [int]$portValue -lt 1 -or [int]$portValue -gt 65535) {
    throw "HCOP_PORT no es válido en $environmentFile."
  }

  if (-not $values.ContainsKey("HCOP_BOOTSTRAP_USERNAME")) {
    $username = Read-Host "Usuario administrador [marcolyto]"
    if ([string]::IsNullOrWhiteSpace($username)) { $username = "marcolyto" }
    $values["HCOP_BOOTSTRAP_USERNAME"] = $username
    $changed = $true
  }
  if (-not $values.ContainsKey("HCOP_BOOTSTRAP_PASSWORD")) {
    do {
      $securePassword = Read-Host "Contraseña inicial (mínimo 10 caracteres; no se mostrará)" -AsSecureString
      $password = Get-PlainText $securePassword
      if ($password.Length -lt 10) {
        Write-Warning "La contraseña debe tener al menos 10 caracteres."
      }
    } while ($password.Length -lt 10)
    $values["HCOP_BOOTSTRAP_PASSWORD"] = $password
    $changed = $true
  }

  $defaults = @{
    HCOP_DB_NAME = "hcop_jp"
    HCOP_DB_USER = "hcop"
    HCOP_DB_PASSWORD = (New-RandomSecret 32)
    HCOP_BOOTSTRAP_SECOND_USERNAME = "marcolyto2"
    HCOP_QR_SECRET = (New-RandomSecret 48)
    HCOP_ENCRYPTION_SECRET = (New-RandomSecret 48)
    HCOP_PUBLIC_BASE_URL = "http://localhost:$portValue"
  }
  foreach ($key in $defaults.Keys) {
    if (-not $values.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string]$values[$key])) {
      $values[$key] = $defaults[$key]
      $changed = $true
    }
  }

  if ($changed) {
    Write-Step "Guardando la configuración local"
    Write-DotEnv $environmentFile $values
  }
  return $environmentFile
}

function Get-ConfiguredPort([string]$EnvironmentFile) {
  if (-not $EnvironmentFile -or -not (Test-Path -LiteralPath $EnvironmentFile)) { return 5180 }
  $values = Read-DotEnv $EnvironmentFile
  $value = if ($values.ContainsKey("HCOP_PORT")) { [string]$values["HCOP_PORT"] } else { "5180" }
  if ($value -notmatch "^\d{1,5}$" -or [int]$value -lt 1 -or [int]$value -gt 65535) {
    throw "HCOP_PORT no es válido."
  }
  return [int]$value
}

function Test-IsAdministrator {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
  $principal = New-Object Security.Principal.WindowsPrincipal($identity)
  return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Quote-ProcessArgument([string]$Value) {
  '"' + $Value.Replace('"', '\"') + '"'
}

function Restart-Elevated([string]$Root) {
  if ($Elevated) {
    throw "La operación requiere permisos de administrador y no pudo completarse aun con elevación."
  }
  Write-Step "Windows necesita autorizar una preparación única de WSL 2"
  Write-Info "Se abrirá la confirmación de administrador. Este instalador continuará automáticamente."
  Exit-OperationLock
  $arguments = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", (Quote-ProcessArgument $PSCommandPath),
    "-InstallDir", (Quote-ProcessArgument $Root),
    "-Mode", $Mode,
    "-Elevated"
  )
  if ($NoOpenBrowser) { $arguments += "-NoOpenBrowser" }
  try {
    $process = Start-Process -FilePath "powershell.exe" `
      -Verb RunAs `
      -ArgumentList ($arguments -join " ") `
      -Wait `
      -PassThru
  } catch {
    throw "Windows no autorizó la preparación de WSL 2. Ejecute nuevamente y acepte la confirmación de administrador."
  }
  $script:ExitCode = $process.ExitCode
  exit $process.ExitCode
}

function Invoke-NativeCapture(
  [string]$FilePath,
  [string[]]$Arguments,
  [string]$WorkingDirectory = ""
) {
  $previous = Get-Location
  try {
    if ($WorkingDirectory) { Set-Location -LiteralPath $WorkingDirectory }
    $output = @(& $FilePath @Arguments 2>&1)
    $code = $LASTEXITCODE
    return [pscustomobject]@{
      Code = $code
      Output = (($output | ForEach-Object { [string]$_ }) -join "`n")
    }
  } catch {
    return [pscustomobject]@{ Code = 9009; Output = $_.Exception.Message }
  } finally {
    if ($WorkingDirectory) { Set-Location -LiteralPath $previous }
  }
}

function Invoke-LoggedNative(
  [string]$FilePath,
  [string[]]$Arguments,
  [string]$WorkingDirectory = "",
  [string]$Description = "comando",
  [switch]$AllowFailure,
  [string]$InputText = $null
) {
  Write-Info $Description
  $previous = Get-Location
  try {
    if ($WorkingDirectory) { Set-Location -LiteralPath $WorkingDirectory }
    if ($null -ne $InputText) {
      $InputText | & $FilePath @Arguments 2>&1 | ForEach-Object { Write-Host ([string]$_) }
    } else {
      & $FilePath @Arguments 2>&1 | ForEach-Object { Write-Host ([string]$_) }
    }
    $code = $LASTEXITCODE
  } finally {
    if ($WorkingDirectory) { Set-Location -LiteralPath $previous }
  }
  if ($code -ne 0 -and -not $AllowFailure) {
    throw "$Description terminó con código $code."
  }
  return $code
}

function Refresh-ProcessPath {
  $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
  $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
  $env:Path = "$machinePath;$userPath"
}

function Find-Docker {
  $command = Get-Command docker.exe -ErrorAction SilentlyContinue
  if ($command) { return $command.Source }
  $candidates = @(
    (Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\resources\bin\docker.exe"),
    (Join-Path $env:LOCALAPPDATA "Programs\Docker\Docker\resources\bin\docker.exe"),
    (Join-Path $env:ProgramFiles "Docker\Docker\resources\bin\docker.exe")
  )
  foreach ($candidate in $candidates) {
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
  }
  return $null
}

function Find-DockerDesktop {
  $candidates = @(
    (Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\Docker Desktop.exe"),
    (Join-Path $env:LOCALAPPDATA "Programs\Docker\Docker\Docker Desktop.exe"),
    (Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe")
  )
  foreach ($candidate in $candidates) {
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
  }
  return $null
}

function Get-WslInfo {
  $wsl = Get-Command wsl.exe -ErrorAction SilentlyContinue
  if (-not $wsl) {
    return [pscustomobject]@{ Installed = $false; Version = ""; Ready = $false; Detail = "wsl.exe no está disponible" }
  }
  $result = Invoke-NativeCapture $wsl.Source @("--version")
  $text = $result.Output.Replace(([string][char]0), "")
  $version = ""
  $match = [regex]::Match($text, '(?m)(?:WSL|Subsistema)[^0-9]*(\d+\.\d+(?:\.\d+)?)')
  if ($match.Success) { $version = $match.Groups[1].Value }
  $ready = $result.Code -eq 0 -and -not [string]::IsNullOrWhiteSpace($version)
  return [pscustomobject]@{
    Installed = $ready
    Version = $version
    Ready = $ready
    Detail = $text.Trim()
  }
}

function Test-MinimumWsl([string]$Version) {
  try {
    $parsed = [version]$Version
    return $parsed -ge [version]"2.1.5"
  } catch {
    return $false
  }
}

function Ensure-Wsl([string]$Root, [bool]$AllowInstall) {
  $info = Get-WslInfo
  if ($info.Ready -and (Test-MinimumWsl $info.Version)) {
    $marker = Join-Path $Root "REINICIO-PENDIENTE.txt"
    if (Test-Path -LiteralPath $marker) { Remove-Item -LiteralPath $marker -Force }
    Write-Ok "WSL $($info.Version) disponible."
    return
  }
  if (-not $AllowInstall) {
    throw "WSL 2 no está listo. Ejecute 'Reparar HCOP JP.bat' con Internet y reinicie Windows si se solicita."
  }
  if (-not (Test-IsAdministrator)) { Restart-Elevated $Root }

  $wsl = (Get-Command wsl.exe -ErrorAction SilentlyContinue).Source
  if (-not $wsl) { $wsl = Join-Path $env:SystemRoot "System32\wsl.exe" }
  if ($info.Installed) {
    Write-Step "Actualizando WSL 2"
    $code = Invoke-LoggedNative $wsl @("--update", "--web-download") `
      -Description "Actualización de WSL 2" `
      -AllowFailure
    if ($code -ne 0) {
      Invoke-LoggedNative $wsl @("--update") -Description "Actualización alternativa de WSL 2"
    }
    $updated = Get-WslInfo
    if ($updated.Ready -and (Test-MinimumWsl $updated.Version)) {
      Write-Ok "WSL $($updated.Version) quedó actualizado."
      return
    }
  } else {
    Write-Step "Instalando WSL 2"
    $code = Invoke-LoggedNative $wsl @("--install", "--no-distribution", "--web-download") `
      -Description "Instalación de WSL 2" `
      -AllowFailure
    if ($code -ne 0) {
      $fallbackCode = Invoke-LoggedNative $wsl @("--install", "--no-distribution") `
        -Description "Instalación alternativa de WSL 2" `
        -AllowFailure
      if ($fallbackCode -ne 0) {
        Invoke-LoggedNative $wsl @("--install") `
          -Description "Instalación compatible de WSL 2"
      }
    }
  }

  $marker = Join-Path $Root "REINICIO-PENDIENTE.txt"
  [System.IO.File]::WriteAllText(
    $marker,
    "Reinicie Windows y vuelva a ejecutar el mismo instalador de HCOP JP.`r`n",
    (New-Object System.Text.UTF8Encoding($false)))
  Write-Host ""
  Write-Host "WSL 2 fue preparado. REINICIE WINDOWS y vuelva a ejecutar el mismo instalador." -ForegroundColor Yellow
  Write-Host "La instalación continuará sin perder lo ya realizado."
  $script:ExitCode = 3010
  throw "Reinicio de Windows pendiente. Consulte $marker"
}

function Find-Winget {
  $command = Get-Command winget.exe -ErrorAction SilentlyContinue
  if ($command) { return $command.Source }
  $candidate = Join-Path $env:LOCALAPPDATA "Microsoft\WindowsApps\winget.exe"
  if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
  return $null
}

function Test-DockerEngine([string]$DockerPath) {
  if (-not $DockerPath) { return $false }
  $result = Invoke-NativeCapture $DockerPath @("info", "--format", "{{.ServerVersion}}")
  return $result.Code -eq 0 -and -not [string]::IsNullOrWhiteSpace($result.Output)
}

function Test-DockerCompose([string]$DockerPath) {
  if (-not $DockerPath) {
    return [pscustomobject]@{ Ready = $false; Version = ""; Detail = "docker.exe no encontrado" }
  }
  $result = Invoke-NativeCapture $DockerPath @("compose", "version", "--short")
  $version = $result.Output.Trim()
  $ready = $result.Code -eq 0 -and $version -match '^\d+\.\d+'
  return [pscustomobject]@{ Ready = $ready; Version = $version; Detail = $result.Output.Trim() }
}

function Ensure-Docker([string]$Root, [bool]$AllowInstall) {
  $docker = Find-Docker
  if (-not $docker) {
    if (-not $AllowInstall) {
      throw "Docker Desktop no está instalado. Ejecute 'Reparar HCOP JP.bat' con Internet."
    }
    Ensure-Wsl $Root $true
    $winget = Find-Winget
    if (-not $winget) {
      throw "No se encontró winget. Instale Docker Desktop desde https://www.docker.com/products/docker-desktop/ y repita."
    }
    Write-Step "Instalando Docker Desktop"
    Invoke-LoggedNative $winget @(
      "install",
      "--exact",
      "--id", "Docker.DockerDesktop",
      "--accept-source-agreements",
      "--accept-package-agreements"
    ) -Description "Instalación de Docker Desktop"
    Refresh-ProcessPath
    $docker = Find-Docker
  }
  if (-not $docker) {
    throw "Docker Desktop fue instalado pero docker.exe todavía no está disponible. Reinicie Windows y ejecute nuevamente."
  }
  $script:DockerPath = $docker

  if (-not (Test-DockerEngine $docker)) {
    $desktop = Find-DockerDesktop
    if ($desktop) {
      Write-Step "Iniciando Docker Desktop"
      Start-Process -FilePath $desktop | Out-Null
    } elseif (-not $AllowInstall) {
      throw "Docker no responde y no se encontró Docker Desktop para iniciarlo."
    }
    $deadline = (Get-Date).AddMinutes(10)
    $nextMessage = Get-Date
    while ((Get-Date) -lt $deadline) {
      if (Test-DockerEngine $docker) { break }
      if ((Get-Date) -ge $nextMessage) {
        Write-Info "Esperando que Docker Desktop termine de iniciar..."
        $nextMessage = (Get-Date).AddSeconds(20)
      }
      Start-Sleep -Seconds 4
    }
  }
  if (-not (Test-DockerEngine $docker)) {
    throw "Docker Desktop no respondió en 10 minutos. Abra Docker Desktop, complete su primer inicio y ejecute 'Reparar HCOP JP.bat'."
  }

  $compose = Test-DockerCompose $docker
  if (-not $compose.Ready) {
    throw "Docker está activo, pero Docker Compose v2 no está disponible. Actualice Docker Desktop."
  }
  try {
    $parsed = [version]$compose.Version
    if ($parsed -lt [version]"2.20.0") {
      throw "Docker Compose $($compose.Version) es antiguo. Se requiere 2.20 o posterior."
    }
  } catch {
    if ($_.Exception.Message -like "Docker Compose * es antiguo*") { throw }
  }
  Write-Ok "Docker y Compose $($compose.Version) disponibles."
}

function Get-PortListeners([int]$Port) {
  try {
    return @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop)
  } catch {
    $rows = @()
    try {
      foreach ($line in @(netstat.exe -ano -p tcp 2>$null)) {
        if ($line -match "^\s*TCP\s+\S+:$Port\s+\S+\s+LISTENING\s+(\d+)\s*$") {
          $rows += [pscustomobject]@{ LocalPort = $Port; OwningProcess = [int]$matches[1] }
        }
      }
    } catch {}
    return $rows
  }
}

function Get-ResolvedInstallRoot([string]$Value) {
  if ([string]::IsNullOrWhiteSpace($Value)) { throw "La carpeta de instalación está vacía." }
  $resolved = [System.IO.Path]::GetFullPath($Value)
  $driveRoot = [System.IO.Path]::GetPathRoot($resolved)
  if ($resolved.Length -lt 5 -or $resolved.TrimEnd("\") -eq $driveRoot.TrimEnd("\")) {
    throw "La carpeta de instalación no puede ser la raíz de una unidad."
  }
  return $resolved.TrimEnd("\")
}

function Test-InstallerScript([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "Falta $Path."
  }
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile(
    $Path,
    [ref]$tokens,
    [ref]$errors) | Out-Null
  if ($errors.Count -gt 0) {
    $messages = @($errors | ForEach-Object { "$($_.Message) (línea $($_.Extent.StartLineNumber))" })
    throw "El instalador descargado no es válido:`n$($messages -join "`n")"
  }
}

function Ensure-GitHubCli {
  $command = Get-Command gh.exe -ErrorAction SilentlyContinue
  if (-not $command) {
    Write-Step "Instalando GitHub CLI para acceder al repositorio"
    $winget = Find-Winget
    if (-not $winget) {
      throw "Se necesita GitHub CLI. Instálelo desde https://cli.github.com/ y vuelva a intentar."
    }
    Invoke-LoggedNative $winget @(
      "install",
      "--exact",
      "--id", "GitHub.cli",
      "--accept-source-agreements",
      "--accept-package-agreements"
    ) -Description "Instalación de GitHub CLI"
    Refresh-ProcessPath
    $command = Get-Command gh.exe -ErrorAction SilentlyContinue
    if (-not $command) {
      $candidate = Join-Path $env:ProgramFiles "GitHub CLI\gh.exe"
      if (Test-Path -LiteralPath $candidate) { $command = Get-Item -LiteralPath $candidate }
    }
  }
  if (-not $command) { throw "GitHub CLI no está disponible." }
  $executable = if ($command.Source) { $command.Source } else { $command.FullName }
  $status = Invoke-NativeCapture $executable @("auth", "status", "--hostname", "github.com")
  if ($status.Code -ne 0) {
    Write-Step "Autorizando el acceso seguro a GitHub"
    Invoke-LoggedNative $executable @(
      "auth", "login",
      "--hostname", "github.com",
      "--git-protocol", "https",
      "--web"
    ) -Description "Inicio de sesión en GitHub"
  }
  return $executable
}

function Get-GitHubAccess {
  $executable = Ensure-GitHubCli
  $tokenResult = Invoke-NativeCapture $executable @("auth", "token")
  $userResult = Invoke-NativeCapture $executable @("api", "user", "--jq", ".login")
  $token = $tokenResult.Output.Trim()
  $username = $userResult.Output.Trim()
  if ($tokenResult.Code -ne 0 -or $userResult.Code -ne 0 -or
      [string]::IsNullOrWhiteSpace($token) -or [string]::IsNullOrWhiteSpace($username)) {
    throw "La sesión de GitHub no entregó credenciales válidas."
  }
  return @{ Executable = $executable; Token = $token; Username = $username }
}

function Get-GitHubHeaders([hashtable]$Access) {
  $headers = @{
    Accept = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
    "User-Agent" = "HCOP-JP-Installer"
  }
  if ($Access -and $Access.Token) { $headers.Authorization = "Bearer $($Access.Token)" }
  return $headers
}

function Download-RepositoryArchive([string]$Destination) {
  $access = $null
  try {
    Invoke-WebRequest -UseBasicParsing -Uri $script:RepositoryZip -OutFile $Destination
  } catch {
    Write-Step "El repositorio requiere autorización de GitHub"
    $access = Get-GitHubAccess
    Invoke-WebRequest -UseBasicParsing `
      -Headers (Get-GitHubHeaders $access) `
      -Uri $script:RepositoryArchiveApi `
      -OutFile $Destination
  }
  if (-not (Test-Path -LiteralPath $Destination) -or (Get-Item -LiteralPath $Destination).Length -lt 1024) {
    throw "GitHub no entregó un archivo de proyecto válido."
  }
  return $access
}

function Get-RemoteCommit([hashtable]$Access) {
  try {
    $commit = Invoke-RestMethod -UseBasicParsing `
      -Headers (Get-GitHubHeaders $Access) `
      -Uri $script:RepositoryCommitApi
    $sha = [string]$commit.sha
    if ($sha -match '^[0-9a-fA-F]{40}$') { return $sha.ToLowerInvariant() }
  } catch {
    Write-Warn "No se pudo identificar el commit remoto; se usará una compilación local verificable."
  }
  return ""
}

function Install-Candidate([string]$Root) {
  Write-Step "Descargando la versión más reciente desde GitHub"
  $temporary = Join-Path ([System.IO.Path]::GetTempPath()) ("hcop-jp-" + [Guid]::NewGuid().ToString("N"))
  $archive = "$temporary.zip"
  try {
    $access = Download-RepositoryArchive $archive
    $commit = Get-RemoteCommit $access
    Expand-Archive -LiteralPath $archive -DestinationPath $temporary -Force
    $source = Get-ChildItem -LiteralPath $temporary -Directory |
      Where-Object {
        Test-Path -LiteralPath (Join-Path $_.FullName "compose.yaml") -PathType Leaf
      } |
      Select-Object -First 1
    if (-not $source) { throw "El paquete descargado no contiene HCOP JP." }

    $suffix = if ($commit) {
      $commit.Substring(0, 12)
    } else {
      (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.Substring(0, 12).ToLowerInvariant()
    }
    $versionName = "{0}-{1}" -f (Get-Date).ToString("yyyyMMdd-HHmmssfff"), $suffix
    $versions = Join-Path $Root "versions"
    $destination = Join-Path $versions $versionName
    New-Item -ItemType Directory -Path $versions -Force | Out-Null
    Copy-Item -LiteralPath $source.FullName -Destination $destination -Recurse

    foreach ($required in @(
        "compose.yaml",
        "compose.github.yaml",
        "Dockerfile",
        "scripts\instalar-desde-github.ps1")) {
      if (-not (Test-Path -LiteralPath (Join-Path $destination $required) -PathType Leaf)) {
        throw "La versión descargada no contiene $required."
      }
    }
    Test-InstallerScript (Join-Path $destination "scripts\instalar-desde-github.ps1")
    return [pscustomobject]@{
      Path = $destination
      Commit = $commit
      Access = $access
    }
  } finally {
    if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Recurse -Force }
  }
}

function Write-ReleaseMetadata(
  [string]$VersionPath,
  [string]$Commit,
  [string]$ReleaseMode,
  [string[]]$ComposeFiles,
  [string]$Image = ""
) {
  $metadata = [ordered]@{
    schemaVersion = 1
    installedAt = (Get-Date).ToUniversalTime().ToString("o")
    commit = $Commit
    mode = $ReleaseMode
    composeFiles = $ComposeFiles
    image = $Image
  }
  [System.IO.File]::WriteAllText(
    (Join-Path $VersionPath "release.json"),
    ($metadata | ConvertTo-Json -Depth 4),
    (New-Object System.Text.UTF8Encoding($false)))
}

function Try-PreparePublishedRelease([pscustomobject]$Candidate) {
  if (-not $Candidate.Commit) { return $false }
  $short = $Candidate.Commit.Substring(0, 7)
  $image = "$($script:PublishedImage):sha-$short"
  if ($Candidate.Access) {
    Write-Step "Autorizando la lectura del paquete Docker privado"
    $refreshCode = Invoke-LoggedNative $Candidate.Access.Executable @(
      "auth", "refresh",
      "--hostname", "github.com",
      "--scopes", "read:packages"
    ) -Description "Permiso read:packages en GitHub" `
      -AllowFailure
    if ($refreshCode -ne 0) {
      Write-Warn "GitHub no concedió read:packages; se construirá desde el código."
      return $false
    }
    $refreshedToken = Invoke-NativeCapture $Candidate.Access.Executable @("auth", "token")
    if ($refreshedToken.Code -ne 0 -or [string]::IsNullOrWhiteSpace($refreshedToken.Output)) {
      Write-Warn "No se pudo obtener el token renovado para GHCR."
      return $false
    }
    $Candidate.Access.Token = $refreshedToken.Output.Trim()
    $loginCode = Invoke-LoggedNative $script:DockerPath @(
      "login", "ghcr.io",
      "--username", $Candidate.Access.Username,
      "--password-stdin"
    ) -Description "Autorización de la imagen privada" `
      -InputText $Candidate.Access.Token `
      -AllowFailure
    if ($loginCode -ne 0) { return $false }
  }
  Write-Step "Buscando la imagen publicada de esta misma versión"
  $pullCode = Invoke-LoggedNative $script:DockerPath @("pull", $image) `
    -Description "Descarga de $image" `
    -AllowFailure
  if ($pullCode -ne 0) { return $false }

  $override = Join-Path $Candidate.Path "compose.release.override.yaml"
  $content = @"
services:
  application:
    image: $image
    pull_policy: missing
"@
  [System.IO.File]::WriteAllText(
    $override,
    $content,
    (New-Object System.Text.UTF8Encoding($false)))
  Write-ReleaseMetadata `
    $Candidate.Path `
    $Candidate.Commit `
    "published" `
    @("compose.github.yaml", "compose.release.override.yaml") `
    $image
  return $true
}

function Prepare-CandidateRelease([pscustomobject]$Candidate) {
  if (Try-PreparePublishedRelease $Candidate) {
    Write-Ok "Se usará la imagen publicada correspondiente al commit descargado."
  } else {
    Write-Warn "La imagen exacta no está disponible. Se construirá desde el código descargado para evitar usar una versión desactualizada."
    Write-ReleaseMetadata $Candidate.Path $Candidate.Commit "local-build" @("compose.yaml")
  }
  return Get-ReleaseFromPath $Candidate.Path
}

function Get-ReleaseFromPath([string]$VersionPath) {
  if (-not (Test-Path -LiteralPath $VersionPath -PathType Container)) {
    throw "La versión $VersionPath no existe."
  }
  $metadataPath = Join-Path $VersionPath "release.json"
  if (Test-Path -LiteralPath $metadataPath -PathType Leaf) {
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $relativeFiles = @($metadata.composeFiles)
    $releaseMode = [string]$metadata.mode
    $commit = [string]$metadata.commit
  } else {
    $relativeFiles = @("compose.yaml")
    $releaseMode = "local-build"
    $commit = ""
  }
  $composeFiles = @()
  foreach ($relative in $relativeFiles) {
    $candidate = Join-Path $VersionPath ([string]$relative)
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
      throw "La versión no contiene $relative."
    }
    $composeFiles += $candidate
  }
  return [pscustomobject]@{
    Path = $VersionPath
    Mode = $releaseMode
    Commit = $commit
    ComposeFiles = $composeFiles
  }
}

function Read-VersionPointer([string]$Root, [string]$Name, [switch]$Optional) {
  $pointer = Join-Path $Root $Name
  if (-not (Test-Path -LiteralPath $pointer -PathType Leaf)) {
    if ($Optional) { return $null }
    throw "No hay una versión instalada. Ejecute 'Actualizar HCOP JP.bat' o el instalador."
  }
  $versionPath = (Get-Content -LiteralPath $pointer -Raw).Trim()
  if ([string]::IsNullOrWhiteSpace($versionPath)) {
    if ($Optional) { return $null }
    throw "$Name está vacío."
  }
  if (-not (Test-SafeVersionPath $Root $versionPath)) {
    throw "$Name apunta fuera de la carpeta versions y fue rechazado por seguridad."
  }
  return Get-ReleaseFromPath $versionPath
}

function Get-ComposeArguments(
  [pscustomobject]$Release,
  [string]$EnvironmentFile,
  [string[]]$CommandArguments
) {
  $arguments = @("compose", "--project-name", $script:ProjectName)
  if ($EnvironmentFile -and (Test-Path -LiteralPath $EnvironmentFile -PathType Leaf)) {
    $arguments += @("--env-file", $EnvironmentFile)
  }
  foreach ($composeFile in $Release.ComposeFiles) {
    $arguments += @("-f", $composeFile)
  }
  $arguments += $CommandArguments
  return $arguments
}

function Invoke-Compose(
  [pscustomobject]$Release,
  [string]$EnvironmentFile,
  [string[]]$CommandArguments,
  [string]$Description,
  [switch]$AllowFailure
) {
  $arguments = Get-ComposeArguments $Release $EnvironmentFile $CommandArguments
  return Invoke-LoggedNative $script:DockerPath $arguments `
    -WorkingDirectory $Release.Path `
    -Description $Description `
    -AllowFailure:$AllowFailure
}

function Invoke-ComposeCapture(
  [pscustomobject]$Release,
  [string]$EnvironmentFile,
  [string[]]$CommandArguments
) {
  $arguments = Get-ComposeArguments $Release $EnvironmentFile $CommandArguments
  return Invoke-NativeCapture $script:DockerPath $arguments $Release.Path
}

function Test-PortOwnedByHcop(
  [int]$Port,
  [pscustomobject]$CurrentRelease,
  [string]$EnvironmentFile
) {
  if (-not $CurrentRelease -or -not $script:DockerPath) { return $false }
  $result = Invoke-ComposeCapture $CurrentRelease $EnvironmentFile @("port", "application", "5180")
  if ($result.Code -ne 0) { return $false }
  return $result.Output -match "(?:^|:)$Port(?:\s|$)"
}

function Assert-PortAvailable(
  [int]$Port,
  [pscustomobject]$CurrentRelease,
  [string]$EnvironmentFile
) {
  $listeners = @(Get-PortListeners $Port)
  if ($listeners.Count -eq 0) { return }
  if (Test-PortOwnedByHcop $Port $CurrentRelease $EnvironmentFile) {
    Write-Info "El puerto $Port ya pertenece a HCOP JP; se actualizará sin superponer servicios."
    return
  }
  $owners = @($listeners | ForEach-Object { $_.OwningProcess } | Where-Object { $_ } | Sort-Object -Unique)
  $detail = if ($owners.Count) { " (PID: $($owners -join ', '))" } else { "" }
  throw "El puerto $Port está ocupado por otro programa$detail. Cambie HCOP_PORT en $EnvironmentFile o cierre ese programa."
}

function Show-ComposeDiagnostics(
  [pscustomobject]$Release,
  [string]$EnvironmentFile
) {
  if (-not $Release -or -not $script:DockerPath) { return }
  Write-Host ""
  Write-Host "Diagnóstico de Docker" -ForegroundColor Yellow
  Invoke-Compose $Release $EnvironmentFile @("ps", "--all") "Estado de contenedores" -AllowFailure | Out-Null
  Invoke-Compose $Release $EnvironmentFile @("logs", "--no-color", "--tail", "200") "Últimos registros" -AllowFailure | Out-Null
}

function Test-HttpSmoke([int]$Port) {
  $baseUrl = "http://127.0.0.1:$Port"
  $deadline = (Get-Date).AddMinutes(3)
  $health = $null
  while ((Get-Date) -lt $deadline) {
    try {
      $health = Invoke-RestMethod -UseBasicParsing -Uri "$baseUrl/actuator/health" -TimeoutSec 10
      if ($health.status -eq "UP") { break }
    } catch {}
    Start-Sleep -Seconds 3
  }
  if ($null -eq $health -or $health.status -ne "UP") {
    throw "La aplicación no alcanzó el estado saludable en $baseUrl."
  }
  $homeResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/" -TimeoutSec 20
  if ($homeResponse.StatusCode -ne 200 -or $homeResponse.Content.Length -lt 500) {
    throw "La interfaz web no respondió correctamente."
  }
  $runtime = Invoke-RestMethod -UseBasicParsing -Uri "$baseUrl/api/runtime/status" -TimeoutSec 20
  if ($null -eq $runtime -or $runtime.ok -ne $true) {
    throw "El estado funcional público no respondió correctamente."
  }
  Write-Ok "Salud, interfaz y estado funcional verificados."
}

function Start-Release(
  [pscustomobject]$Release,
  [string]$EnvironmentFile,
  [pscustomobject]$CurrentRelease
) {
  $port = Get-ConfiguredPort $EnvironmentFile
  Assert-PortAvailable $port $CurrentRelease $EnvironmentFile
  $arguments = @("up", "--detach", "--wait", "--wait-timeout", "360")
  if ($Release.Mode -eq "local-build") { $arguments += "--build" }
  Invoke-Compose $Release $EnvironmentFile $arguments "Inicio de HCOP JP" | Out-Null
  Test-HttpSmoke $port
}

function Stop-Release(
  [pscustomobject]$Release,
  [string]$EnvironmentFile,
  [switch]$AllowFailure
) {
  Invoke-Compose $Release $EnvironmentFile @("down", "--remove-orphans") `
    "Detención de HCOP JP" `
    -AllowFailure:$AllowFailure | Out-Null
}

function Write-AtomicText([string]$Path, [string]$Value) {
  $temporary = "$Path.tmp"
  [System.IO.File]::WriteAllText(
    $temporary,
    $Value,
    (New-Object System.Text.UTF8Encoding($false)))
  Move-Item -LiteralPath $temporary -Destination $Path -Force
}

function Write-LauncherFile(
  [string]$Path,
  [string]$LauncherMode
) {
  $content = @"
@echo off
setlocal EnableExtensions
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar-desde-github.ps1" -Mode $LauncherMode -InstallDir "%~dp0"
set "HCOP_RESULT=%ERRORLEVEL%"
if "%HCOP_RESULT%"=="3010" (
  echo.
  echo Reinicie Windows y vuelva a ejecutar este acceso directo.
  pause
) else if not "%HCOP_RESULT%"=="0" (
  echo.
  echo La operacion no pudo completarse. Revise la ruta del registro mostrada arriba.
  pause
)
exit /b %HCOP_RESULT%
"@
  [System.IO.File]::WriteAllText($Path, $content, [System.Text.Encoding]::ASCII)
}

function Write-Launchers([string]$Root) {
  Write-LauncherFile (Join-Path $Root "Iniciar HCOP JP.bat") "Start"
  Write-LauncherFile (Join-Path $Root "Actualizar HCOP JP.bat") "Update"
  Write-LauncherFile (Join-Path $Root "Reparar HCOP JP.bat") "Repair"
  Write-LauncherFile (Join-Path $Root "Detener HCOP JP.bat") "Stop"
  Write-LauncherFile (Join-Path $Root "Lanzar HCOP JP.bat") "Start"
  try {
    $desktop = [Environment]::GetFolderPath("Desktop")
    $shortcutPath = Join-Path $desktop "HCOP JP.lnk"
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = Join-Path $Root "Iniciar HCOP JP.bat"
    $shortcut.WorkingDirectory = $Root
    $shortcut.Description = "Iniciar HCOP JP sin descargar actualizaciones"
    $shortcut.Save()
  } catch {
    Write-Warn "No se pudo crear el acceso directo; los lanzadores quedaron en $Root."
  }
}

function Promote-ControlScript([string]$Root, [string]$VersionPath) {
  $source = Join-Path $VersionPath "scripts\instalar-desde-github.ps1"
  Test-InstallerScript $source
  $destination = Join-Path $Root "instalar-desde-github.ps1"
  $temporary = "$destination.new"
  Copy-Item -LiteralPath $source -Destination $temporary -Force
  Move-Item -LiteralPath $temporary -Destination $destination -Force
}

function Test-SafeVersionPath([string]$Root, [string]$Path) {
  $versionsRoot = [System.IO.Path]::GetFullPath((Join-Path $Root "versions")).TrimEnd("\")
  $resolved = [System.IO.Path]::GetFullPath($Path).TrimEnd("\")
  return $resolved.StartsWith(
    $versionsRoot + [System.IO.Path]::DirectorySeparatorChar,
    [StringComparison]::OrdinalIgnoreCase)
}

function Remove-SafeVersion([string]$Root, [string]$Path) {
  if (-not (Test-SafeVersionPath $Root $Path)) {
    throw "Se rechazó limpiar una ruta fuera de la carpeta versions: $Path"
  }
  if (Test-Path -LiteralPath $Path -PathType Container) {
    Remove-Item -LiteralPath $Path -Recurse -Force
  }
}

function Cleanup-OldVersions(
  [string]$Root,
  [string[]]$KeepPaths
) {
  $versionsRoot = Join-Path $Root "versions"
  if (-not (Test-Path -LiteralPath $versionsRoot -PathType Container)) { return }
  $keep = @($KeepPaths | Where-Object { $_ } | ForEach-Object {
      [System.IO.Path]::GetFullPath($_).TrimEnd("\").ToLowerInvariant()
    })
  foreach ($directory in Get-ChildItem -LiteralPath $versionsRoot -Directory) {
    $resolved = [System.IO.Path]::GetFullPath($directory.FullName).TrimEnd("\").ToLowerInvariant()
    if ($resolved -notin $keep) {
      Remove-SafeVersion $Root $directory.FullName
    }
  }
}

function Promote-Candidate(
  [string]$Root,
  [pscustomobject]$CandidateRelease,
  [pscustomobject]$PreviousRelease
) {
  Write-Step "Promoviendo la versión verificada"
  if ($PreviousRelease) {
    Write-AtomicText (Join-Path $Root "previous.txt") $PreviousRelease.Path
  }
  Write-AtomicText (Join-Path $Root "current.txt") $CandidateRelease.Path
  Promote-ControlScript $Root $CandidateRelease.Path
  Write-Launchers $Root
  $keep = @($CandidateRelease.Path)
  if ($PreviousRelease) { $keep += $PreviousRelease.Path }
  Cleanup-OldVersions $Root $keep
}

function Open-Hcop([string]$EnvironmentFile) {
  $port = Get-ConfiguredPort $EnvironmentFile
  $url = "http://localhost:$port"
  Write-Host ""
  Write-Host "HCOP JP está funcionando en $url" -ForegroundColor Green
  Write-Host "Swagger / OpenAPI: $url/swagger-ui.html"
  if (-not $NoOpenBrowser) { Start-Process $url }
}

function Install-OrUpdate([string]$Root) {
  Ensure-Wsl $Root $true
  Ensure-Docker $Root $true
  $environment = Ensure-Environment $Root
  Write-Launchers $Root
  $current = Read-VersionPointer $Root "current.txt" -Optional
  $candidateInfo = Install-Candidate $Root
  $candidate = Prepare-CandidateRelease $candidateInfo
  $script:ActiveRelease = $candidate

  if ($current -and $candidate.Commit -and $candidate.Commit -eq $current.Commit) {
    Write-Ok "La versión estable ya corresponde al último commit publicado."
    Remove-SafeVersion $Root $candidate.Path
    Start-Release $current $environment $current
    Open-Hcop $environment
    return
  }

  try {
    Start-Release $candidate $environment $current
  } catch {
    Write-Warn "La versión candidata no superó la validación: $($_.Exception.Message)"
    Show-ComposeDiagnostics $candidate $environment
    if ($current) {
      Write-Step "Restaurando la versión estable anterior"
      try {
        Start-Release $current $environment $candidate
        Write-Ok "La versión anterior volvió a quedar operativa."
      } catch {
        Show-ComposeDiagnostics $current $environment
        throw "Falló la versión nueva y también el rollback. Revise $script:LogPath."
      }
    } else {
      Stop-Release $candidate $environment -AllowFailure
    }
    throw "La actualización fue rechazada; current.txt no fue modificado."
  }

  Promote-Candidate $Root $candidate $current
  Open-Hcop $environment
}

function Start-Stable([string]$Root, [bool]$AllowInstallPlatform) {
  Ensure-Wsl $Root $AllowInstallPlatform
  Ensure-Docker $Root $AllowInstallPlatform
  $environment = Join-Path $Root ".env"
  if (-not (Test-Path -LiteralPath $environment -PathType Leaf)) {
    if ($AllowInstallPlatform) {
      $environment = Ensure-Environment $Root
    } else {
      throw "Falta la configuración $environment. Ejecute 'Reparar HCOP JP.bat'."
    }
  }
  $current = Read-VersionPointer $Root "current.txt"
  $script:ActiveRelease = $current
  try {
    Start-Release $current $environment $current
  } catch {
    Show-ComposeDiagnostics $current $environment
    throw
  }
  Write-Launchers $Root
  Open-Hcop $environment
}

function Repair-Stable([string]$Root) {
  Ensure-Wsl $Root $true
  Ensure-Docker $Root $true
  $environment = Ensure-Environment $Root
  Write-Launchers $Root
  $current = Read-VersionPointer $Root "current.txt" -Optional
  if ($current) {
    try {
      Start-Release $current $environment $current
      Write-Ok "La versión estable fue reparada y validada."
      Open-Hcop $environment
      return
    } catch {
      Write-Warn "La versión estable no pudo arrancar: $($_.Exception.Message)"
      Show-ComposeDiagnostics $current $environment
    }
  }
  $previous = Read-VersionPointer $Root "previous.txt" -Optional
  if ($previous) {
    Write-Step "Intentando recuperar la versión anterior"
    try {
      Start-Release $previous $environment $current
      if ($current) { Write-AtomicText (Join-Path $Root "previous.txt") $current.Path }
      Write-AtomicText (Join-Path $Root "current.txt") $previous.Path
      Promote-ControlScript $Root $previous.Path
      Write-Launchers $Root
      Write-Ok "La versión anterior quedó restaurada."
      Open-Hcop $environment
      return
    } catch {
      Show-ComposeDiagnostics $previous $environment
    }
  }
  Write-Warn "No quedó una versión local utilizable. Se descargará una versión nueva."
  Install-OrUpdate $Root
}

function Stop-Stable([string]$Root) {
  $docker = Find-Docker
  if (-not $docker) { throw "Docker no está instalado; no hay contenedores de HCOP JP que detener." }
  $script:DockerPath = $docker
  if (-not (Test-DockerEngine $docker)) {
    throw "Docker Desktop no está iniciado. HCOP JP ya está detenido o Docker necesita reparación."
  }
  $environment = Join-Path $Root ".env"
  $current = Read-VersionPointer $Root "current.txt"
  Stop-Release $current $environment
  Write-Ok "HCOP JP fue detenido. Los datos se conservaron."
}

function New-SourceRelease([string]$Root) {
  $compose = Join-Path $Root "compose.yaml"
  if (-not (Test-Path -LiteralPath $compose -PathType Leaf)) {
    throw "No se encontró compose.yaml en $Root."
  }
  return [pscustomobject]@{
    Path = $Root
    Mode = "local-build"
    Commit = ""
    ComposeFiles = @($compose)
  }
}

function Start-Source([string]$Root) {
  Ensure-Wsl $Root $true
  Ensure-Docker $Root $true
  $environment = Join-Path $Root ".env"
  if (-not (Test-Path -LiteralPath $environment -PathType Leaf)) { $environment = "" }
  $release = New-SourceRelease $Root
  $script:ActiveRelease = $release
  try {
    Start-Release $release $environment $release
  } catch {
    Show-ComposeDiagnostics $release $environment
    throw
  }
  Open-Hcop $environment
}

function Stop-Source([string]$Root, [switch]$AllowFailure) {
  $docker = Find-Docker
  if (-not $docker) {
    if ($AllowFailure) { return }
    throw "Docker no está instalado."
  }
  $script:DockerPath = $docker
  if (-not (Test-DockerEngine $docker)) {
    if ($AllowFailure) { return }
    throw "Docker Desktop no está iniciado."
  }
  $environment = Join-Path $Root ".env"
  if (-not (Test-Path -LiteralPath $environment -PathType Leaf)) { $environment = "" }
  Stop-Release (New-SourceRelease $Root) $environment -AllowFailure:$AllowFailure
}

function Get-PreflightReport([string]$Root) {
  $wsl = Get-WslInfo
  $docker = Find-Docker
  $compose = Test-DockerCompose $docker
  $engine = Test-DockerEngine $docker
  $environment = Join-Path $Root ".env"
  $port = Get-ConfiguredPort $environment
  $listeners = @(Get-PortListeners $port)
  $currentPointer = Join-Path $Root "current.txt"
  return [ordered]@{
    ok = $true
    mode = "Preflight"
    installDir = $Root
    administrator = (Test-IsAdministrator)
    wslInstalled = $wsl.Installed
    wslVersion = $wsl.Version
    wslMinimumSatisfied = ($wsl.Ready -and (Test-MinimumWsl $wsl.Version))
    dockerPath = if ($docker) { $docker } else { "" }
    dockerEngineReady = $engine
    composeReady = $compose.Ready
    composeVersion = $compose.Version
    configuredPort = $port
    portListening = $listeners.Count -gt 0
    stableVersionPresent = (Test-Path -LiteralPath $currentPointer -PathType Leaf)
    restartPending = (Test-Path -LiteralPath (Join-Path $Root "REINICIO-PENDIENTE.txt") -PathType Leaf)
    readyToStart = (
      $wsl.Ready -and
      (Test-MinimumWsl $wsl.Version) -and
      $engine -and
      $compose.Ready -and
      (Test-Path -LiteralPath $currentPointer -PathType Leaf))
  }
}

function Invoke-ValidateOnly {
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile(
    $PSCommandPath,
    [ref]$tokens,
    [ref]$errors) | Out-Null
  $repositoryRoot = Split-Path -Parent $PSScriptRoot
  $required = @(
    "Dockerfile",
    "compose.yaml",
    "compose.github.yaml",
    "INSTALAR-DESDE-GITHUB.bat",
    "iniciar.bat",
    "detener.bat",
    "reiniciar.bat"
  )
  $missing = @()
  foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot $path) -PathType Leaf)) {
      $missing += $path
    }
  }
  $result = [ordered]@{
    ok = ($errors.Count -eq 0 -and $missing.Count -eq 0)
    mode = "ValidateOnly"
    powershell = $PSVersionTable.PSVersion.ToString()
    parserErrors = @($errors | ForEach-Object { $_.Message })
    missingFiles = $missing
    dockerRequired = $false
    networkRequired = $false
  }
  $result | ConvertTo-Json -Depth 5
  if (-not $result.ok) { throw "La validación estática no fue satisfactoria." }
}

$sourceMode = $Mode -in @("SourceStart", "SourceStop", "SourceRestart")
$resolvedRoot = Get-ResolvedInstallRoot $InstallDir

try {
  if ($Mode -eq "ValidateOnly") {
    Invoke-ValidateOnly
    $script:ExitCode = 0
  } elseif ($Mode -eq "Preflight") {
    (Get-PreflightReport $resolvedRoot) | ConvertTo-Json -Depth 5
    $script:ExitCode = 0
  } else {
    New-Item -ItemType Directory -Path $resolvedRoot -Force | Out-Null
    Start-InstallerLog $resolvedRoot $sourceMode
    Enter-OperationLock $resolvedRoot
    Write-Step "HCOP JP · $Mode"
    switch ($Mode) {
      "Install" {
        Install-OrUpdate $resolvedRoot
      }
      "Update" {
        Install-OrUpdate $resolvedRoot
      }
      "Start" {
        Start-Stable $resolvedRoot $false
      }
      "Repair" {
        Repair-Stable $resolvedRoot
      }
      "Stop" {
        Stop-Stable $resolvedRoot
      }
      "SourceStart" {
        Start-Source $resolvedRoot
      }
      "SourceStop" {
        Stop-Source $resolvedRoot
        Write-Ok "HCOP JP fue detenido. Los datos se conservaron."
      }
      "SourceRestart" {
        Stop-Source $resolvedRoot -AllowFailure
        Start-Source $resolvedRoot
      }
    }
    $script:ExitCode = 0
  }
} catch {
  Write-Host ""
  Write-Host "HCOP JP no pudo completar '$Mode'." -ForegroundColor Red
  Write-Host $_.Exception.Message -ForegroundColor Red
  if ($script:LogPath) {
    Write-Host "Detalle: $script:LogPath" -ForegroundColor Yellow
  }
  if ($script:ExitCode -eq 0) { $script:ExitCode = 1 }
} finally {
  Exit-OperationLock
  Stop-InstallerLog
}

exit $script:ExitCode
