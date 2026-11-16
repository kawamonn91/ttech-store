import Link from "next/link";

export interface Crumb {
  label: string;
  // 無ければリンクにしない（今いるページ）
  href?: string;
}

// 今いる場所と、そこまでの上の階層。押すとその階層へ戻れる。先頭は常にストアのトップ。
export function Breadcrumbs({ items }: { items: Crumb[] }) {
  const all: Crumb[] = [{ label: "ストア", href: "/" }, ...items];
  return (
    <nav aria-label="現在地" className="mb-4">
      <ol className="flex flex-wrap gap-1.5 text-xs text-muted">
        {all.map((item, index) => (
          <li key={`${index}-${item.label}`} className="flex items-center gap-1.5">
            {index > 0 && (
              <span aria-hidden="true" className="text-border-strong">
                /
              </span>
            )}
            {item.href ? (
              <Link href={item.href} className="underline decoration-border underline-offset-2 hover:text-brand">
                {item.label}
              </Link>
            ) : (
              <span aria-current={index === all.length - 1 ? "page" : undefined} className="text-foreground/80">
                {item.label}
              </span>
            )}
          </li>
        ))}
      </ol>
    </nav>
  );
}
