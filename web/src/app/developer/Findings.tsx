import type { Finding } from "@/lib/policy";

const SEVERITY: Record<string, { label: string; className: string }> = {
  block: { label: "却下", className: "bg-red-100 text-red-800" },
  review: { label: "要確認", className: "bg-amber-100 text-amber-800" },
  info: { label: "参考", className: "bg-black/5 text-muted" },
};

/** 自動審査で見つかった点の一覧。開発者と運営の画面で共通に使う(参考は、既定では出さない) */
export function Findings({ findings, showInfo = false }: { findings: Finding[]; showInfo?: boolean }) {
  const items = (findings ?? []).filter((f) => showInfo || f.severity !== "info");
  if (items.length === 0) return null;
  return (
    <ul className="mt-2 space-y-2">
      {items.map((f, i) => (
        <li key={`${f.code}-${i}`} className="rounded-lg border border-border bg-background px-3 py-2 text-xs leading-5">
          <div className="flex items-center gap-2">
            <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${SEVERITY[f.severity]?.className ?? ""}`}>{SEVERITY[f.severity]?.label ?? f.severity}</span>
            <span className="font-semibold">{f.title}</span>
          </div>
          <p className="mt-1 text-muted">{f.detail}</p>
          {f.evidence.length > 0 && <p className="mt-1 break-all font-mono text-[11px] text-muted">{f.evidence.slice(0, 6).join(" / ")}</p>}
        </li>
      ))}
    </ul>
  );
}
