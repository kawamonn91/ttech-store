import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apkKey, DOWNLOAD_URL_TTL_SECONDS, presignDownload, presignUpload, UPLOAD_URL_TTL_SECONDS } from "./r2";

beforeEach(() => {
  vi.stubEnv("R2_ACCOUNT_ID", "acct123");
  vi.stubEnv("R2_ACCESS_KEY_ID", "AKIDEXAMPLE");
  vi.stubEnv("R2_SECRET_ACCESS_KEY", "secret");
  vi.stubEnv("R2_BUCKET", "ttech-store-apk");
});
afterEach(() => vi.unstubAllEnvs());

describe("presignDownload", () => {
  it("期限付き・署名付きの R2 URL を返す", async () => {
    const url = new URL(await presignDownload("apk/app-1/rel-1.apk"));
    expect(url.host).toBe("acct123.r2.cloudflarestorage.com");
    expect(url.pathname).toBe("/ttech-store-apk/apk/app-1/rel-1.apk");
    expect(url.searchParams.get("X-Amz-Expires")).toBe(String(DOWNLOAD_URL_TTL_SECONDS));
    expect(url.searchParams.get("X-Amz-Algorithm")).toBe("AWS4-HMAC-SHA256");
    expect(url.searchParams.get("X-Amz-Signature")).toMatch(/^[0-9a-f]{64}$/);
    expect(url.searchParams.get("X-Amz-Credential")).toContain("AKIDEXAMPLE/");
  });

  it("有効期間は15分", () => {
    expect(DOWNLOAD_URL_TTL_SECONDS).toBe(900);
  });

  it("秘密鍵そのものはURLに含まれない", async () => {
    expect(await presignDownload("k.apk")).not.toContain("secret");
  });

  it("キーが違えば署名も変わる(別ファイルに流用できない)", async () => {
    const a = new URL(await presignDownload("apk/a.apk")).searchParams.get("X-Amz-Signature");
    const b = new URL(await presignDownload("apk/b.apk")).searchParams.get("X-Amz-Signature");
    expect(a).not.toBe(b);
  });

  it("スペースや日本語を含むキーも安全にエンコードする", async () => {
    const url = new URL(await presignDownload("apk/a b/日本語.apk"));
    expect(decodeURIComponent(url.pathname)).toBe("/ttech-store-apk/apk/a b/日本語.apk");
  });
});

describe("presignUpload", () => {
  it("30分有効・PUT用に署名され、対象キーが固定される", async () => {
    const url = new URL(await presignUpload("apk/app-1/rel-1.apk"));
    expect(url.pathname).toBe("/ttech-store-apk/apk/app-1/rel-1.apk");
    expect(url.searchParams.get("X-Amz-Expires")).toBe(String(UPLOAD_URL_TTL_SECONDS));
    expect(url.searchParams.get("X-Amz-SignedHeaders")).toBe("host");
  });

  it("同じキーでも GET 用と PUT 用で署名が異なる(ダウンロードURLをアップロードに流用できない)", async () => {
    const get = new URL(await presignDownload("apk/a.apk")).searchParams.get("X-Amz-Signature");
    const put = new URL(await presignUpload("apk/a.apk")).searchParams.get("X-Amz-Signature");
    expect(get).not.toBe(put);
  });
});

describe("apkKey", () => {
  it("アプリID とリリースID から決まる", () => {
    expect(apkKey("app-1", "rel-1")).toBe("apk/app-1/rel-1.apk");
  });
});
