# フェーズ3: アプリが Supabase を直接呼ばないようにする(Web に API を作る)

## なぜ
日記アプリは、すべてのデータ操作を Supabase の API(PostgREST と Storage)に直接送っていて、守りは RLS だけです。ストアアプリも、アカウント画面の3つの読み取りを PostgREST に直接送っています。Supabase を止めるには、これらを Web の API に置き換える必要があります。

- 新しい API は、**PostgREST が返していた行と同じ形(snake_case の列名)**で返す。アプリ側は、呼び先を変えるだけで、受け取ったデータの読み方は変えずに済む。
- 中身は `asUser(...)`(フェーズ1)で実行するので、RLS がそのまま効く。誰が何を見られるかは今と同じ。
- ログインのトークンは、このフェーズではまだ Supabase Auth のもの。Web が `currentUserFromBearer` で確かめる。
- **アプリの新しい版の公開は、フェーズ4 の最後に1回だけ行う**(ログインの仕組みもフェーズ4で変わるため。2回出し直さない)。このフェーズでは、アプリのコードを直してテストするところまで。
- 日記の写真(`diary-media`)も、ここで R2 に移す。

## 前提
- フェーズ2 が終わっている。
- ブランチ: ストア `supabase-removal/phase3-app-api`、日記アプリ `supabase-removal/phase3-app-api`(どちらも同じ名前)。マージ前の main を「進み具合」に書く。

---

## ステップ 3-1: 日記の API(Web)

場所: `web/src/app/api/v1/diary/`。すべて **Bearer のログインが必須**(`currentUserFromBearer(request)`。null なら 401 `{ error: "ログインしてください" }`)。中身はすべて `asUser(user, trx => ...)`。

| 日記アプリの関数 | メソッドとパス | 受け取るもの | 返すもの |
|---|---|---|---|
| `listTimeline(limit)` | `GET /api/v1/diary/entries?scope=timeline&limit=100` | `limit` は1〜200(既定100) | 200: `diary_entries` の行の配列(全列)。並びは `entry_date desc, created_at desc` |
| `listMyEntries()` | `GET /api/v1/diary/entries?scope=mine` | | 200: 自分の行の配列。並びは `entry_date desc` |
| `createEntry(...)` | `POST /api/v1/diary/entries` | `{ id, body, visibility, photo_path?, mentioned_user_ids }`(アプリが今 insert している形。`user_id` は送られても**無視し、ログイン中のユーザーにする**) | 201: `{}` |
| `restoreEntries(entries)` | `PUT /api/v1/diary/entries` | 行の配列 `[{ id, entry_date, body, photo_path?, visibility, mentioned_user_ids }]`(最大500件。`user_id` は同じく無視) | 200: `{}`。`id` が同じ行は上書き(`on conflict (id) do update`) |
| `deleteEntry(id)` | `DELETE /api/v1/diary/entries/{id}` | | 204(消えた行が0でも 204。今の PostgREST と同じ) |
| (写真のアップロード) | `POST /api/v1/diary/photos?entry_id={uuid}` | 本文は画像そのもの。`Content-Type` は `image/jpeg` か `image/png`。最大 10MB | 201: `{ "path": "diary/<自分のID>/<entry_id>.<jpg\|png>" }` |
| `photoUrl(path)` | `GET /api/v1/diary/photos/{path}` | | 200: 画像。見えない投稿の写真・無い写真は 404 |
| `myFriendCode()` | `GET /api/v1/diary/me` | | 200: `{ id, display_name, friend_code }`(`diary_public_profiles` の自分の行) |
| `findByFriendCode(code)` | `GET /api/v1/diary/profiles?friend_code=XXXX` | 前後の空白を除き大文字にしてから探す | 200: `[{ id, display_name, friend_code }]`(0件か1件) |
| `resolveDisplayNames(ids)` | `GET /api/v1/diary/profiles?ids=<uuid>,<uuid>` | 最大200件。UUID でないものは無視 | 200: `[{ id, display_name }]` |
| `sendFriendRequest(id)` | `POST /api/v1/diary/friendships` | `{ addressee_id }`(`requester_id` はログイン中のユーザー) | 201: `{}` |
| `respondFriendRequest(id, accept)` | `PATCH /api/v1/diary/friendships/{id}` | `{ status: "accepted" \| "declined" }` | 204。更新された行が0なら 404 |
| `removeFriendship(id)` | `DELETE /api/v1/diary/friendships/{id}` | | 204 |
| `listAcceptedFriends()` | `GET /api/v1/diary/friendships?status=accepted` | | 200: `[{ id, requester_id, addressee_id }]` |
| `listFriendships()` | `GET /api/v1/diary/friendships` | | 200: 自分が関わる全行(全列)。並びは `created_at desc` |
| `blockUser(id)` | `POST /api/v1/diary/blocks` | `{ user_id }` | 204(関数 `diary_block_user(p_target)`) |
| `unblockUser(id)` | `DELETE /api/v1/diary/blocks/{userId}` | | 204 |
| `listBlockedUsers()` | `GET /api/v1/diary/blocks` | | 200: `[{ blocked_id, created_at }]`。並びは `created_at desc` |
| `reportEntry(...)` | `POST /api/v1/diary/reports` | `{ entry_id, reason, detail? }` | 204(関数 `diary_report_entry`) |
| `deleteMyAccount()` | `DELETE /api/v1/diary/account` | | 204。下の「アカウントの削除」 |

共通の決まり:
- 本文は zod で検証する。列の型・長さの上限は、`supabase/migrations/` の表の定義と CHECK 制約に合わせる(DB で弾かれるより先に 400 にする)。
- DB のエラーの変換(1か所の関数にまとめる。`src/lib/diary-api.ts`):
  - 関数の `raise exception`(`code: "P0001"`)→ 400 `{ error: <DB のメッセージ> }`(日本語のメッセージなので、そのまま利用者に見せてよい)
  - RLS の拒否(`42501`)→ 403 `{ error: "この操作はできません" }`
  - 一意制約(`23505`)→ 409 `{ error: "すでに登録されています" }`
  - 外部キー(`23503`)→ 404 `{ error: "相手が見つかりません" }`
  - それ以外 → `internalError(e)`(500)
- 写真のアップロード: R2 のキーは `mediaKey("diary-media", path)`。パスの `<自分のID>` はログイン中のユーザー(アプリから受け取らない)。`entry_id` が UUID でなければ 400。
- 写真の取得の権限: `asUser` で `select 1 from diary_entries where photo_path = <path>` が1行以上あるか、パスが `diary/<自分のID>/` で始まる(投稿前のプレビュー)ときだけ返す。R2 に無ければ Supabase Storage(`diary-media`、service role)から返す(移行中の戻り道。フェーズ6で消す)。
- アカウントの削除: (1) R2 の `media/diary-media/diary/<自分のID>/` の下を全部消す(`listKeys` → `deleteObject`)。(2) Supabase Storage の同じフォルダも、service role で一覧して消す(移行中だけ。フェーズ6で消す)。(3) どちらかが失敗したら 500 を返し、アカウントは消さない(今のアプリと同じ考え方)。(4) 関数 `diary_delete_my_account()` を呼ぶ。

### テスト
1. **日記のシナリオを API でも通す**: `tests/scenarios/diary.ts` に `apiDiaryDriver()` を足す(上の API を Bearer で呼ぶ)。`tests/api/diary.test.ts` を、**`postgrest` と `api` の両方のドライバーで同じシナリオを回す**形にする(`describe.each(["postgrest", "api"])`)。両方とも合格すること = 新しい API の権限が、今の直接アクセスと同じであること。
2. **権限の表に、新しい API の全行を足す**(`routes-inventory.test.ts` が足し忘れを見つける)。日記の API は Bearer のみ。`anon` は 401。
3. **写真のテスト** `tests/api/flows/diary-photos.test.ts`: `user1` が friends の投稿に写真をつける → `user1`・`user2`(友達)は 200、`user4`(他人)は 404、ログインなしは 401。`user4` が `diary/<user1>/...` のパスで上げようとしても、保存先は `diary/<user4>/...` になる。10MB を超える・`image/gif` は 400。
4. **応答の形**: `GET entries?scope=timeline` の1行を、PostgREST の `GET /rest/v1/diary_entries?id=eq.<id>&select=*`(同じユーザーの Bearer)の結果と `toEqual` で比べるテストを足す(列名・日時の形が同じこと)。

完了の条件: `npm run test:api` が合格。
コミット: API のまとまりごと(投稿・写真・プロフィール・友達・ブロックと通報・アカウントの削除)に1つずつ。

## ステップ 3-2: ストアアプリのアカウント画面の API(Web)

ストアアプリの `AccountScreen.kt` は、次の3つを PostgREST に直接送っている:
1. `profiles?id=eq.<自分>&select=role`
2. `apps?developer_id=eq.<自分>&select=name,status,app_releases(version_name,status,auto_approved,policy_verdict,created_at)&order=created_at.desc`
3. `app_releases?status=in.(scanned,approved)&select=id,version_name,status,policy_findings,apps(name,developers(name))&order=created_at.desc`(3つ目は、AccountScreen.kt を読んで、どの条件で呼んでいるか確かめる)

これを1つにまとめる: `GET /api/v1/me/account`(Bearer 必須)→ 200:
```json
{ "role": "user", "apps": [ /* 2 と同じ形 */ ], "pending_releases": [ /* 3 と同じ形。3 を呼ばない条件のときは [] */ ] }
```
- 中身は `asUser`(RLS がそのまま効く)。埋め込みは `jsonArrayFrom`・`jsonObjectFrom` で、PostgREST と同じ入れ子の形にする。
- テスト: `tests/api/flows/me-account.test.ts` で、`dev1`・`admin`・`user1` それぞれについて、新しい API の `apps`・`pending_releases` が、同じ人の Bearer で PostgREST に 2・3 を直接送った結果と `toEqual` であること。権限の表にも行を足す。

コミット: `ストアアプリのアカウント画面用の API を足す`

## ステップ 3-3: 日記の写真を R2 に複製する

フェーズ2 の `copy-storage-to-r2.mjs` を `--bucket diary-media` で使う。テスト `tests/api/copy-storage.test.ts` に `diary-media` の場合を足す。
本番では 3-7 で実行する。

## ステップ 3-4: Web の管理画面の日記の写真

`src/lib/admin-queries.ts` の日記の写真の署名付き URL(`svc.storage.from("diary-media").createSignedUrls`)と、`src/lib/moderation.ts` の写真の削除(`storage.from("diary-media").remove`)を、R2 にする:
- 署名付き URL: `presignDownload(mediaKey("diary-media", path))`(既存の関数。有効期限は今の `PHOTO_URL_TTL_SECONDS` に合わせる。既存の関数の期限が違えば、期限を引数で渡せるようにする)。R2 に無いもの(移行前)は、今の Storage の署名付き URL を返す(戻り道。フェーズ6で消す)。
- 削除: `deleteObject(mediaKey("diary-media", path))` と、Storage 側の削除の両方(移行中だけ)。
- テスト: `moderation.test.ts`(API テスト)で、写真つきの投稿を削除したら R2 からも消えること。管理 API の日記の一覧のスナップショットは、署名付き URL のクエリを消してから比べている(フェーズT)ので、URL のホストが変わる分だけ更新する(フェーズ2 の 2-3 と同じ確かめ方)。

コミット: `管理画面の日記の写真を R2 から扱う`

## ステップ 3-5: 日記アプリ(Android)を新しい API にする

リポジトリ: `C:\dev\diary-app`。**このフェーズでは公開しない。** テストとビルドまで。

1. `app/build.gradle.kts`: `diaryApiBase`(既定 `https://store.kawamonn.com/api/v1/diary`、`local.properties` の `diaryApiBase` で上書き可)を `BuildConfig.DIARY_API_BASE` として足す。Supabase の URL と anon キーは**まだ残す**(ログインがフェーズ4まで Supabase のため)。
2. `data/api/DiaryApi.kt`(新規): OkHttp で上の表の API を呼ぶクラス。ヘッダー `Authorization: Bearer <今のアクセストークン>`。今の `PostgrestApi` と同じく、戻り値は `JsonArray`/`JsonObject`(読み方を変えないため)。エラーのときは、応答の `{ "error": "..." }` を例外のメッセージにする。
3. `data/DiaryRepository.kt`: `postgrest.*`・`storage.*` の呼び出しを、`DiaryApi` の対応する関数に置き換える(上の表の左の列が対応表)。
   - `createEntry` の写真: 先に `POST photos?entry_id=...` で上げ、返ってきた `path` を `photo_path` に入れて投稿する。
   - `photoUrl(path)` は `"$DIARY_API_BASE/photos/$path"` を返す。
   - `deleteMyAccount()` は `DELETE account` を呼ぶだけ(写真の削除はサーバーが行う)。
4. 画像の読み込み(`DiaryApp.kt` の Coil の `ImageLoader`): 今、写真の URL に認証のヘッダーをつけている OkHttp のしくみを確かめ、新しい URL(`DIARY_API_BASE` で始まる)にも `Authorization: Bearer` がつくようにする。
5. 使われなくなった `PostgrestApi.kt`・`StorageApi.kt` を消す。
6. テスト `app/src/test/.../DiaryApiTest.kt`(MockWebServer。`okhttp.mockwebserver` が依存に無ければ、`gradle/libs.versions.toml` にあるか確かめて足す): 上の表の全関数について、(a) メソッド・パス・クエリ・本文・`Authorization` ヘッダーが正しい、(b) 応答の JSON を正しく読める、(c) 401・400 のときに `error` のメッセージで例外になる。
7. 版はまだ上げない(フェーズ4で上げる)。

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Set-Location C:\dev\diary-app
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
```
完了の条件: 成功(`$LASTEXITCODE` が 0)。
コミット(diary-app): `日記の読み書きを Supabase ではなくストアの API で行う`

## ステップ 3-6: ストアアプリ(Android)のアカウント画面

リポジトリ: `C:\dev\appstore\store-app`。
1. `ui/account/AccountScreen.kt` の3つの `postgrest.select(...)` を、`GET {STORE_API_BASE}/me/account`(Bearer)の1回の呼び出しにする。受け取った `apps`・`pending_releases` を、今と同じ型に読み込む。呼び出しは、ストアアプリの既存の API クライアント(`STORE_API_BASE` を使っているクラス)に関数を足して行う。
2. `PostgrestApi` が他で使われていなければ、`PostgrestApi.kt` と `AppContainer.kt` の `postgrest` を消す。
3. テスト: 足した関数を MockWebServer で確かめる(パス・ヘッダー・応答の読み込み)。
```powershell
Set-Location C:\dev\appstore\store-app
.\gradlew.bat testDebugUnitTest assembleDebug --console=plain
```
完了の条件: 成功。
コミット(appstore): `ストアアプリのアカウント画面を、Supabase ではなくストアの API で読む`

## ステップ 3-7: 本番に出す前の合格条件と、本番に出す(本番の操作)

合格条件:
- [ ] `npm run typecheck`・`npm test`・`npm run test:db`・`npm run build` が合格
- [ ] `npm run test:api` が3回続けて合格(日記のシナリオが `postgrest` と `api` の両方で合格)
- [ ] 日記アプリ・ストアアプリの `testDebugUnitTest` が合格
- [ ] GitHub Actions が全部緑(appstore)

本番(Web だけ。アプリはフェーズ4で公開):
```powershell
Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
Set-Location C:\dev\appstore\web
$env:TTECH_ENV_FILE = ".env.prod.local"; node scripts\ops\copy-storage-to-r2.mjs --bucket diary-media; Remove-Item Env:\TTECH_ENV_FILE
git -C C:\dev\appstore fetch origin
git -C C:\dev\appstore rebase origin/main
git -C C:\dev\appstore push --force-with-lease origin supabase-removal/phase3-app-api
gh run watch --repo kawamonn91/ttech-store --exit-status
git -C C:\dev\appstore switch main
git -C C:\dev\appstore merge --ff-only supabase-removal/phase3-app-api
git -C C:\dev\appstore push origin main
Backup-ProdDb "phase3-step7"
Invoke-WebDeploy
$env:SMOKE_DIARY_DRIVER = "api"; npm run test:smoke; Remove-Item Env:\SMOKE_DIARY_DRIVER
npm run test:smoke
```
- スモークテストを、日記のドライバーを `api` にして1回、今のまま(`postgrest`)で1回流す。両方合格すること(古い版のアプリも新しい版も動くこと)。
- 合格したら、`web/.env.prod.local` に `SMOKE_DIARY_DRIVER=api` を足す(以降のスモークテストは新しい API で確かめる)。
- 日記アプリのブランチは、まだ main にマージしない(フェーズ4で、ログインの変更と一緒にマージして公開する)。push だけしておく: `git -C C:\dev\diary-app push -u origin supabase-removal/phase3-app-api`。

失敗したら [ops.md](ops.md) の 6-1 で戻す。

## ステップ 3-8: 記録
「進み具合」を埋め、計画の「状態」を `フェーズ3 まで完了(アプリの公開はフェーズ4)` にする。コミットして push。

---

## 進み具合

| ステップ | 日付 | 結果・メモ |
|---|---|---|
| 3-0 | | マージ前の main: appstore=`` / diary-app=`` |
| 3-1 | | |
| 3-2 | | |
| 3-3 | | |
| 3-4 | | |
| 3-5 | | |
| 3-6 | | |
| 3-7 | | |
| 3-8 | | |
