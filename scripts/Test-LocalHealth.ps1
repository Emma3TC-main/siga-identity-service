param([string]$Instance = 'team')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Import-LocalEnvironment.ps1" -Instance $Instance
$management = "http://127.0.0.1:$env:MANAGEMENT_PORT"
foreach ($path in @('/actuator/health','/actuator/health/liveness','/actuator/health/readiness')) {
    $response = Invoke-RestMethod "$management$path"
    if ($response.status -ne 'UP' -or $response.components -or $response.details) { throw "Unexpected health response at $path" }
    Write-Host "$path UP; no components/details exposed"
}
$metrics = Invoke-WebRequest -UseBasicParsing "$management/actuator/prometheus"
if ($metrics.StatusCode -ne 200 -or $metrics.Content -notmatch 'jvm_memory_used_bytes') { throw 'Prometheus scrape failed.' }
Write-Host 'Prometheus scrape OK on management loopback port.'
foreach ($url in @("http://127.0.0.1:$env:PORT/actuator/prometheus", "$management/actuator/env")) {
    $status = 0
    try { $status = (Invoke-WebRequest -UseBasicParsing $url).StatusCode }
    catch { if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode } else { throw } }
    if ($status -notin @(401,403,404)) { throw "Restricted endpoint unexpectedly accessible: $url ($status)" }
    Write-Host "Restricted endpoint denied ($status): $url"
}
