import { beforeEach, describe, expect, it, vi } from "vitest";
import { NextResponse } from "next/server";

let auth: unknown;
let release: unknown;
let releaseUpdateResult: { error: { message: string } | null };
let appUpdateResult: { error: { message: string } | null };
const releaseUpdate = vi.fn();
const appUpdate = vi.fn();

vi.mock("@/lib/admin", () => ({ requireAdmin: async () => auth }));
vi.mock("@/lib/supabase", () => ({
  serviceClient: () => ({
    from: (table: string) => {
      if (table === "apps") {
        return {
          update: (cols: unknown) => {
            appUpdate(cols);
            return { eq: async () => appUpdateResult };
          },
        };
      }
      return {
        select: () => ({ eq: () => ({ maybeSingle: async () => ({ data: release }) }) }),
        update: (cols: unknown) => {
          releaseUpdate(cols);
          return { eq: async () => releaseUpdateResult };
        },
      };
    },
  }),
}));

import { POST } from "./route";

const call = (body: unknown) =>
  POST(new Request("http://localhost/api", { method: "POST", body: JSON.stringify(body) }), {
    params: Promise.resolve({ id: "rel-1" }),
  });

beforeEach(() => {
  releaseUpdate.mockReset();
  appUpdate.mockReset();
  auth = { admin: { id: "admin-1", email: "a@example.com" } };
  release = { id: "rel-1", status: "scanned", app_id: "app-1", apps: { status: "draft" } };
  releaseUpdateResult = { error: null };
  appUpdateResult = { error: null };
});

describe("POST /api/admin/releases/:id/decision", () => {
  it("管理者でなければ何もせず認証エラーを返す", async () => {
    auth = { response: NextResponse.json({ error: "管理者としてログインしてください" }, { status: 401 }) };
    expect((await call({ action: "publish" })).status).toBe(401);
    expect(releaseUpdate).not.toHaveBeenCalled();
  });

  it("不正な action は 400", async () => {
    expect((await call({ action: "delete-everything" })).status).toBe(400);
    expect(releaseUpdate).not.toHaveBeenCalled();
  });

  it("検査済み(scanned)のリリースを公開し、承認者を記録する。まだ非公開のアプリも合わせて公開する", async () => {
    expect((await call({ action: "publish" })).status).toBe(200);
    expect(releaseUpdate).toHaveBeenCalledWith({ status: "published", reviewed_by: "admin-1" });
    expect(appUpdate).toHaveBeenCalledWith({ status: "published" });
  });

  it("アプリが既に公開中なら、アプリ側の更新は呼ばない", async () => {
    release = { id: "rel-1", status: "scanned", app_id: "app-1", apps: { status: "published" } };
    expect((await call({ action: "publish" })).status).toBe(200);
    expect(appUpdate).not.toHaveBeenCalled();
  });

  it("検査待ち(uploaded)・却下済みのリリースは公開できない(409)", async () => {
    for (const status of ["uploaded", "rejected", "published"]) {
      release = { id: "rel-1", status, app_id: "app-1", apps: { status: "draft" } };
      expect((await call({ action: "publish" })).status).toBe(409);
    }
    expect(releaseUpdate).not.toHaveBeenCalled();
  });

  it("DBトリガー(署名鍵の不一致など)の拒否は 422 でメッセージをそのまま返す", async () => {
    releaseUpdateResult = { error: { message: "署名鍵が既存のアプリと一致しません" } };
    const res = await call({ action: "publish" });
    expect(res.status).toBe(422);
    expect(await res.json()).toEqual({ error: "署名鍵が既存のアプリと一致しません" });
    expect(appUpdate).not.toHaveBeenCalled();
  });

  it("アプリ側の更新が失敗したら 422 で返す", async () => {
    appUpdateResult = { error: { message: "boom" } };
    const res = await call({ action: "publish" });
    expect(res.status).toBe(422);
  });

  it("公開中のリリースは先に公開停止しないと却下できない", async () => {
    release = { id: "rel-1", status: "published", app_id: "app-1", apps: { status: "published" } };
    expect((await call({ action: "reject" })).status).toBe(409);
  });

  it("却下時はアプリ側を更新しない", async () => {
    release = { id: "rel-1", status: "scanned", app_id: "app-1", apps: { status: "draft" } };
    expect((await call({ action: "reject" })).status).toBe(200);
    expect(appUpdate).not.toHaveBeenCalled();
  });

  it("公開停止は公開中のリリースだけ。非公開(approved)に戻す", async () => {
    release = { id: "rel-1", status: "published", app_id: "app-1", apps: { status: "published" } };
    expect((await call({ action: "unpublish" })).status).toBe(200);
    expect(releaseUpdate).toHaveBeenCalledWith({ status: "approved" });
    expect(appUpdate).not.toHaveBeenCalled();

    release = { id: "rel-1", status: "scanned", app_id: "app-1", apps: { status: "draft" } };
    expect((await call({ action: "unpublish" })).status).toBe(409);
  });

  it("存在しないリリースは 404", async () => {
    release = null;
    expect((await call({ action: "publish" })).status).toBe(404);
  });
});
