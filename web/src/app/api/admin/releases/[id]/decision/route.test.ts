import { beforeEach, describe, expect, it, vi } from "vitest";
import { NextResponse } from "next/server";

let auth: unknown;
let release: unknown;
let updateResult: { error: { message: string } | null };
const update = vi.fn();

vi.mock("@/lib/admin", () => ({ requireAdmin: async () => auth }));
vi.mock("@/lib/supabase", () => ({
  serviceClient: () => ({
    from: () => ({
      select: () => ({ eq: () => ({ maybeSingle: async () => ({ data: release }) }) }),
      update: (cols: unknown) => {
        update(cols);
        return { eq: async () => updateResult };
      },
    }),
  }),
}));

import { POST } from "./route";

const call = (body: unknown) =>
  POST(new Request("http://localhost/api", { method: "POST", body: JSON.stringify(body) }), {
    params: Promise.resolve({ id: "rel-1" }),
  });

beforeEach(() => {
  update.mockReset();
  auth = { admin: { id: "admin-1", email: "a@example.com" } };
  release = { id: "rel-1", status: "scanned" };
  updateResult = { error: null };
});

describe("POST /api/admin/releases/:id/decision", () => {
  it("管理者でなければ何もせず認証エラーを返す", async () => {
    auth = { response: NextResponse.json({ error: "管理者としてログインしてください" }, { status: 401 }) };
    expect((await call({ action: "publish" })).status).toBe(401);
    expect(update).not.toHaveBeenCalled();
  });

  it("不正な action は 400", async () => {
    expect((await call({ action: "delete-everything" })).status).toBe(400);
    expect(update).not.toHaveBeenCalled();
  });

  it("検査済み(scanned)のリリースを公開し、承認者を記録する", async () => {
    expect((await call({ action: "publish" })).status).toBe(200);
    expect(update).toHaveBeenCalledWith({ status: "published", reviewed_by: "admin-1" });
  });

  it("検査待ち(uploaded)・却下済みのリリースは公開できない(409)", async () => {
    for (const status of ["uploaded", "rejected", "published"]) {
      release = { id: "rel-1", status };
      expect((await call({ action: "publish" })).status).toBe(409);
    }
    expect(update).not.toHaveBeenCalled();
  });

  it("DBトリガー(署名鍵の不一致など)の拒否は 422 でメッセージをそのまま返す", async () => {
    updateResult = { error: { message: "署名鍵が既存のアプリと一致しません" } };
    const res = await call({ action: "publish" });
    expect(res.status).toBe(422);
    expect(await res.json()).toEqual({ error: "署名鍵が既存のアプリと一致しません" });
  });

  it("公開中のリリースは先に公開停止しないと却下できない", async () => {
    release = { id: "rel-1", status: "published" };
    expect((await call({ action: "reject" })).status).toBe(409);
  });

  it("公開停止は公開中のリリースだけ。非公開(approved)に戻す", async () => {
    release = { id: "rel-1", status: "published" };
    expect((await call({ action: "unpublish" })).status).toBe(200);
    expect(update).toHaveBeenCalledWith({ status: "approved" });

    release = { id: "rel-1", status: "scanned" };
    expect((await call({ action: "unpublish" })).status).toBe(409);
  });

  it("存在しないリリースは 404", async () => {
    release = null;
    expect((await call({ action: "publish" })).status).toBe(404);
  });
});
