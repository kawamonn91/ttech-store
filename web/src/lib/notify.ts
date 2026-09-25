import type { SupabaseClient } from "@supabase/supabase-js";
import type { MailMessage } from "./mail";

/** DB(pg_net)が通知してくる出来事の種類。DB側の private.trg_notify_* と揃えること */
export type NotifyKind = "diary_report" | "review_report" | "developer_application";
export const NOTIFY_KINDS: readonly NotifyKind[] = ["diary_report", "review_report", "developer_application"];

const DIARY_REASON: Record<string, string> = {
  spam: "スパム・宣伝",
  harassment: "嫌がらせ・暴言",
  inappropriate: "不適切な内容",
  privacy: "プライバシーの侵害",
  other: "その他",
};

const MAX_TEXT = 500;

/** 利用者が書いた文字列をメールに載せるための整形(長さを切り、制御文字を除く) */
export function clip(value: unknown, max = MAX_TEXT): string {
  const text = typeof value === "string" ? value : "";
  const cleaned = text.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "");
  return cleaned.length > max ? `${cleaned.slice(0, max)}…` : cleaned;
}

export interface DiaryReportInfo {
  id: string;
  reason: string;
  detail: string | null;
  entryBody: string | null;
  reporterName: string;
  reportedName: string;
  createdAt: string;
}

export interface ReviewReportInfo {
  id: number | string;
  reason: string;
  reviewBody: string;
  rating: number | null;
  appName: string;
  authorName: string;
  reporterName: string;
  createdAt: string;
}

export interface DeveloperApplicationInfo {
  userId: string;
  name: string;
  contactEmail: string;
  website: string | null;
  createdAt: string;
}

const FOOTER = "\n\n(このメールは T-tech Store から自動で送られています。対応は管理アプリで行えます)";

export function diaryReportMail(r: DiaryReportInfo): MailMessage {
  const reason = DIARY_REASON[r.reason] ?? r.reason;
  return {
    subject: `[ひとこと日記] 投稿が報告されました(${reason})`,
    text:
      `ひとこと日記の投稿が報告されました。\n\n` +
      `理由: ${reason}\n` +
      `詳細: ${clip(r.detail) || "(なし)"}\n` +
      `報告した人: ${clip(r.reporterName, 80)}\n` +
      `報告された人: ${clip(r.reportedName, 80)}\n` +
      `報告日時: ${r.createdAt}\n\n` +
      `--- 報告された投稿(報告時点の控え) ---\n${clip(r.entryBody) || "(本文なし)"}\n` +
      `---` +
      FOOTER,
  };
}

export function reviewReportMail(r: ReviewReportInfo): MailMessage {
  return {
    subject: `[T-tech Store] レビューが通報されました(${clip(r.appName, 40)})`,
    text:
      `アプリのレビューが通報されました。\n\n` +
      `アプリ: ${clip(r.appName, 80)}\n` +
      `通報の理由: ${clip(r.reason)}\n` +
      `通報した人: ${clip(r.reporterName, 80)}\n` +
      `レビューの投稿者: ${clip(r.authorName, 80)}\n` +
      `評価: ${r.rating ?? "-"}\n` +
      `通報日時: ${r.createdAt}\n\n` +
      `--- レビュー本文 ---\n${clip(r.reviewBody) || "(本文なし)"}\n---` +
      FOOTER,
  };
}

export function developerApplicationMail(d: DeveloperApplicationInfo): MailMessage {
  return {
    subject: `[T-tech Store] 開発者の申請が届きました(${clip(d.name, 40)})`,
    text:
      `開発者登録の申請が届きました。承認するかどうかを確認してください。\n\n` +
      `名前: ${clip(d.name, 80)}\n` +
      `連絡先メール: ${clip(d.contactEmail, 120)}\n` +
      `Webサイト: ${clip(d.website, 200) || "(なし)"}\n` +
      `申請日時: ${d.createdAt}` +
      FOOTER,
  };
}

async function displayNames(supabase: SupabaseClient, ids: (string | null | undefined)[]): Promise<Map<string, string>> {
  const unique = [...new Set(ids.filter((v): v is string => Boolean(v)))];
  if (unique.length === 0) return new Map();
  const { data } = await supabase.from("profiles").select("id, display_name").in("id", unique);
  return new Map((data ?? []).map((p) => [p.id as string, (p.display_name as string) || "(名前なし)"]));
}

/** 通知された出来事の内容をDBから読み、メールの文面にする。対象が無ければ null */
export async function loadNotification(supabase: SupabaseClient, kind: NotifyKind, id: string): Promise<MailMessage | null> {
  if (kind === "diary_report") {
    const { data } = await supabase
      .from("diary_reports")
      .select("id, reason, detail, entry_body, reporter_id, reported_user_id, created_at")
      .eq("id", id)
      .maybeSingle();
    if (!data) return null;
    const names = await displayNames(supabase, [data.reporter_id, data.reported_user_id]);
    return diaryReportMail({
      id: data.id,
      reason: data.reason,
      detail: data.detail,
      entryBody: data.entry_body,
      reporterName: names.get(data.reporter_id) ?? "(不明)",
      reportedName: (data.reported_user_id && names.get(data.reported_user_id)) || "(不明)",
      createdAt: data.created_at,
    });
  }

  if (kind === "review_report") {
    const { data } = await supabase
      .from("review_reports")
      .select("id, reason, reporter_id, created_at, review:reviews(body, rating, user_id, app:apps(name))")
      .eq("id", id)
      .maybeSingle();
    if (!data) return null;
    const review = Array.isArray(data.review) ? data.review[0] : data.review;
    const app = review ? (Array.isArray(review.app) ? review.app[0] : review.app) : null;
    const names = await displayNames(supabase, [data.reporter_id, review?.user_id]);
    return reviewReportMail({
      id: data.id,
      reason: data.reason,
      reviewBody: review?.body ?? "",
      rating: review?.rating ?? null,
      appName: app?.name ?? "(不明なアプリ)",
      authorName: (review?.user_id && names.get(review.user_id)) || "(不明)",
      reporterName: names.get(data.reporter_id) ?? "(不明)",
      createdAt: data.created_at,
    });
  }

  const { data } = await supabase
    .from("developers")
    .select("user_id, name, contact_email, website, created_at")
    .eq("user_id", id)
    .maybeSingle();
  if (!data) return null;
  return developerApplicationMail({
    userId: data.user_id,
    name: data.name,
    contactEmail: data.contact_email,
    website: data.website,
    createdAt: data.created_at,
  });
}

// ---------------------------------------------------------------- APKの自動審査の結果

export interface ReleaseReviewInfo {
  appId: string;
  appName: string;
  packageName: string;
  versionName: string | null;
  versionCode: number | null;
  developerName: string;
  /** 自動審査の判定。運営に知らせる必要があるのは要確認(needs_review)と自動公開(auto_approve) */
  decision: "auto_approve" | "needs_review" | "reject";
  /** 一行ずつの理由(policy.ts の describeFindings) */
  reasons: string[];
  siteUrl: string;
}

const versionLabel = (r: ReleaseReviewInfo) => `${clip(r.versionName ?? "?", 40)} (versionCode ${r.versionCode ?? "?"})`;

/** 運営宛: 自動審査で疑いが見つかったので、内容を確認して承認するかを決めてほしい */
export function releaseNeedsReviewMail(r: ReleaseReviewInfo): MailMessage {
  return {
    subject: `[T-tech Store] 承認待ちのアプリがあります(${clip(r.appName, 40)})`,
    text:
      `開発者がアップロードしたアプリに、自動審査で確認が必要な点が見つかりました。公開はまだされていません。

` +
      `アプリ: ${clip(r.appName, 80)}(${clip(r.packageName, 120)})
` +
      `バージョン: ${versionLabel(r)}
` +
      `開発者: ${clip(r.developerName, 80)}

` +
      `--- 確認が必要な点 ---
${r.reasons.map((x) => clip(x, 300)).join("\n") || "(理由なし)"}
---

` +
      `内容を見て、公開・却下を決めてください:
${r.siteUrl}/admin/apps/${r.appId}` +
      FOOTER,
  };
}

/** 運営宛: 自動審査を通ったので、運営の承認なしに公開した(事後の確認用) */
export function releaseAutoPublishedMail(r: ReleaseReviewInfo): MailMessage {
  return {
    subject: `[T-tech Store] アプリが自動審査を通って公開されました(${clip(r.appName, 40)})`,
    text:
      `開発者のアプリが、自動審査(通信機能・端末データの破壊の可能性がないことの確認)を通り、自動で公開されました。

` +
      `アプリ: ${clip(r.appName, 80)}(${clip(r.packageName, 120)})
` +
      `バージョン: ${versionLabel(r)}
` +
      `開発者: ${clip(r.developerName, 80)}

` +
      `問題があれば、管理コンソールから公開を停止できます:
${r.siteUrl}/admin/apps/${r.appId}` +
      FOOTER,
  };
}

/** 開発者宛: 審査の結果のお知らせ */
export function developerReleaseMail(r: ReleaseReviewInfo): MailMessage {
  const label = `${clip(r.appName, 40)} ${versionLabel(r)}`;
  const portal = `${r.siteUrl}/developer/apps/${r.appId}`;
  if (r.decision === "auto_approve") {
    return {
      subject: `[T-tech Store] ${label} を公開しました`,
      text: `アップロードしていただいた ${label} が自動審査を通り、公開されました。

${portal}

(このメールは T-tech Store から自動で送られています)`,
    };
  }
  if (r.decision === "needs_review") {
    return {
      subject: `[T-tech Store] ${label} は運営が確認します`,
      text:
        `アップロードしていただいた ${label} に、自動審査で確認が必要な点が見つかりました。運営が内容を確認するまで、公開されません。

` +
        `--- 確認が必要な点 ---
${r.reasons.map((x) => clip(x, 300)).join("\n")}
---

` +
        `これらの要素が不要なら、外してビルドし直したAPKをアップロードすると、自動で公開されます。
${portal}

(このメールは T-tech Store から自動で送られています)`,
    };
  }
  return {
    subject: `[T-tech Store] ${label} を公開できませんでした`,
    text:
      `アップロードしていただいた ${label} は、次の理由で公開できませんでした。

${r.reasons.map((x) => clip(x, 300)).join("\n")}

` +
      `直したAPKを、同じ versionCode でもう一度アップロードできます。
${portal}

(このメールは T-tech Store から自動で送られています)`,
  };
}
