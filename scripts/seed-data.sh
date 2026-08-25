#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CONTAINER_MONGO_URI="mongodb://admin:Admin123!@localhost:27017/adavis_platform?authSource=admin"
HOST_MONGO_URI="${MONGO_URI:-mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin}"
DB_NAME="${DB_NAME:-adavis_platform}"

echo "============================================"
echo " Adavis Platform - Full Database Seeding"
echo "============================================"

# 1. Ensure Mongo container is ready
echo "Checking MongoDB connection inside container..."
until podman exec -i adavis-mongodb mongosh "$CONTAINER_MONGO_URI" --quiet --eval "db.runCommand({ ping: 1 })" >/dev/null 2>&1; do
  echo "Waiting for MongoDB container..."
  sleep 2
done

# 2. Reset and apply init-mongo.js
echo "Applying base platform initialization and schemas (init-mongo.js)..."
podman exec -i adavis-mongodb mongosh "$CONTAINER_MONGO_URI" --quiet < "$REPO_ROOT/docker/init-mongo.js"

# 3. Apply IIOT master seed (seed_data_iiot_file.js)
if [[ -f "$REPO_ROOT/docker/seed_data_iiot_file.js" ]]; then
  echo "Applying IIOT master definitions seed..."
  podman exec -i adavis-mongodb mongosh "$CONTAINER_MONGO_URI" --quiet < "$REPO_ROOT/docker/seed_data_iiot_file.js"
fi

# 4. Run mock data ingestion to seed batches, alarms, audits, and telemetry
if [[ -f "$REPO_ROOT/data_service_layer/mock_data_service.py" && -f "$REPO_ROOT/scheduler/run_scheduler_loop.py" ]]; then
  echo "Seeding batch, alarm, audit, and time-series records via mock ingestion..."
  PYTHON_BIN="$REPO_ROOT/.venv/bin/python3"
  if [[ ! -f "$PYTHON_BIN" ]]; then
    PYTHON_BIN="python3"
  fi

  # Start mock service in background
  mkdir -p "$SCRIPT_DIR/logs"
  PYTHONPATH="$REPO_ROOT" "$PYTHON_BIN" -m data_service_layer.mock_data_service > "$SCRIPT_DIR/logs/mock_data_service.log" 2>&1 &
  MOCK_PID=$!
  sleep 2

  # Run ingestion for all datasets
  DATASETS="G5RMG G6RMG G7RMG G5FBD G6FBD G7FBD G5OGB G6OGB G7OGB"
  for ds in $DATASETS; do
    echo "  - Ingesting dataset: $ds"
    PYTHONPATH="$REPO_ROOT" "$PYTHON_BIN" -m scheduler.run_scheduler_loop \
      --mongo-uri "$HOST_MONGO_URI" \
      --db-name "$DB_NAME" \
      --dataset-ids "$ds" \
      --once >/dev/null 2>&1 || true
  done

  # Stop mock service
  kill "$MOCK_PID" 2>/dev/null || true
fi

echo "============================================"
echo "Database seeding completed successfully!"
echo "============================================"
