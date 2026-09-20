import { notFound } from "next/navigation";
import { mediaUrl } from "@/lib/dto";
import { serviceClient } from "@/lib/supabase";
import { AppForm } from "../../AppForm";
import { updateApp } from "../../actions";
import { AppStatusControls } from "./AppStatusControls";
import { MediaManager } from "./MediaManager";
import { ReleaseManager, type ReleaseView } from "./ReleaseManager";

export default async function AdminAppPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const supabase = serviceClient();

  const [{ data: app }, { data: categories }, { data: releases }] = await Promise.all([
    supabase.from("apps").select("*").eq("id", id).maybeSingle(),
    supabase.from("categories").select("id, name").order("sort_order"),
    supabase
      .from("app_releases")
      .select("id, version_name, version_code, status, apk_size, sha256, signing_cert_sha256, permissions, release_notes, scan_result, created_at")
      .eq("app_id", id)
      .order("created_at", { ascending: false }),
  ]);
  if (!app) notFound();

  return (
    <div className="space-y-10">
      <header>
        <h1 className="text-xl font-bold">{app.name}</h1>
        <p className="text-sm text-muted">
          {app.package_name} ・ /apps/{app.slug}
        </p>
        <div className="mt-3">
          <AppStatusControls appId={app.id} status={app.status} featured={app.featured} />
        </div>
      </header>

      <section>
        <h2 className="mb-3 text-lg font-bold">リリース(APK)</h2>
        <ReleaseManager appId={app.id} releases={(releases ?? []) as ReleaseView[]} lockedCert={app.signing_cert_sha256} />
      </section>

      <section>
        <h2 className="mb-3 text-lg font-bold">アイコン・スクリーンショット</h2>
        <MediaManager
          appId={app.id}
          iconUrl={mediaUrl(app.icon_path)}
          screenshots={(app.screenshots as string[]).map((path) => ({ path, url: mediaUrl(path)! }))}
        />
      </section>

      <section>
        <h2 className="mb-3 text-lg font-bold">基本情報</h2>
        <AppForm
          editing
          action={updateApp.bind(null, app.id)}
          categories={categories ?? []}
          initial={{
            name: app.name,
            shortDesc: app.short_desc,
            description: app.description,
            categoryId: app.category_id,
          }}
        />
      </section>
    </div>
  );
}
