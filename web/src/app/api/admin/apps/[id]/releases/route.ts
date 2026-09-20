import { z } from "zod";
import { requireAdmin } from "@/lib/admin";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { apkKey, presignUpload, UPLOAD_URL_TTL_SECONDS } from "@/lib/r2";
import { serviceClient } from "@/lib/supabase";

const Body = z.object({ releaseNotes: z.string().max(4000).default("") });
const MAX_APK_BYTES = 500 * 1024 * 1024;

/** 新しいリリースを作り、APK を R2 へ直接アップロードするための署名付きURLを返す */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireAdmin();
  if ("response" in auth) return auth.response;

  const { id: appId } = await params;
  const body = Body.safeParse(await request.json().catch(() => ({})));
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
      uploadUrl: await presignUpload(key),
      maxBytes: MAX_APK_BYTES,
      expiresInSeconds: UPLOAD_URL_TTL_SECONDS,
    });
  } catch (e) {
    return internalError(e);
  }
}
