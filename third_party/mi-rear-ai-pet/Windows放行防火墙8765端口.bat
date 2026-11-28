@echo off
chcp 65001 >nul 2>nul
setlocal
title 小米妙享背屏 AI 桌宠 · 放行防火墙端口

echo 正在为「背屏 AI 桌宠」放行 Windows 防火墙端口 (TCP 8765 / UDP 8766)...
net session >nul 2>&1
if %errorlevel% neq 0 goto :elevate

netsh advfirewall firewall delete rule name="MiRearPet HTTP 8765" >nul 2>&1
netsh advfirewall firewall delete rule name="MiRearPet Discovery UDP 8766" >nul 2>&1
netsh advfirewall firewall add rule name="MiRearPet HTTP 8765" dir=in action=allow protocol=TCP localport=8765
if %errorlevel% neq 0 goto :netsh_failed
netsh advfirewall firewall add rule name="MiRearPet Discovery UDP 8766" dir=in action=allow protocol=UDP localport=8766
if %errorlevel% neq 0 goto :netsh_failed

echo.
echo [完成] 已放行端口 TCP 8765 与 UDP 8766，手机同 Wi-Fi 下现在可以正常连接电脑！
pause
exit /b 0

:elevate
echo 正在请求管理员权限...
set "MI_BAT_PATH=%~f0"
powershell -NoProfile -Command "Start-Process -FilePath $env:MI_BAT_PATH -Verb RunAs"
if %errorlevel% neq 0 goto :elevate_failed
exit /b 0

:elevate_failed
echo.
echo [错误] 无法自动请求管理员权限（可能点了取消，或系统限制了 PowerShell）。
echo 请右键「Windows放行防火墙8765端口.bat」，手动选择「以管理员身份运行」。
echo.
pause
exit /b 1

:netsh_failed
echo.
echo [错误] 添加防火墙规则失败，请查看上方报错信息。
echo.
pause
exit /b 1
