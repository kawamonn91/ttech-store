import { beforeEach, describe, expect, it, vi } from "vitest";
import { NextResponse } from "next/server";
import { DEVELOPER_LIMITS } from "@/lib/app-rules";
import { fakeSupabase } from "@/lib/testing/fake-supabase";

let auth: unknown;
let fake: ReturnType<typeof fakeSupabase>;

const r2 = vi.hoisted(() => ({
  headObject: vi.fn(),
  copyObject: vi.fn(),
  deleteObject: vi.fn(),
}));
const dispatch = vi.hoisted(() => ({ dispatchScan: vi.fn() }));

vi.mock("@/lib/admin", () => ({ requireAdmin: async () => auth }));
vi.mock("@/lib/supabase", () => ({ serviceClient: () => fake.client }));
vi.mock("@/lib/r2", async (orig) => ({ ...(await orig<typeof import("@/lib/r2")>()), ...r2 }));
vi.mock("@/lib/scan-dispatch", () => dispatch);

import { POST } from "./route";

const APP_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const REL_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

const call = (id = REL_ID) =>
  POST(new Request("http://localhost/api", { method: "POST" }), {
    params: Promise.resolve({ id }),
  });

beforeEach(() => {
  auth = { admin: { id: "admin-1", email: "a@example.com" } };
  fake = fakeSupabase({
    results: {
      "app_releases.select": {
        data: { id: REL_ID, app_id: APP_ID, apk_key: `apk/${APP_ID}/${REL_ID}.apk`, status: "uploaded" },
      },
    },
  });
  r2.headObject.mockReset().mockResolvedValue({ size: 1024 });
  r2.copyObject.mockReset().mockResolvedValue(true);
  r2.deleteObject.mockReset().mockResolvedValue(undefined);
  dispatch.dispatchScan.mockReset().mockResolvedValue({ dispatched: true });
});

describe("POST /api/admin/releases/:id/uploaded", () => {
  it("管理者でなければ 401", async () => {
    auth = { response: NextResponse.json({ error: "管理者としてログインしてください" }, { status: 401 }) };
    expect((await call()).status).toBe(401);
    expect(r2.headObject).not.toHaveBeenCalled();
  });

  it("IDがUUIDでなければ 400", async () => {
    expect((await call("not-a-uuid")).status).toBe(400);
  });

  it("uploadKey から apkKey へ複製して元を削除し、検査を起動する", async () => {
    const res = await call();
    expect(res.status).toBe(200);
    expect(r2.headObject).toHaveBeenCalledWith(`upload/${APP_ID}/${REL_ID}.apk`);
    expect(r2.copyObject).toHaveBeenCalledWith(`upload/${APP_ID}/${REL_ID}.apk`, `apk/${APP_ID}/${REL_ID}.apk`);
    expect(r2.deleteObject).toHaveBeenCalledWith(`upload/${APP_ID}/${REL_ID}.apk`);
    expect(dispatch.dispatchScan).toHaveBeenCalledWith(REL_ID, `apk/${APP_ID}/${REL_ID}.apk`);
  });

  it("上限を超えるAPKは削除して 413 で却下する", async () => {
    r2.headObject.mockResolvedValue({ size: DEVELOPER_LIMITS.maxApkBytes + 1 });
    const res = await call();
    expect(res.status).toBe(413);
    expect(r2.deleteObject).toHaveBeenCalledWith(`upload/${APP_ID}/${REL_ID}.apk`);
    expect(r2.copyObject).not.toHaveBeenCalled();
    expect(dispatch.dispatchScan).not.toHaveBeenCalled();
    expect(fake.callsTo("app_releases", "update")[0].payload).toMatchObject({ status: "rejected" });
  });
});
