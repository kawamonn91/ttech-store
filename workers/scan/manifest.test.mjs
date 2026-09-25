// node --test workers/scan/manifest.test.mjs
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { extractManifestFacts, parseXmlTree } from "./manifest.mjs";

// 実際にビルドしたAPK(ランニング記録)の `aapt2 dump xmltree --file AndroidManifest.xml` の出力
const text = readFileSync(new URL("./fixtures/manifest-run-tracker.txt", import.meta.url), "utf8");
const facts = extractManifestFacts(parseXmlTree(text));

test("パッケージ名・バージョン・SDK・アプリの属性を取り出す", () => {
  assert.equal(facts.package, "com.ttech.runtracker");
  assert.equal(facts.versionCode, 1);
  assert.equal(facts.versionName, "1.0.0");
  assert.equal(facts.minSdk, 26);
  assert.equal(facts.targetSdk, 36);
  assert.equal(facts.debuggable, false);
  assert.equal(facts.testOnly, false);
  assert.equal(facts.hasCode, true);
  assert.equal(facts.sharedUserId, null);
});

test("uses-permission を、独自権限も含めてすべて取り出す", () => {
  const names = facts.usesPermissions.map((p) => p.name);
  assert.ok(names.includes("android.permission.INTERNET"));
  assert.ok(names.includes("android.permission.ACCESS_FINE_LOCATION"));
  assert.ok(names.includes("android.permission.FOREGROUND_SERVICE_LOCATION"));
  assert.ok(names.includes("com.ttech.runtracker.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"));
  assert.equal(names.length, 8);
});

test("独自に宣言した権限と、その保護レベル(2 = signature)を取り出す", () => {
  assert.deepEqual(facts.declaredPermissions, [{ name: "com.ttech.runtracker.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", protectionLevel: 2 }]);
});

test("コンポーネントの種類・公開・権限・インテントフィルターを取り出す", () => {
  const byName = Object.fromEntries(facts.components.map((c) => [c.name.split(".").at(-1), c]));
  assert.equal(byName.MainActivity.kind, "activity");
  assert.equal(byName.MainActivity.exported, true);
  assert.deepEqual(byName.MainActivity.actions, ["android.intent.action.MAIN"]);
  assert.deepEqual(byName.MainActivity.categories, ["android.intent.category.LAUNCHER"]);

  assert.equal(byName.RunService.kind, "service");
  assert.equal(byName.RunService.exported, false);

  assert.equal(byName.FileProvider.kind, "provider");
  assert.equal(byName.FileProvider.exported, false);
  assert.equal(byName.FileProvider.grantUriPermissions, true);
  assert.equal(byName.FileProvider.authorities, "com.ttech.runtracker.fileprovider");

  // 公開されているが、権限(DUMP)で守られたレシーバー(AndroidX が自動で足す)
  assert.equal(byName.ProfileInstallReceiver.kind, "receiver");
  assert.equal(byName.ProfileInstallReceiver.exported, true);
  assert.equal(byName.ProfileInstallReceiver.permission, "android.permission.DUMP");
  assert.ok(byName.ProfileInstallReceiver.actions.includes("androidx.profileinstaller.action.INSTALL_PROFILE"));
});

test("meta-data など、コンポーネントでない要素は含めない", () => {
  assert.ok(facts.components.every((c) => ["activity", "activity-alias", "service", "receiver", "provider"].includes(c.kind)));
  assert.deepEqual(facts.usesLibraries, ["androidx.window.extensions", "androidx.window.sidecar"]);
});

test("exported を書いていないコンポーネントは、インテントフィルターがあれば公開・無ければ非公開とみなす", () => {
  const xml = [
    "N: android=http://schemas.android.com/apk/res/android (line=2)",
    "  E: manifest (line=2)",
    '    A: package="com.example.a" (Raw: "com.example.a")',
    "      E: application (line=5)",
    "          E: service (line=6)",
    '            A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.example.WithFilter" (Raw: "com.example.WithFilter")',
    "              E: intent-filter (line=7)",
    "                  E: action (line=8)",
    '                    A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.example.ACTION" (Raw: "com.example.ACTION")',
    "          E: service (line=10)",
    '            A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.example.NoFilter" (Raw: "com.example.NoFilter")',
    "          E: provider (line=12)",
    '            A: http://schemas.android.com/apk/res/android:name(0x01010003)="com.example.OpenProvider" (Raw: "com.example.OpenProvider")',
    "            A: http://schemas.android.com/apk/res/android:exported(0x01010010)=(type 0x12)0xffffffff",
  ].join("\n");
  const f = extractManifestFacts(parseXmlTree(xml));
  const by = Object.fromEntries(f.components.map((c) => [c.name, c]));
  assert.equal(by["com.example.WithFilter"].exported, true);
  assert.equal(by["com.example.NoFilter"].exported, false);
  // (type 0x12)0xffffffff は真偽値の true
  assert.equal(by["com.example.OpenProvider"].exported, true);
});

test("sharedUserId・debuggable・testOnly・hasCode=false を読み取る", () => {
  const xml = [
    "N: android=http://schemas.android.com/apk/res/android (line=2)",
    "  E: manifest (line=2)",
    '    A: package="com.example.b" (Raw: "com.example.b")',
    '    A: http://schemas.android.com/apk/res/android:sharedUserId(0x0101000b)="com.example.shared" (Raw: "com.example.shared")',
    "      E: uses-sdk (line=4)",
    "        A: http://schemas.android.com/apk/res/android:targetSdkVersion(0x01010270)=24",
    "      E: application (line=6)",
    "        A: http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=true",
    "        A: http://schemas.android.com/apk/res/android:testOnly(0x01010272)=true",
    "        A: http://schemas.android.com/apk/res/android:hasCode(0x0101000c)=false",
  ].join("\n");
  const f = extractManifestFacts(parseXmlTree(xml));
  assert.equal(f.sharedUserId, "com.example.shared");
  assert.equal(f.debuggable, true);
  assert.equal(f.testOnly, true);
  assert.equal(f.hasCode, false);
  assert.equal(f.targetSdk, 24);
  assert.equal(f.minSdk, null);
});

test("manifest が無い・空の入力は null を返す(呼び出し側で解析失敗として扱う)", () => {
  assert.equal(parseXmlTree(""), null);
  assert.equal(extractManifestFacts(null), null);
});
