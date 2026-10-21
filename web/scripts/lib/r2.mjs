// CLIスクリプトから R2 へ直接アップロードする。管理コンソールと違い、スクリプトは R2 の秘密鍵を
// 直接持っているので、署名付きURLを経由せず、その場で署名して PUT する。

import { AwsClient } from "aws4fetch";
import { Agent, fetch as ufetch } from "undici";
import { need } from "./cli-env.mjs";

export class R2 {
  /**
   * @param {Record<string, string | undefined>} env R2_ACCOUNT_ID・R2_ACCESS_KEY_ID・R2_SECRET_ACCESS_KEY・R2_BUCKET を含む
   * @param {{ expiresSeconds?: number, noTimeout?: boolean }} options noTimeout: 大きいファイル用に、転送の時間制限を外す
   */
  constructor(env, { expiresSeconds = 300, noTimeout = false } = {}) {
    this.client = new AwsClient({
      accessKeyId: need(env, "R2_ACCESS_KEY_ID"),
      secretAccessKey: need(env, "R2_SECRET_ACCESS_KEY"),
      service: "s3",
      region: "auto",
    });
    this.base = `https://${need(env, "R2_ACCOUNT_ID")}.r2.cloudflarestorage.com/${need(env, "R2_BUCKET")}`;
    this.expiresSeconds = expiresSeconds;
    // VPN/ゼロトラストクライアントの影響で IPv6 経路がタイムアウトすることがあるため、
    // R2 向けの通信だけ IPv4 接続に固定する(詳細: web/src/lib/r2.ts の同様のコメント)
    this.agent = new Agent({ connect: { family: 4 }, ...(noTimeout ? { headersTimeout: 0, bodyTimeout: 0 } : {}) });
  }

  async put(key, bytes) {
    const url = new URL(`${this.base}/${key.split("/").map(encodeURIComponent).join("/")}`);
    url.searchParams.set("X-Amz-Expires", String(this.expiresSeconds));
    const signed = await this.client.sign(url.toString(), { method: "PUT", aws: { signQuery: true } });
    const res = await ufetch(signed.url, { method: "PUT", body: bytes, dispatcher: this.agent });
    if (!res.ok) throw new Error(`R2 PUT failed: ${res.status} ${await res.text()}`);
  }
}
