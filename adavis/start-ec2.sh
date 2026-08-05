#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

export PORT="${PORT:-3000}"
export HOSTNAME="${HOSTNAME:-0.0.0.0}"

# Standalone runtime needs static/public assets under .next/standalone.
mkdir -p .next/standalone/.next

if [ -d .next/static ]; then
  rm -rf .next/standalone/.next/static
  cp -R .next/static .next/standalone/.next/static
fi

if [ -d public ]; then
  rm -rf .next/standalone/public
  cp -R public .next/standalone/public
fi

if [ -f .env.production ]; then
  set -a
  . ./.env.production
  set +a
fi

exec node .next/standalone/server.js
