import { describe, expect, it } from "vitest";
import { safeNextPath } from "./redirect";

describe("safeNextPath", () => {
  it("このサイトの中のパスは、そのまま使う(クエリ・# も残す)", () => {
    expect(safeNextPath("/account", "/x")).toBe("/account");
    expect(safeNextPath("/developer/apps/1?tab=a#b", "/x")).toBe("/developer/apps/1?tab=a#b");
  });

  it("無い・空なら既定の行き先", () => {
    expect(safeNextPath(null, "/account")).toBe("/account");
    expect(safeNextPath(undefined, "/account")).toBe("/account");
    expect(safeNextPath("", "/account")).toBe("/account");
  });

  it("別のサイトに行く値は、既定の行き先に置き換える", () => {
    for (const next of [
      "https://evil.example/",
      "//evil.example",
      "/\\evil.example",
      "/\t/evil.example",
      "\\\\evil.example",
      "javascript:alert(1)",
      "http:evil.example",
    ]) {
      expect(safeNextPath(next, "/account"), next).toBe("/account");
    }
  });

  it("オリジンにつなげても別のホストにならない(パスとして扱う)", () => {
    expect(safeNextPath("@evil.example", "/account")).toBe("/@evil.example");
    expect(safeNextPath(".evil.example", "/account")).toBe("/.evil.example");
  });
});
