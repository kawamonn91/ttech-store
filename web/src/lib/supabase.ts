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

/** ログイン中かつ role=admin のユーザーを返す。そうでなければ null */
export async function getAdminUser(): Promise<AdminUser | null> {
  const supabase = await sessionClient();
  const { data } = await supabase.auth.getUser();
  const user = data.user;
  if (!user) return null;
  const { data: profile } = await supabase.from("profiles").select("role").eq("id", user.id).maybeSingle();
  if (profile?.role !== "admin") return null;
  return { id: user.id, email: user.email };
}
