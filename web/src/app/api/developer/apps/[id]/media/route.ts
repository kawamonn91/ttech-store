import { z } from "zod";
import { DEVELOPER_LIMITS, loadOwnApp, requireDeveloper } from "@/lib/developer";
import { internalError, jsonError, jsonOk } from "@/lib/http";
import { ImageError, processImage } from "@/lib/images";

const BUCKET = "app-media";

/**
 * アイコン・スクリーンショットの追加(multipart: kind = icon | screenshot, file = 画像)。
 * 画像は検査してPNGに作り直してから保存する(lib/images.ts)。アイコンは置き換え、スクリーンショットは最大8枚まで追加できる。
 */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireDeveloper();
  if ("response" in auth) return auth.response;
  const { user, svc } = auth;
  const { id } = await params;

  const form = await request.formData().catch(() => null);
  const kind = form?.get("kind");
  const file = form?.get("file");
  if ((kind !== "icon" && kind !== "screenshot") || !(file instanceof File)) return jsonError("リクエストが不正です", 400);
  if (file.size > DEVELOPER_LIMITS.maxImageBytes) return jsonError(`画像は ${DEVELOPER_LIMITS.maxImageBytes / 1024 / 1024}MB 以下にしてください`, 413);

  try {
    const app = await loadOwnApp(svc, id, user.id, "id, status, developer_id, icon_path, screenshots");
    if (!app) return jsonError("アプリが見つかりません", 404);
    if (app.status === "suspended") return jsonError("このアプリは停止されています。運営にお問い合わせください", 403);
    const screenshots = (app.screenshots as string[] | null) ?? [];
    if (kind === "screenshot" && screenshots.length >= DEVELOPER_LIMITS.maxScreenshots) {
      return jsonError(`スクリーンショットは最大${DEVELOPER_LIMITS.maxScreenshots}枚までです`, 409);
    }

    let png: Buffer;
    try {
      png = await processImage(Buffer.from(await file.arrayBuffer()), kind);
    } catch (e) {
      if (e instanceof ImageError) return jsonError(e.message, 400);
      throw e;
    }

    const path = `${id}/${kind}-${Date.now()}.png`;
    const { error: uploadError } = await svc.storage.from(BUCKET).upload(path, png, { contentType: "image/png", upsert: false });
    if (uploadError) throw uploadError;

    const patch = kind === "icon" ? { icon_path: path } : { screenshots: [...screenshots, path] };
    const { error } = await svc.from("apps").update(patch).eq("id", id).eq("developer_id", user.id);
    if (error) {
      await svc.storage.from(BUCKET).remove([path]).catch(() => {});
      throw error;
    }
    // 置き換えられた古いアイコンは消す(失敗しても構わない)
    const old = app.icon_path as string | null;
    if (kind === "icon" && old && old !== path && old.startsWith(`${id}/`)) await svc.storage.from(BUCKET).remove([old]).catch(() => {});

    return jsonOk({ ok: true, path });
  } catch (e) {
    return internalError(e);
  }
}

const DeleteBody = z.object({ path: z.string().min(1).max(300) });

/** スクリーンショットを1枚削除する(自分のアプリのフォルダにあるものだけ) */
export async function DELETE(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const auth = await requireDeveloper();
  if ("response" in auth) return auth.response;
  const { user, svc } = auth;
  const { id } = await params;

  const body = DeleteBody.safeParse(await request.json().catch(() => null));
  if (!body.success) return jsonError("リクエストが不正です", 400);

  try {
    const app = await loadOwnApp(svc, id, user.id, "id, status, developer_id, screenshots");
    if (!app) return jsonError("アプリが見つかりません", 404);
    const screenshots = (app.screenshots as string[] | null) ?? [];
    if (!body.data.path.startsWith(`${id}/`) || !screenshots.includes(body.data.path)) return jsonError("画像が見つかりません", 404);

    const { error } = await svc.from("apps").update({ screenshots: screenshots.filter((p) => p !== body.data.path) }).eq("id", id).eq("developer_id", user.id);
    if (error) throw error;
    await svc.storage.from(BUCKET).remove([body.data.path]).catch(() => {});
    return jsonOk({ ok: true });
  } catch (e) {
    return internalError(e);
  }
}
