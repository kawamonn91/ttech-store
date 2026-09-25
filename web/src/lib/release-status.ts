/** リリースの状態を、開発者・運営の画面に出す言葉と色に変える */

export type Tone = "ok" | "wait" | "bad" | "muted";

export interface ReleaseStateInput {
  status: string;
  auto_approved?: boolean | null;
  policy_verdict?: string | null;
}

export function releaseState(r: ReleaseStateInput): { label: string; tone: Tone; hint?: string } {
  switch (r.status) {
    case "uploaded":
      return { label: "検査中", tone: "wait", hint: "APKを自動で検査しています。数分かかります" };
    case "scanned":
      return r.policy_verdict === "needs_review"
        ? { label: "運営が確認中", tone: "wait", hint: "自動審査で確認が必要な点が見つかったため、運営が内容を確認するまで公開されません" }
        : { label: "承認待ち", tone: "wait", hint: "運営が承認すると公開されます" };
    case "approved":
      return { label: "承認済み(非公開)", tone: "muted" };
    case "published":
      return { label: r.auto_approved ? "公開中(自動審査を通過)" : "公開中", tone: "ok" };
    case "rejected":
      return { label: "公開できませんでした", tone: "bad" };
    default:
      return { label: r.status, tone: "muted" };
  }
}

export const APP_STATUS_LABEL: Record<string, { label: string; tone: Tone }> = {
  draft: { label: "非公開", tone: "muted" },
  pending: { label: "審査中", tone: "wait" },
  published: { label: "公開中", tone: "ok" },
  suspended: { label: "停止中", tone: "bad" },
};

export const TONE_CLASS: Record<Tone, string> = {
  ok: "bg-green-100 text-green-800",
  wait: "bg-amber-100 text-amber-800",
  bad: "bg-red-100 text-red-800",
  muted: "bg-black/5 text-muted",
};
