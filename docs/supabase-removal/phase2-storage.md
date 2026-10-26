# フェーズ2: アイコン・スクリーンショットを R2 に移す

## なぜ
アプリのアイコンとスクリーンショットは、Supabase Storage のバケット `app-media`(公開)に置いてあり、URL も Supabase の形(`/storage/v1/object/public/app-media/...`)です。これを、APK と同じ Cloudflare R2 のバケットに移し、Web の URL(`/media/app/...`)から配るようにします。

- R2 のバケットは公開しない(APK も入っているため)。画像は Web のルートが R2 から読んで返す。`Cache-Control` を長くするので、Cloudflare が画像をキャッシュし、Web への負荷はほとんど増えない。
- R2 のキーは `media/app-media/<今のパス>`。DB に入っているパス(`apps.icon_path`・`apps.screenshots`)は**変えない**。
- 日記の写真(`diary-media`)は、日記アプリがまだ Storage を直接使っているので、フェーズ3で移す。

## 前提
- フェーズ1 が終わっている。
- ブランチ: `supabase-removal/phase2-storage`。マージ前の main を「進み具合」に書く。

## 変えないもの
- DB のパス。アプリの API の応答の形(URL の文字列だけが変わる)。

---

## ステップ 2-1: R2 の読み書きの関数を足す

`web/src/lib/r2.ts` に足す(既存の署名の作り方 `aws4fetch` と、IPv4 固定の `Agent` を使い回す):
```ts
/** 画像などのキー。DB に入っているパス(例: "<appId>/icon-123.png")の前に、置き場ごとの接頭辞をつける */
export function mediaKey(bucket: "app-media" | "diary-media", path: string): string {
  return `media/${bucket}/${path}`;
}

/** サーバーから R2 に直接書く(画像など、サーバーが中身を持っているもの) */
export async function putObject(key: string, body: Buffer, contentType: string): Promise<void>;

/** R2 から読む。無ければ null */
export async function getObject(key: string): Promise<{ body: ReadableStream<Uint8Array>; contentType: string | null; size: number | null } | null>;

/** 接頭辞の下のキーをすべて返す(ListObjectsV2。1000件ずつ続きを読む) */
export async function listKeys(prefix: string): Promise<string[]>;
```
中身は、既存の `headObject`・`deleteObject` と同じく、`objectUrl(key)` に署名して `fetch` する。`listKeys` は `?list-type=2&prefix=...&continuation-token=...` を使い、XML の `<Key>` と `<NextContinuationToken>` を正規表現で読む。

テスト `tests/api/r2.test.ts`(偽の R2 を使う): `putObject` → `getObject` で同じ中身、`listKeys` で見つかる、`deleteObject` で消える、無いキーの `getObject` が null。

完了の条件: `npm run typecheck`・`npm run test:api` が合格。
コミット: `R2 にサーバーから直接読み書きする関数を足す`

## ステップ 2-2: 画像を配るルート `src/app/media/app/[...path]/route.ts`

```ts
import { getObject, mediaKey } from "@/lib/r2";
import { env } from "@/lib/env";

const SEGMENT = /^[A-Za-z0-9._-]+$/;
const TYPES: Record<string, string> = { png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", webp: "image/webp" };

/**
 * アプリのアイコン・スクリーンショットを R2 から返す。パスには作成時刻が入っていて中身は変わらないので、
 * 長くキャッシュさせる。R2 に無ければ(移行の途中)、Supabase Storage から返す。この戻り道はフェーズ6で消す。
 */
export async function GET(_request: Request, { params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params;
  if (path.length < 2 || path.length > 4 || !path.every((s) => SEGMENT.test(s) && s !== "." && s !== "..")) {
    return new Response("Not Found", { status: 404 });
  }
  const joined = path.join("/");
  const ext = joined.split(".").pop()?.toLowerCase() ?? "";
  const type = TYPES[ext];
  if (!type) return new Response("Not Found", { status: 404 });

  const obj = await getObject(mediaKey("app-media", joined));
  if (obj) {
    return new Response(obj.body, { headers: { "Content-Type": type, "Cache-Control": "public, max-age=31536000, immutable" } });
  }
  // 移行の途中の戻り道(フェーズ6で削除)
  const legacy = await fetch(`${env.supabaseUrl()}/storage/v1/object/public/app-media/${joined}`);
  if (legacy.ok && legacy.body) {
    return new Response(legacy.body, { headers: { "Content-Type": type, "Cache-Control": "public, max-age=300" } });
  }
  return new Response("Not Found", { status: 404 });
}
```
- Next.js のこの版のルートハンドラの書き方(`params` が `Promise` か、動的セグメントの名前など)を、`node_modules/next/dist/docs/` で確かめてから書く。
- `src/app/api` の外なので、権限の表の対象ではない。代わりにテスト `tests/api/flows/media.test.ts` を作る:
  - 偽の R2 に `media/app-media/<fx-published の id>/icon-1.png` を置く → `GET /media/app/<id>/icon-1.png` が 200・`image/png`・`immutable`。
  - ローカルの Supabase Storage(`app-media`)にだけ置いた画像 → 200(戻り道)。
  - `GET /media/app/..%2F..%2Fetc/passwd`・`GET /media/app/a/b.exe`・存在しないパス → 404。

完了の条件: `npm run test:api` が合格。
コミット: `アプリの画像を R2 から配るルートを足す`

## ステップ 2-3: 画像の URL を新しいルートに向ける

`web/src/lib/dto.ts` の `mediaUrl`:
置き換え前:
```ts
  const baseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || env.supabaseUrl();
  return `${baseUrl}/storage/v1/object/public/app-media/${path}`;
```
置き換え後:
```ts
  return `${env.siteUrl()}/media/app/${path}`;
```
`dto.test.ts` などで URL を確かめているテストがあれば、期待値を新しい形に直す。

API テストのスナップショットは、画像の URL の部分だけが変わる。**意図した変更なので更新する**が、他の部分が変わっていないことを確かめる:
```powershell
Set-Location C:\dev\appstore\web
npx vitest run --config vitest.api.config.ts -u tests/api/shapes.test.ts
git diff --unified=0 -- tests/api/__snapshots__ | Select-String '^[+-][^+-]' | Where-Object { $_.Line -notmatch '/media/app/|/storage/v1/object/public/app-media/' }
```
完了の条件: 最後のコマンドが何も表示しない(URL 以外の行が変わっていない)。`npm run test:api` が合格。
コミット: `アプリの画像の URL を、Web の /media/app/ に変える`

## ステップ 2-4: 開発者の画像のアップロード・削除を R2 にする

`web/src/lib/app-media.ts`(新規)に、`src/app/api/developer/apps/[id]/media/route.ts` の中身(画像の検査・保存・DB の更新・古いアイコンの削除)を移し、管理者からも使えるようにする:
```ts
/** 画像を検査して PNG に作り直し、R2 に置いて、アプリの icon_path / screenshots を更新する */
export async function addAppMedia(trx: Db, app: { id: string; icon_path: string | null; screenshots: string[] | null }, kind: "icon" | "screenshot", file: File): Promise<{ path: string } | { error: string; status: number }>;

/** スクリーンショットを1枚外し、R2 からも消す */
export async function removeScreenshot(trx: Db, app: { id: string; screenshots: string[] | null }, path: string): Promise<{ ok: true } | { error: string; status: number }>;
```
- 置き場の書き込みは `putObject(mediaKey("app-media", path), png, "image/png")`、削除は `deleteObject(mediaKey("app-media", path))`(失敗しても続ける。今と同じ)。
- 古いアイコン(R2 に無く Supabase Storage にだけある場合も)は、R2 から消すだけでよい(Storage 側はフェーズ6でまとめて消える)。
- 開発者のルートは、所有者の確認(`loadOwnApp`)と停止中の確認をしてから、この関数を呼ぶだけにする。応答(ステータス・本文)は今と同じにする。

完了の条件: `npm run test:api` が合格(`developer-release.test.ts` の「アイコンを上げて表示される」を含む)。
コミット: `開発者の画像の保存先を R2 にし、処理を lib/app-media.ts にまとめる`

## ステップ 2-5: 管理画面の画像の扱いをサーバー経由にする

今の `MediaManager.tsx` は、ブラウザから Supabase Storage に直接アップロードしている(検査なし)。開発者側と同じ処理を通すようにする。

1. `src/app/api/admin/apps/[id]/media/route.ts`(新規): `POST`(multipart: `kind` = `icon`|`screenshot`、`file`)と `DELETE`(本文 `{ path }`)。`requireAdmin(request)` を通ったら、所有者を問わずにアプリを読み(`asService`)、`addAppMedia`・`removeScreenshot` を呼ぶ。大きさの上限は開発者と同じ(`DEVELOPER_LIMITS`)。
2. `MediaManager.tsx`: アップロードは `FormData` をこのルートに `POST`、スクリーンショットの削除は `DELETE`。`browserClient` の import を消す。`kind` は今の `"icon" | "shot"` を、API の `"icon" | "screenshot"` に合わせる。
3. `src/app/admin/(console)/actions.ts` の `saveMedia` が使われなくなったら削除する(`Select-String` で使用箇所が無いことを確かめる)。
4. **権限の表に、新しいルートの `POST`・`DELETE` の行を足す**(管理者だけ 2xx。`adminAal1` は 401)。

完了の条件: `npm run test:api` が合格。管理者で画像を上げるテストを `tests/api/flows/admin-review.test.ts` に足し、合格する。
コミット: `管理画面の画像のアップロードを、検査つきのサーバー経由にする`

## ステップ 2-6: 手元のスクリプトの画像の部分

`web/scripts/set-app-icon.mjs`・`add-screenshot.mjs`・`upload-tool.mjs` のうち、Supabase Storage(`/storage/v1/`)に書いている部分を、R2 のキー `media/app-media/<パス>` への書き込みにする。
- 同じ処理(R2 への PUT)が複数のスクリプトにあれば、`web/scripts/lib/r2.mjs` にまとめて使う(`publish-app.mjs` の `class R2` もここに移す)。
- `R2_ENDPOINT` があればそれを使う(`src/lib/r2.ts` と同じ)。

完了の条件: `Select-String -Path scripts\*.mjs,scripts\lib\*.mjs -Pattern 'storage/v1'` が何も表示しない。`npm test` が合格。
コミット: `手元のスクリプトの画像の保存先を R2 にする`

## ステップ 2-7: 既存の画像を R2 に複製するスクリプト

`web/scripts/ops/copy-storage-to-r2.mjs`(新規):
- 使い方: `node scripts/ops/copy-storage-to-r2.mjs --bucket app-media [--dry-run]`。`TTECH_ENV_FILE` で設定ファイルを選ぶ(`loadEnvLocal()`)。
- Supabase Storage の一覧: `POST {NEXT_PUBLIC_SUPABASE_URL}/storage/v1/object/list/<bucket>`(service role の鍵、本文 `{ prefix, limit: 1000, offset }`)。`id` が `null` の項目はフォルダなので、その下を再帰的にたどる。
- 1件ずつ: `GET /storage/v1/object/<bucket>/<path>`(service role)で読み、R2 の `media/<bucket>/<path>` に、同じ `Content-Type` で PUT。R2 に同じ大きさのものが既にあれば飛ばす(何度実行してもよい)。PUT のあと HEAD で大きさを確かめる。
- 最後に `合計 / 複製 / 既存で飛ばした / 失敗` の件数を表示する。失敗が1件でもあれば終了コード 1。
- 処理の本体を `export async function copyBucket({ bucket, dryRun, log })` として export し、テストから呼べるようにする。

テスト `tests/api/copy-storage.test.ts`: ローカルの Supabase Storage の `app-media` に画像を3つ(うち1つはフォルダの2階層下)置き、`copyBucket` を偽の R2 に対して実行 → 3件が同じ中身で入る。2回目の実行は `既存で飛ばした = 3`。

完了の条件: `npm run test:api` が合格。
コミット: `Supabase Storage の画像を R2 に複製するスクリプトを足す`

## ステップ 2-8: 本番に出す前の合格条件

- [ ] `npm run typecheck`・`npm test`・`npm run test:db`・`npm run build` が合格
- [ ] `npm run test:api` が3回続けて合格
- [ ] スナップショットの差分が画像の URL だけ(2-3 の確認)
- [ ] GitHub Actions が全部緑

## ステップ 2-9: 本番に出す(本番の操作)

順番が大事: **先に複製 → デプロイ → もう一度複製**(デプロイまでの間に上げられた画像を拾う)。画像のルートには Supabase Storage への戻り道があるので、順番が多少ずれても画像は消えない。
```powershell
Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
Set-Location C:\dev\appstore\web
$env:TTECH_ENV_FILE = ".env.prod.local"
node scripts\ops\copy-storage-to-r2.mjs --bucket app-media --dry-run
node scripts\ops\copy-storage-to-r2.mjs --bucket app-media
Remove-Item Env:\TTECH_ENV_FILE
```
完了の条件: 失敗 0 件。`--dry-run` の合計と、本番の件数が同じ。

マージとデプロイ(フェーズ1 の 1-11 と同じ手順。ブランチ名だけ変える):
```powershell
git -C C:\dev\appstore fetch origin
git -C C:\dev\appstore rebase origin/main
git -C C:\dev\appstore push --force-with-lease origin supabase-removal/phase2-storage
gh run watch --repo kawamonn91/ttech-store --exit-status
git -C C:\dev\appstore switch main
git -C C:\dev\appstore merge --ff-only supabase-removal/phase2-storage
git -C C:\dev\appstore push origin main
Backup-ProdDb "phase2-step9"
Invoke-WebDeploy
$env:TTECH_ENV_FILE = ".env.prod.local"; node scripts\ops\copy-storage-to-r2.mjs --bucket app-media; Remove-Item Env:\TTECH_ENV_FILE
npm run test:smoke
```
完了の条件: 2回目の複製の失敗が 0 件。スモークテスト(公開中の全アプリの画像が 200)が合格。失敗したら [ops.md](ops.md) の 6-1 で戻す(R2 に複製した画像は残してよい)。

## ステップ 2-10: 記録
「進み具合」を埋め、計画の「状態」を `フェーズ2 まで完了` にする。コミットして push。

---

## 進み具合

| ステップ | 日付 | 結果・メモ |
|---|---|---|
| 2-0 | | マージ前の main: `` |
| 2-1 | | |
| 2-2 | | |
| 2-3 | | |
| 2-4 | | |
| 2-5 | | |
| 2-6 | | |
| 2-7 | | |
| 2-8 | | |
| 2-9 | | 複製: 合計 / 複製 / 飛ばした / 失敗 = |
| 2-10 | | |
