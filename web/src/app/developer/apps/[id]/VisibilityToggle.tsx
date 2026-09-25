"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

/** 公開・非公開の切り替え(公開できるのは、公開済みのリリースがあるアプリだけ) */
export function VisibilityToggle({ appId, status, canPublish }: { appId: string; status: string; canPublish: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const published = status === "published";

  async function toggle() {
    setBusy(true);
    setError(null);
    const res = await fetch(`/api/developer/apps/${appId}/visibility`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ visible: !published }),
    });
    setBusy(false);
    if (!res.ok) setError(String((await res.json().catch(() => ({}))).error ?? "変更に失敗しました"));
    else router.refresh();
  }

  if (status === "suspended") return <p className="text-sm text-danger">このアプリは運営により停止されています。運営にお問い合わせください。</p>;
  return (
    <div className="space-y-1">
      <button onClick={toggle} disabled={busy || (!published && !canPublish)} className="rounded-full border border-border px-4 py-1.5 text-sm font-semibold hover:border-brand disabled:opacity-50">
        {published ? "非公開にする" : "公開する"}
      </button>
      {!published && !canPublish && <p className="text-xs text-muted">APKをアップロードして審査を通ると、公開できます。</p>}
      {published && <p className="text-xs text-muted">非公開にしても、すでにインストールした人のアプリはそのまま使えます。</p>}
      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  );
}
