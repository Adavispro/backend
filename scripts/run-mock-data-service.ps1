[CmdletBinding()]
param(
    [int]$Port = 8000
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$logDir = Join-Path $PSScriptRoot "logs"
$stateDir = Join-Path $logDir "mock-data-service"
$stdoutLog = Join-Path $stateDir "stdout.log"
$stderrLog = Join-Path $stateDir "stderr.log"
$pidFile = Join-Path $stateDir "mock-data-service.pid"
$scriptPath = Join-Path $PSScriptRoot "..\data_service_layer\mock_data_service.py"
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
            Write-Host "Mock data service already running as PID $($existingProcess.Id)."
            Write-Host "Stop it with: .\scripts\stop-mock-data-service.ps1"
            return
        }
        catch {
            Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        }
    }
}

if (Test-Path $stdoutLog) { Remove-Item $stdoutLog -Force -ErrorAction SilentlyContinue }
if (Test-Path $stderrLog) { Remove-Item $stderrLog -Force -ErrorAction SilentlyContinue }

$process = Start-Process -FilePath $pythonExe `
    -ArgumentList @($scriptPath, $Port) `
    -WorkingDirectory (Join-Path $PSScriptRoot "..") `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

Set-Content -Path $pidFile -Value $process.Id
Write-Host "Mock data service started on http://localhost:$Port as PID $($process.Id)."
Write-Host "Logs: $stdoutLog"
Write-Host "Stop: .\scripts\stop-mock-data-service.ps1"
