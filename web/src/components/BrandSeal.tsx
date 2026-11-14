// T-tech のマーク。朱の角印に T。app/icon.svg と同じ形（www・UoA Online とも共通）。
export function BrandSeal({ size = 26 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false" className="shrink-0">
      <rect x="1" y="1" width="30" height="30" rx="5" fill="var(--brand)" />
      <rect x="4" y="4" width="24" height="24" rx="2.5" fill="none" stroke="var(--brand-foreground)" strokeWidth="1.2" opacity="0.55" />
      <path d="M9 10h14v3.2h-5.4V24h-3.2V13.2H9z" fill="var(--brand-foreground)" />
    </svg>
  );
}
