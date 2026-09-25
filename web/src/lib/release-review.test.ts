import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { reviewScannedRelease } from "./release-review";
import { ScanResultSchema } from "./scan";
import offlineFacts from "./testing/fixtures/facts-offline-app.json";
import runTrackerFacts from "./testing/fixtures/facts-run-tracker.json";
import { fakeSupabase, type FakeCall } from "./testing/fake-supabase";

const SHA = "a".repeat(64);
const CERT = "b".repeat(64);
const PKG = "com.ttech.weightlog";

const scan = (over: Record<string, unknown> = {}) =>
  ScanResultSchema.parse({
    packageName: PKG, versionName: "1.2", versionCode: 7, minSdk: 26, targetSdk: 36, permissions: [], apkSize: 1000,
    sha256: SHA, signingCertSha256: CERT, signerCount: 1, signature: { v1: false, v2: true, v3: true, subject: "CN=Dev" },
    facts: offlineFacts, ...over,
  });

interface Setup {
  release?: unknown;
  latestVersion?: number | null;
  updateError?: { message: string; code?: string } | null;
}

/** 第三者の開発者(auto)の、公開前のアプリの、アップロード直後のリリース */
const baseRelease = (over: { developer?: Record<string, unknown>; app?: Record<string, unknown>; status?: string } = {}) => ({
  id: "rel-1",
  status: over.status ?? "uploaded",
  app_id: "app-1",
  app: {
    id: "app-1", name: "体重ログ", status: "draft", package_name: PKG, signing_cert_sha256: null,
    developer: { name: "ケイ", contact_email: "kei@example.com", review_mode: "auto", status: "approved", ...over.developer },
    ...over.app,
  },
});

function setup(s: Setup = {}) {
  let updates = 0;
  return fakeSupabase({
    results: {
      "app_releases.select": (call: FakeCall) =>
        call.chain.some(([m]) => m === "in")
          ? { data: s.latestVersion == null ? [] : [{ version_code: s.latestVersion }] }
          : { data: "release" in s ? s.release : baseRelease() },
      "app_releases.update": () => {
        updates++;
        // 1回目の更新だけ失敗させられる(unique 制約のテスト用)
        return updates === 1 && s.updateError ? { error: s.updateError } : { error: null };
      },
      "apps.update": { error: null },
    },
  });
}

let mails: { to: string[]; subject: string; text: string }[] = [];

beforeEach(() => {
  mails = [];
  vi.stubEnv("RESEND_API_KEY", "re_test");
  vi.stubEnv("ADMIN_NOTIFY_EMAIL", "owner@example.com");
  vi.stubEnv("NEXT_PUBLIC_SITE_URL", "https://store.example.com");
  vi.stubGlobal(
    "fetch",
    vi.fn(async (_url: string, init: { body: string }) => {
      mails.push(JSON.parse(init.body));
      return { ok: true, status: 200, text: async () => "" };
    }),
  );
  vi.spyOn(console, "warn").mockImplementation(() => {});
});
afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

const updateOf = (f: ReturnType<typeof fakeSupabase>) => f.callsTo("app_releases", "update")[0].payload as Record<string, unknown>;

describe("reviewScannedRelease: 自動承認", () => {
  it("自動審査を通った第三者のアプリは、そのまま公開され、アプリも公開になる。運営と開発者に知らせる", async () => {
    const fake = setup();
    const r = await reviewScannedRelease(fake.client, "rel-1", scan());
    expect(r).toEqual({ kind: "ok", status: "published", reason: null, autoApproved: true, decision: "auto_approve" });
    expect(updateOf(fake)).toMatchObject({ status: "published", auto_approved: true, policy_verdict: "auto_approve", version_code: 7, sha256: SHA });
    expect(fake.callsTo("apps", "update")[0].payload).toEqual({ status: "published" });

    expect(mails.map((m) => m.to[0]).sort()).toEqual(["kei@example.com", "owner@example.com"]);
    expect(mails.find((m) => m.to[0] === "owner@example.com")!.subject).toContain("自動審査を通って公開");
    expect(mails.find((m) => m.to[0] === "kei@example.com")!.subject).toContain("を公開しました");
  });

  it("すでにアプリが公開されていれば、アプリの状態は更新しない(新しいバージョンの自動公開)", async () => {
    const fake = setup({ release: baseRelease({ app: { status: "published" } }), latestVersion: 6 });
    const r = await reviewScannedRelease(fake.client, "rel-1", scan());
    expect(r).toMatchObject({ kind: "ok", status: "published" });
    expect(fake.callsTo("apps", "update")).toHaveLength(0);
  });

  it("公開中と同じ・古い versionCode は自動では公開せず、承認待ちにする", async () => {
    const fake = setup({ latestVersion: 7 });
    const r = await reviewScannedRelease(fake.client, "rel-1", scan({ versionCode: 7 }));
    expect(r).toMatchObject({ kind: "ok", status: "scanned", autoApproved: false });
    expect((updateOf(fake).policy_findings as { code: string }[]).map((f) => f.code)).toContain("release.version-not-newer");
  });
});

describe("reviewScannedRelease: 要確認・却下", () => {
  it("通信の可能性が見つかれば承認待ちにし、運営へ理由つきでメールする。アプリは公開しない", async () => {
    const fake = setup({ release: baseRelease({ app: { package_name: "com.ttech.runtracker" } }) });
    const r = await reviewScannedRelease(fake.client, "rel-1", scan({ facts: runTrackerFacts, packageName: "com.ttech.runtracker" }));
    expect(r).toMatchObject({ kind: "ok", status: "scanned", autoApproved: false, decision: "needs_review" });
    expect(fake.callsTo("apps", "update")).toHaveLength(0);
    expect(updateOf(fake)).toMatchObject({ status: "scanned", auto_approved: false, policy_verdict: "needs_review" });

    const admin = mails.find((m) => m.to[0] === "owner@example.com")!;
    expect(admin.subject).toContain("承認待ち");
    expect(admin.text).toContain("通信につながる権限を要求しています(INTERNET)");
    expect(admin.text).toContain("https://store.example.com/admin/apps/app-1");
    const dev = mails.find((m) => m.to[0] === "kei@example.com")!;
    expect(dev.subject).toContain("運営が確認します");
  });

  it("明らかな不正(デバッグ署名)は却下する。開発者にだけ知らせ、versionCode は記録しない", async () => {
    const fake = setup();
    const r = await reviewScannedRelease(fake.client, "rel-1", scan({ signature: { v1: false, v2: true, v3: true, subject: "CN=Android Debug" } }));
    expect(r).toMatchObject({ kind: "ok", status: "rejected", decision: "reject" });
    expect(r.kind === "ok" && r.reason).toContain("デバッグ用の署名です");
    expect(updateOf(fake)).not.toHaveProperty("version_code");
    expect(mails.map((m) => m.to[0])).toEqual(["kei@example.com"]);
    expect(mails[0].subject).toContain("公開できませんでした");
  });

  it("パッケージ名の不一致など基本チェックでの却下も、開発者に知らせる", async () => {
    const fake = setup();
    const r = await reviewScannedRelease(fake.client, "rel-1", scan({ packageName: "com.other.app" }));
    expect(r).toMatchObject({ kind: "ok", status: "rejected", decision: null });
    expect(mails.map((m) => m.to[0])).toEqual(["kei@example.com"]);
    expect(mails[0].text).toContain("パッケージ名");
  });

  it("同じ versionCode のリリースがすでにあれば(unique 制約)、versionCode を上げるよう案内して却下する", async () => {
    const fake = setup({ updateError: { message: 'duplicate key value violates unique constraint "app_releases_app_id_version_code_key"', code: "23505" } });
    const r = await reviewScannedRelease(fake.client, "rel-1", scan());
    expect(r).toMatchObject({ kind: "ok", status: "rejected" });
    expect(r.kind === "ok" && r.reason).toContain("versionCode を上げて");
    const updates = fake.callsTo("app_releases", "update");
    expect(updates).toHaveLength(2);
    expect(updates[1].payload).toMatchObject({ status: "rejected" });
  });

  it("その他のDBエラーは例外にする(呼び出し側が 500 を返し、検査の再送で再試行できる)", async () => {
    const fake = setup({ updateError: { message: "connection lost" } });
    await expect(reviewScannedRelease(fake.client, "rel-1", scan())).rejects.toMatchObject({ message: "connection lost" });
  });
});

describe("reviewScannedRelease: 運営の承認が必要な開発者・アプリ", () => {
  it.each([
    ["運営自身の開発者(manual)", { developer: { review_mode: "manual" } }],
    ["停止された開発者", { developer: { status: "suspended" } }],
    ["審査方式が未設定", { developer: { review_mode: null } }],
    ["停止されたアプリ", { app: { status: "suspended" } }],
  ])("%s は、自動審査を通っても、必ず承認待ちにする", async (_name, over) => {
    const fake = setup({ release: baseRelease(over) });
    const r = await reviewScannedRelease(fake.client, "rel-1", scan());
    expect(r).toMatchObject({ kind: "ok", status: "scanned", autoApproved: false });
    expect(updateOf(fake)).toMatchObject({ status: "scanned", auto_approved: false });
    expect(fake.callsTo("apps", "update")).toHaveLength(0);
    expect(mails).toEqual([]); // 第三者の自動審査の対象でないので、通知もしない
  });
});

describe("reviewScannedRelease: 状態の確認・通知の失敗", () => {
  it("リリースが無ければ not_found。公開済み・承認済みは conflict で、何も書かない", async () => {
    expect(await reviewScannedRelease(setup({ release: null }).client, "x", scan())).toEqual({ kind: "not_found" });
    for (const status of ["published", "approved"]) {
      const fake = setup({ release: baseRelease({ status }) });
      expect(await reviewScannedRelease(fake.client, "rel-1", scan())).toEqual({ kind: "conflict" });
      expect(fake.callsTo("app_releases", "update")).toHaveLength(0);
    }
  });

  it("メールを送れなくても、審査結果の反映は成功する(通知は付随の処理)", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: false, status: 403, text: async () => "domain not verified" })));
    const fake = setup();
    const r = await reviewScannedRelease(fake.client, "rel-1", scan());
    expect(r).toMatchObject({ kind: "ok", status: "published" });
    expect(console.warn).toHaveBeenCalled();
  });

  it("RESEND_API_KEY が未設定でも、審査結果の反映は成功する", async () => {
    vi.stubEnv("RESEND_API_KEY", "");
    const r = await reviewScannedRelease(setup().client, "rel-1", scan());
    expect(r).toMatchObject({ kind: "ok", status: "published" });
  });
});
