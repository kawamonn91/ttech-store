import { z } from "zod";
import { requireAdmin } from "@/lib/admin";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { serviceClient } from "@/lib/supabase";

const Body = z.object({ action: z.enum(["publish", "reject", "unpublish"]) });

/**
 * リリースの承認・却下・公開停止。
 * 公開時は DB トリガーが署名鍵の固定/一致を検証し、違反すれば例外になる(その内容をそのまま返す)。
 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireAdmin();
  if ("response" in auth) return auth.response;

  const { id } = await params;
  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return jsonError("リクエストが不正です", 400);

  try {
    const supabase = serviceClient();
    const { data: release } = await supabase.from("app_releases").select("id, status, app_id, apps(status)").eq("id", id).maybeSingle();
    if (!release) return jsonError("リリースが見つかりません", 404);

    const { action } = body.data;
    let update: Record<string, unknown>;
    if (action === "publish") {
      if (!["scanned", "approved"].includes(release.status)) {
        return jsonError("検査が完了したリリースだけ公開できます", 409);
      }
      update = { status: "published", reviewed_by: auth.admin.id };
    } else if (action === "reject") {
      if (release.status === "published") return jsonError("公開中のリリースは先に公開停止してください", 409);
      update = { status: "rejected", reviewed_by: auth.admin.id };
    } else {
      if (release.status !== "published") return jsonError("公開中のリリースではありません", 409);
      update = { status: "approved" };
    }

    const { error } = await supabase.from("app_releases").update(update).eq("id", id);
    if (error) {
      // トリガーの例外(署名鍵不一致など)は利用者に分かる文言で返す
      return jsonError(error.message, 422);
    }

    // リリースを公開したら、アプリ自体もまだ非公開なら合わせて公開する(マイページ・管理コンソールどちらの
    // 承認操作も、これ1回で完結させるため)
    const app = Array.isArray(release.apps) ? release.apps[0] : release.apps;
    if (action === "publish" && app && app.status !== "published") {
      const { error: appError } = await supabase.from("apps").update({ status: "published" }).eq("id", release.app_id);
      if (appError) return jsonError(appError.message, 422);
    }

    return jsonOk({ ok: true });
  } catch (e) {
    return internalError(e);
  }
}
