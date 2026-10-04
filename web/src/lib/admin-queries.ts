import type { SupabaseClient } from "@supabase/supabase-js";
import { sanitizeQuery } from "./catalog";
import { ModerationError } from "./moderation";
import type { Finding } from "./policy";

/**
 * 管理アプリ向けの読み取り。service_role のクライアントを渡して使う
 * (呼び出し側が requireAdmin を通っていること)。返す形はそのまま管理アプリのJSONになる。
 */

const PHOTO_URL_TTL_SECONDS = 600;

const iso = (daysAgo: number) => new Date(Date.now() - daysAgo * 86_400_000).toISOString();

async function countRows(query: PromiseLike<{ count: number | null }>): Promise<number> {
  return (await query).count ?? 0;
}

// ---------------------------------------------------------------- 概要

export interface OverviewDto {
  users: { total: number; new7d: number; banned: number };
  diary: { entries: number; openReports: number };
  reviews: { openReports: number };
  developers: { pending: number };
  releases: { pendingApproval: number };
  apps: { published: number };
  downloads: { total: number; last7d: number };
}

export async function getOverview(svc: SupabaseClient): Promise<OverviewDto> {
  const head = (table: string) => svc.from(table).select("*", { count: "exact", head: true });
  const [total, new7d, banned, entries, diaryOpen, reviewOpen, devPending, relPending, published, dlTotal, dl7d] = await Promise.all([
    countRows(head("profiles")),
    countRows(head("profiles").gte("created_at", iso(7))),
    countRows(head("profiles").not("banned_at", "is", null)),
    countRows(head("diary_entries")),
    countRows(head("diary_reports").eq("status", "open")),
    countRows(head("review_reports").eq("resolved", false)),
    countRows(head("developers").eq("status", "pending")),
    countRows(head("app_releases").in("status", ["scanned", "approved"])),
    countRows(head("apps").eq("status", "published").eq("admin_only", false)),
    countRows(head("downloads")),
    countRows(head("downloads").gte("created_at", iso(7))),
  ]);
  return {
    users: { total, new7d, banned },
    diary: { entries, openReports: diaryOpen },
    reviews: { openReports: reviewOpen },
    developers: { pending: devPending },
    releases: { pendingApproval: relPending },
    apps: { published },
    downloads: { total: dlTotal, last7d: dl7d },
  };
}

// ---------------------------------------------------------------- ユーザー

export interface UserRowDto {
  id: string;
  email: string | null;
  displayName: string;
  role: string;
  provider: string;
  createdAt: string;
  lastSignInAt: string | null;
  bannedAt: string | null;
  banReason: string | null;
  diaryCount: number;
  reviewCount: number;
  reportsAgainst: number;
}

export async function listUsers(
  svc: SupabaseClient,
  opts: { q?: string; bannedOnly?: boolean; limit?: number; offset?: number },
): Promise<{ items: UserRowDto[]; total: number }> {
  const { data, error } = await svc.rpc("admin_list_users", {
    p_q: opts.q ? sanitizeQuery(opts.q) : null,
    p_banned_only: Boolean(opts.bannedOnly),
    p_limit: opts.limit ?? 30,
    p_offset: opts.offset ?? 0,
  });
  if (error) throw new Error(error.message);
  const rows = (data ?? []) as Record<string, unknown>[];
  return {
    total: rows.length > 0 ? Number(rows[0].total) : 0,
    items: rows.map((r) => ({
      id: r.id as string,
      email: (r.email as string | null) ?? null,
      displayName: (r.display_name as string) ?? "",
      role: r.role as string,
      provider: (r.provider as string) ?? "email",
      createdAt: r.created_at as string,
      lastSignInAt: (r.last_sign_in_at as string | null) ?? null,
      bannedAt: (r.banned_at as string | null) ?? null,
      banReason: (r.ban_reason as string | null) ?? null,
      diaryCount: Number(r.diary_count ?? 0),
      reviewCount: Number(r.review_count ?? 0),
      reportsAgainst: Number(r.reports_against ?? 0),
    })),
  };
}

export async function getUserDetail(svc: SupabaseClient, id: string) {
  const [{ data: profile }, { data: authData }] = await Promise.all([
    svc.from("profiles").select("id, display_name, role, banned_at, ban_reason, created_at").eq("id", id).maybeSingle(),
    svc.auth.admin.getUserById(id),
  ]);
  if (!profile) throw new ModerationError("ユーザーが見つかりません", 404);
  const user = authData?.user;

  const [developer, diaryCount, reviewCount, reportsAgainstCount, reportsFiledCount, downloadCount, entries, reviews, reports] = await Promise.all([
    svc.from("developers").select("status, name, contact_email, website, created_at").eq("user_id", id).maybeSingle(),
    countRows(svc.from("diary_entries").select("*", { count: "exact", head: true }).eq("user_id", id)),
    countRows(svc.from("reviews").select("*", { count: "exact", head: true }).eq("user_id", id)),
    countRows(svc.from("diary_reports").select("*", { count: "exact", head: true }).eq("reported_user_id", id)),
    countRows(svc.from("diary_reports").select("*", { count: "exact", head: true }).eq("reporter_id", id)),
    countRows(svc.from("downloads").select("*", { count: "exact", head: true }).eq("user_id", id)),
    svc.from("diary_entries").select("id, body, visibility, photo_path, created_at").eq("user_id", id).order("created_at", { ascending: false }).limit(10),
    svc.from("reviews").select("id, rating, body, status, created_at, app:apps(name)").eq("user_id", id).order("created_at", { ascending: false }).limit(10),
    svc.from("diary_reports").select("id, reason, detail, status, entry_body, created_at").eq("reported_user_id", id).order("created_at", { ascending: false }).limit(10),
  ]);

  return {
    id: profile.id as string,
    email: user?.email ?? null,
    emailConfirmed: Boolean(user?.email_confirmed_at),
    displayName: (profile.display_name as string) ?? "",
    role: profile.role as string,
    provider: (user?.app_metadata?.provider as string | undefined) ?? "email",
    createdAt: profile.created_at as string,
    lastSignInAt: user?.last_sign_in_at ?? null,
    bannedAt: (profile.banned_at as string | null) ?? null,
    banReason: (profile.ban_reason as string | null) ?? null,
    developer: developer.data
      ? {
          status: developer.data.status as string,
          name: developer.data.name as string,
          contactEmail: developer.data.contact_email as string,
          website: (developer.data.website as string | null) ?? null,
        }
      : null,
    counts: { diaryEntries: diaryCount, reviews: reviewCount, reportsAgainst: reportsAgainstCount, reportsFiled: reportsFiledCount, downloads: downloadCount },
    recentEntries: (entries.data ?? []).map((e) => ({
      id: e.id as string,
      body: e.body as string,
      visibility: e.visibility as string,
      hasPhoto: Boolean(e.photo_path),
      createdAt: e.created_at as string,
    })),
    recentReviews: (reviews.data ?? []).map((r) => {
      const app = Array.isArray(r.app) ? r.app[0] : r.app;
      return {
        id: r.id as string,
        rating: r.rating as number,
        body: r.body as string,
        status: r.status as string,
        appName: (app?.name as string | undefined) ?? "",
        createdAt: r.created_at as string,
      };
    }),
    reportsAgainst: (reports.data ?? []).map((r) => ({
      id: r.id as string,
      reason: r.reason as string,
      detail: (r.detail as string | null) ?? null,
      status: r.status as string,
      entryBody: (r.entry_body as string | null) ?? null,
      createdAt: r.created_at as string,
    })),
  };
}

// ---------------------------------------------------------------- 報告

async function displayNames(svc: SupabaseClient, ids: (string | null | undefined)[]): Promise<Map<string, string>> {
  const unique = [...new Set(ids.filter((v): v is string => Boolean(v)))];
  if (unique.length === 0) return new Map();
  const { data } = await svc.from("profiles").select("id, display_name").in("id", unique);
  return new Map((data ?? []).map((p) => [p.id as string, (p.display_name as string) || "(名前なし)"]));
}

export interface ReportDto {
  kind: "diary" | "review";
  id: string;
  createdAt: string;
  reason: string;
  detail: string | null;
  /** diary: open/reviewed/actioned/dismissed。review: open/resolved */
  status: string;
  reporter: { id: string; name: string };
  target: { userId: string | null; name: string };
  /** 報告された投稿・レビューの本文(日記は報告時点の控え) */
  body: string;
  /** 日記: 投稿ID(既に削除されていれば null)。レビュー: レビューID */
  contentId: string | null;
  contentLabel: string;
}

export async function listReports(svc: SupabaseClient, opts: { openOnly: boolean; limit?: number }): Promise<ReportDto[]> {
  const limit = opts.limit ?? 50;

  let diaryQuery = svc
    .from("diary_reports")
    .select("id, reason, detail, status, entry_id, entry_body, reporter_id, reported_user_id, created_at")
    .order("created_at", { ascending: false })
    .limit(limit);
  if (opts.openOnly) diaryQuery = diaryQuery.eq("status", "open");

  let reviewQuery = svc
    .from("review_reports")
    .select("id, reason, resolved, reporter_id, created_at, review:reviews(id, body, rating, user_id, app:apps(name))")
    .order("created_at", { ascending: false })
    .limit(limit);
  if (opts.openOnly) reviewQuery = reviewQuery.eq("resolved", false);

  const [{ data: diary }, { data: review }] = await Promise.all([diaryQuery, reviewQuery]);

  // 報告された日記の投稿がまだ残っているか(削除済みなら「削除する」操作は不要)
  const entryIds = (diary ?? []).map((r) => r.entry_id as string);
  const { data: liveEntries } = entryIds.length
    ? await svc.from("diary_entries").select("id").in("id", entryIds)
    : { data: [] as { id: string }[] };
  const live = new Set((liveEntries ?? []).map((e) => e.id as string));

  const reviews = (review ?? []).map((r) => {
    const rv = Array.isArray(r.review) ? r.review[0] : r.review;
    const app = rv ? (Array.isArray(rv.app) ? rv.app[0] : rv.app) : null;
    return { r, rv, app };
  });
  const names = await displayNames(svc, [
    ...(diary ?? []).flatMap((r) => [r.reporter_id as string, r.reported_user_id as string | null]),
    ...reviews.flatMap(({ r, rv }) => [r.reporter_id as string, rv?.user_id as string | undefined]),
  ]);

  const items: ReportDto[] = [
    ...(diary ?? []).map((r) => ({
      kind: "diary" as const,
      id: r.id as string,
      createdAt: r.created_at as string,
      reason: r.reason as string,
      detail: (r.detail as string | null) ?? null,
      status: r.status as string,
      reporter: { id: r.reporter_id as string, name: names.get(r.reporter_id as string) ?? "(不明)" },
      target: {
        userId: (r.reported_user_id as string | null) ?? null,
        name: (r.reported_user_id && names.get(r.reported_user_id as string)) || "(削除済み)",
      },
      body: (r.entry_body as string | null) ?? "",
      contentId: live.has(r.entry_id as string) ? (r.entry_id as string) : null,
      contentLabel: "ひとこと日記の投稿",
    })),
    ...reviews.map(({ r, rv, app }) => ({
      kind: "review" as const,
      id: String(r.id),
      createdAt: r.created_at as string,
      reason: r.reason as string,
      detail: null,
      status: r.resolved ? "resolved" : "open",
      reporter: { id: r.reporter_id as string, name: names.get(r.reporter_id as string) ?? "(不明)" },
      target: { userId: (rv?.user_id as string | undefined) ?? null, name: (rv?.user_id && names.get(rv.user_id as string)) || "(不明)" },
      body: (rv?.body as string | undefined) ?? "",
      contentId: (rv?.id as string | undefined) ?? null,
      contentLabel: `アプリのレビュー(${(app?.name as string | undefined) ?? "不明なアプリ"}・★${rv?.rating ?? "-"})`,
    })),
  ];
  return items.sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, limit);
}

// ---------------------------------------------------------------- ひとこと日記の投稿

async function signedPhotoUrls(svc: SupabaseClient, paths: string[]): Promise<Map<string, string>> {
  if (paths.length === 0) return new Map();
  const { data } = await svc.storage.from("diary-media").createSignedUrls(paths, PHOTO_URL_TTL_SECONDS);
  return new Map((data ?? []).filter((d) => d.path && d.signedUrl).map((d) => [d.path as string, d.signedUrl as string]));
}

export interface DiaryEntryDto {
  id: string;
  userId: string;
  userName: string;
  body: string;
  visibility: string;
  createdAt: string;
  /** 管理者が写真を確認するための、短時間だけ有効なURL */
  photoUrl: string | null;
}

export async function listDiaryEntries(
  svc: SupabaseClient,
  opts: { q?: string; userId?: string; limit?: number; offset?: number },
): Promise<DiaryEntryDto[]> {
  const limit = Math.min(Math.max(opts.limit ?? 30, 1), 100);
  const offset = Math.max(opts.offset ?? 0, 0);
  let query = svc
    .from("diary_entries")
    .select("id, user_id, body, visibility, photo_path, created_at")
    .order("created_at", { ascending: false })
    .range(offset, offset + limit - 1);
  if (opts.userId) query = query.eq("user_id", opts.userId);
  const q = opts.q ? sanitizeQuery(opts.q) : "";
  if (q) query = query.ilike("body", `%${q}%`);

  const { data, error } = await query;
  if (error) throw new Error(error.message);
  const rows = data ?? [];
  const [names, urls] = await Promise.all([
    displayNames(svc, rows.map((r) => r.user_id as string)),
    signedPhotoUrls(svc, rows.map((r) => r.photo_path as string | null).filter((p): p is string => Boolean(p))),
  ]);
  return rows.map((r) => ({
    id: r.id as string,
    userId: r.user_id as string,
    userName: names.get(r.user_id as string) ?? "(不明)",
    body: r.body as string,
    visibility: r.visibility as string,
    createdAt: r.created_at as string,
    photoUrl: r.photo_path ? (urls.get(r.photo_path as string) ?? null) : null,
  }));
}

// ---------------------------------------------------------------- 開発者・アプリ・監査ログ

export interface DeveloperDto {
  userId: string;
  name: string;
  contactEmail: string;
  website: string | null;
  status: string;
  createdAt: string;
}

export async function listDevelopers(svc: SupabaseClient, status?: string): Promise<DeveloperDto[]> {
  let query = svc.from("developers").select("user_id, name, contact_email, website, status, created_at").order("created_at", { ascending: false });
  if (status) query = query.eq("status", status);
  const { data, error } = await query;
  if (error) throw new Error(error.message);
  return (data ?? []).map((d) => ({
    userId: d.user_id as string,
    name: d.name as string,
    contactEmail: d.contact_email as string,
    website: (d.website as string | null) ?? null,
    status: d.status as string,
    createdAt: d.created_at as string,
  }));
}

export interface AppRowDto {
  id: string;
  slug: string;
  name: string;
  packageName: string;
  status: string;
  adminOnly: boolean;
  downloadCount: number;
  ratingAvg: number;
  ratingCount: number;
  latestVersion: string | null;
}

export async function listAllApps(svc: SupabaseClient): Promise<AppRowDto[]> {
  const { data, error } = await svc
    .from("apps")
    .select("id, slug, name, package_name, status, admin_only, download_count, rating_avg, rating_count")
    .order("created_at", { ascending: false });
  if (error) throw new Error(error.message);
  const apps = data ?? [];
  const { data: releases } = apps.length
    ? await svc
        .from("app_releases")
        .select("app_id, version_name, version_code")
        .eq("status", "published")
        .in("app_id", apps.map((a) => a.id as string))
    : { data: [] as { app_id: string; version_name: string | null; version_code: number }[] };
  const latest = new Map<string, { name: string | null; code: number }>();
  for (const r of releases ?? []) {
    const cur = latest.get(r.app_id as string);
    if (!cur || (r.version_code as number) > cur.code) latest.set(r.app_id as string, { name: r.version_name as string | null, code: r.version_code as number });
  }
  return apps.map((a) => ({
    id: a.id as string,
    slug: a.slug as string,
    name: a.name as string,
    packageName: a.package_name as string,
    status: a.status as string,
    adminOnly: Boolean(a.admin_only),
    downloadCount: Number(a.download_count ?? 0),
    ratingAvg: Number(a.rating_avg ?? 0),
    ratingCount: Number(a.rating_count ?? 0),
    latestVersion: latest.get(a.id as string)?.name ?? null,
  }));
}

export interface AuditRowDto {
  id: number;
  action: string;
  targetType: string;
  targetId: string | null;
  adminName: string;
  detail: Record<string, unknown>;
  createdAt: string;
}

export async function listAudit(svc: SupabaseClient, limit = 50): Promise<AuditRowDto[]> {
  const { data, error } = await svc
    .from("admin_audit_log")
    .select("id, admin_id, action, target_type, target_id, detail, created_at")
    .order("created_at", { ascending: false })
    .limit(Math.min(Math.max(limit, 1), 200));
  if (error) throw new Error(error.message);
  const rows = data ?? [];
  const names = await displayNames(svc, rows.map((r) => r.admin_id as string | null));
  return rows.map((r) => ({
    id: Number(r.id),
    action: r.action as string,
    targetType: r.target_type as string,
    targetId: (r.target_id as string | null) ?? null,
    adminName: (r.admin_id && names.get(r.admin_id as string)) || "(削除済み)",
    detail: (r.detail as Record<string, unknown>) ?? {},
    createdAt: r.created_at as string,
  }));
}

// ---------------------------------------------------------------- 承認待ちリリース

export interface PendingReleaseDto {
  id: string;
  versionName: string | null;
  status: string;
  policyVerdict: string | null;
  policyFindings: Finding[];
  app: { id: string; slug: string; name: string } | null;
}

/** 公開の承認待ち(scanned)・公開停止の承認待ち(approved)のリリース。新しい順 */
export async function listPendingReleases(svc: SupabaseClient): Promise<PendingReleaseDto[]> {
  const { data, error } = await svc
    .from("app_releases")
    .select("id, version_name, status, policy_verdict, policy_findings, app:apps(id, slug, name)")
    .in("status", ["scanned", "approved"])
    .order("created_at", { ascending: false });
  if (error) throw new Error(error.message);
  return (data ?? []).map((r) => ({
    id: r.id as string,
    versionName: (r.version_name as string | null) ?? null,
    status: r.status as string,
    policyVerdict: (r.policy_verdict as string | null) ?? null,
    policyFindings: (r.policy_findings as Finding[] | null) ?? [],
    app: Array.isArray(r.app) ? (r.app[0] ?? null) : (r.app ?? null),
  }));
}

// ---------------------------------------------------------------- 管理者専用アプリ

export interface PrivateAppDto {
  appId: string;
  slug: string;
  name: string;
  packageName: string;
  releaseId: string;
  versionName: string | null;
  versionCode: number;
  apkSize: number | null;
}

/** 管理者専用アプリ(admin_only)と、その最新の公開リリース */
export async function listPrivateApps(svc: SupabaseClient): Promise<PrivateAppDto[]> {
  const { data: apps, error } = await svc
    .from("apps")
    .select("id, slug, name, package_name")
    .eq("admin_only", true)
    .eq("status", "published");
  if (error) throw new Error(error.message);
  if (!apps?.length) return [];

  const { data: releases } = await svc
    .from("app_releases")
    .select("id, app_id, version_name, version_code, apk_size")
    .eq("status", "published")
    .in("app_id", apps.map((a) => a.id as string))
    .order("version_code", { ascending: false });

  const result: PrivateAppDto[] = [];
  for (const app of apps) {
    const release = (releases ?? []).find((r) => r.app_id === app.id);
    if (!release) continue;
    result.push({
      appId: app.id as string,
      slug: app.slug as string,
      name: app.name as string,
      packageName: app.package_name as string,
      releaseId: release.id as string,
      versionName: (release.version_name as string | null) ?? null,
      versionCode: release.version_code as number,
      apkSize: (release.apk_size as number | null) ?? null,
    });
  }
  return result;
}
