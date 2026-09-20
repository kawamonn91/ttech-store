import { describe, expect, it } from "vitest";
import { decideScan, ScanResultSchema, signBody, verifySignature, type ScanResult } from "./scan";

const SHA = "a".repeat(64);
const CERT = "b".repeat(64);
const app = { package_name: "com.example.app", signing_cert_sha256: null as string | null };

const good = (over: Partial<ScanResult> = {}): ScanResult =>
  ScanResultSchema.parse({
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

describe("verifySignature", () => {
  const secret = "s3cret";
  const body = '{"a":1}';

  it("正しい署名を受け付ける", () => {
    expect(verifySignature(secret, body, signBody(secret, body))).toBe(true);
  });
  it("本文が改ざんされていたら拒否する", () => {
    expect(verifySignature(secret, '{"a":2}', signBody(secret, body))).toBe(false);
  });
  it("別のシークレットの署名は拒否する", () => {
    expect(verifySignature(secret, body, signBody("other", body))).toBe(false);
  });
  it("署名なし・不正な形式は拒否する", () => {
    expect(verifySignature(secret, body, null)).toBe(false);
    expect(verifySignature(secret, body, "zzzz")).toBe(false);
    expect(verifySignature(secret, body, "")).toBe(false);
  });
});

describe("decideScan", () => {
  it("正常なAPKは承認待ちになり、列が埋まる", () => {
    const d = decideScan(good(), app);
    expect(d.status).toBe("scanned");
    expect(d.columns).toMatchObject({ version_code: 12, version_name: "1.2.0", sha256: SHA, signing_cert_sha256: CERT, min_sdk: 26 });
  });

  it("パッケージ名が登録と違えば却下し、理由を残す", () => {
    const d = decideScan(good({ packageName: "com.evil.app" }), app);
    expect(d.status).toBe("rejected");
    expect(d.reason).toContain("パッケージ名");
    expect((d.columns.scan_result as { error: string }).error).toBe(d.reason);
  });

  it("署名鍵が固定済みの鍵と違えば却下する", () => {
    const d = decideScan(good(), { ...app, signing_cert_sha256: "c".repeat(64) });
    expect(d.status).toBe("rejected");
    expect(d.reason).toContain("署名鍵");
  });

  it("署名鍵が同じなら承認待ち", () => {
    expect(decideScan(good(), { ...app, signing_cert_sha256: CERT }).status).toBe("scanned");
  });

  it("署名者が複数なら却下する", () => {
    expect(decideScan(good({ signerCount: 2 }), app).status).toBe("rejected");
  });

  it("ウイルススキャンで検出されたら却下する", () => {
    expect(decideScan(good({ virusTotal: { status: "flagged", malicious: 3 } }), app).status).toBe("rejected");
  });

  it("ウイルススキャンを省略(skipped)しても止めない", () => {
    expect(decideScan(good({ virusTotal: { status: "skipped" } }), app).status).toBe("scanned");
  });

  it("検査側がエラーを返したら却下する", () => {
    const d = decideScan(ScanResultSchema.parse({ error: "apksigner failed" }), app);
    expect(d.status).toBe("rejected");
    expect(d.reason).toBe("apksigner failed");
  });

  it("必要な情報が欠けていたら却下する", () => {
    expect(decideScan(good({ sha256: undefined }), app).status).toBe("rejected");
  });
});
