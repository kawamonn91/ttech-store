# Supabase 依存をなくす作業の手順書(実行する AI エージェント向け)

全体の方針・決定事項は [../supabase-removal-plan.md](../supabase-removal-plan.md)。このフォルダは、その計画を**上から順に実行するための手順書**です。
実行するエージェント(Gemini Flash を想定)は、このファイルを最初に読み、書いてあるルールを全フェーズで守ってください。

## 実行する順番

| 順 | 手順書 | 内容 | 本番への影響 |
|---|---|---|---|
| 1 | [phase0-app-endpoints.md](phase0-app-endpoints.md) | 管理アプリ・日記アプリの接続先を直して出し直す | アプリの新しい版の公開 |
| 2 | [phaseT-tests.md](phaseT-tests.md) | テスト基盤(ローカルの本番相当環境・API テスト・スモークテスト・運用スクリプト) | スモーク用アカウントの作成のみ |
| 3 | [phase1-db.md](phase1-db.md) | Web の DB アクセスを Kysely + PostgreSQL 直結にする(RLS は残す) | DB のポート公開、Web のデプロイ |
| 4 | [phase2-storage.md](phase2-storage.md) | アイコン・スクリーンショットを R2 に移す | ファイルの複製、Web のデプロイ |
| 5 | [phase3-app-api.md](phase3-app-api.md) | 日記・ストアアプリ用の API を Web に作り、日記の写真を R2 に移す | Web のデプロイ |
| 6 | [phase4-auth.md](phase4-auth.md) | 認証を Better Auth に移し、3つのアプリを出し直す | **ログインの切り替え(全員再ログイン)** |
| 7 | [phase5-notify.md](phase5-notify.md) | 運営への通知を Web の処理に移す | Web のデプロイ、DB の変更 |
| 8 | [phase6-shutdown.md](phase6-shutdown.md) | 素の PostgreSQL に移し、Supabase を止める | **短い停止時間あり** |
| 9 | [phase7-drop-rls.md](phase7-drop-rls.md) | 権限の判定を Web のコードに移し、RLS をやめる | Web のデプロイ、DB の変更 |

運用の共通手順(バックアップ・DB への接続・マイグレーションの適用・デプロイ・戻し方)は [ops.md](ops.md) にまとめています。各フェーズからはそこを参照します。

---

## 作業のルール(必ず守る)

### 1. 進め方
- 手順書の**ステップを1つずつ**実行する。ステップの「完了の条件」をすべて満たしてから次へ進む。
- 手順書に書いていない変更(ついでのリファクタ、依存の追加、ファイル名の変更)はしない。必要だと思ったら、作業を止めて「状況の報告」(下記)に書く。
- ステップを終えるたびに、そのフェーズの手順書の末尾にある「進み具合」の表を更新する(日付・結果)。
- フェーズを終えたら、[../supabase-removal-plan.md](../supabase-removal-plan.md) の冒頭の「状態」を更新する。

### 2. 作業する場所と道具
- **Windows の PowerShell** で作業する。WSL は使わない。
- リポジトリ: ストア `C:\dev\appstore`(GitHub: `kawamonn91/ttech-store`、**公開**)、日記アプリ `C:\dev\diary-app`(GitHub: `kawamonn91/diary`、非公開)。
- git は Windows の git を使う(WSL の git で見ると改行コードの差で全ファイルが変更扱いになるため)。
- Android のビルドの前に、毎回次を設定する:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
  $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
  ```
- Docker Desktop が起動していること(`docker info` が成功すること)。ローカルの検証環境に使う。
- PowerShell の注意:
  - 外部コマンドの成否は `$LASTEXITCODE` で確かめる(`0` 以外なら失敗)。
  - **バイナリ(DB のダンプ・APK など)を `>` でファイルに書き出さない**(Windows PowerShell 5.1 では文字コードが変わって壊れる)。サーバー側でファイルに書き、`scp` で持ってくる。

### 3. Web(Next.js)のコードを書くとき
- `web/AGENTS.md` のとおり、この Next.js(16.x)は学習データと違う。**ルートハンドラ・ページ・`proxy.ts` を書く前に `web/node_modules/next/dist/docs/` の該当ガイドを読む。** 例: ルートハンドラの `params` は `Promise` で、`await` してから使う。
- Kysely・Better Auth などのライブラリは、**インストールされた版の型定義(`node_modules/<pkg>/dist/*.d.ts`)と README を読んでから使う。** 手順書に書いた API 名が実際と違ったら、実際の版に合わせ、違っていたことを「状況の報告」に書く。
- コメントは日本語で、既存のファイルと同じくらいの量にする。

### 4. 秘密情報
- `.env*` ファイル(`web/.env.local` = 手元の検証環境、`web/.env.prod.local` = 本番)は**コミットしない**(`.gitignore` 済み)。中身を画面やログに表示しない。値が要るときは、変数名だけを表示する。
- **`web/.env.production.local` という名前のファイルを作らない。** Next.js が `npm run build`・`next start` で自動で読み込み、手元のビルドやテストに本番の鍵が混ざる。本番の設定は必ず `web/.env.prod.local` に置く。
- このリポジトリは**公開**。手順書・コード・コミットメッセージに、サーバーの IP・ユーザー名・パス、鍵、パスワードを書かない。
- 権限(RLS・認可)に関わる変更は、**本番に適用して確かめてから push する**([../supabase-migration.md](../supabase-migration.md) の原則)。具体的な順番は各フェーズの手順に書いてある。

### 5. 本番に触れる操作
本番に触れる操作(DB の変更、Web のデプロイ、アプリの公開、コンテナの停止)は、持ち主の承認なしで実行してよい。ただし次を**必ず**守る。
1. 直前に [ops.md](ops.md) の「バックアップ」を取る(DB を変えないデプロイでも取る)。
2. そのフェーズの「本番に出す前の合格条件」を**すべて**満たしていること。1つでも満たしていなければ本番に触れない。
3. 本番に出した直後に「本番のスモークテスト」(`npm run test:smoke`)を実行する。失敗したら、**直ちに** [ops.md](ops.md) の「戻し方」で元に戻し、作業を止めて報告する。直そうとして本番で試行錯誤しない。

### 6. 止まる条件
次のときは、それ以上進めずに作業を止め、「状況の報告」を書く。
- 同じテストの失敗を、3回直そうとしても直らないとき。
- 手順書の前提(ファイルの中身・コマンドの出力・サーバーの構成)が書いてあることと違うとき。
- 本番のスモークテストが失敗したとき(戻したあとで止まる)。
- 手順書に書いていない本番の操作が必要になったとき。

**状況の報告**: そのフェーズの手順書の末尾の「進み具合」の表の下に、日付・どのステップで・何が起きたか・試したこと・戻したかどうかを書き、コミットして push する(権限に関わるコードが未適用のまま含まれる場合は push せず、ローカルのコミットだけにする)。

### 7. コミットとブランチ
- フェーズごとにブランチを切る: `supabase-removal/phase1-db` など(フェーズ0だけは main に直接)。
- ステップごとにコミットする。メッセージは日本語で、既存の履歴と同じく「何をしたか」を1行で書き、空行のあとに理由を書く。
- フェーズの合格条件を満たし、本番に出してスモークテストが通ったら、main に早送りマージ(`git merge --ff-only`)して push する。マージ前に `git rebase origin/main` で最新に揃える。

---

## 手元の設定ファイル(`web/.env.prod.local`)に足していく変数

本番の操作に使う値は、すべて `web/.env.prod.local` に置きます(コミットされない)。各フェーズで足す変数は次のとおり。足すときは `web/.env.example` にも**変数名だけ**を足してください。

| 変数 | 足すフェーズ | 中身 |
|---|---|---|
| `VT_API_KEY` | 0 | ウイルススキャンのキー。`web/.env.local` の同じ行を写す |
| `DEPLOY_SSH` / `DEPLOY_DIR` | T | `web/.env.local` の同じ行を写す |
| `DEPLOY_ENV_FILE` | T | サーバーの Web の環境変数ファイルの場所(調べ方は phaseT-tests.md の T-7) |
| `SUPABASE_DOCKER_DIR` / `PROD_DB_CONTAINER` / `PROD_DB_SUPERUSER` | T | サーバーの Supabase の docker compose の場所、DB コンテナの名前、DB の管理者ユーザー名(調べ方は phaseT-tests.md の T-7) |
| `SMOKE_USER_EMAIL` / `SMOKE_USER_PASSWORD` | T | 本番のスモークテスト用の一般ユーザー |
| `SMOKE_ADMIN_EMAIL` / `SMOKE_ADMIN_PASSWORD` / `SMOKE_ADMIN_TOTP_SECRET` | T | 本番のスモークテスト用の管理者(2段階認証つき) |
| `DATABASE_URL` | 1 | トンネル経由の本番 DB(`postgres://ttech_web:...@127.0.0.1:54329/postgres`) |
| `BETTER_AUTH_SECRET` / `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` / `MFA_ENCRYPTION_KEY` | 4 | 認証の秘密情報(サーバーの環境変数にだけ置く。手元には置かない) |
| `AUTH_DATABASE_URL` | 4 | Better Auth が DB につなぐ接続文字列(フェーズ6で使うので手元にも置く) |
| `SMOKE_DIARY_DRIVER` / `SMOKE_AUTH_DRIVER` | 3 / 4 | スモークテストが使う日記の呼び方(`api`)・ログインの方法(`better-auth`) |
| `PROD_DB_PORT` | 6 | トンネルでつなぐ本番の DB のポート(新しい DB に移したら `54330`) |

## 確認に使うコマンド(どのフェーズでも同じ)

`C:\dev\appstore\web` で実行する。

| 名前 | コマンド | いつ |
|---|---|---|
| 型チェック | `npm run typecheck` | コードを変えるたび |
| 単体テスト | `npm test` | コードを変えるたび |
| API テスト | `npm run test:api` | ステップの終わり(フェーズTで作る) |
| DB テスト | `npm run test:db` | DB に関わる変更のたび(フェーズTで作る) |
| ビルド | `npm run build` | ステップの終わり |
| 本番スモーク | `npm run test:smoke` | 本番に出した直後(フェーズTで作る) |
| Android | `.\gradlew.bat testDebugUnitTest` | アプリを変えたとき(各アプリのフォルダで) |
