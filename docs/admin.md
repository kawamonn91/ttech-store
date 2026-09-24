# 管理アプリ・報告メール通知

## 全体像

```
管理アプリ「T-tech 管理」(Android) ── Bearer ──▶ Web の /api/admin/*  ──(service_role)──▶ Supabase
                                       ログインは Supabase Auth(メール+パスワード、2段階認証があればTOTP)

利用者が報告 ─▶ DBトリガー ─(pg_net)─▶ /api/internal/notify ─▶ Resend ─▶ 運営のメール
```

- 管理アプリは Supabase を直接触らない。**すべて Web の `/api/admin/*` 経由**で、サーバー側が毎回 `requireAdmin`
  (role=admin、2段階認証を設定済みなら aal2)を確認する。管理者でなければ 401。
- 管理者専用アプリ(`apps.admin_only`)は、公開カタログ(一覧・検索・更新確認・最新リリースのビュー)に出ず、
  ダウンロードAPIも管理者のトークンが無ければ「見つからない」と同じ応答を返す。
  ダウンロードできるのは Web のマイページ、または ストアアプリのマイページ(どちらも管理者ログイン時のみ)。

## 管理アプリでできること

| 画面 | 内容 | API |
|---|---|---|
| ホーム | 全サービスの件数、対応が必要なもの(未対応の報告・開発者の申請) | `GET /api/admin/overview` |
| 報告 | ひとこと日記の報告・レビュー通報。問題なし / 投稿を削除(レビューは非表示) / 投稿者をBAN | `GET /api/admin/reports`、`POST /api/admin/reports/{diary\|review}/{id}` |
| ユーザー | メール・表示名で検索、BAN中のみ。詳細(アカウント・各サービスでの活動・報告された履歴)、BAN/解除 | `GET /api/admin/users`、`GET /api/admin/users/{id}`、`POST .../ban`、`POST .../unban` |
| 投稿 | ひとこと日記の投稿(非公開も含む)を本文で検索、写真の確認、削除 | `GET /api/admin/diary/entries`、`DELETE /api/admin/diary/entries/{id}` |
| その他 | 開発者の承認・停止、アプリの公開・停止、監査ログ | `/api/admin/developers`、`/api/admin/apps`、`/api/admin/audit` |

すべての操作は `admin_audit_log` に記録される(誰が・いつ・何を)。

### BANの仕組み

1. `profiles.banned_at` を立てる → **restrictive RLSポリシー**で、今持っているトークンでも読み書きが即座に止まる
   (ひとこと日記・友達・ブロック・報告・レビュー・日記の写真)。BANされた人の投稿は、他の人からも見えなくなる。
2. Supabase Auth の `ban_duration` を設定 → 新しいログイン・トークン更新ができなくなる。

自分自身と、ほかの管理者はBANできない。アカウントの削除(利用者自身によるもの)は、BAN中でもできる。

## 初回セットアップ(一度だけ)

1. **DBの更新**: Supabase の SQL Editor で `supabase/migrations/20260925000000_admin.sql` を実行する。
   (実行するまで、Web をデプロイしてはいけない。ダウンロードAPIが `apps.admin_only` 列を参照するため)
2. **メールの準備**: [Resend](https://resend.com) のアカウントを作り、API キーを発行する。
   独自ドメインを認証していない間は、差出人が `onboarding@resend.dev` になり、
   **Resend のアカウントに登録したメールアドレス宛にだけ**届く(運営自身宛の通知なので、それで足りる)。
3. **Vercel の環境変数**(Production): `RESEND_API_KEY`、`ADMIN_NOTIFY_EMAIL`(通知の宛先)、`NOTIFY_WEBHOOK_SECRET`
   (ランダムな長い文字列)。独自ドメインを認証したら `MAIL_FROM` も設定する。
4. **DBに通知先を登録**: SQL Editor で次を実行する(`secret` は Vercel の `NOTIFY_WEBHOOK_SECRET` と同じ値)。
   ```sql
   insert into private.notify_settings (key, value) values
     ('url', 'https://store.kawamonn.com/api/internal/notify'),
     ('secret', '<NOTIFY_WEBHOOK_SECRET と同じ値>')
   on conflict (key) do update set value = excluded.value;
   ```
   未設定のあいだは通知が送られないだけで、報告の登録は普通にできる。通知が失敗しても報告は止まらない(警告だけ出る)。
5. Web をデプロイする。

## 通知が届く出来事

| 出来事 | 件名 |
|---|---|
| ひとこと日記の投稿が報告された | `[ひとこと日記] 投稿が報告されました(理由)` |
| アプリのレビューが通報された | `[T-tech Store] レビューが通報されました(アプリ名)` |
| 開発者の申請が届いた | `[T-tech Store] 開発者の申請が届きました(名前)` |

メールは**テキストのみ**(利用者が書いた内容を含むため、HTMLとして解釈させない)。DBから届くのは種類とIDだけで、
本文はサーバーがDBから読み直してメールにする。

## 管理アプリの公開

```sh
cd native-apps && ./gradlew :ttech-admin:assembleRelease          # JDK 17
cd ../web && node scripts/publish-app.mjs \
  --apk ../native-apps/ttech-admin/build/outputs/apk/release/ttech-admin-release.apk \
  --name "T-tech 管理" --short-desc "T-tech の管理者専用アプリ" --admin-only --publish
```

- `--admin-only` は**新規登録の時点から**管理者専用にする(あとから切り替えると一瞬公開される)。
- 署名鍵(`native-apps/ttech-admin/ttech-admin.jks`)は Git に入れない。バックアップは `G:\マイドライブ\app\ttech-admin\`。
  失くす・変えると、管理アプリを更新できなくなる。

## テスト

- Web: `cd web && npx vitest run`(`moderation`・`admin-queries`・`notify`・`mail`・各APIルートのテスト)
- DB: `supabase/tests/admin/README.md`(Docker の使い捨て Postgres で、BAN・管理者専用アプリ・通知トリガーを検証)
- Android: `cd native-apps && ./gradlew :ttech-admin:testDebugUnitTest`
- 管理アプリのUI確認用に、実サーバーの代わりになる検証用サーバーを使った(本番には繋がない)。
  デバッグビルドだけ、`./gradlew -PadminWebBase=http://10.0.2.2:8787 -PsupabaseUrl=http://10.0.2.2:8787 :ttech-admin:assembleDebug` で接続先を変えられる。
