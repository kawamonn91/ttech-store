# 運用の共通手順(本番に触れる操作)

各フェーズの手順書から参照する、本番の操作の決まった手順です。ここに書いたスクリプトは**フェーズT のステップ T-7 で作ります**。それより前(フェーズ0)は、フェーズ0の手順書に書いたコマンドだけを使います。

前提: [README.md](README.md) の「作業のルール」。特に「本番に触れる操作」の3つの約束(直前のバックアップ・合格条件・直後のスモークテスト)。

---

## 1. しくみ

- サーバーへの接続は `web/.env.prod.local` の `DEPLOY_SSH`(`ssh` の接続先)を使う。鍵での接続が設定済み(`ssh -o BatchMode=yes` で入れる)。
- サーバー上で動かす処理は、`web/scripts/ops/remote/*.sh` の短い bash スクリプトを `scp` で送って実行する(PowerShell から `ssh` に複雑な引用符を渡すと壊れやすいため)。
- 手元(PowerShell)からは、`web/scripts/ops/ops.psm1` の関数を使う:
  ```powershell
  Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
  ```

| 関数 | すること |
|---|---|
| `Get-OpsSetting NAME` | 環境変数 → `web/.env.prod.local` → `web/.env.local` の順に値を探す |
| `Backup-ProdDb LABEL` | 本番 DB の全体のダンプを取り、`C:\dev\backups\ttech-store\` に持ってくる |
| `Start-DbTunnel` / `Stop-DbTunnel` | 本番 DB への SSH トンネル(手元の `127.0.0.1:54329`)を開く・閉じる。フェーズ1 のステップ 1-8 以降で使える |
| `Invoke-ProdSqlFile PATH` | SQL ファイルを本番 DB で1つのトランザクションとして実行する |
| `Invoke-ProdMigration PATH` | マイグレーションを本番に適用し、`ops.applied_migrations` に記録する(適用済みなら何もしない) |
| `Set-ServerEnv NAME VALUE` | サーバーの Web の環境変数ファイルに1行を書く(前の版は `.bak.<日時>` として残る) |
| `Invoke-WebDeploy` | `web/scripts/deploy-web.ps1` を実行する(サーバーで pull → `npm ci` → build → `pm2 restart`) |
| `Set-WebProcess stop\|start` | サーバーの Web のプロセスを止める・動かす(DB を丸ごと戻すときだけ使う) |

---

## 2. スクリプトの中身(フェーズT の T-7 でこのとおりに作る)

### `.gitattributes`(リポジトリの直下。無ければ作る、あれば行を足す)
```
*.sh text eol=lf
```
シェルスクリプトは、Windows で取り出しても LF のままにする(CRLF だと bash が動かない)。

### `web/scripts/ops/remote/backup-db.sh`
```bash
#!/usr/bin/env bash
# usage: backup-db.sh <db-container> <db-superuser> <label>
# Dumps the whole database (custom format), keeps it under ~/ttech-backups, prints the path.
set -euo pipefail
container="$1"; superuser="$2"; label="$3"
ts="$(date -u +%Y%m%dT%H%M%SZ)"
dir="$HOME/ttech-backups"
mkdir -p "$dir"
file="$ts-$label.dump"
docker exec "$container" pg_dump -U "$superuser" -d postgres -Fc -f "/tmp/$file"
docker cp "$container:/tmp/$file" "$dir/$file"
docker exec "$container" rm -f "/tmp/$file"
docker exec -i "$container" pg_restore --list < "$dir/$file" > /dev/null
echo "$dir/$file"
```

### `web/scripts/ops/remote/psql-file.sh`
```bash
#!/usr/bin/env bash
# usage: psql-file.sh <db-container> <db-superuser> <sql-file-on-server>
# Runs the file in a single transaction. Any error rolls back everything.
set -euo pipefail
docker exec -i "$1" psql -U "$2" -d postgres -v ON_ERROR_STOP=1 --single-transaction < "$3"
```

### `web/scripts/ops/remote/psql-query.sh`
```bash
#!/usr/bin/env bash
# usage: psql-query.sh <db-container> <db-superuser>   (SQL is read from stdin)
# Prints rows as unaligned text without headers.
set -euo pipefail
sql="$(cat | tr -d '\r')"
docker exec -i "$1" psql -U "$2" -d postgres -v ON_ERROR_STOP=1 -tA -c "$sql"
```

### `web/scripts/ops/remote/set-env.sh`
```bash
#!/usr/bin/env bash
# usage: printf '%s' "$value" | set-env.sh <env-file> <NAME>
# Replaces (or adds) NAME=value. The value comes from stdin so it never appears in the process list.
set -euo pipefail
file="$1"; name="$2"
value="$(cat | tr -d '\r\n')"
touch "$file"
cp "$file" "$file.bak.$(date -u +%Y%m%dT%H%M%SZ)"
grep -v "^$name=" "$file" > "$file.tmp" || true
printf '%s=%s\n' "$name" "$value" >> "$file.tmp"
mv "$file.tmp" "$file"
chmod 600 "$file"
echo "set $name in $file"
```

### `web/scripts/ops/remote/web-ctl.sh`
```bash
#!/usr/bin/env bash
# usage: web-ctl.sh stop|start
set -euo pipefail
pm2 "$1" ttech-store-web
```

### `web/scripts/ops/remote/discover.sh`
```bash
#!/usr/bin/env bash
# usage: discover.sh <deploy-dir>
# Prints the server layout (names and paths only, never values).
set -uo pipefail
echo "== containers"
docker ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}'
db="$(docker ps --format '{{.Names}} {{.Image}}' | awk '$2 ~ /postgres/ {print $1}' | head -1)"
echo "== db_container=$db"
if [ -n "$db" ]; then
  echo "== compose_dir=$(docker inspect "$db" --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}')"
  echo "== db_version=$(docker exec "$db" psql -U postgres -d postgres -tAc 'show server_version' 2>&1 | head -1)"
fi
echo "== pm2 processes"
pm2 jlist 2>/dev/null | python3 -c 'import json,sys
for p in json.load(sys.stdin): print(p["name"], p["pm2_env"].get("pm_cwd"), p["pm2_env"].get("status"))' || true
echo "== web env files (names only)"
ls -la "$1"/web/.env* 2>/dev/null || echo "(none)"
for f in "$1"/web/.env*; do [ -e "$f" ] && echo "$f -> $(readlink -f "$f")"; done
echo "== env file candidates"
ls -la "$HOME"/.config/*/env 2>/dev/null || echo "(none)"
```

### `web/scripts/ops/ops.psm1`
```powershell
# 本番の運用操作(バックアップ・DB 接続・マイグレーション・デプロイ)をまとめたモジュール。
# 使い方: Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
# 手順は docs/supabase-removal/ops.md。値(鍵・接続先)は web/.env.prod.local から読み、表示しない。
$ErrorActionPreference = "Stop"
$script:WebRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$script:RemoteDir = "/tmp/ttech-ops"
$script:BackupDir = "C:\dev\backups\ttech-store"
$script:TunnelPidFile = Join-Path $env:TEMP "ttech-db-tunnel.pid"

function Get-OpsSetting {
  param([Parameter(Mandatory)][string]$Name, [switch]$Optional)
  $value = [Environment]::GetEnvironmentVariable($Name)
  foreach ($file in @(".env.prod.local", ".env.local")) {
    if ($value) { break }
    $path = Join-Path $script:WebRoot $file
    if (Test-Path $path) {
      $line = Get-Content $path | Where-Object { $_ -match "^$Name=" } | Select-Object -First 1
      if ($line) { $value = $line.Substring($Name.Length + 1).Trim().Trim('"') }
    }
  }
  if (-not $value -and -not $Optional) { throw "$Name が設定されていません(web/.env.prod.local を確認してください)" }
  return $value
}

function Send-RemoteFile([string]$LocalPath, [string]$RemoteName) {
  $ssh = Get-OpsSetting "DEPLOY_SSH"
  ssh -o BatchMode=yes $ssh "mkdir -p $($script:RemoteDir)"
  if ($LASTEXITCODE -ne 0) { throw "サーバーに接続できません" }
  scp -q -o BatchMode=yes $LocalPath "$($ssh):$($script:RemoteDir)/$RemoteName"
  if ($LASTEXITCODE -ne 0) { throw "scp に失敗しました: $RemoteName" }
  return "$($script:RemoteDir)/$RemoteName"
}

function Invoke-RemoteScript {
  param([Parameter(Mandatory)][string]$Script, [string[]]$Arguments = @(), [string]$InputText)
  $ssh = Get-OpsSetting "DEPLOY_SSH"
  $remote = Send-RemoteFile (Join-Path $PSScriptRoot "remote\$Script") $Script
  $argText = ($Arguments | ForEach-Object { "'" + ($_ -replace "'", "'\''") + "'" }) -join " "
  if ($PSBoundParameters.ContainsKey("InputText")) {
    $out = $InputText | ssh -o BatchMode=yes $ssh "bash $remote $argText"
  } else {
    $out = ssh -o BatchMode=yes $ssh "bash $remote $argText"
  }
  if ($LASTEXITCODE -ne 0) { throw "サーバーでの $Script が失敗しました (exit $LASTEXITCODE)" }
  return $out
}

function Invoke-ProdQuery([Parameter(Mandatory)][string]$Sql) {
  Invoke-RemoteScript "psql-query.sh" @((Get-OpsSetting "PROD_DB_CONTAINER"), (Get-OpsSetting "PROD_DB_SUPERUSER")) -InputText $Sql
}

function Backup-ProdDb([Parameter(Mandatory)][string]$Label) {
  $out = Invoke-RemoteScript "backup-db.sh" @((Get-OpsSetting "PROD_DB_CONTAINER"), (Get-OpsSetting "PROD_DB_SUPERUSER"), $Label)
  $remotePath = ($out | Select-Object -Last 1).Trim()
  New-Item -ItemType Directory -Force $script:BackupDir | Out-Null
  scp -q -o BatchMode=yes "$((Get-OpsSetting 'DEPLOY_SSH')):$remotePath" $script:BackupDir
  if ($LASTEXITCODE -ne 0) { throw "バックアップを手元に持ってこられませんでした" }
  $localPath = Join-Path $script:BackupDir (Split-Path $remotePath -Leaf)
  if ((Get-Item $localPath).Length -lt 10000) { throw "バックアップが小さすぎます: $localPath" }
  Write-Host "バックアップ: $localPath"
  return $localPath
}

function Invoke-ProdSqlFile([Parameter(Mandatory)][string]$Path) {
  $remote = Send-RemoteFile (Resolve-Path $Path) ("sql-" + (Split-Path $Path -Leaf))
  Invoke-RemoteScript "psql-file.sh" @((Get-OpsSetting "PROD_DB_CONTAINER"), (Get-OpsSetting "PROD_DB_SUPERUSER"), $remote)
}

function Invoke-ProdMigration([Parameter(Mandatory)][string]$Path) {
  $name = [IO.Path]::GetFileNameWithoutExtension($Path)
  $applied = (Invoke-ProdQuery "select exists (select 1 from ops.applied_migrations where name = '$name')" | Select-Object -Last 1).Trim()
  if ($applied -eq "t") { Write-Host "適用済みのため何もしません: $name"; return }
  $tmp = Join-Path $env:TEMP "$name.sql"
  $body = [IO.File]::ReadAllText((Resolve-Path $Path), [Text.Encoding]::UTF8)
  $record = "`n`ninsert into ops.applied_migrations (name) values ('$name');`n"
  [IO.File]::WriteAllText($tmp, $body + $record, (New-Object System.Text.UTF8Encoding $false))
  Invoke-ProdSqlFile $tmp
  Write-Host "適用しました: $name"
}

function Set-ServerEnv([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Value) {
  Invoke-RemoteScript "set-env.sh" @((Get-OpsSetting "DEPLOY_ENV_FILE"), $Name) -InputText $Value
}

function Set-WebProcess([ValidateSet("stop", "start")][string]$Action) {
  Invoke-RemoteScript "web-ctl.sh" @($Action)
}

function Invoke-WebDeploy {
  & (Join-Path $script:WebRoot "scripts\deploy-web.ps1")
}

function Test-TunnelPort {
  $client = New-Object Net.Sockets.TcpClient
  try { $client.Connect("127.0.0.1", 54329); return $true } catch { return $false } finally { $client.Dispose() }
}

function Start-DbTunnel {
  if (Test-TunnelPort) { Write-Host "トンネルは起動済みです"; return }
  $ssh = Get-OpsSetting "DEPLOY_SSH"
  $p = Start-Process ssh -ArgumentList @("-N", "-o", "BatchMode=yes", "-o", "ExitOnForwardFailure=yes", "-L", "54329:127.0.0.1:54329", $ssh) -WindowStyle Hidden -PassThru
  Set-Content $script:TunnelPidFile $p.Id
  for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Milliseconds 500
    if (Test-TunnelPort) { Write-Host "トンネルを開きました(127.0.0.1:54329)"; return }
  }
  throw "トンネルを開けませんでした"
}

function Stop-DbTunnel {
  if (Test-Path $script:TunnelPidFile) {
    Stop-Process -Id (Get-Content $script:TunnelPidFile) -ErrorAction SilentlyContinue
    Remove-Item $script:TunnelPidFile
  }
}

Export-ModuleMember -Function Get-OpsSetting, Invoke-RemoteScript, Invoke-ProdQuery, Backup-ProdDb, Invoke-ProdSqlFile, Invoke-ProdMigration, Set-ServerEnv, Set-WebProcess, Invoke-WebDeploy, Start-DbTunnel, Stop-DbTunnel
```

---

## 3. バックアップ

本番に触れる操作の**直前に毎回**取る。ラベルには、フェーズとステップを入れる(例: `phase1-step8`)。
```powershell
Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
$backup = Backup-ProdDb "phase1-step8"
```
- 完了の条件: `バックアップ: C:\dev\backups\ttech-store\...dump` と表示され、エラーがないこと。
- ダンプには個人情報が入る。`C:\dev\backups\` はリポジトリの外なのでコミットされないが、**フェーズ6が終わって2週間たったら、それより古いダンプは消す**。

## 4. マイグレーション

- 新しいマイグレーションは、フェーズ5までは `supabase/migrations/<YYYYMMDDHHMMSS>_<内容>.sql` に置く(フェーズ6で `db/migrations/` に移す)。**適用済みのファイルは書き換えない。**
- 各マイグレーションには、元に戻す SQL を `supabase/rollback/<同じ名前>.down.sql` に必ず一緒に作る。最後の行は次にする:
  ```sql
  delete from ops.applied_migrations where name = '<同じ名前(拡張子なし)>';
  ```
- 本番への適用:
  ```powershell
  Invoke-ProdMigration C:\dev\appstore\supabase\migrations\<名前>.sql
  ```
- ローカルでは `npm run test:db`(フェーズTで作る)が、全マイグレーションを空の DB に適用し直してから pgTAP を実行する。**ローカルで `down.sql` → もう一度 `up` が通ることも確かめる**(各フェーズの手順に書いてある)。

## 5. デプロイ

```powershell
Invoke-WebDeploy
npm run test:smoke   # C:\dev\appstore\web で実行
```
- `deploy-web.ps1` は「main が push 済み」であることを確かめてから、サーバーで main を取り込んでビルドする。**main に push してから**実行する。
- ビルド中(数十秒)は一時的にエラーになるページがある。スモークテストはデプロイが終わってから実行する。

## 6. 戻し方

失敗したら、上から順に、元に戻るまで行う。戻したら必ずスモークテストを流し、通ることを確かめてから報告する。

### 6-1. Web のコードを戻す
フェーズを main にマージする前の main の位置を、作業開始時に「進み具合」の表に書いておく(`git rev-parse origin/main` の値)。
```powershell
git -C C:\dev\appstore revert --no-edit <マージ前の main>..HEAD
git -C C:\dev\appstore push origin main
Invoke-WebDeploy
```

### 6-2. DB の変更を戻す
適用したマイグレーションを、新しいものから順に `down.sql` で戻す。
```powershell
Invoke-ProdSqlFile C:\dev\appstore\supabase\rollback\<名前>.down.sql
```

### 6-3. サーバーの環境変数を戻す
`Set-ServerEnv` は、書き換える前の版を `<ファイル>.bak.<日時>` として残している。前の値を同じ `Set-ServerEnv` で書き戻して、`Invoke-WebDeploy` する。

### 6-4. DB を丸ごと戻す(最後の手段)
6-2 で戻せないときだけ行う。直前のバックアップから、アプリのデータの部分(`public`・`private`・`ops`)だけを戻す。
```powershell
Set-WebProcess stop
# サーバーにあるダンプのパス(Backup-ProdDb が表示した名前と同じもの)を使う
Invoke-RemoteScript "restore-db.sh" @((Get-OpsSetting "PROD_DB_CONTAINER"), (Get-OpsSetting "PROD_DB_SUPERUSER"), "<サーバー上のダンプのパス>")
Set-WebProcess start
```
`web/scripts/ops/remote/restore-db.sh`(T-7 で一緒に作る):
```bash
#!/usr/bin/env bash
# usage: restore-db.sh <db-container> <db-superuser> <dump-file-on-server>
# Restores only the application schemas. Auth/storage schemas are left as they are.
set -euo pipefail
docker cp "$3" "$1:/tmp/restore.dump"
docker exec "$1" pg_restore -U "$2" -d postgres --clean --if-exists -n public -n private -n ops /tmp/restore.dump
docker exec "$1" rm -f /tmp/restore.dump
```
6-4 を行ったら、成否にかかわらず作業を止めて報告する。

## 7. 本番のスモークテスト

`npm run test:smoke`(フェーズTの T-6 で作る)。`https://store.kawamonn.com` に対して、次を確かめる。
- 公開のページと API(トップ・アプリ一覧・アプリ詳細・カテゴリ)が 200 で、形が正しい。
- 公開中のすべてのアプリのアイコン・スクリーンショットの URL が 200。
- スモーク用の一般ユーザーでログインでき、アカウントの情報が読める。日記に投稿して、自分のタイムラインに出て、消せる。
- スモーク用の一般ユーザーでは、管理 API が 401/403 になる。
- スモーク用の管理者(2段階認証つき)でログインでき、管理 API の一覧が 200。
- ログインせずに管理 API・開発者 API を呼ぶと 401。

どのフェーズの時点でも同じコマンドで実行できるように、ログインの方法だけ `tests/support/auth-driver.ts`(フェーズTで作る)で切り替える。
