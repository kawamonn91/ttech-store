import { CreateAppBody, DEVELOPER_LIMITS, firstIssue, nameProblem, packageProblem, requireDeveloper, slugProblem, suggestSlug } from "@/lib/developer";
import { internalError, jsonError, jsonOk } from "@/lib/http";

/**
 * 新しいアプリを登録する(まだ非公開の下書き)。APKをアップロードして自動審査を通ると、公開される。
 * パッケージ名は、OS・他社・運営のものが予約されていて使えない。1人あたりの本数にも上限がある。
 */
export async function POST(request: Request) {
  const auth = await requireDeveloper();
  if ("response" in auth) return auth.response;
  const { user, svc } = auth;

  const body = CreateAppBody.safeParse(await request.json().catch(() => null));
  if (!body.success) return jsonError(firstIssue(body.error), 400);
  const input = body.data;

  const problem = nameProblem("app", input.name) ?? packageProblem(input.packageName) ?? (input.slug ? slugProblem(input.slug) : null);
  if (problem) return jsonError(problem, 400);

  try {
    const { count } = await svc.from("apps").select("id", { count: "exact", head: true }).eq("developer_id", user.id);
    if ((count ?? 0) >= DEVELOPER_LIMITS.maxApps) {
      return jsonError(`登録できるアプリは1人${DEVELOPER_LIMITS.maxApps}本までです。使わないアプリは運営にご連絡ください`, 403, { code: "app_limit" });
    }
    if (input.categoryId) {
      const { data: category } = await svc.from("categories").select("id").eq("id", input.categoryId).maybeSingle();
      if (!category) return jsonError("カテゴリが見つかりません", 400);
    }

    const slugGiven = !!input.slug;
    let slug = input.slug ?? suggestSlug(input.packageName);
    if (slugProblem(slug)) slug = `${slug}-app`;

    // URL用の名前が自動で決めたものなら、すでに使われていても、ランダムな接尾辞を付けて1回だけやり直す
    for (let attempt = 0; attempt < 2; attempt++) {
      const { data, error } = await svc
        .from("apps")
        .insert({
          slug,
          package_name: input.packageName,
          developer_id: user.id,
          name: input.name,
          short_desc: input.shortDesc,
          description: input.description,
          category_id: input.categoryId ?? null,
          status: "draft",
        })
        .select("id, slug")
        .maybeSingle();
      if (!error) return jsonOk({ id: (data as { id: string }).id, slug });

      const message = `${error.message}`;
      const duplicate = (error as { code?: string }).code === "23505" || /duplicate|unique/i.test(message);
      if (!duplicate) throw error;
      if (/package_name/.test(message)) return jsonError("このパッケージ名は、すでに登録されています", 409);
      if (slugGiven || attempt === 1) return jsonError("このURL用の名前は、すでに使われています。別の名前にしてください", 409);
      slug = `${slug.slice(0, 50)}-${Math.random().toString(36).slice(2, 6)}`;
    }
    return jsonError("アプリを登録できませんでした", 500);
  } catch (e) {
    return internalError(e);
  }
}
