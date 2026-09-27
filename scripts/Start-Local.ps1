param([string]$Instance, [switch]$Background)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $Instance -and -not (Test-Path '.env')) {
    java scripts/SetupKeys.java
    if ($LASTEXITCODE -ne 0) { throw 'Local key setup failed.' }
}
. "$PSScriptRoot/Import-LocalEnvironment.ps1" -Instance $Instance
$ErrorActionPreference = 'Continue'
$buildDirectory = if ($Instance) { ".local/$Instance/target" } else { '.local/target' }
if ($Background) {
    if (-not $Instance) { throw 'Background mode requires an isolated team instance.' }
    $pidPath = Join-Path $repoRoot ".local/$Instance/identity.pid"
    if (Test-Path $pidPath) {
        $existing = Get-Process -Id ([int](Get-Content $pidPath)) -ErrorAction SilentlyContinue
        if ($existing) { throw 'Recorded PID is still running; inspect/stop this instance first.' }
    }
    foreach ($port in @([int]$env:PORT, [int]$env:MANAGEMENT_PORT)) {
        $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $port)
        try { $listener.Start() } catch { throw "Port $port is occupied; do not stop another instance." }
        finally { $listener.Stop() }
    }
    ./mvnw.cmd -B "-Dsiga.build.directory=$buildDirectory" '-DskipTests' package
    if ($LASTEXITCODE -ne 0) { throw 'Local build failed.' }
    $builtJar = Join-Path $repoRoot "$buildDirectory/siga-identity-service-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $builtJar)) { throw 'Packaged Identity JAR is missing.' }
    $runtimeDirectory = Join-Path $repoRoot ".local/$Instance/runtime"
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
    $artifactHash = (Get-FileHash -LiteralPath $builtJar -Algorithm SHA256).Hash.ToLowerInvariant()
    $jar = Join-Path $runtimeDirectory "siga-identity-service-$artifactHash.jar"
    if (Test-Path -LiteralPath $jar) {
        $runtimeHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($runtimeHash -ne $artifactHash) { throw 'Runtime JAR hash mismatch; preserve it for diagnosis.' }
    } else {
        Copy-Item -LiteralPath $builtJar -Destination $jar
    }
    $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar', ('"{0}"' -f $jar)) -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput ".local/$Instance/identity.stdout.log" -RedirectStandardError ".local/$Instance/identity.stderr.log"
    $process.Id | Set-Content $pidPath
    $jar | Set-Content (Join-Path $repoRoot ".local/$Instance/identity.jar")
    Write-Host "Identity PID=$($process.Id); logs in .local/$Instance/identity.*.log"
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        $process.Refresh()
        if ($process.HasExited) { throw 'Identity exited; inspect its local logs.' }
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$env:MANAGEMENT_PORT/actuator/health/readiness" -TimeoutSec 2
            if ($health.status -eq 'UP') { Write-Host 'Identity readiness UP.'; return }
        } catch { }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Readiness timeout. Process/logs preserved for diagnosis.'
}
./mvnw.cmd "-Dsiga.build.directory=$buildDirectory" spring-boot:run
$mavenExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
exit $mavenExit
