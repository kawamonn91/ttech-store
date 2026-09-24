-- pgTAP: 管理機能(20260925000000_admin.sql)の検証。本物の Supabase(auth / storage / pg_net)上で動く。
--   * 管理者専用アプリが公開カタログに出ないこと
--   * BANされた利用者が、有効なトークンでも読み書きできないこと(restrictive RLS)
--   * 管理者向けのユーザー一覧・監査ログが service_role 専用であること
--   * 報告が入ったときの通知トリガーが、報告の登録を止めないこと・設定(シークレット)が守られていること
-- 実行: supabase test db   (Docker で使い捨てのPostgresに代用品を入れて動かす版は supabase/sql-tests/admin)
begin;
select plan(27);

-- ---------------------------------------------------------------- フィクスチャ(postgres 権限で投入)
insert into auth.users (id, aud, role, email) values
  ('a1000000-0000-0000-0000-00000000000a', 'authenticated', 'authenticated', 'adm-alice@example.com'),
  ('b1000000-0000-0000-0000-00000000000b', 'authenticated', 'authenticated', 'adm-bob@example.com'),
  ('e1000000-0000-0000-0000-00000000000e', 'authenticated', 'authenticated', 'adm-admin@example.com');
update public.profiles set role = 'admin' where id = 'e1000000-0000-0000-0000-00000000000e';
update public.profiles set display_name = 'アリス' where id = 'a1000000-0000-0000-0000-00000000000a';

insert into public.developers (user_id, name, contact_email, status)
values ('e1000000-0000-0000-0000-00000000000e', 'T-tech', 'adm-admin@example.com', 'approved');

insert into public.apps (id, slug, package_name, developer_id, name, status, admin_only) values
  ('aa100000-0000-0000-0000-000000000001', 'adm-public-app', 'com.example.admpub', 'e1000000-0000-0000-0000-00000000000e', '公開アプリ', 'published', false),
  ('aa100000-0000-0000-0000-000000000002', 'adm-private-app', 'com.example.admprv', 'e1000000-0000-0000-0000-00000000000e', '管理アプリ', 'published', true);
insert into public.app_releases (app_id, version_name, version_code, apk_key, sha256, signing_cert_sha256, status) values
  ('aa100000-0000-0000-0000-000000000001', '1.0', 1, 'k1', repeat('a', 64), repeat('b', 64), 'published'),
  ('aa100000-0000-0000-0000-000000000002', '1.0', 1, 'k2', repeat('c', 64), repeat('d', 64), 'published');

insert into public.diary_entries (id, user_id, body, visibility) values
  ('11100000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-00000000000a', 'Aliceの公開投稿', 'public'),
  ('11100000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-00000000000b', 'Bobの公開投稿', 'public');

-- ---------------------------------------------------------------- 1. 管理者専用アプリは公開カタログに出ない
select set_config('request.jwt.claims', '{"role":"anon"}', true);
set local role anon;
select is((select count(*) from public.apps where slug like 'adm-%'), 1::bigint, '未ログインには公開アプリだけ見える(管理者専用は見えない)');
select is((select count(*) from public.latest_releases where app_id in ('aa100000-0000-0000-0000-000000000001', 'aa100000-0000-0000-0000-000000000002')),
  1::bigint, '最新リリースのビューにも管理者専用アプリは出ない');

reset role;
select set_config('request.jwt.claims', '{"sub":"b1000000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
select is((select count(*) from public.apps where slug = 'adm-private-app'), 0::bigint, '一般ユーザーにも管理者専用アプリは見えない');
select is((select count(*) from public.app_releases where app_id = 'aa100000-0000-0000-0000-000000000002'), 0::bigint, '一般ユーザーには管理者専用アプリのリリースも見えない');

reset role;
select set_config('request.jwt.claims', '{"sub":"e1000000-0000-0000-0000-00000000000e","role":"authenticated"}', true);
set local role authenticated;
select is((select count(*) from public.apps where slug like 'adm-%'), 2::bigint, '管理者本人には両方見える');
select throws_ok(
  $$ update public.apps set admin_only = false where slug = 'adm-private-app' $$,
  '42501', null, '開発者は admin_only を自分で変えられない(列権限)'
);

-- ---------------------------------------------------------------- 2. BAN(restrictive RLS)
reset role;
select is(public.is_user_banned('a1000000-0000-0000-0000-00000000000a'), false, 'BANされていない利用者は false');
update public.profiles set banned_at = now(), ban_reason = '迷惑行為' where id = 'a1000000-0000-0000-0000-00000000000a';
select is(public.is_user_banned('a1000000-0000-0000-0000-00000000000a'), true, 'BANされた利用者は true');

select set_config('request.jwt.claims', '{"sub":"b1000000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
select is((select count(*) from public.diary_entries where id in ('11100000-0000-0000-0000-000000000001', '11100000-0000-0000-0000-000000000002')),
  1::bigint, 'BANされた人の公開投稿は、他の人から見えなくなる');
select lives_ok(
  $$ insert into public.reviews (app_id, user_id, rating, body) values ('aa100000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-00000000000b', 5, 'よい') $$,
  'BANされていない人はレビューを書ける'
);

reset role;
select set_config('request.jwt.claims', '{"sub":"a1000000-0000-0000-0000-00000000000a","role":"authenticated"}', true);
set local role authenticated;
select is((select count(*) from public.diary_entries), 0::bigint, 'BANされた本人は、自分の投稿も読めない');
select throws_ok(
  $$ insert into public.diary_entries (user_id, body, visibility) values ('a1000000-0000-0000-0000-00000000000a', 'こっそり', 'public') $$,
  '42501', null, 'BANされた本人は投稿できない'
);
select throws_ok(
  $$ insert into public.reviews (app_id, user_id, rating, body) values ('aa100000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-00000000000a', 1, 'x') $$,
  '42501', null, 'BANされた本人はレビューを書けない'
);
select throws_ok(
  $$ insert into public.diary_friendships (requester_id, addressee_id) values ('a1000000-0000-0000-0000-00000000000a', 'b1000000-0000-0000-0000-00000000000b') $$,
  '42501', null, 'BANされた本人は友達申請できない'
);

-- BAN解除で元に戻る
reset role;
update public.profiles set banned_at = null, ban_reason = null where id = 'a1000000-0000-0000-0000-00000000000a';
select set_config('request.jwt.claims', '{"sub":"b1000000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
select is((select count(*) from public.diary_entries where id in ('11100000-0000-0000-0000-000000000001', '11100000-0000-0000-0000-000000000002')),
  2::bigint, 'BAN解除で、投稿がまた見える');

-- ---------------------------------------------------------------- 3. 管理者向けのユーザー一覧・監査ログは service_role 専用
reset role;
select set_config('request.jwt.claims', '{"sub":"e1000000-0000-0000-0000-00000000000e","role":"authenticated"}', true);
set local role authenticated;
select throws_ok($$ select * from public.admin_list_users('adm-') $$, '42501', null, '管理者でも、アプリ(authenticated)からは一覧関数を呼べない');
select throws_ok($$ select * from public.admin_audit_log $$, '42501', null, '管理者でも、アプリから監査ログは読めない');

reset role;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
set local role anon;
select throws_ok($$ select * from public.admin_list_users('adm-') $$, '42501', null, '未ログインも一覧関数を呼べない');

reset role;
select set_config('request.jwt.claims', '{"role":"service_role"}', true);
set local role service_role;
select is((select count(*) from public.admin_list_users('adm-')), 3::bigint, 'service_role は一覧を取得できる(検索語で絞れる)');
select is((select diary_count from public.admin_list_users('adm-alice')), 1::bigint, '一覧に投稿数が入る');
select is((select count(*) from public.admin_list_users('%')), (select count(*) from public.admin_list_users('')), 'ワイルドカードは検索語として効かない');
insert into public.admin_audit_log (admin_id, action, target_type, target_id) values ('e1000000-0000-0000-0000-00000000000e', 'user.ban', 'user', 'x');
select is((select count(*) from public.admin_audit_log), 1::bigint, 'service_role は監査ログを書ける');

-- ---------------------------------------------------------------- 4. 通知トリガー
reset role;
-- 通知先を設定した状態で報告しても、報告の登録は成功する(通知は非同期で、失敗しても登録を止めない)
insert into private.notify_settings (key, value) values ('url', 'http://127.0.0.1:9/api/internal/notify'), ('secret', 'test-secret')
  on conflict (key) do update set value = excluded.value;
select set_config('request.jwt.claims', '{"sub":"b1000000-0000-0000-0000-00000000000b","role":"authenticated"}', true);
set local role authenticated;
select lives_ok(
  $$ select public.diary_report_entry('11100000-0000-0000-0000-000000000001', 'spam', '通知トリガーのテスト') $$,
  '通知先が設定されていても、報告は登録できる'
);
select throws_ok($$ select * from private.notify_settings $$, '42501', null, 'authenticated は通知設定(シークレット)を読めない');

reset role;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
set local role anon;
select throws_ok($$ select * from private.notify_settings $$, '42501', null, 'anon も通知設定を読めない');

reset role;
select is((select count(*) from public.diary_reports where entry_id = '11100000-0000-0000-0000-000000000001'), 1::bigint, '報告が保存されている');
select is(
  (select count(*) from pg_trigger where tgname in ('diary_reports_notify', 'review_reports_notify', 'developers_notify') and not tgisinternal),
  3::bigint,
  '報告・レビュー通報・開発者申請に通知トリガーが付いている'
);

select * from finish();
rollback;
