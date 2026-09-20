import { describe, expect, it } from "vitest";
import { sanitizeQuery } from "./catalog";

describe("sanitizeQuery", () => {
  it("PostgREST の or() を壊す文字(, ( ) % * \\)を除く", () => {
    expect(sanitizeQuery("a,b(c)d%e*f\\g")).toBe("a b c d e f g");
  });
  it("前後の空白を落とし、80文字に切り詰める", () => {
    expect(sanitizeQuery("  memo  ")).toBe("memo");
    expect(sanitizeQuery("あ".repeat(200))).toHaveLength(80);
  });
  it("日本語・英数字はそのまま通す", () => {
    expect(sanitizeQuery("ヨムメモ 2")).toBe("ヨムメモ 2");
  });
  it("フィルタ注入の試み(name.eq.x,id.gt.0)が区切り文字を持たなくなる", () => {
    expect(sanitizeQuery("x,id.gt.0")).not.toContain(",");
  });
});
