"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { browserClient } from "@/lib/supabase-browser";
import { saveMedia } from "../../actions";

const MAX_BYTES = 5 * 1024 * 1024;

/** 画像をブラウザから Supabase Storage へ直接アップロードし、パスをアプリに保存する */
export function MediaManager({
  appId,
  iconUrl,
  screenshots,
}: {
  appId: string;
  iconUrl: string | null;
  screenshots: { path: string; url: string }[];
}) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function upload(file: File, kind: "icon" | "shot") {
    setError(null);
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) return setError("PNG / JPEG / WebP の画像を選んでください");
    if (file.size > MAX_BYTES) return setError("画像は5MB以下にしてください");
    setBusy(true);
    try {
      const ext = file.type === "image/png" ? "png" : file.type === "image/webp" ? "webp" : "jpg";
      const path = `${appId}/${kind}-${Date.now()}.${ext}`;
      const { error: upErr } = await browserClient().storage.from("app-media").upload(path, file, { contentType: file.type });
      if (upErr) throw new Error(upErr.message);
      const res = await saveMedia(appId, kind === "icon" ? { icon: path } : { addScreenshot: path });
      if (res.error) throw new Error(res.error);
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "アップロードに失敗しました");
    } finally {
      setBusy(false);
    }
  }

  const pick = "cursor-pointer rounded-full border border-border bg-surface px-4 py-1.5 text-sm font-semibold";
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-4">
        {iconUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={iconUrl} alt="アイコン" className="h-20 w-20 rounded-2xl object-cover" />
        ) : (
          <div className="flex h-20 w-20 items-center justify-center rounded-2xl bg-border text-xs text-muted">未設定</div>
        )}
        <label className={pick}>
          アイコンを選ぶ(512×512 推奨)
          <input type="file" accept="image/png,image/jpeg,image/webp" hidden disabled={busy} onChange={(e) => e.target.files?.[0] && upload(e.target.files[0], "icon")} />
        </label>
      </div>

      <div className="flex flex-wrap gap-3">
        {screenshots.map((s) => (
          <div key={s.path} className="relative">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={s.url} alt="スクリーンショット" className="h-48 rounded-xl border border-border" />
            <button
              className="absolute right-1 top-1 rounded-full bg-black/60 px-2 text-sm text-white"
              aria-label="削除"
              onClick={async () => {
                const res = await saveMedia(appId, { removeScreenshot: s.path });
                if (res.error) setError(res.error);
                router.refresh();
              }}
            >
              ×
            </button>
          </div>
        ))}
      </div>
      <label className={pick + " inline-block"}>
        スクリーンショットを追加(最大8枚)
        <input type="file" accept="image/png,image/jpeg,image/webp" hidden disabled={busy} onChange={(e) => e.target.files?.[0] && upload(e.target.files[0], "shot")} />
      </label>
      {busy && <p className="text-sm text-muted">アップロード中…</p>}
      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  );
}
