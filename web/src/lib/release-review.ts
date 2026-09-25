import type { SupabaseClient } from "@supabase/supabase-js";
import { env } from "./env";
import { sendAdminMail, sendMailTo, type MailMessage } from "./mail";
import { developerReleaseMail, releaseAutoPublishedMail, releaseNeedsReviewMail, type ReleaseReviewInfo } from "./notify";
import { describeFindings } from "./policy";
import { decideRelease, type ReleaseDecision, type ReviewMode, type ScanResult } from "./scan";

export type ReviewResult =
  | { kind: "not_found" }
  /** すでに公開・承認済みなど、検査結果で書き換えてはいけない状態 */
  | { kind: "conflict" }
  | { kind: "ok"; status: ReleaseDecision["status"]; reason: string | null; autoApproved: boolean; decision: string | null };

interface DeveloperRow {
  name: string;
  contact_email: string;
  review_mode: ReviewMode | null;
  status: string;
}

interface AppRow {
  id: string;
  name: string;
  status: string;
  package_name: string;
  signing_cert_sha256: string | null;
  developer: DeveloperRow | DeveloperRow[] | null;
}

const first = <T>(v: T | T[] | null | undefined): T | null => (Array.isArray(v) ? (v[0] ?? null) : (v ?? null));

/** 通知は本来の処理(審査結果の反映)を止めない。送れなかったら記録だけ残す */
async function bestEffort(label: string, send: () => Promise<void>): Promise<void> {
  try {
    await send();
  } catch (e) {
    console.warn(`${label}の通知を送れませんでした:`, e instanceof Error ? e.message : e);
  }
}

/**
 * 検査ワークフローから届いた結果を反映する。
 *   - 開発者の審査方式(review_mode)が auto で、APKの自動審査を通れば、そのまま公開する(自動承認)
 *   - 疑いがあれば「承認待ち」にして、運営に理由つきでメールする
 *   - 明らかに不正なものは却下する
 * 運営自身の開発者(manual)・停止された開発者/アプリは、常に運営の承認待ちにする。
 */
export async function reviewScannedRelease(supabase: SupabaseClient, releaseId: string, result: ScanResult): Promise<ReviewResult> {
  const { data: release } = await supabase
    .from("app_releases")
    .select("id, status, app_id, app:apps(id, name, status, package_name, signing_cert_sha256, developer:developers(name, contact_email, review_mode, status))")
    .eq("id", releaseId)
    .maybeSingle();
  if (!release) return { kind: "not_found" };
  // 公開済み・承認済みのリリースは検査結果で書き換えさせない
  if (!["uploaded", "scanned", "rejected"].includes(release.status)) return { kind: "conflict" };

  const app = first<AppRow>(release.app as AppRow | AppRow[] | null);
  if (!app) return { kind: "not_found" };
  const developer = first<DeveloperRow>(app.developer);

  // 自動で公開してよいのは、審査方式が auto の、承認済みの開発者の、停止されていないアプリだけ
  const mode: ReviewMode = developer?.review_mode === "auto" && developer.status === "approved" && app.status !== "suspended" ? "auto" : "manual";

  const { data: latest } = await supabase
    .from("app_releases")
    .select("version_code")
    .eq("app_id", release.app_id)
    .in("status", ["published", "approved"])
    .order("version_code", { ascending: false })
    .limit(1);
  const latestCode = Array.isArray(latest) ? ((latest[0] as { version_code: number | null } | undefined)?.version_code ?? null) : null;

  let decision = decideRelease(result, app, mode, { latestPublishedVersionCode: latestCode });
  let { error } = await supabase.from("app_releases").update({ ...decision.columns, status: decision.status }).eq("id", releaseId);
  if (error) {
    // 同じ versionCode のリリースがすでにある(unique 制約)。開発者に分かる理由で却下する
    const code = (error as { code?: string }).code;
    if (code === "23505" || /duplicate|unique/i.test(error.message)) {
      const reason = `同じバージョンコード(${result.versionCode})のリリースがすでにあります。versionCode を上げてビルドし直してください`;
      decision = { status: "rejected", reason, columns: { scan_result: { ...result, error: reason } }, verdict: decision.verdict, autoApproved: false };
      ({ error } = await supabase.from("app_releases").update({ ...decision.columns, status: "rejected" }).eq("id", releaseId));
    }
    if (error) throw error;
  }

  // 公開したリリースのアプリは、まだ非公開なら合わせて公開する(初めてのリリースを自動公開したとき)
  if (decision.status === "published" && app.status !== "published") {
    const { error: appError } = await supabase.from("apps").update({ status: "published" }).eq("id", release.app_id);
    if (appError) throw appError;
  }

  await notifyResult({ app, developer, result, decision, mode });

  return {
    kind: "ok",
    status: decision.status,
    reason: decision.reason ?? null,
    autoApproved: decision.autoApproved,
    decision: decision.verdict?.decision ?? null,
  };
}

async function notifyResult(input: { app: AppRow; developer: DeveloperRow | null; result: ScanResult; decision: ReleaseDecision; mode: ReviewMode }) {
  const { app, developer, result, decision, mode } = input;
  // 運営自身のアプリ(manual)は、運営がアップロードした本人なので、通知しない
  if (mode !== "auto") return;

  const info: ReleaseReviewInfo = {
    appId: app.id,
    appName: app.name,
    packageName: app.package_name,
    versionName: result.versionName ?? null,
    versionCode: result.versionCode ?? null,
    developerName: developer?.name ?? "(不明)",
    decision: decision.status === "published" ? "auto_approve" : decision.status === "rejected" ? "reject" : "needs_review",
    reasons: decision.verdict ? describeFindings(decision.verdict) : decision.reason ? [decision.reason] : [],
    siteUrl: env.siteUrl(),
  };

  // 運営へ: 自動で公開した(事後の確認用)/ 疑いがあって承認待ち。却下は、開発者に伝えれば足りる
  const admin: MailMessage | null =
    decision.status === "published" ? releaseAutoPublishedMail(info) : decision.status === "scanned" ? releaseNeedsReviewMail(info) : null;
  if (admin) await bestEffort("運営へ", () => sendAdminMail(admin));

  // 開発者へ。差出人のドメインが未認証のうちは、自分以外のアドレスには送れないことがあるので、失敗しても構わない
  if (developer?.contact_email) {
    const message = developerReleaseMail(info);
    await bestEffort("開発者へ", () => sendMailTo(developer.contact_email, message));
  }
}
