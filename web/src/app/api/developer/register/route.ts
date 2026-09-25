import { firstIssue, nameProblem, RegisterBody, requireUser } from "@/lib/developer";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { sendAdminMail } from "@/lib/mail";

/**
 * 開発者としての会員登録。誰でも登録でき、承認を待たずにすぐ使える
 * (アプリは、APKの自動審査を通ったものだけが自動で公開され、疑いがあれば運営が確認するため)。
 *
 * 条件: ログイン済み・メールアドレスの確認済み・開発者向け規約への同意・BANされていない・停止中でない。
 * 開発者名は、大文字小文字を区別せず重複できず、運営・他社・公的機関を思わせる語は使えない。
 */
export async function POST(request: Request) {
  const auth = await requireUser();
  if ("response" in auth) return auth.response;
  const { user, svc } = auth;

  const body = RegisterBody.safeParse(await request.json().catch(() => null));
  if (!body.success) return jsonError(firstIssue(body.error), 400);
  if (!user.emailConfirmed) {
    return jsonError("メールアドレスの確認が済んでいません。届いた確認メールのリンクを開いてから、もう一度お試しください", 403, { code: "email_unconfirmed" });
  }
  const problem = nameProblem("developer", body.data.name);
  if (problem) return jsonError(problem, 400);
  const contactEmail = body.data.contactEmail ?? user.email;
  if (!contactEmail) return jsonError("連絡先のメールアドレスを入力してください", 400);

  try {
    const { data: existing } = await svc.from("developers").select("status").eq("user_id", user.id).maybeSingle();
    if (existing?.status === "suspended") return jsonError("この開発者アカウントは停止されています。運営にお問い合わせください", 403, { code: "suspended" });
    if (existing?.status === "approved") return jsonOk({ ok: true, already: true });

    const row = {
      name: body.data.name,
      contact_email: contactEmail,
      website: body.data.website ?? null,
      status: "approved",
      review_mode: "auto",
      terms_accepted_at: new Date().toISOString(),
    };
    // 以前の申請フォームで「承認待ち(pending)」になっている人は、そのまま承認済みにする
    const { error } = existing
      ? await svc.from("developers").update(row).eq("user_id", user.id)
      : await svc.from("developers").insert({ user_id: user.id, ...row });
    if (error) {
      if ((error as { code?: string }).code === "23505" || /developers_name/i.test(error.message)) {
        return jsonError("その開発者名は、すでに使われています。別の名前にしてください", 409);
      }
      throw error;
    }

    // 運営への知らせ(参考)。送れなくても、登録は成功させる
    await sendAdminMail({
      subject: `[T-tech Store] 新しい開発者が登録しました(${body.data.name.slice(0, 40)})`,
      text: `開発者として新しく登録がありました。承認は不要で、すぐに利用できます。\n\n開発者名: ${body.data.name}\n連絡先: ${contactEmail}\nウェブサイト: ${body.data.website ?? "(なし)"}\n\n問題があれば、管理コンソールから停止できます。\n\n(このメールは T-tech Store から自動で送られています)`,
    }).catch((e) => console.warn("開発者の登録の通知を送れませんでした:", e instanceof Error ? e.message : e));

    return jsonOk({ ok: true });
  } catch (e) {
    return internalError(e);
  }
}
