@echo off
setlocal
cd /d "%~dp0"
call COMPILA.bat
if errorlevel 1 exit /b 1
if not exist build\test mkdir build\test
if exist testsources.txt del testsources.txt
for /r test %%f in (*.java) do echo "%%f">>testsources.txt
javac --release 17 -encoding UTF-8 -cp build\main -d build\test @testsources.txt
if errorlevel 1 exit /b 1
java -cp build\main;build\test gbm.IntegrationTest
if errorlevel 1 exit /b 1
java -cp build\main;build\test gbm.ProviderTest
if errorlevel 1 exit /b 1
java -Djava.awt.headless=true -cp build\main;build\test;resources gbm.ThemeTest
if errorlevel 1 exit /b 1
if defined GBM_TEST_H2_JAR java -cp "build\main;build\test;%GBM_TEST_H2_JAR%" gbm.DatabaseTest
pause
