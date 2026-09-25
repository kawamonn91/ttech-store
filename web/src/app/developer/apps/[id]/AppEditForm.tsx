"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { AppFields, type AppFieldValues, type Category } from "../../AppFields";

export function AppEditForm({ appId, initial, categories, disabled }: { appId: string; initial: AppFieldValues; categories: Category[]; disabled: boolean }) {
  const router = useRouter();
  const [values, setValues] = useState(initial);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    const res = await fetch(`/api/developer/apps/${appId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(values),
    });
    setBusy(false);
    if (!res.ok) {
      setMessage({ text: String((await res.json().catch(() => ({}))).error ?? "保存に失敗しました"), ok: false });
      return;
    }
    setMessage({ text: "保存しました", ok: true });
    router.refresh();
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <fieldset disabled={disabled} className="space-y-3">
        <AppFields values={values} onChange={setValues} categories={categories} />
      </fieldset>
      {message && <p className={`text-sm ${message.ok ? "text-brand" : "text-danger"}`}>{message.text}</p>}
      <button disabled={busy || disabled} className="rounded-full bg-brand px-5 py-2 font-semibold text-brand-foreground disabled:opacity-60">
        {busy ? "保存しています…" : "保存する"}
      </button>
    </form>
  );
}
