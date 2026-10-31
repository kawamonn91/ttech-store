# エージェント向けの指示

## フォルダ構成(パスを推測する前に見る)

- `web/` — Next.js(store.kawamonn.com)。ストア用スクリプトは `web/scripts/`(`publish-app.mjs`・`set-app-icon.mjs` など)
- `store-app/` — ストアアプリ。コードは `store-app/app/src/main/java/com/kawamonn/store/`、画面は `ui/`(`ui/account/`・`ui/auth/` など)。`native-apps/` の下ではない
- `native-apps/` — 配布アプリの Gradle マルチモジュール(一覧は `native-apps/settings.gradle.kts`)。各アプリのコードは `native-apps/<slug>/src/main/java/com/ttech/<slug からハイフンを除いた名前>/`(例: `drive-record` → `com/ttech/driverecord`)。例外: `ttech-admin` → `com/ttech/admin`、`track-common` → `com/ttech/track`
- `workers/scan/` — APK の検査、`supabase/` — マイグレーション、`docs/` — 手順書と計画書

## アプリを公開するとき

- `publish-app.mjs` の前に `versionCode` を上げる。同じ versionCode だと Supabase が 409(`(app_id, version_code)` の重複)を返して失敗する

## リファクタリング計画

- リファクタリング計画は [docs/refactoring-plan.md](docs/refactoring-plan.md) にある。
- **計画の実行(または計画の一部の実行)を頼まれたら、コードを変更する前に、計画書の「着手前に決めておくべき事項」のうち「決定」が空欄の項目を持ち主に確認し、了承を得ること。**
  決まった内容は、計画書の「決定」の列に日付つきで書き込んでから作業を始める。
- 作業を進めたら、計画書の冒頭の「状態」と、各項目の進み具合を更新する。

## Supabase 依存をなくす計画

- 計画は [docs/supabase-removal-plan.md](docs/supabase-removal-plan.md) にある。
- 実行の手順書は [docs/supabase-removal/README.md](docs/supabase-removal/README.md) から始まる。実行する前に README の「作業のルール」を読むこと。
- 実行を頼まれたときの扱いは、上のリファクタリング計画と同じ(「決定」が空欄の項目を先に確認し、日付つきで書き込んでから始める)。
