import type { Finding } from "@/lib/policy";
import { SEVERITY_LABEL, TONE_CLASS } from "@/lib/release-status";

/** 自動審査で見つかった点の一覧。開発者と運営の画面で共通に使う(参考は、既定では出さない) */
export function Findings({ findings, showInfo = false }: { findings: Finding[]; showInfo?: boolean }) {
  const items = (findings ?? []).filter((f) => showInfo || f.severity !== "info");
  if (items.length === 0) return null;
  return (
    <ul className="mt-2 space-y-2">
      {items.map((f, i) => {
        const severity = SEVERITY_LABEL[f.severity];
        return (
          <li key={`${f.code}-${i}`} className="rounded-lg border border-border bg-background px-3 py-2 text-xs leading-5">
            <div className="flex items-center gap-2">
              <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${severity ? TONE_CLASS[severity.tone] : ""}`}>{severity?.label ?? f.severity}</span>
              <span className="font-semibold">{f.title}</span>
            </div>
            <p className="mt-1 text-muted">{f.detail}</p>
            {f.evidence.length > 0 && <p className="mt-1 break-all font-mono text-[11px] text-muted">{f.evidence.slice(0, 6).join(" / ")}</p>}
          </li>
        );
      })}
    </ul>
  );
}
