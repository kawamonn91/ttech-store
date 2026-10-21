// CLIスクリプトから Supabase の REST API(テーブル・Storage)を service_role で使う。
// supabase-js を増やさず、必要な操作だけを fetch で呼ぶ。

import { need } from "./cli-env.mjs";

export class SupabaseAdmin {
  /** NEXT_PUBLIC_SUPABASE_URL・SUPABASE_SERVICE_ROLE_KEY から作る */
  static fromEnv(env = process.env) {
    return new SupabaseAdmin(need(env, "NEXT_PUBLIC_SUPABASE_URL"), need(env, "SUPABASE_SERVICE_ROLE_KEY"));
  }

  constructor(url, serviceRoleKey) {
    this.url = url;
    this.auth = { apikey: serviceRoleKey, Authorization: `Bearer ${serviceRoleKey}` };
  }

  async request(path, init = {}) {
    const res = await fetch(`${this.url}${path}`, { ...init, headers: { ...this.auth, ...(init.headers ?? {}) } });
    const text = await res.text();
    if (!res.ok) throw new Error(`Supabase ${init.method ?? "GET"} ${path} -> ${res.status}: ${text}`);
    return text ? JSON.parse(text) : null;
  }

  /** query は PostgREST の形(例: `slug=eq.xxx&select=id,name`)。値は呼び出し側で encodeURIComponent すること */
  select(table, query) {
    return this.request(`/rest/v1/${table}?${query}`);
  }

  insert(table, row) {
    return this.request(`/rest/v1/${table}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Prefer: "return=representation" },
      body: JSON.stringify(row),
    });
  }

  update(table, query, patch) {
    return this.request(`/rest/v1/${table}?${query}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", Prefer: "return=minimal" },
      body: JSON.stringify(patch),
    });
  }

  /** Storage にファイルを置く(同じパスがあれば上書き) */
  upload(bucket, objectPath, body, contentType) {
    return this.request(`/storage/v1/object/${bucket}/${objectPath}`, {
      method: "POST",
      headers: { "Content-Type": contentType, "x-upsert": "true" },
      body,
    });
  }

  /** URL用の名前(slug)でアプリを1件引く。無ければ例外 */
  async appBySlug(slug, columns = "id,name") {
    const [app] = await this.select("apps", `slug=eq.${encodeURIComponent(slug)}&select=${columns}`);
    if (!app) throw new Error(`アプリが見つかりません: slug=${slug}`);
    return app;
  }
}
