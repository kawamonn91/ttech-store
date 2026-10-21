import { z } from "zod";
import { requireAdmin } from "@/lib/admin";
import { adminError } from "@/lib/admin-api";
import { isUuid } from "@/lib/db";
import { jsonError, jsonOk, parseJsonBody } from "@/lib/http";
import { setDeveloperStatus } from "@/lib/moderation";
import { serviceClient } from "@/lib/supabase";

const Body = z.object({ status: z.enum(["approved", "suspended"]) });

/** 開発者の承認・停止 */
export async function POST(request: Request, { params }: { params: Promise<{ userId: string }> }) {
  const auth = await requireAdmin(request);
  if ("response" in auth) return auth.response;
  const { userId } = await params;
  if (!isUuid(userId)) return jsonError("開発者が見つかりません", 404);
  const body = await parseJsonBody(request, Body);
  if (!body.success) return jsonError("リクエストが不正です", 400);
  try {
    await setDeveloperStatus(serviceClient(), auth.admin, userId, body.data.status);
    return jsonOk({ ok: true });
  } catch (e) {
    return adminError(e);
  }
}
