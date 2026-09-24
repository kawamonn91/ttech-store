import { z } from "zod";
import { requireAdmin } from "./admin";
import { internalError, jsonError } from "./http";
import { ModerationError } from "./moderation";
import type { AdminUser } from "./supabase";

/** 管理APIのエラー応答。管理者向けの文言(ModerationError)はそのまま、想定外は500 */
export function adminError(e: unknown): Response {
  if (e instanceof ModerationError) return jsonError(e.message, e.status);
  return internalError(e);
}

/** 管理者でなければ認証エラーのレスポンスを返す。管理者なら admin を返す */
export async function adminOr401(request: Request): Promise<{ admin: AdminUser } | { response: Response }> {
  return requireAdmin(request);
}

export const IdParam = z.string().uuid();

/** ?limit=&offset= を安全な範囲に収める */
export function paging(url: URL, defaults = { limit: 30, max: 100 }): { limit: number; offset: number } {
  const limit = Math.min(Math.max(Number(url.searchParams.get("limit")) || defaults.limit, 1), defaults.max);
  const offset = Math.max(Number(url.searchParams.get("offset")) || 0, 0);
  return { limit, offset };
}
