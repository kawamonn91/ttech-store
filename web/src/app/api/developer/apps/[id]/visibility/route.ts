import { z } from "zod";
import { loadOwnApp, requireDeveloper } from "@/lib/developer";
import { internalError, jsonError, jsonOk } from "@/lib/http";

const Body = z.object({ visible: z.boolean() });

/**
 * 自分のアプリの公開・非公開を切り替える。公開できるのは、公開済みのリリースがあるアプリだけ。
 * 非公開にしても、すでにインストールした人のアプリはそのまま動く(ストアの一覧・配信から外れる)。
 * 運営に停止されたアプリは、ここからは変えられない。
 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireDeveloper();
  if ("response" in auth) return auth.response;
  const { user, svc } = auth;
  const { id } = await params;

  const body = Body.safeParse(await request.json().catch(() => null));
  if (!body.success) return jsonError("リクエストが不正です", 400);

  try {
    const app = await loadOwnApp(svc, id, user.id);
    if (!app) return jsonError("アプリが見つかりません", 404);
    if (app.status === "suspended") return jsonError("このアプリは停止されています。運営にお問い合わせください", 403);

    if (body.data.visible) {
      const { data: published } = await svc.from("app_releases").select("id").eq("app_id", id).eq("status", "published").limit(1);
      if (!Array.isArray(published) || published.length === 0) {
        return jsonError("公開できるリリースがまだありません。APKをアップロードして、審査を通してください", 409);
      }
    }
    const { error } = await svc.from("apps").update({ status: body.data.visible ? "published" : "draft" }).eq("id", id).eq("developer_id", user.id);
    if (error) throw error;
    return jsonOk({ ok: true, status: body.data.visible ? "published" : "draft" });
  } catch (e) {
    return internalError(e);
  }
}
