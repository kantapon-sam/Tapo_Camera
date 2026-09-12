$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppJar = Join-Path $Root "app\tapo-rtsp-viewer.jar"
$Config = Join-Path $Root "config\cameras.properties"

function Find-Java {
    $localJava = Join-Path $Root "runtime\bin\java.exe"
    if (Test-Path $localJava) {
        return $localJava
    }

    if ($env:JAVA_HOME) {
        $javaHomeExe = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $javaHomeExe) {
            return $javaHomeExe
        }
    }

    $javaFolders = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
        Sort-Object Name -Descending

    if ($javaFolders) {
        return (Join-Path $javaFolders[0].FullName "bin\java.exe")
    }

    $command = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "Java 8+ was not found. Install Java, set JAVA_HOME, or place a JRE in the runtime folder."
}

function Find-LocalVlc {
    $candidates = @(
        (Join-Path $Root "vlc"),
        (Join-Path $Root "vlc\vlc"),
        "C:\Program Files\VideoLAN\VLC",
        "C:\Program Files (x86)\VideoLAN\VLC"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "libvlc.dll")) {
            return $candidate
        }
    }

    return $null
}

try {
    Push-Location $Root
    $java = Find-Java
    $javaArgs = @()
    $vlc = Find-LocalVlc
    if ($vlc) {
        $env:VLC_PLUGIN_PATH = Join-Path $vlc "plugins"
        $javaArgs += "-Djna.library.path=$vlc"
    }

    & $java @javaArgs -jar $AppJar $Config
    if ($LASTEXITCODE -ne 0) {
        throw "Tapo RTSP Viewer exited with code $LASTEXITCODE"
    }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Read-Host "Press Enter to close"
    exit 1
} finally {
    Pop-Location
}
