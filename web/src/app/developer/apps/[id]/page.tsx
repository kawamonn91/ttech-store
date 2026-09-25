import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { mediaUrl } from "@/lib/dto";
import type { Finding } from "@/lib/policy";
import { APP_STATUS_LABEL, releaseState, TONE_CLASS } from "@/lib/release-status";
import { serviceClient, sessionClient } from "@/lib/supabase";
import { AutoRefresh } from "../../AutoRefresh";
import { Findings } from "../../Findings";
import { AppEditForm } from "./AppEditForm";
import { MediaManager } from "./MediaManager";
import { ReleaseUploader } from "./ReleaseUploader";
import { VisibilityToggle } from "./VisibilityToggle";

export const metadata: Metadata = { title: "アプリの管理" };
export const dynamic = "force-dynamic";

const Section = ({ title, children }: { title: string; children: React.ReactNode }) => (
  <section className="space-y-3">
    <h2 className="font-bold">{title}</h2>
    <div className="rounded-xl border border-border bg-surface p-5">{children}</div>
  </section>
);

/** アップロードしたのに検査が始まらないまま、30分以上たっているか(途中で止まった可能性) */
function isStalledUpload(createdAt: string): boolean {
  return Date.now() - new Date(createdAt).getTime() > 30 * 60 * 1000;
}

interface ReleaseRow {
  id: string;
  version_name: string | null;
  version_code: number | null;
  status: string;
  created_at: string;
  release_notes: string;
  auto_approved: boolean;
  policy_verdict: string | null;
  policy_findings: Finding[] | null;
  scan_result: { error?: string } | null;
}

export default async function DeveloperAppPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const supabase = await sessionClient();
  const { data: auth } = await supabase.auth.getUser();
  if (!auth.user) redirect(`/login?next=/developer/apps/${id}`);
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();

  const svc = serviceClient();
  // 自分のアプリだけ(他人のアプリは、存在しないものと同じ扱い)
  const { data: app } = await svc
    .from("apps")
    .select("id, slug, name, short_desc, description, category_id, icon_path, screenshots, status, package_name, download_count")
    .eq("id", id)
    .eq("developer_id", auth.user.id)
    .maybeSingle();
  if (!app) notFound();

  const [{ data: categories }, { data: releaseRows }] = await Promise.all([
    svc.from("categories").select("id, name").order("sort_order"),
    svc
      .from("app_releases")
      .select("id, version_name, version_code, status, created_at, release_notes, auto_approved, policy_verdict, policy_findings, scan_result")
      .eq("app_id", id)
      .order("created_at", { ascending: false })
      .limit(20),
  ]);
  const releases = (releaseRows ?? []) as ReleaseRow[];
  const suspended = app.status === "suspended";
  const status = APP_STATUS_LABEL[app.status] ?? { label: app.status, tone: "muted" as const };

  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <AutoRefresh active={releases.some((r) => r.status === "uploaded")} />
      <div>
        <Link href="/developer" className="text-sm text-muted hover:text-brand">
          ← 開発者ダッシュボード
        </Link>
        <div className="mt-1 flex items-center gap-3">
          <h1 className="text-xl font-bold">{app.name}</h1>
          <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${TONE_CLASS[status.tone]}`}>{status.label}</span>
        </div>
        <p className="font-mono text-xs text-muted">{app.package_name}</p>
        {app.status === "published" && (
          <Link href={`/apps/${app.slug}`} className="text-sm text-brand underline">
            公開ページを見る
          </Link>
        )}
      </div>

      <Section title="新しいバージョンをアップロード">
        <ReleaseUploader appId={id} disabled={suspended} />
        <p className="mt-3 text-xs text-muted">
          アップロードしたAPKは自動で検査され、問題が見つからなければすぐ公開されます(2回目以降は、versionCode を前より大きくしてください)。検査のため、APKは VirusTotal にも送信されます(詳しくは開発者向け規約の第2条)。
        </p>
      </Section>

      <Section title="アップロードの履歴と審査結果">
        {releases.length === 0 ? (
          <p className="text-sm text-muted">まだアップロードがありません。</p>
        ) : (
          <ul className="space-y-3">
            {releases.map((r) => {
              const s = releaseState(r);
              const reason = r.status === "rejected" ? r.scan_result?.error : null;
              return (
                <li key={r.id} className="rounded-lg border border-border px-4 py-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-sm font-semibold">
                      {r.version_name ? `v${r.version_name}` : "(検査中)"}
                      {r.version_code ? <span className="ml-1 font-normal text-muted">(versionCode {r.version_code})</span> : null}
                    </span>
                    <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold ${TONE_CLASS[s.tone]}`}>{s.label}</span>
                  </div>
                  <p className="text-xs text-muted">{new Date(r.created_at).toLocaleString("ja-JP")}</p>
                  {s.hint && <p className="mt-1 text-xs text-muted">{s.hint}</p>}
                  {r.status === "uploaded" && isStalledUpload(r.created_at) && (
                    <p className="mt-1 text-xs text-danger">検査が完了していません(アップロードが途中で止まった可能性があります)。もう一度アップロードしてください。</p>
                  )}
                  {reason && <p className="mt-1 text-sm text-danger">{reason}</p>}
                  <Findings findings={r.policy_findings ?? []} />
                  {r.status === "published" && r.auto_approved && (r.policy_findings ?? []).some((f) => f.severity === "info") && (
                    <details className="mt-2 text-xs text-muted">
                      <summary className="cursor-pointer">公開ページに載せる情報(参考)</summary>
                      <Findings findings={r.policy_findings ?? []} showInfo />
                    </details>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </Section>

      <Section title="公開の設定">
        <VisibilityToggle appId={id} status={app.status} canPublish={releases.some((r) => r.status === "published")} />
      </Section>

      <Section title="アイコン・スクリーンショット">
        <MediaManager
          appId={id}
          icon={mediaUrl(app.icon_path)}
          screenshots={((app.screenshots as string[] | null) ?? []).map((p) => ({ path: p, url: mediaUrl(p) ?? "" }))}
          disabled={suspended}
        />
      </Section>

      <Section title="掲載内容">
        <AppEditForm
          appId={id}
          initial={{ name: app.name, shortDesc: app.short_desc, description: app.description, categoryId: app.category_id }}
          categories={categories ?? []}
          disabled={suspended}
        />
      </Section>
    </div>
  );
}
