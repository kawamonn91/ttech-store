package com.ttech.bikenavi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BRouterParserTest {
    @Test
    fun `実際の応答から、ルート・標高・曲がり角を取り出せる`() {
        val route = Fixtures.shortRoute()
        assertEquals(3308.0, route.distanceM, 50.0)
        assertEquals(533.0, route.durationS, 1.0)
        assertTrue(route.line.points.size > 50)
        assertTrue("曲がり角が1つも無い", route.maneuvers.isNotEmpty())
        assertEquals(ManeuverKind.Arrive, route.maneuvers.last().kind)
    }

    @Test
    fun `標高は出発地からの道のり順に並び、登り・下りが非負で求まる`() {
        val route = Fixtures.shortRoute()
        assertTrue(route.elevation.size > 50)
        val ats = route.elevation.map { it.atM }
        assertEquals(ats.sorted(), ats)
        assertTrue(route.ascendM >= 0.0)
        assertTrue(route.descendM >= 0.0)
    }

    @Test
    fun `曲がり角の道のりは、近い順に並び、最後は目的地`() {
        val route = Fixtures.shortRoute()
        val at = route.maneuvers.map { it.atM }
        assertEquals(at.sorted(), at)
        assertEquals(route.distanceM, at.last(), 0.001)
    }

    @Test
    fun `ルートが無い応答(プレーンテキストのエラー)は、分かりやすい文言の例外にする`() {
        try {
            BRouterParser.parse("from-position not mapped in existing datafile")
            fail()
        } catch (e: BikeException) {
            assertTrue(e.message!!.contains("見つかりませんでした"))
        }
        try {
            BRouterParser.parse("これはJSONではない")
            fail()
        } catch (e: BikeException) {
            // 短い本文はそのまま、長い本文は「読み取れませんでした」になる
            assertTrue(e.message!!.isNotBlank())
        }
    }
}
