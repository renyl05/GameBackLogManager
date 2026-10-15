@echo off
setlocal
cd /d "%~dp0"
java -cp dist\GameBackLogManager.jar gbm.Server
if errorlevel 1 pause
