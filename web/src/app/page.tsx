import Link from "next/link";
import { AppGrid } from "@/components/AppCard";
import { getHome, listApps } from "@/lib/catalog";

export const dynamic = "force-dynamic";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-8">
      <h2 className="mb-3 text-xl font-bold">{title}</h2>
      {children}
    </section>
  );
}

export default async function HomePage() {
  const [{ featured, newest, popular }, { total }] = await Promise.all([getHome(), listApps({ limit: 1 })]);
  const empty = featured.length + newest.length + popular.length === 0;

  return (
    <>
      <div className="mb-8 rounded-3xl bg-brand p-6 text-brand-foreground">
        <h1 className="text-2xl font-bold">T-tech のAndroidアプリ、ここに集合。</h1>
        <p className="mt-2 text-sm opacity-90">
          広告なし・シンプルなアプリをまとめて配布しています。インストールと更新は専用のストアアプリで。
        </p>
        <Link
          href="/download"
          className="mt-4 inline-block rounded-full bg-surface px-5 py-2 text-sm font-semibold text-brand"
        >
          ストアアプリを入手
        </Link>
      </div>

      {empty && <p className="text-muted">公開中のアプリはまだありません。</p>}
      {featured.length > 0 && (
        <Section title="おすすめ">
          <AppGrid apps={featured} />
        </Section>
      )}
      {newest.length > 0 && (
        <Section title="新着">
          <AppGrid apps={newest} />
        </Section>
      )}
      {popular.length > 0 && (
        <Section title="人気">
          <AppGrid apps={popular} />
        </Section>
      )}
      {total > 0 && (
        <div className="mb-8 text-center">
          <Link href="/search" className="inline-block rounded-full border border-border bg-surface px-6 py-2.5 text-sm font-semibold hover:text-brand">
            すべてのアプリを見る({total}件)
          </Link>
        </div>
      )}
    </>
  );
}
