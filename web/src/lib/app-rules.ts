// アプリの登録内容の決まり(利用枠・パッケージ名と URL 用の名前の形式)。
// API・管理コンソールのサーバー側と、ブラウザの入力欄の両方から使うので、サーバー専用のものを import しない。

/** 1人の開発者あたりの利用枠 */
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

/** パッケージ名(applicationId)。例: com.example.myapp */
export const PACKAGE_RE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/;

/** URL 用の名前(/apps/<slug>)。半角英小文字・数字・ハイフンの2〜63文字 */
export const SLUG_RE = /^[a-z0-9][a-z0-9-]{1,62}$/;

/** よくある語だけの部分(app・com など)は URL 用の名前に向かないので使わない */
const GENERIC_SEGMENTS = new Set(["app", "android", "mobile", "client", "www", "com", "co", "jp", "org", "net", "io"]);

/** パッケージ名の後ろの部分から、そのアプリらしい URL 用の名前の案を作る。使える部分が無ければ null */
export function slugFromPackage(packageName: string): string | null {
  const parts = packageName.toLowerCase().split(".").filter(Boolean);
  for (let i = parts.length - 1; i >= 0; i--) {
    const seg = parts[i].replace(/_/g, "-").replace(/[^a-z0-9-]/g, "").replace(/^-+|-+$/g, "");
    if (seg.length >= 2 && !GENERIC_SEGMENTS.has(seg)) return seg.slice(0, 40);
  }
  return null;
}
