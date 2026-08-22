[CmdletBinding()]
param(
    [switch]$NoReset
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "common.ps1")

Write-Section "Database Seed"
Assert-Command -Name "docker"

if (-not (Test-DockerRunning)) {
    throw "Docker is not running. Start Docker Desktop first."
}

$initScript = Join-Path (Get-RepoRoot) "docker\init-mongo.js"
if (-not (Test-Path $initScript)) {
    throw "Mongo seed script not found: $initScript"
}

$iiotSeedScript = Join-Path (Get-RepoRoot) "docker\seed_data_iiot_file.js"
$realtimeExportScript = Join-Path (Get-RepoRoot) "scripts\export-realtime-sheets.ps1"
$realtimeJsonDir = Join-Path (Get-RepoRoot) "realtime_sample_data\json"

function Test-ContainerExists {
    param([Parameter(Mandatory)][string]$Name)

    $match = (& docker ps -a --filter "name=^${Name}$" --format "{{.Names}}" 2>$null)
    return ($LASTEXITCODE -eq 0 -and ($match -contains $Name))
}

function Get-ContainerRuntimeStatus {
    param([Parameter(Mandatory)][string]$Name)

    $status = (& docker inspect -f "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $Name 2>$null)
    if ($LASTEXITCODE -ne 0) {
        return "missing"
    }

    return ("$status".Trim())
}

function Ensure-MongoContainerReady {
    if (-not (Test-ContainerExists -Name "adavis-mongodb")) {
        Write-Step "Mongo container not found; creating and starting mongodb via docker compose"
        Invoke-DockerCompose -Arguments @("up", "-d", "mongodb")
    }

    $status = Get-ContainerRuntimeStatus -Name "adavis-mongodb"
    if ($status -notmatch "running|healthy") {
        Write-Step "Starting mongodb container"
        Invoke-DockerCompose -Arguments @("up", "-d", "mongodb")
    }

    $deadline = (Get-Date).AddSeconds(120)
    do {
        $status = Get-ContainerRuntimeStatus -Name "adavis-mongodb"
        if ($status -match "running|healthy") {
            return
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)

    throw "Container adavis-mongodb did not become ready in time (last status: $status)."
}

Ensure-MongoContainerReady

function Wait-MongoReady {
    param(
        [Parameter(Mandatory)][string[]]$Uris,
        [int]$TimeoutSeconds = 90
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        foreach ($uri in $Uris) {
            $previousNativePreference = $null
            $hasNativePreference = $false
            if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -Scope Global -ErrorAction SilentlyContinue) {
                $hasNativePreference = $true
                $previousNativePreference = $Global:PSNativeCommandUseErrorActionPreference
                $Global:PSNativeCommandUseErrorActionPreference = $false
            }

            try {
                try {
                    & docker exec adavis-mongodb mongosh $uri --quiet --eval "db.runCommand({ ping: 1 })" *> $null
                    if ($LASTEXITCODE -eq 0) {
                        return
                    }
                } catch {
                    # Ignore transient connect/auth failures while probing readiness and continue retries.
                }
            } finally {
                if ($hasNativePreference) {
                    $Global:PSNativeCommandUseErrorActionPreference = $previousNativePreference
                }
            }
        }

        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "MongoDB is not accepting connections yet (timed out after $TimeoutSeconds seconds)."
}

# Try common auth layouts because local environments may have been initialized in different ways.
$mongoUris = @(
    "mongodb://admin:Admin123!@localhost:27017/adavis_platform?authSource=admin",
    "mongodb://admin:Admin123!@localhost:27017/adavis_platform?authSource=adavis_platform",
    "mongodb://localhost:27017/adavis_platform"
)

function Invoke-MongoCommand {
    param(
        [Parameter(Mandatory)][string[]]$Uris,
        [Parameter(Mandatory)][string[]]$Arguments,
        [switch]$Silent
    )

    $lastError = $null
    foreach ($uri in $Uris) {
        $previousNativePreference = $null
        $hasNativePreference = $false
        if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -Scope Global -ErrorAction SilentlyContinue) {
            $hasNativePreference = $true
            $previousNativePreference = $Global:PSNativeCommandUseErrorActionPreference
            $Global:PSNativeCommandUseErrorActionPreference = $false
        }

        try {
            try {
                $output = & docker exec adavis-mongodb mongosh $uri --quiet @Arguments 2>&1
            } catch {
                $output = @($_.Exception.Message)
            }
        } finally {
            if ($hasNativePreference) {
                $Global:PSNativeCommandUseErrorActionPreference = $previousNativePreference
            }
        }

        if ($LASTEXITCODE -eq 0) {
            if (-not $Silent -and $null -ne $output -and "$output".Length -gt 0) {
                $output
            }
            return $true
        }

        $lastError = $output
    }

    if ($null -ne $lastError -and "$lastError".Length -gt 0) {
        Write-Error ("Mongo command failed. Last error: " + ($lastError -join [Environment]::NewLine))
    }

    return $false
}

Wait-MongoReady -Uris $mongoUris

if (-not $NoReset) {
    Write-Step "Dropping existing adavis_platform database"
    if (-not (Invoke-MongoCommand -Uris $mongoUris -Arguments @("--eval", "db.dropDatabase()"))) {
        throw "Mongo database reset failed."
    }
}

Write-Step "Applying seed script to MongoDB"
if (-not (Invoke-MongoCommand -Uris $mongoUris -Arguments @("/docker-entrypoint-initdb.d/init-mongo.js") -Silent)) {
    throw "Mongo seed operation failed."
}

if ((Test-Path $realtimeExportScript) -and (Test-Path (Join-Path (Get-RepoRoot) "realtime_sample_data"))) {
    Write-Step "Exporting realtime Excel sheets to JSON"
    & $realtimeExportScript
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to export realtime sheets."
    }
}

if (Test-Path $iiotSeedScript) {
    Write-Step "Applying IIOT sample seed script to MongoDB"
    Get-Content -Raw -Path $iiotSeedScript | & docker exec -i adavis-mongodb mongosh "mongodb://admin:Admin123!@localhost:27017/adavis_platform?authSource=admin" --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "IIOT seed operation failed."
    }
}
else {
    Write-Step "IIOT seed script not found, skipping: $iiotSeedScript"
}

Write-Step "Verifying seeded collections"
if (-not (Invoke-MongoCommand -Uris $mongoUris -Arguments @("--eval", "db.getCollectionNames().sort().forEach(function(name){ print(name); })"))) {
    throw "Mongo seed verification failed."
}

Write-Step "Database seed completed successfully."