# Docker Local Infrastructure Commands

## 1. Start Infrastructure (Data is Preserved by Default)
```powershell
# From workspace root:
docker compose -f backend/docker/docker-compose.local.yml up -d

# Or from backend/docker:
cd backend/docker
docker compose -f docker-compose.local.yml up -d
```
> **Note:** By default, `MONGO_INIT_RESET_DB` and `MONGO_INIT_SEED_DATA` are both `false`. Starting containers will **never** drop or seed your database.

---

## 2. Start Infrastructure with Explicit Seeding / Reset (Inline Flags)

### A. Start and Seed MDM Master Data:
```powershell
# PowerShell:
$env:MONGO_INIT_SEED_DATA="true"; docker compose -f backend/docker/docker-compose.local.yml up -d; Remove-Item Env:\MONGO_INIT_SEED_DATA

# Bash / WSL:
MONGO_INIT_SEED_DATA=true docker compose -f backend/docker/docker-compose.local.yml up -d
```

### B. Start, Wipe Database and Re-seed Fresh (Clean Init):
```powershell
# PowerShell:
$env:MONGO_INIT_RESET_DB="true"; $env:MONGO_INIT_SEED_DATA="true"; docker compose -f backend/docker/docker-compose.local.yml up -d; Remove-Item Env:\MONGO_INIT_RESET_DB; Remove-Item Env:\MONGO_INIT_SEED_DATA

# Bash / WSL:
MONGO_INIT_RESET_DB=true MONGO_INIT_SEED_DATA=true docker compose -f backend/docker/docker-compose.local.yml up -d
```

---

## 3. Check Status & Logs
```powershell
# Check running containers and health:
docker compose -f backend/docker/docker-compose.local.yml ps

# Follow live logs:
docker compose -f backend/docker/docker-compose.local.yml logs -f
```

---

## 4. Stop Containers (Data is Preserved)
```powershell
docker compose -f backend/docker/docker-compose.local.yml down
```

---

## 5. On-Demand Database Reset & Master Seeding (While Containers Run)

To drop the database and seed the full MDM schema, topology, auth users, permissions, and IIoT equipment masters:

### Option A: Dedicated Reset & Seed Script (Recommended)
```powershell
# From workspace root:
powershell -ExecutionPolicy Bypass -File backend\scripts\reset-and-seed.ps1

# On Linux/macOS:
bash backend/scripts/reset-and-seed.sh
```

### Option B: Direct Shell Command
```powershell
Get-Content backend\seed_data\seed_mdm_data.js -Raw | docker exec -i adavis-mongodb mongosh -u admin -p Admin123! --authenticationDatabase admin --quiet
```

---

## 6. Standalone Script Executions Inside Container

```powershell
# Reset Database Only:
docker exec -i adavis-mongodb mongosh -u admin -p Admin123! --authenticationDatabase admin /seed_data/reset_db.js

# Seed MDM Data Only:
docker exec -i adavis-mongodb mongosh -u admin -p Admin123! --authenticationDatabase admin /seed_data/seed_mdm_data.js
```

---

## 7. Full Volume Reset (Wipe volume and restart empty)
```powershell
docker compose -f backend/docker/docker-compose.local.yml down -v
docker compose -f backend/docker/docker-compose.local.yml up -d
```
