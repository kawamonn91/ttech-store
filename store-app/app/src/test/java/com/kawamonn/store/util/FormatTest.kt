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
}
