"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";

export interface ReleaseView {
  id: string;
  version_name: string | null;
  version_code: number | null;
  status: "uploaded" | "scanned" | "approved" | "rejected" | "published";
  apk_size: number | null;
  sha256: string | null;
  signing_cert_sha256: string | null;
  permissions: string[];
  release_notes: string;
  scan_result: { error?: string; virusTotal?: { status: string; permalink?: string } } | null;
  created_at: string;
}

const STATUS_LABEL: Record<ReleaseView["status"], string> = {
  uploaded: "検査待ち",
  scanned: "承認待ち",
  approved: "非公開(承認済み)",
  rejected: "却下",
  published: "公開中",
};

function mb(bytes: number | null) {
  return bytes == null ? "-" : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function api(path: string, body?: unknown): Promise<{ ok: boolean; data: Record<string, unknown> }> {
  const res = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body ?? {}),
  });
  const data = await res.json().catch(() => ({}));
  return { ok: res.ok, data };
}

/** APK を署名付きURLで R2 に直接PUTする。進捗を出すため fetch ではなく XHR を使う */
function putWithProgress(url: string, file: File, onProgress: (ratio: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", url);
    xhr.setRequestHeader("Content-Type", "application/vnd.android.package-archive");
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(e.loaded / e.total);
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`アップロードに失敗しました (${xhr.status})`)));
    xhr.onerror = () => reject(new Error("アップロードに失敗しました(通信エラー / R2 の CORS 設定を確認してください)"));
    xhr.send(file);
  });
}

export function ReleaseManager({
  appId,
  releases,
  lockedCert,
}: {
  appId: string;
  releases: ReleaseView[];
  lockedCert: string | null;
}) {
  const router = useRouter();
  const fileRef = useRef<HTMLInputElement>(null);
  const [notes, setNotes] = useState("");
  const [progress, setProgress] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function upload(file: File) {
    setError(null);
    setMessage(null);
    if (!file.name.toLowerCase().endsWith(".apk")) return setError("APK ファイルを選んでください(AAB は配布できません)");
    try {
      setProgress(0);
      const init = await api(`/api/admin/apps/${appId}/releases`, { releaseNotes: notes });
      if (!init.ok) throw new Error(String(init.data.error ?? "リリースを作成できませんでした"));
      const { releaseId, uploadUrl } = init.data as { releaseId: string; uploadUrl: string };

      await putWithProgress(uploadUrl, file, setProgress);
      setProgress(null);

      const done = await api(`/api/admin/releases/${releaseId}/uploaded`);
      if (!done.ok) throw new Error(String(done.data.error ?? "検査を開始できませんでした"));
      setMessage(
        done.data.dispatched
          ? "アップロードしました。APK検査が完了するまで数分かかります(画面を再読み込みして確認してください)。"
          : String(done.data.note ?? "アップロードしました。"),
      );
      setNotes("");
      router.refresh();
    } catch (e) {
      setProgress(null);
      setError(e instanceof Error ? e.message : "アップロードに失敗しました");
    } finally {
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function decide(id: string, action: "publish" | "reject" | "unpublish") {
    setError(null);
    const res = await api(`/api/admin/releases/${id}/decision`, { action });
    if (!res.ok) setError(String(res.data.error ?? "操作に失敗しました"));
    router.refresh();
  }

  const btn = "rounded-full border border-border bg-surface px-3 py-1 text-sm font-semibold";
  return (
    <div className="space-y-4">
      <div className="space-y-2 rounded-xl border border-border bg-surface p-4">
        <p className="font-semibold">新しいバージョンをアップロード</p>
        {lockedCert && (
          <p className="text-xs text-muted">
            署名鍵は固定済みです(SHA-256: <code>{lockedCert.slice(0, 16)}…</code>)。別の鍵で署名したAPKは自動的に却下されます。
          </p>
        )}
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
          placeholder="リリースノート(更新内容)"
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        />
        <input ref={fileRef} type="file" accept=".apk" disabled={progress !== null} onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])} />
        {progress !== null && (
          <div className="h-2 overflow-hidden rounded-full bg-border">
            <div className="h-full bg-brand" style={{ width: `${Math.round(progress * 100)}%` }} />
          </div>
        )}
        {message && <p className="text-sm">{message}</p>}
        {error && <p className="text-sm text-danger">{error}</p>}
      </div>

      {releases.length === 0 ? (
        <p className="text-sm text-muted">まだリリースがありません。</p>
      ) : (
        <ul className="space-y-2">
          {releases.map((r) => (
            <li key={r.id} className="rounded-xl border border-border bg-surface p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <span className="font-semibold">{r.version_name ?? "(検査中)"}</span>
                  {r.version_code != null && <span className="ml-2 text-sm text-muted">versionCode {r.version_code}</span>}
                  <span className="ml-3 rounded-full bg-border px-2 py-0.5 text-xs">{STATUS_LABEL[r.status]}</span>
                </div>
                <div className="flex gap-2">
                  {(r.status === "scanned" || r.status === "approved") && (
                    <button className={btn + " !bg-brand !text-brand-foreground"} onClick={() => decide(r.id, "publish")}>
                      公開する
                    </button>
                  )}
                  {r.status === "published" && (
                    <button className={btn} onClick={() => decide(r.id, "unpublish")}>
                      公開停止
                    </button>
                  )}
                  {r.status !== "published" && r.status !== "rejected" && (
                    <button className={btn} onClick={() => decide(r.id, "reject")}>
                      却下
                    </button>
                  )}
                </div>
              </div>
              <dl className="mt-2 grid grid-cols-[7rem_1fr] gap-y-0.5 text-xs text-muted">
                <dt>サイズ</dt>
                <dd>{mb(r.apk_size)}</dd>
                {r.sha256 && (
                  <>
                    <dt>SHA-256</dt>
                    <dd className="break-all">{r.sha256}</dd>
                  </>
                )}
                {r.signing_cert_sha256 && (
                  <>
                    <dt>署名証明書</dt>
                    <dd className="break-all">{r.signing_cert_sha256}</dd>
                  </>
                )}
                {r.permissions.length > 0 && (
                  <>
                    <dt>権限</dt>
                    <dd>{r.permissions.map((p) => p.replace("android.permission.", "")).join(", ")}</dd>
                  </>
                )}
                {r.scan_result?.virusTotal && (
                  <>
                    <dt>ウイルススキャン</dt>
                    <dd>
                      {r.scan_result.virusTotal.status}
                      {r.scan_result.virusTotal.permalink && (
                        <a className="ml-2 underline" href={r.scan_result.virusTotal.permalink} target="_blank" rel="noopener noreferrer">
                          詳細
                        </a>
                      )}
                    </dd>
                  </>
                )}
              </dl>
              {r.scan_result?.error && <p className="mt-2 text-sm text-danger">却下理由: {r.scan_result.error}</p>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
