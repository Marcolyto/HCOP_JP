param(
  [ValidateSet("Start", "Update", "Stop", "Status", "ValidateOnly")]
  [string]$Mode = "Start",
  [string]$DataDirectory = (Join-Path $env:LOCALAPPDATA "HCOP_JP-Docker"),
  [switch]$NoOpenBrowser
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$script:ApplicationImage = "ghcr.io/marcolyto/hcop_jp:latest"
$script:PostgresImage = "postgres:18.4-alpine"
$script:ApplicationUrl = "http://localhost:5180"
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
  return @'
name: hcop-jp

services:
  database:
    image: postgres:18.4-alpine
    restart: unless-stopped
    shm_size: 128mb
    environment:
      POSTGRES_DB: ${HCOP_DB_NAME:-hcop_jp}
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

  application:
    image: ghcr.io/marcolyto/hcop_jp:latest
    pull_policy: missing
    restart: unless-stopped
    init: true
    stop_grace_period: 45s
    depends_on:
      database:
        condition: service_healthy
    environment:
      HCOP_DB_URL: jdbc:postgresql://database:5432/${HCOP_DB_NAME:-hcop_jp}
      HCOP_DB_USER: ${HCOP_DB_USER:-hcop}
      HCOP_DB_PASSWORD: ${HCOP_DB_PASSWORD:?Falta HCOP_DB_PASSWORD en .env}
      HCOP_BOOTSTRAP_USERNAME: ${HCOP_BOOTSTRAP_USERNAME:-marcolyto}
      HCOP_BOOTSTRAP_PASSWORD: ${HCOP_BOOTSTRAP_PASSWORD:?Falta HCOP_BOOTSTRAP_PASSWORD en .env}
      HCOP_BOOTSTRAP_SECOND_USERNAME: ${HCOP_BOOTSTRAP_SECOND_USERNAME:-marcolyto2}
      HCOP_QR_SECRET: ${HCOP_QR_SECRET:?Falta HCOP_QR_SECRET en .env}
      HCOP_ENCRYPTION_SECRET: ${HCOP_ENCRYPTION_SECRET:?Falta HCOP_ENCRYPTION_SECRET en .env}
      HCOP_PUBLIC_BASE_URL: ${HCOP_PUBLIC_BASE_URL:-http://localhost:5180}
      HCOP_BIND_ADDRESS: 0.0.0.0
      HCOP_PORT: 5180
    ports:
      - "0.0.0.0:${HCOP_PORT:-5180}:5180"
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

volumes:
  hcop_postgres:
    name: hcop_jp_postgres
  hcop_storage:
    name: hcop_jp_storage

networks:
  hcop_internal:
    name: hcop_jp_internal
    internal: true
  hcop_egress:
    name: hcop_jp_egress
'@
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

function Read-InitialCredentials {
  Write-Step "Credenciales iniciales"
  Write-Info "Se solicitarán una sola vez y se guardarán localmente. No se mostrarán en pantalla ni en el registro."
  $username = Read-Host "Usuario administrador [marcolyto]"
  if ([string]::IsNullOrWhiteSpace($username)) { $username = "marcolyto" }
  if ($username -notmatch "^[A-Za-z0-9._-]{3,64}$") {
    throw "El usuario debe tener entre 3 y 64 caracteres: letras, números, punto, guion o guion bajo."
  }

  while ($true) {
    $firstSecure = Read-Host "Contraseña inicial (mínimo 8 caracteres)" -AsSecureString
    $secondSecure = Read-Host "Repita la contraseña" -AsSecureString
    $first = ConvertFrom-SecureStringPlain $firstSecure
    $second = ConvertFrom-SecureStringPlain $secondSecure
    if ($first.Length -lt 8) {
      Write-Warn "La contraseña debe tener al menos 8 caracteres."
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
    return [pscustomobject]@{
      Username = $username
      Password = $first
    }
  }
}

function Get-EnvironmentKeys([string]$Path) {
  $keys = @{}
  foreach ($line in [IO.File]::ReadAllLines($Path)) {
    if ($line -match "^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=") {
      $keys[$matches[1]] = $true
    }
  }
  return $keys
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
    "HCOP_PUBLIC_BASE_URL")

  if (Test-Path -LiteralPath $Path -PathType Leaf) {
    $keys = Get-EnvironmentKeys $Path
    $missing = @($required | Where-Object { -not $keys.ContainsKey($_) })
    if ($missing.Count -gt 0) {
      throw "El archivo .env existente está incompleto y no fue sobrescrito. Faltan: $($missing -join ', ')."
    }
    Protect-EnvironmentFile $Path
    Write-Ok "Se conservaron la configuración y los secretos existentes."
    return
  }

  $existingDatabase = Invoke-NativeCapture $DockerPath @(
    "volume", "inspect", "hcop_jp_postgres")
  if ($existingDatabase.ExitCode -eq 0) {
    throw @"
Existe el volumen hcop_jp_postgres, pero falta el archivo local .env.
No se generó una contraseña nueva porque dejaría la base existente inaccesible.
Recupere %LOCALAPPDATA%\HCOP_JP-Docker\.env desde su copia de seguridad o restaure juntos la base y su configuración.
"@
  }

  $credentials = Read-InitialCredentials
  try {
    $content = (@(
      "HCOP_PORT=5180",
      "HCOP_DB_NAME=hcop_jp",
      "HCOP_DB_USER=hcop",
      "HCOP_DB_PASSWORD=$(ConvertTo-EnvLiteral (New-RandomHex 32))",
      "HCOP_BOOTSTRAP_USERNAME=$(ConvertTo-EnvLiteral $credentials.Username)",
      "HCOP_BOOTSTRAP_PASSWORD=$(ConvertTo-EnvLiteral $credentials.Password)",
      "HCOP_BOOTSTRAP_SECOND_USERNAME=marcolyto2",
      "HCOP_QR_SECRET=$(ConvertTo-EnvLiteral (New-RandomHex 48))",
      "HCOP_ENCRYPTION_SECRET=$(ConvertTo-EnvLiteral (New-RandomHex 48))",
      "HCOP_PUBLIC_BASE_URL=http://localhost:5180"
    ) -join "`r`n") + "`r`n"
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
  $pull = Invoke-NativeLogged $DockerPath $arguments `
    "Descargando $($script:ApplicationImage) y $($script:PostgresImage)" -AllowFailure
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
  $applicationAvailable = Test-ImageAvailable $DockerPath $script:ApplicationImage
  $databaseAvailable = Test-ImageAvailable $DockerPath $script:PostgresImage
  if ($applicationAvailable -and $databaseAvailable) {
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
  return (
    $null -ne $response -and
    $response.StatusCode -eq 200 -and
    $response.Content -match '"status"\s*:\s*"UP"')
}

function Assert-ApplicationSmoke {
  $home = Get-HttpResponse "/"
  if ($null -eq $home -or $home.StatusCode -ne 200 -or [string]::IsNullOrWhiteSpace($home.Content)) {
    throw "La página principal no respondió correctamente."
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
      "up", "--detach", "--wait", "--wait-timeout", "360"
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
  Write-Ok "HCOP JP está listo en $($script:ApplicationUrl)."
  if (-not $NoOpenBrowser) {
    Start-Process $script:ApplicationUrl
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
  Write-Info "Datos persistentes: volúmenes hcop_jp_postgres y hcop_jp_storage."
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
    "ghcr.io/marcolyto/hcop_jp:latest",
    "postgres:18.4-alpine",
    "hcop_jp_postgres",
    "hcop_jp_storage",
    "/actuator/health")
  $missing = @($requiredFragments | Where-Object { -not $compose.Contains($_) })
  $result = [ordered]@{
    ok = ($errors.Count -eq 0 -and $missing.Count -eq 0)
    mode = "ValidateOnly"
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

$root = Resolve-DataDirectory $DataDirectory
$composePath = Join-Path $root "compose.yaml"
$environmentPath = Join-Path $root ".env"

try {
  New-Item -ItemType Directory -Path $root -Force | Out-Null
  Start-OperationLog $root
  Enter-OperationLock $root
  Write-Step "HCOP JP desde GitHub Container Registry · $Mode"
  Write-Info "Carpeta local: $root"

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
      Write-Ok "La imagen latest fue actualizada y aplicada."
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
