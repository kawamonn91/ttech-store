import { jsonError } from "./http";
import { getAdminUser, publicClient, serviceClient, type AdminUser } from "./supabase";

/**
 * 管理用 Route Handler の入口。管理者でなければエラーレスポンスを返す。
 * これを通った後にだけ service_role のクライアントを使うこと。
 *
 * Web管理コンソール(Cookieセッション)に加えて、Storeアプリ(ネイティブ)からの
 * `Authorization: Bearer <access_token>` ヘッダーでの呼び出しにも対応する。
 * 管理者がTOTP(2段階認証)を設定済みの場合、Bearerトークン経由でも
 * aal2(2段階認証済み)のトークンでなければ許可しない。ストアアプリ側は
 * `code: "totp_required"` を見てTOTPコード入力→ /auth/v1/factors/:id/verify で
 * aal2トークンを取得してから再試行する。
 */
export async function requireAdmin(request?: Request): Promise<{ admin: AdminUser } | { response: Response }> {
  const cookieAdmin = await getAdminUser();
  if (cookieAdmin) return { admin: cookieAdmin };

  const bearer = request?.headers.get("authorization")?.match(/^Bearer (.+)$/i)?.[1];
  if (bearer) {
    const result = await adminFromBearerToken(bearer);
    if (result.ok) return { admin: result.admin };
    if (result.totpRequired) {
      return { response: jsonError("2段階認証が必要です", 401, { code: "totp_required" }) };
    }
  }
  return { response: jsonError("管理者としてログインしてください", 401) };
}

type BearerAdminResult = { ok: true; admin: AdminUser } | { ok: false; totpRequired: boolean };

async function adminFromBearerToken(accessToken: string): Promise<BearerAdminResult> {
  const { data } = await publicClient().auth.getUser(accessToken);
  const user = data.user;
  if (!user) return { ok: false, totpRequired: false };

  const svc = serviceClient();
  const { data: profile } = await svc.from("profiles").select("role").eq("id", user.id).maybeSingle();
  if (profile?.role !== "admin") return { ok: false, totpRequired: false };

  const { data: full } = await svc.auth.admin.getUserById(user.id);
  const hasVerifiedTotp = (full?.user?.factors ?? []).some((f) => f.factor_type === "totp" && f.status === "verified");
  if (hasVerifiedTotp && decodeAal(accessToken) !== "aal2") {
    return { ok: false, totpRequired: true };
  }

  return { ok: true, admin: { id: user.id, email: user.email } };
}

/**
 * アクセストークン(JWT)の `aal` クレームをローカルでデコードする(supabase-jsの
 * `mfa.getAuthenticatorAssuranceLevel()` と同じ考え方: 署名検証はすでに
 * `auth.getUser(accessToken)` がAuthサーバー側で行っているので、ここではペイロードを
 * 読むだけでよい)。
 */
function decodeAal(accessToken: string): string | null {
  try {
    const payload = accessToken.split(".")[1];
    if (!payload) return null;
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const json = Buffer.from(base64, "base64").toString("utf-8");
    return JSON.parse(json)?.aal ?? null;
  } catch {
    return null;
  }
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
