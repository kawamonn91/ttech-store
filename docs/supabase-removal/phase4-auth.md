# フェーズ4: 認証を Better Auth に移し、3つのアプリを出し直す

## なぜ
ログイン(パスワード・Google・2段階認証・アプリのトークン)は、まだ Supabase Auth(GoTrue)が行っています。これを Web の中の Better Auth に移します。

決まっていること([../supabase-removal-plan.md](../supabase-removal-plan.md) の決定事項):
- 認証は Better Auth(D-4)。切り替え時は**全員にもう一度ログインしてもらう**。パスワードのハッシュと Google の紐づけは移すので、パスワードの再設定は要らない(D-7)。2段階認証(TOTP)は**設定し直してもらう**(D-8)。
- 古い版のアプリは、新しい版を出したらすぐに止める(D-6)。

## 作りの方針(この3つを守る)
1. **Better Auth に任せるもの**: ユーザー・パスワード・Google ログイン・セッション(Web は Cookie、アプリは Bearer)。
2. **自前で作るもの: TOTP の「上乗せ」**。今の仕組み(パスワードでログインすると aal1、TOTP を確かめると同じセッションが aal2 になる。管理者の操作は aal2 が必要)を変えないため、セッションに `aal` の列を足し、TOTP は自前の表 `mfa_totp` で扱う。Better Auth の twoFactor プラグインは使わない(ログインの流れそのものが変わり、アプリと管理者の判定 `is_admin()` を大きく作り直すことになるため)。
3. **アプリ向けに「Supabase Auth と同じ形の API」を Web に作る**(`/api/v1/auth/*`)。パス・本文・応答・エラーの形を GoTrue と同じにするので、3つのアプリは**呼び先の URL を変えるだけ**で済む。

> [!WARNING]
> 移行期間中は、Supabase の PostgREST がまだ動いています。Supabase は、`public` に作った表に、未ログイン(`anon`)・ログイン中(`authenticated`)のロールの権限を**自動で与えます**。新しい認証の表(パスワードのハッシュが入る)をそのままにすると、**誰でも `/rest/v1/ba_account` で読めてしまいます。** 4-2 のマイグレーションで必ず権限を外し、4-10 のテストで確かめてください。

## 前提
- フェーズ3 が終わっている。
- [phase0-app-endpoints.md](phase0-app-endpoints.md) の「進み具合」の 0-4 に、本番の `autoconfirm`(メールの確認が要らないか)が記録されている。
- **持ち主が済ませておくこと**(エージェントにはできない): Google Cloud Console で、Web 用の OAuth クライアントの「承認済みのリダイレクト URI」に `https://store.kawamonn.com/api/auth/callback/google` と `http://127.0.0.1:3100/api/auth/callback/google` を足す。済んでいなければ、4-13 に入る前に止まって依頼する。
- ブランチ: ストア・日記アプリとも `supabase-removal/phase4-auth`(日記アプリはフェーズ3のブランチから切る)。マージ前の main を「進み具合」に書く。

---

## ステップ 4-1: Better Auth を入れて、使う API を確かめる

```powershell
Set-Location C:\dev\appstore\web
npm install better-auth qrcode bcryptjs
npm install --save-dev @types/qrcode @types/bcryptjs @better-auth/cli
```
`otplib` はフェーズT で開発用に入れた。Web の本体でも使うので `npm install otplib` で `dependencies` に移す。

インストールした版の資料(`node_modules/better-auth/README.md` と型定義)を読み、次の名前が**この版で正しいか**を確かめて、`docs/supabase-removal/better-auth-notes.md`(新規)に「版・確かめた名前・手順書との違い」を書く。違っていたら、以降の手順はこのメモの名前で読み替える。

| 使うもの | 手順書での名前 |
|---|---|
| 本体 | `betterAuth` from `"better-auth"` |
| Next.js のルート | `toNextJsHandler` from `"better-auth/next-js"`、Cookie の書き込み用プラグイン `nextCookies` from `"better-auth/next-js"` |
| Bearer のプラグイン | `bearer` from `"better-auth/plugins"`(ログインの応答のヘッダー `set-auth-token` にトークンが入る) |
| ブラウザのクライアント | `createAuthClient` from `"better-auth/react"` |
| パスワードの関数 | `hashPassword`・`verifyPassword` from `"better-auth/crypto"` |
| 設定 | `database`(pg の `Pool`)、`secret`、`baseURL`、`user`/`session`/`account`/`verification` の `modelName` と `fields`、`session.additionalFields`、`session.expiresIn`・`updateAge`、`emailAndPassword.password.hash`/`verify`、`emailAndPassword.requireEmailVerification`、`emailVerification.sendVerificationEmail`、`socialProviders.google`、`account.accountLinking`、`advanced.database.generateId`、`databaseHooks` |
| サーバーの呼び出し | `auth.api.signInEmail`・`signUpEmail`・`signInSocial`(本文に `idToken: { token, nonce }`)・`getSession`・`signOut`。`returnHeaders: true` で応答のヘッダーを受け取れること |

**Google の IDトークンの `nonce` の扱い**も確かめてメモに書く: Better Auth の Google の IDトークンの検証が、渡した `nonce` をトークンの `nonce` とそのまま比べるのか、ハッシュしてから比べるのか(ソースの `social-providers` の google の `verifyIdToken` を読む)。今のアプリは、Google には「元の nonce の SHA-256(16進)」を渡し、Supabase には「元の nonce」を送っている(Supabase がハッシュして比べる)。4-7 の互換 API が、この違いを吸収する。

完了の条件: メモができている。`npm run typecheck` が合格。
コミット: `Better Auth を入れ、使う API をこの版で確かめたメモを残す`

## ステップ 4-2: 認証の表を作るマイグレーション(A)

1. Better Auth の CLI で、4-3 の設定に対応する SQL を出す(4-3 の設定ファイルを先に下書きしておく):
   ```powershell
   npx @better-auth/cli generate --config src/lib/auth.ts --output ..\supabase\better-auth-generated.sql
   ```
   出てきた SQL を**そのまま使わず**、次の決まりで `supabase/migrations/<日時>_better_auth_tables.sql` を手で書く(生成した SQL はコミットしない。消す):
   - 表の名前: `ba_user`・`ba_session`・`ba_account`・`ba_verification`(4-3 の `modelName`)。
   - **ID は `uuid`**(`ba_user.id` は、今の `auth.users.id` と同じ値を入れるため)。`ba_session.userId`・`ba_account.userId` も `uuid` で、`ba_user(id) on delete cascade` への外部キー。
   - 列名は Better Auth の既定(`"emailVerified"`・`"createdAt"` など。大文字を含むので `"` で囲む)のまま。
   - `ba_session` に `aal text not null default 'aal1' check (aal in ('aal1', 'aal2'))` を足す(4-3 の `additionalFields`)。
   - `ba_user.email` に一意の索引、`ba_session.token` に一意の索引、`ba_session."userId"`・`ba_account."userId"` に索引。
2. TOTP の表を同じファイルに作る:
   ```sql
   create table public.mfa_totp (
     user_id uuid primary key references public.ba_user (id) on delete cascade,
     secret_ciphertext text not null,   -- AES-256-GCM で暗号化した秘密鍵(src/lib/mfa.ts)
     verified_at timestamptz,            -- 確認済みなら日時。null は登録の途中
     last_used_step bigint,              -- 最後に使ったコードの時間枠(同じコードの使い回しを防ぐ)
     created_at timestamptz not null default now()
   );
   ```
3. Better Auth が DB につなぐロール(ログインのパスワードは運用で設定する):
   ```sql
   do $$ begin
     if not exists (select 1 from pg_roles where rolname = 'ttech_auth') then create role ttech_auth nologin; end if;
   end $$;
   grant usage on schema public to ttech_auth;
   grant select, insert, update, delete on public.ba_user, public.ba_session, public.ba_account, public.ba_verification, public.mfa_totp to ttech_auth;
   grant select (id, banned_at, role) on public.profiles to ttech_auth;
   ```
4. **未ログイン・ログイン中のロールから権限を外し、RLS を有効にしてポリシーは作らない**(PostgREST から一切読めないようにする):
   ```sql
   revoke all on public.ba_user, public.ba_session, public.ba_account, public.ba_verification, public.mfa_totp from anon, authenticated;
   alter table public.ba_user enable row level security;
   alter table public.ba_session enable row level security;
   alter table public.ba_account enable row level security;
   alter table public.ba_verification enable row level security;
   alter table public.mfa_totp enable row level security;
   ```
   `service_role` は Supabase で最初から RLS を通らない。`ttech_auth` には、この5つの表と `profiles` の読み取りだけを許すポリシーを作る(`bypassrls` はローカルのマイグレーションの権限では付けられないため、使わない):
   ```sql
   create policy ba_user_auth on public.ba_user to ttech_auth using (true) with check (true);
   create policy ba_session_auth on public.ba_session to ttech_auth using (true) with check (true);
   create policy ba_account_auth on public.ba_account to ttech_auth using (true) with check (true);
   create policy ba_verification_auth on public.ba_verification to ttech_auth using (true) with check (true);
   create policy mfa_totp_auth on public.mfa_totp to ttech_auth using (true) with check (true);
   create policy profiles_select_auth on public.profiles for select to ttech_auth using (true);
   ```
5. `supabase/rollback/<同じ名前>.down.sql`: 表とロールを消す(`drop table ... cascade; drop role if exists ttech_auth;`)+ `ops.applied_migrations` の行を消す。
6. ローカル専用の `tests/support/db-roles.sql` に足す: `alter role ttech_auth login password 'local-only';`(マイグレーションの後で実行されるので、ロールはある)。`scripts/write-local-env.mjs` に `AUTH_DATABASE_URL=postgresql://ttech_auth:local-only@127.0.0.1:<port>/postgres` を足す。

完了の条件: `npm run test:db` が合格。`npm run db:types` で型を作り直し、`npm run typecheck` が合格。
コミット: `Better Auth の表と TOTP の表を足す(未ログインのロールからは見えないようにする)`

## ステップ 4-3: Better Auth の設定 `src/lib/auth.ts`

`src/lib/env.ts` に足す: `authDatabaseUrl`(`AUTH_DATABASE_URL`)、`betterAuthSecret`(`BETTER_AUTH_SECRET`)、`googleClientId`、`googleClientSecret`、`mfaEncryptionKey`(`MFA_ENCRYPTION_KEY`。64文字の16進数)、`authAutoconfirm`(`AUTH_AUTOCONFIRM === "true"`)、`legacySupabaseAuth`(`LEGACY_SUPABASE_AUTH === "1"`)。`.env.example` にも名前を足す。`write-local-env.mjs` には、ローカル用の固定値(`BETTER_AUTH_SECRET=local-better-auth-secret-0123456789abcdef`、`MFA_ENCRYPTION_KEY=` 64桁の `0`、`AUTH_AUTOCONFIRM=true`、`GOOGLE_CLIENT_ID=local`、`GOOGLE_CLIENT_SECRET=local`、`LEGACY_SUPABASE_AUTH=1`)を足す。

設定の骨組み(4-1 のメモで名前を確かめてから書く):
```ts
import { betterAuth } from "better-auth";
import { nextCookies } from "better-auth/next-js";
import { bearer } from "better-auth/plugins";
import { hashPassword, verifyPassword } from "better-auth/crypto";
import bcrypt from "bcryptjs";
import pg from "pg";
import { env } from "./env";
import { sendVerificationMail } from "./mail";

const globalForAuth = globalThis as unknown as { ttechAuthPool?: pg.Pool };
const pool = (globalForAuth.ttechAuthPool ??= new pg.Pool({ connectionString: env.authDatabaseUrl(), max: 5, options: "-c TimeZone=UTC" }));

export const auth = betterAuth({
  database: pool,
  secret: env.betterAuthSecret(),
  baseURL: env.siteUrl(),
  user: { modelName: "ba_user" },
  session: {
    modelName: "ba_session",
    expiresIn: 60 * 60 * 24 * 30,   // 30日(アプリで何度もログインし直さずに済むように)
    updateAge: 60 * 60 * 24,        // 1日ごとに期限を延ばす
    additionalFields: { aal: { type: "string", defaultValue: "aal1", input: false } },
  },
  account: { modelName: "ba_account", accountLinking: { enabled: true, trustedProviders: ["google"] } },
  verification: { modelName: "ba_verification" },
  advanced: { database: { generateId: () => crypto.randomUUID() } },
  emailAndPassword: {
    enabled: true,
    requireEmailVerification: !env.authAutoconfirm(),
    password: {
      hash: (password) => hashPassword(password),
      // Supabase から移したパスワードは bcrypt($2a$/$2b$)。新しく設定したものは Better Auth の形式
      verify: async ({ hash, password }) => (hash.startsWith("$2") ? bcrypt.compare(password, hash) : verifyPassword({ hash, password })),
    },
  },
  emailVerification: { sendVerificationEmail: async ({ user, url }) => sendVerificationMail(user.email, url) },
  socialProviders: { google: { clientId: env.googleClientId(), clientSecret: env.googleClientSecret() } },
  databaseHooks: {
    session: {
      create: {
        // BAN 中のユーザーにはセッションを作らない(ログインできない)
        before: async (session) => {
          const { rows } = await pool.query("select banned_at from public.profiles where id = $1", [session.userId]);
          if (rows[0]?.banned_at) return false;
        },
      },
    },
  },
  plugins: [bearer(), nextCookies()],
});
```
- `sendVerificationMail` は `src/lib/mail.ts` に足す(既存の `sendMailTo` を使い、件名「メールアドレスの確認」、本文に URL)。`RESEND_API_KEY` が空なら送らずにログに出すだけ(既存の動きと同じ)。
- 新規ユーザーの `profiles` の行は、DB のトリガーで作る(4-4 のマイグレーション B)。
- ルート `src/app/api/auth/[...all]/route.ts`: `export const { GET, POST } = toNextJsHandler(auth);`
- `routes-inventory.test.ts` は `src/app/api/auth/[...all]` を**対象外**にする(Better Auth の内部のルートで、表で網羅できないため)。代わりに 4-10 のテストで確かめる。

完了の条件: `npm run typecheck` が合格。
コミット: `Better Auth の設定とルートを足す`

## ステップ 4-4: 移行のマイグレーション(B)

`supabase/migrations/<日時>_auth_cutover.sql`。**本番では 4-13 の切り替えのときに適用する。** ローカルでは他のマイグレーションと同じく `db reset` で入る(このときはユーザーがいないので、データの写しは0件)。

内容(この順番で):
1. **ユーザーを写す**(`auth.users` → `ba_user`):
   ```sql
   insert into public.ba_user (id, name, email, "emailVerified", "createdAt", "updatedAt")
   select u.id,
          coalesce(nullif(p.display_name, ''), split_part(u.email, '@', 1)),
          lower(u.email),
          u.email_confirmed_at is not null,
          u.created_at,
          coalesce(u.updated_at, u.created_at)
   from auth.users u left join public.profiles p on p.id = u.id
   where u.email is not null and u.deleted_at is null
   on conflict (id) do nothing;
   ```
2. **パスワードを写す**(ハッシュのまま。`ba_account` の `providerId = 'credential'`、`accountId` はユーザーの ID の文字列):
   ```sql
   insert into public.ba_account (id, "accountId", "providerId", "userId", password, "createdAt", "updatedAt")
   select gen_random_uuid(), u.id::text, 'credential', u.id, u.encrypted_password, u.created_at, now()
   from auth.users u join public.ba_user b on b.id = u.id
   where coalesce(u.encrypted_password, '') <> '';
   ```
3. **Google の紐づけを写す**(`accountId` は Google のユーザー ID = `auth.identities.provider_id`):
   ```sql
   insert into public.ba_account (id, "accountId", "providerId", "userId", "createdAt", "updatedAt")
   select gen_random_uuid(), i.provider_id, 'google', i.user_id, i.created_at, now()
   from auth.identities i join public.ba_user b on b.id = i.user_id
   where i.provider = 'google';
   ```
   (列名は 4-2 で作った表に合わせる。`ba_account` の必須の列が他にあれば埋める)
4. **`profiles` の外部キーの付け替え**: `profiles.id` → `auth.users(id)` を外し、`ba_user(id) on delete cascade` にする(制約の名前は `\d public.profiles` で確かめる)。`profiles` にあって `ba_user` に無い ID があると失敗する。その場合は失敗させてよい(トランザクションごと戻る)。
5. **新規ユーザーのトリガーの付け替え**: `auth.users` の `on_auth_user_created` を消し、`ba_user` に同じ役目のトリガーを作る(`profiles` が既にあれば何もしない: `on conflict (id) do nothing`。表示名は `ba_user.name`、空ならメールの `@` の前)。
6. **`auth.*` の表を読んでいる関数を書き換える**。対象を DB で探す:
   ```sql
   select p.proname from pg_proc p where p.pronamespace = 'public'::regnamespace and (p.prosrc ilike '%auth.users%' or p.prosrc ilike '%auth.mfa_factors%' or p.prosrc ilike '%auth.identities%');
   ```
   少なくとも次がある。それぞれ同じ意味のまま書き換える:
   - `is_admin()`: `auth.mfa_factors` の確認を、`public.mfa_totp`(`verified_at is not null`)に。`auth.jwt() ->> 'aal'` はそのまま(Web が `asUser` で渡している)。
   - `admin_list_users(...)`: `auth.users` の列を `ba_user` から取る。`provider` は Google の `ba_account` があれば `google`、なければ `email`。`last_sign_in_at` は、そのユーザーの `ba_session."createdAt"` の最大値。戻り値の列の名前と型は変えない(管理画面とスナップショットのため)。
   - `diary_delete_my_account()`: `delete from auth.users ...` を `delete from public.ba_user where id = auth.uid()` に。
7. `down.sql`: 6 → 5 → 4 を元に戻す(関数は元の定義に。元の定義は、この前までのマイグレーションの中にある)。1〜3 で写した行は `ba_*` から消す。`ops.applied_migrations` の行を消す。

テスト `tests/migration/auth-cutover.test.ts`(新しい設定 `vitest.migration.config.ts`。`package.json` に `"test:migration"` を足す):
1. `npx supabase db reset --workdir .. --version <マイグレーション B の直前の日時>` で、B の手前まで入れた DB を作る(`--version` が使えない CLI の版なら、B のファイルを一時的に別の場所に移して reset し、終わったら戻す。どちらにしたかをメモに書く)。
2. Supabase Auth で、パスワードのユーザー2人(うち1人は管理者で TOTP 登録済み)と、`auth.identities` に Google の行を持つユーザー1人を作る(Google の行は SQL で直接入れてよい)。
3. B のファイルを `DATABASE_ADMIN_URL` で流す。
4. 確かめる: 全員が `ba_user` にいる。パスワードのユーザーは、**Better Auth で同じパスワードでログインできる**(Web を起動し `POST /api/v1/auth/token?grant_type=password`)。Google のユーザーは `ba_account` に `providerId = 'google'`・`accountId` = 元の `provider_id` の行がある。管理者は `mfa_totp` が空なので、`is_admin()` が aal1 でも true(D-8 の「設定し直すまで」の状態)。`admin_list_users` が全員を返す。
5. `down.sql` を流すと元に戻り、もう一度 B を流しても成功する。

完了の条件: `npm run test:migration` が合格。
コミット: `Supabase Auth のユーザーを Better Auth に移すマイグレーションと、そのテストを足す`

## ステップ 4-5: TOTP `src/lib/mfa.ts`

```ts
/** TOTP(2段階認証)。秘密鍵は AES-256-GCM で暗号化して mfa_totp に置く。鍵は MFA_ENCRYPTION_KEY(32バイトの16進) */
export function encryptSecret(secret: string): string;           // "iv.tag.ciphertext"(base64url を . でつなぐ)
export function decryptSecret(payload: string): string;
export async function startEnrollment(userId: string, email: string): Promise<{ secret: string; uri: string; qrSvgDataUrl: string }>;
export async function confirmEnrollment(userId: string, code: string): Promise<boolean>;  // 正しければ verified_at を入れる
export async function verifyCode(userId: string, code: string): Promise<boolean>;       // 確認済みの鍵で確かめる。同じ時間枠の使い回しは false
export async function hasVerifiedTotp(userId: string): Promise<boolean>;
export async function removeTotp(userId: string): Promise<void>;
export async function markSessionAal2(sessionId: string): Promise<void>;                 // ba_session.aal を 'aal2' に
```
- 暗号化は Node の `crypto.createCipheriv("aes-256-gcm", key, iv)`(iv は12バイトの乱数)。
- コードの確認は `otplib` の `authenticator`(30秒・6桁・前後1枠まで許す)。**使った時間枠を `last_used_step` に保存し、それ以下の枠のコードは拒む**(同じコードの使い回しを防ぐ)。
- `startEnrollment` は、既に確認済みの鍵があれば例外にする(置き換えるときは先に `removeTotp`)。確認前の鍵は上書きしてよい。
- QR は `qrcode` の `toString(uri, { type: "svg" })` を data URL にする。`uri` の発行者は `T-tech Store`。
- DB は `asService`(フェーズ1)で読み書きする。
- 単体テスト `src/lib/mfa.test.ts`: 暗号化して戻せる、鍵が違えば戻せない、`otplib` で作ったコードで確認できる、同じコードの2回目は false、30秒前のコードは通る・90秒前は通らない(時計は `vi.setSystemTime`)。DB に触れる関数は API テスト(4-10)で確かめる。

コミット: `TOTP の登録と確認を自前で行う(秘密鍵は暗号化して保存する)`

## ステップ 4-6: 「今のユーザー」を Better Auth にする

`src/lib/current-user.ts` の中身を置き換える(関数の名前と戻り値の形は変えない):
- `currentUserFromCookies()`: `auth.api.getSession({ headers: await headers() })`(`next/headers`)。セッションがあれば `{ id: user.id, email: user.email, aal: session.aal }`。
- `currentUserFromBearer(request)`: `auth.api.getSession({ headers: request.headers })`(Bearer のプラグインが `Authorization` を読む)。
- どちらも、見つからず、かつ `env.legacySupabaseAuth()` が true のときだけ、**今までの Supabase の確認**(今の中身)を続けて試す。古い版のアプリのための一時的な戻り道で、4-14 で切る。フェーズ6で消す。
- BAN 中のユーザーは、セッションがあっても null を返す(`profiles.banned_at` を見る)。

`src/lib/supabase.ts` の `getAdminUser`・`adminStepUpDone`・`verifiedTotpFactor`、`src/lib/admin.ts` の `adminFromBearerToken` を、`current-user.ts` と `mfa.ts` を使う形にする:
- 管理者 = `profiles.role = 'admin'` かつ BAN でない。
- TOTP を確認済みなら、`aal === "aal2"` のときだけ管理者として扱う。Bearer でこれを満たさないときは、今と同じく 401 `{ code: "totp_required" }`。

コミット: `ログイン中のユーザーの確認を Better Auth のセッションにする(古いトークンは一時的に受け付ける)`

## ステップ 4-7: アプリ向けの互換 API `/api/v1/auth/*`

GoTrue(Supabase Auth)と同じパス・本文・応答にする。場所: `src/app/api/v1/auth/`。共通の処理は `src/lib/gotrue-compat.ts` に置く。

**セッションの応答**(ログイン・更新・TOTP の確認で返す):
```json
{
  "access_token": "<Better Auth のセッションのトークン(set-auth-token の値)>",
  "token_type": "bearer",
  "expires_in": 2592000,
  "expires_at": 1790000000,
  "refresh_token": "<access_token と同じ値>",
  "user": { "id": "<uuid>", "email": "a@example.com", "factors": [ { "id": "<uuid>", "factor_type": "totp", "status": "verified" } ] }
}
```
- `expires_in` はセッションの残り秒数、`expires_at` はその時刻(UNIX 秒)。
- `factors` は、`mfa_totp` があれば1件(`id` はユーザーの ID、`status` は確認済みなら `verified`、途中なら `unverified`)、なければ空の配列。

**エラーの応答**(GoTrue と同じ。アプリは `error_code`・`msg`・`error_description`・`error` を読む):
```json
{ "error": "invalid_grant", "error_description": "Invalid login credentials", "msg": "Invalid login credentials", "error_code": "invalid_credentials" }
```

| パス | 本文 | 動き |
|---|---|---|
| `POST token?grant_type=password` | `{ email, password }` | `auth.api.signInEmail(..., returnHeaders: true)` → `set-auth-token` をトークンにしてセッションの応答。違えば 400 `invalid_credentials`。BAN 中は 400 `user_banned`。メール未確認は 400 `email_not_confirmed` |
| `POST token?grant_type=id_token` | `{ provider: "google", id_token, nonce }` | `auth.api.signInSocial({ body: { provider: "google", idToken: { token: id_token, nonce: <4-1 で確かめた形> } } })`。**アプリは「元の nonce」を送ってくる**。Better Auth がトークンの `nonce` とそのまま比べるなら、ここで SHA-256(16進)にしてから渡す |
| `POST token?grant_type=refresh_token` | `{ refresh_token }` | そのトークンのセッションが有効なら、同じトークンでセッションの応答(期限は Better Auth が延ばす)。無効なら 400 `refresh_token_not_found` |
| `POST signup` | `{ email, password }` | `auth.api.signUpEmail`。`AUTH_AUTOCONFIRM` なら、続けてログインしてセッションの応答。そうでなければ `{ id, email, confirmation_sent_at }`(確認メールは Better Auth が送る)。既にあれば 422 `user_already_exists` |
| `POST logout` | (Bearer) | セッションを消す。204 |
| `GET user` | (Bearer) | `{ id, email, factors }`。未ログインは 401 |
| `POST factors` | `{ factor_type: "totp", friendly_name? }`(Bearer か Cookie) | `startEnrollment` → `{ id, type: "totp", totp: { qr_code: <SVG の data URL>, secret, uri } }` |
| `POST factors/{id}/challenge` | (Bearer か Cookie) | `{ id: <challenge_id>, expires_at }`。challenge_id は、`{ userId, sessionId, exp(5分) }` を `BETTER_AUTH_SECRET` で HMAC 署名した文字列(DB に保存しない) |
| `POST factors/{id}/verify` | `{ challenge_id, code }` | challenge_id の署名・期限・セッションが一致し、コードが正しければ: 登録の途中なら `confirmEnrollment`、確認済みなら `verifyCode`。成功したら `markSessionAal2` してセッションの応答。違えば 422 `mfa_verification_failed` |
| `DELETE factors/{id}` | (Bearer か Cookie) | aal2 のセッションのときだけ `removeTotp`。aal1 なら 403 `insufficient_aal` |

- `{id}` が自分のユーザーの ID でなければ 404。
- 権限の表に、`POST logout`・`GET user`・`factors*` の行を足す(`token`・`signup` は未ログインで使うので、未ログインで 400/422 になる行を書く)。

コミット: `アプリ向けに Supabase Auth と同じ形のログインの API を足す`

## ステップ 4-8: Web の画面を Better Auth にする

ブラウザのクライアント `src/lib/auth-client.ts`: `export const authClient = createAuthClient({ baseURL: window.location.origin })`(4-1 のメモで書き方を確かめる)。

| ファイル | 今 | これから |
|---|---|---|
| `app/login/page.tsx` | `signInWithPassword`・`signUp`・`signInWithOAuth({ provider: "google" })` | `authClient.signIn.email`・`authClient.signUp.email`・`authClient.signIn.social({ provider: "google", callbackURL: next })`。エラーの文言は今と同じにする |
| `app/admin/login/page.tsx` | パスワード → `mfa.challengeAndVerify` | パスワードは `authClient.signIn.email`。TOTP が要るときは `/api/v1/auth/factors/{id}/challenge` → `/verify` を `fetch`(Cookie のまま) |
| `app/account/TotpSetup.tsx` | `mfa.enroll`・`verify`・`unenroll`・`listFactors` | `/api/v1/auth/factors`(登録)→ QR を表示 → `challenge` → `verify`。解除は `DELETE factors/{id}` |
| `app/admin/(console)/LogoutButton.tsx`・`app/account/DeleteAccountButton.tsx` | `browserClient().auth.signOut()` | `authClient.signOut()` |
| `app/layout.tsx`・`app/account/page.tsx` | `supabase.auth.getUser()` | `currentUserFromCookies()` |
| `app/auth/callback/route.ts` | Supabase の code をセッションに交換 | Google の戻り先は Better Auth(`/api/auth/callback/google`)になる。このルートは `next` へ転送するだけにする(古いリンクのため) |
| `src/proxy.ts` | Supabase のセッションを毎回更新 | Better Auth は更新が要らない。中身を消して `NextResponse.next()` だけにするか、ファイルごと消す(この版の Next.js で proxy を消してよいか資料で確かめる) |
| `src/lib/supabase-browser.ts` | | 使われなくなったら消す |

ページのテスト(`tests/api/flows/pages.test.ts`)は、Cookie を 4-10 の新しいドライバーで作るので、そのまま通るはず。

コミット: 画面ごとに1つずつ。

## ステップ 4-9: サーバー側の管理の処理

- BAN(`src/lib/moderation.ts`): `auth.admin.updateUserById(ban_duration)` をやめ、`profiles.banned_at` を立てたうえで、そのユーザーの `ba_session` を全部消す(`asService`)。解除は `banned_at` を null に。ログインは 4-3 のフックで拒まれる。
- アカウントの削除(`src/app/api/account/delete/route.ts`・管理者による削除): `auth.admin.deleteUser` をやめ、`asService` で `delete from ba_user where id = ...`(`profiles` 以下は外部キーで消える)。
- 管理画面のユーザーの情報(`src/lib/admin-queries.ts`): `auth.admin.getUserById` をやめ、`ba_user`・`ba_account`・`ba_session` から読む(メール・ログイン方法・最終ログイン)。
- 確認: `Select-String -Path (Get-ChildItem -Recurse src -Include *.ts,*.tsx) -Pattern '\.auth\.'` で、残っているのは `current-user.ts` の古いトークンの戻り道だけ。

コミット: `BAN・アカウントの削除・ユーザーの情報を Better Auth の表で行う`

## ステップ 4-10: テストを Better Auth で回す

1. `tests/support/auth-driver.ts` に `betterAuthDriver()` を足し、`AUTH_DRIVER=better-auth` で選べるようにする:
   - `createUser`: `POST /api/v1/auth/signup`。ローカルは `AUTH_AUTOCONFIRM=true` なので、そのままログインできる。
   - `bearer`: `POST /api/v1/auth/token?grant_type=password` の `access_token`。aal2 は `GET user` の `factors` → `challenge` → `verify`(コードは `freshTotp`)。
   - `cookie`: `POST /api/auth/sign-in/email`(本文 `{ email, password }`)の `Set-Cookie` を集める。aal2 は、その Cookie で `challenge` → `verify`。
   - `enrollTotp`: Cookie で `POST /api/v1/auth/factors` → `challenge` → `verify`。
   - 人物の作成は、Web が起動したあとでないとできない。`global-setup.ts` の順番を「Next.js を起動 → 人物とデータを作る」に入れ替える(`AUTH_DRIVER=supabase` のときも同じ順番で問題ない)。
2. `vitest.api.config.ts` の既定を `AUTH_DRIVER=better-auth` にする(`test.env`)。**フェーズT からの全テスト(権限の表・スナップショット・業務の流れ・日記のシナリオ)が、変更なしで合格すること。** スナップショットで変わってよいのは、管理画面のユーザーの最終ログイン日時(`<ts:...>` の形は同じ)と、ログイン方法の値だけ。
3. 新しいテスト `tests/api/flows/auth.test.ts`:
   - パスワードの誤り → 400 `invalid_credentials`。BAN 中 → ログインできない。BAN したら、それまでのセッションも使えなくなる。
   - 同じ TOTP のコードを2回使うと、2回目は失敗する。
   - 管理者(TOTP あり)は、aal1 のセッションで管理 API が 401 `totp_required`、aal2 で 200。
   - `refresh_token` で同じトークンが返り、ログアウト後は 400。
   - **未ログイン・一般ユーザーのトークンで、PostgREST から `ba_user`・`ba_session`・`ba_account`・`ba_verification`・`mfa_totp` が読めない**(`GET {Supabase}/rest/v1/ba_account?select=*` が 401/403 か空の配列)。
   - 古いトークン(`LEGACY_SUPABASE_AUTH=1`): Supabase Auth で作ったユーザー(同じ ID の行を `ba_user`・`profiles` にも入れておく)の Supabase のトークンで、`GET /api/v1/me/account` が 200。
4. スモークテストに `betterAuthDriver` を使えるようにする(`SMOKE_AUTH_DRIVER=better-auth`)。

完了の条件: `npm run test:api` が3回続けて合格。
コミット: `API テストを Better Auth のログインで回す`

## ステップ 4-11: 3つのアプリのログインの呼び先を変える

どのアプリも、ログインの API の呼び先を `{SUPABASE_URL}/auth/v1/` から `https://store.kawamonn.com/api/v1/auth/` に変える。パス(`token?grant_type=...`・`user`・`logout`・`signup`・`factors/...`)・本文・応答の読み方は**変えない**。`apikey` ヘッダーは送っても無視されるので、消してよい。

| アプリ | ファイル | 版 |
|---|---|---|
| ストアアプリ | `store-app/app/build.gradle.kts`(`authBase` を足す。既定 `https://store.kawamonn.com/api/v1/auth/`、`local.properties` の `authBase` で上書き可)、`auth/SupabaseAuthApi.kt`(ベース URL を `BuildConfig.AUTH_BASE` に) | `versionCode 12` / `0.7.0` |
| 管理アプリ | `native-apps/ttech-admin/build.gradle.kts`(同じく `authBase`)、`data/AuthApi.kt` | `versionCode 5` / `1.1.0` |
| 日記アプリ | `diary-app/app/build.gradle.kts`(同じく `authBase`)、`auth/SupabaseAuthApi.kt` | `versionCode 6` / `1.2.0` |

- `SUPABASE_URL`・`SUPABASE_ANON_KEY` の `buildConfigField` と、`build.gradle.kts` のそれらの既定値は、使われなくなったら消す。
- 各アプリに MockWebServer のテストを足す(既にあれば直す): パスワードのログイン・更新・ログアウト・ユーザーの取得・TOTP の challenge と verify(管理アプリ・ストアアプリ)・新規登録(日記アプリ)で、パスと本文が GoTrue と同じであること、応答を読めること、400 のエラーの `msg` を利用者向けのメッセージにすること。
- ビルドとテスト:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
  $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
  Set-Location C:\dev\appstore\store-app; .\gradlew.bat testDebugUnitTest assembleRelease --console=plain
  Set-Location C:\dev\appstore\native-apps; .\gradlew.bat :ttech-admin:testDebugUnitTest :ttech-admin:assembleRelease --console=plain
  Set-Location C:\dev\diary-app; .\gradlew.bat :app:testDebugUnitTest :app:assembleRelease --console=plain
  ```
- 各リリース APK を [phase0-app-endpoints.md](phase0-app-endpoints.md) の `Get-ApkEndpoints` で調べ、`supabase.co` が無く、`store.kawamonn.com` があること。

コミット: アプリごとに1つ(`ストアアプリ 0.7.0: ログインをストアのサーバーで行う` など)。

## ステップ 4-12: 本番に出す前の合格条件

- [ ] `npm run typecheck`・`npm test`・`npm run test:db`・`npm run test:migration`・`npm run build` が合格
- [ ] `npm run test:api` が3回続けて合格(`AUTH_DRIVER=better-auth`)
- [ ] 3つのアプリの `testDebugUnitTest` と `assembleRelease` が成功し、APK の接続先が正しい
- [ ] GitHub Actions が全部緑
- [ ] 持ち主が Google Cloud Console のリダイレクト URI を足し終えている(「前提」)

## ステップ 4-13: 本番の切り替え(本番の操作)

一つずつ実行し、各段の「確かめ」が通ってから次へ進む。**A〜F のどこかで失敗したら、下の「戻し方」で戻して止まる。**

```powershell
Import-Module C:\dev\appstore\web\scripts\ops\ops.psm1 -Force
Set-Location C:\dev\appstore\web
```

**A. バックアップと、表の追加(マイグレーション A)**
```powershell
Backup-ProdDb "phase4-A"
Invoke-ProdMigration C:\dev\appstore\supabase\migrations\<日時>_better_auth_tables.sql
```
確かめ: `Invoke-ProdQuery "select has_table_privilege('anon', 'public.ba_account', 'select'), has_table_privilege('authenticated', 'public.ba_account', 'select')"` が `f|f`。

**B. 秘密情報をサーバーに置く**(値は画面に出さない)
```powershell
function New-Hex([int]$n) { $b = New-Object byte[] $n; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { $_.ToString("x2") }) }
# Better Auth が DB につなぐロールのパスワード
$pw = New-Hex 24
$tmp = Join-Path $env:TEMP "ttech-auth-role.sql"
"alter role ttech_auth login password '$pw';" | Set-Content -Path $tmp -Encoding ASCII
Invoke-ProdSqlFile $tmp; Remove-Item $tmp
Set-ServerEnv "AUTH_DATABASE_URL" "postgres://ttech_auth:$pw@127.0.0.1:54329/postgres"
Add-Content -Path C:\dev\appstore\web\.env.prod.local -Value "AUTH_DATABASE_URL=postgres://ttech_auth:$pw@127.0.0.1:54329/postgres" -Encoding UTF8   # フェーズ6 で使う
Remove-Variable pw
Set-ServerEnv "BETTER_AUTH_SECRET" (New-Hex 32)
Set-ServerEnv "MFA_ENCRYPTION_KEY" (New-Hex 32)
Set-ServerEnv "LEGACY_SUPABASE_AUTH" "1"
Set-ServerEnv "AUTH_AUTOCONFIRM" "<phase0 の 0-4 の autoconfirm の値。True なら true、False なら false>"
```
Google の ID と秘密は、今の Supabase Auth のコンテナの環境変数から写す。`web/scripts/ops/remote/print-auth-env.sh` を作る:
```bash
#!/usr/bin/env bash
# usage: print-auth-env.sh <variable-name>
# Prints one environment variable of the Supabase auth container (used only to copy it into the web env file).
set -euo pipefail
c="$(docker ps --format '{{.Names}} {{.Image}}' | awk '$2 ~ /gotrue|supabase\/auth/ {print $1}' | head -1)"
[ -n "$c" ] || { echo "auth container not found" >&2; exit 3; }
docker exec "$c" printenv "$1"
```
```powershell
$id = (Invoke-RemoteScript "print-auth-env.sh" @("GOTRUE_EXTERNAL_GOOGLE_CLIENT_ID") | Select-Object -Last 1).Trim()
$secret = (Invoke-RemoteScript "print-auth-env.sh" @("GOTRUE_EXTERNAL_GOOGLE_SECRET") | Select-Object -Last 1).Trim()
Set-ServerEnv "GOOGLE_CLIENT_ID" $id
Set-ServerEnv "GOOGLE_CLIENT_SECRET" $secret
Remove-Variable id, secret
```
(変数の名前が違って失敗したら、`docker exec <auth> env` の**名前だけ**を `cut -d= -f1` で見て、`GOOGLE` を含む名前を使う。値は表示しない)

**C. Supabase Auth の新規登録を止める**(切り替えの間に、古いしくみでユーザーが増えないように)
`web/scripts/ops/remote/supabase-auth-signup.sh` を作る:
```bash
#!/usr/bin/env bash
# usage: supabase-auth-signup.sh <supabase-docker-dir> disable|enable
set -euo pipefail
cd "$1"
value=true; [ "$2" = "enable" ] && value=false
cp .env ".env.bak.$(date -u +%Y%m%dT%H%M%SZ)"
if grep -q '^DISABLE_SIGNUP=' .env; then sed -i "s/^DISABLE_SIGNUP=.*/DISABLE_SIGNUP=$value/" .env; else echo "DISABLE_SIGNUP=$value" >> .env; fi
docker compose up -d auth
echo "DISABLE_SIGNUP=$value"
```
```powershell
Invoke-RemoteScript "supabase-auth-signup.sh" @((Get-OpsSetting "SUPABASE_DOCKER_DIR"), "disable")
```
確かめ: [phase0](phase0-app-endpoints.md) の 0-4 の `/auth/v1/settings` を読むコマンドで `disable_signup=True`。`docker-compose.yml` の auth サービスが `DISABLE_SIGNUP` を読んでいない(値が変わらない)なら、止まって報告する。

**D. ユーザーの移行(マイグレーション B)**
```powershell
Backup-ProdDb "phase4-D"
Invoke-ProdMigration C:\dev\appstore\supabase\migrations\<日時>_auth_cutover.sql
Invoke-ProdQuery "select (select count(*) from auth.users where email is not null and deleted_at is null), (select count(*) from public.ba_user), (select count(*) from public.profiles p left join public.ba_user b on b.id = p.id where b.id is null)"
```
確かめ: 1つ目と2つ目が同じ数、3つ目が 0。

**E. Web をデプロイ**
```powershell
git -C C:\dev\appstore fetch origin
git -C C:\dev\appstore rebase origin/main
git -C C:\dev\appstore push --force-with-lease origin supabase-removal/phase4-auth
gh run watch --repo kawamonn91/ttech-store --exit-status
git -C C:\dev\appstore switch main
git -C C:\dev\appstore merge --ff-only supabase-removal/phase4-auth
git -C C:\dev\appstore push origin main
Invoke-WebDeploy
```

**F. スモークテスト**
スモーク用の管理者の TOTP は D で消えた(D-8)ので、登録し直す。`web/scripts/ops/reenroll-smoke-totp.mjs` を作る(`TTECH_ENV_FILE=.env.prod.local` で読み、`SMOKE_ADMIN_*` で `/api/v1/auth/token` → `factors` → `challenge` → `verify` を行い、新しい秘密鍵で `.env.prod.local` の `SMOKE_ADMIN_TOTP_SECRET` の行を書き換える。値は表示しない)。
```powershell
$env:TTECH_ENV_FILE = ".env.prod.local"; node scripts\ops\reenroll-smoke-totp.mjs; Remove-Item Env:\TTECH_ENV_FILE
$env:SMOKE_AUTH_DRIVER = "better-auth"; npm run test:smoke; Remove-Item Env:\SMOKE_AUTH_DRIVER
```
確かめ: 合格。合格したら `.env.prod.local` に `SMOKE_AUTH_DRIVER=better-auth` を足す。

**戻し方(A〜F で失敗したとき)**: 失敗した段から逆の順に。
- E・F: [ops.md](ops.md) の 6-1 で Web を戻す。
- D: `Invoke-ProdSqlFile C:\dev\appstore\supabase\rollback\<日時>_auth_cutover.down.sql`
- C: `Invoke-RemoteScript "supabase-auth-signup.sh" @((Get-OpsSetting "SUPABASE_DOCKER_DIR"), "enable")`
- B・A: 戻さなくてよい(使われないだけ)。
戻したら、古い組み合わせでスモークテスト(`SMOKE_AUTH_DRIVER=supabase`)が通ることを確かめて止まる。

## ステップ 4-14: アプリを公開し、古いしくみを止める(本番の操作)

1. 3つのアプリのブランチを main にマージして push する(日記アプリは `C:\dev\diary-app` で同じように)。
2. 公開する(ストアアプリ → 管理アプリ → 日記アプリの順。ストアアプリが先なのは、利用者がストア経由で他のアプリを更新するため):
   ```powershell
   Start-DbTunnel
   Set-Location C:\dev\appstore\web
   $env:TTECH_ENV_FILE = ".env.prod.local"
   node scripts\publish-app.mjs --apk ..\store-app\app\build\outputs\apk\release\<ストアアプリの APK の名前>.apk --notes "ログインのしくみを新しくしました。更新後にもう一度ログインしてください" --publish
   node scripts\publish-app.mjs --apk ..\native-apps\ttech-admin\build\outputs\apk\release\ttech-admin-release.apk --notes "ログインのしくみを新しくしました。更新後にもう一度ログインし、2段階認証を設定し直してください" --publish
   node scripts\publish-app.mjs --apk C:\dev\diary-app\app\build\outputs\apk\release\app-release.apk --notes "ログインのしくみを新しくしました。更新後にもう一度ログインしてください" --publish
   Remove-Item Env:\TTECH_ENV_FILE
   ```
   (ストアアプリの APK の名前は `store-app\app\build\outputs\apk\release\` を見て確かめる)
   確かめ: 3つとも `公開しました`。
3. 古いトークンの受け付けを止める(D-6: 新しい版を出したらすぐ止める):
   ```powershell
   Set-ServerEnv "LEGACY_SUPABASE_AUTH" "0"
   Invoke-WebDeploy
   npm run test:smoke
   ```
4. 持ち主への報告に、次の依頼を書く(エージェントにはできないこと):
   - Web の `/account` で**2段階認証を今すぐ設定し直す**こと(設定し直すまでは、管理者のアカウントがパスワードだけで守られている)。
   - スマホの3つのアプリを更新し、ログインし直す。管理アプリでは2段階認証も。
   - Web とアプリで、**Google でのログイン**ができるか確かめる(Google のログインは自動のテストで確かめられない)。

## ステップ 4-15: 記録
「進み具合」を埋め、計画の「状態」を `フェーズ4 まで完了` にする。コミットして push(両方のリポジトリ)。

---

## 進み具合

| ステップ | 日付 | 結果・メモ |
|---|---|---|
| 4-0 | | マージ前の main: appstore=`` / diary-app=`` |
| 4-1 | | Better Auth の版: |
| 4-2 | | |
| 4-3 | | |
| 4-4 | | |
| 4-5 | | |
| 4-6 | | |
| 4-7 | | |
| 4-8 | | |
| 4-9 | | |
| 4-10 | | |
| 4-11 | | |
| 4-12 | | |
| 4-13 | | A / B / C / D / E / F |
| 4-14 | | |
| 4-15 | | |
