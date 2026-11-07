import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { LEGACY_SUPABASE_HOST, loadEnvLocal } from "./cli-env.mjs";
import { SupabaseAdmin } from "./supabase-admin.mjs";

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
});

describe("SupabaseAdmin.fromEnv", () => {
  const key = "test-key";

  it("旧クラウド Supabase を指していたらエラーで止める(本番へ勝手に切り替えない)", () => {
    expect(() =>
      SupabaseAdmin.fromEnv({ NEXT_PUBLIC_SUPABASE_URL: `https://${LEGACY_SUPABASE_HOST}`, SUPABASE_SERVICE_ROLE_KEY: key }),
    ).toThrow("誤登録防止ガード");
  });

  it("ALLOW_LEGACY_SUPABASE を付ければ旧環境にもつなげる", () => {
    const db = SupabaseAdmin.fromEnv({
      NEXT_PUBLIC_SUPABASE_URL: `https://${LEGACY_SUPABASE_HOST}`,
      SUPABASE_SERVICE_ROLE_KEY: key,
      ALLOW_LEGACY_SUPABASE: "1",
    });
    expect(db.url).toBe(`https://${LEGACY_SUPABASE_HOST}`);
  });

  it("本番の接続先はそのまま使う", () => {
    const db = SupabaseAdmin.fromEnv({ NEXT_PUBLIC_SUPABASE_URL: "https://store.kawamonn.com", SUPABASE_SERVICE_ROLE_KEY: key });
    expect(db.url).toBe("https://store.kawamonn.com");
  });
});
