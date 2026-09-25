"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";

const MAX_BYTES = 200 * 1024 * 1024;

type Phase = { kind: "idle" } | { kind: "uploading"; percent: number } | { kind: "registering" };

/** R2 へ直接アップロードする(進み具合を出すため XMLHttpRequest を使う) */
function put(url: string, file: File, onProgress: (percent: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", url);
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(Math.round((e.loaded / e.total) * 100));
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`アップロードに失敗しました (${xhr.status})`)));
    xhr.onerror = () => reject(new Error("アップロードに失敗しました。通信状況を確認して、もう一度お試しください"));
    xhr.send(file);
  });
}

/** 新しいバージョンのAPKをアップロードする。アップロード後は自動で検査され、結果はこの画面に出る */
export function ReleaseUploader({ appId, disabled }: { appId: string; disabled: boolean }) {
  const router = useRouter();
  const input = useRef<HTMLInputElement>(null);
  const [notes, setNotes] = useState("");
  const [phase, setPhase] = useState<Phase>({ kind: "idle" });
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function start(file: File | undefined) {
    if (!file) return;
    setError(null);
    setDone(false);
    if (!/\.apk$/i.test(file.name)) return setError("APKファイル(.apk)を選んでください。AAB(.aab)は対応していません");
    if (file.size > MAX_BYTES) return setError(`APKが大きすぎます(上限 ${MAX_BYTES / 1024 / 1024}MB)`);

    try {
      setPhase({ kind: "uploading", percent: 0 });
      const created = await fetch(`/api/developer/apps/${appId}/releases`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ releaseNotes: notes }),
      });
      const info = await created.json().catch(() => ({}));
      if (!created.ok) throw new Error(String(info.error ?? "アップロードを始められませんでした"));

      await put(info.uploadUrl, file, (percent) => setPhase({ kind: "uploading", percent }));

      setPhase({ kind: "registering" });
      const uploaded = await fetch(`/api/developer/releases/${info.releaseId}/uploaded`, { method: "POST" });
      if (!uploaded.ok) throw new Error(String((await uploaded.json().catch(() => ({}))).error ?? "検査を始められませんでした"));
      setDone(true);
      setNotes("");
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      router.refresh();
    } finally {
      setPhase({ kind: "idle" });
      if (input.current) input.current.value = "";
    }
  }

  const busy = phase.kind !== "idle";
  return (
    <div className="space-y-3">
      <label className="block text-sm">
        <span className="text-muted">このバージョンの変更点(任意)</span>
        <textarea value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={2000} rows={2} className="mt-1 w-full rounded-xl border border-border bg-surface px-4 py-2" disabled={busy || disabled} />
      </label>
      <label className={`inline-block cursor-pointer rounded-full bg-brand px-5 py-2 font-semibold text-brand-foreground ${busy || disabled ? "opacity-60" : ""}`}>
        {phase.kind === "uploading" ? `アップロード中… ${phase.percent}%` : phase.kind === "registering" ? "検査を始めています…" : "APKを選んでアップロード"}
        <input ref={input} type="file" accept=".apk,application/vnd.android.package-archive" className="hidden" disabled={busy || disabled} onChange={(e) => start(e.target.files?.[0])} />
      </label>
      {phase.kind === "uploading" && (
        <div className="h-2 w-full overflow-hidden rounded-full bg-black/10">
          <div className="h-full bg-brand transition-all" style={{ width: `${phase.percent}%` }} />
        </div>
      )}
      {done && <p className="text-sm text-brand">アップロードしました。自動で検査しています(数分かかります)。結果はこの画面に出ます。</p>}
      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  );
}
