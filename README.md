# T-tech Store

T-tech のAndroidアプリを配布するストア。Webサイト(`store.kawamonn.com`)と専用ストアアプリ(Kotlin)で構成する。
`kawamonn.com`(別リポジトリ `kawamonn-site`)のハブページからリンクされる。

```
web/            Next.js (App Router) — 公開サイト・管理コンソール・API(/api/v1)
store-app/      Kotlin + Jetpack Compose — ストアアプリ(com.kawamonn.store)
supabase/       DBマイグレーション(スキーマ + RLS)とseed
workers/scan/   APK検査(署名・パッケージ情報・ウイルススキャン)。GitHub Actionsから実行
docs/           セットアップ手順・API仕様
```

## 仕組み

1. 管理コンソール(`/admin`)でアプリを登録し、APKをアップロードする(ブラウザから Cloudflare R2 へ直接PUT)。
2. GitHub Actions が APK を検査(SHA-256・`apksigner` による署名検証・`aapt2` による package/version/権限・VirusTotal)し、結果を署名付きで Web に通知する。
3. 検査に通ったリリースを管理者が「公開する」。DBトリガーが**署名鍵を初回の値に固定**し、以降は別の鍵のAPKを承認できない。
4. ストアアプリは公開中のアプリを一覧・検索・詳細表示し、インストール時に
   ダウンロード → **SHA-256照合** → **パッケージ名/versionCode/署名証明書の照合** → PackageInstaller でインストールする。
5. アップデートは端末内で判定する(サーバーにインストール済みアプリ一覧を送らない)。WorkManager が12時間ごとに確認して通知する。

アプリのダウンロードはストアアプリ内で完結する。Webからは「ストアアプリで開く」導線のみ(ストアアプリ自身のAPKだけは `/download` から直接取得できる)。

## セットアップ

[docs/setup.md](docs/setup.md) を参照。

## 開発

```bash
# Web
cd web && npm install && npm run dev      # http://localhost:3000
npm test && npm run typecheck && npm run build

# APK検査ワーカーのパーサーテスト
node --test workers/scan/inspect.test.mjs

# ストアアプリ
cd store-app && ./gradlew testDebugUnitTest assembleDebug
# エミュレータからローカルのWebに繋ぐ: store-app/local.properties に
#   storeApiBase=http://10.0.2.2:3000/api/v1
```

## 方針メモ

- 課金は未実装。`apps.price_yen` は将来用(常に0)。導入時は Stripe + `entitlements` + ダウンロード時の権限チェック(`web/src/lib/download.ts`)を想定。
  既存アプリの Google Play Billing は Play 配布でないと使えないため、ストア配布版には別方式が必要。
- Vercel CLI はこのPCのホスト名が日本語のため `vercel login` が失敗する。Personal Access Token を作って `--token` を付けて使う。
- ストアアプリの署名鍵(`store-app/keystore.properties`)は**失くさない・変えない**(ストア経由の自己更新ができなくなる)。
