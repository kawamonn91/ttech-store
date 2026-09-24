import { adminError, adminOr401, IdParam, paging } from "@/lib/admin-api";
import { listDiaryEntries } from "@/lib/admin-queries";
import { jsonError, jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

/** ひとこと日記の投稿一覧(非公開のものも含む)。?q=本文 &userId= &limit= &offset= */
export async function GET(request: Request) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  const url = new URL(request.url);
  const userId = url.searchParams.get("userId") ?? undefined;
  if (userId && !IdParam.safeParse(userId).success) return jsonError("userId が不正です", 400);
  try {
    return jsonOk({ items: await listDiaryEntries(serviceClient(), { q: url.searchParams.get("q") ?? undefined, userId, ...paging(url) }) });
  } catch (e) {
    return adminError(e);
  }
}
