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

$backendRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$pythonExe = Join-Path $backendRoot ".venv\Scripts\python.exe"
$mockEndpoint = "$SourceApiBaseUrl?pointname=db:G5RMG.BATCHDETAILS"

if (-not (Test-Path $pythonExe)) {
    throw "Python executable not found at $pythonExe. Create the backend virtual environment first."
}

$validDatasetIds = @($DatasetIds | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($validDatasetIds.Count -eq 0) {
    throw "At least one dataset ID is required."
}

Write-Host "Checking mock data service at $SourceApiBaseUrl..." -ForegroundColor Cyan
try {
    $response = Invoke-RestMethod -Uri $mockEndpoint -Method Get -TimeoutSec 10
    if ($response.status -ne "success") {
        throw "Mock data service returned status '$($response.status)'."
    }
}
catch {
    throw "Mock data service is not reachable at $SourceApiBaseUrl. Start it with .\scripts\run-mock-data-service.ps1. Details: $($_.Exception.Message)"
}

Write-Host "Running one ingestion cycle for $($validDatasetIds.Count) dataset(s)..." -ForegroundColor Cyan
Write-Host "Mongo database: $DbName" -ForegroundColor Gray
Write-Host "Datasets: $($validDatasetIds -join ', ')" -ForegroundColor Gray

$previousSourceApiBaseUrl = $env:SOURCE_API_BASE_URL
$env:SOURCE_API_BASE_URL = $SourceApiBaseUrl
try {
    Push-Location $backendRoot
    & $pythonExe -m scheduler.run_scheduler_loop `
        --mongo-uri $MongoUri `
        --db-name $DbName `
        --dataset-ids $validDatasetIds `
        --once
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
    $env:SOURCE_API_BASE_URL = $previousSourceApiBaseUrl
}

if ($exitCode -ne 0) {
    throw "Ingestion cycle failed with exit code $exitCode."
}

Write-Host "Ingestion cycle completed successfully." -ForegroundColor Green
