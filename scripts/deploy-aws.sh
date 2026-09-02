#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/docker/docker-compose.aws.yml"
ENV_FILE="$REPO_ROOT/.env.aws"
STARTUP_TIMEOUT_SECONDS="${STARTUP_TIMEOUT_SECONDS:-180}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose plugin is required" >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for post-deploy health checks" >&2
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE. Copy .env.aws.example to .env.aws and update values." >&2
  exit 1
fi

required_dockerfiles=(
  "$REPO_ROOT/services/api-gateway/Dockerfile"
  "$REPO_ROOT/services/auth-service/Dockerfile"
  "$REPO_ROOT/services/mdm-service/Dockerfile"
  "$REPO_ROOT/services/iiot-service/Dockerfile"
  "$REPO_ROOT/services/license-service/Dockerfile"
  "$REPO_ROOT/services/audit-service/Dockerfile"
)

required_artifact_patterns=(
  "$REPO_ROOT/services/api-gateway/target/*.jar"
  "$REPO_ROOT/services/auth-service/target/*.jar"
  "$REPO_ROOT/services/mdm-service/target/*.jar"
  "$REPO_ROOT/services/iiot-service/target/*.jar"
  "$REPO_ROOT/services/license-service/target/license-service-*.jar"
  "$REPO_ROOT/services/audit-service/target/audit-service-*.jar"
)

for dockerfile in "${required_dockerfiles[@]}"; do
  if [ ! -f "$dockerfile" ]; then
    echo "Missing Dockerfile: $dockerfile" >&2
    echo "Ensure Dockerfiles are committed to git and available on this host." >&2
    exit 1
  fi

  if grep -Eq '^FROM[[:space:]]+openjdk:21-jdk-slim([[:space:]]|$)' "$dockerfile"; then
    echo "Invalid base image detected in $dockerfile: openjdk:21-jdk-slim" >&2
    echo "Use a valid Java 21 image such as eclipse-temurin:21-jdk-jammy." >&2
    exit 1
  fi
done

FORCE_BUILD="${FORCE_BUILD:-true}"
missing_artifacts=0
for artifact_pattern in "${required_artifact_patterns[@]}"; do
  if ! compgen -G "$artifact_pattern" >/dev/null; then
    missing_artifacts=1
    break
  fi
done

if [ "$missing_artifacts" -eq 1 ] || [ "$FORCE_BUILD" = "true" ]; then
  echo "Building fresh Maven artifacts before docker compose..."

  if ! command -v id >/dev/null 2>&1; then
    echo "Unable to resolve current user id. Install coreutils (id) and retry." >&2
    exit 1
  fi

  mkdir -p "$HOME/.m2"
  docker run --rm \
    --user "$(id -u):$(id -g)" \
    -v "$REPO_ROOT:/workspace" \
    -v "$HOME/.m2:/var/maven/.m2" \
    -w /workspace \
    -e MAVEN_CONFIG=/var/maven/.m2 \
    maven:3.9.9-eclipse-temurin-21 \
    mvn -DskipTests clean package

  for artifact_pattern in "${required_artifact_patterns[@]}"; do
    if ! compgen -G "$artifact_pattern" >/dev/null; then
      echo "Missing expected artifact after Maven build: $artifact_pattern" >&2
      exit 1
    fi
  done
fi

required_keys=(
  JWT_SECRET
  MONGODB_URI
  REDIS_HOST
  REDIS_PORT
  KAFKA_BOOTSTRAP_SERVERS
  CORS_ALLOWED_ORIGINS
)

for key in "${required_keys[@]}"; do
  value="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d'=' -f2- || true)"
  if [ -z "$value" ]; then
    echo "Missing required key in .env.aws: $key" >&2
    exit 1
  fi

  if [[ "$value" == REPLACE_* ]]; then
    echo "Placeholder value detected for $key. Update .env.aws before deployment." >&2
    exit 1
  fi
done

jwt_secret="$(grep -E '^JWT_SECRET=' "$ENV_FILE" | tail -n 1 | cut -d'=' -f2- || true)"
if [ "${#jwt_secret}" -lt 32 ]; then
  echo "JWT_SECRET must be at least 32 characters." >&2
  exit 1
fi

echo "Validating docker compose configuration..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config >/dev/null

echo "Building Adavis Platform services sequentially (AWS profile)..."
services_to_build=(
  auth-service
  mdm-service
  iiot-service
  license-service
  audit-service
  api-gateway
)

for service in "${services_to_build[@]}"; do
  echo "Building service: $service"
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build "$service"
done

echo "Starting Adavis Platform services..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --remove-orphans

echo "Waiting for API gateway health..."
deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
until curl -fsS http://localhost/actuator/health >/dev/null 2>&1; do
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "Gateway did not become healthy within ${STARTUP_TIMEOUT_SECONDS}s." >&2
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
    exit 1
  fi
  sleep 3
done

echo "Deployment is healthy. Use scripts/status-aws.sh to inspect details."
