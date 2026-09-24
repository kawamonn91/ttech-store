# ひとこと日記の安全対策 (20260924000000_diary_safety.sql) の検証

使い捨てのPostgreSQLで、ブロック・報告・写真の閲覧制限・アカウント削除のRLSを検証する。
SupabaseのCLIが無くても、Dockerだけで実行できる。auth / storage は 00_stubs.sql の最小の代用品。

```sh
docker run -d --name pgtest -e POSTGRES_PASSWORD=pw postgres:15
docker cp 00_stubs.sql pgtest:/00.sql
docker cp ../../migrations/20260920000000_init.sql pgtest:/01.sql
docker cp ../../migrations/20260922000000_diary.sql pgtest:/02.sql
docker cp ../../migrations/20260924000000_diary_safety.sql pgtest:/03.sql
docker cp 10_tests.sql pgtest:/10.sql
for f in 00 01 02 03; do docker exec pgtest psql -U postgres -v ON_ERROR_STOP=1 -q -f /$f.sql; done
docker exec pgtest psql -U postgres -v ON_ERROR_STOP=1 -f /10.sql   # 最後に「全部PASS」と出れば成功
docker rm -f pgtest
```
