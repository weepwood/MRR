@echo off
chcp 65001 >nul
set "MRR_ROOT=%~dp0"
if exist "C:\MRR\ops\mrr-manager.ps1" set "MRR_ROOT=C:\MRR"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%MRR_ROOT%\ops\mrr-manager.ps1" -Root "%MRR_ROOT%"
if errorlevel 1 pause
