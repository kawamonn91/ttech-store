import { getHome } from "@/lib/catalog";
import { internalError, jsonOk } from "@/lib/http";

export async function GET() {
  try {
    return jsonOk(await getHome(), { cache: true });
  } catch (e) {
    return internalError(e);
  }
}
