\set ON_ERROR_STOP on
\set QUIET on

create or replace function public.expect(label text, actual bigint, expected bigint) returns void language plpgsql as $$
begin
  if actual is distinct from expected then
    raise exception 'FAIL [%]: 期待 % / 実際 %', label, expected, actual;
  end if;
  raise notice 'PASS [%]', label;
end $$;

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

-- ---------------------------------------------------------------- 準備
insert into auth.users (id, email, raw_app_meta_data, last_sign_in_at) values
  ('a0000000-0000-0000-0000-00000000000a', 'alice@example.com', '{"provider":"google"}', now()),
  ('b0000000-0000-0000-0000-00000000000b', 'bob@example.com', '{"provider":"email"}', null),
  ('c0000000-0000-0000-0000-00000000000c', 'carol@example.com', '{}', null),
  ('e0000000-0000-0000-0000-00000000000e', 'admin@example.com', '{}', now());
update public.profiles set role = 'admin' where id = 'e0000000-0000-0000-0000-00000000000e';
update public.profiles set display_name = 'アリス' where id = 'a0000000-0000-0000-0000-00000000000a';

insert into public.developers (user_id, name, contact_email, status)
values ('e0000000-0000-0000-0000-00000000000e', 'T-tech', 'admin@example.com', 'approved');

-- 公開アプリと、管理者専用アプリ(どちらも公開状態・公開リリースあり)
insert into public.apps (id, slug, package_name, developer_id, name, status, admin_only) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'public-app', 'com.example.pub', 'e0000000-0000-0000-0000-00000000000e', '公開アプリ', 'published', false),
  ('aaaaaaaa-0000-0000-0000-000000000002', 'admin-app', 'com.example.adm', 'e0000000-0000-0000-0000-00000000000e', '管理アプリ', 'published', true);
insert into public.app_releases (app_id, version_name, version_code, apk_key, sha256, signing_cert_sha256, status) values
  ('aaaaaaaa-0000-0000-0000-000000000001', '1.0', 1, 'k1', repeat('a', 64), repeat('b', 64), 'published'),
  ('aaaaaaaa-0000-0000-0000-000000000002', '1.0', 1, 'k2', repeat('c', 64), repeat('d', 64), 'published');

insert into public.diary_entries (id, user_id, body, visibility) values
  ('11111111-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-00000000000a', 'Aliceの公開投稿', 'public'),
  ('11111111-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-00000000000b', 'Bobの公開投稿', 'public');

-- ---------------------------------------------------------------- 1. 管理者専用アプリは公開カタログに出ない
set role anon;
select expect('未ログインには公開アプリだけ見える', (select count(*) from public.apps), 1);
select expect('未ログインには公開リリースだけ見える', (select count(*) from public.app_releases), 1);
select expect('最新リリースのビューにも管理者専用アプリは出ない', (select count(*) from public.latest_releases), 1);
reset role;
set role authenticated;
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
select expect('一般ユーザーにも管理者専用アプリは見えない', (select count(*) from public.apps where slug = 'admin-app'), 0);
select set_config('request.jwt.claim.sub', 'e0000000-0000-0000-0000-00000000000e', false);
select expect('管理者本人には両方見える', (select count(*) from public.apps), 2);
select expect_error('開発者は admin_only を自分で変えられない(列権限)',
  $$update public.apps set admin_only = false where slug = 'admin-app'$$, 'permission denied');
reset role;

-- ---------------------------------------------------------------- 2. BAN
select expect('BANされていない利用者は false',
  (select case when public.is_user_banned('a0000000-0000-0000-0000-00000000000a') then 1 else 0 end), 0);
update public.profiles set banned_at = now(), ban_reason = '迷惑行為' where id = 'a0000000-0000-0000-0000-00000000000a';
select expect('BANされた利用者は true',
  (select case when public.is_user_banned('a0000000-0000-0000-0000-00000000000a') then 1 else 0 end), 1);

set role authenticated;
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
select expect('BANされた人の公開投稿は、他の人から見えなくなる', (select count(*) from public.diary_entries), 1);

select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-00000000000a', false);
select expect('BANされた本人は、自分の投稿も読めない', (select count(*) from public.diary_entries), 0);
select expect_error('BANされた本人は投稿できない',
  $$insert into public.diary_entries (user_id, body, visibility) values ('a0000000-0000-0000-0000-00000000000a', 'こっそり', 'public')$$,
  'row-level security');
select expect_error('BANされた本人はレビューを書けない',
  $$insert into public.reviews (app_id, user_id, rating, body) values ('aaaaaaaa-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-00000000000a', 1, 'x')$$,
  'row-level security');
select expect_error('BANされた本人は友達申請できない',
  $$insert into public.diary_friendships (requester_id, addressee_id) values ('a0000000-0000-0000-0000-00000000000a', 'b0000000-0000-0000-0000-00000000000b')$$,
  'row-level security');

-- BANされていない人は今まで通り書ける
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
insert into public.reviews (app_id, user_id, rating, body) values ('aaaaaaaa-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-00000000000b', 5, 'よい');
select expect('BANされていない人はレビューを書ける', (select count(*) from public.reviews), 1);
reset role;

-- BAN解除で元に戻る
update public.profiles set banned_at = null, ban_reason = null where id = 'a0000000-0000-0000-0000-00000000000a';
set role authenticated;
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
select expect('BAN解除で、投稿がまた見える', (select count(*) from public.diary_entries), 2);
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-00000000000a', false);
select expect('BAN解除で、本人も読める', (select count(*) from public.diary_entries), 2);
reset role;

-- ---------------------------------------------------------------- 3. 管理者向けユーザー一覧
set role authenticated;
select set_config('request.jwt.claim.sub', 'e0000000-0000-0000-0000-00000000000e', false);
select expect_error('管理者でも、アプリ(authenticated)からは一覧関数を呼べない(service_role 専用)',
  $$select * from public.admin_list_users()$$, 'permission denied');
reset role;
set role anon;
select expect_error('未ログインも呼べない', $$select * from public.admin_list_users()$$, 'permission denied');
reset role;

set role service_role;
select expect('一覧: 全ユーザーが返る', (select count(*) from public.admin_list_users()), 4);
select expect('一覧: total は全件数', (select max(total) from public.admin_list_users(null, false, 2, 0)), 4);
select expect('一覧: limit が効く', (select count(*) from public.admin_list_users(null, false, 2, 0)), 2);
select expect('一覧: メールで検索できる', (select count(*) from public.admin_list_users('alice')), 1);
select expect('一覧: 表示名で検索できる', (select count(*) from public.admin_list_users('アリス')), 1);
select expect('一覧: ワイルドカードは検索語として効かない', (select count(*) from public.admin_list_users('%')), 4);
select expect('一覧: 投稿数が数えられる', (select diary_count from public.admin_list_users('alice')), 1);
select expect('一覧: ログイン方法が分かる', (select count(*) from public.admin_list_users() where provider = 'google'), 1);
reset role;
update public.profiles set banned_at = now() where id = 'c0000000-0000-0000-0000-00000000000c';
set role service_role;
select expect('一覧: BAN中だけに絞れる', (select count(*) from public.admin_list_users(null, true)), 1);
reset role;
update public.profiles set banned_at = null where id = 'c0000000-0000-0000-0000-00000000000c';

-- ---------------------------------------------------------------- 4. 監査ログは service_role だけ
set role authenticated;
select set_config('request.jwt.claim.sub', 'e0000000-0000-0000-0000-00000000000e', false);
select expect_error('管理者でもアプリから監査ログは読めない', $$select * from public.admin_audit_log$$, 'permission denied');
reset role;
set role service_role;
insert into public.admin_audit_log (admin_id, action, target_type, target_id) values ('e0000000-0000-0000-0000-00000000000e', 'ban', 'user', 'x');
select expect('service_role は監査ログを書ける', (select count(*) from public.admin_audit_log), 1);
reset role;

-- ---------------------------------------------------------------- 5. メール通知
-- 未設定(URLとシークレットが無い)ときは、何も呼ばず、報告は普通に登録できる
set role authenticated;
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
select public.diary_report_entry('11111111-0000-0000-0000-000000000001', 'spam', '未設定のとき');
reset role;
select expect('通知が未設定なら外部呼び出しをしない', (select count(*) from net.calls), 0);
select expect('未設定でも報告は登録される', (select count(*) from public.diary_reports), 1);

insert into private.notify_settings (key, value) values ('url', 'https://example.test/api/internal/notify'), ('secret', 'shh');

set role authenticated;
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-00000000000c', false);
select public.diary_report_entry('11111111-0000-0000-0000-000000000001', 'harassment', '設定後');
reset role;
select expect('報告が入ると通知が1回呼ばれる', (select count(*) from net.calls), 1);
select expect('通知の種類は diary_report', (select count(*) from net.calls where body ->> 'kind' = 'diary_report'), 1);
select expect('通知には報告のIDが入る(本文は載せない)',
  (select count(*) from net.calls c join public.diary_reports r on r.id::text = c.body ->> 'id' where c.body ? 'id' and not c.body ? 'entry_body'), 1);
select expect('共有シークレットがヘッダーに入る', (select count(*) from net.calls where headers ->> 'x-notify-secret' = 'shh'), 1);
select expect('送信先URLが設定どおり', (select count(*) from net.calls where url = 'https://example.test/api/internal/notify'), 1);

-- 通知が失敗しても、報告の登録は止まらない
update net.behavior set fail = true;
set role authenticated;
select set_config('request.jwt.claim.sub', 'a0000000-0000-0000-0000-00000000000a', false);
select public.diary_report_entry('11111111-0000-0000-0000-000000000002', 'spam', '通知が落ちているとき');
reset role;
update net.behavior set fail = false;
select expect('通知が失敗しても報告は登録される', (select count(*) from public.diary_reports), 3);

-- レビュー通報・開発者申請
set role authenticated;
select set_config('request.jwt.claim.sub', 'c0000000-0000-0000-0000-00000000000c', false);
insert into public.review_reports (review_id, reporter_id, reason)
  select id, 'c0000000-0000-0000-0000-00000000000c', '不適切' from public.reviews limit 1;
insert into public.developers (user_id, name, contact_email) values ('c0000000-0000-0000-0000-00000000000c', 'Carol', 'carol@example.com');
reset role;
select expect('レビュー通報でも通知される', (select count(*) from net.calls where body ->> 'kind' = 'review_report'), 1);
select expect('開発者申請でも通知される(申請者のID)',
  (select count(*) from net.calls where body ->> 'kind' = 'developer_application' and body ->> 'id' = 'c0000000-0000-0000-0000-00000000000c'), 1);

-- 設定テーブルは一般ユーザーからは見えない(シークレットの保護)
set role authenticated;
select set_config('request.jwt.claim.sub', 'b0000000-0000-0000-0000-00000000000b', false);
select expect_error('authenticated は通知設定(シークレット)を読めない', $$select * from private.notify_settings$$, 'permission denied');
reset role;
set role anon;
select expect_error('anon も読めない', $$select * from private.notify_settings$$, 'permission denied');
reset role;

\echo ====== 全部PASS ======
