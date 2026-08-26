param(
  [ValidateSet("Start", "Update", "Stop", "Status", "ValidateOnly")]
  [string]$Mode = "Start",
  [ValidateSet("Stable", "Migration")]
  [string]$Channel = "Stable",
  [string]$DataDirectory = "",
  [switch]$NoOpenBrowser
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$script:ProjectName = if ($Channel -eq "Migration") { "hcop-ahjp" } else { "hcop-jp" }
$script:ResourcePrefix = if ($Channel -eq "Migration") { "hcop_ahjp" } else { "hcop_jp" }
$script:DatabaseName = if ($Channel -eq "Migration") { "hcop_ahjp" } else { "hcop_jp" }
$script:DefaultHostPort = if ($Channel -eq "Migration") { 5181 } else { 5180 }
$defaultDirectoryName = if ($Channel -eq "Migration") { "HCOP_AHJP-Docker" } else { "HCOP_JP-Docker" }
$script:DefaultDataDirectory = Join-Path $env:LOCALAPPDATA $defaultDirectoryName
$script:BackendImage = if ($Channel -eq "Migration") {
  "ghcr.io/marcolyto/hcop_jp-backend:angular-full-parity-v2"
} else {
  "ghcr.io/marcolyto/hcop_jp-backend:latest"
}
$script:FrontendImage = if ($Channel -eq "Migration") {
  "ghcr.io/marcolyto/hcop_jp-frontend:angular-full-parity-v2"
} else {
  "ghcr.io/marcolyto/hcop_jp-frontend:latest"
}
$script:PostgresImage = "postgres:18.4-alpine"
# El canal "Migration" queda anclado al tag angular-full-parity-v2 (previo al split
# backend/bff/frontend, no existe imagen bff para ese tag) — solo el canal "Stable" habla
# con el BFF+Redis (topología real desde F1).
$script:UsesBff = ($Channel -ne "Migration")
$script:BffImage = "ghcr.io/marcolyto/hcop_jp-bff:latest"
$script:RedisImage = "redis:7-alpine"
$script:ApplicationUrl = "http://localhost:$($script:DefaultHostPort)"
$script:ApplicationEntryUrl = $script:ApplicationUrl
$script:PostgresVolume = "$($script:ResourcePrefix)_postgres"
$script:StorageVolume = "$($script:ResourcePrefix)_storage"
$script:LogPath = $null
$script:TranscriptStarted = $false
$script:OperationMutex = $null
$script:ExitCode = 1

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

function Resolve-DataDirectory([string]$Path) {
  if ([string]::IsNullOrWhiteSpace($Path)) {
    throw "La carpeta local de HCOP JP no puede estar vacía."
  }
  return [IO.Path]::GetFullPath([Environment]::ExpandEnvironmentVariables($Path))
}

function Start-OperationLog([string]$Root) {
  $logDirectory = Join-Path $Root "logs"
  New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
  $stamp = (Get-Date).ToString("yyyyMMdd-HHmmssfff")
  $script:LogPath = Join-Path $logDirectory ("hcop-docker-{0}-{1}.log" -f $Mode.ToLowerInvariant(), $stamp)
  try {
    Start-Transcript -LiteralPath $script:LogPath -Force | Out-Null
    $script:TranscriptStarted = $true
  } catch {
    [IO.File]::WriteAllText(
      $script:LogPath,
      "HCOP JP Docker - $Mode - $(Get-Date -Format o)`r`n",
      (New-Object Text.UTF8Encoding($false)))
  }
  Write-Info "Registro: $script:LogPath"
}

function Stop-OperationLog {
  if (-not $script:TranscriptStarted) { return }
  try { Stop-Transcript | Out-Null } catch {}
  $script:TranscriptStarted = $false
}

function Enter-OperationLock([string]$Root) {
  $bytes = [Text.Encoding]::UTF8.GetBytes($Root.ToLowerInvariant())
  $hasher = [Security.Cryptography.SHA256]::Create()
  try {
    $hash = ([BitConverter]::ToString($hasher.ComputeHash($bytes))).Replace("-", "").Substring(0, 20)
  } finally {
    $hasher.Dispose()
  }
  $created = $false
  $mutex = New-Object Threading.Mutex($false, "Local\HCOPJPDocker-$hash", [ref]$created)
  if (-not $mutex.WaitOne(0)) {
    $mutex.Dispose()
    throw "Ya hay otra operación de HCOP JP en curso. Espere y vuelva a intentar."
  }
  $script:OperationMutex = $mutex
}

function Exit-OperationLock {
  if (-not $script:OperationMutex) { return }
  try { $script:OperationMutex.ReleaseMutex() } catch {}
  $script:OperationMutex.Dispose()
  $script:OperationMutex = $null
}

function Write-AtomicUtf8([string]$Path, [string]$Content) {
  $parent = Split-Path -Parent $Path
  New-Item -ItemType Directory -Path $parent -Force | Out-Null
  $temporary = "$Path.tmp-$([guid]::NewGuid().ToString('N'))"
  try {
    [IO.File]::WriteAllText($temporary, $Content, (New-Object Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $temporary -Destination $Path -Force
  } finally {
    if (Test-Path -LiteralPath $temporary) {
      Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    }
  }
}

function Get-ComposeDocument {
  $document = @'
name: __PROJECT_NAME__

services:
  database:
    image: postgres:18.4-alpine
    restart: unless-stopped
    shm_size: 128mb
    environment:
      POSTGRES_DB: ${HCOP_DB_NAME:-__DATABASE_NAME__}
      POSTGRES_USER: ${HCOP_DB_USER:-hcop}
      POSTGRES_PASSWORD: ${HCOP_DB_PASSWORD:?Falta HCOP_DB_PASSWORD en .env}
    volumes:
      - hcop_postgres:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 20
      start_period: 10s
    networks:
      - hcop_internal

  backend:
    image: __BACKEND_IMAGE__
    pull_policy: missing
    restart: unless-stopped
    init: true
    stop_grace_period: 45s
    depends_on:
      database:
        condition: service_healthy
    environment:
      HCOP_DB_URL: jdbc:postgresql://database:5432/${HCOP_DB_NAME:-__DATABASE_NAME__}
      HCOP_DB_USER: ${HCOP_DB_USER:-hcop}
      HCOP_DB_PASSWORD: ${HCOP_DB_PASSWORD:?Falta HCOP_DB_PASSWORD en .env}
      HCOP_BOOTSTRAP_USERNAME: ${HCOP_BOOTSTRAP_USERNAME:-marcolyto}
      HCOP_BOOTSTRAP_PASSWORD: ${HCOP_BOOTSTRAP_PASSWORD:?Falta HCOP_BOOTSTRAP_PASSWORD en .env}
      HCOP_BOOTSTRAP_SECOND_USERNAME: ${HCOP_BOOTSTRAP_SECOND_USERNAME:-marcolyto2}
      HCOP_SEED_EXAMPLE_PATIENT: ${HCOP_SEED_EXAMPLE_PATIENT:-true}
      HCOP_QR_SECRET: ${HCOP_QR_SECRET:?Falta HCOP_QR_SECRET en .env}
      HCOP_ENCRYPTION_SECRET: ${HCOP_ENCRYPTION_SECRET:?Falta HCOP_ENCRYPTION_SECRET en .env}
      HCOP_JWT_SECRET: ${HCOP_JWT_SECRET:?Falta HCOP_JWT_SECRET en .env}
      HCOP_PUBLIC_BASE_URL: ${HCOP_PUBLIC_BASE_URL:-http://localhost:__HOST_PORT__}
      HCOP_BIND_ADDRESS: 0.0.0.0
      HCOP_PORT: 5180
    volumes:
      - hcop_storage:/opt/hcop/runtime/storage
    healthcheck:
      test: ["CMD", "bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/5180 && printf 'GET /actuator/health HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
      interval: 15s
      timeout: 8s
      retries: 8
      start_period: 60s
    networks:
      - hcop_internal
      - hcop_egress

__BFF_SERVICES__
  frontend:
    image: __FRONTEND_IMAGE__
    pull_policy: missing
    restart: unless-stopped
    init: true
    depends_on:
      __FRONTEND_UPSTREAM__:
        condition: service_healthy
    ports:
      - "0.0.0.0:${HCOP_PORT:-__HOST_PORT__}:8080"
    healthcheck:
      test: ["CMD", "wget", "-q", "-O-", "http://127.0.0.1:8080/healthz"]
      interval: 10s
      timeout: 5s
      retries: 8
      start_period: 15s
    networks:
      - hcop_internal
      - hcop_egress

volumes:
  hcop_postgres:
    name: __RESOURCE_PREFIX___postgres
  hcop_storage:
    name: __RESOURCE_PREFIX___storage

networks:
  hcop_internal:
    name: __RESOURCE_PREFIX___internal
    internal: true
  hcop_egress:
    name: __RESOURCE_PREFIX___egress
'@
  $bffServices = if ($script:UsesBff) {
    @'
  redis:
    image: __REDIS_IMAGE__
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 5s
    networks:
      - hcop_internal

  bff:
    image: __BFF_IMAGE__
    pull_policy: missing
    restart: unless-stopped
    init: true
    depends_on:
      backend:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      BACKEND_URL: http://backend:5180
      REDIS_HOST: redis
      REDIS_PORT: 6379
      HCOP_BFF_PORT: 8080
      HCOP_BIND_ADDRESS: 0.0.0.0
    healthcheck:
      test: ["CMD", "bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
      interval: 15s
      timeout: 8s
      retries: 8
      start_period: 30s
    networks:
      - hcop_internal
      - hcop_egress

'@
  } else {
    ""
  }
  $document = $document.Replace("__BFF_SERVICES__", $bffServices)
  $document = $document.Replace("__FRONTEND_UPSTREAM__", $(if ($script:UsesBff) { "bff" } else { "backend" }))
  $document = $document.Replace("__PROJECT_NAME__", $script:ProjectName)
  $document = $document.Replace("__DATABASE_NAME__", $script:DatabaseName)
  $document = $document.Replace("__BACKEND_IMAGE__", $script:BackendImage)
  $document = $document.Replace("__BFF_IMAGE__", $script:BffImage)
  $document = $document.Replace("__REDIS_IMAGE__", $script:RedisImage)
  $document = $document.Replace("__FRONTEND_IMAGE__", $script:FrontendImage)
  $document = $document.Replace("__HOST_PORT__", [string]$script:DefaultHostPort)
  $document = $document.Replace("__RESOURCE_PREFIX__", $script:ResourcePrefix)
  return $document
}

function New-RandomHex([int]$ByteCount) {
  $bytes = New-Object byte[] $ByteCount
  $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
  try {
    $generator.GetBytes($bytes)
    return ([BitConverter]::ToString($bytes)).Replace("-", "").ToLowerInvariant()
  } finally {
    $generator.Dispose()
    [Array]::Clear($bytes, 0, $bytes.Length)
  }
}

function ConvertFrom-SecureStringPlain([Security.SecureString]$SecureValue) {
  $pointer = [IntPtr]::Zero
  try {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  } finally {
    if ($pointer -ne [IntPtr]::Zero) {
      [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
  }
}

function ConvertTo-EnvLiteral([string]$Value) {
  if ($Value -match "[`r`n]") {
    throw "Los valores de configuración no pueden contener saltos de línea."
  }
  $escaped = $Value.Replace("\", "\\").Replace("'", "\'")
  return "'$escaped'"
}

function ConvertFrom-EnvLiteral([string]$Literal) {
  $trimmed = $Literal.Trim()
  if ($trimmed.Length -lt 2 -or
      $trimmed[0] -ne "'" -or
      $trimmed[$trimmed.Length - 1] -ne "'") {
    return $trimmed
  }

  $inner = $trimmed.Substring(1, $trimmed.Length - 2)
  $builder = New-Object Text.StringBuilder
  for ($index = 0; $index -lt $inner.Length; $index++) {
    $character = $inner[$index]
    if ($character -eq "\" -and $index + 1 -lt $inner.Length) {
      $next = $inner[$index + 1]
      if ($next -eq "\" -or $next -eq "'") {
        $null = $builder.Append($next)
        $index++
        continue
      }
    }
    $null = $builder.Append($character)
  }
  return $builder.ToString()
}

function Read-InitialPassword([string]$Label = "Contraseña inicial") {
  while ($true) {
    $firstSecure = Read-Host "$Label (mínimo 10 caracteres)" -AsSecureString
    $secondSecure = Read-Host "Repita la contraseña" -AsSecureString
    $first = ConvertFrom-SecureStringPlain $firstSecure
    $second = ConvertFrom-SecureStringPlain $secondSecure
    if ($first.Length -lt 10) {
      Write-Warn "La contraseña debe tener al menos 10 caracteres."
      $first = $null
      $second = $null
      continue
    }
    if ($first -ne $second) {
      Write-Warn "Las contraseñas no coinciden."
      $first = $null
      $second = $null
      continue
    }
    return $first
  }
}

function Read-InitialPort {
  while ($true) {
    $portText = Read-Host "Puerto web [$($script:DefaultHostPort)]"
    if ([string]::IsNullOrWhiteSpace($portText)) {
      return [int]$script:DefaultHostPort
    }
    $portText = $portText.Trim()
    if ($portText -match "^\d{1,5}$") {
      $port = [int]$portText
      if ($port -ge 1 -and $port -le 65535) {
        return $port
      }
    }
    Write-Warn "El puerto debe ser un número entre 1 y 65535."
  }
}

function Read-InitialCredentials {
  Write-Step "Credenciales iniciales"
  Write-Info "Se solicitarán una sola vez y se guardarán localmente. No se mostrarán en pantalla ni en el registro."
  $username = Read-Host "Usuario administrador [marcolyto]"
  if ([string]::IsNullOrWhiteSpace($username)) { $username = "marcolyto" }
  if ($username -notmatch "^[A-Za-z0-9._-]{3,64}$") {
    throw "El usuario debe tener entre 3 y 64 caracteres: letras, números, punto, guion o guion bajo."
  }

  return [pscustomobject]@{
    Username = $username
    Password = (Read-InitialPassword)
  }
}

function New-InitialEnvironmentContent(
  [string]$Username,
  [string]$Password,
  [int]$Port
) {
  return (@(
    "HCOP_PORT=$Port",
    "HCOP_DB_NAME=$($script:DatabaseName)",
    "HCOP_DB_USER=hcop",
    "HCOP_DB_PASSWORD=$(ConvertTo-EnvLiteral (New-RandomHex 32))",
    "HCOP_BOOTSTRAP_USERNAME=$(ConvertTo-EnvLiteral $Username)",
    "HCOP_BOOTSTRAP_PASSWORD=$(ConvertTo-EnvLiteral $Password)",
    "HCOP_BOOTSTRAP_SECOND_USERNAME=marcolyto2",
    "HCOP_SEED_EXAMPLE_PATIENT=true",
    "HCOP_QR_SECRET=$(ConvertTo-EnvLiteral (New-RandomHex 48))",
    "HCOP_ENCRYPTION_SECRET=$(ConvertTo-EnvLiteral (New-RandomHex 48))",
    "HCOP_JWT_SECRET=$(ConvertTo-EnvLiteral (New-RandomHex 48))",
    "HCOP_PUBLIC_BASE_URL=http://localhost:$Port"
  ) -join "`r`n") + "`r`n"
}

function Get-EnvironmentValues([string]$Path) {
  $values = @{}
  foreach ($line in [IO.File]::ReadAllLines($Path)) {
    if ($line -match "^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$") {
      $values[$matches[1]] = ConvertFrom-EnvLiteral $matches[2]
    }
  }
  return $values
}

function Set-ApplicationUrlFromEnvironment([string]$Path) {
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    $script:ApplicationUrl = "http://localhost:$($script:DefaultHostPort)"
    $script:ApplicationEntryUrl = $script:ApplicationUrl
    return
  }
  $values = Get-EnvironmentValues $Path
  $port = [string]$values["HCOP_PORT"]
  if ($port -notmatch "^\d{1,5}$" -or [int]$port -lt 1 -or [int]$port -gt 65535) {
    throw "HCOP_PORT no es válido en $Path."
  }
  $script:ApplicationUrl = "http://localhost:$port"
  $script:ApplicationEntryUrl = $script:ApplicationUrl
}

function Set-EnvironmentValue(
  [string]$Path,
  [string]$Name,
  [string]$Value
) {
  $lines = New-Object Collections.Generic.List[string]
  $matched = $false
  $pattern = "^\s*$([regex]::Escape($Name))\s*="
  foreach ($line in [IO.File]::ReadAllLines($Path)) {
    if ($line -match $pattern) {
      $lines.Add("$Name=$Value")
      $matched = $true
    } else {
      $lines.Add($line)
    }
  }
  if (-not $matched) {
    $lines.Add("$Name=$Value")
  }
  Write-AtomicUtf8 $Path (($lines -join "`r`n").TrimEnd() + "`r`n")
}

function Protect-EnvironmentFile([string]$Path) {
  try {
    $currentUser = [Security.Principal.WindowsIdentity]::GetCurrent().User
    if ($null -eq $currentUser) { throw "No se pudo identificar al usuario actual." }
    $security = New-Object Security.AccessControl.FileSecurity
    $security.SetOwner($currentUser)
    $security.SetAccessRuleProtection($true, $false)
    foreach ($sidText in @(
      $currentUser.Value,
      "S-1-5-18",
      "S-1-5-32-544"
    )) {
      $sid = New-Object Security.Principal.SecurityIdentifier($sidText)
      $rule = New-Object Security.AccessControl.FileSystemAccessRule(
        $sid,
        [Security.AccessControl.FileSystemRights]::FullControl,
        [Security.AccessControl.AccessControlType]::Allow)
      $security.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $Path -AclObject $security
    Write-Ok "El archivo .env quedó protegido para el usuario actual, SYSTEM y Administradores."
  } catch {
    Write-Warn "No se pudieron restringir los permisos de .env: $($_.Exception.Message)"
  }
}

function Ensure-Environment([string]$Path, [string]$DockerPath) {
  $required = @(
    "HCOP_PORT",
    "HCOP_DB_NAME",
    "HCOP_DB_USER",
    "HCOP_DB_PASSWORD",
    "HCOP_BOOTSTRAP_USERNAME",
    "HCOP_BOOTSTRAP_PASSWORD",
    "HCOP_BOOTSTRAP_SECOND_USERNAME",
    "HCOP_QR_SECRET",
    "HCOP_ENCRYPTION_SECRET",
    "HCOP_JWT_SECRET",
    "HCOP_PUBLIC_BASE_URL")

  if (Test-Path -LiteralPath $Path -PathType Leaf) {
    $values = Get-EnvironmentValues $Path
    $missing = @($required | Where-Object { -not $values.ContainsKey($_) })
    if ($missing.Count -gt 0) {
      throw "El archivo .env existente está incompleto y no fue sobrescrito. Faltan: $($missing -join ', ')."
    }
    $bootstrapPassword = [string]$values["HCOP_BOOTSTRAP_PASSWORD"]
    if ($bootstrapPassword.Length -lt 10) {
      Write-Step "Reparar credenciales iniciales"
      Write-Warn "La contraseña guardada tiene menos de 10 caracteres y la aplicación no puede iniciar."
      $replacementPassword = Read-InitialPassword "Nueva contraseña inicial"
      try {
        Set-EnvironmentValue `
          $Path `
          "HCOP_BOOTSTRAP_PASSWORD" `
          (ConvertTo-EnvLiteral $replacementPassword)
        Write-Ok "La contraseña inicial fue actualizada sin modificar la base ni los demás secretos."
      } finally {
        $replacementPassword = $null
        $bootstrapPassword = $null
      }
    }
    Protect-EnvironmentFile $Path
    Write-Ok "Se conservaron la configuración y los secretos existentes."
    return
  }

  $existingDatabase = Invoke-NativeCapture $DockerPath @(
    "volume", "inspect", $script:PostgresVolume)
  if ($existingDatabase.ExitCode -eq 0) {
    throw @"
Existe el volumen $($script:PostgresVolume), pero falta el archivo local .env.
No se generó una contraseña nueva porque dejaría la base existente inaccesible.
Recupere $($script:DefaultDataDirectory)\.env desde su copia de seguridad o restaure juntos la base y su configuración.
"@
  }

  $selectedPort = Read-InitialPort
  $credentials = Read-InitialCredentials
  try {
    $content = New-InitialEnvironmentContent `
      $credentials.Username `
      $credentials.Password `
      $selectedPort
    Write-AtomicUtf8 $Path $content
    Protect-EnvironmentFile $Path
    Write-Ok "Configuración inicial creada. Los secretos permanecerán estables en este equipo."
  } finally {
    $credentials.Password = $null
    $credentials = $null
    $content = $null
  }
}

function Ensure-Compose([string]$Path) {
  Write-AtomicUtf8 $Path ((Get-ComposeDocument).TrimStart() + "`r`n")
  Write-Ok "Definición Docker preparada localmente."
}

function Find-Executable([string]$Name, [string[]]$Candidates) {
  $command = Get-Command $Name -ErrorAction SilentlyContinue
  if ($command) { return $command.Source }
  foreach ($candidate in $Candidates) {
    if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
    $expanded = [Environment]::ExpandEnvironmentVariables($candidate)
    if (Test-Path -LiteralPath $expanded -PathType Leaf) { return $expanded }
  }
  return $null
}

function Find-Docker {
  return Find-Executable "docker.exe" @(
    "%ProgramFiles%\Docker\Docker\resources\bin\docker.exe",
    "%LOCALAPPDATA%\Docker\resources\bin\docker.exe")
}

function Find-GitHubCli {
  return Find-Executable "gh.exe" @(
    "%ProgramFiles%\GitHub CLI\gh.exe",
    "%LOCALAPPDATA%\Programs\GitHub CLI\gh.exe")
}

function Invoke-NativeCapture(
  [string]$FilePath,
  [string[]]$Arguments
) {
  # Windows PowerShell 5.1 can promote ordinary native stderr output (for
  # example "no such volume" or "no such image") to a terminating error when
  # ErrorActionPreference is Stop. Capture both streams outside PowerShell so
  # callers can decide from ExitCode whether an absent resource is expected.
  $result = Invoke-ProcessWithInput $FilePath $Arguments $null
  $output = @(
    @($result.StandardOutput, $result.StandardError) |
      Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
      ForEach-Object { $_ -split "\r?\n" } |
      Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
  )
  return [pscustomobject]@{
    ExitCode = $result.ExitCode
    Output = $output
  }
}

function Invoke-NativeLogged(
  [string]$FilePath,
  [string[]]$Arguments,
  [string]$Description,
  [switch]$AllowFailure
) {
  Write-Info $Description
  $result = Invoke-ProcessWithInput $FilePath $Arguments $null
  $lines = New-Object Collections.Generic.List[string]
  foreach ($stream in @($result.StandardOutput, $result.StandardError)) {
    if ([string]::IsNullOrWhiteSpace($stream)) { continue }
    foreach ($line in @($stream -split "\r?\n")) {
      if ([string]::IsNullOrWhiteSpace($line)) { continue }
      $lines.Add($line)
      Write-Host "    $line"
    }
  }
  $exitCode = $result.ExitCode
  if ($exitCode -ne 0 -and -not $AllowFailure) {
    throw "$Description falló (código $exitCode)."
  }
  return [pscustomobject]@{
    ExitCode = $exitCode
    Output = $lines.ToArray()
  }
}

function ConvertTo-ProcessArgument([string]$Value) {
  if ($Value.Length -eq 0) { return '""' }
  if ($Value -notmatch '[\s"]') { return $Value }
  $escaped = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
  $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
  return '"' + $escaped + '"'
}

function Invoke-ProcessWithInput(
  [string]$FilePath,
  [string[]]$Arguments,
  [AllowNull()]
  [string]$StandardInput
) {
  $start = New-Object Diagnostics.ProcessStartInfo
  $start.FileName = $FilePath
  $start.Arguments = (($Arguments | ForEach-Object { ConvertTo-ProcessArgument $_ }) -join " ")
  $start.UseShellExecute = $false
  $start.CreateNoWindow = $true
  $start.RedirectStandardInput = $true
  $start.RedirectStandardOutput = $true
  $start.RedirectStandardError = $true
  $process = New-Object Diagnostics.Process
  $process.StartInfo = $start
  try {
    if (-not $process.Start()) { throw "No se pudo iniciar $FilePath." }
    if ($null -ne $StandardInput) {
      $process.StandardInput.WriteLine($StandardInput)
    }
    $process.StandardInput.Close()
    # Read both redirected streams concurrently. Docker Compose can write most
    # pull/build progress to stderr even on success; sequential reads can fill
    # one pipe and block the child process.
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $stdout = $stdoutTask.Result
    $stderr = $stderrTask.Result
    return [pscustomobject]@{
      ExitCode = $process.ExitCode
      StandardOutput = $stdout
      StandardError = $stderr
      Output = (@($stdout, $stderr) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join "`r`n"
    }
  } finally {
    $process.Dispose()
  }
}

function Assert-DockerReady {
  $docker = Find-Docker
  if (-not $docker) {
    throw "No se encontró Docker. Instale Docker Desktop, ábralo y vuelva a ejecutar este archivo."
  }

  $engine = Invoke-NativeCapture $docker @("version", "--format", "{{.Server.Version}}")
  if ($engine.ExitCode -ne 0) {
    throw "Docker Desktop está instalado, pero su motor no responde. Abra Docker Desktop, espere a que indique que está listo y vuelva a intentar."
  }

  $compose = Invoke-NativeCapture $docker @("compose", "version", "--short")
  if ($compose.ExitCode -ne 0) {
    throw "Docker Compose no está disponible. Actualice Docker Desktop."
  }
  $versionText = ($compose.Output -join " ").Trim()
  if ($versionText -notmatch "(\d+\.\d+\.\d+)") {
    throw "No se pudo interpretar la versión de Docker Compose: $versionText"
  }
  $version = [version]$matches[1]
  if ($version -lt [version]"2.20.0") {
    throw "Docker Compose $version es antiguo. Se requiere 2.20 o posterior; actualice Docker Desktop."
  }
  Write-Ok "Docker Engine y Docker Compose $version están listos."
  return $docker
}

function Get-ComposeArguments(
  [string]$Root,
  [string]$ComposePath,
  [string]$EnvironmentPath,
  [string[]]$Command
) {
  return @(
    "compose",
    "--project-directory", $Root,
    "--env-file", $EnvironmentPath,
    "-f", $ComposePath
  ) + $Command
}

function Test-AuthenticationFailure([string[]]$Output) {
  $message = $Output -join "`n"
  return $message -match "(?i)(unauthorized|authentication required|pull access denied|denied:|forbidden|status code 401|status code 403|\b401\b|\b403\b|requested access to the resource is denied)"
}

function Connect-Ghcr([string]$DockerPath) {
  $gh = Find-GitHubCli
  if (-not $gh) {
    throw @"
La imagen de HCOP JP requiere autenticación y GitHub CLI no está instalado.

Opción recomendada:
  1. Instale GitHub CLI desde https://cli.github.com/
  2. Ejecute este archivo nuevamente; se abrirá la autorización de GitHub.

Opción con PAT:
  1. Cree en GitHub un token clásico con permiso read:packages.
  2. Ejecute: docker login ghcr.io -u SU_USUARIO
  3. Cuando Docker solicite Password, pegue el PAT. No lo escriba en el comando.
"@
  }

  Write-Step "Autorización de la imagen privada"
  $status = Invoke-NativeCapture $gh @("auth", "status", "--hostname", "github.com")
  if ($status.ExitCode -ne 0) {
    $login = Invoke-NativeLogged $gh @(
      "auth", "login",
      "--hostname", "github.com",
      "--git-protocol", "https",
      "--web",
      "--scopes", "read:packages"
    ) "Autorizando GitHub por navegador" -AllowFailure
    if ($login.ExitCode -ne 0) {
      throw "No se pudo iniciar sesión en GitHub. Complete la autorización y vuelva a intentar."
    }
  } else {
    $refresh = Invoke-NativeLogged $gh @(
      "auth", "refresh",
      "--hostname", "github.com",
      "--scopes", "read:packages"
    ) "Verificando permiso read:packages" -AllowFailure
    if ($refresh.ExitCode -ne 0) {
      throw "GitHub no concedió read:packages. Revise la autorización de la cuenta y vuelva a intentar."
    }
  }

  # Estas dos llamadas se capturan mediante Process, fuera de los streams de
  # PowerShell, para que el token no pueda terminar en el transcript.
  $userResult = Invoke-ProcessWithInput $gh @("api", "user", "--jq", ".login") $null
  $tokenResult = Invoke-ProcessWithInput $gh @("auth", "token", "--hostname", "github.com") $null
  $username = $userResult.StandardOutput.Trim()
  $token = $tokenResult.StandardOutput.Trim()
  if ($userResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($username) -or
      $tokenResult.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($token)) {
    $token = $null
    throw "GitHub CLI no pudo entregar una credencial válida para GHCR."
  }

  try {
    Write-Info "Autenticando Docker en ghcr.io sin mostrar la credencial..."
    $loginResult = Invoke-ProcessWithInput $DockerPath @(
      "login", "ghcr.io", "--username", $username, "--password-stdin"
    ) $token
    if ($loginResult.ExitCode -ne 0) {
      throw "Docker no pudo iniciar sesión en ghcr.io. $($loginResult.Output)"
    }
    Write-Ok "Acceso a GHCR autorizado."
  } finally {
    $token = $null
  }
}

function Pull-Images(
  [string]$DockerPath,
  [string]$Root,
  [string]$ComposePath,
  [string]$EnvironmentPath
) {
  Write-Step "Descargando las imágenes publicadas"
  Write-Info "La primera descarga puede tardar varios minutos. Espere hasta que Docker termine; el detalle aparecerá al completar cada intento."
  $arguments = Get-ComposeArguments $Root $ComposePath $EnvironmentPath @("pull")
  $imageList = if ($script:UsesBff) {
    "$($script:BackendImage), $($script:BffImage), $($script:FrontendImage), $($script:RedisImage) y $($script:PostgresImage)"
  } else {
    "$($script:BackendImage), $($script:FrontendImage) y $($script:PostgresImage)"
  }
  $pull = Invoke-NativeLogged $DockerPath $arguments `
    "Descargando $imageList" -AllowFailure
  if ($pull.ExitCode -eq 0) {
    Write-Ok "Imágenes disponibles."
    return
  }
  if (-not (Test-AuthenticationFailure $pull.Output)) {
    throw "No se pudieron descargar las imágenes. Revise Internet y el registro indicado arriba."
  }

  Connect-Ghcr $DockerPath
  $retry = Invoke-NativeLogged $DockerPath $arguments `
    "Reintentando la descarga después de autenticar GitHub" -AllowFailure
  if ($retry.ExitCode -ne 0) {
    throw "La autenticación terminó, pero GHCR rechazó la imagen. Confirme que la cuenta tenga acceso al paquete HCOP_JP."
  }
  Write-Ok "Imágenes disponibles."
}

function Test-ImageAvailable([string]$DockerPath, [string]$Image) {
  $inspection = Invoke-NativeCapture $DockerPath @(
    "image", "inspect", "--format", "{{.Id}}", $Image)
  return ($inspection.ExitCode -eq 0)
}

function Ensure-Images(
  [string]$DockerPath,
  [string]$Root,
  [string]$ComposePath,
  [string]$EnvironmentPath,
  [switch]$ForcePull
) {
  if ($ForcePull) {
    Pull-Images $DockerPath $Root $ComposePath $EnvironmentPath
    return
  }
  $backendAvailable = Test-ImageAvailable $DockerPath $script:BackendImage
  $frontendAvailable = Test-ImageAvailable $DockerPath $script:FrontendImage
  $databaseAvailable = Test-ImageAvailable $DockerPath $script:PostgresImage
  $bffAvailable = -not $script:UsesBff -or (Test-ImageAvailable $DockerPath $script:BffImage)
  $redisAvailable = -not $script:UsesBff -or (Test-ImageAvailable $DockerPath $script:RedisImage)
  if ($backendAvailable -and $bffAvailable -and $frontendAvailable -and $databaseAvailable -and $redisAvailable) {
    Write-Ok "Se usarán las imágenes locales. El inicio diario no necesita Internet."
    return
  }
  Write-Info "Falta al menos una imagen local; se descargarán las imágenes publicadas."
  Pull-Images $DockerPath $Root $ComposePath $EnvironmentPath
}

function Get-HttpResponse([string]$Path, [int]$Attempts = 1) {
  for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
    try {
      return Invoke-WebRequest `
        -Uri "$($script:ApplicationUrl)$Path" `
        -UseBasicParsing `
        -TimeoutSec 8
    } catch {}
    if ($attempt -lt $Attempts) { Start-Sleep -Seconds 3 }
  }
  return $null
}

function Test-ApplicationHealth([int]$Attempts = 12) {
  $response = Get-HttpResponse "/actuator/health" $Attempts
  $content = if ($null -eq $response) {
    ""
  } elseif ($response.Content -is [byte[]]) {
    [System.Text.Encoding]::UTF8.GetString([byte[]]$response.Content)
  } else {
    [string]$response.Content
  }
  return (
    $null -ne $response -and
    $response.StatusCode -eq 200 -and
    $content -match '"status"\s*:\s*"UP"')
}

function Assert-ApplicationSmoke {
  $homeResponse = Get-HttpResponse "/"
  if ($null -eq $homeResponse -or $homeResponse.StatusCode -ne 200 -or
      [string]$homeResponse.Content -notmatch "<app-root") {
    throw "La página principal no entregó el frontend Angular esperado."
  }

  $clinicalResponse = Get-HttpResponse "/api/clinical/status"
  if ($null -eq $clinicalResponse -or $clinicalResponse.StatusCode -ne 200) {
    throw "El control clínico y de PostgreSQL no respondió."
  }
  try { $clinical = $clinicalResponse.Content | ConvertFrom-Json } catch {
    throw "El control clínico devolvió una respuesta inválida."
  }
  if ($clinical.ok -ne $true -or $clinical.engine -ne "java-postgresql" -or
      $clinical.database -ne "postgresql") {
    throw "El control clínico no confirmó Java y PostgreSQL."
  }

  $runtimeResponse = Get-HttpResponse "/api/runtime/status"
  if ($null -eq $runtimeResponse -or $runtimeResponse.StatusCode -ne 200) {
    throw "El control del motor de HCOP JP no respondió."
  }
  try { $runtime = $runtimeResponse.Content | ConvertFrom-Json } catch {
    throw "El control del motor devolvió una respuesta inválida."
  }
  if ($runtime.ok -ne $true -or $runtime.running -ne $true -or
      $runtime.engine -ne "java-postgresql") {
    throw "El control del motor no confirmó que HCOP JP esté operativo."
  }
}

function Start-Hcop(
  [string]$DockerPath,
  [string]$Root,
  [string]$ComposePath,
  [string]$EnvironmentPath,
  [switch]$ForcePull
) {
  Set-ApplicationUrlFromEnvironment $EnvironmentPath
  $validation = Invoke-NativeLogged $DockerPath `
    (Get-ComposeArguments $Root $ComposePath $EnvironmentPath @("config", "--quiet")) `
    "Validando la definición Docker" -AllowFailure
  if ($validation.ExitCode -ne 0) {
    throw "La configuración Docker local no es válida."
  }

  Ensure-Images $DockerPath $Root $ComposePath $EnvironmentPath -ForcePull:$ForcePull

  Write-Step "Iniciando HCOP JP"
  $up = Invoke-NativeLogged $DockerPath `
    (Get-ComposeArguments $Root $ComposePath $EnvironmentPath @(
      "up", "--detach", "--wait", "--wait-timeout", "360", "--remove-orphans"
    )) `
    "Iniciando PostgreSQL y HCOP JP" -AllowFailure
  if ($up.ExitCode -ne 0) {
    $null = Invoke-NativeLogged $DockerPath `
      (Get-ComposeArguments $Root $ComposePath $EnvironmentPath @("ps")) `
      "Estado de los contenedores" -AllowFailure
    $null = Invoke-NativeLogged $DockerPath `
      (Get-ComposeArguments $Root $ComposePath $EnvironmentPath @(
        "logs", "--no-color", "--tail", "150"
      )) `
      "Últimos mensajes de los contenedores" -AllowFailure
    throw "HCOP JP no alcanzó un estado saludable."
  }

  if (-not (Test-ApplicationHealth)) {
    throw "Docker informó que inició, pero $($script:ApplicationUrl)/actuator/health no respondió UP."
  }
  Assert-ApplicationSmoke
  Write-Ok "HCOP JP está listo en $($script:ApplicationEntryUrl)."
  if (-not $NoOpenBrowser) {
    Start-Process $script:ApplicationEntryUrl
  }
}

function Stop-Hcop(
  [string]$DockerPath,
  [string]$Root,
  [string]$ComposePath,
  [string]$EnvironmentPath
) {
  if (-not (Test-Path -LiteralPath $EnvironmentPath -PathType Leaf)) {
    Write-Warn "Todavía no existe una instalación de HCOP JP en $Root."
    return
  }
  Ensure-Compose $ComposePath
  Write-Step "Deteniendo HCOP JP"
  $null = Invoke-NativeLogged $DockerPath `
    (Get-ComposeArguments $Root $ComposePath $EnvironmentPath @("stop")) `
    "Deteniendo los contenedores sin borrar datos"
  Write-Ok "HCOP JP fue detenido. La base y los archivos se conservaron."
}

function Show-HcopStatus(
  [string]$DockerPath,
  [string]$Root,
  [string]$ComposePath,
  [string]$EnvironmentPath
) {
  if (-not (Test-Path -LiteralPath $EnvironmentPath -PathType Leaf)) {
    Write-Warn "Todavía no existe una instalación de HCOP JP en $Root."
    return
  }
  Set-ApplicationUrlFromEnvironment $EnvironmentPath
  Ensure-Compose $ComposePath
  Write-Step "Estado de HCOP JP"
  $null = Invoke-NativeLogged $DockerPath `
    (Get-ComposeArguments $Root $ComposePath $EnvironmentPath @("ps")) `
    "Contenedores"
  if (Test-ApplicationHealth 1) {
    Write-Ok "Aplicación saludable: $($script:ApplicationUrl)"
  } else {
    Write-Warn "La aplicación no responde como saludable en $($script:ApplicationUrl)."
  }
  Write-Info "Datos persistentes: volúmenes $($script:PostgresVolume) y $($script:StorageVolume)."
}

function Invoke-ValidateOnly {
  $tokens = $null
  $errors = $null
  [Management.Automation.Language.Parser]::ParseFile(
    $PSCommandPath,
    [ref]$tokens,
    [ref]$errors) | Out-Null
  $compose = Get-ComposeDocument
  $requiredFragments = @(
    $script:BackendImage,
    $script:FrontendImage,
    "postgres:18.4-alpine",
    $script:PostgresVolume,
    $script:StorageVolume,
    "/actuator/health")
  if ($script:UsesBff) {
    $requiredFragments += @($script:BffImage, $script:RedisImage)
  }
  $missing = @($requiredFragments | Where-Object { -not $compose.Contains($_) })
  $result = [ordered]@{
    ok = ($errors.Count -eq 0 -and $missing.Count -eq 0)
    mode = "ValidateOnly"
    channel = $Channel
    backendImage = $script:BackendImage
    bffImage = $(if ($script:UsesBff) { $script:BffImage } else { $null })
    frontendImage = $script:FrontendImage
    applicationEntryUrl = $script:ApplicationEntryUrl
    projectName = $script:ProjectName
    databaseName = $script:DatabaseName
    dataDirectory = $script:DefaultDataDirectory
    defaultPort = $script:DefaultHostPort
    postgresVolume = $script:PostgresVolume
    storageVolume = $script:StorageVolume
    powershell = $PSVersionTable.PSVersion.ToString()
    parserErrors = @($errors | ForEach-Object { $_.Message })
    missingComposeFragments = $missing
    dockerRequired = $false
    networkRequired = $false
  }
  $result | ConvertTo-Json -Depth 5
  if (-not $result.ok) {
    throw "La validación estática no fue satisfactoria."
  }
}

if ($Mode -eq "ValidateOnly") {
  try {
    Invoke-ValidateOnly
    exit 0
  } catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
  }
}

if ([string]::IsNullOrWhiteSpace($DataDirectory)) {
  $DataDirectory = $script:DefaultDataDirectory
}
$root = Resolve-DataDirectory $DataDirectory
$composePath = Join-Path $root "compose.yaml"
$environmentPath = Join-Path $root ".env"

try {
  New-Item -ItemType Directory -Path $root -Force | Out-Null
  Start-OperationLog $root
  Enter-OperationLock $root
  Write-Step "HCOP JP desde GitHub Container Registry · $Channel · $Mode"
  Write-Info "Carpeta local: $root"
  Write-Info "Imagenes: $($script:BackendImage)$(if ($script:UsesBff) { " · $($script:BffImage)" }) · $($script:FrontendImage)"

  $docker = Assert-DockerReady
  switch ($Mode) {
    "Start" {
      Ensure-Compose $composePath
      Ensure-Environment $environmentPath $docker
      Start-Hcop $docker $root $composePath $environmentPath
    }
    "Update" {
      Ensure-Compose $composePath
      Ensure-Environment $environmentPath $docker
      Start-Hcop $docker $root $composePath $environmentPath -ForcePull
      Write-Ok "Las imagenes $($script:BackendImage)$(if ($script:UsesBff) { ", $($script:BffImage)" }) y $($script:FrontendImage) fueron actualizadas y aplicadas."
    }
    "Stop" {
      Stop-Hcop $docker $root $composePath $environmentPath
    }
    "Status" {
      Show-HcopStatus $docker $root $composePath $environmentPath
    }
  }
  $script:ExitCode = 0
} catch {
  Write-Host ""
  Write-Host "HCOP JP no pudo completar '$Mode'." -ForegroundColor Red
  Write-Host $_.Exception.Message -ForegroundColor Red
  if ($script:LogPath) {
    Write-Host "Detalle: $script:LogPath" -ForegroundColor Yellow
  }
} finally {
  Exit-OperationLock
  Stop-OperationLog
}

exit $script:ExitCode
