import { beforeEach, describe, expect, it, vi } from "vitest";
import { NextResponse } from "next/server";

/**
 * 管理アプリ向けAPI(/api/admin/*)の共通の性質を、全ルートまとめて確認する。
 *  - 管理者でなければ、DBにも操作関数にも触れずに 401
 *  - 不正な入力は 400/404(操作関数を呼ばない)
 *  - 操作関数の失敗(ModerationError)は、その状態コードと文言で返す
 */

const denied = () => ({ response: NextResponse.json({ error: "管理者としてログインしてください" }, { status: 401 }) });
const admin = { admin: { id: "admin-1", email: "a@example.com" } };
let auth: unknown;

const ops = vi.hoisted(() => ({
  banUser: vi.fn(),
  unbanUser: vi.fn(),
  deleteDiaryEntry: vi.fn(),
  resolveReport: vi.fn(),
  setReviewStatus: vi.fn(),
  setDeveloperStatus: vi.fn(),
  setAppStatus: vi.fn(),
  getOverview: vi.fn(),
  listUsers: vi.fn(),
  getUserDetail: vi.fn(),
  listReports: vi.fn(),
  listDiaryEntries: vi.fn(),
  listDevelopers: vi.fn(),
  listAllApps: vi.fn(),
  listAudit: vi.fn(),
  listPrivateApps: vi.fn(),
  issueDownload: vi.fn(),
}));

vi.mock("@/lib/admin", () => ({ requireAdmin: async () => auth }));
vi.mock("@/lib/supabase", () => ({ serviceClient: () => ({ __svc: true }) }));
vi.mock("@/lib/moderation", async (orig) => ({ ...(await orig<typeof import("@/lib/moderation")>()), ...ops }));
vi.mock("@/lib/admin-queries", () => ops);
vi.mock("@/lib/download", async (orig) => ({ ...(await orig<typeof import("@/lib/download")>()), issueDownload: ops.issueDownload }));

import { ModerationError } from "@/lib/moderation";
import { DownloadError } from "@/lib/download";
import * as overview from "./overview/route";
import * as users from "./users/route";
import * as userDetail from "./users/[id]/route";
import * as ban from "./users/[id]/ban/route";
import * as unban from "./users/[id]/unban/route";
import * as reports from "./reports/route";
import * as reportAction from "./reports/[kind]/[id]/route";
import * as entries from "./diary/entries/route";
import * as entry from "./diary/entries/[id]/route";
import * as review from "./reviews/[id]/route";
import * as developers from "./developers/route";
import * as developer from "./developers/[userId]/route";
import * as apps from "./apps/route";
import * as appStatus from "./apps/[id]/status/route";
import * as audit from "./audit/route";
import * as privateApps from "./private-apps/route";
import * as privateDownload from "./private-apps/[releaseId]/download/route";

const UID = "11111111-1111-4111-8111-111111111111";
const req = (method: string, body?: unknown, url = "http://localhost/api/admin/x") =>
  new Request(url, { method, body: body === undefined ? undefined : typeof body === "string" ? body : JSON.stringify(body) });
// ルートごとに params の型が違うので、テストでは型を緩める
const ctx = (params: Record<string, string>) => ({ params: Promise.resolve(params) }) as never;

type Handler = (request: Request, context: { params: Promise<Record<string, string>> }) => Promise<Response>;
const all: [string, Handler, Request][] = [
  ["GET /overview", overview.GET as Handler, req("GET")],
  ["GET /users", users.GET as Handler, req("GET")],
  ["GET /users/:id", userDetail.GET as Handler, req("GET")],
  ["POST /users/:id/ban", ban.POST as Handler, req("POST", { reason: "x" })],
  ["POST /users/:id/unban", unban.POST as Handler, req("POST")],
  ["GET /reports", reports.GET as Handler, req("GET")],
  ["POST /reports/:kind/:id", reportAction.POST as Handler, req("POST", { action: "dismiss" })],
  ["GET /diary/entries", entries.GET as Handler, req("GET")],
  ["DELETE /diary/entries/:id", entry.DELETE as Handler, req("DELETE")],
  ["POST /reviews/:id", review.POST as Handler, req("POST", { status: "hidden" })],
  ["GET /developers", developers.GET as Handler, req("GET")],
  ["POST /developers/:userId", developer.POST as Handler, req("POST", { status: "approved" })],
  ["GET /apps", apps.GET as Handler, req("GET")],
  ["POST /apps/:id/status", appStatus.POST as Handler, req("POST", { status: "suspended" })],
  ["GET /audit", audit.GET as Handler, req("GET")],
  ["GET /private-apps", privateApps.GET as Handler, req("GET")],
  ["POST /private-apps/:releaseId/download", privateDownload.POST as Handler, req("POST")],
];
const params = { id: UID, userId: UID, releaseId: UID, kind: "diary" };

beforeEach(() => {
  auth = admin;
  for (const fn of Object.values(ops)) fn.mockReset().mockResolvedValue({ ok: true });
});

describe("認可: 管理者でなければ全ルート 401", () => {
  it.each(all)("%s", async (_name, handler, request) => {
    auth = denied();
    const res = await handler(request, ctx(params));
    expect(res.status).toBe(401);
    for (const fn of Object.values(ops)) expect(fn).not.toHaveBeenCalled();
  });
});

describe("入力の検証", () => {
  it("IDがUUIDでなければ 404(操作しない)", async () => {
    expect((await userDetail.GET(req("GET"), ctx({ id: "not-uuid" }))).status).toBe(404);
    expect((await ban.POST(req("POST", {}), ctx({ id: "x" }))).status).toBe(404);
    expect((await unban.POST(req("POST"), ctx({ id: "x" }))).status).toBe(404);
    expect((await entry.DELETE(req("DELETE"), ctx({ id: "x" }))).status).toBe(404);
    expect((await review.POST(req("POST", { status: "hidden" }), ctx({ id: "x" }))).status).toBe(404);
    expect((await developer.POST(req("POST", { status: "approved" }), ctx({ userId: "x" }))).status).toBe(404);
    expect((await appStatus.POST(req("POST", { status: "suspended" }), ctx({ id: "x" }))).status).toBe(404);
    expect((await privateDownload.POST(req("POST"), ctx({ releaseId: "x" }))).status).toBe(404);
    expect(ops.banUser).not.toHaveBeenCalled();
    expect(ops.getUserDetail).not.toHaveBeenCalled();
    expect(ops.issueDownload).not.toHaveBeenCalled();
  });

  it("報告の種類・アクション・ステータスが不正なら拒否", async () => {
    expect((await reportAction.POST(req("POST", { action: "dismiss" }), ctx({ kind: "other", id: UID }))).status).toBe(404);
    expect((await reportAction.POST(req("POST", { action: "explode" }), ctx({ kind: "diary", id: UID }))).status).toBe(400);
    expect((await reportAction.POST(req("POST", "not json"), ctx({ kind: "diary", id: UID }))).status).toBe(400);
    expect((await review.POST(req("POST", { status: "deleted" }), ctx({ id: UID }))).status).toBe(400);
    expect((await developer.POST(req("POST", { status: "pending" }), ctx({ userId: UID }))).status).toBe(400);
    expect((await appStatus.POST(req("POST", { status: "draft" }), ctx({ id: UID }))).status).toBe(400);
    expect(ops.resolveReport).not.toHaveBeenCalled();
    expect(ops.setReviewStatus).not.toHaveBeenCalled();
    expect(ops.setDeveloperStatus).not.toHaveBeenCalled();
    expect(ops.setAppStatus).not.toHaveBeenCalled();
  });

  it("BANの理由が長すぎると 400。理由なしでもBANできる", async () => {
    expect((await ban.POST(req("POST", { reason: "あ".repeat(501) }), ctx({ id: UID }))).status).toBe(400);
    expect(ops.banUser).not.toHaveBeenCalled();
    expect((await ban.POST(req("POST"), ctx({ id: UID }))).status).toBe(200);
    expect(ops.banUser).toHaveBeenCalledWith(expect.anything(), admin.admin, UID, "");
  });

  it("日記の一覧: userId がUUIDでなければ 400", async () => {
    expect((await entries.GET(req("GET", undefined, "http://localhost/x?userId=abc"))).status).toBe(400);
    expect(ops.listDiaryEntries).not.toHaveBeenCalled();
  });
});

describe("操作の呼び出し", () => {
  it("BAN/解除は、操作した管理者を渡す", async () => {
    await ban.POST(req("POST", { reason: "迷惑" }), ctx({ id: UID }));
    expect(ops.banUser).toHaveBeenCalledWith(expect.anything(), admin.admin, UID, "迷惑");
    await unban.POST(req("POST"), ctx({ id: UID }));
    expect(ops.unbanUser).toHaveBeenCalledWith(expect.anything(), admin.admin, UID);
  });

  it("報告の処理は、種類・ID・アクション・理由を渡す", async () => {
    const res = await reportAction.POST(req("POST", { action: "ban_author", reason: "嫌がらせ" }), ctx({ kind: "review", id: "42" }));
    expect(res.status).toBe(200);
    expect(ops.resolveReport).toHaveBeenCalledWith(expect.anything(), admin.admin, "review", "42", "ban_author", "嫌がらせ");
  });

  it("ユーザー一覧: 検索語・BAN絞り込み・ページングを渡す。limit は100まで", async () => {
    ops.listUsers.mockResolvedValue({ items: [], total: 0 });
    await users.GET(req("GET", undefined, "http://localhost/x?q=alice&banned=1&limit=500&offset=-5"));
    expect(ops.listUsers).toHaveBeenCalledWith(expect.anything(), { q: "alice", bannedOnly: true, limit: 100, offset: 0 });
  });

  it("報告の一覧: 既定は未対応のみ、status=all で全件", async () => {
    ops.listReports.mockResolvedValue([]);
    await reports.GET(req("GET"));
    expect(ops.listReports).toHaveBeenLastCalledWith(expect.anything(), { openOnly: true });
    await reports.GET(req("GET", undefined, "http://localhost/x?status=all"));
    expect(ops.listReports).toHaveBeenLastCalledWith(expect.anything(), { openOnly: false });
  });

  it("開発者の一覧: 不明な status は絞り込みなし", async () => {
    ops.listDevelopers.mockResolvedValue([]);
    await developers.GET(req("GET", undefined, "http://localhost/x?status=bogus"));
    expect(ops.listDevelopers).toHaveBeenLastCalledWith(expect.anything(), undefined);
    await developers.GET(req("GET", undefined, "http://localhost/x?status=pending"));
    expect(ops.listDevelopers).toHaveBeenLastCalledWith(expect.anything(), "pending");
  });
});

describe("エラーの返し方", () => {
  it("ModerationError は、その状態コードと文言で返す", async () => {
    ops.banUser.mockRejectedValue(new ModerationError("管理者はBANできません", 409));
    const res = await ban.POST(req("POST", { reason: "x" }), ctx({ id: UID }));
    expect(res.status).toBe(409);
    expect(await res.json()).toEqual({ error: "管理者はBANできません" });
  });

  it("想定外のエラーは 500 で、内部の詳細は返さない", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    ops.getOverview.mockRejectedValue(new Error("connection to db.secret.internal refused"));
    const res = await overview.GET(req("GET"));
    expect(res.status).toBe(500);
    expect(JSON.stringify(await res.json())).not.toContain("secret");
  });

  it("キャッシュさせない(no-store)", async () => {
    ops.getOverview.mockResolvedValue({});
    expect((await overview.GET(req("GET"))).headers.get("cache-control")).toBe("no-store");
  });
});

describe("管理者専用アプリのダウンロード", () => {
  it("管理者として発行する(管理者チェックは常に true を渡す)", async () => {
    ops.issueDownload.mockResolvedValue({ url: "https://r2/x" });
    const res = await privateDownload.POST(req("POST"), ctx({ releaseId: UID }));
    expect(res.status).toBe(200);
    const [releaseId, deviceId, userId, isAdmin] = ops.issueDownload.mock.calls[0];
    expect(releaseId).toBe(UID);
    expect(deviceId).toBe("admin-admin-1");
    expect(userId).toBe("admin-1");
    expect(await isAdmin()).toBe(true);
  });

  it("発行できないリリースは、そのステータスで返す", async () => {
    ops.issueDownload.mockRejectedValue(new DownloadError("このアプリは現在ダウンロードできません", 404));
    expect((await privateDownload.POST(req("POST"), ctx({ releaseId: UID }))).status).toBe(404);
  });
});
