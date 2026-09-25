// APK(ZIP)を読むための、依存なしの最小限のリーダー。
// 検査ワーカー(GitHub Actions / ローカル)で、APKの中身の一覧・先頭の数バイト(種類の判定)・
// 特定のファイル(classes.dex など)を取り出すために使う。信用できない入力(第三者のAPK)を読むので、
// 展開するサイズには必ず上限を付け、不正な構造(範囲外の位置・同名の重複・暗号化)は例外にせず「異常」として返す。

import { closeSync, fstatSync, openSync, readSync } from "node:fs";
import { constants, inflateRawSync } from "node:zlib";

const EOCD_SIG = 0x06054b50;
const CEN_SIG = 0x02014b50;
const LOC_SIG = 0x04034b50;

/** 展開後がこれを超えるファイルは、内容を読まない(ZIP爆弾対策) */
export const MAX_ENTRY_BYTES = 200 * 1024 * 1024;

function readAt(fd, position, length) {
  const buf = Buffer.alloc(length);
  let done = 0;
  while (done < length) {
    const n = readSync(fd, buf, done, length - done, position + done);
    if (n === 0) break;
    done += n;
  }
  return done === length ? buf : buf.subarray(0, done);
}

/**
 * ZIP を開く。戻り値:
 *   entries: [{ name, method, compressedSize, size, offset, encrypted }]
 *   anomalies: 構造上の異常(文字列の配列)。空なら問題なし
 *   head(entry, n): 展開後の先頭 n バイト(種類の判定用)
 *   read(entry, maxBytes): 展開後の全体(上限を超える・壊れているときは null)
 *   close()
 */
export function openZip(file) {
  const fd = openSync(file, "r");
  const size = fstatSync(fd).size;
  const anomalies = [];
  const close = () => closeSync(fd);

  // 末尾の End of Central Directory(コメント最大 64KB の手前まで探す)
  const tailLen = Math.min(size, 22 + 0xffff);
  const tail = readAt(fd, size - tailLen, tailLen);
  let eocd = -1;
  for (let i = tail.length - 22; i >= 0; i--) {
    if (tail.readUInt32LE(i) === EOCD_SIG) {
      eocd = i;
      break;
    }
  }
  if (eocd < 0) {
    close();
    throw new Error("ZIP(APK)として読み取れません");
  }
  const total = tail.readUInt16LE(eocd + 10);
  const cdSize = tail.readUInt32LE(eocd + 12);
  const cdOffset = tail.readUInt32LE(eocd + 16);
  if (total === 0xffff || cdSize === 0xffffffff || cdOffset === 0xffffffff) {
    close();
    throw new Error("ZIP64 形式のAPKには対応していません");
  }
  if (cdOffset + cdSize > size) {
    close();
    throw new Error("ZIPの構造が不正です(中央ディレクトリが範囲外)");
  }

  const cd = readAt(fd, cdOffset, cdSize);
  const entries = [];
  const seen = new Set();
  let pos = 0;
  for (let i = 0; i < total; i++) {
    if (pos + 46 > cd.length || cd.readUInt32LE(pos) !== CEN_SIG) {
      anomalies.push("中央ディレクトリの項目が壊れています");
      break;
    }
    const flags = cd.readUInt16LE(pos + 8);
    const method = cd.readUInt16LE(pos + 10);
    const compressedSize = cd.readUInt32LE(pos + 20);
    const uncompressedSize = cd.readUInt32LE(pos + 24);
    const nameLen = cd.readUInt16LE(pos + 28);
    const extraLen = cd.readUInt16LE(pos + 30);
    const commentLen = cd.readUInt16LE(pos + 32);
    const offset = cd.readUInt32LE(pos + 42);
    const name = cd.toString("utf8", pos + 46, pos + 46 + nameLen);
    pos += 46 + nameLen + extraLen + commentLen;

    if (seen.has(name)) anomalies.push(`同じ名前のファイルが複数あります: ${name.slice(0, 80)}`);
    seen.add(name);
    if (name.split("/").includes("..") || name.startsWith("/") || name.includes("\\")) {
      anomalies.push(`不正なパスのファイルがあります: ${name.slice(0, 80)}`);
    }
    if (flags & 1) anomalies.push(`暗号化されたファイルがあります: ${name.slice(0, 80)}`);
    if (method !== 0 && method !== 8) anomalies.push(`未対応の圧縮方式のファイルがあります: ${name.slice(0, 80)}`);
    if (offset + 30 > size) anomalies.push(`ファイルの位置が範囲外です: ${name.slice(0, 80)}`);
    entries.push({ name, method, compressedSize, size: uncompressedSize, offset, encrypted: !!(flags & 1), dir: name.endsWith("/") });
  }
  if (entries.length !== total) anomalies.push("項目数が中央ディレクトリの記載と合いません");

  /** 圧縮されたデータの位置(ローカルヘッダーの名前・追加情報の長さの分だけ後ろ) */
  function dataStart(entry) {
    const h = readAt(fd, entry.offset, 30);
    if (h.length < 30 || h.readUInt32LE(0) !== LOC_SIG) return -1;
    return entry.offset + 30 + h.readUInt16LE(26) + h.readUInt16LE(28);
  }

  function head(entry, n = 16) {
    if (entry.dir || entry.encrypted || entry.size === 0) return Buffer.alloc(0);
    const start = dataStart(entry);
    if (start < 0 || start > size) return Buffer.alloc(0);
    if (entry.method === 0) return readAt(fd, start, Math.min(n, entry.compressedSize));
    try {
      // 先頭の一部だけを読んで、途中まで展開する(Z_SYNC_FLUSH なので、途切れていても出力できた分は返る)
      const raw = readAt(fd, start, Math.min(entry.compressedSize, 4096));
      return inflateRawSync(raw, { finishFlush: constants.Z_SYNC_FLUSH }).subarray(0, n);
    } catch {
      return Buffer.alloc(0);
    }
  }

  function read(entry, maxBytes = MAX_ENTRY_BYTES) {
    if (entry.dir || entry.encrypted || entry.size > maxBytes || entry.compressedSize > maxBytes) return null;
    const start = dataStart(entry);
    if (start < 0 || start + entry.compressedSize > size) return null;
    const raw = readAt(fd, start, entry.compressedSize);
    try {
      const out = entry.method === 0 ? raw : inflateRawSync(raw, { maxOutputLength: maxBytes });
      // 宣言したサイズと違うAPKは、構造が偽られている
      return out.length === entry.size ? out : null;
    } catch {
      return null;
    }
  }

  return { entries, anomalies, head, read, close };
}
