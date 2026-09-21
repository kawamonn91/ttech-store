"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { browserClient } from "@/lib/supabase-browser";

export function DeveloperApplyForm() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [website, setWebsite] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const supabase = browserClient();
    const {
      data: { user },
    } = await supabase.auth.getUser();
    if (!user) return;
    const { error } = await supabase.from("developers").insert({
      user_id: user.id,
      name,
      contact_email: contactEmail || user.email,
      website: website || null,
      status: "pending",
    });
    setBusy(false);
    if (error) {
      setError("申請に失敗しました");
      return;
    }
    setDone(true);
    router.refresh();
  }

  if (done) return <p className="text-sm text-brand">申請しました。運営の承認をお待ちください。</p>;

  return (
    <form onSubmit={submit} className="space-y-2">
      <p className="text-sm text-muted">自分のアプリをT-tech Storeに公開できるようになります。運営の承認後に開発者として利用できます。</p>
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        required
        placeholder="開発者名(表示名)"
        className="w-full rounded-xl border border-border bg-surface px-4 py-2"
      />
      <input
        type="email"
        value={contactEmail}
        onChange={(e) => setContactEmail(e.target.value)}
        placeholder="連絡先メールアドレス(未入力ならログイン用のメールを使用)"
        className="w-full rounded-xl border border-border bg-surface px-4 py-2"
      />
      <input
        value={website}
        onChange={(e) => setWebsite(e.target.value)}
        placeholder="Webサイト(任意)"
        className="w-full rounded-xl border border-border bg-surface px-4 py-2"
      />
      {error && <p className="text-sm text-danger">{error}</p>}
      <button disabled={busy} className="rounded-xl bg-brand px-4 py-2 font-semibold text-brand-foreground disabled:opacity-60">
        {busy ? "送信中…" : "開発者として申請する"}
      </button>
    </form>
  );
}
