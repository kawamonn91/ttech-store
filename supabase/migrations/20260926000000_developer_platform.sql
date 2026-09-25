-- 開発者プラットフォーム: 誰でも開発者として登録し、APKの自動審査を通ったアプリは自動で公開できるようにする。
--
--   * 開発者の登録はサーバー(API)を通して行い、すぐに利用できる。運営(管理者)自身の開発者は
--     これまでどおり手動承認(review_mode = 'manual')。
--   * リリースごとに、自動審査の結果(policy_verdict / policy_findings)と、自動承認されたかどうかを記録する。
--   * アプリ・リリースの作成は、API(service_role)経由だけにする。クライアントから直接作らせると、
--     パッケージ名の予約(com.google.* など)や利用枠の確認を回避できてしまうため。
--
-- 元に戻す場合は、追加した列・制約を削除し、削除したポリシーを 20260920000000_init.sql の定義で作り直す。

-- ---------------------------------------------------------------- developers
alter table public.developers
  add column if not exists terms_accepted_at timestamptz,
  -- auto: APKの自動審査を通れば自動で公開する / manual: 必ず運営が承認してから公開する
  add column if not exists review_mode text not null default 'auto' check (review_mode in ('auto', 'manual'));

-- 運営(管理者)自身のアプリは、これまでどおり運営が承認して公開する
update public.developers set review_mode = 'manual'
where user_id in (select id from public.profiles where role = 'admin');

-- 開発者名の重複(大文字小文字を区別しない)を防ぐ。なりすまし対策
create unique index if not exists developers_name_lower_key on public.developers (lower(name));

-- 公開ページに出さない列(review_mode / terms_accepted_at)は、既存の列権限のまま(select は列を限定して許可済み)

-- ---------------------------------------------------------------- app_releases
alter table public.app_releases
  add column if not exists policy_verdict text check (policy_verdict in ('auto_approve', 'needs_review', 'reject')),
  add column if not exists policy_version integer,
  add column if not exists policy_findings jsonb not null default '[]'::jsonb,
  add column if not exists auto_approved boolean not null default false,
  -- 運営が承認・却下したときのメモ(開発者にも見える)
  add column if not exists review_note text;

-- ---------------------------------------------------------------- apps
-- 掲載内容の長さの上限(開発者は列権限で name / short_desc / description を直接更新できるため、DBでも守る)。
-- 既存の行は検証しない(not valid)。以降の追加・更新には適用される
alter table public.apps drop constraint if exists apps_text_length;
alter table public.apps add constraint apps_text_length check (
  char_length(name) between 1 and 60
  and char_length(short_desc) <= 200
  and char_length(description) <= 4000
  and coalesce(array_length(screenshots, 1), 0) <= 8
) not valid;

-- アイコンとスクリーンショットのパスは、サーバー(画像の検査つきのAPI)だけが設定する
revoke update (icon_path, screenshots) on public.apps from authenticated;

-- ---------------------------------------------------------------- 直接の作成を禁止(API経由のみ)
drop policy if exists apps_insert_owner on public.apps;
drop policy if exists releases_insert_owner on public.app_releases;

-- 開発者が Storage(app-media)へ直接アップロードする経路も閉じる(画像は API が検査して保存する)
drop policy if exists app_media_write_owner on storage.objects;
drop policy if exists app_media_update_owner on storage.objects;
drop policy if exists app_media_delete_owner on storage.objects;
create policy app_media_write_admin on storage.objects for insert to authenticated
  with check (bucket_id = 'app-media' and public.is_admin());
create policy app_media_update_admin on storage.objects for update to authenticated
  using (bucket_id = 'app-media' and public.is_admin());
create policy app_media_delete_admin on storage.objects for delete to authenticated
  using (bucket_id = 'app-media' and public.is_admin());
