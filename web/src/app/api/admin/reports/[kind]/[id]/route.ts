import { z } from "zod";
import { requireAdmin } from "@/lib/admin";
import { adminError } from "@/lib/admin-api";
import { jsonError, jsonOk, parseJsonBody } from "@/lib/http";
import { resolveReport } from "@/lib/moderation";
import { serviceClient } from "@/lib/supabase";

const Kind = z.enum(["diary", "review"]);
const Body = z.object({
  action: z.enum(["dismiss", "delete_content", "ban_author"]),
  reason: z.string().trim().max(500).optional(),
});
const ReportId = z.string().regex(/^[0-9a-fA-F-]{1,40}$/);

/** 報告の処理: 問題なし(dismiss) / 投稿・レビューを削除(delete_content) / 投稿者をBAN(ban_author) */
export async function POST(request: Request, { params }: { params: Promise<{ kind: string; id: string }> }) {
  const auth = await requireAdmin(request);
  if ("response" in auth) return auth.response;
  const { kind, id } = await params;
  const parsedKind = Kind.safeParse(kind);
  if (!parsedKind.success || !ReportId.safeParse(id).success) return jsonError("報告が見つかりません", 404);
  const body = await parseJsonBody(request, Body);
  if (!body.success) return jsonError("リクエストが不正です", 400);
  try {
    await resolveReport(serviceClient(), auth.admin, parsedKind.data, id, body.data.action, body.data.reason);
    return jsonOk({ ok: true });
  } catch (e) {
    return adminError(e);
  }
}
