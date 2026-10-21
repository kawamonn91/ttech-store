import type { NextRequest } from "next/server";
import { safeNextPath } from "@/lib/redirect";
import { sessionClient } from "@/lib/supabase";

/**
 * このサイトの中のパスへ転送する。転送先は相対パスで返す(ブラウザは今いるサイトを基準に解釈する)。
 * request.nextUrl のオリジンは、Next.js が待ち受けているアドレス(本番では localhost:3200)になり、
 * トンネルの外から見た https://store.kawamonn.com にはならないので、使わない。
 */
function redirectTo(path: string): Response {
  return new Response(null, { status: 307, headers: { Location: path } });
}

/**
 * Google OAuth・メール確認リンクの戻り先。Supabase から渡される code をセッションに交換し、
 * 元々行きたかった場所(next。このサイトの中のパスだけ)へ進める。
 */
export async function GET(request: NextRequest) {
  const { searchParams } = request.nextUrl;
  const code = searchParams.get("code");
  const next = safeNextPath(searchParams.get("next"), "/account");

  if (code) {
    const supabase = await sessionClient();
    const { error } = await supabase.auth.exchangeCodeForSession(code);
    if (!error) return redirectTo(next);
  }
  return redirectTo("/login?error=auth");
}
