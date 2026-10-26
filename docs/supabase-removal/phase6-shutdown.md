# フェーズ6: 素の PostgreSQL に移し、Supabase を止める

## なぜ
フェーズ5までで、Web もアプリも Supabase の API(Auth・PostgREST・Storage)を使わなくなりました。残っているのは、Supabase の DB コンテナ(中身は PostgreSQL)と、使われていないその他のコンテナです。ここで、
1. Web のコードから Supabase の名残(古いしくみへの戻り道・ライブラリ・設定)を消し、
2. 手元と CI のテスト環境を、Supabase CLI から素の PostgreSQL(Docker)に替え、
3. 本番の DB を、素の PostgreSQL の新しいコンテナに移し、
4. Supabase のコンテナを止めます。

本番の DB の移し方: **スキーマ(表・関数・ポリシー)はリポジトリのマイグレーションから作り、データだけを今の DB から写す。** 写す前に、今の本番のスキーマとリポジトリのスキーマが同じであることを機械で確かめる。

## 前提
- フェーズ5 が終わっている。4-14 で古いトークンの受け付けを止めてある(`LEGACY_SUPABASE_AUTH=0`)。
- ブランチ: `supabase-removal/phase6-shutdown`。マージ前の main を「進み具合」に書く。
- T-7 で調べた本番の DB の版(`db_version`)を確かめておく。新しいコンテナは**同じメジャー版**の公式イメージ `postgres:<メジャー版>` を使う(以下 `<MAJOR>`)。

---

## ステップ 6-1: 最後の複製

古いしくみへの戻り道(画像・写真を Supabase Storage から返す処理)を消す前に、取りこぼしが無いようにもう一度複製する。
```powershell
Set-Location C:\dev\appstore\web
$env:TTECH_ENV_FILE = ".env.prod.local"
node scripts\ops\copy-storage-to-r2.mjs --bucket app-media
node scripts\ops\copy-storage-to-r2.mjs --bucket diary-media
Remove-Item Env:\TTECH_ENV_FILE
```
完了の条件: どちらも失敗 0 件。

## ステップ 6-2: Web のコードから Supabase を消す

1. 戻り道を消す:
   - `src/lib/current-user.ts` の `LEGACY_SUPABASE_AUTH` の分岐と、`src/lib/env.ts` の `legacySupabaseAuth`。
   - `src/app/media/app/[...path]/route.ts` の Supabase Storage への戻り道(R2 に無ければ 404)。
   - 日記の写真の取得・削除・アカウントの削除・管理画面の署名付き URL の、Supabase Storage の分岐(フェーズ3)。
2. 消すファイル: `src/lib/supabase.ts`・`src/lib/supabase-browser.ts`・`src/lib/auth-cookie.ts`(と、そのテスト)、`src/lib/testing/fake-supabase.ts`(使っているテストが残っていれば、先にフェーズ1の 1-7 と同じ方法で片付ける)。`supabase.ts` にあった型や関数で、まだ使われているもの(`AdminUser` など)は、`src/lib/admin.ts` か `current-user.ts` に移してから消す。
3. `next.config.ts`: `/auth/v1`・`/rest/v1`・`/storage/v1` の転送(`rewrites`)を消す。代わりに、古い画像の URL を新しい URL へ転送する:
   ```ts
   async redirects() {
     return [{ source: "/storage/v1/object/public/app-media/:path*", destination: "/media/app/:path*", permanent: true }];
   },
   ```
4. `src/lib/env.ts`・`.env.example` から、`NEXT_PUBLIC_SUPABASE_URL`・`NEXT_PUBLIC_SUPABASE_ANON_KEY`・`INTERNAL_SUPABASE_URL`・`SUPABASE_SERVICE_ROLE_KEY`・`SUPABASE_JWT_SECRET` を消す。
5. ライブラリを外す: `npm uninstall @supabase/ssr @supabase/supabase-js`
6. 手元のスクリプト: `scripts/ops/copy-storage-to-r2.mjs` を消す(役目を終えた)。他のスクリプトに Supabase の変数の参照が残っていれば消す。
7. `web/scripts/ops/ops.psm1` の `Start-DbTunnel` が使うポートを、設定 `PROD_DB_PORT`(既定 `54329`)から読むようにする(6-6 で新しい DB のポートに替えるため)。
8. 確認: 次が何も表示しない。
   ```powershell
   Set-Location C:\dev\appstore\web
   Select-String -Path (Get-ChildItem -Recurse src,scripts -Include *.ts,*.tsx,*.mjs) -Pattern 'supabase' -SimpleMatch
   ```
   (コメントに残っている「Supabase」の文字も、今の構成の説明として正しくなければ直す)

テスト側:
- `tests/support/auth-driver.ts` の `supabaseAuthDriver` と、`tests/scenarios/diary.ts` の `postgrestDiaryDriver` を消す。既定を `better-auth`・`api` にする。
- PostgREST と比べていたテスト(`db-access.test.ts` の 1、`me-account.test.ts`、日記の行の形の比較)は、比べた結果がスナップショットに入っていることを確かめてから、PostgREST を呼ぶ部分を消す(残すのは「スナップショットと同じ」の確認)。
- `tests/migration/`(フェーズ4の移行のテスト)は、Supabase Auth が無いと動かない。役目を終えたので消す。
- `tests/support/env.ts` の確認の対象から、消した変数を外す。

コミット: まとまりごと(戻り道・ファイル・設定・テスト)に。

## ステップ 6-3: 手元と CI の DB を素の PostgreSQL にする

### 3-1. DB のコンテナ
`db/Dockerfile`(テスト用。pgTAP を入れる):
```dockerfile
FROM postgres:<MAJOR>
RUN apt-get update && apt-get install -y --no-install-recommends postgresql-<MAJOR>-pgtap && rm -rf /var/lib/apt/lists/*
```
`db/compose.yml`(手元と CI 用):
```yaml
services:
  db:
    build: .
    ports:
      - "127.0.0.1:55432:5432"
    environment:
      POSTGRES_PASSWORD: local-only
    volumes:
      - ./tests:/tests:ro
```
(データは残さない。テストのたびに作り直す)

### 3-2. 最初の土台 `db/bootstrap.sql`
Supabase が用意していたロールと、RLS が使う `auth.uid()` などの関数を、同じ意味で作る(フェーズ7で RLS をやめるまで必要)。
```sql
-- 素の PostgreSQL に、これまで Supabase が用意していたものを作る(フェーズ7で RLS をやめるまで使う)
do $$ begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon nologin noinherit; end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin noinherit; end if;
  if not exists (select 1 from pg_roles where rolname = 'service_role') then create role service_role nologin noinherit bypassrls; end if;
  if not exists (select 1 from pg_roles where rolname = 'ttech_web') then create role ttech_web login noinherit; end if;
  if not exists (select 1 from pg_roles where rolname = 'ttech_auth') then create role ttech_auth login; end if;
end $$;
grant anon, authenticated, service_role to ttech_web;

create schema if not exists auth;
grant usage on schema auth to anon, authenticated, service_role, ttech_auth;

-- Supabase の auth.uid()・auth.role()・auth.jwt() と同じ定義(request.jwt.claims を読むだけ)
create or replace function auth.uid() returns uuid language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claim.sub', true), ''), (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub'))::uuid
$$;
create or replace function auth.role() returns text language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claim.role', true), ''), (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role'))::text
$$;
create or replace function auth.jwt() returns jsonb language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claim', true), ''), nullif(current_setting('request.jwt.claims', true), ''))::jsonb
$$;

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
grant usage on schema public to anon, authenticated, service_role, ttech_auth;
```
(パスワードは、手元はテストの前の `db-roles.sql`、本番は 6-6 で設定する)

### 3-3. スキーマの土台(baseline)を作る
**この作業は、まだ Supabase CLI が使えるうち(このステップの中)に行う。**
1. `npm run test:db`(Supabase CLI)で、全マイグレーションが入ったローカルの DB を作る。
2. `web/scripts/db-baseline.mjs`(新規)を作って実行する: ローカルの Supabase の DB から
   `pg_dump --schema-only --no-owner -n public -n private -n ops`(Docker の DB コンテナの中で実行し、`docker cp` で取り出す。手元に `pg_dump` があればそれでもよい)を取り、次の行を取り除いて `db/migrations/<日時>_baseline.sql` に書く:
   - `CREATE SCHEMA public;`・`COMMENT ON SCHEMA public` など、`public` スキーマそのものを作る行
   - `postgres`・`supabase_admin`・`supabase_auth_admin`・`supabase_storage_admin`・`dashboard_user`・`pgbouncer`・`authenticator`・`supabase_realtime_admin`・`supabase_replication_admin`・`supabase_read_only_user`・`supabase_etl_admin` を含む `GRANT`/`REVOKE`/`ALTER DEFAULT PRIVILEGES` の行(素の PostgreSQL に無いロール)
   - 先頭のコメントに「Supabase のマイグレーション(`db/archive/supabase-migrations/`)をすべて入れた状態のスキーマ」と書く。
3. 作った baseline に、次が**含まれていない**ことを確かめる(含まれていたら、どのマイグレーションの名残かを調べ、新しいマイグレーションで直してから作り直す):
   ```powershell
   Select-String -Path C:\dev\appstore\db\migrations\*_baseline.sql -Pattern 'auth\.(users|identities|mfa_factors|sessions)|net\.http|storage\.(objects|buckets)'
   ```
4. 移動する:
   - `supabase/migrations/*` → `db/archive/supabase-migrations/`、`supabase/rollback/*` → `db/archive/supabase-rollback/`(**適用しない。記録として残す**)
   - `supabase/seed.sql` → `db/seed.sql`
   - `supabase/tests/database/*.test.sql` → `db/tests/`。中の `insert into auth.users ...` は `insert into public.ba_user ...`(必要な列を埋める)に、`storage.objects` を確かめている部分は消す。
   - `supabase/sql-tests/` → `db/archive/sql-tests/`
   - `supabase/config.toml`・`supabase/local/` を消す(`supabase/local/` は Git に入っていない。手元のファイルは持ち主のものなので**消さない**。Git の管理から外れているだけでよい)。
5. `db/rollback/` を作り、以降のマイグレーションの `down.sql` はここに置く。[ops.md](ops.md) の「4. マイグレーション」の場所の記述を `db/migrations/`・`db/rollback/` に直す。

### 3-4. スクリプト
- `web/scripts/db-migrate.mjs`(新規): `DATABASE_ADMIN_URL` につなぎ、`db/migrations/*.sql` を名前の順に、`ops.applied_migrations` に無いものだけ、1つずつトランザクションで流して記録する。最初の baseline のときは `ops` スキーマがまだ無いので、表が無ければ「未適用」とみなす(baseline が `ops` の表を作る)。
- `web/package.json` の `scripts` を置き換える:
  ```json
  "stack:start": "docker compose -f ../db/compose.yml up -d --build --wait && node scripts/db-reset.mjs",
  "stack:stop": "docker compose -f ../db/compose.yml down -v",
  "test:db": "node scripts/db-reset.mjs && docker compose -f ../db/compose.yml exec -T db sh -c \"pg_prove -U postgres -d postgres /tests/*.test.sql\""
  ```
  `stack:status`・`supabase` の開発用の依存(`npm uninstall supabase`)は消す。
- `web/scripts/db-reset.mjs`(新規): `drop database if exists app with (force)` ではなく、`postgres` の DB のスキーマ `public`・`private`・`ops`・`auth`・`extensions` を消して作り直す → `db/bootstrap.sql` → `db-migrate.mjs` → `db/seed.sql` → `web/tests/support/db-roles.sql`(ローカルのパスワード)。
- `web/scripts/write-local-env.mjs`: Supabase CLI を呼ばない形にする。値は固定(`DATABASE_URL=postgresql://ttech_web:local-only@127.0.0.1:55432/postgres`、`DATABASE_ADMIN_URL=postgresql://postgres:local-only@127.0.0.1:55432/postgres`、`AUTH_DATABASE_URL=postgresql://ttech_auth:local-only@127.0.0.1:55432/postgres`、他はフェーズT・4 と同じ)。
- `web/scripts/db-types.mjs`: 接続先は `DATABASE_ADMIN_URL` のまま(ポートが変わるだけ)。
- `tests/support/global-setup.ts`: 「DB をまっさらにする」を `scripts/db-reset.mjs` の関数の呼び出しにする。

### 3-5. CI
`.github/workflows/ci.yml` の `database` と `web-api` ジョブを、Supabase CLI ではなく `docker compose -f db/compose.yml up -d --build --wait` を使う形にする(`supabase/setup-cli` を消す)。`database` は `npm run test:db`、`web-api` は `npm run test:api`(どちらも `working-directory: web`、`npm ci` と `node scripts/write-local-env.mjs` の後)。

完了の条件(6-3 全体): `npm run stack:stop; npm run stack:start` の後、`npm run test:db`・`npm run test:api`(3回)・`npm run typecheck`・`npm test`・`npm run build` が合格。`npm run db:types` で作り直した型に差分が無い。GitHub Actions が緑。
コミット: まとまりごと(コンテナと土台・baseline と移動・スクリプト・CI)に。

## ステップ 6-4: Web を出す(本番の操作。DB はまだ今のまま)

6-2 の Web は Supabase の API を一切使わない(DB は `DATABASE_URL` の PostgreSQL、ファイルは R2)ので、**DB を移す前に出せる**。先に出して、DB の移行と問題を切り分ける。
```powershell
Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
git -C C:\dev\appstore fetch origin
git -C C:\dev\appstore rebase origin/main
git -C C:\dev\appstore push --force-with-lease origin supabase-removal/phase6-shutdown
gh run watch --repo kawamonn91/ttech-store --exit-status
git -C C:\dev\appstore switch main
git -C C:\dev\appstore merge --ff-only supabase-removal/phase6-shutdown
git -C C:\dev\appstore push origin main
Backup-ProdDb "phase6-step4"
Invoke-WebDeploy
Set-Location C:\dev\appstore\web; npm run test:smoke
```
完了の条件: スモークテストが合格。さらに、`Invoke-WebRequest https://store.kawamonn.com/rest/v1/ -SkipHttpErrorCheck`(PowerShell 7。5.1 なら try/catch でステータスを読む)が 404。失敗したら 6-1(ops.md)で戻す。

## ステップ 6-5: リハーサル(手元で、本番のデータを使って)

本番の切り替えと同じ手順を、手元の別の DB で先に通す。

### 5-1. スキーマが同じことを確かめる
`web/scripts/ops/remote/dump-schema.sh`(新規):
```bash
#!/usr/bin/env bash
# usage: dump-schema.sh <db-container> <db-superuser>
# Prints the schema (no data, no owners) of the application schemas.
set -euo pipefail
docker exec "$1" pg_dump -U "$2" -d postgres --schema-only --no-owner -n public -n private -n ops
```
```powershell
$prodSchema = Invoke-RemoteScript "dump-schema.sh" @((Get-OpsSetting "PROD_DB_CONTAINER"), (Get-OpsSetting "PROD_DB_SUPERUSER"))
[IO.File]::WriteAllLines("$env:TEMP\prod-schema.sql", $prodSchema)
```
`web/scripts/ops/compare-schema.mjs`(新規): 2つのスキーマのファイルを、3-3 の baseline と同じ決まりで行を取り除き、空行とコメントを消して並べ替えてから比べ、違う行を表示する。手元の側は、`npm run stack:start` した DB から同じ `pg_dump` を取ったもの(`docker compose -f ..\db\compose.yml exec -T db pg_dump ...`)。
```powershell
node scripts\ops\compare-schema.mjs $env:TEMP\prod-schema.sql $env:TEMP\local-schema.sql
```
完了の条件: 違いが0行。違いがあれば、本番に入っていないマイグレーション・手で変えた所が無いかを調べて報告する(勝手に本番を直さない)。

### 5-2. 本番のデータを手元の DB に写す
```powershell
$backup = Backup-ProdDb "phase6-rehearsal"
```
`web/scripts/ops/rehearse-restore.ps1`(新規)を作って実行する:
1. 手元の DB を作り直す(`npm run stack:stop; npm run stack:start`)。**ただし `db/seed.sql` は入れない**(本番のデータと重なるため。`db-reset.mjs` に `--no-seed` を足す)。
2. ダンプのファイルをコンテナにコピーし、`pg_restore -U postgres -d postgres --data-only --disable-triggers --exit-on-error -n public -n private -n ops /tmp/backup.dump`。
3. 終了コードが 0 であること。
4. 表ごとの行数を、本番(`Invoke-ProdQuery` で `select '<表>', count(*) from ...` を `union all` でつないだもの)と手元で比べ、全部同じであること。対象の表は `select table_schema || '.' || table_name from information_schema.tables where table_schema in ('public','private','ops') and table_type = 'BASE TABLE'` で決める。

### 5-3. 写したデータで Web を動かす
手元の Next.js を、写した DB で起動し(`npx next build; npx next start -p 3100 -H 127.0.0.1`、環境変数は `.env.local`)、スモークテストを手元に向けて流す:
```powershell
$env:SMOKE_BASE_URL = "http://127.0.0.1:3100"; npm run test:smoke; Remove-Item Env:\SMOKE_BASE_URL
```
(`tests/smoke/` が `SMOKE_BASE_URL` を読むようにしておく。既定は `https://store.kawamonn.com`。R2 は手元では偽物なので、画像の確認は `SMOKE_BASE_URL` がローカルのときは飛ばす)
完了の条件: 合格。終わったら手元の DB を `npm run stack:stop` で消す(本番の個人情報が入っているため)。

## ステップ 6-6: 本番の DB を移す(本番の操作。停止時間あり)

**停止時間は 5〜15 分の見込み。** 手順を最後まで読んでから始める。各段で失敗したら、下の「戻し方」で戻して止まる。

### A. 新しい DB のコンテナを用意する(まだ使わない)
`web/scripts/ops/remote/new-db-up.sh`(新規):
```bash
#!/usr/bin/env bash
# usage: new-db-up.sh <deploy-dir> <postgres-major>   (superuser password comes from stdin)
# Starts a plain PostgreSQL container for the store on 127.0.0.1:54330, data in a named volume.
set -euo pipefail
dir="$1/db-prod"; major="$2"
password="$(cat | tr -d '\r\n')"
mkdir -p "$dir"
umask 077
printf 'POSTGRES_PASSWORD=%s\n' "$password" > "$dir/postgres.env"
cat > "$dir/compose.yml" <<YAML
services:
  db:
    image: postgres:$major
    container_name: ttech-db
    restart: unless-stopped
    ports:
      - "127.0.0.1:54330:5432"
    env_file: postgres.env
    volumes:
      - ttech-pg-data:/var/lib/postgresql/data
volumes:
  ttech-pg-data: {}
YAML
cd "$dir"
docker compose up -d --wait
docker exec ttech-db psql -U postgres -tAc 'select 1'
```
```powershell
function New-Hex([int]$n) { $b = New-Object byte[] $n; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { $_.ToString("x2") }) }
Invoke-RemoteScript "new-db-up.sh" @((Get-OpsSetting "DEPLOY_DIR"), "<MAJOR>") -InputText (New-Hex 24)
```
(`DEPLOY_DIR` の下の `db-prod/` は、リポジトリの `.gitignore` に入れる: `/db-prod/`)

新しい DB に土台とスキーマを入れる。この間だけ、運用の関数の向き先を新しい DB にする:
```powershell
$env:PROD_DB_CONTAINER = "ttech-db"; $env:PROD_DB_SUPERUSER = "postgres"
Invoke-ProdSqlFile C:\dev\appstore\db\bootstrap.sql
Invoke-ProdSqlFile (Get-ChildItem C:\dev\appstore\db\migrations\*_baseline.sql).FullName
# 2つのロールのパスワードを、今の接続文字列と同じにする(接続文字列はポートだけを替えればよくなる)
$envText = Get-Content C:\dev\appstore\web\.env.prod.local
$webPw = ([regex]::Match(($envText | Where-Object { $_ -match '^DATABASE_URL=' }), '//ttech_web:([^@]+)@')).Groups[1].Value
$authPw = ([regex]::Match(($envText | Where-Object { $_ -match '^AUTH_DATABASE_URL=' }), '//ttech_auth:([^@]+)@')).Groups[1].Value
if (-not $webPw -or -not $authPw) { throw ".env.prod.local の接続文字列を読めませんでした" }
$tmp = Join-Path $env:TEMP "ttech-roles.sql"
"alter role ttech_web password '$webPw'; alter role ttech_auth password '$authPw';" | Set-Content -Path $tmp -Encoding ASCII
Invoke-ProdSqlFile $tmp; Remove-Item $tmp; Remove-Variable webPw, authPw
Remove-Item Env:\PROD_DB_CONTAINER, Env:\PROD_DB_SUPERUSER
```
(`AUTH_DATABASE_URL` が `.env.prod.local` に無ければ、4-13 B の値をサーバーの環境変数ファイルから読む方法は無いので、止まって報告する。4-13 B で `.env.prod.local` にも書いておくこと)

### B. 止めて、データを写す
`web/scripts/ops/remote/copy-data.sh`(新規):
```bash
#!/usr/bin/env bash
# usage: copy-data.sh <old-container> <old-superuser> <new-container>
# Copies data only (public, private, ops) from the old database to the new one, then compares row counts.
set -euo pipefail
old="$1"; olduser="$2"; new="$3"
docker exec "$old" pg_dump -U "$olduser" -d postgres --data-only -Fc -n public -n private -n ops -f /tmp/data.dump
docker cp "$old:/tmp/data.dump" /tmp/ttech-data.dump
docker cp /tmp/ttech-data.dump "$new:/tmp/data.dump"
docker exec "$new" pg_restore -U postgres -d postgres --data-only --disable-triggers --exit-on-error /tmp/data.dump
q="select string_agg(t || '=' || n, ',' order by t) from (select table_schema || '.' || table_name as t, (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text as n from information_schema.tables where table_schema in ('public','private','ops') and table_type = 'BASE TABLE') s"
a="$(docker exec "$old" psql -U "$olduser" -d postgres -tAc "$q")"
b="$(docker exec "$new" psql -U postgres -d postgres -tAc "$q")"
if [ "$a" != "$b" ]; then echo "row counts differ" >&2; echo "old: $a" >&2; echo "new: $b" >&2; exit 6; fi
echo "row counts match"
```
```powershell
Backup-ProdDb "phase6-cutover"
Set-WebProcess stop
Invoke-RemoteScript "copy-data.sh" @((Get-OpsSetting "PROD_DB_CONTAINER"), (Get-OpsSetting "PROD_DB_SUPERUSER"), "ttech-db")
$env:PROD_DB_CONTAINER = "ttech-db"; $env:PROD_DB_SUPERUSER = "postgres"
Invoke-ProdQuery "insert into ops.applied_migrations (name) values ('$([IO.Path]::GetFileNameWithoutExtension((Get-ChildItem C:\dev\appstore\db\migrations\*_baseline.sql).Name))') on conflict do nothing"
Remove-Item Env:\PROD_DB_CONTAINER, Env:\PROD_DB_SUPERUSER
```
確かめ: `row counts match`。

### C. Web の向き先を新しい DB にして動かす
```powershell
$envText = Get-Content C:\dev\appstore\web\.env.prod.local
$db = (($envText | Where-Object { $_ -match '^DATABASE_URL=' }) -split '=', 2)[1].Replace("@127.0.0.1:54329/", "@127.0.0.1:54330/")
$authDb = (($envText | Where-Object { $_ -match '^AUTH_DATABASE_URL=' }) -split '=', 2)[1].Replace("@127.0.0.1:54329/", "@127.0.0.1:54330/")
Set-ServerEnv "DATABASE_URL" $db
Set-ServerEnv "AUTH_DATABASE_URL" $authDb
Remove-Variable db, authDb
Invoke-WebDeploy
Set-Location C:\dev\appstore\web; npm run test:smoke
```
確かめ: スモークテストが合格。

### D. Supabase を止める
`web/scripts/ops/remote/supabase-stack.sh`(新規):
```bash
#!/usr/bin/env bash
# usage: supabase-stack.sh <supabase-docker-dir> stop|start
# Stops or starts the Supabase containers without removing them or their volumes.
set -euo pipefail
cd "$1"
docker compose "$2"
docker compose ps --format '{{.Name}} {{.State}}'
```
```powershell
Invoke-RemoteScript "supabase-stack.sh" @((Get-OpsSetting "SUPABASE_DOCKER_DIR"), "stop")
npm run test:smoke
```
確かめ: スモークテストが合格。

### E. 手元の設定を新しい DB に合わせる
`web/.env.prod.local` を書き換える(値は表示しない): `DATABASE_URL`・`AUTH_DATABASE_URL` のポートを `54330` に、`PROD_DB_CONTAINER=ttech-db`、`PROD_DB_SUPERUSER=postgres`、`PROD_DB_PORT=54330`、`SUPABASE_DOCKER_DIR` は残す(F で使う)。あわせて、Web とトンネルが新しい DB を使うので、トンネルのポートも `PROD_DB_PORT` で 54330 になる。
確かめ: `Start-DbTunnel` の後、フェーズ1 の 1-10-4 と同じ接続の確認が成功する。

### 戻し方(B〜D で失敗したとき)
```powershell
Invoke-RemoteScript "supabase-stack.sh" @((Get-OpsSetting "SUPABASE_DOCKER_DIR"), "start")   # D の後なら
# C の後なら: DATABASE_URL・AUTH_DATABASE_URL を 54329 に戻して Invoke-WebDeploy
Set-WebProcess start
npm run test:smoke
```
切り替えの後に新しい DB に書かれたデータ(数分ぶん)は失われる。戻したら止まって報告する。新しい DB のコンテナ(`ttech-db`)は消さずに残す。

## ステップ 6-7: 手順書と構成の説明を直す

- `docs/setup.md`: 「1. Supabase」を「1. PostgreSQL」に書き換える(`db/compose.yml` と同じ考え方の本番用の compose、`db/bootstrap.sql` → `db/migrations` の順に流す、ロールのパスワードの設定)。「4. 本番サーバー」の環境変数の説明を、今の `.env.example` に合わせる。サーバーの IP・ユーザー名は書かない。
- `docs/supabase-migration.md` → `docs/db-migration.md` に名前を変え、`db/migrations/`・`db/rollback/`・`ops.applied_migrations`・[supabase-removal/ops.md](ops.md) の関数を使う手順に書き換える。リンクしている所(README など)も直す。
- `docs/admin.md`・`docs/developer-platform.md`・`README.md`・`web/README.md`: Supabase の記述を今の構成に直す。
- `AGENTS.md` に、DB の変更の決まり(マイグレーションは `db/migrations/`、`down.sql` は `db/rollback/`、本番の適用は `Invoke-ProdMigration`)を3行で足す。

コミットして push。

## ステップ 6-8: 持ち主への報告と、2週間後の片付け

持ち主への依頼:
- クラウドの Supabase のプロジェクトを、Supabase のダッシュボードで**削除**する(フェーズT の最後に一時停止してある)。
- 手元の `web/.env.cloud.local` と `supabase/local/` を消してよいか判断する(エージェントは消さない)。

2週間たって問題が無ければ、次を行う(このときにもう一度このファイルを開いて実行する):
1. `Backup-ProdDb "phase6-final"`(新しい DB の全体)。
2. 古い Supabase のコンテナとデータを消す。`web/scripts/ops/remote/supabase-stack.sh` に `down` を足すのではなく、次の1回だけのコマンドで行う(消すと戻せないので、実行前に、このファイルの「進み具合」に日付と理由を書く):
   ```powershell
   $dir = Get-OpsSetting "SUPABASE_DOCKER_DIR"
   ssh -o BatchMode=yes (Get-OpsSetting "DEPLOY_SSH") "cd '$dir' && docker compose down -v"
   ```
3. `web/.env.prod.local` から `SUPABASE_DOCKER_DIR` を消す。`C:\dev\backups\ttech-store\` の、この日より古いダンプを消す。サーバーの `~/ttech-backups/` も同じく古いものを消す。

---

## 進み具合

| ステップ | 日付 | 結果・メモ |
|---|---|---|
| 6-0 | | マージ前の main: `` / 本番の DB のメジャー版: |
| 6-1 | | |
| 6-2 | | |
| 6-3 | | |
| 6-4 | | |
| 6-5 | | スキーマの違い: / 行数の比較: |
| 6-6 | | A / B / C / D / E。停止していた時間: |
| 6-7 | | |
| 6-8 | | 2週間後の片付け: |
