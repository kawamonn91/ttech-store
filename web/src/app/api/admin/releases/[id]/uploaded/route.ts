import { requireAdmin } from "@/lib/admin";
import { env } from "@/lib/env";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { headObject, presignDownload } from "@/lib/r2";
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

    const token = env.githubDispatchToken();
    const repo = env.githubRepo();
    if (!token || !repo) {
      return jsonOk({ dispatched: false, note: "GITHUB_DISPATCH_TOKEN / GITHUB_REPO が未設定のため検査は自動起動されません" });
    }

    const res = await fetch(`https://api.github.com/repos/${repo}/dispatches`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        event_type: "scan-release",
        client_payload: {
          releaseId: id,
          downloadUrl: await presignDownload(release.apk_key),
          callbackUrl: `${env.siteUrl()}/api/internal/releases/${id}/scan`,
        },
      }),
    });
    if (!res.ok) return jsonError(`検査の起動に失敗しました (GitHub ${res.status})`, 502);
    return jsonOk({ dispatched: true });
  } catch (e) {
    return internalError(e);
  }
}
