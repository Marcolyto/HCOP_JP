@echo off
setlocal EnableExtensions
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\instalar-desde-github.ps1" -Mode SourceStop -InstallDir "%~dp0" -NoOpenBrowser
set "HCOP_RESULT=%ERRORLEVEL%"
if not "%HCOP_RESULT%"=="0" (
  echo.
  echo No se pudo detener HCOP JP. Revise el registro indicado arriba.
  pause
)
exit /b %HCOP_RESULT%
