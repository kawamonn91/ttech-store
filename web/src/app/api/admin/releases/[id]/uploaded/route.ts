import { requireAdmin } from "@/lib/admin";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { headObject } from "@/lib/r2";
import { dispatchScan } from "@/lib/scan-dispatch";
import { serviceClient } from "@/lib/supabase";

/**
 * アップロード完了の通知。R2 にファイルがあることを確かめ、APK検査(GitHub Actions)を起動する。
 * GitHub が未設定の環境では起動せず、手動で workers/scan/inspect.mjs を実行するための情報を返す。
 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireAdmin(request);
  if ("response" in auth) return auth.response;

  const { id } = await params;
  try {
    const supabase = serviceClient();
    const { data: release } = await supabase.from("app_releases").select("id, apk_key, status").eq("id", id).maybeSingle();
    if (!release) return jsonError("リリースが見つかりません", 404);
    if (release.status !== "uploaded") return jsonError("このリリースは既に検査済みです", 409);

    const head = await headObject(release.apk_key);
    if (!head) return jsonError("APKのアップロードを確認できません", 409);
    await supabase.from("app_releases").update({ apk_size: head.size }).eq("id", id);

    const result = await dispatchScan(id, release.apk_key);
    if ("error" in result) return jsonError(result.error, result.status);
    return jsonOk(result);
  } catch (e) {
    return internalError(e);
  }
}
