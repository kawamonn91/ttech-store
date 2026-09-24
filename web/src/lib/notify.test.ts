import { describe, expect, it } from "vitest";
import type { SupabaseClient } from "@supabase/supabase-js";
import { clip, developerApplicationMail, diaryReportMail, loadNotification, reviewReportMail } from "./notify";

describe("clip", () => {
  it("長い文字列は切って … を付ける", () => {
    expect(clip("あ".repeat(600), 500)).toHaveLength(501);
    expect(clip("あ".repeat(600), 500).endsWith("…")).toBe(true);
  });
  it("制御文字を除き、改行は残す", () => {
    expect(clip("a\u0000b\u0007c\nd")).toBe("abc\nd");
  });
  it("文字列でなければ空", () => {
    expect(clip(null)).toBe("");
    expect(clip(123)).toBe("");
  });
});

describe("メールの文面", () => {
  it("日記の報告: 理由を日本語にし、控えの本文を含める。件名には利用者の入力を入れない", () => {
    const mail = diaryReportMail({
      id: "r1",
      reason: "harassment",
      detail: "しつこい",
      entryBody: "ひどい投稿\n\n<script>alert(1)</script>",
      reporterName: "花子",
      reportedName: "太郎",
      createdAt: "2026-09-25T00:00:00Z",
    });
    expect(mail.subject).toBe("[ひとこと日記] 投稿が報告されました(嫌がらせ・暴言)");
    expect(mail.text).toContain("しつこい");
    expect(mail.text).toContain("ひどい投稿");
    expect(mail.text).toContain("花子");
    expect(mail.text).toContain("太郎");
    // テキストメールなので、HTMLとして解釈されることはない
    expect(mail.text).toContain("<script>alert(1)</script>");
  });

  it("日記の報告: 詳細・本文が無くても崩れない", () => {
    const mail = diaryReportMail({
      id: "r1", reason: "unknown-reason", detail: null, entryBody: null, reporterName: "a", reportedName: "b", createdAt: "x",
    });
    expect(mail.subject).toContain("unknown-reason");
    expect(mail.text).toContain("(なし)");
    expect(mail.text).toContain("(本文なし)");
  });

  it("レビュー通報: アプリ名・評価・本文が入る", () => {
    const mail = reviewReportMail({
      id: 1, reason: "誹謗中傷", reviewBody: "最悪", rating: 1, appName: "家計簿", authorName: "A", reporterName: "B", createdAt: "x",
    });
    expect(mail.subject).toContain("家計簿");
    expect(mail.text).toContain("最悪");
    expect(mail.text).toContain("評価: 1");
  });

  it("開発者申請: 連絡先が入る", () => {
    const mail = developerApplicationMail({ userId: "u", name: "山田", contactEmail: "y@example.com", website: null, createdAt: "x" });
    expect(mail.subject).toContain("山田");
    expect(mail.text).toContain("y@example.com");
    expect(mail.text).toContain("(なし)");
  });

  it("件名が極端に長くならない(アプリ名・開発者名は40文字まで)", () => {
    const long = "あ".repeat(200);
    const review = reviewReportMail({ id: 1, reason: "r", reviewBody: "", rating: 1, appName: long, authorName: "a", reporterName: "b", createdAt: "x" });
    const dev = developerApplicationMail({ userId: "u", name: long, contactEmail: "e", website: null, createdAt: "x" });
    expect(review.subject.length).toBeLessThan(80);
    expect(dev.subject.length).toBeLessThan(80);
  });
});

/** テーブルごとに固定の行を返す最小のSupabaseクライアント */
function fakeSupabase(tables: Record<string, unknown>): SupabaseClient {
  const builder = (rows: unknown) => {
    const b: Record<string, unknown> = {};
    b.select = () => b;
    b.eq = () => b;
    b.in = async () => ({ data: rows });
    b.maybeSingle = async () => ({ data: Array.isArray(rows) ? (rows[0] ?? null) : rows });
    return b;
  };
  return { from: (t: string) => builder(tables[t]) } as unknown as SupabaseClient;
}

describe("loadNotification", () => {
  it("日記の報告: 報告者・対象者の表示名を引いてメールにする", async () => {
    const supabase = fakeSupabase({
      diary_reports: { id: "r1", reason: "spam", detail: null, entry_body: "宣伝です", reporter_id: "u1", reported_user_id: "u2", created_at: "t" },
      profiles: [{ id: "u1", display_name: "花子" }, { id: "u2", display_name: "太郎" }],
    });
    const mail = await loadNotification(supabase, "diary_report", "r1");
    expect(mail?.text).toContain("花子");
    expect(mail?.text).toContain("太郎");
    expect(mail?.text).toContain("宣伝です");
  });

  it("報告された人が削除済み(null)でも作れる", async () => {
    const supabase = fakeSupabase({
      diary_reports: { id: "r1", reason: "spam", detail: null, entry_body: "x", reporter_id: "u1", reported_user_id: null, created_at: "t" },
      profiles: [{ id: "u1", display_name: "花子" }],
    });
    expect((await loadNotification(supabase, "diary_report", "r1"))?.text).toContain("報告された人: (不明)");
  });

  it("対象が無ければ null", async () => {
    expect(await loadNotification(fakeSupabase({ diary_reports: null }), "diary_report", "nope")).toBeNull();
    expect(await loadNotification(fakeSupabase({ review_reports: null }), "review_report", "1")).toBeNull();
    expect(await loadNotification(fakeSupabase({ developers: null }), "developer_application", "u")).toBeNull();
  });

  it("レビュー通報: 入れ子(配列/オブジェクト)のどちらでも読める", async () => {
    const supabase = fakeSupabase({
      review_reports: { id: 5, reason: "不適切", reporter_id: "u1", created_at: "t", review: [{ body: "ひどい", rating: 2, user_id: "u2", app: [{ name: "メモ帳" }] }] },
      profiles: [{ id: "u1", display_name: "B" }, { id: "u2", display_name: "A" }],
    });
    const mail = await loadNotification(supabase, "review_report", "5");
    expect(mail?.subject).toContain("メモ帳");
    expect(mail?.text).toContain("ひどい");
    expect(mail?.text).toContain("レビューの投稿者: A");
  });

  it("開発者申請", async () => {
    const supabase = fakeSupabase({
      developers: { user_id: "u", name: "山田", contact_email: "y@example.com", website: "https://y.example.com", created_at: "t" },
    });
    expect((await loadNotification(supabase, "developer_application", "u"))?.text).toContain("https://y.example.com");
  });
});
