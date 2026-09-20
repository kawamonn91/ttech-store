import { publicClient } from "./supabase";
import {
  mediaUrl,
  toDetail,
  toLatest,
  toSummary,
  type AppDetailDto,
  type AppRow,
  type AppSummaryDto,
  type CategoryDto,
  type IndexEntryDto,
  type ReleaseRow,
} from "./dto";

/**
 * 公開カタログの読み取り。anon 権限で叩くので、RLS により published のものしか返らない。
 * Web ページと /api/v1 の両方がここを使う。
 */

const APP_COLUMNS =
  "id, slug, package_name, name, short_desc, description, icon_path, screenshots, download_count, rating_avg, rating_count, " +
  "category:categories(slug, name), developer:developers(name)";

const RELEASE_COLUMNS = "id, app_id, version_name, version_code, apk_size, min_sdk, permissions, release_notes, published_at";

export type SortKey = "new" | "popular" | "rating";

async function latestByApp(appIds: string[]): Promise<Map<string, ReleaseRow>> {
  if (appIds.length === 0) return new Map();
  const { data, error } = await publicClient().from("latest_releases").select(RELEASE_COLUMNS).in("app_id", appIds);
  if (error) throw new Error(error.message);
  return new Map((data as unknown as ReleaseRow[]).map((r) => [r.app_id, r]));
}

async function summaries(rows: AppRow[]): Promise<AppSummaryDto[]> {
  const latest = await latestByApp(rows.map((r) => r.id));
  return rows.map((r) => toSummary(r, latest.get(r.id)));
}

export async function listCategories(): Promise<CategoryDto[]> {
  const { data, error } = await publicClient().from("categories").select("slug, name").order("sort_order");
  if (error) throw new Error(error.message);
  return data as CategoryDto[];
}

/** PostgREST の or() フィルタを壊す文字を除く */
function sanitizeQuery(q: string): string {
  return q.replace(/[,()%*\\]/g, " ").trim().slice(0, 80);
}

export interface ListOptions {
  q?: string;
  category?: string;
  sort?: SortKey;
  featured?: boolean;
  limit?: number;
  offset?: number;
}

export async function listApps(opts: ListOptions = {}): Promise<{ items: AppSummaryDto[]; total: number }> {
  const limit = Math.min(Math.max(opts.limit ?? 30, 1), 100);
  const offset = Math.max(opts.offset ?? 0, 0);
  const supabase = publicClient();

  let categoryId: number | undefined;
  if (opts.category) {
    const { data } = await supabase.from("categories").select("id").eq("slug", opts.category).maybeSingle();
    if (!data) return { items: [], total: 0 };
    categoryId = data.id as number;
  }

  let query = supabase.from("apps").select(APP_COLUMNS, { count: "exact" }).eq("status", "published");
  if (categoryId !== undefined) query = query.eq("category_id", categoryId);
  if (opts.featured) query = query.eq("featured", true);
  const q = opts.q ? sanitizeQuery(opts.q) : "";
  if (q) query = query.or(`name.ilike.%${q}%,short_desc.ilike.%${q}%`);

  switch (opts.sort ?? "new") {
    case "popular":
      query = query.order("download_count", { ascending: false });
      break;
    case "rating":
      query = query.order("rating_avg", { ascending: false }).order("rating_count", { ascending: false });
      break;
    default:
      query = query.order("created_at", { ascending: false });
  }

  const { data, count, error } = await query.range(offset, offset + limit - 1);
  if (error) throw new Error(error.message);
  return { items: await summaries(data as unknown as AppRow[]), total: count ?? 0 };
}

export async function getHome() {
  const [featured, newest, popular] = await Promise.all([
    listApps({ featured: true, limit: 10 }),
    listApps({ sort: "new", limit: 10 }),
    listApps({ sort: "popular", limit: 10 }),
  ]);
  return { featured: featured.items, newest: newest.items, popular: popular.items };
}

export async function getAppBySlug(slug: string): Promise<AppDetailDto | null> {
  const { data, error } = await publicClient()
    .from("apps")
    .select(APP_COLUMNS)
    .eq("status", "published")
    .eq("slug", slug)
    .maybeSingle();
  if (error) throw new Error(error.message);
  if (!data) return null;
  const row = data as unknown as AppRow;
  const latest = await latestByApp([row.id]);
  return toDetail(row, latest.get(row.id));
}

export async function getAppByPackage(packageName: string): Promise<AppDetailDto | null> {
  const { data, error } = await publicClient()
    .from("apps")
    .select(APP_COLUMNS)
    .eq("status", "published")
    .eq("package_name", packageName)
    .maybeSingle();
  if (error) throw new Error(error.message);
  if (!data) return null;
  const row = data as unknown as AppRow;
  const latest = await latestByApp([row.id]);
  return toDetail(row, latest.get(row.id));
}

/** 更新チェック用: 公開中で最新リリースを持つ全アプリの軽量一覧 */
export async function getIndex(): Promise<IndexEntryDto[]> {
  const supabase = publicClient();
  const { data: apps, error } = await supabase
    .from("apps")
    .select("id, slug, package_name, name, icon_path")
    .eq("status", "published");
  if (error) throw new Error(error.message);
  const rows = apps as unknown as { id: string; slug: string; package_name: string; name: string; icon_path: string | null }[];
  const latest = await latestByApp(rows.map((r) => r.id));

  const items: IndexEntryDto[] = [];
  for (const r of rows) {
    const release = toLatest(latest.get(r.id));
    if (!release) continue;
    items.push({ slug: r.slug, packageName: r.package_name, name: r.name, iconUrl: mediaUrl(r.icon_path), latest: release });
  }
  return items;
}
