import { beforeEach, describe, expect, it, vi } from "vitest";

const sendAdminMail = vi.fn();
const loadNotification = vi.fn();

vi.mock("@/lib/supabase", () => ({ serviceClient: () => ({}) }));
vi.mock("@/lib/notify", async (orig) => ({
  ...(await orig<typeof import("@/lib/notify")>()),
  loadNotification: (...a: unknown[]) => loadNotification(...a),
}));
vi.mock("@/lib/mail", async (orig) => ({
  ...(await orig<typeof import("@/lib/mail")>()),
  sendAdminMail: (...a: unknown[]) => sendAdminMail(...a),
}));

import { MailNotConfiguredError } from "@/lib/mail";
import { POST } from "./route";

const call = (body: unknown, secret: string | null = "topsecret") =>
  POST(
    new Request("http://localhost/api/internal/notify", {
      method: "POST",
      headers: secret === null ? {} : { "x-notify-secret": secret },
      body: typeof body === "string" ? body : JSON.stringify(body),
    }),
  );

beforeEach(() => {
  vi.stubEnv("NOTIFY_WEBHOOK_SECRET", "topsecret");
  sendAdminMail.mockReset();
  loadNotification.mockReset();
  loadNotification.mockResolvedValue({ subject: "s", text: "t" });
  sendAdminMail.mockResolvedValue(undefined);
});

describe("POST /api/internal/notify", () => {
  it("シークレットが無い/違うと 401 で、何も読まず何も送らない", async () => {
    expect((await call({ kind: "diary_report", id: "1" }, null)).status).toBe(401);
    expect((await call({ kind: "diary_report", id: "1" }, "wrong")).status).toBe(401);
    expect((await call({ kind: "diary_report", id: "1" }, "topsecre")).status).toBe(401);
    expect(loadNotification).not.toHaveBeenCalled();
    expect(sendAdminMail).not.toHaveBeenCalled();
  });

  it("不正な本文は 400", async () => {
    expect((await call("not json")).status).toBe(400);
    expect((await call({ kind: "other", id: "1" })).status).toBe(400);
    expect((await call({ kind: "diary_report" })).status).toBe(400);
  });

  it("正しければ内容を読んでメールを送る", async () => {
    const res = await call({ kind: "diary_report", id: "abc" });
    expect(res.status).toBe(200);
    expect(loadNotification).toHaveBeenCalledWith(expect.anything(), "diary_report", "abc");
    expect(sendAdminMail).toHaveBeenCalledWith({ subject: "s", text: "t" });
  });

  it("対象が無ければ 404 でメールは送らない", async () => {
    loadNotification.mockResolvedValue(null);
    expect((await call({ kind: "review_report", id: "9" })).status).toBe(404);
    expect(sendAdminMail).not.toHaveBeenCalled();
  });

  it("メール未設定なら 503", async () => {
    sendAdminMail.mockRejectedValue(new MailNotConfiguredError("x"));
    expect((await call({ kind: "developer_application", id: "u" })).status).toBe(503);
  });

  it("メール送信の失敗は 500(内容は返さない)", async () => {
    sendAdminMail.mockRejectedValue(new Error("Resend down: key=re_secret"));
    const res = await call({ kind: "diary_report", id: "1" });
    expect(res.status).toBe(500);
    expect(JSON.stringify(await res.json())).not.toContain("re_secret");
  });
});
