# SafePath AI — PowerShell run script (Windows)
Set-Location $PSScriptRoot

if (-not (Test-Path out)) { New-Item -ItemType Directory -Path out | Out-Null }
if (-not (Test-Path lib\mysql-connector-j.jar)) {
  Write-Error "Missing lib\mysql-connector-j.jar"
  exit 1
}

Write-Host "=== Compiling SafePath ==="
javac -cp 'lib/*' -d out -sourcepath src src/server/Server.java
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$conn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
  Write-Host "Stopping existing server on port 8080..."
  Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
  Start-Sleep -Seconds 1
}

Write-Host "=== Starting http://localhost:8080/ ==="
java -cp 'out;lib/*' server.Server
