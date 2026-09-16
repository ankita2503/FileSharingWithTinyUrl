#!/usr/bin/env bash
set -euo pipefail

export MINIO_ROOT_USER="${TINYURL_STORAGE_ACCESS_KEY:-minioadmin}"
export MINIO_ROOT_PASSWORD="${TINYURL_STORAGE_SECRET_KEY:-minioadmin}"

echo "Starting MinIO..."
minio server /data/minio --address ":9000" --console-address ":9001" &
MINIO_PID=$!

# If MinIO dies, the container should die too rather than serve a half-working app.
trap 'kill -TERM $MINIO_PID 2>/dev/null || true' EXIT

echo "Waiting for MinIO..."
for i in $(seq 1 30); do
  if curl -fsS http://localhost:9000/minio/health/live > /dev/null 2>&1; then
    echo "MinIO is up."
    break
  fi
  sleep 1
done

echo "Starting application..."
exec java -jar /app/app.jar