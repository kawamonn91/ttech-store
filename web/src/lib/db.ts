import type { SupabaseClient } from "@supabase/supabase-js";

// Supabase(PostgREST)から読んだ行を扱うための小さな道具。

/** 埋め込み(1対1の関連)は、オブジェクトでも1要素の配列でも返ることがあるため、どちらでも1件として取り出す */
export function first<T>(value: T | T[] | null | undefined): T | null {
  return Array.isArray(value) ? (value[0] ?? null) : (value ?? null);
}

/** 一意制約の違反(同じ値がすでにある)か */
export function isUniqueViolation(error: { code?: string; message: string }): boolean {
  return error.code === "23505" || /duplicate|unique/i.test(error.message);
}

/** UUID の形(16進数の 8-4-4-4-12)か。形の違う ID は、DB に問い合わせる前に「見つからない」として扱う */
export function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

/** 利用者の表示名(profiles.display_name)をまとめて引く。表示名が空の人は「(名前なし)」 */
export async function displayNames(supabase: SupabaseClient, ids: (string | null | undefined)[]): Promise<Map<string, string>> {
  const unique = [...new Set(ids.filter((v): v is string => Boolean(v)))];
  if (unique.length === 0) return new Map();
  const { data } = await supabase.from("profiles").select("id, display_name").in("id", unique);
  return new Map((data ?? []).map((p) => [p.id as string, (p.display_name as string) || "(名前なし)"]));
}

/** 一覧の件数(limit)と開始位置(offset)を、使ってよい範囲に収める */
export function clampPage(
  page: { limit?: number; offset?: number },
  { fallback = 30, max = 100 }: { fallback?: number; max?: number } = {},
): { limit: number; offset: number } {
  return {
    limit: Math.min(Math.max(page.limit ?? fallback, 1), max),
    offset: Math.max(page.offset ?? 0, 0),
  };
}
