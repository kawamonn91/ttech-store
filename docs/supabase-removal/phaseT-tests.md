# フェーズT: テスト基盤を作る

## なぜ
このあとのフェーズは、認証・権限・データの置き場所を順に入れ替えます。入れ替えの前後で「外から見た動き」(API の応答の形、誰が何を見られるか)が変わっていないことを、**人が確かめなくても機械で判定できる**ようにするのがこのフェーズです。ここで作るテストが、以降のすべてのフェーズの合格条件になります。

方針:
- テストは**今の実装の動きをそのまま記録する**(特性テスト)。このフェーズではアプリのコードの動きを変えない。例外は、テストのための差し込み口(R2 の接続先の上書き)だけ。
- テストは HTTP で Web を外から呼ぶ。中の実装(Supabase か Kysely か)に依存しないので、以降のフェーズでも同じテストがそのまま使える。
- ログインの方法(今は Supabase Auth、フェーズ4から Better Auth)と、日記の呼び方(今は PostgREST 直、フェーズ3から Web の API)だけは、差し替えられる「ドライバー」にする。

## 前提
- フェーズ0 が終わっている。
- Docker Desktop が起動している。
- ブランチ: `git -C C:\dev\appstore switch -c supabase-removal/phaseT-tests`。マージ前の main(`git rev-parse origin/main`)を「進み具合」に書く。

## このフェーズで作るもの(一覧)

| 場所 | 中身 |
|---|---|
| `web/.env.local` | **手元の検証環境の設定に置き換える**(今のクラウド向けの設定は `.env.cloud.local` に退避) |
| `web/package.json` | `supabase`・`pg`・`otplib` などの開発用の依存と、`stack:*`・`test:*` のスクリプト |
| `web/src/lib/r2.ts`・`web/src/lib/env.ts` | R2 の接続先を `R2_ENDPOINT` で上書きできるようにする(テスト用) |
| `web/scripts/write-local-env.mjs` | ローカルの Supabase の情報から `.env.local` を作る |
| `web/tests/support/` | 安全装置・偽の R2・テスト用サーバーの起動・データの用意・ログインのドライバー |
| `web/tests/api/` | API テスト(権限の表・応答の形のスナップショット・業務の流れ) |
| `web/tests/scenarios/` | 日記の公開範囲などのシナリオ(ドライバーを差し替えて使い回す) |
| `web/tests/smoke/` | 本番のスモークテスト |
| `web/scripts/ops/` | [ops.md](ops.md) の運用スクリプト |
| `supabase/migrations/20261027000000_ops_schema.sql` | 適用済みマイグレーションの記録用の表 |
| `.github/workflows/ci.yml` | API テストのジョブ |
| `docs/supabase-removal/rls-inventory.md` | 今の RLS ポリシーの一覧(フェーズ7で使う) |

---

## ステップ T-1: 設定ファイルを分ける

今の `web/.env.local` はクラウドの Supabase と本物の R2・GitHub のトークンを向いている。テストのたびに本物へ書き込まないよう、`.env.local` は**手元の検証環境専用**にする。

1. 今の `.env.local` を退避する(Next.js が読まない名前):
   ```powershell
   Set-Location C:\dev\appstore\web
   if (Test-Path .env.cloud.local) { throw ".env.cloud.local がすでにあります" }
   Copy-Item .env.local .env.cloud.local
   ```
2. 運用に使う行を `.env.prod.local` に写す(無いものだけ。値は表示しない):
   ```powershell
   foreach ($name in @("DEPLOY_SSH", "DEPLOY_DIR")) {
     if (-not (Select-String -Path .env.prod.local -Pattern "^$name=" -Quiet)) {
       $line = Select-String -Path .env.cloud.local -Pattern "^$name=" | Select-Object -First 1
       if (-not $line) { throw ".env.cloud.local に $name がありません" }
       Add-Content -Path .env.prod.local -Value $line.Line -Encoding UTF8
     }
   }
   ```
3. `web/scripts/deploy-web.ps1` の `Get-DeploySetting` を、`.env.prod.local` を先に、なければ `.env.local` を見るように変える。
   置き換え前:
   ```powershell
     if (-not $value -and (Test-Path ".env.local")) {
       $line = Get-Content ".env.local" | Where-Object { $_ -match "^$name=" } | Select-Object -First 1
       if ($line) { $value = $line.Substring($name.Length + 1).Trim().Trim('"') }
     }
     if (-not $value) { throw "$name が web/.env.local にも環境変数にも設定されていません" }
   ```
   置き換え後:
   ```powershell
     foreach ($file in @(".env.prod.local", ".env.local")) {
       if ($value -or -not (Test-Path $file)) { continue }
       $line = Get-Content $file | Where-Object { $_ -match "^$name=" } | Select-Object -First 1
       if ($line) { $value = $line.Substring($name.Length + 1).Trim().Trim('"') }
     }
     if (-not $value) { throw "$name が web/.env.prod.local・web/.env.local・環境変数のどれにも設定されていません" }
   ```
   ファイル冒頭のコメントの `web/.env.local(または環境変数)` も `web/.env.prod.local(または .env.local・環境変数)` に直す。

`.env.local` 自体の書き換えは T-3 で行う(ローカルの Supabase を起動してから)。

完了の条件: `.env.cloud.local` があり、`.env.prod.local` に `DEPLOY_SSH`・`DEPLOY_DIR` の行がある(`Select-String -Quiet` が `True`)。
コミット: `デプロイスクリプトが本番の設定を .env.prod.local から読むようにする`

## ステップ T-2: 依存とスクリプトを足す

```powershell
Set-Location C:\dev\appstore\web
npm install --save-dev supabase pg @types/pg otplib
```
- `supabase`: Supabase CLI(ローカルの Supabase を Docker で動かす)。
- `pg`: テストからローカルの DB に直接つなぐ(データの用意・確認)。
- `otplib`: テストで TOTP のコードを作る。

`web/package.json` の `scripts` に足す(既存の行は変えない):
```json
"stack:start": "supabase start --workdir .. -x edge-runtime,imgproxy,logflare,vector,studio,mailpit,realtime,supavisor",
"stack:stop": "supabase stop --workdir ..",
"stack:status": "supabase status --workdir ..",
"test:db": "supabase db reset --workdir .. && supabase test db --workdir ..",
"test:api": "vitest run --config vitest.api.config.ts",
"test:smoke": "vitest run --config vitest.smoke.config.ts",
"local-env": "node scripts/write-local-env.mjs"
```
既存の `vitest.config.ts` の `test.include` は `src/**` と `scripts/**` だけなので、`tests/**` は `npm test` には含まれない(別の設定で動かす)。そのままにする。

`.gitignore`(`web/.gitignore`)に足す:
```
/tests/.fixtures.json
/tests/.server.log
```

完了の条件: `npm run stack:start` が成功し、`npm run stack:status` に `API URL`・`DB URL` が表示される(初回は Docker のイメージの取得で数分かかる)。
コミット: `ローカルの Supabase とテスト用の依存・スクリプトを足す`

## ステップ T-3: 手元の検証環境の `.env.local` を作る

### `web/scripts/write-local-env.mjs`(新規)
```js
#!/usr/bin/env node
// ローカルの Supabase(npm run stack:start)の接続情報から web/.env.local を作る。
// 外部のサービス(本物の R2・GitHub・メール)には一切つながない値だけを書く。
// 今の .env.local がローカル以外を向いていて、退避(.env.cloud.local)が無ければ止まる。
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const target = path.join(webRoot, ".env.local");

if (existsSync(target)) {
  const current = readFileSync(target, "utf8");
  const url = current.match(/^NEXT_PUBLIC_SUPABASE_URL=(.*)$/m)?.[1]?.trim() ?? "";
  const isLocal = /^http:\/\/(127\.0\.0\.1|localhost)[:/]/.test(url);
  if (!isLocal && !existsSync(path.join(webRoot, ".env.cloud.local"))) {
    throw new Error(".env.local がローカル以外を向いています。先に .env.cloud.local に退避してください");
  }
}

const out = execFileSync("npx", ["supabase", "status", "-o", "env", "--workdir", ".."], { cwd: webRoot, encoding: "utf8", shell: true });
const status = Object.fromEntries(
  out.split(/\r?\n/).filter((l) => l.includes("=")).map((l) => {
    const i = l.indexOf("=");
    return [l.slice(0, i).trim(), l.slice(i + 1).trim().replace(/^"|"$/g, "")];
  }),
);
for (const key of ["API_URL", "ANON_KEY", "SERVICE_ROLE_KEY", "DB_URL", "JWT_SECRET"]) {
  if (!status[key]) throw new Error(`supabase status の出力に ${key} がありません(CLI の版で名前が違う可能性。出力のキー名を確かめて直すこと)`);
}

const lines = {
  NEXT_PUBLIC_SUPABASE_URL: status.API_URL,
  NEXT_PUBLIC_SUPABASE_ANON_KEY: status.ANON_KEY,
  SUPABASE_SERVICE_ROLE_KEY: status.SERVICE_ROLE_KEY,
  SUPABASE_JWT_SECRET: status.JWT_SECRET,
  DATABASE_URL: status.DB_URL,
  R2_ACCOUNT_ID: "local",
  R2_ACCESS_KEY_ID: "local",
  R2_SECRET_ACCESS_KEY: "local",
  R2_BUCKET: "ttech-test",
  R2_ENDPOINT: "http://127.0.0.1:59000",
  SCAN_WEBHOOK_SECRET: "local-scan-secret",
  DEVICE_HASH_SALT: "local-salt",
  NOTIFY_WEBHOOK_SECRET: "local-notify-secret",
  NEXT_PUBLIC_SITE_URL: "http://127.0.0.1:3100",
  GITHUB_DISPATCH_TOKEN: "",
  GITHUB_REPO: "",
  RESEND_API_KEY: "",
  ADMIN_NOTIFY_EMAIL: "",
  ADMIN_USER_ID: "",
};
const body = "# 手元の検証環境(npm run local-env で作り直せる)。本物のサービスにはつながない\n"
  + Object.entries(lines).map(([k, v]) => `${k}=${v}`).join("\n") + "\n";
writeFileSync(target, body);
console.log(`書きました: ${target}`);
```

実行:
```powershell
Set-Location C:\dev\appstore\web
npm run local-env
Select-String -Path .env.local -Pattern '^NEXT_PUBLIC_SUPABASE_URL=http://127\.0\.0\.1' -Quiet
```
完了の条件: `True`。
コミット: `ローカルの Supabase から手元の検証環境の設定を作るスクリプトを足す`

## ステップ T-4: R2 の接続先を上書きできるようにする

テストでは、本物の R2 の代わりに、テストが立てる偽の R2(`127.0.0.1:59000`)を使う。

`web/src/lib/env.ts` の `r2Bucket` の行の下に足す:
```ts
  /** テスト用: R2 の代わりにつなぐ S3 互換の接続先(例: http://127.0.0.1:59000)。本番では設定しない */
  r2Endpoint: () => process.env.R2_ENDPOINT?.replace(/\/+$/, "") || null,
```
`web/src/lib/r2.ts` の28行目あたり:
置き換え前:
```ts
  return new URL(`https://${env.r2AccountId()}.r2.cloudflarestorage.com/${env.r2Bucket()}/${encoded}`);
```
置き換え後:
```ts
  const base = env.r2Endpoint() ?? `https://${env.r2AccountId()}.r2.cloudflarestorage.com`;
  return new URL(`${base}/${env.r2Bucket()}/${encoded}`);
```
同じファイルに IPv4 に固定する `Agent` があれば、そのままでよい(`127.0.0.1` は IPv4)。

`web/.env.example` の R2 の欄に足す:
```
# テスト用: R2 の代わりの S3 互換の接続先。本番では設定しない
R2_ENDPOINT=
```
テスト: `web/src/lib/r2.test.ts` が無ければ作り、あれば足す。`R2_ENDPOINT` を設定したときに URL が `http://127.0.0.1:59000/<bucket>/<key>` になること、未設定のときに `https://<account>.r2.cloudflarestorage.com/...` になることを確かめる(`process.env` を `beforeEach` で設定し、`afterEach` で消す)。

完了の条件: `npm run typecheck` と `npm test` が合格。
コミット: `テストのために R2 の接続先を R2_ENDPOINT で上書きできるようにする`

## ステップ T-5: テストの土台(`web/tests/support/`)

### 5-1. 安全装置 `tests/support/env.ts`
すべてのテストが最初に読み込む。**ローカル以外を向いていたら、何もせずに止める。**
```ts
import { readFileSync } from "node:fs";
import path from "node:path";

export const webRoot = path.resolve(__dirname, "../..");

/** web/.env.local を読む(process.env には書かない) */
export function readLocalEnv(): Record<string, string> {
  const text = readFileSync(path.join(webRoot, ".env.local"), "utf8");
  const out: Record<string, string> = {};
  for (const line of text.split(/\r?\n/)) {
    if (!line.includes("=") || line.startsWith("#")) continue;
    const i = line.indexOf("=");
    out[line.slice(0, i).trim()] = line.slice(i + 1).trim();
  }
  return out;
}

const LOCAL = /^(127\.0\.0\.1|localhost)$/;

/** 本番や外部に書き込まないための確認。1つでも外れたら例外 */
export function assertLocal(env: Record<string, string>): void {
  for (const key of ["NEXT_PUBLIC_SUPABASE_URL", "DATABASE_URL", "R2_ENDPOINT", "NEXT_PUBLIC_SITE_URL"]) {
    const value = env[key];
    if (!value) continue;
    const host = new URL(value.replace(/^postgres(ql)?:/, "http:")).hostname;
    if (!LOCAL.test(host)) throw new Error(`${key} がローカルを向いていません。テストを中止します`);
  }
  for (const key of ["GITHUB_DISPATCH_TOKEN", "RESEND_API_KEY"]) {
    if (env[key]) throw new Error(`${key} が設定されています。テストでは空にしてください`);
  }
}
```
(`DATABASE_URL` はフェーズ1から使う。今のうちから確認の対象に入れておく)

### 5-2. 偽の R2 `tests/support/fake-r2.ts`
S3 互換の最小限のサーバー。メモリに保存する。署名は確かめない。対応する操作:
- `PUT /<bucket>/<key>`: 本文を保存。ヘッダー `x-amz-copy-source: /<bucket>/<from-key>` があれば、その中身を複製(元が無ければ 404)。200 を返す。
- `GET /<bucket>/<key>`: 中身を返す(無ければ 404)。`Content-Type` は保存時のものか `application/octet-stream`。
- `HEAD /<bucket>/<key>`: `Content-Length` を返す(無ければ 404)。
- `DELETE /<bucket>/<key>`: 消す。204。
- `GET /<bucket>?list-type=2&prefix=...`: 該当するキーを `<ListBucketResult><Contents><Key>..</Key><Size>..</Size></Contents>...</ListBucketResult>` の XML で返す。
- キーは URL の `%` エンコードを戻してから扱う。クエリ(`X-Amz-*`)は無視する。

公開する関数: `startFakeR2(port = 59000): Promise<{ stop(): Promise<void>; objects: Map<string, { body: Buffer; contentType: string }> }>`。
単体テスト `tests/support/fake-r2.test.ts` を作り、上の5つの操作を確かめる(このファイルは `test:api` で一緒に動く)。

### 5-3. ログインのドライバー `tests/support/auth-driver.ts`
API テストとスモークテストは、ログインの方法をこのインターフェース越しにだけ使う。**フェーズ4で Better Auth 版を足し、環境変数 `AUTH_DRIVER` で切り替える。**
```ts
export interface Credentials { email: string; password: string; totpSecret?: string }

export interface AuthDriver {
  /** 管理者の鍵でユーザーを作る(メール確認済み)。作ったユーザーの ID を返す */
  createUser(c: Credentials & { displayName: string }): Promise<string>;
  /** TOTP を登録して確認済みにする。秘密鍵を返す */
  enrollTotp(c: Credentials): Promise<string>;
  /** Authorization: Bearer に使うトークン。aal2 を求めたら TOTP まで済ませる */
  bearer(c: Credentials, opts?: { aal2?: boolean }): Promise<string>;
  /** Cookie ヘッダーの値(ブラウザでログインした状態) */
  cookie(c: Credentials, opts?: { aal2?: boolean }): Promise<string>;
}

export function authDriver(): AuthDriver {
  const name = process.env.AUTH_DRIVER ?? "supabase";
  if (name === "supabase") return supabaseAuthDriver();
  throw new Error(`未対応の AUTH_DRIVER: ${name}`);
}
```
`supabaseAuthDriver()` の作り方(同じファイルに書く。接続先と鍵は、API テストでは `readLocalEnv()`、スモークテストでは本番の値を受け取れるよう、引数 `{ url, anonKey, serviceRoleKey? }` で渡す形にする):
- `createUser`: `POST {url}/auth/v1/admin/users`(ヘッダー `apikey`・`Authorization: Bearer <serviceRoleKey>`)、本文 `{ email, password, email_confirm: true, user_metadata: { full_name: displayName } }`。返り値の `id`。
- `bearer`: `POST {url}/auth/v1/token?grant_type=password`(`apikey: anonKey`)、本文 `{ email, password }` → `access_token`。`aal2` のときは、続けて `GET {url}/auth/v1/user` の `factors` から `factor_type === "totp" && status === "verified"` のものを選び、`POST /auth/v1/factors/{id}/challenge` → `POST /auth/v1/factors/{id}/verify`(本文 `{ challenge_id, code }`)の返り値の `access_token` を使う。
- `enrollTotp`: パスワードでログイン → `POST /auth/v1/factors`(本文 `{ factor_type: "totp", friendly_name: "test" }`)→ 返り値の `totp.secret` → challenge → verify(コードは下の `freshTotp`)。
- `cookie`: **Web と同じライブラリでログインして、書かれた Cookie を集める**(Cookie の形式を自分で組み立てない):
  ```ts
  import { createServerClient } from "@supabase/ssr";
  import { authCookieName } from "../../src/lib/auth-cookie";

  const jar = new Map<string, string>();
  const client = createServerClient(url, anonKey, {
    cookieOptions: { name: authCookieName(url) },
    cookies: {
      getAll: () => [...jar].map(([name, value]) => ({ name, value })),
      setAll: (list) => { for (const { name, value } of list) { if (value) jar.set(name, value); else jar.delete(name); } },
    },
  });
  const { error } = await client.auth.signInWithPassword({ email, password });
  if (error) throw error;
  if (opts?.aal2) {
    const { data } = await client.auth.mfa.listFactors();
    const factor = data?.totp.find((f) => f.status === "verified");
    if (!factor) throw new Error("TOTP が登録されていません");
    const { error: e2 } = await client.auth.mfa.challengeAndVerify({ factorId: factor.id, code: await freshTotp(c.totpSecret!) });
    if (e2) throw e2;
  }
  return [...jar].map(([name, value]) => `${name}=${value}`).join("; ");
  ```
  スモークテストでは `url` が `https://store.kawamonn.com` なので、Cookie の名前は本番の Web と同じになる。

TOTP のコード `tests/support/totp.ts`:
```ts
import { authenticator } from "otplib";

const lastStep = new Map<string, number>();

/** 同じ秘密鍵で、前回と同じ時間枠(30秒)のコードを二度使わない(再利用を拒む実装に備える) */
export async function freshTotp(secret: string): Promise<string> {
  for (;;) {
    const step = Math.floor(Date.now() / 30000);
    if (lastStep.get(secret) !== step) {
      lastStep.set(secret, step);
      return authenticator.generate(secret);
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
}
```
(`otplib` の版によって import の形が違う。インストールした版の README に合わせる)

### 5-4. テスト用のデータ `tests/support/fixtures.ts`
`setupFixtures()` を作る。DB をまっさらにしたあとで、次の人物とデータを作り、ID などを `tests/.fixtures.json` に書く(テストはこれを読む)。

人物(パスワードはすべて `Test-password-1!`、メールは `<key>@example.test`):

| key | 役割 | 作り方 |
|---|---|---|
| `user1`〜`user5` | 一般ユーザー(日記のシナリオ用) | `createUser` |
| `dev1` | 承認済みの開発者 | `createUser` + `developers` に `status='approved'` の行 |
| `dev2` | 承認済みの開発者(他人のアプリの確認用) | 同上 |
| `admin` | 管理者(TOTP なし) | `createUser` + `profiles.role='admin'` |
| `adminTotp` | 管理者(TOTP あり) | 上と同じ + `enrollTotp` |
| `banned` | BAN 済みの一般ユーザー | `createUser` + Web の BAN と同じ状態(`profiles.banned_at` と Auth の BAN)。**作り方は `src/lib/moderation.ts` の BAN の処理を読んで、同じ2つを行う** |
| `deletable` | アカウント削除の確認用 | `createUser` |

データ(`pg` で `DATABASE_URL` に直接 SQL を流す。NOT NULL の列は `supabase/migrations/` を読んで埋める):

| 名前 | 中身 |
|---|---|
| カテゴリ | `supabase/seed.sql` に入っているものを使う(`supabase db reset` で入る) |
| アプリ `fx-published` | `dev1` の、公開中のアプリ。公開済みのリリース(`version_code=1`)と、承認待ち(`scanned`)のリリース(`version_code=2`)を持つ |
| アプリ `fx-draft` | `dev1` の下書き |
| アプリ `fx-suspended` | `dev1` の停止中のアプリ |
| アプリ `fx-admin-only` | `admin` の、管理者専用(`admin_only=true`)の公開中のアプリと公開済みリリース |
| アプリ `fx-dev2` | `dev2` の公開中のアプリと公開済みリリース |
| 日記 | 下の「日記のシナリオ」に必要な投稿・友達関係・ブロック |
| 通報 | `user2` が `user1` の公開投稿を通報した行(`diary_reports`) |

DB をまっさらにする方法: `npx supabase db reset --workdir ..`(全マイグレーションと `seed.sql` を入れ直す。30秒ほどかかる)。

### 5-5. テスト用サーバー `tests/support/global-setup.ts`
`vitest.api.config.ts` の `globalSetup`。順番:
1. `readLocalEnv()` → `assertLocal()`。
2. 偽の R2 を起動(`startFakeR2(59000)`)。
3. DB をまっさらにする → `setupFixtures()`。
4. `SKIP_BUILD=1` でなければ `npx next build`(環境変数は `readLocalEnv()` の値を `process.env` に**上乗せ**して渡す)。
5. `npx next start -p 3100 -H 127.0.0.1` を子プロセスで起動(同じ環境変数)。出力は `tests/.server.log` に書く。
6. `http://127.0.0.1:3100/api/v1/categories` が 200 を返すまで待つ(最大60秒)。
7. 戻り値の関数(teardown)で、Next.js と偽の R2 を止める。

`web/vitest.api.config.ts`:
```ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["tests/api/**/*.test.ts", "tests/support/**/*.test.ts"],
    globalSetup: ["tests/support/global-setup.ts"],
    fileParallelism: false,
    testTimeout: 60_000,
    hookTimeout: 300_000,
  },
});
```

`tests/support/http.ts` に、テストから使う小さな道具を作る:
- `api(path, { method, persona, via: "bearer" | "cookie", aal2, body, headers })` → `{ status, json, headers }`。`persona` があれば `authDriver()` でトークンか Cookie を取ってから呼ぶ(同じ人物・同じ方法の結果は覚えておき、毎回ログインしない)。
- `fixtures()` → `tests/.fixtures.json` の中身。
- `resetFixtures()` → 5-4 を最初からやり直す(データを壊すテストの前に呼ぶ)。

完了の条件: 中身が `expect(true).toBe(true)` だけの `tests/api/boot.test.ts` を置いて `npm run test:api` が合格する(Next.js が起動し、データが入る)。確かめたら boot.test.ts は消す。
コミット: `API テストの土台(安全装置・偽の R2・データの用意・ログインのドライバー)を作る`

## ステップ T-6: API テスト

**このステップでは、アプリのコードを変えない。** テストの期待値は「今の実装がそう動く」ことを書く。コードを読んで予想した結果と実際が違ったら、実際の動きを期待値にする。ただし、**次のどれかに当たる動きを見つけたら、期待値にせず止まって報告する**(穴をテストで固定しないため):
- ログインしていない人・一般ユーザーが、管理 API・開発者 API・他人のデータで 2xx になる。
- BAN された人が書き込みで 2xx になる。
- 他人の非公開の日記・下書きのアプリが見える。

### 6-1. 権限の表 `tests/api/authz-matrix.ts` と `tests/api/authz.test.ts`
`src/app/api/**/route.ts` の**すべての** `GET`/`POST`/`PUT`/`PATCH`/`DELETE` について、次の人物ごとの HTTP ステータスを1行で書く。

人物の列: `anon`(ログインなし)・`user`(`user1`)・`banned`・`dev`(`dev1`)・`otherDev`(`dev2`。`dev1` のアプリに対して)・`admin`・`adminAal1`(`adminTotp` が TOTP 前)・`adminAal2`(`adminTotp` が TOTP 後)。

```ts
export interface MatrixRow {
  route: string;               // "src/app/api" からのパス。例: "admin/apps/[id]/status"
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  via: "bearer" | "cookie";    // その API がどちらでログインを受け付けるか(コードを読んで決める)
  path: (fx: Fixtures) => string;          // 実際に呼ぶ URL のパス
  body?: (fx: Fixtures) => unknown;        // 必要なら、通る形の本文
  destructive?: boolean;                   // 呼ぶとデータが変わるなら true(前後で resetFixtures する)
  expect: Partial<Record<"anon" | "user" | "banned" | "dev" | "otherDev" | "admin" | "adminAal1" | "adminAal2", number>>;
}
```
- `internal/*` は人物ではなく「署名なし → 401」「正しい署名 → 2xx」を書く(署名の作り方は各ルートのコードと既存の `*.test.ts` を読む)。
- 1つのルートでも、ログインを Cookie と Bearer の両方で受け付けるもの(`requireAdmin` を使う管理 API)は、`via` を変えて2行書く。
- `authz.test.ts` は、表の全行 × 全人物を `it.each` で回し、ステータスだけを確かめる。

**表に漏れが無いことを確かめるテスト** `tests/api/routes-inventory.test.ts`: `src/app/api/**/route.ts` を読み、`export async function (GET|POST|PUT|PATCH|DELETE)` を全部拾い、表に同じ `route` と `method` の行が無ければ失敗させる。以降のフェーズで API を足したときに、表を書き忘れないようにするため。

### 6-2. 応答の形のスナップショット `tests/api/shapes.test.ts`
次の応答の本文を `toMatchSnapshot()` で記録する。比べる前に `normalize()` で、毎回変わる値を記号に置き換える:
- UUID(`/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i`)→ `"<uuid>"`
- 日時は**形式ごとに別の記号**にする(形式が変わったら気づけるように):
  - `/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?\+00:00$/` → `"<ts:+00:00>"`
  - `/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/` → `"<ts:Z>"`
  - `/^\d{4}-\d{2}-\d{2}$/` → `"<date>"`
- 署名付き URL のクエリ(`X-Amz-*`)は消す。

対象: `v1/index`・`v1/home`・`v1/apps`・`v1/apps/fx-published`・`v1/categories`、管理 API の一覧(`admin/overview`・`admin/apps`・`admin/releases`・`admin/users`・`admin/users/[id]`・`admin/developers`・`admin/reports`・`admin/diary/entries`・`admin/audit`・`admin/private-apps`)、開発者 API(`developer/apps` の GET があれば)。スナップショットのファイル(`tests/api/__snapshots__/`)はコミットする。

### 6-3. 業務の流れ `tests/api/flows/*.test.ts`
1つのファイルに1つの流れ。各ファイルの最初に `resetFixtures()`。
- `developer-release.test.ts`: `dev1` がアプリを作る → 情報を直す → アイコンを上げる(本文は 512x512 の PNG。`sharp` で作る)→ 表示されるアイコンの URL を GET して 200 → リリースを作る → 返ってきた署名付き URL に APK のバイト列を PUT(偽の R2 に入る)→ 「アップロード済み」を知らせる → リリースの状態が変わる。
- `admin-review.test.ts`: `admin` が承認待ちのリリースを承認・却下できる、アプリの状態を変えられる、監査ログに残る。
- `moderation.test.ts`: `admin` が `user1` を BAN → `user1` の Bearer で開発者 API・アカウント API が拒否される → BAN 解除で戻る。日記の通報の一覧と対応、投稿の削除。
- `download.test.ts`: `v1/releases/[id]/download` が署名付き URL へ転送する。ダウンロード数が増える(同じ端末からの2回目は増えない)。
- `account-delete.test.ts`: `deletable` がアカウントを削除 → もうログインできない。
- `internal.test.ts`: 検査結果の通知(正しい署名でリリースの状態が変わる、署名が違えば 401)。運営への通知(署名が違えば 401)。
- `pages.test.ts`: ページの表示。`/`・`/apps/fx-published` が 200。`/admin` は未ログインならログイン画面へ転送、`admin` の Cookie なら 200。`/developer` は `dev1` の Cookie で 200。`/account` は `user1` の Cookie で 200。

### 6-4. 日記のシナリオ `tests/scenarios/diary.ts` と `tests/api/diary.test.ts`
日記は、今はアプリが PostgREST を直接呼んでいる。**フェーズ3で Web の API に変わるので、シナリオとドライバーを分ける。**
```ts
export interface DiaryDriver {
  timeline(viewer: string, limit?: number): Promise<{ id: string; user_id: string }[]>;
  createEntry(author: string, e: { id?: string; body: string; visibility: "private" | "friends" | "public"; mentioned_user_ids?: string[] }): Promise<string>;
  deleteEntry(author: string, id: string): Promise<number>;           // HTTP ステータス
  sendFriendRequest(from: string, toUserId: string): Promise<number>;
  respondFriendRequest(actor: string, friendshipId: string, accept: boolean): Promise<number>;
  block(actor: string, targetUserId: string): Promise<number>;
  unblock(actor: string, targetUserId: string): Promise<number>;
  report(actor: string, entryId: string, reason: string): Promise<number>;
  deleteMyAccount(actor: string): Promise<number>;
}
```
`actor`・`viewer`・`author` は人物の key(`user1` など)。フェーズTでは `postgrestDiaryDriver()`(`{url}/rest/v1/...` を Bearer で呼ぶ。呼び方は日記アプリの `DiaryRepository.kt` と同じにする)を作る。環境変数 `DIARY_DRIVER`(既定 `postgrest`)で選ぶ。

シナリオ(すべて書く。人物の関係: `user1` と `user2` は友達、`user1` は `user5` をブロック、`user3`・`user4` は誰とも友達でない):

| # | 準備 | 確かめること |
|---|---|---|
| D-1 | `user1` が private・friends・public・friends かつ `user3` をメンション、の4件を投稿 | `user1` のタイムラインに4件すべて |
| D-2 | 同上 | `user2`(友達)には friends・public・(メンションつきの friends も友達なので見える)の3件 |
| D-3 | 同上 | `user3`(メンションされた)には public とメンションつきの2件 |
| D-4 | 同上 | `user4`(他人)には public の1件だけ |
| D-5 | 同上 | `user5`(ブロックされた)には0件(public も見えない) |
| D-6 | 同上 | ログインなしは0件 |
| D-7 | `user3` が `user4` に友達申請 | `user3`(申請者)は自分で承認できない(4xx)。`user4` は承認できる。承認後、`user4` は `user3` の friends の投稿が見える |
| D-8 | `user1` が `user2` をブロック | 友達関係が消え、互いの投稿が見えなくなる。解除後、public は見える(友達には戻らない) |
| D-9 | `user5` が `user1` に友達申請 | ブロックされているので失敗する |
| D-10 | `user2` が `user1` の public を通報 | 成功。自分の投稿の通報・見えない投稿の通報は失敗 |
| D-11 | `user1` が自分の投稿を消す / `user2` が `user1` の投稿を消す | 自分のは消える。他人のは消えない(投稿が残っていることを `user1` のタイムラインで確かめる) |
| D-12 | `user4` がアカウントを削除 | 成功し、`user4` の投稿が消える。`dev1`・`admin` は削除できない(4xx) |
| D-13 | `banned` が投稿する | 今の実装の結果を記録する(書けてしまうなら、上の「止まって報告」に当たる) |

完了の条件(T-6 全体): `npm run test:api` が**3回続けて**合格する(不安定なテストがないことの確認)。
コミット: 6-1〜6-4 ごとに1つずつ。

## ステップ T-7: 運用スクリプトと本番の調査(本番は読むだけ)

1. [ops.md](ops.md) の「2. スクリプトの中身」のとおりに、`.gitattributes`・`web/scripts/ops/ops.psm1`・`web/scripts/ops/remote/*.sh`(`backup-db.sh`・`psql-file.sh`・`psql-query.sh`・`set-env.sh`・`web-ctl.sh`・`discover.sh`・`restore-db.sh`)を作る。`.sh` は ASCII 文字だけで書く。
2. 改行の確認: `git -C C:\dev\appstore add --renormalize .` のあと、`git -C C:\dev\appstore ls-files --eol web/scripts/ops/remote/` の各行が `i/lf` であること。
3. サーバーの構成を調べる(値は出ない):
   ```powershell
   Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
   Invoke-RemoteScript "discover.sh" @((Get-OpsSetting "DEPLOY_DIR"))
   ```
   出力から次を決めて、`web/.env.prod.local` に書く:
   - `PROD_DB_CONTAINER`: `db_container=` の値(イメージが `supabase/postgres` のもの)。
   - `SUPABASE_DOCKER_DIR`: `compose_dir=` の値。
   - `DEPLOY_ENV_FILE`: `web/.env...` の `->` の右側(リンクの実体)。Web が読んでいる環境変数ファイル。**候補が2つ以上ある、または見つからないときは止まって報告する。**
   - `PROD_DB_SUPERUSER`: 次の結果で決める。
     ```powershell
     $env:PROD_DB_SUPERUSER = "supabase_admin"; Invoke-ProdQuery "select 1"
     ```
     `1` が出れば `supabase_admin`。エラーなら `postgres` で同じことを試す。どちらもだめなら止まって報告する。
4. 本番に、今の6つのマイグレーションが適用済みであることを確かめる:
   ```powershell
   Invoke-ProdQuery "select to_regclass('public.apps') is not null, to_regclass('public.diary_entries') is not null, to_regclass('public.diary_blocks') is not null, to_regclass('public.admin_audit_log') is not null, exists (select 1 from information_schema.columns where table_name = 'app_releases' and column_name = 'policy_verdict'), pg_get_functiondef('public.is_admin'::regproc) like '%banned_at%'"
   ```
   完了の条件: `t|t|t|t|t|t`。1つでも `f` なら止まって報告する。

完了の条件(T-7 全体): 上の4つ。`Backup-ProdDb "phaseT-test"` が成功し、`C:\dev\backups\ttech-store\` にダンプができる。
コミット: `本番の運用スクリプト(バックアップ・SQL の実行・デプロイ)を足す`

## ステップ T-8: 適用済みマイグレーションの記録表(本番の操作)

`supabase/migrations/20261027000000_ops_schema.sql`:
```sql
-- 運用: どのマイグレーションを本番に適用したかを記録する表(docs/supabase-removal/ops.md)
create schema if not exists ops;
revoke all on schema ops from public;

create table if not exists ops.applied_migrations (
  name text primary key,
  applied_at timestamptz not null default now()
);

insert into ops.applied_migrations (name) values
  ('20260920000000_init'),
  ('20260922000000_diary'),
  ('20260924000000_diary_safety'),
  ('20260925000000_admin'),
  ('20260926000000_developer_platform'),
  ('20261022000000_security_hardening'),
  ('20261027000000_ops_schema')
on conflict (name) do nothing;
```
`supabase/rollback/20261027000000_ops_schema.down.sql`:
```sql
drop schema if exists ops cascade;
```
ローカルで確かめる: `npm run test:db` が合格。

本番に適用する(この1回だけは記録表がまだ無いので `Invoke-ProdSqlFile` を使う):
```powershell
Backup-ProdDb "phaseT-step8"
Invoke-ProdSqlFile C:\dev\appstore\supabase\migrations\20261027000000_ops_schema.sql
Invoke-ProdQuery "select count(*) from ops.applied_migrations"
```
完了の条件: `7`。
コミット: `適用済みのマイグレーションを本番で記録する表を足す`

## ステップ T-9: 本番のスモークテスト(本番の操作)

### 9-1. スモーク用のアカウントを本番に作る
`web/scripts/ops/create-smoke-accounts.mjs` を作る。`TTECH_ENV_FILE=.env.prod.local` で `scripts/lib/cli-env.mjs` の `loadEnvLocal()` を使って本番の値を読み、`tests/support/auth-driver.ts` と同じ手順(Supabase の管理 API)で:
- `smoke-user@store.kawamonn.com`(一般)と `smoke-admin@store.kawamonn.com`(管理者)を作る。パスワードは `crypto.randomBytes(24).toString("base64url")`。
- `smoke-admin` の `profiles.role` を `admin` にする(`PATCH {url}/rest/v1/profiles?id=eq.<id>`、本番の service role の鍵で)。
- `smoke-admin` に TOTP を登録する。
- 作った値を `web/.env.prod.local` に追記する: `SMOKE_USER_EMAIL`・`SMOKE_USER_PASSWORD`・`SMOKE_ADMIN_EMAIL`・`SMOKE_ADMIN_PASSWORD`・`SMOKE_ADMIN_TOTP_SECRET`。**画面には表示しない。** 既に同じメールのユーザーがいたら、作らずに止まる。
(`.mjs` から TypeScript の `auth-driver.ts` は読めないので、同じ手順をこのスクリプトの中に書く)

```powershell
Backup-ProdDb "phaseT-step9"
Set-Location C:\dev\appstore\web
$env:TTECH_ENV_FILE = ".env.prod.local"; node scripts\ops\create-smoke-accounts.mjs; Remove-Item Env:\TTECH_ENV_FILE
```

### 9-2. スモークテスト `tests/smoke/smoke.test.ts` と `vitest.smoke.config.ts`
- `vitest.smoke.config.ts`: `include: ["tests/smoke/**/*.test.ts"]`、`fileParallelism: false`、`testTimeout: 60_000`。globalSetup は使わない(サーバーを起動しない)。
- テストの最初に `web/.env.prod.local` から `NEXT_PUBLIC_SUPABASE_URL`・`NEXT_PUBLIC_SUPABASE_ANON_KEY`・`SMOKE_*` だけを読む。**service role の鍵は読まない**(スモークテストは利用者と同じ権限だけで確かめる)。`SMOKE_AUTH_DRIVER`(既定 `supabase`)・`SMOKE_DIARY_DRIVER`(既定 `postgrest`)があればドライバーをそれに切り替える。
- 確かめる内容は [ops.md](ops.md) の「7. 本番のスモークテスト」のとおり。日記の投稿は `smoke-user` の private の投稿にし、最後に必ず消す(`afterAll`)。

完了の条件: `npm run test:smoke` が合格する。
コミット: `本番のスモークテストとスモーク用アカウントの作成スクリプトを足す`

## ステップ T-10: CI に API テストを足す

`.github/workflows/ci.yml` に、既存の `database` ジョブを参考にジョブ `web-api` を足す:
1. `actions/checkout@v4` → `supabase/setup-cli@v1`(`version: latest`)→ `actions/setup-node@v4`(既存の web のジョブと同じ Node の版)。
2. `supabase start -x edge-runtime,imgproxy,logflare,vector,studio,mailpit,realtime,supavisor`
3. `working-directory: web` で `npm ci` → `node scripts/write-local-env.mjs` → `npm run test:api`。
4. 失敗したら `web/tests/.server.log` を artifact として上げる。

`write-local-env.mjs` は CI では `npx supabase` が使える(`npm ci` で入る)。
完了の条件: push したブランチで GitHub Actions の `web-api` が緑。
```powershell
git -C C:\dev\appstore push -u origin supabase-removal/phaseT-tests
gh run watch --repo kawamonn91/ttech-store --exit-status
```

## ステップ T-11: RLS の一覧を作る(フェーズ7の準備)

`web/scripts/rls-inventory.mjs`: `.env.local` の `DATABASE_URL`(ローカル)に `pg` でつなぎ、
```sql
select schemaname, tablename, policyname, cmd, roles::text, qual, with_check
from pg_policies where schemaname in ('public', 'storage') order by schemaname, tablename, policyname;
```
の結果を `docs/supabase-removal/rls-inventory.md` に Markdown の表で書く(各セルの `|` は `\|` に置き換える)。続けて、`security definer` の関数の一覧(`select proname, pg_get_function_identity_arguments(oid) from pg_proc where prosecdef and pronamespace = 'public'::regnamespace`)も同じファイルに書く。

完了の条件: ファイルができ、ポリシーの行数が 61 前後。
コミット: `RLS ポリシーの一覧を作るスクリプトと、その結果を足す`

## ステップ T-12: 仕上げ

本番に出す前の合格条件(このフェーズは Web のコードを本番に出さないが、main に入れる前に確かめる):
- [ ] `npm run typecheck`・`npm test`・`npm run test:db`・`npm run build` が合格
- [ ] `npm run test:api` が3回続けて合格
- [ ] `npm run test:smoke` が合格
- [ ] GitHub Actions が全部緑

満たしたら:
```powershell
git -C C:\dev\appstore fetch origin
git -C C:\dev\appstore rebase origin/main
git -C C:\dev\appstore switch main
git -C C:\dev\appstore merge --ff-only supabase-removal/phaseT-tests
git -C C:\dev\appstore push origin main
```
このフェーズの変更(`r2.ts` の上書き口)は本番では使われない(`R2_ENDPOINT` を設定しない)ので、デプロイは不要。ただし次のフェーズの最初のデプロイに含まれる。

持ち主への報告に書くこと:
- クラウドの Supabase は、手元の設定(`.env.cloud.local`)以外からは使われなくなった。**持ち主に、Supabase のダッシュボードでクラウドのプロジェクトを一時停止(Pause)してもらう。** 削除はフェーズ6の最後。

---

## 進み具合

| ステップ | 日付 | 結果・メモ |
|---|---|---|
| T-0 | | マージ前の main: `` |
| T-1 | | |
| T-2 | | |
| T-3 | | |
| T-4 | | |
| T-5 | | |
| T-6 | | |
| T-7 | | PROD_DB_CONTAINER= / PROD_DB_SUPERUSER= / DEPLOY_ENV_FILE は .env.prod.local に記録済みか |
| T-8 | | |
| T-9 | | |
| T-10 | | |
| T-11 | | |
| T-12 | | |
