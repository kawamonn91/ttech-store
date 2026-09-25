import { AwsClient } from "aws4fetch";
import { Agent, fetch as undiciFetch } from "undici";
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

/**
 * サーバーからR2への直接リクエスト(headObject)専用に IPv4 接続を強制する。
 * 一部のネットワーク環境(VPN/ゼロトラストクライアント等)で、R2のIPv6経路がブロックされて
 * Node の fetch がタイムアウトすることがあるため、その回避策として明示的に使う。
 */
const r2Agent = new Agent({ connect: { family: 4 } });

function objectUrl(key: string): URL {
  const encoded = key.split("/").map(encodeURIComponent).join("/");
  return new URL(`https://${env.r2AccountId()}.r2.cloudflarestorage.com/${env.r2Bucket()}/${encoded}`);
}

async function presign(method: "GET" | "PUT", key: string, expiresSeconds: number) {
  const url = objectUrl(key);
  url.searchParams.set("X-Amz-Expires", String(expiresSeconds));
  const signed = await client().sign(url.toString(), {
    method,
    aws: { signQuery: true },
  });
  return signed.url;
}

export const DOWNLOAD_URL_TTL_SECONDS = 15 * 60;
export const UPLOAD_URL_TTL_SECONDS = 30 * 60;

export function presignDownload(key: string): Promise<string> {
  return presign("GET", key, DOWNLOAD_URL_TTL_SECONDS);
}

/**
 * 管理コンソールからの直接アップロード用。署名されるのは host のみで、Content-Type は署名対象にならない。
 * 中身が本当に正しいAPKかどうかは、アップロード後の検査(workers/scan)で確認する。
 */
export function presignUpload(key: string): Promise<string> {
  return presign("PUT", key, UPLOAD_URL_TTL_SECONDS);
}

/** アップロード済みか確認し、サイズを返す。無ければ null */
export async function headObject(key: string): Promise<{ size: number } | null> {
  const url = await client().sign(objectUrl(key).toString(), { method: "HEAD", aws: { signQuery: true } });
  const res = await undiciFetch(url.url, { method: "HEAD", dispatcher: r2Agent });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`R2 HEAD failed: ${res.status}`);
  return { size: Number(res.headers.get("content-length") ?? 0) };
}

/** APK のオブジェクトキー。推測されにくいよう release id を含める */
export function apkKey(appId: string, releaseId: string): string {
  return `apk/${appId}/${releaseId}.apk`;
}

/**
 * 開発者がアップロードする先のキー。検査・公開に使うキー(apkKey)とは別にしてある。
 * 署名付きURLは失効させられないので、検査の後に別のファイルへ差し替えられないよう、
 * アップロード完了の通知を受けたら、中身を apkKey に複製して、このキーは削除する
 * (開発者が持っている署名付きURLは、もう使われないキーにしか書き込めなくなる)。
 */
export function uploadKey(appId: string, releaseId: string): string {
  return `upload/${appId}/${releaseId}.apk`;
}

/** サーバー側でオブジェクトを複製する(R2 の CopyObject)。複製元が無ければ false */
export async function copyObject(fromKey: string, toKey: string): Promise<boolean> {
  const source = `/${env.r2Bucket()}/${fromKey.split("/").map(encodeURIComponent).join("/")}`;
  const signed = await client().sign(objectUrl(toKey).toString(), { method: "PUT", headers: { "x-amz-copy-source": source } });
  const res = await undiciFetch(signed.url, { method: "PUT", headers: Object.fromEntries(signed.headers), dispatcher: r2Agent });
  if (res.status === 404) return false;
  if (!res.ok) throw new Error(`R2 COPY failed: ${res.status}`);
  return true;
}

/** オブジェクトを削除する。無くてもエラーにしない */
export async function deleteObject(key: string): Promise<void> {
  const signed = await client().sign(objectUrl(key).toString(), { method: "DELETE" });
  const res = await undiciFetch(signed.url, { method: "DELETE", headers: Object.fromEntries(signed.headers), dispatcher: r2Agent });
  if (!res.ok && res.status !== 404) throw new Error(`R2 DELETE failed: ${res.status}`);
}
