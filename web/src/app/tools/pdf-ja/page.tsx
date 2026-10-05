import type { Metadata } from "next";

export const metadata: Metadata = { title: "PDF・PowerPoint 日本語化ツール(Windows)" };

export default function PdfJaToolPage() {
  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-2xl font-bold">PDF・PowerPoint 日本語化ツール</h1>
      <p className="mb-6 text-muted">
        英語のPDFやPowerPointを、レイアウト・画像・フォントの見た目を保ったまま日本語にする Windows 用ツールです。
        はみ出す文字は自動で小さくし、気に入らない箇所は文字サイズや位置を手で調整できます。
      </p>

      <div className="mb-8 rounded-2xl border border-border bg-surface p-5">
        <p className="mb-3 text-sm text-muted">Windows 10 / 11 ・ 翻訳には Claude API キーが必要です</p>
        <a
          href="/download/pdf-ja"
          className="inline-block rounded-full bg-brand px-6 py-2.5 font-semibold text-brand-foreground"
        >
          Windows 版をダウンロード(zip)
        </a>
      </div>

      <h2 className="mb-2 text-lg font-bold">はじめかた</h2>
      <ol className="mb-8 list-decimal space-y-2 pl-5">
        <li>ダウンロードした zip を開き、中の「PDF-JA-Translator」フォルダを好きな場所に展開します。</li>
        <li>「PDF-JA-Translator.exe」を起動し、「APIキー登録」を押します。画面の手順に沿って、Claude API キーを登録してください。</li>
        <li>「開く」で PDF または PowerPoint を選び、「すべて翻訳」を押します。</li>
        <li>確認して直したい箇所は、一覧から選んで文字サイズ・位置・訳文を調整します。</li>
        <li>「日本語版を保存」で、日本語版のファイルを書き出します。</li>
      </ol>

      <h2 className="mb-2 text-lg font-bold">ご注意</h2>
      <ul className="mb-8 list-disc space-y-2 pl-5 text-sm">
        <li>
          Claude API の利用料金は、Anthropic の従量課金です(使った分だけ)。キーは Anthropic Console で作成し、利用上限を設定しておくと安心です。
        </li>
        <li>翻訳するため、文書の英語の文章が Claude API に送信されます。機密の文書は扱いにご注意ください。</li>
        <li>PowerPoint の画面プレビューには、PowerPoint がインストールされたPCが必要です(なくても翻訳と保存はできます)。</li>
        <li>Windows の警告が出た場合は、ダウンロード元を確認のうえ「詳細情報」から実行してください。</li>
      </ul>
    </div>
  );
}
