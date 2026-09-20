import { serviceClient } from "@/lib/supabase";
import { AppForm } from "../../AppForm";
import { createApp } from "../../actions";

export default async function NewAppPage() {
  const { data: categories } = await serviceClient().from("categories").select("id, name").order("sort_order");
  return (
    <>
      <h1 className="mb-4 text-xl font-bold">新規アプリ</h1>
      <p className="mb-4 text-sm text-muted">まず基本情報を登録します。アイコン・スクリーンショット・APKは次の画面で追加します。</p>
      <AppForm action={createApp} categories={categories ?? []} />
    </>
  );
}
