// CLIスクリプト共通のちょっとした処理(.env.local読み込み・簡易な引数パース)。
// dotenv 等のライブラリは増やさず、これまでの publish-app.mjs と同じ簡易実装を共有する。

import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
export const webRoot = path.resolve(here, "../..");
export const LEGACY_SUPABASE_HOST = "bniehgjuylkgxbstmjdh.supabase.co";

/**
 * 既定で読む環境変数ファイル名を返す。
 * TTECH_ENV_FILE が未指定で、.env.local が廃止済みの旧クラウド Supabase を指しており、
 * かつ同階層に .env.prod.local がある場合は自動的に .env.prod.local を使う。
 */
export function resolveDefaultEnvFile(root = webRoot, env = process.env) {
  if (env.TTECH_ENV_FILE) return env.TTECH_ENV_FILE;
  const localFile = path.resolve(root, ".env.local");
  const prodFile = path.resolve(root, ".env.prod.local");
  if (
    !env.ALLOW_LEGACY_SUPABASE &&
    existsSync(localFile) &&
    existsSync(prodFile) &&
    readFileSync(localFile, "utf8").includes(LEGACY_SUPABASE_HOST)
  ) {
    return ".env.prod.local";
  }
  return ".env.local";
}

/**
 * 設定ファイルを読み込み、未設定の環境変数にだけ反映する。
 * 既定は web/.env.local（旧クラウド Supabase を指している場合は .env.prod.local に自動フォールバック）。
 * 環境変数 TTECH_ENV_FILE で別のファイルを選べる。
 * 相対パスは web/ から、絶対パスはそのまま解釈する。読んだファイルのパスを返す(無ければ null)。
 */
export function loadEnvLocal(fileName = resolveDefaultEnvFile()) {
  const file = path.resolve(webRoot, fileName);
  if (!existsSync(file)) return null;
  for (const line of readFileSync(file, "utf8").split("\n")) {
    if (!line.includes("=") || line.startsWith("#")) continue;
    const i = line.indexOf("=");
    const key = line.slice(0, i).trim();
    const value = line.slice(i + 1).trim();
    if (key && process.env[key] === undefined) process.env[key] = value;
  }
  return file;
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
