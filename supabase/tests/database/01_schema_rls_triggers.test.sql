-- pgTAP: RLS・列権限・トリガー(署名鍵の固定)・集計の振る舞いを検証する。
-- 実行: supabase test db   (CI では supabase start の後に実行)
begin;
select plan(22);

-- ---------------------------------------------------------------- フィクスチャ(postgres 権限で投入)
insert into auth.users (id, aud, role, email) values
  ('00000000-0000-0000-0000-00000000000a', 'authenticated', 'authenticated', 'admin@example.com'),
  ('00000000-0000-0000-0000-00000000000d', 'authenticated', 'authenticated', 'dev@example.com'),
  ('00000000-0000-0000-0000-0000000000e1', 'authenticated', 'authenticated', 'user1@example.com'),
  ('00000000-0000-0000-0000-0000000000e2', 'authenticated', 'authenticated', 'user2@example.com');

update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-00000000000a';

insert into public.developers (user_id, name, contact_email, status) values
  ('00000000-0000-0000-0000-00000000000d', 'Dev Co', 'secret-contact@example.com', 'approved');

insert into public.apps (id, slug, package_name, developer_id, name, status) values
  ('10000000-0000-0000-0000-000000000001', 'pub-app', 'com.example.pub', '00000000-0000-0000-0000-00000000000d', 'Published', 'published'),
  ('10000000-0000-0000-0000-000000000002', 'draft-app', 'com.example.draft', '00000000-0000-0000-0000-00000000000d', 'Draft', 'draft');

-- ---------------------------------------------------------------- 匿名ユーザー
select set_config('request.jwt.claims', '{"role":"anon"}', true);
set local role anon;

select is((select count(*) from public.apps), 1::bigint, '匿名は公開中のアプリだけ読める');
select is((select count(*) from public.downloads), 0::bigint, '匿名はダウンロード記録を読めない');
select throws_ok(
  $$ select contact_email from public.developers $$,
  '42501',
  null,
  '開発者の連絡先メールは列権限で読めない'
);
select is(
  (select name from public.developers where user_id = '00000000-0000-0000-0000-00000000000d'),
  'Dev Co',
  '承認済み開発者の名前は公開ページ用に読める'
);
select throws_ok(
  $$ select public.record_download('20000000-0000-0000-0000-000000000001', null, 'x') $$,
  '42501',
  null,
  '匿名は record_download を実行できない'
);

-- ---------------------------------------------------------------- 一般ユーザー(user1)
reset role;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-0000000000e1","role":"authenticated"}', true);
set local role authenticated;

select throws_ok(
  $$ update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-0000000000e1' $$,
  '42501',
  null,
  '自分の role を admin に書き換えられない(列権限)'
);
select throws_ok(
  $$ insert into public.apps (slug, package_name, developer_id, name) values ('x-app', 'com.example.x', '00000000-0000-0000-0000-0000000000e1', 'X') $$,
  null,
  null,
  '開発者でないユーザーはアプリを登録できない'
);

-- ---------------------------------------------------------------- 承認済み開発者
reset role;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000d","role":"authenticated"}', true);
set local role authenticated;

select is((select count(*) from public.apps), 2::bigint, '開発者は自分の下書きも読める');
select throws_ok(
  $$ insert into public.apps (slug, package_name, developer_id, name, status) values ('self-pub', 'com.example.selfpub', '00000000-0000-0000-0000-00000000000d', 'S', 'published') $$,
  '42501',
  null,
  '開発者は自分で published にして登録できない'
);
select throws_ok(
  $$ update public.apps set status = 'published', featured = true where id = '10000000-0000-0000-0000-000000000002' $$,
  '42501',
  null,
  '開発者は status / featured を変更できない(列権限)'
);
select lives_ok(
  $$ update public.apps set short_desc = '説明' where id = '10000000-0000-0000-0000-000000000002' $$,
  '開発者は自分のアプリの説明は変更できる'
);

-- ---------------------------------------------------------------- 署名鍵の固定トリガー(postgres 権限)
reset role;
select set_config('request.jwt.claims', null, true);

insert into public.app_releases (id, app_id, apk_key, version_name, version_code, sha256, signing_cert_sha256, status) values
  ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', 'k1', '1.0', 1, repeat('a', 64), repeat('c', 64), 'scanned'),
  ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'k2', '2.0', 2, repeat('b', 64), repeat('d', 64), 'scanned'),
  ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000002', 'k3', '3.0', 3, repeat('e', 64), repeat('c', 64), 'scanned'),
  ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', 'k4', null, null, null, null, 'uploaded');

update public.app_releases set status = 'published' where id = '20000000-0000-0000-0000-000000000001';
select is(
  (select signing_cert_sha256 from public.apps where id = '10000000-0000-0000-0000-000000000002'),
  repeat('c', 64),
  '最初に公開したリリースの署名鍵がアプリに固定される'
);

select throws_ok(
  $$ update public.app_releases set status = 'published' where id = '20000000-0000-0000-0000-000000000002' $$,
  null,
  null,
  '別の署名鍵のリリースは公開できない'
);
select lives_ok(
  $$ update public.app_releases set status = 'published' where id = '20000000-0000-0000-0000-000000000003' $$,
  '同じ署名鍵のリリースは公開できる'
);
select throws_ok(
  $$ update public.app_releases set status = 'published' where id = '20000000-0000-0000-0000-000000000004' $$,
  null,
  'APK検査が完了していないリリースは承認できません',
  '検査未完了(ハッシュ・署名・バージョン未確定)のリリースは公開できない'
);

-- ---------------------------------------------------------------- latest_releases / record_download
update public.apps set status = 'published' where id = '10000000-0000-0000-0000-000000000002';
select is(
  (select version_code from public.latest_releases where app_id = '10000000-0000-0000-0000-000000000002'),
  3,
  'latest_releases は公開中で最大の version_code を返す'
);

select is(
  public.record_download('20000000-0000-0000-0000-000000000003', null, 'device-1'),
  true,
  '初回のダウンロードは記録される'
);
select is(
  public.record_download('20000000-0000-0000-0000-000000000003', null, 'device-1'),
  false,
  '同じ端末の24時間以内の再ダウンロードは重複として数えない'
);
select is(
  (select download_count from public.apps where id = '10000000-0000-0000-0000-000000000002'),
  1::bigint,
  'ダウンロード数は1のまま'
);

-- ---------------------------------------------------------------- レビュー集計
insert into public.reviews (app_id, user_id, rating) values
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000e1', 4),
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000e2', 2);
select is(
  (select rating_avg::text || '/' || rating_count from public.apps where id = '10000000-0000-0000-0000-000000000001'),
  '3.00/2',
  '評価の平均と件数がトリガーで集計される'
);
update public.reviews set status = 'hidden' where user_id = '00000000-0000-0000-0000-0000000000e2';
select is(
  (select rating_avg::text || '/' || rating_count from public.apps where id = '10000000-0000-0000-0000-000000000001'),
  '4.00/1',
  '非表示にしたレビューは集計から外れる'
);
select throws_ok(
  $$ insert into public.reviews (app_id, user_id, rating) values ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-0000000000e1', 5) $$,
  '23505',
  null,
  '同じユーザーが同じアプリに2件目のレビューは書けない'
);

select * from finish();
rollback;
