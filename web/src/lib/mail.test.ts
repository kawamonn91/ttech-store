import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MailNotConfiguredError, sendAdminMail } from "./mail";

const message = { subject: "件名", text: "本文" };

const bodyOf = (fetchMock: ReturnType<typeof vi.fn>, call: number) =>
  JSON.parse((fetchMock.mock.calls[call] as unknown as [string, RequestInit])[1].body as string);

beforeEach(() => {
  vi.stubEnv("RESEND_API_KEY", "re_test");
  vi.stubEnv("ADMIN_NOTIFY_EMAIL", "owner@example.com");
  vi.stubEnv("MAIL_FROM", "");
});
afterEach(() => vi.unstubAllEnvs());

describe("sendAdminMail", () => {
  it("Resend にテキストメールとして送る(HTMLにはしない)", async () => {
    const fetchMock = vi.fn(async () => new Response("{}", { status: 200 }));
    await sendAdminMail(message, fetchMock as unknown as typeof fetch);

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("https://api.resend.com/emails");
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer re_test");
    const sent = bodyOf(fetchMock, 0);
    expect(sent.to).toEqual(["owner@example.com"]);
    expect(sent.subject).toBe("件名");
    expect(sent.text).toBe("本文");
    expect(sent.html).toBeUndefined();
  });

  it("差出人は MAIL_FROM があればそれ、なければ onboarding@resend.dev", async () => {
    const fetchMock = vi.fn(async () => new Response("{}", { status: 200 }));
    await sendAdminMail(message, fetchMock as unknown as typeof fetch);
    expect(bodyOf(fetchMock, 0).from).toContain("onboarding@resend.dev");

    vi.stubEnv("MAIL_FROM", "T-tech <noreply@kawamonn.com>");
    await sendAdminMail(message, fetchMock as unknown as typeof fetch);
    expect(bodyOf(fetchMock, 1).from).toBe("T-tech <noreply@kawamonn.com>");
  });

  it("APIキーか通知先が未設定なら送らずに MailNotConfiguredError", async () => {
    const fetchMock = vi.fn();
    vi.stubEnv("RESEND_API_KEY", "");
    await expect(sendAdminMail(message, fetchMock as unknown as typeof fetch)).rejects.toBeInstanceOf(MailNotConfiguredError);
    vi.stubEnv("RESEND_API_KEY", "re_test");
    vi.stubEnv("ADMIN_NOTIFY_EMAIL", "");
    await expect(sendAdminMail(message, fetchMock as unknown as typeof fetch)).rejects.toBeInstanceOf(MailNotConfiguredError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("Resend がエラーを返したら例外にする(ステータスを含める)", async () => {
    const fetchMock = vi.fn(async () => new Response("bad", { status: 422 }));
    await expect(sendAdminMail(message, fetchMock as unknown as typeof fetch)).rejects.toThrow("422");
  });
});
