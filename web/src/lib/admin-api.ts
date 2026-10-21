import { clampPage } from "./db";
import { internalError, jsonError } from "./http";
import { ModerationError } from "./moderation";

// 管理用 Route Handler の共通処理。認証は requireAdmin(./admin)で行う。

/** 管理APIのエラー応答。管理者向けの文言(ModerationError)はそのまま、想定外は500 */
export function adminError(e: unknown): Response {
  if (e instanceof ModerationError) return jsonError(e.message, e.status);
  return internalError(e);
}

/** ?limit=&offset= を安全な範囲に収める */
export function paging(url: URL, { fallback = 30, max = 100 }: { fallback?: number; max?: number } = {}): { limit: number; offset: number } {
  const num = (name: string) => Number(url.searchParams.get(name)) || undefined;
  return clampPage({ limit: num("limit"), offset: num("offset") }, { fallback, max });
}
