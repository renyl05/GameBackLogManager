@echo off
setlocal
cd /d "%~dp0"
set /p "GBM_SERVER_URL=URL HTTPS del server condiviso: "
if not defined GBM_SERVER_URL set "GBM_SERVER_URL=https://gamebacklogmanager.onrender.com"
java -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -jar dist\GameBackLogManager.jar
if errorlevel 1 pause
