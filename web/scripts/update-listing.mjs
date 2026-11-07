#!/usr/bin/env node
// ストアの掲載文(短い説明・説明)を更新する。新しい版を出さずに文面だけ直すときに使う。
//
// 使い方: node scripts/update-listing.mjs --slug <slug> [--short-desc "..."] [--description-file <path>]
//
// 説明は長く、引数に書くと改行や引用符が壊れやすいのでファイルで渡す(UTF-8)。

import { readFileSync } from "node:fs";
import path from "node:path";
import { loadEnvLocal, need, parseArgs } from "./lib/cli-env.mjs";
import { SupabaseAdmin } from "./lib/supabase-admin.mjs";

async function main() {
  loadEnvLocal();
  const args = parseArgs(process.argv.slice(2));
  const slug = need(args, "slug");

  const patch = {};
  if (args["short-desc"]) patch.short_desc = args["short-desc"];
  if (args["description-file"]) {
    patch.description = readFileSync(path.resolve(args["description-file"]), "utf8").replace(/\r\n/g, "\n").trim();
  }
  if (Object.keys(patch).length === 0) throw new Error("--short-desc か --description-file を指定してください");

  const db = SupabaseAdmin.fromEnv();
  const app = await db.appBySlug(slug);
  await db.update("apps", `id=eq.${app.id}`, patch);

  console.log(`掲載文を更新しました: ${app.name} (${Object.keys(patch).join(", ")})`);
}

main().catch((e) => {
  console.error(e.message);
  process.exitCode = 1;
});
