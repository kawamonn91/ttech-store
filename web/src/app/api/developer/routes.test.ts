import sharp from "sharp";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fakeSupabase, type FakeCall } from "@/lib/testing/fake-supabase";

// ---- 認証(セッション)・R2・GitHub・メールは差し替える。判定・検証・DB操作は本物のコードを通す
const state = vi.hoisted(() => ({ auth: null as null | { user: unknown; developer?: unknown; svc: unknown } }));
vi.mock("@/lib/developer", async (orig) => {
  const actual = await orig<typeof import("@/lib/developer")>();
  const { jsonError } = await import("@/lib/http");
  const guard = () => (state.auth ? state.auth : { response: jsonError("ログインしてください", 401) });
  return { ...actual, requireUser: async () => guard(), requireDeveloper: async () => guard() };
});
const r2 = vi.hoisted(() => ({
  headObject: vi.fn(), copyObject: vi.fn(), deleteObject: vi.fn(), presignUpload: vi.fn(async (k: string) => `https://r2.example/${k}?sig=1`),
}));
vi.mock("@/lib/r2", async (orig) => ({ ...(await orig<typeof import("@/lib/r2")>()), ...r2 }));
const dispatch = vi.hoisted(() => ({ dispatchScan: vi.fn() }));
vi.mock("@/lib/scan-dispatch", () => dispatch);

import { DELETE as deleteMedia, POST as postMedia } from "./apps/[id]/media/route";
import { PATCH as patchApp } from "./apps/[id]/route";
import { POST as postRelease } from "./apps/[id]/releases/route";
import { POST as postVisibility } from "./apps/[id]/visibility/route";
import { POST as postApp } from "./apps/route";
import { POST as postRegister } from "./register/route";
import { POST as postUploaded } from "./releases/[id]/uploaded/route";

const USER = { id: "11111111-1111-1111-1111-111111111111", email: "kei@example.com", emailConfirmed: true };
const APP_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
const REL_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

function login(fake: ReturnType<typeof fakeSupabase>, over: { user?: object } = {}) {
  state.auth = { user: { ...USER, ...over.user }, developer: { name: "ケイ", status: "approved", review_mode: "auto" }, svc: fake.client };
}
const json = (body: unknown) => new Request("http://localhost/api", { method: "POST", body: JSON.stringify(body) });
const ctx = (id: string) => ({ params: Promise.resolve({ id }) });
const isCount = (c: FakeCall) => c.chain.some(([m, a]) => m === "select" && (a[1] as { head?: boolean } | undefined)?.head);

const OWN_APP = { id: APP_ID, slug: "kei", name: "ケイ", package_name: "jp.kei.app", status: "draft", developer_id: USER.id };

beforeEach(() => {
  state.auth = null;
  vi.stubEnv("RESEND_API_KEY", "re_test");
  vi.stubEnv("ADMIN_NOTIFY_EMAIL", "owner@example.com");
  vi.stubGlobal("fetch", vi.fn(async () => ({ ok: true, status: 200, text: async () => "" })));
  Object.values(r2).forEach((f) => f.mockReset());
  r2.presignUpload.mockImplementation(async (k: string) => `https://r2.example/${k}?sig=1`);
  dispatch.dispatchScan.mockReset().mockResolvedValue({ dispatched: true });
  vi.spyOn(console, "warn").mockImplementation(() => {});
  vi.spyOn(console, "error").mockImplementation(() => {});
});
afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("POST /api/developer/register(開発者の会員登録)", () => {
  const ok = { name: "ケイのアプリ", contactEmail: "", website: "", acceptTerms: true };

  it("ログインしていなければ 401", async () => {
    expect((await postRegister(json(ok))).status).toBe(401);
  });

  it("承認を待たず、すぐ使える開発者(承認済み・自動審査)として登録される。規約の同意日時が残る", async () => {
    const fake = fakeSupabase();
    login(fake);
    const res = await postRegister(json(ok));
    expect(res.status).toBe(200);
    const insert = fake.callsTo("developers", "insert")[0].payload as Record<string, unknown>;
    expect(insert).toMatchObject({ user_id: USER.id, name: "ケイのアプリ", contact_email: "kei@example.com", status: "approved", review_mode: "auto", website: null });
    expect(typeof insert.terms_accepted_at).toBe("string");
  });

  it("運営に、登録があったことをメールで知らせる(送れなくても登録は成功する)", async () => {
    const fake = fakeSupabase();
    login(fake);
    await postRegister(json(ok));
    expect(fetch).toHaveBeenCalledTimes(1);
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: false, status: 500, text: async () => "" })));
    expect((await postRegister(json(ok))).status).toBe(200);
  });

  it("規約に同意していない・名前が短い・使えない語を含む名前は拒否する。DBには書かない", async () => {
    const fake = fakeSupabase();
    login(fake);
    expect((await postRegister(json({ ...ok, acceptTerms: false }))).status).toBe(400);
    expect((await postRegister(json({ ...ok, name: "あ" }))).status).toBe(400);
    const reserved = await postRegister(json({ ...ok, name: "Google Japan" }));
    expect(reserved.status).toBe(400);
    expect((await reserved.json()).error).toContain("紛らわしい");
    expect(fake.calls.filter((c) => c.table === "developers" && c.op !== "select")).toHaveLength(0);
  });

  it("メールアドレスの確認が済んでいなければ 403", async () => {
    const fake = fakeSupabase();
    login(fake, { user: { emailConfirmed: false } });
    const res = await postRegister(json(ok));
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe("email_unconfirmed");
  });

  it("停止された開発者は 403。すでに承認済みなら何もしない。以前の申請(pending)は承認済みに更新する", async () => {
    let status: string | null = "suspended";
    const fake = fakeSupabase({ results: { "developers.select": () => ({ data: status ? { status } : null }) } });
    login(fake);
    expect((await postRegister(json(ok))).status).toBe(403);
    status = "approved";
    expect(await (await postRegister(json(ok))).json()).toEqual({ ok: true, already: true });
    status = "pending";
    expect((await postRegister(json(ok))).status).toBe(200);
    expect(fake.callsTo("developers", "update")[0].payload).toMatchObject({ status: "approved", review_mode: "auto" });
    expect(fake.callsTo("developers", "insert")).toHaveLength(0);
  });

  it("同じ開発者名(大文字小文字違いを含む)がすでにあれば 409", async () => {
    const fake = fakeSupabase({ results: { "developers.insert": { error: { message: 'duplicate key value violates unique constraint "developers_name_lower_key"', code: "23505" } as never } } });
    login(fake);
    const res = await postRegister(json(ok));
    expect(res.status).toBe(409);
    expect((await res.json()).error).toContain("すでに使われています");
  });
});

describe("POST /api/developer/apps(アプリの登録)", () => {
  const body = { packageName: "jp.kei.weightlog", name: "体重ログ", shortDesc: "体重を記録", description: "説明" };
  const setup = (over: { count?: number; insert?: unknown | ((n: number) => unknown); category?: unknown } = {}) => {
    let inserts = 0;
    return fakeSupabase({
      results: {
        "apps.select": (c: FakeCall) => (isCount(c) ? { count: over.count ?? 0 } : { data: null }),
        "categories.select": { data: "category" in over ? over.category : { id: 2 } },
        "apps.insert": () => {
          inserts++;
          const r = typeof over.insert === "function" ? over.insert(inserts) : over.insert;
          return (r ?? { data: { id: APP_ID, slug: "weightlog" } }) as never;
        },
      },
    });
  };

  it("ログインしていなければ 401", async () => {
    expect((await postApp(json(body))).status).toBe(401);
  });

  it("下書きとして登録する。所有者はセッションから決まり、リクエストの値は使わない。slug はパッケージ名から作る", async () => {
    const fake = setup();
    login(fake);
    const res = await postApp(json({ ...body, developerId: "someone-else", status: "published" }));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ id: APP_ID, slug: "weightlog" });
    expect(fake.callsTo("apps", "insert")[0].payload).toEqual({
      slug: "weightlog", package_name: "jp.kei.weightlog", developer_id: USER.id, name: "体重ログ", short_desc: "体重を記録", description: "説明", category_id: null, status: "draft",
    });
  });

  it("予約されたパッケージ名(com.google. など)・紛らわしい名前・使えない slug は 400", async () => {
    const fake = setup();
    login(fake);
    expect((await postApp(json({ ...body, packageName: "com.google.android.apps.maps" }))).status).toBe(400);
    expect((await postApp(json({ ...body, packageName: "com.ttech.fake" }))).status).toBe(400);
    expect((await postApp(json({ ...body, name: "YouTube Premium" }))).status).toBe(400);
    expect((await postApp(json({ ...body, slug: "admin" }))).status).toBe(400);
    expect(fake.callsTo("apps", "insert")).toHaveLength(0);
  });

  it("登録できる本数の上限を超えると 403", async () => {
    login(setup({ count: 10 }));
    const res = await postApp(json(body));
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe("app_limit");
  });

  it("存在しないカテゴリは 400", async () => {
    login(setup({ category: null }));
    expect((await postApp(json({ ...body, categoryId: 99 }))).status).toBe(400);
  });

  it("パッケージ名が登録済みなら 409", async () => {
    login(setup({ insert: { error: { message: 'duplicate key value violates unique constraint "apps_package_name_key"', code: "23505" } } }));
    const res = await postApp(json(body));
    expect(res.status).toBe(409);
    expect((await res.json()).error).toContain("パッケージ名");
  });

  it("自動で決めた slug が使われていれば、接尾辞を付けて1回やり直す。自分で指定した slug は 409", async () => {
    const dup = { error: { message: 'duplicate key value violates unique constraint "apps_slug_key"', code: "23505" } };
    const fake = setup({ insert: (n: number) => (n === 1 ? dup : undefined) });
    login(fake);
    const res = await postApp(json(body));
    expect(res.status).toBe(200);
    expect((await res.json()).slug).toMatch(/^weightlog-[a-z0-9]{1,4}$/);
    expect(fake.callsTo("apps", "insert")).toHaveLength(2);

    login(setup({ insert: dup }));
    expect((await postApp(json({ ...body, slug: "weight-log" }))).status).toBe(409);
  });
});

describe("PATCH /api/developer/apps/:id(掲載内容の更新)", () => {
  const patch = (id: string, body: unknown) => patchApp(new Request("http://localhost/api", { method: "PATCH", body: JSON.stringify(body) }), ctx(id));
  const setup = (app: unknown = OWN_APP) => fakeSupabase({ results: { "apps.select": { data: app }, "categories.select": { data: { id: 3 } } } });

  it("自分のアプリの名前・説明・カテゴリだけを更新できる(状態・パッケージ名の指定は無視される)", async () => {
    const fake = setup();
    login(fake);
    const res = await patch(APP_ID, { name: "新しい名前", description: "新しい説明", categoryId: 3, status: "published", package_name: "com.evil.app" });
    expect(res.status).toBe(200);
    expect(fake.callsTo("apps", "update")[0].payload).toEqual({ name: "新しい名前", description: "新しい説明", category_id: 3 });
  });

  it("他人のアプリ・存在しないアプリは 404。停止されたアプリは 403。紛らわしい名前は 400", async () => {
    login(setup(null));
    expect((await patch(APP_ID, { name: "x" })).status).toBe(404);
    expect((await patch("not-a-uuid", { name: "x" })).status).toBe(404);
    login(setup({ ...OWN_APP, status: "suspended" }));
    expect((await patch(APP_ID, { name: "x" })).status).toBe(403);
    login(setup());
    expect((await patch(APP_ID, { name: "Google公式" })).status).toBe(400);
    expect((await patch(APP_ID, {})).status).toBe(400);
  });
});

describe("POST /api/developer/apps/:id/releases(アップロードの開始)", () => {
  const setup = (over: { count?: number; app?: unknown } = {}) =>
    fakeSupabase({
      results: {
        "apps.select": (c: FakeCall) => (c.chain.some(([m]) => m === "maybeSingle") ? { data: "app" in over ? over.app : OWN_APP } : { data: [{ id: APP_ID }] }),
        "app_releases.select": { count: over.count ?? 0 },
        "app_releases.insert": { error: null },
      },
    });

  it("リリースを作り、アップロード専用のキー(検査・公開用とは別)への署名付きURLを返す", async () => {
    const fake = setup();
    login(fake);
    const res = await postRelease(json({ releaseNotes: "初回" }), ctx(APP_ID));
    expect(res.status).toBe(200);
    const out = await res.json();
    expect(out.uploadUrl).toBe(`https://r2.example/upload/${APP_ID}/${out.releaseId}.apk?sig=1`);
    expect(out.maxBytes).toBe(200 * 1024 * 1024);
    expect(fake.callsTo("app_releases", "insert")[0].payload).toMatchObject({ id: out.releaseId, app_id: APP_ID, apk_key: `upload/${APP_ID}/${out.releaseId}.apk`, release_notes: "初回", status: "uploaded" });
  });

  it("24時間に10回を超えると 429。他人のアプリは 404。停止されたアプリは 403", async () => {
    login(setup({ count: 10 }));
    const limited = await postRelease(json({}), ctx(APP_ID));
    expect(limited.status).toBe(429);
    expect((await limited.json()).code).toBe("rate_limited");
    login(setup({ app: null }));
    expect((await postRelease(json({}), ctx(APP_ID))).status).toBe(404);
    login(setup({ app: { ...OWN_APP, status: "suspended" } }));
    expect((await postRelease(json({}), ctx(APP_ID))).status).toBe(403);
  });
});

describe("POST /api/developer/releases/:id/uploaded(アップロード完了→複製して検査)", () => {
  const upKey = `upload/${APP_ID}/${REL_ID}.apk`;
  const finalKey = `apk/${APP_ID}/${REL_ID}.apk`;
  const release = (over: Record<string, unknown> = {}, appOver: Record<string, unknown> = {}) => ({
    id: REL_ID, app_id: APP_ID, apk_key: upKey, status: "uploaded", app: { id: APP_ID, developer_id: USER.id, status: "draft", ...appOver }, ...over,
  });
  const setup = (rel: unknown = release()) => fakeSupabase({ results: { "app_releases.select": { data: rel }, "app_releases.update": { error: null } } });
  const call = () => postUploaded(json({}), ctx(REL_ID));

  beforeEach(() => {
    r2.headObject.mockResolvedValue({ size: 5_000_000 });
    r2.copyObject.mockResolvedValue(true);
    r2.deleteObject.mockResolvedValue(undefined);
  });

  it("アップロードされたAPKを、検査・公開用のキーへ複製し、アップロード先を削除してから、検査を起動する", async () => {
    const fake = setup();
    login(fake);
    const res = await call();
    expect(res.status).toBe(200);
    expect(r2.copyObject).toHaveBeenCalledWith(upKey, finalKey);
    expect(r2.deleteObject).toHaveBeenCalledWith(upKey);
    expect(fake.callsTo("app_releases", "update")[0].payload).toEqual({ apk_key: finalKey, apk_size: 5_000_000 });
    // 検査は、複製したほう(開発者が書き換えられない場所)を対象にする
    expect(dispatch.dispatchScan).toHaveBeenCalledWith(REL_ID, finalKey);
    // 複製が終わる前に検査を起動しない
    expect(r2.copyObject.mock.invocationCallOrder[0]).toBeLessThan(dispatch.dispatchScan.mock.invocationCallOrder[0]);
    expect(r2.deleteObject.mock.invocationCallOrder[0]).toBeLessThan(dispatch.dispatchScan.mock.invocationCallOrder[0]);
  });

  it("他人のリリース・存在しないリリースは 404(存在を知らせない)。検査に進んでいるものは 409", async () => {
    login(setup(release({}, { developer_id: "someone-else" })));
    expect((await call()).status).toBe(404);
    login(setup(null));
    expect((await call()).status).toBe(404);
    expect((await postUploaded(json({}), ctx("../../etc"))).status).toBe(404);
    login(setup(release({ status: "scanned" })));
    expect((await call()).status).toBe(409);
    expect(r2.copyObject).not.toHaveBeenCalled();
    expect(dispatch.dispatchScan).not.toHaveBeenCalled();
  });

  it("APKがまだ無い・複製元が消えているときは 409。検査は起動しない", async () => {
    login(setup());
    r2.headObject.mockResolvedValueOnce(null);
    expect((await call()).status).toBe(409);
    r2.copyObject.mockResolvedValueOnce(false);
    expect((await call()).status).toBe(409);
    expect(dispatch.dispatchScan).not.toHaveBeenCalled();
  });

  it("大きすぎるAPKは、削除して却下する(413)", async () => {
    const fake = setup();
    login(fake);
    r2.headObject.mockResolvedValueOnce({ size: 201 * 1024 * 1024 });
    const res = await call();
    expect(res.status).toBe(413);
    expect(r2.deleteObject).toHaveBeenCalledWith(upKey);
    expect(fake.callsTo("app_releases", "update")[0].payload).toMatchObject({ status: "rejected" });
    expect(dispatch.dispatchScan).not.toHaveBeenCalled();
  });

  it("アップロード先の削除に失敗しても、検査は起動する(そのキーは、もう使わないため)", async () => {
    login(setup());
    r2.deleteObject.mockRejectedValueOnce(new Error("R2 down"));
    expect((await call()).status).toBe(200);
    expect(dispatch.dispatchScan).toHaveBeenCalled();
  });

  it("検査の起動に失敗したら、その内容を返す(502)", async () => {
    login(setup());
    dispatch.dispatchScan.mockResolvedValueOnce({ dispatched: false, error: "検査の起動に失敗しました (GitHub 401)", status: 502 });
    expect((await call()).status).toBe(502);
  });
});

describe("POST /api/developer/apps/:id/visibility(公開・非公開)", () => {
  const setup = (over: { app?: unknown; published?: unknown[] } = {}) =>
    fakeSupabase({ results: { "apps.select": { data: "app" in over ? over.app : OWN_APP }, "app_releases.select": { data: over.published ?? [{ id: REL_ID }] }, "apps.update": { error: null } } });

  it("公開済みのリリースがあれば公開でき、いつでも非公開(下書き)に戻せる", async () => {
    const fake = setup();
    login(fake);
    expect(await (await postVisibility(json({ visible: true }), ctx(APP_ID))).json()).toEqual({ ok: true, status: "published" });
    expect(await (await postVisibility(json({ visible: false }), ctx(APP_ID))).json()).toEqual({ ok: true, status: "draft" });
    expect(fake.callsTo("apps", "update").map((c) => c.payload)).toEqual([{ status: "published" }, { status: "draft" }]);
  });

  it("公開済みのリリースが無ければ公開できない(409)。停止されたアプリは変えられない(403)", async () => {
    login(setup({ published: [] }));
    expect((await postVisibility(json({ visible: true }), ctx(APP_ID))).status).toBe(409);
    login(setup({ app: { ...OWN_APP, status: "suspended" } }));
    expect((await postVisibility(json({ visible: true }), ctx(APP_ID))).status).toBe(403);
    login(setup({ app: null }));
    expect((await postVisibility(json({ visible: false }), ctx(APP_ID))).status).toBe(404);
  });
});

describe("アイコン・スクリーンショット(/api/developer/apps/:id/media)", () => {
  const png = (w: number, h: number) => sharp({ create: { width: w, height: h, channels: 3, background: { r: 252, g: 76, b: 2 } } }).png().toBuffer();
  const upload = async (kind: string, data: Buffer | null, id = APP_ID) => {
    const form = new FormData();
    form.set("kind", kind);
    if (data) form.set("file", new File([new Uint8Array(data)], "x.png", { type: "image/png" }));
    return postMedia(new Request("http://localhost/api", { method: "POST", body: form }), ctx(id));
  };
  const setup = (app: unknown = { ...OWN_APP, icon_path: `${APP_ID}/icon-1.png`, screenshots: [] }) =>
    fakeSupabase({ results: { "apps.select": { data: app }, "apps.update": { error: null } } });

  it("アイコン: 検査して 512×512 のPNGに作り直して保存し、パスを更新し、古いアイコンを削除する", async () => {
    const fake = setup();
    login(fake);
    const res = await upload("icon", await png(300, 300));
    expect(res.status).toBe(200);
    const up = fake.storageCalls.find((c) => c.op === "upload")!;
    expect(up.bucket).toBe("app-media");
    expect(up.paths[0]).toMatch(new RegExp(`^${APP_ID}/icon-\\d+\\.png$`));
    const meta = await sharp(up.body as Buffer).metadata();
    expect([meta.format, meta.width, meta.height]).toEqual(["png", 512, 512]);
    expect(fake.callsTo("apps", "update")[0].payload).toEqual({ icon_path: up.paths[0] });
    expect(fake.storageCalls.find((c) => c.op === "remove")!.paths).toEqual([`${APP_ID}/icon-1.png`]);
  });

  it("スクリーンショット: 長辺1600pxまでに縮小して、既存の一覧に追加する。上限(8枚)を超えると 409", async () => {
    const fake = setup({ ...OWN_APP, icon_path: null, screenshots: [`${APP_ID}/screenshot-1.png`] });
    login(fake);
    expect((await upload("screenshot", await png(1080, 2400))).status).toBe(200);
    const up = fake.storageCalls.find((c) => c.op === "upload")!;
    const meta = await sharp(up.body as Buffer).metadata();
    expect(Math.max(meta.width!, meta.height!)).toBe(1600);
    expect(fake.callsTo("apps", "update")[0].payload).toEqual({ screenshots: [`${APP_ID}/screenshot-1.png`, up.paths[0]] });

    const full = Array.from({ length: 8 }, (_, i) => `${APP_ID}/screenshot-${i}.png`);
    login(setup({ ...OWN_APP, icon_path: null, screenshots: full }));
    expect((await upload("screenshot", await png(400, 800))).status).toBe(409);
  });

  it("画像でないもの・小さすぎる画像・縦横が大きく違うアイコン・大きすぎるファイルは、保存せず 400/413", async () => {
    const fake = setup();
    login(fake);
    for (const bad of [Buffer.from("これは画像ではありません"), await png(50, 50)]) expect((await upload("screenshot", bad)).status).toBe(400);
    expect((await upload("icon", await png(600, 200))).status).toBe(400);
    const huge = await upload("screenshot", Buffer.alloc(3 * 1024 * 1024 + 1));
    expect(huge.status).toBe(413);
    expect(fake.storageCalls.filter((c) => c.op === "upload")).toHaveLength(0);
  });

  it("形式の違うリクエスト・他人のアプリ・停止されたアプリは拒否する", async () => {
    login(setup());
    expect((await upload("video", await png(300, 300))).status).toBe(400);
    expect((await upload("icon", null)).status).toBe(400);
    login(setup(null));
    expect((await upload("icon", await png(300, 300))).status).toBe(404);
    login(setup({ ...OWN_APP, status: "suspended", icon_path: null, screenshots: [] }));
    expect((await upload("icon", await png(300, 300))).status).toBe(403);
  });

  it("スクリーンショットの削除: 自分のアプリの、登録済みの画像だけ削除できる", async () => {
    const path = `${APP_ID}/screenshot-1.png`;
    const fake = setup({ ...OWN_APP, screenshots: [path, `${APP_ID}/screenshot-2.png`] });
    login(fake);
    const del = (p: string) => deleteMedia(new Request("http://localhost/api", { method: "DELETE", body: JSON.stringify({ path: p }) }), ctx(APP_ID));
    expect((await del(path)).status).toBe(200);
    expect(fake.callsTo("apps", "update")[0].payload).toEqual({ screenshots: [`${APP_ID}/screenshot-2.png`] });
    expect(fake.storageCalls.find((c) => c.op === "remove")!.paths).toEqual([path]);
    // 他のアプリのフォルダ・一覧にない画像は、削除できない
    expect((await del("cccccccc-cccc-cccc-cccc-cccccccccccc/screenshot-1.png")).status).toBe(404);
    expect((await del(`${APP_ID}/not-listed.png`)).status).toBe(404);
  });
});
