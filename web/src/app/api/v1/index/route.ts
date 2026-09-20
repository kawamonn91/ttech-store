import { getIndex } from "@/lib/catalog";
import { internalError, jsonOk } from "@/lib/http";

/**
 * 更新チェック用の全件インデックス。
 * 端末側で「インストール済みの versionCode」と突き合わせるので、
 * 利用者のインストール済みアプリ一覧はサーバーに送られない。
 */
export async function GET() {
  try {
    return jsonOk({ items: await getIndex() }, { cache: true });
  } catch (e) {
    return internalError(e);
  }
}
