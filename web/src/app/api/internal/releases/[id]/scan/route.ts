import { env } from "@/lib/env";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { decideScan, ScanResultSchema, verifySignature } from "@/lib/scan";
import { serviceClient } from "@/lib/supabase";

/**
 * APK検査ワークフロー(GitHub Actions)からの結果を受け取る。
 * 認証は本文の HMAC 署名(x-signature)。署名が正しいものだけを受け付ける。
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
    const supabase = serviceClient();
    const { data: release } = await supabase
      .from("app_releases")
      .select("id, status, app:apps(package_name, signing_cert_sha256)")
      .eq("id", id)
      .maybeSingle();
    if (!release) return jsonError("リリースが見つかりません", 404);
    // 公開済み・承認済みのリリースは検査結果で書き換えさせない
    if (!["uploaded", "scanned", "rejected"].includes(release.status)) return jsonError("このリリースは更新できません", 409);

    const app = Array.isArray(release.app) ? release.app[0] : release.app;
    const decision = decideScan(parsed.data, app);
    const { error } = await supabase
      .from("app_releases")
      .update({ ...decision.columns, status: decision.status })
      .eq("id", id);
    if (error) throw error;

    return jsonOk({ status: decision.status, reason: decision.reason ?? null });
  } catch (e) {
    return internalError(e);
  }
}
