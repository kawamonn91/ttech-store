import sharp, { type Metadata } from "sharp";
import { DEVELOPER_LIMITS } from "./developer";

export type ImageKind = "icon" | "screenshot";

export class ImageError extends Error {}

/**
 * 開発者がアップロードした画像を検査し、安全な形式(PNG)に作り直す。
 * 元のファイルをそのまま保存しないのは、画像に見せかけた別の形式のファイルや、
 * メタデータ(位置情報など)を含んだファイルを配信しないため。
 *   - アイコン: 512×512 の正方形に整える(縦横が大きく違うものは不可)
 *   - スクリーンショット: 長辺 1600px までに縮小(拡大はしない)
 */
export async function processImage(input: Buffer, kind: ImageKind): Promise<Buffer> {
  if (input.length === 0) throw new ImageError("画像が空です");
  if (input.length > DEVELOPER_LIMITS.maxImageBytes) {
    throw new ImageError(`画像は ${DEVELOPER_LIMITS.maxImageBytes / 1024 / 1024}MB 以下にしてください`);
  }

  let meta: Metadata;
  try {
    meta = await sharp(input, { limitInputPixels: 40_000_000, failOn: "error" }).metadata();
  } catch {
    throw new ImageError("画像として読み取れません。PNG・JPEG・WebP の画像を選んでください");
  }
  if (!meta.format || !["png", "jpeg", "webp"].includes(meta.format)) {
    throw new ImageError("PNG・JPEG・WebP の画像を選んでください");
  }
  const { width = 0, height = 0 } = meta;
  if (Math.min(width, height) < 96) throw new ImageError("画像が小さすぎます(短い辺が96px以上必要です)");

  const image = sharp(input, { limitInputPixels: 40_000_000, failOn: "error" }).rotate(); // 向きの情報を反映し、メタデータは出力しない
  if (kind === "icon") {
    const ratio = Math.max(width, height) / Math.min(width, height);
    if (ratio > 1.15) throw new ImageError("アイコンは、正方形に近い画像にしてください");
    return image.resize(512, 512, { fit: "cover" }).png({ compressionLevel: 9 }).toBuffer();
  }
  return image.resize(1600, 1600, { fit: "inside", withoutEnlargement: true }).png({ compressionLevel: 9 }).toBuffer();
}
