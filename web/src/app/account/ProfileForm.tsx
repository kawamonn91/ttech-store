"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { browserClient } from "@/lib/supabase-browser";

export function ProfileForm({ initialName }: { initialName: string }) {
  const router = useRouter();
  const [name, setName] = useState(initialName);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setMessage(null);
    const supabase = browserClient();
    const {
      data: { user },
    } = await supabase.auth.getUser();
    if (!user) return;
    const { error } = await supabase.from("profiles").update({ display_name: name }).eq("id", user.id);
    setBusy(false);
    if (error) {
      setError("保存に失敗しました");
      return;
    }
    setMessage("保存しました");
    router.refresh();
  }

  return (
    <form onSubmit={save} className="flex items-end gap-2">
      <label className="flex-1">
        <span className="mb-1 block text-xs text-muted">ユーザーネーム</span>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={60}
          className="w-full rounded-xl border border-border bg-surface px-4 py-2"
        />
      </label>
      <button disabled={busy} className="rounded-xl bg-brand px-4 py-2 font-semibold text-brand-foreground disabled:opacity-60">
        保存
      </button>
      {message && <span className="text-sm text-brand">{message}</span>}
      {error && <span className="text-sm text-danger">{error}</span>}
    </form>
  );
}
