import { z } from "zod";
import { adminError, adminOr401, IdParam } from "@/lib/admin-api";
import { jsonError, jsonOk } from "@/lib/http";
import { setAppStatus } from "@/lib/moderation";
import { serviceClient } from "@/lib/supabase";

const Body = z.object({ status: z.enum(["published", "suspended"]) });

/** アプリの公開・公開停止 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  const { id } = await params;
  if (!IdParam.safeParse(id).success) return jsonError("アプリが見つかりません", 404);
  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return jsonError("リクエストが不正です", 400);
  try {
    await setAppStatus(serviceClient(), auth.admin, id, body.data.status);
    return jsonOk({ ok: true });
  } catch (e) {
    return adminError(e);
  }
}
