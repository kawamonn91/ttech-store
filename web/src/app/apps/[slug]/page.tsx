import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AppIcon, formatBytes, formatCount } from "@/components/AppCard";
import { getAppBySlug } from "@/lib/catalog";

export const dynamic = "force-dynamic";

export async function generateMetadata({ params }: PageProps<"/apps/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const app = await getAppBySlug(slug);
  return app ? { title: app.name, description: app.shortDesc } : { title: "アプリが見つかりません" };
}

const SDK_TO_ANDROID: Record<number, string> = {
  26: "8.0", 27: "8.1", 28: "9", 29: "10", 30: "11", 31: "12", 32: "12L", 33: "13", 34: "14", 35: "15", 36: "16",
};

export default async function AppPage({ params }: PageProps<"/apps/[slug]">) {
  const { slug } = await params;
  const app = await getAppBySlug(slug);
  if (!app) notFound();
  const latest = app.latest;

  return (
    <article>
      <div className="flex items-center gap-4">
        <AppIcon url={app.iconUrl} size={96} />
        <div className="min-w-0">
          <h1 className="text-2xl font-bold">{app.name}</h1>
          {app.developerName && <p className="text-sm text-brand">{app.developerName}</p>}
          <p className="mt-1 flex flex-wrap gap-x-4 text-sm text-muted">
            {app.ratingCount > 0 && <span>★ {app.ratingAvg.toFixed(1)}</span>}
            <span>{formatCount(app.downloadCount)} ダウンロード</span>
            {app.category && <span>{app.category.name}</span>}
          </p>
        </div>
      </div>

      {/* ダウンロードはストアアプリ内で完結させる。Webからはアプリを開く/入手する導線のみ */}
      <div className="mt-5 flex flex-wrap gap-3">
        <a
          href={`ttechstore://apps/${app.slug}`}
          className="rounded-full bg-brand px-6 py-2.5 font-semibold text-brand-foreground"
        >
          ストアアプリで開く
        </a>
        <Link href="/download" className="rounded-full border border-border bg-surface px-6 py-2.5 font-semibold">
          ストアアプリを入手
        </Link>
      </div>
      <p className="mt-2 text-xs text-muted">
        インストールと更新は T-tech Store アプリ内で行います(Android スマートフォンで開いてください)。
      </p>

      {app.screenshots.length > 0 && (
        <div className="mt-6 flex gap-3 overflow-x-auto pb-2">
          {app.screenshots.map((url) => (
            // eslint-disable-next-line @next/next/no-img-element
            <img key={url} src={url} alt="スクリーンショット" className="h-72 shrink-0 rounded-xl border border-border" />
          ))}
        </div>
      )}

      <section className="mt-6">
        <h2 className="mb-2 text-lg font-bold">アプリについて</h2>
        <p className="whitespace-pre-wrap">{app.description || app.shortDesc}</p>
      </section>

      {latest && (
        <section className="mt-6">
          <h2 className="mb-2 text-lg font-bold">最新バージョン {latest.versionName}</h2>
          {latest.releaseNotes && <p className="mb-3 whitespace-pre-wrap">{latest.releaseNotes}</p>}
          <dl className="grid grid-cols-[8rem_1fr] gap-y-1 text-sm">
            {latest.apkSize != null && (
              <>
                <dt className="text-muted">サイズ</dt>
                <dd>{formatBytes(latest.apkSize)}</dd>
              </>
            )}
            {latest.minSdk != null && (
              <>
                <dt className="text-muted">必要なOS</dt>
                <dd>Android {SDK_TO_ANDROID[latest.minSdk] ?? `(API ${latest.minSdk})`} 以上</dd>
              </>
            )}
            {latest.permissions.length > 0 && (
              <>
                <dt className="text-muted">必要な権限</dt>
                <dd className="break-words">{latest.permissions.map((p) => p.replace("android.permission.", "")).join(", ")}</dd>
              </>
            )}
          </dl>
        </section>
      )}
    </article>
  );
}
