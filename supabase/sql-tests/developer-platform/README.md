# 開発者プラットフォーム (20260926000000_developer_platform.sql) の検証

使い捨てのPostgreSQLで、開発者名の重複防止・アプリ/リリースの直接作成の禁止・画像パスの保護・
掲載内容の長さの上限・列権限を検証する。SupabaseのCLIが無くても、Dockerだけで実行できる
(auth / storage は `../diary-safety/00_stubs.sql` の最小の代用品)。
CIで動く本物のSupabase上の検証は `supabase/tests/database/03_developer_platform.test.sql`。

```sh
cd supabase/sql-tests/developer-platform
docker run -d --name pgtest-devplat -e POSTGRES_PASSWORD=pw postgres:15
docker cp ../diary-safety/00_stubs.sql pgtest-devplat:/00.sql
docker cp ../admin/01_net_stub.sql pgtest-devplat:/01.sql
docker cp ../../migrations/20260920000000_init.sql pgtest-devplat:/02.sql
docker cp ../../migrations/20260922000000_diary.sql pgtest-devplat:/03.sql
docker cp ../../migrations/20260924000000_diary_safety.sql pgtest-devplat:/04.sql
docker cp ../../migrations/20260925000000_admin.sql pgtest-devplat:/05.sql
docker cp 05b_existing_admin.sql pgtest-devplat:/05b.sql
docker cp ../../migrations/20260926000000_developer_platform.sql pgtest-devplat:/06.sql
docker cp 10_tests.sql pgtest-devplat:/10.sql
for f in 00 01 02 03 04 05 05b 06; do docker exec pgtest-devplat psql -U postgres -v ON_ERROR_STOP=1 -q -f /$f.sql; done
docker exec pgtest-devplat psql -U postgres -v ON_ERROR_STOP=1 -f /10.sql   # 最後に「全部PASS」と出れば成功
docker rm -f pgtest-devplat
```

`05b_existing_admin.sql` は、このマイグレーションを適用する前から運営(管理者)の開発者が存在する状況を再現する
(適用後に review_mode が manual になることの確認用)。
