#!/bin/bash
echo "=== SafePath AI Build & Run ==="
mkdir -p out
if [ ! -f lib/mysql-connector-j.jar ]; then
  echo "ERROR: Missing lib/mysql-connector-j.jar"
  exit 1
fi
javac -cp "lib/*" -d out -sourcepath src src/server/Server.java 2>&1
if [ $? -ne 0 ]; then
  echo "BUILD FAILED"
  exit 1
fi
echo "Build successful!"
java -cp "out:lib/*" server.Server
