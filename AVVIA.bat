@echo off
setlocal
cd /d "%~dp0"
java -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true -jar dist\GameBackLogManager.jar
if errorlevel 1 (
 echo.
 echo Avvio non riuscito. Serve Java 17 o superiore. Leggi LEGGIMI.md.
 pause
)
