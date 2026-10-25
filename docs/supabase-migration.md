# Supabase データベースマイグレーション運用手順

`supabase/migrations/` のマイグレーションを本番の DB に適用するときの原則と、適用を確かめる方法。
本番の DB への接続方法(どのサーバーでどう流すか)は、公開しない運用資料に書く。

---

## 1. 原則

> **【重要原則】セキュリティ・RLS 修正のマイグレーションは、本番に適用し、確かめてからコードを push / デプロイする。**

- 公開リポジトリなので、先に push すると、塞ぐ前の穴の場所を公開することになる。
- バックエンドのコードが新しいスキーマや関数(例: `aal` を考慮した `is_admin()`)を前提にしている場合、
  未適用のままコードを反映すると、権限チェックの不整合や予期しないエラーになる。

---

## 2. 適用確認用 SQL

### 管理者権限関数 (`is_admin`) の確認
```sql
SELECT pg_get_functiondef('public.is_admin'::regproc);
```

- **適用済み(正常)**: `auth.mfa_factors` と `auth.jwt() ->> 'aal'`(二要素認証のレベル)を検査していること。
- **未適用**: `select exists (select 1 from public.profiles where id = auth.uid() and role = 'admin');` のみ。

### テーブル定義・RLS ポリシーの確認
```sql
-- RLS が有効になっているか
SELECT tablename, rowsecurity
FROM pg_tables
WHERE schemaname = 'public';

-- 適用されたポリシー一覧
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
FROM pg_policies
WHERE schemaname = 'public';
```
