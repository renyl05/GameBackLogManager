@echo off
setlocal
cd /d "%~dp0"
if not exist build\main mkdir build\main
if not exist dist mkdir dist
if exist sources.txt del sources.txt
for /r src %%f in (*.java) do echo "%%f">>sources.txt
javac --release 17 -encoding UTF-8 -d build\main @sources.txt
if errorlevel 1 goto fail
jar --create --file dist\GameBackLogManager.jar --main-class gbm.App --manifest manifest.mf -C build\main . -C resources .
if errorlevel 1 goto fail
echo Compilazione completata.
exit /b 0
:fail
echo Compilazione fallita. Installa un JDK 17 o superiore.
pause
exit /b 1
