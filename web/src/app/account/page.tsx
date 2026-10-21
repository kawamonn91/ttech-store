import Link from "next/link";
import { redirect } from "next/navigation";
import { listPendingReleases, listPrivateApps } from "@/lib/admin-queries";
import { first } from "@/lib/db";
import { APP_STATUS_LABEL } from "@/lib/release-status";
import { adminStepUpDone, serviceClient, sessionClient, verifiedTotpFactor } from "@/lib/supabase";
import { DeleteAccountButton } from "./DeleteAccountButton";
import { PendingReleases } from "./PendingReleases";
import { PrivateApps } from "./PrivateApps";
import { ProfileForm } from "./ProfileForm";
import { TotpSetup } from "./TotpSetup";

export const dynamic = "force-dynamic";

/** 管理者向けの欄は、読み込めなくてもマイページ全体は表示する(例: DBの更新が未適用のとき) */
function orEmpty<T>(promise: Promise<T[]>): Promise<T[]> {
  return promise.catch((e) => {
    console.error(e);
    return [];
  });
}

export default async function AccountPage() {
  const supabase = await sessionClient();
  const { data: userData } = await supabase.auth.getUser();
  const user = userData.user;
  if (!user) redirect("/login?next=/account");

  const [{ data: profile }, { data: developer }] = await Promise.all([
    supabase.from("profiles").select("display_name, role, created_at").eq("id", user.id).maybeSingle(),
    supabase.from("developers").select("status, name, created_at").eq("user_id", user.id).maybeSingle(),
  ]);

  const isAdminRole = profile?.role === "admin";
  const admin = isAdminRole && (await adminStepUpDone(supabase, user));
  // role は admin だが、2段階認証(aal2)が済んでいないので管理者の操作ができない状態。
  // 管理メニューを黙って消すのではなく、認証すれば出ることを案内する
  const needsStepUp = isAdminRole && !admin;
  const totpFactor = isAdminRole ? verifiedTotpFactor(user) : null;
  const svc = serviceClient();

  const [myApps, downloads, pendingReleases, privateApps] = await Promise.all([
    developer
      ? svc.from("apps").select("id, slug, name, status, download_count").eq("developer_id", user.id).order("created_at", { ascending: false })
      : Promise.resolve({ data: null }),
    svc
      .from("downloads")
      .select("created_at, release:app_releases(version_name, app:apps(slug, name))")
      .eq("user_id", user.id)
      .order("created_at", { ascending: false })
      .limit(20),
    admin ? orEmpty(listPendingReleases(svc)) : [],
    admin ? orEmpty(listPrivateApps(svc)) : [],
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
          <dd>{isAdminRole ? "管理者" : developer?.status === "approved" ? "開発者" : "利用者"}</dd>
        </dl>
      </Section>

      <Section title="プロフィール">
        <ProfileForm initialName={profile?.display_name ?? ""} />
      </Section>

      {isAdminRole && (
        <Section title="2段階認証(管理者)">
          <TotpSetup initialFactor={totpFactor && { id: totpFactor.id, status: totpFactor.status }} />
        </Section>
      )}

      <Section title="開発者">
        {developer ? (
          <p className="text-sm">
            状態: {developer.status === "approved" ? "登録済み" : developer.status === "pending" ? "承認待ち" : "停止中"}
            {developer.status === "approved" && (
              <>
                {" "}
                ・{" "}
                <Link href="/developer" className="text-brand underline">
                  開発者ダッシュボードを開く
                </Link>
              </>
            )}
          </p>
        ) : (
          <p className="text-sm text-muted">
            自分のアプリを公開できます。登録は無料で、APKの自動審査を通れば、すぐに公開されます。{" "}
            <Link href="/developer" className="text-brand underline">
              開発者として登録する
            </Link>
          </p>
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
                    {APP_STATUS_LABEL[a.status]?.label ?? a.status} ・ {a.download_count} DL
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Section>
      )}

      {admin && (
        <Section title="承認待ちのリリース(管理者)">
          <PendingReleases items={pendingReleases} />
        </Section>
      )}

      {admin && (
        <Section title="管理者用アプリ">
          <PrivateApps items={privateApps} />
        </Section>
      )}

      {needsStepUp && (
        <Section title="管理者メニュー">
          <p className="text-sm text-muted">
            2段階認証が必要です。{" "}
            <Link href="/admin/login?next=/account" className="text-brand underline">
              認証する
            </Link>{" "}
            と、承認待ちのリリースや管理者用アプリがここに表示されます。
          </p>
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
              const app = first(first(d.release)?.app);
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
