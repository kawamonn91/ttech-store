import { createHmac, timingSafeEqual } from "node:crypto";
import { z } from "zod";

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
