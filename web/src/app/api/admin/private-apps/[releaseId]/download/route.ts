import { adminError, adminOr401, IdParam } from "@/lib/admin-api";
import { DownloadError, issueDownload } from "@/lib/download";
import { jsonError, jsonOk } from "@/lib/http";

/**
 * 管理者専用アプリのダウンロードURL(署名付き・短時間有効)と検証情報を返す。
 * 管理者としてログインしている(Cookie または Bearer)ときだけ。
 */
export async function POST(request: Request, { params }: { params: Promise<{ releaseId: string }> }) {
  const auth = await adminOr401(request);
  if ("response" in auth) return auth.response;
  const { releaseId } = await params;
  if (!IdParam.safeParse(releaseId).success) return jsonError("リリースが見つかりません", 404);
  try {
    // DL数の重複判定用の端末IDは、管理者のIDで代用する(管理者は少人数で、端末は特定しない)
    return jsonOk(await issueDownload(releaseId, `admin-${auth.admin.id}`, auth.admin.id, async () => true));
  } catch (e) {
    if (e instanceof DownloadError) return jsonError(e.message, e.status);
    return adminError(e);
  }
}
