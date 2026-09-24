import { adminError, adminOr401 } from "@/lib/admin-api";
import { listAllApps } from "@/lib/admin-queries";
import { jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

/** ストアの全アプリ(非公開・管理者専用も含む)と、DL数・評価 */
export async function GET(request: Request) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  try {
    return jsonOk({ items: await listAllApps(serviceClient()) });
  } catch (e) {
    return adminError(e);
  }
}
