param(
    [string]$Config = "config\cameras.properties"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
        return $env:JAVA_HOME
    }

    $candidates = @(
        "C:\Program Files\Java\latest",
        "C:\Program Files\Java\jdk1.8.0_202"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "bin\javac.exe")) {
            return $candidate
        }
    }

    $javaFolders = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\javac.exe") } |
        Sort-Object Name -Descending

    if ($javaFolders) {
        return $javaFolders[0].FullName
    }

    throw "JDK not found. Install Java 8+ JDK and set JAVA_HOME."
}

function Find-Maven {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $tools = Join-Path $Root ".tools"
    $mavenVersion = "3.9.9"
    $mavenDir = Join-Path $tools "apache-maven-$mavenVersion"
    $mavenCmd = Join-Path $mavenDir "bin\mvn.cmd"

    if (!(Test-Path $mavenCmd)) {
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        $zip = Join-Path $tools "apache-maven-$mavenVersion-bin.zip"
        if (!(Test-Path $zip)) {
            Write-Host "Downloading Apache Maven $mavenVersion..."
            Invoke-WebRequest `
                -Uri "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip" `
                -OutFile $zip
        }
        Expand-Archive -Path $zip -DestinationPath $tools -Force
    }

    return $mavenCmd
}

function Find-InstalledVlc {
    $candidates = @(
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

$env:JAVA_HOME = Find-JavaHome
$maven = Find-Maven
$localRepo = Join-Path $Root ".tools\m2repo"
$java = Join-Path $env:JAVA_HOME "bin\java.exe"

Push-Location $Root
try {
    & $maven "-Dmaven.repo.local=$localRepo" compile dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=target\dependency"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE"
    }

    $classpath = New-Object System.Collections.Generic.List[string]
    $classpath.Add((Join-Path $Root "target\classes"))
    Get-ChildItem (Join-Path $Root "target\dependency") -Filter "*.jar" | ForEach-Object {
        $classpath.Add($_.FullName)
    }

    $javaArgs = @()
    $vlc = Find-InstalledVlc
    if ($vlc) {
        $env:VLC_PLUGIN_PATH = Join-Path $vlc "plugins"
        $javaArgs += "-Djna.library.path=$vlc"
    }

    $javaArgs += @(
        "-cp",
        ($classpath -join [System.IO.Path]::PathSeparator),
        "local.tapo.viewer.App",
        $Config
    )

    & $java @javaArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Tapo RTSP Viewer exited with code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
