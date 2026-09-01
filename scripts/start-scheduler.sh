#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
STATE_DIR="$SCRIPT_DIR/.state"

mkdir -p "$LOG_DIR" "$STATE_DIR"

SCHEDULER_PID_FILE="$STATE_DIR/scheduler.pid"
MOCK_PID_FILE="$STATE_DIR/mock-data-service.pid"
SCHEDULER_LOG="$LOG_DIR/scheduler.log"
MOCK_LOG="$LOG_DIR/mock-data-service.log"

# Default configuration
INTERVAL_SECONDS="${INTERVAL_SECONDS:-600}" # 10 minutes
MONGO_URI="${MONGO_URI:-mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin}"
DB_NAME="${DB_NAME:-adavis_platform}"
MOCK_PORT="${MOCK_PORT:-8000}"
FOREGROUND=0

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    -i|--interval)
      INTERVAL_SECONDS="$2"
      shift 2
      ;;
    --interval=*)
      INTERVAL_SECONDS="${1#*=}"
      shift
      ;;
    -u|--mongo-uri)
      MONGO_URI="$2"
      shift 2
      ;;
    --mongo-uri=*)
      MONGO_URI="${1#*=}"
      shift
      ;;
    -d|--db-name)
      DB_NAME="$2"
      shift 2
      ;;
    --db-name=*)
      DB_NAME="${1#*=}"
      shift
      ;;
    -f|--foreground)
      FOREGROUND=1
      shift
      ;;
    -h|--help)
      echo "Usage: $0 [options]"
      echo ""
      echo "Options:"
      echo "  -i, --interval SECONDS   Interval between ingestion cycles in seconds (default: 600 = 10m)"
      echo "  -u, --mongo-uri URI      MongoDB connection string"
      echo "  -d, --db-name NAME       Database name (default: adavis_platform)"
      echo "  -f, --foreground         Run scheduler in foreground instead of daemon"
      echo "  -h, --help               Show this help message"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

# Resolve Python environment
PYTHON_BIN="python3"
if [[ -f "$REPO_ROOT/.venv/bin/python3" ]]; then
  PYTHON_BIN="$REPO_ROOT/.venv/bin/python3"
elif command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1; then
  PYTHON_BIN="python"
else
  echo "Error: Python 3 is required but was not found." >&2
  exit 1
fi

# 1. Start Mock Data Service if not already running on port 8000
is_mock_running() {
  if curl -s -m 2 "http://localhost:${MOCK_PORT}/fwxapi/rest/v1/Dataset/BATCHDETAILS?pointname=db:G5RMG.BATCHDETAILS" >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

if is_mock_running; then
  echo "Mock data service is already running on port $MOCK_PORT."
else
  echo "Starting mock data service on port $MOCK_PORT..."
  setsid "$PYTHON_BIN" "$REPO_ROOT/data_service_layer/mock_data_service.py" </dev/null >> "$MOCK_LOG" 2>&1 &
  MOCK_PID=$!
  disown "$MOCK_PID" 2>/dev/null || true
  echo "$MOCK_PID" > "$MOCK_PID_FILE"

  # Wait for mock service to respond
  for i in {1..15}; do
    if is_mock_running; then
      echo "Mock data service is ready (PID: $MOCK_PID)."
      break
    fi
    sleep 1
  done
fi

# 2. Check if scheduler is already running
if [[ -f "$SCHEDULER_PID_FILE" ]]; then
  EXISTING_PID=$(cat "$SCHEDULER_PID_FILE" 2>/dev/null || true)
  if [[ -n "$EXISTING_PID" ]] && kill -0 "$EXISTING_PID" 2>/dev/null; then
    echo "Scheduler loop is already running (PID: $EXISTING_PID)."
    echo "To stop it, run: ./stop-scheduler.sh"
    exit 0
  fi
  rm -f "$SCHEDULER_PID_FILE"
fi

echo "Starting scheduler ingestion loop..."
echo "  - Ingestion Interval: ${INTERVAL_SECONDS}s ($((INTERVAL_SECONDS / 60)) min)"
echo "  - Database:           $DB_NAME"
echo "  - Log File:           $SCHEDULER_LOG"

export PYTHONPATH="$REPO_ROOT:${PYTHONPATH:-}"

if [[ "$FOREGROUND" -eq 1 ]]; then
  echo "$$" > "$SCHEDULER_PID_FILE"
  trap 'rm -f "$SCHEDULER_PID_FILE"; exit 0' SIGINT SIGTERM EXIT
  exec "$PYTHON_BIN" -m scheduler.run_scheduler_loop \
    --mongo-uri "$MONGO_URI" \
    --db-name "$DB_NAME" \
    --interval-seconds "$INTERVAL_SECONDS"
else
  setsid "$PYTHON_BIN" -m scheduler.run_scheduler_loop \
    --mongo-uri "$MONGO_URI" \
    --db-name "$DB_NAME" \
    --interval-seconds "$INTERVAL_SECONDS" </dev/null >> "$SCHEDULER_LOG" 2>&1 &
  SCHEDULER_PID=$!
  disown "$SCHEDULER_PID" 2>/dev/null || true
  echo "$SCHEDULER_PID" > "$SCHEDULER_PID_FILE"

  echo "============================================"
  echo "Scheduler loop successfully started!"
  echo "PID:        $SCHEDULER_PID"
  echo "Interval:   Every $((INTERVAL_SECONDS / 60)) minutes (${INTERVAL_SECONDS}s)"
  echo "Log:        $SCHEDULER_LOG"
  echo "To stop:    ./stop-scheduler.sh"
  echo "============================================"
fi
