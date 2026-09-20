-- T-tech Store: 初期スキーマ + RLS
-- 方針:
--   * 公開(published)のアプリ/リリースは誰でも読める
--   * 書き込みは所有者(承認済み開発者)または admin のみ
--   * ダウンロード記録・スキャン結果など信頼が必要な書き込みは service_role(サーバーAPI)経由のみ
--   * 署名鍵の固定(apps.signing_cert_sha256)はトリガーでDB側でも強制する

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- enums
create type public.user_role as enum ('user', 'developer', 'admin');
create type public.developer_status as enum ('pending', 'approved', 'suspended');
create type public.app_status as enum ('draft', 'pending', 'published', 'suspended');
create type public.release_status as enum ('uploaded', 'scanned', 'approved', 'rejected', 'published');
create type public.review_status as enum ('visible', 'hidden');

-- ---------------------------------------------------------------- profiles
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null default '',
  role public.user_role not null default 'user',
  created_at timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, display_name)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name', split_part(new.email, '@', 1), '')
  );
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.profiles where id = auth.uid() and role = 'admin');
$$;

-- ---------------------------------------------------------------- developers
create table public.developers (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  name text not null,
  contact_email text not null,
  website text,
  status public.developer_status not null default 'pending',
  verified_at timestamptz,
  created_at timestamptz not null default now()
);

create or replace function public.is_approved_developer()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.developers where user_id = auth.uid() and status = 'approved');
$$;

-- ---------------------------------------------------------------- categories
create table public.categories (
  id smallint generated always as identity primary key,
  slug text not null unique,
  name text not null,
  sort_order smallint not null default 0
);

-- ---------------------------------------------------------------- apps
create table public.apps (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique check (slug ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
  package_name text not null unique check (package_name ~ '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$'),
  developer_id uuid not null references public.developers (user_id),
  name text not null,
  short_desc text not null default '',
  description text not null default '',
  category_id smallint references public.categories (id),
  icon_path text,
  screenshots text[] not null default '{}',
  status public.app_status not null default 'draft',
  featured boolean not null default false,
  price_yen integer not null default 0 check (price_yen >= 0), -- 課金は将来用。MVPは常に0
  -- 最初に承認されたリリースの署名証明書(SHA-256, 小文字hex)。以降のリリースはこれと一致必須
  signing_cert_sha256 text,
  download_count bigint not null default 0,
  rating_avg numeric(3, 2) not null default 0,
  rating_count integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index apps_status_idx on public.apps (status);
create index apps_category_idx on public.apps (category_id);
create index apps_search_idx on public.apps using gin (to_tsvector('simple', name || ' ' || short_desc));

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;
create trigger apps_touch before update on public.apps
  for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------- app_releases
create table public.app_releases (
  id uuid primary key default gen_random_uuid(),
  app_id uuid not null references public.apps (id) on delete cascade,
  -- アップロード直後は未確定。APK検査(GitHub Actions)が AndroidManifest から読み取って埋める
  version_name text,
  version_code integer check (version_code > 0),
  apk_key text not null,                       -- R2 のオブジェクトキー
  apk_size bigint,
  sha256 text check (sha256 ~ '^[0-9a-f]{64}$'),
  signing_cert_sha256 text check (signing_cert_sha256 ~ '^[0-9a-f]{64}$'),
  min_sdk integer,
  target_sdk integer,
  permissions text[] not null default '{}',
  release_notes text not null default '',
  status public.release_status not null default 'uploaded',
  scan_result jsonb,
  reviewed_by uuid references public.profiles (id),
  published_at timestamptz,
  created_at timestamptz not null default now(),
  unique (app_id, version_code)
);
create index app_releases_app_idx on public.app_releases (app_id, version_code desc);

-- 承認/公開時に、署名鍵を固定・一致検証する(なりすまし更新の防止)
create or replace function public.enforce_release_signing()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  locked text;
begin
  if new.status in ('approved', 'published') then
    if new.sha256 is null or new.signing_cert_sha256 is null or new.version_code is null then
      raise exception 'APK検査が完了していないリリースは承認できません';
    end if;
    select signing_cert_sha256 into locked from public.apps where id = new.app_id for update;
    if locked is null then
      update public.apps set signing_cert_sha256 = new.signing_cert_sha256 where id = new.app_id;
    elsif locked <> new.signing_cert_sha256 then
      raise exception '署名鍵が既存のアプリと一致しません (expected %, got %)', locked, new.signing_cert_sha256;
    end if;
    if new.status = 'published' and new.published_at is null then
      new.published_at = now();
    end if;
  end if;
  return new;
end;
$$;
create trigger app_releases_signing
  before insert or update of status, sha256, signing_cert_sha256, version_code on public.app_releases
  for each row execute function public.enforce_release_signing();

-- 各アプリの最新の公開リリース
create view public.latest_releases as
select distinct on (r.app_id) r.*
from public.app_releases r
join public.apps a on a.id = r.app_id
where r.status = 'published' and a.status = 'published'
order by r.app_id, r.version_code desc;

-- ---------------------------------------------------------------- downloads
create table public.downloads (
  id bigint generated always as identity primary key,
  release_id uuid not null references public.app_releases (id) on delete cascade,
  user_id uuid references public.profiles (id) on delete set null,
  device_hash text not null,
  created_at timestamptz not null default now()
);
create index downloads_dedupe_idx on public.downloads (release_id, device_hash, created_at desc);

-- 24時間以内の同一端末・同一リリースは重複カウントしない。service_role のみ実行可
create or replace function public.record_download(p_release_id uuid, p_user_id uuid, p_device_hash text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_app_id uuid;
begin
  if exists (
    select 1 from public.downloads
    where release_id = p_release_id and device_hash = p_device_hash and created_at > now() - interval '24 hours'
  ) then
    return false;
  end if;
  insert into public.downloads (release_id, user_id, device_hash) values (p_release_id, p_user_id, p_device_hash);
  select app_id into v_app_id from public.app_releases where id = p_release_id;
  update public.apps set download_count = download_count + 1 where id = v_app_id;
  return true;
end;
$$;
revoke all on function public.record_download(uuid, uuid, text) from public, anon, authenticated;
grant execute on function public.record_download(uuid, uuid, text) to service_role;

-- ---------------------------------------------------------------- reviews (Phase 2 で利用)
create table public.reviews (
  id uuid primary key default gen_random_uuid(),
  app_id uuid not null references public.apps (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  rating smallint not null check (rating between 1 and 5),
  body text not null default '' check (char_length(body) <= 2000),
  version_code integer,
  status public.review_status not null default 'visible',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (app_id, user_id)
);
create trigger reviews_touch before update on public.reviews
  for each row execute function public.touch_updated_at();

create table public.review_reports (
  id bigint generated always as identity primary key,
  review_id uuid not null references public.reviews (id) on delete cascade,
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  reason text not null check (char_length(reason) <= 500),
  resolved boolean not null default false,
  created_at timestamptz not null default now(),
  unique (review_id, reporter_id)
);

create or replace function public.refresh_app_rating()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_app uuid := coalesce(new.app_id, old.app_id);
begin
  update public.apps a
  set rating_avg = coalesce((select round(avg(rating)::numeric, 2) from public.reviews where app_id = v_app and status = 'visible'), 0),
      rating_count = (select count(*) from public.reviews where app_id = v_app and status = 'visible')
  where a.id = v_app;
  return null;
end;
$$;
create trigger reviews_rating after insert or update or delete on public.reviews
  for each row execute function public.refresh_app_rating();

-- ---------------------------------------------------------------- RLS
alter table public.profiles enable row level security;
alter table public.developers enable row level security;
alter table public.categories enable row level security;
alter table public.apps enable row level security;
alter table public.app_releases enable row level security;
alter table public.downloads enable row level security;
alter table public.reviews enable row level security;
alter table public.review_reports enable row level security;

-- profiles: 本人と admin が閲覧。本人は display_name のみ更新可(role の自己昇格を防ぐ)
create policy profiles_select on public.profiles for select
  using (id = auth.uid() or public.is_admin());
create policy profiles_update_self on public.profiles for update
  using (id = auth.uid()) with check (id = auth.uid());
revoke update on public.profiles from authenticated;
grant update (display_name) on public.profiles to authenticated;
create policy profiles_admin_all on public.profiles for all
  using (public.is_admin()) with check (public.is_admin());

-- developers: 本人は申請(pending)・閲覧のみ。承認は admin
create policy developers_select on public.developers for select
  using (user_id = auth.uid() or public.is_admin());
-- 承認済み開発者の名前は公開ページに表示する。連絡先メール(contact_email)は
-- 列権限で API から読めないようにし、管理コンソール(service_role)だけが読む
create policy developers_select_public on public.developers for select
  using (status = 'approved');
revoke select on public.developers from anon, authenticated;
grant select (user_id, name, website, status, verified_at, created_at) on public.developers to anon, authenticated;
create policy developers_apply on public.developers for insert
  with check (user_id = auth.uid() and status = 'pending');
create policy developers_admin_all on public.developers for all
  using (public.is_admin()) with check (public.is_admin());

-- categories: 誰でも閲覧、admin のみ編集
create policy categories_select on public.categories for select using (true);
create policy categories_admin_all on public.categories for all
  using (public.is_admin()) with check (public.is_admin());

-- apps
create policy apps_select_public on public.apps for select using (status = 'published');
create policy apps_select_owner on public.apps for select using (developer_id = auth.uid());
create policy apps_insert_owner on public.apps for insert
  with check (developer_id = auth.uid() and public.is_approved_developer() and status in ('draft', 'pending'));
create policy apps_update_owner on public.apps for update
  using (developer_id = auth.uid() and status in ('draft', 'pending', 'published'))
  with check (developer_id = auth.uid() and status in ('draft', 'pending', 'published'));
create policy apps_admin_all on public.apps for all
  using (public.is_admin()) with check (public.is_admin());
-- 開発者が触れてはいけない列は列権限で保護する
revoke update on public.apps from authenticated;
grant update (name, short_desc, description, category_id, icon_path, screenshots) on public.apps to authenticated;
-- (admin の更新はサーバー側 service_role 経由。管理画面は /api/admin/* を使う)

-- app_releases: 公開分は誰でも閲覧。所有者は自分のアプリの分を閲覧・アップロード(uploaded のみ)
create policy releases_select_public on public.app_releases for select
  using (status = 'published' and exists (select 1 from public.apps a where a.id = app_id and a.status = 'published'));
create policy releases_select_owner on public.app_releases for select
  using (exists (select 1 from public.apps a where a.id = app_id and a.developer_id = auth.uid()));
create policy releases_insert_owner on public.app_releases for insert
  with check (status = 'uploaded' and exists (select 1 from public.apps a where a.id = app_id and a.developer_id = auth.uid()));
create policy releases_admin_all on public.app_releases for all
  using (public.is_admin()) with check (public.is_admin());

-- downloads: クライアントからは一切アクセス不可(ポリシー無し)。record_download() 経由のみ

-- reviews: 表示中は誰でも閲覧。投稿・更新は本人
create policy reviews_select on public.reviews for select
  using (status = 'visible' or user_id = auth.uid() or public.is_admin());
create policy reviews_insert_self on public.reviews for insert
  with check (user_id = auth.uid() and status = 'visible');
create policy reviews_update_self on public.reviews for update
  using (user_id = auth.uid()) with check (user_id = auth.uid() and status = 'visible');
create policy reviews_delete_self on public.reviews for delete using (user_id = auth.uid());
create policy reviews_admin_all on public.reviews for all
  using (public.is_admin()) with check (public.is_admin());

create policy review_reports_insert on public.review_reports for insert
  with check (reporter_id = auth.uid());
create policy review_reports_admin_all on public.review_reports for all
  using (public.is_admin()) with check (public.is_admin());

-- 公開ビュー・関数への権限
grant select on public.latest_releases to anon, authenticated;

-- ---------------------------------------------------------------- Storage (アイコン/スクショ)
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('app-media', 'app-media', true, 5242880, array['image/png', 'image/jpeg', 'image/webp'])
on conflict (id) do nothing;

create policy app_media_write_owner on storage.objects for insert to authenticated
  with check (
    bucket_id = 'app-media'
    and (
      public.is_admin()
      or exists (
        select 1 from public.apps a
        where a.id::text = (storage.foldername(name))[1] and a.developer_id = auth.uid()
      )
    )
  );
create policy app_media_update_owner on storage.objects for update to authenticated
  using (
    bucket_id = 'app-media'
    and (
      public.is_admin()
      or exists (
        select 1 from public.apps a
        where a.id::text = (storage.foldername(name))[1] and a.developer_id = auth.uid()
      )
    )
  );
create policy app_media_delete_owner on storage.objects for delete to authenticated
  using (
    bucket_id = 'app-media'
    and (
      public.is_admin()
      or exists (
        select 1 from public.apps a
        where a.id::text = (storage.foldername(name))[1] and a.developer_id = auth.uid()
      )
    )
  );
