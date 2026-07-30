@echo off
setlocal EnableExtensions
set "HCOP_INSTALLER=%TEMP%\hcop-jp-instalar-%RANDOM%.ps1"
set "HCOP_BOOTSTRAP_LOG=%TEMP%\hcop-jp-bootstrap.log"
echo HCOP JP bootstrap %DATE% %TIME%>"%HCOP_BOOTSTRAP_LOG%"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/Marcolyto/HCOP_JP/main/scripts/instalar-desde-github.ps1' -OutFile '%HCOP_INSTALLER%'" >>"%HCOP_BOOTSTRAP_LOG%" 2>&1
if errorlevel 1 (
  echo El repositorio requiere ingreso a GitHub. Preparando acceso seguro...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $gh=(Get-Command gh.exe -ErrorAction SilentlyContinue).Source; if(-not $gh){ $winget=(Get-Command winget.exe -ErrorAction SilentlyContinue); if(-not $winget){ throw 'Se necesita GitHub CLI o winget.' }; & $winget install --exact --id GitHub.cli --accept-source-agreements --accept-package-agreements; if($LASTEXITCODE -ne 0){ throw 'No se pudo instalar GitHub CLI.' }; $env:Path=[Environment]::GetEnvironmentVariable('Path','Machine')+';'+[Environment]::GetEnvironmentVariable('Path','User'); $candidate=Join-Path $env:ProgramFiles 'GitHub CLI\gh.exe'; if(Test-Path -LiteralPath $candidate){$gh=$candidate}else{$gh=(Get-Command gh.exe -ErrorAction SilentlyContinue).Source} }; if(-not $gh){throw 'GitHub CLI no esta disponible.'}; & $gh auth status --hostname github.com; if($LASTEXITCODE -ne 0){& $gh auth login --hostname github.com --git-protocol https --web}; if($LASTEXITCODE -ne 0){throw 'No se pudo iniciar sesion en GitHub.'}; $token=(& $gh auth token).Trim(); $headers=@{Authorization=('Bearer '+$token);Accept='application/vnd.github+json';'X-GitHub-Api-Version'='2022-11-28';'User-Agent'='HCOP-JP-Bootstrap'}; $item=Invoke-RestMethod -UseBasicParsing -Headers $headers -Uri 'https://api.github.com/repos/Marcolyto/HCOP_JP/contents/scripts/instalar-desde-github.ps1?ref=main'; [IO.File]::WriteAllBytes('%HCOP_INSTALLER%',[Convert]::FromBase64String(($item.content -replace '\s','')))"
  if errorlevel 1 (
    echo No se pudo descargar el instalador privado desde GitHub.
    echo Diagnostico: %HCOP_BOOTSTRAP_LOG%
    pause
    exit /b 1
  )
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%HCOP_INSTALLER%" -Mode Install
set "HCOP_RESULT=%ERRORLEVEL%"
del "%HCOP_INSTALLER%" >nul 2>nul
if "%HCOP_RESULT%"=="3010" (
  echo.
  echo Reinicie Windows y ejecute nuevamente este mismo archivo.
  pause
) else if not "%HCOP_RESULT%"=="0" (
  echo.
  echo El arranque inicial fallo. Registro de descarga: %HCOP_BOOTSTRAP_LOG%
  pause
)
exit /b %HCOP_RESULT%
