import { adminError, adminOr401, paging } from "@/lib/admin-api";
import { listUsers } from "@/lib/admin-queries";
import { jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

/** ユーザー一覧(全サービス共通のアカウント)。?q=メール/表示名 &banned=1 &limit= &offset= */
export async function GET(request: Request) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  try {
    const url = new URL(request.url);
    return jsonOk(
      await listUsers(serviceClient(), {
        q: url.searchParams.get("q") ?? undefined,
        bannedOnly: url.searchParams.get("banned") === "1",
        ...paging(url),
      }),
    );
  } catch (e) {
    return adminError(e);
  }
}
