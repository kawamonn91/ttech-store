import { NextResponse } from "next/server";
import { presignDownload } from "@/lib/r2";

const TOOL_KEY = "tools/pdf-ja-translator/PDF-JA-Translator-win64.zip";

/** Windows版ツール(PDF・PPTX日本語化)の配布。R2 の署名付きURL(15分有効)へ転送する */
export async function GET() {
  try {
    return NextResponse.redirect(await presignDownload(TOOL_KEY), 302);
  } catch (e) {
    console.error(e);
    return new NextResponse("ただいま準備中です", { status: 503 });
  }
}
