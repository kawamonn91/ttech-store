#!/usr/bin/env node
// APK 検査ワーカー(GitHub Actions / ローカルの両方で動く。外部依存なし)
//
// 入力(環境変数):
//   RELEASE_ID          対象リリースのID
//   DOWNLOAD_URL        APK を取得する署名付きURL(サーバーが発行した期限付き)
//   CALLBACK_URL        検査結果を POST する先(/api/internal/releases/:id/scan)
//   SCAN_WEBHOOK_SECRET 結果に HMAC 署名を付ける共有シークレット
//   VT_API_KEY          (任意) VirusTotal API キー。無ければウイルススキャンは skipped
//   ANDROID_HOME        aapt2 / apksigner を探す Android SDK の場所
//
// やること: SHA-256 算出 → apksigner で署名検証・証明書取得 → aapt2 で package/version/権限取得
//           → VirusTotal 照会 → 結果を署名付きで POST。失敗時も { error } を POST して却下扱いにする。

import { createHash, createHmac } from "node:crypto";
import { execFile } from "node:child_process";
import { createWriteStream, existsSync, readdirSync, statSync } from "node:fs";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { pathToFileURL } from "node:url";
import { promisify } from "node:util";
import { collectFacts } from "./facts.mjs";

const run = promisify(execFile);
const isWin = process.platform === "win32";

function need(name) {
  const v = process.env[name];
  if (!v) throw new Error(`環境変数 ${name} が未設定です`);
  return v;
}

/** Android SDK の build-tools から最新版の実行ファイルを探す */
export function findBuildTool(name) {
  const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  if (!sdk) throw new Error("ANDROID_HOME が未設定です");
  const root = join(sdk, "build-tools");
  if (!existsSync(root)) throw new Error(`${root} がありません(Android SDK build-tools をインストールしてください)`);
  const versions = readdirSync(root)
    .filter((d) => statSync(join(root, d)).isDirectory())
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))
    .reverse();
  const exe = (n) => (isWin ? `${n}.exe` : n);
  const file = name === "apksigner" ? (isWin ? "apksigner.bat" : "apksigner") : name === "dexdump" ? exe("dexdump") : exe("aapt2");
  for (const v of versions) {
    const p = join(root, v, file);
    if (existsSync(p)) return p;
  }
  throw new Error(`build-tools に ${file} が見つかりません`);
}

async function download(url, dest) {
  const res = await fetch(url);
  if (!res.ok || !res.body) throw new Error(`APK のダウンロードに失敗しました (${res.status})`);
  await pipeline(Readable.fromWeb(res.body), createWriteStream(dest));
}

async function sha256(file) {
  const { createReadStream } = await import("node:fs");
  const hash = createHash("sha256");
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest("hex");
}

/** `apksigner verify --print-certs` の出力から、検証結果と署名者の証明書 SHA-256 を取り出す */
export function parseApksigner(output) {
  const verified = /^Verifies$/m.test(output);
  // 出力形式は2通り: "Signer #1 certificate SHA-256 digest: ..."(-v なし)と
  // "V2 Signer: certificate SHA-256 digest: ..."(-v あり。v1/v2/v3 それぞれの行に同じ証明書が出る)
  const found = [
    ...output.matchAll(/^(?:Signer #\d+|V\d+(?:\.\d+)? Signer(?: \(minSdk=\d+\))?):? certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$/gm),
  ].map((m) => m[1].toLowerCase());
  return { verified, certs: [...new Set(found)] };
}

/** `apksigner verify -v --print-certs` の出力から、署名方式(v1/v2/v3)と証明書の持ち主(DN)を取り出す */
export function parseSignatureFacts(output) {
  const scheme = (v) => new RegExp(`Verified using ${v} scheme[^\n]*: true`, "i").test(output);
  const dn = output.match(/certificate DN: (.+)$/m);
  return { v1: scheme("v1"), v2: scheme("v2"), v3: scheme("v3(?:\.1)?"), subject: dn ? dn[1].trim().slice(0, 300) : undefined };
}

/** `aapt2 dump badging` の出力から package / version / SDK / 権限を取り出す */
export function parseBadging(output) {
  const pkg = output.match(/^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'/m);
  if (!pkg) return null;
  const num = (re) => {
    const m = output.match(re);
    return m ? Number(m[1]) : undefined;
  };
  const permissions = [...output.matchAll(/^uses-permission(?:-sdk-\d+)?: name='([^']+)'/gm)].map((m) => m[1]);
  return {
    packageName: pkg[1],
    versionCode: Number(pkg[2]),
    versionName: pkg[3],
    // 新しい aapt2 は minSdkVersion、古いものは sdkVersion と出力する
    minSdk: num(/^(?:minSdkVersion|sdkVersion):'(\d+)'/m),
    targetSdk: num(/^targetSdkVersion:'(\d+)'/m),
    permissions: [...new Set(permissions)],
  };
}

const VT = "https://www.virustotal.com/api/v3";

async function virusTotal(hash, file, size, apiKey) {
  if (!apiKey) return { status: "skipped" };
  const headers = { "x-apikey": apiKey };
  const permalink = `https://www.virustotal.com/gui/file/${hash}`;

  const summarize = (stats) => {
    const malicious = stats?.malicious ?? 0;
    const suspicious = stats?.suspicious ?? 0;
    // 1〜2件の誤検知で止めないよう、悪意ありが3件以上のときだけ「検出」とする(運営が詳細で最終判断)
    return { status: malicious >= 3 ? "flagged" : "clean", malicious, suspicious, permalink };
  };

  let res = await fetch(`${VT}/files/${hash}`, { headers });
  if (res.ok) return summarize((await res.json()).data.attributes.last_analysis_stats);
  if (res.status !== 404) return { status: "skipped" };

  // 未知のファイル: アップロードして解析を待つ(無料枠は 32MB まで。超える場合はスキップ)
  if (size > 32 * 1024 * 1024) return { status: "skipped" };
  const { readFile } = await import("node:fs/promises");
  const form = new FormData();
  form.append("file", new Blob([await readFile(file)]), "app.apk");
  res = await fetch(`${VT}/files`, { method: "POST", headers, body: form });
  if (!res.ok) return { status: "skipped" };
  const analysisId = (await res.json()).data.id;

  for (let i = 0; i < 12; i++) {
    await new Promise((r) => setTimeout(r, 15_000));
    const a = await fetch(`${VT}/analyses/${analysisId}`, { headers });
    if (!a.ok) continue;
    const attrs = (await a.json()).data.attributes;
    if (attrs.status === "completed") return summarize(attrs.stats);
  }
  return { status: "pending", permalink };
}

// web/scripts/publish-app.mjs からも直接呼べるよう export する(GitHub Actions からの利用が main())
export async function inspect(file) {
  const size = statSync(file).size;
  const hash = await sha256(file);

  // -v を付けないと成功時に "Verifies" が出力されない。Windows の .bat は shell 経由でないと起動できない
  const signer = await run(findBuildTool("apksigner"), ["verify", "-v", "--print-certs", file], {
    maxBuffer: 10 * 1024 * 1024,
    shell: isWin,
  }).catch((e) => ({ stdout: e.stdout ?? "", stderr: e.stderr ?? String(e), failed: true }));
  const { verified, certs } = parseApksigner(signer.stdout);
  if (signer.failed || !verified) {
    return { error: `APK の署名を検証できませんでした: ${(signer.stderr || signer.stdout).trim().slice(0, 300)}` };
  }

  const badging = await run(findBuildTool("aapt2"), ["dump", "badging", file], { maxBuffer: 20 * 1024 * 1024 }).catch(() => null);
  const info = badging && parseBadging(badging.stdout);
  if (!info) return { error: "AndroidManifest を読み取れませんでした" };

  const vt = await virusTotal(hash, file, size, process.env.VT_API_KEY).catch(() => ({ status: "skipped" }));

  // 審査に使う事実(権限・コード・同梱ファイル)。dexdump が無い環境では、その部分は「解析できなかった」として記録する
  let dexdump = null;
  try {
    dexdump = findBuildTool("dexdump");
  } catch {
    // 無ければ null のまま(facts.incomplete に理由が残り、自動承認されない)
  }
  const facts = await collectFacts(file, { aapt2: findBuildTool("aapt2"), dexdump }).catch((e) => ({
    version: 1, manifest: null, zip: { entryCount: 0, totalSize: 0, anomalies: [] }, dexFiles: [], nativeLibs: [], embedded: [], dex: null,
    incomplete: [`事実の収集に失敗しました: ${e instanceof Error ? e.message.slice(0, 200) : e}`],
  }));

  return {
    ...info,
    apkSize: size,
    sha256: hash,
    signingCertSha256: certs[0],
    signerCount: certs.length,
    virusTotal: vt,
    signature: parseSignatureFacts(signer.stdout),
    facts,
  };
}

async function main() {
  const releaseId = need("RELEASE_ID");
  const callback = need("CALLBACK_URL");
  const secret = need("SCAN_WEBHOOK_SECRET");

  const dir = await mkdtemp(join(tmpdir(), "apk-scan-"));
  let result;
  try {
    const file = join(dir, "app.apk");
    await download(need("DOWNLOAD_URL"), file);
    result = await inspect(file);
  } catch (e) {
    result = { error: e instanceof Error ? e.message : String(e) };
  } finally {
    await rm(dir, { recursive: true, force: true });
  }

  const body = JSON.stringify(result);
  const signature = createHmac("sha256", secret).update(body).digest("hex");
  const res = await fetch(callback, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-signature": signature },
    body,
  });
  console.log(`release ${releaseId}: ${res.status} ${await res.text()}`);
  if (!res.ok) process.exit(1);
}

// テストから import できるよう、直接実行されたときだけ main を走らせる
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}
