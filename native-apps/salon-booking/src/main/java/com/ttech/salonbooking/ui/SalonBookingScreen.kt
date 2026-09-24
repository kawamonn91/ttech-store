package com.ttech.salonbooking.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttech.salonbooking.data.BookingStore
import com.ttech.salonbooking.domain.Booking
import com.ttech.salonbooking.domain.buildBooking
import com.ttech.salonbooking.domain.upcoming
import com.ttech.salonbooking.domain.whenLabel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SalonBookingScreen() {
    val context = LocalContext.current
    val store = remember { BookingStore(context) }
    val scope = rememberCoroutineScope()
    val bookings by store.bookings.collectAsState(initial = emptyList())

    var customerName by remember { mutableStateOf("") }
    var service by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf("10:00") }

    fun addBooking() {
        val booking = buildBooking(UUID.randomUUID().toString(), customerName, service, date.toString(), time) ?: return
        scope.launch { store.add(booking) }
        customerName = ""; service = ""
    }

    val upcoming = bookings.upcoming(LocalDate.now().toString())

    Scaffold(topBar = { TopAppBar(title = { Text("サロン予約台帳") }) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            label = { Text("お客様名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = service,
                            onValueChange = { service = it },
                            label = { Text("メニュー") },
                            placeholder = { Text("例: カット+カラー") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateField("日付", date, Modifier.weight(1f)) { date = it }
                            TimeField("時間", time, Modifier.weight(1f)) { time = it }
                        }
                        Button(onClick = ::addBooking, enabled = customerName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Text("予約を登録")
                        }
                    }
                }
            }

            item {
                Text(
                    "今後の予約(${upcoming.size}件)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (upcoming.isEmpty()) {
                item { Text("今後の予約はありません", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            items(upcoming, key = Booking::id) { b ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.customerName, style = MaterialTheme.typography.titleMedium)
                            Text(b.whenLabel(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { scope.launch { store.remove(b.id) } }) { Text("削除") }
                    }
                }
            }
        }
    }
}

private fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate, modifier: Modifier = Modifier, onChange: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(date.toString())
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochMillis())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(it.toLocalDate()) }
                    showPicker = false
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val context = LocalContext.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = {
                val (h, m) = value.split(":").map { it.toIntOrNull() ?: 0 }
                TimePickerDialog(context, { _, hour, minute -> onChange("%02d:%02d".format(hour, minute)) }, h, m, true).show()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text(value) }
    }
}
