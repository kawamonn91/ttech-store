package com.ttech.pocketdev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTargetTest {

    @Test
    fun `ホストだけならhttpと既定ポートを補う`() {
        assertEquals("http://192.168.1.10:8765", ConnectionTarget.normalizeBaseUrl("192.168.1.10"))
    }

    @Test
    fun `ポート指定はそのまま使う`() {
        assertEquals("http://192.168.1.10:9000", ConnectionTarget.normalizeBaseUrl("192.168.1.10:9000"))
    }

    @Test
    fun `末尾のスラッシュと前後の空白を落とす`() {
        assertEquals("http://pc.local:8765", ConnectionTarget.normalizeBaseUrl("  http://pc.local:8765/  "))
    }

    @Test
    fun `httpsは443を既定にする`() {
        assertEquals("https://example.com:443", ConnectionTarget.normalizeBaseUrl("https://example.com"))
    }

    @Test
    fun `パスが付いていても取り除く`() {
        assertEquals("http://pc.local:8765", ConnectionTarget.normalizeBaseUrl("http://pc.local:8765/ide.html"))
    }

    @Test
    fun `空や不正な入力はnull`() {
        assertNull(ConnectionTarget.normalizeBaseUrl(""))
        assertNull(ConnectionTarget.normalizeBaseUrl("   "))
        assertNull(ConnectionTarget.normalizeBaseUrl("http://"))
        assertNull(ConnectionTarget.normalizeBaseUrl("ftp://192.168.1.10"))
        assertNull(ConnectionTarget.normalizeBaseUrl("192.168.1.10:99999"))
    }

    @Test
    fun `スキームだけの入力はnull`() {
        assertNull(ConnectionTarget.normalizeBaseUrl("http://"))
        assertNull(ConnectionTarget.normalizeBaseUrl("https://"))
        assertNull(ConnectionTarget.normalizeBaseUrl("http:///"))
    }

    @Test
    fun `ホスト名は小文字に揃える`() {
        assertEquals("http://pc.local:8765", ConnectionTarget.normalizeBaseUrl("http://PC.Local:8765"))
    }

    @Test
    fun `画面のURLにトークンを載せる`() {
        val target = ConnectionTarget("http://192.168.1.10:8765", "abc-123_XY")

        assertEquals("http://192.168.1.10:8765/?token=abc-123_XY", target.terminalUrl)
        assertEquals("http://192.168.1.10:8765/ide.html?token=abc-123_XY", target.ideUrl)
    }

    @Test
    fun `トークンに記号が含まれてもURLエンコードする`() {
        val target = ConnectionTarget("http://pc.local:8765", "a+b/c=d")
        assertTrue(target.terminalUrl.endsWith("/?token=a%2Bb%2Fc%3Dd"))
    }

    @Test
    fun `自分のホストかどうかを判定する`() {
        val target = ConnectionTarget("http://192.168.1.10:8765", "t")

        assertTrue(target.isOwnHost("http://192.168.1.10:8765/ide.html"))
        assertTrue(target.isOwnHost("http://192.168.1.10:8765/api/fs/list?path="))
        assertFalse(target.isOwnHost("http://192.168.1.11:8765/"))
        assertFalse(target.isOwnHost("http://192.168.1.10:9000/"))
        assertFalse(target.isOwnHost("https://192.168.1.10:8765/"))
        assertFalse(target.isOwnHost("https://example.com/"))
        assertFalse(target.isOwnHost("not a url"))
    }

    @Test
    fun `既定ポートが省略されたURLも自分のホストとみなす`() {
        val target = ConnectionTarget("https://example.com:443", "t")
        assertTrue(target.isOwnHost("https://example.com/ide.html"))
    }
}
