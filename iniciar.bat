@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "HCOP_MODE=SourceStart"
set "HCOP_ARG=%~1"
if /i "%HCOP_ARG%"=="detener" set "HCOP_MODE=SourceStop"
if /i "%HCOP_ARG%"=="stop" set "HCOP_MODE=SourceStop"
if /i "%HCOP_ARG%"=="reiniciar" set "HCOP_MODE=SourceRestart"
if /i "%HCOP_ARG%"=="restart" set "HCOP_MODE=SourceRestart"

set "HCOP_NOBROWSER="
if "%HCOP_MODE%"=="SourceStop" set "HCOP_NOBROWSER=-NoOpenBrowser"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\instalar-desde-github.ps1" -Mode %HCOP_MODE% -InstallDir "%~dp0" %HCOP_NOBROWSER%
set "HCOP_RESULT=%ERRORLEVEL%"

if "%HCOP_RESULT%"=="3010" (
  echo.
  echo Reinicie Windows y vuelva a ejecutar iniciar.bat.
  pause
) else if not "%HCOP_RESULT%"=="0" (
  echo.
  echo HCOP JP no pudo completar la operacion. Revise el registro indicado arriba.
  pause
)
exit /b %HCOP_RESULT%
