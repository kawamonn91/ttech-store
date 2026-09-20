import { AwsClient } from "aws4fetch";
import { env } from "./env";

/**
 * Cloudflare R2(S3互換)への署名付き URL を発行する。
 * バケットは非公開のままにし、APK へのアクセスは必ずここで発行した期限付きURLを経由させる
 * (課金導入後の権限チェックや、DL数の集計をすり抜けられないようにするため)。
 */
function client() {
  return new AwsClient({
    accessKeyId: env.r2AccessKeyId(),
    secretAccessKey: env.r2SecretAccessKey(),
    service: "s3",
    region: "auto",
  });
}

function objectUrl(key: string): URL {
  const encoded = key.split("/").map(encodeURIComponent).join("/");
  return new URL(`https://${env.r2AccountId()}.r2.cloudflarestorage.com/${env.r2Bucket()}/${encoded}`);
}

async function presign(method: "GET" | "PUT", key: string, expiresSeconds: number, headers?: Record<string, string>) {
  const url = objectUrl(key);
  url.searchParams.set("X-Amz-Expires", String(expiresSeconds));
  const signed = await client().sign(url.toString(), {
    method,
    headers,
    aws: { signQuery: true },
  });
  return signed.url;
}

export const DOWNLOAD_URL_TTL_SECONDS = 15 * 60;
export const UPLOAD_URL_TTL_SECONDS = 30 * 60;

export function presignDownload(key: string): Promise<string> {
  return presign("GET", key, DOWNLOAD_URL_TTL_SECONDS);
}

export function presignUpload(key: string, contentType = "application/vnd.android.package-archive"): Promise<string> {
  return presign("PUT", key, UPLOAD_URL_TTL_SECONDS, { "Content-Type": contentType });
}

/** アップロード済みか確認し、サイズを返す。無ければ null */
export async function headObject(key: string): Promise<{ size: number } | null> {
  const res = await client().fetch(objectUrl(key).toString(), { method: "HEAD" });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`R2 HEAD failed: ${res.status}`);
  return { size: Number(res.headers.get("content-length") ?? 0) };
}

/** APK のオブジェクトキー。推測されにくいよう release id を含める */
export function apkKey(appId: string, releaseId: string): string {
  return `apk/${appId}/${releaseId}.apk`;
}
