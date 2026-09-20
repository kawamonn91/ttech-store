-- カテゴリの初期データ
insert into public.categories (slug, name, sort_order) values
  ('productivity', '仕事効率化', 10),
  ('health', '健康・ライフログ', 20),
  ('finance', '家計・お金', 30),
  ('education', '学習・スキル', 40),
  ('family', '子育て・家族', 50),
  ('hobby', '趣味・ホビー', 60),
  ('tools', 'ツール', 70),
  ('business', 'ビジネス', 80),
  ('entertainment', 'エンタメ', 90)
on conflict (slug) do nothing;
