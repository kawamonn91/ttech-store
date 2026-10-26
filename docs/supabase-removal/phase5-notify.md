# フェーズ5: 運営への通知を Web の処理に移す

## なぜ
日記の通報・レビューの通報・開発者の登録があると、DB のトリガー(`private.trg_notify_*`)が `pg_net` の `net.http_post` で Web の `/api/internal/notify` を呼び、運営にメールが届きます。`pg_net` は Supabase の拡張で、素の PostgreSQL には入っていません。フェーズ3・4で、これらの書き込みはすべて Web を通るようになったので、**書き込んだ Web の処理の中で、直接通知を送る**形にします。

## 前提
- フェーズ4 が終わっている。
- ブランチ: `supabase-removal/phase5-notify`。マージ前の main を「進み具合」に書く。

---

## ステップ 5-1: 通知を送る関数を1つにする

`src/lib/notify.ts` に足す:
```ts
/**
 * 運営に通知メールを送る。送れなくても呼び出し元の処理は失敗させない(ログに残すだけ)。
 * これまで DB のトリガー → pg_net → /api/internal/notify で行っていたことを、Web の処理の中で直接行う。
 */
export async function notifyAdmin(kind: NotifyKind, id: string): Promise<void>;
```
- 中身: `loadNotification(..., kind, id)` でメールを作り、既存の送信の関数(`/api/internal/notify` の `POST` の中で使っているもの)で送る。例外は `console.error` して握りつぶす。
- `loadNotification` の第1引数(今は `SupabaseClient`)は、フェーズ1で `Db` に変わっているはず。`asService` の中で呼ぶ。
- `/api/internal/notify` のルートは、`notifyAdmin` を呼ぶだけにする(署名の確認はそのまま。フェーズ6まで、DB の古いトリガーから呼ばれる可能性があるため残す)。

## ステップ 5-2: 書き込みの直後に通知を送る

| 通知の種類 | 書き込む場所 | 足す呼び出し |
|---|---|---|
| 日記の通報 | `src/app/api/v1/diary/reports/route.ts`(フェーズ3) | `diary_report_entry` の成功のあと、作られた `diary_reports.id` で `notifyAdmin("diary_report", id)`。関数が ID を返さなければ、`asUser` の同じトランザクションで、自分の最新の通報の ID を読む |
| 開発者の登録 | `src/app/api/developer/register/route.ts` | 登録の成功のあと `notifyAdmin("developer", <user_id>)`(種類の名前は `NOTIFY_KINDS` に合わせる) |
| レビューの通報 | (今は、どのアプリ・画面からも書き込まれていない) | 足さない。`NOTIFY_KINDS` と `reviewReportMail` は残す |

- 送信は応答を返す前に `await` する(PM2 の1つのプロセスで動いていて、応答のあとに処理を続ける仕組みに頼らないため)。送信が遅くても、`notifyAdmin` は例外を出さないので、利用者の操作は失敗しない。

テスト(API テスト。メールは実際には送らない): `RESEND_API_KEY` が空のとき、送信の関数は「送らなかった」ことを返す/ログに出す。テストでは送信の関数を差し替えられるようにし(`src/lib/mail.ts` に、テストのときだけ送った内容を配列に貯める口を作る。`process.env.MAIL_SINK_FILE` があれば、そのファイルに JSON を1行ずつ追記する)、日記の通報・開発者の登録のあとに、正しい種類と ID のメールが1通貯まることを確かめる。`write-local-env.mjs` に `MAIL_SINK_FILE=tests/.mail-sink.jsonl` を足し、`web/.gitignore` にも足す。

コミット: `運営への通知を、書き込んだ Web の処理の中から直接送る`

## ステップ 5-3: DB のトリガーを外すマイグレーション

`supabase/migrations/<日時>_drop_notify_triggers.sql`:
- `diary_reports`・`review_reports`・`developers` の通知のトリガー(`20260925000000_admin.sql` で作ったもの。名前はそのファイルで確かめる)を `drop trigger if exists`。
- 関数 `private.trg_notify_diary_report()`・`private.trg_notify_review_report()`・`private.trg_notify_developer()`・`private.notify_admin(text, text)` を `drop function if exists`。
- 表 `private.notify_settings` を `drop table if exists`(本番にだけ入っている URL と鍵が消える。もう使わない)。
- `pg_net` の拡張は**消さない**(Supabase 自身が使っている可能性があるため。フェーズ6で、新しい DB には最初から入れない)。
- `down.sql`: `20260925000000_admin.sql` の該当部分(関数・トリガー・表)を作り直す SQL。`notify_settings` の中身は戻らないので、`down.sql` の先頭のコメントに「戻したあと、`supabase/local/notify-settings.sql` の内容を持ち主に流してもらう必要がある」と書く。

`supabase/sql-tests/admin/01_net_stub.sql` など、`net.http_post` を前提にしたテストがあれば、トリガーが無くなったことに合わせて直す(`npm run test:db` が合格するように)。

完了の条件: `npm run test:db`・`npm run test:api` が合格。
コミット: `pg_net を使う通知のトリガーを外す`

## ステップ 5-4: 本番に出す(本番の操作)

合格条件:
- [ ] `npm run typecheck`・`npm test`・`npm run test:db`・`npm run build` が合格
- [ ] `npm run test:api` が3回続けて合格
- [ ] GitHub Actions が全部緑

順番: **先に Web をデプロイ**(Web から通知を送るようになる)→ **そのあとトリガーを外す**(逆にすると、その間の通報の通知が届かない。両方あるあいだは通知が2通届くことがあるが、害はない)。
```powershell
Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
git -C C:\dev\appstore fetch origin
git -C C:\dev\appstore rebase origin/main
git -C C:\dev\appstore push --force-with-lease origin supabase-removal/phase5-notify
gh run watch --repo kawamonn91/ttech-store --exit-status
git -C C:\dev\appstore switch main
git -C C:\dev\appstore merge --ff-only supabase-removal/phase5-notify
git -C C:\dev\appstore push origin main
Backup-ProdDb "phase5-step4"
Invoke-WebDeploy
Set-Location C:\dev\appstore\web; npm run test:smoke
Invoke-ProdMigration C:\dev\appstore\supabase\migrations\<日時>_drop_notify_triggers.sql
npm run test:smoke
```
完了の条件: 2回のスモークテストが合格。
持ち主への報告: 「日記の通報を1件試し、運営宛のメールが届くか確かめてください」(メールの受信はエージェントには確かめられない)。

## ステップ 5-5: 記録
「進み具合」を埋め、計画の「状態」を `フェーズ5 まで完了` にする。コミットして push。

---

## 進み具合

| ステップ | 日付 | 結果・メモ |
|---|---|---|
| 5-0 | | マージ前の main: `` |
| 5-1 | | |
| 5-2 | | |
| 5-3 | | |
| 5-4 | | |
| 5-5 | | |
