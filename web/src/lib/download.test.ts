import { beforeEach, describe, expect, it, vi } from "vitest";

const rpc = vi.fn();
let releaseRow: unknown;

vi.mock("./supabase", () => ({
  serviceClient: () => ({
    from: () => ({
      select: () => ({ eq: () => ({ maybeSingle: async () => ({ data: releaseRow, error: null }) }) }),
    }),
    rpc,
  }),
}));
vi.mock("./r2", () => ({
  DOWNLOAD_URL_TTL_SECONDS: 900,
  presignDownload: async (key: string) => `https://r2.example/${key}?sig=abc`,
}));

import { DownloadError, hashDevice, issueDownload } from "./download";

const published = () => ({
  id: "rel-1",
  apk_key: "apk/app-1/rel-1.apk",
  apk_size: 1000,
  sha256: "a".repeat(64),
  signing_cert_sha256: "b".repeat(64),
  version_code: 7,
  status: "published",
  app: { package_name: "jp.yomumemo.app", status: "published", admin_only: false },
});

beforeEach(() => {
  vi.stubEnv("DEVICE_HASH_SALT", "salt");
  rpc.mockReset().mockResolvedValue({ error: null });
  releaseRow = published();
});

describe("hashDevice", () => {
  it("同じ端末IDなら同じ、違えば違う値", () => {
    expect(hashDevice("device-1")).toBe(hashDevice("device-1"));
    expect(hashDevice("device-1")).not.toBe(hashDevice("device-2"));
  });
  it("生の端末IDを含まず、ソルトでも変わる", () => {
    const before = hashDevice("device-1");
    expect(before).toMatch(/^[0-9a-f]{64}$/);
    expect(before).not.toContain("device-1");
    vi.stubEnv("DEVICE_HASH_SALT", "other");
    expect(hashDevice("device-1")).not.toBe(before);
  });
});

describe("issueDownload", () => {
  it("公開中のリリースなら署名付きURLと検証情報を返し、DLを記録する", async () => {
    const info = await issueDownload("rel-1", "device-1", null);
    expect(info).toMatchObject({
      url: "https://r2.example/apk/app-1/rel-1.apk?sig=abc",
      sha256: "a".repeat(64),
      signingCertSha256: "b".repeat(64),
      versionCode: 7,
      packageName: "jp.yomumemo.app",
      apkSize: 1000,
    });
    expect(rpc).toHaveBeenCalledWith("record_download", {
      p_release_id: "rel-1",
      p_user_id: null,
      p_device_hash: hashDevice("device-1"),
    });
    expect(new Date(info.expiresAt).getTime()).toBeGreaterThan(Date.now());
  });

  it("存在しないリリースは 404", async () => {
    releaseRow = null;
    await expect(issueDownload("nope", "device-1", null)).rejects.toMatchObject({ status: 404 });
    expect(rpc).not.toHaveBeenCalled();
  });

  it.each([
    ["リリースが未公開(承認待ち)", () => ({ ...published(), status: "scanned" })],
    ["リリースが却下", () => ({ ...published(), status: "rejected" })],
    ["アプリが公開停止中", () => ({ ...published(), app: { package_name: "x.y", status: "suspended", admin_only: false } })],
  ])("%s なら 404 で、URLもDL記録も出さない", async (_name, make) => {
    releaseRow = make();
    await expect(issueDownload("rel-1", "device-1", null)).rejects.toBeInstanceOf(DownloadError);
    expect(rpc).not.toHaveBeenCalled();
  });

  describe("管理者専用アプリ(admin_only)", () => {
    const adminOnly = () => ({ ...published(), app: { package_name: "com.ttech.admin", status: "published", admin_only: true } });

    it("管理者でなければ、通常のアプリと同じ 404(存在を知らせない)で、URLもDL記録も出さない", async () => {
      releaseRow = adminOnly();
      await expect(issueDownload("rel-1", "device-1", null)).rejects.toMatchObject({ status: 404 });
      await expect(issueDownload("rel-1", "device-1", null, async () => false)).rejects.toMatchObject({ status: 404 });
      expect(rpc).not.toHaveBeenCalled();
    });

    it("管理者なら発行できる", async () => {
      releaseRow = adminOnly();
      await expect(issueDownload("rel-1", "device-1", null, async () => true)).resolves.toMatchObject({ packageName: "com.ttech.admin" });
      expect(rpc).toHaveBeenCalledTimes(1);
    });

    it("通常のアプリでは管理者かどうかを確認しない", async () => {
      const isAdmin = vi.fn(async () => true);
      await issueDownload("rel-1", "device-1", null, isAdmin);
      expect(isAdmin).not.toHaveBeenCalled();
    });
  });

  it("検証情報(ハッシュ/署名/バージョン)が欠けたリリースは 409", async () => {
    releaseRow = { ...published(), sha256: null };
    await expect(issueDownload("rel-1", "device-1", null)).rejects.toMatchObject({ status: 409 });
  });

  it("DL数の記録に失敗しても、ダウンロード自体は止めない", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    rpc.mockResolvedValue({ error: { message: "db down" } });
    await expect(issueDownload("rel-1", "device-1", null)).resolves.toMatchObject({ versionCode: 7 });
  });
});
