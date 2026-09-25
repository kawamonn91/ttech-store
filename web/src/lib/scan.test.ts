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

// ---------------------------------------------------------------- decideRelease(自動承認)
import { decideRelease } from "./scan";
import offlineFacts from "./testing/fixtures/facts-offline-app.json";
import runTrackerFacts from "./testing/fixtures/facts-run-tracker.json";

describe("decideRelease", () => {
  const offlineApp = { package_name: "com.ttech.weightlog", signing_cert_sha256: null as string | null };
  const sig = { v1: false, v2: true, v3: true, subject: "CN=Example Dev" };
  const scan = (over: Record<string, unknown> = {}) =>
    ScanResultSchema.parse({
      packageName: "com.ttech.weightlog", versionName: "1.0", versionCode: 5, minSdk: 26, targetSdk: 36, permissions: [],
      apkSize: 1000, sha256: SHA, signingCertSha256: CERT, signerCount: 1, signature: sig, facts: offlineFacts, ...over,
    });

  it("自動審査を通った第三者のアプリ(通信なしの実APKの解析結果)は、運営の承認なしに公開される", () => {
    const d = decideRelease(scan(), offlineApp, "auto");
    expect(d.status).toBe("published");
    expect(d.autoApproved).toBe(true);
    expect(d.verdict?.decision).toBe("auto_approve");
    expect(d.columns).toMatchObject({ auto_approved: true, policy_verdict: "auto_approve", version_code: 5, sha256: SHA, signing_cert_sha256: CERT });
    expect(d.columns.policy_findings).toEqual([]);
  });

  it("通信につながる要素(INTERNET権限など)があれば、公開せず運営の承認待ちにする。理由が記録される", () => {
    const d = decideRelease(scan({ facts: runTrackerFacts, packageName: "com.ttech.runtracker" }), { ...offlineApp, package_name: "com.ttech.runtracker" }, "auto");
    expect(d.status).toBe("scanned");
    expect(d.autoApproved).toBe(false);
    expect(d.verdict?.decision).toBe("needs_review");
    expect(d.columns.auto_approved).toBe(false);
    const codes = (d.columns.policy_findings as { code: string }[]).map((f) => f.code);
    expect(codes).toEqual(expect.arrayContaining(["net.permission", "net.api"]));
  });

  it("解析結果(facts)が無ければ、自動承認しない", () => {
    const d = decideRelease(scan({ facts: undefined }), offlineApp, "auto");
    expect(d.status).toBe("scanned");
    expect(d.verdict?.findings.map((f) => f.code)).toEqual(["analysis.missing"]);
  });

  it("運営自身のアプリ(manual)は、自動審査を通っても、これまでどおり承認待ちにする(結果は記録する)", () => {
    const d = decideRelease(scan(), offlineApp, "manual");
    expect(d.status).toBe("scanned");
    expect(d.autoApproved).toBe(false);
    expect(d.columns).toMatchObject({ policy_verdict: "auto_approve", auto_approved: false });
  });

  it("デバッグ署名などの明らかな不正は、その場で却下する。versionCode は記録しない(出し直せるように)", () => {
    const d = decideRelease(scan({ signature: { ...sig, subject: "C=US, O=Android, CN=Android Debug" } }), offlineApp, "auto");
    expect(d.status).toBe("rejected");
    expect(d.reason).toContain("デバッグ用の署名です");
    expect(d.verdict?.decision).toBe("reject");
    expect(d.columns).not.toHaveProperty("version_code");
    expect(d.columns).toMatchObject({ policy_verdict: "reject", auto_approved: false });
    expect((d.columns.scan_result as { error: string }).error).toContain("デバッグ用の署名です");
  });

  it("署名・パッケージ名などの基本チェックで却下されたときは、自動審査を行わない", () => {
    const d = decideRelease(scan({ packageName: "com.other.app" }), offlineApp, "auto");
    expect(d.status).toBe("rejected");
    expect(d.reason).toContain("パッケージ名");
    expect(d.verdict).toBeNull();
    const locked = decideRelease(scan(), { ...offlineApp, signing_cert_sha256: "c".repeat(64) }, "auto");
    expect(locked.status).toBe("rejected");
    expect(locked.reason).toContain("署名鍵");
    const vt = decideRelease(scan({ virusTotal: { status: "flagged", malicious: 9 } }), offlineApp, "auto");
    expect(vt.status).toBe("rejected");
  });

  it("公開中と同じ・古い versionCode は、自動では公開せず、承認待ちにする。新しければ公開する", () => {
    const same = decideRelease(scan({ versionCode: 12 }), offlineApp, "auto", { latestPublishedVersionCode: 12 });
    expect(same.status).toBe("scanned");
    expect(same.verdict?.findings.map((f) => f.code)).toContain("release.version-not-newer");
    const older = decideRelease(scan({ versionCode: 3 }), offlineApp, "auto", { latestPublishedVersionCode: 12 });
    expect(older.status).toBe("scanned");
    const newer = decideRelease(scan({ versionCode: 13 }), offlineApp, "auto", { latestPublishedVersionCode: 12 });
    expect(newer.status).toBe("published");
    const first = decideRelease(scan({ versionCode: 1 }), offlineApp, "auto", { latestPublishedVersionCode: null });
    expect(first.status).toBe("published");
  });
});
