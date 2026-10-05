# Deploy the store web app to Vercel production.
# Reads VERCEL_TOKEN from web/.env.local (never printed).
$ErrorActionPreference = "Stop"
$webRoot = Split-Path -Parent $PSScriptRoot
Set-Location $webRoot

$line = Get-Content ".env.local" | Where-Object { $_ -match '^VERCEL_TOKEN=' } | Select-Object -First 1
if (-not $line) { throw "VERCEL_TOKEN is not set in web/.env.local" }
$token = $line.Substring("VERCEL_TOKEN=".Length).Trim()

npx --yes vercel@latest deploy --prod --yes --token $token
if ($LASTEXITCODE -ne 0) { throw "vercel deploy failed (exit $LASTEXITCODE)" }
Write-Host "deploy finished"
