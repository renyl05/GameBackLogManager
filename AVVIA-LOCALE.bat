@echo off
setlocal
cd /d "%~dp0"
java -Dgbm.server=http://127.0.0.1:8787 -jar dist\GameBackLogManager.jar
if errorlevel 1 pause
