import { createHash } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { getAppByPackage } from "@/lib/catalog";
import { DownloadError, issueDownload } from "@/lib/download";
import { env } from "@/lib/env";

/**
 * ストアアプリ自身のAPKだけは、最初の1回のためにWebから直接ダウンロードさせる。
 * (他のアプリは、ストアアプリ内でダウンロード・検証・インストールまで完結させる)
 */
export async function GET(request: NextRequest) {
  const app = await getAppByPackage(env.storePackageName());
  if (!app?.latest) return new NextResponse("ストアアプリは現在準備中です", { status: 404 });

  // Web には端末IDが無いので、IP+UA から作った疑似IDで24時間の重複を除く
  const ip = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? "unknown";
  const ua = request.headers.get("user-agent") ?? "";
  const pseudoId = "web-" + createHash("sha256").update(`${ip}|${ua}`).digest("hex").slice(0, 32);

  try {
    const info = await issueDownload(app.latest.releaseId, pseudoId, null);
    return NextResponse.redirect(info.url, 302);
  } catch (e) {
    if (e instanceof DownloadError) return new NextResponse(e.message, { status: e.status });
    console.error(e);
    return new NextResponse("サーバーでエラーが発生しました", { status: 500 });
  }
}
