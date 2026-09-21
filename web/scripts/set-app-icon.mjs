#!/usr/bin/env node
// アプリのストア掲載アイコンを設定する。SVG(ランチャーアイコンと同じ意匠でよい)から
// PNGを生成してSupabase Storage(app-media)にアップロードし、apps.icon_path を更新する。
//
// 使い方: node scripts/set-app-icon.mjs --slug <slug> --svg <path-to-svg> [--size 512]

import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";
import { loadEnvLocal, parseArgs, need } from "./lib/cli-env.mjs";

async function main() {
  loadEnvLocal();
  const args = parseArgs(process.argv.slice(2));
  const slug = need(args, "slug");
  const svgPath = need(args, "svg");
  const size = Number(args.size ?? 512);

  const url = need(process.env, "NEXT_PUBLIC_SUPABASE_URL");
  const serviceKey = need(process.env, "SUPABASE_SERVICE_ROLE_KEY");
  const headers = { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` };

  const appRes = await fetch(`${url}/rest/v1/apps?slug=eq.${encodeURIComponent(slug)}&select=id,name`, { headers });
  if (!appRes.ok) throw new Error(`アプリ取得に失敗: ${appRes.status} ${await appRes.text()}`);
  const [app] = await appRes.json();
  if (!app) throw new Error(`アプリが見つかりません: slug=${slug}`);

  const svg = readFileSync(path.resolve(svgPath));
  const png = await sharp(svg).resize(size, size).png().toBuffer();

  const objectPath = `${app.id}/icon-${Date.now()}.png`;
  const upRes = await fetch(`${url}/storage/v1/object/app-media/${objectPath}`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "image/png", "x-upsert": "true" },
    body: png,
  });
  if (!upRes.ok) throw new Error(`アップロードに失敗: ${upRes.status} ${await upRes.text()}`);

  const patchRes = await fetch(`${url}/rest/v1/apps?id=eq.${app.id}`, {
    method: "PATCH",
    headers: { ...headers, "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify({ icon_path: objectPath }),
  });
  if (!patchRes.ok) throw new Error(`icon_path更新に失敗: ${patchRes.status} ${await patchRes.text()}`);

  console.log(`アイコンを設定しました: ${app.name} -> ${objectPath}`);
}

main().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
