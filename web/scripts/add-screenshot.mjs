#!/usr/bin/env node
// ストア掲載用のスクリーンショットを1枚追加する(既存のscreenshotsに追記、最大8枚)。
// PNGはすでに実機/エミュレータで撮ったものをそのまま使う想定なので、ここでは加工しない。
//
// 使い方: node scripts/add-screenshot.mjs --slug <slug> --image <path-to-png>

import { readFileSync } from "node:fs";
import path from "node:path";
import { loadEnvLocal, need, parseArgs } from "./lib/cli-env.mjs";
import { SupabaseAdmin } from "./lib/supabase-admin.mjs";

const MAX_SCREENSHOTS = 8; // web/src/lib/app-rules.ts の DEVELOPER_LIMITS.maxScreenshots と同じ

async function main() {
  loadEnvLocal();
  const args = parseArgs(process.argv.slice(2));
  const slug = need(args, "slug");
  const imagePath = need(args, "image");

  const db = SupabaseAdmin.fromEnv();
  const app = await db.appBySlug(slug, "id,name,screenshots");

  const objectPath = `${app.id}/screenshot-${Date.now()}.png`;
  await db.upload("app-media", objectPath, readFileSync(path.resolve(imagePath)), "image/png");

  const shots = [...(app.screenshots ?? []), objectPath].slice(0, MAX_SCREENSHOTS);
  await db.update("apps", `id=eq.${app.id}`, { screenshots: shots });

  console.log(`スクリーンショットを追加しました: ${app.name} -> ${objectPath} (${shots.length}枚目)`);
}

main().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
