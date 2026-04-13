@echo off
setlocal

REM --- Configuration ---
set APP_NAME=signing-desktop
set APP_VERSION=0.0.1
set MAIN_JAR=signing-desktop\target\signing-desktop-0.0.1-SNAPSHOT.jar
set OUTPUT_DIR=dist
set RUNTIME_DIR=custom-jre

echo [1/5] Don dep va bat dau build du an... 
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo Build that bai!
    exit /b %errorlevel%
)

echo [2/5] Dang tao Custom JRE voi jlink...
if exist %RUNTIME_DIR% rmdir /s /q %RUNTIME_DIR%

REM Fix: Call jlink explicitly
call "%JAVA_HOME%\bin\jlink" ^
    --add-modules java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.scripting,java.security.jgss,java.sql,java.xml,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.crypto.cryptoki,java.smartcardio ^
    --bind-services ^
    --strip-debug ^
    --compress 2 ^
    --no-header-files ^
    --no-man-pages ^
    --output %RUNTIME_DIR%

if %errorlevel% neq 0 (
    echo jlink loi!
    exit /b %errorlevel%
)

echo [3/5] Packaging App Image (Portable EXE)...
if exist %OUTPUT_DIR%\%APP_NAME% rmdir /s /q %OUTPUT_DIR%\%APP_NAME%

call "%JAVA_HOME%\bin\jpackage" ^
    --type app-image ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --input signing-desktop\target ^
    --main-jar signing-desktop-0.0.1-SNAPSHOT.jar ^
    --main-class org.springframework.boot.loader.launch.JarLauncher ^
    --runtime-image %RUNTIME_DIR% ^
    --dest %OUTPUT_DIR% ^
    --icon "signing-desktop\src\main\resources\ky-so-lt.ico" ^
    --win-console

if %errorlevel% neq 0 (
    echo jpackage app-image loi!
    exit /b %errorlevel%
)

echo [4/5] Dang kiem tra WiX Toolset (Yeu cau de build MSI)...
where candle >nul 2>nul
if %errorlevel% neq 0 (
    echo [WARNING] WiX Toolset khong tim thay!
    echo Bo qua build file cai dat .msi. Vui long tai xuong WiX v3.x de build file cai dat .msi.
    echo Download: https://github.com/wixtoolset/wix3/releases
    goto :finish
)

echo [5/5] Dang tao MSI Installer...
call "%JAVA_HOME%\bin\jpackage" ^
    --type msi ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --input signing-desktop\target ^
    --main-jar signing-desktop-0.0.1-SNAPSHOT.jar ^
    --main-class org.springframework.boot.loader.launch.JarLauncher ^
    --runtime-image %RUNTIME_DIR% ^
    --dest %OUTPUT_DIR% ^
    --icon "signing-desktop\src\main\resources\ky-so-lt.ico" ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut ^
    --win-console

if %errorlevel% neq 0 (
    echo jpackage MSI loi!
    exit /b %errorlevel%
)

:finish
echo.
echo ==================================================
echo Build thanh cong!
echo [1] Portable App: %OUTPUT_DIR%\%APP_NAME%
echo [1] Debug Run: dist\signing-desktop\signing-desktop.exe
if exist %OUTPUT_DIR%\*.msi echo [2] MSI Installer: %OUTPUT_DIR%\*.msi
echo ==================================================

endlocal
