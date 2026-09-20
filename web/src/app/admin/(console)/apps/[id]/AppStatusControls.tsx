"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { setAppStatus, setFeatured } from "../../actions";

const LABEL: Record<string, string> = { draft: "下書き", pending: "審査中", published: "公開中", suspended: "停止中" };

export function AppStatusControls({ appId, status, featured }: { appId: string; status: string; featured: boolean }) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();

  const run = (fn: () => Promise<{ error?: string }>) =>
    start(async () => {
      const res = await fn();
      setError(res.error ?? null);
      router.refresh();
    });

  const btn = "rounded-full border border-border bg-surface px-4 py-1.5 text-sm font-semibold disabled:opacity-60";
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="rounded-full bg-border px-3 py-1 text-sm">{LABEL[status] ?? status}</span>
      {status !== "published" && (
        <button disabled={pending} className={btn} onClick={() => run(() => setAppStatus(appId, "published"))}>
          アプリを公開する
        </button>
      )}
      {status === "published" && (
        <button disabled={pending} className={btn} onClick={() => run(() => setAppStatus(appId, "suspended"))}>
          公開を停止する
        </button>
      )}
      <button disabled={pending} className={btn} onClick={() => run(() => setFeatured(appId, !featured))}>
        {featured ? "おすすめから外す" : "おすすめにする"}
      </button>
      {error && <span className="text-sm text-danger">{error}</span>}
    </div>
  );
}
