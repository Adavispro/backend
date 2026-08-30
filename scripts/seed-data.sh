#!/usr/bin/env bash

set -euo pipefail

export MSYS_NO_PATHCONV=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CONTAINER_MONGO_URI="mongodb://admin:Admin123!@localhost:27017/adavis_platform?authSource=admin"
HOST_MONGO_URI="${MONGO_URI:-mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin}"
DB_NAME="${DB_NAME:-adavis_platform}"

echo "============================================"
echo " Adavis Platform - Full Database Seeding"
echo "============================================"

# Detect container runtime (docker, docker.exe, or podman)
if command -v docker.exe >/dev/null 2>&1 && docker.exe ps >/dev/null 2>&1; then
  CONTAINER_CLI="docker.exe"
elif command -v docker >/dev/null 2>&1 && docker ps >/dev/null 2>&1; then
  CONTAINER_CLI="docker"
elif command -v podman >/dev/null 2>&1; then
  CONTAINER_CLI="podman"
else
  echo "Error: Neither docker nor podman is installed."
  exit 1
fi

CONTAINER_NAME="adavis-mongodb"
if ! $CONTAINER_CLI ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  MATCHING=$($CONTAINER_CLI ps --format '{{.Names}}' | grep "mongodb" | head -n 1 || true)
  if [[ -n "$MATCHING" ]]; then
    CONTAINER_NAME="$MATCHING"
  fi
fi

# 1. Ensure Mongo container is ready
echo "Checking MongoDB connection inside container ($CONTAINER_NAME) using $CONTAINER_CLI..."
ATTEMPTS=0
until $CONTAINER_CLI exec -i "$CONTAINER_NAME" mongosh "$CONTAINER_MONGO_URI" --quiet --eval "db.runCommand({ ping: 1 })" >/dev/null 2>&1; do
  echo "Waiting for MongoDB container ($CONTAINER_NAME)..."
  sleep 2
  ATTEMPTS=$((ATTEMPTS + 1))
  if [[ $ATTEMPTS -ge 15 ]]; then
    echo "Error: Timed out waiting for MongoDB container '$CONTAINER_NAME'."
    echo "Check if the container is running: $CONTAINER_CLI ps"
    exit 1
  fi
done

# 2. Reset and apply init-mongo.js
echo "Applying base platform initialization and schemas (init-mongo.js)..."
cat "$REPO_ROOT/docker/init-mongo.js" | $CONTAINER_CLI exec -i "$CONTAINER_NAME" mongosh "$CONTAINER_MONGO_URI" --quiet

# 3. Apply IIOT master seed (seed_data_iiot_file.js)
if [[ -f "$REPO_ROOT/docker/seed_data_iiot_file.js" ]]; then
  echo "Applying IIOT master definitions seed..."
  cat "$REPO_ROOT/docker/seed_data_iiot_file.js" | $CONTAINER_CLI exec -i "$CONTAINER_NAME" mongosh "$CONTAINER_MONGO_URI" --quiet
fi

# 4. Run mock data ingestion to seed batches, alarms, audits, and time-series records
if [[ -f "$REPO_ROOT/data_service_layer/mock_data_service.py" && -f "$REPO_ROOT/scheduler/run_scheduler_loop.py" ]]; then
  echo "Seeding batch, alarm, audit, and time-series records via mock ingestion..."
  PYTHON_BIN="$REPO_ROOT/.venv/bin/python3"
  if [[ ! -f "$PYTHON_BIN" ]]; then
    if command -v python3 >/dev/null 2>&1; then
      PYTHON_BIN="python3"
    elif command -v python.exe >/dev/null 2>&1; then
      PYTHON_BIN="python.exe"
    else
      PYTHON_BIN="python"
    fi
  fi

  # Check if required Python modules are available
  if ! "$PYTHON_BIN" -c "import pymongo, requests" >/dev/null 2>&1; then
    echo "  Installing required Python modules (pymongo, requests)..."
    "$PYTHON_BIN" -m pip install pymongo requests >/dev/null 2>&1 || {
      echo "  [WARN] Could not install pymongo/requests automatically. Ensure they are installed via 'pip install pymongo requests'."
    }
  fi

  # Start mock service in background with safety trap
  mkdir -p "$SCRIPT_DIR/logs"
  export DATA_INGESTION_START_DATE="2026-08-29 20:00:00"
  PYTHONPATH="$REPO_ROOT" "$PYTHON_BIN" -m data_service_layer.mock_data_service > "$SCRIPT_DIR/logs/mock_data_service.log" 2>&1 &
  MOCK_PID=$!
  trap '[[ -n "${MOCK_PID:-}" ]] && kill "$MOCK_PID" 2>/dev/null || true' EXIT INT TERM
  sleep 2

  # Run single unified ingestion cycle across all datasets
  echo "  - Ingesting all datasets (G5RMG, G5FBD, G5OGB, G5COAT)..."
  PYTHONPATH="$REPO_ROOT" "$PYTHON_BIN" -m scheduler.run_scheduler_loop \
    --mongo-uri "$HOST_MONGO_URI" \
    --db-name "$DB_NAME" \
    --dataset-ids G5RMG G5FBD G5OGB G5COAT \
    --once >> "$SCRIPT_DIR/logs/ingestion.log" 2>&1 || {
      echo "    [WARN] Ingestion encountered an error. Check scripts/logs/ingestion.log"
    }

  # Stop mock service
  kill "$MOCK_PID" 2>/dev/null || true
  trap - EXIT INT TERM
fi

# 5. Display collection summary
echo "Verifying database collection counts..."
$CONTAINER_CLI exec -i "$CONTAINER_NAME" mongosh "$CONTAINER_MONGO_URI" --quiet --eval "
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
" || true

echo "============================================"
echo "Database seeding completed successfully!"
echo "============================================"
