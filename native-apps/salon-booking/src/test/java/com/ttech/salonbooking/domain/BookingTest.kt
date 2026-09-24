package com.ttech.salonbooking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookingTest {
    private fun b(id: String, date: String, time: String, service: String = "") = Booking(id, "客$id", service, date, time)

    @Test
    fun `入力値から予約を作りお客様名が空なら作らない`() {
        assertEquals(b("1", "2026-09-24", "10:00", "カット").copy(customerName = "山田"), buildBooking("1", " 山田 ", " カット ", "2026-09-24", "10:00"))
        assertNull(buildBooking("1", " ", "カット", "2026-09-24", "10:00"))
    }

    @Test
    fun `今後の予約は過去の日付を除き日時の早い順`() {
        val bookings = listOf(
            b("1", "2026-09-25", "15:00"),
            b("2", "2026-09-23", "10:00"),
            b("3", "2026-09-24", "09:00"),
            b("4", "2026-09-25", "10:30"),
        )
        assertEquals(listOf("3", "4", "1"), bookings.upcoming("2026-09-24").map { it.id })
    }

    @Test
    fun `補足行はメニューがあるときだけ付ける`() {
        assertEquals("2026-09-24 10:00 ・ カット+カラー", b("1", "2026-09-24", "10:00", "カット+カラー").whenLabel())
        assertEquals("2026-09-24 10:00", b("1", "2026-09-24", "10:00").whenLabel())
    }
}
