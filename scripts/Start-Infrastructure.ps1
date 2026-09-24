$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location $repoRoot
if (-not (Test-Path '.env')) { java scripts/SetupKeys.java }
if (Get-Command docker -ErrorAction SilentlyContinue) {
  docker compose -p siga-iam up -d --wait postgres redis rabbitmq
} else {
  New-Item -ItemType Directory -Force '.local' | Out-Null
  $running = $false
  if (Test-Path '.local/wsl-keeper.pid') {
    $keeperId = [int](Get-Content '.local/wsl-keeper.pid')
    $running = $null -ne (Get-Process -Id $keeperId -ErrorAction SilentlyContinue)
  }
  if (-not $running) {
    # WSL systemd services alone do not keep the distribution alive.
    $keeper = Start-Process wsl.exe -ArgumentList @('-d','Ubuntu','-u','root','--','sleep','infinity') -WindowStyle Hidden -PassThru
    $keeper.Id | Set-Content '.local/wsl-keeper.pid'
  }
  wsl -d Ubuntu -u root --cd $repoRoot -- docker compose -p siga-iam up -d --wait postgres redis rabbitmq
}
if ($LASTEXITCODE -ne 0) { throw 'Could not start SIGA infrastructure' }
