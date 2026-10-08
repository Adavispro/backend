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

# 4. Run authentic unified IIoT ingestion (4 API Equipment + 1 Compression Equipment)
echo "Seeding authentic IIoT batches, master data, parameters, alarms, audits, and time-series records..."
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

mkdir -p "$SCRIPT_DIR/logs"
PYTHONPATH="$REPO_ROOT/ingestion_services" "$PYTHON_BIN" "$REPO_ROOT/ingestion_services/unified_ingestion_runner.py" \
  --truncate-and-load \
  --mongo-uri "$HOST_MONGO_URI" \
  --db-name "$DB_NAME" \
  --once >> "$SCRIPT_DIR/logs/ingestion.log" 2>&1 || {
    echo "    [WARN] Unified ingestion encountered an error. Check scripts/logs/ingestion.log"
  }

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
