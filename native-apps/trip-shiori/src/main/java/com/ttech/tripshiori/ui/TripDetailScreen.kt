package com.ttech.tripshiori.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.share.copyTripText
import com.ttech.tripshiori.share.shareTripFile
import com.ttech.tripshiori.share.shareTripPdf
import com.ttech.tripshiori.share.shareTripText
import java.time.LocalDate

private enum class Tab(val label: String) { SCHEDULE("日程"), PACKING("持ち物"), INFO("情報"), COST("費用") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    trip: Trip,
    today: LocalDate,
    onBack: () -> Unit,
    onUpdate: ((Trip) -> Trip) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.SCHEDULE) }
    var shareMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Toast.makeText(context, "共有できませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Filled.Share, contentDescription = "共有") }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(text = { Text("テキストで共有(LINE・メール)") }, onClick = { shareMenu = false; safely { shareTripText(context, trip) } })
                            DropdownMenuItem(text = { Text("PDFで共有") }, onClick = { shareMenu = false; safely { shareTripPdf(context, trip) } })
                            DropdownMenuItem(text = { Text("しおりファイルで共有") }, onClick = { shareMenu = false; safely { shareTripFile(context, trip) } })
                            DropdownMenuItem(
                                text = { Text("テキストをコピー") },
                                onClick = {
                                    shareMenu = false
                                    safely { copyTripText(context, trip); Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show() }
                                },
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "その他") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text("基本情報を編集") }, onClick = { moreMenu = false; editing = true })
                            DropdownMenuItem(text = { Text("このしおりを複製") }, onClick = { moreMenu = false; onDuplicate() })
                            DropdownMenuItem(text = { Text("このしおりを削除") }, onClick = { moreMenu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.SCHEDULE -> Icons.Filled.CalendarMonth
                                    Tab.PACKING -> Icons.Filled.Checklist
                                    Tab.INFO -> Icons.Filled.Info
                                    Tab.COST -> Icons.Filled.AttachMoney
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.SCHEDULE -> ScheduleTab(trip, today, onUpdate)
                Tab.PACKING -> PackingTab(trip, onUpdate)
                Tab.INFO -> InfoTab(trip, onUpdate, onEditTrip = { editing = true })
                Tab.COST -> CostTab(trip)
            }
        }
    }

    if (editing) {
        TripEditDialog(trip = trip, today = today, onSave = { edited -> onUpdate { edited }; editing = false }, onDismiss = { editing = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "しおりを削除しますか?",
            message = "「${trip.title}」を削除します。元に戻せません。",
            confirmLabel = "削除",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
