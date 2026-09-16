#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${CODESPACE_NAME:-}" ]]; then
  echo "Not running in a Codespace. Use 'docker compose up' locally instead."
  exit 1
fi

BASE_URL="https://${CODESPACE_NAME}-8080.${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN}"

export TINYURL_APP_BASE_URL="$BASE_URL"
export TINYURL_STORAGE_PROXY_PUBLIC_URL="${BASE_URL}/storage"
export TINYURL_STORAGE_ENDPOINT="http://localhost:9000"
export TINYURL_STORAGE_PUBLIC_ENDPOINT="http://localhost:9000"
export TINYURL_STORAGE_BUCKET="tinyurl-files"
export TINYURL_STORAGE_ACCESS_KEY="minioadmin"
export TINYURL_STORAGE_SECRET_KEY="minioadmin"

echo "Starting dependencies..."
docker compose up -d postgres redis minio

echo "Waiting for health checks..."
for i in $(seq 1 60); do
  unhealthy=$(docker compose ps --format '{{.Health}}' postgres redis minio | grep -cv healthy || true)
  [[ "$unhealthy" -eq 0 ]] && break
  sleep 2
done

echo
echo "=================================================="
echo "  Public URL: $BASE_URL"
echo "=================================================="
echo

./gradlew bootRun