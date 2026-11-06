import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { LEGACY_SUPABASE_HOST, loadEnvLocal, resolveDefaultEnvFile } from "./cli-env.mjs";

describe("loadEnvLocal", () => {
  afterEach(() => {
    delete process.env.TTECH_TEST_VALUE;
    delete process.env.TTECH_ENV_FILE;
  });

  it("TTECH_ENV_FILE で指定したファイルを読む", () => {
    const dir = mkdtempSync(path.join(tmpdir(), "cli-env-"));
    const file = path.join(dir, "test.env");
    writeFileSync(file, "TTECH_TEST_VALUE=from-file\n");
    process.env.TTECH_ENV_FILE = file;
    expect(loadEnvLocal()).toBe(file);
    expect(process.env.TTECH_TEST_VALUE).toBe("from-file");
  });

  it("すでに設定されている環境変数は上書きしない", () => {
    const dir = mkdtempSync(path.join(tmpdir(), "cli-env-"));
    const file = path.join(dir, "test.env");
    writeFileSync(file, "TTECH_TEST_VALUE=from-file\n");
    process.env.TTECH_TEST_VALUE = "already";
    loadEnvLocal(file);
    expect(process.env.TTECH_TEST_VALUE).toBe("already");
  });

  it("ファイルが無ければ null を返す", () => {
    expect(loadEnvLocal(path.join(tmpdir(), "does-not-exist.env"))).toBeNull();
  });

  it(".env.local が旧クラウド Supabase を指し .env.prod.local がある場合は .env.prod.local を選ぶ", () => {
    const dir = mkdtempSync(path.join(tmpdir(), "cli-env-"));
    writeFileSync(path.join(dir, ".env.local"), `NEXT_PUBLIC_SUPABASE_URL=https://${LEGACY_SUPABASE_HOST}\n`);
    writeFileSync(path.join(dir, ".env.prod.local"), "NEXT_PUBLIC_SUPABASE_URL=https://store.kawamonn.com\n");
    expect(resolveDefaultEnvFile(dir, {})).toBe(".env.prod.local");
    expect(resolveDefaultEnvFile(dir, { ALLOW_LEGACY_SUPABASE: "1" })).toBe(".env.local");
  });
});
