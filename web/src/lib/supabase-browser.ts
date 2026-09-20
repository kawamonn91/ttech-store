"use client";

import { createBrowserClient } from "@supabase/ssr";

/** ブラウザ用の Supabase クライアント(ログイン・画像アップロード用)。anon キーのみを使う */
export function browserClient() {
  return createBrowserClient(process.env.NEXT_PUBLIC_SUPABASE_URL!, process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!);
}
