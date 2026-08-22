[CmdletBinding()]
param(
    [string]$MongoUri = "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin",

    [string]$DbName = "adavis_platform",

    [string]$SourceApiBaseUrl = "http://localhost:8000/fwxapi/rest/v1/Dataset",

    [string[]]$DatasetIds = @(
        "G5RMG",
        "G6RMG",
        "G7RMG",
        "G5FBD",
        "G6FBD",
        "G7FBD",
        "G5OGB",
        "G6OGB",
        "G7OGB"
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ------------------------------------------------------------
# Resolve backend root
# ------------------------------------------------------------

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
# Validate dataset IDs
# ------------------------------------------------------------

$validDatasetIds = @(
    $DatasetIds |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }
)

if ($validDatasetIds.Count -eq 0) {
    throw "At least one dataset ID is required."
}

# ------------------------------------------------------------
# Build mock endpoint
# ------------------------------------------------------------

$testDatasetId = $validDatasetIds[0]

$mockEndpoint = "${SourceApiBaseUrl}?pointname=db:${testDatasetId}.BATCHDETAILS"

# ------------------------------------------------------------
# Check mock data service
# ------------------------------------------------------------

Write-Host ""
Write-Host "============================================" -ForegroundColor DarkCyan
Write-Host " ADAVIS IIOT - INGESTION TEST" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor DarkCyan
Write-Host ""

Write-Host "Checking mock data service..." -ForegroundColor Cyan
Write-Host "URL            : $SourceApiBaseUrl" -ForegroundColor Gray
Write-Host "Test endpoint  : $mockEndpoint" -ForegroundColor Gray
Write-Host ""

try {

    $response = Invoke-RestMethod `
        -Uri $mockEndpoint `
        -Method Get `
        -TimeoutSec 10

    if ($null -eq $response) {
        throw "Mock data service returned an empty response."
    }

    if ($response.status -ne "success") {
        throw "Mock data service returned status '$($response.status)'."
    }

    Write-Host "Mock data service is reachable." -ForegroundColor Green
}
catch {

    throw @"
Mock data service is not reachable at:

$SourceApiBaseUrl

Start the mock service with:

./run-mock-data-service.ps1

Details:
$($_.Exception.Message)
"@
}

# ------------------------------------------------------------
# Display ingestion configuration
# ------------------------------------------------------------

Write-Host ""
Write-Host "Running one ingestion cycle..." -ForegroundColor Cyan
Write-Host "Mongo database : $DbName" -ForegroundColor Gray
Write-Host "Backend root   : $backendRoot" -ForegroundColor Gray
Write-Host "Python         : $pythonCommand" -ForegroundColor Gray
Write-Host "Dataset count  : $($validDatasetIds.Count)" -ForegroundColor Gray
Write-Host "Datasets       : $($validDatasetIds -join ', ')" -ForegroundColor Gray
Write-Host ""

# ------------------------------------------------------------
# Preserve existing environment variable
# ------------------------------------------------------------

$previousSourceApiBaseUrl = $env:SOURCE_API_BASE_URL

$env:SOURCE_API_BASE_URL = $SourceApiBaseUrl

$exitCode = 1

try {

    Push-Location $backendRoot

    Write-Host "Starting scheduler ingestion..." -ForegroundColor Cyan
    Write-Host ""

    & $pythonCommand `
        -m scheduler.run_scheduler_loop `
        --mongo-uri $MongoUri `
        --db-name $DbName `
        --dataset-ids $validDatasetIds `
        --once

    $exitCode = $LASTEXITCODE
}
finally {

    Pop-Location

    # Restore previous environment variable
    if ($null -eq $previousSourceApiBaseUrl) {
        Remove-Item Env:SOURCE_API_BASE_URL -ErrorAction SilentlyContinue
    }
    else {
        $env:SOURCE_API_BASE_URL = $previousSourceApiBaseUrl
    }
}

# ------------------------------------------------------------
# Validate scheduler result
# ------------------------------------------------------------

if ($exitCode -ne 0) {
    throw "Ingestion cycle failed with exit code $exitCode."
}

Write-Host ""
Write-Host "============================================" -ForegroundColor DarkGreen
Write-Host " INGESTION COMPLETED SUCCESSFULLY" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor DarkGreen
Write-Host ""