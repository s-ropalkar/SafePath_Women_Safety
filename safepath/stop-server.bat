@echo off
REM Stop SafePath so "mvn clean" can delete target\safepath-1.0.0.jar
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-server.ps1"
exit /b 0
