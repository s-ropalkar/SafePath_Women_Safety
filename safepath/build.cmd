@echo off
cd /d "%~dp0"
call stop-server.bat
call mvnw.cmd clean package -DskipTests %*
exit /b %ERRORLEVEL%
