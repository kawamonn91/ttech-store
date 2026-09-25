import { createHmac, timingSafeEqual } from "node:crypto";
import { z } from "zod";
import { describeFindings, evaluatePolicy, FactsSchema, SignatureFactsSchema, type Finding, type PolicyVerdict } from "./policy";

/** APK検査ワークフロー(workers/scan/inspect.mjs)が送ってくる結果 */
export const ScanResultSchema = z.object({
  error: z.string().max(2000).optional(),
  packageName: z.string().max(200).optional(),
  versionName: z.string().max(100).optional(),
  versionCode: z.number().int().positive().optional(),
  minSdk: z.number().int().optional(),
  targetSdk: z.number().int().optional(),
  permissions: z.array(z.string().max(200)).max(500).default([]),
  apkSize: z.number().int().nonnegative().optional(),
  sha256: z.string().regex(/^[0-9a-f]{64}$/).optional(),
  signingCertSha256: z.string().regex(/^[0-9a-f]{64}$/).optional(),
  signerCount: z.number().int().optional(),
  virusTotal: z
    .object({
      status: z.enum(["clean", "flagged", "skipped", "pending"]),
      malicious: z.number().int().optional(),
      suspicious: z.number().int().optional(),
      permalink: z.string().url().optional(),
    })
    .optional(),
  /** 署名の方式・証明書の持ち主(デバッグ鍵の検出などに使う) */
  signature: SignatureFactsSchema.optional(),
  /** 審査(自動承認の判定)に使う事実。権限・コード内のAPI・同梱ファイルなど(workers/scan/facts.mjs) */
  facts: FactsSchema.optional(),
});
export type ScanResult = z.infer<typeof ScanResultSchema>;

/** リクエスト本文の HMAC-SHA256(hex)。ワークフロー側も同じ方式で署名する */
export function signBody(secret: string, rawBody: string): string {
  return createHmac("sha256", secret).update(rawBody).digest("hex");
}

export function verifySignature(secret: string, rawBody: string, signature: string | null): boolean {
  if (!signature || !/^[0-9a-f]+$/i.test(signature)) return false;
  const expected = Buffer.from(signBody(secret, rawBody), "hex");
  const given = Buffer.from(signature, "hex");
  return given.length === expected.length && timingSafeEqual(given, expected);
}

export interface ScanDecision {
  status: "scanned" | "rejected";
  reason?: string;
  /** app_releases に反映する列 */
  columns: Record<string, unknown>;
}

/**
 * 検査結果を見て、リリースを「承認待ち(scanned)」にするか「却下(rejected)」にするか決める。
 * 署名鍵の固定チェックは DB トリガー(承認時)でも行うが、ここでも早めに弾いて理由を残す。
 */
export function decideScan(
  result: ScanResult,
  app: { package_name: string; signing_cert_sha256: string | null },
): ScanDecision {
  // 却下理由は管理画面に出せるよう scan_result.error に残す
  const reject = (reason: string): ScanDecision => ({
    status: "rejected",
    reason,
    columns: { scan_result: { ...result, error: reason } },
  });

  if (result.error) return reject(result.error);
  if (!result.sha256 || !result.signingCertSha256 || !result.versionCode || !result.packageName) {
    return reject("APKから必要な情報を読み取れませんでした");
  }
  if (result.signerCount !== undefined && result.signerCount !== 1) {
    return reject("署名者が1つでないAPKは対応していません");
  }
  if (result.packageName !== app.package_name) {
    return reject(`パッケージ名がアプリの登録内容と一致しません (${result.packageName} ≠ ${app.package_name})`);
  }
  if (app.signing_cert_sha256 && app.signing_cert_sha256 !== result.signingCertSha256) {
    return reject("署名鍵が既存のアプリと一致しません");
  }
  if (result.virusTotal?.status === "flagged") {
    return reject("ウイルススキャンで検出されました");
  }
  return {
    status: "scanned",
    columns: {
      scan_result: result,
      version_name: result.versionName ?? String(result.versionCode),
      version_code: result.versionCode,
      apk_size: result.apkSize ?? null,
      sha256: result.sha256,
      signing_cert_sha256: result.signingCertSha256,
      min_sdk: result.minSdk ?? null,
      target_sdk: result.targetSdk ?? null,
      permissions: result.permissions,
    },
  };
}

// ---------------------------------------------------------------- 自動承認

/** 開発者の審査方式。auto: 自動審査を通れば自動で公開 / manual: 必ず運営が承認してから公開 */
export type ReviewMode = "auto" | "manual";

export interface ReleaseDecision {
  status: "scanned" | "rejected" | "published";
  reason?: string;
  columns: Record<string, unknown>;
  /** 自動審査の結果。署名・パッケージ名などの基本チェックで却下されたときは null */
  verdict: PolicyVerdict | null;
  /** 運営の承認なしに公開した(自動承認)か */
  autoApproved: boolean;
}

/** 判定の材料のうち、APKの外にあるもの */
export interface ReleaseContext {
  /** このアプリで、すでに公開(承認)されているリリースの最大の versionCode。まだ無ければ null */
  latestPublishedVersionCode: number | null;
}

function policyColumns(verdict: PolicyVerdict) {
  return { policy_verdict: verdict.decision, policy_version: verdict.version, policy_findings: verdict.findings };
}

/**
 * 検査結果を見て、リリースを「公開(自動承認)」「運営の承認待ち」「却下」のどれにするかを決める。
 *
 * 1. 署名・パッケージ名・ウイルススキャンなどの基本チェック([decideScan])。落ちたら却下。
 * 2. APKの自動審査([evaluatePolicy])。通信機能・端末データの破壊・隠れたコードなどの可能性を調べる。
 * 3. 開発者の審査方式が manual(運営自身のアプリ)なら、自動審査の結果は記録するだけで、承認待ちにする。
 *    auto(第三者の開発者)なら、疑いが1つも無いときだけ自動で公開し、疑いがあれば承認待ち、明らかに不正なら却下。
 */
export function decideRelease(
  result: ScanResult,
  app: { package_name: string; signing_cert_sha256: string | null },
  reviewMode: ReviewMode,
  context: ReleaseContext = { latestPublishedVersionCode: null },
): ReleaseDecision {
  const base = decideScan(result, app);
  if (base.status === "rejected") return { ...base, verdict: null, autoApproved: false };

  const verdict = evaluatePolicy({ facts: result.facts ?? null, signature: result.signature ?? null, packageName: app.package_name });

  // 公開済みより新しいバージョンでなければ、自動では公開しない(古い版への差し戻し・同じ版の差し替えを防ぐ)
  const latest = context.latestPublishedVersionCode;
  if (latest !== null && result.versionCode !== undefined && result.versionCode <= latest) {
    const finding: Finding = {
      code: "release.version-not-newer",
      severity: "review",
      category: "integrity",
      title: `公開中のバージョン(versionCode ${latest})より新しくありません`,
      detail: "versionCode が公開中のものと同じか古いAPKは、自動では公開しません。更新として公開するには versionCode を上げてビルドし直してください。",
      evidence: [`versionCode=${result.versionCode}`],
    };
    verdict.findings.push(finding);
    if (verdict.decision === "auto_approve") verdict.decision = "needs_review";
  }

  const columns = { ...base.columns, ...policyColumns(verdict), auto_approved: false };

  if (reviewMode === "manual") {
    return { status: "scanned", columns, verdict, autoApproved: false };
  }
  if (verdict.decision === "reject") {
    const reason = verdict.findings.filter((f) => f.severity === "block").map((f) => f.title).join(" / ");
    // 却下したリリースの versionCode は記録しない(直したAPKを同じ versionCode で出し直せるように)
    return {
      status: "rejected",
      reason,
      columns: { scan_result: { ...result, error: reason }, ...policyColumns(verdict), auto_approved: false },
      verdict,
      autoApproved: false,
    };
  }
  if (verdict.decision === "needs_review") {
    return { status: "scanned", columns, verdict, autoApproved: false };
  }
  return { status: "published", columns: { ...columns, auto_approved: true }, verdict, autoApproved: true };
}

export { describeFindings };
