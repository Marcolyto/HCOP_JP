@echo off
setlocal
chcp 65001 >nul
set "HCOP_MODE=%~1"
if "%HCOP_MODE%"=="" set "HCOP_MODE=Start"

powershell.exe -NoProfile -ExecutionPolicy Bypass ^
  -File "%~dp0EJECUTAR-DOCKER-DESDE-GITHUB.ps1" ^
  -Mode "%HCOP_MODE%"
set "HCOP_EXIT=%ERRORLEVEL%"

if not "%HCOP_EXIT%"=="0" (
  echo.
  echo HCOP JP no pudo completar la operacion.
  pause
)
exit /b %HCOP_EXIT%
