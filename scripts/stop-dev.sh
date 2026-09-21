#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/docker/docker-compose.local.yml"
STATE_FILE="$SCRIPT_DIR/.state/services.pid"

echo "Stopping running Spring Boot services..."
if [[ -f "$STATE_FILE" ]]; then
  while IFS=: read -r name pid; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "Stopping $name (PID: $pid)..."
      kill "$pid" 2>/dev/null || true
    fi
  done < "$STATE_FILE"
  rm -f "$STATE_FILE"
fi

# Also kill any leftover service processes on standard ports
for port in 9080 9081 9082 9083 9084 9085; do
  fuser -k -n tcp "$port" 2>/dev/null || true
done

echo "Spring Boot services stopped."

if [[ "${1:-}" == "--all" || "${1:-}" == "-a" ]]; then
  echo "Stopping Docker/Podman infrastructure containers..."
  if docker compose version >/dev/null 2>&1; then
    docker compose -f "$COMPOSE_FILE" down
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose -f "$COMPOSE_FILE" down
  elif podman compose version >/dev/null 2>&1; then
    podman compose -f "$COMPOSE_FILE" down
  elif command -v podman-compose >/dev/null 2>&1; then
    podman-compose -f "$COMPOSE_FILE" down
  else
    echo "Missing a working Docker Compose or Podman Compose runtime." >&2
    exit 1
  fi
  echo "Containers stopped."
fi
