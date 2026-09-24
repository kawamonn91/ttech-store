import { adminError, adminOr401 } from "@/lib/admin-api";
import { listReports } from "@/lib/admin-queries";
import { jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

/** 報告の一覧(ひとこと日記の報告 + アプリのレビュー通報)。既定は未対応のみ。?status=all で全件 */
export async function GET(request: Request) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  try {
    const url = new URL(request.url);
    return jsonOk({ items: await listReports(serviceClient(), { openOnly: url.searchParams.get("status") !== "all" }) });
  } catch (e) {
    return adminError(e);
  }
}
