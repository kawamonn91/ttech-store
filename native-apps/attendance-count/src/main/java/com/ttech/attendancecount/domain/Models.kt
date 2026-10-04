package com.ttech.attendancecount.domain

import kotlinx.serialization.Serializable

@Serializable
data class Attendee(val id: String, val name: String, val checkedIn: Boolean = false)

@Serializable
data class EventItem(
    val id: String,
    val name: String,
    val attendees: List<Attendee> = emptyList(),
    val walkIns: Int = 0,
)

data class EventSummary(val registered: Int, val checkedIn: Int, val absent: Int, val walkIns: Int) {
    val total: Int get() = checkedIn + walkIns
}

fun EventItem.summary(): EventSummary {
    val checkedInCount = attendees.count { it.checkedIn }
    return EventSummary(
        registered = attendees.size,
        checkedIn = checkedInCount,
        absent = attendees.size - checkedInCount,
        walkIns = walkIns,
    )
}
