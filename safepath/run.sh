#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ -x ./stop-server.sh ]]; then
  echo "=== Stopping old server (unlocks JAR for rebuild) ==="
  ./stop-server.sh
fi

if command -v mvn >/dev/null 2>&1; then
  MVN=mvn
else
  MVN=./mvnw
  chmod +x ./mvnw 2>/dev/null || true
fi

echo "=== Building SafePath (Maven) ==="
$MVN -q clean package -DskipTests

echo "=== Starting http://localhost:8080/ ==="
exec java -jar target/safepath-1.0.0.jar
