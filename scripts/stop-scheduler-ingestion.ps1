[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$logDir = Join-Path $PSScriptRoot "logs"
$stateDir = Join-Path $logDir "scheduler-ingestion"
$pidFile = Join-Path $stateDir "scheduler-ingestion.pid"

if (-not (Test-Path $pidFile)) {
    Write-Host "Scheduler ingestion PID file not found. If it is running in terminal, stop it there."
    return
}

$pidText = Get-Content -Path $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($pidText)) {
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    Write-Host "Scheduler ingestion PID file was empty and has been removed."
    return
}

try {
    $process = Get-Process -Id ([int]$pidText) -ErrorAction Stop
    Stop-Process -Id $process.Id -Force
    Write-Host "Stopped scheduler ingestion PID $($process.Id)."
}
catch {
    Write-Host "Scheduler ingestion PID $pidText is not running. Removing stale PID file."
}
finally {
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}
