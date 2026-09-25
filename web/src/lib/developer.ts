import type { SupabaseClient } from "@supabase/supabase-js";
import { z } from "zod";
import { jsonError } from "./http";
import { serviceClient, sessionClient } from "./supabase";

// ---------------------------------------------------------------- 利用枠(1人の開発者あたり)

export const DEVELOPER_LIMITS = {
  /** 登録できるアプリの数 */
  maxApps: 10,
  /** 24時間にアップロードできるAPKの数(検査・配信の負荷と、悪用の防止) */
  maxReleasesPerDay: 10,
  /** APK1つの最大サイズ */
  maxApkBytes: 200 * 1024 * 1024,
  maxScreenshots: 8,
  /** 画像1枚の最大サイズ(アップロード時) */
  maxImageBytes: 3 * 1024 * 1024,
} as const;

// ---------------------------------------------------------------- 名前の検証

/** 全角・半角のゆれをそろえ、大文字小文字・空白・記号を除いた比較用の形にする */
export function normalizeForCompare(text: string): string {
  return text.normalize("NFKC").toLowerCase().replace(/[\s\-_.・·]+/g, "");
}

/** 運営・他社・公的な組織になりすます名前を避けるための、使えない語(比較用の形で含まれていたら不可) */
const RESERVED_NAME_WORDS = [
  "ttech", "kawamonn", "運営", "公式", "official", "admin", "administrator", "support", "サポート", "事務局",
  "google", "android", "youtube", "gmail", "chrome", "pixel", "apple", "iphone", "microsoft", "windows", "amazon", "facebook", "instagram", "meta",
  "twitter", "tiktok", "line", "whatsapp", "paypal", "rakuten", "楽天", "yahoo", "ヤフー", "docomo", "ドコモ", "softbank", "ソフトバンク", "kddi", "au",
  "任天堂", "nintendo", "sony", "ソニー", "samsung", "netflix", "spotify", "zoom", "slack", "discord",
  "警察", "police", "国税", "税務署", "銀行", "bank", "郵便", "japanpost", "厚生労働省", "総務省", "消費者庁", "政府", "government",
];

/** 開発者名・アプリ名に使えない語を含むか。含むなら、その語を返す */
export function findReservedWord(name: string): string | null {
  const n = normalizeForCompare(name);
  for (const w of RESERVED_NAME_WORDS) {
    const nw = normalizeForCompare(w);
    // 短い英数字の語("au" や "line")は、別の単語の一部にも現れる("Audio" "Pauline")ので、完全一致のときだけ不可にする。
    // 日本語の語(「楽天」「銀行」など、2文字でも意味がある)は、含まれていれば不可にする
    const shortLatin = /^[a-z0-9]+$/.test(nw) && nw.length <= 4;
    if (shortLatin ? n === nw : n.includes(nw)) return w;
  }
  return null;
}

/** パッケージ名の先頭(前方一致)で、開発者に使わせないもの。OS・他社・運営のもの */
const RESERVED_PACKAGE_PREFIXES = [
  "android.", "androidx.", "java.", "javax.", "kotlin.", "dalvik.",
  "com.android.", "com.google.", "com.samsung.", "com.sec.", "com.huawei.", "com.xiaomi.", "com.miui.", "com.oneplus.", "com.oppo.", "com.vivo.",
  "com.sony.", "com.kddi.", "com.softbank.", "com.rakuten.", "com.nintendo.", "com.microsoft.", "com.amazon.", "com.apple.", "com.facebook.",
  "com.instagram.", "com.whatsapp.", "com.twitter.", "com.spotify.", "com.netflix.", "com.zhiliaoapp.", "com.ss.android.", "com.line.", "jp.naver.",
  "jp.co.nttdocomo.", "jp.co.rakuten.", "us.zoom.", "org.mozilla.", "com.paypal.",
  // 運営のもの(運営のアプリは、管理コンソール・公開スクリプトから登録する)
  "com.ttech.", "com.kawamonn.", "jp.yomumemo.", "com.yomumemo.", "com.debtrunapp.",
];

export function isReservedPackage(packageName: string): boolean {
  const p = packageName.toLowerCase();
  return RESERVED_PACKAGE_PREFIXES.some((prefix) => p.startsWith(prefix));
}

/** アプリのURL(/apps/<slug>)や既存のページと衝突するので、slug に使えないもの */
const RESERVED_SLUGS = new Set(["new", "admin", "api", "search", "legal", "developer", "account", "login", "download", "auth", "apps", "static", "_next"]);

/** パッケージ名の最後の区切りから、URL用の名前(slug)の案を作る */
export function suggestSlug(packageName: string): string {
  const parts = packageName.toLowerCase().split(".").filter(Boolean);
  const generic = new Set(["app", "android", "mobile", "client", "com", "co", "jp", "org", "net", "io"]);
  for (let i = parts.length - 1; i >= 0; i--) {
    const seg = parts[i].replace(/_/g, "-").replace(/[^a-z0-9-]/g, "").replace(/^-+|-+$/g, "");
    if (seg.length >= 2 && !generic.has(seg)) return seg.slice(0, 40);
  }
  return `app-${Math.random().toString(36).slice(2, 8)}`;
}

// ---------------------------------------------------------------- 入力の形式(zod)

const PACKAGE_RE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/;
const SLUG_RE = /^[a-z0-9][a-z0-9-]{1,62}$/;
const CONTROL_CHARS = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/;

const text = (min: number, max: number) =>
  z.string().trim().min(min).max(max).refine((v) => !CONTROL_CHARS.test(v), "使えない文字が含まれています");

const httpUrl = z
  .string()
  .trim()
  .max(200)
  .url()
  .refine((u) => /^https?:\/\//i.test(u), "http:// または https:// で始まるURLを入力してください");

export const RegisterBody = z.object({
  name: text(2, 40),
  contactEmail: z.string().trim().max(200).email().optional().or(z.literal("").transform(() => undefined)),
  website: httpUrl.optional().or(z.literal("").transform(() => undefined)),
  acceptTerms: z.literal(true, { error: "開発者向け規約への同意が必要です" }),
});
export type RegisterInput = z.infer<typeof RegisterBody>;

export const CreateAppBody = z.object({
  packageName: z.string().trim().max(150).regex(PACKAGE_RE, "パッケージ名は com.example.myapp のような形式で入力してください"),
  slug: z.string().trim().toLowerCase().regex(SLUG_RE, "URL用の名前は、半角英小文字・数字・ハイフンの2〜63文字にしてください").optional().or(z.literal("").transform(() => undefined)),
  name: text(1, 60),
  shortDesc: text(0, 200).default(""),
  description: text(0, 4000).default(""),
  categoryId: z.number().int().positive().nullable().optional(),
});
export type CreateAppInput = z.infer<typeof CreateAppBody>;

export const UpdateAppBody = z
  .object({
    name: text(1, 60),
    shortDesc: text(0, 200),
    description: text(0, 4000),
    categoryId: z.number().int().positive().nullable(),
  })
  .partial()
  .refine((v) => Object.keys(v).length > 0, "更新する項目がありません");

export const CreateReleaseBody = z.object({ releaseNotes: text(0, 2000).default("") });

/** 入力を検証したときの、利用者に見せるエラー文言(最初の1つ) */
export function firstIssue(error: z.ZodError): string {
  return error.issues[0]?.message ?? "入力が正しくありません";
}

/** 使えない名前・パッケージ名・slug のときの、利用者に見せる理由。使えるなら null */
export function nameProblem(kind: "developer" | "app", name: string): string | null {
  const w = findReservedWord(name);
  return w ? `「${w}」を含む名前は、他社や運営と紛らわしいため使えません` : null;
}

export function packageProblem(packageName: string): string | null {
  return isReservedPackage(packageName) ? "このパッケージ名は、OS・他社・運営のものとして予約されているため使えません。自分で管理しているドメイン(逆順)などを使ってください" : null;
}

export function slugProblem(slug: string): string | null {
  return RESERVED_SLUGS.has(slug) ? "この URL 用の名前は使えません" : null;
}

// ---------------------------------------------------------------- 認証

export interface SessionUser {
  id: string;
  email: string | undefined;
  emailConfirmed: boolean;
}

export interface DeveloperRow {
  user_id: string;
  name: string;
  contact_email: string;
  website: string | null;
  status: "pending" | "approved" | "suspended";
  review_mode: "auto" | "manual";
  terms_accepted_at: string | null;
}

/** ログイン中のユーザー(Cookieのセッション)。ログインしていなければ null */
export async function getSessionUser(): Promise<SessionUser | null> {
  const supabase = await sessionClient();
  const { data } = await supabase.auth.getUser();
  const user = data.user;
  if (!user) return null;
  return { id: user.id, email: user.email, emailConfirmed: !!user.email_confirmed_at };
}

/** BANされていないか(BANされたユーザーは、開発者としての操作もできない) */
async function isBanned(svc: SupabaseClient, userId: string): Promise<boolean> {
  const { data } = await svc.from("profiles").select("banned_at").eq("id", userId).maybeSingle();
  return !!data?.banned_at;
}

/** ログイン中の、BANされていないユーザーを求める。開発者の登録などで使う */
export async function requireUser(): Promise<{ user: SessionUser; svc: SupabaseClient } | { response: Response }> {
  const user = await getSessionUser();
  if (!user) return { response: jsonError("ログインしてください", 401) };
  const svc = serviceClient();
  if (await isBanned(svc, user.id)) return { response: jsonError("このアカウントは利用を停止されています", 403) };
  return { user, svc };
}

/**
 * 承認済みの開発者としてログインしていることを求める。
 * この後にだけ service_role のクライアントを使う(所有者の確認は、各ルートで、アプリの developer_id と照合する)。
 */
export async function requireDeveloper(): Promise<{ user: SessionUser; developer: DeveloperRow; svc: SupabaseClient } | { response: Response }> {
  const auth = await requireUser();
  if ("response" in auth) return auth;
  const { data } = await auth.svc
    .from("developers")
    .select("user_id, name, contact_email, website, status, review_mode, terms_accepted_at")
    .eq("user_id", auth.user.id)
    .maybeSingle();
  const developer = data as DeveloperRow | null;
  if (!developer) return { response: jsonError("開発者として登録してください", 403, { code: "not_developer" }) };
  if (developer.status === "suspended") return { response: jsonError("この開発者アカウントは停止されています。運営にお問い合わせください", 403, { code: "suspended" }) };
  if (developer.status !== "approved") return { response: jsonError("開発者アカウントはまだ承認されていません", 403, { code: "pending" }) };
  return { user: auth.user, developer, svc: auth.svc };
}

/** 自分のアプリを取得する。他人のアプリ・存在しないアプリは、どちらも null(存在を知らせない) */
export async function loadOwnApp<T extends string = "id, slug, name, package_name, status, developer_id">(
  svc: SupabaseClient,
  appId: string,
  userId: string,
  columns: T = "id, slug, name, package_name, status, developer_id" as T,
) {
  if (!/^[0-9a-f-]{36}$/i.test(appId)) return null;
  const { data } = await svc.from("apps").select(columns).eq("id", appId).eq("developer_id", userId).maybeSingle();
  return data as Record<string, unknown> | null;
}
