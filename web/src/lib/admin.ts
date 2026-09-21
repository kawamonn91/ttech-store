import { jsonError } from "./http";
import { getAdminUser, publicClient, serviceClient, type AdminUser } from "./supabase";

/**
 * 管理用 Route Handler の入口。管理者でなければエラーレスポンスを返す。
 * これを通った後にだけ service_role のクライアントを使うこと。
 *
 * Web管理コンソール(Cookieセッション)に加えて、Storeアプリ(ネイティブ)からの
 * `Authorization: Bearer <access_token>` ヘッダーでの呼び出しにも対応する。
 * ネイティブアプリ側は2段階認証(TOTP)の仕組みを持たないため、Bearerトークン経由の
 * 場合は管理者がTOTP未設定のときだけ許可する(設定済みなら、Web側でのみ操作できる)。
 */
export async function requireAdmin(request?: Request): Promise<{ admin: AdminUser } | { response: Response }> {
  const cookieAdmin = await getAdminUser();
  if (cookieAdmin) return { admin: cookieAdmin };

  const bearer = request?.headers.get("authorization")?.match(/^Bearer (.+)$/i)?.[1];
  if (bearer) {
    const admin = await adminFromBearerToken(bearer);
    if (admin) return { admin };
  }
  return { response: jsonError("管理者としてログインしてください", 401) };
}

async function adminFromBearerToken(accessToken: string): Promise<AdminUser | null> {
  const { data } = await publicClient().auth.getUser(accessToken);
  const user = data.user;
  if (!user) return null;

  const svc = serviceClient();
  const { data: profile } = await svc.from("profiles").select("role").eq("id", user.id).maybeSingle();
  if (profile?.role !== "admin") return null;

  const { data: full } = await svc.auth.admin.getUserById(user.id);
  const hasVerifiedTotp = (full?.user?.factors ?? []).some((f) => f.factor_type === "totp" && f.status === "verified");
  if (hasVerifiedTotp) return null; // TOTP設定済みならBearerトークンだけでは不可(Web側でのみ)

  return { id: user.id, email: user.email };
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
