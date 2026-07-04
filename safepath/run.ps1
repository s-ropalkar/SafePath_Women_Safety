# SafePath AI — PowerShell run script (Windows)
Set-Location $PSScriptRoot

$mvn = if (Get-Command mvn -ErrorAction SilentlyContinue) { "mvn" } else { Join-Path $PSScriptRoot "mvnw.cmd" }

Write-Host "=== Stopping old server (unlocks JAR for rebuild) ==="
& (Join-Path $PSScriptRoot "stop-server.ps1")

Write-Host "=== Building SafePath (Maven) ==="
& $mvn -q clean package
if ($LASTEXITCODE -ne 0) {
  Write-Host "BUILD FAILED — if JAR is locked, close any java -jar safepath window and retry."
  exit $LASTEXITCODE
}

Write-Host "=== Starting http://localhost:8080/ ==="
java -jar target/safepath-1.0.0.jar
