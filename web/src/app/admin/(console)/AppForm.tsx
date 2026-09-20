"use client";

import { useActionState } from "react";
import type { ActionState } from "./actions";

export interface AppFormValues {
  slug?: string;
  packageName?: string;
  name?: string;
  shortDesc?: string;
  description?: string;
  categoryId?: number | null;
}

const input = "w-full rounded-xl border border-border bg-surface px-4 py-2";

/** 新規作成と編集で共通のフォーム。編集時は slug と packageName を変更不可にする */
export function AppForm({
  action,
  categories,
  initial = {},
  editing = false,
}: {
  action: (prev: ActionState, form: FormData) => Promise<ActionState>;
  categories: { id: number; name: string }[];
  initial?: AppFormValues;
  editing?: boolean;
}) {
  const [state, formAction, pending] = useActionState(action, {});

  return (
    <form action={formAction} className="space-y-4">
      <label className="block">
        <span className="text-sm text-muted">アプリ名</span>
        <input name="name" required maxLength={60} defaultValue={initial.name} className={input} />
      </label>
      {!editing && (
        <>
          <label className="block">
            <span className="text-sm text-muted">スラッグ(URLに使われます。例: yomumemo)</span>
            <input name="slug" required pattern="[a-z0-9][a-z0-9\-]{1,62}" defaultValue={initial.slug} className={input} />
          </label>
          <label className="block">
            <span className="text-sm text-muted">パッケージ名(APKの applicationId と一致させる。例: jp.yomumemo.app)</span>
            <input name="packageName" required defaultValue={initial.packageName} className={input} />
          </label>
        </>
      )}
      <label className="block">
        <span className="text-sm text-muted">ひとこと説明(80文字まで)</span>
        <input name="shortDesc" maxLength={80} defaultValue={initial.shortDesc} className={input} />
      </label>
      <label className="block">
        <span className="text-sm text-muted">詳しい説明</span>
        <textarea name="description" rows={8} maxLength={4000} defaultValue={initial.description} className={input} />
      </label>
      <label className="block">
        <span className="text-sm text-muted">カテゴリ</span>
        <select name="categoryId" defaultValue={initial.categoryId ?? ""} className={input}>
          <option value="">(未設定)</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      {state.error && <p className="text-sm text-danger">{state.error}</p>}
      <button disabled={pending} className="rounded-xl bg-brand px-6 py-2 font-semibold text-brand-foreground disabled:opacity-60">
        {pending ? "保存中…" : editing ? "保存" : "登録"}
      </button>
    </form>
  );
}
