import { z } from "zod";
import { requireAdmin } from "@/lib/admin";
import { DownloadError, issueDownload } from "@/lib/download";
import { internalError, jsonError, jsonOk } from "@/lib/http";

const Body = z.object({ deviceId: z.string().min(8).max(100) });
const UuidParam = z.string().uuid();

/**
 * 管理者専用アプリ(admin_only)だけは、管理者としてログインした呼び出し(Authorization: Bearer)でなければ発行しない。
 * ストアアプリ向け: 公開中リリースの署名付きダウンロードURLと、検証用の
 * SHA-256・署名証明書ハッシュを返す。呼び出しごとにDL数を(端末単位24時間で重複除外して)記録する。
 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!UuidParam.safeParse(id).success) return jsonError("リリースが見つかりません", 404);

  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return jsonError("リクエストが不正です", 400);

  try {
    return jsonOk(await issueDownload(id, body.data.deviceId, null, async () => !("response" in (await requireAdmin(request)))));
  } catch (e) {
    if (e instanceof DownloadError) return jsonError(e.message, e.status);
    return internalError(e);
  }
}
