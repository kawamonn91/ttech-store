package com.kawamonn.store.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `バイト数の表示`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2 KB", formatBytes(2048))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("2.00 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `ダウンロード数の表示`() {
        assertEquals("999", formatCount(999))
        assertEquals("1,234", formatCount(1234))
        assertEquals("1.2万", formatCount(12_000))
        assertEquals("3万", formatCount(30_000))
    }

    @Test
    fun `権限名の短縮`() {
        assertEquals("CAMERA", shortPermission("android.permission.CAMERA"))
        assertEquals("com.x.PERM", shortPermission("com.x.PERM"))
    }

    @Test
    fun `インターネット権限の有無`() {
        assertEquals(true, usesInternet(listOf("android.permission.CAMERA", "android.permission.INTERNET")))
        assertEquals(false, usesInternet(listOf("android.permission.CAMERA")))
        assertEquals(false, usesInternet(emptyList()))
        // 自分のアプリ専用の権限や、名前が似ているだけのものは数えない
        assertEquals(false, usesInternet(listOf("com.example.INTERNET", "android.permission.ACCESS_NETWORK_STATE")))
    }

    @Test
    fun `利用者に見せる権限は、アプリ内部だけの権限を除く`() {
        val all = listOf("android.permission.CAMERA", "com.ttech.tripshiori.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", "com.google.android.c2dm.permission.RECEIVE")
        assertEquals(
            listOf("android.permission.CAMERA", "com.google.android.c2dm.permission.RECEIVE"),
            visiblePermissions("com.ttech.tripshiori", all),
        )
        assertEquals(emptyList<String>(), visiblePermissions("com.ttech.tripshiori", listOf("com.ttech.tripshiori.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")))
        // パッケージ名が前方一致するだけの別の名前(com.ttech.tripshiori2)は除かない
        assertEquals(listOf("com.ttech.tripshiori2.X"), visiblePermissions("com.ttech.tripshiori", listOf("com.ttech.tripshiori2.X")))
    }
}
