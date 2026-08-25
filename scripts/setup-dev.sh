#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/docker/docker-compose.local.yml"
LOG_DIR="$SCRIPT_DIR/logs"
STATE_DIR="$SCRIPT_DIR/.state"
STATE_FILE="$STATE_DIR/services.pid"

mkdir -p "$LOG_DIR" "$STATE_DIR"

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/jvm/jdk-21}"
export PATH="$JAVA_HOME/bin:$HOME/.local/bin:$PATH"
export DOCKER_HOST="${DOCKER_HOST:-unix:///run/user/1000/podman/podman.sock}"

BUILD_FIRST="${BUILD_FIRST:-0}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

wait_for_service() {
  local name="$1"
  local url="$2"
  local timeout="${3:-180}"
  local deadline=$((SECONDS + timeout))
  echo "Waiting for $name to be UP at $url..."
  while (( SECONDS < deadline )); do
    if curl -fsS "$url" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "Service $name is UP."
      return 0
    fi
    sleep 2
  done
  echo "Service $name failed health check. Last 50 log lines:" >&2
  tail -n 50 "$LOG_DIR/$name.log" >&2 || true
  return 1
}

launch_service() {
  local name="$1"
  local path="$2"
  local jar_file="$REPO_ROOT/$path/target/$name-1.0.0-SNAPSHOT.jar"

  echo "Launching $name in background..."
  if [[ -f "$jar_file" && "$BUILD_FIRST" != "1" ]]; then
    (cd "$REPO_ROOT/$path" && exec java -jar "target/$name-1.0.0-SNAPSHOT.jar") > "$LOG_DIR/$name.log" 2>&1 &
  else
    (cd "$REPO_ROOT/$path" && exec mvn spring-boot:run -DskipTests) > "$LOG_DIR/$name.log" 2>&1 &
  fi
  local pid=$!
  echo "$name:$pid" >> "$STATE_FILE"
}

require_cmd java
require_cmd curl

echo "Ensuring infrastructure containers are running..."
if command -v docker-compose >/dev/null 2>&1; then
  docker-compose -f "$COMPOSE_FILE" up -d
elif command -v podman-compose >/dev/null 2>&1; then
  podman-compose -f "$COMPOSE_FILE" up -d
else
  podman compose -f "$COMPOSE_FILE" up -d
fi

if [[ "$BUILD_FIRST" == "1" ]]; then
  echo "Building all modules..."
  mvn -f "$REPO_ROOT/pom.xml" clean install -DskipTests
fi

"$SCRIPT_DIR/seed-data.sh"

: > "$STATE_FILE"
launch_service auth-service services/auth-service
launch_service license-service services/license-service
launch_service audit-service services/audit-service
launch_service mdm-service services/mdm-service
launch_service iiot-service services/iiot-service
launch_service api-gateway services/api-gateway

wait_for_service auth-service http://localhost:9081/actuator/health
wait_for_service license-service http://localhost:8082/actuator/health
wait_for_service audit-service http://localhost:8084/actuator/health
wait_for_service mdm-service http://localhost:9083/actuator/health
wait_for_service iiot-service http://localhost:9085/actuator/health
wait_for_service api-gateway http://localhost:9080/actuator/health

echo "============================================"
echo "Backend microservices are running and healthy!"
echo "API Gateway:     http://localhost:9080"
echo "Auth Service:    http://localhost:9081"
echo "License Service: http://localhost:8082"
echo "MDM Service:     http://localhost:9083"
echo "Audit Service:   http://localhost:8084"
echo "IIOT Service:    http://localhost:9085"
echo "Logs are located in: $LOG_DIR"
echo "============================================"

# Trap signals for graceful shutdown
cleanup() {
  echo "Shutting down services..."
  if [[ -f "$STATE_FILE" ]]; then
    while IFS=: read -r name pid; do
      if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null || true
      fi
    done < "$STATE_FILE"
    rm -f "$STATE_FILE"
  fi
}
trap cleanup SIGINT SIGTERM EXIT

wait