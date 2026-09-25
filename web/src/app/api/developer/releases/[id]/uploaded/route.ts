import { DEVELOPER_LIMITS, requireDeveloper } from "@/lib/developer";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { apkKey, copyObject, deleteObject, headObject } from "@/lib/r2";
import { dispatchScan } from "@/lib/scan-dispatch";

/**
 * APKのアップロード完了の通知。
 *  1. アップロードされたファイルを、検査・公開用の場所(apkKey)へ複製し、アップロード先は削除する。
 *     署名付きURLは失効させられないので、検査のあとで別のファイルに差し替えられないようにするため。
 *  2. 検査(GitHub Actions)を起動する。結果は /api/internal/releases/:id/scan に届き、
 *     自動審査を通れば公開、疑いがあれば運営の承認待ちになる。
 */
export async function POST(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireDeveloper();
  if ("response" in auth) return auth.response;
  const { user, svc } = auth;
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) return jsonError("リリースが見つかりません", 404);

  try {
    const { data } = await svc.from("app_releases").select("id, app_id, apk_key, status, app:apps(id, developer_id, status)").eq("id", id).maybeSingle();
    const release = data as { id: string; app_id: string; apk_key: string; status: string; app: { developer_id: string; status: string } | { developer_id: string; status: string }[] | null } | null;
    const app = Array.isArray(release?.app) ? release?.app[0] : release?.app;
    // 他人のリリースは、存在しないものと同じに見せる
    if (!release || !app || app.developer_id !== user.id) return jsonError("リリースが見つかりません", 404);
    if (release.status !== "uploaded") return jsonError("このリリースは、すでに検査に進んでいます", 409);
    if (app.status === "suspended") return jsonError("このアプリは停止されています。運営にお問い合わせください", 403);

    const head = await headObject(release.apk_key);
    if (!head) return jsonError("APKのアップロードを確認できません。アップロードが終わってから、もう一度お試しください", 409);
    if (head.size > DEVELOPER_LIMITS.maxApkBytes) {
      await deleteObject(release.apk_key).catch(() => {});
      const reason = `APKが大きすぎます(上限 ${DEVELOPER_LIMITS.maxApkBytes / 1024 / 1024}MB)`;
      await svc.from("app_releases").update({ status: "rejected", scan_result: { error: reason } }).eq("id", id);
      return jsonError(reason, 413);
    }

    // 検査・公開に使うのは、複製したほう。開発者が持っている署名付きURLは、削除するアップロード先にしか書き込めない
    const finalKey = apkKey(release.app_id, id);
    if (!(await copyObject(release.apk_key, finalKey))) return jsonError("APKのアップロードを確認できません", 409);
    const { error } = await svc.from("app_releases").update({ apk_key: finalKey, apk_size: head.size }).eq("id", id);
    if (error) throw error;
    await deleteObject(release.apk_key).catch((e) => console.warn("アップロード先の削除に失敗しました:", e instanceof Error ? e.message : e));

    const result = await dispatchScan(id, finalKey);
    if ("error" in result) return jsonError(result.error, result.status);
    return jsonOk(result);
  } catch (e) {
    return internalError(e);
  }
}
