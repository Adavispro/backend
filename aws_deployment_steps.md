Adavis Platform deployment and operations guide

This document covers both local execution and AWS EC2 Docker deployment.

Use local PowerShell scripts on Windows for day-to-day development.
Use Docker Compose on AWS EC2 for instance-based deployment.

Local development on Windows

Prerequisites

- Java 21
- Maven
- Docker Desktop
- Git

Useful local ports

- API Gateway: `9080`
- Auth Service: `9081`
- License Service: `8082`
- MDM Service: `9083`
- Audit Service: `8084`
- IIoT Service: `9085`
- MongoDB: `37017`
- Redis: `8379`
- Redpanda: `10092`

Primary local commands

Environment check:

```powershell
./scripts/check-environment.ps1
```

First-time setup and startup:

```powershell
./scripts/setup-dev.ps1
```

Setup with full Maven build first:

```powershell
./scripts/setup-dev.ps1 -BuildFirst
```

Start local infrastructure and services without reseeding:

```powershell
./scripts/run-local.ps1
```

Restart everything cleanly:

```powershell
./scripts/stop-all.ps1
./scripts/setup-dev.ps1 -ForceRestart
```

Seed database

Reset and reseed MongoDB:

```powershell
./scripts/seed-data.ps1
```

Alternate command for Aws:
pwsh -File "seed-data.ps1"

Seed without dropping existing data:

```powershell
./scripts/seed-data.ps1 -NoReset
```

Current local service logs

When services are started through the PowerShell scripts, logs are written to:

- `scripts/logs/auth-service.log`
- `scripts/logs/mdm-service.log`
- `scripts/logs/iiot-service.log`
- `scripts/logs/license-service.log`
- `scripts/logs/audit-service.log`
- `scripts/logs/api-gateway.log`

Tail a local service log:

```powershell
Get-Content .\scripts\logs\auth-service.log -Tail 100
Get-Content .\scripts\logs\mdm-service.log -Tail 100
Get-Content .\scripts\logs\api-gateway.log -Tail 100
```

Docker infrastructure logs locally:

```powershell
docker compose -f docker/docker-compose.local.yml logs --tail=100 mongodb
docker compose -f docker/docker-compose.local.yml logs --tail=100 redis
docker compose -f docker/docker-compose.local.yml logs --tail=100 redpanda
```

Stop local stack

Stop services and Docker, preserving Docker data:

```powershell
./scripts/stop-all.ps1 -KeepData
```

Stop services and Docker, removing Docker volumes:

```powershell
./scripts/stop-all.ps1
```

Restart one local service

If only one Java service needs restart, stop the process on its port and run it again.

Stop by port examples:

```powershell
Get-NetTCPConnection -LocalPort 9081 -State Listen | Select-Object -ExpandProperty OwningProcess | Select-Object -Unique | ForEach-Object { Stop-Process -Id $_ -Force }
Get-NetTCPConnection -LocalPort 9083 -State Listen | Select-Object -ExpandProperty OwningProcess | Select-Object -Unique | ForEach-Object { Stop-Process -Id $_ -Force }
Get-NetTCPConnection -LocalPort 8084 -State Listen | Select-Object -ExpandProperty OwningProcess | Select-Object -Unique | ForEach-Object { Stop-Process -Id $_ -Force }
```

Rebuild and restart one service from its jar:

```powershell
mvn -pl services/auth-service -am -DskipTests package
Set-Location .\services\auth-service
java -jar target/auth-service-1.0.0-SNAPSHOT.jar
```

```powershell
mvn -pl services/mdm-service -am -DskipTests package
Set-Location .\services\mdm-service
java -jar target/mdm-service-1.0.0-SNAPSHOT.jar
```

```powershell
mvn -pl services/audit-service -am -DskipTests package
Set-Location .\services\audit-service
java -jar target/audit-service-1.0.0-SNAPSHOT.jar
```

```powershell
mvn -pl services/api-gateway -am -DskipTests package
Set-Location .\services\api-gateway
java -jar target/api-gateway-1.0.0-SNAPSHOT.jar
```

Local health checks

```powershell
Invoke-WebRequest http://localhost:9080/actuator/health -UseBasicParsing
Invoke-WebRequest http://localhost:9081/actuator/health -UseBasicParsing
Invoke-WebRequest http://localhost:9083/actuator/health -UseBasicParsing
Invoke-WebRequest http://localhost:8084/actuator/health -UseBasicParsing
```

Local dependency recovery

If services fail with connection refused to MongoDB, Redis, or Redpanda, bring them up explicitly:

```powershell
Set-Location .\docker
docker compose -f docker-compose.local.yml up -d mongodb redis redpanda
```

Local build command

```powershell
./scripts/build-all.ps1
```

Local cleanup when memory or disk pressure happens

Stop all managed services first:

```powershell
./scripts/stop-all.ps1 -KeepData
```

Stop and remove local Docker infrastructure including volumes:

```powershell
docker compose -f docker/docker-compose.local.yml down -v
```

Free Docker disk space:

```powershell
docker system df
docker system prune -af
docker builder prune -af
docker volume prune -f
```

Optional deeper cleanup if containers are corrupted:

```powershell
docker compose -f docker/docker-compose.local.yml down --remove-orphans -v
```

AWS EC2 deployment

Recommended EC2 baseline

- Ubuntu Server 22.04 LTS or 24.04 LTS
- `t3.large` minimum for initial testing
- 30 to 50 GiB gp3 storage

Security group

- `22` from your IP only
- `80` open for API gateway
- `443` open if TLS is configured
- `37017` Inbound with IP
Install prerequisites

```bash
sudo apt update
sudo apt install -y git curl ca-certificates gnupg lsb-release docker.io docker-compose-v2
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker ubuntu
newgrp docker
```

Clone repository

```bash
git clone <your-repo-url>
cd ADAVIS_PLATFORM_BACKEND
git checkout <your-branch>
git pull
```

Prepare AWS environment file

```bash
cp .env.aws.example .env.aws
nano .env.aws
```

Required `.env.aws` keys

- `JWT_SECRET`
- `MONGODB_URI`
- `REDIS_HOST`
- `REDIS_PORT`
- `KAFKA_BOOTSTRAP_SERVERS`
- `CORS_ALLOWED_ORIGINS`

IIOT Phase 1 batch ingestion keys

- `IIOT_INGESTION_SCHEDULER_DELAY_MS` (default `15000`)
- `IIOT_SOURCE_DB_URL`
- `IIOT_SOURCE_DB_USERNAME`
- `IIOT_SOURCE_DB_PASSWORD`

Deploy on AWS

```bash
chmod +x scripts/deploy-aws.sh scripts/status-aws.sh scripts/stop-aws.sh
./scripts/deploy-aws.sh
```

Seed IIOT sample data for UI development support

After deployment, run:

```bash
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml exec -T mongodb \
	mongosh "mongodb://admin:Admin123!@localhost:27017/adavis_platform?authSource=admin" \
	--file /seed/seed_data_iiot_file.js
```

If the above path is unavailable in your image, run from repository root on EC2:

```bash
mongosh "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin" --file docker/seed_data_iiot_file.js
```

What `deploy-aws.sh` does

- validates `.env.aws`
- validates `JWT_SECRET`
- builds missing JARs if needed
- validates Docker Compose configuration
- builds service images sequentially to reduce Docker exporter failures on smaller EC2 hosts
- starts MongoDB, Redis, Redpanda, and all Spring services
- waits for API gateway health

Check AWS deployment status

```bash
./scripts/status-aws.sh
```

Stop AWS deployment

```bash
./scripts/stop-aws.sh
```

AWS logs

Tail all logs:

```bash
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml logs --tail=200
```

Tail one service:

```bash
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml logs --tail=200 auth-service
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml logs --tail=200 mdm-service
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml logs --tail=200 audit-service
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml logs --tail=200 api-gateway
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml logs --tail=200 license-service
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml logs --tail=200 iiot-service
```

Follow one service log continuously:

```bash
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml logs -f auth-service
```

Restart one AWS service

Simple restart:

```bash
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml restart auth-service
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml restart mdm-service
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml restart audit-service
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml restart api-gateway
```

Rebuild and restart one service:

```bash
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml up -d --build auth-service
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml up -d --build mdm-service
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml up -d --build audit-service
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml up -d --build api-gateway
```

Restart the full AWS stack

```bash
./scripts/stop-aws.sh
./scripts/deploy-aws.sh
```

Seed data notes

Local Windows seeding is supported by:

```powershell
./scripts/seed-data.ps1
```

AWS compose already mounts `docker/init-mongo.js` into MongoDB initialization.
If you need a fresh AWS reseed, the simplest safe approach is:

```bash
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml down
docker volume rm adavis-platform_mongodb_data adavis-platform_redis_data adavis-platform_redpanda_data || true
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml up -d --build
```

Only do this when you intentionally want to discard environment data.

Health and troubleshooting commands

Check local ports quickly on Windows:

```powershell
@(37017,8379,10092,9080,9081,9083,8082,8084,9085) | ForEach-Object { Test-NetConnection -ComputerName localhost -Port $_ -WarningAction SilentlyContinue }
```

Check AWS container status:

```bash
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml ps
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

Check disk and Docker usage:

```bash
df -h
docker system df
```

If deployment fails with `ERROR [mdm-service] exporting to image`:

```bash
# 1) verify free space first
df -h
docker system df

# 2) reclaim Docker cache/layers if disk is tight
docker system prune -af
docker builder prune -af

# 3) build mdm-service alone with plain logs to see the exact failing layer
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml build --no-cache --progress=plain mdm-service

# 4) continue full deployment
./scripts/deploy-aws.sh
```

Notes:

- Service-level `.dockerignore` files (for `auth-service`, `mdm-service`, `audit-service`, `api-gateway`, `iiot-service`, `license-service`) should be present to keep build context small on EC2.
- If `build --progress=plain` still fails, capture the last 30 to 50 lines of output and inspect `no space left on device`, `input/output error`, or registry pull failures.

Cleanup commands for AWS memory or disk pressure

Stop stack first if needed:

```bash
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml down --remove-orphans
```

Remove unused Docker objects:

```bash
docker system prune -af
docker builder prune -af
docker volume prune -f
```

If you need a full clean rebuild of the AWS stack:

```bash
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml down --remove-orphans
docker system prune -af
docker builder prune -af
docker compose --env-file .env.aws -f docker/docker-compose.aws.yml up -d --build
```

Operational notes

- Local script-managed service logs are under `scripts/logs/`.
- Local auth-service and mdm-service require MongoDB on `37017`.
- Local audit-service requires MongoDB on `37017` and Redpanda on `10092`.
- API gateway local route for audit uses `http://localhost:8084`.
- If `GET /api/v1/audit/trails` fails with `Connection refused ... localhost:8084`, start audit-service and Redpanda.
- If refresh fails with raw token parsing issues, the auth refresh endpoint now accepts both documented JSON and raw token bodies.