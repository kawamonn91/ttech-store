import { timingSafeEqual } from "node:crypto";
import { z } from "zod";
import { env } from "@/lib/env";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { MailNotConfiguredError, sendAdminMail } from "@/lib/mail";
import { loadNotification, NOTIFY_KINDS } from "@/lib/notify";
import { serviceClient } from "@/lib/supabase";

const Body = z.object({ kind: z.enum(NOTIFY_KINDS as [string, ...string[]]), id: z.string().min(1).max(64) });

function secretMatches(given: string | null): boolean {
  if (!given) return false;
  const a = Buffer.from(given);
  const b = Buffer.from(env.notifyWebhookSecret());
  return a.length === b.length && timingSafeEqual(a, b);
}

/**
 * DB(pg_net のトリガー)からの通知を受けて、運営にメールを送る。
 * 認証は共有シークレット(x-notify-secret)。本文には種類とIDしか載っていないので、
 * 内容(報告の本文など)はここでDBから読み直してメールにする。
 */
export async function POST(request: Request) {
  if (!secretMatches(request.headers.get("x-notify-secret"))) return jsonError("unauthorized", 401);

  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return jsonError("リクエストが不正です", 400);

  try {
    const message = await loadNotification(serviceClient(), body.data.kind as Parameters<typeof loadNotification>[1], body.data.id);
    if (!message) return jsonError("対象が見つかりません", 404);
    await sendAdminMail(message);
    return jsonOk({ ok: true });
  } catch (e) {
    if (e instanceof MailNotConfiguredError) return jsonError("メール送信が設定されていません", 503);
    return internalError(e);
  }
}
