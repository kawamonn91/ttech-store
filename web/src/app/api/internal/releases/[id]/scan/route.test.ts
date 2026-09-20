import { beforeEach, describe, expect, it, vi } from "vitest";
import { signBody } from "@/lib/scan";

let release: unknown;
const update = vi.fn();
const updateEq = vi.fn();

vi.mock("@/lib/supabase", () => ({
  serviceClient: () => ({
    from: () => ({
      select: () => ({ eq: () => ({ maybeSingle: async () => ({ data: release }) }) }),
      update: (cols: unknown) => {
        update(cols);
        return { eq: updateEq };
      },
    }),
  }),
}));

import { POST } from "./route";

const SECRET = "test-secret";
const SHA = "a".repeat(64);
const CERT = "b".repeat(64);

const result = (over: Record<string, unknown> = {}) => ({
  packageName: "jp.yomumemo.app",
  versionName: "1.0.0",
  versionCode: 1,
  minSdk: 26,
  targetSdk: 36,
  permissions: ["android.permission.INTERNET"],
  apkSize: 100,
  sha256: SHA,
  signingCertSha256: CERT,
  signerCount: 1,
  ...over,
});

function call(body: unknown, opts: { signature?: string | null; secret?: string } = {}) {
  const raw = typeof body === "string" ? body : JSON.stringify(body);
  const headers: Record<string, string> = {};
  const sig = opts.signature === undefined ? signBody(opts.secret ?? SECRET, raw) : opts.signature;
  if (sig !== null) headers["x-signature"] = sig;
  return POST(new Request("http://localhost/api", { method: "POST", body: raw, headers }), {
    params: Promise.resolve({ id: "rel-1" }),
  });
}

beforeEach(() => {
  vi.stubEnv("SCAN_WEBHOOK_SECRET", SECRET);
  update.mockReset();
  updateEq.mockReset().mockResolvedValue({ error: null });
  release = { id: "rel-1", status: "uploaded", app: { package_name: "jp.yomumemo.app", signing_cert_sha256: null } };
});

describe("POST /api/internal/releases/:id/scan", () => {
  it("署名なし・誤った署名は 401 で、DBを触らない", async () => {
    expect((await call(result(), { signature: null })).status).toBe(401);
    expect((await call(result(), { secret: "wrong" })).status).toBe(401);
    expect(update).not.toHaveBeenCalled();
  });

  it("正しい署名の正常結果は「承認待ち(scanned)」にして列を保存する", async () => {
    const res = await call(result());
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ status: "scanned", reason: null });
    expect(update).toHaveBeenCalledWith(
      expect.objectContaining({ status: "scanned", version_code: 1, sha256: SHA, signing_cert_sha256: CERT }),
    );
  });

  it("パッケージ名が違えば却下(rejected)にし、理由を返す", async () => {
    const res = await call(result({ packageName: "com.evil.app" }));
    const body = await res.json();
    expect(body.status).toBe("rejected");
    expect(body.reason).toContain("パッケージ名");
    expect(update).toHaveBeenCalledWith(expect.objectContaining({ status: "rejected" }));
  });

  it("署名鍵が固定済みの鍵と違えば却下する", async () => {
    release = { id: "rel-1", status: "uploaded", app: { package_name: "jp.yomumemo.app", signing_cert_sha256: "c".repeat(64) } };
    expect((await (await call(result())).json()).status).toBe("rejected");
  });

  it("公開済み・承認済みのリリースは検査結果で書き換えさせない(409)", async () => {
    release = { id: "rel-1", status: "published", app: { package_name: "jp.yomumemo.app", signing_cert_sha256: CERT } };
    expect((await call(result())).status).toBe(409);
    expect(update).not.toHaveBeenCalled();
  });

  it("存在しないリリースは 404", async () => {
    release = null;
    expect((await call(result())).status).toBe(404);
  });

  it("署名は正しくても形式が不正な結果は 400(sha256 の桁違い)", async () => {
    expect((await call(result({ sha256: "abc" }))).status).toBe(400);
    expect((await call("{not json")).status).toBe(400);
  });

  it("検査側の error は却下として記録される", async () => {
    const res = await call({ error: "APK の署名を検証できませんでした" });
    expect((await res.json()).status).toBe("rejected");
  });
});
