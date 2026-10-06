# 本番サーバーに Web を反映する。
# サーバーは GitHub の main を取り込み、依存を入れ直してビルドし、PM2 のプロセスを再起動する。
# サーバーは GitHub から取り込むので、先に変更を main に push しておくこと。
# 接続先は web/.env.local(または環境変数)の DEPLOY_SSH(例: user@host)と
# DEPLOY_DIR(サーバー上のリポジトリの場所)で指定する。どちらもリポジトリには入れない。
$ErrorActionPreference = "Stop"
$webRoot = Split-Path -Parent $PSScriptRoot
Set-Location $webRoot

function Get-DeploySetting([string]$name) {
  $value = [Environment]::GetEnvironmentVariable($name)
  if (-not $value -and (Test-Path ".env.local")) {
    $line = Get-Content ".env.local" | Where-Object { $_ -match "^$name=" } | Select-Object -First 1
    if ($line) { $value = $line.Substring($name.Length + 1).Trim().Trim('"') }
  }
  if (-not $value) { throw "$name が web/.env.local にも環境変数にも設定されていません" }
  return $value
}

$sshTarget = Get-DeploySetting "DEPLOY_SSH"
$repoDir = Get-DeploySetting "DEPLOY_DIR"
$processName = "ttech-store-web"

git fetch -q origin main
$unpushed = git log --oneline origin/main..HEAD
if ($unpushed) { throw "main に push していないコミットがあります。先に push してください" }

# ビルド中(数十秒)は、古いビルドを読んでいるページが一時的にエラーになることがある
ssh -o BatchMode=yes $sshTarget "set -e; cd '$repoDir/web'; git pull --ff-only; npm ci --no-audit --no-fund; npm run build; pm2 restart $processName --update-env"
if ($LASTEXITCODE -ne 0) { throw "deploy failed (exit $LASTEXITCODE)" }
Write-Host "deploy finished"
