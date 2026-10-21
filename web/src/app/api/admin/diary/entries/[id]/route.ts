import { requireAdmin } from "@/lib/admin";
import { adminError } from "@/lib/admin-api";
import { isUuid } from "@/lib/db";
import { jsonError, jsonOk } from "@/lib/http";
import { deleteDiaryEntry } from "@/lib/moderation";
import { serviceClient } from "@/lib/supabase";

/** ひとこと日記の投稿を削除する(添付写真も消える) */
export async function DELETE(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireAdmin(request);
  if ("response" in auth) return auth.response;
  const { id } = await params;
  if (!isUuid(id)) return jsonError("投稿が見つかりません", 404);
  try {
    await deleteDiaryEntry(serviceClient(), auth.admin, id);
    return jsonOk({ ok: true });
  } catch (e) {
    return adminError(e);
  }
}
