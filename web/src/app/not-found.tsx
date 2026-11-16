import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md py-12 text-center">
      <h1 className="text-xl font-bold">ページが見つかりません</h1>
      <p className="mt-2 text-sm text-muted">URL が変わったか、公開を終えたページです。</p>
      <p className="mt-6 flex flex-wrap justify-center gap-4 text-sm">
        <Link href="/" className="text-brand underline underline-offset-2">
          アプリの一覧へ戻る
        </Link>
        <Link href="/search" className="text-muted underline decoration-border underline-offset-2 hover:text-brand">
          アプリを探す
        </Link>
      </p>
    </div>
  );
}
