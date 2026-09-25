import { env } from "@/lib/env";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { reviewScannedRelease } from "@/lib/release-review";
import { ScanResultSchema, verifySignature } from "@/lib/scan";
import { serviceClient } from "@/lib/supabase";

/**
 * APK検査ワークフロー(GitHub Actions)からの結果を受け取る。
 * 認証は本文の HMAC 署名(x-signature)。署名が正しいものだけを受け付ける。
 *
 * 結果に応じて、リリースを「公開(第三者の開発者の自動審査を通ったもの)」「運営の承認待ち」「却下」のどれかにする。
 * 判定の詳細は lib/release-review.ts。
 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const raw = await request.text();
  if (!verifySignature(env.scanWebhookSecret(), raw, request.headers.get("x-signature"))) {
    return jsonError("unauthorized", 401);
  }

  let json: unknown;
  try {
    json = JSON.parse(raw);
  } catch {
    return jsonError("リクエストが不正です", 400);
  }
  const parsed = ScanResultSchema.safeParse(json);
  if (!parsed.success) return jsonError("検査結果の形式が不正です", 400);

  try {
    const outcome = await reviewScannedRelease(serviceClient(), id, parsed.data);
    if (outcome.kind === "not_found") return jsonError("リリースが見つかりません", 404);
    if (outcome.kind === "conflict") return jsonError("このリリースは更新できません", 409);
    return jsonOk({ status: outcome.status, reason: outcome.reason, autoApproved: outcome.autoApproved, decision: outcome.decision });
  } catch (e) {
    return internalError(e);
  }
}
