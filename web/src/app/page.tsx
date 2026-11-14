import Link from "next/link";
import { unstable_cache } from "next/cache";
import { AppGrid } from "@/components/AppCard";
import { getHome } from "@/lib/catalog";

// レイアウトがログイン状態を読むのでページは毎回組み立てる(ビルド時に DB へ繋がないためにも必要)
export const dynamic = "force-dynamic";

// トップの一覧はログイン不要の公開情報で、全員に同じ内容を返す。Web を動かしているサーバーから
// Supabase への問い合わせは、回線の遅延で 1.5〜5 秒かかり、つながりにくいこともあるため、結果を 60 秒キャッシュする。
// 期限が切れた後の最初の表示では直前の内容を返し、裏で取り直す。
// そのため新しく公開したアプリがトップに出るまで、最大 60 秒ほど遅れる(運営者の了承済み)。
const loadHome = unstable_cache(
  async () => {
    return getHome();
  },
  ["home-page"],
  { revalidate: 60 },
);

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-8">
      <h2 className="mb-3 text-base font-bold tracking-tight">{title}</h2>
      {children}
    </section>
  );
}

export default async function HomePage() {
  const { featured, newest, popular, total } = await loadHome();
  const empty = featured.length + newest.length + popular.length === 0;

  return (
    <>
      <div className="mb-8 flex flex-col justify-between gap-4 border-b border-border pb-6 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">T-tech のAndroidアプリ・ツール配布</h1>
          <p className="mt-1.5 text-sm text-muted">
            広告なし・シンプルな実用アプリをまとめて配布しています。インストールと更新は専用のストアアプリで行えます。
          </p>
        </div>
        <div className="shrink-0">
          <Link
            href="/download"
            className="inline-block rounded-md bg-brand px-4 py-2 text-sm font-semibold text-brand-foreground transition hover:opacity-90"
          >
            ストアアプリを入手
          </Link>
        </div>
      </div>

      <Section title="Windows 版ツール">
        <Link
          href="/tools/pdf-ja"
          className="flex items-center justify-between gap-4 rounded-lg border border-border bg-surface p-4 transition hover:border-foreground/40"
        >
          <div>
            <div className="flex items-center gap-2">
              <span className="font-semibold">PDF・PowerPoint 日本語化ツール</span>
              <span className="rounded border border-border bg-background px-1.5 py-0.5 font-mono text-[10px] text-muted">
                Windows 10 / 11
              </span>
            </div>
            <p className="mt-1 text-sm text-muted">
              英語の PDF・PowerPoint を、見た目を保ったまま日本語に。はみ出しは自動で調整し、手で直すこともできます。
            </p>
          </div>
          <span className="shrink-0 font-mono text-xs text-muted">詳細 →</span>
        </Link>
      </Section>

      {empty && <p className="text-sm text-muted">公開中のアプリはまだありません。</p>}
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
        <div className="mb-8 pt-2">
          <Link
            href="/search"
            className="inline-block rounded-md border border-border bg-surface px-4 py-2 text-sm font-medium transition hover:border-foreground/40"
          >
            すべてのアプリを見る ({total}件) →
          </Link>
        </div>
      )}
    </>
  );
}
