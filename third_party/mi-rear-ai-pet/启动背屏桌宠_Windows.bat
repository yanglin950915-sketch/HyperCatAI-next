@echo off
chcp 65001 >nul 2>nul
setlocal
title 小米妙享背屏 AI 桌宠 · Windows 控制台

pushd "%~dp0" >nul 2>&1
if %errorlevel% neq 0 goto :pushd_failed

echo ============================================================
echo   小米妙享背屏 AI 桌宠 · Windows 一键启动
echo ============================================================
echo.

if not exist "server.py" goto :missing_server

set "PY_EXE="
set "PY_ARG="

echo 正在检测本机 Python 3 环境...

call :try_python "py" "-3"
call :try_python "python"
call :try_python "python3"

if defined PY_EXE goto :found_python
for /d %%D in ("%LOCALAPPDATA%\Programs\Python\Python3*") do if exist "%%~D\python.exe" call :try_python "%%~D\python.exe"
if defined PY_EXE goto :found_python
for /d %%D in ("%ProgramFiles%\Python3*") do if exist "%%~D\python.exe" call :try_python "%%~D\python.exe"
if defined PY_EXE goto :found_python
for /d %%D in ("C:\Python3*") do if exist "%%~D\python.exe" call :try_python "%%~D\python.exe"
if defined PY_EXE goto :found_python
if exist "%USERPROFILE%\anaconda3\python.exe" call :try_python "%USERPROFILE%\anaconda3\python.exe"
if exist "%USERPROFILE%\miniconda3\python.exe" call :try_python "%USERPROFILE%\miniconda3\python.exe"
if exist "%LOCALAPPDATA%\anaconda3\python.exe" call :try_python "%LOCALAPPDATA%\anaconda3\python.exe"
if exist "%LOCALAPPDATA%\miniconda3\python.exe" call :try_python "%LOCALAPPDATA%\miniconda3\python.exe"
if exist "C:\ProgramData\anaconda3\python.exe" call :try_python "C:\ProgramData\anaconda3\python.exe"
if exist "C:\ProgramData\miniconda3\python.exe" call :try_python "C:\ProgramData\miniconda3\python.exe"
if exist "%USERPROFILE%\scoop\apps\python\current\python.exe" call :try_python "%USERPROFILE%\scoop\apps\python\current\python.exe"
if exist "%LOCALAPPDATA%\Programs\Python\Launcher\py.exe" call :try_python "%LOCALAPPDATA%\Programs\Python\Launcher\py.exe" "-3"
if exist "%WINDIR%\py.exe" call :try_python "%WINDIR%\py.exe" "-3"

if not defined PY_EXE goto :no_python

:found_python
echo 已找到 Python：%PY_EXE% %PY_ARG%
echo 正在启动本地服务并自动打开浏览器控制台，请稍候...
echo.

set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"

if defined PY_ARG goto :run_with_arg
"%PY_EXE%" server.py --open
goto :after_run

:run_with_arg
"%PY_EXE%" %PY_ARG% server.py --open

:after_run
set "EXIT_CODE=%errorlevel%"
if %EXIT_CODE% neq 0 goto :run_failed

popd >nul 2>&1
exit /b 0

:pushd_failed
echo [错误] 无法切换到脚本所在目录，请将文件夹移到本机磁盘后重试。
echo.
pause
exit /b 1

:run_failed
echo.
echo ============================================================
echo [错误] 服务启动失败或异常退出（错误码: %EXIT_CODE%）。
echo 请查看上方报错信息；如无法解决，请截图反馈以便排查。
echo ============================================================
echo.
pause
popd >nul 2>&1
exit /b %EXIT_CODE%

:missing_server
echo [错误] 当前目录下找不到 server.py ！
echo.
echo 可能原因：你直接双击打开了 ZIP 压缩包里的脚本，还没有解压。
echo 解决方法：请先右键将整个压缩包「全部解压缩」到一个文件夹，
echo           再进入解压后的文件夹双击「启动背屏桌宠_Windows.bat」。
echo.
pause
popd >nul 2>&1
exit /b 1

:no_python
echo.
echo [提示] 未检测到可用的 Python 3（需要 Python 3.7 及以上版本）。
echo.
echo 请任选以下一种方式安装 Python 3，装好后重新双击本脚本：
echo   1. 打开微软商店（Microsoft Store）搜索「Python 3.12」免费安装；
echo   2. 或前往 https://www.python.org/downloads/ 下载安装包，
echo      安装界面底部务必勾选「Add python.exe to PATH」。
echo.
echo （注意：Windows 系统自带的空壳 python.exe 占位符无法运行本程序）
echo.
pause
popd >nul 2>&1
exit /b 1

:try_python
if defined PY_EXE exit /b 0
if "%~1"=="" exit /b 1
"%~1" %~2 -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 7) else 1)" >nul 2>nul
if %errorlevel% neq 0 exit /b 1
set "PY_EXE=%~1"
set "PY_ARG=%~2"
exit /b 0
