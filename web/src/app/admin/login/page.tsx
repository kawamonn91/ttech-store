"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { browserClient } from "@/lib/supabase-browser";

export default function AdminLoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const { error } = await browserClient().auth.signInWithPassword({ email, password });
    if (error) {
      setError("メールアドレスまたはパスワードが正しくありません");
      setBusy(false);
      return;
    }
    router.push("/admin");
    router.refresh();
  }

  return (
    <form onSubmit={submit} className="mx-auto max-w-sm space-y-4">
      <h1 className="text-xl font-bold">管理コンソール ログイン</h1>
      <input
        type="email"
        required
        autoComplete="username"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="メールアドレス"
        className="w-full rounded-xl border border-border bg-surface px-4 py-2"
      />
      <input
        type="password"
        required
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="パスワード"
        className="w-full rounded-xl border border-border bg-surface px-4 py-2"
      />
      {error && <p className="text-sm text-danger">{error}</p>}
      <button disabled={busy} className="w-full rounded-xl bg-brand px-4 py-2 font-semibold text-brand-foreground disabled:opacity-60">
        {busy ? "ログイン中…" : "ログイン"}
      </button>
    </form>
  );
}
