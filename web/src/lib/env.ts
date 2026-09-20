/**
 * 環境変数の取得口。値が無いときはどの変数が足りないかが分かるエラーにする。
 * ビルド時ではなく実際に使う時点(リクエスト時)に評価するため、関数で包んでいる。
 */
function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`環境変数 ${name} が設定されていません`);
  return value;
}

export const env = {
  supabaseUrl: () => required("NEXT_PUBLIC_SUPABASE_URL"),
  supabaseAnonKey: () => required("NEXT_PUBLIC_SUPABASE_ANON_KEY"),
  supabaseServiceRoleKey: () => required("SUPABASE_SERVICE_ROLE_KEY"),

  r2AccountId: () => required("R2_ACCOUNT_ID"),
  r2AccessKeyId: () => required("R2_ACCESS_KEY_ID"),
  r2SecretAccessKey: () => required("R2_SECRET_ACCESS_KEY"),
  r2Bucket: () => required("R2_BUCKET"),

  /** GitHub Actions(APK検査)からの結果通知を検証する共有シークレット */
  scanWebhookSecret: () => required("SCAN_WEBHOOK_SECRET"),
  /** 検査ワークフローを起動するための GitHub トークン(repo の Actions:write 権限) */
  githubDispatchToken: () => process.env.GITHUB_DISPATCH_TOKEN ?? "",
  githubRepo: () => process.env.GITHUB_REPO ?? "",

  /** ストアアプリ自身の packageName(/download でだけ直接ダウンロードさせる) */
  storePackageName: () => process.env.STORE_PACKAGE_NAME ?? "com.kawamonn.store",
  /** ダウンロードの重複カウント判定に使う端末IDのハッシュ用ソルト */
  deviceHashSalt: () => required("DEVICE_HASH_SALT"),
  siteUrl: () => process.env.NEXT_PUBLIC_SITE_URL ?? "https://store.kawamonn.com",
};
