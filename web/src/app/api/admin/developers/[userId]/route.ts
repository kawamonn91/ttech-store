import { z } from "zod";
import { adminError, adminOr401, IdParam } from "@/lib/admin-api";
import { jsonError, jsonOk } from "@/lib/http";
import { setDeveloperStatus } from "@/lib/moderation";
import { serviceClient } from "@/lib/supabase";

const Body = z.object({ status: z.enum(["approved", "suspended"]) });

/** 開発者の承認・停止 */
export async function POST(request: Request, { params }: { params: Promise<{ userId: string }> }) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  const { userId } = await params;
  if (!IdParam.safeParse(userId).success) return jsonError("開発者が見つかりません", 404);
  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return jsonError("リクエストが不正です", 400);
  try {
    await setDeveloperStatus(serviceClient(), auth.admin, userId, body.data.status);
    return jsonOk({ ok: true });
  } catch (e) {
    return adminError(e);
  }
}
