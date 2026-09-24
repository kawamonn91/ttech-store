import { adminError, adminOr401 } from "@/lib/admin-api";
import { getOverview } from "@/lib/admin-queries";
import { jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

/** 管理アプリのホーム: 全サービスの件数のまとめ */
export async function GET(request: Request) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  try {
    return jsonOk(await getOverview(serviceClient()));
  } catch (e) {
    return adminError(e);
  }
}
