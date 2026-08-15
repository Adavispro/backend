[CmdletBinding()]
param(
    [int]$IntervalSeconds = 900,
    [string]$MongoUri = "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin",
    [string]$DbName = "adavis_platform",
    [string[]]$DatasetIds = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$logDir = Join-Path $PSScriptRoot "logs"
$stateDir = Join-Path $logDir "scheduler-ingestion"
$stdoutLog = Join-Path $stateDir "stdout.log"
$stderrLog = Join-Path $stateDir "stderr.log"
$pidFile = Join-Path $stateDir "scheduler-ingestion.pid"
$scriptPath = Join-Path $PSScriptRoot "..\scheduler\run_scheduler_loop.py"
$pythonExe = Join-Path $PSScriptRoot "..\.venv\Scripts\python.exe"

if (-not (Test-Path $pythonExe)) {
    throw "Python executable not found at $pythonExe. Activate the virtual environment first or create it under .venv."
}

New-Item -ItemType Directory -Force -Path $stateDir | Out-Null

if (Test-Path $pidFile) {
    $existingPid = Get-Content -Path $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not [string]::IsNullOrWhiteSpace($existingPid)) {
        try {
            $existingProcess = Get-Process -Id ([int]$existingPid) -ErrorAction Stop
            Write-Host "Scheduler ingestion already running as PID $($existingProcess.Id)."
            Write-Host "Stop it with: .\scripts\stop-scheduler-ingestion.ps1"
            return
        }
        catch {
            Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        }
    }
}

if (Test-Path $stdoutLog) { Remove-Item $stdoutLog -Force -ErrorAction SilentlyContinue }
if (Test-Path $stderrLog) { Remove-Item $stderrLog -Force -ErrorAction SilentlyContinue }

$arguments = @(
    $scriptPath,
    "--mongo-uri", $MongoUri,
    "--db-name", $DbName,
    "--interval-seconds", [string]$IntervalSeconds
)

if ($DatasetIds.Count -gt 0) {
    $arguments += "--dataset-ids"
    $arguments += $DatasetIds
}

$process = Start-Process -FilePath $pythonExe `
    -ArgumentList $arguments `
    -WorkingDirectory (Join-Path $PSScriptRoot "..") `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

Set-Content -Path $pidFile -Value $process.Id
Write-Host "Scheduler ingestion started as PID $($process.Id)."
Write-Host "Logs: $stdoutLog"
Write-Host "Stop: .\scripts\stop-scheduler-ingestion.ps1"
