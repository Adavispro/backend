[CmdletBinding()]
param(
    [int]$Port = 8000
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ------------------------------------------------------------
# Resolve paths
# ------------------------------------------------------------

$logDir = Join-Path $PSScriptRoot "logs"
$stateDir = Join-Path $logDir "mock-data-service"

$stdoutLog = Join-Path $stateDir "stdout.log"
$stderrLog = Join-Path $stateDir "stderr.log"
$pidFile = Join-Path $stateDir "mock-data-service.pid"

$scriptPath = Join-Path $PSScriptRoot "../data_service_layer/mock_data_service.py"
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
    # Try "python3" - commonly available on Ubuntu/Linux
    $python3Path = Get-Command python3 -ErrorAction SilentlyContinue

    if ($null -ne $python3Path) {
        $pythonCommand = $python3Path.Source
    }
    else {
        # Try Windows Python launcher
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
# Validate mock data service script
# ------------------------------------------------------------

if (-not (Test-Path $scriptPath)) {
    throw "Mock data service script not found at '$scriptPath'."
}

# ------------------------------------------------------------
# Validate port
# ------------------------------------------------------------

if ($Port -lt 1 -or $Port -gt 65535) {
    throw "Port must be between 1 and 65535."
}

# ------------------------------------------------------------
# Create log/state directory
# ------------------------------------------------------------

New-Item `
    -ItemType Directory `
    -Force `
    -Path $stateDir |
    Out-Null

# ------------------------------------------------------------
# Check whether mock service is already running
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
            Write-Host "Mock data service is already running." -ForegroundColor Yellow
            Write-Host "PID  : $($existingProcess.Id)"
            Write-Host "Port : $Port"
            Write-Host ""
            Write-Host "Stop it with:" -ForegroundColor Cyan
            Write-Host "./stop-mock-data-service.ps1"
            Write-Host ""

            return
        }
        catch {

            # PID file exists but process is no longer running
            Remove-Item `
                $pidFile `
                -Force `
                -ErrorAction SilentlyContinue
        }
    }
}

# ------------------------------------------------------------
# Clean previous logs
# ------------------------------------------------------------

if (Test-Path $stdoutLog) {
    Remove-Item `
        $stdoutLog `
        -Force `
        -ErrorAction SilentlyContinue
}

if (Test-Path $stderrLog) {
    Remove-Item `
        $stderrLog `
        -Force `
        -ErrorAction SilentlyContinue
}

# ------------------------------------------------------------
# Display configuration
# ------------------------------------------------------------

Write-Host ""
Write-Host "============================================" -ForegroundColor DarkCyan
Write-Host " ADAVIS IIOT - MOCK DATA SERVICE" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor DarkCyan
Write-Host ""

Write-Host "Python      : $pythonCommand" -ForegroundColor Gray
Write-Host "Version     : $pythonVersion" -ForegroundColor Gray
Write-Host "Script      : $scriptPath" -ForegroundColor Gray
Write-Host "Working Dir : $backendRoot" -ForegroundColor Gray
Write-Host "Port        : $Port" -ForegroundColor Gray
Write-Host "Output Log  : $stdoutLog" -ForegroundColor Gray
Write-Host "Error Log   : $stderrLog" -ForegroundColor Gray
Write-Host ""

# ------------------------------------------------------------
# Start mock data service
# ------------------------------------------------------------

$process = Start-Process `
    -FilePath $pythonCommand `
    -ArgumentList @(
        $scriptPath,
        $Port
    ) `
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

Write-Host "Mock data service started successfully." -ForegroundColor Green
Write-Host ""
Write-Host "URL  : http://localhost:$Port"
Write-Host "PID  : $($process.Id)"
Write-Host "Logs : $stdoutLog"
Write-Host "Error: $stderrLog"
Write-Host ""
Write-Host "Stop with:" -ForegroundColor Cyan
Write-Host "./stop-mock-data-service.ps1"
Write-Host ""