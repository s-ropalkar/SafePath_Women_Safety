@echo off
cd /d "%~dp0"
title SafePath AI (Maven)

echo === Stopping old server (unlocks JAR for rebuild) ===
call "%~dp0stop-server.bat"

echo === SafePath AI — Maven build ===
where mvn >nul 2>&1
if errorlevel 1 (
  set "MVN=%~dp0mvnw.cmd"
) else (
  set "MVN=mvn"
)

call "%MVN%" -q clean package
if errorlevel 1 (
  echo BUILD FAILED — close any running SafePath server window and retry.
  pause
  exit /b 1
)

echo.
echo Starting server at http://localhost:8080
echo Keep this window open while using SafePath.
echo.
start "" "http://localhost:8080/"
java -jar target\safepath-1.0.0.jar
pause
