import { describe, expect, it } from "vitest";
import { authCookieName } from "./auth-cookie";

describe("authCookieName", () => {
  it("supabase-js の既定と同じく、ホスト名の先頭から作る", () => {
    expect(authCookieName("https://proj.supabase.co")).toBe("sb-proj-auth-token");
    expect(authCookieName("https://store.example.com")).toBe("sb-store-auth-token");
  });

  it("公開の URL から作るので、内部の接続先(IP アドレス)の名前にはならない", () => {
    expect(authCookieName("https://store.example.com/")).not.toBe(authCookieName("http://127.0.0.1:8000"));
  });
});
