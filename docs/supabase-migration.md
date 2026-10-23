# Supabase データベースマイグレーション運用手順

本ドキュメントは、T-tech Store の Supabase PostgreSQL データベースに対するマイグレーション適用手順および検証方法を定めます。

---

## 1. 原則

> **【重要原則】セキュリティ・RLS 修正のマイグレーションは、本番 Supabase に適用完了してからコードを push / デプロイする。**

バックエンドコードが新しいスキーマや関数（例: `aal` を考慮した `is_admin()`）を前提として動作する場合、未適用のままコードを反映すると権限チェックの不整合や予期せぬアクセス制御エラーが発生します。

---

## 2. マイグレーションの適用方法

Supabase の無料利用枠では、ダイレクト DB 接続（`db.<project-ref>.supabase.co:5432`）が IPv6 のみ解決される場合があり、Raspberry Pi などの IPv4 専用環境からは CLI の自動 `db push` が到達できない場合があります。そのため、本番環境への反映は **Supabase Web Dashboard の SQL Editor** を通じて実施します。

### 手順

1. [Supabase Dashboard](https://supabase.com/dashboard) にログインします。
2. 対象プロジェクト（`bniehgjuylkgxbstmjdh`）を選択します。
3. 左サイドバーから **SQL Editor** を開きます。
4. `supabase/migrations/` 配下の該当マイグレーション SQL（例: `20261022000000_security_hardening.sql`）の内容を貼り付けます。
5. **Run**（実行）をクリックします。
6. `Success. No rows returned` または結果が表示されたことを確認します。

---

## 3. 適用確認用 SQL

マイグレーションが正しく適用されたかを検証するには、以下のクエリを SQL Editor で実行します。

### 管理者権限関数 (`is_admin`) の確認
```sql
SELECT proname, prosrc 
FROM pg_proc 
WHERE proname = 'is_admin';
```

- **適用済み（正常）**: `auth.jwt() ->> 'aal'` （二要素認証レベルの検査）が含まれていること。
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
