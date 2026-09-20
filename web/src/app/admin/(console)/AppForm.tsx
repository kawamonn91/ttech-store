"use client";

import { useActionState, useState } from "react";
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
const SLUG_RE = /^[a-z0-9][a-z0-9-]{1,62}$/;
const PACKAGE_RE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/;

/** よくある語だけのセグメントは避け、パッケージ名から「そのアプリらしい」部分をスラッグ案として拾う */
function suggestSlug(packageName: string): string {
  const generic = new Set(["app", "android", "mobile", "client", "www", "com", "co", "jp", "org", "net", "io"]);
  const parts = packageName.split(".").filter(Boolean);
  for (let i = parts.length - 1; i >= 0; i--) {
    const seg = parts[i]
      .toLowerCase()
      .replace(/[_\s]+/g, "-")
      .replace(/[^a-z0-9-]/g, "");
    if (seg && !generic.has(seg)) return seg;
  }
  return "";
}

function Hint({ ok, text }: { ok: boolean; text: string }) {
  return <p className={"mt-1 text-xs " + (ok ? "text-muted" : "text-danger")}>{text}</p>;
}

/**
 * 新規作成と編集で共通のフォーム。編集時は slug と packageName を変更不可にする。
 *
 * すべての項目を制御コンポーネントにしているのは、サーバー側のバリデーションに失敗しても
 * (例: パッケージ名の形式エラー)入力済みの内容が消えないようにするため。
 */
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

  const [name, setName] = useState(initial.name ?? "");
  const [slug, setSlug] = useState(initial.slug ?? "");
  const [slugEdited, setSlugEdited] = useState(Boolean(initial.slug));
  const [packageName, setPackageName] = useState(initial.packageName ?? "");
  const [shortDesc, setShortDesc] = useState(initial.shortDesc ?? "");
  const [description, setDescription] = useState(initial.description ?? "");
  const [categoryId, setCategoryId] = useState(initial.categoryId != null ? String(initial.categoryId) : "");

  const slugValid = slug === "" || SLUG_RE.test(slug);
  const packageValid = packageName === "" || PACKAGE_RE.test(packageName);

  // パッケージ名から、まだ自分でスラッグを編集していなければ提案を自動入力する
  function fillSlugFromPackage() {
    if (slugEdited || !PACKAGE_RE.test(packageName)) return;
    const suggestion = suggestSlug(packageName);
    if (suggestion) setSlug(suggestion);
  }

  return (
    <form action={formAction} className="space-y-4">
      <label className="block">
        <span className="text-sm text-muted">アプリ名</span>
        <input
          name="name"
          required
          maxLength={60}
          value={name}
          onChange={(e) => setName(e.target.value)}
          className={input}
        />
      </label>
      {!editing && (
        <>
          <label className="block">
            <span className="text-sm text-muted">パッケージ名(APKの applicationId と一致させる。例: jp.yomumemo.app)</span>
            <input
              name="packageName"
              required
              placeholder="jp.example.app"
              value={packageName}
              onChange={(e) => setPackageName(e.target.value)}
              onBlur={fillSlugFromPackage}
              className={input}
            />
            <Hint ok={packageValid} text={packageValid ? "ドットで区切って2つ以上、各部分は英字で始まる" : "形式が正しくありません(例: jp.example.app)"} />
          </label>
          <label className="block">
            <span className="text-sm text-muted">スラッグ(URLに使われます)</span>
            <input
              name="slug"
              required
              placeholder="yomumemo"
              value={slug}
              onChange={(e) => {
                setSlugEdited(true);
                setSlug(e.target.value);
              }}
              className={input}
            />
            <Hint
              ok={slugValid}
              text={slugValid ? "パッケージ名から自動入力されます。必要なら書き換えてください" : "半角英小文字・数字・ハイフンで2〜63文字"}
            />
          </label>
        </>
      )}
      <label className="block">
        <span className="text-sm text-muted">ひとこと説明(80文字まで)</span>
        <input name="shortDesc" maxLength={80} value={shortDesc} onChange={(e) => setShortDesc(e.target.value)} className={input} />
      </label>
      <label className="block">
        <span className="text-sm text-muted">詳しい説明</span>
        <textarea
          name="description"
          rows={8}
          maxLength={4000}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          className={input}
        />
      </label>
      <label className="block">
        <span className="text-sm text-muted">カテゴリ</span>
        <select name="categoryId" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} className={input}>
          <option value="">(未設定)</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
      {state.error && <p className="text-sm text-danger">{state.error}</p>}
      <button
        disabled={pending || !slugValid || !packageValid}
        className="rounded-xl bg-brand px-6 py-2 font-semibold text-brand-foreground disabled:opacity-60"
      >
        {pending ? "保存中…" : editing ? "保存" : "登録"}
      </button>
    </form>
  );
}
