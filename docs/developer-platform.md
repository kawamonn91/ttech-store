# 開発者プラットフォーム(開発者登録・APKの自動審査・自動公開)

誰でも開発者として登録し、自分のアプリをアップロードできる。アップロードされたAPKは自動で検査され、
**外部との通信機能がなく、端末のデータを壊す可能性がない**と確かめられたものだけが、運営の承認なしに自動で公開される。
少しでも疑いがあれば公開せず、理由つきで運営にメールが届き、運営が承認して初めて公開される。

## 全体の流れ

```
開発者(Web /developer)
  ① 登録(無料・承認不要)  POST /api/developer/register
  ② アプリを登録(下書き)   POST /api/developer/apps
  ③ APKをアップロード
       POST /api/developer/apps/:id/releases     … アップロード先(署名付きURL)を発行
       PUT  (R2 へ直接)                          … upload/<app>/<release>.apk
       POST /api/developer/releases/:id/uploaded … 複製 → 検査を起動
                                                                  │
GitHub Actions(scan-release.yml / workers/scan)                   ▼
  APKから「事実」を集める(署名・権限・コード内のAPI・同梱ファイル・ネイティブライブラリ)
  → 署名(HMAC)つきで POST /api/internal/releases/:id/scan
                                                                  │
Web(lib/release-review.ts + lib/policy.ts)                        ▼
  事実をルールに当てて判定 ─┬ 疑いなし   → 自動で公開(アプリも公開)+ 運営・開発者にメール
                            ├ 疑いあり   → 承認待ち(scanned)+ 運営に理由つきでメール
                            └ 明らかな不正 → 却下 + 開発者にメール
```

- 運営(管理者)自身のアプリは、これまでどおり必ず運営が承認してから公開する(`developers.review_mode = 'manual'`)。
  自動審査の結果は記録だけする。
- 停止された開発者・停止されたアプリは、自動審査を通っても必ず承認待ちになる。

## 何を検査するか(ルール: `web/src/lib/policy.ts`)

ワーカーは**事実の抽出だけ**を行い(`workers/scan/facts.mjs`)、通す・止めるの**判断は Web 側**が行う。
ルールを直しても、検査用の環境(GitHub Actions)を触らずに済むようにするため。

| 分類 | 見つけるもの | 結果 |
|---|---|---|
| 通信 | INTERNET・Wi-Fi・Bluetooth・NFC などの権限 / `java.net`・WebView・DownloadManager・OkHttp などのAPI呼び出し(難読化されていても、OSのAPIへの参照で見つける。**INTERNET などの権限があるときだけ**。下の「権限のないアプリの参照」を参照) / コードに含まれる外部URL / 共有ユーザーID / 権限で守られていない公開の Service・ContentProvider | 要確認 |
| 破壊 | 共有ストレージへの書き込み・連絡先やカレンダーの変更・アプリの削除やインストール・SMS・電話・端末管理者・SYSTEM_ALERT_WINDOW などの権限 / 端末管理者・PackageInstaller・設定の書き換え・フォルダ内のファイル削除のAPI / ユーザー補助・通知リスナー・VPN・キーボードのサービス / フォルダ全体へのアクセス要求 | 要確認 |
| 隠れたコード | DEX の動的読み込み・外部プロセスの起動 / assets などに隠された DEX・ELF・入れ子のZIP・スクリプト / **既知でないネイティブライブラリ**(SHA-256 が `known-native-libs.ts` に無いもの) / `System.load` があるのに .so が無い | 要確認 |
| 署名・構造 | デバッグ署名・testOnly・同名ファイルの重複や不正なパスなど構造の異常 | **却下** |
| 署名・構造 | v1 署名のみ・debuggable・古い targetSdk(28未満) | 要確認 |
| 解析不能 | dexdump が使えない・マニフェストを読めない・解析結果が届かない | 要確認(自動承認しない) |
| 更新 | 公開中と同じか古い versionCode | 要確認 |
| 参考 | 位置情報・カメラ・マイク・写真・連絡先を**読む**権限 | 自動承認できる(通信がなければ端末の外には出せない)。参考として記録 |

自動承認の条件は「疑いがまったく無い」こと。要確認の理由は、開発者の画面・メール、運営のメール・マイページに出る。

### 判断の前提

Android は INTERNET 権限のないアプリのネットワーク通信を OS の仕組みで禁止する。そのため通信できないことの決め手は権限だが、
権限の外にある持ち出しの手段(他のアプリ経由・ブラウザに渡すURL・後から読み込むコード)まで、コードと同梱ファイルを調べて確かめる。
ネイティブコードは静的に解析できないので、AndroidX など広く使われている公式ライブラリの既知のバイナリ(SHA-256)だけを許可する。

### 権限のないアプリの参照

縮小(R8)していないビルドには、Kotlin 標準ライブラリや AndroidX の**使われないコード**に、`java.net.URL.openStream` や `TelephonyManager.getImei` などへの参照が入っている(Android Studio の既定の release ビルドがこの状態)。
INTERNET 権限が無ければ OS がソケットの作成を拒否するので、そのAPIを呼んでも通信できない。そこで、**その動作に必要な権限が無いときは、参照があっても指摘にせず、参考情報(`net.api-inert`)として記録するだけ**にしている。

| 参照するAPI | 必要な権限(どれかがあれば指摘する) |
|---|---|
| `java.net`・`java.nio.channels`・`javax.net`・HTTPライブラリ・WebView・DownloadManager | INTERNET |
| SMS・電話(SmsManager・TelephonyManager) | SEND_SMS・READ_PHONE_STATE など(`policy.ts` の `SMS_PHONE_PERMISSIONS`) |

Bluetooth・NFC・USB・ローカルソケット・VPN など、権限がなくても(または別の手段で)使えるものは、参照があれば要確認のまま。
また、権限を持っているアプリは、権限そのものが指摘される。

### 限界(正直に)

- 静的解析なので、難読化された文字列や、複雑なリフレクションで組み立てた処理は見つけられないことがある。
  ただし、実際の通信・端末データの操作は OS の権限で制限されているため、権限なしにできることは限られる。
- 「自動審査を通った」ことは、アプリの品質・適法性・掲載内容の適切さを保証しない(開発者向け規約に明記)。
  掲載内容(名前・説明・画像)は自動審査の対象外で、事後の通報・停止で対応する(下の「今後」)。

## 安全のための仕組み

- **検査後のすり替えの防止**: 署名付きURLは失効させられない。そこで、アップロード先(`upload/...`)と検査・公開に使う場所(`apk/...`)を分け、
  アップロード完了の通知を受けたらサーバー側で複製し、アップロード先を削除する。開発者が持つURLは、使われないキーにしか書き込めない。
- **署名鍵の固定**: 最初に公開したリリースの署名鍵に固定され、以降の更新は同じ鍵でなければ却下(既存のDBトリガー)。
- **なりすまし対策**: 開発者名・アプリ名に「公式」「Google」など紛らわしい語は使えない。パッケージ名は OS・他社・運営のもの(`com.google.`・`com.ttech.` など)が予約されている。開発者名の重複は大文字小文字を区別せずに禁止。
- **利用枠**: 1人あたり、アプリ10本・アップロード24時間に10回・APK 200MB・スクリーンショット8枚(`lib/developer.ts`の`DEVELOPER_LIMITS`)。
- **画像**: サーバーで検査し、PNGに作り直して保存(`lib/images.ts`)。画像に見せかけたファイル・位置情報などのメタデータは配信しない。
- **直接作成の禁止**: アプリ・リリースの作成と、アイコン・スクリーンショットのパスの設定は、API(service_role)経由だけ(RLS・列権限で閉じている)。
- **停止**: 管理コンソール・管理アプリから、開発者・アプリをいつでも停止できる。

## 運営の作業

### 承認待ち(要確認)が来たとき

メール(件名「承認待ちのアプリがあります」)のリンク、またはマイページの「承認待ちのリリース」から、確認が必要な点を見て「公開する」「却下」を選ぶ。
通信が必要な正当なアプリ(地図・天気など)は、内容を確認したうえで公開してよい。

### ネイティブライブラリを既知に加える

AndroidX などのライブラリを更新して、ネイティブライブラリ(`libandroidx.graphics.path.so` など)のバイナリが変わると、
それを含むアプリは「未知のネイティブライブラリ」として要確認になる。中身を確かめたうえで、SHA-256 を `web/src/lib/known-native-libs.ts` に加える。
新しい値は、そのAPKで次のコマンドを実行すると分かる:

```sh
ANDROID_HOME=<Android SDK> node workers/scan/facts-cli.mjs app-release.apk
```

### 審査ルールを調整する

1. 手元のAPKで `facts-cli.mjs` を実行して、事実(権限・API・URLなど)を確認する。
2. `workers/scan/patterns.mjs`(コード内のAPIの一覧)や `web/src/lib/policy.ts`(判断)を直す。
3. 実APKから取った事実を `web/src/lib/testing/fixtures/` に置き、`policy.test.ts` の「実際のAPKでの判定」で確かめる。
   (自作アプリ53本での較正では、通信なしのアプリは自動承認、WebView・PDFライブラリ・外部URLを含むアプリは要確認になることを確認した。)

## 初回セットアップ(一度だけ)

1. **DBのマイグレーションを適用する**: `supabase/migrations/20260926000000_developer_platform.sql`
   (Supabase の SQL エディタに貼って実行)。運営の開発者は自動で `manual` になる。
   - 検証: `supabase test db`(CI)/ Docker だけで確かめる場合は `supabase/sql-tests/developer-platform/README.md`。
2. **GitHub の検査ワークフロー**: `scan-release.yml` は既存のもの(変更は、ツールの存在確認の手順の追加のみ)。
   実行環境に `dexdump`(Android SDK の build-tools)があることを確認する。無いと、コードを解析できず、すべて要確認になる(安全側に倒れる)。
   Vercel の環境変数 `GITHUB_DISPATCH_TOKEN` / `GITHUB_REPO`、GitHub の Secrets `SCAN_WEBHOOK_SECRET` は、管理コンソールからのアップロードで使っているものと同じ。
3. **メール(Resend)**: 運営宛は今の設定で届く。**開発者宛**のメールは、差出人が `onboarding@resend.dev` のうちは、自分以外のアドレスには送れない。
   独自ドメインを Resend で認証し、`MAIL_FROM` を設定すると届く(未設定でも、開発者の画面には結果が出る)。
4. **R2 の CORS**: 開発者のブラウザから直接アップロードする。管理コンソールで使っている設定(サイトのオリジンからの PUT)と同じ。

## テスト

| 対象 | 実行 |
|---|---|
| ワーカー(ZIP・マニフェスト・DEX・署名) | `node --test "workers/scan/*.test.mjs"` |
| Web(ルール・判定・自動公開・開発者API・画像・R2) | `cd web && npm test` |
| DB(RLS・列権限・制約) | `supabase test db` / `supabase/sql-tests/developer-platform` |

## 今後(未実装)

- アプリの**通報**(掲載内容の不適切さ・なりすましの通報窓口と、運営の対応画面)。
- 公開後の**再検査**(ルールを強化したときに、公開中のアプリを検査し直す)。
- 開発者のメールアドレスの確認以上の本人確認。
