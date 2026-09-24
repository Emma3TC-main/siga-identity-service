$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
Get-Content '.env' | ForEach-Object {
  if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
}
# A dedicated database is mandatory; tests refuse any database without the _test suffix.
$env:DB_URL='jdbc:postgresql://localhost:55432/siga_identity_test'
./mvnw.cmd -B verify
exit $LASTEXITCODE
