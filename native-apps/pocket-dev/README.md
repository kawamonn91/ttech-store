# pocket-dev（アプリ）

スマホ・タブレットで使う IDE とターミナルのアプリ（`com.ttech.pocketdev`）。
画面は pocket-dev（kawamonn91/pocket-dev）の `web/` を WebView で開く。取り込んだ写しは `src/main/assets/pocket-dev/web/`。

## 接続先

| タブ | つなぐ先 | 編集 | 実行（ターミナル） |
|---|---|---|---|
| Pocket Linux | 同じ端末の Pocket Linux の IDE のサーバー（127.0.0.1） | ○ | ○ |
| リモート | PC・VPS で動かす pocket-dev のサーバー | ○ | ○ |
| SSH | SSH のサーバー（SFTP とシェル） | ○ | ○ |
| Drive | Google ドライブ（直結） | ○ | ×（実行は Pocket Linux で） |

SSH と Drive は、アプリ内の中継サーバー（`bridge/BridgeServer.kt`）が pocket-dev のサーバーと同じ API
（`/api/info`・`/api/fs/*`・`/ws/pty`）を 127.0.0.1 に出し、WebView はそこを開く。
中継サーバーのトークンはつなぐたびに作り、`/health` 以外は WebSocket も含めてトークンを確かめる。
Drive にはターミナルが無いので、`/api/info` の `terminal` を `false` にし、IDE は実行のボタンを隠す。

## Google ドライブ直結の仕組み

- 同梱の rclone（`src/main/jniLibs/*/librclone.so`）をアプリのプロセスから直接動かす（`drive/Rclone.kt`）
  - 名前解決は端末内の CONNECT プロキシ（`drive/ConnectProxy.kt`、`HTTPS_PROXY`）、証明書は `SSL_CERT_DIR`。Pocket Linux と同じ
  - ログインは `rclone authorize drive <client_id> <client_secret>`（下の「Google のログインの設定」）。
    ブラウザ（Custom Tabs）で許可している間にアプリが凍結されないよう、ログインの間だけ常駐サービス（`drive/DriveLoginService.kt`）を動かす
  - ログインの情報はアプリ専用の領域の `files/rclone/rclone.conf`
- 編集は `rclone rcd`（遠隔操作の API）を 127.0.0.1 で動かして頼む（`drive/RcloneDaemon.kt`・`drive/DriveBackend.kt`）。合言葉は起動ごとに作る
  - 一覧 `operations/list`、読む `--rc-serve`（`GET /[gdrive:]/パス`）、書く `operations/uploadfile`、
    フォルダの作成 `operations/mkdir`、削除 `operations/deletefile`・`purge`・`rmdir`（Drive のゴミ箱に入る）、
    名前の変更 `operations/movefile`（ファイル）・`sync/move`（フォルダ。そのあと `fscache/clear`）
  - Google ドキュメントなどは一覧に出さない（`--drive-skip-gdocs`）
- 同梱は arm64-v8a と x86_64 だけ（Pocket Linux と同じ）。32 ビットだけの端末にはインストールできない

## Google のログインの設定

rclone 共有の client_id は 2026 年中に使えなくなり（[rclone の案内](https://rclone.org/drive/#making-your-own-client-id)）、
利用者全員で上限を分け合うので `rateLimitExceeded` も出やすい。そのため自前の OAuth クライアントを使う。

- Google Cloud の専用のプロジェクトで Drive API を有効にし、種類が「デスクトップ アプリ」のクライアントを作る。
  ID と秘密鍵を `google-oauth.properties`（Git の対象外）に書くと、ビルドのときに `BuildConfig` に入る

  ```properties
  clientId=xxxx.apps.googleusercontent.com
  clientSecret=xxxx
  ```

- このファイルが無いと、デバッグ版は Drive タブに「設定が入っていない」と出し、リリース版はビルドを止める
- 秘密鍵は APK に入る。デスクトップ用のクライアントの秘密鍵は、Google も秘密としては扱っていない。
  それでも公開リポジトリには入れない（検出されて無効にされるのを避けるため）。秘密鍵は作ったときにしか表示されないので、ファイルは控えておく
- 同意画面（アプリ名「T-tech Pocket」）は「本番環境」で公開する。「テスト中」のままだとログインが 7 日で切れる
- Google の確認は受けていないので、同意画面に「このアプリは Google で確認されていません」と出て、利用者は 100 人までになる。
  それを超えて配るには、`drive` の範囲についての Google の審査が要る
- トークンは取ったクライアントでしか更新できないので、クライアントを変えるとログインし直しになる（`Rclone.isLoggedInWith`）

## rclone を作り直す

```bash
# Linux か WSL で（Go 1.27.3 と git が必要）
./scripts/build-rclone.sh
RCLONE_SRC=~/src/rclone-v1.75.2 ./scripts/build-rclone.sh   # 取得済みのソースを使う
```

入れる機能は `scripts/rclone/main.go`（Drive・authorize・rcd・version）。
CI（`.github/workflows/pocket-dev-rclone.yml`）が作り直して、コミットされたものと同じかを確かめる。
ライセンス（MIT）は `src/main/assets/licenses/rclone.txt` で、アプリの「オープンソースのライセンス」に出す。
