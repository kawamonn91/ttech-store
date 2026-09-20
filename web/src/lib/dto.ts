import { env } from "./env";

// ストアアプリ(store-app/.../data/api/Models.kt)が受け取る JSON と1対1で対応する。
// 項目を足すときは Kotlin 側も更新すること。

export interface CategoryDto {
  slug: string;
  name: string;
}

export interface LatestReleaseDto {
  releaseId: string;
  versionName: string;
  versionCode: number;
  apkSize: number | null;
  minSdk: number | null;
  permissions: string[];
  releaseNotes: string;
  publishedAt: string | null;
}

export interface AppSummaryDto {
  id: string;
  slug: string;
  packageName: string;
  name: string;
  shortDesc: string;
  iconUrl: string | null;
  category: CategoryDto | null;
  downloadCount: number;
  ratingAvg: number;
  ratingCount: number;
  latest: LatestReleaseDto | null;
}

export interface AppDetailDto extends AppSummaryDto {
  description: string;
  screenshots: string[];
  developerName: string;
}

export interface IndexEntryDto {
  slug: string;
  packageName: string;
  name: string;
  iconUrl: string | null;
  latest: LatestReleaseDto;
}

export interface DownloadInfoDto {
  url: string;
  sha256: string;
  signingCertSha256: string;
  apkSize: number | null;
  versionCode: number;
  packageName: string;
  expiresAt: string;
}

// --- DB の行 -> DTO ----------------------------------------------------------

export interface AppRow {
  id: string;
  slug: string;
  package_name: string;
  name: string;
  short_desc: string;
  description: string;
  icon_path: string | null;
  screenshots: string[];
  download_count: number;
  rating_avg: number | string;
  rating_count: number;
  category: CategoryDto | CategoryDto[] | null;
  developer: { name: string } | { name: string }[] | null;
}

export interface ReleaseRow {
  id: string;
  app_id: string;
  version_name: string;
  version_code: number;
  apk_size: number | null;
  min_sdk: number | null;
  permissions: string[];
  release_notes: string;
  published_at: string | null;
}

/** Supabase の埋め込みは 1:1 でも配列で返ることがあるため、どちらでも受け取れるようにする */
function first<T>(value: T | T[] | null): T | null {
  return Array.isArray(value) ? (value[0] ?? null) : value;
}

export function mediaUrl(path: string | null): string | null {
  if (!path) return null;
  return `${env.supabaseUrl()}/storage/v1/object/public/app-media/${path}`;
}

export function toLatest(row: ReleaseRow | undefined): LatestReleaseDto | null {
  if (!row) return null;
  return {
    releaseId: row.id,
    versionName: row.version_name,
    versionCode: row.version_code,
    apkSize: row.apk_size,
    minSdk: row.min_sdk,
    permissions: row.permissions ?? [],
    releaseNotes: row.release_notes ?? "",
    publishedAt: row.published_at,
  };
}

export function toSummary(row: AppRow, latest: ReleaseRow | undefined): AppSummaryDto {
  return {
    id: row.id,
    slug: row.slug,
    packageName: row.package_name,
    name: row.name,
    shortDesc: row.short_desc,
    iconUrl: mediaUrl(row.icon_path),
    category: first(row.category),
    downloadCount: Number(row.download_count),
    ratingAvg: Number(row.rating_avg),
    ratingCount: row.rating_count,
    latest: toLatest(latest),
  };
}

export function toDetail(row: AppRow, latest: ReleaseRow | undefined): AppDetailDto {
  return {
    ...toSummary(row, latest),
    description: row.description,
    screenshots: (row.screenshots ?? []).map((p) => mediaUrl(p)).filter((u): u is string => u !== null),
    developerName: first(row.developer)?.name ?? "",
  };
}
