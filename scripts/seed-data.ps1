# ============================================
# Adavis Platform - Full Database Seeding (PowerShell)
# ============================================

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir

$ContainerMongoUri = "mongodb://admin:Admin123!@localhost:27017/adavis_platform?authSource=admin"
$HostMongoUri = if ($env:MONGO_URI) { $env:MONGO_URI } else { "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin" }
$DbName = if ($env:DB_NAME) { $env:DB_NAME } else { "adavis_platform" }

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " Adavis Platform - Full Database Seeding" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

$ContainerName = "adavis-mongodb"

# 1. Ensure Mongo container is ready
Write-Host "Checking MongoDB connection inside container ($ContainerName)..." -ForegroundColor Yellow
$attempts = 0
$connected = $false

while ($attempts -lt 15) {
    try {
        $res = docker exec -i $ContainerName mongosh -u admin -p Admin123! --authenticationDatabase admin --quiet --eval "db.runCommand({ ping: 1 })" 2>$null
        if ($res -match "ok.*1") {
            $connected = $true
            break
        }
    } catch { }
    Write-Host "Waiting for MongoDB container ($ContainerName)..."
    Start-Sleep -Seconds 2
    $attempts++
}

if (-not $connected) {
    Write-Host "Error: Timed out waiting for MongoDB container '$ContainerName'." -ForegroundColor Red
    exit 1
}

# 2. Reset and apply init-mongo.js
Write-Host "Applying base platform initialization and schemas (init-mongo.js)..." -ForegroundColor Yellow
Get-Content "$RepoRoot\docker\init-mongo.js" -Raw | docker exec -i $ContainerName mongosh -u admin -p Admin123! --authenticationDatabase admin --quiet

# 3. Apply IIOT master seed (seed_data_iiot_file.js)
if (Test-Path "$RepoRoot\docker\seed_data_iiot_file.js") {
    Write-Host "Applying IIOT master definitions seed..." -ForegroundColor Yellow
    Get-Content "$RepoRoot\docker\seed_data_iiot_file.js" -Raw | docker exec -i $ContainerName mongosh -u admin -p Admin123! --authenticationDatabase admin --quiet
}

# 4. Run mock data ingestion to seed batches, alarms, audits, and time-series records
if ((Test-Path "$RepoRoot\data_service_layer\mock_data_service.py") -and (Test-Path "$RepoRoot\scheduler\run_scheduler_loop.py")) {
    Write-Host "Seeding batch, alarm, audit, and time-series records via mock ingestion..." -ForegroundColor Yellow
    
    $pythonBin = "python"

    # Start mock service process
    New-Item -ItemType Directory -Force -Path "$ScriptDir\logs" | Out-Null
    $mockOutLog = "$ScriptDir\logs\mock_data_service.log"
    $mockErrLog = "$ScriptDir\logs\mock_data_service.err.log"
    $ingestLog = "$ScriptDir\logs\ingestion.log"

    $env:PYTHONPATH = $RepoRoot
    $env:DATA_INGESTION_START_DATE = "2026-08-29 20:00:00"
    $mockProcess = Start-Process -FilePath $pythonBin -ArgumentList "-m data_service_layer.mock_data_service" -PassThru -RedirectStandardOutput $mockOutLog -RedirectStandardError $mockErrLog -NoNewWindow
    Start-Sleep -Seconds 2

    try {
        Write-Host "  - Running unified ingestion for all equipment categories (G5RMG, G5FBD, G5OGB, G5COAT)..." -ForegroundColor Gray
        & $pythonBin -m scheduler.run_scheduler_loop --mongo-uri "$HostMongoUri" --db-name "$DbName" --dataset-ids G5RMG G5FBD G5OGB G5COAT --once *>> $ingestLog
    } finally {
        if ($mockProcess -and -not $mockProcess.HasExited) {
            Stop-Process -Id $mockProcess.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

# 5. Display collection summary
Write-Host "Verifying database collection counts..." -ForegroundColor Yellow
docker exec -i $ContainerName mongosh -u admin -p Admin123! --authenticationDatabase admin --quiet --eval @"
  const collections = [
    'mdm_tenants',
    'mdm_plants',
    'auth_users',
    'mdm_user_profiles',
    'mdm_roles',
    'iiot_equipment_master',
    'iiot_equipment_critical_parameters',
    'iiot_equipment_critical_parameters_limit',
    'iiot_product_master',
    'iiot_batch_summary',
    'iiot_ingestion_checkpoint',
    'iiot_ingestion_job_run',
    'iiot_ts_batch_G5RMG',
    'iiot_ts_batch_G5FBD',
    'iiot_ts_batch_G5OGB',
    'iiot_ts_batch_G5COAT',
    'iiot_ts_alarm_G5RMG',
    'iiot_ts_alarm_G5FBD',
    'iiot_ts_alarm_G5OGB',
    'iiot_ts_alarm_G5COAT',
    'iiot_ts_audit_G5RMG',
    'iiot_ts_audit_G5FBD',
    'iiot_ts_audit_G5OGB',
    'iiot_ts_audit_G5COAT'
  ];
  collections.forEach(col => {
    print('  - ' + col.padEnd(42) + ': ' + db.getCollection(col).countDocuments({}));
  });
"@

Write-Host "============================================" -ForegroundColor Green
Write-Host "Database seeding completed successfully!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
