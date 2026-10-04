#!/usr/bin/env node
// 診断用(読み取り専用): admin_only なアプリの登録状況・公開中リリースの有無を確認する。
import { loadEnvLocal, need } from "./lib/cli-env.mjs";

async function main() {
  loadEnvLocal();
  const url = need(process.env, "NEXT_PUBLIC_SUPABASE_URL");
  const key = need(process.env, "SUPABASE_SERVICE_ROLE_KEY");
  const headers = { apikey: key, Authorization: `Bearer ${key}` };

  const appsRes = await fetch(`${url}/rest/v1/apps?admin_only=eq.true&select=id,slug,name,package_name,status,admin_only`, { headers });
  const apps = await appsRes.json();
  console.log("admin_only=true のアプリ:", JSON.stringify(apps, null, 2));

  for (const app of apps) {
    const relRes = await fetch(
      `${url}/rest/v1/app_releases?app_id=eq.${app.id}&select=id,status,version_name,version_code&order=version_code.desc`,
      { headers },
    );
    const releases = await relRes.json();
    console.log(`  ${app.slug} のリリース:`, JSON.stringify(releases, null, 2));
  }
}

main().catch((e) => {
  console.error(e.message || e);
  process.exit(1);
});
