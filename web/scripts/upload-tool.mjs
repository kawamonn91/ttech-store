#!/usr/bin/env node
// 配布用ファイル(Windowsツールのzipなど)を R2 にアップロードする。
// 使い方: node scripts/upload-tool.mjs <ファイル> <R2のキー>
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { AwsClient } from "aws4fetch";
import { Agent, fetch as ufetch } from "undici";

const here = path.dirname(fileURLToPath(import.meta.url));
const webRoot = path.resolve(here, "..");

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

function need(env, name) {
  const value = env[name];
  if (!value) throw new Error(`${name} が .env.local にありません`);
  return value;
}

class R2 {
  constructor(env) {
    this.client = new AwsClient({
      accessKeyId: need(env, "R2_ACCESS_KEY_ID"),
      secretAccessKey: need(env, "R2_SECRET_ACCESS_KEY"),
      service: "s3",
      region: "auto",
    });
    this.base = `https://${need(env, "R2_ACCOUNT_ID")}.r2.cloudflarestorage.com/${need(env, "R2_BUCKET")}`;
    // 大きいファイルを送るため、転送の時間制限は外す。接続はIPv4に固定する(publish-app.mjs と同じ理由)
    this.agent = new Agent({ connect: { family: 4 }, headersTimeout: 0, bodyTimeout: 0 });
  }

  async put(key, bytes) {
    const url = new URL(`${this.base}/${key.split("/").map(encodeURIComponent).join("/")}`);
    url.searchParams.set("X-Amz-Expires", "900");
    const signed = await this.client.sign(url.toString(), { method: "PUT", aws: { signQuery: true } });
    const res = await ufetch(signed.url, { method: "PUT", body: bytes, dispatcher: this.agent });
    if (!res.ok) throw new Error(`R2 PUT failed: ${res.status} ${await res.text()}`);
  }
}

async function main() {
  loadEnvLocal();
  const [file, key] = process.argv.slice(2);
  if (!file || !key) throw new Error("使い方: node scripts/upload-tool.mjs <ファイル> <R2のキー>");
  const abs = path.resolve(file);
  if (!existsSync(abs)) throw new Error(`ファイルが見つかりません: ${abs}`);
  const bytes = readFileSync(abs);
  console.log(`アップロード中: ${abs} (${bytes.length} bytes) -> ${key}`);
  await new R2(process.env).put(key, bytes);
  console.log("アップロードしました");
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
