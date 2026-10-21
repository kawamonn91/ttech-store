// 画面に出す数値・権限名の整形。サーバーとブラウザの両方から使う。

/** ダウンロード数の表示用。1万以上は「1.2万」の形にする */
export function formatCount(n: number): string {
  if (n < 1000) return String(n);
  if (n < 10000) return n.toLocaleString("ja-JP");
  return `${(n / 10000).toFixed(1).replace(/\.0$/, "")}万`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`;
}

/** "android.permission.CAMERA" → "CAMERA" */
export function shortPermission(name: string): string {
  return name.replace(/^android\.permission\./, "");
}
