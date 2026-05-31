#Requires -Version 7.0

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CliArgs = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Path $PSScriptRoot -Parent

function Get-Java23Home {
    param([Parameter(Mandatory)][string]$RepoRoot)

    if ($env:JAVA_HOME_23) {
        $javaExe = Join-Path -Path $env:JAVA_HOME_23 -ChildPath "bin\java.exe"
        if (-not $IsWindows) {
            $javaExe = Join-Path -Path $env:JAVA_HOME_23 -ChildPath "bin/java"
        }
        if (Test-Path -LiteralPath $javaExe) {
            return $env:JAVA_HOME_23
        }
        throw "JAVA_HOME_23 is set but java was not found: $javaExe"
    }

    $localHome = Join-Path -Path $RepoRoot -ChildPath ".jdks\jdk-23"
    $localJava = Join-Path -Path $localHome -ChildPath "bin\java.exe"
    if (-not $IsWindows) {
        $localHome = Join-Path -Path $RepoRoot -ChildPath ".jdks/jdk-23"
        $localJava = Join-Path -Path $localHome -ChildPath "bin/java"
    }
    if (Test-Path -LiteralPath $localJava) {
        return $localHome
    }

    throw "Java 23 was not found. Set JAVA_HOME_23 or run tools/gradle-java23.ps1 installDist first."
}

function Invoke-NativeProcess {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter()][string[]]$Arguments = @(),
        [Parameter(Mandatory)][string]$JavaHome,
        [Parameter(Mandatory)][string]$WorkingDirectory
    )

    $javaBin = Join-Path -Path $JavaHome -ChildPath "bin"
    $separator = [System.IO.Path]::PathSeparator

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $startInfo.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    $startInfo.Environment["JAVA_HOME"] = $JavaHome
    $startInfo.Environment["PATH"] = "$javaBin$separator$($startInfo.Environment["PATH"])"

    foreach ($argument in $Arguments) {
        [void]$startInfo.ArgumentList.Add($argument)
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void]$process.Start()

    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()

    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    if ($stdout.Length -gt 0) {
        [Console]::Out.Write($stdout)
    }
    if ($stderr.Length -gt 0) {
        [Console]::Error.Write($stderr)
    }

    return $process.ExitCode
}

$javaHome = Get-Java23Home -RepoRoot $repoRoot
$cliName = if ($IsWindows) { "markdown-to-qti.bat" } else { "markdown-to-qti" }
$cliPath = Join-Path -Path $repoRoot -ChildPath "build\install\markdown-to-qti\bin\$cliName"
if (-not $IsWindows) {
    $cliPath = Join-Path -Path $repoRoot -ChildPath "build/install/markdown-to-qti/bin/$cliName"
}

if (-not (Test-Path -LiteralPath $cliPath)) {
    throw "Installed CLI was not found: $cliPath. Run tools/gradle-java23.ps1 installDist first."
}

$exitCode = Invoke-NativeProcess `
    -FilePath $cliPath `
    -Arguments $CliArgs `
    -JavaHome $javaHome `
    -WorkingDirectory (Get-Location).Path
exit $exitCode
