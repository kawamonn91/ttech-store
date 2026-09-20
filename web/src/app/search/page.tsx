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
    listApps({ q, category: category || undefined, sort: "popular", limit: 60 }),
  ]);

  const chip = (active: boolean) =>
    `rounded-full border px-3 py-1 text-sm ${active ? "border-brand bg-brand text-brand-foreground" : "border-border bg-surface"}`;
  const href = (c: string) => `/search?${new URLSearchParams({ ...(q ? { q } : {}), ...(c ? { category: c } : {}) })}`;

  return (
    <>
      <form action="/search" className="mb-4 flex gap-2">
        {category && <input type="hidden" name="category" value={category} />}
        <input
          name="q"
          defaultValue={q}
          placeholder="アプリを検索"
          className="min-w-0 flex-1 rounded-xl border border-border bg-surface px-4 py-2"
        />
        <button className="rounded-xl bg-brand px-5 py-2 font-semibold text-brand-foreground">検索</button>
      </form>
      <div className="mb-6 flex flex-wrap gap-2">
        <Link href={href("")} className={chip(!category)}>
          すべて
        </Link>
        {categories.map((c) => (
          <Link key={c.slug} href={href(c.slug)} className={chip(category === c.slug)}>
            {c.name}
          </Link>
        ))}
      </div>
      {result.items.length === 0 ? <p className="text-muted">見つかりませんでした。</p> : <AppGrid apps={result.items} />}
    </>
  );
}
