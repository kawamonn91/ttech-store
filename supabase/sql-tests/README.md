# Docker で動かす SQL 検証(pgTAP ではない)

ここのスクリプトは、Docker の使い捨て Postgres に、auth / storage / pg_net の**代用品**を入れて動かす普通の SQL。
Supabase の CLI が無くても、Docker だけで手元で確かめられる(各フォルダの README を参照)。

**`supabase/tests/` の外に置いている理由**: `supabase test db`(CI の Database ジョブ)は `supabase/tests/` 以下の
すべての `.sql` を pgTAP テストとして実行する。ここのスクリプトは代用品を作る(本物の Supabase と衝突する)うえ、
テストデータを消さないため、そこに置くと CI が失敗する。

CI で動く pgTAP テスト(本物の Supabase 上)は `supabase/tests/database/` にある。
