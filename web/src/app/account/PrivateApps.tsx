"use client";

import { useState } from "react";
import type { PrivateAppDto } from "@/lib/admin-queries";
import { callApi } from "@/lib/client-api";
import type { DownloadInfoDto } from "@/lib/dto";
import { formatBytes } from "@/lib/format";

/**
 * 管理者専用アプリ(公開カタログには出ない)のダウンロード欄。管理者だけに表示される。
 * ダウンロードURLは押した時点で発行する(短時間だけ有効な署名付きURL)。
 */
export function PrivateApps({ items }: { items: PrivateAppDto[] }) {
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sha, setSha] = useState<Record<string, string>>({});

  async function download(item: PrivateAppDto) {
    setBusyId(item.releaseId);
    setError(null);
    const res = await callApi<DownloadInfoDto>(`/api/admin/private-apps/${item.releaseId}/download`, { fallbackError: "ダウンロードを開始できませんでした" });
    setBusyId(null);
    if (!res.ok) return setError(res.error);
    setSha((prev) => ({ ...prev, [item.releaseId]: res.data.sha256 }));
    window.location.assign(res.data.url);
  }

  if (items.length === 0) return <p className="text-sm text-muted">管理者用のアプリはまだ公開されていません。</p>;

  return (
    <div className="space-y-2">
      {error && <p className="text-sm text-danger">{error}</p>}
      <ul className="space-y-2">
        {items.map((a) => (
          <li key={a.releaseId} className="rounded-xl border border-border bg-surface px-4 py-2.5 text-sm">
            <div className="flex items-center justify-between gap-3">
              <span className="min-w-0 flex-1 truncate">
                {a.name} <span className="text-muted">v{a.versionName ?? a.versionCode}</span>
                {a.apkSize ? <span className="ml-2 text-xs text-muted">{formatBytes(a.apkSize)}</span> : null}
              </span>
              <button
                onClick={() => download(a)}
                disabled={busyId === a.releaseId}
                className="shrink-0 rounded-full bg-brand px-3 py-1 text-xs font-semibold text-brand-foreground disabled:opacity-60"
              >
                {busyId === a.releaseId ? "準備中…" : "ダウンロード"}
              </button>
            </div>
            {sha[a.releaseId] && <p className="mt-1 break-all text-xs text-muted">SHA-256: {sha[a.releaseId]}</p>}
          </li>
        ))}
      </ul>
      <p className="text-xs text-muted">
        このアプリは管理者だけがダウンロードできます。Android でダウンロードしたあと、開いてインストールしてください
        (「提供元不明のアプリ」の許可が必要な場合があります)。
      </p>
    </div>
  );
}
