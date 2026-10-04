package com.ttech.attendancecount.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ttech.attendancecount.data.EventStore
import com.ttech.attendancecount.domain.EventItem
import kotlinx.coroutines.launch

/** 画面の切り替え: イベント一覧 ←→ イベント詳細(参加者・出席の管理) */
@Composable
fun AttendanceApp() {
    val context = LocalContext.current
    val store = remember { EventStore(context) }
    val scope = rememberCoroutineScope()
    val events by store.events.collectAsState(initial = emptyList())
    var selectedId by remember { mutableStateOf<String?>(null) }

    fun update(newList: List<EventItem>) = scope.launch { store.save(newList) }

    BackHandler(enabled = selectedId != null) { selectedId = null }

    val selected = events.find { it.id == selectedId }
    if (selected != null) {
        EventDetailScreen(
            event = selected,
            onBack = { selectedId = null },
            onChange = { updated -> update(events.map { if (it.id == updated.id) updated else it }) },
            onDelete = {
                update(events.filterNot { it.id == selected.id })
                selectedId = null
            },
        )
    } else {
        EventListScreen(
            events = events,
            onOpen = { selectedId = it.id },
            onCreate = { name ->
                val newEvent = EventItem(id = System.currentTimeMillis().toString(), name = name)
                update(events + newEvent)
                selectedId = newEvent.id
            },
            onDelete = { target -> update(events.filterNot { it.id == target.id }) },
        )
    }
}
