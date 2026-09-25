import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// サーバーからR2への直接のリクエスト(複製・削除)は undici の fetch を使う。テストでは実際に通信しないよう差し替える
const undiciFetch = vi.hoisted(() => vi.fn());
vi.mock("undici", () => ({ Agent: class {}, fetch: undiciFetch }));

import { apkKey, copyObject, deleteObject, DOWNLOAD_URL_TTL_SECONDS, presignDownload, presignUpload, UPLOAD_URL_TTL_SECONDS, uploadKey } from "./r2";

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

describe("uploadKey", () => {
  it("検査・公開に使うキー(apkKey)とは別のキーになる", () => {
    expect(uploadKey("app-1", "rel-1")).toBe("upload/app-1/rel-1.apk");
    expect(uploadKey("app-1", "rel-1")).not.toBe(apkKey("app-1", "rel-1"));
  });
});

describe("copyObject / deleteObject(アップロード後にAPKを差し替えられないようにするための操作)", () => {
  beforeEach(() => undiciFetch.mockReset());

  it("複製は、複製先への PUT に、複製元を x-amz-copy-source で指定して署名する", async () => {
    undiciFetch.mockResolvedValue({ ok: true, status: 200 });
    expect(await copyObject("upload/app-1/rel-1.apk", "apk/app-1/rel-1.apk")).toBe(true);
    const [url, init] = undiciFetch.mock.calls[0];
    expect(new URL(url).pathname).toBe("/ttech-store-apk/apk/app-1/rel-1.apk");
    expect(init.method).toBe("PUT");
    expect(init.headers["x-amz-copy-source"]).toBe("/ttech-store-apk/upload/app-1/rel-1.apk");
    // 認証は署名(Authorization ヘッダー)で行い、秘密鍵そのものは送らない
    expect(String(init.headers.authorization ?? init.headers.Authorization)).toContain("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/");
    expect(JSON.stringify(init.headers)).not.toContain("secret");
  });

  it("複製元が無ければ false。それ以外の失敗は例外", async () => {
    undiciFetch.mockResolvedValueOnce({ ok: false, status: 404 });
    expect(await copyObject("upload/a.apk", "apk/a.apk")).toBe(false);
    undiciFetch.mockResolvedValueOnce({ ok: false, status: 500 });
    await expect(copyObject("upload/a.apk", "apk/a.apk")).rejects.toThrow("R2 COPY failed: 500");
  });

  it("削除は DELETE。すでに無くてもエラーにしない", async () => {
    undiciFetch.mockResolvedValueOnce({ ok: true, status: 204 });
    await deleteObject("upload/app-1/rel-1.apk");
    expect(undiciFetch.mock.calls[0][1].method).toBe("DELETE");
    undiciFetch.mockResolvedValueOnce({ ok: false, status: 404 });
    await expect(deleteObject("upload/gone.apk")).resolves.toBeUndefined();
    undiciFetch.mockResolvedValueOnce({ ok: false, status: 403 });
    await expect(deleteObject("upload/x.apk")).rejects.toThrow("R2 DELETE failed: 403");
  });
});
