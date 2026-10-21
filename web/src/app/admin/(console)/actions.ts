"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";
import { ensureAdminDeveloper } from "@/lib/admin";
import { DEVELOPER_LIMITS, PACKAGE_RE, SLUG_RE } from "@/lib/app-rules";
import { isUniqueViolation } from "@/lib/db";
import { ModerationError, setAppStatus as setAppStatusWithAudit } from "@/lib/moderation";
import { getAdminUser, serviceClient } from "@/lib/supabase";

async function requireAdminOrThrow() {
  const admin = await getAdminUser();
  if (!admin) throw new Error("管理者としてログインしてください");
  return admin;
}

const AppFields = z.object({
  slug: z.string().regex(SLUG_RE, "スラッグは半角英小文字・数字・ハイフンで2〜63文字"),
  packageName: z.string().regex(PACKAGE_RE, "パッケージ名の形式が正しくありません (例: com.example.app)"),
  name: z.string().min(1, "名前を入力してください").max(60),
  shortDesc: z.string().max(80).default(""),
  description: z.string().max(4000).default(""),
  categoryId: z.coerce.number().int().positive().optional(),
});

function formToObject(form: FormData) {
  const raw = Object.fromEntries(form.entries());
  return { ...raw, categoryId: raw.categoryId || undefined };
}

export interface ActionState {
  error?: string;
}

export async function createApp(_prev: ActionState, form: FormData): Promise<ActionState> {
  const admin = await requireAdminOrThrow();
  const parsed = AppFields.safeParse(formToObject(form));
  if (!parsed.success) return { error: parsed.error.issues[0].message };
  const f = parsed.data;

  await ensureAdminDeveloper(admin);
  const { data, error } = await serviceClient()
    .from("apps")
    .insert({
      slug: f.slug,
      package_name: f.packageName,
      name: f.name,
      short_desc: f.shortDesc,
      description: f.description,
      category_id: f.categoryId ?? null,
      developer_id: admin.id,
      status: "draft",
    })
    .select("id")
    .single();
  if (error) {
    return { error: isUniqueViolation(error) ? "同じスラッグまたはパッケージ名のアプリが既にあります" : error.message };
  }
  redirect(`/admin/apps/${data.id}`);
}

export async function updateApp(appId: string, _prev: ActionState, form: FormData): Promise<ActionState> {
  await requireAdminOrThrow();
  const parsed = AppFields.omit({ slug: true, packageName: true }).safeParse(formToObject(form));
  if (!parsed.success) return { error: parsed.error.issues[0].message };
  const f = parsed.data;

  const { error } = await serviceClient()
    .from("apps")
    .update({ name: f.name, short_desc: f.shortDesc, description: f.description, category_id: f.categoryId ?? null })
    .eq("id", appId);
  if (error) return { error: error.message };
  revalidatePath(`/admin/apps/${appId}`);
  return {};
}

/** アプリ自体の公開・公開停止。公開には「公開中のリリース」が1つ以上必要。管理アプリからの操作と同じく監査ログに残す */
export async function setAppStatus(appId: string, status: "published" | "suspended"): Promise<ActionState> {
  const admin = await requireAdminOrThrow();
  try {
    await setAppStatusWithAudit(serviceClient(), admin, appId, status);
  } catch (e) {
    if (e instanceof ModerationError) return { error: e.message };
    throw e;
  }
  revalidatePath(`/admin/apps/${appId}`);
  revalidatePath("/admin");
  return {};
}

export async function setFeatured(appId: string, featured: boolean): Promise<ActionState> {
  await requireAdminOrThrow();
  const { error } = await serviceClient().from("apps").update({ featured }).eq("id", appId);
  if (error) return { error: error.message };
  revalidatePath(`/admin/apps/${appId}`);
  return {};
}

/** 画像アップロード(ブラウザから Storage へ直接)の後に、パスを apps に反映する */
export async function saveMedia(
  appId: string,
  change: { icon?: string; addScreenshot?: string; removeScreenshot?: string },
): Promise<ActionState> {
  await requireAdminOrThrow();
  const supabase = serviceClient();
  const { data: app } = await supabase.from("apps").select("screenshots").eq("id", appId).maybeSingle();
  if (!app) return { error: "アプリが見つかりません" };

  const update: Record<string, unknown> = {};
  if (change.icon) update.icon_path = change.icon;
  let shots: string[] = app.screenshots ?? [];
  if (change.addScreenshot) shots = [...shots, change.addScreenshot].slice(0, DEVELOPER_LIMITS.maxScreenshots);
  if (change.removeScreenshot) shots = shots.filter((s) => s !== change.removeScreenshot);
  if (change.addScreenshot || change.removeScreenshot) update.screenshots = shots;

  const { error } = await supabase.from("apps").update(update).eq("id", appId);
  if (error) return { error: error.message };
  revalidatePath(`/admin/apps/${appId}`);
  return {};
}
