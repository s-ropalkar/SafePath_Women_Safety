#!/usr/bin/env sh
# Stop SafePath so mvn clean can delete target/safepath-1.0.0.jar
pkill -f 'safepath-1.0.0.jar' 2>/dev/null || true
if command -v lsof >/dev/null 2>&1; then
  lsof -ti:8080 2>/dev/null | xargs kill -9 2>/dev/null || true
fi
sleep 2
exit 0
