/**
 * 自動審査が「中身を確かめ済み」として扱うネイティブライブラリ(.so)の SHA-256 → 名前。
 * ネイティブコードは静的に解析できないので、AndroidX など広く使われている公式ライブラリの
 * 既知のバイナリだけを許可し、それ以外を含むAPKは運営の確認に回す。
 * 新しいバージョンのライブラリが増えたら、運営が中身を確かめたうえで、ここに追加する
 * (手順は docs/developer-platform.md)。
 */
export const KNOWN_NATIVE_LIBS: Record<string, string> = {
  // AndroidX Graphics Path / DataStore(Compose・DataStoreを使うと自動で入る)。自作アプリ53本の実APKのバイナリから作成
  "41e9a793c43a0f4fddb19e33f346bace464f30f888ba7b9eaf96294ea115bfb6": "libandroidx.graphics.path.so (arm64-v8a)",
  "41399eba6fc2a60f6f14642375c1824f3cf25eb8fec7397d753730a3ceda3e2b": "libandroidx.graphics.path.so (armeabi-v7a)",
  "4e56c996f13670e70082658de7880c4020eabf4f25e43387f88ed78a713fc9f0": "libandroidx.graphics.path.so (x86_64)",
  "eb0570b41fd3bff25d8204a967c03bd7550719e768b791f680cc40cbe35f29af": "libandroidx.graphics.path.so (x86)",
  "deed4546c8dafad0e68ea2c25e4c0a62ca97343614ae386b7ed2af6abb7fa999": "libdatastore_shared_counter.so (arm64-v8a)",
  "f51d0b6801896a20be66bd389a024f30be3284b757ac02377833d20e70b9c628": "libdatastore_shared_counter.so (armeabi-v7a)",
  "c3973140a0e6144a83e7dd7ee3c4e161f42181591feda254e12c8395d3bbacd0": "libdatastore_shared_counter.so (x86_64)",
  "c38104bd173ef2e405d32e90ef753725c54aaeca08f2e9a57820df8782f4d790": "libdatastore_shared_counter.so (x86)",
};
