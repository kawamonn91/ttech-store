import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

const exchange = vi.hoisted(() => vi.fn());
vi.mock("@/lib/supabase", () => ({ sessionClient: async () => ({ auth: { exchangeCodeForSession: exchange } }) }));

import { GET } from "./route";

// 本番では、Next.js が待ち受けているアドレス(localhost)でリクエストが届く
const call = (query: string) => GET(new NextRequest(`http://localhost:3200/auth/callback${query}`));

beforeEach(() => {
  exchange.mockReset();
  exchange.mockResolvedValue({ error: null });
});

describe("GET /auth/callback", () => {
  it("code をセッションに交換し、next へ相対パスで転送する(localhost のオリジンを付けない)", async () => {
    const res = await call("?code=abc&next=%2Fdeveloper%2Fapps%2Fnew");
    expect(exchange).toHaveBeenCalledWith("abc");
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toBe("/developer/apps/new");
  });

  it("next が無ければマイページへ", async () => {
    expect((await call("?code=abc")).headers.get("location")).toBe("/account");
  });

  it("サイトの外を指す next は使わない", async () => {
    for (const next of ["//evil.example", "https://evil.example/", "/\\evil.example"]) {
      expect((await call(`?code=abc&next=${encodeURIComponent(next)}`)).headers.get("location"), next).toBe("/account");
    }
  });

  it("code が無い・交換に失敗したらログイン画面へ", async () => {
    expect((await call("?next=/developer")).headers.get("location")).toBe("/login?error=auth");
    expect(exchange).not.toHaveBeenCalled();
    exchange.mockResolvedValue({ error: { message: "invalid" } });
    expect((await call("?code=bad")).headers.get("location")).toBe("/login?error=auth");
  });
});
