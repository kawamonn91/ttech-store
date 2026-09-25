import { describe, expect, it } from "vitest";
import { KNOWN_NATIVE_LIBS } from "./known-native-libs";
import { describeFindings, evaluatePolicy, FactsSchema, POLICY_VERSION, type Facts } from "./policy";
import offlineApp from "./testing/fixtures/facts-offline-app.json";
import pdfboxApp from "./testing/fixtures/facts-pdfbox-app.json";
import runTracker from "./testing/fixtures/facts-run-tracker.json";
import unminifiedOffline from "./testing/fixtures/facts-unminified-offline.json";
import webviewApp from "./testing/fixtures/facts-webview-app.json";

const PKG = "com.example.offline";

/** 通信しない・端末のデータに触れない、ごく普通のオフラインアプリの事実 */
function clean(): Facts {
  return {
    version: 1,
    manifest: {
      package: PKG,
      versionCode: 1,
      versionName: "1.0",
      sharedUserId: null,
      minSdk: 26,
      targetSdk: 36,
      debuggable: false,
      testOnly: false,
      hasCode: true,
      usesPermissions: [
        { name: "android.permission.VIBRATE", maxSdk: null },
        { name: `${PKG}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, maxSdk: null },
      ],
      declaredPermissions: [{ name: `${PKG}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, protectionLevel: 2 }],
      components: [
        {
          kind: "activity", name: `${PKG}.MainActivity`, exported: true, hasIntentFilter: true,
          actions: ["android.intent.action.MAIN"], categories: ["android.intent.category.LAUNCHER"],
          permission: null, readPermission: null, writePermission: null, grantUriPermissions: false, authorities: null,
        },
        {
          kind: "receiver", name: "androidx.profileinstaller.ProfileInstallReceiver", exported: true, hasIntentFilter: true,
          actions: ["androidx.profileinstaller.action.INSTALL_PROFILE"], categories: [],
          permission: "android.permission.DUMP", readPermission: null, writePermission: null, grantUriPermissions: false, authorities: null,
        },
        {
          kind: "provider", name: "androidx.core.content.FileProvider", exported: false, hasIntentFilter: false,
          actions: [], categories: [], permission: null, readPermission: null, writePermission: null, grantUriPermissions: true, authorities: `${PKG}.fileprovider`,
        },
      ],
      usesLibraries: [],
    },
    zip: { entryCount: 120, totalSize: 3_000_000, anomalies: [] },
    dexFiles: ["classes.dex"],
    nativeLibs: [],
    embedded: [],
    dex: { classes: 2000, methods: 9000, apiHits: {}, stringHits: {}, urlHosts: [], nativeMethods: { count: 0, examples: [] } },
    incomplete: [],
  };
}

const RELEASE_SIG = { v1: false, v2: true, v3: true, subject: "CN=T-tech App, O=Example, C=JP" };
const codes = (f: Facts, sig: typeof RELEASE_SIG | null = RELEASE_SIG) => evaluatePolicy({ facts: f, signature: sig, packageName: PKG }).findings.map((x) => x.code);
const verdict = (f: Facts, sig: typeof RELEASE_SIG | null = RELEASE_SIG) => evaluatePolicy({ facts: f, signature: sig, packageName: PKG });
const withPerms = (names: string[]) => {
  const f = clean();
  f.manifest!.usesPermissions.push(...names.map((name) => ({ name, maxSdk: null })));
  return f;
};

describe("evaluatePolicy: 自動承認", () => {
  it("通信も端末データの操作もないアプリは、自動承認される", () => {
    const v = verdict(clean());
    expect(v.decision).toBe("auto_approve");
    expect(v.findings).toEqual([]);
    expect(v.summary).toEqual({ network: "none", destruction: "none" });
    expect(v.version).toBe(POLICY_VERSION);
  });

  it("位置情報・カメラなど、読むだけの権限は、自動承認されるが参考として記録される", () => {
    const v = verdict(withPerms(["android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION", "android.permission.READ_MEDIA_IMAGES"]));
    expect(v.decision).toBe("auto_approve");
    const info = v.findings.find((f) => f.code === "perm.sensitive")!;
    expect(info.severity).toBe("info");
    expect(info.evidence).toEqual(["CAMERA", "ACCESS_FINE_LOCATION", "READ_MEDIA_IMAGES"]);
  });

  it("前面サービス・通知・振動・起動時の自動起動・ネットワーク状態の参照は、問題にしない", () => {
    const v = verdict(withPerms([
      "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_LOCATION", "android.permission.POST_NOTIFICATIONS",
      "android.permission.WAKE_LOCK", "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.SCHEDULE_EXACT_ALARM",
      "android.permission.USE_BIOMETRIC", "android.permission.ACCESS_NETWORK_STATE",
    ]));
    expect(v.decision).toBe("auto_approve");
  });

  it("URLとして扱わない無害な参照(ライブラリの案内など)しか無ければ、自動承認される(ワーカーが除いた結果)", () => {
    const f = clean();
    f.dex!.stringHits = { "str.view-url": { count: 1, examples: ["a: android.intent.action.VIEW"] }, "str.send": { count: 1, examples: ["b: android.intent.action.SEND"] } };
    expect(verdict(f).decision).toBe("auto_approve");
  });
});

describe("evaluatePolicy: 外部との通信", () => {
  it("INTERNET 権限があれば要確認(通信の可能性あり)", () => {
    const v = verdict(withPerms(["android.permission.INTERNET"]));
    expect(v.decision).toBe("needs_review");
    expect(v.findings.map((f) => f.code)).toEqual(["net.permission"]);
    expect(v.summary.network).toBe("possible");
    expect(v.summary.destruction).toBe("none");
  });

  it.each(["BLUETOOTH_CONNECT", "NEARBY_WIFI_DEVICES", "NFC", "CHANGE_WIFI_STATE", "ACCESS_WIFI_STATE", "BLUETOOTH_SCAN"])("%s も通信につながる権限として要確認", (p) => {
    expect(codes(withPerms([`android.permission.${p}`]))).toContain("net.permission");
  });

  it("共有ユーザーIDは、別のアプリ経由の持ち出しになりうるので要確認", () => {
    const f = clean();
    f.manifest!.sharedUserId = "com.example.shared";
    expect(codes(f)).toEqual(["net.shared-user-id"]);
  });

  it("権限で守られていない公開の Service / Provider は要確認。権限で守られたもの・非公開のものは問題なし", () => {
    const base = clean().manifest!.components[0];
    const f = clean();
    f.manifest!.components.push(
      { ...base, kind: "provider", name: "com.example.OpenProvider", exported: true, hasIntentFilter: false, actions: [], categories: [] },
      { ...base, kind: "service", name: "com.example.OpenService", exported: true, hasIntentFilter: true, actions: ["com.example.BIND"], categories: [] },
    );
    const v = verdict(f);
    expect(v.findings.map((x) => x.code)).toEqual(["net.exposed-component"]);
    expect(v.findings[0].evidence).toEqual(["provider: com.example.OpenProvider", "service: com.example.OpenService"]);

    const g = clean();
    g.manifest!.components.push(
      { ...base, kind: "service", name: "androidx.work.impl.background.systemjob.SystemJobService", exported: true, hasIntentFilter: false, actions: [], categories: [], permission: "android.permission.BIND_JOB_SERVICE" },
      { ...base, kind: "provider", name: "com.example.PrivateProvider", exported: false, hasIntentFilter: false, actions: [], categories: [] },
      { ...base, kind: "provider", name: "com.example.GuardedProvider", exported: true, hasIntentFilter: false, actions: [], categories: [], readPermission: "com.example.READ", writePermission: "com.example.WRITE" },
    );
    expect(verdict(g).decision).toBe("auto_approve");
  });

  it("通信APIの呼び出しがあり、INTERNET 権限もあれば要確認(ライブラリの中にあるものも含む)", () => {
    const f = withPerms(["android.permission.INTERNET"]);
    f.dex!.apiHits = { "net.java-net": { count: 3, examples: ["La;.b → Ljava/net/Socket;.<init> (呼び出し)"], note: "java.net の通信クラス" } };
    const v = verdict(f);
    expect(v.decision).toBe("needs_review");
    expect(v.findings.map((x) => x.code)).toEqual(["net.permission", "net.api"]);
    expect(v.findings[1].evidence[0]).toContain("java.net の通信クラス");
  });

  it("INTERNET 権限が無ければ、通信APIへの参照があっても実行できないので、自動承認される(参考として記録)", () => {
    const f = clean();
    f.dex!.apiHits = { "net.java-net": { count: 31, examples: ["Lkotlin/io/TextStreamsKt;.readBytes → Ljava/net/URL;.openStream (呼び出し)"], note: "java.net の通信クラス" } };
    const v = verdict(f);
    expect(v.decision).toBe("auto_approve");
    expect(v.summary.network).toBe("none");
    const inert = v.findings.find((x) => x.code === "net.api-inert");
    expect(inert?.severity).toBe("info");
    expect(inert?.evidence[0]).toContain("TextStreamsKt");
  });

  it("権限が無くても実行できる通信の手段(Bluetooth・NFC・LocalSocket・VPN)は、参照があれば要確認のまま", () => {
    for (const id of ["net.bluetooth-nfc-usb", "net.android-http"]) {
      const f = clean();
      f.dex!.apiHits = { [id]: { count: 1, examples: ["x"], note: id } };
      expect(codes(f), id).toContain("net.api");
    }
  });

  it("WebView・ダウンロード・HTTPライブラリ・SMS も、対応する権限があれば通信につながるAPIとして要確認", () => {
    const perms: Record<string, string> = {
      "net.webview": "android.permission.INTERNET",
      "net.download-manager": "android.permission.INTERNET",
      "net.http-library": "android.permission.INTERNET",
      "net.nio-channels": "android.permission.INTERNET",
      "net.javax-net": "android.permission.INTERNET",
      "net.sms-telephony": "android.permission.SEND_SMS",
    };
    for (const [id, perm] of Object.entries(perms)) {
      const granted = withPerms([perm]);
      granted.dex!.apiHits = { [id]: { count: 1, examples: ["x"], note: id } };
      expect(codes(granted), id).toContain("net.api");
      // 同じ参照でも、権限が無ければ実行できないので指摘にならない
      const without = clean();
      without.dex!.apiHits = { [id]: { count: 1, examples: ["x"], note: id } };
      expect(codes(without), id).toEqual(["net.api-inert"]);
    }
  });

  it("SMS・電話のAPIは、SMS・電話の権限が1つでもあれば要確認。無ければ実行できない", () => {
    const f = withPerms(["android.permission.READ_PHONE_STATE"]);
    f.dex!.apiHits = { "net.sms-telephony": { count: 1, examples: ["x"], note: "SMS・電話回線" } };
    expect(codes(f)).toContain("net.api");
  });

  it("コードに外部のURLがあれば要確認(ブラウザ経由で情報を出せる)", () => {
    const f = clean();
    f.dex!.urlHosts = [{ host: "example.com", count: 1, example: "La;.b" }];
    const v = verdict(f);
    expect(v.findings.map((x) => x.code)).toEqual(["net.url-string"]);
    expect(v.findings[0].evidence).toEqual(["example.com"]);
  });
});

describe("evaluatePolicy: 端末のデータの破壊", () => {
  it.each([
    "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "WRITE_CONTACTS", "WRITE_CALENDAR", "REQUEST_DELETE_PACKAGES", "REQUEST_INSTALL_PACKAGES",
    "SEND_SMS", "READ_SMS", "CALL_PHONE", "BIND_DEVICE_ADMIN", "SYSTEM_ALERT_WINDOW", "QUERY_ALL_PACKAGES", "PACKAGE_USAGE_STATS", "WRITE_SETTINGS",
    "MASTER_CLEAR", "READ_PHONE_STATE", "GET_ACCOUNTS", "KILL_BACKGROUND_PROCESSES",
  ])("%s は要確認", (p) => {
    const v = verdict(withPerms([`android.permission.${p}`]));
    expect(v.decision).toBe("needs_review");
    expect(v.findings.map((f) => f.code)).toEqual(["destroy.permission"]);
    expect(v.summary.destruction).toBe("possible");
  });

  it("独自・他社の権限は、内容が分からないので要確認", () => {
    const v = verdict(withPerms(["com.google.android.c2dm.permission.RECEIVE", "com.android.vending.BILLING", "android.permission.SOME_FUTURE_PERMISSION"]));
    expect(v.decision).toBe("needs_review");
    expect(v.findings.map((f) => f.code)).toEqual(["perm.unknown"]);
    expect(v.findings[0].evidence).toHaveLength(3);
  });

  it("自分のアプリの独自権限は、signature(保護レベル2)なら問題なし。normal・dangerous なら要確認", () => {
    const f = clean();
    f.manifest!.usesPermissions.push({ name: `${PKG}.ADMIN`, maxSdk: null });
    f.manifest!.declaredPermissions.push({ name: `${PKG}.ADMIN`, protectionLevel: 2 });
    expect(verdict(f).decision).toBe("auto_approve");
    f.manifest!.declaredPermissions[1].protectionLevel = 0;
    expect(codes(f)).toEqual(["perm.unknown"]);
  });

  it("ユーザー補助・通知の読み取り・VPN・端末管理者のサービスは、権限を要求していなくても要確認", () => {
    const base = clean().manifest!.components[0];
    for (const [permission, text] of [
      ["android.permission.BIND_ACCESSIBILITY_SERVICE", "ユーザー補助"],
      ["android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "通知リスナー"],
      ["android.permission.BIND_VPN_SERVICE", "VPN"],
      ["android.permission.BIND_DEVICE_ADMIN", "端末管理者"],
      ["android.permission.BIND_INPUT_METHOD", "キーボード"],
    ]) {
      const f = clean();
      f.manifest!.components.push({ ...base, kind: "service", name: "com.example.Special", exported: true, hasIntentFilter: true, actions: ["x"], categories: [], permission });
      const v = verdict(f);
      expect(v.findings.map((x) => x.code), permission).toContain("destroy.special-service");
      expect(v.findings.find((x) => x.code === "destroy.special-service")!.evidence[0]).toContain(text);
    }
  });

  it("端末管理者・アプリの削除・設定の書き換えのAPI、フォルダ全体へのアクセス要求があれば要確認", () => {
    for (const id of ["destroy.device-admin", "destroy.recovery", "destroy.package-manager", "destroy.settings-write", "destroy.saf-delete", "destroy.accessibility", "destroy.overlay"]) {
      const f = clean();
      f.dex!.apiHits = { [id]: { count: 1, examples: ["x"], note: id } };
      expect(codes(f), id).toEqual(["destroy.api"]);
    }
    const g = clean();
    g.dex!.stringHits = { "str.saf-tree": { count: 1, examples: ["La;.b: android.intent.action.OPEN_DOCUMENT_TREE"], note: "フォルダ全体へのアクセスを求める" } };
    expect(codes(g)).toEqual(["destroy.string"]);
    g.dex!.stringHits = { "str.install-apk": { count: 1, examples: ["x"], note: "APKのインストール要求" } };
    expect(codes(g)).toEqual(["destroy.string"]);
  });
});

describe("evaluatePolicy: 隠れたコード・ネイティブ・構造", () => {
  it("DEXの動的読み込み・外部プロセスの起動は要確認", () => {
    for (const id of ["code.dex-loader", "code.exec"]) {
      const f = clean();
      f.dex!.apiHits = { [id]: { count: 1, examples: ["x"], note: id } };
      expect(codes(f), id).toEqual(["code.dynamic"]);
    }
  });

  it("未知のネイティブライブラリ(.so)は要確認。既知(SHA-256が一致)なら問題なし", () => {
    const sha = "a".repeat(64);
    const f = clean();
    f.nativeLibs = [{ path: "lib/arm64-v8a/libfoo.so", abi: "arm64-v8a", size: 1000, sha256: sha }];
    expect(codes(f)).toEqual(["code.native-lib"]);
    f.nativeLibs[0].sha256 = null; // ハッシュを取れなかったものも、確かめられない
    expect(codes(f)).toEqual(["code.native-lib"]);
    KNOWN_NATIVE_LIBS[sha] = "テスト用の既知ライブラリ";
    try {
      f.nativeLibs[0].sha256 = sha;
      expect(verdict(f).decision).toBe("auto_approve");
    } finally {
      delete KNOWN_NATIVE_LIBS[sha];
    }
  });

  it("System.loadLibrary があるのに .so が同梱されていなければ、外部のコードを読み込む疑いで要確認", () => {
    const f = clean();
    f.dex!.apiHits = { "code.native-load": { count: 1, examples: ["x"], note: "ネイティブライブラリの読み込み" } };
    expect(codes(f)).toEqual(["code.native-load-external"]);
  });

  it("assets などに隠された DEX・ELF・入れ子のZIP・スクリプトは要確認。Javaのクラスファイルだけなら問題なし", () => {
    for (const kind of ["dex", "elf", "zip", "script"]) {
      const f = clean();
      f.embedded = [{ path: "assets/payload.bin", kind, size: 100 }];
      expect(codes(f), kind).toEqual(["code.embedded-executable"]);
    }
    const g = clean();
    g.embedded = [{ path: "DebugProbesKt.bin", kind: "java-class", size: 1728 }];
    expect(verdict(g).decision).toBe("auto_approve");
  });

  it("ZIPの構造の異常は、要確認ではなくその場で却下", () => {
    const f = clean();
    f.zip.anomalies = ["同じ名前のファイルが複数あります: classes.dex"];
    const v = verdict(f);
    expect(v.decision).toBe("reject");
    expect(v.findings[0]).toMatchObject({ code: "zip.anomaly", severity: "block" });
  });

  it("デバッグ署名・testOnly は却下。debuggable・古い targetSdk・v1のみの署名は要確認", () => {
    expect(verdict(clean(), { ...RELEASE_SIG, subject: "C=US, O=Android, CN=Android Debug" }).decision).toBe("reject");
    const t = clean();
    t.manifest!.testOnly = true;
    expect(verdict(t).decision).toBe("reject");

    const d = clean();
    d.manifest!.debuggable = true;
    expect(codes(d)).toEqual(["manifest.debuggable"]);
    const o = clean();
    o.manifest!.targetSdk = 26;
    expect(codes(o)).toEqual(["manifest.old-target-sdk"]);
    expect(codes(clean(), { v1: true, v2: false, v3: false, subject: "CN=a" })).toEqual(["sign.v1-only"]);
  });

  it("却下(block)が1つでもあれば、要確認の指摘があっても却下になる", () => {
    const f = withPerms(["android.permission.INTERNET"]);
    f.manifest!.testOnly = true;
    const v = verdict(f);
    expect(v.decision).toBe("reject");
    expect(v.findings.map((x) => x.code)).toEqual(expect.arrayContaining(["net.permission", "manifest.test-only"]));
  });
});

describe("evaluatePolicy: 解析できなかったとき", () => {
  it("解析結果が無ければ要確認(自動承認しない)。通信・破壊の可能性は「不明」", () => {
    const v = evaluatePolicy({ facts: null, signature: RELEASE_SIG });
    expect(v.decision).toBe("needs_review");
    expect(v.findings.map((f) => f.code)).toEqual(["analysis.missing"]);
    expect(v.summary).toEqual({ network: "unknown", destruction: "unknown" });
  });

  it("解析が一部失敗していれば、ほかに問題がなくても要確認。「可能性なし」とは言わない", () => {
    const f = clean();
    f.incomplete = ["dexdump が使えないため、コードを解析できませんでした"];
    f.dex = null;
    const v = verdict(f);
    expect(v.decision).toBe("needs_review");
    expect(v.findings.map((x) => x.code)).toEqual(["analysis.incomplete"]);
    expect(v.summary).toEqual({ network: "unknown", destruction: "unknown" });
  });

  it("AndroidManifest を読めていなければ、自動承認しない", () => {
    const f = clean();
    f.manifest = null;
    f.incomplete = ["AndroidManifest を読み取れませんでした"];
    expect(verdict(f).decision).toBe("needs_review");
  });
});

describe("describeFindings / FactsSchema", () => {
  it("理由を1行ずつの文章にする。参考(info)は既定では含めない", () => {
    const f = withPerms(["android.permission.INTERNET", "android.permission.CAMERA"]);
    f.zip.anomalies = ["不正"];
    const v = verdict(f);
    const lines = describeFindings(v);
    expect(lines.some((l) => l.startsWith("【却下】"))).toBe(true);
    expect(lines.some((l) => l.startsWith("【要確認】通信につながる権限を要求しています(INTERNET)"))).toBe(true);
    expect(lines.some((l) => l.startsWith("【参考】"))).toBe(false);
    expect(describeFindings(v, { includeInfo: true }).some((l) => l.startsWith("【参考】"))).toBe(true);
  });

  it("ワーカーが送る形式のデータをそのまま受け付ける", () => {
    expect(FactsSchema.safeParse(clean()).success).toBe(true);
    expect(FactsSchema.safeParse({ ...clean(), version: "x" }).success).toBe(false);
  });
});

// ---------------------------------------------------------------- 実際のAPKから集めた事実での確認
// (workers/scan の collectFacts を、実際にビルドしたAPKに対して実行した結果。ルールを変えたときの回帰確認用)

describe("実際のAPKでの判定", () => {
  const sig = { v1: false, v2: true, v3: true, subject: "CN=Example" };
  const run = (f: unknown) => {
    const parsed = FactsSchema.parse(f); // ワーカーの出力が、Web側の形式に合っていること
    return evaluatePolicy({ facts: parsed, signature: sig });
  };

  it("通信なしにした自作アプリ(Compose + DataStore)は、自動承認される。既知のAndroidXのネイティブライブラリだけなので", () => {
    const v = run(offlineApp);
    expect(v.findings).toEqual([]);
    expect(v.decision).toBe("auto_approve");
    expect(v.summary).toEqual({ network: "none", destruction: "none" });
  });

  it("縮小(R8)していないビルドの、通信しないアプリは、標準ライブラリの未使用コードにある java.net の参照があっても自動承認される", () => {
    // 実際のAPK: Kotlin 標準ライブラリの TextStreamsKt.readBytes が URL.openStream を参照している(権限なし)
    const v = run(unminifiedOffline);
    expect(v.decision).toBe("auto_approve");
    expect(v.summary).toEqual({ network: "none", destruction: "none" });
    expect(v.findings.map((f) => f.code)).toEqual(["net.api-inert"]);
    // 同じコードに INTERNET 権限が加われば、通信できるので要確認になる
    const withNet = FactsSchema.parse(structuredClone(unminifiedOffline));
    withNet.manifest!.usesPermissions.push({ name: "android.permission.INTERNET", maxSdk: null });
    const w = run(withNet);
    expect(w.decision).toBe("needs_review");
    expect(w.findings.map((f) => f.code)).toEqual(["net.permission", "net.api"]);
  });

  it("縮小していない AndroidX のアプリに含まれる参照(TelephonyManager・WebView.findAddress)は、権限が無ければ問題にしない", () => {
    const f = FactsSchema.parse(structuredClone(unminifiedOffline));
    f.dex!.apiHits["net.sms-telephony"] = { count: 3, examples: ["Landroidx/core/telephony/TelephonyManagerCompat$Api26Impl;.getImei → Landroid/telephony/TelephonyManager;.getImei (呼び出し)"], note: "SMS・電話回線" };
    f.dex!.apiHits["net.webview"] = { count: 1, examples: ["Landroidx/core/text/util/LinkifyCompat;.findAddress → Landroid/webkit/WebView;.findAddress (呼び出し)"], note: "WebView" };
    const v = run(f);
    expect(v.decision).toBe("auto_approve");
    expect(v.findings.map((x) => x.code)).toEqual(["net.api-inert"]);
    expect(v.findings[0].evidence.length).toBe(3);
  });

  it("地図を取得するアプリ(INTERNET・HTTPライブラリ・URLあり)は要確認になり、通信の可能性が示される", () => {
    const v = run(runTracker);
    expect(v.decision).toBe("needs_review");
    expect(v.summary.network).toBe("possible");
    const c = v.findings.map((f) => f.code);
    expect(c).toEqual(expect.arrayContaining(["net.permission", "net.api", "net.url-string"]));
    // 位置情報は読むだけの権限として参考に記録される
    expect(v.findings.find((f) => f.code === "perm.sensitive")?.severity).toBe("info");
  });

  it("WebView を使うアプリ・PDFライブラリ(Runtime.exec を含む)は、通信の可能性・外部プロセスの疑いで要確認になる", () => {
    expect(run(webviewApp).findings.map((f) => f.code)).toEqual(expect.arrayContaining(["net.api"]));
    expect(run(pdfboxApp).findings.map((f) => f.code)).toEqual(expect.arrayContaining(["code.dynamic"]));
  });
});
