# T-tech Store

T-tech のAndroidアプリを配布するストア。Webサイト(`store.kawamonn.com`)と専用ストアアプリ(Kotlin)で構成する。
`kawamonn.com`(別リポジトリ `kawamonn-site`)のハブページからリンクされる。

```
web/            Next.js (App Router) — 公開サイト・管理コンソール・API(/api/v1)
                  web/public/policies/ … ストア掲載アプリのプライバシーポリシー
store-app/      Kotlin + Jetpack Compose — ストアアプリ(com.kawamonn.store)
native-apps/    ストアで配布するアプリのソース(Kotlin)。各フォルダが1アプリ
supabase/       DBマイグレーション(スキーマ + RLS)とseed
workers/scan/   APK検査(署名・パッケージ情報・ウイルススキャン)。GitHub Actionsから実行
tools/          モックAPIなどの開発用ツール
docs/           セットアップ手順・API仕様
```

## 関連リポジトリ(このストアには含めない)

ストアで配布しているが、開発を独立して進めるアプリは別リポジトリで管理する。

| アプリ | リポジトリ |
|---|---|
| ヨムメモ(`jp.yomumemo.app`) | https://github.com/kawamonn91/yomumemo (公開サイトは GitHub Pages) |
| DebtRun(`com.ttech.debtrun`) | https://github.com/kawamonn91/Digital_detox_app |
| ひとこと日記(`com.ttech.diary`) | https://github.com/kawamonn91/diary |
| PDF日本語化ツール(Windows版・Android版) | https://github.com/kawamonn91/PDF_Translate_Tool |

ストアのプライバシーポリシーは、上の `web/public/policies/` で配信する(`https://store.kawamonn.com/policies/…`)。
ヨムメモ専用のポリシーだけは、ヨムメモのリポジトリの公開サイトにある。

## 仕組み

1. 管理コンソール(`/admin`)でアプリを登録し、APKをアップロードする(ブラウザから Cloudflare R2 へ直接PUT)。
2. GitHub Actions が APK を検査(SHA-256・`apksigner` による署名検証・`aapt2` による package/version/権限・VirusTotal)し、結果を署名付きで Web に通知する。
3. 検査に通ったリリースを管理者が「公開する」。DBトリガーが**署名鍵を初回の値に固定**し、以降は別の鍵のAPKを承認できない。
   開発者として登録した第三者(`/developer`)のAPKは、通信機能・端末データの破壊・隠れたコードが見つからなければ**自動で公開**され、
   疑わしい点があれば理由つきで管理者の承認待ちになる([docs/developer-platform.md](docs/developer-platform.md))。
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

# APK検査ワーカーのテスト
node --test "workers/scan/*.test.mjs"

# ストアアプリ
cd store-app && ./gradlew testDebugUnitTest assembleDebug
# エミュレータからローカルのWebに繋ぐ: store-app/local.properties に
#   storeApiBase=http://10.0.2.2:3000/api/v1
```

## テストとCI

| 対象 | テスト | 実行 |
|---|---|---|
| Web(署名検証・検査結果の判定・DL発行・R2署名URL・DTO変換・各APIルート) | Vitest(モック使用) | `cd web && npm test` |
| ストアアプリ(ハッシュ/署名の検証ロジック・APIクライアント・再開ダウンロード・更新判定) | JUnit + MockWebServer | `cd store-app && ./gradlew testDebugUnitTest` |
| APK検査ワーカー(apksigner / aapt2 出力のパーサー) | node:test | `node --test "workers/scan/*.test.mjs"` |
| DB(RLS・列権限・署名鍵固定トリガー・DL重複除外・評価集計) | pgTAP | `supabase test db`(要 Docker) |

CI(`.github/workflows/ci.yml`)は push / PR ごとに上の4つ+ Web の lint・build(型チェック込み)+ Android の assembleDebug を実行する。
ストアアプリのインストール処理そのもの(PackageInstaller)は、`tools/mock-api/server.mjs` のモックAPIとエミュレータで手動確認する:

```bash
node tools/mock-api/server.mjs <任意のAPK>     # good / tampered(ハッシュ改ざん) / badcert(署名不一致) の3件を配信
# store-app/local.properties に storeApiBase=http://10.0.2.2:8787/api/v1 を書いてビルド → エミュレータにインストール
```

## 方針メモ

- 課金は未実装。`apps.price_yen` は将来用(常に0)。導入時は Stripe + `entitlements` + ダウンロード時の権限チェック(`web/src/lib/download.ts`)を想定。
  既存アプリの Google Play Billing は Play 配布でないと使えないため、ストア配布版には別方式が必要。
- Vercel CLI はこのPCのホスト名が日本語のため `vercel login` が失敗する。Personal Access Token を作って `--token` を付けて使う。
- ストアアプリの署名鍵(`store-app/keystore.properties`)は**失くさない・変えない**(ストア経由の自己更新ができなくなる)。
