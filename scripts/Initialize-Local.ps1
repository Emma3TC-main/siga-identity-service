param(
    [ValidatePattern('^[a-z][a-z0-9-]{0,30}$')][string]$Instance = 'team',
    [ValidateRange(0,40000)][int]$PortOffset = 0
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$javaVersion = (& java --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '(openjdk|java) 21[.\s]') { throw 'JDK 21 is required on PATH.' }
& "$repoRoot/../siga-infrastructure/scripts/Team-Local.ps1" -Action Init -Instance $Instance -PortOffset $PortOffset
Push-Location $repoRoot
try {
    java scripts/SetupKeys.java ".local/$Instance"
    if ($LASTEXITCODE -ne 0) { throw 'Key setup failed; existing keys were not replaced.' }
} finally { Pop-Location }
