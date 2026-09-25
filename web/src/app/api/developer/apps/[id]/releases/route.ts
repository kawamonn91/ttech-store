import { CreateReleaseBody, DEVELOPER_LIMITS, firstIssue, loadOwnApp, requireDeveloper } from "@/lib/developer";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { presignUpload, UPLOAD_URL_TTL_SECONDS, uploadKey } from "@/lib/r2";

/**
 * 新しいバージョンのアップロードを始める。APK を R2 へ直接アップロードするための署名付きURLを返す。
 * 1人あたり、24時間に作れるリリース数に上限がある(検査・配信の負荷と、悪用の防止のため)。
 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireDeveloper();
  if ("response" in auth) return auth.response;
  const { user, svc } = auth;
  const { id: appId } = await params;

  const body = CreateReleaseBody.safeParse(await request.json().catch(() => ({})));
  if (!body.success) return jsonError(firstIssue(body.error), 400);

  try {
    const app = await loadOwnApp(svc, appId, user.id);
    if (!app) return jsonError("アプリが見つかりません", 404);
    if (app.status === "suspended") return jsonError("このアプリは停止されています。運営にお問い合わせください", 403);

    // 直近24時間に、自分の全アプリで作ったリリースの数
    const { data: mine } = await svc.from("apps").select("id").eq("developer_id", user.id);
    const ids = ((mine as { id: string }[] | null) ?? []).map((a) => a.id);
    const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
    const { count } = await svc.from("app_releases").select("id", { count: "exact", head: true }).in("app_id", ids).gte("created_at", since);
    if ((count ?? 0) >= DEVELOPER_LIMITS.maxReleasesPerDay) {
      return jsonError(`アップロードできるのは、24時間に${DEVELOPER_LIMITS.maxReleasesPerDay}回までです。しばらくしてからお試しください`, 429, { code: "rate_limited" });
    }

    const releaseId = crypto.randomUUID();
    const key = uploadKey(appId, releaseId);
    const { error } = await svc.from("app_releases").insert({
      id: releaseId,
      app_id: appId,
      apk_key: key,
      release_notes: body.data.releaseNotes,
      status: "uploaded",
    });
    if (error) throw error;

    return jsonOk({
      releaseId,
      uploadUrl: await presignUpload(key),
      maxBytes: DEVELOPER_LIMITS.maxApkBytes,
      expiresInSeconds: UPLOAD_URL_TTL_SECONDS,
    });
  } catch (e) {
    return internalError(e);
  }
}
