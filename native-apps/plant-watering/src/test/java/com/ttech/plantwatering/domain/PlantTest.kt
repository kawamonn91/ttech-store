package com.ttech.plantwatering.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PlantTest {
    private val today = LocalDate.of(2026, 9, 24)
    private fun plant(name: String = "モンステラ", interval: Int = 7, last: String = "2026-09-24") =
        Plant(id = name, name = name, intervalDays = interval, lastWateredDate = last)

    @Test
    fun `名前が空や空白だけなら登録できない`() {
        assertTrue(isValidPlantName("モンステラ"))
        assertFalse(isValidPlantName(""))
        assertFalse(isValidPlantName("  "))
    }

    @Test
    fun `間隔が空か0なら7日に戻す`() {
        assertEquals(7, normalizeIntervalDays(null))
        assertEquals(7, normalizeIntervalDays(0))
        assertEquals(3, normalizeIntervalDays(3))
    }

    @Test
    fun `水やりした日からの経過日数を数える`() {
        assertEquals(0, daysSinceWatered(plant(last = "2026-09-24"), today))
        assertEquals(5, daysSinceWatered(plant(last = "2026-09-19"), today))
    }

    @Test
    fun `残り日数は間隔から経過日数を引いた値`() {
        assertEquals(2, remainingDays(plant(interval = 7, last = "2026-09-19"), today))
    }

    @Test
    fun `残りが0日以下なら水やりの時期で超過日数は負の値になる`() {
        val overdue = plant(interval = 7, last = "2026-09-14")
        assertEquals(-3, remainingDays(overdue, today))
        assertTrue(isDue(overdue, today))
        assertTrue(isDue(plant(interval = 7, last = "2026-09-17"), today))
        assertFalse(isDue(plant(interval = 7, last = "2026-09-18"), today))
    }

    @Test
    fun `水やりが近い順に並べ超過している植物が先頭になる`() {
        val plants = listOf(
            plant("あと3日", interval = 7, last = "2026-09-20"),
            plant("超過", interval = 3, last = "2026-09-10"),
            plant("あと6日", interval = 7, last = "2026-09-23"),
        )
        assertEquals(listOf("超過", "あと3日", "あと6日"), plants.sortedByUrgency(today).map { it.name })
    }

    @Test
    fun `残り日数が同じ植物は登録した順のまま`() {
        val plants = listOf(plant("A", 7, "2026-09-20"), plant("B", 7, "2026-09-20"))
        assertEquals(listOf("A", "B"), plants.sortedByUrgency(today).map { it.name })
    }

    @Test
    fun `水やりすると最終日が今日になり期限が延びる`() {
        val watered = plant(interval = 7, last = "2026-09-10").wateredOn(today)
        assertEquals("2026-09-24", watered.lastWateredDate)
        assertEquals(7, remainingDays(watered, today))
    }

    @Test
    fun `通知の対象は水やりの時期の植物の名前だけ`() {
        val plants = listOf(
            plant("元気", interval = 7, last = "2026-09-23"),
            plant("乾燥", interval = 3, last = "2026-09-10"),
        )
        assertEquals(listOf("乾燥"), plants.dueNames(today))
    }

    @Test
    fun `次の通知は9時前なら今日の9時`() {
        val now = ZonedDateTime.of(2026, 9, 24, 7, 30, 0, 0, ZoneId.of("Asia/Tokyo"))
        assertEquals(ZonedDateTime.of(2026, 9, 24, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo")), nextReminderTime(now))
    }

    @Test
    fun `次の通知は9時を過ぎていたら明日の9時`() {
        val now = ZonedDateTime.of(2026, 9, 24, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo"))
        assertEquals(ZonedDateTime.of(2026, 9, 25, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo")), nextReminderTime(now))
    }
}
