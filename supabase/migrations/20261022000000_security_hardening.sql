-- セキュリティ強化マイグレーション (2026-10-22)
--   1. public.is_admin() で BAN されていないことと、TOTP(MFA)登録済みの場合は aal2 を必須化する
--   2. diary_friendships_update で申請者本人(requester_id)による自己承認(status='accepted')を防止し、宛先(addressee_id)のみ更新可能にする
--   3. public.developers / public.apps へのクライアント直接書き込みを閉じ、予約語・メール確認・停止確認を行うサーバー API(service_role)経由に統一する
--   4. public.profiles の本人更新ポリシー(profiles_update_self)で BAN 中の更新を禁止する

-- ---------------------------------------------------------------- 1. 管理者判定の強化(BAN確認 + MFA aal2 強制)
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.profiles p
    where p.id = auth.uid()
      and p.role = 'admin'
      and p.banned_at is null
  )
  and (
    not exists (
      select 1
      from auth.mfa_factors f
      where f.user_id = auth.uid()
        and f.status::text = 'verified'
    )
    or coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2'
  );
$$;

-- ---------------------------------------------------------------- 2. 友達申請の自己承認バグ修正
-- 申請の取り消しは DELETE(diary_friendships_delete)で行うため、UPDATE は宛先本人(addressee_id)のみに限定する
drop policy if exists diary_friendships_update on public.diary_friendships;
create policy diary_friendships_update on public.diary_friendships for update
  to authenticated
  using (addressee_id = auth.uid())
  with check (addressee_id = auth.uid());

-- ---------------------------------------------------------------- 3. developers / apps の直接変更を禁止(API経由のみ)
-- 開発者登録は /api/developer/register (service_role) でメール確認・予約語検査を行うため、直接 INSERT を閉じる
drop policy if exists developers_apply on public.developers;

-- アプリ情報の編集は /api/developer/apps/[id] (service_role) で予約語・制御文字・停止確認を行うため、直接 UPDATE を閉じる
drop policy if exists apps_update_owner on public.apps;
revoke update on public.apps from authenticated;

-- ---------------------------------------------------------------- 4. BAN中のプロフィール更新を禁止
drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles for update
  to authenticated
  using (id = auth.uid() and not public.is_banned())
  with check (id = auth.uid() and not public.is_banned());
