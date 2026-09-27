param([ValidatePattern('^[a-z][a-z0-9-]{0,30}$')][string]$Instance = 'team')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$pidPath = Join-Path $root ".local/$Instance/identity.pid"
if (-not (Test-Path $pidPath)) { Write-Host 'No background PID recorded.'; return }
$identityProcessId = [int](Get-Content $pidPath)
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$identityProcessId"
if (-not $process) { Write-Host 'Identity already stopped.'; return }
$jarPath = Join-Path $root ".local/$Instance/identity.jar"
if (Test-Path -LiteralPath $jarPath) {
    $jar = [IO.Path]::GetFullPath((Get-Content -LiteralPath $jarPath -Raw).Trim())
    $runtimeDirectory = [IO.Path]::GetFullPath((Join-Path $root ".local/$Instance/runtime"))
    if (-not $jar.StartsWith($runtimeDirectory + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Recorded runtime JAR is outside this instance; no process was stopped.'
    }
} else {
    # Compatibility with instances started before runtime artifacts were isolated.
    $jar = Join-Path $root ".local/$Instance/target/siga-identity-service-0.0.1-SNAPSHOT.jar"
}
if ($process.Name -ne 'java.exe' -or -not $process.CommandLine.Contains($jar)) {
    throw 'Recorded PID is not the expected instance JAR; no process was stopped.'
}
Stop-Process -Id $identityProcessId
Write-Host "Stopped Identity instance $Instance; data and credentials preserved."
