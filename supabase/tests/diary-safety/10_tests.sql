\set ON_ERROR_STOP on
\set QUIET on

-- 期待値と一致しなければ例外にするだけの検証用関数
create or replace function public.expect(label text, actual bigint, expected bigint) returns void language plpgsql as $$
begin
  if actual is distinct from expected then
    raise exception 'FAIL [%]: 期待 % / 実際 %', label, expected, actual;
  end if;
  raise notice 'PASS [%]', label;
end $$;

-- 例外が出ることを期待する(メッセージの一部でも確認する)
create or replace function public.expect_error(label text, stmt text, must_contain text default '') returns void language plpgsql as $$
begin
  begin
    execute stmt;
  exception when others then
    if must_contain <> '' and position(must_contain in sqlerrm) = 0 then
      raise exception 'FAIL [%]: 想定と違うエラー: %', label, sqlerrm;
    end if;
    raise notice 'PASS [%] (エラー: %)', label, sqlerrm;
    return;
  end;
  raise exception 'FAIL [%]: エラーになるはずが成功した', label;
end $$;

grant execute on function public.expect(text, bigint, bigint) to public;
grant execute on function public.expect_error(text, text, text) to public;

-- ---------------------------------------------------------------- 準備(RLSを迂回できるpostgresで投入)
insert into auth.users (id, email) values
  ('a0000000-0000-0000-0000-00000000000a', 'a@example.com'),
  ('b0000000-0000-0000-0000-00000000000b', 'b@example.com'),
  ('c0000000-0000-0000-0000-00000000000c', 'c@example.com'),
  ('d0000000-0000-0000-0000-00000000000d', 'd@example.com'),
  ('e0000000-0000-0000-0000-00000000000e', 'admin@example.com');
update public.profiles set role = 'admin' where id = 'e0000000-0000-0000-0000-00000000000e';

-- AとBは友達
insert into public.diary_friendships (requester_id, addressee_id, status)
values ('a0000000-0000-0000-0000-00000000000a', 'b0000000-0000-0000-0000-00000000000b', 'accepted');

-- Aの投稿: 非公開 / 友達 / 公開(それぞれ写真つき)
insert into public.diary_entries (id, user_id, body, visibility, photo_path) values
  ('11111111-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-00000000000a', 'private', 'private', 'diary/a0000000-0000-0000-0000-00000000000a/priv.jpg'),
  ('11111111-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-00000000000a', 'friends', 'friends', 'diary/a0000000-0000-0000-0000-00000000000a/fr.jpg'),
  ('11111111-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-00000000000a', 'public',  'public',  'diary/a0000000-0000-0000-0000-00000000000a/pub.jpg');
insert into storage.objects (bucket_id, name) values
  ('diary-media', 'diary/a0000000-0000-0000-0000-00000000000a/priv.jpg'),
  ('diary-media', 'diary/a0000000-0000-0000-0000-00000000000a/fr.jpg'),
  ('diary-media', 'diary/a0000000-0000-0000-0000-00000000000a/pub.jpg'),
  ('diary-media', 'diary/a0000000-0000-0000-0000-00000000000a/orphan.jpg');

-- ---------------------------------------------------------------- 1. 投稿の閲覧
set role authenticated;
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
select expect('友達Bは 友達+公開 の2件が見える', (select count(*) from public.diary_entries), 2);
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-00000000000c', false);
select expect('他人Cは 公開 の1件だけ見える', (select count(*) from public.diary_entries), 1);
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-00000000000a', false);
select expect('本人Aは 3件とも見える', (select count(*) from public.diary_entries), 3);
reset role;
set role anon;
select set_config('request.jwt.claim.sub', '', false);
select expect('未ログイン(anon)は公開投稿も見えない', (select count(*) from public.diary_entries), 0);
reset role;

-- ---------------------------------------------------------------- 2. 写真の閲覧
set role authenticated;
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
select expect('友達Bは 友達+公開 の写真が読める(非公開は不可)',
  (select count(*) from storage.objects where bucket_id = 'diary-media'), 2);
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-00000000000c', false);
select expect('他人Cは 公開の写真だけ読める',
  (select count(*) from storage.objects where bucket_id = 'diary-media'), 1);
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-00000000000a', false);
select expect('本人Aは 自分の写真を全部読める(投稿に付いていない分も)',
  (select count(*) from storage.objects where bucket_id = 'diary-media'), 4);

-- 抜け道: 攻撃者Dが、Aの非公開の写真パスを指す投稿を作って読み出そうとする
select set_config('request.jwt.claim.sub', 'd0000000-0000-0000-0000-00000000000d', false);
select expect_error('他人の写真パスを指す投稿は作れない(制約)',
  $$insert into public.diary_entries (user_id, body, visibility, photo_path)
    values ('d0000000-0000-0000-0000-00000000000d', 'steal', 'public', 'diary/a0000000-0000-0000-0000-00000000000a/priv.jpg')$$,
  'diary_entries_photo_path_owner');
select expect('攻撃者Dは 公開の写真だけ(非公開は読めない)',
  (select count(*) from storage.objects where bucket_id = 'diary-media' and name like '%priv.jpg'), 0);
reset role;

-- ---------------------------------------------------------------- 3. ブロック
set role authenticated;
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-00000000000c', false);
select public.diary_block_user('a0000000-0000-0000-0000-00000000000a');
select expect('CがAをブロックすると、Cから公開投稿が見えなくなる', (select count(*) from public.diary_entries), 0);
select expect('ブロックした側は自分のブロック一覧を読める', (select count(*) from public.diary_blocks), 1);
select expect('CがAをブロックすると、Aの公開写真も読めなくなる',
  (select count(*) from storage.objects where bucket_id = 'diary-media'), 0);
select expect_error('自分自身はブロックできない',
  $$select public.diary_block_user('c0000000-0000-0000-0000-00000000000c')$$, '自分自身');

select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-00000000000a', false);
select expect('ブロックされた側Aは ブロック一覧に何も見えない(知らされない)', (select count(*) from public.diary_blocks), 0);
select expect_error('ブロックされた相手に友達申請は送れない',
  $$insert into public.diary_friendships (requester_id, addressee_id) values ('a0000000-0000-0000-0000-00000000000a', 'c0000000-0000-0000-0000-00000000000c')$$,
  'row-level security');

-- 友達Bがブロックすると、友達関係も消える
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
select public.diary_block_user('a0000000-0000-0000-0000-00000000000a');
select expect('BがAをブロックすると、Bから友達投稿も見えない', (select count(*) from public.diary_entries), 0);
select expect('ブロックで友達関係が解除される', (select count(*) from public.diary_friendships), 0);
-- ブロックの解除で、再び公開投稿が見える
delete from public.diary_blocks where blocked_id = 'a0000000-0000-0000-0000-00000000000a';
select expect('ブロックを解除すると公開投稿が再び見える(友達は解除済みなので公開のみ)', (select count(*) from public.diary_entries), 1);
reset role;

-- ---------------------------------------------------------------- 4. 報告
set role authenticated;
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
select public.diary_report_entry('11111111-0000-0000-0000-000000000003', 'spam', '宣伝ばかり');
select expect('公開投稿を報告できる', (select count(*) from public.diary_reports), 1);
select public.diary_report_entry('11111111-0000-0000-0000-000000000003', 'spam', '二重');
select expect('同じ投稿を重ねて報告しても増えない', (select count(*) from public.diary_reports), 1);
select expect_error('見えない投稿(非公開)は報告できない',
  $$select public.diary_report_entry('11111111-0000-0000-0000-000000000001', 'other')$$, '見つかりません');
select expect_error('不正な理由は拒否される',
  $$select public.diary_report_entry('11111111-0000-0000-0000-000000000003', 'nonsense')$$, 'check');
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-00000000000a', false);
select expect_error('自分の投稿は報告できない',
  $$select public.diary_report_entry('11111111-0000-0000-0000-000000000003', 'other')$$, '自分の投稿');
select expect('他人の報告は読めない', (select count(*) from public.diary_reports), 0);
reset role;
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
set role authenticated;
update public.diary_reports set status = 'dismissed';
reset role;
select expect('報告者本人でも状態を書き換えられない(open のまま)', (select count(*) from public.diary_reports where status = 'open'), 1);
select expect('報告には本文の控えが残る', (select count(*) from public.diary_reports where entry_body = 'public'), 1);

-- 投稿を消しても報告の証跡は残る
delete from public.diary_entries where id = '11111111-0000-0000-0000-000000000003';
select expect('投稿が削除されても報告は残る', (select count(*) from public.diary_reports), 1);

-- ---------------------------------------------------------------- 5. アカウント削除
set role authenticated;
select set_config('request.jwt.claim.sub', 'e0000000-0000-0000-0000-00000000000e', false);
select expect_error('管理者はアプリから削除できない',
  $$select public.diary_delete_my_account()$$, '管理者・開発者');
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-00000000000a', false);
select public.diary_delete_my_account();
reset role;
select expect('Aのアカウントが削除される', (select count(*) from auth.users where id = 'a0000000-0000-0000-0000-00000000000a'), 0);
select expect('Aのプロフィールも消える', (select count(*) from public.profiles where id = 'a0000000-0000-0000-0000-00000000000a'), 0);
select expect('Aの投稿が全部消える', (select count(*) from public.diary_entries where user_id = 'a0000000-0000-0000-0000-00000000000a'), 0);
select expect('Aが関わる友達関係・ブロックが消える',
  (select count(*) from public.diary_friendships) + (select count(*) from public.diary_blocks), 0);
select expect('報告は残り、報告対象ユーザーはnullになる',
  (select count(*) from public.diary_reports where reported_user_id is null), 1);
select expect('他のユーザーは消えない', (select count(*) from auth.users), 4);

set role anon;
select expect_error('未ログインは削除関数を実行できない', $$select public.diary_delete_my_account()$$, 'permission denied');
reset role;

\echo ====== 全部PASS ======
