import { getAppBySlug } from "@/lib/catalog";
import { internalError, jsonError, jsonOk } from "@/lib/http";

export async function GET(_request: Request, { params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  try {
    const app = await getAppBySlug(slug);
    if (!app) return jsonError("アプリが見つかりません", 404);
    return jsonOk(app, { cache: true });
  } catch (e) {
    return internalError(e);
  }
}
