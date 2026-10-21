import { beforeEach, describe, expect, it, vi } from "vitest";

let authUser: { id: string; email?: string; factors?: { id: string; factor_type: string; status: string }[] } | null;
let profileRole: string | null;
let currentAal: string;

const totp = (status: string) => [{ id: "f1", factor_type: "totp", status }];

vi.mock("@supabase/ssr", () => ({
  createServerClient: () => ({
    auth: {
      getUser: async () => ({ data: { user: authUser } }),
      mfa: {
        getAuthenticatorAssuranceLevel: async () => ({ data: { currentLevel: currentAal } }),
      },
    },
    from: () => ({ select: () => ({ eq: () => ({ maybeSingle: async () => ({ data: { role: profileRole } }) }) }) }),
  }),
}));
vi.mock("next/headers", () => ({ cookies: async () => ({ getAll: () => [], set: () => {} }) }));

import { getAdminUser, verifiedTotpFactor } from "./supabase";

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_SUPABASE_URL", "https://proj.supabase.co");
  vi.stubEnv("NEXT_PUBLIC_SUPABASE_ANON_KEY", "anon");
  authUser = { id: "u1", email: "a@example.com" };
  profileRole = "admin";
  currentAal = "aal1";
});

describe("getAdminUser", () => {
  it("未ログインなら null", async () => {
    authUser = null;
    expect(await getAdminUser()).toBeNull();
  });

  it("admin でなければ null", async () => {
    profileRole = "user";
    expect(await getAdminUser()).toBeNull();
  });

  it("TOTP未設定のadminはaal1のままでも通す(移行期間)", async () => {
    profileRole = "admin";
    currentAal = "aal1";
    expect(await getAdminUser()).toEqual({ id: "u1", email: "a@example.com" });
  });

  it("TOTP設定済みでaal1(未認証)なら null", async () => {
    authUser = { id: "u1", email: "a@example.com", factors: totp("verified") };
    currentAal = "aal1";
    expect(await getAdminUser()).toBeNull();
  });

  it("TOTP設定済みでaal2(認証済み)なら通す", async () => {
    authUser = { id: "u1", email: "a@example.com", factors: totp("verified") };
    currentAal = "aal2";
    expect(await getAdminUser()).toEqual({ id: "u1", email: "a@example.com" });
  });

  it("unverifiedなFactorしか無ければ未設定として扱う", async () => {
    authUser = { id: "u1", email: "a@example.com", factors: totp("unverified") };
    currentAal = "aal1";
    expect(await getAdminUser()).toEqual({ id: "u1", email: "a@example.com" });
  });
});

describe("verifiedTotpFactor", () => {
  it("確認済みの TOTP だけを返す(未確認・TOTP以外は設定していないのと同じ)", () => {
    const user = (factors: { id: string; factor_type: string; status: string }[]) => ({ factors }) as unknown as Parameters<typeof verifiedTotpFactor>[0];
    expect(verifiedTotpFactor(user(totp("verified")))?.id).toBe("f1");
    expect(verifiedTotpFactor(user(totp("unverified")))).toBeNull();
    expect(verifiedTotpFactor(user([{ id: "p1", factor_type: "phone", status: "verified" }]))).toBeNull();
    expect(verifiedTotpFactor(user([]))).toBeNull();
  });
});
