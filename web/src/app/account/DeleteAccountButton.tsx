"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { browserClient } from "@/lib/supabase-browser";

export function DeleteAccountButton({ hasApps }: { hasApps: boolean }) {
  const router = useRouter();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function doDelete() {
    setBusy(true);
    setError(null);
    const res = await fetch("/api/account/delete", { method: "POST" });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      setError(body.error ?? "削除に失敗しました");
      setBusy(false);
      return;
    }
    await browserClient().auth.signOut();
    router.push("/");
    router.refresh();
  }

  if (hasApps) {
    return <p className="text-sm text-muted">作成したアプリが残っているため退会できません。先にアプリを削除するか、運営にご相談ください。</p>;
  }

  if (!confirming) {
    return (
      <button onClick={() => setConfirming(true)} className="rounded-xl border border-danger px-4 py-2 text-sm font-semibold text-danger">
        退会する
      </button>
    );
  }

  return (
    <div className="space-y-2 rounded-xl border border-danger p-4">
      <p className="text-sm">本当に退会しますか?この操作は取り消せません。</p>
      {error && <p className="text-sm text-danger">{error}</p>}
      <div className="flex gap-2">
        <button
          onClick={doDelete}
          disabled={busy}
          className="rounded-xl bg-danger px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {busy ? "処理中…" : "退会を確定する"}
        </button>
        <button onClick={() => setConfirming(false)} className="rounded-xl border border-border px-4 py-2 text-sm">
          キャンセル
        </button>
      </div>
    </div>
  );
}
