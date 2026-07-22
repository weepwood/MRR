@echo off
setlocal EnableExtensions
chcp 65001 >nul
set "SCRIPT_DIR=%~dp0"

if /I "%~1"=="-SelfTest" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File "%SCRIPT_DIR%mrr-manager.ps1" %*
) else (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File "%SCRIPT_DIR%mrr-manager.ps1" %*
)
exit /b %ERRORLEVEL%
