import { z } from "zod";
import { requireAdmin } from "@/lib/admin";
import { DEVELOPER_LIMITS } from "@/lib/app-rules";
import { isUuid } from "@/lib/db";
import { internalError, jsonError, jsonOk, parseJsonBody } from "@/lib/http";
import { apkKey, presignUpload, UPLOAD_URL_TTL_SECONDS, uploadKey } from "@/lib/r2";
import { serviceClient } from "@/lib/supabase";

const Body = z.object({ releaseNotes: z.string().max(4000).default("") });
const MAX_APK_BYTES = DEVELOPER_LIMITS.maxApkBytes;

/** 新しいリリースを作り、APK を R2 のアップロード用キー(uploadKey)へ直接アップロードするための署名付きURLを返す */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireAdmin(request);
  if ("response" in auth) return auth.response;

  const { id: appId } = await params;
  if (!isUuid(appId)) return jsonError("リクエストが不正です", 400);

  const body = await parseJsonBody(request, Body);
  if (!body.success) return jsonError("リクエストが不正です", 400);

  try {
    const supabase = serviceClient();
    const { data: app } = await supabase.from("apps").select("id").eq("id", appId).maybeSingle();
    if (!app) return jsonError("アプリが見つかりません", 404);

    const releaseId = crypto.randomUUID();
    const key = apkKey(appId, releaseId);
    const { error } = await supabase.from("app_releases").insert({
      id: releaseId,
      app_id: appId,
      apk_key: key,
      release_notes: body.data.releaseNotes,
      status: "uploaded",
    });
    if (error) throw error;

    return jsonOk({
      releaseId,
      uploadUrl: await presignUpload(uploadKey(appId, releaseId)),
      maxBytes: MAX_APK_BYTES,
      expiresInSeconds: UPLOAD_URL_TTL_SECONDS,
    });
  } catch (e) {
    return internalError(e);
  }
}
