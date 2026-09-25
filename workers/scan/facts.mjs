// APK から、審査に使う「事実」をまとめて集める。
//   manifest:  権限・コンポーネント・アプリの属性(aapt2 dump xmltree)
//   dex:       OSのAPIの呼び出し・URL・native メソッド(dexdump)
//   nativeLibs: 同梱されたネイティブライブラリ(SHA-256つき)
//   embedded:  assets などに隠された実行可能ファイル(DEX / ELF / 入れ子のZIP・APK / スクリプト)
//   zip:       ZIPの構造上の異常
// ここでは「事実の抽出」だけを行い、通す・止めるの判断(ルール)は Web 側 web/src/lib/policy.ts が行う。
// 解析できなかった部分は incomplete に理由を残す(その場合、Web側は自動承認せず運営の確認に回す)。

import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { promisify } from "node:util";
import { extractManifestFacts, parseXmlTree } from "./manifest.mjs";
import { mergeDexFacts, runDexdump } from "./dex.mjs";
import { openZip } from "./zip.mjs";

const run = promisify(execFile);

export const FACTS_VERSION = 1;

const MAX_NATIVE_BYTES = 64 * 1024 * 1024;
const MAX_DEX_BYTES = 128 * 1024 * 1024;
const MAX_ENTRIES = 50_000;
const MAX_TOTAL_BYTES = 2 * 1024 * 1024 * 1024;

/** 先頭のバイト列から、実行可能・入れ子のファイルの種類を判定する(それ以外は null) */
export function classifyMagic(head) {
  if (head.length >= 4 && head.readUInt32BE(0) === 0x7f454c46) return "elf";
  if (head.length >= 4 && head.toString("latin1", 0, 3) === "dex" && head[3] === 0x0a) return "dex";
  if (head.length >= 4 && head[0] === 0x50 && head[1] === 0x4b && (head[2] === 0x03 || head[2] === 0x05) && (head[3] === 0x04 || head[3] === 0x06)) return "zip";
  if (head.length >= 2 && head[0] === 0x23 && head[1] === 0x21) return "script";
  // Javaのクラスファイル。Androidでは直接は実行できない(DEXに変換が必要)ので、記録するだけ
  if (head.length >= 4 && head.readUInt32BE(0) === 0xcafebabe) return "java-class";
  return null;
}

/** APKの中身(ZIP)の一覧を調べる */
export function inspectZip(apkPath) {
  const zip = openZip(apkPath);
  try {
    const anomalies = [...zip.anomalies];
    const dexFiles = [];
    const nativeLibs = [];
    const embedded = [];
    let total = 0;

    if (zip.entries.length > MAX_ENTRIES) anomalies.push(`ファイル数が多すぎます (${zip.entries.length})`);
    for (const e of zip.entries) {
      total += e.size;
      if (e.dir) continue;
      // 圧縮率が極端に高い大きなファイル(ZIP爆弾)
      if (e.size > 20 * 1024 * 1024 && e.compressedSize > 0 && e.size / e.compressedSize > 2000) {
        anomalies.push(`圧縮率が極端なファイルがあります: ${e.name.slice(0, 80)}`);
      }
      if (/^classes\d*\.dex$/.test(e.name)) {
        dexFiles.push({ name: e.name, size: e.size });
        continue;
      }
      const abi = e.name.match(/^lib\/([^/]+)\/[^/]+\.so$/);
      if (abi) {
        const data = e.size <= MAX_NATIVE_BYTES ? zip.read(e, MAX_NATIVE_BYTES) : null;
        nativeLibs.push({
          path: e.name,
          abi: abi[1],
          size: e.size,
          sha256: data ? createHash("sha256").update(data).digest("hex") : null,
        });
        continue;
      }
      if (e.name.startsWith("META-INF/") && /\.(version|kotlin_module|properties|SF|RSA|DSA|EC|MF)$/.test(e.name)) continue;
      const kind = classifyMagic(zip.head(e, 16));
      if (kind) embedded.push({ path: e.name, kind, size: e.size });
    }
    if (total > MAX_TOTAL_BYTES) anomalies.push("展開後の合計サイズが大きすぎます");
    return { entryCount: zip.entries.length, totalSize: total, anomalies, dexFiles, nativeLibs, embedded };
  } finally {
    zip.close();
  }
}

/** DEXファイルを一時ファイルに取り出す(dexdump は ZIP の中を直接読めない環境があるため) */
function extractDex(apkPath, names) {
  const zip = openZip(apkPath);
  const dir = mkdtempSync(join(tmpdir(), "apk-dex-"));
  try {
    const out = [];
    for (const name of names) {
      const entry = zip.entries.find((e) => e.name === name);
      const data = entry && zip.read(entry, MAX_DEX_BYTES);
      if (!data) throw new Error(`${name} を取り出せませんでした`);
      const file = join(dir, name);
      writeFileSync(file, data);
      out.push(file);
    }
    return { dir, files: out };
  } finally {
    zip.close();
  }
}

/**
 * @param {string} apkPath
 * @param {{ aapt2: string, dexdump: string | null }} tools
 */
export async function collectFacts(apkPath, tools) {
  const incomplete = [];
  let zipFacts = { entryCount: 0, totalSize: 0, anomalies: [], dexFiles: [], nativeLibs: [], embedded: [] };
  try {
    zipFacts = inspectZip(apkPath);
  } catch (e) {
    incomplete.push(`APKの中身を読み取れませんでした: ${e instanceof Error ? e.message : e}`);
  }

  let manifest = null;
  try {
    const { stdout } = await run(tools.aapt2, ["dump", "xmltree", "--file", "AndroidManifest.xml", apkPath], { maxBuffer: 50 * 1024 * 1024 });
    manifest = extractManifestFacts(parseXmlTree(stdout));
    if (!manifest) incomplete.push("AndroidManifest を解釈できませんでした");
  } catch (e) {
    incomplete.push(`AndroidManifest を読み取れませんでした: ${e instanceof Error ? e.message.slice(0, 200) : e}`);
  }

  let dex = null;
  if (zipFacts.dexFiles.length === 0) {
    // コードを持たないAPK(リソースだけ)はあり得るが、hasCode=false と宣言している場合に限って許す
    if (manifest && manifest.hasCode) incomplete.push("classes.dex が見つかりません");
  } else if (!tools.dexdump) {
    incomplete.push("dexdump が使えないため、コードを解析できませんでした");
  } else {
    let extracted;
    try {
      extracted = extractDex(apkPath, zipFacts.dexFiles.map((d) => d.name));
      const results = [];
      for (const file of extracted.files) results.push(await runDexdump(tools.dexdump, file));
      dex = mergeDexFacts(results);
    } catch (e) {
      incomplete.push(`コードの解析に失敗しました: ${e instanceof Error ? e.message.slice(0, 200) : e}`);
    } finally {
      if (extracted) {
        const { rm } = await import("node:fs/promises");
        await rm(extracted.dir, { recursive: true, force: true });
      }
    }
  }

  return {
    version: FACTS_VERSION,
    manifest,
    zip: { entryCount: zipFacts.entryCount, totalSize: zipFacts.totalSize, anomalies: zipFacts.anomalies.slice(0, 20) },
    dexFiles: zipFacts.dexFiles.map((d) => d.name),
    nativeLibs: zipFacts.nativeLibs.slice(0, 100),
    embedded: zipFacts.embedded.slice(0, 100),
    dex,
    incomplete,
  };
}
