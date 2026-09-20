import { beforeEach, describe, expect, it, vi } from "vitest";

const { issueDownload } = vi.hoisted(() => ({ issueDownload: vi.fn() }));
vi.mock("@/lib/download", async () => {
  class DownloadError extends Error {
    constructor(
      message: string,
      readonly status: number,
    ) {
      super(message);
    }
  }
  return { DownloadError, issueDownload };
});

import { DownloadError } from "@/lib/download";
import { POST } from "./route";

const ID = "11111111-1111-4111-8111-111111111111";
const call = (id: string, body: unknown) =>
  POST(
    new Request("http://localhost/api", { method: "POST", body: typeof body === "string" ? body : JSON.stringify(body) }),
    { params: Promise.resolve({ id }) },
  );

beforeEach(() => {
  issueDownload.mockReset();
});

describe("POST /api/v1/releases/:id/download", () => {
  it("正常: 200 で DownloadInfo を返し、端末IDを渡す", async () => {
    issueDownload.mockResolvedValue({ url: "https://r2/x", versionCode: 3 });
    const res = await call(ID, { deviceId: "device-abcdef" });
    expect(res.status).toBe(200);
    expect(await res.json()).toMatchObject({ url: "https://r2/x" });
    expect(issueDownload).toHaveBeenCalledWith(ID, "device-abcdef", null);
    expect(res.headers.get("cache-control")).toBe("no-store");
  });

  it("IDがUUIDでなければ 404(DBに問い合わせない)", async () => {
    const res = await call("not-a-uuid", { deviceId: "device-abcdef" });
    expect(res.status).toBe(404);
    expect(issueDownload).not.toHaveBeenCalled();
  });

  it.each([
    ["deviceId なし", {}],
    ["deviceId が短すぎる", { deviceId: "abc" }],
    ["deviceId が長すぎる", { deviceId: "x".repeat(101) }],
    ["deviceId が文字列でない", { deviceId: 12345678 }],
    ["JSONでない", "not json"],
  ])("不正なリクエスト(%s)は 400", async (_name, body) => {
    const res = await call(ID, body);
    expect(res.status).toBe(400);
    expect(issueDownload).not.toHaveBeenCalled();
  });

  it("公開されていないリリースは、サービスのエラーの状態コードと文言をそのまま返す", async () => {
    issueDownload.mockRejectedValue(new DownloadError("このアプリは現在ダウンロードできません", 404));
    const res = await call(ID, { deviceId: "device-abcdef" });
    expect(res.status).toBe(404);
    expect(await res.json()).toEqual({ error: "このアプリは現在ダウンロードできません" });
  });

  it("想定外のエラーは 500 で、内部情報を漏らさない", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    issueDownload.mockRejectedValue(new Error("connection string postgres://secret"));
    const res = await call(ID, { deviceId: "device-abcdef" });
    expect(res.status).toBe(500);
    expect(JSON.stringify(await res.json())).not.toContain("secret");
  });
});
