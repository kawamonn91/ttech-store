import type { Metadata } from "next";

export const metadata: Metadata = { title: "利用規約" };

// 注意: これは雛形です。公開前に内容を確認し、必要に応じて専門家(弁護士等)のレビューを受けてください。
export default function TermsPage() {
  return (
    <article className="prose-sm mx-auto max-w-2xl space-y-4 leading-7">
      <h1 className="text-2xl font-bold">利用規約</h1>
      <p>この利用規約(以下「本規約」)は、T-tech(以下「運営者」)が提供する「T-tech Store」(Webサイトおよびアプリ。以下「本サービス」)の利用条件を定めます。</p>

      <h2 className="pt-2 text-lg font-bold">第1条(適用)</h2>
      <p>本規約は、本サービスを利用するすべての方に適用されます。本サービスを利用した時点で、本規約に同意したものとみなします。</p>

      <h2 className="pt-2 text-lg font-bold">第2条(本サービスの内容)</h2>
      <p>本サービスは、運営者および運営者が承認した開発者が提供するAndroidアプリを、閲覧・ダウンロード・インストール・更新できるようにするものです。</p>

      <h2 className="pt-2 text-lg font-bold">第3条(利用上の注意)</h2>
      <ol className="list-decimal space-y-1 pl-5">
        <li>本サービスは Google Play 以外の経路でアプリを配布します。インストールには端末の「提供元不明のアプリ」の許可が必要で、ご自身の判断と責任で行ってください。</li>
        <li>アプリの動作・内容は各アプリの提供者が責任を負います。運営者は、公開前にパッケージ名・署名・ウイルススキャン等の確認を行いますが、すべての不具合や危険を排除することを保証するものではありません。</li>
      </ol>

      <h2 className="pt-2 text-lg font-bold">第4条(禁止事項)</h2>
      <ul className="list-disc space-y-1 pl-5">
        <li>法令または公序良俗に違反する行為</li>
        <li>本サービスの運営を妨害する行為、不正アクセス、過度な負荷をかける行為</li>
        <li>ダウンロード数や評価を不正に操作する行為</li>
        <li>他者を誹謗中傷する内容や、権利を侵害する内容の投稿</li>
      </ul>

      <h2 className="pt-2 text-lg font-bold">第5条(免責)</h2>
      <p>運営者は、本サービスおよびアプリの利用により生じた損害について、運営者の故意または重過失による場合を除き、責任を負いません。</p>

      <h2 className="pt-2 text-lg font-bold">第6条(規約の変更)</h2>
      <p>運営者は、必要に応じて本規約を変更できます。変更後の規約は、本サービス上に掲示した時点から効力を生じます。</p>

      <h2 className="pt-2 text-lg font-bold">第7条(準拠法・管轄)</h2>
      <p>本規約は日本法に準拠し、本サービスに関する紛争は、運営者の所在地を管轄する裁判所を第一審の専属的合意管轄とします。</p>

      <p className="pt-4 text-sm text-muted">運営者: T-tech(運営統括責任者: 川本登哉) / お問い合わせ: kawamonn91@gmail.com</p>
    </article>
  );
}
