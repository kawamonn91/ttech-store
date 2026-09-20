package com.kawamonn.store.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkVerifierTest {
    private val sha = "a".repeat(64)
    private val cert = "b".repeat(64)
    private val expected = ExpectedApk("com.example.app", 5, sha, cert)

    private fun actual(
        sha256: String = sha,
        packageName: String? = "com.example.app",
        versionCode: Long? = 5,
        signers: List<String> = listOf(cert),
    ) = ActualApk(sha256, packageName, versionCode, signers)

    private fun assertFailed(result: VerifyResult, contains: String) {
        assertTrue("expected Failed but was $result", result is VerifyResult.Failed)
        assertTrue((result as VerifyResult.Failed).reason, result.reason.contains(contains))
    }

    @Test
    fun `すべて一致すれば OK`() {
        assertEquals(VerifyResult.Ok, ApkVerifier.check(expected, actual()))
    }

    @Test
    fun `ハッシュの大文字小文字は区別しない`() {
        assertEquals(VerifyResult.Ok, ApkVerifier.check(expected, actual(sha256 = sha.uppercase())))
    }

    @Test
    fun `1バイトでも改変されていればハッシュ不一致`() {
        assertFailed(ApkVerifier.check(expected, actual(sha256 = "a".repeat(63) + "c")), "ハッシュ")
    }

    @Test
    fun `別の署名鍵なら拒否`() {
        assertFailed(ApkVerifier.check(expected, actual(signers = listOf("c".repeat(64)))), "署名")
    }

    @Test
    fun `署名が複数あるAPKは拒否`() {
        assertFailed(ApkVerifier.check(expected, actual(signers = listOf(cert, cert))), "署名")
    }

    @Test
    fun `署名が読めなければ拒否`() {
        assertFailed(ApkVerifier.check(expected, actual(signers = emptyList())), "署名")
    }

    @Test
    fun `パッケージ名が違えば拒否`() {
        assertFailed(ApkVerifier.check(expected, actual(packageName = "com.evil.app")), "パッケージ名")
    }

    @Test
    fun `バージョンが違えば拒否`() {
        assertFailed(ApkVerifier.check(expected, actual(versionCode = 4)), "バージョン")
    }

    @Test
    fun `APKを読み取れなければ拒否`() {
        assertFailed(ApkVerifier.check(expected, actual(packageName = null, versionCode = null)), "読み取れません")
    }

    @Test
    fun `sha256Hex は既知の値と一致する`() {
        // SHA-256("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ApkVerifier.sha256Hex("abc".toByteArray()),
        )
    }
}
