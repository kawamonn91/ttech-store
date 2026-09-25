# セットアップ手順

外部サービスのアカウント作業(★)はご自身で行う必要があります。得られた値は `web/.env.local`(ローカル)と Vercel の環境変数(本番)に設定します。
**`SUPABASE_SERVICE_ROLE_KEY` / `R2_SECRET_ACCESS_KEY` / `SCAN_WEBHOOK_SECRET` は秘密情報です。チャットやリポジトリに貼らないでください。**

## 1. Supabase ★

1. https://supabase.com で新規プロジェクトを作成(リージョン: Tokyo)。
2. SQL Editor に `supabase/migrations/20260920000000_init.sql` を貼って実行 → 続けて `supabase/seed.sql` を実行。
3. Project Settings > API から `URL` / `anon key` / `service_role key` を控える。
4. Authentication > Providers で Email を有効化(管理コンソールのログインに使う)。Google ログインは Phase 2。
5. 自分のアカウントを管理者にする: Authentication > Users でユーザーを作成し、SQL Editor で
   ```sql
   update public.profiles set role = 'admin' where id = '<そのユーザーのUUID>';
   ```
6. 無料プロジェクトは1週間アクセスが無いと一時停止する。定期的に開くか、`/api/v1/home` を定期実行する(GitHub Actions の cron 等)。

## 2. Cloudflare R2 ★

1. Cloudflare ダッシュボード > R2 でバケット `ttech-store-apk` を作成(**公開しない**)。
2. R2 > Manage API Tokens で、そのバケットへの「Object Read & Write」トークンを作成 → `Access Key ID` / `Secret Access Key` と、アカウントID を控える。
3. バケットの Settings > CORS に以下を設定(管理コンソールからの直接アップロード用):
   ```json
   [
     {
       "AllowedOrigins": ["https://store.kawamonn.com", "http://localhost:3000"],
       "AllowedMethods": ["PUT"],
       "AllowedHeaders": ["Content-Type"],
       "MaxAgeSeconds": 3600
     }
   ]
   ```

APK は常に期限付き署名URLで配信するため、R2 の公開ドメイン設定は不要。

## 3. GitHub(APK検査) ★

1. `appstore` リポジトリを GitHub に作成して push(プライベート推奨)。
2. リポジトリの Settings > Secrets and variables > Actions に登録:
   - `SCAN_WEBHOOK_SECRET` … ランダムな長い文字列(Web の環境変数と同じ値)
   - `VT_API_KEY` … (任意)VirusTotal の API キー。無ければウイルススキャンは skipped 扱い
3. Web から検査を起動するための Fine-grained PAT を作成(対象リポジトリのみ、`Contents: Read and write`)→ Vercel の `GITHUB_DISPATCH_TOKEN`。`GITHUB_REPO` は `owner/repo`。

## 4. Vercel + ドメイン ★

`store.kawamonn.com` に Web を、`kawamonn.com` にハブ(別リポジトリ `kawamonn-site`)を割り当てる。

1. vercel.com/account/tokens で Personal Access Token を作成(この端末では `vercel login` が使えないため)。
2. `web/` を Vercel プロジェクトとして作成し、`.env.example` の環境変数をすべて設定。
   Web は SSR/API が必要なので static export ではなく通常の Next.js デプロイ。`web/` は単独プロジェクト(pnpm workspace ではない)なので、Vercel 側のビルドでそのまま動く。
3. Vercel の Domains に `store.kawamonn.com` を追加。表示される CNAME を Cloudflare DNS に登録(プロキシは OFF = DNS only を推奨)。
4. `kawamonn-site` を別プロジェクトとしてデプロイし、`kawamonn.com` を割り当てる(Apex は A レコード `76.76.21.21` か CNAME フラットニング)。

## 5. ストアアプリの署名鍵

```bash
keytool -genkeypair -v -keystore store-app/ttech-store.jks -alias ttech-store -keyalg RSA -keysize 2048 -validity 36500
```

`store-app/keystore.properties` に `storeFile` / `storePassword` / `keyAlias` / `keyPassword` を書く(どちらも .gitignore 済み)。
**この鍵は失くさない・変えない。** 変えると既存ユーザーがストア経由で自己更新できなくなる。バックアップを別の場所にも保存すること。

リリースビルド: `cd store-app && ./gradlew assembleRelease` → 管理コンソールで `com.kawamonn.store` としてアップロードして公開すると、`/download` から取得できる。

## 6. 最初のアプリを載せる

1. `/admin/login` にログイン → 新規アプリ(スラッグ・パッケージ名・説明)。
2. アイコン・スクリーンショットを登録し、APK をアップロード。
3. 検査完了後(数分)、「承認待ち」のリリースを確認して「公開する」→「アプリを公開する」。
4. 最初にストアアプリ自身(`com.kawamonn.store`)を同じ手順で登録すると、Web の `/download` から入手できる。

## 7. 開発者の受け入れ(自動審査)

誰でも `/developer` から開発者として登録し、アプリをアップロードできる。APKは自動で検査され、通信機能や端末データを壊す可能性が見つからなければ、
承認なしで公開される。疑わしい点があれば運営の承認待ちになる。初回の設定(DBマイグレーション・検査ツール・メール)は
[developer-platform.md](developer-platform.md) の「初回セットアップ」を参照。
