package com.ttech.tripshiori.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ttech.tripshiori.domain.Contact
import com.ttech.tripshiori.domain.ItemKind
import com.ttech.tripshiori.domain.Limits
import com.ttech.tripshiori.domain.Lodging
import com.ttech.tripshiori.domain.PACKING_PRESETS
import com.ttech.tripshiori.domain.ScheduleItem
import com.ttech.tripshiori.domain.Trip
import com.ttech.tripshiori.domain.dayCount
import com.ttech.tripshiori.domain.dayLabel
import com.ttech.tripshiori.domain.end
import com.ttech.tripshiori.domain.fullLabel
import com.ttech.tripshiori.domain.start
import com.ttech.tripshiori.domain.withDates
import java.time.LocalDate
import java.util.UUID

private fun newId() = UUID.randomUUID().toString()

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
private fun EditDialog(
    title: String,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } },
        confirmButton = { TextButton(onClick = onSave, enabled = saveEnabled) { Text("保存") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("削除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        },
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

/** しおりの基本情報(新規作成・編集の両方)。[trip] が null なら新規 */
@Composable
fun TripEditDialog(trip: Trip?, today: LocalDate, onSave: (Trip) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(trip?.title ?: "") }
    var destination by rememberSaveable { mutableStateOf(trip?.destination ?: "") }
    var travelers by rememberSaveable { mutableStateOf(trip?.travelers?.joinToString("、") ?: "") }
    var notes by rememberSaveable { mutableStateOf(trip?.notes ?: "") }
    var start by rememberSaveable { mutableStateOf((trip?.start() ?: today.plusDays(7)).toString()) }
    var end by rememberSaveable { mutableStateOf((trip?.end() ?: today.plusDays(9)).toString()) }
    var pickDates by remember { mutableStateOf(false) }

    val startDate = LocalDate.parse(start)
    val endDate = LocalDate.parse(end)
    val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

    EditDialog(
        title = if (trip == null) "新しいしおり" else "しおりの基本情報",
        saveEnabled = title.isNotBlank(),
        onSave = {
            val base = trip ?: Trip(id = newId(), title = "", startDate = start, endDate = end)
            val names = travelers.split('、', ',', '，', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            val changed = base.copy(
                title = title.trim(),
                destination = destination.trim(),
                travelers = names,
                notes = notes.trim(),
            )
            onSave(if (trip == null) changed.copy(startDate = start, endDate = end) else changed.withDates(startDate, endDate))
        },
        onDismiss = onDismiss,
    ) {
        Field(title, { title = it }, "タイトル(例: 京都 2泊3日)")
        Field(destination, { destination = it }, "行き先")
        OutlinedButton(onClick = { pickDates = true }, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${startDate.fullLabel()} 〜 ${endDate.fullLabel()}", style = MaterialTheme.typography.bodyMedium)
                Text(if (days == 1) "日帰り" else "${days - 1}泊${days}日", style = MaterialTheme.typography.labelMedium)
            }
        }
        Field(travelers, { travelers = it }, "メンバー(「、」で区切る)", max = 200)
        Field(notes, { notes = it }, "メモ", singleLine = false, max = Limits.MAX_TEXT)
    }
    if (pickDates) {
        DateRangeDialog(
            start = startDate,
            end = endDate,
            onConfirm = { s, e ->
                start = s.toString()
                end = (if (e.isBefore(s)) s else e).toString()
                pickDates = false
            },
            onDismiss = { pickDates = false },
        )
    }
}

/** 予定の追加・編集。[item] が null なら新規(日は [initialDay]) */
@Composable
fun ItemEditDialog(trip: Trip, item: ScheduleItem?, initialDay: Int, onSave: (ScheduleItem) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(item?.kind ?: ItemKind.SIGHT) }
    var day by rememberSaveable { mutableIntStateOf(item?.day ?: initialDay) }
    var time by rememberSaveable { mutableStateOf(item?.time ?: "") }
    var title by rememberSaveable { mutableStateOf(item?.title ?: "") }
    var place by rememberSaveable { mutableStateOf(item?.place ?: "") }
    var memo by rememberSaveable { mutableStateOf(item?.memo ?: "") }
    var cost by rememberSaveable { mutableStateOf(item?.cost?.takeIf { it > 0 }?.toString() ?: "") }
    var pickTime by remember { mutableStateOf(false) }

    EditDialog(
        title = if (item == null) "予定を追加" else "予定を編集",
        saveEnabled = title.isNotBlank(),
        onSave = {
            onSave(
                ScheduleItem(
                    id = item?.id ?: newId(), day = day, time = time, title = title.trim(), place = place.trim(),
                    memo = memo.trim(), kind = kind, cost = cost.filter { it.isDigit() }.take(9).toIntOrNull() ?: 0,
                ),
            )
        },
        onDelete = onDelete,
        onDismiss = onDismiss,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ItemKind.entries) { k ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
            }
        }
        if (trip.dayCount() > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(trip.dayCount()) { d ->
                    FilterChip(selected = day == d, onClick = { day = d }, label = { Text(trip.dayLabel(d)) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickTime = true }) { Text(if (time.isEmpty()) "時刻を選ぶ" else time) }
            if (time.isNotEmpty()) TextButton(onClick = { time = "" }) { Text("時刻なし") }
        }
        Field(title, { title = it }, "やること(例: 清水寺を見学)")
        Field(place, { place = it }, "場所")
        Field(memo, { memo = it }, "メモ(予約番号・持ち物など)", singleLine = false, max = Limits.MAX_TEXT)
        Field(cost, { cost = it.filter { c -> c.isDigit() } }, "費用(円)", max = 9, keyboard = KeyboardType.Number)
    }
    if (pickTime) TimeDialog(current = time, onConfirm = { time = it; pickTime = false }, onDismiss = { pickTime = false })
}

@Composable
fun LodgingDialog(lodging: Lodging?, onSave: (Lodging) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(lodging?.name ?: "") }
    var address by rememberSaveable { mutableStateOf(lodging?.address ?: "") }
    var phone by rememberSaveable { mutableStateOf(lodging?.phone ?: "") }
    var reservation by rememberSaveable { mutableStateOf(lodging?.reservation ?: "") }
    var times by rememberSaveable { mutableStateOf(lodging?.times ?: "") }
    var note by rememberSaveable { mutableStateOf(lodging?.note ?: "") }
    EditDialog(
        title = if (lodging == null) "宿泊先を追加" else "宿泊先を編集",
        saveEnabled = name.isNotBlank(),
        onSave = {
            onSave(Lodging(lodging?.id ?: newId(), name.trim(), address.trim(), phone.trim(), reservation.trim(), times.trim(), note.trim()))
        },
        onDelete = onDelete,
        onDismiss = onDismiss,
    ) {
        Field(name, { name = it }, "宿の名前")
        Field(address, { address = it }, "住所", max = 200)
        Field(phone, { phone = it }, "電話番号", max = 40, keyboard = KeyboardType.Phone)
        Field(times, { times = it }, "チェックイン・アウト(例: 15:00〜 / 11:00まで)")
        Field(reservation, { reservation = it }, "予約番号")
        Field(note, { note = it }, "メモ", singleLine = false, max = Limits.MAX_TEXT)
    }
}

@Composable
fun ContactDialog(contact: Contact?, onSave: (Contact) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(contact?.name ?: "") }
    var phone by rememberSaveable { mutableStateOf(contact?.phone ?: "") }
    var note by rememberSaveable { mutableStateOf(contact?.note ?: "") }
    EditDialog(
        title = if (contact == null) "連絡先を追加" else "連絡先を編集",
        saveEnabled = name.isNotBlank(),
        onSave = { onSave(Contact(contact?.id ?: newId(), name.trim(), phone.trim(), note.trim())) },
        onDelete = onDelete,
        onDismiss = onDismiss,
    ) {
        Field(name, { name = it }, "名前(例: 集合場所の担当・ホテル)")
        Field(phone, { phone = it }, "電話番号", max = 40, keyboard = KeyboardType.Phone)
        Field(note, { note = it }, "メモ", max = 200)
    }
}

/** 持ち物のおすすめセットから、追加するものを選ぶ */
@Composable
fun PresetDialog(existing: Set<String>, onAdd: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("おすすめの持ち物") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (preset in PACKING_PRESETS) {
                    Text(preset.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    for (name in preset.items) {
                        val has = name.trim().lowercase() in existing
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = has || name in selected,
                                enabled = !has,
                                onCheckedChange = { on -> selected = if (on) selected + name else selected - name },
                            )
                            Text(name + if (has) "(追加済み)" else "", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(PACKING_PRESETS.flatMap { it.items }.filter { it in selected }.distinct()) }, enabled = selected.isNotEmpty()) { Text("${selected.size}件を追加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}
