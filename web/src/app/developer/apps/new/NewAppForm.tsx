"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { AppFields, type AppFieldValues, type Category } from "../../AppFields";

const INPUT = "w-full rounded-xl border border-border bg-surface px-4 py-2";

export function NewAppForm({ categories }: { categories: Category[] }) {
  const router = useRouter();
  const [packageName, setPackageName] = useState("");
  const [slug, setSlug] = useState("");
  const [values, setValues] = useState<AppFieldValues>({ name: "", shortDesc: "", description: "", categoryId: null });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const res = await fetch("/api/developer/apps", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ packageName, slug, ...values }),
    });
    const body = await res.json().catch(() => ({}));
    setBusy(false);
    if (!res.ok) {
      setError(String(body.error ?? "登録に失敗しました"));
      return;
    }
    router.push(`/developer/apps/${body.id}`);
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <label className="block text-sm">
        <span className="text-muted">パッケージ名(アプリの applicationId。あとから変えられません)</span>
        <input value={packageName} onChange={(e) => setPackageName(e.target.value)} required placeholder="jp.example.myapp" autoCapitalize="none" className={`${INPUT} mt-1 font-mono`} />
      </label>
      <label className="block text-sm">
        <span className="text-muted">URL用の名前(半角英小文字・数字・ハイフン。空ならパッケージ名から自動で決めます)</span>
        <input value={slug} onChange={(e) => setSlug(e.target.value)} placeholder="my-app" autoCapitalize="none" className={`${INPUT} mt-1 font-mono`} />
      </label>
      <AppFields values={values} onChange={setValues} categories={categories} />
      {error && <p className="text-sm text-danger">{error}</p>}
      <button disabled={busy} className="rounded-full bg-brand px-5 py-2 font-semibold text-brand-foreground disabled:opacity-60">
        {busy ? "登録しています…" : "登録して、APKのアップロードへ進む"}
      </button>
    </form>
  );
}
