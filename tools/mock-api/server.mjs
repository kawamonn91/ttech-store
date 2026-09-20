#!/usr/bin/env node
// ストアアプリの動作確認用モックAPI(Supabase/R2 なしで /api/v1 を再現する。外部依存なし)
//
//   node tools/mock-api/server.mjs <apk> [port]
//
// 指定した APK から package / versionCode / SHA-256 / 署名証明書を実際に読み取り、次の3つのアプリとして公開する:
//   good      … 正しいメタデータ(インストールできるはず)
//   tampered  … SHA-256 を1文字だけ改ざん(ハッシュ検証で拒否されるはず)
//   badcert   … 署名証明書のハッシュが違う(署名検証で拒否されるはず)
// エミュレータからは http://10.0.2.2:<port>/api/v1 で接続する(store-app/local.properties の storeApiBase)。

import { execFileSync } from "node:child_process";
import { createReadStream, statSync } from "node:fs";
import { createServer } from "node:http";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { findBuildTool, parseApksigner, parseBadging } from "../../workers/scan/inspect.mjs";

const apk = process.argv[2];
const port = Number(process.argv[3] ?? 8787);
if (!apk) {
  console.error("usage: node server.mjs <apk> [port]");
  process.exit(1);
}
const isWin = process.platform === "win32";

const sha256 = createHash("sha256").update(readFileSync(apk)).digest("hex");
const signer = parseApksigner(execFileSync(findBuildTool("apksigner"), ["verify", "-v", "--print-certs", apk], { encoding: "utf8", shell: isWin }));
const info = parseBadging(execFileSync(findBuildTool("aapt2"), ["dump", "badging", apk], { encoding: "utf8", maxBuffer: 50e6 }));
if (!signer.verified || signer.certs.length !== 1 || !info) throw new Error("APK を読み取れません");
const size = statSync(apk).size;
console.log(`APK: ${info.packageName} v${info.versionName} (${info.versionCode}) size=${size} sha256=${sha256.slice(0, 12)}… cert=${signer.certs[0].slice(0, 12)}…`);

const flip = (hex) => (hex[0] === "0" ? "1" : "0") + hex.slice(1);
const variants = {
  good: { sha256, cert: signer.certs[0] },
  tampered: { sha256: flip(sha256), cert: signer.certs[0] },
  badcert: { sha256, cert: flip(signer.certs[0]) },
};

const latest = (id) => ({
  releaseId: id,
  versionName: info.versionName,
  versionCode: info.versionCode,
  apkSize: size,
  minSdk: info.minSdk ?? null,
  permissions: info.permissions,
  releaseNotes: `テスト用リリース (${id})`,
  publishedAt: new Date().toISOString(),
});

const apps = Object.keys(variants).map((key, i) => ({
  id: `00000000-0000-4000-8000-00000000000${i + 1}`,
  slug: key === "good" ? "yomumemo" : `yomumemo-${key}`,
  packageName: info.packageName,
  name: key === "good" ? "ヨムメモ(モック)" : `ヨムメモ(${key})`,
  shortDesc: key === "good" ? "正しいメタデータ" : "検証で拒否されるはず",
  description: "モックAPIが返すテスト用のアプリです。\n\nストアアプリのダウンロード・検証・インストールを確認します。",
  iconUrl: null,
  screenshots: [],
  category: { slug: "hobby", name: "趣味・ホビー" },
  developerName: "T-tech",
  downloadCount: 1234 * (i + 1),
  ratingAvg: 4.5,
  ratingCount: 10,
  latest: latest(`00000000-0000-4000-8000-0000000001${i + 1}0`),
  _variant: key,
}));
const strip = ({ _variant, description, screenshots, developerName, ...summary }) => summary;

const json = (res, body, status = 200) => {
  res.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(body));
};

createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const path = url.pathname;
  console.log(req.method, path);

  if (path === "/api/v1/home") return json(res, { featured: apps.slice(0, 1).map(strip), newest: apps.map(strip), popular: apps.map(strip) });
  if (path === "/api/v1/categories") return json(res, { items: [{ slug: "hobby", name: "趣味・ホビー" }] });
  if (path === "/api/v1/apps") {
    const q = url.searchParams.get("q");
    const items = apps.filter((a) => !q || a.name.includes(q)).map(strip);
    return json(res, { items, total: items.length });
  }
  const bySlug = path.match(/^\/api\/v1\/apps\/([^/]+)$/);
  if (bySlug) {
    const a = apps.find((x) => x.slug === bySlug[1]);
    return a ? json(res, a) : json(res, { error: "アプリが見つかりません" }, 404);
  }
  if (path === "/api/v1/index") {
    // 良いアプリだけを返す(同じ package の重複を避ける)
    const a = apps[0];
    return json(res, { items: [{ slug: a.slug, packageName: a.packageName, name: a.name, iconUrl: null, latest: a.latest }] });
  }
  const dl = path.match(/^\/api\/v1\/releases\/([^/]+)\/download$/);
  if (dl && req.method === "POST") {
    const a = apps.find((x) => x.latest.releaseId === dl[1]);
    if (!a) return json(res, { error: "このアプリは現在ダウンロードできません" }, 404);
    const v = variants[a._variant];
    return json(res, {
      url: `http://10.0.2.2:${port}/apk/${a.latest.releaseId}.apk`,
      sha256: v.sha256,
      signingCertSha256: v.cert,
      apkSize: size,
      versionCode: info.versionCode,
      packageName: info.packageName,
      expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
    });
  }
  if (path.startsWith("/apk/")) {
    // Range 対応(再開ダウンロードの確認用)
    const range = req.headers.range?.match(/bytes=(\d+)-(\d*)/);
    if (range) {
      const start = Number(range[1]);
      const end = range[2] ? Number(range[2]) : size - 1;
      res.writeHead(206, { "content-range": `bytes ${start}-${end}/${size}`, "content-length": end - start + 1, "content-type": "application/vnd.android.package-archive" });
      return createReadStream(apk, { start, end }).pipe(res);
    }
    res.writeHead(200, { "content-length": size, "accept-ranges": "bytes", "content-type": "application/vnd.android.package-archive" });
    return createReadStream(apk).pipe(res);
  }
  json(res, { error: "not found" }, 404);
}).listen(port, "0.0.0.0", () => console.log(`mock API on http://localhost:${port}/api/v1`));
