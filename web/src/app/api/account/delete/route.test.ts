import { beforeEach, describe, expect, it, vi } from "vitest";

let sessionUser: { id: string; email?: string } | null;
let appCount: number;
const deleteUser = vi.fn();

vi.mock("@/lib/supabase", () => ({
  sessionClient: async () => ({ auth: { getUser: async () => ({ data: { user: sessionUser } }) } }),
  serviceClient: () => ({
    from: () => ({ select: () => ({ eq: async () => ({ count: appCount }) }) }),
    auth: { admin: { deleteUser } },
  }),
}));

import { POST } from "./route";

beforeEach(() => {
  sessionUser = { id: "user-1", email: "a@example.com" };
  appCount = 0;
  deleteUser.mockReset().mockResolvedValue({ error: null });
});

describe("POST /api/account/delete", () => {
  it("未ログインなら401", async () => {
    sessionUser = null;
    const res = await POST();
    expect(res.status).toBe(401);
    expect(deleteUser).not.toHaveBeenCalled();
  });

  it("アプリが残っていれば409で削除しない", async () => {
    appCount = 2;
    const res = await POST();
    expect(res.status).toBe(409);
    expect(deleteUser).not.toHaveBeenCalled();
  });

  it("アプリが無ければ本人のIDで削除する", async () => {
    const res = await POST();
    expect(res.status).toBe(200);
    expect(deleteUser).toHaveBeenCalledWith("user-1");
  });

  it("削除に失敗したら500", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    deleteUser.mockResolvedValue({ error: { message: "boom" } });
    const res = await POST();
    expect(res.status).toBe(500);
  });
});
