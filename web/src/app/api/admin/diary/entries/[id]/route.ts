import { adminError, adminOr401, IdParam } from "@/lib/admin-api";
import { jsonError, jsonOk } from "@/lib/http";
import { deleteDiaryEntry } from "@/lib/moderation";
import { serviceClient } from "@/lib/supabase";

/** ひとこと日記の投稿を削除する(添付写真も消える) */
export async function DELETE(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  const { id } = await params;
  if (!IdParam.safeParse(id).success) return jsonError("投稿が見つかりません", 404);
  try {
    await deleteDiaryEntry(serviceClient(), auth.admin, id);
    return jsonOk({ ok: true });
  } catch (e) {
    return adminError(e);
  }
}
