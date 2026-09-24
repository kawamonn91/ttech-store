package com.ttech.childgrowth.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthEntryTest {
    private fun entry(id: String, date: String, h: Double = 90.0, w: Double = 12.0, recordedAt: Long = 0L) =
        GrowthEntry(id, date, h, w, recordedAt)

    @Test
    fun `記録一覧は日付の新しい順に並ぶ`() {
        val entries = listOf(entry("1", "2024-01-01"), entry("3", "2024-03-01"), entry("2", "2024-02-01"))
        assertEquals(listOf("3", "2", "1"), entries.sortedByDateDescending().map { it.id })
    }

    @Test
    fun `グラフ用は日付の古い順に並ぶ`() {
        val entries = listOf(entry("1", "2024-01-01"), entry("3", "2024-03-01"), entry("2", "2024-02-01"))
        assertEquals(listOf("1", "2", "3"), entries.sortedByDateAscending().map { it.id })
    }

    @Test
    fun `同じ日に複数回記録しても記録した時刻順に並ぶ`() {
        // 日付だけでは順序が一意に決まらず、グラフの並びが不安定になる不具合があったため、
        // 同日タイブレークとして recordedAt を使うことを確認する。
        val entries = listOf(
            entry("後から追加", "2024-05-01", recordedAt = 2000L),
            entry("先に追加", "2024-05-01", recordedAt = 1000L),
        )
        assertEquals(listOf("先に追加", "後から追加"), entries.sortedByDateAscending().map { it.id })
        assertEquals(listOf("後から追加", "先に追加"), entries.sortedByDateDescending().map { it.id })
    }

    @Test
    fun `点が1つ以下ではグラフを描かない`() {
        assertTrue(chartPoints(emptyList()).isEmpty())
        assertTrue(chartPoints(listOf(10.0)).isEmpty())
    }

    @Test
    fun `2点あれば両端に座標を返す`() {
        val points = chartPoints(listOf(10.0, 20.0), width = 100f, height = 80f)
        assertEquals(2, points.size)
        assertEquals(0f, points[0].x)
        assertEquals(100f, points[1].x)
    }

    @Test
    fun `値が大きいほどyは小さくなる_グラフ座標は上が0のため`() {
        val points = chartPoints(listOf(10.0, 20.0, 15.0), height = 80f)
        // 最大値(20)のy座標が最も小さく、最小値(10)のy座標が最も大きい
        assertTrue(points[1].y < points[0].y)
        assertTrue(points[1].y < points[2].y)
    }

    @Test
    fun `全て同じ値でも0除算にならない`() {
        val points = chartPoints(listOf(10.0, 10.0, 10.0))
        assertEquals(3, points.size)
        points.forEach { assertTrue(it.y.isFinite()) }
    }
}
