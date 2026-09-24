-- 管理アプリ(T-tech 管理)のための追加
--   * ユーザーのBAN(profiles.banned_at)。BANされた利用者は、有効なトークンを持っていても
--     RLSで読み書きが止まる(Auth側の ban_duration はログイン・更新を止める。こちらは残りの有効期間を塞ぐ)
--   * 管理者専用アプリ(apps.admin_only)。公開カタログ・ストアアプリの一覧・更新確認には出さない
--   * 管理操作の監査ログ
--   * 管理者向けのユーザー一覧関数
--   * 報告・開発者申請が入ったときの、運営へのメール通知の呼び出し(pg_net → /api/internal/notify)

-- ---------------------------------------------------------------- BAN
alter table public.profiles
  add column if not exists banned_at timestamptz,
  add column if not exists ban_reason text check (ban_reason is null or char_length(ban_reason) <= 500);

create or replace function public.is_user_banned(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.profiles where id = p_user and banned_at is not null);
$$;

create or replace function public.is_banned()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.is_user_banned(auth.uid());
$$;

grant execute on function public.is_user_banned(uuid), public.is_banned() to authenticated;
revoke execute on function public.is_user_banned(uuid), public.is_banned() from anon;

-- restrictive ポリシーは、既存の(permissive)ポリシーに「さらに」AND される。
-- 既存ポリシーを書き換えずに、BAN中の利用者を一括で止められる。
-- 自分がBANされている間は何も読み書きできず、BANされた人の投稿は他の人からも見えなくなる。
create policy diary_entries_not_banned on public.diary_entries as restrictive for all to authenticated
  using (not public.is_banned() and (user_id = auth.uid() or not public.is_user_banned(user_id)))
  with check (not public.is_banned());

create policy diary_friendships_not_banned on public.diary_friendships as restrictive for all to authenticated
  using (not public.is_banned())
  with check (not public.is_banned());

create policy diary_blocks_not_banned on public.diary_blocks as restrictive for all to authenticated
  using (not public.is_banned())
  with check (not public.is_banned());

create policy diary_reports_not_banned on public.diary_reports as restrictive for all to authenticated
  using (not public.is_banned())
  with check (not public.is_banned());

create policy reviews_not_banned on public.reviews as restrictive for all to authenticated
  using (not public.is_banned())
  with check (not public.is_banned());

create policy review_reports_not_banned on public.review_reports as restrictive for all to authenticated
  using (not public.is_banned())
  with check (not public.is_banned());

create policy diary_media_not_banned on storage.objects as restrictive for all to authenticated
  using (bucket_id <> 'diary-media' or not public.is_banned())
  with check (bucket_id <> 'diary-media' or not public.is_banned());

-- ---------------------------------------------------------------- 管理者専用アプリ
alter table public.apps add column if not exists admin_only boolean not null default false;

drop policy if exists apps_select_public on public.apps;
create policy apps_select_public on public.apps for select
  using (status = 'published' and not admin_only);

drop policy if exists releases_select_public on public.app_releases;
create policy releases_select_public on public.app_releases for select
  using (status = 'published' and exists (
    select 1 from public.apps a where a.id = app_id and a.status = 'published' and not a.admin_only
  ));

-- ビューは所有者権限で動く(RLSを通らない)ので、ビュー自身の条件で管理者専用アプリを除く
create or replace view public.latest_releases as
select distinct on (r.app_id) r.*
from public.app_releases r
join public.apps a on a.id = r.app_id
where r.status = 'published' and a.status = 'published' and not a.admin_only
order by r.app_id, r.version_code desc;

-- ---------------------------------------------------------------- 監査ログ(service_role のみ)
create table public.admin_audit_log (
  id bigint generated always as identity primary key,
  admin_id uuid references public.profiles (id) on delete set null,
  action text not null,
  target_type text not null,
  target_id text,
  detail jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index admin_audit_log_created_idx on public.admin_audit_log (created_at desc);
alter table public.admin_audit_log enable row level security;
revoke all on public.admin_audit_log from anon, authenticated;

-- ---------------------------------------------------------------- 管理者向けユーザー一覧
-- メールアドレスは auth.users にしか無いので、service_role だけが呼べる関数で返す
create or replace function public.admin_list_users(
  p_q text default null,
  p_banned_only boolean default false,
  p_limit integer default 30,
  p_offset integer default 0
)
returns table (
  id uuid,
  email text,
  display_name text,
  role public.user_role,
  provider text,
  created_at timestamptz,
  last_sign_in_at timestamptz,
  banned_at timestamptz,
  ban_reason text,
  diary_count bigint,
  review_count bigint,
  reports_against bigint,
  total bigint
)
language sql
stable
security definer
set search_path = public
as $$
  select
    p.id,
    u.email::text,
    p.display_name,
    p.role,
    coalesce(u.raw_app_meta_data ->> 'provider', 'email'),
    p.created_at,
    u.last_sign_in_at,
    p.banned_at,
    p.ban_reason,
    (select count(*) from public.diary_entries d where d.user_id = p.id),
    (select count(*) from public.reviews r where r.user_id = p.id),
    (select count(*) from public.diary_reports x where x.reported_user_id = p.id),
    count(*) over ()
  from public.profiles p
  join auth.users u on u.id = p.id
  where (not p_banned_only or p.banned_at is not null)
    and (
      coalesce(p_q, '') = ''
      or u.email ilike '%' || replace(replace(p_q, '%', ''), '_', '') || '%'
      or p.display_name ilike '%' || replace(replace(p_q, '%', ''), '_', '') || '%'
    )
  order by p.created_at desc
  limit least(greatest(p_limit, 1), 100)
  offset greatest(p_offset, 0);
$$;
revoke all on function public.admin_list_users(text, boolean, integer, integer) from public, anon, authenticated;
grant execute on function public.admin_list_users(text, boolean, integer, integer) to service_role;

-- ---------------------------------------------------------------- 運営へのメール通知
-- 報告などが入ると、DBから Webアプリの /api/internal/notify を呼ぶ(メールの送信はそこで行う)。
-- 送信先URLと共有シークレットは、コードに埋めずに private.notify_settings に入れる(README参照)。
-- 通知に失敗しても、報告そのものの登録は絶対に止めない(例外は握りつぶして警告だけ出す)。
do $$
begin
  create extension if not exists pg_net with schema extensions;
exception when others then
  raise warning 'pg_net を有効にできませんでした(メール通知は動きません): %', sqlerrm;
end $$;

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table if not exists private.notify_settings (
  key text primary key,
  value text not null
);
alter table private.notify_settings enable row level security;
revoke all on private.notify_settings from public, anon, authenticated;

create or replace function private.notify_admin(p_kind text, p_id text)
returns void
language plpgsql
security definer
set search_path = public, private, extensions
as $$
declare
  v_url text;
  v_secret text;
begin
  select value into v_url from private.notify_settings where key = 'url';
  select value into v_secret from private.notify_settings where key = 'secret';
  if v_url is null or v_secret is null then
    return; -- 未設定(=通知しない)
  end if;
  perform net.http_post(
    url := v_url,
    body := jsonb_build_object('kind', p_kind, 'id', p_id),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-notify-secret', v_secret),
    timeout_milliseconds := 5000
  );
exception when others then
  raise warning 'notify_admin に失敗しました: %', sqlerrm;
end;
$$;

create or replace function private.trg_notify_diary_report() returns trigger
language plpgsql security definer set search_path = public, private as $$
begin
  perform private.notify_admin('diary_report', new.id::text);
  return null;
end;
$$;

create or replace function private.trg_notify_review_report() returns trigger
language plpgsql security definer set search_path = public, private as $$
begin
  perform private.notify_admin('review_report', new.id::text);
  return null;
end;
$$;

create or replace function private.trg_notify_developer() returns trigger
language plpgsql security definer set search_path = public, private as $$
begin
  if new.status = 'pending' then
    perform private.notify_admin('developer_application', new.user_id::text);
  end if;
  return null;
end;
$$;

create trigger diary_reports_notify after insert on public.diary_reports
  for each row execute function private.trg_notify_diary_report();
create trigger review_reports_notify after insert on public.review_reports
  for each row execute function private.trg_notify_review_report();
create trigger developers_notify after insert on public.developers
  for each row execute function private.trg_notify_developer();
