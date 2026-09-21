// CLIスクリプト共通のちょっとした処理(.env.local読み込み・簡易な引数パース)。
// dotenv 等のライブラリは増やさず、これまでの publish-app.mjs と同じ簡易実装を共有する。

import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
export const webRoot = path.resolve(here, "../..");

/** .env.local を読み込み、未設定の環境変数にだけ反映する */
export function loadEnvLocal() {
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

/** `--foo bar` / `--flag` 形式の引数を { foo: "bar", flag: true } に変換する */
export function parseArgs(argv, { booleanFlags = [] } = {}) {
  const out = {};
  for (const f of booleanFlags) out[f] = false;
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith("--")) {
      const name = a.slice(2);
      if (booleanFlags.includes(name)) {
        out[name] = true;
        continue;
      }
      out[name] = argv[++i];
    }
  }
  return out;
}

export function need(env, name) {
  const v = env[name];
  if (!v) throw new Error(`${name} が未設定です(.env.local または引数を確認してください)`);
  return v;
}
