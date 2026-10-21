#!/usr/bin/env node
// 配布用ファイル(Windowsツールのzipなど)を R2 にアップロードする。
// 使い方: node scripts/upload-tool.mjs <ファイル> <R2のキー>
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { loadEnvLocal } from "./lib/cli-env.mjs";
import { R2 } from "./lib/r2.mjs";

async function main() {
  loadEnvLocal();
  const [file, key] = process.argv.slice(2);
  if (!file || !key) throw new Error("使い方: node scripts/upload-tool.mjs <ファイル> <R2のキー>");
  const abs = path.resolve(file);
  if (!existsSync(abs)) throw new Error(`ファイルが見つかりません: ${abs}`);
  const bytes = readFileSync(abs);
  console.log(`アップロード中: ${abs} (${bytes.length} bytes) -> ${key}`);
  // 大きいファイルを送るため、署名の期限を長めにし、転送の時間制限は外す
  await new R2(process.env, { expiresSeconds: 900, noTimeout: true }).put(key, bytes);
  console.log("アップロードしました");
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
