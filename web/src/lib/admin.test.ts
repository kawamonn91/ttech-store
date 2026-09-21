import { beforeEach, describe, expect, it, vi } from "vitest";

let cookieAdmin: { id: string; email?: string } | null;
let bearerUser: { id: string; email?: string } | null;
let profileRole: string | null;
let factors: { factor_type: string; status: string }[];

vi.mock("./supabase", () => ({
  getAdminUser: async () => cookieAdmin,
  publicClient: () => ({ auth: { getUser: async (token: string) => ({ data: { user: token === "valid-token" ? bearerUser : null } }) } }),
  serviceClient: () => ({
    from: () => ({ select: () => ({ eq: () => ({ maybeSingle: async () => ({ data: { role: profileRole } }) }) }) }),
    auth: { admin: { getUserById: async () => ({ data: { user: { factors } } }) } },
  }),
}));

import { requireAdmin } from "./admin";

beforeEach(() => {
  cookieAdmin = null;
  bearerUser = { id: "u1", email: "a@example.com" };
  profileRole = "admin";
  factors = [];
});

const withAuth = (token?: string) => new Request("http://localhost/api", token ? { headers: { authorization: `Bearer ${token}` } } : {});

describe("requireAdmin", () => {
  it("Cookieセッションが管理者ならそちらを優先する(Bearerは見ない)", async () => {
    cookieAdmin = { id: "cookie-admin", email: "c@example.com" };
    const result = await requireAdmin(withAuth("valid-token"));
    expect("admin" in result && result.admin.id).toBe("cookie-admin");
  });

  it("Authorizationヘッダーが無ければ401", async () => {
    const result = await requireAdmin(new Request("http://localhost/api"));
    expect("response" in result).toBe(true);
  });

  it("無効なトークンなら401", async () => {
    const result = await requireAdmin(withAuth("invalid-token"));
    expect("response" in result).toBe(true);
  });

  it("adminロールでなければ401", async () => {
    profileRole = "user";
    const result = await requireAdmin(withAuth("valid-token"));
    expect("response" in result).toBe(true);
  });

  it("TOTP未設定のadminはBearerトークンで通る", async () => {
    factors = [];
    const result = await requireAdmin(withAuth("valid-token"));
    expect("admin" in result && result.admin.id).toBe("u1");
  });

  it("TOTP設定済みのadminはBearerトークンだけでは通らない(Web側でのみ)", async () => {
    factors = [{ factor_type: "totp", status: "verified" }];
    const result = await requireAdmin(withAuth("valid-token"));
    expect("response" in result).toBe(true);
  });

  it("unverifiedなTOTPは未設定として扱う", async () => {
    factors = [{ factor_type: "totp", status: "unverified" }];
    const result = await requireAdmin(withAuth("valid-token"));
    expect("admin" in result && result.admin.id).toBe("u1");
  });

  it("requestを渡さなければCookieのみで判定する", async () => {
    cookieAdmin = null;
    const result = await requireAdmin();
    expect("response" in result).toBe(true);
  });
});
