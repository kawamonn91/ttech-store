# フェーズ0: 管理アプリ・日記アプリの接続先を直して出し直す

## なぜ
配布中の管理アプリ(1.0.2)と日記アプリ(1.1.2)は、以前のクラウドの Supabase に接続する設定のままです([../supabase-removal-plan.md](../supabase-removal-plan.md) のフェーズ0の調査結果)。ストアは 2026-10 に自前のサーバーの Supabase(`https://store.kawamonn.com`)へ移ったので、
- 管理アプリは、ログインはクラウド・操作は新しいサーバーという食い違いで、操作できないとみられます。
- 日記アプリは、新しいサーバーのデータを見ていません。

ストアアプリで 2026-10-25 に行った修正(コミット `1ab38f7`)と同じく、既定の接続先と公開鍵(anon キー)を新しいサーバーのものにして、新しい版を出します。あわせて、本番への公開のたびに `web/.env.local` を書き換えずに済むよう、公開スクリプトが読む設定ファイルを選べるようにします(以前、書き換えたまま戻し忘れたことがあるため)。

## 前提
- [README.md](README.md) の「作業のルール」を読んでいること。
- 本番の設定ファイル `C:\dev\appstore\web\.env.production.local` がある(持ち主が用意済み)。**ステップ 0-3 で `.env.prod.local` に名前を変える。** `.env.production.local` という名前は、Next.js が `npm run build`・`next start` のときに自動で読み込むため、手元でビルドやテストをすると本番の鍵が混ざって危ない。`.env.prod.local` は Next.js が読まない名前で、`.gitignore` の `.env*` にも含まれる。
- 管理アプリの署名鍵 `C:\dev\appstore\native-apps\ttech-admin\keystore.properties` と、日記アプリの署名鍵 `C:\dev\diary-app\keystore.properties` がある。
- 日記アプリの `C:\dev\diary-app\local.properties` に `googleWebClientId` がある。
- このフェーズは main に直接コミットする(ブランチは切らない)。

## 変えないもの
- 管理アプリの `adminWebBase`(すでに `https://store.kawamonn.com`)。
- 署名の設定。署名鍵が変わると、ストアが新しい版を却下する(`publish-app.mjs` の検査)。

---

## ステップ 0-1: 作業前の確認

```powershell
git -C C:\dev\appstore switch main
git -C C:\dev\appstore pull --ff-only
git -C C:\dev\appstore status --short
git -C C:\dev\diary-app switch main
git -C C:\dev\diary-app pull --ff-only
git -C C:\dev\diary-app status --short
```
完了の条件: どちらの `status --short` も何も表示しない。表示されたら、止まって報告する(他の人の作業中の変更を混ぜないため)。

マージ前の main の位置を記録する(戻し方で使う):
```powershell
git -C C:\dev\appstore rev-parse origin/main
git -C C:\dev\diary-app rev-parse origin/main
```
この2つの値を、このファイルの末尾の「進み具合」の表の 0-1 の行に書く。

## ステップ 0-2: 公開スクリプトが読む設定ファイルを選べるようにする

### 変更 1: `web/scripts/lib/cli-env.mjs`
`loadEnvLocal` を、環境変数 `TTECH_ENV_FILE` で読むファイルを選べるようにする。

置き換え前:
```js
/** .env.local を読み込み、未設定の環境変数にだけ反映する */
export function loadEnvLocal() {
  const file = path.join(webRoot, ".env.local");
  if (!existsSync(file)) return;
```
置き換え後:
```js
/**
 * 設定ファイルを読み込み、未設定の環境変数にだけ反映する。
 * 既定は web/.env.local。環境変数 TTECH_ENV_FILE で別のファイルを選べる
 * (例: 本番への公開は TTECH_ENV_FILE=.env.prod.local。.env.local を書き換えずに済む)。
 * 相対パスは web/ から、絶対パスはそのまま解釈する。読んだファイルのパスを返す(無ければ null)。
 */
export function loadEnvLocal(fileName = process.env.TTECH_ENV_FILE || ".env.local") {
  const file = path.resolve(webRoot, fileName);
  if (!existsSync(file)) return null;
```
同じ関数の末尾(`for` のループの後)に `return file;` を足す。

### 変更 2: `web/scripts/publish-app.mjs`
1. ファイル内の `function loadEnvLocal() { ... }`(32〜43行目あたり、`/** .env.local を読み込む` のコメントから閉じかっこまで)を**削除**する。
2. import に次の1行を足す(`import { inspect } ...` の下):
   ```js
   import { loadEnvLocal } from "./lib/cli-env.mjs";
   ```
3. `main()` の先頭の `loadEnvLocal();` を、次に置き換える:
   ```js
   const envFile = loadEnvLocal();
   console.log(`設定ファイル: ${envFile ?? "(なし)"} / 接続先: ${new URL(process.env.NEXT_PUBLIC_SUPABASE_URL ?? "http://unset").host}`);
   ```
   (どの環境に公開しようとしているかを、実行のたびに画面で確かめられるようにする。鍵は表示しない)

### 変更 3: テスト `web/scripts/lib/cli-env.test.mjs`(新規)
```js
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { loadEnvLocal } from "./cli-env.mjs";

describe("loadEnvLocal", () => {
  afterEach(() => {
    delete process.env.TTECH_TEST_VALUE;
    delete process.env.TTECH_ENV_FILE;
  });

  it("TTECH_ENV_FILE で指定したファイルを読む", () => {
    const dir = mkdtempSync(path.join(tmpdir(), "cli-env-"));
    const file = path.join(dir, "test.env");
    writeFileSync(file, "TTECH_TEST_VALUE=from-file\n");
    process.env.TTECH_ENV_FILE = file;
    expect(loadEnvLocal()).toBe(file);
    expect(process.env.TTECH_TEST_VALUE).toBe("from-file");
  });

  it("すでに設定されている環境変数は上書きしない", () => {
    const dir = mkdtempSync(path.join(tmpdir(), "cli-env-"));
    const file = path.join(dir, "test.env");
    writeFileSync(file, "TTECH_TEST_VALUE=from-file\n");
    process.env.TTECH_TEST_VALUE = "already";
    loadEnvLocal(file);
    expect(process.env.TTECH_TEST_VALUE).toBe("already");
  });

  it("ファイルが無ければ null を返す", () => {
    expect(loadEnvLocal(path.join(tmpdir(), "does-not-exist.env"))).toBeNull();
  });
});
```

### 確認
```powershell
Set-Location C:\dev\appstore\web
npm test
```
完了の条件: すべて合格。`cli-env.test.mjs` の3件が含まれている。

コミット: `公開スクリプトが読む設定ファイルを TTECH_ENV_FILE で選べるようにする`

## ステップ 0-3: 本番の設定ファイルの名前を変え、ウイルススキャンのキーを足す

1. 名前を変える(理由は「前提」に書いたとおり)。
2. 本番の設定には `VT_API_KEY` が無く、本番への公開ではウイルススキャンが行われない。`web/.env.local` の同じ行を写す(値は表示しない)。
```powershell
Set-Location C:\dev\appstore\web
if (Test-Path .env.production.local) {
  if (Test-Path .env.prod.local) { throw ".env.prod.local がすでにあります。どちらが正しいか分からないので止まります" }
  Rename-Item .env.production.local .env.prod.local
}
if (Test-Path .env.production.local) { throw "名前を変えられませんでした" }
git -C C:\dev\appstore check-ignore -q web/.env.prod.local; if ($LASTEXITCODE -ne 0) { throw ".env.prod.local が .gitignore の対象になっていません" }
$prod = ".env.prod.local"
if (-not (Select-String -Path $prod -Pattern '^VT_API_KEY=' -Quiet)) {
  $line = Select-String -Path .env.local -Pattern '^VT_API_KEY=' | Select-Object -First 1
  if (-not $line) { throw ".env.local に VT_API_KEY がありません" }
  Add-Content -Path $prod -Value $line.Line -Encoding UTF8
}
(Select-String -Path $prod -Pattern '^VT_API_KEY=' -Quiet)
```
完了の条件: 最後の行が `True`。コミットはしない(`.env*` はコミットしない)。

## ステップ 0-4: 新しいサーバーの認証の設定を確かめる

新しい接続先と公開鍵の組み合わせが受け付けられ、日記アプリが使う Google ログインが有効になっていることを、アプリと同じリクエストで確かめる。公開鍵は `store-app/app/build.gradle.kts` の `supabaseAnonKey` の既定値(`?: "eyJ...` の文字列)を使う。これは公開してよい値で、すでにリポジトリに入っている。
```powershell
$gradle = Get-Content C:\dev\appstore\store-app\app\build.gradle.kts -Raw
$anon = [regex]::Match($gradle, 'supabaseAnonKey[^\n]*\n\s*\?:\s*"([^"]+)"').Groups[1].Value
if (-not $anon.StartsWith("eyJ")) { throw "公開鍵を読み取れませんでした" }
$settings = Invoke-RestMethod -Uri "https://store.kawamonn.com/auth/v1/settings" -Headers @{ apikey = $anon }
"google=$($settings.external.google) email=$($settings.external.email) disable_signup=$($settings.disable_signup) autoconfirm=$($settings.mailer_autoconfirm)"
```
完了の条件: `google=True email=True` が表示される。`google=False` なら、日記アプリの Google ログインが使えないので**止まって報告する**(サーバーの Supabase の Google の設定は持ち主が行う)。
表示された4つの値を「進み具合」の 0-4 の行に書く(フェーズ4で使う)。

## ステップ 0-5: 管理アプリの接続先を直す

ファイル: `C:\dev\appstore\native-apps\ttech-admin\build.gradle.kts`

1. `supabaseUrl` の既定値を変える。
   置き換え前: `val supabaseUrl: String = localOrProject("supabaseUrl") ?: "https://bniehgjuylkgxbstmjdh.supabase.co"`
   置き換え後: `val supabaseUrl: String = localOrProject("supabaseUrl") ?: "https://store.kawamonn.com"`
2. `supabaseAnonKey` の既定値(`?: "sb_publishable_...`)を、ステップ 0-4 の `$anon` と同じ文字列(ストアアプリの既定値)に置き換える。
3. その上のコメント `/** anon キーは「公開してよい」設計の値(ストアアプリ・Web版と同じ。データはRLSで保護される) */` を、次に置き換える:
   ```kotlin
   /**
    * anon キーは「公開してよい」設計の値(ストアアプリ・Web版と同じ。データはRLSで保護される)。
    * 2026-10 に Supabase をストアのサーバーへ移したため、接続先とキーもそれに合わせる
    * (以前のクラウドの Supabase でログインしたトークンは、Web の管理 API が受け付けない)。
    */
   ```
4. 版を上げる: `versionCode = 3` → `versionCode = 4`、`versionName = "1.0.2"` → `versionName = "1.0.3"`。

テストとビルド:
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Set-Location C:\dev\appstore\native-apps
.\gradlew.bat :ttech-admin:testDebugUnitTest :ttech-admin:assembleRelease --console=plain
$LASTEXITCODE
```
完了の条件: 最後の行が `0`。`native-apps\ttech-admin\build\outputs\apk\release\ttech-admin-release.apk` ができている。

埋め込まれた接続先の確認(下の「APK の接続先を調べる関数」を貼ってから実行する):
```powershell
Get-ApkEndpoints C:\dev\appstore\native-apps\ttech-admin\build\outputs\apk\release\ttech-admin-release.apk
```
完了の条件: `https://store.kawamonn.com` が表示され、`supabase.co` を含む行が**ない**。

### APK の接続先を調べる関数
```powershell
function Get-ApkEndpoints([string]$ApkPath) {
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  $zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $ApkPath))
  try {
    $found = @{}
    foreach ($entry in $zip.Entries | Where-Object { $_.Name -like "*.dex" }) {
      $stream = $entry.Open(); $ms = New-Object IO.MemoryStream
      $stream.CopyTo($ms); $stream.Dispose()
      $text = [Text.Encoding]::ASCII.GetString($ms.ToArray())
      foreach ($m in [regex]::Matches($text, 'https?://[A-Za-z0-9.\-]*(supabase\.co|kawamonn\.com)[A-Za-z0-9/._\-]*')) { $found[$m.Value] = $true }
    }
    $found.Keys | Sort-Object
  } finally { $zip.Dispose() }
}
```

コミット(`C:\dev\appstore`): `管理アプリ 1.0.3: 既定の接続先を store.kawamonn.com にする`

## ステップ 0-6: 日記アプリの接続先を直す

ファイル: `C:\dev\diary-app\app\build.gradle.kts`

1. `supabaseUrl` の既定値を `"https://store.kawamonn.com"` にする(0-5 と同じ書き換え)。
2. `supabaseAnonKey` の既定値を、0-5 と同じ文字列にする。
3. 先頭のコメントの `ストアアプリ(store-app)と同じSupabaseプロジェクトを使う(新規インフラ不要の方針)。` の行の下に、次の1行を足す:
   ```
    * 2026-10 に Supabase をストアのサーバー(store.kawamonn.com)へ移したため、接続先とキーもそれに合わせる。
   ```
4. 版を上げる: `versionCode = 4` → `versionCode = 5`、`versionName = "1.1.2"` → `versionName = "1.1.3"`。

テストとビルド:
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Set-Location C:\dev\diary-app
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease --console=plain
$LASTEXITCODE
Get-ApkEndpoints C:\dev\diary-app\app\build\outputs\apk\release\app-release.apk
```
完了の条件: `0` が表示され、接続先に `https://store.kawamonn.com` があり、`supabase.co` が**ない**。

コミット(`C:\dev\diary-app`): `1.1.3: 既定の接続先を store.kawamonn.com にする`

## ステップ 0-7: 新しい接続先でログインの入口が動くことを確かめる

存在しないアカウントでパスワードのログインを試し、「アカウントが違う」という**認証サーバーからの返事**が返ることを確かめる(公開鍵が違うとここで 401 になる)。
```powershell
$body = @{ email = "nobody-$(Get-Random)@example.invalid"; password = "wrong-password" } | ConvertTo-Json
try {
  Invoke-RestMethod -Method Post -Uri "https://store.kawamonn.com/auth/v1/token?grant_type=password" -Headers @{ apikey = $anon } -ContentType "application/json" -Body $body
  "unexpected success"
} catch {
  $_.Exception.Response.StatusCode.value__
}
```
完了の条件: `400` が表示される。`401` なら公開鍵が違うので止まって報告する。

## ステップ 0-8: push する

```powershell
git -C C:\dev\appstore push origin main
git -C C:\dev\diary-app push origin main
```
完了の条件: どちらもエラーなし。

## ステップ 0-9: ストアに公開する(本番の操作)

公開スクリプトは、既に登録済みのアプリには新しい版を足すだけ(`--name` などは不要)。署名鍵が前の版と違えば却下される。
```powershell
Set-Location C:\dev\appstore\web
$env:TTECH_ENV_FILE = ".env.prod.local"
node scripts\publish-app.mjs --apk ..\native-apps\ttech-admin\build\outputs\apk\release\ttech-admin-release.apk --notes "接続先を新しいサーバーに変更しました" --publish
$LASTEXITCODE
node scripts\publish-app.mjs --apk C:\dev\diary-app\app\build\outputs\apk\release\app-release.apk --notes "接続先を新しいサーバーに変更しました" --publish
$LASTEXITCODE
Remove-Item Env:\TTECH_ENV_FILE
```
完了の条件:
- 各実行の最初に `設定ファイル: ...\.env.prod.local / 接続先: store.kawamonn.com` と表示される(違えば、その時点で Ctrl+C で止めて報告する)。
- 各実行に `既存アプリに新バージョンを追加します`・`検査に合格しました`・`公開しました` が表示され、`$LASTEXITCODE` が `0`。
- `ウイルススキャン:` の後ろが `skipped` でない(0-3 のキーが使われている)。`skipped` なら報告に書く(止まらなくてよい)。

## ステップ 0-10: 公開されたことを確かめる

日記アプリ(公開カタログに出る)。カタログは最大で数分キャッシュされるので、30秒おきに最大10回試す。
```powershell
for ($i = 0; $i -lt 10; $i++) {
  $app = Invoke-RestMethod "https://store.kawamonn.com/api/v1/apps/diary?t=$(Get-Random)"
  if ($app.latest.versionName -eq "1.1.3") { "diary ok"; break }
  Start-Sleep -Seconds 30
}
```
管理アプリ(管理者専用で公開カタログに出ないため、本番の管理者用の鍵で DB を直接見る。鍵は表示しない):
```powershell
$envText = Get-Content C:\dev\appstore\web\.env.prod.local
$url = (($envText | Where-Object { $_ -match '^NEXT_PUBLIC_SUPABASE_URL=' }) -split '=', 2)[1].Trim()
$svc = (($envText | Where-Object { $_ -match '^SUPABASE_SERVICE_ROLE_KEY=' }) -split '=', 2)[1].Trim()
$rows = Invoke-RestMethod -Uri "$url/rest/v1/apps?package_name=eq.com.ttech.admin&select=slug,app_releases(version_name,status)" -Headers @{ apikey = $svc; Authorization = "Bearer $svc" }
$rows[0].app_releases | Where-Object { $_.version_name -eq "1.0.3" } | Select-Object version_name, status
```
完了の条件: `diary ok` が表示され、管理アプリの `1.0.3` の `status` が `published`。

## ステップ 0-11: 記録して終える

1. このファイルの末尾の「進み具合」を埋める。
2. [../supabase-removal-plan.md](../supabase-removal-plan.md) のフェーズ0の「調査結果」の最後の箇条書き(`- 対処: ...`)の後ろに、`→ 2026-MM-DD 対応済み(管理アプリ 1.0.3・日記アプリ 1.1.3 を公開)` を足す。
3. コミットして push: `フェーズ0 を終えたことを記録する`
4. 持ち主への報告に、次の依頼を書く: 「スマホで管理アプリを 1.0.3 に更新し、ログインと2段階認証、アプリ一覧の表示ができるか確かめてください」(端末での確認はエージェントにはできないため)。

## 戻し方

- 公開した版に問題があった場合: 管理画面(`https://store.kawamonn.com/admin`)で、その版のリリースを非公開にする(持ち主に依頼する)。ストアのアプリは、古い版に戻す配信はできないので、直した版(1.0.4 / 1.1.4)を出す。
- 0-2 の変更に問題があった場合: `git revert` して push する(本番の Web には影響しない。手元のスクリプトだけの変更)。

---

## 進み具合

| ステップ | 日付 | 結果・メモ |
|---|---|---|
| 0-1 | | マージ前の main: appstore=`` / diary-app=`` |
| 0-2 | | |
| 0-3 | | |
| 0-4 | | google= / email= / disable_signup= / autoconfirm= |
| 0-5 | | |
| 0-6 | | |
| 0-7 | | |
| 0-8 | | |
| 0-9 | | |
| 0-10 | | |
| 0-11 | | |
