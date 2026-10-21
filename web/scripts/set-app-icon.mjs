#!/usr/bin/env node
// アプリのストア掲載アイコンを設定する。SVG(ランチャーアイコンと同じ意匠でよい)から
// PNGを生成してSupabase Storage(app-media)にアップロードし、apps.icon_path を更新する。
//
// 使い方: node scripts/set-app-icon.mjs --slug <slug> --svg <path-to-svg> [--size 512]

import { readFileSync } from "node:fs";
import path from "node:path";
import sharp from "sharp";
import { loadEnvLocal, need, parseArgs } from "./lib/cli-env.mjs";
import { SupabaseAdmin } from "./lib/supabase-admin.mjs";

async function main() {
  loadEnvLocal();
  const args = parseArgs(process.argv.slice(2));
  const slug = need(args, "slug");
  const svgPath = need(args, "svg");
  const size = Number(args.size ?? 512);

  const db = SupabaseAdmin.fromEnv();
  const app = await db.appBySlug(slug);

  const png = await sharp(readFileSync(path.resolve(svgPath))).resize(size, size).png().toBuffer();
  const objectPath = `${app.id}/icon-${Date.now()}.png`;
  await db.upload("app-media", objectPath, png, "image/png");
  await db.update("apps", `id=eq.${app.id}`, { icon_path: objectPath });

  console.log(`アイコンを設定しました: ${app.name} -> ${objectPath}`);
}

main().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
