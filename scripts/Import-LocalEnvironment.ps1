param([string]$Instance, [switch]$ForTests)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
function Read-LocalEnv([string]$path) {
    if (-not (Test-Path $path)) { throw "Missing $path. Follow docs/LOCAL.md." }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $path) {
        if ($line -match '^([A-Z_][A-Z_0-9]*)=(.*)$') { $values[$matches[1]] = $matches[2] }
    }
    return $values
}
if ($Instance) {
    if ($Instance -notmatch '^[a-z][a-z0-9-]{0,30}$' -or $Instance -eq 'local') { throw 'Invalid isolated instance name.' }
    $identity = Read-LocalEnv (Join-Path $repoRoot ".local/$Instance/.env")
    $infra = Read-LocalEnv (Join-Path $repoRoot "../siga-infrastructure/compose/.env.$Instance")
    if ($infra['COMPOSE_PROJECT_NAME'] -ne "siga-$Instance") { throw 'Instance/project mismatch.' }
} else {
    if ($ForTests) { throw 'Use a team instance with a dedicated test login.' }
    $identity = Read-LocalEnv (Join-Path $repoRoot '.env')
    $infra = Read-LocalEnv (Join-Path $repoRoot '../siga-infrastructure/compose/.env.local')
}
foreach ($key in $identity.Keys) { [Environment]::SetEnvironmentVariable($key, $identity[$key], 'Process') }
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_URL = "jdbc:postgresql://127.0.0.1:$($infra['LOCAL_POSTGRES_PORT'])/siga"
$env:DB_USER = 'siga_iam'
$env:DB_PASSWORD = $infra['SIGA_IAM_PASSWORD']
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '6379'
$env:RABBITMQ_HOST = '127.0.0.1'
$env:RABBITMQ_PORT = '5672'
# Match the existing infrastructure broker; no new integration or credential rotation.
$env:RABBITMQ_USER = $infra['LOCAL_RABBITMQ_USER']
$env:RABBITMQ_PASSWORD = $infra['LOCAL_RABBITMQ_PASSWORD']
if ($Instance) {
    $env:REDIS_PORT = $infra['LOCAL_REDIS_PORT']
    $env:RABBITMQ_PORT = $infra['LOCAL_RABBITMQ_PORT']
    $env:RABBITMQ_USER = 'siga_mq'
    $env:PORT = $infra['LOCAL_IDENTITY_PORT']
    $env:MANAGEMENT_PORT = $infra['LOCAL_MANAGEMENT_PORT']
}
if ($ForTests) {
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$($infra['LOCAL_POSTGRES_PORT'])/siga_identity_local_test"
    $env:DB_USER = 'siga_iam_test'
    $env:DB_PASSWORD = $infra['SIGA_TEST_PASSWORD']
}
if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) { throw 'SIGA_IAM_PASSWORD is missing.' }
