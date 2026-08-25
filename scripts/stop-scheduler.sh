#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_DIR="$SCRIPT_DIR/.state"
SCHEDULER_PID_FILE="$STATE_DIR/scheduler.pid"
MOCK_PID_FILE="$STATE_DIR/mock-data-service.pid"

STOP_MOCK="${1:-0}"
if [[ "${1:-}" == "-a" || "${1:-}" == "--all" || "${1:-}" == "-m" || "${1:-}" == "--with-mock" ]]; then
  STOP_MOCK=1
fi

stopped_any=0

# Stop scheduler loop
if [[ -f "$SCHEDULER_PID_FILE" ]]; then
  PID=$(cat "$SCHEDULER_PID_FILE" 2>/dev/null || true)
  if [[ -n "$PID" ]] && kill -0 "$PID" 2>/dev/null; then
    echo "Stopping scheduler loop (PID: $PID)..."
    kill "$PID" 2>/dev/null || true
    stopped_any=1
  fi
  rm -f "$SCHEDULER_PID_FILE"
fi

# Fallback check for any lingering run_scheduler_loop.py process
pkill -f "scheduler.run_scheduler_loop" 2>/dev/null && stopped_any=1 || true

# Stop mock data service if requested or if default
if [[ "$STOP_MOCK" == "1" ]]; then
  if [[ -f "$MOCK_PID_FILE" ]]; then
    M_PID=$(cat "$MOCK_PID_FILE" 2>/dev/null || true)
    if [[ -n "$M_PID" ]] && kill -0 "$M_PID" 2>/dev/null; then
      echo "Stopping mock data service (PID: $M_PID)..."
      kill "$M_PID" 2>/dev/null || true
      stopped_any=1
    fi
    rm -f "$MOCK_PID_FILE"
  fi
  pkill -f "mock_data_service.py" 2>/dev/null || true
fi

if [[ "$stopped_any" -eq 1 ]]; then
  echo "Scheduler loop stopped successfully."
else
  echo "Scheduler loop was not running."
fi
