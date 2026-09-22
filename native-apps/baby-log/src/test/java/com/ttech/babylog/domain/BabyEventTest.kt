package com.ttech.babylog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset

class BabyEventTest {

    @Test
    fun `経過分数は記録時刻と現在時刻の差になる`() {
        val at = 0L
        val now = 5 * 60_000L
        assertEquals(5L, minutesAgo(at, now))
    }

    @Test
    fun `1分未満は0分と表示される`() {
        assertEquals(0L, minutesAgo(atMillis = 0L, nowMillis = 59_000L))
    }

    @Test
    fun `記録時刻が現在時刻より後でも負の値にはならない`() {
        assertEquals(0L, minutesAgo(atMillis = 10_000L, nowMillis = 0L))
    }

    @Test
    fun `時刻表示はM月d日 HH_mm形式になる`() {
        // 2024-03-05 09:07:00 UTC
        val at = java.time.ZonedDateTime.of(2024, 3, 5, 9, 7, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("3/5 09:07", formatEventTime(at, ZoneId.of("UTC")))
    }

    @Test
    fun `分は0埋めされる`() {
        val at = java.time.ZonedDateTime.of(2024, 12, 31, 23, 5, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("12/31 23:05", formatEventTime(at, ZoneId.of("UTC")))
    }

    @Test
    fun `種別ごとの直近の記録を先頭優先で取得する`() {
        val events = listOf(
            BabyEvent("3", EventType.DIAPER, at = 300),
            BabyEvent("2", EventType.FEED, at = 200),
            BabyEvent("1", EventType.FEED, at = 100),
        )
        assertEquals("2", events.lastOf(EventType.FEED)?.id)
        assertEquals("3", events.lastOf(EventType.DIAPER)?.id)
    }

    @Test
    fun `記録がない種別はnullを返す`() {
        val events = listOf(BabyEvent("1", EventType.FEED, at = 100))
        assertNull(events.lastOf(EventType.DIAPER))
    }
}
