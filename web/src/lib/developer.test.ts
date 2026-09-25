import { describe, expect, it } from "vitest";
import {
  CreateAppBody, CreateReleaseBody, findReservedWord, firstIssue, isReservedPackage, nameProblem, normalizeForCompare, packageProblem,
  RegisterBody, slugProblem, suggestSlug, UpdateAppBody,
} from "./developer";

describe("normalizeForCompare / findReservedWord: なりすましになる名前", () => {
  it("大文字小文字・全角半角・空白・記号のゆれをそろえる", () => {
    expect(normalizeForCompare("ＧＯＯＧＬＥ  Maps")).toBe("googlemaps");
    expect(normalizeForCompare("T-tech_Store")).toBe("ttechstore");
  });

  it("他社・運営・公的機関を思わせる語を含む名前は使えない(ゆれがあっても検出する)", () => {
    for (const n of ["Google Maps", "ＹｏｕＴｕｂｅ公式", "T-tech 公式アプリ", "kawamonn", "楽天ペイ風", "○○銀行アプリ", "Official Support", "税務署からのお知らせ"]) {
      expect(findReservedWord(n), n).not.toBeNull();
    }
  });

  it("ふつうの名前は使える。短い語(line・au など)は、別の単語の一部なら問題にしない", () => {
    for (const n of ["ケイのアプリ", "Kei Apps", "体重ログ", "Guitar Lines", "Audio Player", "Baseline", "Pauline", "散歩マップ"]) {
      expect(findReservedWord(n), n).toBeNull();
    }
    // ただし、その語そのもの(完全一致)は不可
    expect(findReservedWord("LINE")).not.toBeNull();
    expect(findReservedWord("au")).not.toBeNull();
  });

  it("理由を、利用者に見せる文章で返す", () => {
    expect(nameProblem("app", "Google Maps")).toContain("紛らわしい");
    expect(nameProblem("developer", "ケイ")).toBeNull();
  });
});

describe("パッケージ名・slug", () => {
  it("OS・他社・運営のパッケージ名は予約されている(大文字小文字を区別しない)", () => {
    for (const p of ["com.google.android.gms", "android.app.foo", "com.android.settings", "com.ttech.myapp", "COM.Google.Maps", "jp.co.nttdocomo.x", "com.debtrunapp.x", "androidx.core.app"]) {
      expect(isReservedPackage(p), p).toBe(true);
      expect(packageProblem(p)).toContain("予約");
    }
  });

  it("自分のドメインのパッケージ名は使える。前方一致なので、似た名前は巻き込まない", () => {
    for (const p of ["jp.kei.weightlog", "com.example.app", "com.googlefan.app", "io.github.kei.app", "com.ttechnology.app"]) {
      expect(isReservedPackage(p), p).toBe(false);
      expect(packageProblem(p)).toBeNull();
    }
  });

  it("slug: 既存のページと衝突する語は使えない", () => {
    for (const s of ["new", "admin", "api", "search", "developer", "account", "login"]) expect(slugProblem(s), s).not.toBeNull();
    expect(slugProblem("weight-log")).toBeNull();
  });

  it("パッケージ名から slug の案を作る(一般的な語は飛ばす)", () => {
    expect(suggestSlug("jp.kei.weightlog")).toBe("weightlog");
    expect(suggestSlug("com.example.my_cool.app")).toBe("my-cool");
    expect(suggestSlug("com.kei.Weight.Log")).toBe("log");
    expect(suggestSlug("com.app")).toMatch(/^app-[a-z0-9]{6}$/); // 使える語が無ければ、ランダムな名前
  });
});

describe("RegisterBody(開発者の登録)", () => {
  const ok = { name: "ケイのアプリ", contactEmail: "kei@example.com", website: "https://kei.example.com", acceptTerms: true };

  it("正しい入力を受け付ける。連絡先・ウェブサイトは省略・空文字でもよい", () => {
    expect(RegisterBody.safeParse(ok).success).toBe(true);
    const r = RegisterBody.safeParse({ name: "ケイ", contactEmail: "", website: "", acceptTerms: true });
    expect(r.success).toBe(true);
    expect(r.success && r.data.contactEmail).toBeUndefined();
    expect(r.success && r.data.website).toBeUndefined();
  });

  it("規約に同意していなければ拒否する。理由が分かる文言で返す", () => {
    const r = RegisterBody.safeParse({ ...ok, acceptTerms: false });
    expect(r.success).toBe(false);
    expect(!r.success && firstIssue(r.error)).toContain("規約");
    expect(RegisterBody.safeParse({ ...ok, acceptTerms: undefined }).success).toBe(false);
  });

  it("名前の長さ・制御文字・メール・URLの形式を検証する(javascript: などは不可)", () => {
    expect(RegisterBody.safeParse({ ...ok, name: "あ" }).success).toBe(false);
    expect(RegisterBody.safeParse({ ...ok, name: "あ".repeat(41) }).success).toBe(false);
    expect(RegisterBody.safeParse({ ...ok, name: "ケイ\u0000" }).success).toBe(false);
    expect(RegisterBody.safeParse({ ...ok, contactEmail: "not-an-email" }).success).toBe(false);
    expect(RegisterBody.safeParse({ ...ok, website: "javascript:alert(1)" }).success).toBe(false);
    expect(RegisterBody.safeParse({ ...ok, website: "ftp://example.com" }).success).toBe(false);
  });
});

describe("CreateAppBody / UpdateAppBody / CreateReleaseBody", () => {
  const app = { packageName: "jp.kei.weightlog", name: "体重ログ", shortDesc: "体重を記録", description: "説明", categoryId: 2 };

  it("正しい入力を受け付け、省略できる項目には既定値が入る。slug は小文字にそろえる", () => {
    const r = CreateAppBody.safeParse({ packageName: "jp.kei.weightlog", name: "体重ログ", slug: "Weight-Log" });
    expect(r.success && r.data).toMatchObject({ slug: "weight-log", shortDesc: "", description: "" });
    expect(CreateAppBody.safeParse(app).success).toBe(true);
  });

  it("パッケージ名・slug・長さの形式を検証する", () => {
    for (const packageName of ["weightlog", "1jp.kei.app", "jp..kei", "jp.kei.app!", "jp.kei.アプリ"]) {
      expect(CreateAppBody.safeParse({ ...app, packageName }).success, packageName).toBe(false);
    }
    for (const slug of ["a", "-abc", "Abc Def", "日本語", "a".repeat(64)]) {
      expect(CreateAppBody.safeParse({ ...app, slug }).success, slug).toBe(false);
    }
    expect(CreateAppBody.safeParse({ ...app, name: "" }).success).toBe(false);
    expect(CreateAppBody.safeParse({ ...app, name: "あ".repeat(61) }).success).toBe(false);
    expect(CreateAppBody.safeParse({ ...app, shortDesc: "あ".repeat(201) }).success).toBe(false);
    expect(CreateAppBody.safeParse({ ...app, description: "あ".repeat(4001) }).success).toBe(false);
    expect(CreateAppBody.safeParse({ ...app, categoryId: 0 }).success).toBe(false);
  });

  it("更新は、指定した項目だけ。空の更新は不可", () => {
    expect(UpdateAppBody.safeParse({ name: "新しい名前" }).success).toBe(true);
    expect(UpdateAppBody.safeParse({ categoryId: null }).success).toBe(true);
    expect(UpdateAppBody.safeParse({}).success).toBe(false);
    // パッケージ名・状態などは、この経路では変えられない(無視される)
    const r = UpdateAppBody.safeParse({ name: "x", status: "published", packageName: "com.evil.app" });
    expect(r.success && Object.keys(r.data)).toEqual(["name"]);
  });

  it("リリースノートの長さ", () => {
    expect(CreateReleaseBody.safeParse({}).success).toBe(true);
    expect(CreateReleaseBody.safeParse({ releaseNotes: "あ".repeat(2001) }).success).toBe(false);
  });
});
