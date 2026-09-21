#!/usr/bin/env node
// APKをビルドした後、ブラウザの管理コンソールを使わずに
// 「アプリ登録(初回のみ)→ アップロード → 検査 → (--publish時のみ)公開」までを1コマンドで行う。
//
// 使い方:
//   node scripts/publish-app.mjs --apk <path> --name "表示名" [--slug xxx] [--short-desc "..."]
//     [--description "..."] [--category カテゴリのslug] [--notes "リリースノート"] [--publish]
//
// パッケージ名はAPK自体(AndroidManifest)から読み取る。既に登録済みのパッケージ名なら
// 新しいバージョンとして追加するだけでよく、--name 等は省略できる。
//
// 検査(署名検証・パッケージ情報・ウイルススキャン)はローカルのAPKに対してその場で行う
// (GitHub Actions は経由しない。CIでの検査は Web からの通常アップロード経路でのみ使う)。
// 判定ロジックは web/src/lib/scan.ts の decideScan と同じ内容をここでも直接実行する
// (TypeScript側から直接importできないための重複。ロジックを変えたら両方直すこと。
//  対応するテストは web/src/lib/scan.test.ts と、このファイル用の scripts/publish-app.test.mjs)。

import { randomUUID } from "node:crypto";
import { existsSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { AwsClient } from "aws4fetch";
import { Agent, fetch as ufetch } from "undici";
import { inspect } from "../../workers/scan/inspect.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(here, "..");

/** .env.local を読み込む(dotenv を増やさず、これまでの調査と同じ簡易パーサーを使う) */
function loadEnvLocal() {
  const file = path.join(webRoot, ".env.local");
  if (!existsSync(file)) return;
  for (const line of readFileSync(file, "utf8").split("\n")) {
    if (!line.includes("=") || line.startsWith("#")) continue;
    const i = line.indexOf("=");
    const key = line.slice(0, i).trim();
    const value = line.slice(i + 1).trim();
    if (key && process.env[key] === undefined) process.env[key] = value;
  }
}

function parseArgs(argv) {
  const out = { publish: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--publish") {
      out.publish = true;
      continue;
    }
    if (a.startsWith("--")) {
      out[a.slice(2)] = argv[++i];
    }
  }
  return out;
}

function need(env, name) {
  const v = env[name];
  if (!v) throw new Error(`環境変数 ${name} が未設定です(.env.local を確認してください)`);
  return v;
}

/** よくある語だけのセグメントは避け、パッケージ名から「そのアプリらしい」部分をスラッグ案として拾う */
function suggestSlug(packageName) {
  const generic = new Set(["app", "android", "mobile", "client", "www", "com", "co", "jp", "org", "net", "io"]);
  const parts = packageName.split(".").filter(Boolean);
  for (let i = parts.length - 1; i >= 0; i--) {
    const seg = parts[i]
      .toLowerCase()
      .replace(/[_\s]+/g, "-")
      .replace(/[^a-z0-9-]/g, "");
    if (seg && !generic.has(seg)) return seg;
  }
  return parts.at(-1)?.toLowerCase() ?? "app";
}

/** web/src/lib/scan.ts の decideScan と同じ判定(ロジックを変えたら両方直すこと) */
export function decideScan(result, app) {
  const reject = (reason) => ({ status: "rejected", reason, columns: { scan_result: { ...result, error: reason } } });

  if (result.error) return reject(result.error);
  if (!result.sha256 || !result.signingCertSha256 || !result.versionCode || !result.packageName) {
    return reject("APKから必要な情報を読み取れませんでした");
  }
  if (result.signerCount !== undefined && result.signerCount !== 1) {
    return reject("署名者が1つでないAPKは対応していません");
  }
  if (result.packageName !== app.package_name) {
    return reject(`パッケージ名がアプリの登録内容と一致しません (${result.packageName} ≠ ${app.package_name})`);
  }
  if (app.signing_cert_sha256 && app.signing_cert_sha256 !== result.signingCertSha256) {
    return reject("署名鍵が既存のアプリと一致しません");
  }
  if (result.virusTotal?.status === "flagged") return reject("ウイルススキャンで検出されました");

  return {
    status: "scanned",
    columns: {
      scan_result: result,
      version_name: result.versionName ?? String(result.versionCode),
      version_code: result.versionCode,
      apk_size: result.apkSize ?? null,
      sha256: result.sha256,
      signing_cert_sha256: result.signingCertSha256,
      min_sdk: result.minSdk ?? null,
      target_sdk: result.targetSdk ?? null,
      permissions: result.permissions,
    },
  };
}

class SupabaseAdmin {
  constructor(url, serviceRoleKey) {
    this.base = `${url}/rest/v1`;
    this.headers = { apikey: serviceRoleKey, Authorization: `Bearer ${serviceRoleKey}`, "Content-Type": "application/json" };
  }

  async request(path, init = {}) {
    const res = await ufetch(`${this.base}${path}`, { ...init, headers: { ...this.headers, ...(init.headers ?? {}) } });
    if (!res.ok) throw new Error(`Supabase ${init.method ?? "GET"} ${path} -> ${res.status}: ${await res.text()}`);
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  }

  select(table, query) {
    return this.request(`/${table}?${query}`);
  }

  insert(table, row) {
    return this.request(`/${table}`, { method: "POST", headers: { Prefer: "return=representation" }, body: JSON.stringify(row) });
  }

  update(table, query, patch) {
    return this.request(`/${table}?${query}`, { method: "PATCH", body: JSON.stringify(patch) });
  }
}

/** R2への直接アップロード。管理コンソールと違い、このスクリプトはR2の秘密鍵を直接持っているので
 *  署名付きURLを経由せず、その場で署名して PUT する。 */
class R2 {
  constructor(env) {
    this.client = new AwsClient({
      accessKeyId: need(env, "R2_ACCESS_KEY_ID"),
      secretAccessKey: need(env, "R2_SECRET_ACCESS_KEY"),
      service: "s3",
      region: "auto",
    });
    this.base = `https://${need(env, "R2_ACCOUNT_ID")}.r2.cloudflarestorage.com/${need(env, "R2_BUCKET")}`;
    // このPCではVPN/ゼロトラストクライアントの影響でIPv6経路がタイムアウトすることがあるため、
    // R2向けの通信だけIPv4接続に固定する(詳細: web/src/lib/r2.ts の同様のコメント)。
    this.agent = new Agent({ connect: { family: 4 } });
  }

  async put(key, bytes) {
    const url = new URL(`${this.base}/${key.split("/").map(encodeURIComponent).join("/")}`);
    url.searchParams.set("X-Amz-Expires", "300");
    const signed = await this.client.sign(url.toString(), { method: "PUT", aws: { signQuery: true } });
    const res = await ufetch(signed.url, { method: "PUT", body: bytes, dispatcher: this.agent });
    if (!res.ok) throw new Error(`R2 PUT failed: ${res.status} ${await res.text()}`);
  }
}

async function main() {
  loadEnvLocal();
  const args = parseArgs(process.argv.slice(2));
  if (!args.apk) throw new Error("--apk <APKファイルのパス> を指定してください");
  const apkPath = path.resolve(args.apk);
  if (!existsSync(apkPath)) throw new Error(`APKが見つかりません: ${apkPath}`);
  if (!apkPath.toLowerCase().endsWith(".apk")) throw new Error("APKファイル(.apk)を指定してください(AABは配布できません)");

  const supabaseUrl = need(process.env, "NEXT_PUBLIC_SUPABASE_URL");
  const db = new SupabaseAdmin(supabaseUrl, need(process.env, "SUPABASE_SERVICE_ROLE_KEY"));
  const r2 = new R2(process.env);
  const adminUserId = need(process.env, "ADMIN_USER_ID");

  console.log(`APKを検査しています: ${apkPath}`);
  const result = await inspect(apkPath);
  if (result.error) {
    console.error(`検査エラー: ${result.error}`);
    process.exit(1);
  }
  console.log(`  package=${result.packageName} version=${result.versionName} (${result.versionCode}) size=${(statSync(apkPath).size / 1024 / 1024).toFixed(1)}MB`);

  // アプリの検索/登録
  let [app] = await db.select("apps", `package_name=eq.${encodeURIComponent(result.packageName)}&select=id,slug,package_name,status,signing_cert_sha256`);
  if (!app) {
    if (!args.name) throw new Error(`パッケージ ${result.packageName} は未登録です。新規登録には --name が必要です`);
    await ensureDeveloper(db, adminUserId);
    const slug = args.slug || suggestSlug(result.packageName);
    let categoryId;
    if (args.category) {
      const [cat] = await db.select("categories", `slug=eq.${encodeURIComponent(args.category)}&select=id`);
      categoryId = cat?.id;
    }
    [app] = await db.insert("apps", {
      slug,
      package_name: result.packageName,
      developer_id: adminUserId,
      name: args.name,
      short_desc: args["short-desc"] ?? "",
      description: args.description ?? "",
      category_id: categoryId ?? null,
      status: "draft",
    });
    console.log(`  新規アプリを登録しました: slug=${app.slug}`);
  } else {
    console.log(`  既存アプリに新バージョンを追加します: slug=${app.slug} (現在の状態: ${app.status})`);
  }

  // リリース作成 + アップロード
  const releaseId = randomUUID();
  const apkKey = `apk/${app.id}/${releaseId}.apk`;
  await db.insert("app_releases", { id: releaseId, app_id: app.id, apk_key: apkKey, release_notes: args.notes ?? "", status: "uploaded" });
  console.log("  R2へアップロード中…");
  await r2.put(apkKey, readFileSync(apkPath));

  // 検査結果の反映(GitHub Actions を経由せず、その場で判定する)
  const decision = decideScan(result, app);
  await db.update("app_releases", `id=eq.${releaseId}`, { ...decision.columns, status: decision.status });

  if (decision.status === "rejected") {
    console.error(`却下されました: ${decision.reason}`);
    process.exit(1);
  }
  console.log(`検査に合格しました(承認待ち)。ウイルススキャン: ${result.virusTotal?.status ?? "skipped"}`);
  console.log(`  管理コンソール: https://store.kawamonn.com/admin/apps/${app.id}`);

  if (!args.publish) {
    console.log("公開するには --publish を付けて実行してください(このリリースだけ再実行することもできます)。");
    return;
  }

  const pub = await db.update("app_releases", `id=eq.${releaseId}`, { status: "published", reviewed_by: adminUserId }).catch((e) => {
    throw new Error(`公開に失敗しました(署名鍵の不一致など): ${e.message}`);
  });
  void pub;
  if (app.status !== "published") {
    await db.update("apps", `id=eq.${app.id}`, { status: "published" });
  }
  console.log(`公開しました: https://store.kawamonn.com/apps/${app.slug}`);
}

async function ensureDeveloper(db, userId) {
  const [existing] = await db.select("developers", `user_id=eq.${userId}&select=user_id`);
  if (existing) return;
  await db.insert("developers", { user_id: userId, name: "T-tech", contact_email: "", status: "approved", verified_at: new Date().toISOString() });
}

// テストから import できるよう、直接実行されたときだけ main を走らせる
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((e) => {
    console.error(e.message || e);
    process.exit(1);
  });
}
