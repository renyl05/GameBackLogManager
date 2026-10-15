@echo off
setlocal
cd /d "%~dp0"
set /p "GBM_SERVER_URL=URL HTTPS del server condiviso: "
if not defined GBM_SERVER_URL exit /b 1
java -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -jar dist\GameBackLogManager.jar
if errorlevel 1 pause
