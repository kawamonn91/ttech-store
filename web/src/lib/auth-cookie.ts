/**
 * ログインのセッションを入れる Cookie の名前。
 * supabase-js の既定は「sb-<接続先 URL のホスト名の先頭>-auth-token」なので、サーバーが
 * INTERNAL_SUPABASE_URL(同じサーバーの中の Supabase)へ直接つなぐと、ブラウザ(公開の URL につなぐ)と
 * 名前が食い違い、ブラウザでログインしてもサーバーでは未ログインになる。
 * 公開の URL(NEXT_PUBLIC_SUPABASE_URL)から作った名前に、ブラウザとサーバーの両方をそろえる。
 */
export function authCookieName(publicSupabaseUrl: string): string {
  return `sb-${new URL(publicSupabaseUrl).hostname.split(".")[0]}-auth-token`;
}
