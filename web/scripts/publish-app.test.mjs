import { describe, expect, it } from "vitest";
import { decideScan } from "./publish-app.mjs";

// web/src/lib/scan.test.ts と同じケースを、CLI側の実装(publish-app.mjs)に対しても確認する。
// ロジックを変えるときは両方のテストを通すこと。

const SHA = "a".repeat(64);
const CERT = "b".repeat(64);
const app = { package_name: "com.example.app", signing_cert_sha256: null };

const good = (over = {}) => ({
  packageName: "com.example.app",
  versionName: "1.2.0",
  versionCode: 12,
  minSdk: 26,
  targetSdk: 36,
  permissions: ["android.permission.INTERNET"],
  apkSize: 1234,
  sha256: SHA,
  signingCertSha256: CERT,
  signerCount: 1,
  ...over,
});

describe("decideScan (CLI版)", () => {
  it("正常なAPKは承認待ちになる", () => {
    const d = decideScan(good(), app);
    expect(d.status).toBe("scanned");
    expect(d.columns).toMatchObject({ version_code: 12, sha256: SHA, signing_cert_sha256: CERT });
  });

  it("パッケージ名が違えば却下する", () => {
    const d = decideScan(good({ packageName: "com.evil.app" }), app);
    expect(d.status).toBe("rejected");
    expect(d.reason).toContain("パッケージ名");
  });

  it("署名鍵が固定済みの鍵と違えば却下する", () => {
    const d = decideScan(good(), { ...app, signing_cert_sha256: "c".repeat(64) });
    expect(d.status).toBe("rejected");
  });

  it("同じ署名鍵なら承認待ち", () => {
    expect(decideScan(good(), { ...app, signing_cert_sha256: CERT }).status).toBe("scanned");
  });

  it("ウイルススキャンで検出されたら却下する", () => {
    expect(decideScan(good({ virusTotal: { status: "flagged" } }), app).status).toBe("rejected");
  });

  it("検査エラーは却下する", () => {
    expect(decideScan({ error: "boom" }, app).status).toBe("rejected");
  });
});
