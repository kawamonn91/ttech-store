import { env } from "./env";

export interface MailMessage {
  subject: string;
  text: string;
}

/** 送信先・APIキーが未設定のとき。呼び出し側は 503 を返し、DB側の報告登録自体には影響させない */
export class MailNotConfiguredError extends Error {}

/**
 * 運営(管理者)宛のメールを Resend の API で送る。
 * 本文はテキストのみ(利用者が書いた内容を含むため、HTMLとして解釈させない)。
 */
export async function sendAdminMail(message: MailMessage, fetchImpl: typeof fetch = fetch): Promise<void> {
  const apiKey = env.resendApiKey();
  const to = env.adminNotifyEmail();
  if (!apiKey || !to) throw new MailNotConfiguredError("RESEND_API_KEY / ADMIN_NOTIFY_EMAIL が未設定です");

  const res = await fetchImpl("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: env.mailFrom(), to: [to], subject: message.subject, text: message.text }),
  });
  if (!res.ok) throw new Error(`メール送信に失敗しました (${res.status}): ${await res.text().catch(() => "")}`);
}
