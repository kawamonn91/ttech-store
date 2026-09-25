import { env } from "./env";
import { presignDownload } from "./r2";

export type DispatchResult = { dispatched: true } | { dispatched: false; note: string } | { dispatched: false; error: string; status: number };

/**
 * APK検査(GitHub Actions の scan-release ワークフロー)を起動する。
 * GitHub が未設定の環境では起動せず、手動で workers/scan/inspect.mjs を実行するための情報を返す。
 * 検査結果は、ワークフローが /api/internal/releases/:id/scan へ署名つきで返す。
 */
export async function dispatchScan(releaseId: string, apkKey: string, fetchImpl: typeof fetch = fetch): Promise<DispatchResult> {
  const token = env.githubDispatchToken();
  const repo = env.githubRepo();
  if (!token || !repo) {
    return { dispatched: false, note: "GITHUB_DISPATCH_TOKEN / GITHUB_REPO が未設定のため検査は自動起動されません" };
  }

  const res = await fetchImpl(`https://api.github.com/repos/${repo}/dispatches`, {
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
        releaseId,
        downloadUrl: await presignDownload(apkKey),
        callbackUrl: `${env.siteUrl()}/api/internal/releases/${releaseId}/scan`,
      },
    }),
  });
  if (!res.ok) return { dispatched: false, error: `検査の起動に失敗しました (GitHub ${res.status})`, status: 502 };
  return { dispatched: true };
}
