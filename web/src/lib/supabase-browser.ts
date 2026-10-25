"use client";

import { createBrowserClient } from "@supabase/ssr";
import { authCookieName } from "./auth-cookie";

/** ブラウザ用の Supabase クライアント(ログイン・画像アップロード用)。anon キーのみを使う */
export function browserClient() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL!;
  // サーバー側(supabase.ts・proxy.ts)と同じ Cookie の名前
  return createBrowserClient(url, process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!, {
    cookieOptions: { name: authCookieName(url) },
  });
}
