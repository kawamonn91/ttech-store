import { adminError, adminOr401 } from "@/lib/admin-api";
import { listPendingReleases } from "@/lib/admin-queries";
import { jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

/** 承認待ちのリリース(公開待ち・公開停止待ち)。マイページの承認UI・管理アプリで使う */
export async function GET(request: Request) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  try {
    return jsonOk({ items: await listPendingReleases(serviceClient()) });
  } catch (e) {
    return adminError(e);
  }
}
