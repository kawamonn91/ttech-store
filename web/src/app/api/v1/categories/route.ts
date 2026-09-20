import { listCategories } from "@/lib/catalog";
import { internalError, jsonOk } from "@/lib/http";

export async function GET() {
  try {
    return jsonOk({ items: await listCategories() }, { cache: true });
  } catch (e) {
    return internalError(e);
  }
}
