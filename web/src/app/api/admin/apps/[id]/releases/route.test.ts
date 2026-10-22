import { beforeEach, describe, expect, it, vi } from "vitest";
import { NextResponse } from "next/server";
import { DEVELOPER_LIMITS } from "@/lib/app-rules";
import { fakeSupabase } from "@/lib/testing/fake-supabase";

let auth: unknown;
let fake: ReturnType<typeof fakeSupabase>;

const presignUpload = vi.hoisted(() => vi.fn(async (k: string) => `https://r2.example/${k}?sig=1`));
vi.mock("@/lib/admin", () => ({ requireAdmin: async () => auth }));
vi.mock("@/lib/supabase", () => ({ serviceClient: () => fake.client }));
vi.mock("@/lib/r2", async (orig) => ({ ...(await orig<typeof import("@/lib/r2")>()), presignUpload }));

import { POST } from "./route";

const APP_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const call = (body: unknown, id = APP_ID) =>
  POST(new Request("http://localhost/api", { method: "POST", body: JSON.stringify(body) }), {
    params: Promise.resolve({ id }),
  });

beforeEach(() => {
  auth = { admin: { id: "admin-1", email: "a@example.com" } };
  fake = fakeSupabase({ results: { "apps.select": { data: { id: APP_ID } } } });
  presignUpload.mockClear();
});

describe("POST /api/admin/apps/:id/releases", () => {
  it("管理者でなければ 401", async () => {
    auth = { response: NextResponse.json({ error: "管理者としてログインしてください" }, { status: 401 }) };
    expect((await call({ releaseNotes: "v1" })).status).toBe(401);
  });

  it("IDがUUIDでなければ 400", async () => {
    expect((await call({ releaseNotes: "v1" }, "not-a-uuid")).status).toBe(400);
  });

  it("DBには apkKey を保存し、アップロード用の署名付きURLは uploadKey に対して発行する", async () => {
    const res = await call({ releaseNotes: "初期リリース" });
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.maxBytes).toBe(DEVELOPER_LIMITS.maxApkBytes);
    expect(body.uploadUrl).toContain(`upload/${APP_ID}/${body.releaseId}.apk`);
    expect(presignUpload).toHaveBeenCalledWith(`upload/${APP_ID}/${body.releaseId}.apk`);
    expect(fake.callsTo("app_releases", "insert")[0].payload).toMatchObject({
      id: body.releaseId,
      app_id: APP_ID,
      apk_key: `apk/${APP_ID}/${body.releaseId}.apk`,
      release_notes: "初期リリース",
      status: "uploaded",
    });
  });
});
