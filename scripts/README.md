# Scripts

This folder contains the local development workflow for the Adavis Platform.

## PowerShell entry points

- `check-environment.ps1`: validates local prerequisites and common port conflicts.
- `build-all.ps1`: runs a repository build from the root Maven pom.
- `seed-data.ps1`: resets and seeds MongoDB from `docker/init-mongo.js`.
- `db-migrate.ps1`: current migration wrapper for the Mongo seed workflow.
- `run-local.ps1`: starts Docker infrastructure and Spring services.
- `stop-all.ps1`: stops managed Spring services and Docker containers.
- `setup-dev.ps1`: one-command setup for check, seed, Docker, and service startup. Use `-BuildFirst` if you also want a full root build.

## Mock Data Service

Start the mock source API in the background:

```powershell
.\scripts\run-mock-data-service.ps1
```

Stop it with:

```powershell
.\scripts\stop-mock-data-service.ps1
```

## Scheduler Ingestion Service

Start scheduler ingestion in the background:

```powershell
.\scripts\run-scheduler-ingestion.ps1
```

Start scheduler ingestion for specific datasets:

```powershell
.\scripts\run-scheduler-ingestion.ps1 -DatasetIds G5RMG,G6RMG,G5FBD,G5OGB
```

Stop it with:

```powershell
.\scripts\stop-scheduler-ingestion.ps1
```

## Typical usage

### First-time local startup

```powershell
./scripts/setup-dev.ps1
```

### Run a full Maven build before startup

```powershell
./scripts/setup-dev.ps1 -BuildFirst
```

### Start the stack while skipping specific services

```powershell
./scripts/setup-dev.ps1 -SkipServices mdm-service
```

This is useful when one service has compile errors but you still want the rest of the platform running.

### Re-seed database and restart everything

```powershell
./scripts/stop-all.ps1
./scripts/setup-dev.ps1 -ForceRestart
```

### Start only Docker infrastructure and services

```powershell
./scripts/run-local.ps1
```

### Seed database as a separate step

```powershell
./scripts/seed-data.ps1
```

### Seed without dropping existing database

```powershell
./scripts/seed-data.ps1 -NoReset
```

### Stop services and keep Docker volumes

```powershell
./scripts/stop-all.ps1 -KeepData
```

## Runtime outputs

- Service logs are written to `scripts/logs/`.
- Managed service PID state is written to `scripts/.state/services.json`.
- Startup failures now print the last log lines from the failing service (default: 80 lines). Override with `-FailureLogTailLines`.

## Notes

- `clean-run-phase1.ps1` remains as a compatibility wrapper and redirects to `setup-dev.ps1`.
- `db-migrate.ps1` currently reuses the Mongo seed script because this repository does not yet have a standalone migration framework.

## AWS Ubuntu deployment scripts

- `deploy-aws.sh`: builds and starts all application containers using `docker/docker-compose.aws.yml` and `.env.aws`.
- `status-aws.sh`: shows running container status and checks API gateway health.
- `stop-aws.sh`: stops AWS deployment containers.

`deploy-aws.sh` validates required keys in `.env.aws`, rejects placeholder values, validates compose config, and waits for API gateway health before returning success.

### Typical AWS flow

```bash
cp .env.aws.example .env.aws
# update .env.aws with production values
./scripts/deploy-aws.sh
./scripts/status-aws.sh
```