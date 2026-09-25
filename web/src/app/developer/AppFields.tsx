"use client";

export interface Category {
  id: number;
  name: string;
}

export interface AppFieldValues {
  name: string;
  shortDesc: string;
  description: string;
  categoryId: number | null;
}

const INPUT = "w-full rounded-xl border border-border bg-surface px-4 py-2";

/** アプリの掲載内容の入力欄(新規登録と編集で共通) */
export function AppFields({ values, onChange, categories }: { values: AppFieldValues; onChange: (v: AppFieldValues) => void; categories: Category[] }) {
  const set = <K extends keyof AppFieldValues>(key: K, value: AppFieldValues[K]) => onChange({ ...values, [key]: value });
  return (
    <div className="space-y-3">
      <label className="block text-sm">
        <span className="text-muted">アプリ名(60文字まで)</span>
        <input value={values.name} onChange={(e) => set("name", e.target.value)} required maxLength={60} className={`${INPUT} mt-1`} />
      </label>
      <label className="block text-sm">
        <span className="text-muted">ひとこと説明(一覧に表示されます・200文字まで)</span>
        <input value={values.shortDesc} onChange={(e) => set("shortDesc", e.target.value)} maxLength={200} className={`${INPUT} mt-1`} />
      </label>
      <label className="block text-sm">
        <span className="text-muted">くわしい説明(4000文字まで)</span>
        <textarea value={values.description} onChange={(e) => set("description", e.target.value)} maxLength={4000} rows={8} className={`${INPUT} mt-1`} />
      </label>
      <label className="block text-sm">
        <span className="text-muted">カテゴリ</span>
        <select value={values.categoryId ?? ""} onChange={(e) => set("categoryId", e.target.value ? Number(e.target.value) : null)} className={`${INPUT} mt-1`}>
          <option value="">(選択しない)</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
