@echo off
setlocal EnableExtensions
chcp 65001 >nul
set "SCRIPT_DIR=%~dp0"

if not "%~1"=="" goto execute

net session >nul 2>&1
if not "%ERRORLEVEL%"=="0" (
  echo 正在请求管理员权限...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

:menu
cls
echo ========================================
echo        MRR Nginx 网关控制中心
echo ========================================
echo  1. 查看 Nginx 状态
echo  2. 启动 Nginx
echo  3. 停止 Nginx
echo  4. 重启 Nginx
echo  5. 平滑重载配置
echo  6. 检查 Nginx 配置
echo  7. 暂停访问（维护模式）
echo  8. 恢复访问
echo  0. 退出
echo ========================================
set /p "choice=请选择操作 [0-8]："

if "%choice%"=="1" call :run status
if "%choice%"=="2" call :run start
if "%choice%"=="3" call :run stop
if "%choice%"=="4" call :run restart
if "%choice%"=="5" call :run reload
if "%choice%"=="6" call :run test
if "%choice%"=="7" call :run pause
if "%choice%"=="8" call :run resume
if "%choice%"=="0" exit /b 0

echo.
pause
goto menu

:run
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%nginxctl.ps1" %1
exit /b %ERRORLEVEL%

:execute
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%nginxctl.ps1" %*
exit /b %ERRORLEVEL%
