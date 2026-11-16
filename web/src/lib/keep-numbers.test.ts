import { describe, expect, it } from "vitest";
import { keepNumbers } from "./keep-numbers";

const NBSP = "\u00a0";

describe("keepNumbers", () => {
  it("keeps a number with the word after it", () => {
    expect(keepNumbers("分析まで、1 台でこなせます。Android 8.0 以上")).toBe(`分析まで、1${NBSP}台でこなせます。Android 8.0${NBSP}以上`);
  });

  it("leaves other spaces alone", () => {
    expect(keepNumbers("JBA の公式スコアシートと IPv4 アドレス")).toBe("JBA の公式スコアシートと IPv4 アドレス");
  });
});
