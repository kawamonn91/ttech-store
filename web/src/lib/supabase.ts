import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { createServerClient } from "@supabase/ssr";
import { cookies } from "next/headers";
import { env } from "./env";

/** 公開データの読み取り用(anon 権限)。RLS により published のものだけ読める */
export function publicClient(): SupabaseClient {
  return createClient(env.supabaseUrl(), env.supabaseAnonKey(), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

/**
 * service_role 権限のクライアント。RLS を素通りするため、サーバー側の
 * 認可チェック(管理者確認・HMAC検証など)を通った後にだけ使うこと。
 */
export function serviceClient(): SupabaseClient {
  return createClient(env.supabaseUrl(), env.supabaseServiceRoleKey(), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

/** ログイン中ユーザーのセッション(Cookie)を扱うクライアント。管理コンソール用 */
export async function sessionClient(): Promise<SupabaseClient> {
  const store = await cookies();
  return createServerClient(env.supabaseUrl(), env.supabaseAnonKey(), {
    cookies: {
      getAll: () => store.getAll(),
      setAll: (list) => {
        try {
          for (const { name, value, options } of list) store.set(name, value, options);
        } catch {
          // Server Component からの呼び出しでは Cookie を書けない。proxy 側で更新されるので無視してよい
        }
      },
    },
  });
}

export interface AdminUser {
  id: string;
  email: string | undefined;
}

/**
 * ログイン中かつ role=admin のユーザーを返す。
 * 管理者がTOTP(2段階認証)を設定済みの場合、このセッションが2段階認証済み(aal2)で
 * なければ null を返す(パスワードだけの状態では管理者権限を行使できない)。
 * まだTOTPを設定していない管理者は、移行期間として通常通り通す
 * (setupAdminMfaStatus で「設定してください」と促す)。
 */
export async function getAdminUser(): Promise<AdminUser | null> {
  const supabase = await sessionClient();
  const { data } = await supabase.auth.getUser();
  const user = data.user;
  if (!user) return null;
  const { data: profile } = await supabase.from("profiles").select("role").eq("id", user.id).maybeSingle();
  if (profile?.role !== "admin") return null;

  const { data: factors } = await supabase.auth.mfa.listFactors();
  const hasVerifiedTotp = (factors?.totp ?? []).some((f) => f.status === "verified");
  if (hasVerifiedTotp) {
    const { data: aal } = await supabase.auth.mfa.getAuthenticatorAssuranceLevel();
    if (aal?.currentLevel !== "aal2") return null;
  }
  return { id: user.id, email: user.email };
}

export interface AdminMfaStatus {
  /** role=admin かどうか(このユーザーがそもそも管理者か) */
  isAdmin: boolean;
  /** TOTPを1つ以上、有効化(verified)しているか */
  hasVerifiedTotp: boolean;
  /** 現在のセッションが2段階認証済み(aal2)か */
  isStepUpDone: boolean;
}

/** /admin/login の画面制御用。「パスワードは合っているがコード入力が要る」状態を区別するために使う */
export async function getAdminMfaStatus(): Promise<AdminMfaStatus | null> {
  const supabase = await sessionClient();
  const { data } = await supabase.auth.getUser();
  if (!data.user) return null;
  const { data: profile } = await supabase.from("profiles").select("role").eq("id", data.user.id).maybeSingle();
  if (profile?.role !== "admin") return { isAdmin: false, hasVerifiedTotp: false, isStepUpDone: false };

  const { data: factors } = await supabase.auth.mfa.listFactors();
  const hasVerifiedTotp = (factors?.totp ?? []).some((f) => f.status === "verified");
  const { data: aal } = await supabase.auth.mfa.getAuthenticatorAssuranceLevel();
  return { isAdmin: true, hasVerifiedTotp, isStepUpDone: aal?.currentLevel === "aal2" };
}
