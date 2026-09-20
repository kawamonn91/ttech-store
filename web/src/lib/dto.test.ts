import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mediaUrl, toDetail, toLatest, toSummary, type AppRow, type ReleaseRow } from "./dto";

beforeEach(() => vi.stubEnv("NEXT_PUBLIC_SUPABASE_URL", "https://proj.supabase.co"));
afterEach(() => vi.unstubAllEnvs());

const row = (over: Partial<AppRow> = {}): AppRow => ({
  id: "app-1",
  slug: "yomumemo",
  package_name: "jp.yomumemo.app",
  name: "ヨムメモ",
  short_desc: "読書メモ",
  description: "説明",
  icon_path: "app-1/icon.png",
  screenshots: ["app-1/s1.png", "app-1/s2.png"],
  download_count: 42,
  rating_avg: "4.50", // numeric は文字列で返ることがある
  rating_count: 3,
  category: { slug: "hobby", name: "趣味" },
  developer: { name: "T-tech" },
  ...over,
});

const release: ReleaseRow = {
  id: "rel-1",
  app_id: "app-1",
  version_name: "1.2.0",
  version_code: 12,
  apk_size: 2048,
  min_sdk: 26,
  permissions: ["android.permission.INTERNET"],
  release_notes: "修正",
  published_at: "2026-09-20T00:00:00Z",
};

describe("mediaUrl", () => {
  it("Storage の公開URLを組み立てる", () => {
    expect(mediaUrl("app-1/icon.png")).toBe("https://proj.supabase.co/storage/v1/object/public/app-media/app-1/icon.png");
  });
  it("パスが無ければ null", () => {
    expect(mediaUrl(null)).toBeNull();
  });
});

describe("toLatest", () => {
  it("リリース行を DTO に変換する", () => {
    expect(toLatest(release)).toEqual({
      releaseId: "rel-1",
      versionName: "1.2.0",
      versionCode: 12,
      apkSize: 2048,
      minSdk: 26,
      permissions: ["android.permission.INTERNET"],
      releaseNotes: "修正",
      publishedAt: "2026-09-20T00:00:00Z",
    });
  });
  it("リリースが無ければ null", () => {
    expect(toLatest(undefined)).toBeNull();
  });
});

describe("toSummary / toDetail", () => {
  it("数値型に正規化し、最新リリースを付ける", () => {
    const s = toSummary(row(), release);
    expect(s.ratingAvg).toBe(4.5);
    expect(s.downloadCount).toBe(42);
    expect(s.packageName).toBe("jp.yomumemo.app");
    expect(s.latest?.versionCode).toBe(12);
    expect(s.iconUrl).toContain("/app-media/app-1/icon.png");
  });

  it("埋め込みが配列で返っても(1:1でも)先頭を使う", () => {
    const s = toSummary(row({ category: [{ slug: "tools", name: "ツール" }] }), undefined);
    expect(s.category).toEqual({ slug: "tools", name: "ツール" });
    expect(s.latest).toBeNull();
  });

  it("カテゴリ未設定は null", () => {
    expect(toSummary(row({ category: null }), undefined).category).toBeNull();
  });

  it("詳細にはスクショURLと開発者名が入る", () => {
    const d = toDetail(row({ developer: [{ name: "T-tech" }] }), release);
    expect(d.developerName).toBe("T-tech");
    expect(d.screenshots).toEqual([
      "https://proj.supabase.co/storage/v1/object/public/app-media/app-1/s1.png",
      "https://proj.supabase.co/storage/v1/object/public/app-media/app-1/s2.png",
    ]);
  });

  it("開発者が取れなければ空文字", () => {
    expect(toDetail(row({ developer: null }), release).developerName).toBe("");
  });
});
