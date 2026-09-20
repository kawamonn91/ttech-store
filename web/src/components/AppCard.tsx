import Link from "next/link";
import type { AppSummaryDto } from "@/lib/dto";

export function AppIcon({ url, size = 64 }: { url: string | null; size?: number }) {
  const style = { width: size, height: size, borderRadius: size / 4.5 };
  if (!url) {
    return (
      <div style={style} className="flex shrink-0 items-center justify-center bg-border text-muted" aria-hidden>
        <svg viewBox="0 0 24 24" width={size / 2} height={size / 2} fill="currentColor">
          <path d="M6 18c0 .55.45 1 1 1h1v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h2v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h1c.55 0 1-.45 1-1V8H6v10zM3.5 8C2.67 8 2 8.67 2 9.5v7c0 .83.67 1.5 1.5 1.5S5 17.33 5 16.5v-7C5 8.67 4.33 8 3.5 8zm17 0c-.83 0-1.5.67-1.5 1.5v7c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5v-7c0-.83-.67-1.5-1.5-1.5zm-4.97-5.84l1.3-1.3c.2-.2.2-.51 0-.71-.2-.2-.51-.2-.71 0l-1.48 1.48A5.84 5.84 0 0012 1c-.96 0-1.86.23-2.66.63L7.85.15c-.2-.2-.51-.2-.71 0-.2.2-.2.51 0 .71l1.31 1.31A5.983 5.983 0 006 7h12c0-1.99-.97-3.75-2.47-4.84zM10 5H9V4h1v1zm5 0h-1V4h1v1z" />
        </svg>
      </div>
    );
  }
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={url} alt="" style={style} className="shrink-0 object-cover" width={size} height={size} />;
}

export function formatCount(n: number): string {
  if (n < 1000) return String(n);
  if (n < 10000) return n.toLocaleString("ja-JP");
  return `${(n / 10000).toFixed(1).replace(/\.0$/, "")}万`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`;
}

export function AppCard({ app }: { app: AppSummaryDto }) {
  return (
    <Link
      href={`/apps/${app.slug}`}
      className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-3 transition hover:border-brand"
    >
      <AppIcon url={app.iconUrl} />
      <div className="min-w-0">
        <div className="truncate font-semibold">{app.name}</div>
        {app.shortDesc && <div className="truncate text-sm text-muted">{app.shortDesc}</div>}
        <div className="mt-0.5 flex flex-wrap gap-x-3 text-xs text-muted">
          {app.ratingCount > 0 && <span>★ {app.ratingAvg.toFixed(1)}</span>}
          <span>{formatCount(app.downloadCount)} DL</span>
          {app.category && <span>{app.category.name}</span>}
        </div>
      </div>
    </Link>
  );
}

export function AppGrid({ apps }: { apps: AppSummaryDto[] }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {apps.map((a) => (
        <AppCard key={a.id} app={a} />
      ))}
    </div>
  );
}
