[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$logDir = Join-Path $PSScriptRoot "logs"
$stateDir = Join-Path $logDir "mock-data-service"
$pidFile = Join-Path $stateDir "mock-data-service.pid"

if (-not (Test-Path $pidFile)) {
    Write-Host "Mock data service PID file not found. If the service is running in a terminal, stop it with Ctrl+C."
    return
}

$pidText = Get-Content -Path $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1
if ([string]::IsNullOrWhiteSpace($pidText)) {
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    Write-Host "Mock data service PID file was empty and has been removed."
    return
}

try {
    $process = Get-Process -Id ([int]$pidText) -ErrorAction Stop
    Stop-Process -Id $process.Id -Force
    Write-Host "Stopped mock data service PID $($process.Id)."
}
catch {
    Write-Host "Mock data service PID $pidText is not running. Removing stale PID file."
}
finally {
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}
