"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { DEVELOPER_LIMITS } from "@/lib/app-rules";
import { callApi } from "@/lib/client-api";

const MAX_IMAGE_MB = DEVELOPER_LIMITS.maxImageBytes / 1024 / 1024;

interface MediaItem {
  path: string;
  url: string;
}

/** アイコンとスクリーンショット。画像はサーバーで検査してPNGに作り直して保存される */
export function MediaManager({ appId, icon, screenshots, disabled }: { appId: string; icon: string | null; screenshots: MediaItem[]; disabled: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function upload(kind: "icon" | "screenshot", file: File | undefined) {
    if (!file) return;
    setBusy(true);
    setError(null);
    const form = new FormData();
    form.set("kind", kind);
    form.set("file", file);
    const res = await callApi(`/api/developer/apps/${appId}/media`, { body: form, fallbackError: "アップロードに失敗しました" });
    setBusy(false);
    if (!res.ok) setError(res.error);
    else router.refresh();
  }

  async function remove(path: string) {
    setBusy(true);
    setError(null);
    const res = await callApi(`/api/developer/apps/${appId}/media`, { method: "DELETE", json: { path }, fallbackError: "削除に失敗しました" });
    setBusy(false);
    if (!res.ok) setError(res.error);
    else router.refresh();
  }

  const pick = "cursor-pointer rounded-full border border-border px-3 py-1.5 text-xs font-semibold hover:border-brand";
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-4">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        {icon ? <img src={icon} alt="アイコン" className="h-16 w-16 rounded-2xl border border-border object-cover" /> : <div className="flex h-16 w-16 items-center justify-center rounded-2xl border border-dashed border-border text-xs text-muted">なし</div>}
        <label className={pick}>
          {icon ? "アイコンを変更" : "アイコンを選ぶ"}
          <input type="file" accept="image/png,image/jpeg,image/webp" className="hidden" disabled={busy || disabled} onChange={(e) => upload("icon", e.target.files?.[0])} />
        </label>
        <p className="text-xs text-muted">正方形に近い画像(PNG・JPEG・WebP、{MAX_IMAGE_MB}MBまで)</p>
      </div>

      <div>
        <div className="flex flex-wrap gap-3">
          {screenshots.map((s) => (
            <div key={s.path} className="relative">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={s.url} alt="スクリーンショット" className="h-40 rounded-lg border border-border object-cover" />
              <button type="button" onClick={() => remove(s.path)} disabled={busy || disabled} aria-label="この画像を削除" className="absolute right-1 top-1 rounded-full bg-black/60 px-2 text-xs text-white">
                ×
              </button>
            </div>
          ))}
        </div>
        <label className={`${pick} mt-3 inline-block`}>
          スクリーンショットを追加({screenshots.length}/{DEVELOPER_LIMITS.maxScreenshots})
          <input type="file" accept="image/png,image/jpeg,image/webp" className="hidden" disabled={busy || disabled || screenshots.length >= DEVELOPER_LIMITS.maxScreenshots} onChange={(e) => upload("screenshot", e.target.files?.[0])} />
        </label>
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  );
}
