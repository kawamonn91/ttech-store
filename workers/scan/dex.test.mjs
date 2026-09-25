// node --test workers/scan/dex.test.mjs
import assert from "node:assert/strict";
import { test } from "node:test";
import { collectDexFacts, mergeDexFacts } from "./dex.mjs";

/** dexdump -d の出力の形式(実際の出力と同じ書式)で、1つのメソッドを書く */
function method(cls, name, sig, access, ops) {
  const dotted = cls.slice(1, -1).replace(/\//g, ".");
  return [
    `    #0              : (in ${cls})`,
    `      name          : '${name}'`,
    `      type          : '${sig}'`,
    `      access        : ${access}`,
    `      code          -`,
    `      registers     : 3`,
    `      insns size    : 6 16-bit code units`,
    `1170fc:                                        |[1170fc] ${dotted}.${name}:${sig}`,
    ...ops.map((op, i) => `11710c: 7010 0000 0000                         |${i.toString(16).padStart(4, "0")}: ${op}`),
    `      catches       : (none)`,
    "",
  ];
}

function classDump(cls, methods) {
  return [`Class #0            -`, `  Class descriptor  : '${cls}'`, `  Access flags      : 0x0001 (PUBLIC)`, `  Direct methods    -`, ...methods.flat()];
}

const facts = async (lines) => collectDexFacts(lines);

test("通信のAPI(java.net・WebView・OkHttp)の呼び出し・生成を、呼び出し元つきで見つける", async () => {
  const f = await facts(
    classDump("Lcom/evil/Main;", [
      method("Lcom/evil/Main;", "run", "()V", "0x0009 (PUBLIC STATIC)", [
        "new-instance v0, Ljava/net/URL; // type@0001",
        "invoke-virtual {v0}, Ljava/net/URL;.openConnection:()Ljava/net/URLConnection; // method@0002",
        "new-instance v1, Ljava/net/Socket; // type@0003",
        "new-instance v2, Landroid/webkit/WebView; // type@0004",
        "invoke-virtual {v2, v3}, Landroid/webkit/WebView;.loadUrl:(Ljava/lang/String;)V // method@0005",
        "invoke-static {}, Lokhttp3/OkHttpClient;.<init>:()V // method@0006",
        "invoke-virtual {v0}, Landroid/net/wifi/WifiManager;.getConnectionInfo:()Landroid/net/wifi/WifiInfo; // method@0007",
      ]),
    ]),
  );
  assert.deepEqual(Object.keys(f.apiHits).sort(), ["net.android-http", "net.http-library", "net.java-net", "net.webview"]);
  assert.equal(f.apiHits["net.java-net"].count, 3); // URL の生成・URL.openConnection・Socket の生成
  assert.match(f.apiHits["net.java-net"].examples[0], /^Lcom\/evil\/Main;\.run → Ljava\/net\/URL;/);
  assert.equal(f.classes, 1);
  assert.equal(f.methods, 1);
});

test("無害なAPI(MimeTypeMap・java.util・String・Log・Context)は数えない", async () => {
  const f = await facts(
    classDump("La;", [
      method("La;", "b", "()V", "0x0001 (PUBLIC)", [
        "invoke-static {}, Landroid/webkit/MimeTypeMap;.getSingleton:()Landroid/webkit/MimeTypeMap; // method@0001",
        "invoke-static {}, Ljava/lang/System;.currentTimeMillis:()J // method@0002",
        "invoke-virtual {v0}, Ljava/lang/Runtime;.availableProcessors:()I // method@0003",
        "invoke-static {}, Ljava/lang/Runtime;.getRuntime:()Ljava/lang/Runtime; // method@0004",
        "invoke-virtual {v0}, Ljava/util/ArrayList;.size:()I // method@0005",
        "invoke-static {v0, v1}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I // method@0006",
        "new-instance v0, Ljava/net/URI; // type@0007",
        "invoke-static {v0}, Ljava/net/URLEncoder;.encode:(Ljava/lang/String;)Ljava/lang/String; // method@0008",
      ]),
    ]),
  );
  assert.deepEqual(f.apiHits, {});
});

test("外部プロセス・DEXの動的読み込み・ネイティブ読み込みを見つける。Runtime.getRuntime だけでは反応しない", async () => {
  const f = await facts(
    classDump("Lcom/evil/Loader;", [
      method("Lcom/evil/Loader;", "go", "()V", "0x0001 (PUBLIC)", [
        "invoke-virtual {v0, v1}, Ljava/lang/Runtime;.exec:(Ljava/lang/String;)Ljava/lang/Process; // method@0001",
        "new-instance v2, Ljava/lang/ProcessBuilder; // type@0002",
        "new-instance v3, Ldalvik/system/DexClassLoader; // type@0003",
        "invoke-static {v4}, Ljava/lang/System;.loadLibrary:(Ljava/lang/String;)V // method@0004",
        "invoke-static {}, Ljava/lang/Runtime;.getRuntime:()Ljava/lang/Runtime; // method@0005",
      ]),
    ]),
  );
  assert.deepEqual(Object.keys(f.apiHits).sort(), ["code.dex-loader", "code.exec", "code.native-load"]);
  assert.equal(f.apiHits["code.exec"].count, 2);
});

test("端末のデータ・他のアプリを操作するAPIを見つける", async () => {
  const f = await facts(
    classDump("Lcom/evil/Wipe;", [
      method("Lcom/evil/Wipe;", "boom", "()V", "0x0001 (PUBLIC)", [
        "invoke-virtual {v0}, Landroid/app/admin/DevicePolicyManager;.wipeData:(I)V // method@0001",
        "invoke-static {v0}, Landroid/os/RecoverySystem;.rebootWipeUserData:(Landroid/content/Context;)V // method@0002",
        "invoke-virtual {v0, v1}, Landroid/content/pm/PackageManager;.deletePackage:(Ljava/lang/String;)V // method@0003",
        "invoke-static {v0, v1, v2}, Landroid/provider/Settings$Secure;.putString:(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z // method@0004",
        "invoke-static {v0, v1}, Landroid/provider/DocumentsContract;.deleteDocument:(Landroid/content/ContentResolver;Landroid/net/Uri;)Z // method@0005",
        "invoke-virtual {v0}, Landroid/content/pm/PackageManager;.getPackageInfo:()V // method@0006",
        "invoke-static {v0}, Landroid/provider/Settings$Secure;.getString:(Landroid/content/ContentResolver;)Ljava/lang/String; // method@0007",
      ]),
    ]),
  );
  assert.deepEqual(Object.keys(f.apiHits).sort(), ["destroy.device-admin", "destroy.package-manager", "destroy.recovery", "destroy.saf-delete", "destroy.settings-write"]);
});

test("定数文字列: 外部URLのホスト・注意したい文字列を集める。無害なホスト(名前空間など)は除く", async () => {
  const f = await facts(
    classDump("La;", [
      method("La;", "s", "()V", "0x0001 (PUBLIC)", [
        'const-string v0, "http://schemas.android.com/apk/res/android" // string@0001',
        'const-string v0, "https://issuetracker.google.com/issues/123" // string@0002',
        'const-string v0, "https://evil.example.com/collect?d=" // string@0003',
        'const-string v0, "ftp://files.example.org/x" // string@0004',
        'const-string v0, "android.intent.action.OPEN_DOCUMENT_TREE" // string@0005',
        'const-string v0, "android.intent.action.VIEW" // string@0006',
        'const-string/jumbo v0, "普通の文字列です" // string@0007',
      ]),
    ]),
  );
  assert.deepEqual(f.urlHosts.map((h) => h.host).sort(), ["evil.example.com", "files.example.org"]);
  assert.ok(f.urlHosts[0].example.startsWith("La;.s"));
  assert.deepEqual(Object.keys(f.stringHits).sort(), ["str.saf-tree", "str.view-url"]);
});

test("native メソッド(JNI)の数と例を数える。フィールドの定義は数えない", async () => {
  const lines = [
    "Class #0            -",
    "  Class descriptor  : 'Lcom/x/Jni;'",
    "  Instance fields   -",
    "    #0              : (in Lcom/x/Jni;)",
    "      name          : 'handle'",
    "      type          : 'J'",
    "      access        : 0x0111 (PUBLIC FINAL NATIVE_LOOKALIKE)",
    "  Direct methods    -",
    ...method("Lcom/x/Jni;", "nativeInit", "()V", "0x010a (PRIVATE STATIC NATIVE)", []),
    ...method("Lcom/x/Jni;", "plain", "()V", "0x0001 (PUBLIC)", []),
  ];
  const f = await facts(lines);
  assert.equal(f.nativeMethods.count, 1);
  assert.deepEqual(f.nativeMethods.examples, ["Lcom/x/Jni;.nativeInit"]);
});

test("複数のDEXの結果を1つにまとめる(回数は足し算、例は最大3つ、ホストは重複をまとめる)", async () => {
  const one = (host) =>
    facts(
      classDump("La;", [
        method("La;", "b", "()V", "0x0001 (PUBLIC)", ["new-instance v0, Ljava/net/Socket; // type@0001", `const-string v0, "https://${host}/x" // string@0002`]),
      ]),
    );
  const merged = mergeDexFacts([await one("a.example.com"), await one("a.example.com"), await one("b.example.com")]);
  assert.equal(merged.dexFiles, 3);
  assert.equal(merged.classes, 3);
  assert.equal(merged.apiHits["net.java-net"].count, 3);
  assert.deepEqual(merged.urlHosts.map((h) => `${h.host}×${h.count}`).sort(), ["a.example.com×2", "b.example.com×1"]);
});

test("非同期の行(ストリーム)でも同じ結果になる", async () => {
  async function* gen() {
    for (const l of classDump("La;", [method("La;", "b", "()V", "0x0001 (PUBLIC)", ["new-instance v0, Ljava/net/Socket; // type@0001"])])) yield l;
  }
  const f = await collectDexFacts(gen());
  assert.equal(f.apiHits["net.java-net"].count, 1);
});
