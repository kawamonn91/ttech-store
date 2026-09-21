import { NextRequest, NextResponse } from "next/server";
import { sessionClient } from "@/lib/supabase";

/**
 * Google OAuth・メール確認リンクの戻り先。Supabase から渡される code をセッションに交換し、
 * 元々行きたかった場所(next)へ進める。
 */
export async function GET(request: NextRequest) {
  const { searchParams, origin } = request.nextUrl;
  const code = searchParams.get("code");
  const next = searchParams.get("next") ?? "/account";

  if (code) {
    const supabase = await sessionClient();
    const { error } = await supabase.auth.exchangeCodeForSession(code);
    if (!error) return NextResponse.redirect(`${origin}${next}`);
  }
  return NextResponse.redirect(`${origin}/login?error=auth`);
}
