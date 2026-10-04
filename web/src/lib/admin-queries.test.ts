import { describe, expect, it } from "vitest";
import { getOverview, getUserDetail, listAllApps, listPendingReleases, listPrivateApps, listReports, listUsers } from "./admin-queries";
import { ModerationError } from "./moderation";
import { fakeSupabase } from "./testing/fake-supabase";

describe("listUsers", () => {
  it("RPCの行を管理アプリ用の形にし、total を先頭行から取る", async () => {
    const fake = fakeSupabase({
      rpc: {
        admin_list_users: {
          data: [
            {
              id: "u1", email: "a@example.com", display_name: "アリス", role: "user", provider: "google",
              created_at: "2026-09-01T00:00:00Z", last_sign_in_at: "2026-09-20T00:00:00Z", banned_at: null, ban_reason: null,
              diary_count: "3", review_count: 1, reports_against: 2, total: "57",
            },
          ],
        },
      },
    });
    const result = await listUsers(fake.client, { q: "ali", bannedOnly: true, limit: 10, offset: 20 });
    expect(result.total).toBe(57);
    expect(result.items[0]).toMatchObject({ id: "u1", email: "a@example.com", displayName: "アリス", provider: "google", diaryCount: 3, reportsAgainst: 2, bannedAt: null });
    expect(fake.rpcCalls[0]).toEqual({ fn: "admin_list_users", args: { p_q: "ali", p_banned_only: true, p_limit: 10, p_offset: 20 } });
  });

  it("検索語は PostgREST を壊す文字を除いて渡す。空なら null", async () => {
    const fake = fakeSupabase({ rpc: { admin_list_users: { data: [] } } });
    expect((await listUsers(fake.client, { q: "a,b(c)" })).total).toBe(0);
    expect((fake.rpcCalls[0].args as { p_q: string }).p_q).toBe("a b c");
    await listUsers(fake.client, {});
    expect((fake.rpcCalls[1].args as { p_q: unknown }).p_q).toBeNull();
  });

  it("RPCが失敗したら例外", async () => {
    const fake = fakeSupabase({ rpc: { admin_list_users: { error: { message: "boom" } } } });
    await expect(listUsers(fake.client, {})).rejects.toThrow("boom");
  });
});

describe("listPrivateApps", () => {
  it("管理者専用アプリごとに、最新(version_code が最大)の公開リリースを返す。リリースが無いアプリは出さない", async () => {
    const fake = fakeSupabase({
      results: {
        "apps.select": {
          data: [
            { id: "a1", slug: "admin", name: "管理", package_name: "com.ttech.admin" },
            { id: "a2", slug: "empty", name: "空", package_name: "com.ttech.empty" },
          ],
        },
        "app_releases.select": {
          data: [
            { id: "r3", app_id: "a1", version_name: "1.2.0", version_code: 3, apk_size: 3000 },
            { id: "r2", app_id: "a1", version_name: "1.1.0", version_code: 2, apk_size: 2000 },
          ],
        },
      },
    });
    const items = await listPrivateApps(fake.client);
    expect(items).toEqual([
      { appId: "a1", slug: "admin", name: "管理", packageName: "com.ttech.admin", releaseId: "r3", versionName: "1.2.0", versionCode: 3, apkSize: 3000 },
    ]);
    // admin_only かつ公開中のアプリだけを引く
    const chain = fake.callsTo("apps", "select")[0].chain;
    expect(chain).toContainEqual(["eq", ["admin_only", true]]);
    expect(chain).toContainEqual(["eq", ["status", "published"]]);
  });

  it("管理者専用アプリが無ければ空(リリースは引かない)", async () => {
    const fake = fakeSupabase({ results: { "apps.select": { data: [] } } });
    expect(await listPrivateApps(fake.client)).toEqual([]);
    expect(fake.callsTo("app_releases", "select")).toHaveLength(0);
  });
});

describe("listPendingReleases", () => {
  it("scanned・approved のリリースを、管理アプリ用の形にする", async () => {
    const fake = fakeSupabase({
      results: {
        "app_releases.select": {
          data: [
            {
              id: "r1", version_name: "1.2.0", status: "scanned", policy_verdict: "needs_review",
              policy_findings: [{ code: "net.permission", severity: "review", category: "network", title: "通信につながる権限", detail: "", evidence: [] }],
              app: { id: "a1", slug: "foo", name: "フー" },
            },
          ],
        },
      },
    });
    const items = await listPendingReleases(fake.client);
    expect(items).toEqual([
      {
        id: "r1", versionName: "1.2.0", status: "scanned", policyVerdict: "needs_review",
        policyFindings: [{ code: "net.permission", severity: "review", category: "network", title: "通信につながる権限", detail: "", evidence: [] }],
        app: { id: "a1", slug: "foo", name: "フー" },
      },
    ]);
  });

  it("リリースが無ければ空", async () => {
    const fake = fakeSupabase({ results: { "app_releases.select": { data: [] } } });
    expect(await listPendingReleases(fake.client)).toEqual([]);
  });
});

describe("listAllApps", () => {
  it("公開中リリースのうち最新のバージョン名を付ける", async () => {
    const fake = fakeSupabase({
      results: {
        "apps.select": {
          data: [{ id: "a1", slug: "s", name: "N", package_name: "p.q", status: "published", admin_only: true, download_count: "12", rating_avg: "4.5", rating_count: 3 }],
        },
        "app_releases.select": {
          data: [
            { app_id: "a1", version_name: "1.0", version_code: 1 },
            { app_id: "a1", version_name: "1.1", version_code: 2 },
          ],
        },
      },
    });
    expect(await listAllApps(fake.client)).toEqual([
      { id: "a1", slug: "s", name: "N", packageName: "p.q", status: "published", adminOnly: true, downloadCount: 12, ratingAvg: 4.5, ratingCount: 3, latestVersion: "1.1" },
    ]);
  });
});

describe("listReports", () => {
  it("日記の報告とレビュー通報を新しい順にまとめ、削除済みの投稿は contentId を null にする", async () => {
    const fake = fakeSupabase({
      results: {
        "diary_reports.select": {
          data: [
            { id: "d1", reason: "spam", detail: null, status: "open", entry_id: "e-live", entry_body: "宣伝A", reporter_id: "u1", reported_user_id: "u2", created_at: "2026-09-24T10:00:00Z" },
            { id: "d2", reason: "other", detail: "x", status: "open", entry_id: "e-gone", entry_body: "宣伝B", reporter_id: "u1", reported_user_id: null, created_at: "2026-09-22T10:00:00Z" },
          ],
        },
        "review_reports.select": {
          data: [
            { id: 7, reason: "誹謗中傷", resolved: false, reporter_id: "u1", created_at: "2026-09-23T10:00:00Z", review: { id: "rv1", body: "最悪", rating: 1, user_id: "u3", app: { name: "家計簿" } } },
          ],
        },
        "diary_entries.select": { data: [{ id: "e-live" }] },
        "profiles.select": { data: [{ id: "u1", display_name: "花子" }, { id: "u2", display_name: "太郎" }, { id: "u3", display_name: "次郎" }] },
      },
    });
    const items = await listReports(fake.client, { openOnly: true });
    expect(items.map((i) => `${i.kind}:${i.id}`)).toEqual(["diary:d1", "review:7", "diary:d2"]);
    expect(items[0]).toMatchObject({ reporter: { name: "花子" }, target: { userId: "u2", name: "太郎" }, body: "宣伝A", contentId: "e-live" });
    expect(items[1]).toMatchObject({ target: { name: "次郎" }, contentId: "rv1", status: "open" });
    expect(items[1].contentLabel).toContain("家計簿");
    expect(items[2]).toMatchObject({ contentId: null, target: { userId: null, name: "(削除済み)" } });
  });

  it("未対応のみのときは、status/resolved で絞る。all では絞らない", async () => {
    const open = fakeSupabase();
    await listReports(open.client, { openOnly: true });
    expect(open.callsTo("diary_reports", "select")[0].chain).toContainEqual(["eq", ["status", "open"]]);
    expect(open.callsTo("review_reports", "select")[0].chain).toContainEqual(["eq", ["resolved", false]]);

    const all = fakeSupabase();
    await listReports(all.client, { openOnly: false });
    expect(all.callsTo("diary_reports", "select")[0].chain.some(([m]) => m === "eq")).toBe(false);
  });
});

describe("getUserDetail", () => {
  it("存在しないユーザーは ModerationError(404)", async () => {
    const fake = fakeSupabase({ results: { "profiles.select": { data: null } } });
    await expect(getUserDetail(fake.client, "nope")).rejects.toBeInstanceOf(ModerationError);
  });

  it("プロフィール・Authの情報・活動の件数と最近の内容をまとめる。写真の中身は返さず有無だけ", async () => {
    const fake = fakeSupabase({
      results: {
        "profiles.select": { data: { id: "u1", display_name: "アリス", role: "user", banned_at: null, ban_reason: null, created_at: "2026-09-01T00:00:00Z" } },
        "diary_entries.select": (call) =>
          call.chain.some(([m]) => m === "limit")
            ? { data: [{ id: "e1", body: "こんにちは", visibility: "public", photo_path: "diary/u1/e1.jpg", created_at: "t" }] }
            : { count: 4 },
        "reviews.select": (call) =>
          call.chain.some(([m]) => m === "limit")
            ? { data: [{ id: "rv1", rating: 5, body: "よい", status: "visible", created_at: "t", app: { name: "家計簿" } }] }
            : { count: 1 },
        "diary_reports.select": (call) =>
          call.chain.some(([m]) => m === "limit")
            ? { data: [{ id: "d1", reason: "spam", detail: null, status: "open", entry_body: "宣伝", created_at: "t" }] }
            : { count: 2 },
        "downloads.select": { count: 9 },
        "developers.select": { data: null },
      },
      authAdmin: {
        getUserById: () => ({
          data: { user: { email: "alice@example.com", email_confirmed_at: "t", last_sign_in_at: "2026-09-20T00:00:00Z", app_metadata: { provider: "google" } } },
          error: null,
        }),
      },
    });
    const detail = await getUserDetail(fake.client, "u1");
    expect(detail).toMatchObject({
      id: "u1", email: "alice@example.com", emailConfirmed: true, provider: "google", displayName: "アリス", developer: null,
      counts: { diaryEntries: 4, reviews: 1, reportsAgainst: 2, reportsFiled: 2, downloads: 9 },
    });
    expect(detail.recentEntries).toEqual([{ id: "e1", body: "こんにちは", visibility: "public", hasPhoto: true, createdAt: "t" }]);
    expect(detail.recentReviews[0]).toMatchObject({ appName: "家計簿", rating: 5 });
    expect(detail.reportsAgainst[0]).toMatchObject({ reason: "spam", entryBody: "宣伝" });
  });
});

describe("getOverview", () => {
  it("各件数を集め、管理者専用アプリは公開アプリ数に含めない", async () => {
    const fake = fakeSupabase({ results: { "profiles.select": { count: 10 }, "apps.select": { count: 5 } } });
    const overview = await getOverview(fake.client);
    expect(overview.users.total).toBe(10);
    expect(overview.apps.published).toBe(5);
    const appsQuery = fake.callsTo("apps", "select")[0].chain;
    expect(appsQuery).toContainEqual(["eq", ["admin_only", false]]);
  });
});
