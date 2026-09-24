# 管理機能 (20260925000000_admin.sql) の検証

使い捨てのPostgreSQLで、BAN・管理者専用アプリ・管理者向けユーザー一覧・監査ログ・メール通知のDB側を検証する。
SupabaseのCLIが無くても、Dockerだけで実行できる。auth / storage / pg_net は最小の代用品
(`../diary-safety/00_stubs.sql` と `01_net_stub.sql`)。

```sh
cd supabase/tests/admin
docker run -d --name pgtest -e POSTGRES_PASSWORD=pw postgres:15
docker cp ../diary-safety/00_stubs.sql pgtest:/00.sql
docker cp 01_net_stub.sql pgtest:/01.sql
docker cp ../../migrations/20260920000000_init.sql pgtest:/02.sql
docker cp ../../migrations/20260922000000_diary.sql pgtest:/03.sql
docker cp ../../migrations/20260924000000_diary_safety.sql pgtest:/04.sql
docker cp ../../migrations/20260925000000_admin.sql pgtest:/05.sql
docker cp 10_tests.sql pgtest:/10.sql
for f in 00 01 02 03 04 05; do docker exec pgtest psql -U postgres -v ON_ERROR_STOP=1 -q -f /$f.sql; done
docker exec pgtest psql -U postgres -v ON_ERROR_STOP=1 -f /10.sql   # 最後に「全部PASS」と出れば成功
docker rm -f pgtest
```

(`05.sql` の pg_net の有効化は、この使い捨てDBでは失敗して警告になるが、代用の `net.http_post` があるので問題ない。)
