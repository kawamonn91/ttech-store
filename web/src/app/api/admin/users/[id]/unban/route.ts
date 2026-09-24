import { adminError, adminOr401, IdParam } from "@/lib/admin-api";
import { jsonError, jsonOk } from "@/lib/http";
import { unbanUser } from "@/lib/moderation";
import { serviceClient } from "@/lib/supabase";

/** BANの解除 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  const { id } = await params;
  if (!IdParam.safeParse(id).success) return jsonError("ユーザーが見つかりません", 404);
  try {
    await unbanUser(serviceClient(), auth.admin, id);
    return jsonOk({ ok: true });
  } catch (e) {
    return adminError(e);
  }
}
