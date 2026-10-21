import { z } from "zod";
import { requireAdmin } from "@/lib/admin";
import { adminError } from "@/lib/admin-api";
import { isUuid } from "@/lib/db";
import { jsonError, jsonOk, parseJsonBody } from "@/lib/http";
import { setAppStatus } from "@/lib/moderation";
import { serviceClient } from "@/lib/supabase";

const Body = z.object({ status: z.enum(["published", "suspended"]) });

/** アプリの公開・公開停止 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireAdmin(request);
  if ("response" in auth) return auth.response;
  const { id } = await params;
  if (!isUuid(id)) return jsonError("アプリが見つかりません", 404);
  const body = await parseJsonBody(request, Body);
  if (!body.success) return jsonError("リクエストが不正です", 400);
  try {
    await setAppStatus(serviceClient(), auth.admin, id, body.data.status);
    return jsonOk({ ok: true });
  } catch (e) {
    return adminError(e);
  }
}
