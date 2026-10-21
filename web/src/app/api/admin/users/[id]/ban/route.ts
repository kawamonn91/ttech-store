import { z } from "zod";
import { requireAdmin } from "@/lib/admin";
import { adminError } from "@/lib/admin-api";
import { isUuid } from "@/lib/db";
import { jsonError, jsonOk, parseJsonBody } from "@/lib/http";
import { banUser } from "@/lib/moderation";
import { serviceClient } from "@/lib/supabase";

const Body = z.object({ reason: z.string().trim().max(500).default("") });

/** ユーザーをBANする(全サービス共通のアカウント単位)。自分自身・ほかの管理者は不可 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireAdmin(request);
  if ("response" in auth) return auth.response;
  const { id } = await params;
  if (!isUuid(id)) return jsonError("ユーザーが見つかりません", 404);
  const body = await parseJsonBody(request, Body);
  if (!body.success) return jsonError("リクエストが不正です", 400);
  try {
    await banUser(serviceClient(), auth.admin, id, body.data.reason);
    return jsonOk({ ok: true });
  } catch (e) {
    return adminError(e);
  }
}
