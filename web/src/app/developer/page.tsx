import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { APP_STATUS_LABEL, TONE_CLASS } from "@/lib/release-status";
import { serviceClient, sessionClient } from "@/lib/supabase";
import { RegisterForm } from "./RegisterForm";

export const metadata: Metadata = { title: "開発者向け" };
export const dynamic = "force-dynamic";

const Card = ({ children }: { children: React.ReactNode }) => <div className="rounded-xl border border-border bg-surface p-5">{children}</div>;

const CHECKS = [
  ["外部との通信機能がないこと", "インターネット・Wi-Fi・Bluetooth などの権限や、通信するコード、外部のURLを持たないか"],
  ["端末のデータを壊さないこと", "共有ストレージへの書き込み・連絡先の変更・アプリの削除・端末管理者などの権限やコードがないか"],
  ["隠れたコードがないこと", "後から別のコードを読み込む処理・隠された実行ファイル・中身の分からないネイティブライブラリがないか"],
  ["署名と構造が正しいこと", "リリース用の鍵で署名され、デバッグ用ビルドや壊れたAPKでないか"],
];

export default async function DeveloperPage() {
  const supabase = await sessionClient();
  const { data } = await supabase.auth.getUser();
  const user = data.user;
  if (!user) redirect("/login?next=/developer");

  const svc = serviceClient();
  const { data: developer } = await svc.from("developers").select("name, status, review_mode").eq("user_id", user.id).maybeSingle();

  const intro = (
    <Card>
      <h2 className="font-bold">自動審査で、誰でも気軽にアプリを公開できます</h2>
      <p className="mt-1 text-sm text-muted">APKをアップロードすると、次の点を自動で検査します。問題が見つからなければ、運営の承認を待たずにすぐ公開されます。</p>
      <ul className="mt-3 space-y-2 text-sm">
        {CHECKS.map(([title, detail]) => (
          <li key={title}>
            <span className="font-semibold">✓ {title}</span>
            <span className="block text-xs text-muted">{detail}</span>
          </li>
        ))}
      </ul>
      <p className="mt-3 text-xs text-muted">
        疑いが1つでも見つかったときは、公開せずに運営が内容を確認します(理由はこの画面とメールでお知らせします)。通信が必要なアプリも、運営が確認したうえで公開できます。
        詳しくは
        <Link href="/legal/developer" className="text-brand underline">
          開発者向け規約
        </Link>
        をご覧ください。
      </p>
    </Card>
  );

  if (!developer) {
    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <h1 className="text-xl font-bold">開発者として登録する</h1>
        {intro}
        <Card>
          <h2 className="mb-3 font-bold">登録(無料・すぐに使えます)</h2>
          {user.email_confirmed_at ? (
            <RegisterForm defaultEmail={user.email ?? ""} />
          ) : (
            <p className="text-sm text-danger">メールアドレスの確認が済んでいません。届いた確認メールのリンクを開いてから、このページを開き直してください。</p>
          )}
        </Card>
      </div>
    );
  }

  if (developer.status !== "approved") {
    return (
      <div className="mx-auto max-w-2xl space-y-4">
        <h1 className="text-xl font-bold">開発者向け</h1>
        <Card>
          <p className="text-sm">
            {developer.status === "suspended" ? "この開発者アカウントは停止されています。心当たりがない場合や、解除のご相談は、運営にお問い合わせください。" : "開発者アカウントは、運営の承認を待っています。"}
          </p>
        </Card>
      </div>
    );
  }

  const { data: apps } = await svc
    .from("apps")
    .select("id, slug, name, status, download_count, app_releases(status, version_name, created_at)")
    .eq("developer_id", user.id)
    .order("created_at", { ascending: false });

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">開発者ダッシュボード</h1>
          <p className="text-sm text-muted">{developer.name}</p>
        </div>
        <Link href="/developer/apps/new" className="rounded-full bg-brand px-4 py-2 text-sm font-semibold text-brand-foreground">
          アプリを登録
        </Link>
      </div>

      {!apps || apps.length === 0 ? (
        <Card>
          <p className="text-sm text-muted">まだアプリがありません。「アプリを登録」から、最初のアプリを登録しましょう。</p>
        </Card>
      ) : (
        <ul className="space-y-2">
          {apps.map((a) => {
            const releases = ((a.app_releases as { status: string; version_name: string | null; created_at: string }[] | null) ?? []).slice().sort((x, y) => y.created_at.localeCompare(x.created_at));
            const latest = releases[0];
            const status = APP_STATUS_LABEL[a.status] ?? { label: a.status, tone: "muted" as const };
            return (
              <li key={a.id}>
                <Link href={`/developer/apps/${a.id}`} className="flex items-center justify-between gap-3 rounded-xl border border-border bg-surface px-4 py-3 hover:border-brand">
                  <span className="min-w-0">
                    <span className="block truncate font-semibold">{a.name}</span>
                    <span className="block text-xs text-muted">
                      {latest ? `最新のアップロード: v${latest.version_name ?? "?"}` : "まだAPKがありません"} ・ {a.download_count} DL
                    </span>
                  </span>
                  <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ${TONE_CLASS[status.tone]}`}>{status.label}</span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
      {intro}
    </div>
  );
}
