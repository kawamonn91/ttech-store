-- ひとこと日記: 安全対策(ブロック・報告・アカウント削除)と、写真・投稿の閲覧ポリシーの厳格化。
-- 20260922000000_diary.sql の続き。同じSupabaseプロジェクト(T-tech Storeと共通のアカウント)に適用する。
--
-- 変更点:
--   1. ブロック(diary_blocks): ブロックした相手・された相手の投稿は、互いに見えなくなる。友達関係も解除される。
--   2. 報告(diary_reports): 不適切な投稿を運営に報告できる。投稿が消されても証跡が残るよう、本文を控えて保存する。
--   3. アカウント削除(diary_delete_my_account): 本人がアプリから、アカウントと全データを削除できる。
--   4. 写真の閲覧: これまで「ログイン済みなら誰でも読める」だったのを、「自分の写真」または
--      「自分が見られる投稿に付いている写真」だけに絞る。
--   5. 投稿の閲覧: 未ログイン(anon)では公開投稿も読めないようにする。

-- ---------------------------------------------------------------- ブロック
create table public.diary_blocks (
  blocker_id uuid not null references public.profiles (id) on delete cascade,
  blocked_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  constraint diary_blocks_not_self check (blocker_id <> blocked_id)
);

alter table public.diary_blocks enable row level security;

-- ブロックした側だけが、自分のブロック一覧を見られる(された側には知らせない)
create policy diary_blocks_select on public.diary_blocks for select
  using (blocker_id = auth.uid());
create policy diary_blocks_insert on public.diary_blocks for insert
  with check (blocker_id = auth.uid());
create policy diary_blocks_delete on public.diary_blocks for delete
  using (blocker_id = auth.uid());

-- 「aとbの間にブロックがあるか(どちら向きでも)」。ブロックされた側は一覧を読めないので、
-- 所有者権限(security definer)で判定する。API(rpc)から他人同士の関係を探られないよう、
-- 呼んだ本人(auth.uid())がaかbのどちらかである場合にだけ答える。
create or replace function public.diary_blocked_between(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select auth.uid() in (a, b)
    and exists (
      select 1 from public.diary_blocks
      where (blocker_id = a and blocked_id = b) or (blocker_id = b and blocked_id = a)
    );
$$;

-- ブロックする。同時に、その相手との友達関係(申請中も含む)を解除する。
create or replace function public.diary_block_user(p_target uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'ログインしてください';
  end if;
  if p_target = auth.uid() then
    raise exception '自分自身はブロックできません';
  end if;
  if not exists (select 1 from public.profiles where id = p_target) then
    raise exception '相手が見つかりません';
  end if;

  insert into public.diary_blocks (blocker_id, blocked_id)
  values (auth.uid(), p_target)
  on conflict do nothing;

  delete from public.diary_friendships
  where (requester_id = auth.uid() and addressee_id = p_target)
     or (requester_id = p_target and addressee_id = auth.uid());
end;
$$;

-- ---------------------------------------------------------------- 投稿の閲覧(ブロックを反映)
drop policy diary_entries_select on public.diary_entries;

-- ログイン済みユーザーだけに適用する(未ログインのanonには、公開投稿も含めて何も見せない)
create policy diary_entries_select on public.diary_entries for select
  to authenticated
  using (
    user_id = auth.uid()
    or (
      not public.diary_blocked_between(auth.uid(), user_id)
      and (
        auth.uid() = any (mentioned_user_ids)
        or visibility = 'public'
        or (visibility = 'friends' and public.diary_are_friends(auth.uid(), user_id))
      )
    )
  );

-- ブロックしている相手・されている相手には、友達申請も送れない
drop policy diary_friendships_insert on public.diary_friendships;

create policy diary_friendships_insert on public.diary_friendships for insert
  to authenticated
  with check (
    requester_id = auth.uid()
    and not public.diary_blocked_between(requester_id, addressee_id)
  );

-- ---------------------------------------------------------------- 報告
create table public.diary_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  -- 投稿が削除されても報告の証跡を残すため、投稿へは外部キーを張らず、本文の控えを持つ
  entry_id uuid not null,
  reported_user_id uuid references public.profiles (id) on delete set null,
  reason text not null check (reason in ('spam', 'harassment', 'inappropriate', 'privacy', 'other')),
  detail text check (detail is null or char_length(detail) <= 500),
  entry_body text,
  status text not null default 'open' check (status in ('open', 'reviewed', 'actioned', 'dismissed')),
  created_at timestamptz not null default now(),
  unique (reporter_id, entry_id)
);

create index diary_reports_status_idx on public.diary_reports (status, created_at desc);

alter table public.diary_reports enable row level security;

-- 利用者は、自分が送った報告を書き込む・読むことだけができる(更新・削除は運営のみ)
create policy diary_reports_insert on public.diary_reports for insert
  with check (reporter_id = auth.uid() and status = 'open');
create policy diary_reports_select on public.diary_reports for select
  using (reporter_id = auth.uid());

-- 報告する。security invoker(既定)なので、投稿の取得には閲覧ポリシーがそのまま効き、
-- 自分が見られない投稿は報告できない。
create or replace function public.diary_report_entry(p_entry_id uuid, p_reason text, p_detail text default null)
returns void
language plpgsql
set search_path = public
as $$
declare
  v_owner uuid;
  v_body text;
begin
  if auth.uid() is null then
    raise exception 'ログインしてください';
  end if;

  select user_id, body into v_owner, v_body
  from public.diary_entries
  where id = p_entry_id;

  if v_owner is null then
    raise exception '報告する投稿が見つかりません';
  end if;
  if v_owner = auth.uid() then
    raise exception '自分の投稿は報告できません';
  end if;

  insert into public.diary_reports (reporter_id, entry_id, reported_user_id, reason, detail, entry_body)
  values (auth.uid(), p_entry_id, v_owner, p_reason, nullif(trim(p_detail), ''), v_body)
  on conflict (reporter_id, entry_id) do nothing;
end;
$$;

-- ---------------------------------------------------------------- 写真の閲覧を絞る
-- 旧: ログイン済みなら誰でも(パスを知っていれば非公開の投稿の写真も)読めた。
-- 新: 自分のフォルダの写真、または「自分が閲覧できる投稿(=RLSを通る投稿)」に付いている写真だけ。
-- 投稿の photo_path は本人が書けるため、他人のフォルダの写真を指す投稿を作って読み出すことが
-- できないよう、「写真のフォルダが投稿者本人のもの」であることも条件にする。
drop policy if exists diary_media_read on storage.objects;

create policy diary_media_read on storage.objects for select
  using (
    bucket_id = 'diary-media'
    and auth.uid() is not null
    and (
      (storage.foldername(name))[2] = auth.uid()::text
      or exists (
        select 1 from public.diary_entries e
        where e.photo_path = name
          and (storage.foldername(name))[2] = e.user_id::text
      )
    )
  );

-- 投稿の写真パスは、必ず投稿者本人のフォルダの中を指す(新規・更新される行に適用)
alter table public.diary_entries
  add constraint diary_entries_photo_path_owner
  check (photo_path is null or photo_path like 'diary/' || user_id::text || '/%')
  not valid;

-- ---------------------------------------------------------------- アカウント削除
-- 本人のアカウントと、それに紐づくすべてのデータ(投稿・友達関係・ブロック・報告など)を削除する。
-- profiles・diary_* は auth.users への外部キーの on delete cascade で連鎖して消える。
-- 写真ファイル(Storage)は、直接のSQLでは消せないため、アプリが先にStorage APIで消してから呼ぶ。
-- T-tech Store と共通のアカウントなので、管理者・開発者は誤って消さないよう対象外にする。
create or replace function public.diary_delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_role public.user_role;
begin
  if auth.uid() is null then
    raise exception 'ログインしてください';
  end if;

  select role into v_role from public.profiles where id = auth.uid();
  if v_role is distinct from 'user' then
    raise exception '管理者・開発者のアカウントは、アプリからは削除できません';
  end if;

  delete from auth.users where id = auth.uid();
end;
$$;

-- ---------------------------------------------------------------- 実行権限
revoke all on function public.diary_blocked_between(uuid, uuid) from public, anon;
revoke all on function public.diary_block_user(uuid) from public, anon;
revoke all on function public.diary_report_entry(uuid, text, text) from public, anon;
revoke all on function public.diary_delete_my_account() from public, anon;

grant execute on function public.diary_blocked_between(uuid, uuid) to authenticated;
grant execute on function public.diary_block_user(uuid) to authenticated;
grant execute on function public.diary_report_entry(uuid, text, text) to authenticated;
grant execute on function public.diary_delete_my_account() to authenticated;
