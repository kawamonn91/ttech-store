import type { Metadata } from "next";

export const metadata: Metadata = { title: "プライバシーポリシー" };

// 注意: これは雛形です。ログイン・レビュー機能を有効にするときは「取得する情報」を更新してください。
export default function PrivacyPage() {
  return (
    <article className="mx-auto max-w-2xl space-y-4 leading-7">
      <h1 className="text-2xl font-bold">プライバシーポリシー</h1>
      <p>T-tech(以下「運営者」)は、「T-tech Store」(以下「本サービス」)における情報の取り扱いについて、以下のとおり定めます。</p>

      <h2 className="pt-2 text-lg font-bold">1. 取得する情報</h2>
      <ul className="list-disc space-y-1 pl-5">
        <li>
          <b>ダウンロードの記録</b>: アプリのダウンロード数を集計するため、端末ごとにアプリが作成するランダムな識別子を、ソルト付きハッシュ化した値で記録します。氏名・電話番号・広告ID等の個人を特定できる情報とは結び付けません。
        </li>
        <li>
          <b>アクセスログ</b>: 不正利用の防止と障害対応のため、ホスティング事業者(Vercel、Supabase、Cloudflare)にIPアドレス等のアクセスログが一定期間記録されます。
        </li>
        <li>
          <b>インストール済みアプリの情報</b>: アップデートの確認は端末内で行います。端末にインストールされているアプリの一覧は、運営者のサーバーに送信しません。
        </li>
      </ul>

      <h2 className="pt-2 text-lg font-bold">2. 利用目的</h2>
      <p>サービスの提供・改善、ダウンロード数の集計、不正利用の防止、お問い合わせへの対応のために利用します。</p>

      <h2 className="pt-2 text-lg font-bold">3. 第三者への提供</h2>
      <p>法令に基づく場合を除き、本人の同意なく第三者に個人情報を提供しません。ただし、サービスの提供に必要な範囲で、以下の事業者のサービスを利用します: Vercel(Webホスティング)、Supabase(データベース・認証)、Cloudflare(ファイル配信)。</p>

      <h2 className="pt-2 text-lg font-bold">4. アプリごとの取り扱い</h2>
      <p>各アプリが取得する情報については、各アプリのプライバシーポリシーをご確認ください。</p>

      <h2 className="pt-2 text-lg font-bold">5. 改定</h2>
      <p>本ポリシーは、必要に応じて改定することがあります。改定後は本ページに掲示します。</p>

      <h2 className="pt-2 text-lg font-bold">6. お問い合わせ</h2>
      <p>T-tech(運営統括責任者: 川本登哉) / kawamonn91@gmail.com</p>
    </article>
  );
}
