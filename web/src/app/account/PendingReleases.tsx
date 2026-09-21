"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import Link from "next/link";

export interface PendingReleaseItem {
  id: string;
  version_name: string | null;
  status: string;
  app: { id: string; slug: string; name: string } | null;
}

const STATUS_LABEL: Record<string, string> = { scanned: "承認待ち", approved: "非公開(承認済み)" };

/** マイページ内で完結する承認UI。管理コンソール(/admin)へ移動しなくても公開・却下できる */
export function PendingReleases({ items }: { items: PendingReleaseItem[] }) {
  const router = useRouter();
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function decide(releaseId: string, action: "publish" | "reject") {
    setBusyId(releaseId);
    setError(null);
    const res = await fetch(`/api/admin/releases/${releaseId}/decision`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ action }),
    });
    setBusyId(null);
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      setError(String(body.error ?? "操作に失敗しました"));
      return;
    }
    router.refresh();
  }

  if (items.length === 0) return <p className="text-sm text-muted">承認待ちのリリースはありません。</p>;

  return (
    <div className="space-y-2">
      {error && <p className="text-sm text-danger">{error}</p>}
      <ul className="space-y-2">
        {items.map((r) => (
          <li key={r.id} className="flex items-center justify-between gap-3 rounded-xl border border-border bg-surface px-4 py-2.5 text-sm">
            <Link href={`/apps/${r.app?.slug}`} className="min-w-0 flex-1 truncate hover:text-brand">
              {r.app?.name} <span className="text-muted">v{r.version_name ?? "?"}</span>
            </Link>
            <span className="shrink-0 text-xs text-muted">{STATUS_LABEL[r.status] ?? r.status}</span>
            <div className="flex shrink-0 gap-2">
              <button
                onClick={() => decide(r.id, "publish")}
                disabled={busyId === r.id}
                className="rounded-full bg-brand px-3 py-1 text-xs font-semibold text-brand-foreground disabled:opacity-60"
              >
                公開する
              </button>
              <button
                onClick={() => decide(r.id, "reject")}
                disabled={busyId === r.id}
                className="rounded-full border border-border px-3 py-1 text-xs disabled:opacity-60"
              >
                却下
              </button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
