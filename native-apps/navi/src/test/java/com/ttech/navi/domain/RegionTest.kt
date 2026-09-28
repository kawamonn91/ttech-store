package com.ttech.navi.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionTest {
    private val table = MuniTable.parse(
        """
        07202,福島県,会津若松市
        07201,福島県,福島市
        04101,宮城県,仙台市　青葉区
        04100,宮城県,仙台市
        04202,宮城県,白石市
        13101,東京都,千代田区
        """.trimIndent(),
    )

    @Test
    fun `市区町村コードから、県と市を求める。政令指定都市の区は市にまとめる`() {
        assertEquals(Region(7, "福島県", "会津若松市"), table.regionOf(7202))
        assertEquals(Region(4, "宮城県", "仙台市"), table.regionOf(4101))
        assertEquals(Region(13, "東京都", "千代田区"), table.regionOf(13101))
        // 表に無いコードでも、県は分かる
        assertEquals(Region(7, "福島県", null), table.regionOf(7999))
    }

    @Test
    fun `実際の市区町村表(アプリに入れるもの)を読める`() {
        val real = MuniTable.parse(File("src/main/assets/muni.csv").readText())
        assertTrue("件数 ${real.size}", real.size > 1800)
        assertEquals(Region(7, "福島県", "会津若松市"), real.regionOf(7202))
        assertEquals(Region(4, "宮城県", "仙台市"), real.regionOf(4101))
        assertEquals(Region(1, "北海道", "札幌市"), real.regionOf(1101))
        assertEquals("猪苗代町", real.regionOf(7408).city)
    }

    @Test
    fun `最初に分かった地域では何も言わず、同じ地域の間は何も言わない`() {
        val t = RegionTracker()
        assertNull(t.onRegion(table.regionOf(7202)))
        assertNull(t.onRegion(table.regionOf(7202)))
        assertNull(t.onRegion(table.regionOf(7202)))
    }

    @Test
    fun `別の市に入ったら、続けて2回見えたところで知らせる`() {
        val t = RegionTracker()
        t.onRegion(table.regionOf(7202))
        assertNull(t.onRegion(table.regionOf(7201))) // 1回目は確かめ中
        assertTrue(t.checking)
        assertEquals("福島市に入りました。", t.onRegion(table.regionOf(7201)))
        assertNull(t.onRegion(table.regionOf(7201))) // 同じ地域では、もう言わない
    }

    @Test
    fun `別の県に入ったら、県と市を知らせる`() {
        val t = RegionTracker()
        t.onRegion(table.regionOf(7201))
        t.onRegion(table.regionOf(4202))
        assertEquals("宮城県、白石市に入りました。", t.onRegion(table.regionOf(4202)))
    }

    @Test
    fun `境界の近くで行ったり来たりしても、何度も言わない`() {
        val t = RegionTracker()
        t.onRegion(table.regionOf(7202))
        val spoken = listOf(7201, 7202, 7201, 7202, 7201, 7202).mapNotNull { t.onRegion(table.regionOf(it)) }
        assertTrue(spoken.isEmpty())
    }
}
