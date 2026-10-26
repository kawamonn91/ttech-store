# フェーズ1: Web の DB アクセスを Kysely + PostgreSQL 直結にする

## なぜ
Web は今、DB を Supabase の API(PostgREST)越しに読み書きしています(`supabase-js` の `.from()`・`.rpc()` が約110か所)。これを PostgreSQL への直接接続(Kysely)に置き換えます。**ログインはまだ Supabase Auth のまま、権限の判定もまだ RLS のまま**にして、変えるのは「DB への道筋」だけです。

RLS をそのまま使うしくみ: PostgREST は、リクエストごとに「誰のリクエストか」を DB の設定 `request.jwt.claims` に入れ、ロールを `anon`/`authenticated` に切り替えてから SQL を実行しています。RLS のポリシーの中の `auth.uid()` は、その設定を読んでいるだけです。Web も同じことをトランザクションごとに行えば、61個のポリシーを1つも変えずに使えます。

## 前提
- フェーズT が終わり、`npm run test:api` と `npm run test:smoke` が合格している。
- ブランチ: `git -C C:\dev\appstore switch -c supabase-removal/phase1-db`。マージ前の main を「進み具合」に書く。
- [README.md](README.md) の「Web のコードを書くとき」を読み直す(Next.js と Kysely の資料を先に読む)。

## 変えないもの
- ログイン(Supabase Auth)。`supabase.auth.*` の呼び出しは残す(フェーズ4で置き換える)。
- ファイル置き場(`supabase.storage.*`)。フェーズ2・3で置き換える。
- RLS のポリシーと DB の関数。マイグレーションは足さない。
- API の応答の形。**フェーズT のスナップショットと権限の表が、変更なしで合格し続けること。**

---

## ステップ 1-1: 依存と DB のログイン用ロール(ローカル)

```powershell
Set-Location C:\dev\appstore\web
npm install kysely pg
npm install --save-dev kysely-codegen
```
`pg` はフェーズT で開発用の依存に入れたので、`dependencies` に移る(`npm install pg` で移る。`package.json` で確かめる)。

Web が DB にログインするロール `ttech_web` を作る。このロールは**それ自体には権限がなく**(`noinherit`)、トランザクションの中で `anon`・`authenticated`・`service_role` に切り替えてから SQL を実行する。切り替え忘れの SQL は、権限がなくて必ず失敗する。

`web/tests/support/db-roles.sql`(新規。ローカル専用。パスワードはローカル専用の固定値):
```sql
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'ttech_web') then
    create role ttech_web login password 'local-only' noinherit;
  end if;
end $$;
grant anon, authenticated, service_role to ttech_web;
```
- `tests/support/global-setup.ts` の「DB をまっさらにする」の直後に、このファイルを管理者の接続(下の `DATABASE_ADMIN_URL`)で実行する。
- `scripts/write-local-env.mjs` を直す: `DATABASE_URL` を `postgresql://ttech_web:local-only@127.0.0.1:54322/postgres` にし、今までの `DB_URL`(postgres ユーザー)は `DATABASE_ADMIN_URL` として書く。ポート番号は `DB_URL` から取る(54322 と決め打ちしない)。
- `tests/support/env.ts` の `assertLocal` の確認の対象に `DATABASE_ADMIN_URL` を足す。
- `tests/support/fixtures.ts` は、データの用意に `DATABASE_ADMIN_URL` を使う。
- `npm run local-env` で `.env.local` を作り直す。

完了の条件: `npm run test:api` が合格(まだ何も置き換えていないので、通るはず)。
コミット: `Web が DB に直接つなぐためのロールをローカルに作る`

## ステップ 1-2: DB の型を生成する

`web/scripts/db-types.mjs`(新規): ローカルの DB(`.env.local` の `DATABASE_ADMIN_URL`)から `kysely-codegen` で `src/lib/db/schema.ts` を作り、日時・大きい整数の型を、このフェーズの実行時の形(下の 1-3)に合わせて書き換える。
```js
#!/usr/bin/env node
// ローカルの DB から Kysely の型(src/lib/db/schema.ts)を作る。
// 日時は文字列、int8・numeric は数値で返す設定(src/lib/db/index.ts)に合わせて、型を書き換える。
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadEnvLocal } from "./lib/cli-env.mjs";

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
loadEnvLocal(".env.local");
const url = process.env.DATABASE_ADMIN_URL;
if (!url || !/@(127\.0\.0\.1|localhost):/.test(url)) throw new Error("DATABASE_ADMIN_URL がローカルではありません");

const out = path.join(webRoot, "src/lib/db/schema.ts");
execFileSync("npx", ["kysely-codegen", "--url", url, "--out-file", out, "--include-pattern", "public.*"], { cwd: webRoot, stdio: "inherit", shell: true });

const replacements = [
  [/export type Timestamp = ColumnType<Date, Date \| string, Date \| string>;/, "export type Timestamp = ColumnType<string, Date | string, Date | string>;"],
  [/export type Int8 = ColumnType<string, bigint \| number \| string, bigint \| number \| string>;/, "export type Int8 = ColumnType<number, bigint | number | string, bigint | number | string>;"],
  [/export type Numeric = ColumnType<string, number \| string, number \| string>;/, "export type Numeric = ColumnType<number, number | string, number | string>;"],
];
let text = readFileSync(out, "utf8");
for (const [from, to] of replacements) {
  if (from.test(text)) text = text.replace(from, to);
}
writeFileSync(out, "// このファイルは scripts/db-types.mjs が生成する。手で書き換えないこと\n" + text);
console.log(`書きました: ${out}`);
```
- 生成された `schema.ts` に `Timestamp`・`Int8`・`Numeric` の型があるのに置き換えが起きなかった場合は、`kysely-codegen` の版で書き方が違う。実際の行を見て、置き換えの左側を直す。
- `package.json` の `scripts` に `"db:types": "node scripts/db-types.mjs"` を足す。

完了の条件: `npm run db:types` で `src/lib/db/schema.ts` ができ、`interface DB` に `apps`・`app_releases`・`profiles`・`diary_entries`・`latest_releases` などがある。`npm run typecheck` が合格。
コミット: `ローカルの DB から Kysely の型を生成するスクリプトと、生成した型を足す`

## ステップ 1-3: DB の入口 `src/lib/db/index.ts`

`web/src/lib/env.ts` に足す:
```ts
  /** Web が PostgreSQL に直接つなぐ接続文字列(ロール ttech_web) */
  databaseUrl: () => required("DATABASE_URL"),
```
`web/.env.example` の Supabase の欄の下に足す:
```
# ---- PostgreSQL(Web が直接つなぐ。ロールは ttech_web)
DATABASE_URL=
```

`web/src/lib/db/index.ts`(新規):
```ts
import { Kysely, PostgresDialect, sql, type Transaction } from "kysely";
import pg from "pg";
import { env } from "../env";
import type { DB } from "./schema";

/**
 * 日時は、これまで(PostgREST)と同じ形の文字列で返す。API の応答の形を変えないため。
 * 例: "2026-10-04 15:10:00.123456+00" → "2026-10-04T15:10:00.123456+00:00"
 * 接続のタイムゾーンは UTC に固定している(下の options)。
 */
export function toPostgrestTimestamp(value: string): string {
  return value.replace(" ", "T").replace(/([+-]\d{2})$/, "$1:00");
}
pg.types.setTypeParser(pg.types.builtins.TIMESTAMPTZ, toPostgrestTimestamp);
pg.types.setTypeParser(pg.types.builtins.TIMESTAMP, (v) => v.replace(" ", "T"));
pg.types.setTypeParser(pg.types.builtins.DATE, (v) => v);
pg.types.setTypeParser(pg.types.builtins.INT8, (v) => Number(v));
pg.types.setTypeParser(pg.types.builtins.NUMERIC, (v) => Number(v));

export type Db = Transaction<DB>;

/** RLS の判定に渡す「誰のリクエストか」。aal は2段階認証済みか(管理者の判定 is_admin() が使う) */
export interface DbUser {
  id: string;
  aal: "aal1" | "aal2";
}

// 開発中のホットリロードで接続が増え続けないよう、1つだけ作って使い回す
const globalForDb = globalThis as unknown as { ttechDb?: Kysely<DB> };

function db(): Kysely<DB> {
  globalForDb.ttechDb ??= new Kysely<DB>({
    dialect: new PostgresDialect({
      pool: new pg.Pool({ connectionString: env.databaseUrl(), max: 10, options: "-c TimeZone=UTC" }),
    }),
  });
  return globalForDb.ttechDb;
}

async function begin(trx: Db, role: "anon" | "authenticated" | "service_role", claims: Record<string, unknown>) {
  // PostgREST と同じく、トランザクションの中だけで有効な設定にする(true = local)
  await sql`select set_config('request.jwt.claims', ${JSON.stringify(claims)}, true)`.execute(trx);
  if (role === "anon") await sql`set local role anon`.execute(trx);
  else if (role === "authenticated") await sql`set local role authenticated`.execute(trx);
  else await sql`set local role service_role`.execute(trx);
}

/** 未ログインの人と同じ権限で実行する(RLS が効く。公開データの読み取り用) */
export function asAnon<T>(fn: (trx: Db) => Promise<T>): Promise<T> {
  return db().transaction().execute(async (trx) => {
    await begin(trx, "anon", { role: "anon" });
    return fn(trx);
  });
}

/** ログイン中のユーザーとして実行する(RLS が効く) */
export function asUser<T>(user: DbUser, fn: (trx: Db) => Promise<T>): Promise<T> {
  return db().transaction().execute(async (trx) => {
    await begin(trx, "authenticated", { sub: user.id, role: "authenticated", aal: user.aal });
    return fn(trx);
  });
}

/**
 * サーバーの権限で実行する(RLS を通らない)。これまでの serviceClient() と同じ扱い:
 * 管理者の確認・署名の検証などの認可チェックを通った後にだけ使うこと。
 */
export function asService<T>(fn: (trx: Db) => Promise<T>): Promise<T> {
  return db().transaction().execute(async (trx) => {
    await begin(trx, "service_role", { role: "service_role" });
    return fn(trx);
  });
}

export { sql };
```
`db()` は外に出さない(切り替えなしの SQL を書けないようにするため)。

### テスト `tests/api/db-access.test.ts`(新規)
API テストの設定で動かす(ローカルの DB を使う)。テストの中で `process.env.DATABASE_URL` を `readLocalEnv()` の値にしてから `src/lib/db` を import する。
1. **行の形が PostgREST と同じこと**: 次の各テーブル・ビューの全行を、(a) PostgREST(`{NEXT_PUBLIC_SUPABASE_URL}/rest/v1/<table>?select=*&order=<主キー>`、service role の鍵)と、(b) `asService(trx => trx.selectFrom(<table>).selectAll().orderBy(<主キー>).execute())` で読み、`expect(b).toEqual(a)`。対象: `profiles`・`developers`・`categories`・`apps`・`app_releases`・`downloads`・`reviews`・`diary_entries`・`diary_friendships`・`diary_blocks`・`diary_reports`・`admin_audit_log`・`latest_releases`。違いが出たら、1-3 の型の変換を直す(テスト側の期待値を変えない)。
2. **権限が効くこと**: `asAnon` では `fx-draft` が読めない。`asUser(dev1)` では読める。`asService` では全部読める。
3. **設定がトランザクションの外に漏れないこと**: `asUser(user1)` の直後に、同じプール(接続数を1にしたプールで確かめるとよい)で `asAnon` を実行し、`select current_setting('request.jwt.claims', true)` が `{"role":"anon"}` であること、`select current_user` が `anon` であること。
4. **切り替えなしでは何も読めないこと**: テストの中だけで `ttech_web` の接続を作り、`select * from public.apps` が権限エラーになること。

完了の条件: このテストを含めて `npm run test:api` が合格。
コミット: `PostgreSQL に直接つなぐ入口(asAnon・asUser・asService)を足す`

## ステップ 1-4: 「今のユーザー」の取り出し口 `src/lib/current-user.ts`

ログインの確認を1か所にまとめる。**フェーズ4では、このファイルの中身だけを Better Auth に置き換える。**
```ts
import { publicClient, sessionClient } from "./supabase";
import type { DbUser } from "./db";

export interface CurrentUser extends DbUser {
  email: string | null;
}

/** JWT の aal クレームを読む(署名の確認は getUser が Auth サーバーで済ませている) */
function aalOf(accessToken: string | undefined): "aal1" | "aal2" {
  try {
    const payload = JSON.parse(Buffer.from((accessToken ?? "").split(".")[1] ?? "", "base64url").toString("utf8"));
    return payload?.aal === "aal2" ? "aal2" : "aal1";
  } catch {
    return "aal1";
  }
}

/** ブラウザのログイン(Cookie)のユーザー。いなければ null */
export async function currentUserFromCookies(): Promise<CurrentUser | null> {
  const supabase = await sessionClient();
  const { data } = await supabase.auth.getUser();
  if (!data.user) return null;
  const { data: session } = await supabase.auth.getSession();
  return { id: data.user.id, email: data.user.email ?? null, aal: aalOf(session.session?.access_token) };
}

/** Authorization: Bearer のユーザー(ストアアプリなど)。いなければ null */
export async function currentUserFromBearer(request: Request): Promise<CurrentUser | null> {
  const token = request.headers.get("authorization")?.match(/^Bearer (.+)$/i)?.[1];
  if (!token) return null;
  const { data } = await publicClient().auth.getUser(token);
  if (!data.user) return null;
  return { id: data.user.id, email: data.user.email ?? null, aal: aalOf(token) };
}

/** Cookie を先に、なければ Bearer を見る */
export async function currentUser(request?: Request): Promise<CurrentUser | null> {
  return (await currentUserFromCookies()) ?? (request ? await currentUserFromBearer(request) : null);
}
```
- `src/lib/admin.ts` の `decodeAal` と同じ考え方。`admin.ts` 側も、このファイルの関数を使う形に寄せる(1-6 で)。
- 単体テスト `src/lib/current-user.test.ts`: `aalOf` を export せずに確かめるため、`currentUserFromBearer` に、`publicClient` をモックした上で `aal: "aal2"` を含む JWT 形の文字列を渡し、`aal` が `aal2` になること、壊れたトークンで `aal1` になることを確かめる(`vi.mock("./supabase")`)。

完了の条件: `npm run typecheck`・`npm test` が合格。
コミット: `ログイン中のユーザーと2段階認証の状態を取り出す口を1つにまとめる`

## ステップ 1-5: 書き換えの決まり

`supabase-js` の書き方を、次の表のとおりに Kysely に置き換える。**どのクライアントを使っていたかで、使う入口が決まる:**

| これまで | これから |
|---|---|
| `serviceClient()` | `asService(trx => ...)` |
| `sessionClient()` で `.from()`/`.rpc()` | `asUser(user, trx => ...)`。`user` は `currentUserFromCookies()` で取る(取れなければ、これまで未ログインのときに返していた応答を返す) |
| `publicClient()` で `.from()`/`.rpc()` | `asAnon(trx => ...)` |
| `publicClient().auth.getUser(token)` のあとの `.from()` | `asUser(await currentUserFromBearer(request), ...)` |

| supabase-js | Kysely |
|---|---|
| `.from("t").select("a, b")` | `trx.selectFrom("t").select(["a", "b"])` |
| `.select("*")` | `.selectAll()` |
| `.eq("c", v)` / `.neq` / `.gt` / `.gte` / `.lt` / `.lte` | `.where("c", "=", v)` / `"<>"` / `">"` / `">="` / `"<"` / `"<="` |
| `.in("c", list)` | `.where("c", "in", list)`。**`list` が空なら SQL が壊れるので、先に空かどうかを確かめて、空なら DB を呼ばずに空の結果にする** |
| `.is("c", null)` / `.not("c", "is", null)` | `.where("c", "is", null)` / `.where("c", "is not", null)` |
| `.ilike("c", "%x%")` | `.where("c", "ilike", "%x%")` |
| `.or("a.eq.1,b.eq.2")` | `.where((eb) => eb.or([eb("a", "=", 1), eb("b", "=", 2)]))` |
| `.order("c", { ascending: false })` | `.orderBy("c", "desc")`(`nullsFirst` の指定があれば `sql` で `nulls first`) |
| `.limit(n)` / `.range(from, to)` | `.limit(n)` / `.offset(from).limit(to - from + 1)` |
| `.maybeSingle()` | `.executeTakeFirst()` の結果を `?? null` |
| `.single()` | `.executeTakeFirst()` で `undefined` なら、これまで `error` だったときと同じ処理をする(**例外にしない**) |
| `{ count: "exact", head: true }` | `.select((eb) => eb.fn.countAll<number>().as("count"))` → `.executeTakeFirstOrThrow()` の `count` |
| `.insert(row)` | `trx.insertInto("t").values(row).execute()` |
| `.insert(row).select().single()` | `.values(row).returningAll().executeTakeFirstOrThrow()` |
| `.update(patch).eq(...)` | `trx.updateTable("t").set(patch).where(...).execute()` |
| `.upsert(row, { onConflict: "c" })` | `.insertInto("t").values(row).onConflict((oc) => oc.column("c").doUpdateSet(<row から c を除いたもの>))` |
| `.delete().eq(...)` | `trx.deleteFrom("t").where(...).execute()` |
| `.rpc("f", { p_a: x, p_b: y })` | `` sql`select public.f(p_a => ${x}, p_b => ${y}) as r`.execute(trx) `` の `rows[0].r`。表を返す関数は `` sql`select * from public.f(...)` `` |
| 埋め込み `select("*, apps(name)")`(1対1・多対1) | `import { jsonObjectFrom } from "kysely/helpers/postgres"` で `jsonObjectFrom(eb.selectFrom("apps").select("name").whereRef("apps.id", "=", "t.app_id")).as("apps")` |
| 埋め込み(1対多) | `jsonArrayFrom(...)` |

エラーの扱い:
- supabase-js は `{ data, error }` を返したが、Kysely は**例外を投げる**。これまで `if (error)` で特定の応答(404・409 など)を返していた所は、`try { ... } catch (e) { ... }` で同じ応答を返す。
- 一意制約の違反は `src/lib/db.ts` の `isUniqueViolation(e)` で判定できる(pg のエラーにも `code: "23505"` がある)。
- RLS で拒否された書き込みは、pg のエラー `code: "42501"` になる。これまでの扱い(多くは 403 か 500)を、スナップショットと権限の表が通るように合わせる。
- 埋め込みの取り出しに使っていた `first()`(`src/lib/db.ts`)は、`jsonObjectFrom` に置き換えたら不要になる。全部置き換え終わったら削除する。
- `displayNames(supabase, ids)` は `displayNames(trx, ids)` に変える。

## ステップ 1-6: ファイルを順に書き換える

**1ファイル書き換えるたびに** `npm run typecheck` と `npm test` を実行し、**3〜5ファイルごとに** `npm run test:api` を実行する。失敗したら、そのファイルの書き換えを見直す(テストの期待値は変えない)。各ファイルを書き換えたら、ファイル単位でコミットする(メッセージ例: `catalog.ts の DB アクセスを Kysely にする`)。

書き換える順番(上から。件数は `.from`/`.rpc`/`.auth`/`.storage` の出現数の目安):

| # | ファイル | 備考 |
|---|---|---|
| 1 | `src/lib/db.ts` | `displayNames` の引数を `Db` に。`first` は最後に消す |
| 2 | `src/lib/catalog.ts` | 公開カタログ。`asAnon` |
| 3 | `src/lib/download.ts` | `record_download` は RPC |
| 4 | `src/lib/dto.ts` | DB アクセスがあれば |
| 5 | `src/lib/scan.ts` | |
| 6 | `src/lib/release-review.ts` | |
| 7 | `src/lib/developer.ts` | `requireDeveloper`・`loadOwnApp` は `currentUserFromCookies()` を使う |
| 8 | `src/lib/notify.ts` | |
| 9 | `src/lib/moderation.ts` | `supabase.auth.admin.*`(BAN)と `storage` は**残す** |
| 10 | `src/lib/admin-queries.ts` | 最も多い。`auth.admin.*` と `storage`(日記の写真の署名付き URL)は残す |
| 11 | `src/lib/supabase.ts` | `getAdminUser` の `profiles` の読み取りを `asService` に。`adminStepUpDone` はそのまま |
| 12 | `src/lib/admin.ts` | `adminFromBearerToken`・`ensureAdminDeveloper` の `profiles`・`developers` を `asService` に。`decodeAal` は `current-user.ts` に寄せてよい |
| 13 | `src/app/api/**/route.ts` | 上の表の件数が多い順に。1ルートずつ |
| 14 | `src/app/**/page.tsx`・`layout.tsx`・`actions.ts` | サーバー側の `.from()` |
| 15 | `src/app/account/ProfileForm.tsx` | ブラウザから `profiles` を直接更新している。→ 下の「表示名の変更 API」に置き換える |
| 16 | `src/lib/db.ts` の `first` の削除 | 使われていないことを確かめてから |

### 表示名の変更 API(新規): `src/app/api/account/profile/route.ts`
- `PATCH`。本文 `{ display_name: string }`(zod で、前後の空白を除いて1〜50文字。これまでの `ProfileForm` の入力の制限があればそれに合わせる)。
- `currentUserFromCookies()` が null なら 401。
- `asUser(user, trx => trx.updateTable("profiles").set({ display_name }).where("id", "=", user.id).execute())`(RLS の `profiles_update_self` がそのまま効く。BAN 中は更新されない)。更新された行が0なら 403。
- `ProfileForm.tsx` は `fetch("/api/account/profile", { method: "PATCH", ... })` を呼ぶ形にし、`browserClient` の import を消す。
- **フェーズT の権限の表(`tests/api/authz-matrix.ts`)に、この API の行を足す**(足さないと `routes-inventory.test.ts` が失敗する)。期待値: `anon` 401、`user` 200、`banned` 403。

### 書き換えの終わりの確認
```powershell
Set-Location C:\dev\appstore\web
Select-String -Path (Get-ChildItem -Recurse src -Include *.ts,*.tsx | Where-Object { $_.FullName -notmatch '\\testing\\|\.test\.' }) -Pattern '\.from\("|\.rpc\(' | Where-Object { $_.Line -notmatch '\.storage\.from\(' }
```
完了の条件: 何も表示されない(残っているのは `.storage.from(` だけ)。

## ステップ 1-7: 単体テストの整理

`src/lib/testing/fake-supabase.ts` を使う単体テストのうち、DB の呼び出しの中身(どのテーブルに何を書いたか)を確かめていたものは、書き換えで失敗するようになる。1つずつ次のどちらかにする:
- **純粋な判断の部分**(入力の検証・状態の決め方など)を確かめているテスト: 判断の関数を DB から切り離して呼ぶ形に直し、残す。
- **DB への書き込みを確かめているテスト**: 同じことを API テスト(`tests/api/`)で確かめているかを探す。確かめていれば消す。確かめていなければ、API テストに足してから消す。どのテストをどこに移したかを「進み具合」のメモに書く。

`fake-supabase.ts` は、`auth`・`storage` のテストで使われている間は残す。

完了の条件: `npm test`・`npm run test:api` が合格。
コミット: `DB を直接使うようにしたため、単体テストを API テストに移す`

## ステップ 1-8: 手元のスクリプトの DB 部分

`web/scripts/` の `publish-app.mjs`・`upload-tool.mjs`・`set-app-icon.mjs`・`add-screenshot.mjs`(とその他、`/rest/v1` を呼んでいるもの。`Select-String -Path scripts\*.mjs -Pattern 'rest/v1'` で探す)は、PostgREST を service role の鍵で呼んでいる。フェーズ6で PostgREST が無くなるので、ここで直接接続に変える。

1. `web/scripts/lib/db.mjs`(新規): `pg` の `Pool` を `DATABASE_URL` で作り、1-3 と同じ型の変換を設定し、`withService(fn)`(`begin` → `set local role service_role` → `fn(client)` → `commit`。失敗したら `rollback`)を export する。
2. 各スクリプトの `SupabaseAdmin`(`select`/`insert`/`update`)を、同じ意味の SQL に置き換える(パラメータは必ず `$1` などで渡す。文字列をつなげて SQL を作らない)。Storage を使っている部分は**残す**(フェーズ2)。
3. 本番に対して使うときは、**DB へのトンネルが必要**になる。`publish-app.mjs` の冒頭のコメントの使い方に、次を足す:
   ```
   // 本番に公開するとき: Import-Module web\scripts\ops\ops.psm1; Start-DbTunnel を先に実行し、
   //   $env:TTECH_ENV_FILE = ".env.prod.local" で実行する(DATABASE_URL はトンネルの 127.0.0.1:54329 を向く)
   ```
4. テスト `tests/api/scripts-db.test.ts`: `scripts/lib/db.mjs` と、各スクリプトから切り出した DB の関数(例: `findAppByPackage`・`insertRelease`)を、ローカルの DB で呼んで結果を確かめる。

完了の条件: `npm run test:api` が合格。`Select-String -Path scripts\*.mjs,scripts\lib\*.mjs -Pattern 'rest/v1'` が何も表示しない。
コミット: `手元のスクリプトの DB アクセスを PostgreSQL の直接接続にする`

## ステップ 1-9: 本番に出す前の合格条件

- [ ] `npm run typecheck`・`npm test`・`npm run test:db`・`npm run build` が合格
- [ ] `npm run test:api` が**3回続けて**合格(スナップショット・権限の表・`db-access.test.ts` を含む)
- [ ] `git diff origin/main -- tests/api/__snapshots__` が空(応答の形を変えていない)
- [ ] `git diff origin/main -- tests/api/authz-matrix.ts` が、表示名の変更 API の行の追加だけ
- [ ] ブランチを push して GitHub Actions が全部緑

## ステップ 1-10: 本番の DB を Web から直接つなげるようにする(本番の操作)

### 10-1. バックアップ
```powershell
Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
Backup-ProdDb "phase1-step10"
```

### 10-2. DB のポートをサーバーの中だけに開く
本番の Supabase の DB コンテナは、ポートをサーバーに公開していない。Web(サーバー上の Node.js)と、手元からのトンネルがつなげるように、**サーバー自身(127.0.0.1)からだけ**つなげるポート 54329 を開く。

`web/scripts/ops/remote/expose-db-port.sh`(新規):
```bash
#!/usr/bin/env bash
# usage: expose-db-port.sh <supabase-docker-dir>
# Publishes the db container's 5432 on 127.0.0.1:54329 (host only) with a compose override.
set -euo pipefail
cd "$1"
if [ -e docker-compose.override.yml ]; then
  echo "docker-compose.override.yml already exists. Stop and ask the owner." >&2
  exit 3
fi
docker compose config --services | grep -qx db || { echo "service 'db' not found" >&2; exit 4; }
cat > docker-compose.override.yml <<'YAML'
services:
  db:
    ports:
      - "127.0.0.1:54329:5432"
YAML
docker compose up -d db
for i in $(seq 1 60); do
  if docker compose exec -T db pg_isready -U postgres -h localhost >/dev/null 2>&1; then break; fi
  sleep 2
done
ss -ltn | grep -q '127.0.0.1:54329' || { echo "port 54329 is not listening" >&2; exit 5; }
echo "ok"
```
```powershell
Invoke-RemoteScript "expose-db-port.sh" @((Get-OpsSetting "SUPABASE_DOCKER_DIR"))
npm run test:smoke   # DB のコンテナを作り直したので、今の本番が動いていることを確かめる
```
完了の条件: `ok` が表示され、スモークテストが合格。`exit 3`(上書き設定がすでにある)なら止まって報告する。

### 10-3. ロール `ttech_web` を本番に作る
パスワードは手元で作り、一時ファイル経由で流す(画面に出さない)。
```powershell
$bytes = New-Object byte[] 24; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$pw = -join ($bytes | ForEach-Object { $_.ToString("x2") })
$tmp = Join-Path $env:TEMP "create-ttech-web.sql"
@"
do `$`$
begin
  if not exists (select 1 from pg_roles where rolname = 'ttech_web') then
    create role ttech_web login noinherit;
  end if;
end `$`$;
alter role ttech_web password '$pw';
grant anon, authenticated, service_role to ttech_web;
"@ | Set-Content -Path $tmp -Encoding ASCII
Invoke-ProdSqlFile $tmp
Remove-Item $tmp
$url = "postgres://ttech_web:$pw@127.0.0.1:54329/postgres"
Set-ServerEnv "DATABASE_URL" $url
Add-Content -Path C:\dev\appstore\web\.env.prod.local -Value "DATABASE_URL=$url" -Encoding UTF8
Remove-Variable pw, url
```
(サーバーの Web も手元のトンネルも、どちらも `127.0.0.1:54329` でつなぐので、同じ接続文字列でよい)

### 10-4. 手元からトンネル越しにつながることを確かめる
```powershell
Start-DbTunnel
Set-Location C:\dev\appstore\web
$env:TTECH_ENV_FILE = ".env.prod.local"
node -e "import('./scripts/lib/cli-env.mjs').then(async ({loadEnvLocal}) => { loadEnvLocal(); const pg = (await import('pg')).default; const c = new pg.Client({ connectionString: process.env.DATABASE_URL }); await c.connect(); const r = await c.query('select current_user, (select count(*) from pg_auth_members m join pg_roles r on r.oid = m.roleid where m.member = (select oid from pg_roles where rolname = current_user)) as roles'); console.log(r.rows[0]); await c.end(); })"
Remove-Item Env:\TTECH_ENV_FILE
```
完了の条件: `{ current_user: 'ttech_web', roles: 3 }`(数は3以上)。接続できなければ止まって報告する(DB の `pg_hba.conf` が許可していない可能性がある)。

## ステップ 1-11: マージしてデプロイ(本番の操作)

```powershell
git -C C:\dev\appstore fetch origin
git -C C:\dev\appstore rebase origin/main
git -C C:\dev\appstore push --force-with-lease origin supabase-removal/phase1-db
# GitHub Actions が緑になるのを待つ
gh run watch --repo kawamonn91/ttech-store --exit-status
git -C C:\dev\appstore switch main
git -C C:\dev\appstore merge --ff-only supabase-removal/phase1-db
git -C C:\dev\appstore push origin main
Backup-ProdDb "phase1-step11"
Invoke-WebDeploy
Set-Location C:\dev\appstore\web
npm run test:smoke
```
完了の条件: スモークテストが合格。失敗したら [ops.md](ops.md) の「6-1. Web のコードを戻す」で戻し、スモークテストが通ることを確かめてから止まって報告する(10-2・10-3 の変更は戻さなくてよい。使われないだけで害はない)。

## ステップ 1-12: 記録
「進み具合」を埋め、[../supabase-removal-plan.md](../supabase-removal-plan.md) の「状態」を `フェーズ1 まで完了` にする。コミットして push。

---

## 進み具合

| ステップ | 日付 | 結果・メモ |
|---|---|---|
| 1-0 | | マージ前の main: `` |
| 1-1 | | |
| 1-2 | | |
| 1-3 | | |
| 1-4 | | |
| 1-6 | | 書き換えたファイル: |
| 1-7 | | 移したテスト: |
| 1-8 | | |
| 1-9 | | |
| 1-10 | | |
| 1-11 | | |
| 1-12 | | |
