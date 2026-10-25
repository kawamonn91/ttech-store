import { createServerClient } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";
import { authCookieName } from "./lib/auth-cookie";

/**
 * 管理コンソール・開発者ポータル・アカウントページのセッション(Cookie)を最新に保つ。
 * Supabase のアクセストークンは短命なので、リクエストのたびにここで更新する。
 */
export async function proxy(request: NextRequest) {
  const publicUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const url = process.env.INTERNAL_SUPABASE_URL || publicUrl;
  const anon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  if (!url || !anon || !publicUrl) return NextResponse.next();

  let response = NextResponse.next({ request });
  const supabase = createServerClient(url, anon, {
    cookieOptions: { name: authCookieName(publicUrl) },
    cookies: {
      getAll: () => request.cookies.getAll(),
      setAll: (list) => {
        for (const { name, value } of list) request.cookies.set(name, value);
        response = NextResponse.next({ request });
        for (const { name, value, options } of list) response.cookies.set(name, value, options);
      },
    },
  });
  await supabase.auth.getUser();
  return response;
}

export const config = { matcher: ["/admin/:path*", "/developer/:path*", "/account/:path*"] };
