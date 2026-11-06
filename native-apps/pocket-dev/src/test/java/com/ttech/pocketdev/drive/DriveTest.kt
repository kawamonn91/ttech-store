package com.ttech.pocketdev.drive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLDecoder

class DriveTest {

    // ------------------------------------------------------------ Rclone

    private val client = DriveClient("id-1.apps.googleusercontent.com", "secret-1")

    @Test
    fun `DriveClient_IDと秘密鍵がそろって初めて設定済みになる`() {
        assertTrue(client.configured)
        assertFalse(DriveClient("", "").configured)
        assertFalse(DriveClient("id", "").configured)
        assertFalse(DriveClient("", "secret").configured)
    }

    @Test
    fun `DriveClient_文字列にしても秘密鍵が出ない`() {
        assertFalse(client.toString().contains("secret-1"))
    }

    @Test
    fun `authorizeArgs_自前のクライアントでログインする`() {
        assertEquals(
            listOf("authorize", "drive", "id-1.apps.googleusercontent.com", "secret-1", "--auth-no-open-browser"),
            Rclone.authorizeArgs(client),
        )
    }

    @Test
    fun `configText_クライアントとトークンを書く`() {
        assertEquals(
            "[gdrive]\ntype = drive\nscope = drive\n" +
                "client_id = id-1.apps.googleusercontent.com\nclient_secret = secret-1\ntoken = {\"access_token\":\"x\"}\n",
            Rclone.configText(client, """{"access_token":"x"}"""),
        )
    }

    @Test
    fun `isLoggedInWith_今のクライアントでログインした情報だけを有効にする`() {
        val config = Rclone.configText(client, """{"access_token":"x"}""").lines()
        assertTrue(Rclone.isLoggedInWith(config, client))
        // rclone 共有のクライアントで取ったトークン
        assertFalse(Rclone.isLoggedInWith(listOf("[gdrive]", "type = drive", "scope = drive", "token = {}"), client))
        assertFalse(Rclone.isLoggedInWith(config, DriveClient("id-2", "secret-2")))
        assertFalse(Rclone.isLoggedInWith(emptyList(), client))
    }

    @Test
    fun `loginError_クライアントが使えなくなったときは更新を案内する`() {
        assertTrue(Rclone.loginError("oauth2: cannot fetch token: 401\nResponse: {\"error\": \"invalid_client\"}")!!.contains("更新"))
        assertTrue(Rclone.loginError("Error 401: deleted_client")!!.contains("更新"))
    }

    @Test
    fun `parseToken_貼り付け用のトークンを取り出す`() {
        val output = """
            NOTICE: Please go to the following link: http://127.0.0.1:53682/auth?state=abc
            NOTICE: Got code
            Paste the following into your remote machine --->
            {"access_token":"ya29.x","token_type":"Bearer","refresh_token":"1//y","expiry":"2026-11-05T12:00:00Z"}
            <---End paste
        """.trimIndent()
        assertEquals(
            """{"access_token":"ya29.x","token_type":"Bearer","refresh_token":"1//y","expiry":"2026-11-05T12:00:00Z"}""",
            Rclone.parseToken(output),
        )
    }

    @Test
    fun `parseToken_失敗したときはnull`() {
        assertNull(Rclone.parseToken("ERROR : Failed to get token: oauth2: cannot fetch token"))
        assertNull(Rclone.parseToken("--->\nnot json\n<---End paste"))
        assertNull(Rclone.parseToken("--->\n{\"foo\":1}\n<---End paste"))
    }

    @Test
    fun `loginError_ほかのアプリがログイン中のときは理由を出す`() {
        val message = Rclone.loginError("Failed to start auth webserver: listen tcp 127.0.0.1:53682: bind: address already in use")
        assertTrue(message!!.contains("Pocket Linux"))
        assertNull(Rclone.loginError("something else"))
    }

    // ------------------------------------------------------- RcloneDaemon

    @Test
    fun `percentEncode_英数字と一部の記号以外を符号にしURLDecoderで戻せる`() {
        val name = "メモ 1+2=[3]&'*.py"
        val encoded = RcloneDaemon.percentEncode(name)
        assertTrue(encoded, encoded.all { it.isLetterOrDigit() || it in "-._~%" })
        assertTrue(encoded.contains("%20"))
        assertEquals(name, URLDecoder.decode(encoded, "UTF-8"))
        assertEquals("%5Bgdrive%3A%5D", RcloneDaemon.percentEncode("[gdrive:]"))
    }

    @Test
    fun `readLimited_上限までは読み上限を超えたら止める`() {
        val data = ByteArray(1000) { it.toByte() }
        assertArrayEquals(data, RcloneDaemon.readLimited(ByteArrayInputStream(data), 1000))
        val error = runCatching { RcloneDaemon.readLimited(ByteArrayInputStream(data), 999) }.exceptionOrNull()
        assertTrue(error is TooLargeException)
    }

    // ------------------------------------------------------- DriveBackend

    @Test
    fun `パスのつなぎと分け`() {
        assertEquals("a/b", DriveBackend.join("a", "b"))
        assertEquals("b", DriveBackend.join("", "b"))
        assertEquals("a", DriveBackend.join("a", ""))
        assertEquals("a/b", DriveBackend.parentOf("a/b/c.txt"))
        assertEquals("", DriveBackend.parentOf("c.txt"))
        assertEquals("c.txt", DriveBackend.nameOf("a/b/c.txt"))
    }

    @Test
    fun `ModTimeをUNIX秒にする`() {
        assertEquals(1_793_880_600L, DriveBackend.parseModTime("2026-11-05T12:10:00.123Z"))
        assertEquals(1_793_880_600L, DriveBackend.parseModTime("2026-11-05T21:10:00+09:00"))
        assertEquals(0L, DriveBackend.parseModTime(null))
        assertEquals(0L, DriveBackend.parseModTime("broken"))
    }

    @Test
    fun `rcloneの英語のエラーを日本語の文にする`() {
        fun message(raw: String) = DriveBackend.friendlyMessage(RcException(500, raw))
        assertTrue(message("couldn't fetch token: oauth2: \"invalid_grant\"").contains("ログインし直して"))
        assertTrue(message("failed to find object: object not found").contains("ファイルが見つかりません"))
        assertTrue(message("directory not found").contains("フォルダが見つかりません"))
        assertTrue(message("googleapi: Error 403: User Rate Limit Exceeded, userRateLimitExceeded").contains("上限"))
        assertTrue(message("proxyconnect tcp: Bad Gateway").contains("ネットワーク"))
        assertTrue(DriveBackend.friendlyMessage(IOException("weird")).endsWith("weird"))
    }
}
