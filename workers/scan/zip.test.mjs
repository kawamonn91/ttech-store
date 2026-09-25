// node --test workers/scan/zip.test.mjs
import assert from "node:assert/strict";
import { writeFileSync } from "node:fs";
import { test } from "node:test";
import { classifyMagic, inspectZip } from "./facts.mjs";
import { makeZip } from "./testing.mjs";
import { openZip } from "./zip.mjs";

const ELF = Buffer.concat([Buffer.from([0x7f, 0x45, 0x4c, 0x46]), Buffer.alloc(60, 1)]);
const DEX = Buffer.concat([Buffer.from("dex\n035\0"), Buffer.alloc(60, 2)]);

test("ZIPの項目一覧と、圧縮・無圧縮の両方の先頭バイト・全体を読める", () => {
  const file = makeZip([
    { name: "AndroidManifest.xml", data: "manifest-bytes", method: 8 },
    { name: "assets/a.bin", data: ELF, method: 0 },
    { name: "assets/b.bin", data: DEX, method: 8 },
  ]);
  const zip = openZip(file);
  try {
    assert.deepEqual(zip.entries.map((e) => e.name), ["AndroidManifest.xml", "assets/a.bin", "assets/b.bin"]);
    assert.deepEqual(zip.anomalies, []);
    const [manifest, a, b] = zip.entries;
    assert.equal(manifest.size, "manifest-bytes".length);
    assert.equal(zip.read(manifest).toString(), "manifest-bytes");
    assert.equal(zip.head(a, 4).toString("hex"), "7f454c46");
    assert.equal(zip.head(b, 4).toString("latin1"), "dex\n");
    assert.deepEqual(zip.read(b), DEX);
  } finally {
    zip.close();
  }
});

test("展開後のサイズが上限を超えるファイルは読まない(ZIP爆弾対策)", () => {
  const file = makeZip([{ name: "big.bin", data: Buffer.alloc(10_000, 0) }]);
  const zip = openZip(file);
  try {
    assert.equal(zip.read(zip.entries[0], 5_000), null);
    assert.equal(zip.read(zip.entries[0], 20_000).length, 10_000);
  } finally {
    zip.close();
  }
});

test("構造の異常(同名の重複・不正なパス・暗号化)を検出する", () => {
  const file = makeZip([
    { name: "classes.dex", data: "a" },
    { name: "classes.dex", data: "b" },
    { name: "../evil", data: "c" },
    { name: "secret.bin", data: "d", flags: 1 },
  ]);
  const zip = openZip(file);
  try {
    const text = zip.anomalies.join("\n");
    assert.match(text, /同じ名前のファイルが複数あります: classes\.dex/);
    assert.match(text, /不正なパスのファイルがあります: \.\.\/evil/);
    assert.match(text, /暗号化されたファイルがあります: secret\.bin/);
  } finally {
    zip.close();
  }
});

test("ZIPでないファイルは、読み取れないことを例外で示す", () => {
  const file = makeZip([]);
  writeFileSync(file, Buffer.from("これはZIPではありません".repeat(10)));
  assert.throws(() => openZip(file), /ZIP\(APK\)として読み取れません/);
});

test("先頭のバイト列から、実行ファイル・入れ子のZIPなどを見分ける", () => {
  assert.equal(classifyMagic(ELF), "elf");
  assert.equal(classifyMagic(DEX), "dex");
  assert.equal(classifyMagic(Buffer.from([0x50, 0x4b, 0x03, 0x04, 0])), "zip");
  assert.equal(classifyMagic(Buffer.from("#!/system/bin/sh\n")), "script");
  assert.equal(classifyMagic(Buffer.from([0xca, 0xfe, 0xba, 0xbe, 0, 0, 0, 52])), "java-class");
  assert.equal(classifyMagic(Buffer.from([0x89, 0x50, 0x4e, 0x47])), null); // PNG
  assert.equal(classifyMagic(Buffer.alloc(0)), null);
});

test("APKの中身の分類: DEX・ネイティブライブラリ(SHA-256つき)・隠された実行ファイル・無視するもの", () => {
  const lib = Buffer.concat([ELF, Buffer.from("libcontent")]);
  const file = makeZip([
    { name: "AndroidManifest.xml", data: "x" },
    { name: "classes.dex", data: DEX },
    { name: "classes2.dex", data: DEX },
    { name: "lib/arm64-v8a/libfoo.so", data: lib },
    { name: "assets/payload.bin", data: ELF },
    { name: "assets/nested.zip", data: Buffer.from([0x50, 0x4b, 0x03, 0x04, 1, 2, 3]) },
    { name: "assets/readme.txt", data: "ふつうのテキスト" },
    { name: "res/drawable/icon.png", data: Buffer.from([0x89, 0x50, 0x4e, 0x47, 1, 2]) },
    { name: "DebugProbesKt.bin", data: Buffer.from([0xca, 0xfe, 0xba, 0xbe, 0, 0, 0, 52]) },
    { name: "META-INF/androidx.core_core.version", data: "1.0" },
    { name: "assets/dir/", data: "" },
  ]);
  const f = inspectZip(file);
  assert.deepEqual(f.dexFiles.map((d) => d.name), ["classes.dex", "classes2.dex"]);
  assert.equal(f.nativeLibs.length, 1);
  assert.equal(f.nativeLibs[0].abi, "arm64-v8a");
  assert.equal(f.nativeLibs[0].sha256.length, 64);
  assert.deepEqual(f.embedded.map((e) => `${e.path}:${e.kind}`).sort(), ["DebugProbesKt.bin:java-class", "assets/nested.zip:zip", "assets/payload.bin:elf"]);
  assert.deepEqual(f.anomalies, []);
});
