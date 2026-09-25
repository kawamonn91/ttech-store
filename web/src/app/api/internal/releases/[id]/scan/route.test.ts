import { beforeEach, describe, expect, it, vi } from "vitest";
import { signBody } from "@/lib/scan";

const review = vi.hoisted(() => vi.fn());
vi.mock("@/lib/release-review", () => ({ reviewScannedRelease: review }));
vi.mock("@/lib/supabase", () => ({ serviceClient: () => ({}) }));

import { POST } from "./route";

const SECRET = "test-secret";
const SHA = "a".repeat(64);
const CERT = "b".repeat(64);

const result = (over: Record<string, unknown> = {}) => ({
  packageName: "jp.yomumemo.app", versionName: "1.0.0", versionCode: 1, minSdk: 26, targetSdk: 36, permissions: ["android.permission.INTERNET"],
  apkSize: 100, sha256: SHA, signingCertSha256: CERT, signerCount: 1, ...over,
});

function call(body: unknown, opts: { signature?: string | null; secret?: string } = {}) {
  const raw = typeof body === "string" ? body : JSON.stringify(body);
  const headers: Record<string, string> = {};
  const sig = opts.signature === undefined ? signBody(opts.secret ?? SECRET, raw) : opts.signature;
  if (sig !== null) headers["x-signature"] = sig;
  return POST(new Request("http://localhost/api", { method: "POST", body: raw, headers }), { params: Promise.resolve({ id: "rel-1" }) });
}

beforeEach(() => {
  vi.stubEnv("SCAN_WEBHOOK_SECRET", SECRET);
  review.mockReset().mockResolvedValue({ kind: "ok", status: "scanned", reason: null, autoApproved: false, decision: "needs_review" });
});

describe("POST /api/internal/releases/:id/scan", () => {
  it("署名なし・誤った署名は 401 で、審査処理を呼ばない", async () => {
    expect((await call(result(), { signature: null })).status).toBe(401);
    expect((await call(result(), { secret: "wrong" })).status).toBe(401);
    expect(review).not.toHaveBeenCalled();
  });

  it("正しい署名の結果は、リリースIDと検査結果を審査処理に渡し、その結果を返す", async () => {
    const res = await call(result());
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ status: "scanned", reason: null, autoApproved: false, decision: "needs_review" });
    expect(review).toHaveBeenCalledWith(expect.anything(), "rel-1", expect.objectContaining({ packageName: "jp.yomumemo.app", versionCode: 1 }));
  });

  it("自動公開・却下の結果もそのまま返す", async () => {
    review.mockResolvedValueOnce({ kind: "ok", status: "published", reason: null, autoApproved: true, decision: "auto_approve" });
    expect(await (await call(result())).json()).toMatchObject({ status: "published", autoApproved: true });
    review.mockResolvedValueOnce({ kind: "ok", status: "rejected", reason: "パッケージ名が一致しません", autoApproved: false, decision: null });
    expect(await (await call(result())).json()).toMatchObject({ status: "rejected", reason: "パッケージ名が一致しません" });
  });

  it("存在しないリリースは 404、公開済みなど書き換えられない状態は 409", async () => {
    review.mockResolvedValueOnce({ kind: "not_found" });
    expect((await call(result())).status).toBe(404);
    review.mockResolvedValueOnce({ kind: "conflict" });
    expect((await call(result())).status).toBe(409);
  });

  it("署名は正しくても形式が不正な結果は 400。審査処理は呼ばない", async () => {
    expect((await call(result({ sha256: "abc" }))).status).toBe(400);
    expect((await call("{not json")).status).toBe(400);
    expect(review).not.toHaveBeenCalled();
  });

  it("検査側の error は、そのまま審査処理に渡る(却下として記録される)", async () => {
    await call({ error: "APK の署名を検証できませんでした" });
    expect(review).toHaveBeenCalledWith(expect.anything(), "rel-1", expect.objectContaining({ error: "APK の署名を検証できませんでした" }));
  });

  it("審査処理が失敗したら 500(検査ワークフローは再送できる)", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    review.mockRejectedValueOnce(new Error("db down"));
    expect((await call(result())).status).toBe(500);
  });
});
