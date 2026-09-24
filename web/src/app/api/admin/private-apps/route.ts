import { adminError, adminOr401 } from "@/lib/admin-api";
import { listPrivateApps } from "@/lib/admin-queries";
import { jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

/** 管理者専用アプリ(公開カタログには出ない)と、その最新リリース。マイページのダウンロード欄で使う */
export async function GET(request: Request) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  try {
    return jsonOk({ items: await listPrivateApps(serviceClient()) });
  } catch (e) {
    return adminError(e);
  }
}
