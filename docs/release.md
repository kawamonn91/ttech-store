# CD（ストアアプリと native-apps のリリース版）

`.github/workflows/release-app.yml`（ジョブ `release`）が、署名済みのリリース版を作って GitHub Releases に置く。
T-tech Store への公開は、ここで作った APK を手元から `web/scripts/publish-app.mjs` で行う（本番の Supabase と R2 の鍵を GitHub に置かないため）。
Web（store.kawamonn.com）は対象外で、今までどおり `web/scripts/deploy-web.ps1` で raspi1 に出す。

## 動き

`アプリ名/v版` のタグを push すると動く。アプリ名は `store-app`、または `native-apps/settings.gradle.kts` に include したモジュール名。

| タグの例 | 作るもの |
| --- | --- |
| `store-app/v0.6.5` | ストアアプリ（`ttech-store-0.6.5.apk`） |
| `weight-log/v2.0.2` | native-apps の weight-log（`weight-log-2.0.2.apk`） |

1. タグの版が、そのアプリの `build.gradle.kts` の `versionName` と合っているか確かめる
2. GitHub Secrets の `SIGNING_<アプリ名>`（大文字にして `-` を `_` に。`store-app` → `SIGNING_STORE_APP`）から、鍵の束をアプリのフォルダに展開する
3. そのアプリの単体テストとリリース版のビルド
4. `apksigner` で署名を確かめる。証明書の SHA-256 が、ストアに出している版と同じか（ワークフローに書いてある値）も見る
5. APK と `SHA256SUMS` を GitHub Releases に置く（リリースの名前はタグと同じ）

手で動かす（`gh workflow run release-app.yml -f app=weight-log`）と、4 までを行ってリリースは作らない（CD の動作確認用）。

## GitHub Secrets

アプリごとに 1 つ。中身は、そのアプリのフォルダの次のファイルを tar.gz にまとめて base64 にしたもの。

- `keystore.properties` と、そこに書いた署名鍵（`*.jks`）
- pocket-dev は `google-oauth.properties`（Drive のログイン）も
- store-app は `googleWebClientId` だけを書いた `local.properties`（Google ログイン）も

登録（し直し）は `bash tools/set-release-secrets.sh`（全部）か `bash tools/set-release-secrets.sh weight-log`（指定したものだけ）。
手元のファイルから読み、値をコマンドの引数に出さずに標準入力から `gh secret set` に渡す。

## 新しい版を出す

1. 作業ブランチで、そのアプリの `versionCode` を 1 つ上げ、`versionName` を変える（同じ versionCode だと公開で 409 になる）
2. PR を作り、CI が通って main にマージされるのを待つ
3. main でタグを付けて push し、`release-app` が終わるのを待つ

   ```bash
   git switch main && git pull --ff-only
   git tag weight-log/v2.0.2 && git push origin weight-log/v2.0.2
   gh run watch "$(gh run list --workflow release-app.yml --limit 1 --json databaseId -q '.[0].databaseId')"
   ```

4. Releases の APK を手元に取り、`publish-app.mjs` で出す（本番の鍵は raspi1 から環境変数にだけ読む。`~/.claude/notes/infra.md` の「T-tech Store のスクリプトを本番に向けて動かす」）

   ```bash
   gh release download weight-log/v2.0.2 -R kawamonn91/ttech-store -p '*.apk' -D "$SP"
   cd web
   while IFS='=' read -r k v; do [ -n "$k" ] && export "$k=$v"; done < <(ssh -o BatchMode=yes raspi1 'grep -E "^(NEXT_PUBLIC_SUPABASE_URL|SUPABASE_SERVICE_ROLE_KEY|R2_[A-Z_]+)=" /home/pi/.config/ttech-store/env')
   node scripts/publish-app.mjs --apk "$SP/weight-log-2.0.2.apk" --notes "…" --publish
   ```

## アプリを足したとき

1. `native-apps/settings.gradle.kts` に include する
2. 鍵の証明書の SHA-256 を `release-app.yml` の `case` に足す（`keytool -list -v -keystore <鍵> -alias <別名> -storepass:env 変数名` の `SHA256:` を、`:` を除いて小文字にしたもの）
3. `bash tools/set-release-secrets.sh <アプリ名>` で鍵を登録する
