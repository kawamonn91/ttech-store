import { firstIssue, loadOwnApp, nameProblem, requireDeveloper, UpdateAppBody } from "@/lib/developer";
import { internalError, jsonError, jsonOk } from "@/lib/http";

/** 自分のアプリの掲載内容(名前・説明・カテゴリ)を更新する。パッケージ名・状態・アイコン等は、この経路では変えられない */
export async function PATCH(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireDeveloper();
  if ("response" in auth) return auth.response;
  const { user, svc } = auth;
  const { id } = await params;

  const body = UpdateAppBody.safeParse(await request.json().catch(() => null));
  if (!body.success) return jsonError(firstIssue(body.error), 400);
  const input = body.data;
  if (input.name !== undefined) {
    const problem = nameProblem("app", input.name);
    if (problem) return jsonError(problem, 400);
  }

  try {
    const app = await loadOwnApp(svc, id, user.id);
    if (!app) return jsonError("アプリが見つかりません", 404);
    if (app.status === "suspended") return jsonError("このアプリは停止されています。運営にお問い合わせください", 403);
    if (input.categoryId) {
      const { data: category } = await svc.from("categories").select("id").eq("id", input.categoryId).maybeSingle();
      if (!category) return jsonError("カテゴリが見つかりません", 400);
    }

    const patch: Record<string, unknown> = {};
    if (input.name !== undefined) patch.name = input.name;
    if (input.shortDesc !== undefined) patch.short_desc = input.shortDesc;
    if (input.description !== undefined) patch.description = input.description;
    if (input.categoryId !== undefined) patch.category_id = input.categoryId;
    const { error } = await svc.from("apps").update(patch).eq("id", id).eq("developer_id", user.id);
    if (error) throw error;
    return jsonOk({ ok: true });
  } catch (e) {
    return internalError(e);
  }
}
