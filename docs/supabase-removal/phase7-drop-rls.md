# フェーズ7: 権限の判定を Web のコードに移し、RLS をやめる

## なぜ
フェーズ6で Supabase は止まりましたが、「誰が何を読み書きできるか」は、まだ DB の RLS(ポリシー約61個)と、それが使う `auth.uid()` の代わりの関数(`db/bootstrap.sql`)に頼っています。最終的な方針(D-2)は「RLS もやめる」なので、判定を Web のコードの中に書き、DB は素の表と制約だけにします。

**守りが2重から1重になる作業**です。必ず次の順で進めます。
1. RLS を残したまま、コードの側にも同じ判定を書く(2重の状態)。
2. **テストの DB だけ RLS を切って**、全テストが通ることを確かめる(= コードの判定だけで足りている証拠)。
3. 本番の RLS を切る(ポリシーは消さずに残す。すぐ戻せる)。
4. しばらく問題が無ければ、ポリシー・切り替えのしくみ・使わなくなったロールと関数を消す。

## 前提
- フェーズ6 が終わっている(6-8 の2週間後の片付けは、このフェーズと並行でよい)。
- ブランチ: `supabase-removal/phase7-drop-rls`。マージ前の main を「進み具合」に書く。
- `docs/supabase-removal/rls-inventory.md` を作り直す(フェーズT の T-11 のスクリプトを、今のローカルの DB で実行する)。

---

## ステップ 7-1: 判定の一覧を作る

`rls-inventory.md` の表の各行(ポリシー)について、次の列を足した表 `docs/supabase-removal/authz-map.md` を作る:

| 表 | ポリシー | 操作 | 条件(USING / WITH CHECK) | その操作をしている Web の場所(ファイルと関数) | コードに書く判定 |
|---|---|---|---|---|---|

- 「Web の場所」は、`asAnon`・`asUser` の中でその表を読み書きしている所を `Select-String` で探して全部書く。`asService` の中の操作は、もともと RLS を通っていないので対象外(ただし一覧には「service」と書く)。
- 「コードに書く判定」は、ポリシーの条件を、そのまま SQL の `where` かコードの `if` に直したもの。例:
  - `diary_entries` の SELECT → `where user_id = $me or (not diary_blocked_between($me, user_id) and ($me = any(mentioned_user_ids) or visibility = 'public' or (visibility = 'friends' and diary_are_friends($me, user_id))))`
  - `apps` の SELECT(公開カタログ)→ `where status = 'published' and not admin_only`(条件は rls-inventory の実物を写す)
- `security definer` の関数のうち、中で `auth.uid()` を使っているもの(`is_admin`・`is_banned`・`is_approved_developer`・`diary_block_user`・`diary_report_entry`・`diary_delete_my_account`・`record_download` など。rls-inventory の関数の一覧で確かめる)も、行を作る。

完了の条件: 全ポリシー・全関数に「コードに書く判定」がある。
コミット: `RLS の各ポリシーを、コードのどこでどう判定するかの対応表を作る`

## ステップ 7-2: コードに判定を書く(RLS は残したまま)

1. `src/lib/authz.ts`(新規)に、対応表の判定を関数にまとめる。例:
   ```ts
   /** 日記の投稿を、この人(viewerId)が見られるか、の SQL の条件。diary_entries の SELECT のポリシーと同じ */
   export function diaryEntryVisibleTo(eb: ExpressionBuilder<DB, "diary_entries">, viewerId: string): Expression<SqlBool>;
   /** 管理者として操作できるか。is_admin() と同じ(BAN でない・TOTP 確認済みなら aal2) */
   export async function isAdmin(trx: Db, user: DbUser): Promise<boolean>;
   ```
2. 対応表の「Web の場所」すべてで、読み取りにはその条件を `where` に足し、書き込みの前にはその判定を行う(通らなければ、RLS に拒否されたときと同じ応答: 403 か 404。今の権限の表の期待値に合わせる)。
3. `auth.uid()` を使う関数は、**引数で人を受け取る新しい版**をマイグレーションで足す(例: `diary_block_user_as(p_actor uuid, p_target uuid)`)。中身は元の関数の `auth.uid()` を `p_actor` に置き換えたもの。Web はそちらを呼ぶ。元の関数は 7-6 で消す。
   - マイグレーション: `db/migrations/<日時>_explicit_actor_functions.sql`、`db/rollback/` に down。
4. この段階では、まだ `asUser`(RLS が効く)のまま実行する。判定は2重になる。

完了の条件: `npm run test:api` が3回続けて合格(期待値は1つも変えない)。
コミット: 対応表の表ごとに1つ。

## ステップ 7-3: RLS を切ったテストで確かめる

1. `tests/support/global-setup.ts` に、環境変数 `DISABLE_RLS=1` のとき、データを入れた後に次を行う処理を足す:
   ```sql
   do $$ declare r record; begin
     for r in select schemaname, tablename from pg_tables where schemaname in ('public', 'private') and rowsecurity loop
       execute format('alter table %I.%I disable row level security', r.schemaname, r.tablename);
     end loop;
   end $$;
   ```
2. `web/package.json` に `"test:api:no-rls": "cross-env DISABLE_RLS=1 vitest run --config vitest.api.config.ts"` を足す(`cross-env` が無ければ、`vitest.api.config.ts` を読み込む小さな `.mjs` で `process.env.DISABLE_RLS = "1"` を設定してから vitest を起動する。PowerShell の `$env:` でもよいが、CI でも同じコマンドで動くようにする)。
3. `npm run test:api:no-rls` を実行する。

完了の条件: **全テストが、期待値を1つも変えずに合格**(3回続けて)。失敗したテストは、コードの判定が足りない所。7-2 に戻って直す(テストの期待値は変えない)。
CI の `web-api` ジョブに、`npm run test:api:no-rls` の実行を足す。
コミット: `RLS を切った DB でも全テストが通ることを確かめるテストを足す`

## ステップ 7-4: 本番に出す(本番の操作)— コードの判定を足した版

合格条件:
- [ ] `npm run typecheck`・`npm test`・`npm run test:db`・`npm run build` が合格
- [ ] `npm run test:api`・`npm run test:api:no-rls` がどちらも3回続けて合格
- [ ] GitHub Actions が全部緑

```powershell
Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
Backup-ProdDb "phase7-step4"
Invoke-ProdMigration (Get-ChildItem C:\dev\appstore\db\migrations\*_explicit_actor_functions.sql).FullName
git -C C:\dev\appstore fetch origin
git -C C:\dev\appstore rebase origin/main
git -C C:\dev\appstore push --force-with-lease origin supabase-removal/phase7-drop-rls
gh run watch --repo kawamonn91/ttech-store --exit-status
git -C C:\dev\appstore switch main
git -C C:\dev\appstore merge --ff-only supabase-removal/phase7-drop-rls
git -C C:\dev\appstore push origin main
Invoke-WebDeploy
Set-Location C:\dev\appstore\web; npm run test:smoke
```

## ステップ 7-5: 本番の RLS を切る(本番の操作)

`db/migrations/<日時>_disable_rls.sql`: 7-3 と同じ `do` ブロックで、`public`・`private` の全部の表の RLS を切る。**ポリシーは消さない**(すぐ戻せるように)。あわせて、Web のロールがロールを切り替えずに読み書きできるように、直接の権限を与える:
```sql
grant usage on schema public, private to ttech_web;
grant select, insert, update, delete on all tables in schema public to ttech_web;
grant usage, select on all sequences in schema public to ttech_web;
grant execute on all functions in schema public to ttech_web;
```
(`private` の表を Web が使っていれば、同じく足す)
`db/rollback/<同じ名前>.down.sql`: RLS を有効に戻し(切る前に RLS が有効だった表の一覧を、マイグレーションの中で `ops.rls_tables_before_disable` という表に保存しておき、それを使う)、上の `grant` を `revoke` する。

ローカルで確かめる: `npm run test:db`(pgTAP の RLS のテストは、ここで失敗するようになる。7-6 で消すので、このマイグレーションの適用後に失敗するテストの名前を「進み具合」に書き、`db/tests/` から該当のテストを外す)、`npm run test:api`。

本番:
```powershell
Backup-ProdDb "phase7-step5"
Invoke-ProdMigration (Get-ChildItem C:\dev\appstore\db\migrations\*_disable_rls.sql).FullName
npm run test:smoke
```
完了の条件: スモークテストが合格。失敗したら `down.sql` で戻す([ops.md](ops.md) の 6-2)。

**この状態で1週間様子を見る。** 次のステップは、1週間後にこのファイルを開いて続ける(問題があれば `down.sql` で RLS を戻せる)。

## ステップ 7-6: 片付け(1週間後)

1. Web のコード: `asAnon`・`asUser`・`asService` を、ロールの切り替えも `request.jwt.claims` の設定もしない `withDb(fn)` 1つにまとめる(`src/lib/db/index.ts`)。呼び出し元はすべて `withDb` に置き換える(判定は 7-2 でコードに入っている)。`DbUser` の `aal` は、判定(`isAdmin`)で使うので残す。
2. マイグレーション `db/migrations/<日時>_drop_rls.sql`:
   - 全ポリシーを消す(`drop policy if exists ... on ...`。rls-inventory の一覧から作る)。
   - `auth.uid()` を使う元の関数(7-2 で新しい版を作ったもの)と、`is_admin()` など、Web がもう呼ばない関数を消す(呼んでいないことを `Select-String` で確かめてから)。
   - `auth` スキーマ(`uid`・`role`・`jwt`)を消す。
   - ロール `anon`・`authenticated`・`service_role` から権限を外し(`revoke all on all tables in schema public from ...`)、ロールを消す。`ttech_web` から、これらのロールのメンバーシップを外す。
   - `ttech_auth` 用のポリシー(フェーズ4)も、RLS が無いので消す(権限の `grant` は残す)。
   - `ops.rls_tables_before_disable` を消す。
   - `down.sql` は「この時点のバックアップから戻す」としか書けない(ポリシーを全部作り直すのは現実的でない)。先頭のコメントにそう書き、[ops.md](ops.md) の 6-4 の手順を示す。
3. `db/bootstrap.sql` から、`anon`・`authenticated`・`service_role` と `auth` スキーマの部分を消す(新しく作る環境では最初から作らない)。`ttech_web` は `noinherit` をやめる(直接の権限で動くため)。
4. `db/tests/` の、RLS を前提にしたテストを消す(7-5 で外したもの)。代わりの守りは API テスト(権限の表・日記のシナリオ)。
5. `tests/support/global-setup.ts` の `DISABLE_RLS` の処理と、`test:api:no-rls` を消す(RLS がもう無いので、普通の `test:api` が同じことを確かめている)。
6. `docs/setup.md`・`docs/db-migration.md` などに、「権限は Web のコード(`src/lib/authz.ts`)で判定する。DB に RLS は無い」と書く。`rls-inventory.md`・`authz-map.md` は記録として残す。

合格条件 → 本番:
- [ ] `npm run typecheck`・`npm test`・`npm run test:db`・`npm run test:api`(3回)・`npm run build` が合格、GitHub Actions が緑
```powershell
Backup-ProdDb "phase7-step6"
# 先に Web(withDb は、RLS が無く直接の権限がある今の DB でそのまま動く)
git -C C:\dev\appstore switch main; git -C C:\dev\appstore merge --ff-only supabase-removal/phase7-drop-rls; git -C C:\dev\appstore push origin main
Invoke-WebDeploy
npm run test:smoke
# そのあと DB の片付け
Invoke-ProdMigration (Get-ChildItem C:\dev\appstore\db\migrations\*_drop_rls.sql).FullName
npm run test:smoke
```

## ステップ 7-7: 終わり

- [../supabase-removal-plan.md](../supabase-removal-plan.md) の「状態」を **完了** にし、日付を書く。
- [../refactoring-plan.md](../refactoring-plan.md) のうち、この計画で済んだ項目(例: 0-7 の写真の残り、1-1 の管理者の画像のアップロード、1-4 のスクリプトの共通化の一部)があれば、そちらの「状態」も更新する。
- 持ち主への報告: 計画の完了と、残っている持ち主の作業(クラウドの Supabase の削除・2週間後の片付けの結果)。

---

## 進み具合

| ステップ | 日付 | 結果・メモ |
|---|---|---|
| 7-0 | | マージ前の main: `` |
| 7-1 | | |
| 7-2 | | |
| 7-3 | | |
| 7-4 | | |
| 7-5 | | 外した pgTAP のテスト: |
| 7-6 | | |
| 7-7 | | |
