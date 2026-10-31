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
      <h2 className="mb-3 text-xl font-bold">{title}</h2>
      {children}
    </section>
  );
}

export default async function HomePage() {
  const { featured, newest, popular, total } = await loadHome();
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

      <Section title="Windows 版ツール">
        <Link
          href="/tools/pdf-ja"
          className="block rounded-2xl border border-border bg-surface p-5 hover:border-brand"
        >
          <p className="font-bold">PDF・PowerPoint 日本語化ツール</p>
          <p className="mt-1 text-sm text-muted">
            英語の PDF・PowerPoint を、見た目を保ったまま日本語に。はみ出しは自動で調整し、手で直すこともできます。
          </p>
        </Link>
      </Section>

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
