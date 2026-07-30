@echo off
setlocal EnableExtensions
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\instalar-desde-github.ps1" -Mode SourceStart -InstallDir "%~dp0"
set "HCOP_RESULT=%ERRORLEVEL%"
if "%HCOP_RESULT%"=="3010" (
  echo.
  echo Reinicie Windows y vuelva a ejecutar iniciar.bat.
  pause
) else if not "%HCOP_RESULT%"=="0" (
  echo.
  echo No se pudo iniciar HCOP JP. Revise el registro indicado arriba.
  pause
)
exit /b %HCOP_RESULT%
