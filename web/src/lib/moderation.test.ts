import { describe, expect, it } from "vitest";
import { banUser, deleteDiaryEntry, ModerationError, resolveReport, setAppStatus, setDeveloperStatus, unbanUser } from "./moderation";
import { fakeSupabase } from "./testing/fake-supabase";

const admin = { id: "admin-1", email: "admin@example.com" };

async function rejection(promise: Promise<unknown>): Promise<ModerationError> {
  try {
    await promise;
  } catch (e) {
    expect(e).toBeInstanceOf(ModerationError);
    return e as ModerationError;
  }
  throw new Error("エラーになるはずが成功した");
}

describe("banUser", () => {
  it("プロフィールに BAN を記録し、Auth も BAN し、監査ログを残す", async () => {
    const fake = fakeSupabase({ results: { "profiles.select": { data: { id: "u1", role: "user", banned_at: null } } } });
    await banUser(fake.client, admin, "u1", "迷惑行為");

    const update = fake.callsTo("profiles", "update")[0];
    expect((update.payload as Record<string, unknown>).ban_reason).toBe("迷惑行為");
    expect((update.payload as Record<string, unknown>).banned_at).toEqual(expect.any(String));
    expect(fake.authCalls[0]).toEqual({ fn: "updateUserById", args: ["u1", { ban_duration: "876000h" }] });
    const log = fake.callsTo("admin_audit_log", "insert")[0].payload as Record<string, unknown>;
    expect(log).toMatchObject({ admin_id: "admin-1", action: "user.ban", target_type: "user", target_id: "u1" });
  });

  it("自分自身はBANできない(何も書かない)", async () => {
    const fake = fakeSupabase();
    expect((await rejection(banUser(fake.client, admin, "admin-1", "x"))).status).toBe(409);
    expect(fake.calls).toHaveLength(0);
    expect(fake.authCalls).toHaveLength(0);
  });

  it("ほかの管理者もBANできない", async () => {
    const fake = fakeSupabase({ results: { "profiles.select": { data: { id: "admin-2", role: "admin" } } } });
    const error = await rejection(banUser(fake.client, admin, "admin-2", "x"));
    expect(error.status).toBe(409);
    expect(fake.callsTo("profiles", "update")).toHaveLength(0);
    expect(fake.authCalls).toHaveLength(0);
  });

  it("存在しないユーザーは 404", async () => {
    const fake = fakeSupabase({ results: { "profiles.select": { data: null } } });
    expect((await rejection(banUser(fake.client, admin, "nobody", ""))).status).toBe(404);
  });

  it("Auth のBANに失敗したら、その旨を返す(RLS側の制限は既に効いている)", async () => {
    const fake = fakeSupabase({
      results: { "profiles.select": { data: { id: "u1", role: "user" } } },
      authAdmin: { updateUserById: () => ({ data: null, error: { message: "auth down" } }) },
    });
    const error = await rejection(banUser(fake.client, admin, "u1", "x"));
    expect(error.status).toBe(502);
    expect(error.message).toContain("データへのアクセスは止めました");
    expect(fake.callsTo("admin_audit_log", "insert")).toHaveLength(0);
  });

  it("プロフィールの更新に失敗したら Auth には触れない", async () => {
    const fake = fakeSupabase({
      results: {
        "profiles.select": { data: { id: "u1", role: "user" } },
        "profiles.update": { error: { message: "db down" } },
      },
    });
    expect((await rejection(banUser(fake.client, admin, "u1", "x"))).status).toBe(500);
    expect(fake.authCalls).toHaveLength(0);
  });
});

describe("unbanUser", () => {
  it("Auth のBANを外し、プロフィールを元に戻し、監査ログを残す", async () => {
    const fake = fakeSupabase({ results: { "profiles.select": { data: { id: "u1", banned_at: "t" } } } });
    await unbanUser(fake.client, admin, "u1");
    expect(fake.authCalls[0]).toEqual({ fn: "updateUserById", args: ["u1", { ban_duration: "none" }] });
    expect(fake.callsTo("profiles", "update")[0].payload).toEqual({ banned_at: null, ban_reason: null });
    expect((fake.callsTo("admin_audit_log", "insert")[0].payload as Record<string, unknown>).action).toBe("user.unban");
  });

  it("存在しないユーザーは 404", async () => {
    const fake = fakeSupabase({ results: { "profiles.select": { data: null } } });
    expect((await rejection(unbanUser(fake.client, admin, "nobody"))).status).toBe(404);
  });
});

describe("deleteDiaryEntry", () => {
  it("写真を消してから投稿を消し、本文の一部を監査ログに残す", async () => {
    const fake = fakeSupabase({
      results: { "diary_entries.select": { data: { id: "e1", user_id: "u1", body: "あ".repeat(300), photo_path: "diary/u1/e1.jpg" } } },
    });
    await deleteDiaryEntry(fake.client, admin, "e1");
    expect(fake.storageCalls).toEqual([{ bucket: "diary-media", op: "remove", paths: ["diary/u1/e1.jpg"] }]);
    expect(fake.callsTo("diary_entries", "delete")).toHaveLength(1);
    const detail = (fake.callsTo("admin_audit_log", "insert")[0].payload as { detail: { body: string; had_photo: boolean } }).detail;
    expect(detail.body).toHaveLength(200);
    expect(detail.had_photo).toBe(true);
  });

  it("写真が無い投稿はストレージに触れない", async () => {
    const fake = fakeSupabase({ results: { "diary_entries.select": { data: { id: "e1", user_id: "u1", body: "x", photo_path: null } } } });
    await deleteDiaryEntry(fake.client, admin, "e1");
    expect(fake.storageCalls).toHaveLength(0);
    expect(fake.callsTo("diary_entries", "delete")).toHaveLength(1);
  });

  it("写真の削除に失敗したら、投稿は消さない(写真だけ残る事故を防ぐ)", async () => {
    const fake = fakeSupabase({
      results: { "diary_entries.select": { data: { id: "e1", user_id: "u1", body: "x", photo_path: "p" } } },
      storageRemove: () => ({ error: { message: "storage down" } }),
    });
    expect((await rejection(deleteDiaryEntry(fake.client, admin, "e1"))).status).toBe(502);
    expect(fake.callsTo("diary_entries", "delete")).toHaveLength(0);
  });

  it("存在しない投稿は 404", async () => {
    const fake = fakeSupabase({ results: { "diary_entries.select": { data: null } } });
    expect((await rejection(deleteDiaryEntry(fake.client, admin, "nope"))).status).toBe(404);
  });
});

describe("resolveReport: ひとこと日記", () => {
  const report = { id: "r1", entry_id: "e1", reported_user_id: "u1", status: "open" };

  it("dismiss: 投稿にもユーザーにも触れず、dismissed にする", async () => {
    const fake = fakeSupabase({ results: { "diary_reports.select": { data: report } } });
    await resolveReport(fake.client, admin, "diary", "r1", "dismiss");
    expect(fake.callsTo("diary_reports", "update")[0].payload).toEqual({ status: "dismissed" });
    expect(fake.callsTo("diary_entries", "delete")).toHaveLength(0);
    expect(fake.authCalls).toHaveLength(0);
  });

  it("delete_content: 投稿を削除し、actioned にする", async () => {
    const fake = fakeSupabase({
      results: {
        "diary_reports.select": { data: report },
        "diary_entries.select": { data: { id: "e1", user_id: "u1", body: "x", photo_path: null } },
      },
    });
    await resolveReport(fake.client, admin, "diary", "r1", "delete_content");
    expect(fake.callsTo("diary_entries", "delete")).toHaveLength(1);
    expect(fake.callsTo("diary_reports", "update")[0].payload).toEqual({ status: "actioned" });
  });

  it("delete_content: 投稿が既に無くても、報告は処理済みにできる", async () => {
    const fake = fakeSupabase({ results: { "diary_reports.select": { data: report }, "diary_entries.select": { data: null } } });
    await resolveReport(fake.client, admin, "diary", "r1", "delete_content");
    expect(fake.callsTo("diary_entries", "delete")).toHaveLength(0);
    expect(fake.callsTo("diary_reports", "update")[0].payload).toEqual({ status: "actioned" });
  });

  it("ban_author: 報告された人をBANする", async () => {
    const fake = fakeSupabase({
      results: { "diary_reports.select": { data: report }, "profiles.select": { data: { id: "u1", role: "user" } } },
    });
    await resolveReport(fake.client, admin, "diary", "r1", "ban_author", "嫌がらせ");
    expect(fake.authCalls[0].args[0]).toBe("u1");
    expect(fake.callsTo("diary_reports", "update")[0].payload).toEqual({ status: "actioned" });
  });

  it("ban_author: 投稿者のアカウントが削除済みなら 409(報告は閉じない)", async () => {
    const fake = fakeSupabase({ results: { "diary_reports.select": { data: { ...report, reported_user_id: null } } } });
    expect((await rejection(resolveReport(fake.client, admin, "diary", "r1", "ban_author"))).status).toBe(409);
    expect(fake.callsTo("diary_reports", "update")).toHaveLength(0);
  });

  it("存在しない報告は 404", async () => {
    const fake = fakeSupabase({ results: { "diary_reports.select": { data: null } } });
    expect((await rejection(resolveReport(fake.client, admin, "diary", "nope", "dismiss"))).status).toBe(404);
  });
});

describe("resolveReport: レビュー", () => {
  it("delete_content: レビューを hidden にし、通報を resolved にする", async () => {
    const fake = fakeSupabase({ results: { "review_reports.select": { data: { id: 5, review_id: "rv1" } } } });
    await resolveReport(fake.client, admin, "review", "5", "delete_content");
    expect(fake.callsTo("reviews", "update")[0].payload).toEqual({ status: "hidden" });
    expect(fake.callsTo("review_reports", "update")[0].payload).toEqual({ resolved: true });
  });

  it("dismiss: レビューには触れず、通報だけ resolved にする", async () => {
    const fake = fakeSupabase({ results: { "review_reports.select": { data: { id: 5, review_id: "rv1" } } } });
    await resolveReport(fake.client, admin, "review", "5", "dismiss");
    expect(fake.callsTo("reviews", "update")).toHaveLength(0);
    expect(fake.callsTo("review_reports", "update")[0].payload).toEqual({ resolved: true });
  });

  it("ban_author: レビューの投稿者をBANする", async () => {
    const fake = fakeSupabase({
      results: {
        "review_reports.select": { data: { id: 5, review_id: "rv1" } },
        "reviews.select": { data: { user_id: "u9" } },
        "profiles.select": { data: { id: "u9", role: "user" } },
      },
    });
    await resolveReport(fake.client, admin, "review", "5", "ban_author");
    expect(fake.authCalls[0].args[0]).toBe("u9");
  });
});

describe("setDeveloperStatus / setAppStatus", () => {
  it("開発者を承認すると verified_at が入る", async () => {
    const fake = fakeSupabase({ results: { "developers.select": { data: { user_id: "d1" } } } });
    await setDeveloperStatus(fake.client, admin, "d1", "approved");
    const patch = fake.callsTo("developers", "update")[0].payload as Record<string, unknown>;
    expect(patch.status).toBe("approved");
    expect(patch.verified_at).toEqual(expect.any(String));
  });

  it("開発者を停止しても verified_at は触らない", async () => {
    const fake = fakeSupabase({ results: { "developers.select": { data: { user_id: "d1" } } } });
    await setDeveloperStatus(fake.client, admin, "d1", "suspended");
    expect(fake.callsTo("developers", "update")[0].payload).toEqual({ status: "suspended" });
  });

  it("公開中のリリースが無いアプリは公開にできない", async () => {
    const fake = fakeSupabase({
      results: { "apps.select": { data: { id: "a1", status: "suspended" } }, "app_releases.select": { count: 0 } },
    });
    expect((await rejection(setAppStatus(fake.client, admin, "a1", "published"))).status).toBe(409);
    expect(fake.callsTo("apps", "update")).toHaveLength(0);
  });

  it("公開中のリリースがあれば公開にできる。停止はいつでもできる", async () => {
    const fake = fakeSupabase({
      results: { "apps.select": { data: { id: "a1", status: "suspended" } }, "app_releases.select": { count: 2 } },
    });
    await setAppStatus(fake.client, admin, "a1", "published");
    expect(fake.callsTo("apps", "update")[0].payload).toEqual({ status: "published" });

    const stop = fakeSupabase({ results: { "apps.select": { data: { id: "a1", status: "published" } } } });
    await setAppStatus(stop.client, admin, "a1", "suspended");
    expect(stop.callsTo("apps", "update")[0].payload).toEqual({ status: "suspended" });
    expect(stop.callsTo("app_releases", "select")).toHaveLength(0);
  });
});
