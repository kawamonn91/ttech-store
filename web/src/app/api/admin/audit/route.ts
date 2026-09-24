import { adminError, adminOr401, paging } from "@/lib/admin-api";
import { listAudit } from "@/lib/admin-queries";
import { jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

/** 管理操作の監査ログ(いつ・誰が・何をしたか) */
export async function GET(request: Request) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  try {
    const { limit } = paging(new URL(request.url), { limit: 50, max: 200 });
    return jsonOk({ items: await listAudit(serviceClient(), limit) });
  } catch (e) {
    return adminError(e);
  }
}
