# Stop SafePath server so Maven can delete target/safepath-1.0.0.jar
$ErrorActionPreference = "SilentlyContinue"

Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like '*safepath*' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }

Start-Sleep -Seconds 2
