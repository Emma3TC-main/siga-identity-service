param([string]$Instance = 'team', [string]$Tests)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
. "$PSScriptRoot/Import-LocalEnvironment.ps1" -Instance $Instance -ForTests
# Dedicated test database: the integration suite truncates its IAM tables and Redis DB 1.
# Windows PowerShell 5.1 treats redirected JVM warnings as errors with Stop.
# Maven's exit code, not stderr warnings, determines the result.
$ErrorActionPreference = 'Continue'
$mavenArgs = @('-B',"-Dsiga.build.directory=.local/$Instance/test-build",'verify')
if ($Tests) { $mavenArgs += "-Dtest=$Tests" }
./mvnw.cmd @mavenArgs
$mavenExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
exit $mavenExit
