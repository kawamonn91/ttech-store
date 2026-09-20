import { NextRequest } from "next/server";
import { listApps, type SortKey } from "@/lib/catalog";
import { internalError, jsonOk } from "@/lib/http";

const SORTS: SortKey[] = ["new", "popular", "rating"];

export async function GET(request: NextRequest) {
  const p = request.nextUrl.searchParams;
  const sort = p.get("sort") as SortKey | null;
  try {
    const result = await listApps({
      q: p.get("q") ?? undefined,
      category: p.get("category") ?? undefined,
      sort: sort && SORTS.includes(sort) ? sort : "new",
      limit: Number(p.get("limit")) || undefined,
      offset: Number(p.get("offset")) || undefined,
    });
    return jsonOk(result, { cache: true });
  } catch (e) {
    return internalError(e);
  }
}
