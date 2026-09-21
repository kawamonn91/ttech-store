import { NextResponse } from "next/server";

/** 公開カタログ用: CDN で少しだけキャッシュして Supabase への負荷を減らす */
const PUBLIC_CACHE = "public, s-maxage=60, stale-while-revalidate=300";

export function jsonOk<T>(data: T, opts: { cache?: boolean } = {}): NextResponse {
  return NextResponse.json(data, {
    headers: opts.cache ? { "Cache-Control": PUBLIC_CACHE } : { "Cache-Control": "no-store" },
  });
}

/**
 * クライアント(ストアアプリ)は { error: "..." } の error を利用者向けメッセージとして表示する。
 * `code` は機械判定用(例: "totp_required" でストアアプリにTOTP入力を促す)。
 */
export function jsonError(message: string, status: number, opts: { code?: string } = {}): NextResponse {
  return NextResponse.json(
    { error: message, ...(opts.code ? { code: opts.code } : {}) },
    { status, headers: { "Cache-Control": "no-store" } },
  );
}

export function internalError(e: unknown): NextResponse {
  console.error(e);
  return jsonError("サーバーでエラーが発生しました", 500);
}
