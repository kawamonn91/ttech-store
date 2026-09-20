import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? "https://store.kawamonn.com"),
  title: { default: "T-tech Store", template: "%s | T-tech Store" },
  description: "T-tech のAndroidアプリをまとめて配布するストア。専用アプリからインストール・更新ができます。",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ja" className="h-full antialiased">
      <body className="min-h-full flex flex-col">
        <header className="border-b border-border bg-surface">
          <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
            <Link href="/" className="text-lg font-bold">
              T-tech Store
            </Link>
            <nav className="flex items-center gap-4 text-sm">
              <Link href="/search" className="hover:text-brand">
                検索
              </Link>
              <Link href="/download" className="rounded-full bg-brand px-3 py-1.5 font-semibold text-brand-foreground">
                ストアアプリを入手
              </Link>
            </nav>
          </div>
        </header>
        <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">{children}</main>
        <footer className="border-t border-border py-6 text-center text-sm text-muted">
          <p className="space-x-4">
            <a href="https://kawamonn.com" className="hover:text-brand">
              kawamonn.com
            </a>
            <Link href="/legal/terms" className="hover:text-brand">
              利用規約
            </Link>
            <Link href="/legal/privacy" className="hover:text-brand">
              プライバシーポリシー
            </Link>
          </p>
          <p className="mt-1">© T-tech</p>
        </footer>
      </body>
    </html>
  );
}
