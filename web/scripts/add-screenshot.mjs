#!/usr/bin/env node
// ストア掲載用のスクリーンショットを1枚追加する(既存のscreenshotsに追記、最大8枚)。
// PNGはすでに実機/エミュレータで撮ったものをそのまま使う想定なので、ここでは加工しない。
//
// 使い方: node scripts/add-screenshot.mjs --slug <slug> --image <path-to-png>

import { readFileSync } from "node:fs";
import path from "node:path";
import { loadEnvLocal, parseArgs, need } from "./lib/cli-env.mjs";

async function main() {
  loadEnvLocal();
  const args = parseArgs(process.argv.slice(2));
  const slug = need(args, "slug");
  const imagePath = need(args, "image");

  const url = need(process.env, "NEXT_PUBLIC_SUPABASE_URL");
  const serviceKey = need(process.env, "SUPABASE_SERVICE_ROLE_KEY");
  const headers = { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` };

  const appRes = await fetch(`${url}/rest/v1/apps?slug=eq.${encodeURIComponent(slug)}&select=id,name,screenshots`, { headers });
  if (!appRes.ok) throw new Error(`アプリ取得に失敗: ${appRes.status} ${await appRes.text()}`);
  const [app] = await appRes.json();
  if (!app) throw new Error(`アプリが見つかりません: slug=${slug}`);

  const png = readFileSync(path.resolve(imagePath));
  const objectPath = `${app.id}/screenshot-${Date.now()}.png`;
  const upRes = await fetch(`${url}/storage/v1/object/app-media/${objectPath}`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "image/png", "x-upsert": "true" },
    body: png,
  });
  if (!upRes.ok) throw new Error(`アップロードに失敗: ${upRes.status} ${await upRes.text()}`);

  const shots = [...(app.screenshots ?? []), objectPath].slice(0, 8);
  const patchRes = await fetch(`${url}/rest/v1/apps?id=eq.${app.id}`, {
    method: "PATCH",
    headers: { ...headers, "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify({ screenshots: shots }),
  });
  if (!patchRes.ok) throw new Error(`screenshots更新に失敗: ${patchRes.status} ${await patchRes.text()}`);

  console.log(`スクリーンショットを追加しました: ${app.name} -> ${objectPath} (${shots.length}枚目)`);
}

main().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
