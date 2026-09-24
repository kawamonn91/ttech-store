import { adminError, adminOr401, IdParam } from "@/lib/admin-api";
import { getUserDetail } from "@/lib/admin-queries";
import { jsonError, jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

/** ユーザー1人の詳細(アカウント情報・各サービスでの活動・報告された履歴) */
export async function GET(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  const { id } = await params;
  if (!IdParam.safeParse(id).success) return jsonError("ユーザーが見つかりません", 404);
  try {
    return jsonOk(await getUserDetail(serviceClient(), id));
  } catch (e) {
    return adminError(e);
  }
}
