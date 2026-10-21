// ブラウザの画面から、このサイトの API を呼ぶための道具。

export type ApiResult<T> = { ok: true; data: T } | { ok: false; error: string };

/**
 * API を呼んで、応答の JSON を受け取る。失敗したときは、サーバーが返した利用者向けの文言(error)、
 * 無ければ fallbackError を返す。json を渡すと JSON として送り、body(FormData など)はそのまま送る。
 */
export async function callApi<T = Record<string, unknown>>(
  path: string,
  {
    method = "POST",
    json,
    body,
    fallbackError = "操作に失敗しました",
  }: { method?: string; json?: unknown; body?: BodyInit; fallbackError?: string } = {},
): Promise<ApiResult<T>> {
  let res: Response;
  try {
    res = await fetch(path, json === undefined ? { method, body } : { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(json) });
  } catch {
    return { ok: false, error: "通信に失敗しました。接続を確認して、もう一度お試しください" };
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) return { ok: false, error: String(data.error ?? fallbackError) };
  return { ok: true, data: data as T };
}

/** 署名付きURLへファイルを直接 PUT する。進み具合(0〜1)を出すため、fetch ではなく XMLHttpRequest を使う */
export function putWithProgress(
  url: string,
  file: File,
  onProgress: (ratio: number) => void,
  { contentType, networkError }: { contentType?: string; networkError: string },
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", url);
    if (contentType) xhr.setRequestHeader("Content-Type", contentType);
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(e.loaded / e.total);
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`アップロードに失敗しました (${xhr.status})`)));
    xhr.onerror = () => reject(new Error(networkError));
    xhr.send(file);
  });
}
