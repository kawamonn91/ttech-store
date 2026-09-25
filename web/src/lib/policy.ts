import { z } from "zod";
import { KNOWN_NATIVE_LIBS } from "./known-native-libs";

/**
 * 第三者の開発者がアップロードしたAPKの自動審査ルール。
 *
 * 目的は2つ:
 *   1. 外部との通信機能がないこと
 *   2. 端末のデータを壊す・書き換える可能性がないこと
 * を、APKの中身(権限・コード・同梱ファイル)から確かめ、疑いが「まったくない」ときだけ自動で公開する。
 * 少しでも疑いがあれば、運営が理由を見て承認するまで公開しない(却下ではなく「要確認」)。
 * 明らかに不正なもの(構造の偽装・デバッグ署名・テスト用ビルド)だけは、その場で却下する。
 *
 * ここで判断するのは、ワーカー(workers/scan)が APK から集めた「事実」だけ。事実の集め方と、
 * この判断を分けているのは、ルールを直すたびに検査用の環境(GitHub Actions)を触らなくて済むようにするため。
 *
 * 前提: Android は、INTERNET 権限のないアプリのネットワーク通信を OS の仕組みで禁止する。
 * そのため「通信できない」ことの決め手は権限だが、
 *   - 他のアプリを経由した持ち出し(共有ユーザーID、公開されたコンポーネント、ブラウザを開くURL)
 *   - 後から読み込む別のコード(DEXの動的読み込み、隠したネイティブライブラリ)
 * は権限の外にあるので、コードと同梱ファイルまで調べて確かめる。
 */

export const POLICY_VERSION = 1;

// ---------------------------------------------------------------- 入力(ワーカーが送る事実)

const Hit = z.object({ count: z.number().int().nonnegative(), examples: z.array(z.string().max(300)).max(5), note: z.string().max(300).optional() });

export const FactsSchema = z.object({
  version: z.number().int(),
  manifest: z
    .object({
      package: z.string().max(200),
      versionCode: z.number().nullable(),
      versionName: z.string().max(200).nullable(),
      sharedUserId: z.string().max(200).nullable(),
      minSdk: z.number().nullable(),
      targetSdk: z.number().nullable(),
      debuggable: z.boolean(),
      testOnly: z.boolean(),
      hasCode: z.boolean(),
      usesPermissions: z.array(z.object({ name: z.string().max(200), maxSdk: z.number().nullable() })).max(500),
      declaredPermissions: z.array(z.object({ name: z.string().max(200), protectionLevel: z.number() })).max(200),
      components: z
        .array(
          z.object({
            kind: z.string().max(40),
            name: z.string().max(300),
            exported: z.boolean(),
            hasIntentFilter: z.boolean(),
            actions: z.array(z.string().max(200)).max(50),
            categories: z.array(z.string().max(200)).max(50),
            permission: z.string().max(200).nullable(),
            readPermission: z.string().max(200).nullable(),
            writePermission: z.string().max(200).nullable(),
            grantUriPermissions: z.boolean(),
            authorities: z.string().max(300).nullable(),
          }),
        )
        .max(1000),
      usesLibraries: z.array(z.string().max(200)).max(100),
    })
    .nullable(),
  zip: z.object({ entryCount: z.number(), totalSize: z.number(), anomalies: z.array(z.string().max(300)).max(20) }),
  dexFiles: z.array(z.string().max(100)).max(50),
  nativeLibs: z.array(z.object({ path: z.string().max(300), abi: z.string().max(40), size: z.number(), sha256: z.string().regex(/^[0-9a-f]{64}$/).nullable() })).max(100),
  embedded: z.array(z.object({ path: z.string().max(300), kind: z.string().max(40), size: z.number() })).max(100),
  dex: z
    .object({
      dexFiles: z.number().optional(),
      classes: z.number(),
      methods: z.number(),
      apiHits: z.record(z.string().max(80), Hit),
      stringHits: z.record(z.string().max(80), Hit),
      urlHosts: z.array(z.object({ host: z.string().max(300), count: z.number(), example: z.string().max(300) })).max(100),
      nativeMethods: z.object({ count: z.number(), examples: z.array(z.string().max(300)).max(5) }),
    })
    .nullable(),
  incomplete: z.array(z.string().max(400)).max(20),
});
export type Facts = z.infer<typeof FactsSchema>;

/** 署名の情報(apksigner の出力から) */
export const SignatureFactsSchema = z.object({
  v1: z.boolean(),
  v2: z.boolean(),
  v3: z.boolean(),
  subject: z.string().max(400).optional(),
});
export type SignatureFacts = z.infer<typeof SignatureFactsSchema>;

// ---------------------------------------------------------------- 出力

export type Severity = "block" | "review" | "info";
export type Category = "network" | "destruction" | "integrity" | "privacy" | "analysis";

export interface Finding {
  code: string;
  severity: Severity;
  category: Category;
  /** 一覧に出す短い見出し */
  title: string;
  /** 何が見つかったか・どう直せばよいか */
  detail: string;
  /** 根拠(権限名・呼び出し箇所など)。多いときは先頭のいくつか */
  evidence: string[];
}

export type Decision = "auto_approve" | "needs_review" | "reject";

export interface PolicyVerdict {
  version: number;
  decision: Decision;
  findings: Finding[];
  /** 画面に出す要約。none = 解析の結果、可能性は見つからなかった */
  summary: { network: "none" | "possible" | "unknown"; destruction: "none" | "possible" | "unknown" };
}

// ---------------------------------------------------------------- 権限の分類

/** 通信・近くの機器との通信につながる権限 */
const NETWORK_PERMISSIONS = new Set([
  "INTERNET",
  "CHANGE_NETWORK_STATE",
  "ACCESS_WIFI_STATE",
  "CHANGE_WIFI_STATE",
  "CHANGE_WIFI_MULTICAST_STATE",
  "NEARBY_WIFI_DEVICES",
  "BLUETOOTH",
  "BLUETOOTH_ADMIN",
  "BLUETOOTH_CONNECT",
  "BLUETOOTH_SCAN",
  "BLUETOOTH_ADVERTISE",
  "NFC",
  "NFC_TRANSACTION_EVENT",
  "UWB_RANGING",
  "TRANSMIT_IR",
  "USE_SIP",
  "MANAGE_NETWORK_POLICY",
]);

/** 端末のデータ・設定・他のアプリを書き換える・消せる権限、または悪用されやすい強い権限 */
const DESTRUCTIVE_PERMISSIONS = new Set([
  "WRITE_EXTERNAL_STORAGE",
  "MANAGE_EXTERNAL_STORAGE",
  "WRITE_CONTACTS",
  "WRITE_CALENDAR",
  "WRITE_CALL_LOG",
  "WRITE_SETTINGS",
  "WRITE_SECURE_SETTINGS",
  "WRITE_SMS",
  "WRITE_APN_SETTINGS",
  "WRITE_SYNC_SETTINGS",
  "WRITE_VOICEMAIL",
  "DELETE_PACKAGES",
  "REQUEST_DELETE_PACKAGES",
  "REQUEST_INSTALL_PACKAGES",
  "INSTALL_PACKAGES",
  "CLEAR_APP_USER_DATA",
  "CLEAR_APP_CACHE",
  "MASTER_CLEAR",
  "REBOOT",
  "SHUTDOWN",
  "BIND_DEVICE_ADMIN",
  "MANAGE_DEVICE_ADMINS",
  "SEND_SMS",
  "RECEIVE_SMS",
  "RECEIVE_MMS",
  "READ_SMS",
  "CALL_PHONE",
  "CALL_PRIVILEGED",
  "ANSWER_PHONE_CALLS",
  "PROCESS_OUTGOING_CALLS",
  "READ_CALL_LOG",
  "READ_PHONE_STATE",
  "READ_PHONE_NUMBERS",
  "BIND_ACCESSIBILITY_SERVICE",
  "BIND_NOTIFICATION_LISTENER_SERVICE",
  "BIND_VPN_SERVICE",
  "BIND_INPUT_METHOD",
  "BIND_WALLPAPER",
  "BIND_APPWIDGET",
  "SYSTEM_ALERT_WINDOW",
  "USE_FULL_SCREEN_INTENT",
  "KILL_BACKGROUND_PROCESSES",
  "FORCE_STOP_PACKAGES",
  "PACKAGE_USAGE_STATS",
  "QUERY_ALL_PACKAGES",
  "GET_ACCOUNTS",
  "GET_ACCOUNTS_PRIVILEGED",
  "AUTHENTICATE_ACCOUNTS",
  "MANAGE_ACCOUNTS",
  "READ_LOGS",
  "DUMP",
  "INTERACT_ACROSS_USERS",
  "INTERACT_ACROSS_USERS_FULL",
  "CAPTURE_AUDIO_OUTPUT",
  "MEDIA_CONTENT_CONTROL",
  "MODIFY_PHONE_STATE",
  "MOUNT_UNMOUNT_FILESYSTEMS",
  "MANAGE_USERS",
  "SET_DEBUG_APP",
  "READ_FRAME_BUFFER",
  "BROADCAST_SMS",
]);

/** 利用者のデータを読む・センサーを使う権限。通信機能がなければ外へは出せないので許すが、公開ページには載せて知らせる */
const SENSITIVE_ALLOWED_PERMISSIONS = new Set([
  "CAMERA",
  "RECORD_AUDIO",
  "ACCESS_FINE_LOCATION",
  "ACCESS_COARSE_LOCATION",
  "ACCESS_BACKGROUND_LOCATION",
  "ACCESS_MEDIA_LOCATION",
  "READ_MEDIA_IMAGES",
  "READ_MEDIA_VIDEO",
  "READ_MEDIA_AUDIO",
  "READ_MEDIA_VISUAL_USER_SELECTED",
  "READ_EXTERNAL_STORAGE",
  "READ_CONTACTS",
  "READ_CALENDAR",
  "ACTIVITY_RECOGNITION",
  "BODY_SENSORS",
  "BODY_SENSORS_BACKGROUND",
  "HIGH_SAMPLING_RATE_SENSORS",
  "READ_SYNC_SETTINGS",
]);

/** 問題のない権限(アプリの動作の補助) */
const SAFE_PERMISSIONS = new Set([
  "VIBRATE",
  "WAKE_LOCK",
  "POST_NOTIFICATIONS",
  "RECEIVE_BOOT_COMPLETED",
  "SCHEDULE_EXACT_ALARM",
  "USE_EXACT_ALARM",
  "USE_BIOMETRIC",
  "USE_FINGERPRINT",
  "FLASHLIGHT",
  "MODIFY_AUDIO_SETTINGS",
  "DISABLE_KEYGUARD",
  "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
  "ACCESS_NOTIFICATION_POLICY",
  "SET_ALARM",
  "EXPAND_STATUS_BAR",
  "BROADCAST_STICKY",
  "REORDER_TASKS",
  // 通信はしないが、ネットワークの状態を知るだけの権限(INTERNET がなければ通信はできない)
  "ACCESS_NETWORK_STATE",
]);

/** 他のアプリを開く・共有する操作の文字列。利用者が操作するもので、それだけでは問題にしない */
const INFO_STRING_IDS = new Set(["str.view-url", "str.send"]);

/** FOREGROUND_SERVICE と、その種類ごとの権限は許す */
const FOREGROUND_SERVICE = /^FOREGROUND_SERVICE(_[A-Z_]+)?$/;

/** 特別な権限を要求して、他のアプリの画面・通知・入力に触れるサービス(BIND_* で守られたサービス) */
const SENSITIVE_SERVICE_PERMISSIONS: Record<string, string> = {
  "android.permission.BIND_ACCESSIBILITY_SERVICE": "ユーザー補助サービス(他のアプリの画面を読み取り・操作できる)",
  "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE": "通知リスナー(他のアプリの通知を読み取れる)",
  "android.permission.BIND_DEVICE_ADMIN": "端末管理者(端末の初期化・ロックができる)",
  "android.permission.BIND_VPN_SERVICE": "VPNサービス(端末の通信をすべて経由できる)",
  "android.permission.BIND_INPUT_METHOD": "キーボード(入力内容をすべて読み取れる)",
  "android.permission.BIND_AUTOFILL_SERVICE": "自動入力サービス",
  "android.permission.BIND_CALL_REDIRECTION_SERVICE": "通話の転送",
  "android.permission.BIND_SCREENING_SERVICE": "着信の選別",
  "android.permission.BIND_INCALL_SERVICE": "通話画面",
  "android.permission.BIND_CONNECTION_SERVICE": "通話の接続",
  "android.permission.BIND_TEXT_SERVICE": "スペルチェック",
  "android.permission.BIND_VOICE_INTERACTION": "音声アシスタント",
  "android.permission.BIND_CARRIER_SERVICES": "携帯会社向けサービス",
};

// ---------------------------------------------------------------- ルール本体

function short(names: string[], max = 8): string[] {
  return names.length > max ? [...names.slice(0, max), `ほか${names.length - max}件`] : names;
}

function shortName(permission: string): string {
  return permission.replace(/^android\.permission\./, "");
}

export function evaluatePolicy(input: { facts: Facts | null; signature?: SignatureFacts | null; packageName?: string | null }): PolicyVerdict {
  const findings: Finding[] = [];
  const add = (f: Finding) => findings.push(f);
  const { facts, signature } = input;

  // ---- 事実が得られていない・解析が不完全 → 自動では承認できない
  if (!facts) {
    add({
      code: "analysis.missing",
      severity: "review",
      category: "analysis",
      title: "APKの自動解析の結果がありません",
      detail: "検査の仕組みが解析結果を返しませんでした。運営が内容を確認します。",
      evidence: [],
    });
    return finish(findings, false);
  }
  if (facts.incomplete.length > 0) {
    add({
      code: "analysis.incomplete",
      severity: "review",
      category: "analysis",
      title: "APKの一部を自動では解析できませんでした",
      detail: "解析できなかった部分に、通信や端末データの破壊につながる処理が含まれていないかを確かめられないため、運営が内容を確認します。",
      evidence: short(facts.incomplete),
    });
  }
  const m = facts.manifest;
  const complete = facts.incomplete.length === 0 && m !== null && (facts.dex !== null || (facts.dexFiles.length === 0 && !m.hasCode));

  // ---- APKの構造の異常(偽装の疑い)→ 却下
  if (facts.zip.anomalies.length > 0) {
    add({
      code: "zip.anomaly",
      severity: "block",
      category: "integrity",
      title: "APKの構造が不正です",
      detail: "同名ファイルの重複・不正なパス・暗号化されたファイルなど、正常なビルドでは起きない構造です。ビルドし直したAPKをアップロードしてください。",
      evidence: short(facts.zip.anomalies),
    });
  }

  // ---- 署名
  if (signature) {
    if (/CN=Android Debug/i.test(signature.subject ?? "")) {
      add({
        code: "sign.debug-cert",
        severity: "block",
        category: "integrity",
        title: "デバッグ用の署名です",
        detail: "Android Studio が自動で使うデバッグ鍵で署名されたAPKは公開できません。自分専用のリリース用の鍵(keystore)で署名してください。",
        evidence: [signature.subject ?? ""],
      });
    }
    if (!signature.v2 && !signature.v3) {
      add({
        code: "sign.v1-only",
        severity: "review",
        category: "integrity",
        title: "古い形式(v1)の署名だけです",
        detail: "v2 以降の署名がないAPKは、署名を壊さずに中身を書き換えられる既知の弱点があります。v2 / v3 署名を付けてビルドしてください。",
        evidence: [],
      });
    }
  }

  if (m) {
    if (m.testOnly) {
      add({
        code: "manifest.test-only",
        severity: "block",
        category: "integrity",
        title: "テスト用のビルド(testOnly)です",
        detail: "android:testOnly が有効なAPKは、通常の端末にインストールできません。リリースビルドをアップロードしてください。",
        evidence: [],
      });
    }
    if (m.debuggable) {
      add({
        code: "manifest.debuggable",
        severity: "review",
        category: "integrity",
        title: "デバッグ可能なビルドです",
        detail: "android:debuggable が有効だと、端末に接続した機器からアプリの内部を操作できます。リリースビルドをアップロードしてください。",
        evidence: [],
      });
    }
    if (m.targetSdk !== null && m.targetSdk < 28) {
      add({
        code: "manifest.old-target-sdk",
        severity: "review",
        category: "integrity",
        title: `古い targetSdkVersion(${m.targetSdk})です`,
        detail: "古い targetSdk のアプリは、共有ストレージへの自由な読み書きなど、近年のAndroidが制限している動作をそのまま使えます。targetSdk を 28 以上(できれば最新)にしてください。",
        evidence: [`targetSdkVersion=${m.targetSdk}`],
      });
    }
    if (m.sharedUserId) {
      add({
        code: "net.shared-user-id",
        severity: "review",
        category: "network",
        title: "共有ユーザーIDを使っています",
        detail: "同じ共有ユーザーIDの別のアプリと権限(通信の権限を含む)を共有できます。通信のできないアプリでも、別のアプリ経由で外へ出せる可能性があります。",
        evidence: [m.sharedUserId],
      });
    }

    // ---- 権限
    const own = (name: string) => (m.package && name.startsWith(`${m.package}.`)) || (input.packageName ? name.startsWith(`${input.packageName}.`) : false);
    const net: string[] = [];
    const destructive: string[] = [];
    const unknown: string[] = [];
    const sensitive: string[] = [];
    for (const p of m.usesPermissions) {
      const name = p.name;
      const short_ = shortName(name);
      if (name.startsWith("android.permission.") || !name.includes(".")) {
        if (NETWORK_PERMISSIONS.has(short_)) net.push(name);
        else if (DESTRUCTIVE_PERMISSIONS.has(short_)) destructive.push(name);
        else if (SENSITIVE_ALLOWED_PERMISSIONS.has(short_)) sensitive.push(name);
        else if (SAFE_PERMISSIONS.has(short_) || FOREGROUND_SERVICE.test(short_)) continue;
        else unknown.push(name);
      } else if (own(name)) {
        // 自分のアプリ専用の権限(AndroidX が自動で足す DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION など)
        const declared = m.declaredPermissions.find((d) => d.name === name);
        if (declared && declared.protectionLevel !== 2) unknown.push(name);
      } else {
        // 他社・他アプリ・Google の独自権限(プッシュ通知・課金・広告など。通信を伴うものが多い)
        unknown.push(name);
      }
    }
    if (net.length > 0) {
      add({
        code: "net.permission",
        severity: "review",
        category: "network",
        title: "通信につながる権限を要求しています",
        detail:
          "インターネット・Wi-Fi・Bluetooth・NFC などの権限は、端末の外へ情報を送る手段になります。通信が不要なアプリなら、これらの権限と、それを使うライブラリ(OkHttp・広告・解析など)を外してください。通信が必要なアプリは、運営が内容を確認したうえで公開します。",
        evidence: short(net.map(shortName)),
      });
    }
    if (destructive.length > 0) {
      add({
        code: "destroy.permission",
        severity: "review",
        category: "destruction",
        title: "端末のデータや設定を変更できる権限を要求しています",
        detail:
          "共有ストレージへの書き込み・連絡先やカレンダーの変更・アプリの削除やインストール・SMSや電話・他のアプリの画面の操作など、端末のデータを壊したり書き換えたりできる権限です。本当に必要な場合は、運営が内容を確認したうえで公開します。",
        evidence: short(destructive.map(shortName)),
      });
    }
    if (unknown.length > 0) {
      add({
        code: "perm.unknown",
        severity: "review",
        category: "privacy",
        title: "審査の対象外の権限を要求しています",
        detail: "自動審査が把握していない権限(独自の権限や、他社のサービス向けの権限)です。何のための権限かを運営が確認します。",
        evidence: short(unknown),
      });
    }
    if (sensitive.length > 0) {
      add({
        code: "perm.sensitive",
        severity: "info",
        category: "privacy",
        title: "利用者のデータ・センサーを読む権限を使います",
        detail: "位置情報・カメラ・マイク・写真・連絡先などを読む権限です。通信機能がないため端末の外には出せませんが、アプリの詳細ページで利用者に知らせます。",
        evidence: short(sensitive.map(shortName)),
      });
    }

    // ---- コンポーネント(他のアプリに公開されているもの・特別な権限で動くもの)
    const exposed: string[] = [];
    const special: string[] = [];
    for (const c of m.components) {
      const guard = c.permission ?? "";
      if (SENSITIVE_SERVICE_PERMISSIONS[guard]) {
        special.push(`${SENSITIVE_SERVICE_PERMISSIONS[guard]}: ${c.name}`);
        continue;
      }
      if (!c.exported) continue;
      if (c.kind === "provider") {
        // 公開されたContentProviderは、他のアプリがデータを読み書きできる。権限で守られていなければ要確認
        if (!c.permission && !(c.readPermission && c.writePermission)) exposed.push(`provider: ${c.name}`);
      } else if (c.kind === "service") {
        if (!c.permission) exposed.push(`service: ${c.name}`);
      }
    }
    if (special.length > 0) {
      add({
        code: "destroy.special-service",
        severity: "review",
        category: "destruction",
        title: "他のアプリや端末全体に触れる特別なサービスを含みます",
        detail: "ユーザー補助・通知の読み取り・VPN・キーボード・端末管理者などは、悪用されると端末のデータや他のアプリに大きな影響を与えます。運営が用途を確認します。",
        evidence: short(special),
      });
    }
    if (exposed.length > 0) {
      add({
        code: "net.exposed-component",
        severity: "review",
        category: "network",
        title: "他のアプリから自由に呼び出せるコンポーネントがあります",
        detail: "権限で守られていない公開のサービス・ContentProvider は、別のアプリとの間でデータのやり取りに使えます(通信できるアプリへ情報を渡す経路になりえます)。公開する必要がなければ android:exported=\"false\" にしてください。",
        evidence: short(exposed),
      });
    }
  }

  // ---- 同梱ファイル
  const unknownLibs = facts.nativeLibs.filter((l) => !l.sha256 || !(l.sha256 in KNOWN_NATIVE_LIBS));
  if (unknownLibs.length > 0) {
    add({
      code: "code.native-lib",
      severity: "review",
      category: "integrity",
      title: "自動では中身を確かめられないネイティブライブラリ(.so)を含みます",
      detail:
        "ネイティブコードは、通信や端末データの操作を含んでいても静的な解析ができません。AndroidX などの広く使われているライブラリは自動で認識しますが、それ以外は運営が確認します。ネイティブコードが不要なら外してください。",
      evidence: short(unknownLibs.map((l) => l.path)),
    });
  }
  const badEmbedded = facts.embedded.filter((e) => e.kind !== "java-class");
  if (badEmbedded.length > 0) {
    add({
      code: "code.embedded-executable",
      severity: "review",
      category: "integrity",
      title: "APKの中に、別の実行ファイルやアーカイブが隠されています",
      detail: "assets などに DEX・実行ファイル(ELF)・スクリプト・入れ子のZIP/APK が入っています。後から読み込んで実行できるため、内容を運営が確認します。",
      evidence: short(badEmbedded.map((e) => `${e.path} (${e.kind})`)),
    });
  }

  // ---- コード
  const dex = facts.dex;
  if (dex) {
    const hitIds = Object.keys(dex.apiHits);
    const byPrefix = (prefix: string) => hitIds.filter((id) => id.startsWith(prefix));
    const evidenceOf = (ids: string[]) => short(ids.flatMap((id) => dex.apiHits[id].examples.slice(0, 1).map((e) => `${dex.apiHits[id].note ?? id}: ${e}`)));

    const netIds = byPrefix("net.");
    if (netIds.length > 0) {
      add({
        code: "net.api",
        severity: "review",
        category: "network",
        title: "通信につながるAPIを呼び出すコードがあります",
        detail:
          "ソケット・HTTP・WebView・ダウンロード・Bluetooth など、外部と通信するAPIが使われています(組み込んだライブラリの中にあるものも含みます)。通信が不要なら、そのライブラリを外してください。",
        evidence: evidenceOf(netIds),
      });
    }
    if (dex.urlHosts.length > 0) {
      add({
        code: "net.url-string",
        severity: "review",
        category: "network",
        title: "外部のURLがコードに含まれています",
        detail:
          "ウェブサイトへのリンクなどのURLは、ブラウザ経由で情報を外へ出すのに使えます(URLに利用者のデータを付ける、など)。リンクが必要なときは、運営が用途を確認します。",
        evidence: short(dex.urlHosts.map((h) => h.host)),
      });
    }
    const loaders = hitIds.filter((id) => id === "code.dex-loader" || id === "code.exec");
    if (loaders.length > 0) {
      add({
        code: "code.dynamic",
        severity: "review",
        category: "integrity",
        title: "後から別のコードを読み込む・外部プログラムを起動する処理があります",
        detail: "DEXの動的読み込みや外部プロセスの起動は、審査の後に動作を変えられる手段になります。運営が用途を確認します。",
        evidence: evidenceOf(loaders),
      });
    }
    if ((dex.apiHits["code.native-load"]?.count ?? 0) > 0 && facts.nativeLibs.length === 0) {
      add({
        code: "code.native-load-external",
        severity: "review",
        category: "integrity",
        title: "APKに含まれないネイティブライブラリを読み込む処理があります",
        detail: "System.load / loadLibrary が呼ばれているのに、対応する .so がAPKの中にありません。外部から取得した(または隠した)コードを読み込む可能性があります。",
        evidence: evidenceOf(["code.native-load"]),
      });
    }
    const destroyIds = byPrefix("destroy.");
    if (destroyIds.length > 0) {
      add({
        code: "destroy.api",
        severity: "review",
        category: "destruction",
        title: "端末のデータ・設定・他のアプリを操作できるAPIを呼び出すコードがあります",
        detail: "端末管理者・アプリの削除やインストール・設定の書き換え・フォルダ内のファイルの削除・他のアプリの画面の操作などのAPIです。運営が用途を確認します。",
        evidence: evidenceOf(destroyIds),
      });
    }
    const stringIds = Object.keys(dex.stringHits).filter((id) => !INFO_STRING_IDS.has(id));
    if (stringIds.length > 0) {
      add({
        code: "destroy.string",
        severity: "review",
        category: "destruction",
        title: "端末のファイルやアプリへの強い操作を求める処理があります",
        detail: "フォルダ全体・すべてのファイルへのアクセスや、APKのインストールを求める処理です。運営が用途を確認します。",
        evidence: short(stringIds.map((id) => `${dex.stringHits[id].note ?? id}: ${dex.stringHits[id].examples[0] ?? ""}`)),
      });
    }
  }

  return finish(findings, complete);
}

function finish(findings: Finding[], complete: boolean): PolicyVerdict {
  const has = (cat: Category) => findings.some((f) => f.category === cat && f.severity !== "info");
  const decision: Decision = findings.some((f) => f.severity === "block") ? "reject" : findings.some((f) => f.severity === "review") ? "needs_review" : "auto_approve";
  // 解析が完全に終わり、その分野で引っかかったものがなければ「可能性なし」と言える
  const state = (cat: Category): "none" | "possible" | "unknown" => (has(cat) ? "possible" : complete ? "none" : "unknown");
  return { version: POLICY_VERSION, decision, findings, summary: { network: state("network"), destruction: state("destruction") } };
}

/** 却下・要確認の理由を、1行ずつの文章にする(メール・一覧用) */
export function describeFindings(verdict: PolicyVerdict, opts: { includeInfo?: boolean } = {}): string[] {
  return verdict.findings
    .filter((f) => f.severity !== "info" || opts.includeInfo)
    .map((f) => `${f.severity === "block" ? "【却下】" : f.severity === "review" ? "【要確認】" : "【参考】"}${f.title}${f.evidence.length ? `(${f.evidence.slice(0, 5).join(", ")})` : ""}`);
}
