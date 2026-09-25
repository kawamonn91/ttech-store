package com.ttech.track.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceLabelTest {
    @Test
    fun `国名と郵便番号を省く`() {
        assertEquals("福島県会津若松市追手町2-41", PlaceLabel.shorten("日本、〒965-0000 福島県会津若松市追手町2-41"))
        assertEquals("東京都千代田区丸の内1丁目", PlaceLabel.shorten("日本、〒100-0005 東京都千代田区丸の内1丁目"))
        assertEquals("福島県会津若松市", PlaceLabel.shorten("福島県会津若松市"))
    }

    @Test
    fun `空なら null`() {
        assertNull(PlaceLabel.shorten(null))
        assertNull(PlaceLabel.shorten("  "))
        assertNull(PlaceLabel.shorten("日本、〒965-0000"))
    }
}
