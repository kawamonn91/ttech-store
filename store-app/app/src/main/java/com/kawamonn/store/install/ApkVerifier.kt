package com.kawamonn.store.install

import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/** サーバーが「このリリースはこういうAPKのはず」と申告する内容 */
data class ExpectedApk(
    val packageName: String,
    val versionCode: Long,
    val sha256: String,
    val signingCertSha256: String,
)

/** ダウンロードしたAPKファイルから端末側で実際に読み取った内容 */
data class ActualApk(
    val sha256: String,
    val packageName: String?,
    val versionCode: Long?,
    val signerCertSha256: List<String>,
)

sealed interface VerifyResult {
    data object Ok : VerifyResult
    data class Failed(val reason: String) : VerifyResult
}

/**
 * ダウンロードしたAPKを、インストール前に検証する。
 *
 * 1. SHA-256 … 通信途中の破損・改ざん、配信元ストレージの差し替えを検知
 * 2. パッケージ名/versionCode … 別アプリや古い版へのすり替えを検知
 * 3. 署名証明書 … カタログに固定された署名鍵と一致しなければ拒否(なりすまし更新の防止)
 *
 * 3 は Android 自身も更新時に強制するが、新規インストール時は OS では防げないため、ここで必ず確認する。
 */
object ApkVerifier {

    /** 判定ロジック本体。Android API に依存しないので単体テストできる */
    fun check(expected: ExpectedApk, actual: ActualApk): VerifyResult {
        if (!expected.sha256.equals(actual.sha256, ignoreCase = true)) {
            return VerifyResult.Failed("ファイルが破損しているか、改ざんされている可能性があります(ハッシュ不一致)")
        }
        if (actual.packageName == null || actual.versionCode == null) {
            return VerifyResult.Failed("APKの情報を読み取れませんでした")
        }
        if (actual.packageName != expected.packageName) {
            return VerifyResult.Failed("アプリのパッケージ名が想定と異なります")
        }
        if (actual.versionCode != expected.versionCode) {
            return VerifyResult.Failed("アプリのバージョンが想定と異なります")
        }
        if (actual.signerCertSha256.size != 1) {
            return VerifyResult.Failed("署名情報を確認できませんでした")
        }
        if (!expected.signingCertSha256.equals(actual.signerCertSha256.single(), ignoreCase = true)) {
            return VerifyResult.Failed("署名が登録済みの開発者と一致しません。インストールを中止しました")
        }
        return VerifyResult.Ok
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** APKファイルから端末側で情報を読み取る。読めない(壊れている等)場合は sha256 のみ入った結果を返す */
    @Suppress("DEPRECATION")
    fun inspect(pm: PackageManager, file: File): ActualApk {
        val sha = sha256Hex(file)
        val flags = if (android.os.Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return ActualApk(sha, null, null, emptyList())
        val signers = if (android.os.Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        return ActualApk(
            sha256 = sha,
            packageName = info.packageName,
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            signerCertSha256 = signers.map { sha256Hex(it.toByteArray()) },
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
