import { requireAdmin } from "@/lib/admin";
import { DEVELOPER_LIMITS } from "@/lib/app-rules";
import { isUuid } from "@/lib/db";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { apkKey, copyObject, deleteObject, headObject, uploadKey } from "@/lib/r2";
import { dispatchScan } from "@/lib/scan-dispatch";
import { serviceClient } from "@/lib/supabase";

const MAX_APK_BYTES = DEVELOPER_LIMITS.maxApkBytes;

/**
 * アップロード完了の通知。R2 のアップロード用キー(uploadKey)にあるファイルのサイズを確かめ、
 * 検査・公開用キー(apkKey)へ複製してアップロード元を削除したうえで、APK検査(GitHub Actions)を起動する。
 * GitHub が未設定の環境では起動せず、手動で workers/scan/inspect.mjs を実行するための情報を返す。
 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireAdmin(request);
  if ("response" in auth) return auth.response;

  const { id } = await params;
  if (!isUuid(id)) return jsonError("リクエストが不正です", 400);

  try {
    const supabase = serviceClient();
    const { data: release } = await supabase.from("app_releases").select("id, app_id, apk_key, status").eq("id", id).maybeSingle();
    if (!release) return jsonError("リリースが見つかりません", 404);
    if (release.status !== "uploaded") return jsonError("このリリースは既に検査済みです", 409);

    const fromKey = uploadKey(release.app_id, id);
    const toKey = apkKey(release.app_id, id);
    const head = await headObject(fromKey);
    if (!head) return jsonError("APKのアップロードを確認できません", 409);
    if (head.size > MAX_APK_BYTES) {
      await deleteObject(fromKey).catch(() => {});
      const reason = `APKが大きすぎます(上限 ${MAX_APK_BYTES / 1024 / 1024}MB)`;
      await supabase.from("app_releases").update({ status: "rejected", scan_result: { error: reason } }).eq("id", id);
      return jsonError(reason, 413);
    }

    if (!(await copyObject(fromKey, toKey))) return jsonError("APKのアップロードを確認できません", 409);
    await supabase.from("app_releases").update({ apk_key: toKey, apk_size: head.size }).eq("id", id);
    await deleteObject(fromKey).catch((e) => console.warn("アップロード先の削除に失敗しました:", e instanceof Error ? e.message : e));

    const result = await dispatchScan(id, toKey);
    if ("error" in result) return jsonError(result.error, result.status);
    return jsonOk(result);
  } catch (e) {
    return internalError(e);
  }
}
