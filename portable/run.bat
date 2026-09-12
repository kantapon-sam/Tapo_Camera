@echo off
setlocal

set "ROOT=%~dp0"
set "APP_JAR=%ROOT%app\tapo-rtsp-viewer.jar"
set "CONFIG=%ROOT%config\cameras.properties"
set "JAVA_EXE="
set "VLC_DIR="

cd /d "%ROOT%"

if exist "%ROOT%runtime\bin\java.exe" set "JAVA_EXE=%ROOT%runtime\bin\java.exe"
if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE for /f "delims=" %%J in ('dir /b /ad /o-n "C:\Program Files\Java" 2^>nul') do if exist "C:\Program Files\Java\%%J\bin\java.exe" if not defined JAVA_EXE set "JAVA_EXE=C:\Program Files\Java\%%J\bin\java.exe"
if not defined JAVA_EXE (
    where java.exe >nul 2>nul
    if not errorlevel 1 set "JAVA_EXE=java.exe"
)

if not defined JAVA_EXE (
    echo Java 8+ was not found.
    echo Install Java, set JAVA_HOME, or place a JRE in the runtime folder.
    pause
    exit /b 1
)

if exist "%ROOT%vlc\libvlc.dll" set "VLC_DIR=%ROOT%vlc"
if not defined VLC_DIR if exist "%ROOT%vlc\vlc\libvlc.dll" set "VLC_DIR=%ROOT%vlc\vlc"
if not defined VLC_DIR if exist "C:\Program Files\VideoLAN\VLC\libvlc.dll" set "VLC_DIR=C:\Program Files\VideoLAN\VLC"
if not defined VLC_DIR if exist "C:\Program Files (x86)\VideoLAN\VLC\libvlc.dll" set "VLC_DIR=C:\Program Files (x86)\VideoLAN\VLC"
if defined VLC_DIR set "VLC_PLUGIN_PATH=%VLC_DIR%\plugins"

if defined VLC_DIR (
    "%JAVA_EXE%" "-Djna.library.path=%VLC_DIR%" -jar "%APP_JAR%" "%CONFIG%"
) else (
    "%JAVA_EXE%" -jar "%APP_JAR%" "%CONFIG%"
)

if errorlevel 1 (
    echo.
    echo Tapo RTSP Viewer exited with an error.
    pause
)
