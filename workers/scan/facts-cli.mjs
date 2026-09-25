#!/usr/bin/env node
// 手元のAPKから、審査に使う「事実」(権限・コード内のAPI・同梱ファイルなど)を取り出して表示する。
// 審査ルール(web/src/lib/policy.ts)を調整するとき・テスト用の実データ(web/src/lib/testing/fixtures)を作るときに使う。
//
// 使い方: ANDROID_HOME=<Android SDK> node workers/scan/facts-cli.mjs <apk> [出力するJSONファイル]

import { writeFileSync } from "node:fs";
import { collectFacts } from "./facts.mjs";
import { findBuildTool } from "./inspect.mjs";

const [apk, out] = process.argv.slice(2);
if (!apk) {
  console.error("使い方: node workers/scan/facts-cli.mjs <apk> [出力するJSONファイル]");
  process.exit(1);
}

let dexdump = null;
try {
  dexdump = findBuildTool("dexdump");
} catch {
  console.warn("dexdump が見つかりません。コードの解析はスキップします");
}
const facts = await collectFacts(apk, { aapt2: findBuildTool("aapt2"), dexdump });

const m = facts.manifest;
console.log("パッケージ:", m?.package, `(versionCode ${m?.versionCode}, targetSdk ${m?.targetSdk})`);
console.log("権限:", m?.usesPermissions.map((p) => p.name.replace("android.permission.", "")).join(", ") || "(なし)");
console.log("ネイティブライブラリ:", facts.nativeLibs.map((l) => `${l.path} ${l.sha256?.slice(0, 12)}`).join(", ") || "(なし)");
console.log("隠されたファイル:", facts.embedded.map((e) => `${e.path}[${e.kind}]`).join(", ") || "(なし)");
console.log("公開されたコンポーネント:", m?.components.filter((c) => c.exported).map((c) => `${c.kind}:${c.name}`).join(", ") || "(なし)");
for (const [id, h] of Object.entries(facts.dex?.apiHits ?? {})) console.log(`API ${id} ×${h.count}: ${h.examples[0]}`);
for (const [id, h] of Object.entries(facts.dex?.stringHits ?? {})) console.log(`文字列 ${id} ×${h.count}: ${h.examples[0]}`);
console.log("外部URLのホスト:", (facts.dex?.urlHosts ?? []).map((u) => u.host).join(", ") || "(なし)");
if (facts.incomplete.length) console.log("解析できなかった部分:", facts.incomplete);
if (out) {
  writeFileSync(out, JSON.stringify(facts, null, 1));
  console.log("書き出し:", out);
}
