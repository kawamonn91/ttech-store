const BASE = "https://same-site.invalid";

/**
 * ログイン後などに進む先(?next=)を、このサイトの中のパスに限る。
 * 「//evil.example」や「/\evil.example」、途中にタブを挟んだものなど、ブラウザが別のサイトとして扱う値は、
 * 既定の行き先に置き換える(ブラウザと同じ規則で URL として解釈して、オリジンが変わらないかで判断する)。
 */
export function safeNextPath(next: string | null | undefined, fallback: string): string {
  if (!next) return fallback;
  try {
    const url = new URL(next, BASE);
    return url.origin === BASE ? `${url.pathname}${url.search}${url.hash}` : fallback;
  } catch {
    return fallback;
  }
}
