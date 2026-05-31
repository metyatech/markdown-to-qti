#Requires -Version 7.0

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CliArgs = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Path $PSScriptRoot -Parent

function Resolve-Java23Home {
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

    $localHome = Join-Path -Path (Join-Path -Path $RepoRoot -ChildPath ".jdks") -ChildPath "jdk-23"
    $localJava = Join-Path -Path (Join-Path -Path $localHome -ChildPath "bin") -ChildPath "java.exe"
    if (-not $IsWindows) {
        $localJava = Join-Path -Path (Join-Path -Path $localHome -ChildPath "bin") -ChildPath "java"
    }
    if (Test-Path -LiteralPath $localJava) {
        return $localHome
    }

    return $null
}

function Invoke-NativeProcess {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [Parameter()][string[]]$Arguments = @(),
        [Parameter()][AllowNull()][string]$JavaHome = $null,
        [Parameter(Mandatory)][string]$WorkingDirectory,
        [Parameter()][ValidateSet("Output", "Error")][string]$StandardOutputTarget = "Output"
    )

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $FilePath
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $startInfo.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    if ($JavaHome) {
        $javaBin = Join-Path -Path $JavaHome -ChildPath "bin"
        $separator = [System.IO.Path]::PathSeparator
        $startInfo.Environment["JAVA_HOME"] = $JavaHome
        $startInfo.Environment["PATH"] = "$javaBin$separator$($startInfo.Environment["PATH"])"
    }

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
        if ($StandardOutputTarget -eq "Error") {
            [Console]::Error.Write($stdout)
        } else {
            [Console]::Out.Write($stdout)
        }
    }
    if ($stderr.Length -gt 0) {
        [Console]::Error.Write($stderr)
    }

    return $process.ExitCode
}

$cliName = if ($IsWindows) { "markdown-to-qti.bat" } else { "markdown-to-qti" }
$installRoot = Join-Path -Path $repoRoot -ChildPath "build"
$installRoot = Join-Path -Path $installRoot -ChildPath "install"
$installRoot = Join-Path -Path $installRoot -ChildPath "markdown-to-qti"
$cliPath = Join-Path -Path $installRoot -ChildPath "bin"
$cliPath = Join-Path -Path $cliPath -ChildPath $cliName

function Invoke-InstallDist {
    param([Parameter(Mandatory)][string]$RepoRoot)

    $gradleLauncher = Join-Path -Path (Join-Path -Path $RepoRoot -ChildPath "tools") -ChildPath "gradle-java23.ps1"
    if (-not (Test-Path -LiteralPath $gradleLauncher)) {
        throw "Gradle Java 23 launcher was not found: $gradleLauncher"
    }

    $powerShellExe = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
    [Console]::Error.WriteLine("Preparing markdown-to-qti CLI with installDist...")
    return Invoke-NativeProcess `
        -FilePath $powerShellExe `
        -Arguments @("-NoProfile", "-File", $gradleLauncher, "installDist") `
        -JavaHome $null `
        -WorkingDirectory $RepoRoot `
        -StandardOutputTarget "Error"
}

function Ensure-InstalledCli {
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [Parameter(Mandatory)][string]$CliPath
    )

    $javaHome = Resolve-Java23Home -RepoRoot $RepoRoot
    if ($javaHome -and (Test-Path -LiteralPath $CliPath)) {
        return $javaHome
    }

    if (-not $javaHome) {
        [Console]::Error.WriteLine("Java 23 was not found; the launcher will prepare the local JDK if needed.")
    } elseif (-not (Test-Path -LiteralPath $CliPath)) {
        [Console]::Error.WriteLine("Installed CLI was not found: $CliPath")
    }

    $installExitCode = Invoke-InstallDist -RepoRoot $RepoRoot
    if ($installExitCode -ne 0) {
        exit $installExitCode
    }

    $javaHome = Resolve-Java23Home -RepoRoot $RepoRoot
    if (-not $javaHome) {
        throw "Java 23 was not found after installDist. Set JAVA_HOME_23 to a JDK 23 installation."
    }
    if (-not (Test-Path -LiteralPath $CliPath)) {
        throw "Installed CLI was not found after installDist: $CliPath"
    }

    return $javaHome
}

$javaHome = Ensure-InstalledCli -RepoRoot $repoRoot -CliPath $cliPath
$exitCode = Invoke-NativeProcess `
    -FilePath $cliPath `
    -Arguments $CliArgs `
    -JavaHome $javaHome `
    -WorkingDirectory (Get-Location).Path
exit $exitCode
