import Link from "next/link";
import { redirect } from "next/navigation";
import { getAdminUser } from "@/lib/supabase";
import { LogoutButton } from "./LogoutButton";

export const dynamic = "force-dynamic";

/** 管理コンソール共通: 管理者以外はログイン画面へ */
export default async function ConsoleLayout({ children }: { children: React.ReactNode }) {
  const admin = await getAdminUser();
  if (!admin) redirect("/admin/login");

  return (
    <>
      <div className="mb-6 flex items-center justify-between rounded-xl border border-border bg-surface px-4 py-2 text-sm">
        <nav className="flex gap-4 font-semibold">
          <Link href="/admin">アプリ一覧</Link>
          <Link href="/admin/apps/new">新規アプリ</Link>
        </nav>
        <div className="flex items-center gap-3 text-muted">
          <span>{admin.email}</span>
          <LogoutButton />
        </div>
      </div>
      {children}
    </>
  );
}
