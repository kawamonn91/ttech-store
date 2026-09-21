import { redirect } from "next/navigation";
import { getAdminUser, sessionClient, serviceClient } from "@/lib/supabase";
import { ProfileForm } from "./ProfileForm";
import { DeleteAccountButton } from "./DeleteAccountButton";
import { DeveloperApplyForm } from "./DeveloperApplyForm";
import { TotpSetup } from "./TotpSetup";
import { PendingReleases } from "./PendingReleases";

export const dynamic = "force-dynamic";

const APP_STATUS_LABEL: Record<string, string> = { draft: "下書き", pending: "審査中", published: "公開中", suspended: "停止中" };

export default async function AccountPage() {
  const supabase = await sessionClient();
  const { data: userData } = await supabase.auth.getUser();
  const user = userData.user;
  if (!user) redirect("/login?next=/account");

  const [{ data: profile }, { data: developer }] = await Promise.all([
    supabase.from("profiles").select("display_name, role, created_at").eq("id", user.id).maybeSingle(),
    supabase.from("developers").select("status, name, created_at").eq("user_id", user.id).maybeSingle(),
  ]);

  const admin = await getAdminUser();
  const svc = serviceClient();

  let totpFactor: { id: string; status: string } | null = null;
  if (profile?.role === "admin") {
    const { data: factors } = await supabase.auth.mfa.listFactors();
    totpFactor = factors?.totp?.find((f) => f.status === "verified") ?? null;
  }

  const [myApps, downloads, pendingReleases] = await Promise.all([
    developer
      ? svc.from("apps").select("id, slug, name, status, download_count").eq("developer_id", user.id).order("created_at", { ascending: false })
      : Promise.resolve({ data: null }),
    svc
      .from("downloads")
      .select("created_at, release:app_releases(version_name, app:apps(slug, name))")
      .eq("user_id", user.id)
      .order("created_at", { ascending: false })
      .limit(20),
    admin
      ? svc
          .from("app_releases")
          .select("id, version_name, status, app:apps(id, slug, name)")
          .in("status", ["scanned", "approved"])
          .order("created_at", { ascending: false })
      : Promise.resolve({ data: null }),
  ]);

  const provider = user.app_metadata?.provider === "google" ? "Google" : "メールアドレス";

  return (
    <div className="mx-auto max-w-2xl space-y-8">
      <div>
        <h1 className="text-xl font-bold">マイページ</h1>
        <p className="text-sm text-muted">{user.email}</p>
      </div>

      <Section title="認証状態">
        <dl className="grid grid-cols-[8rem_1fr] gap-y-1 text-sm">
          <dt className="text-muted">ログイン方法</dt>
          <dd>{provider}</dd>
          <dt className="text-muted">メール確認</dt>
          <dd>{user.email_confirmed_at ? "確認済み" : "未確認"}</dd>
          <dt className="text-muted">権限</dt>
          <dd>{profile?.role === "admin" ? "管理者" : developer?.status === "approved" ? "開発者" : "利用者"}</dd>
        </dl>
      </Section>

      <Section title="プロフィール">
        <ProfileForm initialName={profile?.display_name ?? ""} />
      </Section>

      {profile?.role === "admin" && (
        <Section title="2段階認証(管理者)">
          <TotpSetup initialFactor={totpFactor} />
        </Section>
      )}

      <Section title="開発者登録">
        {developer ? (
          <p className="text-sm">
            状態:{" "}
            {developer.status === "approved" ? "承認済み" : developer.status === "pending" ? "承認待ち" : "停止中"}
          </p>
        ) : (
          <DeveloperApplyForm />
        )}
      </Section>

      {developer && (
        <Section title="作成したアプリ">
          {!myApps.data || myApps.data.length === 0 ? (
            <p className="text-sm text-muted">まだアプリがありません。</p>
          ) : (
            <ul className="space-y-2">
              {myApps.data.map((a) => (
                <li key={a.id} className="flex items-center justify-between rounded-xl border border-border bg-surface px-4 py-2.5 text-sm">
                  <span>{a.name}</span>
                  <span className="text-muted">
                    {APP_STATUS_LABEL[a.status] ?? a.status} ・ {a.download_count} DL
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Section>
      )}

      {admin && (
        <Section title="承認待ちのリリース(管理者)">
          <PendingReleases
            items={(pendingReleases.data ?? []).map((r) => ({
              id: r.id,
              version_name: r.version_name,
              status: r.status,
              app: Array.isArray(r.app) ? r.app[0] : r.app,
            }))}
          />
        </Section>
      )}

      <Section title="ダウンロード履歴">
        <p className="mb-2 text-xs text-muted">
          プライバシー保護のため、端末に今インストールされているかはサーバーからは分かりません。インストール状況はストアアプリ内で確認できます。
        </p>
        {!downloads.data || downloads.data.length === 0 ? (
          <p className="text-sm text-muted">まだダウンロード履歴がありません。</p>
        ) : (
          <ul className="space-y-1 text-sm">
            {downloads.data.map((d, i) => {
              const release = Array.isArray(d.release) ? d.release[0] : d.release;
              const app = release ? (Array.isArray(release.app) ? release.app[0] : release.app) : null;
              return (
                <li key={i} className="flex justify-between text-muted">
                  <span className="text-foreground">{app?.name ?? "(削除済み)"}</span>
                  <span>{new Date(d.created_at).toLocaleDateString("ja-JP")}</span>
                </li>
              );
            })}
          </ul>
        )}
      </Section>

      <Section title="アカウントの削除">
        <DeleteAccountButton hasApps={Boolean(myApps.data && myApps.data.length > 0)} />
      </Section>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="mb-2 text-sm font-bold text-muted">{title}</h2>
      {children}
    </section>
  );
}
