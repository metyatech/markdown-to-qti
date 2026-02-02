#Requires -Version 7.0

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs = @("installDist")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Path $PSScriptRoot -Parent

function Ensure-Directory {
    param([Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
    $item = Get-Item -LiteralPath $Path -ErrorAction Stop
    if (-not $item.PSIsContainer) {
        throw "Expected a directory but got: $Path"
    }
}

function Get-Java23Home {
    if ($env:JAVA_HOME_23) {
        $javaExe = Join-Path -Path $env:JAVA_HOME_23 -ChildPath "bin\\java.exe"
        if (-not $IsWindows) {
            $javaExe = Join-Path -Path $env:JAVA_HOME_23 -ChildPath "bin/java"
        }
        if (Test-Path -LiteralPath $javaExe) {
            return $env:JAVA_HOME_23
        }
        throw "JAVA_HOME_23 is set but java was not found: $javaExe"
    }

    $localRoot = Join-Path -Path $repoRoot -ChildPath ".jdks"
    $localHome = Join-Path -Path $localRoot -ChildPath "jdk-23"
    $localJava = Join-Path -Path $localHome -ChildPath "bin\\java.exe"
    if (-not $IsWindows) {
        $localJava = Join-Path -Path $localHome -ChildPath "bin/java"
    }
    if (Test-Path -LiteralPath $localJava) {
        return $localHome
    }

    if (-not $IsWindows) {
        throw "Java 23 is required. Install JDK 23 and set JAVA_HOME_23."
    }

    Ensure-Directory -Path $localRoot

    $arch = if ($env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "aarch64" } else { "x64" }
    $url = "https://api.adoptium.net/v3/binary/latest/23/ga/windows/$arch/jdk/hotspot/normal/eclipse"
    $zipPath = Join-Path -Path $localRoot -ChildPath "jdk-23.zip"
    $extractDir = Join-Path -Path $localRoot -ChildPath "tmp"

    if (Test-Path -LiteralPath $extractDir) {
        Remove-Item -Path $extractDir -Recurse -Force
    }

    Write-Host ("Downloading JDK 23 from {0}" -f $url)
    Invoke-WebRequest -Uri $url -OutFile $zipPath | Out-Null
    Expand-Archive -Path $zipPath -DestinationPath $extractDir -Force
    Remove-Item -Path $zipPath -Force

    $extracted = Get-ChildItem -Path $extractDir -Directory | Select-Object -First 1
    if (-not $extracted) {
        throw "Failed to extract JDK 23 into: $extractDir"
    }

    if (Test-Path -LiteralPath $localHome) {
        Remove-Item -Path $localHome -Recurse -Force
    }
    Move-Item -Path $extracted.FullName -Destination $localHome | Out-Null
    Remove-Item -Path $extractDir -Recurse -Force

    $finalJava = Join-Path -Path $localHome -ChildPath "bin\\java.exe"
    if (-not (Test-Path -LiteralPath $finalJava)) {
        throw "JDK 23 installation is missing java.exe: $localHome"
    }
    return $localHome
}

$javaHome = Get-Java23Home
$javaBin = Join-Path -Path $javaHome -ChildPath "bin"
$sep = [System.IO.Path]::PathSeparator
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaBin$sep$($env:PATH)"

$resolvedArgs = @($GradleArgs)
if (-not ($resolvedArgs -contains "--no-daemon")) {
    $resolvedArgs += "--no-daemon"
}

& gradle @resolvedArgs
if ($LASTEXITCODE -ne 0) {
    throw "Gradle failed with exit code ${LASTEXITCODE}: gradle $($resolvedArgs -join ' ')"
}
