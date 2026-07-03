@echo off
cd /d "%~dp0"
echo === SafePath AI Build ^& Run ===
if not exist out mkdir out
if not exist lib\mysql-connector-j.jar (
  echo ERROR: Missing lib\mysql-connector-j.jar
  exit /b 1
)
javac -cp "lib/*" -d out -sourcepath src src\server\Server.java
if errorlevel 1 (
  echo BUILD FAILED
  exit /b 1
)
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080.*LISTENING"') do (
  echo Server already running on http://localhost:8080/
  start "" "http://localhost:8080/"
  pause
  exit /b 0
)
echo.
echo Starting server at http://localhost:8080
echo Keep this window open while using SafePath.
echo.
start "" "http://localhost:8080/"
java -cp "out;lib/*" server.Server
pause
