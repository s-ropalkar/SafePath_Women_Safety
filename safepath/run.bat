@echo off
cd /d "%~dp0"
title SafePath AI (Maven)

echo === SafePath AI — Maven build ===
where mvn >nul 2>&1
if errorlevel 1 (
  set "MVN=%~dp0mvnw.cmd"
) else (
  set "MVN=mvn"
)

call "%MVN%" -q clean package
if errorlevel 1 (
  echo BUILD FAILED
  exit /b 1
)

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080.*LISTENING"') do (
  echo Stopping existing server on port 8080...
  taskkill /PID %%a /F >nul 2>&1
  timeout /t 1 /nobreak >nul
)

echo.
echo Starting server at http://localhost:8080
echo Keep this window open while using SafePath.
echo.
start "" "http://localhost:8080/"
java -jar target\safepath-1.0.0.jar
pause
