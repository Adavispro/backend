[CmdletBinding()]
param(
    [int]$IntervalSeconds = 900,
    [string]$MongoUri = "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin",
    [string]$DbName = "adavis_platform",
    [string[]]$DatasetIds = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ------------------------------------------------------------
# Resolve paths
# ------------------------------------------------------------

$logDir = Join-Path $PSScriptRoot "logs"
$stateDir = Join-Path $logDir "scheduler-ingestion"

$stdoutLog = Join-Path $stateDir "stdout.log"
$stderrLog = Join-Path $stateDir "stderr.log"
$pidFile = Join-Path $stateDir "scheduler-ingestion.pid"

$scriptPath = Join-Path $PSScriptRoot "../scheduler/run_scheduler_loop.py"
$backendRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

# ------------------------------------------------------------
# Resolve Python executable
# ------------------------------------------------------------

$pythonCommand = $null

# Try "python"
$pythonPath = Get-Command python -ErrorAction SilentlyContinue

if ($null -ne $pythonPath) {
    $pythonCommand = $pythonPath.Source
}
else {
    # Try "python3" - Ubuntu/Linux
    $python3Path = Get-Command python3 -ErrorAction SilentlyContinue

    if ($null -ne $python3Path) {
        $pythonCommand = $python3Path.Source
    }
    else {
        # Try Windows Python launcher "py"
        $pyPath = Get-Command py -ErrorAction SilentlyContinue

        if ($null -ne $pyPath) {
            $pythonCommand = $pyPath.Source
        }
    }
}

if ($null -eq $pythonCommand) {
    throw @"
Python was not found on this machine.

Please verify Python is installed and available in PATH.

Try:

    python --version
    python3 --version
"@
}

# ------------------------------------------------------------
# Validate Python
# ------------------------------------------------------------

try {
    $pythonVersion = & $pythonCommand --version 2>&1

    if ($LASTEXITCODE -ne 0) {
        throw "Python command returned exit code $LASTEXITCODE."
    }

    Write-Host "Python executable : $pythonCommand" -ForegroundColor Gray
    Write-Host "Python version    : $pythonVersion" -ForegroundColor Gray
}
catch {
    throw "Python was found but could not be executed. Details: $($_.Exception.Message)"
}

# ------------------------------------------------------------
# Validate scheduler script
# ------------------------------------------------------------

if (-not (Test-Path $scriptPath)) {
    throw "Scheduler script not found at '$scriptPath'."
}

# ------------------------------------------------------------
# Validate interval
# ------------------------------------------------------------

if ($IntervalSeconds -le 0) {
    throw "IntervalSeconds must be greater than 0."
}

# ------------------------------------------------------------
# Create log/state directory
# ------------------------------------------------------------

New-Item -ItemType Directory -Force -Path $stateDir | Out-Null

# ------------------------------------------------------------
# Check whether scheduler is already running
# ------------------------------------------------------------

if (Test-Path $pidFile) {

    $existingPid = Get-Content `
        -Path $pidFile `
        -ErrorAction SilentlyContinue |
        Select-Object -First 1

    if (-not [string]::IsNullOrWhiteSpace($existingPid)) {

        try {
            $existingProcess = Get-Process `
                -Id ([int]$existingPid) `
                -ErrorAction Stop

            Write-Host ""
            Write-Host "Scheduler ingestion is already running." -ForegroundColor Yellow
            Write-Host "PID      : $($existingProcess.Id)"
            Write-Host "Interval : $IntervalSeconds seconds"
            Write-Host ""
            Write-Host "Stop it with:" -ForegroundColor Cyan
            Write-Host "./stop-scheduler-ingestion.ps1"
            Write-Host ""

            return
        }
        catch {
            # PID file exists but process is no longer running
            Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        }
    }
}

# ------------------------------------------------------------
# Clean previous logs
# ------------------------------------------------------------

if (Test-Path $stdoutLog) {
    Remove-Item $stdoutLog -Force -ErrorAction SilentlyContinue
}

if (Test-Path $stderrLog) {
    Remove-Item $stderrLog -Force -ErrorAction SilentlyContinue
}

# ------------------------------------------------------------
# Build scheduler arguments
# ------------------------------------------------------------

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

# ------------------------------------------------------------
# Display configuration
# ------------------------------------------------------------

Write-Host ""
Write-Host "============================================" -ForegroundColor DarkCyan
Write-Host " ADAVIS IIOT - SCHEDULER INGESTION" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor DarkCyan
Write-Host ""

Write-Host "Python     : $pythonCommand" -ForegroundColor Gray
Write-Host "Script     : $scriptPath" -ForegroundColor Gray
Write-Host "Working Dir: $backendRoot" -ForegroundColor Gray
Write-Host "Database   : $DbName" -ForegroundColor Gray
Write-Host "Interval   : $IntervalSeconds seconds" -ForegroundColor Gray

if ($DatasetIds.Count -gt 0) {
    Write-Host "Datasets   : $($DatasetIds -join ', ')" -ForegroundColor Gray
}
else {
    Write-Host "Datasets   : All configured datasets" -ForegroundColor Gray
}

Write-Host ""

# ------------------------------------------------------------
# Start scheduler ingestion
# ------------------------------------------------------------

$process = Start-Process `
    -FilePath $pythonCommand `
    -ArgumentList $arguments `
    -WorkingDirectory $backendRoot `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

# ------------------------------------------------------------
# Save PID
# ------------------------------------------------------------

Set-Content `
    -Path $pidFile `
    -Value $process.Id `
    -Encoding UTF8

# ------------------------------------------------------------
# Result
# ------------------------------------------------------------

Write-Host "Scheduler ingestion started successfully." -ForegroundColor Green
Write-Host ""
Write-Host "PID  : $($process.Id)"
Write-Host "Logs : $stdoutLog"
Write-Host "Error: $stderrLog"
Write-Host ""
Write-Host "Stop with:" -ForegroundColor Cyan
Write-Host "./stop-scheduler-ingestion.ps1"
Write-Host ""