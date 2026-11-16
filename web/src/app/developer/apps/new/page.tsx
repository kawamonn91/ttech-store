import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { getSessionUser } from "@/lib/developer";
import { serviceClient } from "@/lib/supabase";
import { NewAppForm } from "./NewAppForm";
import { Breadcrumbs } from "@/components/Breadcrumbs";

export const metadata: Metadata = { title: "アプリを登録" };
export const dynamic = "force-dynamic";

export default async function NewAppPage() {
  const user = await getSessionUser();
  if (!user) redirect("/login?next=/developer/apps/new");

  const svc = serviceClient();
  const [{ data: developer }, { data: categories }] = await Promise.all([
    svc.from("developers").select("status").eq("user_id", user.id).maybeSingle(),
    svc.from("categories").select("id, name").order("sort_order"),
  ]);
  if (developer?.status !== "approved") redirect("/developer");

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div>
        <Breadcrumbs items={[{ label: "開発者ダッシュボード", href: "/developer" }, { label: "アプリを登録" }]} />
        <h1 className="mt-1 text-xl font-bold">アプリを登録</h1>
        <p className="text-sm text-muted">まず、アプリの基本情報を登録します。次の画面で、アイコン・スクリーンショットとAPKをアップロードします。</p>
      </div>
      <div className="rounded-xl border border-border bg-surface p-5">
        <NewAppForm categories={categories ?? []} />
      </div>
    </div>
  );
}
