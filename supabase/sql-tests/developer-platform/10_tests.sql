\set ON_ERROR_STOP on
\set QUIET on

create or replace function public.expect(label text, actual anyelement, expected anyelement) returns void language plpgsql as $$
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
grant execute on function public.expect(text, anyelement, anyelement) to public;
grant execute on function public.expect_error(text, text, text) to public;

-- ---------------------------------------------------------------- 準備(マイグレーション適用後の追加分)
insert into auth.users (id, email) values
  ('d1000000-0000-0000-0000-00000000000d', 'carol@example.com'),
  ('d2000000-0000-0000-0000-00000000000d', 'dave@example.com');
insert into public.developers (user_id, name, contact_email, status)
values ('d1000000-0000-0000-0000-00000000000d', 'Carol Apps', 'carol@example.com', 'approved');
insert into public.apps (id, slug, package_name, developer_id, name, status) values
  ('dd100000-0000-0000-0000-000000000001', 'carol-app', 'com.example.carol', 'd1000000-0000-0000-0000-00000000000d', 'キャロルのアプリ', 'draft');
insert into public.app_releases (id, app_id, apk_key, status) values
  ('dd200000-0000-0000-0000-000000000001', 'dd100000-0000-0000-0000-000000000001', 'k-carol', 'uploaded');

-- ---------------------------------------------------------------- 1. マイグレーション時の変換・既定値
select public.expect('運営の開発者は、適用時に manual になる(これまでどおり運営が承認して公開)',
  (select review_mode from public.developers where user_id = 'e0000000-0000-0000-0000-00000000000e'), 'manual'::text);
select public.expect('新しい開発者の審査は既定で自動(auto)',
  (select review_mode from public.developers where user_id = 'd1000000-0000-0000-0000-00000000000d'), 'auto'::text);
select public.expect('リリースの auto_approved は既定で false',
  (select auto_approved from public.app_releases where id = 'dd200000-0000-0000-0000-000000000001'), false);
select public.expect('policy_findings は既定で空の配列',
  (select policy_findings from public.app_releases where id = 'dd200000-0000-0000-0000-000000000001'), '[]'::jsonb);
select public.expect('既存のアプリは、そのまま残る',
  (select count(*) from public.apps where slug = 'admin-app'), 1::bigint);

-- ---------------------------------------------------------------- 2. 開発者名の重複(大文字小文字を区別しない)
select public.expect_error('開発者名は、大文字小文字を区別せず重複できない',
  $q$ insert into public.developers (user_id, name, contact_email, status) values ('d2000000-0000-0000-0000-00000000000d', 'CAROL apps', 'dave@example.com', 'approved') $q$,
  'developers_name_lower_key');

-- ---------------------------------------------------------------- 3. 開発者本人(authenticated)にできないこと
set role authenticated;
select set_config('request.jwt.claim.sub', 'd1000000-0000-0000-0000-00000000000d', false);
select set_config('request.jwt.claim.role', 'authenticated', false);

select public.expect_error('承認済みの開発者でも、アプリを直接作成できない',
  $q$ insert into public.apps (slug, package_name, developer_id, name, status) values ('carol-direct', 'com.google.android.gms', 'd1000000-0000-0000-0000-00000000000d', '直接', 'draft') $q$,
  'row-level security');
select public.expect_error('開発者は、リリースを直接作成できない',
  $q$ insert into public.app_releases (app_id, apk_key, status) values ('dd100000-0000-0000-0000-000000000001', 'k-evil', 'uploaded') $q$,
  'row-level security');
select public.expect_error('開発者は、アイコンのパスを直接書き換えられない(列権限)',
  $q$ update public.apps set icon_path = 'someone-else/icon.png' where id = 'dd100000-0000-0000-0000-000000000001' $q$,
  'permission denied');
select public.expect_error('開発者は、スクリーンショットのパスを直接書き換えられない(列権限)',
  $q$ update public.apps set screenshots = array['x/y.png'] where id = 'dd100000-0000-0000-0000-000000000001' $q$,
  'permission denied');
update public.apps set description = 'よい説明' where id = 'dd100000-0000-0000-0000-000000000001';
select public.expect('開発者は、自分のアプリの説明は更新できる',
  (select description from public.apps where id = 'dd100000-0000-0000-0000-000000000001'), 'よい説明'::text);
select public.expect_error('説明が長すぎる更新は、DBが拒否する',
  $q$ update public.apps set description = repeat('あ', 5000) where id = 'dd100000-0000-0000-0000-000000000001' $q$,
  'apps_text_length');
select public.expect_error('review_mode は API から読めない(列権限)',
  $q$ select review_mode from public.developers $q$, 'permission denied');
select public.expect_error('開発者は、画像を Storage へ直接アップロードできない',
  $q$ insert into storage.objects (bucket_id, name) values ('app-media', 'dd100000-0000-0000-0000-000000000001/icon.png') $q$,
  'row-level security');

-- ---------------------------------------------------------------- 4. 運営(管理者)にはできる
select set_config('request.jwt.claim.sub', 'e0000000-0000-0000-0000-00000000000e', false);
insert into storage.objects (bucket_id, name) values ('app-media', 'aaaaaaaa-0000-0000-0000-000000000001/icon.png');

-- (検証用の代用品には、authenticated 向けの読み取りポリシーが無いので、件数は権限を戻して数える)
reset role;
select public.expect('運営は、画像を Storage に保存できる(これまでどおり)',
  (select count(*) from storage.objects where bucket_id = 'app-media'), 1::bigint);
\echo ==== 全部PASS ====
