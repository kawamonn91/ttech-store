import Link from "next/link";
import { serviceClient } from "@/lib/supabase";

const STATUS_LABEL: Record<string, string> = {
  draft: "下書き",
  pending: "審査中",
  published: "公開中",
  suspended: "停止中",
};

export default async function AdminHome() {
  const supabase = serviceClient();
  const { data: apps } = await supabase
    .from("apps")
    .select("id, name, package_name, status, download_count, app_releases(status)")
    .order("created_at", { ascending: false });

  // 承認待ち(検査が終わって公開を待っている)リリースがあるアプリを目立たせる
  const rows = (apps ?? []).map((a) => ({
    ...a,
    waiting: (a.app_releases as { status: string }[]).filter((r) => r.status === "scanned").length,
  }));

  return (
    <>
      <h1 className="mb-4 text-xl font-bold">アプリ一覧</h1>
      {rows.length === 0 ? (
        <p className="text-muted">
          まだアプリがありません。<Link href="/admin/apps/new" className="text-brand underline">新規アプリ</Link>から登録してください。
        </p>
      ) : (
        <ul className="space-y-2">
          {rows.map((a) => (
            <li key={a.id}>
              <Link
                href={`/admin/apps/${a.id}`}
                className="flex items-center justify-between rounded-xl border border-border bg-surface px-4 py-3 hover:border-brand"
              >
                <div className="min-w-0">
                  <div className="font-semibold">{a.name}</div>
                  <div className="truncate text-xs text-muted">{a.package_name}</div>
                </div>
                <div className="flex shrink-0 items-center gap-3 text-sm">
                  {a.waiting > 0 && <span className="rounded-full bg-brand px-2 py-0.5 text-xs text-brand-foreground">承認待ち {a.waiting}</span>}
                  <span className="text-muted">{a.download_count} DL</span>
                  <span>{STATUS_LABEL[a.status] ?? a.status}</span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
