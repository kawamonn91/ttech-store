import { describe, expect, it } from "vitest";
import { APP_STATUS_LABEL, releaseState } from "./release-status";

describe("releaseState", () => {
  it("状態ごとに、開発者に分かる言葉を返す", () => {
    expect(releaseState({ status: "uploaded" })).toMatchObject({ label: "検査中", tone: "wait" });
    expect(releaseState({ status: "rejected" })).toMatchObject({ label: "公開できませんでした", tone: "bad" });
    expect(releaseState({ status: "approved" }).tone).toBe("muted");
  });

  it("疑いがあって承認待ちのものは「運営が確認中」。自動審査を通って公開されたものは、そう分かる", () => {
    expect(releaseState({ status: "scanned", policy_verdict: "needs_review" })).toMatchObject({ label: "運営が確認中", tone: "wait" });
    expect(releaseState({ status: "scanned", policy_verdict: "auto_approve" }).label).toBe("承認待ち");
    expect(releaseState({ status: "published", auto_approved: true }).label).toBe("公開中(自動審査を通過)");
    expect(releaseState({ status: "published", auto_approved: false }).label).toBe("公開中");
  });

  it("未知の状態でも壊れない", () => {
    expect(releaseState({ status: "weird" }).label).toBe("weird");
    expect(APP_STATUS_LABEL.suspended.tone).toBe("bad");
  });
});
