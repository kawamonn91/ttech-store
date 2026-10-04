package com.ttech.attendancecount.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    private var nextId = 0
    private fun attendee(checkedIn: Boolean) = Attendee(id = (nextId++).toString(), name = "x", checkedIn = checkedIn)

    @Test
    fun `登録のみで誰も出席していないとき、出席も合計も0`() {
        val event = EventItem(id = "1", name = "e", attendees = listOf(attendee(false), attendee(false)))
        val s = event.summary()
        assertEquals(2, s.registered)
        assertEquals(0, s.checkedIn)
        assertEquals(2, s.absent)
        assertEquals(0, s.total)
    }

    @Test
    fun `出席者と飛び込みをあわせて合計になる`() {
        val event = EventItem(id = "1", name = "e", attendees = listOf(attendee(true), attendee(true), attendee(false)), walkIns = 3)
        val s = event.summary()
        assertEquals(3, s.registered)
        assertEquals(2, s.checkedIn)
        assertEquals(1, s.absent)
        assertEquals(3, s.walkIns)
        assertEquals(5, s.total)
    }

    @Test
    fun `参加者がいないときは登録も出席も0`() {
        val event = EventItem(id = "1", name = "e")
        val s = event.summary()
        assertEquals(0, s.registered)
        assertEquals(0, s.checkedIn)
        assertEquals(0, s.absent)
        assertEquals(0, s.total)
    }
}
