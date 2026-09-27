param([string]$Instance, [switch]$WithMinio)
$ErrorActionPreference = 'Stop'
$infra = Join-Path (Split-Path $PSScriptRoot -Parent) '../siga-infrastructure'
if ($Instance) {
    & "$infra/scripts/Team-Local.ps1" -Action Start -Instance $Instance -WithMinio:$WithMinio
    return
}
& (Join-Path $infra 'scripts/Setup-Local.ps1')
Push-Location $infra
try {
    docker compose --env-file compose/.env.local -f compose/compose.local.yml config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Invalid local Compose configuration.' }
    docker compose --env-file compose/.env.local -f compose/compose.local.yml up -d --no-recreate redis rabbitmq minio
    if ($LASTEXITCODE -ne 0) { throw 'Could not start existing local services.' }
    docker compose --env-file compose/.env.local -f compose/compose.local.yml up -d --wait postgres
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL is not healthy.' }
    docker compose --env-file compose/.env.local -f compose/compose.local.yml exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/10-identity-role.sql'
    if ($LASTEXITCODE -ne 0) { throw 'Could not provision Identity login. Diagnose the volume; do not reset it.' }
    'SELECT current_user, current_database();' | docker compose --env-file compose/.env.local -f compose/compose.local.yml exec -T postgres sh -c 'PGPASSWORD=$SIGA_IAM_PASSWORD exec psql -h 127.0.0.1 -U siga_iam -d siga -v ON_ERROR_STOP=1'
    if ($LASTEXITCODE -ne 0) { throw 'Existing IAM credentials differ from .env.local. Diagnose; do not reset or rotate automatically.' }
} finally { Pop-Location }
