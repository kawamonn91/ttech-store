import { jsonError } from "./http";
import { getAdminUser, serviceClient, type AdminUser } from "./supabase";

/**
 * 管理用 Route Handler の入口。管理者でなければエラーレスポンスを返す。
 * これを通った後にだけ service_role のクライアントを使うこと。
 */
export async function requireAdmin(): Promise<{ admin: AdminUser } | { response: Response }> {
  const admin = await getAdminUser();
  if (!admin) return { response: jsonError("管理者としてログインしてください", 401) };
  return { admin };
}

/**
 * 管理者自身を「承認済み開発者」として登録しておく(apps.developer_id の参照先になるため)。
 * 最初は自分のアプリしか載せないので、管理者=開発者として扱う。
 */
export async function ensureAdminDeveloper(admin: AdminUser): Promise<void> {
  const supabase = serviceClient();
  const { data } = await supabase.from("developers").select("user_id").eq("user_id", admin.id).maybeSingle();
  if (data) return;
  const { error } = await supabase.from("developers").insert({
    user_id: admin.id,
    name: "T-tech",
    contact_email: admin.email ?? "",
    status: "approved",
    verified_at: new Date().toISOString(),
  });
  if (error) throw error;
}
