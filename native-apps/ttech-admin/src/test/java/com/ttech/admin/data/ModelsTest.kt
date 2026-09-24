package com.ttech.admin.data

import com.ttech.admin.ui.sampleSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun `AuthTokensからセッションを作る(expires_atがあればそれを優先)`() {
        val t = AuthTokens("a", "r", expires_in = 3600, expires_at = 5_000, user = AuthUser("u1", "a@example.com"))
        val s = t.toSession(nowEpochSec = 100)
        assertEquals(5_000L, s.expiresAtEpochSec)
        assertEquals("u1", s.userId)
        assertEquals("a@example.com", s.email)
    }

    @Test
    fun `expires_atが無ければ今+expires_in`() {
        val s = AuthTokens("a", "r", expires_in = 3600, user = AuthUser("u1")).toSession(nowEpochSec = 100)
        assertEquals(3_700L, s.expiresAtEpochSec)
        assertNull(s.email)
    }

    @Test
    fun `userが無い応答でも、トークンのsubからユーザーIDを取る`() {
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"from-jwt"}""".toByteArray())
        val s = AuthTokens("h.$payload.s", "r").toSession(0)
        assertEquals("from-jwt", s.userId)
    }

    @Test
    fun `ユーザーIDが分からなければ例外`() {
        val e = runCatching { AuthTokens("bad", "r").toSession(0) }.exceptionOrNull()
        assertTrue(e is AuthApiException)
    }

    @Test
    fun `画像の縮小率は、長辺が上限以上に保たれる最大の2のべき乗`() {
        assertEquals(1, sampleSize(800, 600, 1280))
        assertEquals(1, sampleSize(1280, 720, 1280))
        assertEquals(2, sampleSize(3000, 2000, 1280))
        assertEquals(2, sampleSize(4032, 3024, 1280)) // 2016px(4なら1008pxで足りなくなる)
        assertEquals(4, sampleSize(10000, 200, 1280))
    }
}
