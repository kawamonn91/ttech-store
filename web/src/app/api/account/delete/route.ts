import { internalError, jsonError, jsonOk } from "@/lib/http";
import { sessionClient, serviceClient } from "@/lib/supabase";

/**
 * 本人によるアカウント削除。公開中/審査中のアプリが残っている開発者は、
 * 先にアプリを整理してもらう(意図せずアプリが宙に浮くのを防ぐため)。
 */
export async function POST() {
  const supabase = await sessionClient();
  const { data } = await supabase.auth.getUser();
  const user = data.user;
  if (!user) return jsonError("ログインしてください", 401);

  try {
    const svc = serviceClient();
    const { count } = await svc.from("apps").select("id", { count: "exact", head: true }).eq("developer_id", user.id);
    if (count) return jsonError("作成したアプリが残っています。先にアプリを削除するか運営にご相談ください", 409);

    const { error } = await svc.auth.admin.deleteUser(user.id);
    if (error) throw error;
    return jsonOk({ ok: true });
  } catch (e) {
    return internalError(e);
  }
}
