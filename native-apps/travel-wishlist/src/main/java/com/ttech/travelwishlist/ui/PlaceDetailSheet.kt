package com.ttech.travelwishlist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ttech.travelwishlist.domain.Place
import com.ttech.travelwishlist.domain.Status
import com.ttech.travelwishlist.domain.monthsLabel
import com.ttech.travelwishlist.domain.parseDateOrNull
import com.ttech.travelwishlist.domain.stars
import com.ttech.travelwishlist.domain.yen
import com.ttech.travelwishlist.share.openInMaps
import com.ttech.travelwishlist.share.openUrl
import com.ttech.travelwishlist.share.sharePlace

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaceDetailSheet(
    place: Place,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onVisited: () -> Unit,
    onSetStatus: (Status) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(place.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text(place.status.label, style = MaterialTheme.typography.labelLarge, color = statusColor(place.status))
            }
            val summary = place.summaryLine()
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("行きたい度", style = MaterialTheme.typography.bodyMedium)
                Stars(place.priority, size = 20.dp)
            }
            if (place.months.isNotEmpty()) Text("行きたい時期: ${monthsLabel(place.months)}", style = MaterialTheme.typography.bodyMedium)
            if (place.budget > 0) Text("予算の目安: ${yen(place.budget)}", style = MaterialTheme.typography.bodyMedium)
            if (place.memo.isNotEmpty()) Text(place.memo, style = MaterialTheme.typography.bodyMedium)
            if (place.url.isNotEmpty()) {
                Text(
                    place.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    maxLines = 2,
                    modifier = Modifier.linkClickable { openUrl(context, place.url) },
                )
            }

            if (place.status == Status.VISITED) {
                SoftCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionTitle("行ったときの記録")
                        parseDateOrNull(place.visitedDate)?.let { Text("${it.year}/${it.monthValue}/${it.dayOfMonth}", style = MaterialTheme.typography.bodyMedium) }
                        if (place.rating > 0) Text(stars(place.rating), color = StarColor, style = MaterialTheme.typography.titleMedium)
                        if (place.impression.isNotEmpty()) Text(place.impression, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (place.status == Status.VISITED) {
                    Button(onClick = onVisited) { Text("記録を編集") }
                    OutlinedButton(onClick = { onSetStatus(Status.WANT) }) { Text("行きたいに戻す") }
                } else {
                    Button(onClick = onVisited) { Text("行った!") }
                    if (place.status == Status.WANT) OutlinedButton(onClick = { onSetStatus(Status.PLANNING) }) { Text("計画中にする") }
                    else OutlinedButton(onClick = { onSetStatus(Status.WANT) }) { Text("行きたいに戻す") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("編集") }
                TextButton(onClick = { openInMaps(context, listOf(place.name, place.region.takeIf { it != "海外" } ?: place.country).filter { it.isNotEmpty() }.joinToString(" ")) }) { Text("地図で見る") }
                TextButton(onClick = { sharePlace(context, place) }) { Text("共有") }
                TextButton(onClick = { confirmDelete = true }) { Text("削除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "削除しますか?",
            message = "「${place.name}」を削除します。元に戻せません。",
            confirmLabel = "削除",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** 波紋なしで押せるようにする(リンク風の文字用) */
@Composable
private fun Modifier.linkClickable(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
