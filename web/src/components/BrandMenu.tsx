"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { BrandSeal } from "@/components/BrandSeal";

// ロゴを押すと開くメニュー。ストアのトップと、T-tech のほかのページへ進める。
// ハブ（www）・UoA Online と同じ並び（T-tech のトップ → ストア → ストレージ → 学習）。
const OTHER_SITES = [
  { href: "https://www.kawamonn.com/", name: "T-tech のトップ", note: "サービスの一覧" },
  { href: "https://storage.kawamonn.com/", name: "UoA Online ストレージ", note: "ファイルと SSH 端末" },
  { href: "https://labs.kawamonn.com/", name: "UoA Online 学習", note: "ネットワークとセキュリティの教材" },
];

export function BrandMenu() {
  // 開いたときのページを覚えておき、ページを移ったら自然に閉じた扱いにする
  const [openedAt, setOpenedAt] = useState<string | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelId = useId();
  const pathname = usePathname();
  const open = openedAt === pathname;
  const setOpen = (value: boolean) => setOpenedAt(value ? pathname : null);

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpenedAt(null);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setOpenedAt(null);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div className="relative" ref={rootRef}>
      <button
        ref={buttonRef}
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen(!open)}
        className="-ml-2 flex min-h-10 items-center gap-2.5 rounded-md px-2 text-base font-bold transition hover:bg-surface-2 aria-expanded:bg-surface-2"
      >
        <BrandSeal />
        <span className="whitespace-nowrap">T-tech Store</span>
        <svg
          viewBox="0 0 12 12"
          aria-hidden="true"
          focusable="false"
          className={`h-3 w-3 text-muted transition-transform ${open ? "rotate-180" : ""}`}
        >
          <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" strokeWidth="1.5" />
        </svg>
        <span className="sr-only">（メニュー）</span>
      </button>
      <div
        id={panelId}
        hidden={!open}
        className="absolute top-[calc(100%+6px)] -left-2 z-20 w-[min(280px,calc(100vw-32px))] rounded-md border border-border bg-surface p-1.5 shadow-[0_8px_24px_rgb(0_0_0/0.12)]"
      >
        <Link href="/" className="grid gap-px rounded px-2.5 py-2 hover:bg-surface-2">
          <span className="flex items-center gap-2 text-sm font-semibold">
            ストアのトップ
            <span className="h-1.5 w-1.5 rounded-full bg-brand" aria-hidden="true" />
          </span>
          <span className="text-xs text-muted">アプリの一覧</span>
        </Link>
        <div className="mx-2.5 mt-1.5 mb-0.5 border-t border-border pt-2 text-[11px] tracking-wider text-muted">
          T-tech のほかのページ
        </div>
        {OTHER_SITES.map((site) => (
          <a key={site.href} href={site.href} className="grid gap-px rounded px-2.5 py-2 hover:bg-surface-2">
            <span className="text-sm font-semibold">{site.name}</span>
            <span className="text-xs text-muted">{site.note}</span>
          </a>
        ))}
      </div>
    </div>
  );
}
