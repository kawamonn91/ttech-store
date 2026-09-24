package com.kawamonn.store.auth

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNonceTest {
    @Test
    fun `SHA-256は16進の小文字で返す(標準のテストベクトル)`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", GoogleNonce.sha256Hex("abc"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", GoogleNonce.sha256Hex(""))
    }

    @Test
    fun `元のnonceは32桁の16進で、毎回違う`() {
        val a = GoogleNonce.newRaw()
        val b = GoogleNonce.newRaw()
        assertTrue(a.matches(Regex("[0-9a-f]{32}")))
        assertNotEquals(a, b)
    }

    @Test
    fun `Googleに渡す値は元の値のハッシュで、元の値そのものではない`() {
        val raw = GoogleNonce.newRaw(SecureRandom())
        val hashed = GoogleNonce.sha256Hex(raw)
        assertNotEquals(raw, hashed) // 同じ値を渡すと Nonces mismatch になる
        assertEquals(64, hashed.length)
        assertEquals(hashed, GoogleNonce.sha256Hex(raw)) // Supabaseが同じ計算をして突き合わせる
    }
}
