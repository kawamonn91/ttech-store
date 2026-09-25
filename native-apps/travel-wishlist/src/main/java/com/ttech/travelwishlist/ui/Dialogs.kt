package com.ttech.travelwishlist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.travelwishlist.domain.Category
import com.ttech.travelwishlist.domain.Japan
import com.ttech.travelwishlist.domain.Limits
import com.ttech.travelwishlist.domain.Place
import com.ttech.travelwishlist.domain.Status
import com.ttech.travelwishlist.domain.locationLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, singleLine: Boolean = true, max: Int = Limits.MAX_SHORT, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(max)) },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 行きたい場所の追加・編集。[place] が null なら新規 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceEditDialog(place: Place?, onSave: (Place) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(place?.name ?: "") }
    var region by rememberSaveable { mutableStateOf(place?.region ?: "") }
    var country by rememberSaveable { mutableStateOf(place?.country ?: "") }
    var category by rememberSaveable { mutableStateOf(place?.category ?: Category.OTHER) }
    var priority by rememberSaveable { mutableIntStateOf(place?.priority ?: 2) }
    var months by rememberSaveable { mutableStateOf((place?.months ?: emptyList()).joinToString(",")) }
    var budget by rememberSaveable { mutableStateOf(place?.budget?.takeIf { it > 0 }?.toString() ?: "") }
    var url by rememberSaveable { mutableStateOf(place?.url ?: "") }
    var memo by rememberSaveable { mutableStateOf(place?.memo ?: "") }
    var pickRegion by remember { mutableStateOf(false) }
    val monthSet = months.split(',').mapNotNull { it.toIntOrNull() }.toSet()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (place == null) "行きたい場所を追加" else "行きたい場所を編集") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "場所の名前(例: 草津温泉)", max = Limits.MAX_NAME)
                OutlinedButton(onClick = { pickRegion = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (region.isEmpty()) "所在地を選ぶ(都道府県・海外)" else "所在地: $region")
                }
                if (region == Japan.OVERSEAS) Field(country, { country = it }, "国・地域(例: フランス)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Category.entries) { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("行きたい度", style = MaterialTheme.typography.bodyMedium)
                    StarInput(priority, { priority = it })
                }
                Text("行きたい時期", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in 1..12) {
                        FilterChip(
                            selected = m in monthSet,
                            onClick = { months = (if (m in monthSet) monthSet - m else monthSet + m).sorted().joinToString(",") },
                            label = { Text("${m}月") },
                        )
                    }
                }
                Field(budget, { budget = it.filter { c -> c.isDigit() } }, "予算の目安(円)", max = 9, keyboard = KeyboardType.Number)
                Field(url, { url = it }, "参考URL(http://・https://)", max = Limits.MAX_URL, keyboard = KeyboardType.Uri)
                Field(memo, { memo = it }, "メモ", singleLine = false, max = Limits.MAX_TEXT)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = place ?: Place(id = UUID.randomUUID().toString(), name = "")
                    onSave(
                        base.copy(
                            name = name.trim(),
                            region = region,
                            country = if (region == Japan.OVERSEAS) country.trim() else "",
                            category = category,
                            priority = priority,
                            months = monthSet.sorted(),
                            budget = budget.toIntOrNull() ?: 0,
                            url = url.trim(),
                            memo = memo.trim(),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
    if (pickRegion) {
        RegionDialog(current = region, onPick = { region = it; pickRegion = false }, onDismiss = { pickRegion = false })
    }
}

/** 所在地(都道府県・海外)を選ぶ */
@Composable
private fun RegionDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("所在地") },
        text = {
            LazyColumn {
                item { RegionRow("指定しない", current.isEmpty()) { onPick("") } }
                item { RegionRow("海外", current == Japan.OVERSEAS) { onPick(Japan.OVERSEAS) } }
                for ((area, prefectures) in Japan.REGIONS) {
                    item {
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        SectionTitle(area, Modifier.padding(top = 8.dp, bottom = 2.dp))
                    }
                    items(prefectures) { p -> RegionRow(p, current == p) { onPick(p) } }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

@Composable
private fun RegionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

/** 「行った」を記録する: 行った日・評価・感想 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitDialog(place: Place, today: LocalDate, onSave: (LocalDate, String, Int) -> Unit, onDismiss: () -> Unit) {
    var date by rememberSaveable { mutableStateOf((com.ttech.travelwishlist.domain.parseDateOrNull(place.visitedDate) ?: today).toString()) }
    var rating by rememberSaveable { mutableIntStateOf(place.rating) }
    var impression by rememberSaveable { mutableStateOf(place.impression) }
    var pickDate by remember { mutableStateOf(false) }
    val d = LocalDate.parse(date)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「${place.name}」に行きました") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("行った日: ${d.year}/${d.monthValue}/${d.dayOfMonth}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("満足度", style = MaterialTheme.typography.bodyMedium)
                    StarInput(rating, { rating = it }, max = 5, allowZero = true, size = 30.dp)
                }
                Field(impression, { impression = it }, "感想・思い出", singleLine = false, max = Limits.MAX_TEXT)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(d, impression, rating) }) { Text("記録する") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        date = Instant.ofEpochMilli(state.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        pickDate = false
                    },
                ) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("キャンセル") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun statusColor(status: Status) = when (status) {
    Status.WANT -> MaterialTheme.colorScheme.primary
    Status.PLANNING -> MaterialTheme.colorScheme.tertiary
    Status.VISITED -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun Place.summaryLine(): String = listOf(locationLabel(), category.label).filter { it.isNotEmpty() }.joinToString("・")
