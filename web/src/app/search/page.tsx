import type { Metadata } from "next";
import Link from "next/link";
import { AppGrid } from "@/components/AppCard";
import { listApps, listCategories } from "@/lib/catalog";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "検索" };

export default async function SearchPage({ searchParams }: PageProps<"/search">) {
  const sp = await searchParams;
  const q = typeof sp.q === "string" ? sp.q : "";
  const category = typeof sp.category === "string" ? sp.category : "";

  const [categories, result] = await Promise.all([
    listCategories(),
    listApps({ q, category: category || undefined, sort: "popular", limit: 100 }),
  ]);

  const chip = (active: boolean) =>
    `rounded-md border px-2.5 py-1 text-xs font-medium transition ${
      active
        ? "border-brand bg-brand text-brand-foreground"
        : "border-border bg-surface text-muted hover:border-foreground/40 hover:text-foreground"
    }`;
  const href = (c: string) => `/search?${new URLSearchParams({ ...(q ? { q } : {}), ...(c ? { category: c } : {}) })}`;

  return (
    <>
      <form action="/search" className="mb-4 flex gap-2">
        {category && <input type="hidden" name="category" value={category} />}
        <input
          name="q"
          defaultValue={q}
          placeholder="アプリを検索..."
          className="min-w-0 flex-1 rounded-md border border-border bg-surface px-3.5 py-2 text-sm outline-none focus:border-foreground"
        />
        <button className="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-brand-foreground transition hover:opacity-90">
          検索
        </button>
      </form>
      <div className="mb-6 flex flex-wrap gap-1.5">
        <Link href={href("")} className={chip(!category)}>
          すべて
        </Link>
        {categories.map((c) => (
          <Link key={c.slug} href={href(c.slug)} className={chip(category === c.slug)}>
            {c.name}
          </Link>
        ))}
      </div>
      {result.items.length === 0 ? (
        <p className="text-sm text-muted">見つかりませんでした。</p>
      ) : (
        <>
          <p className="mb-3 font-mono text-xs text-muted">{result.total}件</p>
          <AppGrid apps={result.items} />
        </>
      )}
    </>
  );
}
