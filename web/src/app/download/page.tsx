import type { Metadata } from "next";
import { formatBytes } from "@/components/AppCard";
import { getAppByPackage } from "@/lib/catalog";
import { env } from "@/lib/env";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "ストアアプリを入手" };

export default async function DownloadPage() {
  const app = await getAppByPackage(env.storePackageName());
  const latest = app?.latest ?? null;

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-bold">T-tech Store アプリを入手</h1>
      <p className="mb-6 text-muted">
        T-tech のアプリは、この専用ストアアプリからインストール・更新します。最初の1回だけ、こちらからインストールしてください。
      </p>

      {latest ? (
        <div className="mb-8 rounded-2xl border border-border bg-surface p-5">
          <p className="mb-3 text-sm text-muted">
            バージョン {latest.versionName}
            {latest.apkSize != null && ` ・ ${formatBytes(latest.apkSize)}`} ・ Android {latest.minSdk ?? 8} 以上
          </p>
          <a
            href="/download/store"
            className="inline-block rounded-full bg-brand px-6 py-2.5 font-semibold text-brand-foreground"
          >
            APK をダウンロード
          </a>
        </div>
      ) : (
        <p className="mb-8 rounded-2xl border border-border bg-surface p-5 text-muted">
          ストアアプリは現在準備中です。
        </p>
      )}

      <h2 className="mb-2 text-lg font-bold">インストール手順</h2>
      <ol className="mb-8 list-decimal space-y-2 pl-5">
        <li>上のボタンから APK をダウンロードします(ブラウザに「この種類のファイルは危険」と表示された場合は「ダウンロード」を選びます)。</li>
        <li>ダウンロードしたファイルを開き、「提供元不明のアプリ」の許可を求められたら、お使いのブラウザに対して許可します。</li>
        <li>インストールが終わったら T-tech Store を開きます。以降のアプリのインストール・更新はアプリ内で完結します。</li>
      </ol>

      <h2 className="mb-2 text-lg font-bold">安全性について</h2>
      <ul className="list-disc space-y-2 pl-5 text-sm">
        <li>Google Play 以外から入手するアプリのため、端末に警告が出ることがあります。</li>
        <li>ストアアプリは、ダウンロードしたアプリの改ざん(ハッシュ)と開発者の署名を確認してからインストールします。</li>
        {app && latest && (
          <li>
            このストアアプリのバージョン {latest.versionName} は、詳細ページで公開している内容と一致しているかを確認できます:{" "}
            <a className="text-brand underline" href={`/apps/${app.slug}`}>
              アプリ情報
            </a>
          </li>
        )}
      </ul>
    </div>
  );
}
