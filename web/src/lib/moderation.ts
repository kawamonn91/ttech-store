import type { SupabaseClient } from "@supabase/supabase-js";
import type { AdminUser } from "./supabase";

/** 管理操作の失敗。message は管理者にそのまま見せてよい文言 */
export class ModerationError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

/** Supabase Auth の ban_duration(約100年 = 実質、解除するまで永久) */
const BAN_FOREVER = "876000h";

/** 監査ログに残す。失敗しても、本体の操作は取り消さない(ログだけ欠けるのを許容し、原因は console に残す) */
export async function audit(
  supabase: SupabaseClient,
  admin: AdminUser,
  action: string,
  targetType: string,
  targetId: string | null,
  detail: Record<string, unknown> = {},
): Promise<void> {
  const { error } = await supabase
    .from("admin_audit_log")
    .insert({ admin_id: admin.id, action, target_type: targetType, target_id: targetId, detail });
  if (error) console.error("admin_audit_log insert failed", error);
}

/**
 * 利用者をBANする。
 *  1. profiles.banned_at を立てる → RLSにより、今持っているトークンでも読み書きできなくなる(即時)
 *  2. Auth 側も BAN → 新しいログイン・トークン更新ができなくなる
 * 自分自身と、ほかの管理者はBANできない(誤操作で管理者が全員締め出されるのを防ぐ)。
 */
export async function banUser(supabase: SupabaseClient, admin: AdminUser, targetId: string, reason: string): Promise<void> {
  if (targetId === admin.id) throw new ModerationError("自分自身はBANできません", 409);

  const { data: target } = await supabase.from("profiles").select("id, role, banned_at").eq("id", targetId).maybeSingle();
  if (!target) throw new ModerationError("ユーザーが見つかりません", 404);
  if (target.role === "admin") throw new ModerationError("管理者はBANできません", 409);

  const { error } = await supabase
    .from("profiles")
    .update({ banned_at: new Date().toISOString(), ban_reason: reason || null })
    .eq("id", targetId);
  if (error) throw new ModerationError(`BANに失敗しました: ${error.message}`, 500);

  const { error: authError } = await supabase.auth.admin.updateUserById(targetId, { ban_duration: BAN_FOREVER });
  if (authError) {
    // 利用者側のRLS制限はすでに効いている。ログイン側の制限だけ失敗したことを伝える
    throw new ModerationError(`データへのアクセスは止めましたが、ログインの停止に失敗しました: ${authError.message}`, 502);
  }
  await audit(supabase, admin, "user.ban", "user", targetId, { reason });
}

export async function unbanUser(supabase: SupabaseClient, admin: AdminUser, targetId: string): Promise<void> {
  const { data: target } = await supabase.from("profiles").select("id, banned_at").eq("id", targetId).maybeSingle();
  if (!target) throw new ModerationError("ユーザーが見つかりません", 404);

  const { error: authError } = await supabase.auth.admin.updateUserById(targetId, { ban_duration: "none" });
  if (authError) throw new ModerationError(`ログインの停止を解除できませんでした: ${authError.message}`, 502);

  const { error } = await supabase.from("profiles").update({ banned_at: null, ban_reason: null }).eq("id", targetId);
  if (error) throw new ModerationError(`BAN解除に失敗しました: ${error.message}`, 500);
  await audit(supabase, admin, "user.unban", "user", targetId);
}

/** ひとこと日記の投稿を削除する(添付写真も消す)。削除した本文の一部は監査ログに残す */
export async function deleteDiaryEntry(supabase: SupabaseClient, admin: AdminUser, entryId: string): Promise<void> {
  const { data: entry } = await supabase
    .from("diary_entries")
    .select("id, user_id, body, photo_path")
    .eq("id", entryId)
    .maybeSingle();
  if (!entry) throw new ModerationError("投稿が見つかりません", 404);

  if (entry.photo_path) {
    const { error: storageError } = await supabase.storage.from("diary-media").remove([entry.photo_path]);
    if (storageError) throw new ModerationError(`写真を削除できませんでした: ${storageError.message}`, 502);
  }
  const { error } = await supabase.from("diary_entries").delete().eq("id", entryId);
  if (error) throw new ModerationError(`投稿を削除できませんでした: ${error.message}`, 500);

  await audit(supabase, admin, "diary_entry.delete", "diary_entry", entryId, {
    user_id: entry.user_id,
    body: String(entry.body ?? "").slice(0, 200),
    had_photo: Boolean(entry.photo_path),
  });
}

export type ReportKind = "diary" | "review";
export type ReportAction = "dismiss" | "delete_content" | "ban_author";

/**
 * 報告の処理。
 *  - dismiss: 問題なしとして閉じる
 *  - delete_content: 日記の投稿を削除 / レビューを非表示にして閉じる
 *  - ban_author: 投稿者をBANして閉じる(投稿は残す。削除したければ delete_content を先に)
 */
export async function resolveReport(
  supabase: SupabaseClient,
  admin: AdminUser,
  kind: ReportKind,
  reportId: string,
  action: ReportAction,
  banReason = "",
): Promise<void> {
  if (kind === "diary") {
    const { data: report } = await supabase
      .from("diary_reports")
      .select("id, entry_id, reported_user_id, status")
      .eq("id", reportId)
      .maybeSingle();
    if (!report) throw new ModerationError("報告が見つかりません", 404);

    if (action === "delete_content") {
      // 投稿が既に削除されていても、報告は処理済みにできる(=本人が消した場合など)
      const { data: entry } = await supabase.from("diary_entries").select("id").eq("id", report.entry_id).maybeSingle();
      if (entry) await deleteDiaryEntry(supabase, admin, report.entry_id);
    } else if (action === "ban_author") {
      if (!report.reported_user_id) throw new ModerationError("投稿者のアカウントは既に削除されています", 409);
      await banUser(supabase, admin, report.reported_user_id, banReason || "報告を受けての対応");
    }

    const status = action === "dismiss" ? "dismissed" : "actioned";
    const { error } = await supabase.from("diary_reports").update({ status }).eq("id", reportId);
    if (error) throw new ModerationError(`報告を更新できませんでした: ${error.message}`, 500);
    await audit(supabase, admin, `report.diary.${action}`, "diary_report", reportId, { status });
    return;
  }

  const { data: report } = await supabase.from("review_reports").select("id, review_id").eq("id", reportId).maybeSingle();
  if (!report) throw new ModerationError("通報が見つかりません", 404);

  if (action === "delete_content") {
    const { error } = await supabase.from("reviews").update({ status: "hidden" }).eq("id", report.review_id);
    if (error) throw new ModerationError(`レビューを非表示にできませんでした: ${error.message}`, 500);
  } else if (action === "ban_author") {
    const { data: review } = await supabase.from("reviews").select("user_id").eq("id", report.review_id).maybeSingle();
    if (!review) throw new ModerationError("レビューが見つかりません", 404);
    await banUser(supabase, admin, review.user_id, banReason || "レビューの通報を受けての対応");
  }

  const { error } = await supabase.from("review_reports").update({ resolved: true }).eq("id", reportId);
  if (error) throw new ModerationError(`通報を更新できませんでした: ${error.message}`, 500);
  await audit(supabase, admin, `report.review.${action}`, "review_report", reportId);
}

export async function setReviewStatus(
  supabase: SupabaseClient,
  admin: AdminUser,
  reviewId: string,
  status: "visible" | "hidden",
): Promise<void> {
  const { data: review } = await supabase.from("reviews").select("id").eq("id", reviewId).maybeSingle();
  if (!review) throw new ModerationError("レビューが見つかりません", 404);
  const { error } = await supabase.from("reviews").update({ status }).eq("id", reviewId);
  if (error) throw new ModerationError(`更新できませんでした: ${error.message}`, 500);
  await audit(supabase, admin, `review.${status}`, "review", reviewId);
}

export async function setDeveloperStatus(
  supabase: SupabaseClient,
  admin: AdminUser,
  userId: string,
  status: "approved" | "suspended",
): Promise<void> {
  const { data: developer } = await supabase.from("developers").select("user_id").eq("user_id", userId).maybeSingle();
  if (!developer) throw new ModerationError("開発者が見つかりません", 404);
  const patch = status === "approved" ? { status, verified_at: new Date().toISOString() } : { status };
  const { error } = await supabase.from("developers").update(patch).eq("user_id", userId);
  if (error) throw new ModerationError(`更新できませんでした: ${error.message}`, 500);
  await audit(supabase, admin, `developer.${status}`, "developer", userId);
}

export async function setAppStatus(
  supabase: SupabaseClient,
  admin: AdminUser,
  appId: string,
  status: "published" | "suspended",
): Promise<void> {
  const { data: app } = await supabase.from("apps").select("id, status").eq("id", appId).maybeSingle();
  if (!app) throw new ModerationError("アプリが見つかりません", 404);
  if (status === "published") {
    // 公開できるのは、公開済みのリリースが1つ以上あるアプリだけ(空のアプリを出さない)
    const { count } = await supabase
      .from("app_releases")
      .select("id", { count: "exact", head: true })
      .eq("app_id", appId)
      .eq("status", "published");
    if (!count) throw new ModerationError("公開中のリリースがないので、公開にできません", 409);
  }
  const { error } = await supabase.from("apps").update({ status }).eq("id", appId);
  if (error) throw new ModerationError(`更新できませんでした: ${error.message}`, 500);
  await audit(supabase, admin, `app.${status}`, "app", appId, { from: app.status });
}
