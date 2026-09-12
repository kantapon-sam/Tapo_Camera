param(
    [string]$Output = "dist\tapo-rtsp-viewer-portable",
    [switch]$IncludeCurrentJre,
    [switch]$IncludeInstalledVlc
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Resolve-InRootPath {
    param([string]$PathValue)

    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return [System.IO.Path]::GetFullPath($PathValue)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $Root $PathValue))
}

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

$resolvedOutput = Resolve-InRootPath $Output
$distRoot = [System.IO.Path]::GetFullPath((Join-Path $Root "dist"))
$distRootWithSlash = $distRoot.TrimEnd('\') + "\"
if (!$resolvedOutput.StartsWith($distRootWithSlash, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Output must be inside $distRoot"
}

$env:JAVA_HOME = Find-JavaHome
$maven = Find-Maven
$localRepo = Join-Path $Root ".tools\m2repo"
$savedCameraNamesPath = $null
$existingCameraNames = Join-Path $resolvedOutput "config\camera-names.properties"
if (Test-Path $existingCameraNames) {
    $savedCameraNamesPath = Join-Path ([System.IO.Path]::GetTempPath()) ("tapo-camera-names-" + [System.Guid]::NewGuid().ToString("N") + ".properties")
    Copy-Item -LiteralPath $existingCameraNames -Destination $savedCameraNamesPath
}

Push-Location $Root
try {
    & $maven "-Dmaven.repo.local=$localRepo" clean package dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=target\dependency"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

if (Test-Path $resolvedOutput) {
    Remove-Item -LiteralPath $resolvedOutput -Recurse -Force
}

$appDir = Join-Path $resolvedOutput "app"
$libDir = Join-Path $appDir "lib"
$configDir = Join-Path $resolvedOutput "config"
$recordingsDir = Join-Path $resolvedOutput "recordings"
$runtimeDir = Join-Path $resolvedOutput "runtime"
$vlcDir = Join-Path $resolvedOutput "vlc"
$toolsDir = Join-Path $resolvedOutput "tools"

New-Item -ItemType Directory -Force -Path $libDir, $configDir, $recordingsDir | Out-Null
Copy-Item -LiteralPath (Join-Path $Root "target\tapo-rtsp-viewer-1.0.0.jar") -Destination (Join-Path $appDir "tapo-rtsp-viewer.jar")
Copy-Item -Path (Join-Path $Root "target\dependency\*.jar") -Destination $libDir
Copy-Item -Path (Join-Path $Root "config\*") -Destination $configDir -Recurse
if ($null -ne $savedCameraNamesPath) {
    Copy-Item -LiteralPath $savedCameraNamesPath -Destination (Join-Path $configDir "camera-names.properties") -Force
    Remove-Item -LiteralPath $savedCameraNamesPath -Force
}
Copy-Item -LiteralPath (Join-Path $Root "portable\run.bat") -Destination (Join-Path $resolvedOutput "run.bat")
Copy-Item -LiteralPath (Join-Path $Root "portable\run.ps1") -Destination (Join-Path $resolvedOutput "run.ps1")
Copy-Item -LiteralPath (Join-Path $Root "portable\README-PORTABLE.txt") -Destination (Join-Path $resolvedOutput "README-PORTABLE.txt")

$ffmpegSource = Join-Path $Root ".tools\ffmpeg"
if (Test-Path (Join-Path $ffmpegSource "bin\ffmpeg.exe")) {
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    Copy-Item -LiteralPath $ffmpegSource -Destination (Join-Path $toolsDir "ffmpeg") -Recurse
}

if ($IncludeCurrentJre) {
    $jreSource = Join-Path $env:JAVA_HOME "jre"
    if (!(Test-Path (Join-Path $jreSource "bin\java.exe"))) {
        $jreSource = $env:JAVA_HOME
    }
    Copy-Item -LiteralPath $jreSource -Destination $runtimeDir -Recurse
} else {
    New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
    "Optional: place a Java 8+ runtime here so run.bat can use runtime\bin\java.exe." |
        Set-Content -Path (Join-Path $runtimeDir "PUT_JAVA_RUNTIME_HERE.txt") -Encoding UTF8
}

if ($IncludeInstalledVlc) {
    $installedVlc = Find-InstalledVlc
    if (!$installedVlc) {
        throw "Installed VLC was not found. Install VLC 64-bit or omit -IncludeInstalledVlc."
    }
    Copy-Item -LiteralPath $installedVlc -Destination $vlcDir -Recurse
} else {
    New-Item -ItemType Directory -Force -Path $vlcDir | Out-Null
    "Optional: copy VLC 64-bit files here. libvlc.dll should be directly inside the vlc folder." |
        Set-Content -Path (Join-Path $vlcDir "PUT_VLC_FILES_HERE.txt") -Encoding UTF8
}

$zipPath = $resolvedOutput.TrimEnd('\') + ".zip"
if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}
Compress-Archive -Path (Join-Path $resolvedOutput "*") -DestinationPath $zipPath -Force

Write-Host "Portable package created:"
Write-Host $resolvedOutput
Write-Host "Zip package created:"
Write-Host $zipPath
Write-Host "Run it with run.bat"
