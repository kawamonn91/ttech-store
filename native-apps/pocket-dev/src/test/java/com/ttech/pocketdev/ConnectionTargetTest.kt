package com.ttech.pocketdev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun `pocketLinuxヘルパーでローカルURLと既定値が生成される`() {
        val target = ConnectionTarget.pocketLinux("secret-token")
        assertEquals("http://127.0.0.1:8765", target.baseUrl)
        assertEquals("secret-token", target.token)
        assertEquals(TargetType.POCKET_LINUX, target.type)
        assertEquals("Pocket Linux", target.displayName)
    }

    @Test
    fun `pocketLinuxヘルパーでポート指定が反映される`() {
        val target = ConnectionTarget.pocketLinux("secret-token", port = 9000)
        assertEquals("http://127.0.0.1:9000", target.baseUrl)
    }

    @Test
    fun `JSONシリアライズとデシリアライズで正しく復元できる`() {
        val target = ConnectionTarget(
            baseUrl = "http://192.168.1.50:8765",
            token = "my-token-123",
            name = "自宅サーバー",
            type = TargetType.CUSTOM_SERVER,
        )

        val json = target.toJson()
        val restored = ConnectionTarget.fromJson(json)

        assertNotNull(restored)
        assertEquals(target.baseUrl, restored?.baseUrl)
        assertEquals(target.token, restored?.token)
        assertEquals(target.name, restored?.name)
        assertEquals(target.type, restored?.type)
        assertEquals(target.id, restored?.id)
        assertEquals("自宅サーバー", restored?.displayName)
    }

    @Test
    fun `複数ターゲットのリストをJSONに保存復元できる`() {
        val list = listOf(
            ConnectionTarget.pocketLinux("token-a"),
            ConnectionTarget("http://server.local:8765", "token-b", name = "PC"),
        )

        val json = ConnectionTarget.listToJson(list)
        val restored = ConnectionTarget.listFromJson(json)

        assertEquals(2, restored.size)
        assertEquals("http://127.0.0.1:8765", restored[0].baseUrl)
        assertEquals(TargetType.POCKET_LINUX, restored[0].type)
        assertEquals("http://server.local:8765", restored[1].baseUrl)
        assertEquals(TargetType.CUSTOM_SERVER, restored[1].type)
    }

    @Test
    fun `不正なJSONのfromJsonはnullを返す`() {
        assertNull(ConnectionTarget.fromJson("{invalid-json"))
        assertEquals(emptyList<ConnectionTarget>(), ConnectionTarget.listFromJson("{invalid"))
    }
}
