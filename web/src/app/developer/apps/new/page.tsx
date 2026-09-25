import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { serviceClient, sessionClient } from "@/lib/supabase";
import { NewAppForm } from "./NewAppForm";

export const metadata: Metadata = { title: "アプリを登録" };
export const dynamic = "force-dynamic";

export default async function NewAppPage() {
  const supabase = await sessionClient();
  const { data } = await supabase.auth.getUser();
  if (!data.user) redirect("/login?next=/developer/apps/new");

  const svc = serviceClient();
  const [{ data: developer }, { data: categories }] = await Promise.all([
    svc.from("developers").select("status").eq("user_id", data.user.id).maybeSingle(),
    svc.from("categories").select("id, name").order("sort_order"),
  ]);
  if (developer?.status !== "approved") redirect("/developer");

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div>
        <Link href="/developer" className="text-sm text-muted hover:text-brand">
          ← 開発者ダッシュボード
        </Link>
        <h1 className="mt-1 text-xl font-bold">アプリを登録</h1>
        <p className="text-sm text-muted">まず、アプリの基本情報を登録します。次の画面で、アイコン・スクリーンショットとAPKをアップロードします。</p>
      </div>
      <div className="rounded-xl border border-border bg-surface p-5">
        <NewAppForm categories={categories ?? []} />
      </div>
    </div>
  );
}
