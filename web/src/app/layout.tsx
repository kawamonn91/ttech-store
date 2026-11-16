import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";
import { env } from "@/lib/env";
import { sessionClient } from "@/lib/supabase";
import { BrandMenu } from "@/components/BrandMenu";

export const metadata: Metadata = {
  metadataBase: new URL(env.siteUrl()),
  title: { default: "T-tech Store", template: "%s | T-tech Store" },
  description: "T-tech のAndroidアプリをまとめて配布するストア。専用アプリからインストール・更新ができます。",
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  const supabase = await sessionClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  return (
    <html lang="ja" className="h-full antialiased">
      <body className="min-h-full flex flex-col">
        <header className="border-b border-border bg-surface">
          <div className="mx-auto flex h-14 max-w-5xl items-center justify-between gap-4 px-4 sm:px-6">
            <BrandMenu />
            <nav className="flex items-center gap-4 text-sm whitespace-nowrap">
              <Link href="/search" className="text-muted transition hover:text-foreground">
                検索
              </Link>
              <Link href="/developer" className="hidden text-muted transition hover:text-foreground sm:inline">
                開発者向け
              </Link>
              <Link href={user ? "/account" : "/login"} className="text-muted transition hover:text-foreground">
                {user ? "マイページ" : "ログイン"}
              </Link>
              <Link
                href="/download"
                className="hidden rounded-md bg-brand px-3 py-1.5 text-xs font-semibold text-brand-foreground transition hover:opacity-90 sm:inline-block"
              >
                ストアアプリを入手
              </Link>
            </nav>
          </div>
        </header>
        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8 sm:px-6">{children}</main>
        <footer className="border-t border-border bg-surface py-5 text-xs text-muted">
          <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-4 sm:px-6">
            <p>© T-tech</p>
            <p className="flex flex-wrap gap-4">
              <a href="https://www.kawamonn.com" className="transition hover:text-foreground">
                T-tech のトップ
              </a>
              <Link href="/developer" className="transition hover:text-foreground">
                開発者向け
              </Link>
              <Link href="/legal/terms" className="transition hover:text-foreground">
                利用規約
              </Link>
              <Link href="/legal/developer" className="transition hover:text-foreground">
                開発者向け規約
              </Link>
              <Link href="/legal/privacy" className="transition hover:text-foreground">
                プライバシーポリシー
              </Link>
            </p>
          </div>
        </footer>
      </body>
    </html>
  );
}
