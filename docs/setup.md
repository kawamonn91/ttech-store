# セットアップ手順

外部サービスのアカウント作業(★)はご自身で行う必要があります。得られた値は `web/.env.local`(ローカル)と、本番サーバーの環境変数ファイル(リポジトリの外。4 を参照)に設定します。
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
3. Web から検査を起動するための Fine-grained PAT を作成(対象リポジトリのみ、`Contents: Read and write`)→ Web の環境変数 `GITHUB_DISPATCH_TOKEN`。`GITHUB_REPO` は `owner/repo`。

## 4. 本番サーバー + ドメイン ★

Web は自前のサーバー(Node.js 24 と PM2 が入った Linux。今は Raspberry Pi)で動かし、Cloudflare Tunnel で
`store.kawamonn.com` に公開する(ルーターのポート開放は不要)。`kawamonn.com` のハブ(別リポジトリ `kawamonn-site`)も同じサーバーから配信している。
Web は SSR/API が必要なので static export ではなく `next start` で動かす。`web/` は単独プロジェクトなので、`web/` だけあれば動く。

1. サーバーでリポジトリを取得する(Web だけなら sparse checkout でよい):
   ```bash
   git clone --filter=blob:none --sparse https://github.com/kawamonn91/ttech-store.git
   cd ttech-store && git sparse-checkout set web
   ```
2. `.env.example` の環境変数をすべて入れたファイルを**リポジトリの外**(例: `~/.config/ttech-store/env`、権限 600)に作り、
   `web/.env.production.local` からシンボリックリンクで参照する(`.env*` は .gitignore 済み)。
   `NEXT_PUBLIC_*` はビルド時に埋め込まれるので、変えたらビルドし直す。
3. ビルドして PM2 で常駐させる。待ち受けは `127.0.0.1` だけにして、外からは Tunnel 経由でしか届かないようにする:
   ```bash
   cd web && npm ci && npm run build
   pm2 start npm --name ttech-store-web -- run start -- -p 3200 -H 127.0.0.1
   pm2 save
   ```
4. Cloudflare の Tunnels で、トンネルの公開アプリケーションに `store.kawamonn.com` → `http://localhost:3200` を追加する。
   同じ名前の DNS レコードが既にあると自動では作られないので、`store` をトンネルの CNAME(プロキシ有効)に書き換える。
5. 以後の反映は、main に push してから手元で `web/scripts/deploy-web.ps1` を実行する(サーバーで pull → `npm ci` → build → `pm2 restart`)。
   接続先は `web/.env.local` の `DEPLOY_SSH`(例: `user@host`)と `DEPLOY_DIR`(サーバー上のリポジトリの場所)で指定する(リポジトリには入れない)。

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
