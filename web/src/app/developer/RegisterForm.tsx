"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

const INPUT = "w-full rounded-xl border border-border bg-surface px-4 py-2";

/** 開発者としての会員登録。承認を待たず、登録するとすぐにアプリを登録できる */
export function RegisterForm({ defaultEmail }: { defaultEmail: string }) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [website, setWebsite] = useState("");
  const [accept, setAccept] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const res = await fetch("/api/developer/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, contactEmail, website, acceptTerms: accept }),
    });
    setBusy(false);
    if (!res.ok) {
      setError(String((await res.json().catch(() => ({}))).error ?? "登録に失敗しました"));
      return;
    }
    router.refresh();
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <label className="block text-sm">
        <span className="text-muted">開発者名(ストアに表示されます)</span>
        <input value={name} onChange={(e) => setName(e.target.value)} required minLength={2} maxLength={40} placeholder="例: ケイのアプリ工房" className={`${INPUT} mt-1`} />
      </label>
      <label className="block text-sm">
        <span className="text-muted">連絡先メールアドレス(公開されません。運営からの連絡・審査結果の通知に使います)</span>
        <input type="email" value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} placeholder={defaultEmail || "you@example.com"} className={`${INPUT} mt-1`} />
      </label>
      <label className="block text-sm">
        <span className="text-muted">ウェブサイト(任意。ストアに表示されます)</span>
        <input type="url" value={website} onChange={(e) => setWebsite(e.target.value)} placeholder="https://" className={`${INPUT} mt-1`} />
      </label>
      <label className="flex items-start gap-2 text-sm">
        <input type="checkbox" checked={accept} onChange={(e) => setAccept(e.target.checked)} className="mt-1" />
        <span>
          <Link href="/legal/developer" target="_blank" className="text-brand underline">
            開発者向け規約
          </Link>
          に同意します
        </span>
      </label>
      {error && <p className="text-sm text-danger">{error}</p>}
      <button disabled={busy || !accept} className="rounded-full bg-brand px-5 py-2 font-semibold text-brand-foreground disabled:opacity-60">
        {busy ? "登録しています…" : "開発者として登録する"}
      </button>
    </form>
  );
}
