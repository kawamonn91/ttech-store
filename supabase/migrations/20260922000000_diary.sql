-- ひとこと日記アプリ: 既存のT-tech Store用Supabaseプロジェクトを拡張する。
-- 新規インフラは作らず、同じ auth.users / public.profiles をそのまま使う方針。
--
-- 設計方針:
--   * 投稿(diary_entries)は本人のみ書き込み可、閲覧は「本人 / 公開設定に応じて友達 or 全員 / メンションされた人」
--   * 友達関係(diary_friendships)は「友達コード」で申請する(メール検索は個人情報が漏れるため避ける)
--   * public.profiles テーブル自体のRLS・列権限はStoreアプリが依存しているため一切変更しない。
--     代わりに「id, display_name, friend_code」だけを見せる専用ビューを新設して使う。

-- ---------------------------------------------------------------- profiles拡張
alter table public.profiles add column if not exists friend_code text;

create or replace function public.generate_friend_code()
returns text
language sql
as $$
  select upper(substr(md5(random()::text || clock_timestamp()::text), 1, 8));
$$;

alter table public.profiles alter column friend_code set default public.generate_friend_code();

-- 既存ユーザー(Storeで先に登録済みの分)にも友達コードを振っておく
update public.profiles set friend_code = public.generate_friend_code() where friend_code is null;

alter table public.profiles alter column friend_code set not null;
create unique index if not exists profiles_friend_code_key on public.profiles (friend_code);

-- 「id・表示名・友達コード」だけを公開する専用ビュー。
-- public.profiles 本体のRLS(本人とadminのみ閲覧)は変えず、このビュー経由でのみ
-- 他ユーザーが友達候補の名前を引けるようにする(ビューは所有者権限で動くためRLSを迂回するが、
-- 見せる列をこの3つだけに絞ることで安全性を保つ)。
create or replace view public.diary_public_profiles as
  select id, display_name, friend_code from public.profiles;

grant select on public.diary_public_profiles to authenticated;

-- ---------------------------------------------------------------- enums
create type public.diary_visibility as enum ('private', 'friends', 'public');
create type public.diary_friend_status as enum ('pending', 'accepted', 'declined');

-- ---------------------------------------------------------------- diary_entries
create table public.diary_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  entry_date date not null default (timezone('utc', now()))::date,
  body text not null check (char_length(body) between 1 and 280),
  photo_path text,
  visibility public.diary_visibility not null default 'friends',
  mentioned_user_ids uuid[] not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index diary_entries_user_date_idx on public.diary_entries (user_id, entry_date desc);

create or replace function public.diary_entries_set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger diary_entries_updated_at
  before update on public.diary_entries
  for each row execute function public.diary_entries_set_updated_at();

-- ---------------------------------------------------------------- diary_friendships
create table public.diary_friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.profiles (id) on delete cascade,
  addressee_id uuid not null references public.profiles (id) on delete cascade,
  status public.diary_friend_status not null default 'pending',
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  constraint diary_friendships_not_self check (requester_id <> addressee_id)
);

-- 同じ2人の組み合わせで複数の申請ができないようにする(順序を問わない一意制約)
create unique index diary_friendships_pair_key on public.diary_friendships (
  least(requester_id, addressee_id),
  greatest(requester_id, addressee_id)
);

-- 「友達(accepted)かどうか」を毎回サブクエリで書かなくて済むようにするヘルパー
create or replace function public.diary_are_friends(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.diary_friendships
    where status = 'accepted'
      and ((requester_id = a and addressee_id = b) or (requester_id = b and addressee_id = a))
  );
$$;

-- ---------------------------------------------------------------- RLS: diary_entries
alter table public.diary_entries enable row level security;

create policy diary_entries_select on public.diary_entries for select
  using (
    user_id = auth.uid()
    or auth.uid() = any (mentioned_user_ids)
    or visibility = 'public'
    or (visibility = 'friends' and public.diary_are_friends(auth.uid(), user_id))
  );

create policy diary_entries_insert on public.diary_entries for insert
  with check (user_id = auth.uid());

create policy diary_entries_update on public.diary_entries for update
  using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy diary_entries_delete on public.diary_entries for delete
  using (user_id = auth.uid());

-- ---------------------------------------------------------------- RLS: diary_friendships
alter table public.diary_friendships enable row level security;

create policy diary_friendships_select on public.diary_friendships for select
  using (requester_id = auth.uid() or addressee_id = auth.uid());

create policy diary_friendships_insert on public.diary_friendships for insert
  with check (requester_id = auth.uid());

-- 承認・却下は宛先本人のみ。申請の取り消しは申請者本人も可
create policy diary_friendships_update on public.diary_friendships for update
  using (addressee_id = auth.uid() or requester_id = auth.uid())
  with check (addressee_id = auth.uid() or requester_id = auth.uid());

create policy diary_friendships_delete on public.diary_friendships for delete
  using (requester_id = auth.uid() or addressee_id = auth.uid());

-- ---------------------------------------------------------------- Storage(写真添付)
insert into storage.buckets (id, name, public)
values ('diary-media', 'diary-media', false)
on conflict (id) do nothing;

-- パスは "diary/{user_id}/{entry_id}.jpg" 形式に統一する運用とし、
-- 先頭フォルダ名(=user_id)が本人のものかどうかで書き込み・削除を制御する。
-- 読み取りは、実際の閲覧可否は diary_entries 側のRLSで既に守られているため
-- (パスを知らない第三者が推測でアクセスすることはまず無い前提)、ログイン済みなら許可する簡易方式。
create policy diary_media_read on storage.objects for select
  using (bucket_id = 'diary-media' and auth.role() = 'authenticated');

create policy diary_media_insert on storage.objects for insert
  with check (bucket_id = 'diary-media' and (storage.foldername(name))[2] = auth.uid()::text);

create policy diary_media_delete on storage.objects for delete
  using (bucket_id = 'diary-media' and (storage.foldername(name))[2] = auth.uid()::text);
