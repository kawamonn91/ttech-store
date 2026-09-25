-- pgTAP: 開発者プラットフォーム(20260926000000_developer_platform.sql)の検証。
--   * 開発者名が(大文字小文字を区別せず)重複できないこと
--   * 開発者が、アプリ・リリースを直接作れない(API経由のみ)こと
--   * アイコン/スクリーンショットのパス・Storage への直接書き込みが、開発者にはできないこと
--   * 掲載内容の長さの上限、review_mode が公開されないこと
-- 実行: supabase test db
begin;
select plan(11);

-- ---------------------------------------------------------------- フィクスチャ
insert into auth.users (id, aud, role, email) values
  ('d1000000-0000-0000-0000-00000000000d', 'authenticated', 'authenticated', 'dev-carol@example.com'),
  ('d2000000-0000-0000-0000-00000000000d', 'authenticated', 'authenticated', 'dev-dave@example.com');

insert into public.developers (user_id, name, contact_email, status)
values ('d1000000-0000-0000-0000-00000000000d', 'Carol Apps', 'dev-carol@example.com', 'approved');

insert into public.apps (id, slug, package_name, developer_id, name, status) values
  ('dd100000-0000-0000-0000-000000000001', 'carol-app', 'com.example.carol', 'd1000000-0000-0000-0000-00000000000d', 'キャロルのアプリ', 'draft');
insert into public.app_releases (id, app_id, apk_key, status) values
  ('dd200000-0000-0000-0000-000000000001', 'dd100000-0000-0000-0000-000000000001', 'k-carol', 'uploaded');

-- ---------------------------------------------------------------- 1. 既定値
select is((select review_mode from public.developers where user_id = 'd1000000-0000-0000-0000-00000000000d'), 'auto', '新しい開発者の審査は既定で自動(review_mode = auto)');
select is((select auto_approved from public.app_releases where id = 'dd200000-0000-0000-0000-000000000001'), false, 'リリースの auto_approved は既定で false');
select is((select policy_findings from public.app_releases where id = 'dd200000-0000-0000-0000-000000000001'), '[]'::jsonb, 'policy_findings は既定で空の配列');

-- ---------------------------------------------------------------- 2. 開発者名の重複
select throws_ok(
  $$ insert into public.developers (user_id, name, contact_email, status) values ('d2000000-0000-0000-0000-00000000000d', 'CAROL apps', 'dev-dave@example.com', 'approved') $$,
  '23505', null, '開発者名は大文字小文字を区別せず重複できない'
);

-- ---------------------------------------------------------------- 3. 開発者本人(authenticated)にできないこと
select set_config('request.jwt.claims', '{"sub":"d1000000-0000-0000-0000-00000000000d","role":"authenticated"}', true);
set local role authenticated;

select throws_ok(
  $$ insert into public.apps (slug, package_name, developer_id, name, status) values ('carol-direct', 'com.google.android.gms', 'd1000000-0000-0000-0000-00000000000d', '直接作成', 'draft') $$,
  '42501', null, '承認済みの開発者でも、アプリを直接作成できない(APIでの予約名の確認を回避できてしまうため)'
);
select throws_ok(
  $$ insert into public.app_releases (app_id, apk_key, status) values ('dd100000-0000-0000-0000-000000000001', 'k-evil', 'uploaded') $$,
  '42501', null, '開発者は、リリースを直接作成できない(他人のAPKのキーを指定できてしまうため)'
);
select throws_ok(
  $$ update public.apps set icon_path = 'someone-else/icon.png' where id = 'dd100000-0000-0000-0000-000000000001' $$,
  '42501', null, '開発者は、アイコンのパスを直接書き換えられない'
);
select lives_ok(
  $$ update public.apps set description = 'よい説明' where id = 'dd100000-0000-0000-0000-000000000001' $$,
  '開発者は、自分のアプリの説明は更新できる'
);
select throws_ok(
  $$ update public.apps set description = repeat('あ', 5000) where id = 'dd100000-0000-0000-0000-000000000001' $$,
  '23514', null, '説明が長すぎる更新は、DBが拒否する'
);
select throws_ok(
  $$ select review_mode from public.developers $$,
  '42501', null, 'review_mode は API から読めない(列権限)'
);
select throws_ok(
  $$ insert into storage.objects (bucket_id, name, owner) values ('app-media', 'dd100000-0000-0000-0000-000000000001/icon.png', 'd1000000-0000-0000-0000-00000000000d') $$,
  '42501', null, '開発者は、画像を Storage へ直接アップロードできない'
);

select * from finish();
rollback;
