import { beforeEach, describe, expect, it, vi } from "vitest";

let authUser: { id: string; email?: string } | null;
let profileRole: string | null;
let totpFactors: { id: string; status: string }[];
let currentAal: string;

vi.mock("@supabase/ssr", () => ({
  createServerClient: () => ({
    auth: {
      getUser: async () => ({ data: { user: authUser } }),
      mfa: {
        listFactors: async () => ({ data: { totp: totpFactors } }),
        getAuthenticatorAssuranceLevel: async () => ({ data: { currentLevel: currentAal } }),
      },
    },
    from: () => ({ select: () => ({ eq: () => ({ maybeSingle: async () => ({ data: { role: profileRole } }) }) }) }),
  }),
}));
vi.mock("next/headers", () => ({ cookies: async () => ({ getAll: () => [], set: () => {} }) }));

import { getAdminUser, getAdminMfaStatus } from "./supabase";

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_SUPABASE_URL", "https://proj.supabase.co");
  vi.stubEnv("NEXT_PUBLIC_SUPABASE_ANON_KEY", "anon");
  authUser = { id: "u1", email: "a@example.com" };
  profileRole = "admin";
  totpFactors = [];
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
    totpFactors = [];
    currentAal = "aal1";
    expect(await getAdminUser()).toEqual({ id: "u1", email: "a@example.com" });
  });

  it("TOTP設定済みでaal1(未認証)なら null", async () => {
    totpFactors = [{ id: "f1", status: "verified" }];
    currentAal = "aal1";
    expect(await getAdminUser()).toBeNull();
  });

  it("TOTP設定済みでaal2(認証済み)なら通す", async () => {
    totpFactors = [{ id: "f1", status: "verified" }];
    currentAal = "aal2";
    expect(await getAdminUser()).toEqual({ id: "u1", email: "a@example.com" });
  });

  it("unverifiedなFactorしか無ければ未設定として扱う", async () => {
    totpFactors = [{ id: "f1", status: "unverified" }];
    currentAal = "aal1";
    expect(await getAdminUser()).toEqual({ id: "u1", email: "a@example.com" });
  });
});

describe("getAdminMfaStatus", () => {
  it("未ログインなら null", async () => {
    authUser = null;
    expect(await getAdminMfaStatus()).toBeNull();
  });

  it("admin以外は isAdmin=false", async () => {
    profileRole = "user";
    expect(await getAdminMfaStatus()).toEqual({ isAdmin: false, hasVerifiedTotp: false, isStepUpDone: false });
  });

  it("adminでTOTP未設定かつaal1", async () => {
    expect(await getAdminMfaStatus()).toEqual({ isAdmin: true, hasVerifiedTotp: false, isStepUpDone: false });
  });

  it("adminでTOTP設定済み・aal2", async () => {
    totpFactors = [{ id: "f1", status: "verified" }];
    currentAal = "aal2";
    expect(await getAdminMfaStatus()).toEqual({ isAdmin: true, hasVerifiedTotp: true, isStepUpDone: true });
  });
});
