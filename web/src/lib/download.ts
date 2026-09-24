import { createHash } from "node:crypto";
import { env } from "./env";
import { DOWNLOAD_URL_TTL_SECONDS, presignDownload } from "./r2";
import { serviceClient } from "./supabase";
import type { DownloadInfoDto } from "./dto";

export class DownloadError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

/** 端末IDはそのまま保存せず、ソルト付きハッシュにする(DL数の重複除外にだけ使う) */
export function hashDevice(deviceId: string): string {
  return createHash("sha256").update(`${env.deviceHashSalt()}:${deviceId}`).digest("hex");
}

interface AppInfo {
  package_name: string;
  status: string;
  admin_only: boolean;
}

interface ReleaseWithApp {
  id: string;
  apk_key: string;
  apk_size: number | null;
  sha256: string | null;
  signing_cert_sha256: string | null;
  version_code: number | null;
  status: string;
  app: AppInfo | AppInfo[];
}

/**
 * 公開中リリースのダウンロードURLを発行し、DL数を記録する。
 * 公開されていないリリースは(管理者であっても)ここからは取得できない。
 * 管理者専用アプリ(admin_only)は、呼び出し側が渡す isAdmin() が true のときだけ発行する
 * (存在を知らせないよう、権限が無いときは「見つからない」と同じ応答にする)。
 */
export async function issueDownload(
  releaseId: string,
  deviceId: string,
  userId: string | null,
  isAdmin: () => Promise<boolean> = async () => false,
): Promise<DownloadInfoDto> {
  const supabase = serviceClient();
  const { data, error } = await supabase
    .from("app_releases")
    .select("id, apk_key, apk_size, sha256, signing_cert_sha256, version_code, status, app:apps(package_name, status, admin_only)")
    .eq("id", releaseId)
    .maybeSingle();
  if (error) throw error;

  const release = data as unknown as ReleaseWithApp | null;
  const app = release && (Array.isArray(release.app) ? release.app[0] : release.app);
  if (!release || !app || release.status !== "published" || app.status !== "published") {
    throw new DownloadError("このアプリは現在ダウンロードできません", 404);
  }
  if (app.admin_only && !(await isAdmin())) {
    throw new DownloadError("このアプリは現在ダウンロードできません", 404);
  }
  if (!release.sha256 || !release.signing_cert_sha256 || release.version_code == null) {
    throw new DownloadError("このアプリは現在ダウンロードできません", 409);
  }

  const { error: recordError } = await supabase.rpc("record_download", {
    p_release_id: release.id,
    p_user_id: userId,
    p_device_hash: hashDevice(deviceId),
  });
  if (recordError) console.error("record_download failed", recordError); // DL数の失敗でダウンロード自体は止めない

  return {
    url: await presignDownload(release.apk_key),
    sha256: release.sha256,
    signingCertSha256: release.signing_cert_sha256,
    apkSize: release.apk_size,
    versionCode: release.version_code,
    packageName: app.package_name,
    expiresAt: new Date(Date.now() + DOWNLOAD_URL_TTL_SECONDS * 1000).toISOString(),
  };
}
