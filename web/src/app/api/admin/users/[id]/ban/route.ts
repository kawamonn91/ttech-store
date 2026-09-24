import { z } from "zod";
import { adminError, adminOr401, IdParam } from "@/lib/admin-api";
import { jsonError, jsonOk } from "@/lib/http";
import { banUser } from "@/lib/moderation";
import { serviceClient } from "@/lib/supabase";

const Body = z.object({ reason: z.string().trim().max(500).default("") });

/** ユーザーをBANする(全サービス共通のアカウント単位)。自分自身・ほかの管理者は不可 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  const { id } = await params;
  if (!IdParam.safeParse(id).success) return jsonError("ユーザーが見つかりません", 404);
  const body = Body.safeParse(await request.json().catch(() => ({})));
  if (!body.success) return jsonError("リクエストが不正です", 400);
  try {
    await banUser(serviceClient(), auth.admin, id, body.data.reason);
    return jsonOk({ ok: true });
  } catch (e) {
    return adminError(e);
  }
}
