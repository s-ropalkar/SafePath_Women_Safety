# SafePath AI — PowerShell run script (Windows)
Set-Location $PSScriptRoot

$mvn = if (Get-Command mvn -ErrorAction SilentlyContinue) { "mvn" } else { Join-Path $PSScriptRoot "mvnw.cmd" }

Write-Host "=== Building SafePath (Maven) ==="
& $mvn -q clean package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$conn = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($conn) {
  Write-Host "Stopping existing server on port 8080..."
  Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
  Start-Sleep -Seconds 1
}

Write-Host "=== Starting http://localhost:8080/ ==="
java -jar target/safepath-1.0.0.jar
