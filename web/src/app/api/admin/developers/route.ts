import { adminError, adminOr401 } from "@/lib/admin-api";
import { listDevelopers } from "@/lib/admin-queries";
import { jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

const STATUSES = ["pending", "approved", "suspended"];

/** 開発者の一覧。?status=pending|approved|suspended で絞り込み */
export async function GET(request: Request) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  try {
    const status = new URL(request.url).searchParams.get("status");
    return jsonOk({ items: await listDevelopers(serviceClient(), status && STATUSES.includes(status) ? status : undefined) });
  } catch (e) {
    return adminError(e);
  }
}
