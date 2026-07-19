@echo off
chcp 65001 >nul
set "SCRIPT=%~dp0install.ps1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell.exe -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File ""%SCRIPT%"" -Root ""C:\MRR""'"
if errorlevel 1 pause
